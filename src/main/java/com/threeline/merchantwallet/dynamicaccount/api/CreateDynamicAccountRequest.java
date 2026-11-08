package com.threeline.merchantwallet.dynamicaccount.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDynamicAccountRequest(

        @Schema(example = "Northstar - Order 1001")
        @Size(max = 150)
        String accountName,

        @Schema(example = "7500.00")
        @DecimalMin(value = "0.01", message = "must be at least 0.01")
        @Digits(integer = 13, fraction = 2, message = "must have at most 2 decimal places")
        BigDecimal expectedAmount
) {
}
