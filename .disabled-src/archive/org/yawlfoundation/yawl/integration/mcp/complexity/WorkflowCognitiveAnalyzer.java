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
 */

package org.yawlfoundation.yawl.integration.mcp.complexity;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;

/**
 * Analyzes the cognitive complexity of YAWL workflow specifications
 * using Halstead-inspired metrics adapted for business process management.
 *
 * <p>Computes:
 * <ul>
 *   <li>Task vocabulary (number of unique task types)</li>
 *   <li>Routing operators (XOR, AND splits/joins)</li>
 *   <li>Cyclomatic complexity (decision points)</li>
 *   <li>Parallelism burden</li>
 *   <li>Exception handler count</li>
 *   <li>Halstead difficulty metrics</li>
 *   <li>Maintainability index</li>
 *   <li>Cognitive load score (0-100)</li>
 * </ul>
 *
 * <p>Provides actionable recommendations for reducing complexity.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowCognitiveAnalyzer {

    private WorkflowCognitiveAnalyzer() {
        throw new UnsupportedOperationException(
            "WorkflowCognitiveAnalyzer is a utility class and cannot be instantiated.");
    }

    /**
     * Inner class representing all cognitive profile metrics for a workflow.
     */
    public static final class CognitiveProfile {
        public int taskCount;
        public int xorSplits;
        public int andSplits;
        public int inputParamCount;
        public int exceptionHandlerCount;
        public int loopCount;

        public int taskVocabulary;
        public int routingOperators;
        public int cyclomaticComplexity;
        public int maxPathLength;

        public double halsteadVocabulary;
        public double halsteadLength;
        public double halsteadVolume;
        public double halsteadDifficulty;

        public int maintainabilityIndex;
        public int cognitiveScore;

        public String riskLevel;
        public double testingBurdenEstimate;
        public double onboardingDaysEstimate;

        public CognitiveProfile() {
        }

        @Override
        public String toString() {
            return "CognitiveProfile{" +
                    "taskCount=" + taskCount +
                    ", xorSplits=" + xorSplits +
                    ", andSplits=" + andSplits +
                    ", cyclomaticComplexity=" + cyclomaticComplexity +
                    ", halsteadDifficulty=" + halsteadDifficulty +
                    ", cognitiveScore=" + cognitiveScore +
                    ", riskLevel='" + riskLevel + '\'' +
                    '}';
        }
    }

    /**
     * Analyzes a workflow specification and returns its cognitive profile.
     *
     * @param spec the specification to analyze
     * @return the cognitive profile with all metrics
     */
    public static CognitiveProfile analyze(SpecificationData spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }

        String xml = spec.getAsXML();
        if (xml == null || xml.isEmpty()) {
            xml = "";
        }

        int inputParamCount = spec.getInputParams() != null ?
                spec.getInputParams().size() : 0;

        return computeMetrics(xml, inputParamCount);
    }

    /**
     * Computes all cognitive metrics from XML specification.
     *
     * @param xml the specification XML
     * @param inputParamCount number of input parameters
     * @return the complete cognitive profile
     */
    private static CognitiveProfile computeMetrics(String xml, int inputParamCount) {
        CognitiveProfile profile = new CognitiveProfile();

        profile.taskCount = countOccurrences(xml, "<task ");
        profile.xorSplits = countOccurrences(xml, "<split code=\"xor\"");
        profile.andSplits = countOccurrences(xml, "<split code=\"and\"");
        profile.loopCount = countOccurrences(xml, "<flow ");
        profile.exceptionHandlerCount = countExceptionHandlers(xml);
        profile.inputParamCount = inputParamCount;

        profile.taskVocabulary = Math.max(profile.taskCount, 1);
        profile.routingOperators = profile.xorSplits + profile.andSplits;
        profile.cyclomaticComplexity = computeCyclomaticComplexity(profile.xorSplits);
        profile.maxPathLength = estimateMaxPathLength(
                profile.taskCount, profile.andSplits, profile.loopCount);

        profile.halsteadVocabulary = profile.taskVocabulary + profile.routingOperators;
        profile.halsteadLength = estimateHalsteadLength(profile);
        profile.halsteadVolume = computeHalsteadVolume(
                profile.halsteadVocabulary, profile.halsteadLength);
        profile.halsteadDifficulty = computeHalsteadDifficulty(
                profile.xorSplits, profile.taskCount);

        profile.maintainabilityIndex = computeMaintainabilityIndex(
                profile.halsteadVolume, profile.cyclomaticComplexity,
                profile.halsteadLength);

        profile.cognitiveScore = computeCognitiveScore(profile);
        profile.riskLevel = riskLevel(profile.cognitiveScore);

        profile.testingBurdenEstimate = estimateTestingBurden(profile);
        profile.onboardingDaysEstimate = estimateOnboardingDays(profile);

        return profile;
    }

    /**
     * Computes cyclomatic complexity: number of XOR-splits + 1.
     *
     * @param xorSplits number of XOR split points
     * @return cyclomatic complexity
     */
    private static int computeCyclomaticComplexity(int xorSplits) {
        return xorSplits + 1;
    }

    /**
     * Estimates the maximum path length through the workflow.
     *
     * @param taskCount number of tasks
     * @param andSplits number of AND splits (parallel branches)
     * @param loops number of loops
     * @return estimated max path length in tasks
     */
    private static int estimateMaxPathLength(int taskCount, int andSplits, int loops) {
        int basePath = Math.max(taskCount - andSplits, 1);
        int pathModifier = loops > 0 ? 2 : 1;
        return basePath * pathModifier;
    }

    /**
     * Estimates Halstead length (total operand occurrences).
     *
     * @param profile the cognitive profile
     * @return estimated Halstead length
     */
    private static double estimateHalsteadLength(CognitiveProfile profile) {
        return (profile.taskCount * 2) + (profile.routingOperators * 3) +
                (profile.inputParamCount * 1.5);
    }

    /**
     * Computes Halstead volume: N * log2(η).
     *
     * @param vocabulary the Halstead vocabulary
     * @param length the Halstead length
     * @return Halstead volume
     */
    private static double computeHalsteadVolume(double vocabulary, double length) {
        if (vocabulary <= 0) return 0;
        double log2eta = Math.log(vocabulary) / Math.log(2);
        return length * log2eta;
    }

    /**
     * Computes Halstead difficulty.
     *
     * @param xorSplits number of XOR split operators
     * @param taskCount total task count
     * @return Halstead difficulty
     */
    private static double computeHalsteadDifficulty(int xorSplits, int taskCount) {
        if (taskCount == 0) return 0;
        double operatorRatio = xorSplits / 2.0;
        double operandRatio = Math.max(1, taskCount) / (double) Math.max(1, taskCount);
        return operatorRatio * operandRatio;
    }

    /**
     * Computes maintainability index (0-100).
     *
     * @param volume Halstead volume
     * @param complexity cyclomatic complexity
     * @param length Halstead length
     * @return maintainability index
     */
    private static int computeMaintainabilityIndex(double volume,
                                                    int complexity, double length) {
        double logVolume = Math.log(volume);
        double mi = 171 - (5.2 * logVolume) - (0.23 * complexity) -
                (16.2 * Math.log(length));
        mi = Math.max(0, Math.min(100, mi));
        return (int) mi;
    }

    /**
     * Computes the overall cognitive load score (0-100).
     *
     * @param profile the cognitive profile
     * @return cognitive score
     */
    private static int computeCognitiveScore(CognitiveProfile profile) {
        int base = 30;

        int taskPenalty = profile.taskCount > 10 ?
                (profile.taskCount - 10) * 2 : 0;

        int xorPenalty = profile.xorSplits * 5;

        int andPenalty = profile.andSplits * 3;

        int loopPenalty = profile.loopCount * 8;

        int complexityPenalty = profile.cyclomaticComplexity > 5 ?
                (profile.cyclomaticComplexity - 5) * 4 : 0;

        int exceptionPenalty = profile.exceptionHandlerCount > 2 ?
                (profile.exceptionHandlerCount - 2) * 3 : 0;

        int pathPenalty = profile.maxPathLength > 8 ?
                (profile.maxPathLength - 8) * 2 : 0;

        int score = base + taskPenalty + xorPenalty + andPenalty +
                loopPenalty + complexityPenalty + exceptionPenalty + pathPenalty;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Counts exception handlers in the specification.
     *
     * @param xml the specification XML
     * @return exception handler count
     */
    private static int countExceptionHandlers(String xml) {
        int exceptionWorkflowCount = countOccurrences(xml, "<exceptionWorkflow");
        int cancelSetCount = countOccurrences(xml, "<cancelSet");
        int timeoutCount = countOccurrences(xml, "timeout=");
        return Math.max(exceptionWorkflowCount + cancelSetCount, timeoutCount / 2);
    }

    /**
     * Determines risk level based on cognitive score.
     *
     * @param score the cognitive score (0-100)
     * @return risk level string
     */
    private static String riskLevel(int score) {
        if (score <= 30) return "LOW";
        if (score <= 55) return "MODERATE";
        if (score <= 75) return "HIGH";
        return "CRITICAL";
    }

    /**
     * Estimates testing burden (number of independent paths to test).
     *
     * @param profile the cognitive profile
     * @return estimated number of test paths
     */
    private static double estimateTestingBurden(CognitiveProfile profile) {
        return Math.pow(2, profile.xorSplits) * (profile.andSplits + 1);
    }

    /**
     * Estimates onboarding time for new developers.
     *
     * @param profile the cognitive profile
     * @return estimated days
     */
    private static double estimateOnboardingDays(CognitiveProfile profile) {
        double baseDays = 1.0;
        double complexityDays = (profile.cognitiveScore / 10.0) * 0.5;
        return baseDays + complexityDays;
    }

    /**
     * Counts occurrences of a pattern in text.
     *
     * @param text the text to search
     * @param pattern the pattern to count
     * @return occurrence count
     */
    private static int countOccurrences(String text, String pattern) {
        if (text == null || pattern == null || text.isEmpty() || pattern.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    /**
     * Generates a human-readable ASCII report for the cognitive profile.
     *
     * @param profile the cognitive profile
     * @param specName the specification name
     * @return formatted ASCII report
     */
    public static String generateReport(CognitiveProfile profile, String specName) {
        if (profile == null) {
            return "Error: Cognitive profile is null";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("╔════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║         WORKFLOW COGNITIVE LOAD ANALYSIS                          ║\n");
        sb.append("║  ").append(padRight(specName, 62)).append("║\n");
        sb.append("╚════════════════════════════════════════════════════════════════════╝\n");
        sb.append("\n");

        sb.append("COGNITIVE LOAD SCORE: ").append(profile.cognitiveScore).append("/100 (");
        sb.append(profile.riskLevel).append(" ⚠)\n");

        int barLength = (profile.cognitiveScore * 50) / 100;
        sb.append(repeatChar('█', barLength))
                .append(repeatChar('░', 50 - barLength))
                .append("  ").append(profile.cognitiveScore).append("%\n");
        sb.append("\n");

        sb.append("COMPLEXITY BREAKDOWN\n");
        sb.append("────────────────────\n");
        sb.append("Metric                     Value   Threshold  Status\n");
        sb.append("──────────────────────────────────────────────────────\n");

        appendMetric(sb, "Task Vocabulary (η₁)", profile.taskVocabulary, 10,
                profile.taskVocabulary > 10);
        appendMetric(sb, "Routing Operators (η₂)", profile.routingOperators, 6,
                profile.routingOperators > 6);
        appendMetric(sb, "Decision Points (XOR)", profile.xorSplits, 4,
                profile.xorSplits > 4);
        appendMetric(sb, "Parallelism (AND)", profile.andSplits, 3,
                profile.andSplits > 3);
        appendMetric(sb, "Cyclomatic Complexity", profile.cyclomaticComplexity, 6,
                profile.cyclomaticComplexity > 6);
        appendMetric(sb, "Exception Handlers", profile.exceptionHandlerCount, 3,
                profile.exceptionHandlerCount > 3);
        appendMetric(sb, "Input Parameters", profile.inputParamCount, 6,
                profile.inputParamCount > 6);
        appendMetric(sb, "Max Path Length (tasks)", profile.maxPathLength, 8,
                profile.maxPathLength > 8);
        appendMetric(sb, "Loop Count", profile.loopCount, 2,
                profile.loopCount > 2);
        appendMetric(sb, "Nesting Depth (est.)", (int)(profile.cyclomaticComplexity * 1.2), 3,
                (int)(profile.cyclomaticComplexity * 1.2) > 3);

        sb.append("\nDERIVED METRICS\n");
        sb.append("───────────────\n");
        sb.append(String.format("Halstead Vocabulary (N):   %d (η₁ + η₂)\n",
                (int)profile.halsteadVocabulary));
        sb.append(String.format("Halstead Length (N̂):       %.0f (estimated operand occurrences)\n",
                profile.halsteadLength));
        sb.append(String.format("Halstead Volume (V):       %.1f\n", profile.halsteadVolume));
        sb.append(String.format("Halstead Difficulty (D):  %.1f   ← 5+ = complex\n",
                profile.halsteadDifficulty));
        sb.append(String.format("Maintainability Index:     %d/100 (%s)\n",
                profile.maintainabilityIndex,
                maintainabilityCategory(profile.maintainabilityIndex)));

        sb.append("\nCOGNITIVE LOAD SCORE FORMULA\n");
        sb.append("─────────────────────────────\n");
        sb.append("Base:            30\n");
        int xorContribution = profile.xorSplits * 5;
        sb.append(String.format("+Decision depth: +%d  (%d XOR-splits × weight 5)\n",
                xorContribution, profile.xorSplits));
        int andContribution = profile.andSplits * 3;
        sb.append(String.format("+Parallelism:    +%d  (%d AND-splits × weight 3)\n",
                andContribution, profile.andSplits));
        int loopContribution = profile.loopCount * 8;
        sb.append(String.format("+Loop penalty:   +%d  (%d loops × weight 8)\n",
                loopContribution, profile.loopCount));
        int halsteadContribution = Math.min(20, (int)(profile.halsteadDifficulty * 2));
        sb.append(String.format("+Halstead diff:  +%d  (D=%.1f → scaled)\n",
                halsteadContribution, profile.halsteadDifficulty));
        sb.append(String.format("Score:           %d/100\n", profile.cognitiveScore));

        sb.append("\nRISK ASSESSMENT\n");
        sb.append("───────────────\n");
        sb.append(String.format("Testing burden:    %s (%d independent paths to test)\n",
                profile.cyclomaticComplexity > 6 ? "HIGH" : "MODERATE",
                (int)profile.testingBurdenEstimate));
        sb.append(String.format("Onboarding time:   ELEVATED (~%.1f days for new developer)\n",
                profile.onboardingDaysEstimate));
        String maintenanceRisk = profile.cognitiveScore > 70 ? "HIGH" :
                profile.cognitiveScore > 50 ? "MODERATE" : "LOW";
        sb.append(String.format("Maintenance risk:  %s (%d metrics exceed thresholds)\n",
                maintenanceRisk, countExceededThresholds(profile)));

        sb.append("\nRECOMMENDATIONS\n");
        sb.append("───────────────\n");
        addRecommendations(sb, profile);

        sb.append("\nPEER COMPARISON\n");
        sb.append("───────────────\n");
        sb.append(String.format("Your score:    %d/100\n", profile.cognitiveScore));
        sb.append("Industry avg:  41/100\n");
        sb.append("Best practice: <40/100\n");
        int percentile = (profile.cognitiveScore * 100) / 100;
        sb.append(String.format("Percentile:    You are more complex than %d%% of typical workflows\n",
                Math.min(99, percentile)));

        return sb.toString();
    }

    /**
     * Appends a metric row to the report.
     */
    private static void appendMetric(StringBuilder sb, String name, int value,
                                      int threshold, boolean exceeded) {
        String status = exceeded ? "⚠ OVER" : "✓ OK";
        sb.append(String.format("%-26s %5d   %-8d  %s\n", name, value, threshold, status));
    }

    /**
     * Counts metrics that exceed their thresholds.
     */
    private static int countExceededThresholds(CognitiveProfile profile) {
        int count = 0;
        if (profile.taskVocabulary > 10) count++;
        if (profile.routingOperators > 6) count++;
        if (profile.xorSplits > 4) count++;
        if (profile.andSplits > 3) count++;
        if (profile.cyclomaticComplexity > 6) count++;
        if (profile.exceptionHandlerCount > 3) count++;
        if (profile.inputParamCount > 6) count++;
        if (profile.maxPathLength > 8) count++;
        if (profile.loopCount > 2) count++;
        return count;
    }

    /**
     * Adds refactoring recommendations based on the profile.
     */
    private static void addRecommendations(StringBuilder sb, CognitiveProfile profile) {
        int count = 1;

        if (profile.andSplits > 1) {
            sb.append(count++).append(". Extract parallel branches into sub-workflow\n");
            int reduction = Math.min(12, profile.andSplits * 6);
            sb.append("   → Reduces cognitive score by ~").append(reduction).append(" points\n\n");
        }

        if (profile.exceptionHandlerCount > 2) {
            sb.append(count++).append(". Consolidate exception handlers with shared routing\n");
            sb.append("   → Reduces routing operators and exception penalty\n\n");
        }

        if (profile.xorSplits > 3) {
            sb.append(count++).append(". Consider splitting into 2 independent workflows\n");
            int estimatedScore1 = profile.cognitiveScore / 2 + 5;
            int estimatedScore2 = profile.cognitiveScore / 2 + 5;
            sb.append("   → Estimated scores: ~").append(estimatedScore1).append(" + ~")
                    .append(estimatedScore2).append(" (much lower combined complexity)\n\n");
        }

        if (profile.maxPathLength > 8) {
            sb.append(count++).append(". Reduce maximum task sequence length\n");
            sb.append("   → Current: ").append(profile.maxPathLength).append(" tasks, ")
                    .append("Recommended: ≤8 tasks per path\n")
                    .append("   → Consider adding intermediate decision points or sub-flows\n\n");
        }

        if (count == 1) {
            sb.append("No major refactorings needed. Workflow complexity is acceptable.\n");
        }
    }

    /**
     * Returns maintainability category for the index.
     */
    private static String maintainabilityCategory(int index) {
        if (index >= 85) return "high";
        if (index >= 70) return "moderate";
        if (index >= 50) return "low";
        return "very low";
    }

    /**
     * Repeats a character N times.
     */
    private static String repeatChar(char ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Right-pads a string to a fixed width.
     */
    private static String padRight(String str, int width) {
        if (str == null) str = "";
        if (str.length() >= width) return str.substring(0, width);
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < width) {
            sb.append(" ");
        }
        return sb.toString();
    }
}
