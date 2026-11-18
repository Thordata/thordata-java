package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thordata.sdk.errors.ThorDataWebScraperException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts downloaded JSON records into the stable Web Scraper response type. */
public final class WebScraperResultParser {
    private static final Set<String> IGNORED_FIELDS = Set.of(
            "input", "input_url", "url", "job_url", "keyword", "success", "error", "error_code");

    private WebScraperResultParser() {
    }

    public static WebScraperResponse parse(Object payload, String taskId, String spiderId,
                                           List<String> inputKeys, List<String> secrets) {
        JsonNode value;
        try {
            value = payload instanceof String text ? JsonSupport.parseDocuments(text)
                    : payload instanceof JsonNode node ? node : JsonSupport.MAPPER.valueToTree(payload);
        } catch (RuntimeException exception) {
            throw scraperError("invalid_result", "parse", taskId, spiderId, exception.getMessage(), secrets);
        }
        if (value == null || value.isNull()) {
            throw scraperError("invalid_result", "parse", taskId, spiderId, "empty result", secrets);
        }
        List<JsonNode> rows = new ArrayList<>();
        if (value.isArray()) {
            value.forEach(rows::add);
        } else if (value.isObject()) {
            rows.add(value);
        } else {
            throw scraperError("invalid_result", "parse", taskId, spiderId, "records must be objects", secrets);
        }
        if (rows.stream().anyMatch(row -> !row.isObject())) {
            throw scraperError("invalid_result", "parse", taskId, spiderId, "records must be objects", secrets);
        }
        List<Map<String, Object>> records = rows.stream()
                .map(row -> JsonSupport.MAPPER.convertValue(row, new TypeReference<LinkedHashMap<String, Object>>() { }))
                .map(valueMap -> (Map<String, Object>) SecretRedactor.redactValue(valueMap, secrets))
                .toList();
        int errors = 0;
        for (JsonNode row : rows) {
            if (failedRecord(row)) {
                errors++;
            }
        }
        if (!records.isEmpty() && errors == records.size()) {
            JsonNode first = rows.get(0);
            throw scraperError("scrape_failed", "parse", taskId, spiderId,
                    first.path("error").asText(null), secrets);
        }
        Set<String> input = new HashSet<>(inputKeys == null ? List.of() : inputKeys);
        boolean meaningful = rows.stream().anyMatch(row -> {
            Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!input.contains(field.getKey()) && !IGNORED_FIELDS.contains(field.getKey())
                        && isMeaningful(field.getValue())) {
                    return true;
                }
            }
            return false;
        });
        WebScraperResponse.Status status = errors > 0 ? WebScraperResponse.Status.PARTIAL
                : meaningful ? WebScraperResponse.Status.COMPLETED : WebScraperResponse.Status.EMPTY;
        JsonNode raw = SecretRedactor.redact(value, secrets);
        return new WebScraperResponse(records, taskId, spiderId, status, records.size(), errors, raw);
    }

    private static boolean failedRecord(JsonNode row) {
        if (row.path("success").isBoolean() && !row.path("success").asBoolean()) {
            return true;
        }
        JsonNode error = row.get("error");
        if (error != null && !error.isNull()) {
            if (error.isBoolean()) {
                if (error.asBoolean()) {
                    return true;
                }
            } else if (error.isNumber()) {
                if (error.doubleValue() != 0) {
                    return true;
                }
            } else if (isMeaningful(error)) {
                return true;
            }
        }
        JsonNode code = row.get("error_code");
        if (code == null || code.isNull() || (code.isTextual() && code.textValue().isBlank())) {
            return false;
        }
        if (code.isNumber()) {
            return code.intValue() != 0 && code.intValue() != 200;
        }
        return !code.asText().equals("0") && !code.asText().equals("200");
    }

    private static boolean isMeaningful(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isTextual()) {
            return !value.textValue().isBlank();
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (isMeaningful(item)) {
                    return true;
                }
            }
            return false;
        }
        if (value.isObject()) {
            Iterator<JsonNode> values = value.elements();
            while (values.hasNext()) {
                if (isMeaningful(values.next())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static ThorDataWebScraperException scraperError(String code, String stage, String taskId,
                                                            String spiderId, Object reason, List<String> secrets) {
        return new ThorDataWebScraperException(code, stage, taskId, spiderId, reason, null,
                Set.copyOf(secrets == null ? List.of() : secrets));
    }
}
