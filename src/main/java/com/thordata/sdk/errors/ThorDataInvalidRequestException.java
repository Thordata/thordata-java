package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class ThorDataInvalidRequestException extends ThorDataAPIError {
    public ThorDataInvalidRequestException(String message, Integer code, Integer statusCode, JsonNode data) {
        super(message, code, statusCode, data);
    }
}
