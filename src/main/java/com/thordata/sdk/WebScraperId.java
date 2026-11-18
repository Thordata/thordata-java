package com.thordata.sdk;

import java.util.Arrays;

/** Known Web Scraper product identifiers. */
public enum WebScraperId {
    LINKEDIN_COMPANY("linkedin_company_information_by-url"),
    LINKEDIN_JOBS("linkedin_job_listings_information_by-job-url"),
    YOUTUBE_KEYWORD("youtube_video-post_by-keyword"),
    FACEBOOK_PROFILE("facebook_profile_by-profiles-url"),
    YOUTUBE_EXPLORE("youtube_video-post_by-explore"),
    TWITTER_PROFILE("twitter_profile_by-profileurl"),
    AMAZON_CATEGORY("amazon_product_by-category-url");

    private final String value;

    WebScraperId(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static WebScraperId fromValue(String value) {
        return Arrays.stream(values()).filter(id -> id.value.equals(value)).findFirst().orElse(null);
    }
}
