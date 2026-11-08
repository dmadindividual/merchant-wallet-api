package com.threeline.merchantwallet.ledger.service;

import com.threeline.merchantwallet.common.error.ErrorCode;
import com.threeline.merchantwallet.common.error.WalletException;

final class PostingErrors {

    private PostingErrors() {
    }

    static WalletException toException(String code, String sourceAccount, String destinationAccount) {
        return switch (code) {
            case "INVALID_AMOUNT" -> new WalletException(ErrorCode.INVALID_AMOUNT);
            case "IDEMPOTENCY_KEY_REUSED" -> new WalletException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            case "DUPLICATE_SESSION_MISMATCH" -> new WalletException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This session id was already used for a different account or amount");
            case "SOURCE_ACCOUNT_NOT_FOUND" -> new WalletException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Source account " + sourceAccount + " not found for this merchant");
            case "DESTINATION_ACCOUNT_NOT_FOUND" -> new WalletException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Destination account " + destinationAccount + " not found");
            case "SAME_ACCOUNT_TRANSFER" -> new WalletException(ErrorCode.SAME_ACCOUNT_TRANSFER);
            case "AMOUNT_MISMATCH" -> new WalletException(ErrorCode.DYNAMIC_ACCOUNT_AMOUNT_MISMATCH,
                    "Dynamic account " + destinationAccount + " expects a different amount; the payment was not accepted");
            default -> throw new IllegalStateException("Unknown posting error code from database: " + code);
        };
    }
}
