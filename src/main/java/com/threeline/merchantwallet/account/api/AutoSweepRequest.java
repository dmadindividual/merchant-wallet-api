package com.threeline.merchantwallet.account.api;

import jakarta.validation.constraints.NotNull;

public record AutoSweepRequest(@NotNull Boolean autoSweep) {
}
