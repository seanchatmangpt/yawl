package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates required configuration for demo execution.
 * Fails fast on missing required settings.
 */
public class RequiredConfigValidator {
    private static final Logger LOG = LoggerFactory.getLogger(RequiredConfigValidator.class);

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void validate() {
        errors.clear();
        warnings.clear();

        // Check YAWL credentials (required for engine connection)
        requireEnv("YAWL_USERNAME", "YAWL Engine username required");
        requireEnv("YAWL_PASSWORD", "YAWL Engine password required");

        // Check database credentials (required for persistence)
        requireEnv("DB_PASSWORD", "Database password required");

        // Check optional but recommended settings
        recommendEnv("ZAI_API_KEY", "AI commentary will be disabled");

        // Validate format
        if (hasEnv("YAWL_PASSWORD")) {
            String password = System.getenv("YAWL_PASSWORD");
            if (password.length() < 8) {
                warnings.add("YAWL_PASSWORD should be at least 8 characters");
            }
        }

        // Report
        if (!errors.isEmpty()) {
            LOG.error("Configuration validation failed:");
            errors.forEach(e -> LOG.error("  - {}", e));
            throw new IllegalStateException("Missing required configuration");
        }

        if (!warnings.isEmpty()) {
            LOG.warn("Configuration warnings:");
            warnings.forEach(w -> LOG.warn("  - {}", w));
        }

        LOG.info("Configuration validation passed");
    }

    private void requireEnv(String name, String message) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            errors.add(message + " (set " + name + ")");
        }
    }

    private void recommendEnv(String name, String consequence) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            warnings.add(name + " not set: " + consequence);
        }
    }

    private boolean hasEnv(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}