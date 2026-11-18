package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class ThorDataRateLimitException extends ThorDataAPIError {
    public ThorDataRateLimitException(String message, Integer code, Integer statusCode, JsonNode data) {
        super(message, code, statusCode, data);
    }
}
