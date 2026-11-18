package com.thordata.sdk;

import java.util.Map;

public record HttpResponseData(int statusCode, String body, Map<String, String> headers) {
}
