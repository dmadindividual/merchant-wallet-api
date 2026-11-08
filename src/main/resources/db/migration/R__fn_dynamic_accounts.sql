-- =====================================================================
-- R__fn_dynamic_accounts.sql
-- Dynamic (one-time, time-limited) accounts.
--
--   fn_allocate_dynamic_account  lease a number from the pool to a merchant
--   fn_post_dynamic_inflow       route a payment made to a dynamic number
--   fn_expire_dynamic_accounts   housekeeping: expire leases, recycle numbers
--
-- Rules
--   * The lease's expires_at is the source of truth. A payment after it is
--     late even if the expiry job has not run yet.
--   * A number is live for at most one lease at a time
--     (uq_dynamic_accounts_one_active_per_number).
--   * A released number waits out a cooldown before it can be leased again,
--     so late payments and bank retries always hit the OLD lease.
-- =====================================================================


-- ---------------------------------------------------------------------
-- Leases a number to a merchant for p_ttl_seconds.
-- Returns NO_DYNAMIC_NUMBER_AVAILABLE when the pool has no usable number;
-- the caller then mints a new number into the pool and calls again.
-- SKIP LOCKED: concurrent allocations take different rows instead of
-- queueing on the same one.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_allocate_dynamic_account(
    p_merchant_id      BIGINT,
    p_account_name     TEXT,
    p_expected_amount  BIGINT,
    p_ttl_seconds      INT
)
RETURNS TABLE (out_dynamic_account_id BIGINT, out_error_code TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
v_number  CHAR(10);
    v_id      BIGINT;
BEGIN
    IF p_ttl_seconds IS NULL OR p_ttl_seconds <= 0 THEN
        RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'DYNAMIC_INVALID_TTL';
END IF;
    IF p_expected_amount IS NOT NULL AND p_expected_amount <= 0 THEN
        RETURN QUERY SELECT NULL::BIGINT, 'INVALID_AMOUNT'::TEXT;
RETURN;
END IF;

SELECT account_number INTO v_number
FROM dynamic_account_numbers
WHERE status = 'AVAILABLE' AND available_from <= now()
ORDER BY available_from, account_number
    LIMIT 1
    FOR UPDATE SKIP LOCKED;

IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::BIGINT, 'NO_DYNAMIC_NUMBER_AVAILABLE'::TEXT;
RETURN;
END IF;

UPDATE dynamic_account_numbers
SET status = 'LEASED', times_leased = times_leased + 1
WHERE account_number = v_number;

INSERT INTO dynamic_accounts (reference, merchant_id, account_number, account_name,
                              expected_amount, expires_at)
VALUES (fn_new_reference('DYN'), p_merchant_id, v_number, p_account_name,
        p_expected_amount, now() + make_interval(secs => p_ttl_seconds))
    RETURNING id INTO v_id;

RETURN QUERY SELECT v_id, NULL::TEXT;
END;
$$;


-- ---------------------------------------------------------------------
-- A payment arrived for a number that is not a permanent account.
-- Called by fn_post_inflow (after session dedupe), so it never needs to
-- check for duplicates itself.
--
--   lease ACTIVE and in time  -> credit merchant PRIMARY, lease -> PAID
--   expected amount differs   -> reject AMOUNT_MISMATCH (lease stays open)
--   lease late / already used -> credit SYS_SUSPENSE, linked to the lease
--   number never leased       -> reject DESTINATION_ACCOUNT_NOT_FOUND
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_post_dynamic_inflow(
    p_number       TEXT,
    p_amount       BIGINT,
    p_session_id   TEXT,
    p_sender_name  TEXT,
    p_narration    TEXT
)
RETURNS TABLE (out_transaction_id BIGINT, out_replayed BOOLEAN, out_error_code TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
v_lease          dynamic_accounts%ROWTYPE;
    v_primary        accounts%ROWTYPE;
    v_settlement_id  BIGINT;
    v_suspense_id    BIGINT;
    v_txn_id         BIGINT;
    v_sender         TEXT := COALESCE(NULLIF(p_sender_name, ''), 'external sender');
BEGIN
    -- The most recent lease of this number decides where the money belongs.
    -- Locked, so two payments racing for one lease cannot both pay it.
SELECT * INTO v_lease
FROM dynamic_accounts
WHERE account_number = p_number
ORDER BY created_at DESC, id DESC
    LIMIT 1
    FOR UPDATE;

IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::BIGINT, FALSE, 'DESTINATION_ACCOUNT_NOT_FOUND'::TEXT;
RETURN;
END IF;

SELECT id INTO v_settlement_id FROM accounts WHERE system_code = 'SYS_INBOUND_SETTLEMENT';

-- ---- In time: pay the merchant ---------------------------------------
IF v_lease.status = 'ACTIVE' AND now() < v_lease.expires_at THEN

        IF v_lease.expected_amount IS NOT NULL AND v_lease.expected_amount <> p_amount THEN
            RETURN QUERY SELECT NULL::BIGINT, FALSE, 'AMOUNT_MISMATCH'::TEXT;
RETURN;
END IF;

SELECT * INTO v_primary
FROM accounts
WHERE merchant_id = v_lease.merchant_id AND account_type = 'PRIMARY';

PERFORM fn_lock_accounts(ARRAY[v_settlement_id, v_primary.id]);
SELECT * INTO v_primary FROM accounts WHERE id = v_primary.id;

IF v_primary.status <> 'ACTIVE' THEN
            INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                                      source_account_id, destination_account_id, dynamic_account_id,
                                      external_reference, narration, failure_code, failure_reason, completed_at)
            VALUES (fn_new_reference('INF'), v_lease.merchant_id, 'INFLOW', 'FAILED', p_amount, v_primary.currency,
                    v_settlement_id, v_primary.id, v_lease.id,
                    p_session_id, COALESCE(NULLIF(p_narration, ''), 'Transfer from ' || v_sender),
                    'ACCOUNT_NOT_ACTIVE', format('Merchant primary account %s is %s',
                                                 v_primary.account_number, v_primary.status), now())
            RETURNING id INTO v_txn_id;

RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
RETURN;
END IF;

INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                          source_account_id, destination_account_id, dynamic_account_id,
                          external_reference, narration)
VALUES (fn_new_reference('INF'), v_lease.merchant_id, 'INFLOW', 'PENDING', p_amount, v_primary.currency,
        v_settlement_id, v_primary.id, v_lease.id,
        p_session_id, COALESCE(NULLIF(p_narration, ''), 'Transfer from ' || v_sender))
    RETURNING id INTO v_txn_id;

PERFORM fn_post_journal(v_txn_id, ARRAY[
            ROW(v_settlement_id, 'DEBIT',  p_amount)::ledger_line,
            ROW(v_primary.id,    'CREDIT', p_amount)::ledger_line
        ]);

UPDATE dynamic_accounts
SET status = 'PAID', paid_transaction_id = v_txn_id, updated_at = now()
WHERE id = v_lease.id;

RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
RETURN;
END IF;

    -- ---- Late, or the one-time account was already used: hold in suspense --
    IF v_lease.status = 'ACTIVE' THEN
        -- Past expires_at but the job has not run yet: time is the truth.
UPDATE dynamic_accounts SET status = 'EXPIRED', updated_at = now() WHERE id = v_lease.id;
END IF;

SELECT id INTO v_suspense_id FROM accounts WHERE system_code = 'SYS_SUSPENSE';
PERFORM fn_lock_accounts(ARRAY[v_settlement_id, v_suspense_id]);

INSERT INTO transactions (reference, merchant_id, transaction_type, status, amount, currency,
                          source_account_id, destination_account_id, dynamic_account_id,
                          external_reference, narration)
VALUES (fn_new_reference('INF'), v_lease.merchant_id, 'INFLOW', 'PENDING', p_amount, 'NGN',
        v_settlement_id, v_suspense_id, v_lease.id,
        p_session_id,
        format('Held in suspense: payment from %s to dynamic account %s (%s) after it was %s',
               v_sender, p_number, v_lease.reference,
               CASE WHEN v_lease.status = 'PAID' THEN 'already used' ELSE 'expired' END))
    RETURNING id INTO v_txn_id;

PERFORM fn_post_journal(v_txn_id, ARRAY[
        ROW(v_settlement_id, 'DEBIT',  p_amount)::ledger_line,
        ROW(v_suspense_id,   'CREDIT', p_amount)::ledger_line
    ]);

RETURN QUERY SELECT v_txn_id, FALSE, NULL::TEXT;
END;
$$;



CREATE OR REPLACE FUNCTION fn_expire_dynamic_accounts(p_cooldown_seconds INT, p_batch_size INT)
RETURNS TABLE (out_expired INT, out_released INT)
LANGUAGE plpgsql
AS $$
DECLARE
v_expired  INT;
    v_released INT;
BEGIN
UPDATE dynamic_accounts
SET status = 'EXPIRED', updated_at = now()
WHERE id IN (
    SELECT id FROM dynamic_accounts
    WHERE status = 'ACTIVE' AND expires_at <= now()
    ORDER BY expires_at
    LIMIT p_batch_size
    FOR UPDATE SKIP LOCKED
            );
GET DIAGNOSTICS v_expired = ROW_COUNT;

UPDATE dynamic_account_numbers n
SET status = 'AVAILABLE',
    available_from = now() + make_interval(secs => p_cooldown_seconds)
WHERE n.status = 'LEASED'
  AND NOT EXISTS (
    SELECT 1 FROM dynamic_accounts d
    WHERE d.account_number = n.account_number AND d.status = 'ACTIVE'
);
GET DIAGNOSTICS v_released = ROW_COUNT;

RETURN QUERY SELECT v_expired, v_released;
END;
$$;