package com.threeline.merchantwallet.account.domain;

import com.threeline.merchantwallet.ledger.domain.EntrySide;
import lombok.*;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Account {

    @EqualsAndHashCode.Include
    private final long id;
    private final Long merchantId;
    private final String accountNumber;
    private final String systemCode;
    private final String accountName;
    private final AccountType type;
    private final EntrySide normalBalance;
    private final String currency;
    private final long balanceKobo;
    private final boolean autoSweep;

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;

    private final AccountStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isOwnedBy(long merchantId) {
        return this.merchantId != null && this.merchantId == merchantId;
    }

    public boolean hasSufficientFunds(long amountKobo) {
        return balanceKobo >= amountKobo;
    }
}
