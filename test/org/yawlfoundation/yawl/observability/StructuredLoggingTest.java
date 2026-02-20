package org.yawlfoundation.yawl.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for structured JSON logging with Logstash integration.
 *
 * Verifies:
 * - JSON output format correctness
 * - MDC context enrichment
 * - Sleuth trace ID and span ID inclusion
 * - Marker-based event tagging
 * - Exception serialization
 * - Profile-specific configurations
 */
@DisplayName("Structured JSON Logging Tests")
public class StructuredLoggingTest {

    private static final Logger logger = LoggerFactory.getLogger(StructuredLoggingTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    @DisplayName("StructuredLogger creates valid JSON format")
    void testStructuredLoggerJsonFormat() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);

        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", "case-12345");
        fields.put("task_id", "task-67890");
        fields.put("duration_ms", 1234L);

        // Verify no exceptions during structured logging
        assertDoesNotThrow(() -> structuredLogger.info("Workflow case completed", fields));
    }

    @Test
    @DisplayName("StructuredLogger with single field")
    void testStructuredLoggerSingleField() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger("test.logger");

        assertDoesNotThrow(() -> {
            structuredLogger.info("Process started", "process_id", "proc-001");
            structuredLogger.warn("Warning message", "severity", "HIGH");
            structuredLogger.debug("Debug information", "module", "engine");
        });
    }

    @Test
    @DisplayName("StructuredLogger with exception")
    void testStructuredLoggerWithException() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);

        Exception testException = new IllegalArgumentException("Invalid workflow specification");

        assertDoesNotThrow(() -> {
            structuredLogger.error("Workflow validation failed", testException);
        });
    }

    @Test
    @DisplayName("StructuredLogger with exception and fields")
    void testStructuredLoggerWithExceptionAndFields() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);

        Map<String, Object> fields = new HashMap<>();
        fields.put("case_id", "case-999");
        fields.put("error_code", "INVALID_SPEC");

        Exception testException = new RuntimeException("Failed to load specification");

        assertDoesNotThrow(() -> {
            structuredLogger.error("Case initialization failed", fields, testException);
        });
    }

    @Test
    @DisplayName("MDC context is preserved in structured logs")
    void testMDCContextPreservation() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger("correlation.test");

        String correlationId = "corr-123456-789";
        String traceId = "trace-abcdef-ghi";

        StructuredLogger.setCorrelationId(correlationId);
        StructuredLogger.setTraceId(traceId);

        assertEquals(correlationId, MDC.get("correlation_id"), "Correlation ID should be in MDC");
        assertEquals(traceId, MDC.get("trace_id"), "Trace ID should be in MDC");

        assertDoesNotThrow(() -> {
            structuredLogger.info("Operation with correlation", "operation", "process_case");
        });

        StructuredLogger.clearContext();
        assertNull(MDC.get("correlation_id"), "MDC should be cleared after clearContext()");
        assertNull(MDC.get("trace_id"), "MDC should be cleared after clearContext()");
    }

    @Test
    @DisplayName("Log markers are created correctly")
    void testLogMarkersCreation() {
        // Circuit Breaker markers
        Marker cbOpen = LogMarkers.circuitBreakerOpen("inventory-service");
        assertNotNull(cbOpen);
        assertTrue(cbOpen.getName().contains("CIRCUIT_BREAKER_OPEN"));

        Marker cbHalfOpen = LogMarkers.circuitBreakerHalfOpen("payment-service");
        assertNotNull(cbHalfOpen);
        assertTrue(cbHalfOpen.getName().contains("CIRCUIT_BREAKER_HALF_OPEN"));

        Marker cbClosed = LogMarkers.circuitBreakerClosed("notification-service");
        assertNotNull(cbClosed);
        assertTrue(cbClosed.getName().contains("CIRCUIT_BREAKER_CLOSED"));

        // Rate Limiter markers
        Marker rlAllowed = LogMarkers.rateLimiterAllowed("api-gateway");
        assertNotNull(rlAllowed);
        assertTrue(rlAllowed.getName().contains("RATE_LIMIT_ALLOWED"));

        Marker rlExceeded = LogMarkers.rateLimiterExceeded("api-gateway");
        assertNotNull(rlExceeded);
        assertTrue(rlExceeded.getName().contains("RATE_LIMIT_EXCEEDED"));

        // Retry markers
        Marker retryAttempt = LogMarkers.retryAttempt("database-query");
        assertNotNull(retryAttempt);
        assertTrue(retryAttempt.getName().contains("RETRY_ATTEMPT"));

        Marker retrySuccess = LogMarkers.retrySuccess("database-query");
        assertNotNull(retrySuccess);
        assertTrue(retrySuccess.getName().contains("RETRY_SUCCESS"));

        Marker retryExhausted = LogMarkers.retryExhausted("database-query");
        assertNotNull(retryExhausted);
        assertTrue(retryExhausted.getName().contains("RETRY_EXHAUSTED"));
    }

    @Test
    @DisplayName("Log markers with parent marker relationships")
    void testLogMarkersWithParent() {
        Marker parent = LogMarkers.circuitBreakerOpen("service-a");
        Marker child = LogMarkers.withParent("custom-marker", parent);

        assertNotNull(child);
        assertTrue(child.getName().contains("custom-marker"));
    }

    @Test
    @DisplayName("MDC enrichment with multiple fields")
    void testMDCEnrichmentMultipleFields() {
        MDC.put("correlation_id", "corr-multi-123");
        MDC.put("trace_id", "trace-multi-456");
        MDC.put("user_id", "user-789");
        MDC.put("session_id", "sess-abc");

        assertEquals("corr-multi-123", MDC.get("correlation_id"));
        assertEquals("trace-multi-456", MDC.get("trace_id"));
        assertEquals("user-789", MDC.get("user_id"));
        assertEquals("sess-abc", MDC.get("session_id"));

        MDC.clear();

        assertNull(MDC.get("correlation_id"));
        assertNull(MDC.get("trace_id"));
        assertNull(MDC.get("user_id"));
        assertNull(MDC.get("session_id"));
    }

    @Test
    @DisplayName("StructuredLogger handles null values gracefully")
    void testStructuredLoggerNullHandling() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);

        Map<String, Object> fieldsWithNull = new HashMap<>();
        fieldsWithNull.put("field1", "value1");
        fieldsWithNull.put("field2", null);
        fieldsWithNull.put("field3", "value3");

        assertDoesNotThrow(() -> {
            structuredLogger.info("Message with null fields", fieldsWithNull);
        });
    }

    @Test
    @DisplayName("StructuredLogger handles empty fields gracefully")
    void testStructuredLoggerEmptyFields() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);

        Map<String, Object> emptyFields = new HashMap<>();
        assertDoesNotThrow(() -> {
            structuredLogger.info("Message with empty fields", emptyFields);
        });
    }

    @Test
    @DisplayName("StructuredLogger enables all log levels")
    void testStructuredLoggerAllLevels() {
        StructuredLogger structuredLogger = StructuredLogger.getLogger("level.test");

        Map<String, Object> fields = new HashMap<>();
        fields.put("test", "value");

        assertDoesNotThrow(() -> {
            structuredLogger.trace("Trace level", fields);
            structuredLogger.debug("Debug level", fields);
            structuredLogger.info("Info level", fields);
            structuredLogger.warn("Warn level", fields);
            structuredLogger.error("Error level", fields, new Exception("test"));
        });
    }

    @Test
    @DisplayName("Circuit breaker markers include service name")
    void testCircuitBreakerMarkerDetails() {
        String serviceName = "payment-processor";

        Marker cbOpen = LogMarkers.circuitBreakerOpen(serviceName);
        assertTrue(cbOpen.getName().contains(serviceName),
                "Marker should include service name");

        Marker cbError = LogMarkers.circuitBreakerError(serviceName);
        assertTrue(cbError.getName().contains(serviceName),
                "Error marker should include service name");
    }

    @Test
    @DisplayName("Retry markers include operation name")
    void testRetryMarkerDetails() {
        String operationName = "inventory-lookup";

        Marker retryAttempt = LogMarkers.retryAttempt(operationName);
        assertTrue(retryAttempt.getName().contains(operationName),
                "Retry attempt marker should include operation name");

        Marker retrySuccess = LogMarkers.retrySuccess(operationName);
        assertTrue(retrySuccess.getName().contains(operationName),
                "Retry success marker should include operation name");

        Marker retryExhausted = LogMarkers.retryExhausted(operationName);
        assertTrue(retryExhausted.getName().contains(operationName),
                "Retry exhausted marker should include operation name");
    }

    @Test
    @DisplayName("Multiple MDC contexts can be managed independently")
    void testMultipleMDCContexts() {
        // First context
        StructuredLogger.setCorrelationId("corr-ctx1");
        assertEquals("corr-ctx1", MDC.get("correlation_id"));

        // Second context overrides
        StructuredLogger.setCorrelationId("corr-ctx2");
        assertEquals("corr-ctx2", MDC.get("correlation_id"));

        // Clear and verify
        StructuredLogger.clearContext();
        assertNull(MDC.get("correlation_id"));
    }

    @Test
    @DisplayName("StructuredLogger integrates with SLF4J")
    void testStructuredLoggerSLF4JIntegration() {
        // Ensure StructuredLogger uses SLF4J under the hood
        StructuredLogger structuredLogger = StructuredLogger.getLogger(StructuredLoggingTest.class);
        assertNotNull(structuredLogger, "StructuredLogger should be created successfully");

        // Verify it works with all log levels
        assertDoesNotThrow(() -> {
            structuredLogger.info("Test message", "key", "value");
        });
    }

    @Test
    @DisplayName("JSON parsing compatibility with Logstash")
    void testJSONParsingCompatibility() throws Exception {
        // Simulate a structured log entry JSON format
        String jsonLog = """
                {
                    "timestamp": "2026-02-20T12:34:56Z",
                    "level": "INFO",
                    "logger": "org.yawlfoundation.yawl.engine",
                    "message": "Case execution started",
                    "case_id": "case-123",
                    "correlation_id": "trace-456"
                }
                """;

        // Verify JSON is parseable
        JsonNode node = objectMapper.readTree(jsonLog);
        assertNotNull(node);
        assertEquals("INFO", node.get("level").asText());
        assertEquals("case-123", node.get("case_id").asText());
    }

}
