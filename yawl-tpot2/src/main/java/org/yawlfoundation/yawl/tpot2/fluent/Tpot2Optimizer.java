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

package org.yawlfoundation.yawl.tpot2.fluent;

import org.yawlfoundation.yawl.tpot2.Tpot2Bridge;
import org.yawlfoundation.yawl.tpot2.Tpot2Config;
import org.yawlfoundation.yawl.tpot2.Tpot2Exception;
import org.yawlfoundation.yawl.tpot2.Tpot2Result;
import org.yawlfoundation.yawl.tpot2.TrainingDataset;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TPOT2 AutoML optimizer for process mining.
 *
 * <p>This class wraps the TPOT2 genetic programming optimizer for process mining
 * tasks like case outcome prediction, remaining time estimation, and anomaly detection.
 *
 * <p><b>JOR4J Pattern:</b></p>
 * <pre>
 * Java (Tpot2Optimizer) → OTP (tpot2_bridge.erl) → Rust (yawl_ml_bridge NIF)
 *      → PyO3 → Python (tpot2)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2Optimizer implements AutoCloseable {

    private final Tpot2Config config;
    private final List<double[]> features;
    private final List<String> labels;
    private final Tpot2Bridge bridge;
    private Tpot2Result lastResult;

    /**
     * Create an optimizer with configuration.
     *
     * @param config TPOT2 configuration
     * @param features training features (may be null if using pre-collected data)
     * @param labels training labels (may be null if using pre-collected data)
     * @throws Tpot2Exception if the bridge cannot be initialized
     */
    Tpot2Optimizer(Tpot2Config config, List<double[]> features, List<?> labels) throws Tpot2Exception {
        this.config = config;
        this.features = features;
        this.labels = labels != null ? labels.stream().map(Object::toString).collect(Collectors.toList()) : null;
        this.bridge = new Tpot2Bridge();
    }

    /**
     * Get the configuration used by this optimizer.
     *
     * @return the configuration
     */
    public Tpot2Config config() {
        return config;
    }

    /**
     * Run TPOT2 optimization to find the best ML pipeline.
     *
     * <p>This is the main entry point for TPOT2 training. The optimizer will:
     * <ol>
     *   <li>Convert training data to CSV format</li>
     *   <li>Launch Python subprocess with TPOT2</li>
     *   <li>Run evolutionary optimization for specified generations</li>
     *   <li>Export best pipeline as ONNX</li>
     *   <li>Return results including model bytes and score</li>
     * </ol>
     *
     * @return the optimization result containing the best pipeline
     * @throws Tpot2Exception if training fails or Python dependencies are missing
     * @throws IllegalStateException if training data was not provided
     */
    public Tpot2Result fit() throws Tpot2Exception, Exception {
        if (features == null || labels == null) {
            throw new IllegalStateException(
                "Training data required. Call .trainingData() during builder setup.");
        }

        TrainingDataset dataset = new TrainingDataset(
            generateFeatureNames(features),
            features,
            new ArrayList<>(labels.stream().map(Object::toString).toList()),
            "yawl-fluent-api",
            features.size()
        );

        lastResult = bridge.fit(dataset, config);
        return lastResult;
    }

    /**
     * Get the last optimization result.
     *
     * @return the last result, or null if fit() has not been called
     */
    public Tpot2Result lastResult() {
        return lastResult;
    }

    /**
     * Check if fit() has been called successfully.
     *
     * @return true if a model has been trained
     */
    public boolean isTrained() {
        return lastResult != null;
    }

    /**
     * Get the best pipeline score from the last fit.
     *
     * @return the best cross-validated score
     * @throws IllegalStateException if fit() has not been called
     */
    public double bestScore() {
        if (lastResult == null) {
            throw new IllegalStateException("fit() has not been called");
        }
        return lastResult.bestScore();
    }

    /**
     * Get the ONNX model bytes from the last fit.
     *
     * @return the serialized ONNX model
     * @throws IllegalStateException if fit() has not been called
     */
    public byte[] onnxModelBytes() {
        if (lastResult == null) {
            throw new IllegalStateException("fit() has not been called");
        }
        return lastResult.onnxModelBytes();
    }

    /**
     * Get the training time from the last fit.
     *
     * @return training time in milliseconds
     * @throws IllegalStateException if fit() has not been called
     */
    public long trainingTimeMs() {
        if (lastResult == null) {
            throw new IllegalStateException("fit() has not been called");
        }
        return lastResult.trainingTimeMs();
    }

    /**
     * Close the optimizer and release resources.
     *
     * <p>This should be called when the optimizer is no longer needed.
     */
    @Override
    public void close() {
        bridge.close();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<String> generateFeatureNames(List<double[]> features) {
        int numFeatures = features.isEmpty() ? 0 : features.get(0).length;
        List<String> names = new ArrayList<>(numFeatures);
        for (int i = 0; i < numFeatures; i++) {
            names.add("feature_" + i);
        }
        return names;
    }
}
