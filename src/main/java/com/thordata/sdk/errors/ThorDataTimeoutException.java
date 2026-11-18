package com.thordata.sdk.errors;

public class ThorDataTimeoutException extends ThorDataConnectionException {
    public ThorDataTimeoutException(String message) {
        super(message);
    }

    public ThorDataTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
