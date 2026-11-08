
CREATE OR REPLACE FUNCTION fn_audit_row()
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
        v_ignored := array_append(v_ignored, 'balance');
    END IF;

    IF TG_OP = 'UPDATE' THEN
        v_old := to_jsonb(OLD);

        SELECT array_agg(k ORDER BY k)
        INTO v_changed
        FROM unnest(fn_changed_keys(v_old, v_new)) AS k
        WHERE k <> ALL (v_ignored);

        IF v_changed IS NULL THEN
            RETURN NULL;
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
