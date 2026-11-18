package com.thordata.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record WebScraperDefinition(String spiderName, Map<String, Field> fields) {
    record Field(Type type, boolean required, String defaultValue, List<String> options) {
        enum Type { URL, STRING, POSITIVE_INTEGER_STRING, POSITIVE_INTEGER, ALL_TABS, OPTION }
    }

    static final Map<String, WebScraperDefinition> ALL = definitions();

    private static Map<String, WebScraperDefinition> definitions() {
        Map<String, WebScraperDefinition> values = new LinkedHashMap<>();
        values.put("linkedin_company_information_by-url", new WebScraperDefinition("linkedin.com",
                Map.of("url", new Field(Field.Type.URL, true, null, List.of()))));
        values.put("linkedin_job_listings_information_by-job-url", new WebScraperDefinition("linkedin.com",
                Map.of("job_url", new Field(Field.Type.URL, true, null, List.of()))));
        values.put("youtube_video-post_by-keyword", new WebScraperDefinition("youtube.com", Map.of(
                "keyword", new Field(Field.Type.STRING, true, null, List.of()),
                "num_of_posts", new Field(Field.Type.POSITIVE_INTEGER_STRING, false, null, List.of()))));
        values.put("facebook_profile_by-profiles-url", new WebScraperDefinition("facebook.com",
                Map.of("url", new Field(Field.Type.URL, true, null, List.of()))));
        values.put("youtube_video-post_by-explore", new WebScraperDefinition("youtube.com", Map.of(
                "url", new Field(Field.Type.URL, true, null, List.of()),
                "all_tabs", new Field(Field.Type.ALL_TABS, false, null, List.of()))));
        values.put("twitter_profile_by-profileurl", new WebScraperDefinition("x.com",
                Map.of("url", new Field(Field.Type.URL, true, null, List.of()))));
        values.put("amazon_product_by-category-url", new WebScraperDefinition("amazon.com", Map.of(
                "url", new Field(Field.Type.URL, true, null, List.of()),
                "page_turning", new Field(Field.Type.POSITIVE_INTEGER, true, null, List.of()),
                "sort_by", new Field(Field.Type.OPTION, false, "Best Sellers", List.of(
                        "Best Sellers", "Newest Arrivals", "Avg. Customer Review", "Price: High to Low",
                        "Price: Low to High", "Featured")))));
        return Map.copyOf(values);
    }
}
