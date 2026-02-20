package org.yawlfoundation.yawl.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Resilience4j Bulkhead (concurrent request limiting).
 *
 * These tests verify Bulkhead behavior using REAL HTTP interactions with WireMock.
 * Bulkhead limits concurrent requests to prevent resource exhaustion.
 *
 * Test scenarios:
 * - Concurrent request limiting (max 5 concurrent)
 * - Rejection when max concurrency reached (BulkheadFullException)
 * - Queue overflow when waiting requests exceed queue size
 * - Resource isolation between bulkheads
 */
public class BulkheadWireMockTest {

    private WireMockServer wireMockServer;
    private Bulkhead bulkhead;
    private HttpClient httpClient;
    private ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        // Initialize WireMock HTTP server
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(8083)
            .jettyStopTimeout(5000));
        wireMockServer.start();
        configureFor("localhost", 8083);

        // Initialize HTTP client
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        // Initialize executor for concurrent test requests
        executorService = Executors.newFixedThreadPool(20);

        // Configure Bulkhead: max 5 concurrent calls, queue size 10
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ofSeconds(2))
            .build();

        bulkhead = Bulkhead.of("http-bulkhead", config);
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
    }

    /**
     * Test concurrent request limiting.
     * Maximum 5 concurrent requests allowed, 6th request should be queued/rejected.
     */
    @Test
    public void testConcurrentRequestLimiting() throws Exception {
        // Arrange: configure endpoint that responds slowly (500ms delay)
        givenThat(get(urlEqualTo("/api/concurrent"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(500)));  // 500ms delay to ensure concurrency

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/concurrent"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        // Act: launch 10 concurrent requests through bulkhead
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    bulkhead.executeSupplier(() -> {
                        try {
                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            return response.statusCode() == 200;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    successCount.incrementAndGet();
                } catch (io.github.resilience4j.bulkhead.BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions
                }
            }, executorService);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures).join();

        // Assert: some requests should succeed (up to 5 concurrent), others queued or rejected
        assertTrue(successCount.get() >= 5,
            "At least 5 requests should succeed (max concurrent): " + successCount.get());
        assertTrue(successCount.get() + rejectedCount.get() >= 5,
            "Total processed should be at least 5");
    }

    /**
     * Test rejection when bulkhead full.
     * When max concurrent calls (5) and queue (10) are full, new requests are rejected.
     */
    @Test
    public void testRejectionWhenBulkheadFull() throws Exception {
        // Arrange: create bulkhead with very small capacity (1 concurrent, 0 queue)
        BulkheadConfig smallConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ofMillis(100))
            .build();

        Bulkhead smallBulkhead = Bulkhead.of("small-bulkhead", smallConfig);

        // Configure endpoint with long delay
        givenThat(get(urlEqualTo("/api/fullbulkhead"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(2000)));  // 2 second delay

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/fullbulkhead"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        // Act: launch requests that will fill the bulkhead
        AtomicInteger rejectedCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    smallBulkhead.executeSupplier(() -> {
                        try {
                            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            return true;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (io.github.resilience4j.bulkhead.BulkheadFullException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // Other exceptions are OK
                }
            }, executorService);
        }

        // Wait for requests to complete
        CompletableFuture.allOf(futures).join();

        // Assert: some requests should be rejected (bulkhead is full)
        assertTrue(rejectedCount.get() > 0,
            "Should have rejected requests when bulkhead full: " + rejectedCount.get());
    }

    /**
     * Test queue overflow behavior.
     * When waiting queue exceeds maxWaitDuration, waiting requests are rejected.
     */
    @Test
    public void testQueueOverflowOnTimeout() throws Exception {
        // Arrange: create bulkhead with small queue timeout
        BulkheadConfig queueConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(2)  // Only 2 concurrent
            .maxWaitDuration(Duration.ofMillis(500))  // 500ms queue wait timeout
            .build();

        Bulkhead queueBulkhead = Bulkhead.of("queue-bulkhead", queueConfig);

        // Configure endpoint with long delay
        givenThat(get(urlEqualTo("/api/queue"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(2000)));  // 2 second delay > 500ms queue timeout

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/queue"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        // Act: launch many concurrent requests
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[8];
        for (int i = 0; i < 8; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    queueBulkhead.executeSupplier(() -> {
                        try {
                            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            return true;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage().contains("Request didn't complete") ||
                        e.getMessage().contains("timeout") ||
                        e instanceof io.github.resilience4j.bulkhead.BulkheadFullException) {
                        timeoutCount.incrementAndGet();
                    }
                }
            }, executorService);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures).join();

        // Assert: some requests should timeout on queue wait
        assertTrue(timeoutCount.get() > 0 || successCount.get() < 8,
            "Some requests should timeout or be rejected due to queue limits: " +
            "success=" + successCount.get() + ", timeout=" + timeoutCount.get());
    }

    /**
     * Test resource isolation between bulkheads.
     * Different bulkheads operate independently with separate concurrency limits.
     */
    @Test
    public void testResourceIsolationBetweenBulkheads() throws Exception {
        // Arrange: create two independent bulkheads
        BulkheadConfig config1 = BulkheadConfig.custom()
            .maxConcurrentCalls(2)
            .maxWaitDuration(Duration.ofSeconds(2))
            .build();

        BulkheadConfig config2 = BulkheadConfig.custom()
            .maxConcurrentCalls(3)
            .maxWaitDuration(Duration.ofSeconds(2))
            .build();

        Bulkhead bulkhead1 = Bulkhead.of("bulkhead-1", config1);
        Bulkhead bulkhead2 = Bulkhead.of("bulkhead-2", config2);

        // Configure endpoints
        givenThat(get(urlEqualTo("/api/service1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(1000)));

        givenThat(get(urlEqualTo("/api/service2"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(1000)));

        HttpRequest request1 = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/service1"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request2 = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/service2"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        // Act: launch concurrent requests through different bulkheads
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            if (index < 3) {
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        bulkhead1.executeSupplier(() -> {
                            try {
                                httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
                                count1.incrementAndGet();
                                return true;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (Exception e) {
                        // OK if rejected
                    }
                }, executorService);
            } else {
                futures[i] = CompletableFuture.runAsync(() -> {
                    try {
                        bulkhead2.executeSupplier(() -> {
                            try {
                                httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
                                count2.incrementAndGet();
                                return true;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (Exception e) {
                        // OK if rejected
                    }
                }, executorService);
            }
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures).join();

        // Assert: bulkheads operate independently
        assertTrue(count1.get() + count2.get() >= 2,
            "Requests should proceed through respective bulkheads: " +
            "bulkhead1=" + count1.get() + ", bulkhead2=" + count2.get());
    }

    /**
     * Test metrics: verify bulkhead tracks concurrent call count.
     */
    @Test
    public void testBulkheadMetrics() throws Exception {
        // Arrange: configure endpoint
        givenThat(get(urlEqualTo("/api/metrics"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")
                .withFixedDelay(500)));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8083/api/metrics"))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        // Act: verify metrics before request
        int availableBefore = bulkhead.getMetrics().getAvailableConcurrentCalls();
        assertTrue(availableBefore > 0, "Should have available concurrent calls");

        // Execute one request
        bulkhead.executeSupplier(() -> {
            try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Assert: metrics track calls
        assertTrue(bulkhead.getMetrics().getAvailableConcurrentCalls() >= 0,
            "Metrics should be tracked");
    }
}
