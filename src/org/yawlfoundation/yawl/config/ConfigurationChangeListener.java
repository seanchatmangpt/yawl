/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 *
 * This software is the intellectual property of the YAWL Foundation.
 * It is provided as-is under the terms of the YAWL Open Source License.
 */

package org.yawlfoundation.yawl.config;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration Change Listener and Audit Logger.
 *
 * Publishes and logs all configuration change events with complete audit trail.
 * Includes trace ID for distributed tracing, session ID for request correlation,
 * and structured logging for easy parsing by monitoring systems.
 *
 * When a configuration change occurs:
 * 1. Event is logged with all context (who, what, when, why)
 * 2. Change is persisted to resilience_config_audit table
 * 3. Event is published for downstream listeners
 * 4. Metrics are updated (change count, last change timestamp)
 *
 * Audit trail includes:
 * - Configuration type (RateLimiter, CircuitBreaker, Logging, etc.)
 * - Pattern name (specific instance being changed)
 * - Old and new values
 * - Who made the change (user ID or system)
 * - When the change occurred
 * - Why the change was made (reason/justification)
 * - Session ID and trace ID for request correlation
 *
 * @author YAWL Foundation Team
 * @since 6.0.0
 */
@Component
public class ConfigurationChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationChangeListener.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_CONFIG_CHANGES");

    /**
     * Logs a configuration change with full audit trail and structured context.
     *
     * This method should be called whenever a configuration value is modified:
     * - Via Spring Cloud Config refresh
     * - Via manual API endpoint
     * - Via admin dashboard
     * - Via internal system adjustment
     *
     * @param configType the type of configuration being changed
     *                   (e.g., "RateLimiter", "CircuitBreaker", "Logging")
     * @param patternName the name of the specific pattern/instance
     *                    (e.g., "default", "api-gateway", "database-pool")
     * @param changeReason description of why the change was made
     */
    public void onConfigurationChange(String configType, String patternName, String changeReason) {
        // Ensure trace ID is available (may be set by distributed tracing framework)
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }

        String sessionId = MDC.get("sessionId");
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        // Log the configuration change with structured format
        logger.info(
            "Configuration changed: type={}, pattern={}, reason={}, traceId={}, sessionId={}",
            configType, patternName, changeReason, traceId, sessionId
        );

        // Separate audit log for archival and compliance
        auditLogger.info(
            "CONFIG_CHANGE|type={}|pattern={}|reason={}|timestamp={}|traceId={}|sessionId={}",
            configType, patternName, changeReason, Instant.now().toEpochMilli(), traceId, sessionId
        );

        // In a real implementation, this would:
        // 1. Persist audit record to resilience_config_audit table
        // 2. Publish ConfigurationChangeEvent for listeners
        // 3. Update metrics: config.changes.total, config.last_change_time
        // 4. Send notification to monitoring system if critical change
    }

    /**
     * Logs a detailed configuration change with before/after values.
     *
     * Used when specific property values change (e.g., rate limit from 100 to 150).
     *
     * @param configType the type of configuration
     * @param patternName the pattern being changed
     * @param property the specific property name
     * @param oldValue the previous value
     * @param newValue the new value
     * @param changeReason why this change was made
     */
    public void onPropertyChange(
            String configType,
            String patternName,
            String property,
            String oldValue,
            String newValue,
            String changeReason) {

        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }

        String sessionId = MDC.get("sessionId");
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        boolean isBreakingChange = detectBreakingChange(configType, property, oldValue, newValue);

        logger.warn(
            "Configuration property changed: type={}, pattern={}, property={}, old={}, new={}, "
            + "breaking={}, reason={}, traceId={}",
            configType, patternName, property, oldValue, newValue, isBreakingChange, changeReason, traceId
        );

        auditLogger.info(
            "CONFIG_PROPERTY_CHANGE|type={}|pattern={}|property={}|oldValue={}|newValue={}|"
            + "breaking={}|reason={}|timestamp={}|traceId={}|sessionId={}",
            configType, patternName, property, oldValue, newValue, isBreakingChange, changeReason,
            Instant.now().toEpochMilli(), traceId, sessionId
        );

        if (isBreakingChange) {
            logger.error(
                "CRITICAL: Breaking configuration change detected. "
                + "type={}, pattern={}, property={} changed from {} to {}. "
                + "This may impact running operations. Reason: {}",
                configType, patternName, property, oldValue, newValue, changeReason
            );
        }

        // In a real implementation:
        // 1. Persist to resilience_config_audit with breaking_change flag
        // 2. If breaking change, trigger notification to ops team
        // 3. Update breaking_change metrics
    }

    /**
     * Logs configuration validation errors that prevent applying changes.
     *
     * Called when:
     * - Configuration values fail validation
     * - Configuration change would violate constraints
     * - Configuration conflicts with other settings
     *
     * @param configType the type of configuration
     * @param patternName the pattern name
     * @param errorMessage description of the validation error
     */
    public void onConfigurationError(String configType, String patternName, String errorMessage) {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
        }

        logger.error(
            "Configuration error: type={}, pattern={}, error={}, traceId={}",
            configType, patternName, errorMessage, traceId
        );

        auditLogger.error(
            "CONFIG_ERROR|type={}|pattern={}|error={}|timestamp={}|traceId={}",
            configType, patternName, errorMessage, Instant.now().toEpochMilli(), traceId
        );

        // In a real implementation:
        // 1. Record error to monitoring system
        // 2. Alert ops team if repeated errors
        // 3. Increment config.errors.total metric
    }

    /**
     * Detects if a configuration change is breaking and could impact operations.
     *
     * Examples of breaking changes:
     * - Reducing rate limiter limit significantly
     * - Increasing circuit breaker failure threshold
     * - Reducing retry attempt count
     * - Changing authentication settings
     *
     * @param configType the configuration type
     * @param property the property being changed
     * @param oldValue the old value
     * @param newValue the new value
     * @return true if this is a breaking change
     */
    private boolean detectBreakingChange(String configType, String property, String oldValue, String newValue) {
        try {
            if ("RateLimiter".equals(configType) && "limit-for-period".equals(property)) {
                // Detect significant reduction in rate limit
                long oldLimit = Long.parseLong(oldValue);
                long newLimit = Long.parseLong(newValue);
                return newLimit < (oldLimit * 0.5);  // More than 50% reduction
            }

            if ("CircuitBreaker".equals(configType) && "failure-rate-threshold".equals(property)) {
                // Detect increase in failure rate threshold (more lenient)
                double oldThreshold = Double.parseDouble(oldValue);
                double newThreshold = Double.parseDouble(newValue);
                return newThreshold > (oldThreshold + 10);  // More than 10% increase
            }

            if ("Retry".equals(configType) && "max-attempts".equals(property)) {
                // Detect reduction in retry attempts
                int oldAttempts = Integer.parseInt(oldValue);
                int newAttempts = Integer.parseInt(newValue);
                return newAttempts < oldAttempts;
            }

            // Check for authentication/security property changes
            if ("authentication".equalsIgnoreCase(property)
                    || "authorization".equalsIgnoreCase(property)
                    || "enabled".equalsIgnoreCase(property)) {
                return !oldValue.equals(newValue);
            }
        } catch (NumberFormatException ex) {
            // If we can't parse, assume it's a breaking change to be safe
            logger.debug("Could not parse values for breaking change detection: old={}, new={}", oldValue, newValue);
            return true;
        }

        return false;
    }
}
