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

package org.yawlfoundation.yawl.dspy.evaluate;

import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weighted composite metric.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class WeightedMetric implements Metric {

    private final Map<Metric, Double> weightedMetrics;

    WeightedMetric(Map<Metric, Double> weightedMetrics) {
        this.weightedMetrics = Map.copyOf(weightedMetrics);
    }

    @Override
    public double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace) {
        if (weightedMetrics.isEmpty()) return 0.0;

        double totalWeight = weightedMetrics.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight == 0) return 0.0;

        double weightedSum = 0.0;
        for (var entry : weightedMetrics.entrySet()) {
            double score = entry.getKey().score(predicted, expected, trace);
            weightedSum += score * entry.getValue();
        }

        return weightedSum / totalWeight;
    }

    @Override
    public String name() {
        return "weighted";
    }
}
