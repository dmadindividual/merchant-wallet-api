package com.threeline.merchantwallet.dynamicaccount.service;

import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;
import com.threeline.merchantwallet.dynamicaccount.repository.AllocationOutcome;
import com.threeline.merchantwallet.dynamicaccount.repository.DynamicAccountRepository;
import com.threeline.merchantwallet.dynamicaccount.repository.ExpiryOutcome;
import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.repository.TransactionRepository;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DynamicAccountServiceImpl implements DynamicAccountService {

    private final DynamicAccountRepository dynamicAccounts;
    private final TransactionRepository transactions;
    private final NubanGenerator nubans;
    private final AuditedTransactions tx;
    private final DynamicAccountProperties properties;

    public DynamicAccountServiceImpl(DynamicAccountRepository dynamicAccounts, TransactionRepository transactions,
                                     NubanGenerator nubans, AuditedTransactions tx,
                                     DynamicAccountProperties properties) {
        this.dynamicAccounts = dynamicAccounts;
        this.transactions = transactions;
        this.nubans = nubans;
        this.tx = tx;
        this.properties = properties;
    }

    @Override
    public DynamicAccountView create(Merchant merchant, CreateDynamicAccountCommand command) {
        String accountName = command.accountName() == null || command.accountName().isBlank()
                ? merchant.getBusinessName()
                : command.accountName().trim();
        int ttlSeconds = (int) properties.ttl().toSeconds();

        DynamicAccount created = tx.execute(() -> {
            AllocationOutcome outcome = dynamicAccounts.allocate(
                    merchant.getId(), accountName, command.expectedAmountKobo(), ttlSeconds);

            if (outcome.poolEmpty()) {

                dynamicAccounts.addNumberToPool(nubans.next());
                outcome = dynamicAccounts.allocate(merchant.getId(), accountName, command.expectedAmountKobo(), ttlSeconds);
            }

            if (outcome.errorCode() != null) {
                throw switch (outcome.errorCode()) {
                    case "INVALID_AMOUNT" -> new WalletException(ErrorCode.INVALID_AMOUNT);
                    case AllocationOutcome.NO_NUMBER_AVAILABLE -> new WalletException(ErrorCode.NO_DYNAMIC_NUMBER_AVAILABLE);
                    default -> new IllegalStateException("Unknown allocation error: " + outcome.errorCode());
                };
            }
            return dynamicAccounts.findById(outcome.dynamicAccountId()).orElseThrow();
        });

        log.info("Dynamic account {} ({}) issued to merchant {}, expires {}",
                created.getReference(), created.getAccountNumber(), merchant.getPublicId(), created.getExpiresAt());
        return new DynamicAccountView(created, null);
    }

    @Override
    public DynamicAccountView get(long merchantId, String reference) {
        return dynamicAccounts.findByReference(reference)
                .filter(d -> d.getMerchantId() == merchantId)
                .map(this::view)
                .orElseThrow(() -> new WalletException(ErrorCode.DYNAMIC_ACCOUNT_NOT_FOUND,
                        "No dynamic account " + reference + " found for this merchant"));
    }

    @Override
    public List<DynamicAccountView> listRecent(long merchantId, int limit) {
        return dynamicAccounts.findRecentByMerchant(merchantId, limit).stream().map(this::view).toList();
    }

    @Override
    public ExpiryOutcome expireDue() {
        ExpiryOutcome outcome = tx.execute(() -> dynamicAccounts.expireDue(
                (int) properties.cooldown().toSeconds(), properties.expiryBatchSize()));
        if (outcome.expired() > 0 || outcome.released() > 0) {
            log.info("Dynamic account expiry: {} expired, {} numbers returned to pool", outcome.expired(), outcome.released());
        }
        return outcome;
    }

    private DynamicAccountView view(DynamicAccount d) {
        String paymentReference = d.getPaidTransactionId() == null ? null
                : transactions.findById(d.getPaidTransactionId()).map(Transaction::getReference).orElse(null);
        return new DynamicAccountView(d, paymentReference);
    }
}
