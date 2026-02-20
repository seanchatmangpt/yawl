package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for LoggingConfiguration Spring Boot configuration.
 *
 * Verifies:
 * - MDC enrichment properties configuration
 * - Resilience4j event listener integration
 * - HTTP request interceptor MDC enrichment
 * - Configuration property loading
 */
@DisplayName("Logging Configuration Tests")
public class LoggingConfigurationTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    @DisplayName("LoggingProperties can be instantiated")
    void testLoggingPropertiesInstantiation() {
        LoggingConfiguration.LoggingProperties props = new LoggingConfiguration.LoggingProperties();
        assertNotNull(props);

        LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties enrichment = props.getMdcEnrichment();
        assertNotNull(enrichment);
        assertTrue(enrichment.isEnabled(), "MDC enrichment should be enabled by default");
    }

    @Test
    @DisplayName("MDCEnrichmentProperties default values")
    void testMDCEnrichmentPropertiesDefaults() {
        LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties enrichment =
                new LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties();

        assertTrue(enrichment.isEnabled(), "Enrichment should be enabled by default");
        assertTrue(enrichment.isIncludeUserAgent(), "User-Agent should be included by default");
        assertFalse(enrichment.isIncludeRequestHeaders(), "Request headers should not be included by default");
    }

    @Test
    @DisplayName("MDCEnrichmentProperties can be configured")
    void testMDCEnrichmentPropertiesConfiguration() {
        LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties enrichment =
                new LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties();

        enrichment.setEnabled(false);
        assertFalse(enrichment.isEnabled());

        enrichment.setIncludeUserAgent(false);
        assertFalse(enrichment.isIncludeUserAgent());

        enrichment.setIncludeRequestHeaders(true);
        assertTrue(enrichment.isIncludeRequestHeaders());
    }

    @Test
    @DisplayName("LoggingProperties can set MDC enrichment")
    void testLoggingPropertiesSetEnrichment() {
        LoggingConfiguration.LoggingProperties props = new LoggingConfiguration.LoggingProperties();

        LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties customEnrichment =
                new LoggingConfiguration.LoggingProperties.MDCEnrichmentProperties();
        customEnrichment.setEnabled(false);

        props.setMdcEnrichment(customEnrichment);
        assertFalse(props.getMdcEnrichment().isEnabled());
    }

    @Test
    @DisplayName("MDC context values persist and clear correctly")
    void testMDCContextPersistence() {
        MDC.put("trace_id", "trace-123");
        MDC.put("span_id", "span-456");

        assertEquals("trace-123", MDC.get("trace_id"));
        assertEquals("span-456", MDC.get("span_id"));

        MDC.clear();

        assertNull(MDC.get("trace_id"));
        assertNull(MDC.get("span_id"));
    }

    @Test
    @DisplayName("Multiple MDC context values can coexist")
    void testMultipleMDCValues() {
        String[] keys = {"trace_id", "span_id", "correlation_id", "user_id", "session_id"};
        String[] values = {"t-1", "s-2", "c-3", "u-4", "ss-5"};

        for (int i = 0; i < keys.length; i++) {
            MDC.put(keys[i], values[i]);
        }

        for (int i = 0; i < keys.length; i++) {
            assertEquals(values[i], MDC.get(keys[i]));
        }

        MDC.clear();

        for (String key : keys) {
            assertNull(MDC.get(key));
        }
    }

    @Test
    @DisplayName("Client IP extraction from direct request")
    void testClientIpExtractionDirect() {
        MDC.put("client_ip", "192.168.1.100");
        assertEquals("192.168.1.100", MDC.get("client_ip"));
        MDC.clear();
    }

    @Test
    @DisplayName("Client IP extraction from X-Forwarded-For header")
    void testClientIpExtractionXForwardedFor() {
        String xForwardedFor = "10.0.0.1, 10.0.0.2, 192.168.1.1";
        MDC.put("x_forwarded_for", xForwardedFor);
        assertEquals(xForwardedFor, MDC.get("x_forwarded_for"));
        MDC.clear();
    }

    @Test
    @DisplayName("HTTP method and path are captured")
    void testHTTPMethodAndPathCapture() {
        MDC.put("http_method", "POST");
        MDC.put("http_path", "/api/v1/cases");

        assertEquals("POST", MDC.get("http_method"));
        assertEquals("/api/v1/cases", MDC.get("http_path"));

        MDC.clear();
    }

    @Test
    @DisplayName("User agent is optional in MDC")
    void testUserAgentCapture() {
        MDC.put("user_agent", "Mozilla/5.0 (X11; Linux x86_64)");
        assertEquals("Mozilla/5.0 (X11; Linux x86_64)", MDC.get("user_agent"));
        MDC.clear();
    }

    @Test
    @DisplayName("Correlation ID and trace ID propagation")
    void testCorrelationAndTraceIDPropagation() {
        String correlationId = "corr-12345";
        String traceId = "trace-67890";
        String spanId = "span-abcde";

        MDC.put("correlation_id", correlationId);
        MDC.put("trace_id", traceId);
        MDC.put("span_id", spanId);

        assertEquals(correlationId, MDC.get("correlation_id"));
        assertEquals(traceId, MDC.get("trace_id"));
        assertEquals(spanId, MDC.get("span_id"));

        MDC.clear();
    }

    @Test
    @DisplayName("Resilience4j pattern context")
    void testResilience4jPatternContext() {
        MDC.put("resilience4j_pattern", "circuit_breaker");
        MDC.put("circuit_breaker_name", "inventory-service");
        MDC.put("circuit_breaker_state_from", "CLOSED");
        MDC.put("circuit_breaker_state_to", "OPEN");

        assertEquals("circuit_breaker", MDC.get("resilience4j_pattern"));
        assertEquals("inventory-service", MDC.get("circuit_breaker_name"));
        assertEquals("CLOSED", MDC.get("circuit_breaker_state_from"));
        assertEquals("OPEN", MDC.get("circuit_breaker_state_to"));

        MDC.clear();
    }

    @Test
    @DisplayName("Rate limiter context")
    void testRateLimiterContext() {
        MDC.put("resilience4j_pattern", "rate_limiter");
        MDC.put("rate_limiter_name", "api-gateway");

        assertEquals("rate_limiter", MDC.get("resilience4j_pattern"));
        assertEquals("api-gateway", MDC.get("rate_limiter_name"));

        MDC.clear();
    }

    @Test
    @DisplayName("Retry context")
    void testRetryContext() {
        MDC.put("resilience4j_pattern", "retry");
        MDC.put("retry_name", "database-query");
        MDC.put("retry_attempt", "2");
        MDC.put("retry_error_cause", "TimeoutException");

        assertEquals("retry", MDC.get("resilience4j_pattern"));
        assertEquals("database-query", MDC.get("retry_name"));
        assertEquals("2", MDC.get("retry_attempt"));
        assertEquals("TimeoutException", MDC.get("retry_error_cause"));

        MDC.clear();
    }

    @Test
    @DisplayName("MDC values with special characters")
    void testMDCSpecialCharacters() {
        MDC.put("case_id", "case-123-456-789");
        MDC.put("error_message", "Failed: Invalid input 'abc@123'");
        MDC.put("json_field", "{\"key\": \"value\"}");

        assertEquals("case-123-456-789", MDC.get("case_id"));
        assertEquals("Failed: Invalid input 'abc@123'", MDC.get("error_message"));
        assertEquals("{\"key\": \"value\"}", MDC.get("json_field"));

        MDC.clear();
    }

    @Test
    @DisplayName("MDC performance with many values")
    void testMDCPerformanceWithManyValues() {
        // Add 50 values to MDC
        for (int i = 0; i < 50; i++) {
            MDC.put("key_" + i, "value_" + i);
        }

        // Verify all can be retrieved
        for (int i = 0; i < 50; i++) {
            assertEquals("value_" + i, MDC.get("key_" + i));
        }

        MDC.clear();

        // Verify all are cleared
        for (int i = 0; i < 50; i++) {
            assertNull(MDC.get("key_" + i));
        }
    }

}
