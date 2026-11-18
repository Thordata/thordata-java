package com.thordata.sdk;

import java.util.concurrent.CompletableFuture;

/** Injectable HTTP boundary used by production and no-credit tests. */
public interface HttpTransport extends AutoCloseable {
    HttpResponseData send(HttpRequestData request) throws Exception;

    CompletableFuture<HttpResponseData> sendAsync(HttpRequestData request);

    @Override
    default void close() {
    }
}
