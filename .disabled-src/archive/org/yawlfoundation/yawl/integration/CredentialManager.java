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

package org.yawlfoundation.yawl.integration;

/**
 * Centralized credential manager for YAWL integration components.
 *
 * <p>All credentials are loaded exclusively from environment variables.
 * No default credential values are provided. If a required environment variable
 * is missing or blank, an {@link IllegalStateException} is thrown immediately
 * (fail-fast pattern).</p>
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code YAWL_PASSWORD} - YAWL engine admin password</li>
 *   <li>{@code DB_PASSWORD} - database connection password</li>
 * </ul>
 * </p>
 *
 * <p>Optional environment variables (return empty string if absent):
 * <ul>
 *   <li>{@code YAWL_USERNAME} - YAWL admin username (default: admin)</li>
 *   <li>{@code YAWL_ENGINE_URL} - YAWL engine URL (default: http://localhost:8080/yawl)</li>
 *   <li>{@code DB_HOST} - database host (default: localhost)</li>
 *   <li>{@code DB_PORT} - database port (default: 5432)</li>
 *   <li>{@code DB_NAME} - database name (default: yawl)</li>
 *   <li>{@code DB_USER} - database user (default: yawl)</li>
 *   <li>{@code YAWL_JDBC_URL} - full JDBC URL override</li>
 *   <li>{@code YAWL_JDBC_USER} - JDBC username override</li>
 *   <li>{@code YAWL_JDBC_PASSWORD} - JDBC password (required if using JDBC gateway)</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class CredentialManager {

    /** Name of the environment variable holding the YAWL admin password. */
    public static final String ENV_YAWL_PASSWORD = "YAWL_PASSWORD";

    /** Name of the environment variable holding the YAWL admin username. */
    public static final String ENV_YAWL_USERNAME = "YAWL_USERNAME";

    /** Name of the environment variable holding the YAWL engine URL. */
    public static final String ENV_YAWL_ENGINE_URL = "YAWL_ENGINE_URL";

    /** Name of the environment variable holding the database password. */
    public static final String ENV_DB_PASSWORD = "DB_PASSWORD";

    /** Name of the environment variable holding the database user. */
    public static final String ENV_DB_USER = "DB_USER";

    /** Name of the environment variable holding the database host. */
    public static final String ENV_DB_HOST = "DB_HOST";

    /** Name of the environment variable holding the database port. */
    public static final String ENV_DB_PORT = "DB_PORT";

    /** Name of the environment variable holding the database name. */
    public static final String ENV_DB_NAME = "DB_NAME";

    /** Name of the environment variable holding the JDBC URL. */
    public static final String ENV_YAWL_JDBC_URL = "YAWL_JDBC_URL";

    /** Name of the environment variable holding the JDBC user. */
    public static final String ENV_YAWL_JDBC_USER = "YAWL_JDBC_USER";

    /** Name of the environment variable holding the JDBC password. */
    public static final String ENV_YAWL_JDBC_PASSWORD = "YAWL_JDBC_PASSWORD";

    private CredentialManager() {
        throw new UnsupportedOperationException("CredentialManager is a utility class");
    }

    /**
     * Returns the YAWL admin password from the {@code YAWL_PASSWORD} environment variable.
     *
     * @return the YAWL password; never null or blank
     * @throws IllegalStateException if {@code YAWL_PASSWORD} is not set or is blank
     */
    public static String getYawlPassword() {
        return requireEnv(ENV_YAWL_PASSWORD);
    }

    /**
     * Returns the YAWL admin username from the {@code YAWL_USERNAME} environment variable,
     * defaulting to {@code "admin"} if not set.
     *
     * @return the YAWL username; never null
     */
    public static String getYawlUsername() {
        return getEnvOrDefault(ENV_YAWL_USERNAME, "admin");
    }

    /**
     * Returns the YAWL engine URL from the {@code YAWL_ENGINE_URL} environment variable,
     * defaulting to {@code "http://localhost:8080/yawl"} if not set.
     *
     * @return the YAWL engine URL; never null
     */
    public static String getYawlEngineUrl() {
        return getEnvOrDefault(ENV_YAWL_ENGINE_URL, "http://localhost:8080/yawl");
    }

    /**
     * Returns the database password from the {@code DB_PASSWORD} environment variable.
     *
     * @return the database password; never null or blank
     * @throws IllegalStateException if {@code DB_PASSWORD} is not set or is blank
     */
    public static String getDatabasePassword() {
        return requireEnv(ENV_DB_PASSWORD);
    }

    /**
     * Returns the database user from the {@code DB_USER} environment variable,
     * defaulting to {@code "yawl"} if not set.
     *
     * @return the database user; never null
     */
    public static String getDatabaseUser() {
        return getEnvOrDefault(ENV_DB_USER, "yawl");
    }

    /**
     * Returns the database host from the {@code DB_HOST} environment variable,
     * defaulting to {@code "localhost"} if not set.
     *
     * @return the database host; never null
     */
    public static String getDatabaseHost() {
        return getEnvOrDefault(ENV_DB_HOST, "localhost");
    }

    /**
     * Returns the database port from the {@code DB_PORT} environment variable,
     * defaulting to {@code "5432"} if not set.
     *
     * @return the database port string; never null
     */
    public static String getDatabasePort() {
        return getEnvOrDefault(ENV_DB_PORT, "5432");
    }

    /**
     * Returns the database name from the {@code DB_NAME} environment variable,
     * defaulting to {@code "yawl"} if not set.
     *
     * @return the database name; never null
     */
    public static String getDatabaseName() {
        return getEnvOrDefault(ENV_DB_NAME, "yawl");
    }

    /**
     * Returns the full JDBC URL, either from {@code YAWL_JDBC_URL} directly or
     * constructed from {@code DB_HOST}, {@code DB_PORT}, and {@code DB_NAME}.
     *
     * @return a JDBC URL; never null
     */
    public static String getJdbcUrl() {
        String explicit = System.getenv(ENV_YAWL_JDBC_URL);
        if (explicit != null && !explicit.trim().isEmpty()) {
            return explicit.trim();
        }
        return "jdbc:postgresql://" + getDatabaseHost() + ":" + getDatabasePort()
                + "/" + getDatabaseName();
    }

    /**
     * Returns the JDBC user from {@code YAWL_JDBC_USER}, falling back to {@code DB_USER},
     * then to {@code "yawl"}.
     *
     * @return the JDBC user; never null
     */
    public static String getJdbcUser() {
        String user = System.getenv(ENV_YAWL_JDBC_USER);
        if (user != null && !user.trim().isEmpty()) {
            return user.trim();
        }
        return getDatabaseUser();
    }

    /**
     * Returns the JDBC password from the {@code YAWL_JDBC_PASSWORD} environment variable.
     * Falls back to {@code DB_PASSWORD} if {@code YAWL_JDBC_PASSWORD} is not set.
     *
     * @return the JDBC password; never null or blank
     * @throws IllegalStateException if neither {@code YAWL_JDBC_PASSWORD} nor
     *         {@code DB_PASSWORD} is set or is blank
     */
    public static String getJdbcPassword() {
        String pass = System.getenv(ENV_YAWL_JDBC_PASSWORD);
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        return requireEnv(ENV_DB_PASSWORD);
    }

    /**
     * Reads a required environment variable. Throws immediately if it is absent or blank.
     *
     * @param name the environment variable name
     * @return the non-blank value of the environment variable
     * @throws IllegalStateException if the variable is absent or blank
     */
    public static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                "Required environment variable '" + name + "' is not set or is blank. "
                + "See .env.example for configuration instructions.");
        }
        return value.trim();
    }

    /**
     * Reads an optional environment variable, returning a default if absent or blank.
     *
     * @param name         the environment variable name
     * @param defaultValue the value to return if the variable is absent or blank
     * @return the environment variable value, or {@code defaultValue}
     */
    public static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}
