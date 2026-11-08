package com.threeline.merchantwallet.merchant.api;

import com.threeline.merchantwallet.merchant.domain.BusinessType;
import com.threeline.merchantwallet.merchant.service.RegisterMerchantCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RegisterMerchantRequest(

        @Schema(example = "Northstar Logistics")
        @NotBlank @Size(max = 150)
        String businessName,

        @Schema(example = "SOLE_PROPRIETORSHIP")
        @NotNull
        BusinessType businessType,

        @Schema(example = "BN1234567")
        @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "must contain only letters and digits")
        String rcNumber,

        @Schema(example = "ops@northstar.example")
        @NotBlank @Email @Size(max = 150)
        String email,

        @Schema(example = "08062349175")
        @NotBlank
        @Pattern(regexp = "^(\\+?234|0)[789][01]\\d{8}$", message = "must be a Nigerian mobile number, e.g. 08031234567")
        String phone,

        @Schema(example = "12 Allen Avenue, Ikeja, Lagos")
        @Size(max = 255)
        String address,

        @Schema(example = "https://northstar.example/webhooks/wallet")
        @Size(max = 500)
        @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL")
        String webhookUrl
) {
    public RegisterMerchantCommand toCommand() {
        return new RegisterMerchantCommand(businessName, businessType, rcNumber, email, phone, address, webhookUrl);
    }
}
