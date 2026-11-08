
CREATE OR REPLACE FUNCTION fn_post_inflow(
    p_account_number  TEXT,
    p_amount          BIGINT,
    p_session_id      TEXT,
    p_sender_name     TEXT,
    p_narration       TEXT
)
RETURNS TABLE (out_transaction_id BIGINT, out_replayed BOOLEAN, out_error_code TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_existing      transactions%ROWTYPE;
    v_account       accounts%ROWTYPE;
    v_settlement_id BIGINT;
    v_sweep_target  BIGINT;
    v_txn_id        BIGINT;
    v_narration     TEXT;
BEGIN
    IF p_amount IS NULL OR p_amount <= 0 THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'INVALID_AMOUNT'::TEXT;
        RETURN;
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended('inflow:' || p_session_id, 0));

    SELECT * INTO v_existing
    FROM transactions
    WHERE transaction_type = 'INFLOW' AND external_reference = p_session_id;

    IF FOUND THEN
        SELECT * INTO v_account FROM accounts WHERE id = v_existing.destination_account_id;
        IF v_existing.amount <> p_amount OR v_account.account_number IS DISTINCT FROM p_account_number THEN
            RETURN QUERY SELECT NULL::BIGINT, FALSE, 'DUPLICATE_SESSION_MISMATCH'::TEXT;
        ELSE
            RETURN QUERY SELECT v_existing.id, TRUE, NULL::TEXT;
        END IF;
        RETURN;
    END IF;

    SELECT * INTO v_account FROM accounts WHERE account_number = p_account_number;
    IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'DESTINATION_ACCOUNT_NOT_FOUND'::TEXT;
        RETURN;
    END IF;

    SELECT id INTO v_settlement_id FROM accounts WHERE system_code = 'SYS_INBOUND_SETTLEMENT';

    v_sweep_target := fn_sweep_target(v_account.id);
    PERFORM fn_lock_accounts(ARRAY[v_settlement_id, v_account.id, v_sweep_target]);
    SELECT * INTO v_account FROM accounts WHERE id = v_account.id;

    v_narration := COALESCE(NULLIF(p_narration, ''), 'Transfer from ' || COALESCE(NULLIF(p_sender_name, ''), 'external sender'));

    IF v_account.status <> 'ACTIVE' THEN
        INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                                  source_account_id, destination_account_id, external_reference,
                                  narration, failure_code, failure_reason, completed_at)
        VALUES (fn_new_reference('INF'), v_account.merchant_id, 'INFLOW', 'FAILED', p_amount, v_account.currency,
                v_settlement_id, v_account.id, p_session_id,
                v_narration, 'ACCOUNT_NOT_ACTIVE',
                format('Account %s is %s', v_account.account_number, v_account.status), now())
        RETURNING id INTO v_txn_id;

        RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
        RETURN;
    END IF;

    INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                              source_account_id, destination_account_id, external_reference, narration)
    VALUES (fn_new_reference('INF'), v_account.merchant_id, 'INFLOW', 'PENDING', p_amount, v_account.currency,
            v_settlement_id, v_account.id, p_session_id, v_narration)
    RETURNING id INTO v_txn_id;

    PERFORM fn_post_journal(v_txn_id, ARRAY[
        ROW(v_settlement_id, 'DEBIT',  p_amount)::ledger_line,
        ROW(v_account.id,    'CREDIT', p_amount)::ledger_line
    ]);

    PERFORM fn_auto_sweep(v_txn_id, v_account.id, p_amount);

    RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
END;
$$;
