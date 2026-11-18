package com.thordata.sdk.errors;

import com.fasterxml.jackson.databind.JsonNode;

public class ThorDataApiException extends ThorDataException {
    private final Integer code;
    private final Integer statusCode;
    private final JsonNode responseData;

    public ThorDataApiException(String message, Integer code, Integer statusCode, JsonNode responseData) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.responseData = responseData;
    }

    public ThorDataApiException(String message, Integer code, Integer statusCode,
                                JsonNode responseData, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.statusCode = statusCode;
        this.responseData = responseData;
    }

    public Integer code() {
        return code;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public JsonNode responseData() {
        return responseData;
    }
}
