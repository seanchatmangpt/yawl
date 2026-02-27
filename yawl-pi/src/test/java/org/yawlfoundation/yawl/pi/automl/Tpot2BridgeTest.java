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

package org.yawlfoundation.yawl.pi.automl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.TrainingDataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Tpot2Bridge subprocess invocation, CSV serialisation, and error handling.
 *
 * <p>Chicago TDD: real {@link ProcessBuilder} invocations against real temp files.
 * No test-double framework. Does NOT require tpot2/skl2onnx to be installed —
 * each test injects a minimal Python simulator script via the package-private
 * {@link Tpot2Bridge#Tpot2Bridge(Path, Path)} constructor.
 *
 * <p>Requires {@code python3} to be on PATH (standard on Linux CI).
 */
public class Tpot2BridgeTest {

    @TempDir
    Path tempDir;

    /** Expected ONNX bytes written by the simulator Python script to its output path. */
    private static final String SIMULATOR_ONNX_OUTPUT = "ONNX_SIMULATOR_OUTPUT_BYTES";

    /**
     * Minimal Python script that simulates successful TPOT2 output.
     * Validates that CSV and JSON config were passed correctly, writes simulator ONNX bytes,
     * and emits the required JSON metrics on stdout.
     */
    private static final String SIMULATOR_SCRIPT = """
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

            # Write simulator ONNX output (matches SIMULATOR_ONNX_OUTPUT constant in Java)
            with open(args.output, "wb") as f:
                f.write(b"ONNX_SIMULATOR_OUTPUT_BYTES")

            # Emit JSON metrics as the last stdout line
            print(json.dumps({
                "bestScore": 0.87,
                "pipelineDescription": "GradientBoostingClassifier(max_depth=3)",
                "trainingTimeMs": 42
            }))
            sys.stdout.flush()
            """;

    /**
     * Python script that exits non-zero simulating tpot2 import failure.
     */
    private static final String MISSING_TPOT2_SCRIPT = """
            import sys
            print("No module named 'tpot2'", file=sys.stderr)
            sys.exit(1)
            """;

    /**
     * Python script that writes JSON metrics but does NOT write the ONNX file.
     */
    private static final String NO_ONNX_SCRIPT = """
            import argparse, json, sys

            parser = argparse.ArgumentParser()
            parser.add_argument("--data"); parser.add_argument("--config")
            parser.add_argument("--output")
            args = parser.parse_args()
            # intentionally NOT writing ONNX file
            print(json.dumps({"bestScore": 0.5, "pipelineDescription": "x", "trainingTimeMs": 1}))
            sys.stdout.flush()
            """;

    /**
     * Python script that captures the training CSV to a well-known temp path for inspection.
     */
    private Path capturingScriptPath;
    private Path capturedCsvPath;

    private Path simulatorScriptPath;
    private Path missingTpot2ScriptPath;
    private Path noOnnxScriptPath;

    @BeforeEach
    public void setUp() throws IOException {
        simulatorScriptPath = tempDir.resolve("simulator.py");
        Files.writeString(simulatorScriptPath, SIMULATOR_SCRIPT, StandardCharsets.UTF_8);

        missingTpot2ScriptPath = tempDir.resolve("missing_tpot2.py");
        Files.writeString(missingTpot2ScriptPath, MISSING_TPOT2_SCRIPT, StandardCharsets.UTF_8);

        noOnnxScriptPath = tempDir.resolve("no_onnx.py");
        Files.writeString(noOnnxScriptPath, NO_ONNX_SCRIPT, StandardCharsets.UTF_8);

        capturedCsvPath = tempDir.resolve("captured_csv.txt");
        // Write a capturing script that copies the training CSV to capturedCsvPath
        String capturingScript = """
                import argparse, json, sys, shutil
                parser = argparse.ArgumentParser()
                parser.add_argument("--data"); parser.add_argument("--config")
                parser.add_argument("--output")
                args = parser.parse_args()
                shutil.copy(args.data, "%s")
                with open(args.output, "wb") as f: f.write(b"X")
                print(json.dumps({"bestScore": 0.5, "pipelineDescription": "X", "trainingTimeMs": 1}))
                sys.stdout.flush()
                """.formatted(capturedCsvPath.toString().replace("\\", "/"));
        capturingScriptPath = tempDir.resolve("capturing.py");
        Files.writeString(capturingScriptPath, capturingScript, StandardCharsets.UTF_8);
    }

    // ── successful invocation ─────────────────────────────────────────────────

    @Test
    public void testFitWithSimulatorReturnsCorrectResult() throws Exception {
        TrainingDataset dataset = buildTestDataset();
        Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
            Tpot2Result result = bridge.fit(dataset, config);

            assertNotNull(result, "result must not be null");
            assertEquals(Tpot2TaskType.CASE_OUTCOME, result.taskType());
            assertEquals(0.87, result.bestScore(), 0.001);
            assertEquals("GradientBoostingClassifier(max_depth=3)", result.pipelineDescription());
            assertNotNull(result.onnxModelBytes());
            assertTrue(result.onnxModelBytes().length > 0, "onnxModelBytes must be non-empty");
            assertEquals(SIMULATOR_ONNX_OUTPUT,
                new String(result.onnxModelBytes(), StandardCharsets.UTF_8));
            assertTrue(result.trainingTimeMs() >= 0, "trainingTimeMs must be non-negative");
        }
    }

    @Test
    public void testFitConfigWrittenCorrectly() throws Exception {
        // Use a config with a custom scoring metric to verify JSON serialisation
        Tpot2Config config = new Tpot2Config(
            Tpot2TaskType.NEXT_ACTIVITY, 3, 20, 30, 3, "f1_macro", 4, "python3");
        Path capturedConfigPath = tempDir.resolve("captured_config.json");

        String captureConfigScript = """
                import argparse, json, sys, shutil
                parser = argparse.ArgumentParser()
                parser.add_argument("--data"); parser.add_argument("--config")
                parser.add_argument("--output")
                args = parser.parse_args()
                shutil.copy(args.config, "%s")
                with open(args.output, "wb") as f: f.write(b"X")
                print(json.dumps({"bestScore": 0.0, "pipelineDescription": "X", "trainingTimeMs": 1}))
                sys.stdout.flush()
                """.formatted(capturedConfigPath.toString().replace("\\", "/"));

        Path captureConfigScriptPath = tempDir.resolve("capture_config.py");
        Files.writeString(captureConfigScriptPath, captureConfigScript, StandardCharsets.UTF_8);

        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, captureConfigScriptPath)) {
            bridge.fit(buildTestDataset(), config);
        }

        assertTrue(Files.exists(capturedConfigPath), "config JSON must have been written");
        String configJson = Files.readString(capturedConfigPath);
        assertTrue(configJson.contains("\"taskType\":\"NEXT_ACTIVITY\""),
            "config must contain taskType");
        assertTrue(configJson.contains("\"generations\":3"),
            "config must contain generations");
        assertTrue(configJson.contains("\"scoringMetric\":\"f1_macro\""),
            "config must contain scoringMetric");
        assertTrue(configJson.contains("\"nJobs\":4"),
            "config must contain nJobs");
    }

    // ── CSV serialisation ─────────────────────────────────────────────────────

    @Test
    public void testCsvHeaderContainsAllFeatureColumnsAndLabel() throws Exception {
        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, capturingScriptPath)) {
            bridge.fit(buildTestDataset(), Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
        }

        assertTrue(Files.exists(capturedCsvPath), "capturing script must write CSV");
        String csv = Files.readString(capturedCsvPath);
        String[] lines = csv.split("\n");
        assertTrue(lines.length >= 2, "CSV must have at least a header + one data row");

        String header = lines[0];
        assertTrue(header.contains("caseDurationMs"), "CSV header must include caseDurationMs");
        assertTrue(header.contains("taskCount"), "CSV header must include taskCount");
        assertTrue(header.contains("label"), "CSV header must include label column");
    }

    @Test
    public void testCsvDataRowsAreCommaSeparated() throws Exception {
        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, capturingScriptPath)) {
            bridge.fit(buildTestDataset(), Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
        }

        String csv = Files.readString(capturedCsvPath);
        String[] lines = csv.split("\n");
        // Skip header, verify data rows
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                assertTrue(line.contains(","), "data row must be comma-separated: " + line);
                // Last field should be the label (completed/failed)
                String[] fields = line.split(",");
                String label = fields[fields.length - 1];
                assertTrue(label.equals("completed") || label.equals("failed"),
                    "label must be 'completed' or 'failed', got: " + label);
            }
        }
    }

    @Test
    public void testCsvRowCountMatchesDataset() throws Exception {
        TrainingDataset dataset = buildTestDataset(); // 4 rows
        try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, capturingScriptPath)) {
            bridge.fit(dataset, Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
        }

        String csv = Files.readString(capturedCsvPath);
        long dataLines = csv.lines()
            .filter(l -> !l.isBlank())
            .count();
        // 1 header + 4 data rows = 5 lines
        assertEquals(5, dataLines, "CSV must have 1 header + 4 data rows");
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test
    public void testFitThrowsPIExceptionWhenOnnxFileMissing() {
        PIException ex = assertThrows(PIException.class, () -> {
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, noOnnxScriptPath)) {
                bridge.fit(buildTestDataset(), Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
            }
        });
        assertEquals("automl", ex.getConnection());
        assertTrue(ex.getMessage().contains("ONNX output"),
            "exception message must mention ONNX output");
    }

    @Test
    public void testFitThrowsPIExceptionOnNonZeroExit() {
        PIException ex = assertThrows(PIException.class, () -> {
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, missingTpot2ScriptPath)) {
                bridge.fit(buildTestDataset(), Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
            }
        });
        assertEquals("automl", ex.getConnection());
    }

    @Test
    public void testFitThrowsPIExceptionWhenPythonNotFound() {
        Tpot2Config config = new Tpot2Config(
            Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, null, -1, "python_does_not_exist_xyz");

        PIException ex = assertThrows(PIException.class, () -> {
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                bridge.fit(buildTestDataset(), config);
            }
        });
        assertEquals("automl", ex.getConnection());
    }

    // ── null guards ───────────────────────────────────────────────────────────

    @Test
    public void testFitNullDatasetThrows() {
        assertThrows(NullPointerException.class, () -> {
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                bridge.fit(null, Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME));
            }
        });
    }

    @Test
    public void testFitNullConfigThrows() {
        assertThrows(NullPointerException.class, () -> {
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                bridge.fit(buildTestDataset(), null);
            }
        });
    }

    // ── all task types work ───────────────────────────────────────────────────

    @Test
    public void testFitSucceedsForAllTaskTypes() throws Exception {
        for (Tpot2TaskType taskType : Tpot2TaskType.values()) {
            Tpot2Config config = Tpot2Config.defaults(taskType);
            try (Tpot2Bridge bridge = new Tpot2Bridge(tempDir, simulatorScriptPath)) {
                Tpot2Result result = bridge.fit(buildTestDataset(), config);
                assertEquals(taskType, result.taskType(),
                    "result taskType must match config taskType for: " + taskType);
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static TrainingDataset buildTestDataset() {
        List<String> features = List.of(
            "caseDurationMs", "taskCount", "distinctWorkItems",
            "hadCancellations", "avgTaskWaitMs");
        List<double[]> rows = List.of(
            new double[]{1200.0, 3.0, 2.0, 0.0, 400.0},
            new double[]{8000.0, 7.0, 5.0, 1.0, 1100.0},
            new double[]{500.0, 2.0, 1.0, 0.0, 250.0},
            new double[]{15000.0, 10.0, 8.0, 1.0, 3000.0}
        );
        List<String> labels = List.of("completed", "failed", "completed", "failed");
        return new TrainingDataset(features, rows, labels, "test-spec-001", 4);
    }
}
