/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 *
 * This software is the intellectual property of the YAWL Foundation.
 * It is provided as-is under the terms of the YAWL Open Source License.
 */

package org.yawlfoundation.yawl.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Cloud Config Server Configuration.
 *
 * This configuration enables YAWL as a centralized configuration server using
 * Spring Cloud Config. Configuration properties are stored in a Git repository
 * (.cloud/config) and exposed via REST API.
 *
 * Clients can fetch configuration via:
 * - HTTP GET /config/{application}/{profile}[/{label}]
 * - HTTP GET /config/{application}-{profile}.yml
 * - HTTP POST /config/monitor for refresh notifications
 *
 * Configuration files in .cloud/config/:
 * - application.yml              (shared configs for all profiles)
 * - application-dev.yml          (development environment)
 * - application-prod.yml         (production environment)
 * - yawl-engine.yml              (engine-specific settings)
 * - yawl-mcp-a2a.yml             (MCP/A2A integration settings)
 *
 * Secrets are encrypted using Jasypt and stored as {cipher}... in YAML.
 *
 * @author YAWL Foundation Team
 * @since 6.0.0
 */
@Configuration
@EnableConfigProperties(ConfigServerProperties.class)
@ConditionalOnProperty(
    name = "config.server.enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class ConfigServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ConfigServerConfiguration.class);

    /**
     * Configuration properties for the Config Server.
     *
     * Properties are prefixed with "config.server" and support environment-specific
     * overrides via Spring profiles.
     *
     * Key properties:
     * - git.uri: Path/URL to Git repository containing config files
     * - git.default-label: Default Git branch/tag (e.g., main, develop)
     * - git.search-paths: Subdirectories to search for config files
     * - encryption.key: Master encryption key for Jasypt (set via env var)
     */
    @Configuration
    @ConfigurationProperties(prefix = "config.server")
    public static class ConfigServerProperties {

        /**
         * Git repository URI where config files are stored.
         * Default: .cloud/config (relative path)
         *
         * Can be:
         * - Local filesystem: file://.cloud/config or .cloud/config
         * - Remote Git: https://github.com/user/yawl-config.git
         * - SSH: git@github.com:user/yawl-config.git
         */
        private String gitUri = ".cloud/config";

        /**
         * Default Git branch, tag, or commit to fetch from.
         * Default: main
         */
        private String gitDefaultLabel = "main";

        /**
         * Subdirectories within the Git repo to search for config files.
         * Default: empty (search root)
         *
         * Example: "config" would look for configs in .cloud/config/config/
         */
        private String gitSearchPaths = "";

        /**
         * Username for Git repository access (if using private repo).
         * Default: empty
         */
        private String gitUsername = "";

        /**
         * Password/token for Git repository access (if using private repo).
         * Default: empty (set via environment variable CONFIG_SERVER_GIT_PASSWORD)
         */
        private String gitPassword = "";

        /**
         * Enable Config Server endpoints.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Encryption master key for Jasypt secret decryption.
         * Default: empty
         *
         * Set via environment variable:
         * export CONFIG_SERVER_ENCRYPTION_KEY="your-secret-key"
         *
         * Or application.yml:
         * config:
         *   server:
         *     encryption:
         *       key: ${JASYPT_MASTER_KEY}
         */
        private String encryptionKey = "";

        /**
         * Enable HTTP basic auth for Config Server endpoints.
         * Default: false
         */
        private boolean securityEnabled = false;

        /**
         * Username for HTTP basic auth.
         * Default: empty
         */
        private String securityUsername = "";

        /**
         * Password for HTTP basic auth.
         * Default: empty (set via environment variable)
         */
        private String securityPassword = "";

        // Getters and setters for Spring binding

        public String getGitUri() {
            return gitUri;
        }

        public void setGitUri(String gitUri) {
            this.gitUri = gitUri;
        }

        public String getGitDefaultLabel() {
            return gitDefaultLabel;
        }

        public void setGitDefaultLabel(String gitDefaultLabel) {
            this.gitDefaultLabel = gitDefaultLabel;
        }

        public String getGitSearchPaths() {
            return gitSearchPaths;
        }

        public void setGitSearchPaths(String gitSearchPaths) {
            this.gitSearchPaths = gitSearchPaths;
        }

        public String getGitUsername() {
            return gitUsername;
        }

        public void setGitUsername(String gitUsername) {
            this.gitUsername = gitUsername;
        }

        public String getGitPassword() {
            return gitPassword;
        }

        public void setGitPassword(String gitPassword) {
            this.gitPassword = gitPassword;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }

        public boolean isSecurityEnabled() {
            return securityEnabled;
        }

        public void setSecurityEnabled(boolean securityEnabled) {
            this.securityEnabled = securityEnabled;
        }

        public String getSecurityUsername() {
            return securityUsername;
        }

        public void setSecurityUsername(String securityUsername) {
            this.securityUsername = securityUsername;
        }

        public String getSecurityPassword() {
            return securityPassword;
        }

        public void setSecurityPassword(String securityPassword) {
            this.securityPassword = securityPassword;
        }
    }
}
