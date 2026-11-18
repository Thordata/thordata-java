package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

/** Naming-compatible alias for the API error type exposed by the sibling SDKs. */
public class ThorDataAPIError extends ThorDataApiException {
    public ThorDataAPIError(String message, Integer code, Integer statusCode, JsonNode data) {
        super(message, code, statusCode, data);
    }
}
