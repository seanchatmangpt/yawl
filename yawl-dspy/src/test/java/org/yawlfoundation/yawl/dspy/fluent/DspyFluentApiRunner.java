/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.fluent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating all 5 DSPy programs via the Java Fluent API.
 *
 * <p>This test validates the JOR4J (Java > OTP > Rust/Python > OTP > Java) integration
 * by running real DSPy programs through the fluent API.
 *
 * <h2>Programs Tested:</h2>
 * <ol>
 *   <li><b>POWL Generator</b> - NL → POWL workflow</li>
 *   <li><b>Resource Router</b> - Task → Agent allocation</li>
 *   <li><b>Anomaly Forensics</b> - Anomaly → Root cause</li>
 *   <li><b>Runtime Adapter</b> - Metrics → Optimization</li>
 *   <li><b>Worklet Selector</b> - Context → Worklet</li>
 * </ol>
 *
 * <h2>Running Tests:</h2>
 * <pre>{@code
 * # With Groq API key
 * export GROQ_API_KEY=your_key_here
 * mvn test -Dtest=DspyFluentApiRunner -pl yawl-dspy
 *
 * # Or skip LLM tests
 * mvn test -Dtest=DspyFluentApiRunner -pl yawl-dspy -DskipLLMTests=true
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DSPy Fluent API Integration Tests")
class DspyFluentApiRunner {

    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");
    private static final boolean SKIP_LLM_TESTS =
        "true".equalsIgnoreCase(System.getProperty("skipLLMTests"));

    @BeforeAll
    static void configureDspy() {
        if (!SKIP_LLM_TESTS && GROQ_API_KEY != null && !GROQ_API_KEY.isBlank()) {
            Dspy.configure(lm -> lm
                .model("groq/gpt-oss-20b")
                .apiKey(GROQ_API_KEY)
                .temperature(0.0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. POWL GENERATOR - NL → POWL workflow
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. POWL Generator")
    class PowlGeneratorTests {

        @Test
        @DisplayName("Should create POWL generator module")
        void shouldCreatePowlGenerator() {
            DspyModule generator = Dspy.powlGenerator();

            assertNotNull(generator);
            assertEquals(DspyModule.Type.PREDICT, generator.type());
            assertNotNull(generator.signature());
            assertTrue(generator.signature().inputNames().contains("workflow_description"));
            assertTrue(generator.signature().outputNames().contains("powl_json"));
        }

        @Test
        @DisplayName("Should generate POWL from simple workflow")
        @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
        void shouldGeneratePowlFromSimpleWorkflow() {
            if (SKIP_LLM_TESTS) return;

            DspyModule generator = Dspy.powlGenerator()
                .withExample(Dspy.example()
                    .input("workflow_description", "Submit order then process payment")
                    .output("powl_json", "{\"net\": {\"activities\": [\"Submit Order\", \"Process Payment\"]}}")
                    .build());

            DspyResult result = generator.predict(
                "workflow_description",
                "Customer submits order, then system processes payment, then ships product"
            );

            assertNotNull(result);
            assertTrue(result.has("powl_json"));
            String powlJson = result.getString("powl_json");
            assertNotNull(powlJson);
            assertTrue(powlJson.length() > 10, "POWL JSON should have content");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. RESOURCE ROUTER - Task → Agent allocation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Resource Router")
    class ResourceRouterTests {

        @Test
        @DisplayName("Should create resource router module")
        void shouldCreateResourceRouter() {
            DspyModule router = Dspy.resourceRouter();

            assertNotNull(router);
            assertEquals(DspyModule.Type.PREDICT, router.type());
            assertTrue(router.signature().inputNames().contains("task_context"));
            assertTrue(router.signature().inputNames().contains("available_agents"));
            assertTrue(router.signature().outputNames().contains("recommended_agent"));
            assertTrue(router.signature().outputNames().contains("confidence"));
        }

        @Test
        @DisplayName("Should route task to best agent")
        @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
        void shouldRouteTaskToBestAgent() {
            if (SKIP_LLM_TESTS) return;

            DspyModule router = Dspy.resourceRouter();

            DspyResult result = router.predict(
                "task_context", "Complex data analysis requiring Python and ML expertise",
                "available_agents", List.of(
                    Map.of("id", "agent-1", "skills", List.of("java", "sql")),
                    Map.of("id", "agent-2", "skills", List.of("python", "ml", "data-analysis")),
                    Map.of("id", "agent-3", "skills", List.of("javascript", "ui"))
                )
            );

            assertNotNull(result);
            assertTrue(result.has("recommended_agent"));
            String agent = result.getString("recommended_agent");
            assertNotNull(agent);
            // Should recommend agent-2 based on ML and Python skills
            assertTrue(agent.contains("agent-2") || agent.contains("2"),
                "Should recommend agent with ML/Python skills");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. ANOMALY FORENSICS - Anomaly → Root cause
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Anomaly Forensics")
    class AnomalyForensicsTests {

        @Test
        @DisplayName("Should create anomaly forensics module with CoT")
        void shouldCreateAnomalyForensics() {
            DspyModule forensics = Dspy.anomalyForensics();

            assertNotNull(forensics);
            assertEquals(DspyModule.Type.CHAIN_OF_THOUGHT, forensics.type());
            assertTrue(forensics.signature().inputNames().contains("anomaly_data"));
            assertTrue(forensics.signature().outputNames().contains("root_cause"));
            assertTrue(forensics.signature().outputNames().contains("remediation_steps"));
        }

        @Test
        @DisplayName("Should analyze anomaly and suggest remediation")
        @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
        void shouldAnalyzeAnomaly() {
            if (SKIP_LLM_TESTS) return;

            DspyModule forensics = Dspy.anomalyForensics();

            DspyResult result = forensics.predict(
                "anomaly_data", Map.of(
                    "type", "timeout",
                    "service", "payment-gateway",
                    "occurrences", 47,
                    "time_window", "last_hour",
                    "error_rate", 0.23
                )
            );

            assertNotNull(result);
            assertTrue(result.has("root_cause"));
            assertTrue(result.has("remediation_steps"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. RUNTIME ADAPTER - Metrics → Optimization
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Runtime Adapter")
    class RuntimeAdapterTests {

        @Test
        @DisplayName("Should create runtime adapter module")
        void shouldCreateRuntimeAdapter() {
            DspyModule adapter = Dspy.runtimeAdapter();

            assertNotNull(adapter);
            assertEquals(DspyModule.Type.PREDICT, adapter.type());
            assertTrue(adapter.signature().inputNames().contains("process_metrics"));
            assertTrue(adapter.signature().outputNames().contains("optimization_recommendations"));
        }

        @Test
        @DisplayName("Should suggest optimizations from metrics")
        @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
        void shouldSuggestOptimizations() {
            if (SKIP_LLM_TESTS) return;

            DspyModule adapter = Dspy.runtimeAdapter();

            DspyResult result = adapter.predict(
                "process_metrics", Map.of(
                    "avg_case_duration_ms", 45000,
                    "bottleneck_task", "Data Validation",
                    "resource_utilization", 0.85,
                    "throughput_per_hour", 120
                )
            );

            assertNotNull(result);
            assertTrue(result.has("optimization_recommendations"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. WORKLET SELECTOR - Context → Worklet
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Worklet Selector")
    class WorkletSelectorTests {

        @Test
        @DisplayName("Should create worklet selector module")
        void shouldCreateWorkletSelector() {
            DspyModule selector = Dspy.workletSelector();

            assertNotNull(selector);
            assertEquals(DspyModule.Type.PREDICT, selector.type());
            assertTrue(selector.signature().inputNames().contains("work_item_context"));
            assertTrue(selector.signature().outputNames().contains("recommended_worklet"));
        }

        @Test
        @DisplayName("Should select appropriate worklet")
        @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
        void shouldSelectWorklet() {
            if (SKIP_LLM_TESTS) return;

            DspyModule selector = Dspy.workletSelector()
                .withExamples(List.of(
                    Dspy.example()
                        .input("work_item_context", "Emergency order cancellation")
                        .output("recommended_worklet", "cancellation-urgent")
                        .build(),
                    Dspy.example()
                        .input("work_item_context", "Standard refund request")
                        .output("recommended_worklet", "refund-standard")
                        .build()
                ));

            DspyResult result = selector.predict(
                "work_item_context", "Customer needs urgent priority handling for delayed shipment"
            );

            assertNotNull(result);
            assertTrue(result.has("recommended_worklet"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FLUENT API TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fluent API")
    class FluentApiTests {

        @Test
        @DisplayName("Should create signature from shorthand")
        void shouldCreateSignatureFromShorthand() {
            DspySignature sig = Dspy.signature("question -> answer");

            assertNotNull(sig);
            assertEquals(List.of("question"), sig.inputNames());
            assertEquals(List.of("answer"), sig.outputNames());
        }

        @Test
        @DisplayName("Should create signature with builder")
        void shouldCreateSignatureWithBuilder() {
            DspySignature sig = Dspy.signatureBuilder()
                .description("Predict answer to question")
                .input("question", "The question to answer")
                .output("answer", "The predicted answer")
                .output("confidence", "Confidence score 0-1")
                .build();

            assertNotNull(sig);
            assertEquals("Predict answer to question", sig.description());
            assertEquals(1, sig.inputs().size());
            assertEquals(2, sig.outputs().size());
        }

        @Test
        @DisplayName("Should create and configure LM")
        void shouldShouldCreateAndConfigureLm() {
            DspyLM lm = Dspy.lm()
                .model("groq/gpt-oss-20b")
                .apiKey("test-key")
                .temperature(0.0)
                .maxTokens(256)
                .build();

            assertNotNull(lm);
            assertEquals("groq/gpt-oss-20b", lm.model());
            assertEquals("test-key", lm.apiKey());
            assertEquals(0.0, lm.temperature());
            assertEquals(256, lm.maxTokens());
            assertEquals("groq", lm.provider());
            assertEquals("gpt-oss-20b", lm.modelName());
        }

        @Test
        @DisplayName("Should create examples")
        void shouldCreateExamples() {
            DspyExample example = Dspy.example()
                .input("question", "What is 2+2?")
                .output("answer", "4")
                .build();

            assertNotNull(example);
            assertEquals("What is 2+2?", example.inputString("question"));
            assertEquals("4", example.outputString("answer"));
        }

        @Test
        @DisplayName("Should chain module transformations")
        void shouldChainModuleTransformations() {
            DspyModule module = Dspy.predict("question -> answer")
                .withExample(Dspy.example()
                    .input("question", "What is YAWL?")
                    .output("answer", "Workflow language")
                    .build())
                .withTemperature(0.5)
                .withMaxTokens(128);

            assertNotNull(module);
            assertEquals(1, module.examples().size());
        }

        @Test
        @DisplayName("Should access result values")
        void shouldAccessResultValues() {
            DspyResult result = DspyResult.of(
                "answer", "42",
                "confidence", 0.95,
                "tags", List.of("math", "basic")
            );

            assertTrue(result.has("answer"));
            assertEquals("42", result.getString("answer"));
            assertEquals(0.95, result.getDouble("confidence"));
            assertNotNull(result.getList("tags"));
            assertEquals(2, result.getList("tags").size());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN METHOD FOR MANUAL TESTING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Run all 5 DSPy programs and print results.
     *
     * <p>Usage: {@code java -Denv.GROQ_API_KEY=your_key DspyFluentApiRunner}
     */
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║       DSPy Fluent API - JOR4J Integration Demo                ║");
        System.out.println("║       Java > OTP > Python (dspy==3.1.3) > OTP > Java          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        if (GROQ_API_KEY == null || GROQ_API_KEY.isBlank()) {
            System.err.println("ERROR: GROQ_API_KEY environment variable not set");
            System.err.println("Set it with: export GROQ_API_KEY=your_key_here");
            System.exit(1);
        }

        configureDspy();

        System.out.println("✓ DSPy configured with: " + Dspy.getConfiguredLM().model());
        System.out.println();

        // 1. POWL Generator
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("1. POWL Generator (NL → POWL Workflow)");
        System.out.println("══════════════════════════════════════════════════════════════════");

        DspyModule powlGen = Dspy.powlGenerator();
        DspyResult powlResult = powlGen.predict(
            "workflow_description",
            "Customer places order, payment is processed, " +
            "if approved the order ships, otherwise order is cancelled"
        );
        System.out.println("Input: Customer places order, payment is processed...");
        System.out.println("Output: " + powlResult.getString("powl_json"));
        System.out.println("Latency: " + powlResult.latencyMs() + "ms");
        System.out.println();

        // 2. Resource Router
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("2. Resource Router (Task → Agent Allocation)");
        System.out.println("══════════════════════════════════════════════════════════════════");

        DspyModule router = Dspy.resourceRouter();
        DspyResult routerResult = router.predict(
            "task_context", "Machine learning model training with large dataset",
            "available_agents", "[agent-1: java,sql] [agent-2: python,ml] [agent-3: js,ui]"
        );
        System.out.println("Input: ML model training task");
        System.out.println("Recommended Agent: " + routerResult.getString("recommended_agent"));
        System.out.println("Confidence: " + routerResult.getDouble("confidence"));
        System.out.println();

        // 3. Anomaly Forensics
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("3. Anomaly Forensics (Anomaly → Root Cause)");
        System.out.println("══════════════════════════════════════════════════════════════════");

        DspyModule forensics = Dspy.anomalyForensics();
        DspyResult forensicsResult = forensics.predict(
            "anomaly_data", "Payment gateway timeout: 47 occurrences in last hour, 23% error rate"
        );
        System.out.println("Input: Payment gateway timeout anomaly");
        System.out.println("Root Cause: " + forensicsResult.getString("root_cause"));
        System.out.println("Remediation: " + forensicsResult.getString("remediation_steps"));
        System.out.println();

        // 4. Runtime Adapter
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("4. Runtime Adapter (Metrics → Optimization)");
        System.out.println("══════════════════════════════════════════════════════════════════");

        DspyModule adapter = Dspy.runtimeAdapter();
        DspyResult adapterResult = adapter.predict(
            "process_metrics", "Avg duration: 45s, Bottleneck: Data Validation, Utilization: 85%"
        );
        System.out.println("Input: Process metrics with bottleneck");
        System.out.println("Recommendations: " + adapterResult.getString("optimization_recommendations"));
        System.out.println();

        // 5. Worklet Selector
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("5. Worklet Selector (Context → Worklet)");
        System.out.println("══════════════════════════════════════════════════════════════════");

        DspyModule selector = Dspy.workletSelector();
        DspyResult selectorResult = selector.predict(
            "work_item_context", "Urgent customer escalation requiring supervisor approval"
        );
        System.out.println("Input: Urgent escalation context");
        System.out.println("Recommended Worklet: " + selectorResult.getString("recommended_worklet"));
        System.out.println();

        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("✓ All 5 DSPy programs executed successfully via Java Fluent API");
        System.out.println("══════════════════════════════════════════════════════════════════");
    }
}
