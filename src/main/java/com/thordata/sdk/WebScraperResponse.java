package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/** Completed Web Scraper response and task metadata. */
public record WebScraperResponse(
        List<Map<String, Object>> result,
        String taskId,
        String spiderId,
        Status status,
        int recordCount,
        int errorRecordCount,
        JsonNode raw) {
    public enum Status {
        COMPLETED,
        PARTIAL,
        EMPTY
    }
}
