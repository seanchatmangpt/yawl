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

package org.yawlfoundation.yawl.security;

/**
 * Thrown when a {@link CredentialManager} implementation cannot retrieve or rotate
 * a credential from its backing store.
 *
 * <p>This is an unchecked exception so that callers are not forced into try-catch
 * boilerplate, but it must not be silently swallowed. Any catch block that receives
 * this exception must either rethrow it or propagate it via another mechanism that
 * prevents the operation from continuing with a missing credential.
 *
 * @since YAWL 5.3
 */
public class CredentialUnavailableException extends RuntimeException {

    /**
     * Constructs with a descriptive message identifying the credential key and cause.
     *
     * @param key   the credential that could not be retrieved
     * @param cause the underlying failure; must not be {@code null}
     */
    public CredentialUnavailableException(CredentialKey key, Throwable cause) {
        super("Credential unavailable: " + key.name() +
              " - vault integration is required. See SECURITY.md.", cause);
    }

    /**
     * Constructs with a descriptive message when no underlying exception is available.
     *
     * @param key     the credential that could not be retrieved
     * @param details additional context about why the credential is unavailable
     */
    public CredentialUnavailableException(CredentialKey key, String details) {
        super("Credential unavailable: " + key.name() + " - " + details +
              " See SECURITY.md for vault integration steps.");
    }

}
