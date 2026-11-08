
CREATE TYPE ledger_line AS (
    account_id  BIGINT,
    entry_type  VARCHAR(6),
    amount      BIGINT
);
