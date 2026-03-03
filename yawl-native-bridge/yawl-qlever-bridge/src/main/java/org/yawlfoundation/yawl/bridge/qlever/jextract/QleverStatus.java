package org.yawlfoundation.yawl.bridge.qlever.jextract;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.GroupLayout;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static jdk.incubator.foreign.MemoryLayout.PathElement;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static jdk.incubator.foreign.ValueLayout.ADDRESS;

/**
 * Represents the QLever status result structure from native operations.
 *
 * This record provides a safe, immutable wrapper around the native QLeverStatus
 * struct with proper memory management and status codes.
 */
public final class QleverStatus {

    // Status codes matching native QLever implementation
    public static final int OK = 0;
    public static final int ERROR_PARSE = 1;
    public static final int ERROR_EXECUTION = 2;
    public static final int ERROR_TIMEOUT = 3;
    public static final int ERROR_MEMORY = 4;
    public static final int ERROR_CONFIG = 5;

    // Memory layout for QLever status struct - consistent with QleverLayouts
    public static final GroupLayout QLEVER_STATUS = MemoryLayout.structLayout(
        C_INT.withName("code"),
        C_INT.withName("padding"), // Alignment padding
        C_POINTER.withName("message"),
        C_POINTER.withName("data")
    ).withName("QleverStatus");

    // Field offsets and var handles for efficient access
    public static final long CODE_OFFSET = 0;
    public static final long MESSAGE_OFFSET = 8; // After int + padding
    public static final long DATA_OFFSET = 16; // After message pointer

    private static final VarHandle VH_CODE = QLEVER_STATUS.varHandle(PathElement.groupElement("code"));
    private static final VarHandle VH_MESSAGE = QLEVER_STATUS.varHandle(PathElement.groupElement("message"));
    private static final VarHandle VH_DATA = QLEVER_STATUS.varHandle(PathElement.groupElement("data"));

    private final MemorySegment segment;
    private final int code;
    private final String message;
    private final MemorySegment data;

    private QleverStatus(MemorySegment segment) {
        if (segment == null || segment.byteSize() != QLEVER_STATUS.byteSize()) {
            throw new IllegalArgumentException("Invalid QLeverStatus memory segment");
        }
        this.segment = segment;
        this.code = (int) VH_CODE.get(segment);
        this.message = readMessage(segment);
        this.data = (MemorySegment) VH_DATA.get(segment);
    }

    /**
     * Creates a QleverStatus from a memory segment
     */
    public static QleverStatus fromSegment(MemorySegment seg) {
        return new QleverStatus(seg);
    }

    /**
     * Creates a successful status with no message
     */
    public static QleverStatus success() {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(QLEVER_STATUS, scope);
            VH_CODE.set(segment, OK);
            VH_MESSAGE.set(segment, MemoryAddress.NULL);
            VH_DATA.set(segment, MemoryAddress.NULL);
            return new QleverStatus(segment);
        }
    }

    /**
     * Creates a successful status with the given data
     */
    public static QleverStatus success(MemorySegment data) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(QLEVER_STATUS, scope);
            VH_CODE.set(segment, OK);
            VH_MESSAGE.set(segment, MemoryAddress.NULL);
            VH_DATA.set(segment, data);
            return new QleverStatus(segment);
        }
    }

    /**
     * Creates an error status with the given code and message
     */
    public static QleverStatus error(int code, String message) {
        if (message == null) {
            throw new IllegalArgumentException(
                "QLeverStatus error message cannot be null. Use a descriptive error message " +
                "or QleverStatus.error(code, \"Error description\")"
            );
        }

        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(QLEVER_STATUS, scope);
            VH_CODE.set(segment, code);

            // Allocate message string in confined scope
            MemorySegment messageSegment = scope.allocateUtf8String(message);
            VH_MESSAGE.set(segment, messageSegment.address());

            VH_DATA.set(segment, MemoryAddress.NULL);
            return new QleverStatus(segment);
        }
    }

    /**
     * Gets the status code
     */
    public int code() {
        return code;
    }

    /**
     * Gets the status message
     */
    public String message() {
        return message;
    }

    /**
     * Gets the associated data segment
     */
    public MemorySegment data() {
        return data;
    }

    /**
     * Checks if the status indicates success
     */
    public boolean isSuccess() {
        return code == OK;
    }

    /**
     * Checks if the status indicates an error
     */
    public boolean isError() {
        return code != OK;
    }

    /**
     * Reads the message from the memory segment
     */
    private String readMessage(MemorySegment segment) {
        MemoryAddress messageAddr = (MemoryAddress) VH_MESSAGE.get(segment);
        if (messageAddr == null || messageAddr.equals(MemoryAddress.NULL)) {
            throw new UnsupportedOperationException(
                "QLeverStatus message is null - this indicates a native implementation error. " +
                "The native code should either allocate a message buffer or return a valid error code."
            );
        }

        // Create a confined scope to read the string
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment messageSegment = messageAddr.asSegment(Long.MAX_VALUE, scope);
            return messageSegment.getUtf8String(0);
        } catch (Exception e) {
            throw new UnsupportedOperationException(
                "Failed to read QLeverStatus message from native memory: " + e.getMessage() + ". " +
                "This may indicate corrupted memory or invalid UTF-8 encoding from native code."
            );
        }
    }

    /**
     * Gets the underlying memory segment for FFI calls
     */
    public MemorySegment getSegment() {
        return segment;
    }

    /**
     * Creates a status from an existing segment
     */
    public static QleverStatus fromSegment(MemorySegment seg) {
        return new QleverStatus(seg);
    }

    /**
     * Gets the segment used by this status
     */
    public MemorySegment segment() {
        return segment;
    }

  
    /**
     * Creates a status from an exception
     *
     * @param e Exception that occurred
     * @return QleverStatus with ERROR_EXECUTION code and exception message
     */
    public static QleverStatus fromException(Exception e) {
        return error(ERROR_EXECUTION, e.getMessage());
    }

    /**
     * Converts status code to human-readable string using exhaustive pattern matching
     */
    public static String statusCodeToString(int code) {
        return switch (code) {
            case OK -> "OK";
            case ERROR_PARSE -> "Error parsing query";
            case ERROR_EXECUTION -> "Error executing query";
            case ERROR_TIMEOUT -> "Query timeout";
            case ERROR_MEMORY -> "Out of memory";
            case ERROR_CONFIG -> "Configuration error";
            default -> "Unknown error (" + code + ")";
        };
    }

    @Override
    public String toString() {
        return "QleverStatus{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", dataSize=" + (data != null ? data.byteSize() : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QleverStatus that = (QleverStatus) o;
        return code == that.code &&
               java.util.Objects.equals(message, that.message) &&
               java.util.Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(code, message, data);
    }
}