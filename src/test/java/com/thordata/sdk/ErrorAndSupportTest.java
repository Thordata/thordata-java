package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import com.thordata.sdk.errors.ThorDataApiException;
import com.thordata.sdk.errors.ThorDataAPIError;
import com.thordata.sdk.errors.ThorDataError;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ErrorAndSupportTest {
    @Test
    void parsesConcatenatedArraysIntoOneArray() {
        JsonNode value = JsonSupport.parseDocuments("[{\"id\":1}]\n[{\"id\":2}]");
        assertEquals(2, value.size());
    }

    @Test
    void redactsApiKeysInNestedValuesAndUrls() {
        JsonNode input = JsonSupport.parse("{\"url\":\"https://x/download?api_key=secret\",\"value\":\"secret\"}");
        JsonNode output = SecretRedactor.redact(input, List.of("secret"));
        assertFalse(output.toString().contains("secret"));
    }

    @Test
    void apiExceptionRetainsBusinessAndHttpCodes() {
        ThorDataApiException error = new ThorDataInvalidRequestException("bad", 400, 200, null);
        assertEquals(400, error.code());
        assertEquals(200, error.statusCode());
        assertTrue(error instanceof ThorDataAPIError);
        assertTrue(error instanceof ThorDataError);
    }

    @Test
    void scraperErrorRetainsSafeStageAndTask() {
        var error = new com.thordata.sdk.errors.ThorDataWebScraperException(
                "wait_timeout", "poll", "TASK-1", "linkedin_company_information_by-url", null, null, Set.of());
        assertEquals("poll", error.stage());
        assertEquals("TASK-1", error.taskId());
    }
}
