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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;
import org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor;
import org.yawlfoundation.yawl.pi.predictive.TrainingDataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * High-level coordinator for TPOT2-powered AutoML in YAWL process mining.
 *
 * <p>Combines training data extraction, TPOT2 optimisation, and ONNX model
 * registration into a single-method workflow:
 * <ol>
 *   <li>Extract tabular training data via {@link ProcessMiningTrainingDataExtractor}</li>
 *   <li>Run TPOT2 via {@link Tpot2Bridge}</li>
 *   <li>Write the ONNX model bytes to {@code modelDir}</li>
 *   <li>Register the model with the provided {@link PredictiveModelRegistry}</li>
 * </ol>
 *
 * <p><b>Re-training note</b>: {@link PredictiveModelRegistry#register} throws
 * {@link PIException} if the task name is already registered. Callers that need
 * re-training semantics must close and re-create the registry (or extend it with
 * a {@code deregister} method) before calling these methods again.
 *
 * <p><b>Thread safety</b>: all methods are reentrant. Each call creates its own
 * {@link Tpot2Bridge} instance and isolated temp files.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ProcessMiningAutoMl {

    private static final Logger log = LogManager.getLogger(ProcessMiningAutoMl.class);

    /** Maximum cases extracted for training; guards against very large event stores. */
    private static final int DEFAULT_MAX_CASES = 10_000;

    private ProcessMiningAutoMl() {
        throw new UnsupportedOperationException("ProcessMiningAutoMl is a static facade");
    }

    /**
     * Auto-trains a case-outcome prediction model for the specified workflow specification.
     *
     * <p>The model is registered under the task name {@code "<specId>_case_outcome"}
     * in the provided registry.
     *
     * @param specId    workflow specification to train for
     * @param store     event store supplying historical cases for training
     * @param registry  model registry to register the trained ONNX model in
     * @param config    TPOT2 configuration; {@link Tpot2Config#taskType()} must be
     *                  {@link Tpot2TaskType#CASE_OUTCOME}
     * @param modelDir  directory where the ONNX file is persisted (created if absent)
     * @return training result with the best pipeline's score and description
     * @throws PIException          if extraction, training, ONNX export, or registration fails
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if {@code config.taskType()} is not CASE_OUTCOME
     */
    public static Tpot2Result autoTrainCaseOutcome(
            YSpecificationID specId,
            WorkflowEventStore store,
            PredictiveModelRegistry registry,
            Tpot2Config config,
            Path modelDir) throws PIException {

        requireNonNull(specId, "specId");
        requireNonNull(store, "store");
        requireNonNull(registry, "registry");
        requireNonNull(config, "config");
        requireNonNull(modelDir, "modelDir");
        requireTaskType(config, Tpot2TaskType.CASE_OUTCOME);

        return runAutoMl(specId, store, registry, config, modelDir,
            specId.getIdentifier() + "_case_outcome");
    }

    /**
     * Auto-trains a remaining-time prediction model for the specified workflow specification.
     *
     * <p>The model is registered under the task name {@code "<specId>_remaining_time"}.
     * The training dataset must include a numeric {@code remaining_time_ms} label column;
     * use a custom extractor if the default {@link ProcessMiningTrainingDataExtractor}
     * does not produce this column.
     *
     * @param specId    workflow specification to train for
     * @param store     event store supplying historical cases
     * @param registry  model registry for the trained model
     * @param config    TPOT2 configuration; taskType must be {@link Tpot2TaskType#REMAINING_TIME}
     * @param modelDir  directory for persisting the ONNX file
     * @return training result
     * @throws PIException          if training or registration fails
     * @throws NullPointerException if any parameter is null
     */
    public static Tpot2Result autoTrainRemainingTime(
            YSpecificationID specId,
            WorkflowEventStore store,
            PredictiveModelRegistry registry,
            Tpot2Config config,
            Path modelDir) throws PIException {

        requireNonNull(specId, "specId");
        requireNonNull(store, "store");
        requireNonNull(registry, "registry");
        requireNonNull(config, "config");
        requireNonNull(modelDir, "modelDir");
        requireTaskType(config, Tpot2TaskType.REMAINING_TIME);

        return runAutoMl(specId, store, registry, config, modelDir,
            specId.getIdentifier() + "_remaining_time");
    }

    /**
     * Auto-trains a next-activity prediction model for the specified workflow specification.
     *
     * <p>The model is registered under {@code "<specId>_next_activity"}.
     *
     * @param specId    workflow specification to train for
     * @param store     event store supplying historical cases
     * @param registry  model registry for the trained model
     * @param config    TPOT2 configuration; taskType must be {@link Tpot2TaskType#NEXT_ACTIVITY}
     * @param modelDir  directory for persisting the ONNX file
     * @return training result
     * @throws PIException          if training or registration fails
     * @throws NullPointerException if any parameter is null
     */
    public static Tpot2Result autoTrainNextActivity(
            YSpecificationID specId,
            WorkflowEventStore store,
            PredictiveModelRegistry registry,
            Tpot2Config config,
            Path modelDir) throws PIException {

        requireNonNull(specId, "specId");
        requireNonNull(store, "store");
        requireNonNull(registry, "registry");
        requireNonNull(config, "config");
        requireNonNull(modelDir, "modelDir");
        requireTaskType(config, Tpot2TaskType.NEXT_ACTIVITY);

        return runAutoMl(specId, store, registry, config, modelDir,
            specId.getIdentifier() + "_next_activity");
    }

    /**
     * Auto-trains an anomaly detection model for the specified workflow specification.
     *
     * <p>Labels must be "normal" or "anomaly". The model is registered under
     * {@code "<specId>_anomaly_detection"}.
     *
     * @param specId    workflow specification to train for
     * @param store     event store supplying historical cases
     * @param registry  model registry for the trained model
     * @param config    TPOT2 configuration; taskType must be {@link Tpot2TaskType#ANOMALY_DETECTION}
     * @param modelDir  directory for persisting the ONNX file
     * @return training result
     * @throws PIException          if training or registration fails
     * @throws NullPointerException if any parameter is null
     */
    public static Tpot2Result autoTrainAnomalyDetection(
            YSpecificationID specId,
            WorkflowEventStore store,
            PredictiveModelRegistry registry,
            Tpot2Config config,
            Path modelDir) throws PIException {

        requireNonNull(specId, "specId");
        requireNonNull(store, "store");
        requireNonNull(registry, "registry");
        requireNonNull(config, "config");
        requireNonNull(modelDir, "modelDir");
        requireTaskType(config, Tpot2TaskType.ANOMALY_DETECTION);

        return runAutoMl(specId, store, registry, config, modelDir,
            specId.getIdentifier() + "_anomaly_detection");
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static Tpot2Result runAutoMl(
            YSpecificationID specId,
            WorkflowEventStore store,
            PredictiveModelRegistry registry,
            Tpot2Config config,
            Path modelDir,
            String taskName) throws PIException {

        log.info("Starting AutoML: task={}, spec={}", config.taskType(), specId.getIdentifier());

        TrainingDataset dataset = extractTrainingData(specId, store);
        log.info("Extracted {} training cases for spec={}", dataset.caseCount(),
            specId.getIdentifier());

        if (dataset.caseCount() == 0) {
            throw new PIException(
                "No training cases found for specification: " + specId.getIdentifier()
                + ". Ensure the workflow has historical execution data.", "automl");
        }

        Tpot2Result result;
        try (Tpot2Bridge bridge = new Tpot2Bridge()) {
            result = bridge.fit(dataset, config);
        }

        Path onnxPath = persistOnnx(result.onnxModelBytes(), modelDir, taskName);
        registry.register(taskName, onnxPath);

        log.info("AutoML complete: taskName={}, score={}, trainingTimeMs={}",
            taskName, result.bestScore(), result.trainingTimeMs());
        return result;
    }

    private static TrainingDataset extractTrainingData(YSpecificationID specId,
                                                        WorkflowEventStore store)
            throws PIException {
        try {
            ProcessMiningTrainingDataExtractor extractor =
                new ProcessMiningTrainingDataExtractor(store);
            return extractor.extractTabular(specId, DEFAULT_MAX_CASES);
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new PIException(
                "Failed to extract training data for spec: " + specId.getIdentifier(),
                "automl", e);
        }
    }

    private static Path persistOnnx(byte[] onnxBytes, Path modelDir, String taskName)
            throws PIException {
        try {
            Files.createDirectories(modelDir);
            Path onnxPath = modelDir.resolve(taskName + ".onnx");
            Files.write(onnxPath, onnxBytes);
            log.debug("Persisted ONNX model to {}", onnxPath);
            return onnxPath;
        } catch (IOException e) {
            throw new PIException(
                "Failed to write ONNX model file to " + modelDir, "automl", e);
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) throw new NullPointerException(name + " is required");
    }

    private static void requireTaskType(Tpot2Config config, Tpot2TaskType expected) {
        if (config.taskType() != expected) {
            throw new IllegalArgumentException(
                "autoTrain" + capitalize(expected.name()) + " requires taskType="
                + expected + ", got: " + config.taskType());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
