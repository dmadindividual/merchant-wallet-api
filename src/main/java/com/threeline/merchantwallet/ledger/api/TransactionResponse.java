package com.threeline.merchantwallet.ledger.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.domain.TransactionStatus;
import com.threeline.merchantwallet.ledger.domain.TransactionType;
import com.threeline.merchantwallet.ledger.service.TransactionView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TransactionResponse(
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String sourceAccount,
        String destinationAccount,
        String narration,
        String failureCode,
        String failureReason,
        String parentReference,
        List<String> sweepReferences,
        String viaDynamicAccount,
        String dynamicAccountReference,
        Instant createdAt,
        Instant completedAt
) {
    public static TransactionResponse from(TransactionView v) {
        Transaction t = v.transaction();
        return new TransactionResponse(
                t.getReference(), t.getType(), t.getStatus(),
                Money.ofKobo(t.getAmountKobo()).toNaira(), t.getCurrency(),
                v.sourceAccount(), v.destinationAccount(), t.getNarration(),
                t.getFailureCode(), t.getFailureReason(),
                v.parentReference(), v.childReferences(),
                v.viaDynamicAccount(), v.dynamicAccountReference(),
                t.getCreatedAt(), t.getCompletedAt());
    }
}
