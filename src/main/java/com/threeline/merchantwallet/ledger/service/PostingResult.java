package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.ledger.domain.TransactionStatus;

public record PostingResult(TransactionView view, boolean replayed) {

    public boolean isFailed() {
        return view.transaction().getStatus() == TransactionStatus.FAILED;
    }
}
