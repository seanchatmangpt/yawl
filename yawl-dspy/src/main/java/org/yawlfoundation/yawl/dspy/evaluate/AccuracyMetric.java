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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Accuracy metric for classification tasks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class AccuracyMetric implements Metric {

    private final List<String> fields;

    AccuracyMetric(String... fields) {
        this.fields = List.of(fields);
    }

    @Override
    public double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace) {
        int correct = 0;
        int total = 0;

        for (String field : fields) {
            if (expected.containsKey(field)) {
                total++;
                Object predVal = predicted.values().get(field);
                if (Objects.equals(predVal, expected.get(field))) {
                    correct++;
                }
            }
        }

        return total == 0 ? 0.0 : (double) correct / total;
    }

    @Override
    public String name() {
        return "accuracy";
    }
}
