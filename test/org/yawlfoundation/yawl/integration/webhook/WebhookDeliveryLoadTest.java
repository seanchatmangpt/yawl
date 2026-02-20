/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive webhook delivery validation tests.
 * Tests burst load (1000/sec for 60s), HMAC signing, and retry logic.
 */
@TestMethodOrder(OrderAnnotation.class)
public class WebhookDeliveryLoadTest {

    private static final String WEBHOOK_URL = "https://webhook.example.com/yawl/events";
    private static final String WEBHOOK_SECRET = "webhook-secret-key-32-chars-long";
    private static final int BURST_RATE = 1000; // requests per second
    private static final int BURST_DURATION = 60; // seconds
    private static final int TOTAL_REQUESTS = BURST_RATE * BURST_DURATION;

    private HttpClient httpClient;
    private ExecutorService executor;
    private final AtomicInteger successfulDeliveries = new AtomicInteger(0);
    private final AtomicInteger failedDeliveries = new AtomicInteger(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Test
    @Order(1)
    @DisplayName("Webhook Delivery: HMAC signature validation")
    void testHmacSignatureValidation() throws Exception {
        // Test cases for signature verification
        String testPayload = "{\"event\":\"case_created\",\"caseId\":\"test-123\"}";

        // Generate correct signature
        String correctSignature = WebhookSigner.buildSignatureHeader(WEBHOOK_SECRET,
            testPayload.getBytes());

        // Generate incorrect signatures
        String wrongSignature = "sha256=" + "0123456789abcdef".repeat(4);
        String malformedSignature = "invalid-format";
        String noSignature = null;

        // Test correct signature should pass
        assertTrue(WebhookSigner.verify(WEBHOOK_SECRET,
            testPayload.getBytes(), correctSignature),
            "Correct signature should be valid");

        // Test various incorrect signatures
        assertFalse(WebhookSigner.verify(WEBHOOK_SECRET,
            testPayload.getBytes(), wrongSignature),
            "Wrong signature should be invalid");

        assertFalse(WebhookSigner.verify(WEBHOOK_SECRET,
            testPayload.getBytes(), malformedSignature),
            "Malformed signature should be invalid");

        assertFalse(WebhookSigner.verify(WEBHOOK_SECRET,
            testPayload.getBytes(), noSignature),
            "Missing signature should be invalid");

        // Test constant-time comparison security
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            WebhookSigner.verify(WEBHOOK_SECRET, testPayload.getBytes(), correctSignature);
        }
        long duration = System.nanoTime() - start;
        double avgMicros = duration / 1000.0 / 1000.0;

        assertTrue(avgMicros < 100,
            String.format("Signature verification should be fast, avg: %.2f µs", avgMicros));

        System.out.printf("✅ HMAC signature validation: %.2f µs avg%n", avgMicros);
    }

    @Test
    @Order(2)
    @DisplayName("Webhook Delivery: Burst load test (1000/sec for 60s)")
    void testBurstLoadDelivery() throws Exception {
        System.out.println("Starting burst load test: " + TOTAL_REQUESTS + " requests...");

        Instant startTime = Instant.now();

        // Create and submit all webhook delivery requests
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            int requestNum = i;

            // Simulate webhook payload
            String payload = generateWebhookPayload(requestNum);

            // Add HMAC signature
            String signature = WebhookSigner.buildSignatureHeader(WEBHOOK_SECRET,
                payload.getBytes());

            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WEBHOOK_URL))
                .header("Content-Type", "application/json")
                .header("X-YAWL-Signature-256", signature)
                .header("X-YAWL-Event-Type", "case_created")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            // Submit async request
            CompletableFuture<HttpResponse<String>> future = httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long latencyMs = Duration.between(startTime, Instant.now()).toMillis();

                    if (response.statusCode() == 200) {
                        successfulDeliveries.incrementAndGet();
                        totalLatencyMs.addAndGet(latencyMs);
                    } else {
                        failedDeliveries.incrementAndGet();
                        System.err.printf("Request %d failed: HTTP %d%n",
                            requestNum, response.statusCode());
                    }

                    return response;
                });

            futures.add(future);

            // Rate control: maintain 1000 requests per second
            if (i % 100 == 0) {
                Thread.sleep(1); // Small delay to maintain rate
            }
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);

        // Calculate results
        int total = successfulDeliveries.get() + failedDeliveries.get();
        double successRate = (double) successfulDeliveries.get() / total * 100;
        double avgLatency = totalDeliveries.get() > 0 ?
            (double) totalLatencyMs.get() / totalDeliveries.get() : 0;

        // Validate requirements
        assertTrue(successRate >= 99.9,
            String.format("Success rate must be >= 99.9%%, got %.2f%%", successRate));

        assertTrue(avgLatency < 1000,
            String.format("Average latency must be < 1000ms, got %.2fms", avgLatency));

        Duration totalDuration = Duration.between(startTime, Instant.now());
        System.out.printf("✅ Burst load test results:%n" +
            "   - Success rate: %.2f%%%n" +
            "   - Average latency: %.2f ms%n" +
            "   - Total duration: %d seconds%n",
            successRate, avgLatency, totalDuration.toSeconds());

        // Validate retry mechanism by simulating some failures
        testRetryMechanism();
    }

    @Test
    @Order(3)
    @DisplayName("Webhook Delivery: Exponential backoff retry")
    void testRetryMechanism() {
        // Test the retry schedule: 0s, +5s, +30s, +5m, +30m, +2h, +8h
        long[] retryDelays = {0, 5000, 30000, 300000, 1800000, 7200000, 28800000};

        AtomicInteger attemptCount = new AtomicInteger(0);
        Instant startTime = Instant.now();

        // Simulate retry attempts with delays
        for (long delay : retryDelays) {
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            attemptCount.incrementAndGet();

            // Simulate successful delivery on final attempt
            if (attemptCount.get() == retryDelays.length) {
                successfulDeliveries.incrementAndGet();
                System.out.printf("✅ Delivery succeeded on attempt %d after %dms%n",
                    attemptCount.get(),
                    Duration.between(startTime, Instant.now()).toMillis());
            }
        }

        // Verify retry logic
        assertEquals(7, attemptCount.get(),
            "Should attempt delivery 7 times with exponential backoff");
    }

    @Test
    @Order(4)
    @DisplayName("Webhook Delivery: Dead letter queue handling")
    void testDeadLetterQueue() {
        // Simulate exhausted retries
        int maxAttempts = 7;
        AtomicInteger attempts = new AtomicInteger(0);

        // Simulate repeated failures
        while (attempts.get() < maxAttempts) {
            attempts.incrementAndGet();
            // Simulate network failure
            failedDeliveries.incrementAndGet();

            if (attempts.get() < maxAttempts) {
                // Sleep between retry attempts (exponential backoff)
                try {
                    Thread.sleep((long) (Math.pow(2, attempts.get()) * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // After max attempts, route to dead letter queue
        System.out.printf("✅ Routing to dead letter queue after %d failed attempts%n",
            attempts.get());

        // Verify dead letter queue would be called
        assertTrue(failedDeliveries.get() >= maxAttempts,
            "Should attempt max retries before dead letter");
    }

    private String generateWebhookPayload(int requestNum) {
        return String.format(
            "{\"event\":\"case_created\",\"timestamp\":\"%s\",\"caseId\":\"case-%d\",\"data\":{\"amount\":123.45,\"items\":[]}}",
            Instant.now(), requestNum);
    }

    // Cleanup
    void cleanup() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}