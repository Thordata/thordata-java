package com.thordata.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.thordata.sdk.errors.ThorDataConfigurationException;
import com.thordata.sdk.errors.ThorDataConnectionException;
import com.thordata.sdk.errors.ThorDataTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/** Public blocking and asynchronous Thordata client. */
public final class ThorDataClient implements AutoCloseable {
    private final String apiKey;
    private final String apiUrl;
    private final String baseUrl;
    private final Duration timeout;
    private final Map<String, String> headers;
    private final HttpTransport transport;
    private final boolean ownsTransport;
    private final SerpClient serp;
    private final WebScraperRunner scraper;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final ReentrantLock syncScrapeLock = new ReentrantLock();

    ThorDataClient(ThorDataClientBuilder builder) {
        String key = firstNonBlank(builder.apiKey, builder.apiToken, builder.token,
                System.getenv("THORDATA_API_KEY"),
                System.getenv("THORDATA_API_TOKEN"), System.getenv("TALORDATA_API_TOKEN"));
        if (key == null) {
            throw new ThorDataConfigurationException(
                    "ThorData API key is required; pass apiKey or set THORDATA_API_KEY");
        }
        key = key.trim();
        if (!key.chars().allMatch(character -> character < 128)) {
            throw new ThorDataConfigurationException("ThorData API key must contain only ASCII characters");
        }
        if (builder.timeout == null || builder.timeout.isZero() || builder.timeout.isNegative()) {
            throw new ThorDataConfigurationException("timeout must be greater than zero");
        }
        String configuredUrl = firstNonBlank(builder.apiUrl, System.getenv("THORDATA_SERP_API_URL"));
        if (configuredUrl == null && builder.baseUrl != null && !builder.baseUrl.isBlank()) {
            configuredUrl = builder.baseUrl.replaceAll("/+$", "") + "/request";
        }
        if (configuredUrl == null) {
            configuredUrl = ThorDataConstants.DEFAULT_API_URL;
        }
        configuredUrl = configuredUrl.trim();
        try {
            URI uri = URI.create(configuredUrl);
            if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http")
                    || uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ThorDataConfigurationException("apiUrl must use http or https", exception);
        }
        this.apiKey = key;
        this.apiUrl = configuredUrl;
        this.baseUrl = firstNonBlank(builder.baseUrl, configuredUrl.replaceFirst("/request/?$", ""));
        this.timeout = builder.timeout;
        this.headers = Map.copyOf(builder.headers);
        this.transport = builder.transport != null
                ? builder.transport
                : (builder.httpClient == null ? new JdkHttpTransport() : new JdkHttpTransport(builder.httpClient));
        this.ownsTransport = builder.transport == null;
        this.serp = new SerpClient(apiKey, apiUrl, timeout, headers, transport);
        if (builder.executor != null) {
            this.executor = builder.executor;
            this.ownedExecutor = null;
        } else {
            ExecutorService owned = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "thordata-java");
                thread.setDaemon(true);
                return thread;
            });
            this.executor = owned;
            this.ownedExecutor = owned;
        }
        this.scraper = new WebScraperRunner(apiKey, timeout, headers, transport);
    }

    public static ThorDataClientBuilder builder() {
        return new ThorDataClientBuilder();
    }

    public String apiKey() {
        return apiKey;
    }

    public String apiUrl() {
        return apiUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration timeout() {
        return timeout;
    }

    public JsonNode search(Map<String, ?> params) {
        return searchResponse(params).result();
    }

    /** Alias matching the JavaScript SDK's explicit JSON search method. */
    public JsonNode searchJson(Map<String, ?> params) {
        return search(params);
    }

    public JsonNode search(String engine, Map<String, ?> params) {
        return searchResponse(engine, params).result();
    }

    public SearchResponse searchResponse(Map<String, ?> params) {
        return serp.searchResponse(params, null);
    }

    public SearchResponse searchResponse(String engine, Map<String, ?> params) {
        return serp.searchResponse(params, engine);
    }

    public String rawSearch(Map<String, ?> params) {
        return serp.rawSearch(params, null);
    }

    public CompletableFuture<String> rawSearchAsync(Map<String, ?> params) {
        return serp.rawSearchAsync(params, null);
    }

    /** Low-level form request compatible with the JavaScript client's request method. */
    public HttpResponseData request(String method, String path, Map<String, ?> params,
                                    Map<String, String> requestHeaders, boolean filterParams) {
        Map<String, String> form = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((key, value) -> {
                if (value == null || (filterParams && !ThorDataConstants.REQUEST_PARAMETERS.contains(key))) {
                    return;
                }
                String normalized = SerpRequestBuilder.normalizeValue(value);
                if (!normalized.isEmpty()) {
                    form.put(key, normalized);
                }
            });
        }
        String target = path == null || path.isBlank() ? "/request" : path;
        URI uri = target.startsWith("http://") || target.startsWith("https://")
                ? URI.create(target)
                : URI.create(target.equals("/request") || target.equals("request")
                        ? apiUrl : baseUrl.replaceAll("/+$", "") + (target.startsWith("/") ? target : "/" + target));
        Map<String, String> outgoing = new LinkedHashMap<>(headers);
        if (requestHeaders != null) {
            outgoing.putAll(requestHeaders);
        }
        outgoing.putIfAbsent("Accept", "application/json, text/html;q=0.9, */*;q=0.8");
        outgoing.put("Authorization", "Bearer " + apiKey);
        outgoing.put("Content-Type", "application/x-www-form-urlencoded");
        outgoing.putIfAbsent("User-Agent", "thordata-java/" + ThorDataConstants.VERSION);
        outgoing.put("Origin", "Java");
        outgoing.put("platform", "java");
        outgoing.put("api-source", "sdk");
        try {
            return transport.send(new HttpRequestData(method.toUpperCase(), uri, outgoing,
                    formBody(form), timeout));
        } catch (HttpTimeoutException exception) {
            throw new ThorDataTimeoutException("ThorData request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ThorDataConnectionException("ThorData request was cancelled", exception);
        } catch (Exception exception) {
            throw new ThorDataConnectionException("Unable to connect to the ThorData API", exception);
        }
    }

    public HttpResponseData request(String method, String path, Map<String, ?> params) {
        return request(method, path, params, Map.of(), true);
    }

    public CompletableFuture<JsonNode> searchAsync(Map<String, ?> params) {
        return searchResponseAsync(params).thenApply(SearchResponse::result);
    }

    public CompletableFuture<JsonNode> searchJsonAsync(Map<String, ?> params) {
        return searchAsync(params);
    }

    public CompletableFuture<SearchResponse> searchResponseAsync(Map<String, ?> params) {
        return serp.searchResponseAsync(params, null);
    }

    public java.util.List<Map<String, Object>> scrape(String spiderId, Map<String, ?> params,
                                                       ScrapeOptions options) {
        return scrapeResponse(spiderId, params, options).result();
    }

    public java.util.List<Map<String, Object>> scrape(String spiderId, Map<String, ?> params) {
        return scrape(spiderId, params, ScrapeOptions.defaults());
    }

    public WebScraperResponse scrapeResponse(String spiderId, Map<String, ?> params,
                                             ScrapeOptions options) {
        if (!syncScrapeLock.tryLock()) {
            throw new ThorDataConfigurationException(
                    "A synchronous scrape is already active on this client; wait briefly and retry");
        }
        try {
            return scraper.run(spiderId, params, options);
        } finally {
            syncScrapeLock.unlock();
        }
    }

    public WebScraperResponse scrapeResponse(String spiderId, Map<String, ?> params) {
        return scrapeResponse(spiderId, params, ScrapeOptions.defaults());
    }

    public CompletableFuture<java.util.List<Map<String, Object>>> scrapeAsync(
            String spiderId, Map<String, ?> params, ScrapeOptions options) {
        CompletableFuture<WebScraperResponse> source = scrapeResponseAsync(spiderId, params, options);
        return mapCancellable(source, WebScraperResponse::result);
    }

    public CompletableFuture<WebScraperResponse> scrapeResponseAsync(
            String spiderId, Map<String, ?> params, ScrapeOptions options) {
        return scraper.runAsync(spiderId, params, options, executor);
    }

    public CompletableFuture<java.util.List<Map<String, Object>>> scrapeAsync(
            String spiderId, Map<String, ?> params) {
        return scrapeAsync(spiderId, params, ScrapeOptions.defaults());
    }

    public CompletableFuture<WebScraperResponse> scrapeResponseAsync(
            String spiderId, Map<String, ?> params) {
        return scrapeResponseAsync(spiderId, params, ScrapeOptions.defaults());
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
        if (ownsTransport) {
            transport.close();
        }
    }

    @Override
    public String toString() {
        return "ThorDataClient{apiUrl='" + apiUrl + "'}";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String formBody(Map<String, String> values) {
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

    private static <T, R> CompletableFuture<R> mapCancellable(
            CompletableFuture<T> source, java.util.function.Function<T, R> mapper) {
        CompletableFuture<R> result = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                source.cancel(mayInterruptIfRunning);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        source.whenComplete((value, failure) -> {
            if (failure == null) {
                result.complete(mapper.apply(value));
            } else if (source.isCancelled()) {
                result.cancel(false);
            } else {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }
}
