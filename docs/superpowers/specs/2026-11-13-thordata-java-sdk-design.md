# Thordata Java SDK Design and Development Document

> Status: Design approved and implemented
>
> Target directory: `acen-sdk-package/sdk/thordata-java`
>
> Baseline: Java 17, Maven, JDK `HttpClient`

## 1. Background and goals

The Java SDK directory started empty. The Python and JavaScript SDKs already provide the same observable Thordata behavior: SERP search, seven Web Scraper products, business error classification, task metadata, timeout and polling, result parsing, and secret redaction. The Java SDK must preserve those behaviors for the same request and service response.

The implementation goals are:

- Provide a publishable Maven artifact and a stable `com.thordata.sdk` public API.
- Preserve dynamic SERP fields instead of hard-coding result models for each engine.
- Provide blocking and `CompletableFuture` APIs.
- Match the Python and JavaScript request fields, endpoints, errors, timeouts, polling, download URL validation, and redaction rules.
- Use shared contract fixtures and no-credit HTTP tests to verify protocol compatibility.

This scope excludes engine-specific business DTOs, retries, caching, proxy pools, batch scraping, and server-side SDK features.

## 2. Confirmed cross-language protocol

### 2.1 SERP

- Default endpoint: `https://scraperapi.thordata.com/request`.
- Method: `POST` with `application/x-www-form-urlencoded` data.
- Authentication: `Authorization: Bearer <apiKey>`.
- Java source headers: `Origin: Java`, `platform: java`, and `api-source: sdk`.
- `json` and `isjson` default to `1`.
- Booleans encode as `1` or `0`; maps and lists encode as compact JSON; null and empty values are dropped.
- Unknown fields are dropped; `query/country/language` map to `q/gl/hl`.
- Engine aliases: `google_search -> google` and `google_places -> google_local`.
- Yandex uses `text`; when only `q` is supplied, copy it to `text` and remove `q`.
- `google_webpage` and `webpage` are Web Scraper engines and must be rejected by SERP calls.
- `searchResponse` retains `result`, `taskId`, and the original envelope. Bare SERP results use `search_metadata.id` as the fallback task ID.
- `rawSearch` returns the unparsed response text.

The supported engine set is:

```text
google, google_search, google_light, google_web, google_images, google_videos,
google_news, google_shopping, google_local, google_places, google_product,
google_lens, google_trends, google_maps, google_hotels, google_jobs,
google_scholar, google_scholar_cite, google_scholar_author, google_play,
google_play_product, google_play_games, google_play_movies, google_play_books,
google_finance, google_finance_markets, google_flights, google_patents,
google_patents_details, google_ai_mode, bing, bing_images, bing_videos,
bing_news, bing_shopping, bing_maps, yandex, duckduckgo
```

The request allowlist is the union of the Python and JavaScript allowlists and is locked by contract tests. It covers engine, query, locale, response flags, URL, pagination, travel, shopping, image, patent, map, and finance fields.

### 2.2 Web Scraper

| Stage | URL | Method | Authentication |
| --- | --- | --- | --- |
| Submit | `https://scraperapi.thordata.com/builder` | POST | `Authorization: Bearer <apiKey>` |
| Poll | `https://api.thordata.com/api/web-scraper-api/tasks-status` | POST | `sign: <apiKey>` |
| Get download address | `https://api.thordata.com/api/web-scraper-api/tasks-download` | POST | `sign: <apiKey>` |
| Download results | Trusted `https://scraperapi.thordata.com/download` URL returned by the service | GET | Signed URL parameters |

The builder form always contains `spider_name`, `spider_id`, `spider_parameters` as a single-element JSON array, `spider_errors`, `file_name`, `api_source=sdk`, and `source_platform=java`. The status and tasks-download forms use `tasks_ids` or `task_id`, `type=json` where applicable, `api_source=sdk`, and `source_platform=java`.

Supported spiders and parameters:

| Spider ID | `spider_name` | Parameters | Rules |
| --- | --- | --- | --- |
| `linkedin_company_information_by-url` | `linkedin.com` | `url` | Required HTTP/HTTPS URL |
| `linkedin_job_listings_information_by-job-url` | `linkedin.com` | `job_url` | Required HTTP/HTTPS URL |
| `youtube_video-post_by-keyword` | `youtube.com` | `keyword`, `num_of_posts` | Non-empty keyword; positive integer sent as a string |
| `facebook_profile_by-profiles-url` | `facebook.com` | `url` | Required HTTP/HTTPS URL |
| `youtube_video-post_by-explore` | `youtube.com` | `url`, `all_tabs` | Required URL; boolean uses the service value `true`/`flase` |
| `twitter_profile_by-profileurl` | `x.com` | `url` | Required HTTP/HTTPS URL |
| `amazon_product_by-category-url` | `amazon.com` | `url`, `page_turning`, `sort_by` | URL and positive integer required; default sort is `Best Sellers` |

`sort_by` accepts `Best Sellers`, `Newest Arrivals`, `Avg. Customer Review`, `Price: High to Low`, `Price: Low to High`, and `Featured`. Unknown fields, missing required fields, userinfo-bearing URLs, and non-HTTP(S) URLs are rejected before transport.

### 2.3 Web Scraper lifecycle

1. Validate the spider, parameters, `maxWaitSeconds`, `includeErrors`, and `fileName`; create an absolute deadline.
2. Submit exactly one builder request and require `code=200`, a valid `data.task_id`, and a safe task ID.
3. Poll every five seconds until `Running`, `Ready`, or `Failed`; the default total budget is 300 seconds and the accepted range is 1–300 seconds.
4. When the task is `Failed`, optionally download diagnostic records when `includeErrors=true`; diagnostic failures must preserve the original task failure and must never resubmit.
5. When the task is `Ready`, request tasks-download and require `tasks_ids` to match the task ID.
6. Only access a trusted download address: scheme, host, and path must match the fixed endpoint; userinfo, fragments, duplicate or unknown query keys are rejected; `task_id` and `type=json` must match.
7. Parse one JSON document or consecutive JSON arrays into records. All failed records produce `scrape_failed`; mixed records produce `partial`; records without business fields produce `empty`; other successful results produce `completed`.
8. Preserve submission, final status, and download-stage metadata in `raw`, with API keys, signed URLs, and download keys redacted.

A client permits one active synchronous scrape. An asynchronous scrape can run concurrently. A cancelled future signals the active lifecycle and prevents the next poll.

## 3. Public API design

### 3.1 Client configuration

```java
try (ThorDataClient client = ThorDataClient.builder()
        .apiKey(System.getenv("THORDATA_API_KEY"))
        .timeout(Duration.ofSeconds(30))
        .build()) {
    SearchResponse response = client.searchResponse(Map.of(
        "engine", "google_search",
        "q", "latest AI agent research",
        "gl", "us",
        "hl", "en"
    ));
    JsonNode result = response.result();
}
```

Builder fields are `apiKey`, JavaScript-compatible `apiToken` and `token`, `apiUrl`, `baseUrl`, `timeout`, custom headers, an optional `HttpClient`, and an optional executor. Key precedence is explicit `apiKey`, `apiToken`, `token`, then `THORDATA_API_KEY`, `THORDATA_API_TOKEN`, and `TALORDATA_API_TOKEN`. Keys must be non-empty ASCII; URLs must use HTTP or HTTPS; timeout must be positive. Injected executors remain owned by the caller.

### 3.2 Search and scrape methods

```java
JsonNode search(Map<String, ?> params);
SearchResponse searchResponse(Map<String, ?> params);
String rawSearch(Map<String, ?> params);

List<Map<String, Object>> scrape(String spiderId, Map<String, ?> params,
                                 ScrapeOptions options);
WebScraperResponse scrapeResponse(String spiderId, Map<String, ?> params,
                                  ScrapeOptions options);

CompletableFuture<JsonNode> searchAsync(Map<String, ?> params);
CompletableFuture<SearchResponse> searchResponseAsync(Map<String, ?> params);
CompletableFuture<List<Map<String, Object>>> scrapeAsync(
    String spiderId, Map<String, ?> params, ScrapeOptions options);
CompletableFuture<WebScraperResponse> scrapeResponseAsync(
    String spiderId, Map<String, ?> params, ScrapeOptions options);

HttpResponseData request(String method, String path, Map<String, ?> params,
                         Map<String, String> headers, boolean filterParams);
```

Overloads accept an explicit engine, which takes precedence over the map value. `ScrapeOptions` contains the 300-second default budget, `includeErrors=true`, and `fileName="{{TasksID}}"`. Returned scrape futures are cancellable and stop the next poll.

Response types:

```java
public record SearchResponse(JsonNode result, String taskId, JsonNode raw) {}

public record WebScraperResponse(
    List<Map<String, Object>> result,
    String taskId,
    String spiderId,
    Status status,
    int recordCount,
    int errorRecordCount,
    JsonNode raw
) {
    public enum Status { COMPLETED, PARTIAL, EMPTY }
}
```

## 4. Layered architecture and file structure

```text
thordata-java/
├─ pom.xml
├─ README.md
├─ CHANGELOG.md
├─ LICENSE
├─ examples/
│  ├─ BasicSearch.java
│  └─ ScrapeBasic.java
├─ src/main/java/com/thordata/sdk/
│  ├─ ThorDataClient.java
│  ├─ ThorDataClientBuilder.java
│  ├─ ThorDataConstants.java
│  ├─ SearchResponse.java
│  ├─ ScrapeOptions.java
│  ├─ WebScraperResponse.java
│  ├─ SearchEngine.java
│  ├─ WebScraperId.java
│  ├─ WebScraperDefinition.java
│  ├─ SerpRequestBuilder.java
│  ├─ WebScraperRunner.java
│  ├─ WebScraperValidator.java
│  ├─ JsonSupport.java
│  ├─ SecretRedactor.java
│  ├─ CancellationToken.java
│  ├─ HttpTransport.java
│  ├─ JdkHttpTransport.java
│  └─ errors/
└─ src/test/java/com/thordata/sdk/
```

Responsibilities:

- `ThorDataClient` owns public APIs, configuration, and lifecycle; it does not assemble protocol details directly.
- `HttpTransport` is the sync/async HTTP boundary. Production uses JDK `HttpClient`; tests use in-memory response queues.
- `SerpRequestBuilder` handles the allowlist, aliases, value encoding, and engine validation.
- `WebScraperValidator` owns the seven spider schemas; `WebScraperRunner` owns lifecycle, polling, deadlines, cancellation, and result status.
- `JsonSupport` handles nested JSON, consecutive JSON arrays, and business envelopes.
- `SecretRedactor` recursively redacts strings, maps, lists, `JsonNode` values, and exception details.

## 5. Error model

```text
ThorDataException
├─ ThorDataConfigurationException
├─ ThorDataConnectionException
│  └─ ThorDataTimeoutException
├─ ThorDataApiException
│  ├─ ThorDataAuthenticationException
│  ├─ ThorDataPermissionException
│  ├─ ThorDataRateLimitException
│  ├─ ThorDataInvalidRequestException
│  └─ ThorDataNotCollectedException
└─ ThorDataWebScraperException
```

`ThorDataApiException` retains `Integer code`, `Integer statusCode`, and `JsonNode responseData`. SERP code mapping is: `300` not collected, `401` authentication, `402` permission, `429` rate limit, and `400` invalid request. Code `0` with a message containing `Collection failed` maps to not collected; other unknown codes use the base API exception.

`ThorDataWebScraperException` retains `errorCode`, `stage`, safe `taskId`, `spiderId`, `reason`, and `serviceCode`. Supported error codes include `invalid_parameters`, `submission_unknown`, `submission_failed`, `poll_failed`, `scrape_failed`, `download_url_invalid`, `download_failed`, `invalid_result`, `wait_timeout`, `protocol_error`, `authentication_failed`, `permission_denied`, `insufficient_balance`, and `request_failed`.

Exception messages, `responseData`, `raw`, and records are redacted before exposure. API keys, download `api_key` values, and signatures must never appear in diagnostics.

## 6. Async, timeout, and resource management

- SERP blocking calls use `HttpClient.send`; async calls use `sendAsync`.
- Web Scraper async work runs on the configured executor and uses a cooperative cancellation token to stop the next poll.
- `CompletableFuture.cancel(true)` cancels the active lifecycle and maps cancellation to a request failure without resubmission.
- `ThorDataClient` implements `AutoCloseable`; `close()` shuts down only a client-owned executor, transport, or other resource.
- A client reuses its `HttpClient` to avoid creating a new connection pool for each async call.
- A single absolute deadline covers response headers, response bodies, parsing, and polling.

## 7. Testing and acceptance criteria

Tests must not access the live service or consume credits. All HTTP behavior uses injected `HttpTransport` fakes and shared contract fixtures.

Coverage must include:

1. SERP success, not-collected, and collection-failed envelopes from shared fixtures.
2. Engine aliases, parameter aliases, booleans, unknown fields, default response flags, and engine errors.
3. SERP HTTP errors with response bodies, business errors, nested JSON strings, bare-result task IDs, and raw bodies.
4. All seven spider schemas, unknown fields, URL/integer/option validation, Amazon defaults, and `all_tabs=flase`.
5. The builder → Running → Ready → tasks-download → download lifecycle and every relevant form/header field.
6. Failed-task diagnostics, timeout without resubmission, deadline boundaries, cancellation, invalid task IDs, and untrusted download URLs.
7. Single JSON documents, consecutive arrays, all-failed records, partial records, empty records, successful records, and long strings/newlines.
8. Redaction of API keys, download keys, signed URLs, nested objects, exceptions, and raw data.
9. Client close, executor ownership, async concurrency, cancellation, and synchronous per-client mutual exclusion.

Verification commands:

```text
mvn -q clean test
mvn -q package
```

The release check also verifies the main JAR, sources JAR, Javadoc JAR, README examples, CHANGELOG, and Maven metadata.

## 8. Implementation order

1. Initialize Maven, Jackson, JUnit 5, Java 17 compilation, and shared constants.
2. Implement configuration, HTTP transport, JSON support, redaction, cancellation, and exception types.
3. Implement SERP request building, blocking/async calls, response parsing, and raw access.
4. Implement spider definitions, validation, the builder/status/download lifecycle, and result status.
5. Add cancellation, timeout, executor ownership, synchronous mutual exclusion, and concurrency tests.
6. Wire contract fixtures and complete README, examples, CHANGELOG, and Maven release metadata.
7. Run the complete verification suite and compare fields and errors with the Python and JavaScript SDKs.

## 9. Risks and mitigations

- **Dynamic upstream fields:** Preserve raw structures as Jackson `JsonNode` values instead of generating engine-specific DTOs.
- **Java cancellation semantics:** Use a future-linked cancellation token and test that cancellation never submits a second task or performs another poll.
- **Malformed service JSON:** Support consecutive JSON arrays while returning explicit `invalid_result` or `protocol_error` failures for incompatible shapes.
- **Security regressions:** Test redaction independently and never concatenate raw service messages or signed URLs before redaction.
- **Cross-language field drift:** Treat the Python/JavaScript allowlist union as the source for Java contract tests and release notes.
