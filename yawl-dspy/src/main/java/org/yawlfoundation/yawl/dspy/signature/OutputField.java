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
 * Output field in a DSPy signature.
 *
 * @param name        field name (snake_case convention)
 * @param description description shown to the LLM
 * @param type        expected Java type
 * @param reasoning   whether this is a chain-of-thought reasoning field
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OutputField(
    String name,
    String description,
    Class<?> type,
    boolean reasoning
) implements Field {

    public OutputField {
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

    public static OutputField of(String name, String description, Class<?> type) {
        return new OutputField(name, description, type, false);
    }

    /**
     * Create an output field with explicit reasoning flag.
     */
    public static OutputField of(String name, String description, Class<?> type, boolean reasoning) {
        return new OutputField(name, description, type, reasoning);
    }

    /**
     * Create a reasoning field - the LLM will produce chain-of-thought.
     */
    public static OutputField reasoning(String name, String description) {
        return new OutputField(name, description, String.class, true);
    }
}
