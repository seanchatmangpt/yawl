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

package org.yawlfoundation.yawl.datamodelling.sync;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for database backend connections and sync strategies.
 *
 * <p>Encapsulates connection details, sync behavior, checkpointing, and
 * git hooks integration for bidirectional data model synchronization.</p>
 *
 * <h2>Backend Types</h2>
 * <ul>
 *   <li><strong>duckdb</strong>: In-memory or file-based (no server needed)</li>
 *   <li><strong>postgres</strong>: PostgreSQL server (TCP or Unix socket)</li>
 * </ul>
 *
 * <h2>Sync Strategies</h2>
 * <ul>
 *   <li><strong>FULL</strong>: Replace all data (truncate + insert)</li>
 *   <li><strong>INCREMENTAL</strong>: Insert new, update existing, skip deleted</li>
 *   <li><strong>DELETE_SAFE</strong>: Incremental + require explicit delete approval</li>
 * </ul>
 *
 * <h2>Builder Usage</h2>
 * <pre>{@code
 * DatabaseBackendConfig config = DatabaseBackendConfig.builder()
 *     .backendType("postgres")
 *     .host("localhost")
 *     .port(5432)
 *     .database("mydb")
 *     .username("user")
 *     .password("pass")
 *     .syncStrategy(SyncStrategy.INCREMENTAL)
 *     .checkpointPath("/var/lib/yawl/sync-checkpoint.json")
 *     .enableGitHooks(true)
 *     .metadata("team", "data-engineering")
 *     .metadata("sla", "no-delete-without-review")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation (Teammate 4 - Engineer)
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DatabaseBackendConfig {

    /**
     * Synchronization strategy enumeration.
     */
    public enum SyncStrategy {
        /** Replace all data (truncate + insert). Risk: data loss on stale checkpoints. */
        FULL,
        /** Insert new, update existing, skip deleted. Safe default. */
        INCREMENTAL,
        /** Incremental + require explicit approval for deletes. Safest. */
        DELETE_SAFE
    }

    /**
     * Supported backend database types.
     */
    public enum BackendType {
        /** In-memory or file-based (no server). */
        DUCKDB,
        /** PostgreSQL server (TCP or Unix socket). */
        POSTGRES
    }

    @JsonProperty("backend_type")
    private String backendType;

    @JsonProperty("connection_string")
    @Nullable
    private String connectionString;

    @JsonProperty("host")
    @Nullable
    private String host;

    @JsonProperty("port")
    @Nullable
    private Integer port;

    @JsonProperty("database")
    @Nullable
    private String database;

    @JsonProperty("username")
    @Nullable
    private String username;

    @JsonProperty("password")
    @Nullable
    private String password;

    @JsonProperty("sync_strategy")
    private String syncStrategy = "INCREMENTAL";

    @JsonProperty("checkpoint_path")
    @Nullable
    private String checkpointPath;

    @JsonProperty("enable_git_hooks")
    private boolean enableGitHooks = false;

    @JsonProperty("batch_size")
    private int batchSize = 1000;

    @JsonProperty("timeout_seconds")
    private int timeoutSeconds = 300;

    @JsonProperty("metadata")
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Constructs an empty config (for JSON deserialization).
     */
    public DatabaseBackendConfig() {}

    // ── Getters ───────────────────────────────────────────────────────────

    /**
     * Returns the backend type (duckdb or postgres).
     *
     * @return the backend type; never null
     */
    public String getBackendType() {
        return backendType;
    }

    /**
     * Returns the connection string (e.g., ":memory:" for DuckDB or JDBC URL).
     *
     * @return connection string or null if host/port/database used instead
     */
    public @Nullable String getConnectionString() {
        return connectionString;
    }

    /**
     * Returns the hostname for server-based backends.
     *
     * @return hostname or null
     */
    public @Nullable String getHost() {
        return host;
    }

    /**
     * Returns the port number for server-based backends.
     *
     * @return port or null
     */
    public @Nullable Integer getPort() {
        return port;
    }

    /**
     * Returns the database name.
     *
     * @return database name or null
     */
    public @Nullable String getDatabase() {
        return database;
    }

    /**
     * Returns the username for authentication.
     *
     * @return username or null
     */
    public @Nullable String getUsername() {
        return username;
    }

    /**
     * Returns the password for authentication.
     *
     * @return password or null
     */
    public @Nullable String getPassword() {
        return password;
    }

    /**
     * Returns the sync strategy.
     *
     * @return sync strategy as string; never null
     */
    public String getSyncStrategy() {
        return syncStrategy;
    }

    /**
     * Returns the sync strategy as an enum.
     *
     * @return sync strategy enum value
     */
    public SyncStrategy getSyncStrategyEnum() {
        return SyncStrategy.valueOf(syncStrategy);
    }

    /**
     * Returns the checkpoint file path for resuming interrupted syncs.
     *
     * @return checkpoint path or null
     */
    public @Nullable String getCheckpointPath() {
        return checkpointPath;
    }

    /**
     * Returns whether git hooks are enabled for sync validation.
     *
     * @return true if git hooks enabled
     */
    public boolean isEnabledGitHooks() {
        return enableGitHooks;
    }

    /**
     * Returns the batch size for sync operations (default 1000).
     *
     * @return batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Returns the operation timeout in seconds (default 300).
     *
     * @return timeout in seconds
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Returns a metadata value by key.
     *
     * @param key the metadata key; must not be null
     * @return the metadata value or null if not found
     */
    public @Nullable String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Returns all metadata as an immutable map.
     *
     * @return metadata map; never null
     */
    public Map<String, String> getMetadata() {
        return Map.copyOf(metadata);
    }

    // ── Builder ────────────────────────────────────────────────────────────

    /**
     * Creates a new builder for fluent construction.
     *
     * @return a new builder; never null
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for DatabaseBackendConfig.
     */
    public static final class Builder {
        private String backendType;
        private @Nullable String connectionString;
        private @Nullable String host;
        private @Nullable Integer port;
        private @Nullable String database;
        private @Nullable String username;
        private @Nullable String password;
        private String syncStrategy = "INCREMENTAL";
        private @Nullable String checkpointPath;
        private boolean enableGitHooks = false;
        private int batchSize = 1000;
        private int timeoutSeconds = 300;
        private final Map<String, String> metadata = new HashMap<>();

        private Builder() {}

        /**
         * Sets the backend type (duckdb or postgres).
         *
         * @param backendType the backend type; must not be null
         * @return this builder
         */
        public Builder backendType(String backendType) {
            this.backendType = Objects.requireNonNull(backendType, "backendType must not be null");
            return this;
        }

        /**
         * Sets the connection string.
         *
         * @param connectionString the connection string; may be null
         * @return this builder
         */
        public Builder connectionString(@Nullable String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /**
         * Sets the hostname.
         *
         * @param host the hostname; may be null
         * @return this builder
         */
        public Builder host(@Nullable String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the port number.
         *
         * @param port the port number; may be null
         * @return this builder
         */
        public Builder port(@Nullable Integer port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the database name.
         *
         * @param database the database name; may be null
         * @return this builder
         */
        public Builder database(@Nullable String database) {
            this.database = database;
            return this;
        }

        /**
         * Sets the username.
         *
         * @param username the username; may be null
         * @return this builder
         */
        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password.
         *
         * @param password the password; may be null
         * @return this builder
         */
        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the sync strategy.
         *
         * @param strategy the sync strategy; may be null (defaults to INCREMENTAL)
         * @return this builder
         */
        public Builder syncStrategy(@Nullable SyncStrategy strategy) {
            if (strategy != null) {
                this.syncStrategy = strategy.name();
            }
            return this;
        }

        /**
         * Sets the checkpoint file path.
         *
         * @param checkpointPath the checkpoint path; may be null
         * @return this builder
         */
        public Builder checkpointPath(@Nullable String checkpointPath) {
            this.checkpointPath = checkpointPath;
            return this;
        }

        /**
         * Sets whether git hooks are enabled.
         *
         * @param enableGitHooks true to enable git hooks
         * @return this builder
         */
        public Builder enableGitHooks(boolean enableGitHooks) {
            this.enableGitHooks = enableGitHooks;
            return this;
        }

        /**
         * Sets the batch size for sync operations.
         *
         * @param batchSize the batch size (must be > 0)
         * @return this builder
         */
        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be > 0, got: " + batchSize);
            }
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the operation timeout in seconds.
         *
         * @param timeoutSeconds the timeout in seconds (must be > 0)
         * @return this builder
         */
        public Builder timeoutSeconds(int timeoutSeconds) {
            if (timeoutSeconds <= 0) {
                throw new IllegalArgumentException("timeoutSeconds must be > 0, got: " + timeoutSeconds);
            }
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * Adds a metadata key-value pair.
         *
         * @param key the key; must not be null
         * @param value the value; must not be null
         * @return this builder
         */
        public Builder metadata(String key, String value) {
            this.metadata.put(
                Objects.requireNonNull(key, "metadata key must not be null"),
                Objects.requireNonNull(value, "metadata value must not be null")
            );
            return this;
        }

        /**
         * Builds the config.
         *
         * @return a new config; never null
         * @throws IllegalArgumentException if backendType is missing
         */
        public DatabaseBackendConfig build() {
            if (backendType == null) {
                throw new IllegalArgumentException("backendType is required");
            }

            DatabaseBackendConfig config = new DatabaseBackendConfig();
            config.backendType = this.backendType;
            config.connectionString = this.connectionString;
            config.host = this.host;
            config.port = this.port;
            config.database = this.database;
            config.username = this.username;
            config.password = this.password;
            config.syncStrategy = this.syncStrategy;
            config.checkpointPath = this.checkpointPath;
            config.enableGitHooks = this.enableGitHooks;
            config.batchSize = this.batchSize;
            config.timeoutSeconds = this.timeoutSeconds;
            config.metadata = new HashMap<>(this.metadata);
            return config;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseBackendConfig that = (DatabaseBackendConfig) o;
        return enableGitHooks == that.enableGitHooks &&
                batchSize == that.batchSize &&
                timeoutSeconds == that.timeoutSeconds &&
                Objects.equals(backendType, that.backendType) &&
                Objects.equals(connectionString, that.connectionString) &&
                Objects.equals(host, that.host) &&
                Objects.equals(port, that.port) &&
                Objects.equals(database, that.database) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(syncStrategy, that.syncStrategy) &&
                Objects.equals(checkpointPath, that.checkpointPath) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(backendType, connectionString, host, port, database,
                username, password, syncStrategy, checkpointPath, enableGitHooks,
                batchSize, timeoutSeconds, metadata);
    }

    @Override
    public String toString() {
        return "DatabaseBackendConfig{" +
                "backendType='" + backendType + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", username='" + username + '\'' +
                ", syncStrategy='" + syncStrategy + '\'' +
                ", checkpointPath='" + checkpointPath + '\'' +
                ", enableGitHooks=" + enableGitHooks +
                ", batchSize=" + batchSize +
                ", timeoutSeconds=" + timeoutSeconds +
                ", metadata=" + metadata +
                '}';
    }
}
