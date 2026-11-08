package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.common.db.AuditedTransactions;
import com.threeline.merchantwallet.ledger.repository.LedgerFunctions;
import com.threeline.merchantwallet.ledger.repository.PostingOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InflowServiceImpl implements InflowService {

    private final LedgerFunctions ledger;
    private final TransactionService transactionService;
    private final AuditedTransactions tx;

    public InflowServiceImpl(LedgerFunctions ledger, TransactionService transactionService, AuditedTransactions tx) {
        this.ledger = ledger;
        this.transactionService = transactionService;
        this.tx = tx;
    }

    @Override
    public PostingResult receive(InflowCommand c) {
        PostingOutcome outcome = tx.execute(() -> ledger.inflow(
                c.accountNumber(), c.amountKobo(), c.sessionId(), c.senderName(), c.narration()));

        if (outcome.isRejected()) {
            throw PostingErrors.toException(outcome.errorCode(), null, c.accountNumber());
        }

        PostingResult result = new PostingResult(transactionService.describe(outcome.transactionId()), outcome.replayed());
        log.info("Inflow {} {} (replayed={})", result.view().transaction().getReference(),
                result.view().transaction().getStatus(), result.replayed());
        return result;
    }
}
