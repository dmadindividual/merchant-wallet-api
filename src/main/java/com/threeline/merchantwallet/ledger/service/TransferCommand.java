package com.threeline.merchantwallet.ledger.service;

public record TransferCommand(
        long merchantId,
        String sourceAccountNumber,
        String destinationAccountNumber,
        long amountKobo,
        String narration,
        String idempotencyKey
) {

    public String fingerprint() {
        return String.join("|", sourceAccountNumber, destinationAccountNumber,
                Long.toString(amountKobo), narration == null ? "" : narration);
    }
}
