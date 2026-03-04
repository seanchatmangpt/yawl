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

package org.yawlfoundation.yawl.integration.processmining.responsibleai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fairness analysis for process mining using demographic parity and disparate impact.
 *
 * <p>Implements van der Aalst's "Responsible Process Mining" (2022-2024) framework
 * to detect bias in process outcomes (e.g., approvals, escalations) across
 * demographic groups defined by a sensitive attribute.</p>
 *
 * <h2>Fairness Metrics</h2>
 * <ul>
 *   <li><b>Demographic Parity:</b> P(decision=positive|group=A) ≈ P(decision=positive|group=B)
 *       Measured as correlation: 1.0 = perfect parity, 0.0 = maximum disparity</li>
 *   <li><b>Disparate Impact (Four-Fifths Rule):</b>
 *       min(P(D|A1)/P(D|A2), P(D|A2)/P(D|A1)) ≥ 0.8 (US EEOC standard)
 *       Must-pass threshold for legal compliance</li>
 * </ul>
 *
 * @author YAWL Foundation (Wil van der Aalst, Responsible Process Mining)
 * @version 6.0
 */
public final class FairnessAnalyzer {

    /**
     * Fairness analysis report.
     *
     * @param sensitiveAttribute the attribute used for grouping (e.g., "department", "resource")
     * @param demographicParity correlation measure (1.0 = perfect parity, 0.0 = maximum disparity)
     * @param disparateImpact four-fifths rule ratio (≥ 0.8 = fair, < 0.8 = unfair)
     * @param decisionRateByGroup decision rates per group (group → positive outcome rate)
     * @param violations list of fairness violations found
     * @param isFair whether disparateImpact ≥ 0.8 (legal standard)
     */
    public record FairnessReport(
        String sensitiveAttribute,
        double demographicParity,
        double disparateImpact,
        Map<String, Double> decisionRateByGroup,
        List<String> violations,
        boolean isFair
    ) {}

    /**
     * Analyze fairness of process outcomes across demographic groups.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Group cases by sensitive attribute value</li>
     *   <li>For each group, compute positive decision rate = (positive count) / (total count)</li>
     *   <li>Compute disparate impact = min(rate_A/rate_B, rate_B/rate_A)</li>
     *   <li>Mark as fair if disparate impact ≥ 0.8 (four-fifths rule)</li>
     *   <li>Compute demographic parity as correlation of rates</li>
     * </ol>
     *
     * @param caseAttributes list of case attribute maps (caseId → attributes)
     * @param decisions list of decision maps (caseId → decision outcome "positive"/"negative")
     * @param sensitiveAttribute the attribute name to analyze for bias (e.g., "resource", "department")
     * @param positiveOutcome the value representing a positive decision (e.g., "approved")
     * @return fairness report with metrics and violations
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if caseAttributes and decisions have different sizes
     */
    public static FairnessReport analyze(
            List<Map<String, String>> caseAttributes,
            List<Map<String, String>> decisions,
            String sensitiveAttribute,
            String positiveOutcome) {

        Objects.requireNonNull(caseAttributes, "caseAttributes is required");
        Objects.requireNonNull(decisions, "decisions is required");
        Objects.requireNonNull(sensitiveAttribute, "sensitiveAttribute is required");
        Objects.requireNonNull(positiveOutcome, "positiveOutcome is required");

        if (caseAttributes.size() != decisions.size()) {
            throw new IllegalArgumentException(
                "caseAttributes and decisions must have the same size"
            );
        }

        if (caseAttributes.isEmpty()) {
            return new FairnessReport(
                sensitiveAttribute, 1.0, 1.0, Collections.emptyMap(),
                Collections.singletonList("No cases to analyze"),
                true
            );
        }

        // Group by sensitive attribute value
        Map<String, List<Integer>> casesByGroup = new HashMap<>();
        for (int i = 0; i < caseAttributes.size(); i++) {
            String group = caseAttributes.get(i).getOrDefault(sensitiveAttribute, "unknown");
            casesByGroup.computeIfAbsent(group, k -> new ArrayList<>())
                .add(i);
        }

        // Compute decision rates per group
        Map<String, Double> decisionRateByGroup = new HashMap<>();
        List<Double> rateValues = new ArrayList<>();

        for (String group : casesByGroup.keySet()) {
            List<Integer> groupCaseIndices = casesByGroup.get(group);
            long positiveCount = groupCaseIndices.stream()
                .filter(i -> positiveOutcome.equals(decisions.get(i).get("decision")))
                .count();

            double rate = (double) positiveCount / groupCaseIndices.size();
            decisionRateByGroup.put(group, rate);
            rateValues.add(rate);
        }

        // Compute disparate impact (four-fifths rule)
        double disparateImpact = 1.0;
        if (!rateValues.isEmpty()) {
            double maxRate = rateValues.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            double minRate = rateValues.stream().mapToDouble(Double::doubleValue).min().orElse(1.0);
            if (maxRate > 0) {
                disparateImpact = minRate / maxRate;
            }
        }

        // Compute demographic parity (correlation of rates)
        double demographicParity = computeDemographicParity(rateValues);

        // Determine violations
        List<String> violations = new ArrayList<>();
        if (disparateImpact < 0.8) {
            violations.add(
                "Disparate impact violation: " + String.format("%.3f", disparateImpact) +
                " < 0.8 (four-fifths rule). Decision rates: " +
                decisionRateByGroup.entrySet().stream()
                    .map(e -> e.getKey() + "=" + String.format("%.1f%%", e.getValue() * 100))
                    .collect(java.util.stream.Collectors.joining(", "))
            );
        }

        if (demographicParity < 0.7) {
            violations.add(
                "High demographic disparity: parity=" + String.format("%.3f", demographicParity)
            );
        }

        return new FairnessReport(
            sensitiveAttribute,
            demographicParity,
            disparateImpact,
            Collections.unmodifiableMap(decisionRateByGroup),
            Collections.unmodifiableList(violations),
            disparateImpact >= 0.8
        );
    }

    /**
     * Compute demographic parity as variance-based correlation.
     *
     * <p>Uses the ratio of minimum to maximum rate:
     * - 1.0 = all rates equal (perfect parity)
     * - 0.0 = one rate is zero while others are non-zero (maximum disparity)
     *
     * Formula: parity = min(rates) / max(rates)
     */
    private static double computeDemographicParity(List<Double> rateValues) {
        if (rateValues.isEmpty() || rateValues.size() == 1) {
            return 1.0;
        }

        double maxRate = rateValues.stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(1.0);
        double minRate = rateValues.stream()
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0.0);

        if (maxRate == 0.0) {
            return 1.0;
        }

        return minRate / maxRate;
    }
}
