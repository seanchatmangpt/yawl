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

package org.yawlfoundation.yawl.tpot2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.tpot2.interpreter.Tpot2DspyInterpreter;
import org.yawlfoundation.yawl.tpot2.interpreter.Tpot2DspyInterpreter.Interpretation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the rust4pm → TPOT2 → DSPy pipeline.
 *
 * <p>Validates the complete JOR4J machine learning pipeline:
 * <pre>
 * ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
 * │  rust4pm    │────▶│   TPOT2     │────▶│    DSPy     │
 * │  (Features) │     │  (AutoML)   │     │ (Interpret) │
 * └─────────────┘     └─────────────┘     └─────────────┘
 *       │                   │                   │
 *       ▼                   ▼                   ▼
 *  TrainingDataset    Tpot2Result       Interpretation
 * </pre>
 *
 * <h2>Chicago TDD Principles</h2>
 * <ul>
 *   <li>Real objects, minimal test doubles</li>
 *   <li>Embedded Python simulator scripts for TPOT2</li>
 *   <li>Skip LLM tests with -DskipLLMTests=true</li>
 *   <li>Synthetic feature generation (simulates rust4pm output)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("End-to-End: rust4pm → TPOT2 → DSPy Pipeline")
class Rust4pmTpot2DspyE2ETest {

    /** Skip LLM-based tests when true (set via -DskipLLMTests=true) */
    private static final boolean SKIP_LLM_TESTS =
        "true".equalsIgnoreCase(System.getProperty("skipLLMTests"));

    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");

    /** Expected ONNX bytes written by the simulator Python script */
    private static final String SIMULATOR_ONNX_OUTPUT = "ONNX_E2E_SIMULATOR_MODEL_BYTES";

    @TempDir
    Path tempDir;

    private Path simulatorScriptPath;

    // ═══════════════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════════════

    @BeforeAll
    static void configureDspyIfAvailable() {
        if (!SKIP_LLM_TESTS && GROQ_API_KEY != null && !GROQ_API_KEY.isBlank()) {
            Dspy.configure(lm -> lm
                .model("groq/gpt-oss-20b")
                .apiKey(GROQ_API_KEY)
                .temperature(0.0));
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        simulatorScriptPath = createSimulatorScript();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 1: HAPPY PATH - COMPLETE PIPELINE
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full pipeline: Synthetic features → TPOT2 → DSPy interpretation")
    void fullPipeline_withSimulators_completesSuccessfully() throws Exception {
        // STEP 1: Generate synthetic features (simulates rust4pm output)
        TrainingDataset dataset = generateSyntheticFeatures(20);
        assertNotNull(dataset, "dataset must not be null");

        // STEP 2: Run TPOT2 optimization with simulator
        Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);
        Tpot2Result result;

        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
            result = bridge.fit(dataset, config);
        }

        // STEP 3: Verify TPOT2 result
        assertNotNull(result, "result must not be null");
        assertEquals(Tpot2TaskType.CASE_OUTCOME, result.taskType());
        assertTrue(result.bestScore() >= 0.0 && result.bestScore() <= 1.0,
            "bestScore must be in [0,1], got: " + result.bestScore());
        assertNotNull(result.pipelineDescription(), "pipelineDescription must not be null");
        assertArrayEquals(SIMULATOR_ONNX_OUTPUT.getBytes(StandardCharsets.UTF_8),
            result.onnxModelBytes(), "ONNX bytes must match simulator output");
        assertTrue(result.trainingTimeMs() >= 0, "trainingTimeMs must be non-negative");

        // STEP 4: Interpret with DSPy (if available)
        if (!SKIP_LLM_TESTS && Dspy.isConfigured()) {
            Tpot2DspyInterpreter interpreter = new Tpot2DspyInterpreter(result, config);
            Interpretation interpretation = interpreter.interpret();

            assertNotNull(interpretation, "interpretation must not be null");
            assertNotNull(interpretation.explanation(), "explanation must not be null");
            assertNotNull(interpretation.recommendations(), "recommendations must not be null");
            assertNotNull(interpretation.deploymentReadiness(), "deploymentReadiness must not be null");
            assertEquals(result.bestScore(), interpretation.score(), 0.001);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 2: DATA FLOW INTEGRITY
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Feature extraction produces valid TrainingDataset")
    void rust4pmFeatures_toTrainingDataset_validStructure() {
        TrainingDataset dataset = generateSyntheticFeatures(10);

        assertNotNull(dataset.featureNames(), "featureNames must not be null");
        assertEquals(5, dataset.featureNames().size(),
            "must have exactly 5 features (matching rust4pm output)");
        assertTrue(dataset.featureNames().contains("caseDurationMs"),
            "must include caseDurationMs");
        assertTrue(dataset.featureNames().contains("taskCount"),
            "must include taskCount");
        assertTrue(dataset.featureNames().contains("distinctWorkItems"),
            "must include distinctWorkItems");
        assertTrue(dataset.featureNames().contains("hadCancellations"),
            "must include hadCancellations");
        assertTrue(dataset.featureNames().contains("avgTaskWaitMs"),
            "must include avgTaskWaitMs");

        assertEquals(dataset.rows().size(), dataset.labels().size(),
            "rows and labels must have same length");
        assertEquals(10, dataset.caseCount(), "caseCount must match generated rows");
    }

    @Test
    @DisplayName("TPOT2 receives correctly formatted CSV")
    void tpot2ReceivesCorrectlyFormattedCsv() throws Exception {
        Path capturedCsvPath = tempDir.resolve("captured.csv");
        Path capturingScript = createCapturingSimulator(capturedCsvPath);

        TrainingDataset dataset = generateSyntheticFeatures(5);

        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, capturingScript)) {
            bridge.fit(dataset, Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
        }

        assertTrue(Files.exists(capturedCsvPath), "CSV must have been written");
        String csv = Files.readString(capturedCsvPath);

        // Verify header contains all features + label
        String header = csv.lines().findFirst().orElse("");
        assertTrue(header.contains("caseDurationMs"), "header must include caseDurationMs");
        assertTrue(header.contains("taskCount"), "header must include taskCount");
        assertTrue(header.contains("distinctWorkItems"), "header must include distinctWorkItems");
        assertTrue(header.contains("hadCancellations"), "header must include hadCancellations");
        assertTrue(header.contains("avgTaskWaitMs"), "header must include avgTaskWaitMs");
        assertTrue(header.contains("label"), "header must include label column");

        // Verify data row count (header + 5 data rows = 6 lines)
        long lineCount = csv.lines().filter(l -> !l.isBlank()).count();
        assertEquals(6, lineCount, "CSV must have 1 header + 5 data rows");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 3: ERROR HANDLING
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pipeline handles empty dataset gracefully")
    void handlesEmptyDatasetGracefully() throws Exception {
        TrainingDataset emptyDataset = new TrainingDataset(
            List.of("caseDurationMs", "taskCount", "distinctWorkItems",
                "hadCancellations", "avgTaskWaitMs"),
            List.of(),
            List.of(),
            "test-spec-empty",
            0
        );

        // Empty dataset should still work with simulator (returns fixed result)
        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
            Tpot2Result result = bridge.fit(emptyDataset,
                Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
            assertNotNull(result, "result should still be returned for empty dataset");
        }
    }

    @Test
    @DisplayName("DSPy interpreter throws when DSPy not configured")
    void dspyInterpreterThrowsWhenNotConfigured() {
        // Only run this test when DSPy is NOT configured
        if (Dspy.isConfigured()) {
            return; // Skip - DSPy is configured
        }

        Tpot2Result sampleResult = createSampleTpot2Result();
        Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new Tpot2DspyInterpreter(sampleResult, config));

        assertTrue(ex.getMessage().contains("DSPy must be configured"),
            "exception message must mention DSPy configuration");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 4: ALL TASK TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @EnumSource(Tpot2TaskType.class)
    @DisplayName("Pipeline works for all task types")
    void pipelineWorksForAllTaskTypes(Tpot2TaskType taskType) throws Exception {
        TrainingDataset dataset = generateSyntheticFeatures(8);
        Tpot2Config config = Tpot2Config.defaults(taskType);

        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
            Tpot2Result result = bridge.fit(dataset, config);

            assertNotNull(result, "result must not be null for " + taskType);
            assertEquals(taskType, result.taskType(),
                "result taskType must match config for " + taskType);
            assertTrue(result.onnxModelBytes().length > 0,
                "ONNX bytes must not be empty for " + taskType);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 5: DSPy INTERPRETATION (SKIPPABLE)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DSPy Interpretation Tests")
    class DspyInterpretationTests {

        @Test
        @DisplayName("Interpretation contains structured fields")
        void interpretationContainsStructuredFields() throws Exception {
            if (SKIP_LLM_TESTS || !Dspy.isConfigured()) {
                return; // Skip when LLM not available
            }

            TrainingDataset dataset = generateSyntheticFeatures(15);
            Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                Tpot2Result result = bridge.fit(dataset, config);

                Tpot2DspyInterpreter interpreter = new Tpot2DspyInterpreter(result, config);
                Interpretation interpretation = interpreter.interpret();

                // All fields must be non-null
                assertNotNull(interpretation.explanation());
                assertNotNull(interpretation.recommendations());
                assertNotNull(interpretation.deploymentReadiness());
                assertNotNull(interpretation.featureInsights());

                // Score and timing must match
                assertEquals(result.bestScore(), interpretation.score(), 0.001);
                assertEquals(result.trainingTimeMs(), interpretation.trainingTimeMs());
            }
        }

        @Test
        @DisplayName("Quick explanation works without full interpretation")
        void quickExplanationWorks() throws Exception {
            if (SKIP_LLM_TESTS || !Dspy.isConfigured()) {
                return; // Skip when LLM not available
            }

            TrainingDataset dataset = generateSyntheticFeatures(10);
            Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                Tpot2Result result = bridge.fit(dataset, config);

                Tpot2DspyInterpreter interpreter = new Tpot2DspyInterpreter(result, config);
                String quickExplanation = interpreter.quickExplanation();

                assertNotNull(quickExplanation, "quickExplanation must not be null");
                assertTrue(quickExplanation.contains("TPOT2"),
                    "quickExplanation must mention TPOT2");
                assertTrue(quickExplanation.contains(String.format("%.2f", result.bestScore())),
                    "quickExplanation must include score");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST 6: PERFORMANCE
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Feature extraction scales with dataset size")
    void featureExtractionScalesWithDatasetSize() {
        // Generate a larger dataset to verify performance
        long start = System.currentTimeMillis();
        TrainingDataset dataset = generateSyntheticFeatures(100);
        long duration = System.currentTimeMillis() - start;

        assertEquals(100, dataset.caseCount(), "must generate 100 cases");
        assertTrue(duration < 1000,
            "feature generation should be fast (<1s), took: " + duration + "ms");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generate synthetic features matching rust4pm output format.
     *
     * <p>Features:
     * <ul>
     *   <li>caseDurationMs: Total case duration in milliseconds</li>
     *   <li>taskCount: Number of tasks in the case</li>
     *   <li>distinctWorkItems: Number of distinct work items</li>
     *   <li>hadCancellations: 1.0 if case was cancelled, 0.0 otherwise</li>
     *   <li>avgTaskWaitMs: Average wait time between task enable and start</li>
     * </ul>
     *
     * @param caseCount Number of cases to generate
     * @return TrainingDataset with synthetic features
     */
    private TrainingDataset generateSyntheticFeatures(int caseCount) {
        List<String> featureNames = List.of(
            "caseDurationMs", "taskCount", "distinctWorkItems",
            "hadCancellations", "avgTaskWaitMs"
        );

        List<double[]> rows = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Random random = new Random(42); // Deterministic for reproducibility

        for (int i = 0; i < caseCount; i++) {
            // Generate features that correlate with outcome
            boolean failed = random.nextDouble() < 0.3; // 30% failure rate

            double caseDurationMs = failed
                ? 5000 + random.nextDouble() * 45000  // Longer duration for failures
                : 1000 + random.nextDouble() * 20000; // Shorter for successes

            int taskCount = failed
                ? 8 + random.nextInt(12)  // More tasks for failures
                : 2 + random.nextInt(6);  // Fewer tasks for successes

            int distinctWorkItems = (int) (taskCount * (0.7 + random.nextDouble() * 0.3));

            double hadCancellations = failed ? 1.0 : 0.0;

            double avgTaskWaitMs = failed
                ? 2000 + random.nextDouble() * 8000  // Longer waits for failures
                : 100 + random.nextDouble() * 2000;  // Shorter waits for successes

            rows.add(new double[] {
                caseDurationMs, taskCount, distinctWorkItems,
                hadCancellations, avgTaskWaitMs
            });

            labels.add(failed ? "failed" : "completed");
        }

        return new TrainingDataset(
            featureNames, rows, labels,
            "synthetic-e2e-test-spec",
            caseCount
        );
    }

    /**
     * Create the TPOT2 simulator Python script.
     */
    private Path createSimulatorScript() throws IOException {
        String script = """
            import argparse, json, sys

            parser = argparse.ArgumentParser()
            parser.add_argument("--data", required=True)
            parser.add_argument("--config", required=True)
            parser.add_argument("--output", required=True)
            args = parser.parse_args()

            # Validate CSV has expected structure
            with open(args.data) as f:
                csv_content = f.read()
            if "label" not in csv_content:
                print("ERROR: CSV missing 'label' column", file=sys.stderr)
                sys.exit(2)

            # Validate config JSON has taskType
            with open(args.config) as f:
                cfg = json.load(f)
            if "taskType" not in cfg:
                print("ERROR: config missing 'taskType'", file=sys.stderr)
                sys.exit(2)

            # Write simulator ONNX output
            with open(args.output, "wb") as f:
                f.write(b"ONNX_E2E_SIMULATOR_MODEL_BYTES")

            # Emit JSON metrics as the last stdout line
            print(json.dumps({
                "bestScore": 0.89,
                "pipelineDescription": "RandomForestClassifier(n_estimators=100, max_depth=10)",
                "trainingTimeMs": 1250
            }))
            sys.stdout.flush()
            """;

        Path path = tempDir.resolve("tpot2_e2e_simulator.py");
        Files.writeString(path, script, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * Create a simulator that captures the CSV input for inspection.
     */
    private Path createCapturingSimulator(Path capturePath) throws IOException {
        String script = """
            import argparse, json, sys, shutil

            parser = argparse.ArgumentParser()
            parser.add_argument("--data"); parser.add_argument("--config")
            parser.add_argument("--output")
            args = parser.parse_args()

            # Capture the CSV for verification
            shutil.copy(args.data, "%s")

            # Write minimal ONNX output
            with open(args.output, "wb") as f:
                f.write(b"X")

            # Emit minimal metrics
            print(json.dumps({"bestScore": 0.5, "pipelineDescription": "X", "trainingTimeMs": 1}))
            sys.stdout.flush()
            """.formatted(capturePath.toString().replace("\\", "/"));

        Path path = tempDir.resolve("capturing_simulator.py");
        Files.writeString(path, script, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * Create a sample Tpot2Result for testing DSPy without running TPOT2.
     */
    private Tpot2Result createSampleTpot2Result() {
        return new Tpot2Result(
            Tpot2TaskType.CASE_OUTCOME,
            0.87,
            "GradientBoostingClassifier(max_depth=5, n_estimators=50)",
            "SAMPLE_ONNX_BYTES_FOR_TESTING".getBytes(StandardCharsets.UTF_8),
            1500L
        );
    }
}
