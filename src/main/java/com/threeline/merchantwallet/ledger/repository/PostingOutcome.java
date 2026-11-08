package com.threeline.merchantwallet.ledger.repository;

public record PostingOutcome(Long transactionId, boolean replayed, String errorCode) {

    public boolean isRejected() {
        return errorCode != null;
    }
}
