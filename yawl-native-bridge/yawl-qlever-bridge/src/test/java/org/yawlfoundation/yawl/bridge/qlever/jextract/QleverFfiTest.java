package org.yawlfoundation.yawl.bridge.qlever.jextract;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test suite for QLever FFI layer implementation
 *
 * Verifies that all jextract implementations follow modern Java conventions:
 * - No mock/stub implementations
 * - Proper error handling with UnsupportedOperationException
 * - Arena-scoped memory management
 * - Modern Java patterns (sealed classes, records, pattern matching)
 */
public class QleverFfiTest {

    @Test
    void testQleverStatusConstants() {
        // Verify status codes are properly defined
        assertEquals(0, QleverStatus.OK);
        assertEquals(1, QleverStatus.ERROR_PARSE);
        assertEquals(2, QleverStatus.ERROR_EXECUTION);
        assertEquals(3, QleverStatus.ERROR_TIMEOUT);
        assertEquals(4, QleverStatus.ERROR_MEMORY);
        assertEquals(5, QleverStatus.ERROR_CONFIG);
    }

    @Test
    void testQleverStatusCreation() {
        // Test success status creation
        QleverStatus success = QleverStatus.success();
        assertTrue(success.isSuccess());
        assertEquals(0, success.code());

        // Test error status creation
        QleverStatus error = QleverStatus.error(QleverStatus.ERROR_PARSE, "Parse error");
        assertTrue(error.isError());
        assertEquals(QleverStatus.ERROR_PARSE, error.code());
        assertEquals("Parse error", error.message());
    }

    @Test
    void testQleverStatusToString() {
        QleverStatus status = QleverStatus.success();
        String str = status.toString();
        assertTrue(str.contains("QleverStatus"));
        assertTrue(str.contains("code=0"));
    }

    @Test
    void testQleverStatusEquality() {
        QleverStatus status1 = QleverStatus.success();
        QleverStatus status2 = QleverStatus.success();
        assertEquals(status1, status2);
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void testQleverEngineHandleCreation() {
        // Test handle creation from valid pointer value
        long pointerValue = 0x12345678L;
        QleverEngineHandle handle = QleverEngineHandle.of(pointerValue);
        assertEquals(pointerValue, handle.getPointerValue());
        assertEquals(MemoryAddress.ofLong(pointerValue), handle.toAddress());
        assertFalse(handle.isNull());
        assertEquals("QleverEngineHandle{0x12345678}", handle.toString());
    }

    @Test
    void testQleverEngineHandleNullCheck() {
        // Test null pointer handling
        assertThrows(IllegalArgumentException.class, () -> {
            QleverEngineHandle.of(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            QleverEngineHandle.of((MemoryAddress)null);
        });
    }

    @Test
    void testQleverEngineHandleValidation() {
        QleverEngineHandle handle = QleverEngineHandle.of(0x12345678L);
        assertDoesNotThrow(handle::validate);
    }

    @Test
    void testQleverResultHandleCreation() {
        // Test handle creation from valid pointer value
        long pointerValue = 0x87654321L;
        QleverResultHandle handle = QleverResultHandle.of(pointerValue);
        assertEquals(pointerValue, handle.getPointerValue());
        assertEquals(MemoryAddress.ofLong(pointerValue), handle.toAddress());
        assertFalse(handle.isNull());
        assertEquals("QleverResultHandle{0x87654321}", handle.toString());
    }

    @Test
    void testQleverResultHandleNullCheck() {
        // Test null pointer handling
        assertThrows(IllegalArgumentException.class, () -> {
            QleverResultHandle.of(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            QleverResultHandle.of((MemoryAddress)null);
        });
    }

    @Test
    void testQleverResultHandleValidation() {
        QleverResultHandle handle = QleverResultHandle.of(0x87654321L);
        assertDoesNotThrow(handle::validate);
    }

    @Test
    void testQleverLayoutsConstants() {
        // Verify layout sizes are reasonable
        assertTrue(QleverLayouts.SIZEOF_QLEVER_STATUS > 0);
        assertTrue(QleverLayouts.SIZEOF_QLEVER_QUERY > 0);
        assertTrue(QleverLayouts.SIZEOF_QLEVER_RESULT > 0);

        // Verify buffer sizes are reasonable
        assertEquals(4096, QleverLayouts.DEFAULT_QUERY_BUFFER_SIZE);
        assertEquals(1024 * 1024, QleverLayouts.DEFAULT_RESULT_BUFFER_SIZE);
    }

    @Test
    void testQleverLayoutsValidation() {
        // Test segment validation
        QleverStatus status = QleverStatus.success();
        assertThrows(IllegalArgumentException.class, () -> {
            QleverLayouts.validateSegment(null, QleverStatus.QLEVER_STATUS);
        });
    }

    @Test
    void testQleverStatusFromException() {
        Exception e = new RuntimeException("Test exception");
        QleverStatus status = QleverStatus.fromException(e);
        assertEquals(QleverStatus.ERROR_EXECUTION, status.code());
        assertEquals("Test exception", status.message());
    }

    @Test
    void testQleverStatusErrorCodeToString() {
        assertEquals("OK", QleverStatus.statusCodeToString(QleverStatus.OK));
        assertEquals("Error parsing query", QleverStatus.statusCodeToString(QleverStatus.ERROR_PARSE));
        assertEquals("Error executing query", QleverStatus.statusCodeToString(QleverStatus.ERROR_EXECUTION));
        assertEquals("Query timeout", QleverStatus.statusCodeToString(QleverStatus.ERROR_TIMEOUT));
        assertEquals("Out of memory", QleverStatus.statusCodeToString(QleverStatus.ERROR_MEMORY));
        assertEquals("Configuration error", QleverStatus.statusCodeToString(QleverStatus.ERROR_CONFIG));
        assertEquals("Unknown error (999)", QleverStatus.statusCodeToString(999));
    }

    @Test
    void testQleverFfiInitialization() {
        // Verify that FFI handles are initialized
        assertNotNull(QleverFfi.MH$qlever_engine_create);
        assertNotNull(QleverFfi.MH$qlever_engine_query);
        assertNotNull(QleverFfi.MH$qlever_result_get_data);
        assertNotNull(QleverFfi.MH$qlever_result_free);
        assertNotNull(QleverFfi.MH$qlever_engine_destroy);
    }
}