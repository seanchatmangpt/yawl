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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Centralized registry for JSON schemas with caching support.
 *
 * <p>Manages loading, caching, and retrieval of JSON schemas used for
 * validating A2A and MCP protocol messages. Provides thread-safe access
 * to schemas with automatic cache cleanup and version management.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see JsonSchemaValidator
 * @see ValidationConfig
 */
public class SchemaRegistry {

    private static final Logger logger = LogManager.getLogger(SchemaRegistry.class);
    private static final SchemaRegistry INSTANCE = new SchemaRegistry();

    private final Map<String, SchemaEntry> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> loadingLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final JsonSchemaFactory schemaFactory;
    private final ObjectMapper objectMapper;
    private final ValidationConfig config;

    private SchemaRegistry() {
        this.config = ValidationConfig.getDefault();
        this.schemaFactory = JsonSchemaFactory.getInstance(VersionFlag.V7);
        this.objectMapper = new ObjectMapper();
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

        // Start periodic cleanup
        scheduleCleanup();
    }

    /**
     * Gets the singleton instance of the schema registry.
     *
     * @return the schema registry instance
     */
    public static SchemaRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new schema registry with custom configuration.
     *
     * @param config validation configuration
     * @return a new schema registry instance
     */
    public static SchemaRegistry create(ValidationConfig config) {
        SchemaRegistry registry = new SchemaRegistry();
        registry.config = config != null ? config : ValidationConfig.getDefault();
        return registry;
    }

    /**
     * Gets a schema by its resource path.
     *
     * @param resourcePath path to the schema resource (e.g., "schemas/a2a/handoff-message.json")
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getSchema(String resourcePath) throws SchemaValidationError {
        return getSchema(resourcePath, null);
    }

    /**
     * Gets a schema by its resource path with version control.
     *
     * @param resourcePath path to the schema resource
     * @param version schema version (optional)
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getSchema(String resourcePath, String version) throws SchemaValidationError {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new SchemaValidationError("Schema resource path cannot be null or empty",
                SchemaValidationError.ErrorType.SCHEMA_NOT_FOUND, null, null, null);
        }

        String cacheKey = buildCacheKey(resourcePath, version);

        // Check cache first
        SchemaEntry entry = getFromCache(cacheKey);
        if (entry != null && !entry.isExpired()) {
            logger.debug("Cache hit for schema: {}", cacheKey);
            return entry.getSchema();
        }

        // Load and cache the schema
        return loadAndCacheSchema(resourcePath, version, cacheKey);
    }

    /**
     * Gets a schema from the classpath by its filename.
     *
     * @param filename schema filename (e.g., "handoff-message.json")
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getSchemaByFilename(String filename) throws SchemaValidationError {
        return getSchema("schemas/" + filename);
    }

    /**
     * Gets an A2A protocol schema.
     *
     * @param schemaName name of the A2A schema (e.g., "handoff-message", "agent-card")
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getA2ASchema(String schemaName) throws SchemaValidationError {
        return getSchemaByFilename("a2a/" + schemaName + ".json");
    }

    /**
     * Gets an MCP protocol schema.
     *
     * @param schemaName name of the MCP schema (e.g., "tool-call", "tool-result")
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getMCPSchema(String schemaName) throws SchemaValidationError {
        return getSchemaByFilename("mcp/" + schemaName + ".json");
    }

    /**
     * Gets a common schema.
     *
     * @param schemaName name of the common schema (e.g., "error", "timestamp")
     * @return the compiled schema
     * @throws SchemaValidationError if the schema cannot be loaded
     */
    public JsonSchema getCommonSchema(String schemaName) throws SchemaValidationError {
        return getSchemaByFilename("common/" + schemaName + ".json");
    }

    /**
     * Invalidates a cached schema.
     *
     * @param resourcePath path to the schema resource
     * @param version schema version (optional)
     */
    public void invalidateSchema(String resourcePath, String version) {
        String cacheKey = buildCacheKey(resourcePath, version);
        schemaCache.remove(cacheKey);
        logger.debug("Invalidated schema: {}", cacheKey);
    }

    /**
     * Invalidates all cached schemas.
     */
    public void invalidateAll() {
        schemaCache.clear();
        logger.debug("Invalidated all cached schemas");
    }

    /**
     * Gets the number of cached schemas.
     *
     * @return number of cached schemas
     */
    public int getCachedSchemaCount() {
        return schemaCache.size();
    }

    /**
     * Checks if a schema is cached and valid.
     *
     * @param resourcePath path to the schema resource
     * @param version schema version (optional)
     * @return true if the schema is cached and valid
     */
    public boolean isSchemaCached(String resourcePath, String version) {
        String cacheKey = buildCacheKey(resourcePath, version);
        SchemaEntry entry = schemaCache.get(cacheKey);
        return entry != null && !entry.isExpired();
    }

    /**
     * Gets configuration for this registry.
     *
     * @return validation configuration
     */
    public ValidationConfig getConfig() {
        return config;
    }

    /**
     * Sets custom configuration.
     *
     * @param config new configuration
     */
    public void setConfig(ValidationConfig config) {
        if (config != null) {
            this.config = config;
        }
    }

    /**
     * Shuts down the registry and cleans up resources.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        schemaCache.clear();
        loadingLocks.clear();
    }

    private String buildCacheKey(String resourcePath, String version) {
        StringBuilder key = new StringBuilder(resourcePath);
        if (version != null && !version.trim().isEmpty()) {
            key.append("?v=").append(version);
        }
        return key.toString();
    }

    private SchemaEntry getFromCache(String cacheKey) {
        if (!config.isEnableCaching()) {
            return null;
        }

        SchemaEntry entry = schemaCache.get(cacheKey);
        if (entry != null && entry.isExpired()) {
            schemaCache.remove(cacheKey);
            return null;
        }
        return entry;
    }

    private JsonSchema loadAndCacheSchema(String resourcePath, String version, String cacheKey) throws SchemaValidationError {
        // Use lock to prevent concurrent loading of the same schema
        ReentrantLock lock = loadingLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

        try {
            lock.lock();

            // Check cache again after acquiring lock
            SchemaEntry cachedEntry = getFromCache(cacheKey);
            if (cachedEntry != null) {
                return cachedEntry.getSchema();
            }

            logger.debug("Loading schema: {}", cacheKey);

            // Load schema from resources
            URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                throw new SchemaValidationError(
                    "Schema not found: " + resourcePath,
                    SchemaValidationError.ErrorType.SCHEMA_NOT_FOUND,
                    null,
                    "Ensure the schema file exists at the specified path",
                    Map.of("resourcePath", resourcePath)
                );
            }

            // Read and parse schema
            try (InputStream inputStream = resourceUrl.openStream()) {
                JsonNode schemaNode = objectMapper.readTree(inputStream);

                // Validate schema size
                int schemaSize = schemaNode.toString().getBytes().length;
                if (schemaSize > config.getMaxSchemaSize()) {
                    throw new SchemaValidationError(
                        "Schema size exceeds maximum allowed limit",
                        SchemaValidationError.ErrorType.INVALID_SCHEMA,
                        null,
                        "Reduce schema size or increase maxSchemaSize in configuration",
                        Map.of("actualSize", schemaSize, "maxSize", config.getMaxSchemaSize())
                    );
                }

                // Compile schema
                JsonSchema schema = schemaFactory.getSchema(schemaNode);

                // Cache the schema
                SchemaEntry entry = new SchemaEntry(schema, Instant.now().plus(config.getSchemaCacheTimeout()));
                schemaCache.put(cacheKey, entry);

                logger.info("Loaded and cached schema: {}", cacheKey);
                return schema;

            } catch (IOException e) {
                throw new SchemaValidationError(
                    "Failed to load schema: " + resourcePath,
                    SchemaValidationError.ErrorType.SYSTEM_ERROR,
                    null,
                    "Check the schema file and classpath configuration",
                    Map.of("resourcePath", resourcePath, "error", e.getMessage())
                );
            }

        } finally {
            lock.unlock();
            loadingLocks.remove(cacheKey);
        }
    }

    private void scheduleCleanup() {
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredSchemas,
            config.getSchemaCacheTimeout().toMinutes(),
            config.getSchemaCacheTimeout().toMinutes(),
            TimeUnit.MINUTES
        );
    }

    private void cleanupExpiredSchemas() {
        Instant now = Instant.now();
        int removedCount = schemaCache.entrySet().removeIf(entry -> {
            SchemaEntry entryValue = entry.getValue();
            boolean expired = entryValue.isExpired();
            if (expired) {
                logger.debug("Removed expired schema: {}", entry.getKey());
            }
            return expired;
        });

        if (removedCount > 0) {
            logger.info("Cleaned up {} expired schemas", removedCount);
        }
    }

    /**
     * Cache entry for schemas with expiration.
     */
    private static class SchemaEntry {
        private final JsonSchema schema;
        private final Instant expiresAt;

        public SchemaEntry(JsonSchema schema, Instant expiresAt) {
            this.schema = schema;
            this.expiresAt = expiresAt;
        }

        public JsonSchema getSchema() {
            return schema;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}