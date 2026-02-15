package org.yawlfoundation.yawl.integration.autonomous.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enhanced logging wrapper with structured logging support.
 * Adds correlation IDs, context propagation, and JSON-friendly formatting.
 *
 * Thread-safe via SLF4J MDC (uses ThreadLocal).
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class StructuredLogger {

    private final Logger logger;

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TASK_ID_KEY = "taskId";
    public static final String CASE_ID_KEY = "caseId";
    public static final String AGENT_KEY = "agent";
    public static final String DOMAIN_KEY = "domain";

    /**
     * Create structured logger for specified class.
     *
     * @param clazz Class to create logger for
     */
    public StructuredLogger(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz cannot be null");
        }
        this.logger = LoggerFactory.getLogger(clazz);
    }

    /**
     * Create structured logger with specified name.
     *
     * @param name Logger name
     */
    public StructuredLogger(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        this.logger = LoggerFactory.getLogger(name);
    }

    /**
     * Set correlation ID in MDC for request tracing.
     * Generates UUID if not provided.
     *
     * @param correlationId Correlation ID (null to generate)
     * @return Correlation ID that was set
     */
    public String setCorrelationId(String correlationId) {
        String id = (correlationId != null && !correlationId.trim().isEmpty())
                    ? correlationId
                    : UUID.randomUUID().toString();

        MDC.put(CORRELATION_ID_KEY, id);
        return id;
    }

    /**
     * Get current correlation ID from MDC.
     *
     * @return Correlation ID or null if not set
     */
    public String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Clear correlation ID from MDC.
     */
    public void clearCorrelationId() {
        MDC.remove(CORRELATION_ID_KEY);
    }

    /**
     * Set context value in MDC.
     *
     * @param key Context key
     * @param value Context value
     */
    public void setContext(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    /**
     * Set multiple context values in MDC.
     *
     * @param context Context map
     */
    public void setContext(Map<String, String> context) {
        if (context != null) {
            context.forEach(this::setContext);
        }
    }

    /**
     * Get context value from MDC.
     *
     * @param key Context key
     * @return Context value or null if not set
     */
    public String getContext(String key) {
        return MDC.get(key);
    }

    /**
     * Clear all MDC context.
     */
    public void clearContext() {
        MDC.clear();
    }

    /**
     * Log task started event.
     *
     * @param taskId Task identifier
     * @param context Additional context
     */
    public void logTaskStarted(String taskId, Map<String, String> context) {
        if (taskId != null && !taskId.trim().isEmpty()) {
            MDC.put(TASK_ID_KEY, taskId);
        }

        setContext(context);

        logger.info("event=task_started taskId={} context={}",
                   taskId, formatContext(context));
    }

    /**
     * Log task completed event.
     *
     * @param taskId Task identifier
     * @param durationMs Execution duration in milliseconds
     * @param context Additional context
     */
    public void logTaskCompleted(String taskId, long durationMs, Map<String, String> context) {
        if (taskId != null && !taskId.trim().isEmpty()) {
            MDC.put(TASK_ID_KEY, taskId);
        }

        setContext(context);

        logger.info("event=task_completed taskId={} durationMs={} context={}",
                   taskId, durationMs, formatContext(context));
    }

    /**
     * Log task failed event.
     *
     * @param taskId Task identifier
     * @param error Error that caused failure
     * @param context Additional context
     */
    public void logTaskFailed(String taskId, Throwable error, Map<String, String> context) {
        if (taskId != null && !taskId.trim().isEmpty()) {
            MDC.put(TASK_ID_KEY, taskId);
        }

        setContext(context);

        logger.error("event=task_failed taskId={} error={} context={}",
                    taskId, error.getMessage(), formatContext(context), error);
    }

    /**
     * Log case started event.
     *
     * @param caseId Case identifier
     * @param specId Specification ID
     * @param context Additional context
     */
    public void logCaseStarted(String caseId, String specId, Map<String, String> context) {
        if (caseId != null && !caseId.trim().isEmpty()) {
            MDC.put(CASE_ID_KEY, caseId);
        }

        setContext(context);

        logger.info("event=case_started caseId={} specId={} context={}",
                   caseId, specId, formatContext(context));
    }

    /**
     * Log case completed event.
     *
     * @param caseId Case identifier
     * @param context Additional context
     */
    public void logCaseCompleted(String caseId, Map<String, String> context) {
        if (caseId != null && !caseId.trim().isEmpty()) {
            MDC.put(CASE_ID_KEY, caseId);
        }

        setContext(context);

        logger.info("event=case_completed caseId={} context={}",
                   caseId, formatContext(context));
    }

    /**
     * Log agent action event.
     *
     * @param agent Agent name
     * @param action Action performed
     * @param context Additional context
     */
    public void logAgentAction(String agent, String action, Map<String, String> context) {
        if (agent != null && !agent.trim().isEmpty()) {
            MDC.put(AGENT_KEY, agent);
        }

        setContext(context);

        logger.info("event=agent_action agent={} action={} context={}",
                   agent, action, formatContext(context));
    }

    /**
     * Log custom event with structured context.
     *
     * @param eventName Event name
     * @param context Event context
     */
    public void logEvent(String eventName, Map<String, String> context) {
        setContext(context);

        logger.info("event={} context={}",
                   eventName, formatContext(context));
    }

    /**
     * Format context map as JSON-friendly string.
     * Returns actual map structure, not placeholder.
     */
    private String formatContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");

        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            first = false;

            json.append("\"")
                .append(escapeJson(entry.getKey()))
                .append("\": \"")
                .append(escapeJson(entry.getValue()))
                .append("\"");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * Escape special characters for JSON string values.
     *
     * @param value String to escape
     * @return Escaped string
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "null";
        }

        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Delegate standard logging methods to underlying logger.
     */

    public void trace(String msg) {
        logger.trace(msg);
    }

    public void trace(String format, Object... arguments) {
        logger.trace(format, arguments);
    }

    public void debug(String msg) {
        logger.debug(msg);
    }

    public void debug(String format, Object... arguments) {
        logger.debug(format, arguments);
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void info(String format, Object... arguments) {
        logger.info(format, arguments);
    }

    public void warn(String msg) {
        logger.warn(msg);
    }

    public void warn(String format, Object... arguments) {
        logger.warn(format, arguments);
    }

    public void error(String msg) {
        logger.error(msg);
    }

    public void error(String format, Object... arguments) {
        logger.error(format, arguments);
    }

    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }

    /**
     * Create execution context builder for fluent API.
     *
     * @return New context builder
     */
    public ContextBuilder context() {
        return new ContextBuilder(this);
    }

    /**
     * Fluent API for building execution context.
     */
    public static class ContextBuilder {
        private final StructuredLogger logger;
        private final Map<String, String> context;

        private ContextBuilder(StructuredLogger logger) {
            this.logger = logger;
            this.context = new HashMap<>();
        }

        public ContextBuilder correlationId(String correlationId) {
            logger.setCorrelationId(correlationId);
            return this;
        }

        public ContextBuilder taskId(String taskId) {
            context.put(TASK_ID_KEY, taskId);
            return this;
        }

        public ContextBuilder caseId(String caseId) {
            context.put(CASE_ID_KEY, caseId);
            return this;
        }

        public ContextBuilder agent(String agent) {
            context.put(AGENT_KEY, agent);
            return this;
        }

        public ContextBuilder domain(String domain) {
            context.put(DOMAIN_KEY, domain);
            return this;
        }

        public ContextBuilder add(String key, String value) {
            context.put(key, value);
            return this;
        }

        public ContextBuilder addAll(Map<String, String> contextMap) {
            if (contextMap != null) {
                context.putAll(contextMap);
            }
            return this;
        }

        public void apply() {
            logger.setContext(context);
        }

        public Map<String, String> build() {
            return new HashMap<>(context);
        }
    }
}
