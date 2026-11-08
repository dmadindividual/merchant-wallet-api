package com.threeline.merchantwallet.account.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.threeline.merchantwallet.account.domain.Account;
import com.threeline.merchantwallet.account.domain.AccountStatus;
import com.threeline.merchantwallet.account.domain.AccountType;
import com.threeline.merchantwallet.common.money.Money;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        String accountNumber,
        String accountName,
        AccountType type,
        BigDecimal balance,
        String currency,
        boolean autoSweep,
        String firstName,
        String lastName,
        String email,
        String phone,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getAccountNumber(),
                a.getAccountName(),
                a.getType(),
                Money.ofKobo(a.getBalanceKobo()).toNaira(),
                a.getCurrency(),
                a.isAutoSweep(),
                a.getFirstName(),
                a.getLastName(),
                a.getEmail(),
                a.getPhone(),
                a.getStatus(),
                a.getCreatedAt()
        );
    }
}
