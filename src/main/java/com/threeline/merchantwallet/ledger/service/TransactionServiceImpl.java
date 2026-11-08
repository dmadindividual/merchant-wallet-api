package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.account.repository.AccountRepository;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;
import com.threeline.merchantwallet.dynamicaccount.repository.DynamicAccountRepository;
import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final DynamicAccountRepository dynamicAccounts;

    public TransactionServiceImpl(TransactionRepository transactions, AccountRepository accounts,
                                  DynamicAccountRepository dynamicAccounts) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.dynamicAccounts = dynamicAccounts;
    }

    @Override
    public TransactionView getForMerchant(long merchantId, String reference) {
        return transactions.findByReference(reference)
                .filter(t -> Objects.equals(t.getMerchantId(), merchantId))
                .map(this::describe)
                .orElseThrow(() -> new WalletException(ErrorCode.TRANSACTION_NOT_FOUND,
                        "No transaction " + reference + " found for this merchant"));
    }

    @Override
    public TransactionView describe(long transactionId) {
        return describe(transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transaction " + transactionId + " vanished")));
    }

    @Override
    public TransactionView describe(Transaction t) {
        String parentReference = t.getParentTransactionId() == null ? null
                : transactions.findById(t.getParentTransactionId()).map(Transaction::getReference).orElse(null);

        DynamicAccount via = t.getDynamicAccountId() == null ? null
                : dynamicAccounts.findById(t.getDynamicAccountId()).orElse(null);

        return new TransactionView(
                t,
                label(t.getSourceAccountId()),
                label(t.getDestinationAccountId()),
                parentReference,
                transactions.findChildren(t.getId()).stream().map(Transaction::getReference).toList(),
                via == null ? null : via.getAccountNumber(),
                via == null ? null : via.getReference());
    }

    private String label(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accounts.findById(accountId)
                .map(a -> a.getAccountNumber() != null ? a.getAccountNumber() : a.getSystemCode())
                .orElse(null);
    }
}
