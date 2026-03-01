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

package org.yawlfoundation.yawl.schema;

/**
 * Classifies a schema contract violation.
 *
 * @since 6.0.0
 */
public enum ViolationType {

    /**
     * A field required by the ODCS contract is absent from the task's actual data.
     * This is the most common violation: a task predecessor failed to produce an expected field.
     */
    MISSING_FIELD,

    /**
     * The task produced a field not declared in the ODCS contract.
     * Only reported when the contract enforces strict mode; lenient mode (default) ignores extras.
     */
    UNEXPECTED_FIELD
}
