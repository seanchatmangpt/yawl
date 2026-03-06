/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.exceptions;

import java.io.Serial;

/**
 * Exception thrown when authentication fails in the YAWL engine.
 *
 * <p><b>Common causes:</b>
 * <ul>
 *   <li>Invalid username or password
 *   <li>Session token expired or revoked
 *   <li>Missing required authentication credentials
 *   <li>API key invalid or revoked
 *   <li>User account disabled
 * </ul>
 *
 * <p><b>Recovery guidance:</b>
 * <ul>
 *   <li>Verify username and password are correct
 *   <li>Check that session token is still valid (tokens expire)
 *   <li>Ensure API key is configured in environment variables
 *   <li>Re-authenticate if token expired (typically every 1-24 hours)
 *   <li>Contact system administrator if account is disabled
 * </ul>
 *
 * @author Lachlan Aldred
 * @since 26/11/2004
 */
public class YAuthenticationException extends YAWLException {
    @Serial
    private static final long serialVersionUID = 2L;

    private transient String remediation;

    /**
     * Constructs a new authentication exception with no detail message.
     */
    public YAuthenticationException() {
        super();
    }

    /**
     * Constructs a new authentication exception with the specified detail message.
     *
     * @param message the detail message
     */
    public YAuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs a new authentication exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public YAuthenticationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new authentication exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new authentication exception with message, cause, and remediation steps.
     *
     * @param message        the detail message
     * @param cause          the cause of this exception
     * @param remediation    steps to resolve the authentication issue
     */
    public YAuthenticationException(String message, Throwable cause, String remediation) {
        super(message, cause);
        this.remediation = remediation;
    }

    /**
     * Returns remediation steps to resolve this authentication error.
     *
     * @return remediation steps (may be null if not provided)
     */
    public String getRemediation() {
        return remediation;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (remediation == null) {
            return base == null ? "Authentication failed. Check credentials and re-authenticate." : base;
        }
        return base + " Remediation: " + remediation;
    }
}
