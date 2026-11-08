package com.threeline.merchantwallet.account.service;

public record CreateSubsidiaryCommand(
        String accountName,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean autoSweep
) {
}
