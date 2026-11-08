package com.threeline.merchantwallet.ledger.domain;

public enum TransactionStatus {
    PENDING,
    SUCCESSFUL,
    FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
