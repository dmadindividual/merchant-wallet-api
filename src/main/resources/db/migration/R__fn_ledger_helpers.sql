
CREATE OR REPLACE FUNCTION fn_new_reference(p_prefix TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
    SELECT p_prefix || '-'
        || to_char(clock_timestamp() AT TIME ZONE 'UTC', 'YYYYMMDD') || '-'
        || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
$$;



CREATE OR REPLACE FUNCTION fn_lock_accounts(p_account_ids BIGINT[])
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    FOR v_id IN
        SELECT DISTINCT id FROM unnest(p_account_ids) AS id WHERE id IS NOT NULL ORDER BY id
    LOOP
        PERFORM 1 FROM accounts WHERE id = v_id FOR UPDATE;
    END LOOP;
END;
$$;



CREATE OR REPLACE FUNCTION fn_sweep_target(p_account_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
AS $$
    SELECT p.id
    FROM accounts s
    JOIN accounts p ON p.merchant_id = s.merchant_id AND p.account_type = 'PRIMARY'
    WHERE s.id = p_account_id
      AND s.account_type = 'SUBSIDIARY'
      AND s.auto_sweep;
$$;



CREATE OR REPLACE FUNCTION fn_auto_sweep(p_credit_transaction_id BIGINT, p_account_id BIGINT, p_amount BIGINT)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    v_sub      accounts%ROWTYPE;
    v_primary  accounts%ROWTYPE;
    v_sweep_id BIGINT;
BEGIN
    SELECT * INTO v_sub FROM accounts WHERE id = p_account_id;
    IF v_sub.account_type <> 'SUBSIDIARY' OR NOT v_sub.auto_sweep OR v_sub.status <> 'ACTIVE' THEN
        RETURN NULL;
    END IF;

    SELECT * INTO v_primary
    FROM accounts
    WHERE merchant_id = v_sub.merchant_id AND account_type = 'PRIMARY';

    IF NOT FOUND OR v_primary.status <> 'ACTIVE' THEN
        RETURN NULL;
    END IF;

    INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                              source_account_id, destination_account_id, parent_transaction_id, narration)
    VALUES (fn_new_reference('SWP'), v_sub.merchant_id, 'SWEEP', 'PENDING', p_amount, v_sub.currency,
            v_sub.id, v_primary.id, p_credit_transaction_id, 'Auto-sweep to primary account')
    RETURNING id INTO v_sweep_id;

    PERFORM fn_post_journal(v_sweep_id, ARRAY[
        ROW(v_sub.id,     'DEBIT',  p_amount)::ledger_line,
        ROW(v_primary.id, 'CREDIT', p_amount)::ledger_line
    ]);

    RETURN v_sweep_id;
END;
$$;
