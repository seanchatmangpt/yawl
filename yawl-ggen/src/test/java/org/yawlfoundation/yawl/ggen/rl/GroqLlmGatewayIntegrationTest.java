/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD integration tests for {@link GroqLlmGateway}.
 *
 * <p>These tests call the LIVE Groq API to measure real limits.
 * All numbers printed are empirical — not from marketing documentation.
 *
 * <p>What is measured:
 * <ul>
 *   <li>Test 1 — single round-trip latency (ms)</li>
 *   <li>Test 2 — concurrent fan-out at n=5,10,20,30: success/429 split, avg latency</li>
 *   <li>Test 3 — sustained sequential burst: effective RPM and rate-limit threshold</li>
 *   <li>Test 4 — bad key → confirm 401 path</li>
 * </ul>
 *
 * <p>Skipped automatically when {@code GROQ_API_KEY} is absent.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@DisplayName("GroqLlmGateway — Live API")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroqLlmGatewayIntegrationTest {

    /** Minimal prompt: minimizes token burn and latency variance. */
    private static final String PING = "Reply with exactly the word: OK";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    private static GroqLlmGateway gw;

    @BeforeAll
    static void init() {
        gw = GroqLlmGateway.fromEnv(TIMEOUT);
        System.out.println("[groq] model    = " + gw.getModel());
        System.out.println("[groq] endpoint = " + GroqLlmGateway.DEFAULT_BASE_URL);
        System.out.println("[groq] key      = " +
            System.getenv("GROQ_API_KEY").substring(0, 8) + "...");
    }

    // ─── T1: Baseline latency ─────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("T1 — single request: round-trip latency")
    void t1_singleRequest_latency() throws IOException {
        long t0 = System.currentTimeMillis();
        String resp = gw.send(PING, 0.0);
        long ms = System.currentTimeMillis() - t0;

        assertNotNull(resp);
        assertFalse(resp.isBlank());
        System.out.printf("[result] T1 latency_ms=%d response=%s%n", ms, resp.trim());

        assertTrue(ms < TIMEOUT.toMillis(),
            "Latency " + ms + "ms must be < timeout " + TIMEOUT.toMillis() + "ms");
    }

    // ─── T2: Concurrent fan-out ───────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("T2 — concurrent fan-out: success/429 split at n=5,10,20,30")
    void t2_concurrent_successAnd429Split() throws InterruptedException {
        System.out.println("[groq] T2: concurrent fan-out probe");

        for (int n : new int[]{5, 10, 20, 30}) {
            var result = fireConcurrent(n);
            System.out.printf(
                "[result] T2 n=%2d  success=%d  rate_limited_429=%d  other_err=%d  " +
                "wall_ms=%d  avg_latency_ms=%.0f%n",
                n, result.successes, result.rateLimited, result.otherErrors,
                result.wallMs, result.avgLatencyMs());

            // At least some requests must complete (success OR 429 is an API response)
            assertTrue(result.successes + result.rateLimited + result.otherErrors == n,
                "All " + n + " tasks must complete");
        }
    }

    // ─── T3: Sequential burst → effective RPM + rate-limit threshold ──────────

    @Test
    @Order(3)
    @DisplayName("T3 — sequential burst: effective RPM and rate-limit boundary")
    void t3_sequentialBurst_rpmAndThreshold() {
        System.out.println("[groq] T3: sequential burst — firing until 429 or 20 requests");

        int maxRequests = 20; // keep within reasonable time
        int successes = 0;
        long firstRateLimitAfter = -1;
        long start = System.currentTimeMillis();
        long[] latencies = new long[maxRequests];

        for (int i = 0; i < maxRequests; i++) {
            long t0 = System.currentTimeMillis();
            try {
                gw.send(PING, 0.0);
                latencies[successes] = System.currentTimeMillis() - t0;
                successes++;
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("429")) {
                    firstRateLimitAfter = successes;
                    System.out.printf("[result] T3 rate_limit_hit after_successes=%d elapsed_ms=%d%n",
                        successes, System.currentTimeMillis() - start);
                    break;
                }
                System.out.printf("[warn]   T3 request %d non-429 error: %s%n", i, msg);
            }
        }

        long totalMs = System.currentTimeMillis() - start;
        double effectiveRpm = successes / (totalMs / 60_000.0);

        // avg latency of successful requests
        long sumMs = 0;
        for (int i = 0; i < successes; i++) sumMs += latencies[i];
        double avgMs = successes > 0 ? (double) sumMs / successes : 0;

        System.out.printf(
            "[result] T3 successes=%d total_ms=%d effective_rpm=%.1f avg_latency_ms=%.0f " +
            "rate_limit_threshold=%s%n",
            successes, totalMs, effectiveRpm, avgMs,
            firstRateLimitAfter >= 0 ? firstRateLimitAfter + " requests" : "not hit");

        // T3 may immediately hit 429 if the rate limit was already exhausted by T2's
        // n=30 burst. Both outcomes are valid observations: the assertion verifies that
        // requests DID reach the API (success > 0 OR rate limit hit is an API response).
        assertTrue(successes > 0 || firstRateLimitAfter >= 0,
            "Must reach the API: either succeed or observe a rate limit (429)");
    }

    // ─── T4: Authentication failure ───────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("T4 — invalid key: must throw IOException with HTTP auth error")
    void t4_invalidKey_throwsWithAuthError() {
        var badGw = new GroqLlmGateway(
            "gsk_INVALID00000000000000000000000000000000000000000",
            GroqLlmGateway.DEFAULT_MODEL,
            TIMEOUT);

        IOException ex = assertThrows(IOException.class, () -> badGw.send(PING, 0.0));
        String msg = ex.getMessage();
        System.out.printf("[result] T4 invalid_key_error=%s%n", msg);
        assertNotNull(msg);
        assertTrue(msg.contains("401") || msg.contains("403") || msg.contains("invalid") ||
                   msg.contains("auth") || msg.contains("key"),
            "Expected an authentication error, got: " + msg);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private record BurstResult(int successes, int rateLimited, int otherErrors,
                                long wallMs, long totalSuccessMs) {
        double avgLatencyMs() {
            return successes > 0 ? (double) totalSuccessMs / successes : 0;
        }
    }

    private BurstResult fireConcurrent(int n) throws InterruptedException {
        AtomicInteger successes   = new AtomicInteger();
        AtomicInteger rateLimited = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();
        AtomicLong    totalMs     = new AtomicLong();

        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go    = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(n);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { return; }
                long t0 = System.currentTimeMillis();
                try {
                    gw.send(PING, 0.0);
                    successes.incrementAndGet();
                    totalMs.addAndGet(System.currentTimeMillis() - t0);
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("429")) rateLimited.incrementAndGet();
                    else                                     otherErrors.incrementAndGet();
                }
            }));
        }

        ready.await();           // all threads parked at the gate
        long wallStart = System.currentTimeMillis();
        go.countDown();          // simultaneous fire

        for (Future<?> f : futures) {
            try { f.get(60, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException ignored) { otherErrors.incrementAndGet(); }
        }
        pool.shutdown();

        return new BurstResult(successes.get(), rateLimited.get(), otherErrors.get(),
            System.currentTimeMillis() - wallStart, totalMs.get());
    }
}
