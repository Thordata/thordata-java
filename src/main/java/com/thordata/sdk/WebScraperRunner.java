package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.thordata.sdk.errors.ThorDataConnectionException;
import com.thordata.sdk.errors.ThorDataException;
import com.thordata.sdk.errors.ThorDataInvalidRequestException;
import com.thordata.sdk.errors.ThorDataWebScraperException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Executes the submit, poll, download-address, and result-download lifecycle. */
final class WebScraperRunner {
    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    private final String apiKey;
    private final Duration timeout;
    private final Map<String, String> customHeaders;
    private final HttpTransport transport;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    WebScraperRunner(String apiKey, Duration timeout, Map<String, String> customHeaders,
                     HttpTransport transport) {
        this(apiKey, timeout, customHeaders, transport, System::nanoTime, milliseconds -> Thread.sleep(milliseconds));
    }

    WebScraperRunner(String apiKey, Duration timeout, Map<String, String> customHeaders,
                     HttpTransport transport, LongSupplier nanoTime, Sleeper sleeper) {
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.customHeaders = Map.copyOf(customHeaders);
        this.transport = transport;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    WebScraperResponse run(String spiderId, Map<String, ?> params, ScrapeOptions options) {
        return run(spiderId, params, options, null);
    }

    private WebScraperResponse run(String spiderId, Map<String, ?> params, ScrapeOptions options,
                                   CancellationToken cancellationToken) {
        ScrapeOptions actual = options == null ? ScrapeOptions.defaults() : options;
        Map<String, String> normalized = validateOptions(spiderId, params, actual);
        long deadline = nanoTime.getAsLong() + Duration.ofSeconds(actual.maxWaitSeconds()).toNanos();
        Set<String> secrets = new HashSet<>();
        secrets.add(apiKey);
        String taskId = null;
        JsonNode submission = null;
        JsonNode lastStatus = null;
        try {
            checkCancelled(cancellationToken, "submit", null, spiderId, secrets);
            Map<String, String> submit = new LinkedHashMap<>();
            submit.put("spider_name", WebScraperDefinition.ALL.get(spiderId).spiderName());
            submit.put("spider_id", spiderId);
            submit.put("spider_parameters", JsonSupport.stringify(List.of(normalized)));
            submit.put("spider_errors", String.valueOf(actual.includeErrors()));
            submit.put("file_name", actual.fileName());
            submit.put("api_source", ThorDataConstants.INTEGRATION_PLATFORM);
            submit.put("source_platform", "java");
            submission = requestJson("submit", null, ThorDataConstants.WEB_SCRAPER_BUILDER_URL,
                    postHeaders(true), submit, deadline, spiderId, secrets);
            JsonNode submissionData = checkEnvelope(submission, "submit", null, spiderId, 200, "submission_failed", secrets);
            taskId = safeText(submissionData.get("task_id"));
            if (taskId == null || !taskId.matches("^[\\w-]{1,200}$") || taskId.contains(apiKey)) {
                throw scraperError("submission_unknown", "submit", null, spiderId, null, null, secrets);
            }
            while (true) {
                checkCancelled(cancellationToken, "poll", taskId, spiderId, secrets);
                ensureRemaining(deadline, "poll", taskId, spiderId, secrets);
                Map<String, String> pollForm = Map.of(
                        "tasks_ids", taskId,
                        "api_source", ThorDataConstants.INTEGRATION_PLATFORM,
                        "source_platform", "java");
                JsonNode poll = requestJson("poll", taskId,
                        ThorDataConstants.WEB_SCRAPER_TASK_API_BASE_URL + "/tasks-status",
                        postHeaders(false), pollForm, deadline, spiderId, secrets);
                checkCancelled(cancellationToken, "poll", taskId, spiderId, secrets);
                JsonNode data = checkEnvelope(poll, "poll", taskId, spiderId, 200, "protocol_error", secrets);
                if (!data.isArray()) {
                    throw scraperError("protocol_error", "poll", taskId, spiderId, null, null, secrets);
                }
                List<JsonNode> matches = new ArrayList<>();
                String submittedTaskId = taskId;
                data.forEach(row -> {
                    if (row.isObject() && submittedTaskId.equals(safeText(row.get("task_id")))) {
                        matches.add(row);
                    }
                });
                if (matches.size() != 1) {
                    throw scraperError("protocol_error", "poll", taskId, spiderId, null, null, secrets);
                }
                lastStatus = matches.get(0);
                String status = safeText(lastStatus.get("status"));
                if (!("Running".equals(status) || "Ready".equals(status) || "Failed".equals(status))) {
                    throw scraperError("protocol_error", "poll", taskId, spiderId, null, null, secrets);
                }
                if ("Failed".equals(status)) {
                    String reason = lastStatus.path("error").asText(null);
                    if (actual.includeErrors()) {
                        try {
                            JsonNode diagnostic = download(taskId, spiderId, normalized, deadline, secrets);
                            JsonNode first = firstFailedRecord(diagnostic);
                            if (first != null) {
                                reason = first.path("error").asText(reason);
                            }
                        } catch (ThorDataException ignored) {
                            // Preserve the task failure if diagnostic download fails.
                        }
                    }
                    throw scraperError("scrape_failed", "poll", taskId, spiderId, reason,
                            lastStatus.get("error_code"), secrets);
                }
                if ("Ready".equals(status)) {
                    checkCancelled(cancellationToken, "download", taskId, spiderId, secrets);
                    JsonNode downloaded = download(taskId, spiderId, normalized, deadline, secrets);
                    WebScraperResponse parsed = WebScraperResultParser.parse(downloaded, taskId, spiderId,
                            new ArrayList<>(normalized.keySet()), new ArrayList<>(secrets));
                    var raw = JsonSupport.MAPPER.createObjectNode();
                    raw.set("submission", SecretRedactor.redact(submission, new ArrayList<>(secrets)));
                    raw.set("status", SecretRedactor.redact(lastStatus, new ArrayList<>(secrets)));
                    var downloadRaw = JsonSupport.MAPPER.createObjectNode();
                    downloadRaw.put("tasks_ids", taskId);
                    raw.set("download", downloadRaw);
                    return new WebScraperResponse(parsed.result(), parsed.taskId(), parsed.spiderId(), parsed.status(),
                            parsed.recordCount(), parsed.errorRecordCount(), raw);
                }
                long remaining = ensureRemaining(deadline, "poll", taskId, spiderId, secrets);
                try {
                    long delay = Math.min(Duration.ofSeconds(ThorDataConstants.POLL_INTERVAL_SECONDS).toMillis(), remaining);
                    if (cancellationToken == null) {
                        sleeper.sleep(delay);
                    } else {
                        cancellationToken.await(delay);
                    }
                    checkCancelled(cancellationToken, "poll", taskId, spiderId, secrets);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw scraperError("request_failed", "poll", taskId, spiderId, "Request cancelled", null, secrets);
                }
            }
        } catch (ThorDataWebScraperException exception) {
            throw exception;
        } catch (ThorDataConnectionException exception) {
            throw scraperError(taskId == null ? "submission_unknown" : "request_failed",
                    taskId == null ? "submit" : "poll", taskId, spiderId, exception.getMessage(), null, secrets);
        } catch (RuntimeException exception) {
            throw scraperError(taskId == null ? "submission_unknown" : "request_failed",
                    taskId == null ? "submit" : "poll", taskId, spiderId, exception.getMessage(), null, secrets);
        }
    }

    CompletableFuture<WebScraperResponse> runAsync(String spiderId, Map<String, ?> params,
                                                    ScrapeOptions options, Executor executor) {
        CancellationToken cancellationToken = new CancellationToken();
        CompletableFuture<WebScraperResponse> worker = CompletableFuture.supplyAsync(
                () -> run(spiderId, params, options, cancellationToken), executor);
        CompletableFuture<WebScraperResponse> result = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                cancellationToken.cancel();
                worker.cancel(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        worker.whenComplete((value, failure) -> {
            if (failure == null) {
                result.complete(value);
            } else if (worker.isCancelled() && cancellationToken.isCancelled()) {
                result.cancel(false);
            } else {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private JsonNode download(String taskId, String spiderId, Map<String, String> normalized,
                              long deadline, Set<String> secrets) {
        Map<String, String> form = Map.of(
                "tasks_id", taskId,
                "type", "json",
                "api_source", ThorDataConstants.INTEGRATION_PLATFORM,
                "source_platform", "java");
        JsonNode address = requestJson("download_url", taskId,
                ThorDataConstants.WEB_SCRAPER_TASK_API_BASE_URL + "/tasks-download",
                postHeaders(false), form, deadline, spiderId, secrets);
        JsonNode data = checkEnvelope(address, "download_url", taskId, spiderId, 200, "protocol_error", secrets);
        if (!taskId.equals(safeText(data.get("tasks_ids")))) {
            throw scraperError("protocol_error", "download_url", taskId, spiderId, null, null, secrets);
        }
        String url = validateDownloadUrl(safeText(data.get("download")), taskId, spiderId, secrets);
        URI parsed = URI.create(url);
        String downloadKey = queryValues(parsed).get("api_key");
        if (downloadKey != null) {
            secrets.add(downloadKey);
        }
        HttpResponseData response = send(new HttpRequestData("GET", parsed,
                Map.of("Accept", "application/json", "User-Agent", "thordata-java/" + ThorDataConstants.VERSION),
                "", remainingDuration(deadline)), "download", taskId, spiderId, secrets);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw scraperError("download_failed", "download", taskId, spiderId,
                    "HTTP " + response.statusCode(), null, secrets);
        }
        try {
            return JsonSupport.parseDocuments(response.body());
        } catch (RuntimeException exception) {
            throw scraperError("protocol_error", "download", taskId, spiderId,
                    "Response was not valid JSON", null, secrets);
        }
    }

    private JsonNode requestJson(String stage, String taskId, String url, Map<String, String> headers,
                                 Map<String, String> form, long deadline, String spiderId, Set<String> secrets) {
        HttpResponseData response = send(new HttpRequestData("POST", URI.create(url), headers,
                form(form), remainingDuration(deadline)), stage, taskId, spiderId, secrets);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = stage.equals("submit") && response.statusCode() >= 500
                    ? "submission_unknown" : businessError(response.statusCode(), stage);
            throw scraperError(code, stage, taskId, spiderId, "HTTP " + response.statusCode(), response.statusCode(), secrets);
        }
        try {
            return JsonSupport.parseDocuments(response.body());
        } catch (RuntimeException exception) {
            throw scraperError("protocol_error", stage, taskId, spiderId,
                    "Response was not valid JSON", null, secrets);
        }
    }

    private HttpResponseData send(HttpRequestData request, String stage, String taskId,
                                  String spiderId, Set<String> secrets) {
        try {
            return transport.send(request);
        } catch (HttpTimeoutException exception) {
            throw scraperError(stage.equals("submit") ? "submission_unknown" : "wait_timeout",
                    stage, taskId, spiderId, "Request timed out", null, secrets);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw scraperError(stage.equals("submit") ? "submission_unknown" : "request_failed",
                    stage, taskId, spiderId, "Request cancelled", null, secrets);
        } catch (Exception exception) {
            throw scraperError(stage.equals("submit") ? "submission_unknown" : "request_failed",
                    stage, taskId, spiderId, exception.getMessage(), null, secrets);
        }
    }

    private JsonNode checkEnvelope(JsonNode payload, String stage, String taskId, String spiderId,
                                   int expectedCode, String fallback, Set<String> secrets) {
        if (payload == null || !payload.isObject()) {
            throw scraperError("protocol_error", stage, taskId, spiderId, null, null, secrets);
        }
        Integer code = payload.has("code") && payload.get("code").canConvertToInt()
                ? payload.get("code").asInt() : parseInt(payload.get("code"));
        if (code == null || code != expectedCode) {
            throw scraperError(businessError(code, fallback), stage, taskId, spiderId,
                    reason(payload), code, secrets);
        }
        JsonNode data = payload.get("data");
        if (data == null || !data.isObject() && !data.isArray()) {
            throw scraperError("protocol_error", stage, taskId, spiderId, null, null, secrets);
        }
        return data;
    }

    private Map<String, String> validateOptions(String spiderId, Map<String, ?> params, ScrapeOptions options) {
        try {
            return WebScraperValidator.validate(spiderId, params);
        } catch (ThorDataInvalidRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ThorDataInvalidRequestException(exception.getMessage(), 400, null, null);
        }
    }

    private Map<String, String> postHeaders(boolean bearer) {
        Map<String, String> headers = new LinkedHashMap<>();
        customHeaders.forEach((key, value) -> {
            String lower = key.toLowerCase();
            if (!(lower.equals("authorization") || lower.equals("sign") || lower.equals("api-source")
                    || lower.equals("api_source") || lower.equals("source_platform") || lower.equals("platform")
                    || lower.equals("origin"))) {
                headers.put(key, value);
            }
        });
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("User-Agent", "thordata-java/" + ThorDataConstants.VERSION);
        if (bearer) {
            headers.put("Authorization", "Bearer " + apiKey);
        } else {
            headers.put("sign", apiKey);
        }
        return headers;
    }

    private long ensureRemaining(long deadline, String stage, String taskId, String spiderId, Set<String> secrets) {
        long remaining = deadline - nanoTime.getAsLong();
        if (remaining <= 0) {
            throw scraperError("wait_timeout", stage, taskId, spiderId, null, null, secrets);
        }
        long milliseconds = Duration.ofNanos(remaining).toMillis();
        if (milliseconds <= 0) {
            throw scraperError("wait_timeout", stage, taskId, spiderId, null, null, secrets);
        }
        return milliseconds;
    }

    private Duration remainingDuration(long deadline) {
        long remaining = deadline - nanoTime.getAsLong();
        return Duration.ofNanos(Math.max(1, remaining));
    }

    private static String form(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(java.net.URLEncoder.encode(key, StandardCharsets.UTF_8));
            result.append('=').append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    private static String validateDownloadUrl(String value, String taskId, String spiderId, Set<String> secrets) {
        try {
            if (value == null || !value.equals(value.trim())) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(value);
            URI trusted = URI.create(ThorDataConstants.WEB_SCRAPER_DOWNLOAD_URL);
            if (!trusted.getScheme().equalsIgnoreCase(uri.getScheme())
                    || !trusted.getHost().equalsIgnoreCase(uri.getHost())
                    || !trusted.getPath().equals(uri.getPath())
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            Map<String, String> query = queryValues(uri);
            if (query.size() != countQueryPairs(uri) || !query.keySet().stream()
                    .allMatch(key -> Set.of("task_id", "type", "api_key").contains(key))
                    || !taskId.equals(query.get("task_id")) || !"json".equals(query.get("type"))) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException exception) {
            throw scraperError("download_url_invalid", "download_url", taskId, spiderId, null, null, secrets);
        }
    }

    private static Map<String, String> queryValues(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&", -1)) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (values.put(key, value) != null) {
                throw new IllegalArgumentException("duplicate query key");
            }
        }
        return values;
    }

    private static int countQueryPairs(URI uri) {
        return uri.getRawQuery() == null || uri.getRawQuery().isBlank() ? 0 : uri.getRawQuery().split("&", -1).length;
    }

    private static String businessError(Integer code, String fallback) {
        if (code != null && Set.of(401, 10013, 10014, 10047, 10048).contains(code)) {
            return "authentication_failed";
        }
        if (code != null && code == 402) {
            return "insufficient_balance";
        }
        if (code != null && code == 403) {
            return "permission_denied";
        }
        return fallback;
    }

    private static String reason(JsonNode payload) {
        for (String key : List.of("msg", "message", "error")) {
            if (payload.has(key) && !payload.get(key).isNull()) {
                return payload.get(key).asText();
            }
        }
        return payload.has("data") && payload.get("data").isTextual() ? payload.get("data").asText() : null;
    }

    private static JsonNode firstFailedRecord(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        if (payload.isArray()) {
            for (JsonNode row : payload) {
                if (row.isObject() && (row.path("success").isBoolean() && !row.path("success").asBoolean()
                        || row.has("error") && !row.path("error").asText().isBlank())) {
                    return row;
                }
            }
        }
        return null;
    }

    private static String safeText(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private static Integer parseInt(JsonNode node) {
        if (node != null && node.isTextual() && node.textValue().matches("\\d+")) {
            return Integer.valueOf(node.textValue());
        }
        return null;
    }

    private static ThorDataWebScraperException scraperError(String code, String stage, String taskId,
                                                            String spiderId, Object reason, Object serviceCode,
                                                            Set<String> secrets) {
        return new ThorDataWebScraperException(code, stage, taskId, spiderId, reason, serviceCode, secrets);
    }

    private static void checkCancelled(CancellationToken cancellationToken, String stage, String taskId,
                                       String spiderId, Set<String> secrets) {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            throw scraperError("request_failed", stage, taskId, spiderId,
                    "Request cancelled", null, secrets);
        }
    }
}
