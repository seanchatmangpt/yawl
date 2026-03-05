/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations who
 * are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security.pki;

/**
 * Exception thrown during PKI (Public Key Infrastructure) and certificate management operations.
 */
public class PkiException extends Exception {
    /**
     * Creates a PkiException with a message.
     *
     * @param message The error message
     */
    public PkiException(String message) {
        super(message);
    }

    /**
     * Creates a PkiException with a message and cause.
     *
     * @param message The error message
     * @param cause   The underlying exception
     */
    public PkiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a PkiException with a cause.
     *
     * @param cause The underlying exception
     */
    public PkiException(Throwable cause) {
        super(cause);
    }
}
