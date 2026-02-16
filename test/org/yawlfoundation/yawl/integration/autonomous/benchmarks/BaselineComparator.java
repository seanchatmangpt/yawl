/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.benchmarks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Compare current benchmark results against baseline to detect regressions.
 *
 * Usage:
 *   java BaselineComparator baseline.json current.json 10.0
 *
 * Exit code:
 *   0 = no regression
 *   1 = regression detected
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class BaselineComparator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: BaselineComparator <baseline.json> <current.json> [threshold%]");
            System.exit(2);
        }

        Path baselinePath = Paths.get(args[0]);
        Path currentPath = Paths.get(args[1]);
        double thresholdPercent = args.length > 2 ? Double.parseDouble(args[2]) : 10.0;

        if (!Files.exists(baselinePath)) {
            System.err.println("Baseline file not found: " + baselinePath);
            System.exit(2);
        }

        if (!Files.exists(currentPath)) {
            System.err.println("Current results file not found: " + currentPath);
            System.exit(2);
        }

        try {
            String baselineJson = Files.readString(baselinePath);
            String currentJson = Files.readString(currentPath);

            boolean hasRegression = compareResults(baselineJson, currentJson, thresholdPercent);

            if (hasRegression) {
                System.err.println("\nREGRESSION DETECTED! Performance degraded beyond threshold.");
                System.exit(1);
            } else {
                System.out.println("\nNo regression detected. Performance is within acceptable limits.");
                System.exit(0);
            }

        } catch (IOException e) {
            System.err.println("Error reading files: " + e.getMessage());
            System.exit(2);
        }
    }

    private static boolean compareResults(String baselineJson, String currentJson, double thresholdPercent) {
        System.out.println("=== Baseline Comparison ===");
        System.out.println("Threshold: " + thresholdPercent + "%");
        System.out.println();

        boolean hasRegression = false;

        double baselineEligP95 = extractValue(baselineJson, "eligibility_reasoning", "generic_p95_ms");
        double currentEligP95 = extractValue(currentJson, "eligibility_reasoning", "generic_p95_ms");
        if (baselineEligP95 > 0 && currentEligP95 > 0) {
            double regression = ((currentEligP95 - baselineEligP95) / baselineEligP95) * 100.0;
            System.out.printf("Eligibility P95: %.3f ms -> %.3f ms (%.2f%%)%n",
                baselineEligP95, currentEligP95, regression);
            if (regression > thresholdPercent) {
                System.err.println("  ⚠️  REGRESSION: Exceeds " + thresholdPercent + "% threshold");
                hasRegression = true;
            } else {
                System.out.println("  ✅ OK");
            }
        }

        double baselineDecisionP95 = extractValue(baselineJson, "decision_generation", "generic_p95_ms");
        double currentDecisionP95 = extractValue(currentJson, "decision_generation", "generic_p95_ms");
        if (baselineDecisionP95 > 0 && currentDecisionP95 > 0) {
            double regression = ((currentDecisionP95 - baselineDecisionP95) / baselineDecisionP95) * 100.0;
            System.out.printf("Decision P95: %.3f ms -> %.3f ms (%.2f%%)%n",
                baselineDecisionP95, currentDecisionP95, regression);
            if (regression > thresholdPercent) {
                System.err.println("  ⚠️  REGRESSION: Exceeds " + thresholdPercent + "% threshold");
                hasRegression = true;
            } else {
                System.out.println("  ✅ OK");
            }
        }

        double baselineEligThroughput = extractValue(baselineJson, "eligibility_reasoning", "generic_throughput_ops_sec");
        double currentEligThroughput = extractValue(currentJson, "eligibility_reasoning", "generic_throughput_ops_sec");
        if (baselineEligThroughput > 0 && currentEligThroughput > 0) {
            double degradation = ((baselineEligThroughput - currentEligThroughput) / baselineEligThroughput) * 100.0;
            System.out.printf("Eligibility Throughput: %.2f ops/s -> %.2f ops/s (%.2f%%)%n",
                baselineEligThroughput, currentEligThroughput, degradation);
            if (degradation > thresholdPercent / 2.0) {
                System.err.println("  ⚠️  REGRESSION: Exceeds " + (thresholdPercent / 2.0) + "% threshold");
                hasRegression = true;
            } else {
                System.out.println("  ✅ OK");
            }
        }

        return hasRegression;
    }

    private static double extractValue(String json, String section, String key) {
        String searchKey = "\"" + section + "\"";
        int sectionStart = json.indexOf(searchKey);
        if (sectionStart < 0) {
            return -1;
        }

        int braceStart = json.indexOf("{", sectionStart);
        if (braceStart < 0) {
            return -1;
        }

        int braceEnd = json.indexOf("}", braceStart);
        if (braceEnd < 0) {
            return -1;
        }

        String sectionJson = json.substring(braceStart, braceEnd);

        String valueKey = "\"" + key + "\"";
        int keyIdx = sectionJson.indexOf(valueKey);
        if (keyIdx < 0) {
            return -1;
        }

        int colonIdx = sectionJson.indexOf(":", keyIdx);
        if (colonIdx < 0) {
            return -1;
        }

        int start = colonIdx + 1;
        while (start < sectionJson.length() && !Character.isDigit(sectionJson.charAt(start)) && sectionJson.charAt(start) != '-') {
            start++;
        }

        int end = start;
        while (end < sectionJson.length()) {
            char c = sectionJson.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '-') {
                end++;
            } else {
                break;
            }
        }

        try {
            return Double.parseDouble(sectionJson.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
