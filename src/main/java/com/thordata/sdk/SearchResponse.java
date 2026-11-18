package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;

/** Parsed SERP response with the upstream envelope retained. */
public record SearchResponse(JsonNode result, String taskId, JsonNode raw) {
}
