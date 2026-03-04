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

package org.yawlfoundation.yawl.dspy.signature;

import java.util.Map;

/**
 * Few-shot example for signature.
 *
 * @param inputs  example input values
 * @param outputs example output values
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record Example(
    Map<String, Object> inputs,
    Map<String, Object> outputs
) {
    public Example {
        if (inputs == null) inputs = Map.of();
        if (outputs == null) outputs = Map.of();
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    public static Example of(Map<String, Object> inputs, Map<String, Object> outputs) {
        return new Example(inputs, outputs);
    }
}
