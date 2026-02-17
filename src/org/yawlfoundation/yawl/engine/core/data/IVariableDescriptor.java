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

package org.yawlfoundation.yawl.engine.core.data;

/**
 * Minimal interface representing the variable metadata required by the schema
 * validation logic in {@link YCoreDataValidator}.
 *
 * <p>Both {@code org.yawlfoundation.yawl.elements.data.YVariable} (stateful) and
 * {@code org.yawlfoundation.yawl.stateless.elements.data.YVariable} (stateless)
 * implement this interface, enabling the shared {@link YCoreDataValidator} to
 * validate variables from either engine tree without depending on the concrete type.
 * The interface is also extended by {@code YParameter} in both trees since
 * {@code YParameter} is a subtype of {@code YVariable}.</p>
 *
 * <p>Note: this interface intentionally omits the {@code Comparable} ordering methods
 * because {@link YCoreDataValidator} uses the raw {@code Collections.sort(varList)}
 * path which requires the elements themselves to implement {@link Comparable}.
 * Both {@code YVariable} implementations already implement {@code Comparable<YVariable>}
 * which satisfies the raw-type sort.</p>
 *
 * @since 5.2 (Phase 1 deduplication, EngineDedupPlan P1.4)
 */
public interface IVariableDescriptor {

    /**
     * Returns the name of this variable.
     * @return the variable name
     */
    String getName();

    /**
     * Returns the XSD data type name of this variable (e.g., {@code "string"}).
     * @return the data type name
     */
    String getDataTypeName();

    /**
     * Returns {@code true} if this variable is optional (i.e., may be absent
     * in the instance data without causing a validation failure).
     * @return {@code true} if optional
     */
    boolean isOptional();

}
