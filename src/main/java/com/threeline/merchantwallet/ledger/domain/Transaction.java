package com.threeline.merchantwallet.ledger.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Transaction {

    @EqualsAndHashCode.Include
    private final long id;
    private final String reference;
    private final Long merchantId;
    private final TransactionType type;
    private final TransactionStatus status;
    private final long amountKobo;
    private final String currency;
    private final Long sourceAccountId;
    private final Long destinationAccountId;
    private final Long parentTransactionId;
    private final Long dynamicAccountId;
    private final String idempotencyKey;
    private final String requestHash;
    private final String externalReference;
    private final String narration;
    private final String failureCode;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant completedAt;

    public boolean isSuccessful() {
        return status == TransactionStatus.SUCCESSFUL;
    }
}
