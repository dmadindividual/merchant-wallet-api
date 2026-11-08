package com.threeline.merchantwallet.merchant.api;

import com.threeline.merchantwallet.common.openapi.ProblemResponses;
import com.threeline.merchantwallet.common.security.CurrentMerchant;
import com.threeline.merchantwallet.merchant.domain.Merchant;
import com.threeline.merchantwallet.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Merchants")
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "Register a merchant",
            description = "Creates the business and opens its PRIMARY account with a unique NUBAN, atomically. "
                    + "Returns the API key once. No API key needed.")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Merchant and primary account created")
    @ApiResponse(responseCode = "400", description = "Invalid fields", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Email or RC number already registered", content = @Content(mediaType = ProblemResponses.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<MerchantRegistrationResponse> register(@Valid @RequestBody RegisterMerchantRequest request) {
        var registered = merchantService.register(request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/merchants/me"))
                .body(MerchantRegistrationResponse.from(registered));
    }

    @Operation(summary = "Get the calling merchant's profile")
    @GetMapping("/me")
    public MerchantResponse me(@CurrentMerchant Merchant merchant) {
        return MerchantResponse.from(merchant);
    }
}
