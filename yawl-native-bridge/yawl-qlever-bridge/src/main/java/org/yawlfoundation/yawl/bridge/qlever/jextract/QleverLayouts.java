package org.yawlfoundation.yawl.bridge.qlever.jextract;

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;

/**
 * Memory layouts for QLever C++ FFI structures
 *
 * This class defines the MemoryLayout structures that match the native
 * C++ structs used by QLever. These layouts are used by jextract-generated
 * code to properly map between Java and native memory.
 */
public final class QleverLayouts {

    // Basic value layouts for FFI
    public static final ValueLayout C_CHAR = ValueLayout.JAVA_BYTE;
    public static final ValueLayout C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout C_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout C_POINTER = ValueLayout.ADDRESS;

    // QLever status structure layout - matches QleverStatus.java
    public static final GroupLayout QLEVER_STATUS = MemoryLayout.structLayout(
        C_INT.withName("code"),                           // int32_t code
        C_INT.withName("padding"),                        // alignment padding
        C_POINTER.withName("message"),                    // const char* message
        C_POINTER.withName("data")                         // void* data
    ).withName("QleverStatus");

    // QLever query structure layout
    public static final GroupLayout QLEVER_QUERY = MemoryLayout.structLayout(
        C_POINTER.withName("query_string"),              // const char* query_string
        C_LONG.withName("query_length"),                 // size_t query_length
        C_LONG.withName("result_limit"),                 // size_t result_limit
        C_POINTER.withName("engine_handle")              // void* engine_handle
    ).withName("QleverQuery");

    // QLever result structure layout
    public static final GroupLayout QLEVER_RESULT = MemoryLayout.structLayout(
        C_POINTER.withName("result_data"),               // const char* result_data
        C_LONG.withName("result_size"),                  // size_t result_size
        C_INT.withName("error_code"),                    // int32_t error_code
        C_POINTER.withName("error_message")              // const char* error_message
    ).withName("QleverResult");

    // Common size constants
    public static final long SIZEOF_QLEVER_STATUS = QLEVER_STATUS.byteSize();
    public static final long SIZEOF_QLEVER_QUERY = QLEVER_QUERY.byteSize();
    public static final long SIZEOF_QLEVER_RESULT = QLEVER_RESULT.byteSize();

    // Field offsets for QLEVER_STATUS
    public static final long QLEVER_STATUS_CODE_OFFSET = 0;
    public static final long QLEVER_STATUS_MESSAGE_OFFSET = 8;  // After int + padding
    public static final long QLEVER_STATUS_DATA_OFFSET = 16;  // After message pointer

    // Field offsets for QLEVER_QUERY
    public static final long QLEVER_QUERY_STRING_OFFSET = 0;
    public static final long QLEVER_QUERY_LENGTH_OFFSET = 8;
    public static final long QLEVER_QUERY_LIMIT_OFFSET = 16;
    public static final long QLEVER_QUERY_ENGINE_OFFSET = 24;

    // Field offsets for QLEVER_RESULT
    public static final long QLEVER_RESULT_DATA_OFFSET = 0;
    public static final long QLEVER_RESULT_SIZE_OFFSET = 8;
    public static final long QLEVER_RESULT_ERROR_CODE_OFFSET = 16;
    public static final long QLEVER_RESULT_ERROR_MESSAGE_OFFSET = 24;

    // Default query buffer sizes
    public static final long DEFAULT_QUERY_BUFFER_SIZE = 4096;
    public static final long DEFAULT_RESULT_BUFFER_SIZE = 1024 * 1024; // 1MB

    // Character array layout for query strings
    public static final SequenceLayout QUERY_STRING_LAYOUT =
        MemoryLayout.sequenceLayout(DEFAULT_QUERY_BUFFER_SIZE, C_CHAR);

    // Character array layout for result strings
    public static final SequenceLayout RESULT_STRING_LAYOUT =
        MemoryLayout.sequenceLayout(DEFAULT_RESULT_BUFFER_SIZE, C_CHAR);

    // Private constructor to prevent instantiation
    private QleverLayouts() {}

    /**
     * Creates a padded structure to ensure proper alignment
     *
     * @param layout Original memory layout
     * @param alignment Required alignment in bytes
     * @return Padded layout with proper alignment
     */
    public static GroupLayout alignLayout(GroupLayout layout, long alignment) {
        long padding = (alignment - (layout.byteSize() % alignment)) % alignment;
        return MemoryLayout.structLayout(
            layout,
            MemoryLayout.paddingLayout(padding)
        );
    }

    /**
     * Creates a layout for error message buffers
     *
     * @param maxMessageSize Maximum message size in bytes
     * @return Sequence layout for null-terminated strings
     */
    public static SequenceLayout errorMessageLayout(long maxMessageSize) {
        return MemoryLayout.sequenceLayout(maxMessageSize + 1, C_CHAR);
    }

    /**
     * Validates that a memory segment matches the expected layout
     *
     * @param segment Memory segment to validate
     * @param layout Expected layout
     * @throws IllegalArgumentException if segment doesn't match layout
     */
    public static void validateSegment(MemorySegment segment, GroupLayout layout) {
        if (segment == null || segment.byteSize() != layout.byteSize()) {
            throw new IllegalArgumentException(
                String.format("Memory segment size (%d) does not match layout size (%d)",
                    segment != null ? segment.byteSize() : 0, layout.byteSize())
            );
        }
    }
}