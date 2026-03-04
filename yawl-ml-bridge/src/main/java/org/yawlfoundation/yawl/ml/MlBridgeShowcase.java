/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml;

import org.yawlfoundation.yawl.ml.dspy.*;
import org.yawlfoundation.yawl.ml.tpot2.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ML Bridge Showcase - Production presentation for Fortune 500 CTO.
 *
 * <p>Demonstrates production-ready Java API running through:
 * Java -> Erlang/OTP -> Rust NIF -> Python (DSPy 3.1.3, TPOT2)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class MlBridgeShowcase {

    public static void main(String[] args) {
        runPresentation();
    }

    public static void runPresentation() {
        printHeader();
        runDspyPrediction();
        runTpot2Optimization();
        printConclusion();
    }

    private static void printHeader() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║         YAWL ML Bridge - Production Showcase               ║");
        System.out.println("║                                                            ║");
        System.out.println("║   Architecture:                                            ║");
        System.out.println("║   Java -> Erlang/OTP -> Rust NIF -> Python (DSPy, TPOT2)  ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void runDspyPrediction() {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ FEATURE 1: DSPy Case Outcome Prediction                    │");
        System.out.println("└────────────────────────────────────────────────────────────┘");
        System.out.println();

        try {
            System.out.println("1. Define Prediction Signature:");
            Signature signature = Signature.builder()
                .description("Predict case outcome")
                .input("events", "workflow events", String.class)
                .input("duration_ms", Long.class)
                .output("outcome", "predicted outcome", String.class)
                .output("confidence", "confidence score", Double.class)
                .build();

            System.out.println("   Signature: " + signature.description());
            System.out.println();

            System.out.println("2. Configure LLM Provider (Groq):");
            DspyProgram program = DspyProgram.create(signature)
                .withGroq()
                .withExample(
                    Map.of("events", "StartTask -> Approve -> EndTask", "duration_ms", 5000L),
                    Map.of("outcome", "completed", "confidence", 0.95)
                )
                .build();

            System.out.println("   Provider: " + program.provider());
            System.out.println();

            System.out.println("3. Execute Prediction:");
            Map<String, Object> result = program.predict(Map.of(
                "events", "StartTask -> Validate -> Complete",
                "duration_ms", 3000L
            ));

            System.out.println("   Outcome: " + result.get("outcome"));
            System.out.println("   Confidence: " + result.get("confidence"));
            System.out.println();

        } catch (DspyException e) {
            System.out.println("   Status: Requires GROQ_API_KEY and running bridge");
            System.out.println("   Message: " + e.getMessage());
            System.out.println();
        }
    }

    private static void runTpot2Optimization() {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ FEATURE 2: TPOT2 Pipeline Optimization                     │");
        System.out.println("└────────────────────────────────────────────────────────────┘");
        System.out.println();

        try {
            System.out.println("1. Prepare Training Data:");
            List<List<Double>> X = generateTrainingData(100, 5);
            List<Object> y = generateTrainingLabels(100);
            System.out.println("   Samples: 100, Features: 5");
            System.out.println();

            System.out.println("2. Configure Genetic Optimizer:");
            Tpot2Optimizer optimizer = Tpot2Optimizer.quick()
                .withTimeout(Duration.ofMinutes(1))
                .withScoring("accuracy")
                .build();

            System.out.println("   Generations: 10");
            System.out.println("   Population: 50");
            System.out.println();

            System.out.println("3. Execute Optimization:");
            OptimizationResult result = optimizer.optimize(X, y);

            System.out.println("   Fitness Score: " + String.format("%.4f", result.fitnessScore()));
            System.out.println("   Generations Completed: " + result.generations());
            System.out.println("   Best Pipeline: " + truncate(result.bestPipeline(), 50));
            System.out.println();

        } catch (Tpot2Exception e) {
            System.out.println("   Status: Requires running bridge");
            System.out.println("   Message: " + e.getMessage());
            System.out.println();
        }
    }

    private static void printConclusion() {
        System.out.println("┌────────────────────────────────────────────────────────────┐");
        System.out.println("│ ARCHITECTURE BENEFITS                                      │");
        System.out.println("└────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  - Elegant Java API with fluent builders");
        System.out.println("  - Erlang/OTP supervisor for fault tolerance");
        System.out.println("  - Rust NIF for native performance");
        System.out.println("  - Python DSPy + TPOT2 for ML capabilities");
        System.out.println();
        System.out.println("  Production ready. Enterprise grade.");
        System.out.println();
    }

    private static List<List<Double>> generateTrainingData(int samples, int features) {
        List<List<Double>> data = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < features; j++) {
                row.add(Math.random() * 100);
            }
            data.add(row);
        }
        return data;
    }

    private static List<Object> generateTrainingLabels(int samples) {
        List<Object> labels = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            labels.add(i % 2 == 0 ? "positive" : "negative");
        }
        return labels;
    }

    private static String truncate(String s, int maxLen) {
        Objects.requireNonNull(s, "String cannot be null");
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
