package com.thordata.sdk.errors;

public class ThorDataConfigurationException extends ThorDataException {
    public ThorDataConfigurationException(String message) {
        super(message);
    }

    public ThorDataConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
