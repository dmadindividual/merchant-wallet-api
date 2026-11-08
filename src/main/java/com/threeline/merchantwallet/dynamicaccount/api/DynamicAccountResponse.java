package com.threeline.merchantwallet.dynamicaccount.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccount;
import com.threeline.merchantwallet.dynamicaccount.domain.DynamicAccountStatus;
import com.threeline.merchantwallet.dynamicaccount.service.DynamicAccountView;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DynamicAccountResponse(
        String reference,
        String accountNumber,
        String accountName,
        BigDecimal expectedAmount,
        DynamicAccountStatus status,
        Instant expiresAt,
        Long secondsRemaining,
        String paymentReference,
        Instant createdAt
) {
    public static DynamicAccountResponse from(DynamicAccountView view, Instant now) {
        DynamicAccount d = view.account();
        boolean live = d.isPayableAt(now);
        DynamicAccountStatus status = d.getStatus() == DynamicAccountStatus.ACTIVE && !live
                ? DynamicAccountStatus.EXPIRED
                : d.getStatus();

        return new DynamicAccountResponse(
                d.getReference(),
                d.getAccountNumber(),
                d.getAccountName(),
                d.getExpectedAmountKobo() == null ? null : Money.ofKobo(d.getExpectedAmountKobo()).toNaira(),
                status,
                d.getExpiresAt(),
                live ? Duration.between(now, d.getExpiresAt()).toSeconds() : null,
                view.paymentReference(),
                d.getCreatedAt());
    }
}
