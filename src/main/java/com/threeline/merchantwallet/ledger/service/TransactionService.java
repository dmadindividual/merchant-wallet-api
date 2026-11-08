package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.ledger.domain.Transaction;

public interface TransactionService {

    TransactionView getForMerchant(long merchantId, String reference);

    TransactionView describe(Transaction transaction);

    TransactionView describe(long transactionId);
}
