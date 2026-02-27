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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.TrainingDataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Subprocess bridge for TPOT2 AutoML training against YAWL process mining datasets.
 *
 * <p>On construction, extracts {@code tpot2_runner.py} from the classpath to a
 * temporary directory. Each {@link #fit} call then:
 * <ol>
 *   <li>Serialises the {@link TrainingDataset} to a temp CSV file</li>
 *   <li>Serialises the {@link Tpot2Config} to a temp JSON file</li>
 *   <li>Launches: {@code <pythonExecutable> tpot2_runner.py --data <csv> --config <json> --output <onnx>}</li>
 *   <li>Waits for completion (timeout = {@code maxTimeMins + 5} minutes)</li>
 *   <li>Parses the last JSON line from stdout as training metrics</li>
 *   <li>Reads the ONNX output file as bytes</li>
 *   <li>Cleans up per-run temp files, then returns {@link Tpot2Result}</li>
 * </ol>
 *
 * <p><b>Error handling</b>: if Python is not on PATH or tpot2/skl2onnx are not
 * installed, a {@link PIException} with connection {@code "automl"} is thrown.
 * Non-zero subprocess exit codes are treated as fatal errors.
 *
 * <p><b>Thread safety</b>: each {@link #fit} call is independent and creates
 * its own per-run temp files; the bridge may be used concurrently. The extracted
 * script directory is shared across calls but read-only after construction.
 *
 * <p><b>Lifecycle</b>: implements {@link AutoCloseable}; closing removes the
 * temp directory created at construction. Use in try-with-resources for correct cleanup.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2Bridge implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(Tpot2Bridge.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RUNNER_RESOURCE = "tpot2/tpot2_runner.py";

    private final Path bridgeTempDir;  // created once at construction, deleted on close()
    private final Path runnerScript;   // bridgeTempDir/tpot2_runner.py

    /**
     * Constructs a bridge, extracting {@code tpot2_runner.py} from the classpath to a temp dir.
     *
     * @throws PIException if the Python script cannot be found on the classpath or
     *                     if the temp directory cannot be created
     */
    public Tpot2Bridge() throws PIException {
        try {
            this.bridgeTempDir = Files.createTempDirectory("yawl-tpot2-");
            this.runnerScript = bridgeTempDir.resolve("tpot2_runner.py");
            extractRunner(runnerScript);
            log.info("Tpot2Bridge initialised at {}", bridgeTempDir);
        } catch (IOException e) {
            throw new PIException("Failed to initialise Tpot2Bridge temp dir", "automl", e);
        }
    }

    /**
     * Package-private constructor for testing: inject a pre-created temp dir and runner script.
     * The provided directory is NOT deleted on {@link #close()} — the caller owns it.
     *
     * @param bridgeTempDir directory for per-run temp files
     * @param runnerScript  path to the Python runner script
     */
    Tpot2Bridge(Path bridgeTempDir, Path runnerScript) {
        this.bridgeTempDir = bridgeTempDir;
        this.runnerScript = runnerScript;
    }

    /**
     * Runs TPOT2 AutoML on the training dataset and returns the best pipeline as ONNX.
     *
     * @param dataset training data with feature vectors and labels
     * @param config  TPOT2 run configuration
     * @return result containing the best pipeline's ONNX bytes, score, and description
     * @throws PIException if Python is unavailable, tpot2/skl2onnx is not installed,
     *                     the subprocess exits non-zero, or the ONNX output file is missing
     * @throws NullPointerException if dataset or config is null
     */
    public Tpot2Result fit(TrainingDataset dataset, Tpot2Config config) throws PIException {
        if (dataset == null) throw new NullPointerException("dataset is required");
        if (config == null) throw new NullPointerException("config is required");

        Path runDir = null;
        try {
            runDir = Files.createTempDirectory(bridgeTempDir, "run-");
            Path csvPath = runDir.resolve("training_data.csv");
            Path configPath = runDir.resolve("tpot2_config.json");
            Path onnxPath = runDir.resolve("best_pipeline.onnx");

            writeTrainingCsv(dataset, csvPath);
            writeConfig(config, configPath);

            long startMs = System.currentTimeMillis();
            // Allow extra 5 minutes beyond TPOT2's own maxTimeMins for startup + ONNX export
            long timeoutMins = config.maxTimeMins() + 5L;
            String metricsJson = runSubprocess(
                config.pythonExecutable(), csvPath, configPath, onnxPath, timeoutMins);
            long totalTimeMs = System.currentTimeMillis() - startMs;

            if (!Files.exists(onnxPath)) {
                throw new PIException(
                    "TPOT2 runner did not produce ONNX output at: " + onnxPath
                    + " — check that tpot2 and skl2onnx are installed", "automl");
            }

            byte[] onnxBytes = Files.readAllBytes(onnxPath);
            ObjectNode metrics = MAPPER.readValue(metricsJson, ObjectNode.class);

            double bestScore = metrics.path("bestScore").asDouble(0.0);
            String pipelineDescription = metrics.path("pipelineDescription").asText("unknown");

            log.info("TPOT2 fit complete: task={}, score={}, time={}ms",
                config.taskType(), bestScore, totalTimeMs);

            return new Tpot2Result(
                config.taskType(), bestScore, pipelineDescription, onnxBytes, totalTimeMs);

        } catch (IOException e) {
            throw new PIException("IO failure during TPOT2 fit", "automl", e);
        } finally {
            deleteTempDir(runDir);
        }
    }

    /**
     * Removes the temp directory created at construction (including all per-run sub-directories).
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        deleteTempDir(bridgeTempDir);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private void extractRunner(Path target) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RUNNER_RESOURCE)) {
            if (is == null) {
                throw new IOException(
                    "tpot2_runner.py not found on classpath: " + RUNNER_RESOURCE
                    + " — ensure yawl-pi JAR was built with resources included");
            }
            Files.write(target, is.readAllBytes());
        }
    }

    /**
     * Serialises the training dataset to CSV.
     * Format mirrors {@code ProcessMiningTrainingDataExtractor.toCsv()} exactly:
     * header row of feature names + "label", then one data row per case.
     */
    private static void writeTrainingCsv(TrainingDataset dataset, Path csvPath)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        // Header: feature columns + label
        sb.append(String.join(",", dataset.featureNames())).append(",label\n");
        // Data rows
        for (int i = 0; i < dataset.rows().size(); i++) {
            double[] row = dataset.rows().get(i);
            for (double v : row) {
                sb.append(v).append(',');
            }
            sb.append(dataset.labels().get(i)).append('\n');
        }
        Files.writeString(csvPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeConfig(Tpot2Config config, Path configPath)
            throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("taskType", config.taskType().name());
        node.put("generations", config.generations());
        node.put("populationSize", config.populationSize());
        node.put("maxTimeMins", config.maxTimeMins());
        node.put("cvFolds", config.cvFolds());
        node.put("nJobs", config.nJobs());
        if (config.scoringMetric() != null) {
            node.put("scoringMetric", config.scoringMetric());
        }
        MAPPER.writeValue(configPath.toFile(), node);
    }

    private String runSubprocess(String pythonExecutable, Path csvPath,
                                  Path configPath, Path onnxPath,
                                  long timeoutMins) throws PIException {
        List<String> command = List.of(
            pythonExecutable,
            runnerScript.toString(),
            "--data", csvPath.toString(),
            "--config", configPath.toString(),
            "--output", onnxPath.toString()
        );

        log.debug("Launching TPOT2 subprocess: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // Virtual threads for non-blocking I/O — matches OllamaCandidateSampler pattern
            Thread stdoutThread = Thread.ofVirtual().name("tpot2-stdout").start(() ->
                captureStream(process.getInputStream(), stdout, false));
            Thread stderrThread = Thread.ofVirtual().name("tpot2-stderr").start(() ->
                captureStream(process.getErrorStream(), stderr, true));

            boolean finished = process.waitFor(timeoutMins, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new PIException(
                    "TPOT2 subprocess timed out after " + timeoutMins + " minutes", "automl");
            }

            stdoutThread.join(5_000);
            stderrThread.join(5_000);

            int exitCode = process.exitValue();
            String stderrContent = stderr.toString().trim();
            String stdoutContent = stdout.toString().trim();

            if (exitCode != 0) {
                return handleNonZeroExit(exitCode, pythonExecutable, stderrContent);
            }

            String jsonLine = extractLastJsonLine(stdoutContent);
            if (jsonLine == null || jsonLine.isBlank()) {
                throw new PIException(
                    "TPOT2 runner produced no JSON metrics on stdout. stdout=["
                    + stdoutContent.substring(0, Math.min(500, stdoutContent.length())) + "]",
                    "automl");
            }
            return jsonLine;

        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("No such file") || msg.contains("error=2")) {
                throw new PIException(
                    "Python executable not found: '" + pythonExecutable
                    + "'. Ensure Python 3.9+ is on PATH or set pythonExecutable in Tpot2Config.",
                    "automl", e);
            }
            throw new PIException("Failed to launch TPOT2 subprocess", "automl", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PIException("TPOT2 subprocess was interrupted", "automl", e);
        }
    }

    private void captureStream(java.io.InputStream inputStream,
                                StringBuilder target, boolean logToDebug) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logToDebug) {
                    log.debug("tpot2> {}", line);
                }
                target.append(line).append('\n');
            }
        } catch (IOException e) {
            log.warn("Stream read error from TPOT2 subprocess: {}", e.getMessage());
        }
    }

    private static String handleNonZeroExit(int exitCode, String pythonExecutable,
                                             String stderrContent) throws PIException {
        if (stderrContent.contains("No module named 'tpot")
                || stderrContent.contains("tpot2 not installed")) {
            throw new PIException(
                "tpot2 Python library is not installed. "
                + "Install with: pip install tpot2 skl2onnx", "automl");
        }
        if (stderrContent.contains("No module named 'skl2onnx'")) {
            throw new PIException(
                "skl2onnx Python library is not installed. "
                + "Install with: pip install skl2onnx", "automl");
        }
        if (exitCode == 127 || stderrContent.contains("command not found")) {
            throw new PIException(
                "Python executable not found: '" + pythonExecutable
                + "'. Ensure Python 3.9+ is on PATH.", "automl");
        }
        throw new PIException(
            "TPOT2 subprocess failed (exit=" + exitCode + "): "
            + stderrContent.substring(0, Math.min(1000, stderrContent.length())),
            "automl");
    }

    /**
     * Finds the last line in stdout that begins with '{', which is the JSON metrics line.
     * Tolerates progress/log output mixed into stdout before the final JSON.
     */
    private static String extractLastJsonLine(String stdout) {
        if (stdout == null || stdout.isBlank()) return null;
        String[] lines = stdout.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("{")) return line;
        }
        return null;
    }

    private static void deleteTempDir(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
