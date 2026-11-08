
CREATE OR REPLACE FUNCTION fn_post_journal(
    p_transaction_id BIGINT,
    p_lines          ledger_line[]
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_txn           transactions%ROWTYPE;
    v_account       accounts%ROWTYPE;
    v_line          ledger_line;
    v_account_ids   BIGINT[];
    v_account_id    BIGINT;
    v_total_debit   BIGINT := 0;
    v_total_credit  BIGINT := 0;
    v_balance_after BIGINT;
BEGIN
    IF p_lines IS NULL OR cardinality(p_lines) < 2 THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_TOO_FEW_LINES',
            DETAIL = 'A journal needs at least one debit and one credit line';
    END IF;

    FOREACH v_line IN ARRAY p_lines LOOP
        IF v_line.account_id IS NULL THEN
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_MISSING_ACCOUNT';
        END IF;

        IF v_line.amount IS NULL OR v_line.amount <= 0 THEN
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_INVALID_AMOUNT',
                DETAIL = format('Line for account %s has amount %s', v_line.account_id, v_line.amount);
        END IF;

        IF v_line.entry_type = 'DEBIT' THEN
            v_total_debit := v_total_debit + v_line.amount;
        ELSIF v_line.entry_type = 'CREDIT' THEN
            v_total_credit := v_total_credit + v_line.amount;
        ELSE
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_INVALID_ENTRY_TYPE',
                DETAIL = format('Entry type %s is not DEBIT or CREDIT', v_line.entry_type);
        END IF;
    END LOOP;

    IF v_total_debit <> v_total_credit THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_UNBALANCED',
            DETAIL = format('Debits %s do not equal credits %s', v_total_debit, v_total_credit);
    END IF;

    -- 2. The transaction must exist and still be PENDING -----------------
    SELECT * INTO v_txn
    FROM transactions
    WHERE id = p_transaction_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_TRANSACTION_NOT_FOUND',
            DETAIL = format('Transaction %s does not exist', p_transaction_id);
    END IF;

    IF v_txn.status <> 'PENDING' THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_TRANSACTION_NOT_PENDING',
            DETAIL = format('Transaction %s is %s', v_txn.reference, v_txn.status);
    END IF;

    IF v_total_debit <> v_txn.amount THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_AMOUNT_MISMATCH',
            DETAIL = format('Journal total %s does not match transaction amount %s',
                            v_total_debit, v_txn.amount);
    END IF;

    SELECT array_agg(DISTINCT l.account_id ORDER BY l.account_id)
    INTO v_account_ids
    FROM unnest(p_lines) AS l;

    FOREACH v_account_id IN ARRAY v_account_ids LOOP
        SELECT * INTO v_account
        FROM accounts
        WHERE id = v_account_id
        FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_ACCOUNT_NOT_FOUND',
                DETAIL = format('Account %s does not exist', v_account_id);
        END IF;

        IF v_account.status <> 'ACTIVE' THEN
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_ACCOUNT_NOT_ACTIVE',
                DETAIL = format('Account %s is %s', v_account_id, v_account.status);
        END IF;

        IF v_account.currency <> v_txn.currency THEN
            RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'LEDGER_CURRENCY_MISMATCH',
                DETAIL = format('Account %s is %s, transaction is %s',
                                v_account_id, v_account.currency, v_txn.currency);
        END IF;
    END LOOP;

    PERFORM set_config('wallet.ledger_posting', 'on', true);

    FOREACH v_line IN ARRAY p_lines LOOP
        UPDATE accounts
        SET balance    = balance + CASE WHEN v_line.entry_type = normal_balance
                                        THEN v_line.amount
                                        ELSE -v_line.amount END,
            updated_at = now()
        WHERE id = v_line.account_id
        RETURNING balance INTO v_balance_after;

        INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount, balance_after)
        VALUES (p_transaction_id, v_line.account_id, v_line.entry_type, v_line.amount, v_balance_after);
    END LOOP;

    PERFORM set_config('wallet.ledger_posting', 'off', true);

    UPDATE transactions
    SET status       = 'SUCCESSFUL',
        completed_at = now()
    WHERE id = p_transaction_id;
END;
$$;
