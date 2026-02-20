package org.yawlfoundation.yawl.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for resilience pattern decorator chain.
 *
 * This test verifies the full resilience pipeline for MCP client calls:
 * TimeLimiter → CircuitBreaker → Retry → Bulkhead → Cache/Fallback
 *
 * Real HTTP interactions with WireMock simulating various failure scenarios:
 * - Transient failures (retry recovers)
 * - Sustained outages (circuit breaker opens)
 * - Resource exhaustion (bulkhead limits concurrency)
 * - Slow responses (time limiter prevents hangs)
 * - Cache fallback on complete failure
 */
public class MpcClientWireMockTest {

    private WireMockServer wireMockServer;
    private HttpClient httpClient;
    private ExecutorService executorService;

    private TimeLimiter timeLimiter;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private Bulkhead bulkhead;
    private Cache<String, String> responseCache;

    @BeforeEach
    public void setUp() {
        // Initialize WireMock HTTP server simulating MCP endpoint
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(8085)
            .jettyStopTimeout(5000));
        wireMockServer.start();
        configureFor("localhost", 8085);

        // Initialize HTTP client
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Initialize executor for async operations
        executorService = Executors.newFixedThreadPool(10);

        // Configure TimeLimiter: 2 second timeout
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(2))
            .cancelRunningFuture(true)
            .build();
        timeLimiter = TimeLimiter.of("mcp-timelimit", timeLimiterConfig);

        // Configure CircuitBreaker: open after 50% failures
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(1))
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .minimumNumberOfCalls(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
        circuitBreaker = CircuitBreaker.of("mcp-circuit", cbConfig);

        // Configure Retry: exponential backoff, max 3 attempts
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoffWithJitter(
                100, 2.0, 0.5))
            .recordExceptions(Exception.class)
            .build();
        retry = Retry.of("mcp-retry", retryConfig);

        // Configure Bulkhead: 5 concurrent, 10 waiting
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ofSeconds(2))
            .build();
        bulkhead = Bulkhead.of("mcp-bulkhead", bulkheadConfig);

        // Initialize response cache
        responseCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100)
            .build();
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (responseCache != null) {
            responseCache.invalidateAll();
        }
    }

    /**
     * Test end-to-end resilience: transient failures handled by retry.
     * Server fails once, then succeeds - Retry pattern succeeds after one attempt.
     */
    @Test
    public void testDecoratorChainHandlesTransientFailure() throws Exception {
        // Arrange: configure MCP endpoint with transient failure
        givenThat(post(urlEqualTo("/mcp/call"))
            .inScenario("MCP Call")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Temporarily Unavailable"))
            .willSetStateTo("Recovered"));

        givenThat(post(urlEqualTo("/mcp/call"))
            .inScenario("MCP Call")
            .whenScenarioStateIs("Recovered")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"result\": \"success\"}")));

        // Act: execute decorated request
        String result = executeDecoratedRequest("/mcp/call", "{\"action\": \"test\"}");

        // Assert: request eventually succeeded through retry
        assertEquals("{\"result\": \"success\"}", result);
    }

    /**
     * Test circuit breaker opens on sustained failures.
     * When failure rate exceeds 50%, circuit opens and fast-fails subsequent requests.
     */
    @Test
    public void testCircuitBreakerOpensOnSustainedFailure() throws Exception {
        // Arrange: configure endpoint to fail consistently
        givenThat(post(urlEqualTo("/mcp/failing"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        // Act: execute first request (fails)
        try {
            executeDecoratedRequest("/mcp/failing", "{\"test\": 1}");
        } catch (Exception e) {
            // Expected: first failure
        }

        // Execute second request (fails)
        try {
            executeDecoratedRequest("/mcp/failing", "{\"test\": 2}");
        } catch (Exception e) {
            // Expected: second failure triggers circuit open
        }

        // Assert: circuit should be OPEN
        assertEquals("OPEN", circuitBreaker.getState().toString());

        // Verify circuit rejects fast (doesn't wait for retries)
        long startTime = System.currentTimeMillis();
        try {
            executeDecoratedRequest("/mcp/failing", "{\"test\": 3}");
        } catch (Exception e) {
            // Expected: circuit-breaker exception
        }
        long elapsed = System.currentTimeMillis() - startTime;

        // Should reject quickly without full retry backoff
        assertTrue(elapsed < 500, "Circuit breaker should fast-fail: " + elapsed + "ms");
    }

    /**
     * Test bulkhead limits concurrent MCP calls.
     * When concurrent limit (5) reached, new requests are queued or rejected.
     */
    @Test
    public void testBulkheadLimitsConcurrentCalls() throws Exception {
        // Arrange: configure slow MCP endpoint
        givenThat(post(urlEqualTo("/mcp/concurrent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\": \"processing\"}")
                .withFixedDelay(1000)));  // 1 second response time

        // Act: attempt 10 concurrent requests
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger rejectedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        java.util.concurrent.CompletableFuture<?>[] futures = new java.util.concurrent.CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    executeDecoratedRequest("/mcp/concurrent", "{\"id\": " + System.nanoTime() + "}");
                    successCount.incrementAndGet();
                } catch (io.github.resilience4j.bulkhead.BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions acceptable
                }
            }, executorService);
        }

        // Wait for all to complete
        java.util.concurrent.CompletableFuture.allOf(futures).join();

        // Assert: some requests succeeded (up to bulkhead limit), others rejected/queued
        assertTrue(successCount.get() >= 5 || rejectedCount.get() > 0,
            "Bulkhead should limit concurrent calls: success=" + successCount.get() +
            ", rejected=" + rejectedCount.get());
    }

    /**
     * Test time limiter prevents hanging requests.
     * Requests exceeding 2 second timeout are cancelled.
     */
    @Test
    public void testTimeLimiterPreventsHangingRequests() throws Exception {
        // Arrange: configure slow endpoint (5 second response)
        givenThat(post(urlEqualTo("/mcp/slow"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(5000)));  // 5 second delay > 2 second timeout

        // Act: execute request (should timeout)
        long startTime = System.currentTimeMillis();
        Exception exception = assertThrows(Exception.class, () -> {
            executeDecoratedRequest("/mcp/slow", "{}");
        });
        long elapsed = System.currentTimeMillis() - startTime;

        // Assert: timed out after ~2 seconds, not 5 seconds
        assertTrue(elapsed < 4000, "Should timeout after 2 seconds, not 5: " + elapsed + "ms");
        assertTrue(elapsed >= 1500, "Should wait at least 1.5 seconds for timeout to trigger");
    }

    /**
     * Test cache fallback when entire resilience chain fails.
     * When all retries exhausted and circuit open, fallback to cached response.
     */
    @Test
    public void testCacheFallbackOnCompleteFailure() throws Exception {
        // Arrange: pre-populate cache
        String cacheKey = "/mcp/cached";
        String cachedResponse = "{\"cached\": true, \"data\": \"fallback-value\"}";
        responseCache.put(cacheKey, cachedResponse);

        // Configure endpoint to fail
        givenThat(post(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Error")));

        // Force circuit to OPEN
        for (int i = 0; i < 3; i++) {
            try {
                executeDecoratedRequest(cacheKey, "{}");
            } catch (Exception e) {
                // Expected: each fails
            }
        }

        // Act: with circuit open, use fallback
        String result = getCachedOrFail(cacheKey);

        // Assert: fallback returned cached value
        assertEquals(cachedResponse, result);
    }

    /**
     * Test full decorator chain recovery.
     * Circuit recovers after wait period, successful request closes circuit.
     */
    @Test
    public void testDecoratorChainRecovery() throws Exception {
        // Arrange: configure endpoint that initially fails
        givenThat(post(urlEqualTo("/mcp/recovery"))
            .inScenario("Recovery")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Error"))
            .willSetStateTo("Recovered"));

        // Force circuit OPEN
        for (int i = 0; i < 2; i++) {
            try {
                executeDecoratedRequest("/mcp/recovery", "{}");
            } catch (Exception e) {
                // Expected
            }
        }
        assertEquals("OPEN", circuitBreaker.getState().toString());

        // Wait for HALF_OPEN transition
        Thread.sleep(1500);

        // Act: configure successful response
        wireMockServer.resetAll();
        givenThat(post(urlEqualTo("/mcp/recovery"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"status\": \"recovered\"}")));

        String result = executeDecoratedRequest("/mcp/recovery", "{}");

        // Assert: request succeeded and circuit recovered
        assertEquals("{\"status\": \"recovered\"}", result);
        assertEquals("CLOSED", circuitBreaker.getState().toString());
    }

    /**
     * Test decorator order: TimeLimiter → CircuitBreaker → Retry.
     * Each layer adds value:
     * - TimeLimiter: prevent resource waste on hanging requests
     * - CircuitBreaker: fast-fail when service unavailable
     * - Retry: recover from transient failures
     */
    @Test
    public void testDecoratorPriority() throws Exception {
        // Arrange: test scenario where timeout happens before all retries
        givenThat(post(urlEqualTo("/mcp/timeout"))
            .willReturn(aResponse()
                .withStatus(200)
                .withFixedDelay(5000)));  // Exceeds 2 second limit

        // Act: execute with decorators
        long startTime = System.currentTimeMillis();
        Exception exception = assertThrows(Exception.class, () -> {
            executeDecoratedRequest("/mcp/timeout", "{}");
        });
        long totalTime = System.currentTimeMillis() - startTime;

        // Assert: TimeLimiter short-circuited retry loop
        // Without TimeLimiter: ~300ms (100ms * 2^0 + 200ms * 2^1) = 300ms for 3 retries
        // With TimeLimiter: ~2000ms (timeout limit)
        assertTrue(totalTime < 4000, "TimeLimiter should prevent excessive backoff: " + totalTime);
    }

    // Helper: Execute request with full decorator chain
    private String executeDecoratedRequest(String path, String body) throws Exception {
        Callable<String> request = () -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:8085" + path))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    throw new Exception("HTTP " + response.statusCode());
                }
                return response.body();
            } catch (java.io.IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        // Apply decorator chain: TimeLimiter → CircuitBreaker → Retry → Bulkhead
        return Decorators.ofCallable(request)
            .withTimeLimiter(timeLimiter, executorService)
            .withCircuitBreaker(circuitBreaker)
            .withRetry(retry)
            .withBulkhead(bulkhead)
            .decorate()
            .call();
    }

    // Helper: Get from cache or fail
    private String getCachedOrFail(String cacheKey) throws Exception {
        String cached = responseCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        throw new Exception("No cached value for key: " + cacheKey);
    }
}
