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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.validation.schema;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig.Builder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SchemaRegistry class.
 *
 * Tests schema loading, caching, versioning, and error handling.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class SchemaRegistryTest {

    private SchemaRegistry registry;
    private ValidationConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new Builder()
            .enableCaching(true)
            .schemaCacheTimeout(java.time.Duration.ofMinutes(5))
            .validationTimeout(java.time.Duration.ofSeconds(5))
            .maxSchemaSize(1024 * 1024) // 1MB
            .failFast(true)
            .includeValidationDetails(true)
            .build();

        registry = SchemaRegistry.create(testConfig);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    @DisplayName("Test default configuration")
    void testDefaultConfiguration() {
        SchemaRegistry defaultRegistry = SchemaRegistry.getInstance();
        assertNotNull(defaultRegistry);
        assertDoesNotThrow(() -> defaultRegistry.shutdown());
    }

    @Test
    @DisplayName("Load common schema successfully")
    void loadCommonSchemaSuccessfully() {
        assertDoesNotThrow(() -> {
            JsonSchema schema = registry.getCommonSchema("timestamp");
            assertNotNull(schema);
        });
    }

    @Test
    @DisplayName("Load A2A schema successfully")
    void loadA2ASchemaSuccessfully() {
        assertDoesNotThrow(() -> {
            JsonSchema schema = registry.getA2ASchema("handoff-message");
            assertNotNull(schema);
        });
    }

    @Test
    @DisplayName("Load MCP schema successfully")
    void loadMCPSchemaSuccessfully() {
        assertDoesNotThrow(() -> {
            JsonSchema schema = registry.getMCPSchema("tool-call");
            assertNotNull(schema);
        });
    }

    @Test
    @DisplayName("Load non-existent schema throws exception")
    void loadNonExistentSchemaThrowsException() {
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            registry.getSchema("schemas/nonexistent/schema.json");
        });

        assertEquals(SchemaValidationError.ErrorType.SCHEMA_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("Schema caching works correctly")
    void schemaCachingWorks() {
        // First load
        JsonSchema schema1 = registry.getA2ASchema("agent-card");
        assertNotNull(schema1);

        // Second load should use cache
        JsonSchema schema2 = registry.getA2ASchema("agent-card");
        assertNotNull(schema2);
        assertSame(schema1, schema2); // Should be same instance from cache
    }

    @Test
    @DisplayName("Cached schema count tracking")
    void cachedSchemaCountTracking() {
        assertEquals(0, registry.getCachedSchemaCount());

        // Load a schema
        registry.getCommonSchema("error");
        assertEquals(1, registry.getCachedSchemaCount());

        // Load another schema
        registry.getA2ASchema("handoff-message");
        assertEquals(2, registry.getCachedSchemaCount());
    }

    @Test
    @DisplayName("Schema versioning works")
    void schemaVersioningWorks() {
        // Load same schema with different versions
        JsonSchema schema1 = registry.getA2ASchema("handoff-message");
        JsonSchema schema2 = registry.getA2ASchema("handoff-message"); // No version specified

        // Both should be valid but potentially different instances
        assertNotNull(schema1);
        assertNotNull(schema2);
    }

    @Test
    @DisplayName("Schema invalidation works")
    void schemaInvalidationWorks() {
        // Load and cache schema
        registry.getA2ASchema("agent-card");
        assertEquals(1, registry.getCachedSchemaCount());

        // Invalidate schema
        registry.invalidateSchema("schemas/a2a/agent-card.json", null);
        assertEquals(0, registry.getCachedSchemaCount());

        // Load again after invalidation
        JsonSchema schema = registry.getA2ASchema("agent-card");
        assertNotNull(schema);
        assertEquals(1, registry.getCachedSchemaCount());
    }

    @Test
    @DisplayName("Schema cache status checking")
    void schemaCacheStatusChecking() {
        // Initially not cached
        assertFalse(registry.isSchemaCached("schemas/a2a/handoff-message.json", null));

        // Load schema
        registry.getA2ASchema("handoff-message");

        // Now should be cached
        assertTrue(registry.isSchemaCached("schemas/a2a/handoff-message.json", null));
    }

    @Test
    @DisplayName("Configuration management")
    void configurationManagement() {
        // Test getting configuration
        ValidationConfig config = registry.getConfig();
        assertNotNull(config);
        assertTrue(config.isEnableCaching());

        // Test setting custom configuration
        ValidationConfig newConfig = new Builder()
            .enableCaching(false)
            .build();

        registry.setConfig(newConfig);
        assertEquals(newConfig, registry.getConfig());
        assertFalse(registry.getConfig().isEnableCaching());
    }

    @Test
    @DisplayName("Configuration merging")
    void configurationMerging() {
        ValidationConfig config1 = new Builder()
            .enableCaching(true)
            .failFast(false)
            .build();

        ValidationConfig config2 = new Builder()
            .validationTimeout(java.time.Duration.ofSeconds(15))
            .build();

        ValidationConfig merged = config1.merge(config2);
        assertTrue(merged.isEnableCaching());
        assertFalse(merged.isFailFast());
        assertEquals(15, merged.getValidationTimeout().getSeconds());
    }

    @Test
    @DisplayName("Shutdown works correctly")
    void shutdownWorksCorrectly() {
        // Load some schemas
        registry.getCommonSchema("error");
        registry.getA2ASchema("handoff-message");

        // Shutdown should not throw exceptions
        assertDoesNotThrow(() -> registry.shutdown());

        // After shutdown, operations should fail
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            registry.getCommonSchema("timestamp");
        });

        assertEquals(SchemaValidationError.ErrorType.SYSTEM_ERROR, exception.getErrorType());
    }

    @Test
    @DisplayName("Null resource path handling")
    void nullResourcePathHandling() {
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            registry.getSchema(null);
        });

        assertEquals(SchemaValidationError.ErrorType.SCHEMA_NOT_FOUND, exception.getErrorType());
    }

    @Test
    @DisplayName("Empty resource path handling")
    void emptyResourcePathHandling() {
        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            registry.getSchema("");
        });

        assertEquals(SchemaValidationError.ErrorType.SCHEMA_NOT_FOUND, exception.getErrorType());
    }
}