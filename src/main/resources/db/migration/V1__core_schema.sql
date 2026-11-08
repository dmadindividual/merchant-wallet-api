
CREATE SEQUENCE nuban_serial_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 999999999
    NO CYCLE;



CREATE TABLE merchants (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID          NOT NULL DEFAULT gen_random_uuid(),
    business_name   VARCHAR(150)  NOT NULL,
    business_type   VARCHAR(30)   NOT NULL,
    rc_number       VARCHAR(20),
    email           VARCHAR(150)  NOT NULL,
    phone           VARCHAR(20)   NOT NULL,
    address         VARCHAR(255),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    api_key_hash    CHAR(64)      NOT NULL,
    webhook_url     VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_merchants_public_id    UNIQUE (public_id),
    CONSTRAINT uq_merchants_api_key_hash UNIQUE (api_key_hash),
    CONSTRAINT chk_merchants_business_type CHECK (business_type IN (
        'SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'LIMITED_LIABILITY', 'NGO'
    )),
    CONSTRAINT chk_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX uq_merchants_email_lower ON merchants (lower(email));

CREATE UNIQUE INDEX uq_merchants_rc_number ON merchants (rc_number) WHERE rc_number IS NOT NULL;



CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    merchant_id     BIGINT        REFERENCES merchants (id),
    account_number  CHAR(10),
    system_code     VARCHAR(50),
    account_name    VARCHAR(150)  NOT NULL,
    account_type    VARCHAR(20)   NOT NULL,
    normal_balance  VARCHAR(6)    NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'NGN',
    balance         BIGINT        NOT NULL DEFAULT 0,
    auto_sweep      BOOLEAN       NOT NULL DEFAULT FALSE,

    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(150),
    phone           VARCHAR(20),

    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uq_accounts_system_code    UNIQUE (system_code),

    CONSTRAINT chk_accounts_type           CHECK (account_type IN ('PRIMARY', 'SUBSIDIARY', 'SYSTEM')),
    CONSTRAINT chk_accounts_normal_balance CHECK (normal_balance IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_accounts_status         CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_accounts_number_format  CHECK (account_number IS NULL OR account_number ~ '^[0-9]{10}$'),


    CONSTRAINT chk_accounts_shape CHECK (
        (account_type = 'SYSTEM'
            AND merchant_id IS NULL AND account_number IS NULL AND system_code IS NOT NULL)
        OR
        (account_type <> 'SYSTEM'
            AND merchant_id IS NOT NULL AND account_number IS NOT NULL AND system_code IS NULL)
    ),

    CONSTRAINT chk_accounts_no_overdraft CHECK (account_type = 'SYSTEM' OR balance >= 0),

    CONSTRAINT chk_accounts_auto_sweep CHECK (NOT auto_sweep OR account_type = 'SUBSIDIARY'),

    CONSTRAINT chk_accounts_subsidiary_details CHECK (
        account_type <> 'SUBSIDIARY'
        OR (first_name IS NOT NULL AND last_name IS NOT NULL AND email IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_accounts_one_primary_per_merchant
    ON accounts (merchant_id) WHERE account_type = 'PRIMARY';

CREATE INDEX idx_accounts_merchant_id ON accounts (merchant_id);



CREATE TABLE transactions (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference               VARCHAR(40)   NOT NULL,
    merchant_id             BIGINT        REFERENCES merchants (id),
    transaction_type        VARCHAR(20)   NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    amount                  BIGINT        NOT NULL,
    currency                CHAR(3)       NOT NULL DEFAULT 'NGN',
    source_account_id       BIGINT        REFERENCES accounts (id),
    destination_account_id  BIGINT        REFERENCES accounts (id),
    parent_transaction_id   BIGINT        REFERENCES transactions (id),
    idempotency_key         VARCHAR(100),
    request_hash            CHAR(64),
    external_reference      VARCHAR(100),
    narration               VARCHAR(255),
    failure_code            VARCHAR(50),
    failure_reason          VARCHAR(500),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,

    CONSTRAINT uq_transactions_reference UNIQUE (reference),
    CONSTRAINT chk_transactions_type   CHECK (transaction_type IN ('TRANSFER', 'INFLOW', 'SWEEP')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'SUCCESSFUL', 'FAILED')),
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_failure_details CHECK (status <> 'FAILED' OR failure_code IS NOT NULL),
    CONSTRAINT chk_transactions_sweep_has_parent CHECK (transaction_type <> 'SWEEP' OR parent_transaction_id IS NOT NULL),
    CONSTRAINT chk_transactions_not_self CHECK (source_account_id IS NULL OR source_account_id <> destination_account_id)
);

CREATE UNIQUE INDEX uq_transactions_merchant_idempotency
    ON transactions (merchant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Inflow dedupe: the same rail notification (session id) is processed once.
CREATE UNIQUE INDEX uq_transactions_inflow_external_ref
    ON transactions (external_reference) WHERE transaction_type = 'INFLOW' AND external_reference IS NOT NULL;

CREATE INDEX idx_transactions_merchant_created ON transactions (merchant_id, created_at DESC);
CREATE INDEX idx_transactions_parent           ON transactions (parent_transaction_id);



CREATE TABLE ledger_entries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id  BIGINT       NOT NULL REFERENCES transactions (id),
    account_id      BIGINT       NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(6)   NOT NULL,
    amount          BIGINT       NOT NULL,
    balance_after   BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_ledger_entries_type   CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id, id DESC);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

CREATE FUNCTION fn_reject_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION fn_reject_ledger_mutation();
