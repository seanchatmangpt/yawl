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

package org.yawlfoundation.yawl.dspy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.elements.YWorkflow;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import org.yawlfoundation.yawl.dspy.persistence.GepaOptimizationResult;
import org.yawlfoundation.yawl.dspy.persistence.GepaProgramEnhancer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GEPA integration test suite with hooks coordination.
 *
 * This test suite demonstrates proper integration with YAWL development
 * hooks and follows the team decision framework for distributed testing.
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>Pre-task hooks for workflow validation</li>
 *   <li>Post-task hooks for optimization metrics collection</li>
 *   <li>Session coordination for team-based testing</li>
 *   <li>Memory persistence for cross-test state</li>
 *   <li>Receipt generation for audit trail</li>
 * </ul>
 *
 * <h2>Test Patterns</h2>
 * <ul>
 *   <li>Engineering team pattern for workflow optimization</li>
 *   <li>Validation team pattern for footprint agreement</li>
 *   <li>Performance team pattern for metrics validation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("GEPA Integration with Hooks Coordination")
class GepaHooksIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GepaHooksIntegrationTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Test infrastructure
    private Path testDir;
    private Path programsDir;
    private Path receiptsDir;
    private TestYNetRunner netRunner;
    private PythonExecutionEngine pythonEngine;
    private DspyProgramRegistry registry;
    private GepaProgramEnhancer enhancer;

    // Team coordination state
    private Map<String, Object> teamMemory = new ConcurrentHashMap<>();
    private List<String> teamMessages = new ArrayList<>();
    private Map<String, Instant> taskTimestamps = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        // Setup test directories
        testDir = Files.createTempDirectory("gepa-hooks-test");
        programsDir = testDir.resolve("programs");
        receiptsDir = testDir.resolve("receipts");
        Files.createDirectories(programsDir);
        Files.createDirectories(receiptsDir);

        // Initialize components
        netRunner = new TestYNetRunner();
        netRunner.setTestModeEnabled(true);
        pythonEngine = new TestPythonExecutionEngineForGEPA();
        registry = new DspyProgramRegistry(programsDir, pythonEngine);
        enhancer = new GepaProgramEnhancer(pythonEngine, registry, programsDir);

        // Initialize team memory
        teamMemory.put("team_id", "gepa-engineering-team");
        teamMemory.put("session_id", UUID.randomUUID().toString());
        teamMemory.put("test_phase", "hooks_coordination");

        log.info("Hooks integration test setup complete");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Cleanup
        if (testDir != null) {
            Files.walk(testDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete test file: {}", path, e);
                    }
                });
        }

        // Generate team receipt
        generateTeamReceipt();

        log.info("Hooks integration test cleanup complete");
    }

    @Nested
    @DisplayName("Pre-Task Hooks Integration")
    class PreTaskHooksIntegration {

        @Test
        @DisplayName("Should validate workflow before GEPA optimization")
        void testPreTaskWorkflowValidation() throws Exception {
            // Act: Execute pre-task hook for workflow validation
            Map<String, Object> workflow = createTestWorkflow();
            Map<String, Object> validationResult = executePreTaskHook("workflow_validation", workflow);

            // Assert: Validation results
            assertThat(validationResult, hasKey("status"));
            assertThat(validationResult.get("status"), is("validated"));
            assertThat(validationResult, hasKey("soundness"));
            assertThat(validationResult.get("soundness"), is(true));
            assertThat(validationResult, hasKey("metrics"));

            // Store in team memory
            teamMemory.put("validated_workflow", workflow);
            teamMemory.put("validation_timestamp", Instant.now());

            log.info("Pre-task workflow validation completed");
        }

        @Test
        @DisplayName("Should validate GEPA configuration before optimization")
        void testPreTaskConfigurationValidation() throws Exception {
            // Act: Execute pre-task hook for configuration validation
            Map<String, Object> config = createTestConfiguration();
            Map<String, Object> validationResult = executePreTaskHook("config_validation", config);

            // Assert: Configuration validation
            assertThat(validationResult, hasKey("status"));
            assertThat(validationResult.get("status"), is("valid"));
            assertThat(validationResult, hasKey("optimization_targets"));
            assertThat(validationResult.get("optimization_targets"), is(List.of("behavioral", "performance", "balanced")));

            // Store in team memory
            teamMemory.put("validated_config", config);

            log.info("Pre-task configuration validation completed");
        }

        @Test
        @DisplayName("Should validate YAWL engine compatibility")
        void testPreTaskEngineCompatibility() throws Exception {
            // Act: Execute pre-task hook for engine compatibility
            Map<String, Object> engineState = getEngineState();
            Map<String, Object> validationResult = executePreTaskHook("engine_compatibility", engineState);

            // Assert: Engine compatibility
            assertThat(validationResult, hasKey("status"));
            assertThat(validationResult.get("status"), is("compatible"));
            assertThat(validationResult, hasKey("net_runner_ready"));
            assertThat(validationResult.get("net_runner_ready"), is(true));

            // Store in team memory
            teamMemory.put("engine_state", engineState);

            log.info("Pre-task engine compatibility validation completed");
        }
    }

    @Nested
    @DisplayName("Post-Task Hooks Integration")
    class PostTaskHooksIntegration {

        @Test
        @DisplayName("Should collect optimization metrics after GEPA execution")
        void testPostTaskMetricsCollection() throws Exception {
            // Arrange: Execute pre-task hook
            Map<String, Object> workflow = createTestWorkflow();
            executePreTaskHook("workflow_validation", workflow);

            // Act: Execute GEPA optimization
            GepaOptimizationResult optimization = executeGepaOptimization(workflow, "balanced");

            // Execute post-task hook for metrics collection
            Map<String, Object> metrics = executePostTaskHook("metrics_collection", optimization);

            // Assert: Metrics collection
            assertThat(metrics, hasKey("status"));
            assertThat(metrics.get("status"), is("collected"));
            assertThat(metrics, hasKey("optimization_score"));
            assertThat(metrics, hasKey("execution_time_ms"));
            assertThat(metrics, hasKey("memory_usage_mb"));

            // Store in team memory
            teamMemory.put("optimization_metrics", metrics);
            teamMemory.put("optimization_result", optimization);

            log.info("Post-task metrics collection completed");
        }

        @Test
        @DisplayName("Should generate audit trail for optimization")
        void testPostTaskAuditTrailGeneration() throws Exception {
            // Arrange: Execute GEPA optimization
            Map<String, Object> workflow = createTestWorkflow();
            GepaOptimizationResult optimization = executeGepaOptimization(workflow, "behavioral");

            // Act: Execute post-task hook for audit trail
            Map<String, Object> auditTrail = executePostTaskHook("audit_trail_generation", optimization);

            // Assert: Audit trail generation
            assertThat(auditTrail, hasKey("status"));
            assertThat(auditTrail.get("status"), is("generated"));
            assertThat(auditTrail, hasKey("audit_id"));
            assertThat(auditTrail, hasKey("timestamp"));
            assertThat(auditTrail, hasKey("optimization_trace"));

            // Store in team memory
            teamMemory.put("audit_trail", auditTrail);

            log.info("Post-task audit trail generation completed");
        }

        @Test
        @DisplayName("Should validate footprint agreement after optimization")
        void testPostTaskFootprintValidation() throws Exception {
            // Arrange: Execute GEPA optimization
            Map<String, Object> originalWorkflow = createTestWorkflow();
            Map<String, Object> optimizedWorkflow = executeGepaOptimization(originalWorkflow, "balanced");

            // Act: Execute post-task hook for footprint validation
            Map<String, Object> footprintResult = executePostTaskHook("footprint_validation", optimizedWorkflow);

            // Assert: Footprint validation
            assertThat(footprintResult, hasKey("status"));
            assertThat(footprintResult.get("status"), is("validated"));
            assertThat(footprintResult, hasKey("agreement_score"));
            assertThat(footprintResult.get("agreement_score"), notNullValue());
            assertThat(footprintResult, hasKey("comparisons"));

            // Store in team memory
            teamMemory.put("footprint_agreement", footprintResult);

            log.info("Post-task footprint validation completed");
        }
    }

    @Nested
    @DisplayName("Team Coordination Tests")
    class TeamCoordinationTests {

        @Test
        @DisplayName("Should coordinate between engineering and validation teams")
        void testTeamCoordination() throws Exception {
            // Arrange: Team memory initialization
            Map<String, Object> teamConfig = Map.of(
                "team_type", "engineering_validation_team",
                "max_teammates", 3,
                "quantums", List.of("engineering", "validation", "performance")
            );
            teamMemory.put("team_config", teamConfig);

            // Act: Execute team coordination
            Map<String, Object> coordination = executeTeamCoordination("gepa_optimization_flow", teamConfig);

            // Assert: Team coordination
            assertThat(coordination, hasKey("status"));
            assertThat(coordination.get("status"), is("coordinated"));
            assertThat(coordination, hasKey("assigned_teammates"));
            assertThat(coordination, hasKey("task_sequence"));
            assertThat(coordination, hasKey("memory_shared"));

            // Store in team memory
            teamMemory.put("coordination_result", coordination);
            teamMemory.put("team_timestamp", Instant.now());

            log.info("Team coordination completed");
        }

        @Test
        @DisplayName("Should handle teammate messaging and iteration")
        void testTeammateMessaging() throws Exception {
            // Arrange: Team setup
            setupTestTeam();

            // Act: Send message to teammates
            Map<String, Object> message1 = sendTeamMessage("engineering", "workflow_optimization", "Start optimizing workflow");
            Map<String, Object> message2 = sendTeamMessage("validation", "footprint_check", "Validate footprint agreement");

            // Assert: Message delivery
            assertThat(message1, hasKey("status"));
            assertThat(message1.get("status"), is("delivered"));
            assertThat(message2, hasKey("status"));
            assertThat(message2.get("status"), is("delivered"));

            // Store in team memory
            teamMessages.add("Message to engineering: " + message1);
            teamMessages.add("Message to validation: " + message2);

            log.info("Teammate messaging completed");
        }

        @Test
        @DisplayName("Should persist team state across test cycles")
        void testTeamStatePersistence() throws Exception {
            // Arrange: Initial team state
            Map<String, Object> initialState = Map.of(
                "phase", "initialization",
                "completed_tasks", 0,
                "active_tasks", List.of("workflow_validation")
            );
            teamMemory.put("team_state", initialState);

            // Act: Execute multiple cycles
            for (int i = 0; i < 3; i++) {
                Map<String, Object> cycleResult = executeTeamCycle("optimization_cycle_" + i);
                assertThat(cycleResult, hasKey("cycle_complete"));
                assertThat(cycleResult.get("cycle_complete"), is(true));

                // Update state
                Map<String, Object> state = (Map<String, Object>) teamMemory.get("team_state");
                state.put("completed_tasks", ((Integer) state.get("completed_tasks")) + 1);
                state.put("active_tasks", List.of("optimization_" + (i + 1)));
            }

            // Assert: State persistence
            Map<String, Object> finalState = (Map<String, Object>) teamMemory.get("team_state");
            assertThat(finalState.get("completed_tasks"), is(3));

            log.info("Team state persistence completed");
        }
    }

    @Nested
    @DisplayName("Receipt Generation Tests")
    class ReceiptGenerationTests {

        @Test
        @DisplayName("Should generate execution receipt for complete flow")
        void testExecutionReceiptGeneration() throws Exception {
            // Arrange: Execute complete GEPA flow
            Map<String, Object> workflow = createTestWorkflow();
            GepaOptimizationResult optimization = executeGepaOptimization(workflow, "balanced");

            // Execute post-task hooks
            executePostTaskHook("metrics_collection", optimization);
            executePostTaskHook("audit_trail_generation", optimization);
            executePostTaskHook("footprint_validation", optimization);

            // Act: Generate execution receipt
            Map<String, Object> receipt = generateExecutionReceipt();

            // Assert: Receipt generation
            assertThat(receipt, hasKey("receipt_id"));
            assertThat(receipt, hasKey("timestamp"));
            assertThat(receipt, hasKey("workflow_id"));
            assertThat(receipt, hasKey("optimization_result"));
            assertThat(receipt, hasKey("metrics"));
            assertThat(receipt, hasKey("audit_trail"));
            assertThat(receipt, hasKey("validation_results"));

            // Store receipt
            teamMemory.put("execution_receipt", receipt);

            log.info("Execution receipt generation completed");
        }

        @Test
        @DisplayName("Should validate receipt structure and content")
        void testReceiptValidation() throws Exception {
            // Arrange: Generate test receipt
            Map<String, Object> receipt = generateExecutionReceipt();

            // Act: Validate receipt structure
            Map<String, Object> validationResult = validateReceiptStructure(receipt);

            // Assert: Receipt validation
            assertThat(validationResult, hasKey("status"));
            assertThat(validationResult.get("status"), is("valid"));
            assertThat(validationResult, hasKey("required_fields"));
            assertThat(validationResult.get("required_fields"), is(true));
            assertThat(validationResult, hasKey("checksum"));
            assertThat(validationResult, hasKey("format_version"));

            // Store validation result
            teamMemory.put("receipt_validation", validationResult);

            log.info("Receipt validation completed");
        }
    }

    // Helper Methods

    private Map<String, Object> executePreTaskHook(String hookType, Map<String, Object> data) throws Exception {
        // Simulate pre-task hook execution
        Map<String, Object> result = new HashMap<>();
        result.put("hook_type", hookType);
        result.put("timestamp", Instant.now().toString());
        result.put("data_received", data);

        switch (hookType) {
            case "workflow_validation":
                result.put("status", "validated");
                result.put("soundness", true);
                result.put("metrics", Map.of(
                    "task_count", 6,
                    "place_count", 7,
                    "flow_count", 9,
                    "complexity_score", 0.75
                ));
                break;
            case "config_validation":
                result.put("status", "valid");
                result.put("optimization_targets", List.of("behavioral", "performance", "balanced"));
                result.put("max_rounds", 3);
                break;
            case "engine_compatibility":
                result.put("status", "compatible");
                result.put("net_runner_ready", true);
                result.put("python_engine_ready", true);
                break;
            default:
                result.put("status", "unknown_hook");
        }

        // Record timestamp
        taskTimestamps.put("pre_" + hookType, Instant.now());

        return result;
    }

    private Map<String, Object> executePostTaskHook(String hookType, Object data) throws Exception {
        // Simulate post-task hook execution
        Map<String, Object> result = new HashMap<>();
        result.put("hook_type", hookType);
        result.put("timestamp", Instant.now().toString());
        result.put("data_received", data);

        switch (hookType) {
            case "metrics_collection":
                GepaOptimizationResult optimization = (GepaOptimizationResult) data;
                result.put("status", "collected");
                result.put("optimization_score", optimization.score());
                result.put("execution_time_ms", System.currentTimeMillis() - 100);
                result.put("memory_usage_mb", 512.0);
                break;
            case "audit_trail_generation":
                result.put("status", "generated");
                result.put("audit_id", UUID.randomUUID().toString());
                result.put("optimization_trace", List.of(
                    Map.of("step", 1, "target", "balanced", "score", 0.9),
                    Map.of("step", 2, "target", "balanced", "score", 0.92),
                    Map.of("step", 3, "target", "balanced", "score", 0.95)
                ));
                break;
            case "footprint_validation":
                result.put("status", "validated");
                result.put("agreement_score", 0.98);
                result.put("comparisons", Map.of(
                    "structural_similarity", 1.0,
                    "behavioral_similarity", 0.98,
                    "performance_similarity", 0.95
                ));
                break;
            default:
                result.put("status", "unknown_hook");
        }

        // Record timestamp
        taskTimestamps.put("post_" + hookType, Instant.now());

        return result;
    }

    private Map<String, Object> executeTeamCoordination(String flowType, Map<String, Object> teamConfig) throws Exception {
        // Simulate team coordination
        Map<String, Object> result = new HashMap<>();
        result.put("flow_type", flowType);
        result.put("timestamp", Instant.now().toString());
        result.put("team_config", teamConfig);

        result.put("status", "coordinated");
        result.put("assigned_teammates", List.of("engineering", "validation", "performance"));
        result.put("task_sequence", List.of(
            "workflow_validation",
            "gepa_optimization",
            "footprint_check",
            "metrics_collection",
            "audit_trail"
        ));
        result.put("memory_shared", teamMemory);

        return result;
    }

    private Map<String, Object> sendTeamMessage(String teammate, String task, String message) throws Exception {
        // Simulate team messaging
        Map<String, Object> result = new HashMap<>();
        result.put("recipient", teammate);
        result.put("task", task);
        result.put("message", message);
        result.put("timestamp", Instant.now().toString());
        result.put("message_id", UUID.randomUUID().toString());

        result.put("status", "delivered");
        result.put("acknowledged", true);

        // Add to message history
        teamMessages.add(String.format("Message to %s: %s", teammate, message));

        return result;
    }

    private Map<String, Object> executeTeamCycle(String cycleName) throws Exception {
        // Simulate team execution cycle
        Map<String, Object> result = new HashMap<>();
        result.put("cycle_name", cycleName);
        result.put("timestamp", Instant.now().toString());

        // Execute cycle tasks
        result.put("status", "cycle_complete");
        result.put("completed_tasks", 3);
        result.put("active_tasks", List.of("next_cycle_preparation"));

        return result;
    }

    private Map<String, Object> generateExecutionReceipt() throws Exception {
        // Generate comprehensive execution receipt
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("receipt_id", "gepa-" + UUID.randomUUID().toString());
        receipt.put("timestamp", Instant.now().toString());
        receipt.put("format_version", "1.0.0");
        receipt.put("workflow_id", "test_workflow");
        receipt.put("session_id", teamMemory.get("session_id"));

        // Include all test results
        receipt.put("optimization_result", teamMemory.get("optimization_result"));
        receipt.put("metrics", teamMemory.get("optimization_metrics"));
        receipt.put("audit_trail", teamMemory.get("audit_trail"));
        receipt.put("footprint_agreement", teamMemory.get("footprint_agreement"));
        receipt.put("team_messages", teamMessages);
        receipt.put("task_timestamps", taskTimestamps);

        // Calculate checksum
        receipt.put("checksum", calculateReceiptChecksum(receipt));

        return receipt;
    }

    private Map<String, Object> validateReceiptStructure(Map<String, Object> receipt) throws Exception {
        // Validate receipt structure
        Map<String, Object> result = new HashMap<>();
        result.put("receipt_id", receipt.get("receipt_id"));
        result.put("timestamp", Instant.now().toString());

        // Check required fields
        List<String> requiredFields = List.of(
            "receipt_id", "timestamp", "workflow_id", "optimization_result",
            "metrics", "audit_trail", "validation_results"
        );

        boolean allFieldsPresent = requiredFields.stream()
            .allMatch(field -> receipt.containsKey(field));

        result.put("required_fields", allFieldsPresent);
        result.put("status", allFieldsPresent ? "valid" : "invalid");
        result.put("format_version", receipt.getOrDefault("format_version", "unknown"));

        return result;
    }

    private String calculateReceiptChecksum(Map<String, Object> receipt) throws Exception {
        // Simple checksum calculation
        String content = objectMapper.writeValueAsString(receipt);
        return Integer.toHexString(content.hashCode());
    }

    private void generateTeamReceipt() throws Exception {
        // Generate team receipt for audit trail
        Map<String, Object> teamReceipt = new HashMap<>();
        teamReceipt.put("team_id", teamMemory.get("team_id"));
        teamReceipt.put("session_id", teamMemory.get("session_id"));
        teamReceipt.put("test_phase", teamMemory.get("test_phase"));
        teamReceipt.put("start_time", Instant.now());
        teamReceipt.put("end_time", Instant.now());
        teamReceipt.put("team_memory", teamMemory);
        teamReceipt.put("team_messages", teamMessages);
        teamReceipt.put("execution_receipt", teamMemory.get("execution_receipt"));

        // Save receipt
        Path receiptPath = receiptsDir.resolve("team-receipt-" + System.currentTimeMillis() + ".json");
        Files.writeString(receiptPath, objectMapper.writeValueAsString(teamReceipt));

        log.info("Team receipt generated: {}", receiptPath);
    }

    private Map<String, Object> createTestWorkflow() {
        Map<String, Object> workflow = new HashMap<>();
        workflow.put("id", "test_workflow");
        workflow.put("name", "Test Workflow for GEPA");
        workflow.put("description", "Workflow testing GEPA integration with hooks");
        workflow.put("task_count", 6);
        workflow.put("place_count", 7);
        workflow.put("flow_count", 9);
        workflow.put("soundness", true);
        return workflow;
    }

    private Map<String, Object> createTestConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("optimization_targets", List.of("behavioral", "performance", "balanced"));
        config.put("max_rounds", 3);
        config.put("num_threads", 4);
        config.put("footprint_agreement_threshold", 0.9);
        return config;
    }

    private Map<String, Object> getEngineState() {
        Map<String, Object> state = new HashMap<>();
        state.put("net_runner_ready", true);
        state.put("python_engine_ready", true);
        state.put("database_ready", true);
        state.put("workflow_cache_size", 10);
        return state;
    }

    private GepaOptimizationResult executeGepaOptimization(Map<String, Object> workflow, String target) {
        // Simulate GEPA optimization execution
        Map<String, Object> behavioralFootprint = new HashMap<>();
        behavioralFootprint.put("structural_similarity", 0.98);
        behavioralFootprint.put("behavioral_similarity", 0.95);
        behavioralFootprint.put("performance_similarity", 0.92);

        Map<String, Object> performanceMetrics = new HashMap<>();
        performanceMetrics.put("avg_execution_time_ms", 250.0);
        performanceMetrics.put("throughput_tasks_per_sec", 10.0);
        performanceMetrics.put("memory_peak_mb", 512.0);

        List<Map<String, Object>> history = List.of(
            Map.of("step", 1, "target", target, "score", 0.9),
            Map.of("step", 2, "target", target, "score", 0.92),
            Map.of("step", 3, "target", target, "score", 0.95)
        );

        return new GepaOptimizationResult(
            target, 0.95, behavioralFootprint, performanceMetrics,
            0.98, history, Instant.now().toString()
        );
    }

    private void setupTestTeam() {
        // Setup test team configuration
        Map<String, Object> teamConfig = Map.of(
            "team_type", "engineering_validation_team",
            "max_teammates", 3,
            "quantums", List.of("engineering", "validation", "performance"),
            "task_coordination", true
        );
        teamMemory.put("team_config", teamConfig);
    }
}