package com.threeline.merchantwallet.ledger.repository;

public interface LedgerFunctions {

    PostingOutcome transfer(long merchantId, String sourceAccountNumber, String destinationAccountNumber,
                            long amountKobo, String narration, String idempotencyKey, String requestHash);

    PostingOutcome inflow(String accountNumber, long amountKobo, String sessionId,
                          String senderName, String narration);
}
