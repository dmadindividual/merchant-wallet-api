package com.threeline.merchantwallet.simulation;

import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;
import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.ledger.api.TransactionResponse;
import com.threeline.merchantwallet.ledger.domain.Transaction;
import com.threeline.merchantwallet.ledger.service.InflowCommand;
import com.threeline.merchantwallet.ledger.service.InflowService;
import com.threeline.merchantwallet.ledger.service.PostingResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Simulation")
@RestController
@RequestMapping("/api/v1/simulate")
@ConditionalOnProperty(prefix = "wallet.simulation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimulationController {

    private final InflowService inflowService;

    public SimulationController(InflowService inflowService) {
        this.inflowService = inflowService;
    }

    @Operation(summary = "Simulate an incoming bank transfer",
            description = "Credits any account number: primary, subsidiary (auto-sweep applies) or dynamic account. "
                    + "The same sessionId is credited only once, like a NIP session id.")
    @ApiResponse(responseCode = "201", description = "Inflow recorded (or replayed)")
    @ApiResponse(responseCode = "404", description = "Unknown account number", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Dynamic account expects a different amount, or receiving account is frozen", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/inflows")
    public ResponseEntity<TransactionResponse> inflow(@Valid @RequestBody InflowRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "SIM-" + UUID.randomUUID()
                : request.sessionId();

        PostingResult result = inflowService.receive(new InflowCommand(
                request.accountNumber(),
                Money.ofNaira(request.amount()).kobo(),
                sessionId,
                request.senderName(),
                request.narration()));

        Transaction t = result.view().transaction();
        if (result.isFailed()) {
            throw new WalletException(ErrorCode.valueOf(t.getFailureCode()), t.getFailureReason(),
                    Map.of("transactionReference", t.getReference(), "replayed", result.replayed()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(TransactionResponse.from(result.view()));
    }
}
