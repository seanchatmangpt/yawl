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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration settings for JSON schema validation.
 *
 * <p>Provides configurable options for validation behavior, including
 * schema caching, validation timeouts, and error handling preferences.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SchemaRegistry
 * @see JsonSchemaValidator
 */
public class ValidationConfig {

    private static final ValidationConfig DEFAULT = new ValidationConfig();

    private final boolean enableCaching;
    private final Duration schemaCacheTimeout;
    private final Duration validationTimeout;
    private final int maxSchemaSize;
    private final boolean failFast;
    private final boolean includeValidationDetails;
    private final Map<String, Object> customProperties;

    private ValidationConfig() {
        this.enableCaching = true;
        this.schemaCacheTimeout = Duration.ofMinutes(30);
        this.validationTimeout = Duration.ofSeconds(10);
        this.maxSchemaSize = 1024 * 1024; // 1MB
        this.failFast = true;
        this.includeValidationDetails = true;
        this.customProperties = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new validation configuration with custom settings.
     *
     * @param enableCaching whether to enable schema caching
     * @param schemaCacheTimeout timeout for cached schemas
     * @param validationTimeout timeout for individual validations
     * @param maxSchemaSize maximum allowed schema size in bytes
     * @param failFast whether to fail on first validation error
     * @param includeValidationDetails whether to include detailed validation results
     */
    public ValidationConfig(boolean enableCaching, Duration schemaCacheTimeout, Duration validationTimeout,
                          int maxSchemaSize, boolean failFast, boolean includeValidationDetails) {
        this.enableCaching = enableCaching;
        this.schemaCacheTimeout = schemaCacheTimeout != null ? schemaCacheTimeout : Duration.ofMinutes(30);
        this.validationTimeout = validationTimeout != null ? validationTimeout : Duration.ofSeconds(10);
        this.maxSchemaSize = maxSchemaSize;
        this.failFast = failFast;
        this.includeValidationDetails = includeValidationDetails;
        this.customProperties = new ConcurrentHashMap<>();
    }

    /**
     * Gets the default validation configuration.
     *
     * @return default configuration
     */
    public static ValidationConfig getDefault() {
        return DEFAULT;
    }

    /**
     * Creates a new builder for constructing validation configurations.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing validation configurations.
     */
    public static class Builder {
        private boolean enableCaching = true;
        private Duration schemaCacheTimeout = Duration.ofMinutes(30);
        private Duration validationTimeout = Duration.ofSeconds(10);
        private int maxSchemaSize = 1024 * 1024; // 1MB
        private boolean failFast = true;
        private boolean includeValidationDetails = true;

        /**
         * Sets whether to enable schema caching.
         *
         * @param enableCaching true to enable caching
         * @return this builder
         */
        public Builder enableCaching(boolean enableCaching) {
            this.enableCaching = enableCaching;
            return this;
        }

        /**
         * Sets the cache timeout for schemas.
         *
         * @param schemaCacheTimeout cache timeout duration
         * @return this builder
         */
        public Builder schemaCacheTimeout(Duration schemaCacheTimeout) {
            this.schemaCacheTimeout = schemaCacheTimeout;
            return this;
        }

        /**
         * Sets the validation timeout.
         *
         * @param validationTimeout validation timeout duration
         * @return this builder
         */
        public Builder validationTimeout(Duration validationTimeout) {
            this.validationTimeout = validationTimeout;
            return this;
        }

        /**
         * Sets the maximum allowed schema size.
         *
         * @param maxSchemaSize maximum schema size in bytes
         * @return this builder
         */
        public Builder maxSchemaSize(int maxSchemaSize) {
            this.maxSchemaSize = maxSchemaSize;
            return this;
        }

        /**
         * Sets whether to fail on first validation error.
         *
         * @param failFast true to fail fast
         * @return this builder
         */
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * Sets whether to include detailed validation results.
         *
         * @param includeValidationDetails true to include details
         * @return this builder
         */
        public Builder includeValidationDetails(boolean includeValidationDetails) {
            this.includeValidationDetails = includeValidationDetails;
            return this;
        }

        /**
         * Builds the validation configuration.
         *
         * @return the configured validation configuration
         */
        public ValidationConfig build() {
            return new ValidationConfig(
                enableCaching,
                schemaCacheTimeout,
                validationTimeout,
                maxSchemaSize,
                failFast,
                includeValidationDetails
            );
        }
    }

    /**
     * Gets whether schema caching is enabled.
     *
     * @return true if caching is enabled
     */
    public boolean isEnableCaching() {
        return enableCaching;
    }

    /**
     * Gets the schema cache timeout.
     *
     * @return cache timeout duration
     */
    public Duration getSchemaCacheTimeout() {
        return schemaCacheTimeout;
    }

    /**
     * Gets the validation timeout.
     *
     * @return validation timeout duration
     */
    public Duration getValidationTimeout() {
        return validationTimeout;
    }

    /**
     * Gets the maximum allowed schema size.
     *
     * @return maximum schema size in bytes
     */
    public int getMaxSchemaSize() {
        return maxSchemaSize;
    }

    /**
     * Gets whether to fail on first validation error.
     *
     * @return true if fail-fast is enabled
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Gets whether to include detailed validation results.
     *
     * @return true if details should be included
     */
    public boolean isIncludeValidationDetails() {
        return includeValidationDetails;
    }

    /**
     * Gets custom validation properties.
     *
     * @return map of custom properties
     */
    public Map<String, Object> getCustomProperties() {
        return customProperties;
    }

    /**
     * Gets a custom property by name.
     *
     * @param name property name
     * @return property value, or null if not set
     */
    public Object getCustomProperty(String name) {
        return customProperties.get(name);
    }

    /**
     * Sets a custom property.
     *
     * @param name property name
     * @param value property value
     */
    public void setCustomProperty(String name, Object value) {
        customProperties.put(name, value);
    }

    /**
     * Removes a custom property.
     *
     * @param name property name
     * @return the removed value, or null if not present
     */
    public Object removeCustomProperty(String name) {
        return customProperties.remove(name);
    }

    /**
     * Creates a copy of this configuration with modifications.
     *
     * @param builder function to modify the configuration
     * @return the new configuration
     */
    public ValidationConfig withConfig(Builder builder) {
        ValidationConfig newConfig = builder.build();
        newConfig.customProperties.putAll(this.customProperties);
        return newConfig;
    }

    /**
     * Merges this configuration with another configuration.
     *
     * @param other the other configuration to merge
     * @return the merged configuration
     */
    public ValidationConfig merge(ValidationConfig other) {
        if (other == null) {
            return this;
        }

        Builder builder = new Builder()
            .enableCaching(other.enableCaching)
            .schemaCacheTimeout(other.schemaCacheTimeout)
            .validationTimeout(other.validationTimeout)
            .maxSchemaSize(other.maxSchemaSize)
            .failFast(other.failFast)
            .includeValidationDetails(other.includeValidationDetails);

        builder.customProperties.putAll(this.customProperties);
        builder.customProperties.putAll(other.customProperties);

        return builder.build();
    }
}