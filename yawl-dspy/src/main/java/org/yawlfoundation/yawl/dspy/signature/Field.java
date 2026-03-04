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
 * Base interface for signature fields.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed interface Field permits InputField, OutputField {

    /**
     * Field name (snake_case convention for LLM clarity).
     */
    String name();

    /**
     * Human-readable description shown to the LLM.
     */
    String description();

    /**
     * Expected Java type for marshalling.
     */
    Class<?> type();
}
