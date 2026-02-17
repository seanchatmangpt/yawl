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
 * Factory that provides the singleton {@link CredentialManager} instance.
 *
 * <p>The production implementation must be registered via
 * {@link #setInstance(CredentialManager)} during application startup, before any
 * component calls {@link #getInstance()}. Typically this is done in the servlet
 * context initializer or Spring application context configuration.
 *
 * <p>If no implementation is registered, {@link #getInstance()} throws
 * {@link UnsupportedOperationException} to fail fast rather than returning a
 * default that could silently bypass credential enforcement.
 *
 * <h3>Integration Steps</h3>
 * <p>See {@code SECURITY.md}, section "Required vault integration steps".
 *
 * <h3>Example Registration (servlet init)</h3>
 * <pre>{@code
 *   VaultCredentialManager vault = new VaultCredentialManager(vaultConfig);
 *   CredentialManagerFactory.setInstance(vault);
 * }</pre>
 *
 * @since YAWL 5.3
 */
public final class CredentialManagerFactory {

    private static volatile CredentialManager instance;

    private CredentialManagerFactory() { }

    /**
     * Returns the registered {@link CredentialManager} instance.
     *
     * @return the registered instance; never {@code null}
     * @throws UnsupportedOperationException if no instance has been registered via
     *         {@link #setInstance(CredentialManager)} - the vault integration described
     *         in {@code SECURITY.md} must be configured before accessing credentials
     */
    public static CredentialManager getInstance() {
        CredentialManager current = instance;
        if (current == null) {
            throw new UnsupportedOperationException(
                "No CredentialManager has been registered. " +
                "Configure the vault integration and call CredentialManagerFactory.setInstance() " +
                "during application startup. See SECURITY.md for required vault integration steps.");
        }
        return current;
    }

    /**
     * Registers the {@link CredentialManager} implementation to use.
     *
     * <p>This method is intended to be called exactly once during application startup.
     * Calling it a second time replaces the existing instance; this is permitted only
     * during testing via a dedicated test harness.
     *
     * @param credentialManager the implementation to register; must not be {@code null}
     * @throws IllegalArgumentException if {@code credentialManager} is {@code null}
     */
    public static void setInstance(CredentialManager credentialManager) {
        if (credentialManager == null) {
            throw new IllegalArgumentException(
                "CredentialManager instance must not be null.");
        }
        instance = credentialManager;
    }

}
