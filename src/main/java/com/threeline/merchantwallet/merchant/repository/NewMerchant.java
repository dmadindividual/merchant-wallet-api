package com.threeline.merchantwallet.merchant.repository;

import com.threeline.merchantwallet.merchant.domain.BusinessType;

public record NewMerchant(
        String businessName,
        BusinessType businessType,
        String rcNumber,
        String email,
        String phone,
        String address,
        String apiKeyHash,
        String webhookUrl
) {
}
