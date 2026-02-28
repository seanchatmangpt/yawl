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

package org.yawlfoundation.yawl.dspy.program;

import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of program optimization.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OptimizationResult(
    String target,
    double score,
    Map<String, Object> behavioralFootprint,
    Map<String, Object> performanceMetrics,
    double footprintAgreement,
    List<Map<String, Object>> optimizationHistory,
    Instant timestamp
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String target;
        private double score;
        private Map<String, Object> behavioralFootprint;
        private Map<String, Object> performanceMetrics;
        private double footprintAgreement;
        private List<Map<String, Object>> optimizationHistory;
        private Instant timestamp = Instant.now();

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public Builder behavioralFootprint(Map<String, Object> behavioralFootprint) {
            this.behavioralFootprint = behavioralFootprint;
            return this;
        }

        public Builder performanceMetrics(Map<String, Object> performanceMetrics) {
            this.performanceMetrics = performanceMetrics;
            return this;
        }

        public Builder footprintAgreement(double footprintAgreement) {
            this.footprintAgreement = footprintAgreement;
            return this;
        }

        public Builder optimizationHistory(List<Map<String, Object>> optimizationHistory) {
            this.optimizationHistory = optimizationHistory;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public OptimizationResult build() {
            return new OptimizationResult(
                target, score, behavioralFootprint, performanceMetrics,
                footprintAgreement, optimizationHistory, timestamp
            );
        }
    }
}