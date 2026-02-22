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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive unit tests for ZaiDecisionReasoner.
 *
 * <p>Tests cover decision making, data quality assessment, bottleneck analysis,
 * task assignment recommendations, and audit logging.</p>
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 */
@DisplayName("ZAI Decision Reasoner Tests")
public class ZaiDecisionReasonerTest {

    private static final String TEST_API_KEY = "test-api-key-12345";
    private static ZaiService zaiService;
    private static ZaiDecisionReasoner reasoner;
    private static boolean apiAvailable;

    @BeforeAll
    static void setUpClass() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                zaiService = new ZaiService(apiKey);
                apiAvailable = zaiService.verifyConnection();
                if (apiAvailable) {
                    reasoner = new ZaiDecisionReasoner(zaiService);
                }
            } catch (Exception e) {
                apiAvailable = false;
            }
        }

        // Create test instances for unit tests
        if (zaiService == null) {
            zaiService = new ZaiService(TEST_API_KEY);
        }
        if (reasoner == null) {
            reasoner = new ZaiDecisionReasoner(zaiService);
        }
    }

    @AfterAll
    static void tearDownClass() {
        if (reasoner != null) {
            reasoner.clearCache();
        }
        if (zaiService != null) {
            zaiService.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        if (reasoner != null) {
            reasoner.clearCache();
        }
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor with valid ZaiService should succeed")
        void testConstructorWithValidService() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            ZaiDecisionReasoner r = new ZaiDecisionReasoner(service);

            assertNotNull(r, "Reasoner should be created");
            assertEquals(0, r.getCacheSize(), "Initial cache should be empty");
        }

        @Test
        @DisplayName("Constructor with null service should throw")
        void testConstructorWithNullService() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZaiDecisionReasoner(null),
                    "Should throw for null ZaiService");
        }

        @Test
        @DisplayName("Constructor with custom parameters")
        void testConstructorWithCustomParams() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            ZaiDecisionReasoner r = new ZaiDecisionReasoner(
                    service, "glm-5", 500, 1800000);

            assertNotNull(r, "Reasoner should be created with custom params");
        }

        @Test
        @DisplayName("Constructor with null model should throw")
        void testConstructorWithNullModel() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertThrows(IllegalArgumentException.class,
                    () -> new ZaiDecisionReasoner(service, null, 100, 1000),
                    "Should throw for null model");
        }

        @Test
        @DisplayName("Constructor with empty model should throw")
        void testConstructorWithEmptyModel() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertThrows(IllegalArgumentException.class,
                    () -> new ZaiDecisionReasoner(service, "", 100, 1000),
                    "Should throw for empty model");
        }

        @Test
        @DisplayName("Constructor with negative cache size should throw")
        void testConstructorWithNegativeCacheSize() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertThrows(IllegalArgumentException.class,
                    () -> new ZaiDecisionReasoner(service, "GLM-4.7-Flash", -1, 1000),
                    "Should throw for negative cache size");
        }

        @Test
        @DisplayName("Constructor with negative expiration should throw")
        void testConstructorWithNegativeExpiration() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            assertThrows(IllegalArgumentException.class,
                    () -> new ZaiDecisionReasoner(service, "GLM-4.7-Flash", 100, -1),
                    "Should throw for negative expiration");
        }
    }

    // =========================================================================
    // WorkflowContext Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowContext Tests")
    class WorkflowContextTests {

        @Test
        @DisplayName("Create workflow context with all fields")
        void testCreateWorkflowContext() {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-123", "OrderProcessing", "ReviewOrder", "{\"amount\": 5000}");

            assertEquals("CASE-123", context.getCaseId());
            assertEquals("OrderProcessing", context.getWorkflowName());
            assertEquals("ReviewOrder", context.getCurrentTask());
            assertEquals("{\"amount\": 5000}", context.getCaseData());
        }

        @Test
        @DisplayName("Add metadata to context")
        void testAddMetadata() {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-123", "Workflow", "Task", "{}");

            context.withMetadata("key1", "value1");
            context.withMetadata("key2", 123);

            assertEquals("value1", context.getMetadata().get("key1"));
            assertEquals(123, context.getMetadata().get("key2"));
        }
    }

    // =========================================================================
    // RoutingOption Tests
    // =========================================================================

    @Nested
    @DisplayName("RoutingOption Tests")
    class RoutingOptionTests {

        @Test
        @DisplayName("Create routing option with required fields")
        void testCreateRoutingOption() {
            ZaiDecisionReasoner.RoutingOption option = new ZaiDecisionReasoner.RoutingOption(
                    "route-1", "Process normally", "medium");

            assertEquals("route-1", option.getId());
            assertEquals("Process normally", option.getDescription());
            assertEquals("medium", option.getPriority());
            assertNull(option.getCondition());
        }

        @Test
        @DisplayName("Create routing option with condition")
        void testCreateRoutingOptionWithCondition() {
            ZaiDecisionReasoner.RoutingOption option = new ZaiDecisionReasoner.RoutingOption(
                    "route-1", "High priority path", "high", "amount > 10000");

            assertEquals("amount > 10000", option.getCondition());
        }
    }

    // =========================================================================
    // DecisionResult Tests
    // =========================================================================

    @Nested
    @DisplayName("DecisionResult Tests")
    class DecisionResultTests {

        @Test
        @DisplayName("Create decision result")
        void testCreateDecisionResult() {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key", "value");

            ZaiDecisionReasoner.DecisionResult result = new ZaiDecisionReasoner.DecisionResult(
                    "route-1", 0.85, "Best option based on data", metadata);

            assertEquals("route-1", result.getSelectedOption());
            assertEquals(0.85, result.getConfidence(), 0.001);
            assertEquals("Best option based on data", result.getReasoning());
            assertEquals("value", result.getMetadata().get("key"));
        }

        @Test
        @DisplayName("Confidence is clamped to valid range")
        void testConfidenceClamping() {
            ZaiDecisionReasoner.DecisionResult highResult = new ZaiDecisionReasoner.DecisionResult(
                    "route", 1.5, "Test", null);
            assertEquals(1.0, highResult.getConfidence(), 0.001);

            ZaiDecisionReasoner.DecisionResult lowResult = new ZaiDecisionReasoner.DecisionResult(
                    "route", -0.5, "Test", null);
            assertEquals(0.0, lowResult.getConfidence(), 0.001);
        }

        @Test
        @DisplayName("isConfident returns correct value")
        void testIsConfident() {
            ZaiDecisionReasoner.DecisionResult confident = new ZaiDecisionReasoner.DecisionResult(
                    "route", 0.8, "Test", null);
            assertTrue(confident.isConfident());

            ZaiDecisionReasoner.DecisionResult notConfident = new ZaiDecisionReasoner.DecisionResult(
                    "route", 0.5, "Test", null);
            assertFalse(notConfident.isConfident());
        }
    }

    // =========================================================================
    // DataQualityResult Tests
    // =========================================================================

    @Nested
    @DisplayName("DataQualityResult Tests")
    class DataQualityResultTests {

        @Test
        @DisplayName("Create data quality result")
        void testCreateDataQualityResult() {
            List<String> issues = Arrays.asList("Missing field", "Invalid format");
            ZaiDecisionReasoner.DataQualityResult result = new ZaiDecisionReasoner.DataQualityResult(
                    0.75, "Data has some issues", issues);

            assertEquals(0.75, result.getScore(), 0.001);
            assertEquals("Data has some issues", result.getSummary());
            assertEquals(2, result.getIssues().size());
        }

        @Test
        @DisplayName("isAcceptable returns correct value")
        void testIsAcceptable() {
            ZaiDecisionReasoner.DataQualityResult acceptable = new ZaiDecisionReasoner.DataQualityResult(
                    0.8, "Good", Collections.emptyList());
            assertTrue(acceptable.isAcceptable());

            ZaiDecisionReasoner.DataQualityResult notAcceptable = new ZaiDecisionReasoner.DataQualityResult(
                    0.5, "Poor", Collections.emptyList());
            assertFalse(notAcceptable.isAcceptable());
        }
    }

    // =========================================================================
    // Bottleneck Tests
    // =========================================================================

    @Nested
    @DisplayName("Bottleneck Tests")
    class BottleneckTests {

        @Test
        @DisplayName("Create bottleneck")
        void testCreateBottleneck() {
            ZaiDecisionReasoner.Bottleneck bottleneck = new ZaiDecisionReasoner.Bottleneck(
                    "task-1", "wait_time", "high", "Long wait for approval");

            assertEquals("task-1", bottleneck.getTaskId());
            assertEquals("wait_time", bottleneck.getType());
            assertEquals("high", bottleneck.getSeverity());
            assertEquals("Long wait for approval", bottleneck.getDescription());
        }

        @Test
        @DisplayName("Create bottleneck analysis")
        void testCreateBottleneckAnalysis() {
            List<ZaiDecisionReasoner.Bottleneck> bottlenecks = Arrays.asList(
                    new ZaiDecisionReasoner.Bottleneck("t1", "wait_time", "medium", "Issue 1"),
                    new ZaiDecisionReasoner.Bottleneck("t2", "resource", "high", "Issue 2")
            );
            List<String> recommendations = Arrays.asList("Add parallel path", "Increase resources");

            ZaiDecisionReasoner.BottleneckAnalysis analysis =
                    new ZaiDecisionReasoner.BottleneckAnalysis(bottlenecks, recommendations);

            assertEquals(2, analysis.getBottlenecks().size());
            assertEquals(2, analysis.getRecommendations().size());
            assertTrue(analysis.hasBottlenecks());
        }

        @Test
        @DisplayName("Empty bottleneck analysis")
        void testEmptyBottleneckAnalysis() {
            ZaiDecisionReasoner.BottleneckAnalysis analysis =
                    new ZaiDecisionReasoner.BottleneckAnalysis(null, null);

            assertFalse(analysis.hasBottlenecks());
            assertTrue(analysis.getBottlenecks().isEmpty());
            assertTrue(analysis.getRecommendations().isEmpty());
        }
    }

    // =========================================================================
    // WorkerProfile and Assignment Tests
    // =========================================================================

    @Nested
    @DisplayName("Worker and Assignment Tests")
    class WorkerAssignmentTests {

        @Test
        @DisplayName("Create worker profile")
        void testCreateWorkerProfile() {
            ZaiDecisionReasoner.WorkerProfile worker = new ZaiDecisionReasoner.WorkerProfile(
                    "worker-1", Arrays.asList("java", "workflow"), "senior");

            assertEquals("worker-1", worker.getId());
            assertEquals(2, worker.getSkills().size());
            assertEquals("senior", worker.getLevel());
        }

        @Test
        @DisplayName("Worker profile handles null values")
        void testWorkerProfileNullValues() {
            ZaiDecisionReasoner.WorkerProfile worker = new ZaiDecisionReasoner.WorkerProfile(
                    "worker-1", null, null);

            assertEquals("worker-1", worker.getId());
            assertTrue(worker.getSkills().isEmpty());
            assertEquals("standard", worker.getLevel());
        }

        @Test
        @DisplayName("Create assignment recommendation")
        void testCreateAssignmentRecommendation() {
            ZaiDecisionReasoner.AssignmentRecommendation rec =
                    new ZaiDecisionReasoner.AssignmentRecommendation(
                            "worker-1", 0.9, "Best match for skills required");

            assertEquals("worker-1", rec.getRecommendedWorkerId());
            assertEquals(0.9, rec.getConfidence(), 0.001);
            assertEquals("Best match for skills required", rec.getReasoning());
        }
    }

    // =========================================================================
    // Other Result Classes Tests
    // =========================================================================

    @Nested
    @DisplayName("Other Result Classes Tests")
    class OtherResultTests {

        @Test
        @DisplayName("Completion prediction")
        void testCompletionPrediction() {
            ZaiDecisionReasoner.CompletionPrediction prediction =
                    new ZaiDecisionReasoner.CompletionPrediction(
                            45, 0.8, Arrays.asList("Complex task", "High priority"));

            assertEquals(45, prediction.getEstimatedMinutes());
            assertEquals(0.8, prediction.getConfidence(), 0.001);
            assertEquals(2, prediction.getFactors().size());
        }

        @Test
        @DisplayName("Completion prediction clamps negative minutes")
        void testCompletionPredictionNegativeMinutes() {
            ZaiDecisionReasoner.CompletionPrediction prediction =
                    new ZaiDecisionReasoner.CompletionPrediction(-10, 0.5, null);

            assertEquals(0, prediction.getEstimatedMinutes());
        }

        @Test
        @DisplayName("Transition validation - valid")
        void testTransitionValidationValid() {
            ZaiDecisionReasoner.TransitionValidation validation =
                    new ZaiDecisionReasoner.TransitionValidation(
                            true, "All rules satisfied", Collections.singletonList("Warning: long path"));

            assertTrue(validation.isValid());
            assertEquals("All rules satisfied", validation.getReason());
            assertEquals(1, validation.getWarnings().size());
        }

        @Test
        @DisplayName("Transition validation - invalid")
        void testTransitionValidationInvalid() {
            ZaiDecisionReasoner.TransitionValidation validation =
                    new ZaiDecisionReasoner.TransitionValidation(
                            false, "Missing required field", null);

            assertFalse(validation.isValid());
            assertTrue(validation.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("Optimization suggestion")
        void testOptimizationSuggestion() {
            ZaiDecisionReasoner.OptimizationSuggestion suggestion =
                    new ZaiDecisionReasoner.OptimizationSuggestion(
                            "parallelism", "Split task for parallel execution", "high");

            assertEquals("parallelism", suggestion.getArea());
            assertEquals("Split task for parallel execution", suggestion.getSuggestion());
            assertEquals("high", suggestion.getImpact());
        }
    }

    // =========================================================================
    // Cache Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Tests")
    class CacheTests {

        @Test
        @DisplayName("Clear cache resets size to zero")
        void testClearCache() {
            assertEquals(0, reasoner.getCacheSize());
            reasoner.clearCache();
            assertEquals(0, reasoner.getCacheSize());
        }
    }

    // =========================================================================
    // Audit Log Tests
    // =========================================================================

    @Nested
    @DisplayName("Audit Log Tests")
    class AuditLogTests {

        @Test
        @DisplayName("Initial audit log is empty")
        void testInitialAuditLogEmpty() {
            ZaiService service = new ZaiService(TEST_API_KEY);
            ZaiDecisionReasoner r = new ZaiDecisionReasoner(service);

            assertTrue(r.getAuditLog().isEmpty());
        }

        @Test
        @DisplayName("Audit log is unmodifiable")
        void testAuditLogUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> reasoner.getAuditLog().add(null),
                    "Audit log should be unmodifiable");
        }
    }

    // =========================================================================
    // Input Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("makeRoutingDecision with null context throws")
        void testNullContextThrows() {
            List<ZaiDecisionReasoner.RoutingOption> options = Arrays.asList(
                    new ZaiDecisionReasoner.RoutingOption("r1", "Route 1", "medium")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.makeRoutingDecision(null, options),
                    "Should throw for null context");
        }

        @Test
        @DisplayName("makeRoutingDecision with null options throws")
        void testNullOptionsThrows() {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "case-1", "workflow", "task", "{}");

            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.makeRoutingDecision(context, null),
                    "Should throw for null options");
        }

        @Test
        @DisplayName("makeRoutingDecision with empty options throws")
        void testEmptyOptionsThrows() {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "case-1", "workflow", "task", "{}");

            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.makeRoutingDecision(context, Collections.emptyList()),
                    "Should throw for empty options");
        }

        @Test
        @DisplayName("recommendTaskAssignment with null task throws")
        void testNullTaskThrows() {
            List<ZaiDecisionReasoner.WorkerProfile> workers = Arrays.asList(
                    new ZaiDecisionReasoner.WorkerProfile("w1", Arrays.asList("skill1"), "senior")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.recommendTaskAssignment(null, workers, new HashMap<>()),
                    "Should throw for null task");
        }

        @Test
        @DisplayName("recommendTaskAssignment with empty task throws")
        void testEmptyTaskThrows() {
            List<ZaiDecisionReasoner.WorkerProfile> workers = Arrays.asList(
                    new ZaiDecisionReasoner.WorkerProfile("w1", Arrays.asList("skill1"), "senior")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.recommendTaskAssignment("", workers, new HashMap<>()),
                    "Should throw for empty task");
        }

        @Test
        @DisplayName("recommendTaskAssignment with null workers throws")
        void testNullWorkersThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.recommendTaskAssignment("Test task", null, new HashMap<>()),
                    "Should throw for null workers");
        }

        @Test
        @DisplayName("recommendTaskAssignment with empty workers throws")
        void testEmptyWorkersThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> reasoner.recommendTaskAssignment("Test task", Collections.emptyList(), new HashMap<>()),
                    "Should throw for empty workers");
        }
    }

    // =========================================================================
    // Integration Tests (Require API Key)
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests (API Required)")
    @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
    class IntegrationTests {

        @BeforeEach
        void setUp() {
            assumeTrue(apiAvailable, "API not available");
        }

        @Test
        @DisplayName("Make routing decision returns valid result")
        void testMakeRoutingDecision() throws ZaiDecisionReasoner.DecisionException {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-TEST-1", "OrderProcessing", "ReviewOrder",
                    "{\"amount\": 5000, \"customer\": \"ACME\"}");

            List<ZaiDecisionReasoner.RoutingOption> options = Arrays.asList(
                    new ZaiDecisionReasoner.RoutingOption("approve", "Approve order", "high"),
                    new ZaiDecisionReasoner.RoutingOption("reject", "Reject order", "medium"),
                    new ZaiDecisionReasoner.RoutingOption("review", "Manual review", "low")
            );

            ZaiDecisionReasoner.DecisionResult result = reasoner.makeRoutingDecision(context, options);

            assertNotNull(result, "Should return decision result");
            assertNotNull(result.getSelectedOption(), "Should have selected option");
            assertTrue(result.getConfidence() >= 0 && result.getConfidence() <= 1,
                    "Confidence should be between 0 and 1");
        }

        @Test
        @DisplayName("Assess data quality returns valid result")
        void testAssessDataQuality() throws ZaiDecisionReasoner.DecisionException {
            String data = "{\"name\": \"John\", \"email\": \"john@example.com\", \"age\": 25}";
            String schema = "name required, email required and valid, age between 18 and 65";

            ZaiDecisionReasoner.DataQualityResult result = reasoner.assessDataQuality(data, schema);

            assertNotNull(result, "Should return quality result");
            assertTrue(result.getScore() >= 0 && result.getScore() <= 1,
                    "Score should be between 0 and 1");
        }

        @Test
        @DisplayName("Assess data quality with empty data returns zero score")
        void testAssessDataQualityEmpty() throws ZaiDecisionReasoner.DecisionException {
            ZaiDecisionReasoner.DataQualityResult result = reasoner.assessDataQuality(null, null);

            assertEquals(0.0, result.getScore(), 0.001, "Empty data should have zero score");
        }

        @Test
        @DisplayName("Recommend task assignment returns valid result")
        void testRecommendTaskAssignment() throws ZaiDecisionReasoner.DecisionException {
            List<ZaiDecisionReasoner.WorkerProfile> workers = Arrays.asList(
                    new ZaiDecisionReasoner.WorkerProfile("alice", Arrays.asList("approval", "finance"), "senior"),
                    new ZaiDecisionReasoner.WorkerProfile("bob", Arrays.asList("review", "support"), "junior")
            );
            Map<String, Integer> workload = new HashMap<>();
            workload.put("alice", 3);
            workload.put("bob", 1);

            ZaiDecisionReasoner.AssignmentRecommendation rec = reasoner.recommendTaskAssignment(
                    "Review financial approval request", workers, workload);

            assertNotNull(rec, "Should return recommendation");
            assertNotNull(rec.getRecommendedWorkerId(), "Should recommend a worker");
        }

        @Test
        @DisplayName("Predict completion time returns valid result")
        void testPredictCompletionTime() throws ZaiDecisionReasoner.DecisionException {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-TEST-2", "ApprovalWorkflow", "InitialReview", "{\"priority\": \"high\"}");

            ZaiDecisionReasoner.CompletionPrediction prediction =
                    reasoner.predictCompletionTime(context, null);

            assertNotNull(prediction, "Should return prediction");
            assertTrue(prediction.getEstimatedMinutes() >= 0, "Minutes should be non-negative");
        }

        @Test
        @DisplayName("Validate transition returns valid result")
        void testValidateTransition() throws ZaiDecisionReasoner.DecisionException {
            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-TEST-3", "OrderWorkflow", "Review", "{\"approved\": true}");

            ZaiDecisionReasoner.TransitionValidation validation =
                    reasoner.validateTransition("Review", "Approved", context, "Must be approved");

            assertNotNull(validation, "Should return validation result");
        }

        @Test
        @DisplayName("Analyze bottlenecks returns valid result")
        void testAnalyzeBottlenecks() throws ZaiDecisionReasoner.DecisionException {
            String history = "[{\"task\": \"A\", \"duration\": 100}, {\"task\": \"B\", \"duration\": 5000}]";
            String spec = "Simple workflow with tasks A and B";

            ZaiDecisionReasoner.BottleneckAnalysis analysis =
                    reasoner.analyzeBottlenecks(history, spec);

            assertNotNull(analysis, "Should return analysis");
        }

        @Test
        @DisplayName("Generate optimizations returns suggestions")
        void testGenerateOptimizations() throws ZaiDecisionReasoner.DecisionException {
            String spec = "Sequential workflow with three tasks";
            String metrics = "{\"avgDuration\": 5000, \"throughput\": 100}";

            List<ZaiDecisionReasoner.OptimizationSuggestion> suggestions =
                    reasoner.generateOptimizations(spec, metrics);

            assertNotNull(suggestions, "Should return suggestions list");
        }

        @Test
        @DisplayName("Audit log records decisions")
        void testAuditLogRecordsDecisions() throws ZaiDecisionReasoner.DecisionException {
            reasoner.clearCache();
            int initialSize = reasoner.getAuditLog().size();

            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-AUDIT-1", "TestWorkflow", "Task1", "{}");
            List<ZaiDecisionReasoner.RoutingOption> options = Arrays.asList(
                    new ZaiDecisionReasoner.RoutingOption("r1", "Route 1", "medium")
            );

            reasoner.makeRoutingDecision(context, options);

            assertTrue(reasoner.getAuditLog().size() > initialSize,
                    "Audit log should have new entry");
        }

        @Test
        @DisplayName("Export audit log produces valid JSON")
        void testExportAuditLog() throws Exception {
            String json = reasoner.exportAuditLog();

            assertNotNull(json, "Should produce JSON");
            assertTrue(json.contains("entries"), "Should contain entries field");
            assertTrue(json.contains("totalEntries"), "Should contain totalEntries field");
        }

        @Test
        @DisplayName("Cache stores and retrieves decisions")
        void testDecisionCaching() throws ZaiDecisionReasoner.DecisionException {
            reasoner.clearCache();

            ZaiDecisionReasoner.WorkflowContext context = new ZaiDecisionReasoner.WorkflowContext(
                    "CASE-CACHE-1", "TestWorkflow", "Task1", "{\"test\": \"data\"}");
            List<ZaiDecisionReasoner.RoutingOption> options = Arrays.asList(
                    new ZaiDecisionReasoner.RoutingOption("r1", "Route 1", "medium"),
                    new ZaiDecisionReasoner.RoutingOption("r2", "Route 2", "high")
            );

            // First call
            ZaiDecisionReasoner.DecisionResult result1 = reasoner.makeRoutingDecision(context, options);

            // Second call with same context should potentially use cache
            ZaiDecisionReasoner.DecisionResult result2 = reasoner.makeRoutingDecision(context, options);

            assertEquals(result1.getSelectedOption(), result2.getSelectedOption(),
                    "Cached result should match");
        }
    }

    // =========================================================================
    // DecisionCriteria Tests
    // =========================================================================

    @Nested
    @DisplayName("DecisionCriteria Tests")
    class DecisionCriteriaTests {

        @Test
        @DisplayName("Default criteria has expected values")
        void testDefaultCriteria() {
            ZaiDecisionReasoner.DecisionCriteria criteria = ZaiDecisionReasoner.DecisionCriteria.DEFAULT;

            assertFalse(criteria.isHighStakes());
            assertNull(criteria.getAdditionalContext());
            assertTrue(criteria.getConstraints().isEmpty());
        }

        @Test
        @DisplayName("Custom criteria with all fields")
        void testCustomCriteria() {
            Map<String, Object> constraints = new HashMap<>();
            constraints.put("maxCost", 1000);

            ZaiDecisionReasoner.DecisionCriteria criteria = new ZaiDecisionReasoner.DecisionCriteria(
                    true, "Consider weekend hours", constraints);

            assertTrue(criteria.isHighStakes());
            assertEquals("Consider weekend hours", criteria.getAdditionalContext());
            assertEquals(1000, criteria.getConstraints().get("maxCost"));
        }
    }

    // =========================================================================
    // DecisionException Tests
    // =========================================================================

    @Nested
    @DisplayName("DecisionException Tests")
    class DecisionExceptionTests {

        @Test
        @DisplayName("Create exception with message")
        void testExceptionWithMessage() {
            ZaiDecisionReasoner.DecisionException ex =
                    new ZaiDecisionReasoner.DecisionException("Test error");

            assertEquals("Test error", ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("Create exception with message and cause")
        void testExceptionWithCause() {
            RuntimeException cause = new RuntimeException("Root cause");
            ZaiDecisionReasoner.DecisionException ex =
                    new ZaiDecisionReasoner.DecisionException("Test error", cause);

            assertEquals("Test error", ex.getMessage());
            assertEquals(cause, ex.getCause());
        }
    }
}
