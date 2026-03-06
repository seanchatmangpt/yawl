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

import org.yawlfoundation.yawl.tpot2.Tpot2Config;
import org.yawlfoundation.yawl.tpot2.Tpot2TaskType;

/**
 * Fluent API entry point for TPOT2 AutoML integration.
 *
 * <p>This class provides a Python DSPy-like fluent API for TPOT2
 * following the JOR4J pattern (Java > OTP > Rust > Python).
 *
 * <h2>Quick Start:</h2>
 * <pre>{@code
 * // Configure TPOT2
 * Tpot2.configure(config -> config
 *     .taskType(Tpot2TaskType.CASE_OUTCOME)
 *     .generations(10)
 *     .maxTimeMins(30));
 *
 * // Create optimizer
 * Tpot2Optimizer optimizer = Tpot2.optimizer()
 *     .trainingData(features, labels)
 *     .build();
 *
 * // Fit model
 * Tpot2Result result = optimizer.fit();
 *
 * // Get predictions
 * double[] predictions = result.predict(newFeatures);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2 {

    private static Tpot2Config configuredConfig = null;

    private Tpot2() {
        // Private constructor - static factory methods only
    }

    /**
     * Configure TPOT2 with a fluent builder.
     *
     * @param configurer lambda to configure the TPOT2 settings
     */
    public static void configure(java.util.function.Consumer<Tpot2ConfigBuilder> configurer) {
        Tpot2ConfigBuilder builder = new Tpot2ConfigBuilder();
        configurer.accept(builder);
        configuredConfig = builder.build();
    }

    /**
     * Get the currently configured TPOT2 config.
     *
     * @return the configured config, or null if not configured
     */
    public static Tpot2Config getConfiguredConfig() {
        return configuredConfig;
    }

    /**
     * Check if TPOT2 has been configured.
     *
     * @return true if configured, false otherwise
     */
    public static boolean isConfigured() {
        return configuredConfig != null;
    }

    /**
     * Create a new optimizer builder.
     *
     * @return a new optimizer builder
     */
    public static Tpot2OptimizerBuilder optimizer() {
        return new Tpot2OptimizerBuilder();
    }

    /**
     * Create a quick optimizer for case outcome prediction.
     *
     * @return optimizer configured for case outcome prediction
     */
    public static Tpot2OptimizerBuilder caseOutcomeOptimizer() {
        return optimizer().taskType(Tpot2TaskType.CASE_OUTCOME);
    }

    /**
     * Create a quick optimizer for remaining time prediction.
     *
     * @return optimizer configured for remaining time prediction
     */
    public static Tpot2OptimizerBuilder remainingTimeOptimizer() {
        return optimizer().taskType(Tpot2TaskType.REMAINING_TIME);
    }

    /**
     * Create a quick optimizer for next activity prediction.
     *
     * @return optimizer configured for next activity prediction
     */
    public static Tpot2OptimizerBuilder nextActivityOptimizer() {
        return optimizer().taskType(Tpot2TaskType.NEXT_ACTIVITY);
    }

    /**
     * Create a quick optimizer for anomaly detection.
     *
     * @return optimizer configured for anomaly detection
     */
    public static Tpot2OptimizerBuilder anomalyDetectionOptimizer() {
        return optimizer().taskType(Tpot2TaskType.ANOMALY_DETECTION);
    }

    /**
     * Create a quick optimizer with default settings.
     *
     * @return optimizer with quick configuration (5 generations, 50 population)
     */
    public static Tpot2OptimizerBuilder quickOptimizer() {
        return optimizer()
            .generations(5)
            .populationSize(50)
            .maxTimeMins(5);
    }

    /**
     * Create a production optimizer with thorough settings.
     *
     * @return optimizer with production configuration (50 generations, 100 population)
     */
    public static Tpot2OptimizerBuilder productionOptimizer() {
        return optimizer()
            .generations(50)
            .populationSize(100)
            .maxTimeMins(60);
    }
}
