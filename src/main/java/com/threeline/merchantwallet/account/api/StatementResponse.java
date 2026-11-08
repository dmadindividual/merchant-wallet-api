package com.threeline.merchantwallet.account.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.ledger.domain.EntrySide;
import com.threeline.merchantwallet.ledger.domain.StatementEntry;
import com.threeline.merchantwallet.ledger.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StatementResponse(String accountNumber, List<Line> entries, Long nextCursor) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(
            Long cursor,
            String transactionReference,
            TransactionType transactionType,
            EntrySide direction,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String counterparty,
            String narration,
            Instant createdAt
    ) {
        static Line from(StatementEntry e) {
            return new Line(e.getEntryId(), e.getTransactionReference(), e.getTransactionType(), e.getSide(),
                    Money.ofKobo(e.getAmountKobo()).toNaira(), Money.ofKobo(e.getBalanceAfterKobo()).toNaira(),
                    e.getCounterparty(), e.getNarration(), e.getCreatedAt());
        }
    }

    public static StatementResponse of(String accountNumber, List<StatementEntry> entries, int limit) {
        Long next = entries.size() == limit ? entries.get(entries.size() - 1).getEntryId() : null;
        return new StatementResponse(accountNumber, entries.stream().map(Line::from).toList(), next);
    }
}
