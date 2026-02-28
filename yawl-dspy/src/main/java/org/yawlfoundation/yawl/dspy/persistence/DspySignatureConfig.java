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

import java.util.List;

/**
 * DSPy signature configuration from a saved program.
 *
 * <p>A DSPy signature defines the input/output contract for a predictor,
 * including instructions and field definitions.</p>
 *
 * @param instructions   the prompt instructions for the signature
 * @param inputFields    list of input field definitions
 * @param outputFields   list of output field definitions
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspySignatureConfig(
        @JsonProperty("instructions")
        @Nullable String instructions,

        @JsonProperty("input_fields")
        List<DspyFieldConfig> inputFields,

        @JsonProperty("output_fields")
        List<DspyFieldConfig> outputFields
) {
    /**
     * Returns the number of input fields.
     */
    public int inputCount() {
        return inputFields != null ? inputFields.size() : 0;
    }

    /**
     * Returns the number of output fields.
     */
    public int outputCount() {
        return outputFields != null ? outputFields.size() : 0;
    }
}
