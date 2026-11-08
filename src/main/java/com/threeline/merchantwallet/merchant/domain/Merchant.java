package com.threeline.merchantwallet.merchant.domain;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Merchant {

    @EqualsAndHashCode.Include
    private final long id;
    private final UUID publicId;
    private final String businessName;
    private final BusinessType businessType;
    private final String rcNumber;
    private final String email;
    private final String phone;
    private final String address;
    private final MerchantStatus status;

    @ToString.Exclude
    private final String apiKeyHash;

    private final String webhookUrl;
    private final Instant createdAt;
    private final Instant updatedAt;

    public boolean isActive() {
        return status == MerchantStatus.ACTIVE;
    }
}
