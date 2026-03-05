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
 * Contract for all credential retrieval within YAWL.
 *
 * <p>All credential access - passwords, API keys, service secrets - must go through
 * an implementation of this interface. No hardcoded credentials, environment-variable
 * fallbacks to known defaults, or plaintext config files are permitted in production.
 *
 * <p>The sole currently-supported production implementation is a vault-backed provider
 * configured via the deployment runbook (see {@code SECURITY.md}). Until that
 * integration is wired, every method on this interface throws
 * {@link UnsupportedOperationException} to fail fast rather than silently using a
 * default.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   CredentialManager cm = CredentialManagerFactory.getInstance();
 *   String adminPassword = cm.getCredential(CredentialKey.YAWL_ADMIN_PASSWORD);
 * }</pre>
 *
 * <h3>Vault Integration</h3>
 * <p>See {@code SECURITY.md}, section "Required vault integration steps".
 *
 * <h3>Credential Rotation</h3>
 * <p>See {@code SECURITY.md}, section "Credential rotation procedures".
 *
 * @see CredentialKey
 * @see CredentialManagerFactory
 * @since YAWL 5.3
 */
public interface CredentialManager {

    /**
     * Retrieves a credential by key.
     *
     * <p>Implementations must never return {@code null} or an empty string. If the
     * credential is unavailable (vault unreachable, key not found, etc.) they must
     * throw a descriptive runtime exception so that the failure is detected at startup,
     * not silently swallowed.
     *
     * @param key identifies the credential to retrieve; must not be {@code null}
     * @return the credential value; never {@code null} or empty
     * @throws IllegalArgumentException if {@code key} is {@code null}
     * @throws CredentialUnavailableException if the credential cannot be retrieved from
     *         the backing store (vault unreachable, secret not found, permission denied)
     */
    String getCredential(CredentialKey key);

    /**
     * Rotates a credential to a new value in the backing store.
     *
     * <p>After rotation, subsequent calls to {@link #getCredential(CredentialKey)} with
     * the same key must return the new value. The previous value is retained in the vault
     * audit log for the rotation window defined in {@code SECURITY.md}.
     *
     * @param key the credential to rotate; must not be {@code null}
     * @param newValue the new credential value; must not be {@code null} or empty
     * @throws IllegalArgumentException if either argument is {@code null} or {@code newValue}
     *         is empty
     * @throws CredentialUnavailableException if the rotation cannot be persisted to the
     *         backing store
     */
    void rotateCredential(CredentialKey key, String newValue);

}
