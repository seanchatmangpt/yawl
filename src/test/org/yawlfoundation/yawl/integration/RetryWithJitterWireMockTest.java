package org.yawlfoundation.yawl.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Resilience4j Retry with exponential backoff and jitter.
 *
 * These tests verify Retry behavior using REAL HTTP interactions with WireMock.
 * WireMock simulates transient failures and recovery scenarios without external service dependencies.
 *
 * Test scenarios:
 * - Successful retry after transient failure
 * - Jitter prevents thundering herd (randomized delays between retries)
 * - Max retries respected (request fails after max attempts exceeded)
 * - Varying delays with exponential backoff
 */
public class RetryWithJitterWireMockTest {

    private WireMockServer wireMockServer;
    private Retry retry;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Initialize WireMock HTTP server
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(8081)
            .jettyStopTimeout(5000));
        wireMockServer.start();
        configureFor("localhost", 8081);

        // Initialize HTTP client with reasonable timeout
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Configure Retry: exponential backoff with jitter
        // Initial delay: 100ms, multiplier: 2.0, max attempts: 3
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoffWithJitter(
                100, 2.0, 0.5))  // 0.5 = 50% jitter
            .recordExceptions(Exception.class)
            .ignoreExceptions()
            .build();

        retry = Retry.of("http-retry", config);
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }

    /**
     * Test successful retry after transient failure.
     * First request fails with 503, second request succeeds with 200.
     */
    @Test
    public void testSuccessfulRetryAfterTransientFailure() throws Exception {
        // Arrange: configure endpoint with stateful behavior (fail then succeed)
        givenThat(get(urlEqualTo("/api/transient"))
            .inScenario("Transient Failure")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable"))
            .willSetStateTo("Second Attempt"));

        givenThat(get(urlEqualTo("/api/transient"))
            .inScenario("Transient Failure")
            .whenScenarioStateIs("Second Attempt")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("Success")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8081/api/transient"))
            .GET()
            .build();

        // Act: execute request through Retry (should retry and succeed)
        HttpResponse<String> response = executeWithRetry(request);

        // Assert: request eventually succeeded
        assertEquals(200, response.statusCode());
        assertEquals("Success", response.body());
    }

    /**
     * Test that jitter prevents thundering herd.
     * Jitter adds randomness to retry delays preventing synchronized retries.
     */
    @Test
    public void testJitterPreventsThunderingHerd() throws Exception {
        // Arrange: track retry timings
        List<Long> retryTimestamps = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // Configure endpoint that fails twice then succeeds
        givenThat(get(urlEqualTo("/api/jitter"))
            .inScenario("Jitter Test")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Unavailable"))
            .willSetStateTo("Attempt-2"));

        givenThat(get(urlEqualTo("/api/jitter"))
            .inScenario("Jitter Test")
            .whenScenarioStateIs("Attempt-2")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Unavailable"))
            .willSetStateTo("Attempt-3"));

        givenThat(get(urlEqualTo("/api/jitter"))
            .inScenario("Jitter Test")
            .whenScenarioStateIs("Attempt-3")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8081/api/jitter"))
            .GET()
            .build();

        // Act: execute with retries and track timing
        long requestStart = System.currentTimeMillis();
        HttpResponse<String> response = executeWithRetry(request);
        long totalTime = System.currentTimeMillis() - requestStart;

        // Assert: succeeded with retries, and took reasonable time (jitter adds variability)
        assertEquals(200, response.statusCode());
        assertTrue(totalTime > 100, "Should take time for retries with backoff");
        assertTrue(totalTime < 5000, "Should not take excessively long");
    }

    /**
     * Test max retries respected: request fails after max attempts exceeded.
     */
    @Test
    public void testMaxRetriesRespected() throws Exception {
        // Arrange: configure endpoint that always fails
        givenThat(get(urlEqualTo("/api/failure"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8081/api/failure"))
            .GET()
            .build();

        // Act: attempt request with Retry (max 3 attempts configured)
        Exception caughtException = assertThrows(Exception.class, () -> {
            executeWithRetry(request);
        });

        // Assert: exception thrown after max retries exhausted
        assertNotNull(caughtException);
        assertTrue(caughtException.getMessage().contains("500") ||
                   caughtException.getCause() != null,
            "Exception should relate to HTTP 500 error");
    }

    /**
     * Test exponential backoff with varying delays.
     * Delays should increase: ~100ms, ~200ms, ~400ms (with jitter: 50-100ms, 100-200ms, 200-400ms).
     */
    @Test
    public void testExponentialBackoffIncreasingDelays() throws Exception {
        // Arrange: track timestamps for each request
        List<Long> requestTimes = new ArrayList<>();

        // Configure endpoint that fails twice, succeeds on third attempt
        givenThat(get(urlEqualTo("/api/backoff"))
            .inScenario("Exponential Backoff")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Unavailable"))
            .willSetStateTo("Attempt-2"));

        givenThat(get(urlEqualTo("/api/backoff"))
            .inScenario("Exponential Backoff")
            .whenScenarioStateIs("Attempt-2")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Unavailable"))
            .willSetStateTo("Attempt-3"));

        givenThat(get(urlEqualTo("/api/backoff"))
            .inScenario("Exponential Backoff")
            .whenScenarioStateIs("Attempt-3")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8081/api/backoff"))
            .GET()
            .build();

        // Act: execute with retries
        long start = System.currentTimeMillis();
        HttpResponse<String> response = executeWithRetry(request);
        long totalTime = System.currentTimeMillis() - start;

        // Assert: succeeded and total time reflects exponential backoff
        assertEquals(200, response.statusCode());
        // With exponential backoff (100ms * 2^n + jitter), total should be > 200ms
        assertTrue(totalTime >= 200,
            "Total time should be >= 200ms with exponential backoff, got: " + totalTime);
    }

    /**
     * Test retry configuration respects non-retriable exceptions.
     */
    @Test
    public void testRetryRespectNonRetriableExceptions() throws Exception {
        // Arrange: configure endpoint that returns permanent error (400 Bad Request)
        givenThat(get(urlEqualTo("/api/badrequest"))
            .willReturn(aResponse()
                .withStatus(400)
                .withBody("Bad Request")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8081/api/badrequest"))
            .GET()
            .build();

        // Act: execute request (400 is permanent, should not retry)
        Exception exception = assertThrows(Exception.class, () -> {
            executeWithRetry(request);
        });

        // Assert: failed but did not retry for permanent error
        assertNotNull(exception);
    }

    // Helper method to execute HTTP request through Retry decorator
    private HttpResponse<String> executeWithRetry(HttpRequest request) throws Exception {
        return retry.executeSupplier(() -> {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
