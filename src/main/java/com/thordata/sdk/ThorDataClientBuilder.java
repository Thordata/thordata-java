package com.thordata.sdk;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** Fluent configuration builder for {@link ThorDataClient}. */
public final class ThorDataClientBuilder {
    String apiKey;
    String apiToken;
    String token;
    String apiUrl;
    String baseUrl;
    Duration timeout = ThorDataConstants.DEFAULT_TIMEOUT;
    Map<String, String> headers = new LinkedHashMap<>();
    HttpTransport transport;
    HttpClient httpClient;
    Executor executor;

    public ThorDataClientBuilder apiKey(String value) {
        this.apiKey = value;
        return this;
    }

    public ThorDataClientBuilder apiToken(String value) {
        this.apiToken = value;
        return this;
    }

    public ThorDataClientBuilder token(String value) {
        this.token = value;
        return this;
    }

    public ThorDataClientBuilder apiUrl(String value) {
        this.apiUrl = value;
        return this;
    }

    public ThorDataClientBuilder baseUrl(String value) {
        this.baseUrl = value;
        return this;
    }

    public ThorDataClientBuilder timeout(Duration value) {
        this.timeout = value;
        return this;
    }

    public ThorDataClientBuilder headers(Map<String, String> value) {
        this.headers = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        return this;
    }

    public ThorDataClientBuilder header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public ThorDataClientBuilder transport(HttpTransport value) {
        this.transport = value;
        return this;
    }

    public ThorDataClientBuilder httpClient(HttpClient value) {
        this.httpClient = value;
        return this;
    }

    public ThorDataClientBuilder executor(Executor value) {
        this.executor = value;
        return this;
    }

    public ThorDataClient build() {
        return new ThorDataClient(this);
    }
}
