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

/**
 * A single field in a DSPy signature.
 *
 * <p>Fields have a name and optional description, defining the
 * input/output structure of a DSPy predictor.</p>
 *
 * @param name field name
 * @param desc field description (may be null or empty)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyFieldConfig(
        @JsonProperty("name")
        String name,

        @JsonProperty("desc")
        @Nullable String desc
) {
    /**
     * Returns the description, or empty string if not set.
     */
    public String descriptionOrEmpty() {
        return desc != null ? desc : "";
    }
}
