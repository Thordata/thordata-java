package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.thordata.sdk.errors.ThorDataApiException;
import com.thordata.sdk.errors.ThorDataNotCollectedException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SerpClientTest {
    @Test
    void parsesNestedResultAndTaskId() {
        FakeTransport transport = new FakeTransport("{\"code\":200,\"data\":{\"task_id\":\"task-1\",\"result\":\"{\\\"ok\\\":true}\"}}");
        ThorDataClient client = ThorDataClient.builder().apiKey("secret").transport(transport).build();
        SearchResponse response = client.searchResponse(Map.of("q", "pizza"));
        assertEquals("task-1", response.taskId());
        assertEquals(true, response.result().get("ok").asBoolean());
    }

    @Test
    void mapsCollectionFailureAndPreservesRawBody() {
        FakeTransport transport = new FakeTransport(
                "{\"code\":0,\"data\":\"error, Collection failed\"}",
                "{\"code\":0,\"data\":\"error, Collection failed\"}");
        ThorDataClient client = ThorDataClient.builder().apiKey("secret").transport(transport).build();
        ThorDataNotCollectedException error = org.junit.jupiter.api.Assertions.assertThrows(
                ThorDataNotCollectedException.class, () -> client.search(Map.of("q", "pizza")));
        assertEquals(0, error.code());
        assertEquals("{\"code\":0,\"data\":\"error, Collection failed\"}", client.rawSearch(Map.of("q", "pizza")));
    }

    @Test
    void exposesLowLevelRequestAndSearchJsonAliases() {
        FakeTransport transport = new FakeTransport(
                "{\"code\":200,\"data\":{\"result\":{\"ok\":true}}}",
                "raw-body");
        ThorDataClient client = ThorDataClient.builder().apiKey("secret").transport(transport).build();
        assertTrue(client.searchJson(Map.of("q", "one")).path("ok").asBoolean());
        HttpResponseData response = client.request("POST", "/request", Map.of("q", "two"));
        assertEquals(200, response.statusCode());
        assertTrue(transport.requests.get(1).body().contains("q=two"));
    }

    @Test
    void acceptsJavaScriptCompatibleApiTokenBuilderAlias() {
        ThorDataClient client = ThorDataClient.builder()
                .apiToken("token-alias")
                .transport(new FakeTransport("{\"code\":200,\"data\":{\"result\":{}}}"))
                .build();
        assertEquals("token-alias", client.apiKey());
    }

    @Test
    void preservesHttpErrorBodyInApiException() {
        FakeTransport transport = new FakeTransport(
                new HttpResponseData(503, "{\"data\":\"upstream detail\",\"msg\":\"gateway detail\"}", Map.of()));
        ThorDataApiException error = org.junit.jupiter.api.Assertions.assertThrows(
                ThorDataApiException.class,
                () -> ThorDataClient.builder().apiKey("secret").transport(transport).build()
                        .search(Map.of("q", "one")));
        assertEquals("upstream detail", error.getMessage());
        assertEquals("upstream detail", error.responseData().path("data").asText());
    }

    @Test
    void usesInjectedExecutorForAsyncScrapesWithoutOwningIt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-thordata-executor");
            thread.setDaemon(true);
            return thread;
        });
        FakeTransport transport = new FakeTransport(
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"task_id\":\"TASK-EXEC\"}}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":[{\"task_id\":\"TASK-EXEC\",\"status\":\"Ready\"}]}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"tasks_ids\":\"TASK-EXEC\",\"download\":\"https://scraperapi.thordata.com/download?task_id=TASK-EXEC&type=json&api_key=server-key\"}}", Map.of()),
                new HttpResponseData(200, "[{\"name\":\"Example\",\"success\":true}]", Map.of()));
        ThorDataClient client = ThorDataClient.builder().apiKey("secret").transport(transport)
                .executor(executor).build();
        WebScraperResponse response = client.scrapeResponseAsync(
                "linkedin_company_information_by-url", Map.of("url", "https://example.com"),
                new ScrapeOptions(1, true, "{{TasksID}}"))
                .get();
        assertEquals("TASK-EXEC", response.taskId());
        assertTrue(transport.requests.stream().anyMatch(request ->
                request.body().isEmpty() && request.headers().getOrDefault("User-Agent", "").contains("thordata-java")));
        client.close();
        assertEquals(false, executor.isShutdown());
        executor.shutdownNow();
    }

    private static void assertTrue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }

    private static final class FakeTransport implements HttpTransport {
        private final Queue<HttpResponseData> responses = new ArrayDeque<>();
        private final java.util.List<HttpRequestData> requests = new java.util.ArrayList<>();

        private FakeTransport(Object... values) {
            for (Object value : values) {
                responses.add(value instanceof HttpResponseData response
                        ? response : new HttpResponseData(200, String.valueOf(value), Map.of()));
            }
        }

        @Override
        public HttpResponseData send(HttpRequestData request) {
            requests.add(request);
            return responses.remove();
        }

        @Override
        public CompletableFuture<HttpResponseData> sendAsync(HttpRequestData request) {
            return CompletableFuture.completedFuture(send(request));
        }
    }
}
