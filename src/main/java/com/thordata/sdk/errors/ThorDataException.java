package com.thordata.sdk.errors;

/** Base exception for all SDK failures. */
public class ThorDataException extends ThorDataError {
    public ThorDataException(String message) {
        super(message);
    }

    public ThorDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
