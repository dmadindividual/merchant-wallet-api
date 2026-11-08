package com.threeline.merchantwallet.ledger.service;

public record InflowCommand(
        String accountNumber,
        long amountKobo,
        String sessionId,
        String senderName,
        String narration
) {
}
