package com.thordata.sdk;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** JSON parsing helpers shared by SERP and Web Scraper flows. */
public final class JsonSupport {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    public static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }

    public static JsonNode parseDocuments(String text) {
        List<JsonNode> values = new ArrayList<>();
        try (JsonParser parser = MAPPER.getFactory().createParser(text)) {
            while (parser.nextToken() != null) {
                JsonNode value = MAPPER.readTree(parser);
                if (value == null) {
                    continue;
                }
                values.add(value);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid JSON documents", exception);
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (!values.isEmpty() && values.stream().allMatch(JsonNode::isArray)) {
            ArrayNode arrays = MAPPER.createArrayNode();
            values.forEach(value -> value.forEach(arrays::add));
            return arrays;
        }
        throw new IllegalArgumentException("Expected one JSON document or concatenated arrays");
    }

    public static JsonNode parseNested(JsonNode value) {
        JsonNode decoded = decodeJsonString(value);
        if (decoded != null && decoded.isObject()) {
            decoded = decoded.deepCopy();
            if (decoded.has("json") && decoded.get("json").isTextual()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) decoded)
                        .set("json", decodeJsonString(decoded.get("json")));
            }
            if (decoded.has("result")) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) decoded)
                        .set("result", decodeJsonString(decoded.get("result")));
            }
        }
        return decoded;
    }

    public static JsonNode decodeJsonString(JsonNode value) {
        if (value == null || !value.isTextual()) {
            return value;
        }
        String text = value.textValue();
        try {
            return MAPPER.readTree(text);
        } catch (IOException ignored) {
            return value;
        }
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Value is not JSON serializable", exception);
        }
    }
}
