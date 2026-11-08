package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.common.crypto.Hashing;
import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.ledger.repository.LedgerFunctions;
import com.threeline.merchantwallet.ledger.repository.PostingOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TransferServiceImpl implements TransferService {

    private final LedgerFunctions ledger;
    private final TransactionService transactionService;
    private final AuditedTransactions tx;

    public TransferServiceImpl(LedgerFunctions ledger, TransactionService transactionService, AuditedTransactions tx) {
        this.ledger = ledger;
        this.transactionService = transactionService;
        this.tx = tx;
    }

    @Override
    public PostingResult transfer(TransferCommand c) {
        String requestHash = Hashing.sha256Hex(c.fingerprint());

        PostingOutcome outcome = tx.execute(() -> ledger.transfer(
                c.merchantId(), c.sourceAccountNumber(), c.destinationAccountNumber(),
                c.amountKobo(), c.narration(), c.idempotencyKey(), requestHash));

        if (outcome.isRejected()) {
            throw PostingErrors.toException(outcome.errorCode(), c.sourceAccountNumber(), c.destinationAccountNumber());
        }

        PostingResult result = new PostingResult(transactionService.describe(outcome.transactionId()), outcome.replayed());
        log.info("Transfer {} {} (replayed={})", result.view().transaction().getReference(),
                result.view().transaction().getStatus(), result.replayed());
        return result;
    }
}
