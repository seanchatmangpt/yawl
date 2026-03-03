import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class GroqLlmGatewayIntegrationTest {
    private static final String PING = "Reply with exactly the word: OK";
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    // Environment variables
    private static final String API_KEY = System.getenv("GROQ_API_KEY");
    private static final String MODEL = System.getenv("GROQ_MODEL");
    private static final int MAX_CONCURRENCY = Integer.parseInt(System.getenv("GROQ_MAX_CONCURRENCY"));

    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static GroqLlmGateway gw;

    public static void main(String[] args) throws Exception {
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.err.println("GROQ_API_KEY environment variable not set");
            System.exit(1);
        }

        // Initialize gateway
        gw = new GroqLlmGateway();

        System.out.println("[groq] model    = " + MODEL);
        System.out.println("[groq] endpoint = " + BASE_URL);
        System.out.println("[groq] key      = " + API_KEY.substring(0, 8) + "...");

        // Run test 1: Single request latency
        t1_singleRequest_latency();

        // Run test 2: Concurrent fan-out
        t2_concurrent_successAnd429Split();

        // Run test 3: Sequential burst
        t3_sequentialBurst_rpmAndThreshold();

        // Run test 4: Invalid key
        t4_invalidKey_throwsWithAuthError();
    }

    private static void t1_singleRequest_latency() throws Exception {
        System.out.println("\n[groq] T1: single request latency test");

        long t0 = System.currentTimeMillis();
        String resp = gw.send(PING, 0.0);
        long ms = System.currentTimeMillis() - t0;

        System.out.printf("[result] T1 latency_ms=%d response=%s%n", ms, resp.trim());
        System.out.printf("[metric] latency=%dms%n", ms);
    }

    private static void t2_concurrent_successAnd429Split() throws InterruptedException {
        System.out.println("\n[groq] T2: concurrent fan-out probe");

        for (int n : new int[]{5, 10, 20, 30}) {
            BurstResult result = fireConcurrent(n);
            System.out.printf(
                "[result] T2 n=%2d  success=%d  rate_limited_429=%d  other_err=%d  " +
                "wall_ms=%d  avg_latency_ms=%.0f%n",
                n, result.successes, result.rateLimited, result.otherErrors,
                result.wallMs, result.avgLatencyMs());

            System.out.printf("[metric] concurrency_test_%d_success=%d%n", n, result.successes);
            System.out.printf("[metric] concurrency_test_%d_429=%d%n", n, result.rateLimited);
        }
    }

    private static void t3_sequentialBurst_rpmAndThreshold() {
        System.out.println("\n[groq] T3: sequential burst — firing until 429 or 20 requests");

        int maxRequests = 20;
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

        long sumMs = 0;
        for (int i = 0; i < successes; i++) sumMs += latencies[i];
        double avgMs = successes > 0 ? (double) sumMs / successes : 0;

        System.out.printf(
            "[result] T3 successes=%d total_ms=%d effective_rpm=%.1f avg_latency_ms=%.0f " +
            "rate_limit_threshold=%s%n",
            successes, totalMs, effectiveRpm, avgMs,
            firstRateLimitAfter >= 0 ? firstRateLimitAfter + " requests" : "not hit");

        System.out.printf("[metric] sequential_successes=%d%n", successes);
        System.out.printf("[metric] effective_rpm=%.2f%n", effectiveRpm);
    }

    private static void t4_invalidKey_throwsWithAuthError() {
        System.out.println("\n[groq] T4: invalid key test");

        var badGw = new GroqLlmGateway(
            "gsk_INVALID00000000000000000000000000000000000000000",
            MODEL,
            TIMEOUT);

        try {
            badGw.send(PING, 0.0);
            System.err.println("[error] T4 should have thrown an exception");
        } catch (IOException ex) {
            String msg = ex.getMessage();
            System.out.printf("[result] T4 invalid_key_error=%s%n", msg);
            System.out.printf("[metric] auth_error_occurred=true%n");
        }
    }

    private static BurstResult fireConcurrent(int n) throws InterruptedException {
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

        ready.await();
        long wallStart = System.currentTimeMillis();
        go.countDown();

        for (Future<?> f : futures) {
            try { f.get(60, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException ignored) { otherErrors.incrementAndGet(); }
        }
        pool.shutdown();

        return new BurstResult(successes.get(), rateLimited.get(), otherErrors.get(),
            System.currentTimeMillis() - wallStart, totalMs.get());
    }

    private record BurstResult(int successes, int rateLimited, int otherErrors,
                            long wallMs, long totalSuccessMs) {
        double avgLatencyMs() {
            return successes > 0 ? (double) totalSuccessMs / successes : 0;
        }
    }

    private static class GroqLlmGateway {
        private final String apiKey;
        private final String model;
        private final Duration timeout;
        private final HttpClient client;

        public GroqLlmGateway() {
            this(API_KEY, MODEL, TIMEOUT);
        }

        public GroqLlmGateway(String apiKey, String model, Duration timeout) {
            this.apiKey = apiKey;
            this.model = model;
            this.timeout = timeout;
            this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        }

        public String send(String prompt, double temperature) throws IOException {
            String json = String.format(
                "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":%.2f,\"stream\":false}",
                model, prompt, temperature);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(timeout)
                .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 429) {
                    throw new IOException("429 Rate limit exceeded");
                }

                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
                }

                // Parse response to extract content
                String body = response.body();
                if (body.contains("\"content\":")) {
                    int start = body.indexOf("\"content\":") + 10;
                    int end = body.indexOf("\"", start);
                    if (end > start) {
                        return body.substring(start, end).replace("\\\"", "\"");
                    }
                }

                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        }

        public String getModel() {
            return model;
        }
    }
}