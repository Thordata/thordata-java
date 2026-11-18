# Thordata Java SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task by task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a Java 17 Maven SDK in `acen-sdk-package/sdk/thordata-java` with the same SERP and Web Scraper behavior as the Python and JavaScript SDKs.

**Architecture:** Keep protocol work in focused components: an injectable HTTP transport, Jackson JSON helpers, SERP request/response handling, Web Scraper validation/lifecycle, cancellation, and a public client exposing blocking and `CompletableFuture` APIs. Preserve dynamic upstream fields as `JsonNode` or `Map<String,Object>` while making configuration, responses, and exceptions strongly typed.

**Tech Stack:** Java 17, Maven, JDK `HttpClient`, Jackson Databind, JUnit 5.

---

## Task 1: Initialize the Maven module and public value types

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/thordata/sdk/ThorDataConstants.java`
- Create: `src/main/java/com/thordata/sdk/SearchResponse.java`
- Create: `src/main/java/com/thordata/sdk/ScrapeOptions.java`
- Create: `src/main/java/com/thordata/sdk/WebScraperResponse.java`
- Create: `src/main/java/com/thordata/sdk/SearchEngine.java`
- Create: `src/main/java/com/thordata/sdk/WebScraperId.java`
- Test: `src/test/java/com/thordata/sdk/ConstantsTest.java`

- [x] Write failing tests for the engine/spider constants, response records, and default scrape options.
- [x] Run `mvn -q -Dtest=ConstantsTest test` and confirm the expected missing-type compilation failure.
- [x] Add Java 17 compiler settings, Jackson, JUnit Jupiter, Surefire, protocol constants, seven spider IDs, 38 SERP engines, and the request allowlist.
- [x] Run `mvn -q -Dtest=ConstantsTest test` and confirm the test passes.
- [x] Commit the foundation with an English commit message.

## Task 2: Implement exceptions, JSON parsing, redaction, cancellation, and HTTP transport

**Files:**
- Create: `src/main/java/com/thordata/sdk/errors/*.java`
- Create: `src/main/java/com/thordata/sdk/JsonSupport.java`
- Create: `src/main/java/com/thordata/sdk/SecretRedactor.java`
- Create: `src/main/java/com/thordata/sdk/CancellationToken.java`
- Create: `src/main/java/com/thordata/sdk/HttpRequestData.java`
- Create: `src/main/java/com/thordata/sdk/HttpResponseData.java`
- Create: `src/main/java/com/thordata/sdk/HttpTransport.java`
- Create: `src/main/java/com/thordata/sdk/JdkHttpTransport.java`
- Test: `src/test/java/com/thordata/sdk/ErrorAndSupportTest.java`

- [x] Write failing tests for error metadata, concatenated JSON arrays, nested redaction, and scraper cancellation state.
- [x] Run the focused test and confirm the expected missing-class compilation failure.
- [x] Implement the exception hierarchy, JSON parsing, redaction, cooperative cancellation token, and JDK `HttpClient` transport.
- [x] Run the focused test and confirm all support tests pass.
- [x] Keep all public and internal documentation in English.

## Task 3: Implement SERP request normalization and response handling

**Files:**
- Create: `src/main/java/com/thordata/sdk/SerpRequestBuilder.java`
- Create: `src/main/java/com/thordata/sdk/SerpClient.java`
- Create: `src/main/java/com/thordata/sdk/ThorDataClientBuilder.java`
- Create: `src/main/java/com/thordata/sdk/ThorDataClient.java`
- Test: `src/test/java/com/thordata/sdk/SerpRequestBuilderTest.java`
- Test: `src/test/java/com/thordata/sdk/SerpClientTest.java`
- Test fixtures: `src/test/resources/contract-fixtures/serp/*.json`

- [x] Write failing tests for aliases, booleans, defaults, Yandex transformation, unknown fields, and unsupported engines.
- [x] Run the focused tests and confirm the expected missing-builder failure.
- [x] Implement allowlist filtering, alias handling, nested JSON serialization, engine validation, Yandex transformation, and default response flags.
- [x] Implement key precedence, ASCII validation, URL validation, positive timeout validation, custom headers, blocking search, async search, raw search, and response metadata.
- [x] Add regression coverage for HTTP error bodies, including message and `responseData` preservation.
- [x] Run all SERP tests and confirm they pass.

## Task 4: Implement Web Scraper schema validation and result parsing

**Files:**
- Create: `src/main/java/com/thordata/sdk/WebScraperDefinition.java`
- Create: `src/main/java/com/thordata/sdk/WebScraperValidator.java`
- Create: `src/main/java/com/thordata/sdk/WebScraperResultParser.java`
- Test: `src/test/java/com/thordata/sdk/WebScraperValidatorTest.java`
- Test: `src/test/java/com/thordata/sdk/WebScraperResultParserTest.java`

- [x] Write failing validation tests for all seven spiders, URL rules, positive integers, Amazon defaults, and the service spelling `flase`.
- [x] Run validation tests and confirm the expected missing-class failure.
- [x] Implement the exact seven schemas, unknown-field rejection, required-field checks, URL validation, integer validation, and option validation.
- [x] Write parser tests for objects, arrays, consecutive arrays, failed records, partial records, empty records, all-failed records, long strings, and redaction.
- [x] Implement result parsing and completed/partial/empty classification.
- [x] Run validation and parser tests and confirm they pass.

## Task 5: Implement the Web Scraper lifecycle, timeout, cancellation, executor injection, and concurrency

**Files:**
- Create: `src/main/java/com/thordata/sdk/WebScraperRunner.java`
- Modify: `src/main/java/com/thordata/sdk/ThorDataClient.java`
- Modify: `src/main/java/com/thordata/sdk/ThorDataClientBuilder.java`
- Test: `src/test/java/com/thordata/sdk/WebScraperLifecycleTest.java`
- Test fixture: `src/test/resources/contract-fixtures/web-scraper/lifecycle.json`

- [x] Write failing lifecycle tests for builder/status/download fields, trusted URLs, task metadata, timeout without resubmission, diagnostics, invalid URLs, and synchronous mutual exclusion.
- [x] Write a failing async cancellation test that asserts no poll occurs after `future.cancel(true)`.
- [x] Write a failing executor ownership test that injects an executor and verifies `ThorDataClient.close()` does not shut it down.
- [x] Run lifecycle tests and confirm the expected cancellation/executor failures before implementation.
- [x] Implement one-time submission, envelope checks, safe task IDs, absolute deadlines, five-second polling, diagnostics, download URL validation, record parsing, and status metadata.
- [x] Link future cancellation to a cooperative token, wake pending poll waits, prevent the next poll, and preserve one submission.
- [x] Add executor injection and close only client-owned executors.
- [x] Make `scrapeAsync` propagate cancellation to its source future.
- [x] Run lifecycle tests and confirm full pass.

## Task 6: Add contract-driven tests, examples, README, CHANGELOG, and Maven metadata

**Files:**
- Create: `README.md`
- Create: `CHANGELOG.md`
- Create: `examples/BasicSearch.java`
- Create: `examples/ScrapeBasic.java`
- Create: `src/test/java/com/thordata/sdk/ContractFixtureTest.java`
- Modify: `pom.xml`
- Modify: `docs/superpowers/specs/2026-11-13-thordata-java-sdk-design.md`

- [x] Write a fixture test that drives the SDK through the SERP envelope and the Web Scraper builder/status/download lifecycle.
- [x] Add Java 17/Maven usage documentation, key precedence, sync/async methods, all seven spider IDs, errors, cancellation, executor ownership, and no-credit checks.
- [x] State clearly that version `0.1.0` is not currently published to Maven Central and that local consumers should run `mvn install`.
- [x] Add MIT license, developer, and SCM metadata to the POM.
- [x] Attach sources and Javadoc JARs during `mvn package`.
- [x] Keep README, CHANGELOG, examples, design documentation, and implementation plans in English.
- [x] Compile both examples and run the fixture test.

## Task 7: Full verification and release readiness

**Files:**
- Modify only files required by verification findings.

- [x] Run `mvn -q clean test` and confirm exit code 0.
- [x] Run `mvn -q package` and confirm exit code 0.
- [x] Verify the main, sources, and Javadoc JARs exist in `target/`.
- [x] Run `git diff --check` and inspect repository status.
- [x] Verify that only Java SDK files were committed; preserve unrelated uncommitted MCP changes.
- [x] Scan the complete SDK directory for CJK characters and remove any remaining non-English documentation.
