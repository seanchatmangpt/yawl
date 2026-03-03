/*
 * YAWL ERP Systems
 * Copyright (c) 2024 MinCorp Pte Ltd
 *
 * This file is part of YAWL ERP System.
 *
 * YAWL ERP System is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL ERP System is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL ERP System. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.bridge.erlang.jextract;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * MemoryLayout definitions for Erlang ei structures
 *
 * These layouts are derived from the C struct definitions in ei.h.
 * They provide the memory layout information needed for interoperability
 * between Java and C code when passing complex data structures.
 */
public final class EiLayout {
    private EiLayout() {
        throw new UnsupportedOperationException("EiLayout is a utility class - do not instantiate");
    }

    /**
     * ei_x_buff struct layout (from ei.h)
     *
     * typedef struct {
     *     uint32_t index;  // Current position in buffer
     *     uint32_t size;   // Size of buffer
     *     uint8_t* buff;   // Pointer to buffer
     * } ei_x_buff_t;
     */
    public static final MemoryLayout EI_X_BUFF_LAYOUT = MemoryLayout.structLayout(
        // uint32_t index - current position in buffer
        ValueLayout.JAVA_INT.withName("index"),

        // uint32_t size - size of buffer
        ValueLayout.JAVA_INT.withName("size"),

        // uint8_t* buff - pointer to buffer
        ValueLayout.ADDRESS.withName("buff")
    ).withName("ei_x_buff_t");

    // Field offsets within ei_x_buff
    public static final long INDEX_OFFSET = 0;
    public static final long SIZE_OFFSET = INDEX_OFFSET + ValueLayout.JAVA_INT.byteSize();
    public static final long BUFF_OFFSET = SIZE_OFFSET + ValueLayout.JAVA_INT.byteSize();

    /**
     * ei_cnode_t struct layout (from ei.h)
     *
     * typedef struct {
     *     uint16_t creation;
     *     uint8_t hidden;
     *     uint16_t alive;
     *     uint8_t* port;
     *     uint8_t* hostent;
     *     uint8_t* distribution;
     *     uint8_t* status;
     * } ei_cnode_t;
     */
    public static final MemoryLayout EI_CNODE_LAYOUT = MemoryLayout.structLayout(
        // uint16_t creation
        ValueLayout.JAVA_SHORT.withName("creation"),

        // uint8_t hidden
        ValueLayout.JAVA_BYTE.withName("hidden"),

        // uint16_t alive
        ValueLayout.JAVA_SHORT.withName("alive"),

        // uint8_t* port
        ValueLayout.ADDRESS.withName("port"),

        // uint8_t* hostent
        ValueLayout.ADDRESS.withName("hostent"),

        // uint8_t* distribution
        ValueLayout.ADDRESS.withName("distribution"),

        // uint8_t* status
        ValueLayout.ADDRESS.withName("status")
    ).withName("ei_cnode_t");

    // Field offsets within ei_cnode_t
    public static final long CREATION_OFFSET = 0;
    public static final long HIDDEN_OFFSET = CREATION_OFFSET + ValueLayout.JAVA_SHORT.byteSize();
    public static final long ALIVE_OFFSET = HIDDEN_OFFSET + ValueLayout.JAVA_BYTE.byteSize();
    public static final long PORT_OFFSET = ALIVE_OFFSET + ValueLayout.JAVA_SHORT.byteSize();
    public static final long HOSTENT_OFFSET = PORT_OFFSET + ValueLayout.ADDRESS.byteSize();
    public static final long DISTRIBUTION_OFFSET = HOSTENT_OFFSET + ValueLayout.ADDRESS.byteSize();
    public static final long STATUS_OFFSET = DISTRIBUTION_OFFSET + ValueLayout.ADDRESS.byteSize();

    /**
     * ErlConnect struct layout (from erl_connect.h)
     *
     * struct in_addr {
     *     unsigned long s_addr;
     * };
     *
     * typedef struct {
     *     char nodename[MAXATOMLEN];
     *     unsigned char creation;
     *     struct in_addr addr;
     *     int fd;
     * } ErlConnect;
     */
    public static final MemoryLayout ERL_CONNECT_LAYOUT = MemoryLayout.structLayout(
        // char nodename[MAXATOMLEN]
        ValueLayout.JAVA_BYTE.arrayLayout(EiConstants.MAXATOMLEN).withName("nodename"),

        // unsigned char creation
        ValueLayout.JAVA_BYTE.withName("creation"),

        // struct in_addr addr
        MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("s_addr")
        ).withName("addr"),

        // int fd
        ValueLayout.JAVA_INT.withName("fd")
    ).withName("ErlConnect");

    // Field offsets within ErlConnect
    public static final long NODENAME_OFFSET = 0;
    public static final long ERLLIB_CREATION_OFFSET = NODENAME_OFFSET + (EiConstants.MAXATOMLEN * ValueLayout.JAVA_BYTE.byteSize());
    public static final long ADDR_OFFSET = ERLLIB_CREATION_OFFSET + ValueLayout.JAVA_BYTE.byteSize();
    public static final long FD_OFFSET = ADDR_OFFSET + MemoryLayout.structLayout(ValueLayout.JAVA_LONG).byteSize();

    // ErlConnect in_addr sub-layouts
    public static final MemoryLayout IN_ADDR_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("s_addr")
    );

    /**
     * Common element layouts
     */
    public static final ValueLayout C_CHAR = ValueLayout.JAVA_BYTE;
    public static final ValueLayout C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout C_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout C_VOID_P = ValueLayout.ADDRESS;

    /**
     * Layout for atom string (null-terminated)
     */
    public static final MemoryLayout ATOM_STRING_LAYOUT = MemoryLayout.sequenceLayout(
        EiConstants.MAXATOMLEN + 1, // +1 for null terminator
        ValueLayout.JAVA_BYTE
    );

    /**
     * Layout for Erlang binary data
     */
    public static final MemoryLayout ERL_BINARY_LAYOUT = MemoryLayout.sequenceLayout(
        ValueLayout.JAVA_INT.withName("length"),
        ValueLayout.JAVA_BYTE
    );

    /**
     * Layout for Erlang tuple
     */
    public static final MemoryLayout ERL_TUPLE_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("tag"),        // SMALL_TUPLE_EXT or LARGE_TUPLE_EXT
        ValueLayout.JAVA_INT.withName("arity")        // Number of elements
    );

    /**
     * Layout for Erlang list
     */
    public static final MemoryLayout ERL_LIST_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("tag"),        // LIST_EXT
        ValueLayout.JAVA_INT.withName("arity"),       // Number of elements
        ValueLayout.JAVA_BYTE.withName("nil_tag")     // NIL_EXT at end
    );

    /**
     * Layout for Erlang string
     */
    public static final MemoryLayout ERL_STRING_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("tag"),        // STRING_EXT
        ValueLayout.JAVA_SHORT.withName("length"),    // String length (16-bit)
        ValueLayout.JAVA_BYTE.arrayLayout(
            ValueLayout.JAVA_SHORT.withName("length")
        ).withName("data")                             // String data
    );

    /**
     * Layout for Erlang integer
     */
    public static final MemoryLayout ERL_INTEGER_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("tag"),        // INTEGER_EXT
        ValueLayout.JAVA_INT.withName("value")        // Integer value
    );

    /**
     * Helper methods to create memory segments from layouts
     */
    public static MemorySegment createSegment(MemoryLayout layout, Arena arena) {
        return arena.allocate(layout);
    }

    public static MemorySegment createSegment(MemoryLayout layout, Arena arena, Object... values) {
        MemorySegment segment = arena.allocate(layout);

        // Write values to segment using appropriate layout paths
        int offset = 0;
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Byte byteVal) {
                segment.set(ValueLayout.JAVA_BYTE, offset, byteVal);
            } else if (value instanceof Integer intVal) {
                segment.set(ValueLayout.JAVA_INT, offset, intVal);
            } else if (value instanceof Long longVal) {
                segment.set(ValueLayout.JAVA_LONG, offset, longVal);
            } else if (value instanceof Short shortVal) {
                segment.set(ValueLayout.JAVA_SHORT, offset, shortVal);
            } else if (value instanceof MemorySegment memSeg) {
                MemoryLayout segLayout = MemoryLayout.sequenceLayout(memSeg.byteSize(), ValueLayout.JAVA_BYTE);
                MemorySegment.copy(memSeg, 0, segment, offset, memSeg.byteSize());
            } else {
                throw new UnsupportedOperationException(
                    "Unsupported value type: " + value.getClass().getName() +
                    " at position " + i + ". Use Byte, Integer, Long, Short, or MemorySegment only."
                );
            }
            offset += getByteSize(value);
        }

        return segment;
    }

    /**
     * Get byte size of Java primitive types
     */
    private static int getByteSize(Object value) {
        if (value instanceof Byte) return Byte.BYTES;
        if (value instanceof Integer) return Integer.BYTES;
        if (value instanceof Long) return Long.BYTES;
        if (value instanceof Short) return Short.BYTES;
        if (value instanceof MemorySegment) return ((MemorySegment) value).byteSize();
        throw new UnsupportedOperationException("Unsupported value type: " + value.getClass());
    }

    /**
     * Validate memory segment size against layout
     *
     * @param segment the memory segment to validate
     * @param layout the expected layout
     * @throws IllegalArgumentException if segment doesn't match layout size
     */
    public static void validateSegment(MemorySegment segment, MemoryLayout layout) {
        if (segment.byteSize() != layout.byteSize()) {
            throw new IllegalArgumentException(
                String.format("Segment size %d does not match layout size %d",
                    segment.byteSize(), layout.byteSize())
            );
        }
    }
}