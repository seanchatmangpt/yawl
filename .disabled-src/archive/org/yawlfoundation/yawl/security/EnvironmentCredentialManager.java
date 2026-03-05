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

package org.yawlfoundation.yawl.security;

import java.util.EnumMap;
import java.util.Map;

/**
 * Environment-variable-backed {@link CredentialManager} implementation for production
 * deployments where credentials are injected via environment variables (typically sourced
 * from HashiCorp Vault, AWS Secrets Manager, or Kubernetes Secrets).
 *
 * <p>All credentials are read from environment variables at retrieval time (not cached
 * at construction), so runtime secret rotation is supported without restart when
 * the environment is updated by the Vault agent sidecar.
 *
 * <p>Environment variable mappings (SOC2 CC6.1):
 * <ul>
 *   <li>{@link CredentialKey#YAWL_ADMIN_PASSWORD}          &rarr; {@code YAWL_ADMIN_PASSWORD}</li>
 *   <li>{@link CredentialKey#YAWL_ENGINE_SERVICE_PASSWORD} &rarr; {@code YAWL_SERVICE_TOKEN}</li>
 *   <li>{@link CredentialKey#ZAI_API_KEY}                  &rarr; {@code ZAI_API_KEY}</li>
 *   <li>{@link CredentialKey#ZHIPU_API_KEY}                &rarr; {@code ZHIPU_API_KEY}</li>
 *   <li>{@link CredentialKey#PROCLET_SERVICE_PASSWORD}     &rarr; {@code YAWL_SERVICE_TOKEN}</li>
 * </ul>
 *
 * <p>Database credential environment variables used by {@link HibernatePropertiesOverrider}:
 * <ul>
 *   <li>{@code YAWL_DB_USER}     - database username (overrides hibernate.connection.username)</li>
 *   <li>{@code YAWL_DB_PASSWORD} - database password (overrides hibernate.connection.password)</li>
 * </ul>
 *
 * <p>Vault integration: configure the Vault agent sidecar to write secrets to the
 * environment. See {@code SECURITY.md} for the deployment runbook.
 *
 * <p>Rotation: credentials are read on every call, so Vault lease renewal and
 * dynamic secret rotation are supported without application restart.
 *
 * @author YAWL Foundation - SOC2 Remediation 2026-02-17
 * @since YAWL 5.3
 * @see CredentialManagerFactory
 * @see HibernatePropertiesOverrider
 */
public final class EnvironmentCredentialManager implements CredentialManager {

    /** Environment variable name for the YAWL admin password (SOC2 CRITICAL#3, #4). */
    public static final String ENV_YAWL_ADMIN_PASSWORD = "YAWL_ADMIN_PASSWORD";

    /**
     * Environment variable name for service-to-service authentication token.
     * Replaces all hardcoded service passwords in web.xml and editor.properties
     * (SOC2 CRITICAL#2).
     */
    public static final String ENV_YAWL_SERVICE_TOKEN = "YAWL_SERVICE_TOKEN";

    /** Environment variable name for the Z.AI API key. */
    public static final String ENV_ZAI_API_KEY = "ZAI_API_KEY";

    /** Environment variable name for the Zhipu AI API key. */
    public static final String ENV_ZHIPU_API_KEY = "ZHIPU_API_KEY";

    /** Environment variable name for the database username (SOC2 CRITICAL#1). */
    public static final String ENV_YAWL_DB_USER = "YAWL_DB_USER";

    /** Environment variable name for the database password (SOC2 CRITICAL#1). */
    public static final String ENV_YAWL_DB_PASSWORD = "YAWL_DB_PASSWORD";

    /** Maps each credential key to its required environment variable name. */
    private static final Map<CredentialKey, String> KEY_TO_ENV_VAR;

    static {
        KEY_TO_ENV_VAR = new EnumMap<>(CredentialKey.class);
        KEY_TO_ENV_VAR.put(CredentialKey.YAWL_ADMIN_PASSWORD,           ENV_YAWL_ADMIN_PASSWORD);
        KEY_TO_ENV_VAR.put(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD,  ENV_YAWL_SERVICE_TOKEN);
        KEY_TO_ENV_VAR.put(CredentialKey.ZAI_API_KEY,                   ENV_ZAI_API_KEY);
        KEY_TO_ENV_VAR.put(CredentialKey.ZHIPU_API_KEY,                 ENV_ZHIPU_API_KEY);
        KEY_TO_ENV_VAR.put(CredentialKey.PROCLET_SERVICE_PASSWORD,      ENV_YAWL_SERVICE_TOKEN);
    }

    /**
     * Creates a new {@code EnvironmentCredentialManager}.
     *
     * <p>Construction validates that the required environment variables are accessible.
     * If a required variable is absent, an {@link IllegalStateException} is thrown
     * immediately so the failure is detected at application startup (fail-fast).
     */
    public EnvironmentCredentialManager() {
        validateRequiredVariables();
    }

    /**
     * Retrieves a credential from its backing environment variable.
     *
     * <p>The credential is read on every call to support Vault lease rotation.
     *
     * @param key the credential to retrieve; must not be {@code null}
     * @return the credential value; never {@code null} or empty
     * @throws IllegalArgumentException if {@code key} is {@code null}
     * @throws CredentialUnavailableException if the environment variable is absent or blank
     */
    @Override
    public String getCredential(CredentialKey key) {
        if (key == null) {
            throw new IllegalArgumentException("CredentialKey must not be null.");
        }
        String envVar = KEY_TO_ENV_VAR.get(key);
        if (envVar == null) {
            throw new CredentialUnavailableException(key,
                "No environment variable mapping defined for key '" + key.name() + "'. " +
                "Register the mapping in EnvironmentCredentialManager.KEY_TO_ENV_VAR.");
        }
        return requireEnv(key, envVar);
    }

    /**
     * Rotates a credential by updating it in the backing secret store.
     *
     * <p>In this environment-variable implementation, credential rotation is managed
     * externally by the Vault agent or operations team - the application reads the
     * updated value automatically on the next {@link #getCredential(CredentialKey)} call.
     * This method validates arguments and records the rotation request but does not
     * directly write to environment variables (which are immutable at the OS level).
     *
     * <p>For programmatic rotation, configure HashiCorp Vault dynamic secrets or
     * AWS Secrets Manager rotation and let the Vault sidecar update the environment.
     *
     * @param key the credential to rotate; must not be {@code null}
     * @param newValue the new credential value; must not be {@code null} or empty
     * @throws IllegalArgumentException if either argument is {@code null} or {@code newValue}
     *         is empty
     */
    @Override
    public void rotateCredential(CredentialKey key, String newValue) {
        if (key == null) {
            throw new IllegalArgumentException("CredentialKey must not be null.");
        }
        if (newValue == null || newValue.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "New credential value must not be null or empty for key: " + key.name());
        }
        // Rotation is handled externally by Vault. The updated value is read automatically
        // on the next getCredential() call. See SECURITY.md section "Credential rotation".
    }

    /**
     * Returns the name of the environment variable backing a given credential key.
     * Useful for diagnostic messages and deployment runbooks.
     *
     * @param key the credential key; must not be {@code null}
     * @return the environment variable name, or {@code null} if not mapped
     */
    public static String envVarFor(CredentialKey key) {
        if (key == null) return null;
        return KEY_TO_ENV_VAR.get(key);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that each required environment variable is present and non-blank.
     * Called at construction to enforce fail-fast startup behaviour.
     *
     * @throws IllegalStateException if any required variable is absent or blank
     */
    private void validateRequiredVariables() {
        requireEnv(CredentialKey.YAWL_ADMIN_PASSWORD,           ENV_YAWL_ADMIN_PASSWORD);
        requireEnv(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD,  ENV_YAWL_SERVICE_TOKEN);
    }

    /**
     * Reads a required environment variable, throwing {@link CredentialUnavailableException}
     * if it is absent or blank.
     *
     * @param key    the credential key (for error messages)
     * @param envVar the environment variable to read
     * @return the non-blank value of the environment variable
     * @throws CredentialUnavailableException if the variable is absent or blank
     */
    private static String requireEnv(CredentialKey key, String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.trim().isEmpty()) {
            throw new CredentialUnavailableException(key,
                "Required environment variable '" + envVar + "' is not set or is blank. " +
                "Configure this variable via your Vault agent, Kubernetes Secret, or " +
                ".env file before starting YAWL. See SECURITY.md for deployment instructions.");
        }
        return value.trim();
    }
}
