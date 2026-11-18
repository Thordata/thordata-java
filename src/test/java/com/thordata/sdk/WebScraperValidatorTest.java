package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebScraperValidatorTest {
    @Test
    void validatesAmazonDefaultsAndPositivePageTurning() {
        Map<String, String> normalized = WebScraperValidator.validate(
                "amazon_product_by-category-url",
                Map.of("url", "https://amazon.com/cat", "page_turning", 2));
        assertEquals("Best Sellers", normalized.get("sort_by"));
        assertEquals("2", normalized.get("page_turning"));
    }

    @Test
    void preservesTheServiceAllTabsSpelling() {
        Map<String, String> normalized = WebScraperValidator.validate(
                "youtube_video-post_by-explore",
                Map.of("url", "https://youtube.com/feed/explore", "all_tabs", false));
        assertEquals("flase", normalized.get("all_tabs"));
    }

    @Test
    void rejectsUnknownAndCredentialBearingUrls() {
        assertThrows(ThorDataInvalidRequestException.class, () -> WebScraperValidator.validate(
                "linkedin_company_information_by-url",
                Map.of("url", "https://user:pass@example.com", "extra", "nope")));
    }

    @Test
    void rejectsOverflowingPositiveIntegersAsInvalidRequests() {
        assertThrows(ThorDataInvalidRequestException.class, () -> WebScraperValidator.validate(
                "amazon_product_by-category-url",
                Map.of("url", "https://amazon.com/cat", "page_turning", "999999999999999999999999")));
    }
}
