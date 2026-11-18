package com.thordata.sdk;

import com.thordata.sdk.errors.ThorDataInvalidRequestException;

/** Options controlling one Web Scraper task. */
public record ScrapeOptions(int maxWaitSeconds, boolean includeErrors, String fileName) {
    public ScrapeOptions {
        if (maxWaitSeconds < 1 || maxWaitSeconds > ThorDataConstants.DEFAULT_MAX_WAIT_SECONDS) {
            throw new ThorDataInvalidRequestException("maxWaitSeconds must be between 1 and 300", 400, null, null);
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ThorDataInvalidRequestException("fileName must be a non-empty string", 400, null, null);
        }
    }

    public static ScrapeOptions defaults() {
        return new ScrapeOptions(ThorDataConstants.DEFAULT_MAX_WAIT_SECONDS, true, "{{TasksID}}");
    }
}
