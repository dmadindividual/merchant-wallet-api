package com.threeline.merchantwallet.account.service;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.ledger.domain.StatementEntry;
import com.threeline.merchantwallet.merchant.domain.Merchant;

import java.util.List;

public interface AccountService {

    List<Account> listForMerchant(long merchantId);

    Account getOwnedAccount(long merchantId, String accountNumber);

    List<StatementEntry> statement(long merchantId, String accountNumber, Long beforeEntryId, int limit);

    Account createSubsidiary(Merchant merchant, CreateSubsidiaryCommand command);

    Account setAutoSweep(long merchantId, String accountNumber, boolean autoSweep);
}
