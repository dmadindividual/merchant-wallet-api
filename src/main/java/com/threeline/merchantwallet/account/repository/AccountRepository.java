package com.threeline.merchantwallet.account.repository;

import com.threeline.merchantwallet.account.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account insert(NewAccount account);

    Optional<Account> findById(long id);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findPrimaryByMerchantId(long merchantId);

    List<Account> findByMerchantId(long merchantId);

    Optional<Account> findBySystemCode(String systemCode);

    Account updateAutoSweep(long accountId, boolean autoSweep);
}
