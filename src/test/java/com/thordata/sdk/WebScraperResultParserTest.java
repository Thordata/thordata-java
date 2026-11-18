package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thordata.sdk.errors.ThorDataWebScraperException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebScraperResultParserTest {
    @Test
    void flattensConcatenatedArraysAndReportsPartialResults() {
        WebScraperResponse response = WebScraperResultParser.parse(
                "[{\"name\":\"First\",\"success\":true}]\n[{\"success\":false,\"error\":\"blocked\"}]",
                "TASK-1", "linkedin_company_information_by-url", List.of("url"), List.of("secret"));
        assertEquals(WebScraperResponse.Status.PARTIAL, response.status());
        assertEquals(2, response.recordCount());
        assertEquals(1, response.errorRecordCount());
    }

    @Test
    void rejectsWhenEveryRecordFailed() {
        assertThrows(ThorDataWebScraperException.class, () -> WebScraperResultParser.parse(
                "[{\"success\":false,\"error\":\"blocked\"}]",
                "TASK-2", "linkedin_company_information_by-url", List.of("url"), List.of()));
    }

    @Test
    void reportsEmptyWhenOnlyInputFieldsArePresent() {
        WebScraperResponse response = WebScraperResultParser.parse(
                Map.of("url", "https://example.com"), "TASK-3",
                "linkedin_company_information_by-url", List.of("url"), List.of());
        assertEquals(WebScraperResponse.Status.EMPTY, response.status());
    }

    @Test
    void treatsTrueAndNonZeroErrorValuesAsFailedRecords() {
        WebScraperResponse response = WebScraperResultParser.parse(
                "[{\"name\":\"ok\",\"success\":true},{\"error\":true},{\"error_code\":1}]",
                "TASK-4", "linkedin_company_information_by-url", List.of("url"), List.of());
        assertEquals(WebScraperResponse.Status.PARTIAL, response.status());
        assertEquals(2, response.errorRecordCount());
    }
}
