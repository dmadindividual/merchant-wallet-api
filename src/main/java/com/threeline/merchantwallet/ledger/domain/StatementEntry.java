package com.threeline.merchantwallet.ledger.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class StatementEntry {

    private final long entryId;
    private final String transactionReference;
    private final TransactionType transactionType;
    private final EntrySide side;
    private final long amountKobo;
    private final long balanceAfterKobo;
    private final String narration;

    private final String counterparty;
    private final Instant createdAt;
}
