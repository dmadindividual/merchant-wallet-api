package com.threeline.merchantwallet.ledger.api;

import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import com.threeline.merchantwallet.ledger.service.TransactionService;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transactions")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Get a transaction by reference",
            description = "Transfers (TRF-), inflows (INF-) and sweeps (SWP-) belonging to the caller.")
    @ApiResponse(responseCode = "404", description = "No such transaction for this merchant", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{reference}")
    public TransactionResponse get(@CurrentMerchant Merchant merchant, @PathVariable String reference) {
        return TransactionResponse.from(transactionService.getForMerchant(merchant.getId(), reference));
    }
}
