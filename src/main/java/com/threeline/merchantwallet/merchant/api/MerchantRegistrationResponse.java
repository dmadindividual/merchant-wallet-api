package com.threeline.merchantwallet.merchant.api;

import com.threeline.merchantwallet.account.api.AccountResponse;
import com.threeline.merchantwallet.merchant.service.RegisteredMerchant;

public record MerchantRegistrationResponse(
        MerchantResponse merchant,
        AccountResponse primaryAccount,
        String apiKey,
        String apiKeyNotice
) {
    public static MerchantRegistrationResponse from(RegisteredMerchant r) {
        return new MerchantRegistrationResponse(
                MerchantResponse.from(r.merchant()),
                AccountResponse.from(r.primaryAccount()),
                r.apiKey(),
                "Store this API key securely. It is shown only once and cannot be recovered.");
    }
}
