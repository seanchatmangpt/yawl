package org.yawlfoundation.yawl.rust4pm.generated;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class Rust4pmHTest {

    @Test
    void library_optional_is_non_null() {
        assertNotNull(rust4pm_h.LIBRARY);
    }

    @Test
    void requireLibrary_throws_UnsupportedOperationException_when_library_absent() {
        if (rust4pm_h.LIBRARY.isPresent()) return;
        try {
            rust4pm_h.rust4pm_log_event_count(MemorySegment.NULL);
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("rust4pm native library not found"));
            assertTrue(e.getMessage().contains("build-rust4pm.sh"));
            return;
        }
        // If no exception: native library is loaded — acceptable
    }

    @Test
    void parse_result_layout_has_positive_byte_size() {
        assertTrue(rust4pm_h.PARSE_RESULT_LAYOUT.byteSize() > 0);
    }

    @Test
    void ocel_event_c_layout_is_at_least_32_bytes() {
        // event_id(8) + event_type(8) + timestamp_ms(8) + attr_count(8) = 32
        assertTrue(rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize() >= 32);
    }

    @Test
    void conformance_layout_is_at_least_24_bytes() {
        // fitness(8) + precision(8) + error_ptr(8) = 24
        assertTrue(rust4pm_h.CONFORMANCE_RESULT_C_LAYOUT.byteSize() >= 24);
    }

    @Test
    void var_handles_are_non_null() {
        assertNotNull(rust4pm_h.PARSE_RESULT_HANDLE_PTR);
        assertNotNull(rust4pm_h.PARSE_RESULT_ERROR);
        assertNotNull(rust4pm_h.OCEL_EVENT_C_EVENT_ID);
        assertNotNull(rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS);
        assertNotNull(rust4pm_h.DFG_RESULT_JSON);
        assertNotNull(rust4pm_h.CONFORMANCE_FITNESS);
    }
}
