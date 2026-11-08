
CREATE FUNCTION fn_changed_keys(p_old JSONB, p_new JSONB)
RETURNS TEXT[]
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT COALESCE(array_agg(k ORDER BY k), '{}')
    FROM (
        SELECT jsonb_object_keys(p_old) AS k
        UNION
        SELECT jsonb_object_keys(p_new)
    ) keys
    WHERE p_old -> k IS DISTINCT FROM p_new -> k;
$$;


CREATE FUNCTION fn_guard_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_mutable  TEXT[] := string_to_array(TG_ARGV[0], ',');
    v_final    TEXT[] := CASE WHEN TG_NARGS > 1 THEN string_to_array(TG_ARGV[1], ',') ELSE '{}' END;
    v_old      JSONB  := to_jsonb(OLD);
    v_new      JSONB  := to_jsonb(NEW);
    v_illegal  TEXT[];
BEGIN
    IF (v_old ->> 'status') = ANY (v_final) THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'IMMUTABLE_FINAL_STATE',
            DETAIL = format('%s row %s is %s and can no longer change',
                            TG_TABLE_NAME, v_old ->> 'id', v_old ->> 'status');
    END IF;

    SELECT array_agg(k ORDER BY k)
    INTO v_illegal
    FROM unnest(fn_changed_keys(v_old, v_new)) AS k
    WHERE k <> ALL (v_mutable);

    IF v_illegal IS NOT NULL THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'IMMUTABLE_FIELD',
            DETAIL = format('%s row %s: field(s) %s cannot be changed',
                            TG_TABLE_NAME, v_old ->> 'id', array_to_string(v_illegal, ', '));
    END IF;

    RETURN NEW;
END;
$$;


CREATE TRIGGER trg_transactions_guard_update
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_update(
        'status,failure_code,failure_reason,completed_at',
        'SUCCESSFUL,FAILED'
    );

CREATE TRIGGER trg_dynamic_accounts_guard_update
    BEFORE UPDATE ON dynamic_accounts
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_update(
        'status,paid_transaction_id,updated_at',
        'PAID,EXPIRED'
    );


CREATE TRIGGER trg_accounts_guard_update
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_update(
        'balance,account_name,auto_sweep,first_name,last_name,email,phone,status,updated_at',
        'CLOSED'
    );


CREATE TRIGGER trg_merchants_guard_update
    BEFORE UPDATE ON merchants
    FOR EACH ROW
    EXECUTE FUNCTION fn_guard_update(
        'business_name,email,phone,address,status,api_key_hash,webhook_url,updated_at'
    );



CREATE FUNCTION fn_guard_balance_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF current_setting('wallet.ledger_posting', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'BALANCE_CHANGE_OUTSIDE_LEDGER',
            DETAIL = format('Balance of account %s can only change through fn_post_journal', OLD.id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_guard_balance
    BEFORE UPDATE OF balance ON accounts
    FOR EACH ROW
    WHEN (OLD.balance IS DISTINCT FROM NEW.balance)
    EXECUTE FUNCTION fn_guard_balance_change();



CREATE FUNCTION fn_reject_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'IMMUTABLE_TABLE',
        DETAIL = format('%s on %s is not allowed', TG_OP, TG_TABLE_NAME);
END;
$$;

CREATE TRIGGER trg_merchants_no_delete        BEFORE DELETE ON merchants        FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
CREATE TRIGGER trg_accounts_no_delete         BEFORE DELETE ON accounts         FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
CREATE TRIGGER trg_transactions_no_delete     BEFORE DELETE ON transactions     FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();
CREATE TRIGGER trg_dynamic_accounts_no_delete BEFORE DELETE ON dynamic_accounts FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();



CREATE TABLE audit_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name      VARCHAR(63)   NOT NULL,
    row_id          BIGINT        NOT NULL,
    operation       VARCHAR(10)   NOT NULL,
    changed_fields  TEXT[],
    old_values      JSONB,
    new_values      JSONB         NOT NULL,
    actor           VARCHAR(150),
    request_id      VARCHAR(100),
    db_user         VARCHAR(63)   NOT NULL DEFAULT session_user,
    changed_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_log_operation CHECK (operation IN ('INSERT', 'UPDATE'))
);

CREATE INDEX idx_audit_log_row   ON audit_log (table_name, row_id, changed_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor, changed_at DESC);

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_reject_mutation();



CREATE FUNCTION fn_audit_row()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_old       JSONB;
    v_new       JSONB := to_jsonb(NEW);
    v_ignored   TEXT[] := ARRAY['updated_at'];
    v_changed   TEXT[];
BEGIN
    IF TG_TABLE_NAME = 'accounts' THEN
        v_ignored := v_ignored || 'balance';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        v_old := to_jsonb(OLD);

        SELECT array_agg(k ORDER BY k)
        INTO v_changed
        FROM unnest(fn_changed_keys(v_old, v_new)) AS k
        WHERE k <> ALL (v_ignored);

        IF v_changed IS NULL THEN
            RETURN NULL;   -- nothing meaningful changed
        END IF;
    END IF;

    INSERT INTO audit_log (table_name, row_id, operation, changed_fields,
                           old_values, new_values, actor, request_id)
    VALUES (
        TG_TABLE_NAME,
        (v_new ->> 'id')::BIGINT,
        TG_OP,
        v_changed,
        v_old - 'api_key_hash',
        v_new - 'api_key_hash',
        NULLIF(current_setting('app.actor', true), ''),
        NULLIF(current_setting('app.request_id', true), '')
    );

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_merchants_audit
    AFTER INSERT OR UPDATE ON merchants
    FOR EACH ROW EXECUTE FUNCTION fn_audit_row();

CREATE TRIGGER trg_accounts_audit
    AFTER INSERT OR UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION fn_audit_row();

CREATE TRIGGER trg_dynamic_accounts_audit
    AFTER INSERT OR UPDATE ON dynamic_accounts
    FOR EACH ROW EXECUTE FUNCTION fn_audit_row();
