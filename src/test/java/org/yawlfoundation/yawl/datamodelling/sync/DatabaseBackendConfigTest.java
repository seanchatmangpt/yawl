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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseBackendConfig.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Builder pattern construction for DuckDB and PostgreSQL</li>
 *   <li>Sync strategy configuration (FULL, INCREMENTAL, DELETE_SAFE)</li>
 *   <li>Checkpoint and git hooks configuration</li>
 *   <li>Batch size and timeout validation</li>
 *   <li>Metadata management</li>
 *   <li>Equality and immutability</li>
 * </ul>
 *
 * @author YAWL Foundation (Teammate 4 - Engineer)
 * @version 6.0.0
 */
@DisplayName("DatabaseBackendConfig Tests")
class DatabaseBackendConfigTest {

    // ── DuckDB Configuration Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Builder creates DuckDB in-memory config")
    void testBuilder_DuckDBInMemory() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertEquals("duckdb", config.getBackendType());
        assertEquals(":memory:", config.getConnectionString());
        assertNull(config.getHost());
        assertNull(config.getPort());
        assertEquals("INCREMENTAL", config.getSyncStrategy());
    }

    @Test
    @DisplayName("Builder creates DuckDB file-based config")
    void testBuilder_DuckDBFile() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString("/var/lib/yawl/data.duckdb")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.FULL)
            .build();

        assertEquals("duckdb", config.getBackendType());
        assertEquals("/var/lib/yawl/data.duckdb", config.getConnectionString());
        assertEquals("FULL", config.getSyncStrategy());
    }

    // ── PostgreSQL Configuration Tests ─────────────────────────────────────

    @Test
    @DisplayName("Builder creates PostgreSQL config with TCP connection")
    void testBuilder_PostgreSQLTCP() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("postgres")
            .host("localhost")
            .port(5432)
            .database("workflow_db")
            .username("app_user")
            .password("secure_pass")
            .build();

        assertEquals("postgres", config.getBackendType());
        assertEquals("localhost", config.getHost());
        assertEquals(5432, config.getPort());
        assertEquals("workflow_db", config.getDatabase());
        assertEquals("app_user", config.getUsername());
        assertEquals("secure_pass", config.getPassword());
    }

    @Test
    @DisplayName("Builder creates PostgreSQL config with Unix socket")
    void testBuilder_PostgreSQLUnixSocket() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("postgres")
            .host("/var/run/postgresql")
            .database("mydb")
            .username("postgres")
            .build();

        assertEquals("postgres", config.getBackendType());
        assertEquals("/var/run/postgresql", config.getHost());
        assertEquals("mydb", config.getDatabase());
        assertNull(config.getPort(), "Port not needed for Unix socket");
    }

    // ── Sync Strategy Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Builder sets FULL sync strategy")
    void testBuilder_SyncStrategyFull() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.FULL)
            .build();

        assertEquals("FULL", config.getSyncStrategy());
        assertEquals(DatabaseBackendConfig.SyncStrategy.FULL, config.getSyncStrategyEnum());
    }

    @Test
    @DisplayName("Builder sets INCREMENTAL sync strategy (default)")
    void testBuilder_SyncStrategyIncremental() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.INCREMENTAL)
            .build();

        assertEquals("INCREMENTAL", config.getSyncStrategy());
        assertEquals(DatabaseBackendConfig.SyncStrategy.INCREMENTAL, config.getSyncStrategyEnum());
    }

    @Test
    @DisplayName("Builder sets DELETE_SAFE sync strategy")
    void testBuilder_SyncStrategyDeleteSafe() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.DELETE_SAFE)
            .build();

        assertEquals("DELETE_SAFE", config.getSyncStrategy());
        assertEquals(DatabaseBackendConfig.SyncStrategy.DELETE_SAFE, config.getSyncStrategyEnum());
    }

    @Test
    @DisplayName("Default sync strategy is INCREMENTAL")
    void testBuilder_DefaultSyncStrategy() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertEquals("INCREMENTAL", config.getSyncStrategy());
        assertEquals(DatabaseBackendConfig.SyncStrategy.INCREMENTAL, config.getSyncStrategyEnum());
    }

    // ── Checkpoint Configuration Tests ─────────────────────────────────────

    @Test
    @DisplayName("Builder sets checkpoint path")
    void testBuilder_CheckpointPath() {
        String checkpointPath = "/var/lib/yawl/sync-checkpoint.json";

        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .checkpointPath(checkpointPath)
            .build();

        assertEquals(checkpointPath, config.getCheckpointPath());
    }

    @Test
    @DisplayName("Checkpoint path is optional")
    void testBuilder_CheckpointPathOptional() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertNull(config.getCheckpointPath(), "Checkpoint path should be optional");
    }

    // ── Git Hooks Configuration Tests ──────────────────────────────────────

    @Test
    @DisplayName("Builder enables git hooks")
    void testBuilder_GitHooksEnabled() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .enableGitHooks(true)
            .build();

        assertTrue(config.isEnabledGitHooks(), "Git hooks should be enabled");
    }

    @Test
    @DisplayName("Git hooks are disabled by default")
    void testBuilder_GitHooksDisabledByDefault() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertFalse(config.isEnabledGitHooks(), "Git hooks should be disabled by default");
    }

    // ── Batch Size Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("Builder sets custom batch size")
    void testBuilder_CustomBatchSize() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .batchSize(5000)
            .build();

        assertEquals(5000, config.getBatchSize());
    }

    @Test
    @DisplayName("Default batch size is 1000")
    void testBuilder_DefaultBatchSize() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertEquals(1000, config.getBatchSize());
    }

    @Test
    @DisplayName("Builder rejects batch size <= 0")
    void testBuilder_InvalidBatchSize() {
        assertThrows(IllegalArgumentException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType("duckdb")
                .connectionString(":memory:")
                .batchSize(0)
                .build());

        assertThrows(IllegalArgumentException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType("duckdb")
                .connectionString(":memory:")
                .batchSize(-1)
                .build());
    }

    // ── Timeout Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Builder sets custom timeout")
    void testBuilder_CustomTimeout() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .timeoutSeconds(600)
            .build();

        assertEquals(600, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Default timeout is 300 seconds")
    void testBuilder_DefaultTimeout() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertEquals(300, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Builder rejects timeout <= 0")
    void testBuilder_InvalidTimeout() {
        assertThrows(IllegalArgumentException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType("duckdb")
                .connectionString(":memory:")
                .timeoutSeconds(0)
                .build());
    }

    // ── Metadata Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Builder adds single metadata entry")
    void testBuilder_SingleMetadata() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .metadata("team", "data-engineering")
            .build();

        assertEquals("data-engineering", config.getMetadata("team"));
    }

    @Test
    @DisplayName("Builder adds multiple metadata entries")
    void testBuilder_MultipleMetadata() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .metadata("team", "data-engineering")
            .metadata("sla", "no-delete-without-review")
            .metadata("compliance", "gdpr")
            .build();

        assertEquals("data-engineering", config.getMetadata("team"));
        assertEquals("no-delete-without-review", config.getMetadata("sla"));
        assertEquals("gdpr", config.getMetadata("compliance"));
    }

    @Test
    @DisplayName("Metadata retrieval returns immutable map")
    void testBuilder_MetadataImmutable() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .metadata("key", "value")
            .build();

        Map<String, String> metadata = config.getMetadata();
        assertThrows(UnsupportedOperationException.class,
            () -> metadata.put("new_key", "new_value"),
            "Metadata map should be immutable");
    }

    @Test
    @DisplayName("Builder rejects null metadata key")
    void testBuilder_NullMetadataKey() {
        assertThrows(NullPointerException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType("duckdb")
                .connectionString(":memory:")
                .metadata(null, "value")
                .build());
    }

    @Test
    @DisplayName("Builder rejects null metadata value")
    void testBuilder_NullMetadataValue() {
        assertThrows(NullPointerException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType("duckdb")
                .connectionString(":memory:")
                .metadata("key", null)
                .build());
    }

    // ── Builder Validation Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Builder rejects null backend type")
    void testBuilder_NullBackendType() {
        assertThrows(IllegalArgumentException.class,
            () -> DatabaseBackendConfig.builder()
                .backendType(null)
                .build());
    }

    @Test
    @DisplayName("Builder requires backend type")
    void testBuilder_MissingBackendType() {
        assertThrows(IllegalArgumentException.class,
            () -> DatabaseBackendConfig.builder()
                .connectionString(":memory:")
                .build());
    }

    @Test
    @DisplayName("Builder accepts optional connection string")
    void testBuilder_OptionalConnectionString() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("postgres")
            .host("localhost")
            .port(5432)
            .build();

        assertNull(config.getConnectionString(), "Connection string should be optional");
        assertNotNull(config.getHost());
    }

    // ── Equality Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Config equals same config")
    void testEquality_Same() {
        DatabaseBackendConfig config1 = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .batchSize(1000)
            .build();

        DatabaseBackendConfig config2 = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .batchSize(1000)
            .build();

        assertEquals(config1, config2, "Configs with same values should be equal");
        assertEquals(config1.hashCode(), config2.hashCode(), "Equal configs should have same hash");
    }

    @Test
    @DisplayName("Config not equals different config")
    void testEquality_Different() {
        DatabaseBackendConfig config1 = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        DatabaseBackendConfig config2 = DatabaseBackendConfig.builder()
            .backendType("postgres")
            .host("localhost")
            .port(5432)
            .build();

        assertNotEquals(config1, config2, "Configs with different values should not be equal");
    }

    @Test
    @DisplayName("Config not equals null")
    void testEquality_Null() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        assertNotEquals(config, null, "Config should not equal null");
        assertNotEquals(config, "string", "Config should not equal different type");
    }

    // ── toString Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes backend type")
    void testToString_IncludesBackendType() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .build();

        String str = config.toString();
        assertTrue(str.contains("duckdb"), "toString should include backend type");
    }

    @Test
    @DisplayName("toString includes sync strategy")
    void testToString_IncludesSyncStrategy() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("duckdb")
            .connectionString(":memory:")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.DELETE_SAFE)
            .build();

        String str = config.toString();
        assertTrue(str.contains("DELETE_SAFE"), "toString should include sync strategy");
    }

    // ── Fluent Builder Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Builder is fluent and chainable")
    void testBuilder_Fluent() {
        DatabaseBackendConfig config = DatabaseBackendConfig.builder()
            .backendType("postgres")
            .host("db.example.com")
            .port(5432)
            .database("prod_db")
            .username("prod_user")
            .password("prod_pass")
            .syncStrategy(DatabaseBackendConfig.SyncStrategy.DELETE_SAFE)
            .batchSize(2000)
            .timeoutSeconds(600)
            .checkpointPath("/opt/checkpoints/sync.json")
            .enableGitHooks(true)
            .metadata("environment", "production")
            .metadata("team", "data-ops")
            .build();

        assertEquals("postgres", config.getBackendType());
        assertEquals("db.example.com", config.getHost());
        assertEquals(5432, config.getPort());
        assertEquals("prod_db", config.getDatabase());
        assertEquals("prod_user", config.getUsername());
        assertEquals("prod_pass", config.getPassword());
        assertEquals("DELETE_SAFE", config.getSyncStrategy());
        assertEquals(2000, config.getBatchSize());
        assertEquals(600, config.getTimeoutSeconds());
        assertEquals("/opt/checkpoints/sync.json", config.getCheckpointPath());
        assertTrue(config.isEnabledGitHooks());
        assertEquals("production", config.getMetadata("environment"));
        assertEquals("data-ops", config.getMetadata("team"));
    }
}
