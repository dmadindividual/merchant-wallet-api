package com.threeline.merchantwallet.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InflowRequest(

        @Schema(example = "2857182589")
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be a 10-digit account number")
        String accountNumber,

        @Schema(example = "50000.00")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal amount,

        @Schema(example = "NIP-000123")
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "may contain letters, digits, '.', '_', ':' and '-'")
        String sessionId,

        @Schema(example = "Chidi Eze")
        @Size(max = 100)
        String senderName,

        @Schema(example = "Invoice 1001")
        @Size(max = 255)
        String narration
) {
}
