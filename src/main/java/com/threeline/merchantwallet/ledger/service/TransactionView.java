package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.ledger.domain.Transaction;

import java.util.List;

public record TransactionView(
        Transaction transaction,
        String sourceAccount,
        String destinationAccount,
        String parentReference,
        List<String> childReferences,
        String viaDynamicAccount,
        String dynamicAccountReference
) {
}
