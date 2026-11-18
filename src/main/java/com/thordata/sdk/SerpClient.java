package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.thordata.sdk.errors.ThorDataApiException;
import com.thordata.sdk.errors.ThorDataAuthenticationException;
import com.thordata.sdk.errors.ThorDataConnectionException;
import com.thordata.sdk.errors.ThorDataException;
import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import com.thordata.sdk.errors.ThorDataNotCollectedException;
import com.thordata.sdk.errors.ThorDataPermissionException;
import com.thordata.sdk.errors.ThorDataRateLimitException;
import com.thordata.sdk.errors.ThorDataTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** SERP transport and response parser. */
final class SerpClient {
    private final String apiKey;
    private final String apiUrl;
    private final Duration timeout;
    private final Map<String, String> customHeaders;
    private final HttpTransport transport;

    SerpClient(String apiKey, String apiUrl, Duration timeout, Map<String, String> headers,
               HttpTransport transport) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.timeout = timeout;
        this.customHeaders = Map.copyOf(headers);
        this.transport = transport;
    }

    SearchResponse searchResponse(Map<String, ?> params, String explicitEngine) {
        Map<String, String> payload = SerpRequestBuilder.build(params, explicitEngine);
        HttpResponseData response = send(payload);
        return parseResponse(response);
    }

    String rawSearch(Map<String, ?> params, String explicitEngine) {
        Map<String, String> payload = SerpRequestBuilder.build(params, explicitEngine);
        HttpResponseData response = send(payload);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            JsonNode errorBody = parseErrorBody(response.body());
            throw apiError(response.statusCode(), errorMessage(errorBody,
                            "ThorData SERP API returned HTTP " + response.statusCode()),
                    response.statusCode(), errorBody);
        }
        return response.body();
    }

    CompletableFuture<String> rawSearchAsync(Map<String, ?> params, String explicitEngine) {
        Map<String, String> payload = SerpRequestBuilder.build(params, explicitEngine);
        return transport.sendAsync(request(payload)).handle((response, failure) -> {
            if (failure != null) {
                throw new CompletionException(mapFailure(failure));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                JsonNode errorBody = parseErrorBody(response.body());
                throw new CompletionException(apiError(response.statusCode(), errorMessage(errorBody,
                                "ThorData SERP API returned HTTP " + response.statusCode()),
                        response.statusCode(), errorBody));
            }
            return response.body();
        });
    }

    CompletableFuture<SearchResponse> searchResponseAsync(Map<String, ?> params, String explicitEngine) {
        Map<String, String> payload = SerpRequestBuilder.build(params, explicitEngine);
        HttpRequestData request = request(payload);
        return transport.sendAsync(request)
                .handle((response, failure) -> {
                    if (failure != null) {
                        throw new CompletionException(mapFailure(failure));
                    }
                    return parseResponse(response);
                });
    }

    private HttpResponseData send(Map<String, String> payload) {
        try {
            return transport.send(request(payload));
        } catch (HttpTimeoutException exception) {
            throw new ThorDataTimeoutException("ThorData SERP request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ThorDataConnectionException("ThorData request was cancelled", exception);
        } catch (Exception exception) {
            throw mapFailure(exception);
        }
    }

    private HttpRequestData request(Map<String, String> payload) {
        Map<String, String> headers = new LinkedHashMap<>(customHeaders);
        headers.put("Accept", "application/json, text/html;q=0.9, */*;q=0.8");
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "thordata-java/" + ThorDataConstants.VERSION);
        headers.put("Origin", "Java");
        headers.put("platform", "java");
        headers.put("api-source", "sdk");
        return new HttpRequestData("POST", URI.create(apiUrl), headers, form(payload), timeout);
    }

    private SearchResponse parseResponse(HttpResponseData response) {
        JsonNode payload;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            JsonNode errorBody = parseErrorBody(response.body());
            throw apiError(response.statusCode(), errorMessage(errorBody,
                            "ThorData SERP API returned HTTP " + response.statusCode()),
                    response.statusCode(), errorBody);
        }
        try {
            payload = JsonSupport.parse(response.body());
        } catch (IllegalArgumentException exception) {
            throw new ThorDataApiException("ThorData SERP API returned invalid JSON", null,
                    response.statusCode(), null);
        }
        if (!payload.isObject()) {
            throw new ThorDataApiException("ThorData SERP API returned an unexpected response shape", null,
                    response.statusCode(), payload);
        }
        Integer code = integer(payload.get("code"));
        JsonNode data = JsonSupport.parseNested(payload.get("data"));
        if (code != null && code != 200) {
            String message = data != null && data.isTextual() ? data.textValue()
                    : text(payload.get("msg"), "ThorData rejected the SERP request");
            throw apiError(code, message, response.statusCode(), data);
        }
        if (code != null && code == 200 && data != null && data.isObject() && data.has("result")) {
            JsonNode result = JsonSupport.decodeJsonString(data.get("result"));
            String taskId = textOrNull(data.get("task_id"));
            if (taskId == null) {
                taskId = metadataTaskId(payload);
            }
            return new SearchResponse(result, taskId, payload);
        }
        JsonNode result = payload.has("data") ? data : payload;
        return new SearchResponse(result, metadataTaskId(payload), payload);
    }

    private ThorDataException mapFailure(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof ThorDataException exception) {
            return exception;
        }
        if (cause instanceof java.net.http.HttpTimeoutException) {
            return new ThorDataTimeoutException("ThorData SERP request timed out", cause);
        }
        return new ThorDataConnectionException("Unable to connect to the ThorData SERP API", cause);
    }

    private ThorDataApiException apiError(Integer code, String message, Integer statusCode, JsonNode data) {
        String safe = SecretRedactor.redactText(message, java.util.Set.of(apiKey));
        JsonNode safeData = data == null ? null : SecretRedactor.redact(data, java.util.List.of(apiKey));
        if (code != null && (code == 300 || (code == 0 && safe.toLowerCase().contains("collection failed")))) {
            return new ThorDataNotCollectedException(safe, code, statusCode, safeData);
        }
        if (code != null && code == 401) {
            return new ThorDataAuthenticationException(safe, code, statusCode, safeData);
        }
        if (code != null && code == 402) {
            return new ThorDataPermissionException(safe, code, statusCode, safeData);
        }
        if (code != null && code == 429) {
            return new ThorDataRateLimitException(safe, code, statusCode, safeData);
        }
        if (code != null && code == 400) {
            return new ThorDataInvalidRequestException(safe, code, statusCode, safeData);
        }
        return new ThorDataApiException(safe, code, statusCode, safeData);
    }

    private static String form(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(java.net.URLEncoder.encode(key, StandardCharsets.UTF_8));
            result.append('=').append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isBoolean()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.intValue();
        }
        if (node.isTextual() && node.textValue().trim().matches("\\d+")) {
            return Integer.valueOf(node.textValue().trim());
        }
        return null;
    }

    private static String text(JsonNode node, String fallback) {
        return node != null && !node.isNull() && node.isValueNode() ? node.asText() : fallback;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static String metadataTaskId(JsonNode payload) {
        JsonNode metadata = payload.get("search_metadata");
        return metadata == null ? null : textOrNull(metadata.get("id"));
    }

    private static JsonNode parseErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonSupport.parse(body);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String errorMessage(JsonNode body, String fallback) {
        if (body != null && body.isObject()) {
            JsonNode data = body.get("data");
            if (data != null && data.isTextual() && !data.textValue().isBlank()) {
                return data.textValue();
            }
            JsonNode message = body.get("msg");
            if (message != null && message.isTextual() && !message.textValue().isBlank()) {
                return message.textValue();
            }
        }
        return fallback;
    }
}
