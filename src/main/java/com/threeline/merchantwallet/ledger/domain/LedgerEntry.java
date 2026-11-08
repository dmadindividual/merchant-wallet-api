package com.threeline.merchantwallet.ledger.domain;

import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class LedgerEntry {

    @EqualsAndHashCode.Include
    private final long id;
    private final long transactionId;
    private final long accountId;
    private final EntrySide side;
    private final long amountKobo;
    private final long balanceAfterKobo;
    private final Instant createdAt;
}
