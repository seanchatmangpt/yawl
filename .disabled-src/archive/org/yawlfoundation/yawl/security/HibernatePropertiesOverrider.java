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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;

/**
 * Applies environment-variable overrides to Hibernate connection properties at
 * startup, removing the need for plaintext database credentials in
 * {@code hibernate.properties}.
 *
 * <p>SOC2 CRITICAL#1 remediation: all plaintext credentials in
 * {@code build/properties/hibernate.properties} and {@code src/jdbc.properties}
 * are superseded by environment variables. The static values in those files serve
 * only as documentation references; they are not loaded into production sessions
 * when the environment variables are set.
 *
 * <p>Environment variables (see {@link EnvironmentCredentialManager}):
 * <ul>
 *   <li>{@code YAWL_DB_USER}     - overrides {@code hibernate.connection.username}</li>
 *   <li>{@code YAWL_DB_PASSWORD} - overrides {@code hibernate.connection.password}</li>
 *   <li>{@code DATABASE_URL}     - overrides {@code hibernate.connection.url} (optional)</li>
 * </ul>
 *
 * <p>Usage (call this before passing properties to Hibernate's
 * {@code StandardServiceRegistryBuilder}):
 * <pre>{@code
 *   Properties props = loadPropertiesFromFile();
 *   HibernatePropertiesOverrider.apply(props);
 *   standardRegistryBuilder.applySettings(props);
 * }</pre>
 *
 * @author YAWL Foundation - SOC2 Remediation 2026-02-17
 * @since YAWL 5.3
 * @see EnvironmentCredentialManager
 */
public final class HibernatePropertiesOverrider {

    private static final Logger LOG = LogManager.getLogger(HibernatePropertiesOverrider.class);

    /** Hibernate property key for the database username. */
    public static final String PROP_USERNAME = "hibernate.connection.username";

    /** Hibernate property key for the database password. */
    public static final String PROP_PASSWORD = "hibernate.connection.password";

    /** Hibernate property key for the JDBC URL. */
    public static final String PROP_URL = "hibernate.connection.url";

    private HibernatePropertiesOverrider() { }

    /**
     * Applies environment-variable overrides to the supplied Hibernate properties.
     * Any property whose corresponding environment variable is set and non-blank will
     * have its value replaced by the environment variable value.
     *
     * <p>A log message is emitted at INFO level for each override applied (value
     * redacted for passwords). If a required variable ({@code YAWL_DB_USER},
     * {@code YAWL_DB_PASSWORD}) is absent, an {@link IllegalStateException} is thrown
     * immediately to prevent the engine from starting with default credentials.
     *
     * @param props the Hibernate properties to modify in place; must not be {@code null}
     * @throws IllegalArgumentException if {@code props} is {@code null}
     * @throws IllegalStateException if a required environment variable is absent or blank
     */
    public static void apply(Properties props) {
        if (props == null) {
            throw new IllegalArgumentException(
                "Hibernate properties must not be null.");
        }

        applyRequired(props, EnvironmentCredentialManager.ENV_YAWL_DB_USER,
                      PROP_USERNAME, false);
        applyRequired(props, EnvironmentCredentialManager.ENV_YAWL_DB_PASSWORD,
                      PROP_PASSWORD, true);
        applyOptional(props, "DATABASE_URL", PROP_URL, false);
    }

    /**
     * Validates that both required database credential variables are present without
     * modifying any properties object. Call during startup validation before Hibernate
     * is initialised.
     *
     * @throws IllegalStateException if {@code YAWL_DB_USER} or {@code YAWL_DB_PASSWORD}
     *         is absent or blank
     */
    public static void validate() {
        requireEnv(EnvironmentCredentialManager.ENV_YAWL_DB_USER);
        requireEnv(EnvironmentCredentialManager.ENV_YAWL_DB_PASSWORD);
        LOG.info("SOC2 CRITICAL#1 [PASS]: Database credential environment variables are set.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static void applyRequired(Properties props, String envVar,
                                      String hibernateProp, boolean redact) {
        String value = requireEnv(envVar);
        props.setProperty(hibernateProp, value);
        String displayValue = redact ? "***REDACTED***" : value;
        LOG.info("SOC2 CRITICAL#1: Hibernate property '{}' overridden from env var '{}' (value: {}).",
                hibernateProp, envVar, displayValue);
    }

    private static void applyOptional(Properties props, String envVar,
                                      String hibernateProp, boolean redact) {
        String value = System.getenv(envVar);
        if (value != null && !value.trim().isEmpty()) {
            props.setProperty(hibernateProp, value.trim());
            String displayValue = redact ? "***REDACTED***" : value.trim();
            LOG.info("SOC2 CRITICAL#1: Hibernate property '{}' overridden from env var '{}' (value: {}).",
                    hibernateProp, envVar, displayValue);
        }
    }

    private static String requireEnv(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                "SOC2 CRITICAL#1 [FAIL]: Required environment variable '" + envVar + "' " +
                "is not set or is blank. Database credentials must be supplied via environment " +
                "variables, not from hibernate.properties. " +
                "Set YAWL_DB_USER and YAWL_DB_PASSWORD before starting YAWL. " +
                "See SECURITY.md for Vault integration instructions.");
        }
        return value.trim();
    }
}
