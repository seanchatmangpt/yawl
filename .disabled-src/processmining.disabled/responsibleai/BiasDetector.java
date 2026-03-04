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
import java.util.stream.Collectors;

/**
 * Bias detection in process mining using categorical correlation analysis.
 *
 * <p>Identifies attributes that have high correlation with process outcomes
 * (decisions), which may indicate unintended bias in the process.</p>
 *
 * <h2>Bias Measurement</h2>
 * <p>Uses Cramér's V statistic for categorical variables:
 * <ul>
 *   <li>0.0 = no association (unbiased)</li>
 *   <li>1.0 = perfect association (severe bias)</li>
 *   <li>Threshold: correlation > 0.3 = potential bias</li>
 * </ul>
 *
 * @author YAWL Foundation (Responsible Process Mining)
 * @version 6.0
 */
public final class BiasDetector {

    /**
     * Bias detection result.
     *
     * @param attributeCorrelations map of attribute name to correlation with outcome
     * @param highBiasAttributes attributes with correlation > threshold
     * @param biasDetected true if any attribute correlation exceeds threshold
     */
    public record BiasReport(
        Map<String, Double> attributeCorrelations,
        List<String> highBiasAttributes,
        boolean biasDetected
    ) {}

    /**
     * Detect bias in process outcomes by analyzing attribute correlations.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each attribute in caseAttributes:
     *       <ul>
     *         <li>Create contingency table: attribute_value × outcome</li>
     *         <li>Compute Cramér's V statistic</li>
     *       </ul>
     *   <li>Identify attributes with correlation > threshold as high-bias</li>
     * </ol>
     *
     * @param caseAttributes list of case attribute maps (caseId → attributes)
     * @param decisions list of decision maps (caseId → decision outcome)
     * @param outcomeAttribute which decision attribute to analyze (e.g., "decision")
     * @param correlationThreshold bias threshold (default 0.3)
     * @return bias detection report
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if caseAttributes and decisions have different sizes
     */
    public static BiasReport detect(
            List<Map<String, String>> caseAttributes,
            List<Map<String, String>> decisions,
            String outcomeAttribute,
            double correlationThreshold) {

        Objects.requireNonNull(caseAttributes, "caseAttributes is required");
        Objects.requireNonNull(decisions, "decisions is required");
        Objects.requireNonNull(outcomeAttribute, "outcomeAttribute is required");

        if (caseAttributes.size() != decisions.size()) {
            throw new IllegalArgumentException(
                "caseAttributes and decisions must have the same size"
            );
        }

        if (caseAttributes.isEmpty()) {
            return new BiasReport(
                Collections.emptyMap(),
                Collections.emptyList(),
                false
            );
        }

        Map<String, Double> attributeCorrelations = new HashMap<>();
        Set<String> attributeNames = caseAttributes.stream()
            .flatMap(attrs -> attrs.keySet().stream())
            .collect(Collectors.toSet());

        // Extract outcomes
        List<String> outcomes = decisions.stream()
            .map(dec -> dec.get(outcomeAttribute))
            .collect(Collectors.toList());

        // Compute correlation for each attribute
        for (String attrName : attributeNames) {
            List<String> attrValues = caseAttributes.stream()
                .map(attrs -> attrs.getOrDefault(attrName, "unknown"))
                .collect(Collectors.toList());

            double correlation = computeCremaersV(attrValues, outcomes);
            attributeCorrelations.put(attrName, correlation);
        }

        // Identify high-bias attributes
        List<String> highBiasAttributes = attributeCorrelations.entrySet()
            .stream()
            .filter(e -> e.getValue() > correlationThreshold)
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        boolean biasDetected = !highBiasAttributes.isEmpty();

        return new BiasReport(
            Collections.unmodifiableMap(attributeCorrelations),
            Collections.unmodifiableList(highBiasAttributes),
            biasDetected
        );
    }

    /**
     * Detect bias using default correlation threshold (0.3).
     *
     * @param caseAttributes list of case attribute maps
     * @param decisions list of decision maps
     * @param outcomeAttribute which decision attribute to analyze
     * @return bias detection report
     */
    public static BiasReport detect(
            List<Map<String, String>> caseAttributes,
            List<Map<String, String>> decisions,
            String outcomeAttribute) {
        return detect(caseAttributes, decisions, outcomeAttribute, 0.3);
    }

    /**
     * Compute Cramér's V statistic for categorical association.
     *
     * <p>Formula: V = sqrt(chi² / (n * (k-1)))
     * where:
     * - chi² = chi-squared statistic
     * - n = total sample size
     * - k = min(#categories_x, #categories_y)
     *
     * @param variable1 first categorical variable (attribute values)
     * @param variable2 second categorical variable (outcomes)
     * @return Cramér's V in range [0, 1]
     */
    private static double computeCremaersV(List<String> variable1, List<String> variable2) {
        if (variable1.size() != variable2.size()) {
            throw new IllegalArgumentException("Variables must have same size");
        }

        int n = variable1.size();
        if (n == 0) {
            return 0.0;
        }

        // Build contingency table
        Map<String, Map<String, Integer>> contingency = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String val1 = variable1.get(i);
            String val2 = variable2.get(i);
            contingency.computeIfAbsent(val1, k -> new HashMap<>())
                .merge(val2, 1, Integer::sum);
        }

        // Compute chi-squared statistic
        double expectedPerCell = (double) n / (contingency.size() * 2); // rough estimate
        double chiSquared = 0.0;

        for (Map<String, Integer> rowCounts : contingency.values()) {
            for (int observed : rowCounts.values()) {
                if (observed > 0) {
                    double expected = expectedPerCell;
                    double diff = observed - expected;
                    chiSquared += (diff * diff) / expected;
                }
            }
        }

        // Compute Cramér's V
        int minCategories = Math.min(
            contingency.size(),
            contingency.values().stream()
                .mapToInt(Map::size)
                .max()
                .orElse(1)
        );

        double cramersV = Math.sqrt(chiSquared / (n * (minCategories - 1)));
        return Math.min(cramersV, 1.0); // Clamp to [0, 1]
    }
}
