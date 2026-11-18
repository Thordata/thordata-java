package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class ThorDataAuthenticationException extends ThorDataAPIError {
    public ThorDataAuthenticationException(String message, Integer code, Integer statusCode, JsonNode data) {
        super(message, code, statusCode, data);
    }
}
