package com.thordata.sdk;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record HttpRequestData(String method, URI uri, Map<String, String> headers, String body,
                              Duration timeout) {
}
