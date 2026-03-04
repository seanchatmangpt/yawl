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

import java.util.Map;
import java.util.Objects;

/**
 * F1 score metric for binary classification.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class F1Metric implements Metric {

    private final String field;
    private final String positiveValue;

    F1Metric(String field, String positiveValue) {
        this.field = field;
        this.positiveValue = positiveValue;
    }

    @Override
    public double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace) {
        Object predVal = predicted.values().get(field);
        Object expVal = expected.get(field);

        boolean predPositive = Objects.equals(predVal, positiveValue);
        boolean expPositive = Objects.equals(expVal, positiveValue);

        // Calculate F1 from single example (precision and recall are the same for single)
        if (predPositive && expPositive) {
            return 1.0; // True positive
        } else if (predPositive != expPositive) {
            return 0.0; // False positive or false negative
        } else {
            return 1.0; // True negative (neither is positive)
        }
    }

    @Override
    public String name() {
        return "f1";
    }
}
