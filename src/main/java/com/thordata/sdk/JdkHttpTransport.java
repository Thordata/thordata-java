package com.thordata.sdk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** JDK 17 HttpClient implementation. */
public final class JdkHttpTransport implements HttpTransport {
    private final HttpClient client;

    public JdkHttpTransport() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public JdkHttpTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public HttpResponseData send(HttpRequestData request) throws Exception {
        HttpResponse<String> response = client.send(toRequest(request), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new HttpResponseData(response.statusCode(), response.body(), response.headers().map().entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.join(",", entry.getValue()))));
    }

    @Override
    public CompletableFuture<HttpResponseData> sendAsync(HttpRequestData request) {
        return client.sendAsync(toRequest(request), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new HttpResponseData(response.statusCode(), response.body(), response.headers().map().entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> String.join(",", entry.getValue())))));
    }

    private static HttpRequest toRequest(HttpRequestData request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.uri().toString()))
                .timeout(request.timeout())
                .method(request.method(), HttpRequest.BodyPublishers.ofString(request.body() == null ? "" : request.body(), StandardCharsets.UTF_8));
        request.headers().forEach(builder::header);
        return builder.build();
    }
}
