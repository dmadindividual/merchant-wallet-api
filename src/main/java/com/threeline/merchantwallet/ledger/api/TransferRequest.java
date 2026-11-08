package com.threeline.merchantwallet.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest(

        @Schema(example = "2857182589")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
        String sourceAccountNumber,

        @Schema(example = "4479797259")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
        String destinationAccountNumber,

        @Schema(example = "2500.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "Delivery fees")
        @Size(max = 255)
        String narration
) {
}
