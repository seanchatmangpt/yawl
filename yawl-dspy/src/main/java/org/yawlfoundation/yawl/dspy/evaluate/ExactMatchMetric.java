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
 * Exact match metric - returns 1.0 if all outputs match exactly.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class ExactMatchMetric implements Metric {

    @Override
    public double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace) {
        for (var entry : expected.entrySet()) {
            Object predVal = predicted.values().get(entry.getKey());
            if (!Objects.equals(predVal, entry.getValue())) {
                return 0.0;
            }
        }
        return 1.0;
    }

    @Override
    public String name() {
        return "exact_match";
    }
}
