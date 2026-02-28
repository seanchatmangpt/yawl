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

package org.yawlfoundation.yawl.dspy.learning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable training example for DSPy BootstrapFewShot compilation.
 *
 * <p>Represents an input-output pair extracted from historical work items,
 * used to train DSPy programs via bootstrap compilation. Each example maps
 * a natural language workflow description to a compiled POWL JSON output.</p>
 *
 * @param input    Natural language workflow description (from YWorkItem metadata)
 * @param output   Compiled POWL JSON representation (expected or achieved output)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyTrainingExample(
        @JsonProperty("input")
        String input,

        @JsonProperty("output")
        Map<String, Object> output
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Compact constructor with validation.
     */
    public DspyTrainingExample {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (output.isEmpty()) {
            throw new IllegalArgumentException("output must not be empty");
        }
    }
}
