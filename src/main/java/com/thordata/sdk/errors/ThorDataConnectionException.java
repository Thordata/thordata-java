package com.thordata.sdk.errors;

public class ThorDataConnectionException extends ThorDataException {
    public ThorDataConnectionException(String message) {
        super(message);
    }

    public ThorDataConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
