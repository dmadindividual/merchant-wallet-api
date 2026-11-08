package com.threeline.merchantwallet.account.api;

import com.threeline.merchantwallet.account.service.CreateSubsidiaryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSubsidiaryRequest(

        @Schema(example = "Northstar - Ikeja Depot")
        @Size(max = 150)
        String accountName,

        @Schema(example = "Ngozi")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Okafor")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(example = "ikeja@northstar.example")
        @NotBlank @Email @Size(max = 150)
        String email,

        @Schema(example = "08051234567")
        @Pattern(regexp = "^(\\+?234|0)[789][01]\\d{8}$", message = "must be a Nigerian mobile number, e.g. 08031234567")
        String phone,

        @Schema(example = "false")
        Boolean autoSweep
) {
    public CreateSubsidiaryCommand toCommand() {
        return new CreateSubsidiaryCommand(accountName, firstName, lastName, email, phone,
                Boolean.TRUE.equals(autoSweep));
    }
}
