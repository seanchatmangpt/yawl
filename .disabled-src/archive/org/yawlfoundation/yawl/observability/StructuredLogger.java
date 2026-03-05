package org.yawlfoundation.yawl.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Structured logging wrapper for JSON-formatted logs.
 *
 * Emits logs in JSON format compatible with ELK/Loki aggregation:
 *
 * {
 *   "timestamp": "2026-02-17T12:34:56Z",
 *   "level": "INFO",
 *   "logger": "org.yawlfoundation.yawl.engine",
 *   "message": "Case execution started",
 *   "case_id": "case-123",
 *   "correlation_id": "trace-456",
 *   "custom_fields": { ... }
 * }
 *
 * Configuration via system properties:
 * - yawl.logging.level.engine: ERROR log routing destination
 * - yawl.logging.level.debug: DEBUG log archival destination
 */
public class StructuredLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger delegate;
    private final String loggerName;

    public StructuredLogger(Class<?> clazz) {
        this.delegate = LoggerFactory.getLogger(clazz);
        this.loggerName = clazz.getCanonicalName();
    }

    public StructuredLogger(String name) {
        this.delegate = LoggerFactory.getLogger(name);
        this.loggerName = name;
    }

    /**
     * Creates a structured logger for the given class.
     */
    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    /**
     * Creates a structured logger with the given name.
     */
    public static StructuredLogger getLogger(String name) {
        return new StructuredLogger(name);
    }

    /**
     * Logs at INFO level with structured fields.
     */
    public void info(String message, Map<String, Object> fields) {
        if (delegate.isInfoEnabled()) {
            logStructured("INFO", message, fields, null);
        }
    }

    /**
     * Logs at INFO level with a single structured field.
     */
    public void info(String message, String fieldName, Object fieldValue) {
        if (delegate.isInfoEnabled()) {
            Map<String, Object> fields = new HashMap<>();
            fields.put(fieldName, fieldValue);
            logStructured("INFO", message, fields, null);
        }
    }

    /**
     * Logs at WARN level with structured fields.
     */
    public void warn(String message, Map<String, Object> fields) {
        if (delegate.isWarnEnabled()) {
            logStructured("WARN", message, fields, null);
        }
    }

    /**
     * Logs at WARN level with a single structured field.
     */
    public void warn(String message, String fieldName, Object fieldValue) {
        if (delegate.isWarnEnabled()) {
            Map<String, Object> fields = new HashMap<>();
            fields.put(fieldName, fieldValue);
            logStructured("WARN", message, fields, null);
        }
    }

    /**
     * Logs at ERROR level with structured fields and exception.
     */
    public void error(String message, Map<String, Object> fields, Throwable ex) {
        if (delegate.isErrorEnabled()) {
            logStructured("ERROR", message, fields, ex);
        }
    }

    /**
     * Logs at ERROR level with exception.
     */
    public void error(String message, Throwable ex) {
        if (delegate.isErrorEnabled()) {
            logStructured("ERROR", message, new HashMap<>(), ex);
        }
    }

    /**
     * Logs at DEBUG level with structured fields.
     */
    public void debug(String message, Map<String, Object> fields) {
        if (delegate.isDebugEnabled()) {
            logStructured("DEBUG", message, fields, null);
        }
    }

    /**
     * Logs at DEBUG level with a single structured field.
     */
    public void debug(String message, String fieldName, Object fieldValue) {
        if (delegate.isDebugEnabled()) {
            Map<String, Object> fields = new HashMap<>();
            fields.put(fieldName, fieldValue);
            logStructured("DEBUG", message, fields, null);
        }
    }

    /**
     * Logs at TRACE level with structured fields.
     */
    public void trace(String message, Map<String, Object> fields) {
        if (delegate.isTraceEnabled()) {
            logStructured("TRACE", message, fields, null);
        }
    }

    /**
     * Emits structured JSON log entry.
     */
    private void logStructured(String level, String message, Map<String, Object> fields, Throwable ex) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("level", level);
            logEntry.put("logger", loggerName);
            logEntry.put("message", message);

            // Add MDC context if available
            String correlationId = MDC.get("correlation_id");
            if (correlationId != null) {
                logEntry.put("correlation_id", correlationId);
            }

            String traceId = MDC.get("trace_id");
            if (traceId != null) {
                logEntry.put("trace_id", traceId);
            }

            // Add custom fields
            logEntry.putAll(fields);

            // Add exception info if present
            if (ex != null) {
                Map<String, Object> exceptionInfo = new HashMap<>();
                exceptionInfo.put("type", ex.getClass().getName());
                exceptionInfo.put("message", ex.getMessage());
                exceptionInfo.put("stacktrace", getStackTrace(ex));
                logEntry.put("exception", exceptionInfo);
            }

            String jsonLog = objectMapper.writeValueAsString(logEntry);

            // Emit using SLF4J
            switch (level) {
                case "TRACE" -> delegate.trace(jsonLog);
                case "DEBUG" -> delegate.debug(jsonLog);
                case "INFO" -> delegate.info(jsonLog);
                case "WARN" -> delegate.warn(jsonLog);
                case "ERROR" -> delegate.error(jsonLog, ex);
                default -> delegate.info(jsonLog);
            }
        } catch (Exception e) {
            delegate.error("Failed to emit structured log", e);
        }
    }

    /**
     * Gets stack trace as string array.
     */
    private static String[] getStackTrace(Throwable ex) {
        StackTraceElement[] elements = ex.getStackTrace();
        String[] result = new String[elements.length];
        for (int i = 0; i < elements.length; i++) {
            result[i] = elements[i].toString();
        }
        return result;
    }

    /**
     * Sets correlation ID in MDC for log context propagation.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put("correlation_id", correlationId);
        }
    }

    /**
     * Sets trace ID in MDC for trace context propagation.
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put("trace_id", traceId);
        }
    }

    /**
     * Clears all MDC context.
     */
    public static void clearContext() {
        MDC.clear();
    }
}
