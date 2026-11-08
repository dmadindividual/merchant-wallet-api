package com.threeline.merchantwallet.merchant.service;

import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.nuban.NubanGenerator;
import com.threeline.merchantwallet.account.repository.AccountRepository;
import com.threeline.merchantwallet.account.repository.NewAccount;
import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.common.security.ApiKeys;
import com.threeline.merchantwallet.common.text.PhoneNumbers;
import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.repository.MerchantRepository;
import com.threeline.merchantwallet.merchant.repository.NewMerchant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchants;
    private final AccountRepository accounts;
    private final NubanGenerator nubans;
    private final ApiKeys apiKeys;
    private final AuditedTransactions tx;

    public MerchantServiceImpl(MerchantRepository merchants, AccountRepository accounts,
                               NubanGenerator nubans, ApiKeys apiKeys, AuditedTransactions tx) {
        this.merchants = merchants;
        this.accounts = accounts;
        this.nubans = nubans;
        this.apiKeys = apiKeys;
        this.tx = tx;
    }

    @Override
    public RegisteredMerchant register(RegisterMerchantCommand command) {
        String email = command.email().trim().toLowerCase(Locale.ROOT);
        String rcNumber = blankToNull(command.rcNumber());

        if (command.businessType() == BusinessType.LIMITED_LIABILITY && rcNumber == null) {
            throw new WalletException(ErrorCode.VALIDATION_FAILED,
                    "rcNumber is required for LIMITED_LIABILITY businesses");
        }

        if (merchants.existsByEmail(email)) {
            throw new WalletException(ErrorCode.MERCHANT_ALREADY_EXISTS, "A merchant with this email already exists");
        }
        if (rcNumber != null && merchants.existsByRcNumber(rcNumber)) {
            throw new WalletException(ErrorCode.MERCHANT_ALREADY_EXISTS, "A merchant with this RC number already exists");
        }

        String rawApiKey = apiKeys.generate();

        RegisteredMerchant result = tx.execute(() -> {
            Merchant merchant = merchants.insert(new NewMerchant(
                    command.businessName().trim(),
                    command.businessType(),
                    rcNumber,
                    email,
                    PhoneNumbers.normalise(command.phone()),
                    blankToNull(command.address()),
                    apiKeys.hash(rawApiKey),
                    blankToNull(command.webhookUrl())));

            Account primary = accounts.insert(NewAccount.primary(
                    merchant.getId(), nubans.next(), merchant.getBusinessName()));

            return new RegisteredMerchant(merchant, primary, rawApiKey);
        });

        log.info("Registered merchant {} with primary account {}",
                result.merchant().getPublicId(), result.primaryAccount().getAccountNumber());
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
