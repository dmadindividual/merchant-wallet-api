package com.threeline.merchantwallet.account.api;

import com.threeline.merchantwallet.account.service.AccountService;
import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "List the merchant's accounts", description = "Primary first, then subsidiaries, with current balances.")
    @GetMapping
    public List<AccountResponse> list(@CurrentMerchant Merchant merchant) {
        return accountService.listForMerchant(merchant.getId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Operation(summary = "Get one account with its balance")
    @ApiResponse(responseCode = "404", description = "No such account for this merchant", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{accountNumber}")
    public AccountResponse get(@CurrentMerchant Merchant merchant,
                               @PathVariable
                               @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
                               String accountNumber) {
        return AccountResponse.from(accountService.getOwnedAccount(merchant.getId(), accountNumber));
    }

    @Operation(summary = "Account statement (transaction history)",
            description = "Newest first, with running balance and counterparty per line. "
                    + "Paginate by passing the previous page's nextCursor as `before`.")
    @GetMapping("/{accountNumber}/statement")
    public StatementResponse statement(@CurrentMerchant Merchant merchant,
                                       @PathVariable
                                       @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
                                       String accountNumber,
                                       @RequestParam(required = false) Long before,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return StatementResponse.of(accountNumber,
                accountService.statement(merchant.getId(), accountNumber, before, limit), limit);
    }

    @Operation(summary = "Open a subsidiary account",
            description = "Persistent account under the merchant with its own NUBAN. With autoSweep=true every credit "
                    + "moves straight to the primary account; otherwise it holds its own balance and can send transfers.")
    @ApiResponse(responseCode = "201", description = "Subsidiary created")
    @ApiResponse(responseCode = "400", description = "Invalid fields", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/subsidiaries")
    public ResponseEntity<AccountResponse> createSubsidiary(@CurrentMerchant Merchant merchant,
                                                            @Valid @RequestBody CreateSubsidiaryRequest request) {
        AccountResponse created = AccountResponse.from(accountService.createSubsidiary(merchant, request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + created.accountNumber()))
                .body(created);
    }

    @Operation(summary = "Turn auto-sweep on or off for a subsidiary",
            description = "Applies to future credits only; money already in the account stays there.")
    @ApiResponse(responseCode = "422", description = "Account is not a subsidiary (NOT_A_SUBSIDIARY)", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PatchMapping("/{accountNumber}/auto-sweep")
    public AccountResponse setAutoSweep(@CurrentMerchant Merchant merchant,
                                        @PathVariable
                                        @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
                                        String accountNumber,
                                        @Valid @RequestBody AutoSweepRequest request) {
        return AccountResponse.from(accountService.setAutoSweep(merchant.getId(), accountNumber, request.autoSweep()));
    }
}
