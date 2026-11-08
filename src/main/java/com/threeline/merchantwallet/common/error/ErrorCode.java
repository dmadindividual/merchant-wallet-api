package com.threeline.merchantwallet.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request is invalid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body could not be read"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "No endpoint matches this path"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This HTTP method is not supported on this path"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type must be application/json"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Amount must be greater than zero with at most 2 decimal places"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A valid API key is required"),
    FORBIDDEN_ACCOUNT_ACCESS(HttpStatus.FORBIDDEN, "This account does not belong to the calling merchant"),

    MERCHANT_NOT_FOUND(HttpStatus.NOT_FOUND, "Merchant not found"),
    MERCHANT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A merchant with this email or RC number already exists"),
    MERCHANT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "Merchant is not active"),

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Account is not active"),
    NOT_A_SUBSIDIARY(HttpStatus.UNPROCESSABLE_ENTITY, "This operation is only allowed on subsidiary accounts"),
    ACCOUNT_NUMBER_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "No more account numbers can be issued"),

    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds in source account"),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Source and destination accounts must be different"),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Accounts must be in the same currency"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was already used with a different request"),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "A request with this idempotency key is already being processed"),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),

    DYNAMIC_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Dynamic account not found"),
    DYNAMIC_ACCOUNT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Dynamic account has expired"),
    DYNAMIC_ACCOUNT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Amount does not match the amount expected by this dynamic account"),
    NO_DYNAMIC_NUMBER_AVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "No dynamic account number is available right now"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
