package com.threeline.merchantwallet.merchant.service;

import com.threeline.merchantwallet.merchant.domain.BusinessType;

public record RegisterMerchantCommand(
        String businessName,
        BusinessType businessType,
        String rcNumber,
        String email,
        String phone,
        String address,
        String webhookUrl
) {
}
