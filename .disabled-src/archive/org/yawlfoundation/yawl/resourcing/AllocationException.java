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

package org.yawlfoundation.yawl.resourcing;

/**
 * Checked exception thrown when a {@link ResourceAllocator} cannot allocate
 * a participant to a work item.
 *
 * <p>Callers must handle this exception and decide whether to escalate,
 * queue the work item, or fall back to human task allocation. Silent
 * suppression is a Q-invariant violation.
 *
 * @since YAWL 6.0
 */
public class AllocationException extends Exception {

    /**
     * Constructs an {@code AllocationException} with the given message.
     *
     * @param message a non-null description of the allocation failure
     */
    public AllocationException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code AllocationException} with the given message and cause.
     *
     * @param message a non-null description of the allocation failure
     * @param cause   the underlying cause; may be null
     */
    public AllocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
