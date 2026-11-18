package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SerpRequestBuilderTest {
    @Test
    void normalizesAliasesBooleansAndDefaults() {
        Map<String, String> form = SerpRequestBuilder.build(Map.of(
                "engine", "google_search", "query", "pizza", "country", "us",
                "language", "en", "safe", true, "ignored", "drop-me"));
        assertEquals("google", form.get("engine"));
        assertEquals("pizza", form.get("q"));
        assertEquals("1", form.get("safe"));
        assertEquals("1", form.get("json"));
        assertEquals("1", form.get("isjson"));
        assertFalse(form.containsKey("ignored"));
    }

    @Test
    void yandexMovesQueryToTextAndRejectsWebScraperEngine() {
        Map<String, String> form = SerpRequestBuilder.build(Map.of("engine", "yandex", "q", "pizza"));
        assertEquals("pizza", form.get("text"));
        assertFalse(form.containsKey("q"));
        assertThrows(ThorDataInvalidRequestException.class,
                () -> SerpRequestBuilder.build(Map.of("engine", "webpage")));
    }

    @Test
    void explicitEngineOverridesMapEngine() {
        Map<String, String> form = SerpRequestBuilder.build(Map.of("engine", "bing", "q", "pizza"), "google_search");
        assertEquals("google", form.get("engine"));
    }

    @Test
    void rejectsSetValuesInsteadOfUsingAStringRepresentation() {
        assertThrows(ThorDataInvalidRequestException.class,
                () -> SerpRequestBuilder.build(Map.of("filters", Set.of("a", "b"))));
    }
}
