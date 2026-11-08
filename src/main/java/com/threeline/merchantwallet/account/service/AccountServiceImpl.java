package com.threeline.merchantwallet.account.service;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.domain.AccountType;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.account.repository.AccountRepository;
import com.threeline.merchantwallet.account.repository.NewAccount;
import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.common.text.PhoneNumbers;
import com.threeline.merchantwallet.ledger.domain.StatementEntry;
import com.threeline.merchantwallet.ledger.repository.LedgerEntryRepository;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledgerEntries;
    private final NubanGenerator nubans;
    private final AuditedTransactions tx;

    public AccountServiceImpl(AccountRepository accounts, LedgerEntryRepository ledgerEntries,
                              NubanGenerator nubans, AuditedTransactions tx) {
        this.accounts = accounts;
        this.ledgerEntries = ledgerEntries;
        this.nubans = nubans;
        this.tx = tx;
    }

    @Override
    public List<Account> listForMerchant(long merchantId) {
        return accounts.findByMerchantId(merchantId);
    }

    @Override
    public Account getOwnedAccount(long merchantId, String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .filter(account -> account.isOwnedBy(merchantId))
                .orElseThrow(() -> new WalletException(ErrorCode.ACCOUNT_NOT_FOUND,
                        "No account " + accountNumber + " found for this merchant"));
    }

    @Override
    public List<StatementEntry> statement(long merchantId, String accountNumber, Long beforeEntryId, int limit) {
        Account account = getOwnedAccount(merchantId, accountNumber);
        return ledgerEntries.findStatement(account.getId(), beforeEntryId, limit);
    }

    @Override
    public Account createSubsidiary(Merchant merchant, CreateSubsidiaryCommand c) {
        String firstName = c.firstName().trim();
        String lastName = c.lastName().trim();
        String accountName = c.accountName() == null || c.accountName().isBlank()
                ? merchant.getBusinessName() + " - " + firstName + " " + lastName
                : c.accountName().trim();

        return tx.execute(() -> accounts.insert(NewAccount.subsidiary(
                merchant.getId(),
                nubans.next(),
                accountName,
                firstName,
                lastName,
                c.email().trim().toLowerCase(Locale.ROOT),
                PhoneNumbers.normalise(c.phone()),
                c.autoSweep())));
    }

    @Override
    public Account setAutoSweep(long merchantId, String accountNumber, boolean autoSweep) {
        Account account = getOwnedAccount(merchantId, accountNumber);
        if (account.getType() != AccountType.SUBSIDIARY) {
            throw new WalletException(ErrorCode.NOT_A_SUBSIDIARY,
                    "Auto-sweep can only be set on subsidiary accounts; " + accountNumber + " is " + account.getType());
        }
        if (account.isAutoSweep() == autoSweep) {
            return account;
        }
        return tx.execute(() -> accounts.updateAutoSweep(account.getId(), autoSweep));
    }
}
