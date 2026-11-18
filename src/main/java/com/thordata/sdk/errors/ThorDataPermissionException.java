package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class ThorDataPermissionException extends ThorDataAPIError {
    public ThorDataPermissionException(String message, Integer code, Integer statusCode, JsonNode data) {
        super(message, code, statusCode, data);
    }
}
