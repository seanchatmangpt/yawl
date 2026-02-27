package org.yawlfoundation.yawl.integration.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.StringJoiner;

/**
 * A utility class for consistent logging across A2A skills.
 *
 * <p>This class wraps the standard Logger with skill-specific formatting, providing:
 * <ul>
 *   <li>Skill ID and name in log prefixes</li>
 *   <li>Timing information for operations</li>
 *   <li>Structured logging with key-value pairs</li>
 *   <li>Validation-specific logging methods</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SkillLogger logger = SkillLogger.forSkill("order-processor", "OrderProcessor");
 * logger.info("Processing order");
 *
 * Map<String, Object> data = Map.of("orderId", "123", "status", "received");
 * logger.infoStructured("Order status", data);
 *
 * logger.withTiming("Process order", () -> {
 *     // Order processing logic
 * });
 * }</pre>
 *
 * @since YAWL v6.0.0
 */
public final class SkillLogger {

    private final Logger logger;
    private final String skillId;
    private final String skillName;

    /**
     * Private constructor to use the factory method.
     */
    private SkillLogger(String skillId, String skillName) {
        if (skillId == null || skillId.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill ID cannot be null or empty");
        }
        if (skillName == null || skillName.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill name cannot be null or empty");
        }
        this.skillId = skillId;
        this.skillName = skillName;
        this.logger = LogManager.getLogger("yawl.a2a." + skillId);
    }

    /**
     * Creates a new SkillLogger instance for the specified skill.
     *
     * @param skillId The unique identifier for the skill
     * @param skillName The human-readable name of the skill
     * @return A new SkillLogger instance
     * @throws IllegalArgumentException if skillId or skillName is null/empty
     */
    public static SkillLogger forSkill(String skillId, String skillName) {
        return new SkillLogger(skillId, skillName);
    }

    /**
     * Returns the skill-specific log prefix.
     *
     * @return The log prefix string
     */
    private String prefix() {
        return "[" + skillId + "] ";
    }

    // ─── Basic Logging ──────────────────────────────────────────────────────

    /**
     * Logs an informational message.
     *
     * @param message The message to log
     */
    public void info(String message) {
        logger.info(prefix() + message);
    }

    /**
     * Logs an informational message with a single additional parameter.
     *
     * @param message The base message
     * @param param1 The additional parameter
     */
    public void info(String message, Object param1) {
        logger.info(prefix() + message, param1);
    }

    /**
     * Logs an informational message with additional parameters.
     *
     * @param message The base message
     * @param param1 The first additional parameter
     * @param param2 The second additional parameter
     */
    public void info(String message, Object param1, Object param2) {
        logger.info(prefix() + message, param1, param2);
    }

    /**
     * Logs an informational message with additional parameters.
     *
     * @param message The base message
     * @param param1 The first additional parameter
     * @param param2 The second additional parameter
     * @param param3 The third additional parameter
     */
    public void info(String message, Object param1, Object param2, Object param3) {
        logger.info(prefix() + message, param1, param2, param3);
    }

    /**
     * Logs an informational message with additional parameters.
     *
     * @param message The base message
     * @param params The additional parameters
     */
    public void info(String message, Object... params) {
        if (params.length == 1) {
            logger.info(prefix() + message, params[0]);
        } else if (params.length == 2) {
            logger.info(prefix() + message, params[0], params[1]);
        } else if (params.length == 3) {
            logger.info(prefix() + message, params[0], params[1], params[2]);
        } else {
            logger.info(prefix() + message);
        }
    }

    /**
     * Logs a warning message.
     *
     * @param message The message to log
     */
    public void warn(String message) {
        logger.warn(prefix() + message);
    }

    /**
     * Logs an error message.
     *
     * @param message The message to log
     */
    public void error(String message) {
        logger.error(prefix() + message);
    }

    /**
     * Logs an error message with an exception.
     *
     * @param message The message to log
     * @param t The throwable to include in the log
     */
    public void error(String message, Throwable t) {
        logger.error(prefix() + message, t);
    }

    /**
     * Logs a debug message.
     *
     * @param message The message to log
     */
    public void debug(String message) {
        logger.debug(prefix() + message);
    }

    // ─── Timing Logging ─────────────────────────────────────────────────────

    /**
     * Logs the timing of a completed operation.
     *
     * @param operation The name of the operation
     * @param elapsedMs The time in milliseconds the operation took
     */
    public void logTiming(String operation, long elapsedMs) {
        info(operation + " completed in " + elapsedMs + "ms");
    }

    /**
     * Executes the given runnable and automatically logs the timing.
     *
     * @param operation The name of the operation to log
     * @param runnable The operation to execute
     */
    public void withTiming(String operation, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            logTiming(operation, System.currentTimeMillis() - start);
        }
    }

    // ─── Structured Logging ─────────────────────────────────────────────────

    /**
     * Logs an informational message with structured key-value data.
     *
     * @param message The base message
     * @param data Map of key-value pairs to include in the log
     */
    public void infoStructured(String message, Map<String, ?> data) {
        String structuredMessage = formatStructuredMessage(message, data);
        logger.info(prefix() + structuredMessage);
    }

    /**
     * Logs a warning message with structured key-value data.
     *
     * @param message The base message
     * @param data Map of key-value pairs to include in the log
     */
    public void warnStructured(String message, Map<String, ?> data) {
        String structuredMessage = formatStructuredMessage(message, data);
        logger.warn(prefix() + structuredMessage);
    }

    /**
     * Formats a message with structured data using StringJoiner.
     *
     * @param message The base message
     * @param data Map of key-value pairs
     * @return The formatted structured message
     */
    private String formatStructuredMessage(String message, Map<String, ?> data) {
        StringJoiner joiner = new StringJoiner(", ", message + " [", "]");
        data.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }

    // ─── Validation Logging ─────────────────────────────────────────────────

    /**
     * Logs the start of a parameter validation process.
     *
     * @param paramName The name of the parameter being validated
     */
    public void logValidationStart(String paramName) {
        debug("Validating parameter: " + paramName);
    }

    /**
     * Logs successful validation of a parameter.
     *
     * @param paramName The name of the parameter that was validated successfully
     */
    public void logValidationSuccess(String paramName) {
        debug("Parameter " + paramName + " validated successfully");
    }

    /**
     * Logs a validation failure for a parameter.
     *
     * @param paramName The name of the parameter that failed validation
     * @param reason The reason for validation failure
     */
    public void logValidationFailure(String paramName, String reason) {
        warn("Parameter " + paramName + " validation failed: " + reason);
    }
}