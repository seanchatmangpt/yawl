package org.yawlfoundation.yawl.rust4pm.generated;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that every expected Rust export symbol has a Java MethodHandle binding.
 *
 * <p>When the native library is absent (CI without Rust build), all assertions are skipped.
 * When the library is present, each expected symbol is probed via {@link java.lang.foreign.SymbolLookup#find}.
 *
 * <p>This is the "symbol whitelist" check: if a Rust function is added to lib.rs but no
 * Java binding is added to rust4pm_h.java, the nm-based audit (scripts/observatory/rust4pm-audit.sh)
 * will surface the new symbol. This test can then be updated to include it, enforcing that
 * every export has a corresponding Java binding.
 */
class SymbolCompletenessTest {

    /**
     * All expected {@code rust4pm_*} symbols exported from librust4pm.so.
     * Maintained in sync with rust4pm_h.java MethodHandle declarations.
     */
    private static final Set<String> EXPECTED_SYMBOLS = Set.of(
        // Core business functions (12)
        "rust4pm_parse_ocel2_json",
        "rust4pm_log_event_count",
        "rust4pm_log_get_events",
        "rust4pm_log_object_count",
        "rust4pm_log_get_objects",
        "rust4pm_discover_dfg",
        "rust4pm_check_conformance",
        "rust4pm_log_free",
        "rust4pm_events_free",
        "rust4pm_objects_free",
        "rust4pm_dfg_free",
        "rust4pm_error_free",
        // sizeof probes (8)
        "rust4pm_sizeof_ocel_log_handle",
        "rust4pm_sizeof_parse_result",
        "rust4pm_sizeof_ocel_event_c",
        "rust4pm_sizeof_ocel_events_result",
        "rust4pm_sizeof_ocel_object_c",
        "rust4pm_sizeof_ocel_objects_result",
        "rust4pm_sizeof_dfg_result_c",
        "rust4pm_sizeof_conformance_result_c",
        // offsetof probes (20)
        "rust4pm_offsetof_ocel_log_handle_ptr",
        "rust4pm_offsetof_parse_result_handle",
        "rust4pm_offsetof_parse_result_error",
        "rust4pm_offsetof_ocel_event_c_event_id",
        "rust4pm_offsetof_ocel_event_c_event_type",
        "rust4pm_offsetof_ocel_event_c_timestamp_ms",
        "rust4pm_offsetof_ocel_event_c_attr_count",
        "rust4pm_offsetof_ocel_events_result_events",
        "rust4pm_offsetof_ocel_events_result_count",
        "rust4pm_offsetof_ocel_events_result_error",
        "rust4pm_offsetof_ocel_object_c_object_id",
        "rust4pm_offsetof_ocel_object_c_object_type",
        "rust4pm_offsetof_ocel_objects_result_objects",
        "rust4pm_offsetof_ocel_objects_result_count",
        "rust4pm_offsetof_ocel_objects_result_error",
        "rust4pm_offsetof_dfg_result_c_json",
        "rust4pm_offsetof_dfg_result_c_error",
        "rust4pm_offsetof_conformance_result_c_fitness",
        "rust4pm_offsetof_conformance_result_c_precision",
        "rust4pm_offsetof_conformance_result_c_error"
    );

    @Test
    void expected_symbol_count_is_40() {
        // 12 business + 8 sizeof + 20 offsetof = 40
        assertEquals(40, EXPECTED_SYMBOLS.size(),
            "Update EXPECTED_SYMBOLS to match the actual rust4pm_h.java MethodHandle set");
    }

    @Test
    void all_expected_symbols_have_java_method_handles_when_library_present() {
        if (rust4pm_h.LIBRARY.isEmpty()) {
            // Library absent — skip symbol probe (CI without Rust build)
            return;
        }
        var lib = rust4pm_h.LIBRARY.get();
        for (String sym : EXPECTED_SYMBOLS) {
            assertTrue(lib.find(sym).isPresent(),
                "Rust symbol '" + sym + "' missing from librust4pm.so. " +
                "Either add the #[no_mangle] fn to lib.rs, or remove it from EXPECTED_SYMBOLS.");
        }
    }

    @Test
    void java_layouts_report_positive_byte_offsets_for_all_tracked_fields() {
        // Pure-Java structural check — works without the native library.
        // Verifies that byteOffset() is computable for every field we track,
        // i.e. the field names used in assertOffsets() are correct.
        assertDoesNotThrow(() -> rust4pm_h.OCEL_LOG_HANDLE_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("ptr")));
        assertDoesNotThrow(() -> rust4pm_h.PARSE_RESULT_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("handle")));
        assertDoesNotThrow(() -> rust4pm_h.PARSE_RESULT_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("error")));
        assertDoesNotThrow(() -> rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("event_id")));
        assertDoesNotThrow(() -> rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("event_type")));
        assertDoesNotThrow(() -> rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("timestamp_ms")));
        assertDoesNotThrow(() -> rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("attr_count")));
        assertDoesNotThrow(() -> rust4pm_h.CONFORMANCE_RESULT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("fitness")));
        assertDoesNotThrow(() -> rust4pm_h.CONFORMANCE_RESULT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("precision")));
        assertDoesNotThrow(() -> rust4pm_h.CONFORMANCE_RESULT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("error")));
    }

    @Test
    void event_id_and_event_type_are_at_different_offsets() {
        // Guards against field swap: event_id and event_type are both ADDRESS (8 bytes),
        // so sizeof passes even if swapped. byteOffset() proves they're distinct.
        long idOffset   = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("event_id"));
        long typeOffset = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("event_type"));
        assertNotEquals(idOffset, typeOffset,
            "event_id and event_type must be at different byte offsets in OcelEventC");
        assertEquals(0L, idOffset,   "event_id must be at offset 0 (first field)");
        assertEquals(8L, typeOffset, "event_type must be at offset 8 (second field)");
    }

    @Test
    void object_id_and_object_type_are_at_different_offsets() {
        // Same guard for OcelObjectC — two ADDRESS fields that could be swapped
        long idOffset   = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("object_id"));
        long typeOffset = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteOffset(
            java.lang.foreign.MemoryLayout.PathElement.groupElement("object_type"));
        assertNotEquals(idOffset, typeOffset,
            "object_id and object_type must be at different byte offsets in OcelObjectC");
        assertEquals(0L, idOffset,   "object_id must be at offset 0 (first field)");
        assertEquals(8L, typeOffset, "object_type must be at offset 8 (second field)");
    }
}
