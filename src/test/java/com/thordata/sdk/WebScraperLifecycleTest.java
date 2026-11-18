package com.thordata.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thordata.sdk.errors.ThorDataWebScraperException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WebScraperLifecycleTest {
    @Test
    void completesBuilderPollDownloadAndRecordLifecycle() {
        QueueTransport transport = new QueueTransport(
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"task_id\":\"TASK-1\"}}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":[{\"task_id\":\"TASK-1\",\"status\":\"Ready\"}]}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"tasks_ids\":\"TASK-1\",\"download\":\"https://scraperapi.thordata.com/download?task_id=TASK-1&type=json&api_key=server-key\"}}", Map.of()),
                new HttpResponseData(200, "[{\"name\":\"Example\",\"success\":true}]", Map.of()));
        ThorDataClient client = ThorDataClient.builder().apiKey("secret-token").transport(transport).build();
        WebScraperResponse result = client.scrapeResponse("linkedin_company_information_by-url",
                Map.of("url", "https://www.linkedin.com/company/example"), new ScrapeOptions(1, true, "test-file"));
        assertEquals("TASK-1", result.taskId());
        assertEquals(WebScraperResponse.Status.COMPLETED, result.status());
        assertEquals(1, result.recordCount());
        assertEquals("Example", result.result().get(0).get("name"));
        assertEquals(4, transport.requests.size());
        assertEquals("Bearer secret-token", transport.requests.get(0).headers().get("Authorization"));
        assertEquals("sdk", transport.requests.get(0).body().contains("api_source=sdk") ? "sdk" : "missing");
        assertEquals(true, transport.requests.get(0).body().contains("source_platform=java"));
        assertEquals("secret-token", transport.requests.get(1).headers().get("sign"));
        assertEquals(true, transport.requests.get(2).body().contains("tasks_id=TASK-1"));
    }

    @Test
    void rejectsUntrustedDownloadAddress() {
        QueueTransport transport = new QueueTransport(
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"task_id\":\"TASK-2\"}}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":[{\"task_id\":\"TASK-2\",\"status\":\"Ready\"}]}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"tasks_ids\":\"TASK-2\",\"download\":\"https://evil.example/download?task_id=TASK-2&type=json\"}}", Map.of()));
        ThorDataClient client = ThorDataClient.builder().apiKey("secret-token").transport(transport).build();
        ThorDataWebScraperException error = assertThrows(ThorDataWebScraperException.class, () -> client.scrape(
                "linkedin_company_information_by-url", Map.of("url", "https://example.com"), ScrapeOptions.defaults()));
        assertEquals("download_url_invalid", error.errorCode());
        assertEquals(3, transport.requests.size());
    }

    @Test
    void timeoutRetainsTaskIdAndNeverResubmits() {
        QueueTransport transport = new QueueTransport(
                new HttpResponseData(200, "{\"code\":200,\"data\":{\"task_id\":\"TASK-3\"}}", Map.of()),
                new HttpResponseData(200, "{\"code\":200,\"data\":[{\"task_id\":\"TASK-3\",\"status\":\"Running\"}]}", Map.of()));
        ThorDataClient client = ThorDataClient.builder().apiKey("secret-token").transport(transport).build();
        ThorDataWebScraperException error = assertThrows(ThorDataWebScraperException.class, () -> client.scrape(
                "linkedin_company_information_by-url", Map.of("url", "https://example.com"),
                new ScrapeOptions(1, true, "{{TasksID}}")));
        assertEquals("wait_timeout", error.errorCode());
        assertEquals("TASK-3", error.taskId());
        assertEquals(2, transport.requests.size());
    }

    @Test
    void cancellingAsyncScrapeStopsTheNextPoll() throws Exception {
        CountingTransport transport = new CountingTransport();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WebScraperRunner runner = new WebScraperRunner(
                "secret-token", java.time.Duration.ofSeconds(30), Map.of(), transport,
                System::nanoTime, ignored -> Thread.sleep(100));
        CompletableFuture<WebScraperResponse> future = runner.runAsync(
                "linkedin_company_information_by-url",
                Map.of("url", "https://example.com"),
                new ScrapeOptions(30, true, "{{TasksID}}"), executor);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (transport.polls.get() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, transport.polls.get());
        assertEquals(true, future.cancel(true));
        Thread.sleep(350);
        assertEquals(1, transport.polls.get());
        executor.shutdownNow();
    }

    private static final class QueueTransport implements HttpTransport {
        private final Queue<HttpResponseData> responses = new ArrayDeque<>();
        private final java.util.List<HttpRequestData> requests = new java.util.ArrayList<>();

        QueueTransport(HttpResponseData... values) {
            for (HttpResponseData value : values) {
                responses.add(value);
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

    private static final class CountingTransport implements HttpTransport {
        private final AtomicInteger polls = new AtomicInteger();

        @Override
        public HttpResponseData send(HttpRequestData request) {
            if (request.uri().getPath().endsWith("/builder")) {
                return new HttpResponseData(200,
                        "{\"code\":200,\"data\":{\"task_id\":\"TASK-CANCEL\"}}", Map.of());
            }
            polls.incrementAndGet();
            return new HttpResponseData(200,
                    "{\"code\":200,\"data\":[{\"task_id\":\"TASK-CANCEL\",\"status\":\"Running\"}]}",
                    Map.of());
        }

        @Override
        public CompletableFuture<HttpResponseData> sendAsync(HttpRequestData request) {
            return CompletableFuture.completedFuture(send(request));
        }
    }
}
