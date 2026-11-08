package com.threeline.merchantwallet.merchant.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.domain.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MerchantResponse(
        UUID merchantId,
        String businessName,
        BusinessType businessType,
        String rcNumber,
        String email,
        String phone,
        String address,
        String webhookUrl,
        MerchantStatus status,
        Instant createdAt
) {
    public static MerchantResponse from(Merchant m) {
        return new MerchantResponse(m.getPublicId(), m.getBusinessName(), m.getBusinessType(), m.getRcNumber(),
                m.getEmail(), m.getPhone(), m.getAddress(), m.getWebhookUrl(), m.getStatus(), m.getCreatedAt());
    }
}
