package com.thordata.sdk.errors;

/** Naming-compatible base alias for the Python and JavaScript SDK error type. */
public class ThorDataError extends RuntimeException {
    public ThorDataError(String message) {
        super(message);
    }

    public ThorDataError(String message, Throwable cause) {
        super(message, cause);
    }
}
