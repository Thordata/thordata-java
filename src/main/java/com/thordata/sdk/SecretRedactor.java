package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Redacts API keys and signed URLs from diagnostics and returned payloads. */
public final class SecretRedactor {
    private static final Pattern SECRET_URL = Pattern.compile(
            "https?://[^\\s\\\"<>]*(?:api_key|access_token|authorization|sign|token)=[^\\s\\\"<>]*",
            Pattern.CASE_INSENSITIVE);

    private SecretRedactor() {
    }

    public static String redactText(Object value, Set<String> secrets) {
        if (value == null) {
            return "";
        }
        String text = SECRET_URL.matcher(String.valueOf(value)).replaceAll("[redacted URL]");
        List<String> ordered = secrets == null ? List.of() : secrets.stream()
                .filter(secret -> secret != null && !secret.isBlank())
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        for (String secret : ordered) {
            text = text.replace(secret, "[redacted]");
        }
        return text.replaceAll("[\\r\\n\\t]+", " ").trim().substring(0, Math.min(250, text.trim().length()));
    }

    public static JsonNode redact(JsonNode value, List<String> secrets) {
        Set<String> set = Set.copyOf(secrets == null ? List.of() : secrets);
        if (value == null || value.isNull()) {
            return value;
        }
        if (value.isTextual()) {
            return JsonSupport.MAPPER.getNodeFactory().textNode(redactText(value.textValue(), set));
        }
        if (value.isArray()) {
            ArrayNode result = JsonSupport.MAPPER.createArrayNode();
            value.forEach(item -> result.add(redact(item, new ArrayList<>(set))));
            return result;
        }
        if (value.isObject()) {
            ObjectNode result = JsonSupport.MAPPER.createObjectNode();
            value.fields().forEachRemaining(entry -> result.set(redactText(entry.getKey(), set), redact(entry.getValue(), new ArrayList<>(set))));
            return result;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Object redactValue(Object value, List<String> secrets) {
        Set<String> set = Set.copyOf(secrets == null ? List.of() : secrets);
        if (value instanceof String text) {
            return redactText(text, set);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(redactText(key, set), redactValue(item, new ArrayList<>(set))));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> redactValue(item, new ArrayList<>(set))).toList();
        }
        return value;
    }
}
