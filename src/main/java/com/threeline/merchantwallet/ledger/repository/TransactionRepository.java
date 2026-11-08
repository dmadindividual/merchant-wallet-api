package com.threeline.merchantwallet.ledger.repository;

import com.threeline.merchantwallet.ledger.domain.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Optional<Transaction> findById(long id);

    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByMerchantAndIdempotencyKey(long merchantId, String idempotencyKey);

    List<Transaction> findChildren(long parentTransactionId);
}
