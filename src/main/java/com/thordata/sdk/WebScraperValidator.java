package com.thordata.sdk;

import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates and normalizes one Web Scraper parameter object. */
public final class WebScraperValidator {
    private WebScraperValidator() {
    }

    public static Map<String, String> validate(String spiderId, Map<String, ?> params) {
        WebScraperDefinition definition = WebScraperDefinition.ALL.get(spiderId);
        if (definition == null) {
            throw invalid("Unsupported Web Scraper spider " + spiderId);
        }
        if (params == null) {
            throw invalid("Web Scraper params must be an object");
        }
        for (String key : params.keySet()) {
            if (!definition.fields().containsKey(key)) {
                throw invalid("Unknown Web Scraper parameters: " + key);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, WebScraperDefinition.Field> entry : definition.fields().entrySet()) {
            String key = entry.getKey();
            WebScraperDefinition.Field field = entry.getValue();
            Object raw = params.containsKey(key) ? params.get(key) : field.defaultValue();
            if (raw == null || (raw instanceof String text && text.isEmpty())) {
                if (field.required()) {
                    throw invalid("Missing required Web Scraper parameter: " + key);
                }
                continue;
            }
            result.put(key, normalize(key, raw, field));
        }
        return Map.copyOf(result);
    }

    private static String normalize(String key, Object raw, WebScraperDefinition.Field field) {
        return switch (field.type()) {
            case URL -> validateUrl(raw, key);
            case STRING -> {
                if (!(raw instanceof String text) || text.isBlank()) {
                    throw invalid(key + " must be a non-empty string");
                }
                yield text;
            }
            case POSITIVE_INTEGER_STRING -> String.valueOf(positiveInteger(raw, key));
            case POSITIVE_INTEGER -> String.valueOf(positiveInteger(raw, key));
            case ALL_TABS -> {
                if (!(raw instanceof Boolean bool)) {
                    throw invalid("all_tabs must be a boolean");
                }
                yield bool ? "true" : "flase";
            }
            case OPTION -> {
                if (!(raw instanceof String text) || !field.options().contains(text)) {
                    throw invalid(key + " must be one of the supported options");
                }
                yield text;
            }
        };
    }

    private static String validateUrl(Object raw, String key) {
        if (!(raw instanceof String text) || text.isBlank() || !text.equals(text.trim())) {
            throw invalid(key + " must be an HTTP or HTTPS URL");
        }
        try {
            URI uri = URI.create(text);
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return text;
        } catch (IllegalArgumentException exception) {
            throw invalid(key + " must be an HTTP or HTTPS URL");
        }
    }

    private static long positiveInteger(Object raw, String key) {
        if (raw instanceof Boolean || raw == null) {
            throw invalid(key + " must be a positive integer");
        }
        String text = String.valueOf(raw);
        if (!text.matches("\\d+")) {
            throw invalid(key + " must be a positive integer");
        }
        try {
            long value = Long.parseLong(text);
            if (value < 1) {
                throw invalid(key + " must be a positive integer");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(key + " must be a positive integer");
        }
    }

    private static ThorDataInvalidRequestException invalid(String message) {
        return new ThorDataInvalidRequestException(message, 400, null, null);
    }
}
