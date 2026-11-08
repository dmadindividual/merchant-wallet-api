package com.threeline.merchantwallet.dynamicaccount.api;

import com.threeline.merchantwallet.common.money.Money;
import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import com.threeline.merchantwallet.dynamicaccount.service.CreateDynamicAccountCommand;
import com.threeline.merchantwallet.dynamicaccount.service.DynamicAccountService;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Tag(name = "Dynamic accounts")
@RestController
@RequestMapping("/api/v1/dynamic-accounts")
public class DynamicAccountController {

    private final DynamicAccountService service;

    public DynamicAccountController(DynamicAccountService service) {
        this.service = service;
    }

    @Operation(summary = "Issue a one-time dynamic account",
            description = "A NUBAN the merchant's customer can pay into for the next 10 minutes. A payment in time is "
                    + "credited to the merchant's primary account and the account becomes PAID. Late or repeat payments "
                    + "are held in suspense. Optional expectedAmount rejects any other amount.")
    @ApiResponse(responseCode = "201", description = "Dynamic account issued")
    @PostMapping
    public ResponseEntity<DynamicAccountResponse> create(@CurrentMerchant Merchant merchant,
                                                         @Valid @RequestBody(required = false) CreateDynamicAccountRequest request) {
        CreateDynamicAccountRequest body = request == null ? new CreateDynamicAccountRequest(null, null) : request;
        Long expectedKobo = body.expectedAmount() == null ? null : Money.ofNaira(body.expectedAmount()).kobo();

        DynamicAccountResponse created = DynamicAccountResponse.from(
                service.create(merchant, new CreateDynamicAccountCommand(body.accountName(), expectedKobo)),
                Instant.now());
        return ResponseEntity.created(URI.create("/api/v1/dynamic-accounts/" + created.reference())).body(created);
    }

    @Operation(summary = "Get a dynamic account's status",
            description = "ACTIVE (with secondsRemaining), PAID (with paymentReference) or EXPIRED. Poll this to know if the customer paid.")
    @ApiResponse(responseCode = "404", description = "No such dynamic account for this merchant", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{reference}")
    public DynamicAccountResponse get(@CurrentMerchant Merchant merchant, @PathVariable String reference) {
        return DynamicAccountResponse.from(service.get(merchant.getId(), reference), Instant.now());
    }

    @Operation(summary = "List recent dynamic accounts", description = "Newest first.")
    @GetMapping
    public List<DynamicAccountResponse> list(@CurrentMerchant Merchant merchant,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        Instant now = Instant.now();
        return service.listRecent(merchant.getId(), limit).stream()
                .map(v -> DynamicAccountResponse.from(v, now))
                .toList();
    }
}
