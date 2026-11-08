package com.threeline.merchantwallet.ledger.domain;

public enum EntrySide {
    DEBIT,
    CREDIT;

    public EntrySide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
