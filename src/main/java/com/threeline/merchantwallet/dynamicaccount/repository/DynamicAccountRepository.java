package com.threeline.merchantwallet.dynamicaccount.repository;

import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;

import java.util.List;
import java.util.Optional;

public interface DynamicAccountRepository {

    Optional<DynamicAccount> findById(long id);

    Optional<DynamicAccount> findByReference(String reference);

    List<DynamicAccount> findRecentByMerchant(long merchantId, int limit);

    void addNumberToPool(String accountNumber);

    AllocationOutcome allocate(long merchantId, String accountName, Long expectedAmountKobo, int ttlSeconds);

    ExpiryOutcome expireDue(int cooldownSeconds, int batchSize);
}
