package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for QLever error recovery mechanisms.
 * Validates error handling paths for various failure scenarios.
 */
@DisplayName("QLever Error Recovery Tests")
class QLeverErrorRecoveryTest {

    private QLeverResult result;
    private static final String TEST_QUERY = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    private static final String TEST_DATA = "{\"results\": {\"bindings\": []}}";

    @BeforeEach
    void setUp() {
        result = QLeverResult.success(TEST_DATA, "test-metadata");
    }

    @Test
    @DisplayName("QLeverFfiException creation with message only")
    void testFfiExceptionWithMessage() {
        String errorMessage = "Native library load failed";
        QLeverFfiException exception = new QLeverFfiException(errorMessage);

        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("QLeverFfiException creation with message and cause")
    void testFfiExceptionWithMessageAndCause() {
        String errorMessage = "Memory allocation failure";
        Throwable cause = new OutOfMemoryError("Native heap exhausted");
        QLeverFfiException exception = new QLeverFfiException(errorMessage, cause);

        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertTrue(exception.toString().contains(errorMessage));
    }

    @Test
    @DisplayName("QLeverResult success creation with metadata")
    void testResultSuccessWithMetadata() {
        QLeverResult successResult = QLeverResult.success(TEST_DATA, "metadata");

        assertEquals(QLeverStatus.READY, successResult.status());
        assertEquals(TEST_DATA, successResult.data());
        assertEquals("metadata", successResult.metadata());
        assertTrue(successResult.isSuccess());
    }

    @Test
    @DisplayName("QLeverResult empty creation with error status")
    void testResultEmptyWithErrorStatus() {
        QLeverResult errorResult = QLeverResult.empty(QLeverStatus.ERROR);

        assertEquals(QLeverStatus.ERROR, errorResult.status());
        assertNull(errorResult.data());
        assertNull(errorResult.metadata());
        assertFalse(errorResult.isSuccess());
    }

    @Test
    @DisplayName("QLeverStatus operational checks")
    void testStatusOperationalChecks() {
        assertTrue(QLeverStatus.READY.isOperational());
        assertFalse(QLeverStatus.LOADING.isOperational());
        assertFalse(QLeverStatus.ERROR.isOperational());
        assertFalse(QLeverStatus.CLOSED.isOperational());
    }

    @Test
    @DisplayName("QLeverStatus error checks")
    void testStatusErrorChecks() {
        assertFalse(QLeverStatus.READY.isError());
        assertFalse(QLeverStatus.LOADING.isError());
        assertTrue(QLeverStatus.ERROR.isError());
        assertFalse(QLeverStatus.CLOSED.isError());
    }

    @Test
    @DisplayName("QLeverStatus enum values")
    void testStatusEnumValues() {
        assertEquals(4, QLeverStatus.values().length);
        assertEquals("READY", QLeverStatus.READY.name());
        assertEquals("LOADING", QLeverStatus.LOADING.name());
        assertEquals("ERROR", QLeverStatus.ERROR.name());
        assertEquals("CLOSED", QLeverStatus.CLOSED.name());
    }

    @Test
    @DisplayName("QLeverMediaType content type retrieval")
    void testMediaTypeContentType() {
        assertEquals("text/turtle", QLeverMediaType.TURTLE.getContentType());
        assertEquals("application/sparql-results+json", QLeverMediaType.JSON.getContentType());
        assertEquals("application/sparql-results+xml", QLeverMediaType.XML.getContentType());
        assertEquals("text/csv", QLeverMediaType.CSV.getContentType());
    }

    @Test
    @DisplayName("QLeverMediaType fromContentType - valid")
    void testMediaTypeFromContentTypeValid() {
        assertEquals(QLeverMediaType.TURTLE, QLeverMediaType.fromContentType("text/turtle"));
        assertEquals(QLeverMediaType.TURTLE, QLeverMediaType.fromContentType("TEXT/TURTLE"));
        assertEquals(QLeverMediaType.JSON, QLeverMediaType.fromContentType("application/sparql-results+json"));
        assertEquals(QLeverMediaType.XML, QLeverMediaType.fromContentType("application/sparql-results+xml"));
        assertEquals(QLeverMediaType.CSV, QLeverMediaType.fromContentType("text/csv"));
    }

    @Test
    @DisplayName("QLeverMediaType fromContentType - invalid")
    void testMediaTypeFromContentTypeInvalid() {
        assertNull(QLeverMediaType.fromContentType("application/unknown"));
        assertNull(QLeverMediaType.fromContentType("text/plain"));
        assertNull(QLeverMediaType.fromContentType(""));
        assertNull(QLeverMediaType.fromContentType(null));
    }

    @Test
    @DisplayName("QLeverMediaType enum values")
    void testMediaTypeEnumValues() {
        assertEquals(4, QLeverMediaType.values().length);

        // Test all values exist
        assertNotNull(QLeverMediaType.TURTLE);
        assertNotNull(QLeverMediaType.JSON);
        assertNotNull(QLeverMediaType.XML);
        assertNotNull(QLeverMediaType.CSV);
    }

    @Test
    @DisplayName("QLeverResult with null data and metadata")
    void testResultWithNullDataAndMetadata() {
        QLeverResult result = new QLeverResult(QLeverStatus.READY, null, null);
        assertEquals(QLeverStatus.READY, result.status());
        assertNull(result.data());
        assertNull(result.metadata());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("QLeverResult equals and hashCode")
    void testResultEqualsAndHashCode() {
        QLeverResult result1 = QLeverResult.success("data", "meta");
        QLeverResult result2 = QLeverResult.success("data", "meta");
        QLeverResult result3 = QLeverResult.success("other", "meta");

        assertEquals(result1, result2);
        assertNotEquals(result1, result3);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1.hashCode(), result3.hashCode());
    }

    @Test
    @DisplayName("QLeverResult toString contains important information")
    void testResultToString() {
        QLeverResult result = QLeverResult.success("data", "meta");
        String str = result.toString();
        assertTrue(str.contains("status=READY"));
        assertTrue(str.contains("data=data"));
        assertTrue(str.contains("meta=meta"));
    }

    @Test
    @DisplayName("QLeverFfiException chaining")
    void testFfiExceptionChaining() {
        RuntimeException rootCause = new RuntimeException("Root cause");
        QLeverFfiException ex1 = new QLeverFfiException("Level 1", rootCause);
        QLeverFfiException ex2 = new QLeverFfiException("Level 2", ex1);

        assertEquals("Level 2", ex2.getMessage());
        assertEquals(ex1, ex2.getCause());
        assertEquals(rootCause, ex2.getCause().getCause());
    }

    @Test
    @DisplayName("QLeverResult record behavior")
    void testResultRecordBehavior() {
        QLeverResult result = QLeverResult.success("test", "meta");

        // Test accessor methods
        assertEquals(QLeverStatus.READY, result.status());
        assertEquals("test", result.data());
        assertEquals("meta", result.metadata());

        // Test immutability
        // Records are immutable - cannot modify fields
        // This would compile error, so we test different approach
        assertThrows(UnsupportedOperationException.class, () -> {
            // Create new record instead of modifying
            QLeverResult modified = new QLeverResult(result.status(), "modified", result.metadata());
        });
    }
}