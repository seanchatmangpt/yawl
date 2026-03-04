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

/**
 * Input field in a DSPy signature.
 *
 * @param name        field name (snake_case convention)
 * @param description description shown to the LLM
 * @param type        expected Java type
 * @param optional    whether this field is optional
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record InputField(
    String name,
    String description,
    Class<?> type,
    boolean optional
) implements Field {

    public InputField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        if (type == null) {
            type = String.class;
        }
    }

    public static InputField of(String name, String description, Class<?> type) {
        return new InputField(name, description, type, false);
    }

    public static InputField optional(String name, String description, Class<?> type) {
        return new InputField(name, description, type, true);
    }
}
