
CREATE OR REPLACE FUNCTION fn_transfer(
    p_merchant_id         BIGINT,
    p_source_number       TEXT,
    p_destination_number  TEXT,
    p_amount              BIGINT,
    p_narration           TEXT,
    p_idempotency_key     TEXT,
    p_request_hash        TEXT
)
RETURNS TABLE (out_transaction_id BIGINT, out_replayed BOOLEAN, out_error_code TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_existing        transactions%ROWTYPE;
    v_source          accounts%ROWTYPE;
    v_dest            accounts%ROWTYPE;
    v_sweep_target    BIGINT;
    v_txn_id          BIGINT;
    v_failure_code    TEXT;
    v_failure_reason  TEXT;
BEGIN
    IF p_amount IS NULL OR p_amount <= 0 THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'INVALID_AMOUNT'::TEXT;
        RETURN;
    END IF;


    PERFORM pg_advisory_xact_lock(hashtextextended('transfer:' || p_merchant_id || ':' || p_idempotency_key, 0));

    SELECT * INTO v_existing
    FROM transactions
    WHERE merchant_id = p_merchant_id AND idempotency_key = p_idempotency_key;

    IF FOUND THEN
        IF v_existing.request_hash IS DISTINCT FROM p_request_hash THEN
            RETURN QUERY SELECT NULL::BIGINT, FALSE, 'IDEMPOTENCY_KEY_REUSED'::TEXT;
        ELSE
            RETURN QUERY SELECT v_existing.id, TRUE, NULL::TEXT;
        END IF;
        RETURN;
    END IF;


    SELECT * INTO v_source FROM accounts WHERE account_number = p_source_number;
    IF NOT FOUND OR v_source.merchant_id IS DISTINCT FROM p_merchant_id THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'SOURCE_ACCOUNT_NOT_FOUND'::TEXT;
        RETURN;
    END IF;

    SELECT * INTO v_dest FROM accounts WHERE account_number = p_destination_number;
    IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'DESTINATION_ACCOUNT_NOT_FOUND'::TEXT;
        RETURN;
    END IF;

    IF v_source.id = v_dest.id THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'SAME_ACCOUNT_TRANSFER'::TEXT;
        RETURN;
    END IF;


    v_sweep_target := fn_sweep_target(v_dest.id);
    PERFORM fn_lock_accounts(ARRAY[v_source.id, v_dest.id, v_sweep_target]);

    SELECT * INTO v_source FROM accounts WHERE id = v_source.id;
    SELECT * INTO v_dest   FROM accounts WHERE id = v_dest.id;

    IF v_source.status <> 'ACTIVE' THEN
        v_failure_code   := 'ACCOUNT_NOT_ACTIVE';
        v_failure_reason := format('Source account %s is %s', v_source.account_number, v_source.status);
    ELSIF v_dest.status <> 'ACTIVE' THEN
        v_failure_code   := 'ACCOUNT_NOT_ACTIVE';
        v_failure_reason := format('Destination account %s is %s', v_dest.account_number, v_dest.status);
    ELSIF v_source.currency <> v_dest.currency THEN
        v_failure_code   := 'CURRENCY_MISMATCH';
        v_failure_reason := format('Source is %s, destination is %s', v_source.currency, v_dest.currency);
    ELSIF v_source.balance < p_amount THEN
        v_failure_code   := 'INSUFFICIENT_FUNDS';
        v_failure_reason := format('Account %s has NGN %s, transfer needs NGN %s',
                                   v_source.account_number,
                                   to_char(v_source.balance / 100.0, 'FM999999999999990.00'),
                                   to_char(p_amount / 100.0, 'FM999999999999990.00'));
    END IF;

    IF v_failure_code IS NOT NULL THEN
        INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                                  source_account_id, destination_account_id, idempotency_key, request_hash,
                                  narration, failure_code, failure_reason, completed_at)
        VALUES (fn_new_reference('TRF'), p_merchant_id, 'TRANSFER', 'FAILED', p_amount, v_source.currency,
                v_source.id, v_dest.id, p_idempotency_key, p_request_hash,
                p_narration, v_failure_code, v_failure_reason, now())
        RETURNING id INTO v_txn_id;

        RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
        RETURN;
    END IF;

    INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                              source_account_id, destination_account_id, idempotency_key, request_hash,
                              narration)
    VALUES (fn_new_reference('TRF'), p_merchant_id, 'TRANSFER', 'PENDING', p_amount, v_source.currency,
            v_source.id, v_dest.id, p_idempotency_key, p_request_hash,
            p_narration)
    RETURNING id INTO v_txn_id;

    PERFORM fn_post_journal(v_txn_id, ARRAY[
        ROW(v_source.id, 'DEBIT',  p_amount)::ledger_line,
        ROW(v_dest.id,   'CREDIT', p_amount)::ledger_line
    ]);

    PERFORM fn_auto_sweep(v_txn_id, v_dest.id, p_amount);

    RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
END;
$$;
