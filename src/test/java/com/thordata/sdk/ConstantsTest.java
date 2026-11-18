package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

class ConstantsTest {
    @Test
    void exposesAllSupportedEnginesAndSpiders() {
        assertTrue(ThorDataConstants.SUPPORTED_ENGINES.contains("google_ai_mode"));
        assertTrue(ThorDataConstants.SUPPORTED_ENGINES.contains("duckduckgo"));
        assertEquals(7, ThorDataConstants.SUPPORTED_WEB_SCRAPERS.size());
        assertFalse(ThorDataConstants.REQUEST_PARAMETERS.isEmpty());
    }

    @Test
    void responseRecordsRetainMetadata() {
        JsonNode result = JsonNodeFactory.instance.objectNode().put("ok", true);
        SearchResponse response = new SearchResponse(result, "task-1", result);
        assertEquals("task-1", response.taskId());
        assertTrue(response.result().get("ok").asBoolean());
    }

    @Test
    void defaultScrapeOptionsMatchTheOtherSdks() {
        ScrapeOptions options = ScrapeOptions.defaults();
        assertEquals(300, options.maxWaitSeconds());
        assertTrue(options.includeErrors());
        assertEquals("{{TasksID}}", options.fileName());
    }
}
