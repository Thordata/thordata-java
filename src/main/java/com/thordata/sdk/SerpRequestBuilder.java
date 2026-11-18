package com.thordata.sdk;

import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts a Java parameter map into the canonical SERP form payload. */
public final class SerpRequestBuilder {
    private SerpRequestBuilder() {
    }

    public static Map<String, String> build(Map<String, ?> params) {
        return build(params, null);
    }

    public static Map<String, String> build(Map<String, ?> params, String explicitEngine) {
        if (params == null) {
            params = Map.of();
        }
        String requestedEngine = explicitEngine;
        if (requestedEngine == null) {
            Object fromParams = params.get("engine");
            requestedEngine = fromParams == null ? null : String.valueOf(fromParams);
        }
        if (requestedEngine == null || requestedEngine.isBlank()) {
            requestedEngine = "google_search";
        }
        requestedEngine = requestedEngine.trim().toLowerCase(Locale.ROOT);
        String normalizedEngine = normalizeEngine(requestedEngine);
        if (ThorDataConstants.WEB_SCRAPER_ENGINES.contains(normalizedEngine)) {
            throw invalid("Web Scraper engines are not available in the SERP SDK");
        }
        if (!ThorDataConstants.SUPPORTED_ENGINES.contains(requestedEngine)
                && !ThorDataConstants.SUPPORTED_ENGINES.contains(normalizedEngine)) {
            throw invalid("Unsupported SERP engine " + requestedEngine + "; supported engines: "
                    + String.join(", ", ThorDataConstants.SUPPORTED_ENGINES));
        }

        Map<String, String> payload = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            if ("engine".equals(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            String key = alias(entry.getKey());
            if (!ThorDataConstants.REQUEST_PARAMETERS.contains(key)) {
                continue;
            }
            String value = normalizeValue(entry.getValue());
            if (!value.isEmpty()) {
                payload.put(key, value);
            }
        }
        payload.put("engine", normalizedEngine);
        if (normalizedEngine.equals("yandex")) {
            if (!payload.containsKey("text") && payload.containsKey("q")) {
                payload.put("text", payload.get("q"));
            }
            payload.remove("q");
        }
        payload.putIfAbsent("json", "1");
        payload.putIfAbsent("isjson", "1");
        return Map.copyOf(payload);
    }

    static String normalizeEngine(String engine) {
        return switch (engine) {
            case "google_search" -> "google";
            case "google_places" -> "google_local";
            default -> engine;
        };
    }

    static String alias(String key) {
        return switch (key) {
            case "query" -> "q";
            case "country" -> "gl";
            case "language" -> "hl";
            default -> key;
        };
    }

    static String normalizeValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        if (value instanceof Set<?>) {
            throw new ThorDataInvalidRequestException(
                    "SERP parameters must contain JSON-serializable values", 400, null, null);
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
            return JsonSupport.stringify(value);
        }
        return String.valueOf(value).trim();
    }

    private static ThorDataInvalidRequestException invalid(String message) {
        return new ThorDataInvalidRequestException(message, 400, null, null);
    }
}
