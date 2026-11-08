
CREATE TABLE dynamic_account_numbers (
    account_number  CHAR(10)     PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    available_from  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    times_leased    INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_dyn_numbers_status CHECK (status IN ('AVAILABLE', 'LEASED')),
    CONSTRAINT chk_dyn_numbers_format CHECK (account_number ~ '^[0-9]{10}$')
);

CREATE INDEX idx_dyn_numbers_allocatable
    ON dynamic_account_numbers (available_from)
    WHERE status = 'AVAILABLE';



CREATE TABLE dynamic_accounts (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference            VARCHAR(40)   NOT NULL,
    merchant_id          BIGINT        NOT NULL REFERENCES merchants (id),
    account_number       CHAR(10)      NOT NULL REFERENCES dynamic_account_numbers (account_number),
    account_name         VARCHAR(150)  NOT NULL,
    expected_amount      BIGINT,       -- kobo; NULL = accept any amount
    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    expires_at           TIMESTAMPTZ   NOT NULL,
    paid_transaction_id  BIGINT        REFERENCES transactions (id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_dynamic_accounts_reference UNIQUE (reference),
    CONSTRAINT chk_dynamic_accounts_status CHECK (status IN ('ACTIVE', 'PAID', 'EXPIRED')),
    CONSTRAINT chk_dynamic_accounts_expected_amount CHECK (expected_amount IS NULL OR expected_amount > 0),
    CONSTRAINT chk_dynamic_accounts_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_dynamic_accounts_paid_has_txn CHECK (status <> 'PAID' OR paid_transaction_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_dynamic_accounts_one_active_per_number
    ON dynamic_accounts (account_number)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_dynamic_accounts_active_expiry
    ON dynamic_accounts (expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_dynamic_accounts_merchant ON dynamic_accounts (merchant_id, created_at DESC);



ALTER TABLE transactions
    ADD COLUMN dynamic_account_id BIGINT REFERENCES dynamic_accounts (id);

CREATE INDEX idx_transactions_dynamic_account
    ON transactions (dynamic_account_id)
    WHERE dynamic_account_id IS NOT NULL;
