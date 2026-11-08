package com.threeline.merchantwallet.account.nuban;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wallet.nuban")
public record NubanProperties(
        @NotBlank
        @Pattern(regexp = "\\d{3}", message = "bank-code must be exactly 3 digits")
        String bankCode
) {
}
