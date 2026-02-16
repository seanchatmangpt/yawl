package org.yawlfoundation.yawl.engine.interfce.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for REST API resource layer.
 * Tests error response formatting, exception mapping, and REST semantics.
 *
 * Chicago TDD: Real exception mapper and JSON serialization.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class RestResourceUnitTest extends TestCase {

    private YawlExceptionMapper exceptionMapper;
    private ObjectMapper objectMapper;

    public RestResourceUnitTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        exceptionMapper = new YawlExceptionMapper();
        objectMapper = new ObjectMapper();
    }

    @Override
    protected void tearDown() throws Exception {
        exceptionMapper = null;
        objectMapper = null;
        super.tearDown();
    }

    public void testExceptionMapperCreation() {
        assertNotNull("Exception mapper should be created", exceptionMapper);
    }

    public void testExceptionToResponseConversion() {
        RuntimeException exception = new RuntimeException("Test error message");

        jakarta.ws.rs.core.Response response = exceptionMapper.toResponse(exception);

        assertNotNull("Response should not be null", response);
        assertEquals("Status should be 500", 500, response.getStatus());
        assertNotNull("Response should have entity", response.getEntity());
    }

    public void testExceptionResponseFormat() throws Exception {
        RuntimeException exception = new RuntimeException("Operation failed");

        jakarta.ws.rs.core.Response response = exceptionMapper.toResponse(exception);
        String json = (String) response.getEntity();

        assertNotNull("JSON response should not be null", json);
        assertTrue("Should contain error field", json.contains("error"));
        assertTrue("Should contain message field", json.contains("message"));
        assertTrue("Should contain exception class name", json.contains("RuntimeException"));
        assertTrue("Should contain error message", json.contains("Operation failed"));
    }

    public void testExceptionResponseParsable() throws Exception {
        RuntimeException exception = new RuntimeException("Test error");

        jakarta.ws.rs.core.Response response = exceptionMapper.toResponse(exception);
        String json = (String) response.getEntity();

        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

        assertNotNull("Parsed response should not be null", parsed);
        assertTrue("Should have error field", parsed.containsKey("error"));
        assertTrue("Should have message field", parsed.containsKey("message"));
        assertEquals("Error should be exception class", "RuntimeException", parsed.get("error"));
        assertEquals("Message should match", "Test error", parsed.get("message"));
    }

    public void testNullMessageException() throws Exception {
        RuntimeException exception = new RuntimeException();

        jakarta.ws.rs.core.Response response = exceptionMapper.toResponse(exception);
        String json = (String) response.getEntity();

        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

        assertTrue("Should have message field", parsed.containsKey("message"));
        assertNotNull("Message should not be null", parsed.get("message"));
        assertEquals("Should have default message", "Unknown error", parsed.get("message"));
    }

    public void testDifferentExceptionTypes() throws Exception {
        Exception[] exceptions = {
                new IllegalArgumentException("Invalid argument"),
                new IllegalStateException("Invalid state"),
                new NullPointerException("Null value"),
                new UnsupportedOperationException("Not supported")
        };

        for (Exception exception : exceptions) {
            jakarta.ws.rs.core.Response response = exceptionMapper.toResponse(exception);
            String json = (String) response.getEntity();

            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

            assertEquals("Should map exception class name",
                    exception.getClass().getSimpleName(), parsed.get("error"));
            assertEquals("Should preserve error message",
                    exception.getMessage(), parsed.get("message"));
        }
    }

    public void testCaseIdValidation() {
        assertTrue("Valid case ID format", isValidCaseId("123.456"));
        assertTrue("Valid case ID with prefix", isValidCaseId("case-123.456"));
        assertFalse("Empty case ID", isValidCaseId(""));
        assertFalse("Null case ID", isValidCaseId(null));
        assertFalse("Whitespace only", isValidCaseId("   "));
    }

    public void testWorkItemIdValidation() {
        assertTrue("Valid work item ID", isValidWorkItemId("123"));
        assertTrue("Valid UUID format", isValidWorkItemId("abc-123-def-456"));
        assertFalse("Empty work item ID", isValidWorkItemId(""));
        assertFalse("Null work item ID", isValidWorkItemId(null));
    }

    public void testSessionHandleValidation() {
        assertTrue("Valid session handle", isValidSessionHandle("session-abc123"));
        assertTrue("UUID session handle", isValidSessionHandle("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse("Empty session handle", isValidSessionHandle(""));
        assertFalse("Null session handle", isValidSessionHandle(null));
        assertFalse("Whitespace session handle", isValidSessionHandle("   "));
    }

    public void testErrorResponseFormatConsistency() throws Exception {
        Exception exception = new RuntimeException("Test error");

        jakarta.ws.rs.core.Response response1 = exceptionMapper.toResponse(exception);
        jakarta.ws.rs.core.Response response2 = exceptionMapper.toResponse(exception);

        String json1 = (String) response1.getEntity();
        String json2 = (String) response2.getEntity();

        Map<String, Object> parsed1 = objectMapper.readValue(json1, Map.class);
        Map<String, Object> parsed2 = objectMapper.readValue(json2, Map.class);

        assertEquals("Error responses should be consistent", parsed1.get("error"), parsed2.get("error"));
        assertEquals("Error messages should be consistent", parsed1.get("message"), parsed2.get("message"));
    }

    public void testJsonSerialization() throws Exception {
        Map<String, Object> testData = new HashMap<>();
        testData.put("caseId", "123.456");
        testData.put("taskId", "Approve_Order");
        testData.put("status", "enabled");

        String json = objectMapper.writeValueAsString(testData);

        assertNotNull("Serialized JSON should not be null", json);
        assertTrue("Should contain caseId", json.contains("caseId"));
        assertTrue("Should contain taskId", json.contains("taskId"));
        assertTrue("Should contain status", json.contains("status"));
    }

    public void testJsonDeserialization() throws Exception {
        String json = "{\"caseId\":\"123.456\",\"taskId\":\"Approve_Order\",\"status\":\"enabled\"}";

        Map<String, Object> data = objectMapper.readValue(json, Map.class);

        assertNotNull("Deserialized data should not be null", data);
        assertEquals("Should parse caseId", "123.456", data.get("caseId"));
        assertEquals("Should parse taskId", "Approve_Order", data.get("taskId"));
        assertEquals("Should parse status", "enabled", data.get("status"));
    }

    public void testHttpStatusCodeValidation() {
        assertTrue("200 is valid", isValidHttpStatus(200));
        assertTrue("201 is valid", isValidHttpStatus(201));
        assertTrue("400 is valid", isValidHttpStatus(400));
        assertTrue("404 is valid", isValidHttpStatus(404));
        assertTrue("500 is valid", isValidHttpStatus(500));
        assertFalse("0 is invalid", isValidHttpStatus(0));
        assertFalse("600 is invalid", isValidHttpStatus(600));
        assertFalse("Negative is invalid", isValidHttpStatus(-1));
    }

    public void testMediaTypeValidation() {
        assertTrue("JSON is valid", isValidMediaType("application/json"));
        assertTrue("XML is valid", isValidMediaType("application/xml"));
        assertTrue("Plain text is valid", isValidMediaType("text/plain"));
        assertFalse("Empty is invalid", isValidMediaType(""));
        assertFalse("Null is invalid", isValidMediaType(null));
    }

    private boolean isValidCaseId(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return false;
        }
        return caseId.matches(".*\\d+.*");
    }

    private boolean isValidWorkItemId(String workItemId) {
        if (workItemId == null || workItemId.trim().isEmpty()) {
            return false;
        }
        return workItemId.length() > 0;
    }

    private boolean isValidSessionHandle(String sessionHandle) {
        if (sessionHandle == null || sessionHandle.trim().isEmpty()) {
            return false;
        }
        return sessionHandle.trim().equals(sessionHandle) && sessionHandle.length() > 0;
    }

    private boolean isValidHttpStatus(int status) {
        return status >= 100 && status < 600;
    }

    private boolean isValidMediaType(String mediaType) {
        if (mediaType == null || mediaType.trim().isEmpty()) {
            return false;
        }
        return mediaType.contains("/");
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("REST Resource Unit Tests");
        suite.addTestSuite(RestResourceUnitTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
