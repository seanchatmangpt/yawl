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

package org.yawlfoundation.yawl.dspy.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of GEPA optimization for a DSPy program.
 *
 * <p>This record captures the complete optimization state including:</p>
 * <ul>
 *   <li>Optimization target (behavioral, performance, balanced)</li>
 *   <li>Overall optimization score</li>
 *   <li>Behavioral footprint agreement score</li>
 *   <li>Performance metrics</li>
 *   <li>Optimization history trail</li>
 * </ul>
 *
 * @param target              optimization target type
 * @param score               overall optimization score (0.0-1.0)
 * @param behavioralFootprint behavioral footprint data (nullable)
 * @param performanceMetrics  performance metrics map (nullable)
 * @param footprintAgreement  footprint agreement score (0.0-1.0)
 * @param optimizationHistory list of optimization steps
 * @param timestamp           when the optimization was performed
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record GepaOptimizationResult(
        @JsonProperty("target")
        String target,

        @JsonProperty("score")
        double score,

        @JsonProperty("behavioral_footprint")
        @Nullable Map<String, Object> behavioralFootprint,

        @JsonProperty("performance_metrics")
        @Nullable Map<String, Object> performanceMetrics,

        @JsonProperty("footprint_agreement")
        double footprintAgreement,

        @JsonProperty("optimization_history")
        List<Map<String, Object>> optimizationHistory,

        @JsonProperty("timestamp")
        @Nullable String timestamp
) {

    /**
     * Compact constructor with validation and defaults.
     */
    public GepaOptimizationResult {
        Objects.requireNonNull(target, "Target must not be null");

        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }

        if (footprintAgreement < 0.0 || footprintAgreement > 1.0) {
            throw new IllegalArgumentException("Footprint agreement must be between 0.0 and 1.0");
        }

        behavioralFootprint = behavioralFootprint != null
                ? Collections.unmodifiableMap(behavioralFootprint)
                : null;

        performanceMetrics = performanceMetrics != null
                ? Collections.unmodifiableMap(performanceMetrics)
                : null;

        optimizationHistory = optimizationHistory != null
                ? Collections.unmodifiableList(optimizationHistory)
                : Collections.emptyList();

        timestamp = timestamp != null ? timestamp : Instant.now().toString();
    }

    /**
     * Creates a builder for constructing GepaOptimizationResult instances.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns true if this is a perfect optimization (score == 1.0).
     *
     * @return true if perfect
     */
    public boolean isPerfect() {
        return Double.compare(score, 1.0) == 0;
    }

    /**
     * Returns true if footprint agreement is perfect (1.0).
     *
     * @return true if perfect footprint agreement
     */
    public boolean hasPerfectFootprint() {
        return Double.compare(footprintAgreement, 1.0) == 0;
    }

    /**
     * Returns the optimization target as an enum.
     *
     * @return optimization target enum
     */
    public OptimizationTarget getTargetEnum() {
        return OptimizationTarget.fromString(target);
    }

    /**
     * Returns a summary string for logging.
     *
     * @return human-readable summary
     */
    public String summary() {
        return String.format(
                "GepaOptimizationResult[target=%s, score=%.3f, footprint=%.3f, history=%d]",
                target, score, footprintAgreement, optimizationHistory.size()
        );
    }

    /**
     * Optimization target types.
     */
    public enum OptimizationTarget {
        BEHAVIORAL("behavioral"),
        PERFORMANCE("performance"),
        BALANCED("balanced");

        private final String value;

        OptimizationTarget(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static OptimizationTarget fromString(String value) {
            for (OptimizationTarget target : values()) {
                if (target.value.equalsIgnoreCase(value)) {
                    return target;
                }
            }
            return BALANCED; // Default
        }
    }

    /**
     * Builder for GepaOptimizationResult.
     */
    public static final class Builder {
        private String target = "balanced";
        private double score = 0.0;
        private @Nullable Map<String, Object> behavioralFootprint;
        private @Nullable Map<String, Object> performanceMetrics;
        private double footprintAgreement = 0.0;
        private List<Map<String, Object>> optimizationHistory = Collections.emptyList();
        private @Nullable String timestamp;

        private Builder() {}

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder target(OptimizationTarget target) {
            this.target = target.getValue();
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder behavioralFootprint(@Nullable Map<String, Object> footprint) {
            this.behavioralFootprint = footprint;
            return this;
        }

        public Builder performanceMetrics(@Nullable Map<String, Object> metrics) {
            this.performanceMetrics = metrics;
            return this;
        }

        public Builder footprintAgreement(double agreement) {
            this.footprintAgreement = agreement;
            return this;
        }

        public Builder optimizationHistory(List<Map<String, Object>> history) {
            this.optimizationHistory = history;
            return this;
        }

        public Builder timestamp(@Nullable String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public GepaOptimizationResult build() {
            return new GepaOptimizationResult(
                    target,
                    score,
                    behavioralFootprint,
                    performanceMetrics,
                    footprintAgreement,
                    optimizationHistory,
                    timestamp
            );
        }
    }
}
