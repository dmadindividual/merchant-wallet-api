package com.threeline.merchantwallet.ledger.api;

import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.service.PostingResult;
import com.threeline.merchantwallet.ledger.service.TransferCommand;
import com.threeline.merchantwallet.ledger.service.TransferService;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Transfers")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "Transfer funds between accounts",
            description = "Debits one of the caller's accounts and credits any account in the system. "
                    + "If the destination is an auto-sweep subsidiary, the money is swept to its primary account. "
                    + "Retrying with the same Idempotency-Key returns the original result (header Idempotent-Replayed: true).")
    @ApiResponse(responseCode = "201", description = "Transfer SUCCESSFUL (or replayed)")
    @ApiResponse(responseCode = "400", description = "Invalid body or missing/invalid Idempotency-Key", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Source account not owned by caller, or destination unknown", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Idempotency-Key reused with a different request", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Transfer FAILED and recorded (e.g. INSUFFICIENT_FUNDS); body includes transactionReference", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @CurrentMerchant Merchant merchant,
            @Parameter(description = "Unique per transfer; reuse it only to retry the same request.", example = "order-1001-payout")
            @RequestHeader(IDEMPOTENCY_HEADER)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{8,100}$",
                    message = "must be 8-100 characters: letters, digits, '.', '_', ':' or '-'")
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        long amountKobo = Money.ofNaira(request.amount()).kobo();

        PostingResult result = transferService.transfer(new TransferCommand(
                merchant.getId(),
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                amountKobo,
                request.narration() == null || request.narration().isBlank() ? null : request.narration().trim(),
                idempotencyKey));

        Transaction t = result.view().transaction();
        if (result.isFailed()) {
            throw new WalletException(ErrorCode.valueOf(t.getFailureCode()), t.getFailureReason(),
                    Map.of("transactionReference", t.getReference(), "replayed", result.replayed()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(TransactionResponse.from(result.view()));
    }
}
