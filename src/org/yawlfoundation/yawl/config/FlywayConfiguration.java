/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 *
 * This software is the intellectual property of the YAWL Foundation.
 * It is provided as-is under the terms of the YAWL Open Source License.
 */

package org.yawlfoundation.yawl.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Flyway Database Migration Configuration.
 *
 * This configuration bean enables automatic database schema versioning and migration
 * via Flyway. Migrations are discovered in src/main/resources/db/migration/ and
 * executed in version order on application startup.
 *
 * Configuration properties (application.yml):
 * <pre>
 * flyway:
 *   enabled: true                                # Enable/disable migrations
 *   baseline-on-migrate: false                  # Create baseline if needed
 *   out-of-order: false                         # Reject out-of-order migrations
 *   validate-on-migrate: true                   # Validate before migration
 * </pre>
 *
 * Migration files must follow naming convention:
 * - V{number}__{description}.sql  (e.g., V1__Initial_Schema.sql)
 * - Versions are compared numerically, supporting gaps (V1, V3, V5, etc.)
 *
 * @author YAWL Foundation Team
 * @since 6.0.0
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
@ConditionalOnProperty(
    name = "flyway.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FlywayConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfiguration.class);

    /**
     * Creates and configures the Flyway migration bean.
     *
     * Flyway will:
     * 1. Scan classpath:db/migration/ for V{version}__{description}.sql files
     * 2. Create flyway_schema_history table if not exists
     * 3. Execute migrations in ascending version order
     * 4. Lock schema during migration to prevent concurrent runs
     *
     * @param dataSource the configured datasource (injected by Spring Boot)
     * @param properties Flyway configuration properties
     * @return configured Flyway instance
     */
    @Bean
    public Flyway flyway(DataSource dataSource, FlywayProperties properties) {
        logger.info("Initializing Flyway database migrations. BaselineOnMigrate={}, ValidateOnMigrate={}",
                properties.isBaselineOnMigrate(), properties.isValidateOnMigrate());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .validateOnMigrate(properties.isValidateOnMigrate())
                .outOfOrder(properties.isOutOfOrder())
                .cleanDisabled(true)  // NEVER auto-clean in production
                .table(properties.getSchemaHistoryTable())
                .build();

        // Log migration info before executing
        int appliedCount = flyway.info().applied().length;
        int pendingCount = flyway.info().pending().length;
        logger.info("Flyway migration status: Applied={}, Pending={}", appliedCount, pendingCount);

        if (pendingCount > 0) {
            logger.info("Pending migrations:");
            for (var migration : flyway.info().pending()) {
                logger.info("  - V{} ({}) - {}",
                    migration.getVersion(),
                    migration.getType(),
                    migration.getDescription());
            }
        }

        // Execute migrations
        int migrationsExecuted = flyway.migrate().migrationsExecuted;
        logger.info("Flyway migrations executed: {}", migrationsExecuted);

        return flyway;
    }

    /**
     * Configuration properties for Flyway from application.yml.
     *
     * Properties are prefixed with "flyway" and support environment-specific
     * overrides via application-{profile}.yml files.
     */
    @Configuration
    @ConfigurationProperties(prefix = "flyway")
    public static class FlywayProperties {

        /**
         * Enable/disable Flyway migrations on startup.
         * Default: true
         *
         * Disable for offline builds with: flyway.enabled=false
         */
        private boolean enabled = true;

        /**
         * Create a baseline version if flyway_schema_history table doesn't exist.
         * Default: false
         *
         * Set to true when retrofitting Flyway to existing databases.
         * The baseline version represents your current schema state.
         */
        private boolean baselineOnMigrate = false;

        /**
         * Validate migration checksums against flyway_schema_history.
         * Default: true
         *
         * If false, allows modifying already-applied migrations (NOT recommended).
         * Keep true to prevent accidental schema corruption.
         */
        private boolean validateOnMigrate = true;

        /**
         * Allow executing migrations out of order.
         * Default: false
         *
         * If true, prevents detecting missing migrations. Keep false for safety.
         */
        private boolean outOfOrder = false;

        /**
         * Name of the schema history table.
         * Default: flyway_schema_history
         */
        private String schemaHistoryTable = "flyway_schema_history";

        /**
         * Baseline version when creating initial baseline.
         * Default: empty (no baseline)
         */
        private String baselineVersion = "";

        /**
         * Baseline description when creating initial baseline.
         * Default: "Initial baseline"
         */
        private String baselineDescription = "Initial baseline";

        // Getters and setters for Spring binding
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isBaselineOnMigrate() {
            return baselineOnMigrate;
        }

        public void setBaselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
        }

        public boolean isValidateOnMigrate() {
            return validateOnMigrate;
        }

        public void setValidateOnMigrate(boolean validateOnMigrate) {
            this.validateOnMigrate = validateOnMigrate;
        }

        public boolean isOutOfOrder() {
            return outOfOrder;
        }

        public void setOutOfOrder(boolean outOfOrder) {
            this.outOfOrder = outOfOrder;
        }

        public String getSchemaHistoryTable() {
            return schemaHistoryTable;
        }

        public void setSchemaHistoryTable(String schemaHistoryTable) {
            this.schemaHistoryTable = schemaHistoryTable;
        }

        public String getBaselineVersion() {
            return baselineVersion;
        }

        public void setBaselineVersion(String baselineVersion) {
            this.baselineVersion = baselineVersion;
        }

        public String getBaselineDescription() {
            return baselineDescription;
        }

        public void setBaselineDescription(String baselineDescription) {
            this.baselineDescription = baselineDescription;
        }
    }
}
