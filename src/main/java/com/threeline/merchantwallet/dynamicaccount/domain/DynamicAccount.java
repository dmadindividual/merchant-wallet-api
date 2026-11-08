package com.threeline.merchantwallet.dynamicaccount.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class DynamicAccount {

    @EqualsAndHashCode.Include
    private final long id;
    private final String reference;
    private final long merchantId;
    private final String accountNumber;
    private final String accountName;
    private final Long expectedAmountKobo;
    private final DynamicAccountStatus status;
    private final Instant expiresAt;
    private final Long paidTransactionId;
    private final Instant createdAt;
    private final Instant updatedAt;

    public boolean isPayableAt(Instant now) {
        return status == DynamicAccountStatus.ACTIVE && now.isBefore(expiresAt);
    }
}
