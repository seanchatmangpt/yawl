package org.yawlfoundation.yawl.bridge.qlever.native;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QLeverNativeBridge
 */
class QleverNativeBridgeTest {

    @Test
    void testQleverStatusCreation() {
        // Test success status
        QleverStatus success = QleverStatus.SUCCESS;
        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());
        assertEquals(0, success.code());
        assertEquals("Success", success.message());

        // Test error status
        QleverStatus error = new QleverStatus(1, "Test error");
        assertFalse(error.isSuccess());
        assertTrue(error.isFailure());
        assertEquals(1, error.code());
        assertEquals("Test error", error.message());
    }

    @Test
    void testQleverStatusFromJextract() {
        // Create a mock jextract status (would be real in practice)
        org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();

        QleverStatus status = QleverStatus.fromJextract(jextractStatus);
        assertNotNull(status);
        assertTrue(status.isSuccess());
    }

    @Test
    void testQleverExceptionCreation() {
        // Test with status
        QleverStatus errorStatus = new QleverStatus(1, "Test error");
        QleverException exception = new QleverException(errorStatus);

        assertEquals(errorStatus, exception.getStatus());
        assertEquals(1, exception.getErrorCode());
        assertEquals("Test error", exception.getErrorMessage());
        assertTrue(exception.getMessage().contains("Test error"));
    }

    @Test
    void testQleverExceptionErrorTypeChecks() {
        QleverException exception = new QleverException(
            new QleverStatus(QleverStatus.ERROR_QUERY_PARSE_FAILED, "Parse error")
        );

        assertTrue(exception.isOfType(QleverStatus.ERROR_QUERY_PARSE_FAILED));
        assertTrue(exception.isQueryParseError());
        assertFalse(exception.isQueryExecutionError());
        assertFalse(exception.isEngineInitError());
    }

    @Test
    void testQleverExceptionUserFriendlyMessage() {
        QleverException exception = new QleverException(
            new QleverStatus(QleverStatus.ERROR_ENGINE_INIT_FAILED, "Engine init failed")
        );

        String userMessage = exception.getUserFriendlyMessage();
        assertNotNull(userMessage);
        assertTrue(userMessage.contains("initialize QLever engine"));
    }

    @Test
    void testNativeHandleCreation() {
        // Test creating different types of handles
        QleverStatus testStatus = new QleverStatus(0, "Test");

        // Test confineed arena handle
        NativeHandle<QleverStatus> confinedHandle = NativeHandle.createConfined(testStatus);
        assertEquals(testStatus, confinedHandle.get());
        assertFalse(confinedHandle.isClosed());

        // Test shared arena handle
        NativeHandle<QleverStatus> sharedHandle = NativeHandle.createShared(testStatus);
        assertEquals(testStatus, sharedHandle.get());
        assertFalse(sharedHandle.isClosed());

        // Test global handle
        NativeHandle<QleverStatus> globalHandle = NativeHandle.createGlobal(testStatus);
        assertEquals(testStatus, globalHandle.get());
        assertFalse(globalHandle.isClosed());
    }

    @Test
    void testNativeHandleFunctionExecution() {
        QleverStatus status = new QleverStatus(0, "Test");

        // Test with function execution
        NativeHandle<QleverStatus> handle = NativeHandle.createConfined(status);
        String result = handle.withFunction(
            s -> s.message() + " processed",
            false
        );
        assertEquals("Test processed", result);
        assertFalse(handle.isClosed());
    }

    @Test
    void testNativeHandleConsumerExecution() {
        QleverStatus status = new QleverStatus(0, "Test");
        StringBuilder buffer = new StringBuilder();

        // Test with consumer execution
        NativeHandle<QleverStatus> handle = NativeHandle.createConfined(status);
        handle.withConsumer(
            s -> buffer.append(s.message()),
            false
        );
        assertEquals("Test", buffer.toString());
        assertFalse(handle.isClosed());
    }

    @Test
    void testNativeHandleClose() {
        QleverStatus status = new QleverStatus(0, "Test");

        NativeHandle<QleverStatus> handle = NativeHandle.createConfined(status);
        assertFalse(handle.isClosed());

        handle.close();
        assertTrue(handle.isClosed());

        // Verify that accessing closed handle throws exception
        assertThrows(IllegalStateException.class, () -> handle.get());
    }

    @Test
    void testQleverExceptionToString() {
        QleverException exception = new QleverException(
            new QleverStatus(1, "Test error")
        );

        String toString = exception.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("QLever Exception"));
        assertTrue(toString.contains("1"));
        assertTrue(toString.contains("Test error"));
    }

    @Test
    void testQleverStatusToString() {
        QleverStatus status = new QleverStatus(1, "Test error");
        String toString = status.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Error 1: Test error"));

        QleverStatus success = QleverStatus.SUCCESS;
        toString = success.toString();
        assertEquals("Operation succeeded", toString);
    }

    @Test
    void testQleverStatusEquality() {
        QleverStatus status1 = new QleverStatus(1, "Error");
        QleverStatus status2 = new QleverStatus(1, "Error");
        QleverStatus status3 = new QleverStatus(2, "Error");

        assertEquals(status1, status2);
        assertNotEquals(status1, status3);
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void testQleverStatusDetailedMessage() {
        QleverStatus success = QleverStatus.SUCCESS;
        assertEquals("Operation succeeded", success.getDetailedMessage());

        QleverStatus error = new QleverStatus(1, "Something went wrong");
        assertEquals("Error 1: Something went wrong", error.getDetailedMessage());
    }
}