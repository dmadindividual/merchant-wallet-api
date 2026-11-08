package com.threeline.merchantwallet.dynamicaccount.repository;

public record AllocationOutcome(Long dynamicAccountId, String errorCode) {

    public static final String NO_NUMBER_AVAILABLE = "NO_DYNAMIC_NUMBER_AVAILABLE";

    public boolean poolEmpty() {
        return NO_NUMBER_AVAILABLE.equals(errorCode);
    }
}
