/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.tpot2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for TPOT2 via Erlang -> Rust -> Python bridge.
 */
@DisplayName("TPOT2 End-to-End Tests")
class Tpot2EndToEndTest {

    // Bridge check moved to individual integration tests

    @Test
    @DisplayName("Create optimizer with builder")
    void testOptimizerBuilder() {
        Tpot2Optimizer optimizer = Tpot2Optimizer.create()
            .withGenerations(10)
            .withPopulationSize(50)
            .withTimeout(Duration.ofMinutes(5))
            .withScoring("accuracy")
            .build();

        assertThat(optimizer).isNotNull();
    }

    @Test
    @DisplayName("Quick optimizer preset")
    void testQuickOptimizer() {
        Tpot2Optimizer optimizer = Tpot2Optimizer.quick().build();
        assertThat(optimizer).isNotNull();
    }

    @Test
    @DisplayName("Run quick optimization")
    void testQuickOptimization() throws Tpot2Exception {
        // Skip if bridge not available
        if (!isBridgeAvailable()) {
            System.out.println("Skipping testQuickOptimization - ML Bridge not available");
            return;
        }

        // Generate simple classification data
        List<List<Double>> X = generateClassificationData(50, 4);
        List<Object> y = generateLabels(50);

        Tpot2Optimizer optimizer = Tpot2Optimizer.quick()
            .withTimeout(Duration.ofMinutes(1))
            .build();

        OptimizationResult result = optimizer.optimize(X, y);

        assertThat(result).isNotNull();
        assertThat(result.fitnessScore()).isGreaterThanOrEqualTo(0.0);

        System.out.println("Best pipeline: " + result.bestPipeline());
        System.out.println("Fitness score: " + result.fitnessScore());
    }

    // Helper methods

    private static boolean isBridgeAvailable() {
        try {
            return Tpot2BridgeClient.getDefault() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private List<List<Double>> generateClassificationData(int samples, int features) {
        List<List<Double>> data = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < features; j++) {
                row.add(Math.random() * 10);
            }
            data.add(row);
        }
        return data;
    }

    private List<Object> generateLabels(int samples) {
        List<Object> labels = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            labels.add(i % 2 == 0 ? "class_a" : "class_b");
        }
        return labels;
    }
}
