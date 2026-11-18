package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ContractFixtureTest {
    @Test
    void sharedFixturesMatchTheWireEnvelopes() throws IOException {
        Path root = Path.of("..", "contract-fixtures").toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            root = Path.of("src", "test", "resources", "contract-fixtures").toAbsolutePath().normalize();
        }
        JsonNode serp = JsonSupport.MAPPER.readTree(Files.readString(root.resolve("serp/envelopes.json")));
        JsonNode scraper = JsonSupport.MAPPER.readTree(Files.readString(root.resolve("web-scraper/lifecycle.json")));
        assertEquals(0, serp.path("collection_failed").path("code").asInt());
        assertEquals("Running", scraper.path("status").get(0).path("data").get(0).path("status").asText());
        assertTrue(scraper.path("download_address").path("data").path("download").isTextual());
        assertTrue(scraper.path("records").get(0).path("success").asBoolean());
    }

    @Test
    void sdkParsesTheSharedSerpAndScraperEnvelopes() throws IOException {
        Path root = Path.of("src", "test", "resources", "contract-fixtures").toAbsolutePath().normalize();
        JsonNode serp = JsonSupport.MAPPER.readTree(Files.readString(root.resolve("serp/envelopes.json")));
        JsonNode scraper = JsonSupport.MAPPER.readTree(Files.readString(root.resolve("web-scraper/lifecycle.json")));
        FixtureTransport transport = new FixtureTransport(
                new HttpResponseData(200, serp.path("success").toString(), Map.of()),
                new HttpResponseData(200, scraper.path("builder").toString(), Map.of()),
                new HttpResponseData(200, scraper.path("status").get(0).toString(), Map.of()),
                new HttpResponseData(200, scraper.path("status").get(1).toString(), Map.of()),
                new HttpResponseData(200, scraper.path("download_address").toString(), Map.of()),
                new HttpResponseData(200, scraper.path("records").toString(), Map.of()));
        ThorDataClient client = ThorDataClient.builder().apiKey("secret").transport(transport).build();
        SearchResponse search = client.searchResponse(Map.of("q", "pizza"));
        assertEquals("task-serp-1", search.taskId());
        WebScraperRunner runner = new WebScraperRunner(
                "secret", Duration.ofSeconds(30), Map.of(), transport, System::nanoTime, ignored -> { });
        WebScraperResponse scrape = runner.run(
                "linkedin_company_information_by-url", Map.of("url", "https://example.com"),
                new ScrapeOptions(1, true, "{{TasksID}}"));
        assertEquals("task-scrape-1", scrape.taskId());
        assertEquals("example", scrape.result().get(0).get("name"));
    }

    private static final class FixtureTransport implements HttpTransport {
        private final Queue<HttpResponseData> responses = new ArrayDeque<>();

        FixtureTransport(HttpResponseData... values) {
            for (HttpResponseData value : values) {
                responses.add(value);
            }
        }

        @Override
        public HttpResponseData send(HttpRequestData request) {
            return responses.remove();
        }

        @Override
        public CompletableFuture<HttpResponseData> sendAsync(HttpRequestData request) {
            return CompletableFuture.completedFuture(send(request));
        }
    }
}
