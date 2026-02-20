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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive ZAI AI function service validation tests.
 * Tests AI decision reasoning, specification generation, and performance benchmarks.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ZaiServiceValidationTest {

    private ZaiService zaiService;
    private SpecificationGenerator specGenerator;
    private SpecificationOptimizer specOptimizer;
    private ZaiDecisionReasoner reasoner;
    private static final String ZAI_API_URL = "https://api.zai.example.com/v1";
    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        zaiService = new ZaiService(ZAI_API_URL, API_KEY);
        specGenerator = new SpecificationGenerator();
        specOptimizer = new SpecificationOptimizer();
        reasoner = new ZaiDecisionReasoner();
    }

    @Test
    @Order(1)
    @DisplayName("ZAI Service: AI Function Call Performance")
    void testAIFunctionCallPerformance() throws Exception {
        // Generate test prompt
        String prompt = "Generate a workflow specification for a simple order processing system.";

        // Measure performance
        long startTime = System.nanoTime();
        String result = zaiService.callFunction("generate_specification",
            Map.of("prompt", prompt));
        long duration = System.nanoTime() - startTime;

        double responseTimeMs = duration / 1_000_000.0;

        // Validate response
        assertNotNull(result, "ZAI should return a result");
        assertTrue(result.length() > 100, "Response should be substantial");
        assertTrue(responseTimeMs < 5000,
            String.format("AI function call should complete in < 5000ms, got %.2fms",
                responseTimeMs));

        System.out.printf("✅ AI Function Performance: %.2f ms%n", responseTimeMs);
    }

    @Test
    @Order(2)
    @DisplayName("ZAI Service: Specification Generation Integrity")
    void testSpecificationGeneration() {
        // Test workflow specification generation
        String specPrompt = "Create a customer onboarding workflow with steps: " +
            "account creation, email verification, profile setup, approval";

        String specification = specGenerator.generate(specPrompt);

        // Validate YAWL specification structure
        assertNotNull(specification, "Generated specification should not be null");
        assertTrue(specification.contains("<specification>"),
            "Should contain YAWL specification root element");
        assertTrue(specification.contains("<process id="),
            "Should contain process definitions");
        assertTrue(specification.contains("<workflow-schema>"),
            "Should contain workflow schema");

        // Validate against YAWL schema
        assertTrue(validateYAWLSpecification(specification),
            "Generated specification should be YAWL schema compliant");

        System.out.println("✅ Specification generation integrity validated");
    }

    @Test
    @Order(3)
    @DisplayName("ZAI Service: Specification Optimization")
    void testSpecificationOptimization() {
        // Generate an initial specification
        String initialSpec = specGenerator.generate("Simple approval workflow");

        // Optimize the specification
        String optimizedSpec = specOptimizer.optimize(initialSpec,
            List.of("reduce_bloat", "improve_concurrency", "add_error_handling"));

        // Validate optimization
        assertNotNull(optimizedSpec, "Optimized specification should not be null");

        // Check that optimization improved the spec
        assertTrue(optimizedSpec.contains("<exception-handler>"),
            "Optimized spec should have error handling");
        assertTrue(optimizedSpec.contains("<xor-split>"),
            "Optimized spec should have better concurrency");

        System.out.println("✅ Specification optimization validated");
    }

    @Test
    @Order(4)
    @DisplayName("ZAI Service: Decision Reasoning Accuracy")
    void testDecisionReasoningAccuracy() {
        // Test decision reasoning for workflow routing
        Map<String, Object> context1 = Map.of(
            "task_type", "approval",
            "request_amount", 5000,
            "requester_role", "employee",
            "budget_remaining", 10000
        );

        DecisionReasoningResult result1 = reasoner.makeDecision(
            "approve_request", context1);

        assertEquals("approve", result1.getDecision(),
            "Should approve under-budget request");
        assertTrue(result1.getConfidence() > 0.8,
            "Decision confidence should be high");

        // Test rejection scenario
        Map<String, Object> context2 = Map.of(
            "task_type", "approval",
            "request_amount", 15000,
            "requester_role", "employee",
            "budget_remaining", 5000
        );

        DecisionReasoningResult result2 = reasoner.makeDecision(
            "approve_request", context2);

        assertEquals("reject", result2.getDecision(),
            "Should reject over-budget request");

        System.out.println("✅ Decision reasoning accuracy validated");
    }

    @Test
    @Order(5)
    @DisplayName("ZAI Service: Concurrent Request Handling")
    void testConcurrentRequestHandling() throws Exception {
        int concurrentRequests = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Submit concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestNum = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return zaiService.callFunction("generate_specification",
                        Map.of("prompt", "Simple test " + requestNum));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allFutures.get(30, TimeUnit.SECONDS);

        // Validate all results
        int successfulResults = 0;
        for (CompletableFuture<String> future : futures) {
            try {
                String result = future.get();
                if (result != null && !result.isEmpty()) {
                    successfulResults++;
                }
            } catch (Exception e) {
                // Count as failed
            }
        }

        double successRate = (double) successfulResults / concurrentRequests * 100;
        assertTrue(successRate >= 90.0,
            String.format("Concurrent request success rate should be >= 90%%, got %.1f%%",
                successRate));

        System.out.printf("✅ Concurrent requests: %d/%d successful (%.1f%%)%n",
            successfulResults, concurrentRequests, successRate);
    }

    @Test
    @Order(6)
    @DisplayName("ZAI Service: Function Registry Validation")
    void testFunctionRegistry() {
        // Test built-in functions
        List<String> builtInFunctions = zaiService.listAvailableFunctions();
        assertFalse(builtInFunctions.isEmpty(), "Should have built-in functions");

        // Test function existence
        assertTrue(builtInFunctions.contains("generate_specification"),
            "Should have generate_specification function");
        assertTrue(builtInFunctions.contains("optimize_specification"),
            "Should have optimize_specification function");
        assertTrue(builtInFunctions.contains("analyze_workflow"),
            "Should have analyze_workflow function");

        // Test function validation
        assertTrue(zaiService.isFunctionValid("generate_specification"),
            "generate_specification should be valid");
        assertFalse(zaiService.isFunctionValid("invalid_function"),
            "Invalid function should not be valid");

        System.out.printf("✅ Function registry validated: %d functions%n",
            builtInFunctions.size());
    }

    @Test
    @Order(7)
    @DisplayName("ZAI Service: AI Model Fallback")
    void testAIModelFallback() {
        // Test primary model
        String primaryResult = zaiService.callWithModel("gpt-4", "Test prompt");
        assertNotNull(primaryResult, "Primary model should return result");

        // Test fallback model
        String fallbackResult = zaiService.callWithModel("fallback", "Test prompt");
        assertNotNull(fallbackResult, "Fallback model should return result");

        // Test model switching
        zaiService.setPreferredModel("gpt-4");
        assertEquals("gpt-4", zaiService.getPreferredModel(),
            "Preferred model should be set correctly");

        System.out.println("✅ AI model fallback mechanism validated");
    }

    @Test
    @Order(8)
    @DisplayName("ZAI Service: Rate Limiting and Throttling")
    void testRateLimiting() {
        // Test rate limiting enforcement
        int requestsInWindow = 5;
        List<CompletableFuture<RateLimitResult>> futures = new ArrayList<>();

        for (int i = 0; i < requestsInWindow; i++) {
            futures.add(zaiService.callWithRateLimit("generate_specification"));
        }

        // Process results
        int successful = 0;
        int rateLimited = 0;

        for (CompletableFuture<RateLimitResult> future : futures) {
            try {
                RateLimitResult result = future.get(5, TimeUnit.SECONDS);
                if (result.isSuccess()) {
                    successful++;
                } else if (result.isRateLimited()) {
                    rateLimited++;
                }
            } catch (Exception e) {
                // Count as failed
            }
        }

        // Should successfully complete at least 3 out of 5 requests
        assertTrue(successful >= 3,
            String.format("Should successfully complete at least 3 requests, got %d",
                successful));

        System.out.printf("✅ Rate limiting test: %d successful, %d rate limited%n",
            successful, rateLimited);
    }

    @Test
    @Order(9)
    @DisplayName("ZAI Service: Response Caching")
    void testResponseCaching() {
        // Test repeated identical requests (should use cache)
        String prompt = "Generate a hello world workflow";

        // First call (cache miss)
        String result1 = zaiService.callFunction("generate_specification",
            Map.of("prompt", prompt));

        // Second call (cache hit)
        long startTime = System.nanoTime();
        String result2 = zaiService.callFunction("generate_specification",
            Map.of("prompt", prompt));
        long duration = System.nanoTime() - startTime;

        // Validate cached response
        assertEquals(result1, result2, "Cached response should be identical");
        assertTrue(duration < 1000,
            "Cached response should be < 1000ms");

        // Verify cache statistics
        int cacheHits = zaiService.getCacheHits();
        int cacheMisses = zaiService.getCacheMisses();

        assertEquals(1, cacheHits, "Should have 1 cache hit");
        assertEquals(1, cacheMisses, "Should have 1 cache miss");

        System.out.printf("✅ Response caching: %d hits, %d misses%n",
            cacheHits, cacheMisses);
    }

    // Helper methods

    private boolean validateYAWLSpecification(String specification) {
        // Basic YAWL schema validation
        return specification.contains("<specification>") &&
               specification.contains("<process id=") &&
               specification.contains("<inputs>") &&
               specification.contains("<outputs>") &&
               specification.contains("<flows>") &&
               specification.contains("<workitems>");
    }

    // Helper classes

    public static class DecisionReasoningResult {
        private final String decision;
        private final double confidence;
        private final String reasoning;
        private final Map<String, Object> context;

        public DecisionReasoningResult(String decision, double confidence,
                                     String reasoning, Map<String, Object> context) {
            this.decision = decision;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.context = context;
        }

        public String getDecision() { return decision; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getContext() { return context; }
    }

    public static class RateLimitResult {
        private final boolean success;
        private final boolean rateLimited;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean success, boolean rateLimited,
                             long retryAfterSeconds) {
            this.success = success;
            this.rateLimited = rateLimited;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isSuccess() { return success; }
        public boolean isRateLimited() { return rateLimited; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}