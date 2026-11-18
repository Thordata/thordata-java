# Thordata Java SDK

Java 17 client for the Thordata SERP API and seven verified Web Scraper products. The SDK uses the JDK `HttpClient`, Jackson for dynamic JSON payloads, and Maven for packaging.

Bring fresh web data into Java applications without building a separate integration for every search or scraping workflow. Thordata Java SDK gives engineering teams one client for live SERP results and structured web data, supporting research, monitoring, enrichment, RAG, and AI agent applications.

Use it to track search visibility, enrich business and product data, monitor markets, or feed structured web results into the Java services and data pipelines your team already operates.

## Why Thordata Java SDK

- **One client for search and structured web data**: Query live search engines and collect records through ready-to-use Web Scraper actions.
- **Built for modern Java services**: Use Java 17, the JDK HTTP client, and familiar Maven workflows in backend applications and scheduled jobs.
- **Synchronous and asynchronous workflows**: Choose direct calls or cancellable `CompletableFuture` operations for the workload at hand.
- **Ready for downstream systems**: Pass structured results into analytics, enrichment, RAG, agents, and internal applications.
- **Production-minded handling**: Use typed errors, redacted diagnostics, bounded polling, and validated downloads in real data workflows.

Create a Thordata account to get started:

- [Get started with Thordata](https://www.thordata.com/?ls=github&lk=thordata)
- [Read the official documentation](https://doc.thordata.com)

## Install

Version `0.1.0` is currently not published to Maven Central. Install it into your local Maven repository first:

```text
mvn install
```

After the artifact is published, use this dependency:

```xml
<dependency>
    <groupId>com.thordata</groupId>
    <artifactId>thordata-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

Set `THORDATA_API_KEY`, `THORDATA_API_TOKEN`, or the compatible `TALORDATA_API_TOKEN` environment variable. Explicit `apiKey`, `apiToken`, or `token` builder values take precedence.

## SERP

```java
try (ThorDataClient client = ThorDataClient.builder().build()) {
    SearchResponse response = client.searchResponse(Map.of(
            "engine", "google_search",
            "q", "latest AI agent research",
            "gl", "us",
            "hl", "en"));
    System.out.println(response.result());
}
```

Requests are form-encoded POST calls to `https://scraperapi.thordata.com/request` with Bearer authentication. Engine aliases, parameter aliases, JSON defaults, raw response access, business error mapping, and task ID fallback match the Python and JavaScript SDKs.

Use `searchAsync` or `searchResponseAsync` for `CompletableFuture` based calls. SERP fields remain Jackson `JsonNode` values so engine-specific fields are preserved.

## Web Scraper

```java
try (ThorDataClient client = ThorDataClient.builder().build()) {
    List<Map<String, Object>> records = client.scrape(
            "linkedin_company_information_by-url",
            Map.of("url", "https://www.linkedin.com/company/microsoft"));
    System.out.println(records);
}
```

The supported IDs are:

- `linkedin_company_information_by-url`: `url`
- `linkedin_job_listings_information_by-job-url`: `job_url`
- `youtube_video-post_by-keyword`: `keyword`, optional `num_of_posts`
- `facebook_profile_by-profiles-url`: `url`
- `youtube_video-post_by-explore`: `url`, optional `all_tabs`
- `twitter_profile_by-profileurl`: `url`
- `amazon_product_by-category-url`: `url`, `page_turning`, optional `sort_by`

The client submits once, polls every five seconds, downloads the final JSON records, validates the signed download URL, and returns `completed`, `partial`, or `empty` status metadata through `scrapeResponse`. The default wait budget is 300 seconds and can be changed with `new ScrapeOptions(maxWaitSeconds, includeErrors, fileName)`.

`scrapeResponseAsync` and `scrapeAsync` return cancellable `CompletableFuture` instances; cancelling one stops the next poll. Applications can provide an executor with `ThorDataClient.builder().executor(executor)` and retain ownership of that executor.

## Errors and local checks

Errors are typed as configuration, connection, timeout, API, authentication, permission, rate-limit, invalid-request, not-collected, or stage-aware Web Scraper exceptions. API keys and signed URLs are redacted from diagnostics and returned records.

```text
mvn -q test
mvn -q -DskipTests package
```

Tests use fake HTTP transports and contract fixtures; they do not consume Thordata credits. See `examples/` for runnable entry points. Maintainers can follow [RELEASING.md](RELEASING.md) to publish to Maven Central.

## Start Building

Start with a SERP query for live web intelligence, add a Web Scraper action when you need structured records, and connect the results to your Java services or data pipelines. The same client can support a focused prototype and a repeatable production workflow.

[Explore Thordata SERP API](https://www.thordata.com/products/serp-api?op=flora&from=github) | [Explore Thordata Web Scraper](https://www.thordata.com/products/web-scraper?op=flora&from=github)
