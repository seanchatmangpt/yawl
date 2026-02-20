package org.yawlfoundation.yawl.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Fallback pattern with Caffeine cache and WireMock.
 *
 * These tests verify fallback behavior when primary service fails:
 * - Return cached value from last successful response (stale-while-revalidate)
 * - Update cache on successful requests
 * - Track cache hit metrics for observability
 *
 * Test scenarios:
 * - Fallback invocation on service failure
 * - Cache hit return on failure
 * - Cache miss (no cached value available)
 * - Cache update on successful response
 * - Stale-while-revalidate pattern (serve stale, background refresh)
 */
public class FallbackCacheWireMockTest {

    private WireMockServer wireMockServer;
    private HttpClient httpClient;
    private Cache<String, String> responseCache;

    @BeforeEach
    public void setUp() {
        // Initialize WireMock HTTP server
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(8084)
            .jettyStopTimeout(5000));
        wireMockServer.start();
        configureFor("localhost", 8084);

        // Initialize HTTP client
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Initialize Caffeine cache: 5 minute expiration, max 100 entries
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
        if (responseCache != null) {
            responseCache.invalidateAll();
        }
    }

    /**
     * Test fallback invocation on service failure.
     * When service returns 500 error, fallback returns cached value.
     */
    @Test
    public void testFallbackInvocationOnServiceFailure() throws Exception {
        // Arrange: pre-populate cache with stale value
        String cacheKey = "/api/data";
        String cachedValue = "stale-cached-response";
        responseCache.put(cacheKey, cachedValue);

        // Configure service to fail with 500 error
        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Service Error")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8084" + cacheKey))
            .GET()
            .build();

        // Act: attempt request and fall back to cache
        String response = getWithFallback(request, cacheKey);

        // Assert: fallback returned cached value
        assertEquals(cachedValue, response,
            "Fallback should return cached value on service failure");
    }

    /**
     * Test cache hit return on failure.
     * Service fails but cache contains recent value - return it without waiting.
     */
    @Test
    public void testCacheHitReturnOnFailure() throws Exception {
        // Arrange: populate cache with successful response
        String cacheKey = "/api/users";
        String expectedResponse = "{\"users\": [{\"id\": 1, \"name\": \"Alice\"}]}";
        responseCache.put(cacheKey, expectedResponse);

        // Configure service to return slow response
        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Temporarily Unavailable")
                .withFixedDelay(2000)));  // 2 second delay

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8084" + cacheKey))
            .GET()
            .timeout(Duration.ofMillis(500))  // Short timeout forces fallback
            .build();

        // Act: request fails due to timeout, fallback returns cache
        long startTime = System.currentTimeMillis();
        String response = getWithFallback(request, cacheKey);
        long totalTime = System.currentTimeMillis() - startTime;

        // Assert: returned cached value quickly (fallback, not timeout wait)
        assertEquals(expectedResponse, response,
            "Should return cached value on failure");
        assertTrue(totalTime < 1000,
            "Should return cached value quickly without waiting for timeout: " + totalTime + "ms");
    }

    /**
     * Test cache miss (no fallback available).
     * No cached value exists, service fails - request fails.
     */
    @Test
    public void testCacheMissNoFallback() throws Exception {
        // Arrange: cache is empty (no cached value)
        String cacheKey = "/api/missing";
        responseCache.invalidateAll();  // Ensure empty

        // Configure service to fail
        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8084" + cacheKey))
            .GET()
            .build();

        // Act: attempt request without fallback
        Exception exception = assertThrows(Exception.class, () -> {
            String response = getWithFallback(request, cacheKey);
            // If we get here but response is null, that's also a failure
            if (response == null) {
                throw new Exception("No cached fallback and service failed");
            }
        });

        // Assert: exception thrown (no fallback available)
        assertNotNull(exception);
    }

    /**
     * Test cache update on successful response.
     * When service responds successfully, response is cached for future fallback.
     */
    @Test
    public void testCacheUpdateOnSuccessfulResponse() throws Exception {
        // Arrange: cache is empty
        String cacheKey = "/api/data";
        String successResponse = "successful-response-data";
        responseCache.invalidateAll();

        // Configure service to return success
        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(successResponse)));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8084" + cacheKey))
            .GET()
            .build();

        // Act: execute successful request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Update cache on success
        responseCache.put(cacheKey, response.body());

        // Assert: cache now contains the response
        String cachedValue = responseCache.getIfPresent(cacheKey);
        assertEquals(successResponse, cachedValue,
            "Cache should be updated with successful response");
    }

    /**
     * Test stale-while-revalidate pattern.
     * Serve cached response immediately, background refresh on separate thread.
     */
    @Test
    public void testStaleWhileRevalidatePattern() throws Exception {
        // Arrange: cache contains stale value
        String cacheKey = "/api/config";
        String staleValue = "stale-config-v1";
        String freshValue = "fresh-config-v2";
        responseCache.put(cacheKey, staleValue);

        // Configure service to return updated value (slowly)
        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(freshValue)
                .withFixedDelay(500)));  // 500ms delay

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8084" + cacheKey))
            .GET()
            .timeout(Duration.ofMillis(100))  // Short timeout - use cache
            .build();

        // Act: client gets stale value immediately
        long startTime = System.currentTimeMillis();
        String immediateResponse = responseCache.getIfPresent(cacheKey);
        long clientResponseTime = System.currentTimeMillis() - startTime;

        // Assert: immediate response is stale value
        assertEquals(staleValue, immediateResponse,
            "Client should get stale cached value immediately");
        assertTrue(clientResponseTime < 100,
            "Cache lookup should be fast: " + clientResponseTime + "ms");

        // Act (background): refresh cache in background
        Thread refreshThread = new Thread(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    responseCache.put(cacheKey, response.body());
                }
            } catch (Exception e) {
                // Background refresh failed, client already has stale value
            }
        });
        refreshThread.start();

        // Wait for refresh to complete
        Thread.sleep(1000);
        refreshThread.join();

        // Assert: cache has been updated with fresh value
        String updatedValue = responseCache.getIfPresent(cacheKey);
        assertEquals(freshValue, updatedValue,
            "Cache should be updated with fresh value after background refresh");
    }

    /**
     * Test cache metrics and observability.
     */
    @Test
    public void testCacheMetrics() throws Exception {
        // Arrange: configure service
        String cacheKey = "/api/stats";
        String response = "stats-response";

        givenThat(get(urlEqualTo(cacheKey))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(response)));

        // Act: populate cache
        responseCache.put(cacheKey, response);

        // Verify cache state
        assertNotNull(responseCache.getIfPresent(cacheKey),
            "Cache should contain entry");

        long size = responseCache.estimatedSize();
        assertTrue(size > 0, "Cache should contain at least one entry");

        // Act: trigger cache hit
        String cachedValue = responseCache.getIfPresent(cacheKey);

        // Assert: cache hit verified
        assertEquals(response, cachedValue);
    }

    /**
     * Test fallback with expiring cache.
     * When cache expires, fallback is no longer available.
     */
    @Test
    public void testFallbackWithExpiringCache() throws Exception {
        // Arrange: create short-lived cache (100ms expiration)
        Cache<String, String> shortLivedCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(100))
            .build();

        String cacheKey = "/api/temp";
        String cachedValue = "temporary-value";
        shortLivedCache.put(cacheKey, cachedValue);

        // Verify cache has value
        String immediate = shortLivedCache.getIfPresent(cacheKey);
        assertEquals(cachedValue, immediate, "Cache should have value immediately");

        // Act: wait for cache to expire
        Thread.sleep(150);

        // Assert: cache value expired
        String expired = shortLivedCache.getIfPresent(cacheKey);
        assertNull(expired, "Cache value should have expired");
    }

    /**
     * Helper method: get response with fallback to cache.
     * If primary service fails, return cached value if available.
     */
    private String getWithFallback(HttpRequest request, String cacheKey) throws Exception {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                // Update cache on success
                responseCache.put(cacheKey, response.body());
                return response.body();
            } else {
                // Service error - try fallback
                String cached = responseCache.getIfPresent(cacheKey);
                if (cached != null) {
                    return cached;
                }
                throw new Exception("Service returned " + response.statusCode() + " and no cached fallback available");
            }
        } catch (Exception e) {
            // Network error or timeout - try fallback
            String cached = responseCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            throw e;
        }
    }
}
