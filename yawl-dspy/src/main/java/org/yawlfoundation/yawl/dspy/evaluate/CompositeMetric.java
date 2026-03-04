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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Composite metric that averages multiple metrics.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class CompositeMetric implements Metric {

    private final List<Metric> metrics;

    CompositeMetric(Metric... metrics) {
        this.metrics = List.of(metrics);
    }

    @Override
    public double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace) {
        if (metrics.isEmpty()) return 0.0;

        double sum = 0.0;
        for (Metric metric : metrics) {
            sum += metric.score(predicted, expected, trace);
        }
        return sum / metrics.size();
    }

    @Override
    public String name() {
        return "composite";
    }
}
