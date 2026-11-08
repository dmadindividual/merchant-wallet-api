package com.threeline.merchantwallet.common.error;

import java.util.Map;

public class WalletException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public WalletException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public WalletException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of());
    }

    public WalletException(ErrorCode errorCode, String detail, Map<String, Object> properties) {
        super(detail);
        this.errorCode = errorCode;
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
