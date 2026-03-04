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
import java.util.Optional;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.ADDRESS;

/**
 * Layer 1: Raw jextract bindings for Erlang ei.h interface
 *
 * This class provides direct mappings to the C ei.h functions using jextract.
 * DO NOT modify to add business logic - use Layer 2 (ErlangNode) for that.
 */
public final class EiNative {
    private EiNative() {
        throw new UnsupportedOperationException("EiNative is a utility class - do not instantiate");
    }

    // Function pointers from libei.so
    private static final ValueLayout.FunctionDescriptor ERL_CONNECT_INIT_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* cookie */
            JAVA_INT,     /* creation */
            ADDRESS       /* this Process */
        );

    private static final ValueLayout.FunctionDescriptor ERL_CONNECT_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* this Process */
            ADDRESS       /* addr */
        );

    private static final ValueLayout.FunctionDescriptor ERL_RPC_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* this Process */
            ADDRESS,      /* server node name */
            ADDRESS,      /* module */
            ADDRESS,      /* function */
            JAVA_INT,     /* arity */
            ADDRESS,      /* x arguments */
            ADDRESS,      /* x reply */
            ADDRESS       /* timeout */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_NEW_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            JAVA_INT      /* size */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_FREE_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS       /* x */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_ATOM_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            ADDRESS       /* s */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_TUPLE_HEADER_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            JAVA_INT      /* arity */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_LIST_HEADER_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            JAVA_INT      /* arity */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_BINARY_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            ADDRESS,      /* p */
            JAVA_INT      /* len */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_LIST_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            ADDRESS       /* list */
        );

    private static final ValueLayout.FunctionDescriptor EI_X_ENCODE_TUPLE_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* x */
            ADDRESS       /* tuple */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_ATOM_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* buff */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_TUPLE_HEADER_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* arity */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_LIST_HEADER_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* arity */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_BINARY_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* p */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_TUPLE_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* tuple */
        );

    private static final ValueLayout.FunctionDescriptor EI_DECODE_LIST_FD =
        ValueLayout.JAVA_INT.functionDescriptor(
            ADDRESS,      /* buf */
            ADDRESS,      /* index */
            ADDRESS       /* list */
        );

    // Library handle - lazy loaded to support conditional native availability
    private static volatile Optional<Library> libHandle = Optional.empty();

    private static class Library {
        final Arena arena;
        final MemorySegment erlConnectInit;
        final MemorySegment erlConnect;
        final MemorySegment erlRpc;
        final MemorySegment eiXNew;
        final MemorySegment eiXFree;
        final MemorySegment eiXEncodeAtom;
        final MemorySegment eiXEncodeTupleHeader;
        final MemorySegment eiXEncodeListHeader;
        final MemorySegment eiXEncodeBinary;
        final MemorySegment eiXEncodeList;
        final MemorySegment eiXEncodeTuple;
        final MemorySegment eiDecodeAtom;
        final MemorySegment eiDecodeTupleHeader;
        final MemorySegment eiDecodeListHeader;
        final MemorySegment eiDecodeBinary;
        final MemorySegment eiDecodeTuple;
        final MemorySegment eiDecodeList;

        Library() {
            this.arena = Arena.ofConfined();

            // Resolve library symbols - if not found, will throw UnsatisfiedLinkError
            this.erlConnectInit = arena.findOrThrow("erl_connect_init");
            this.erlConnect = arena.findOrThrow("erl_connect");
            this.erlRpc = arena.findOrThrow("erl_rpc");
            this.eiXNew = arena.findOrThrow("ei_x_new");
            this.eiXFree = arena.findOrThrow("ei_x_free");
            this.eiXEncodeAtom = arena.findOrThrow("ei_x_encode_atom");
            this.eiXEncodeTupleHeader = arena.findOrThrow("ei_x_encode_tuple");
            this.eiXEncodeListHeader = arena.findOrThrow("ei_x_encode_list");
            this.eiXEncodeBinary = arena.findOrThrow("ei_x_encode_binary");
            this.eiXEncodeList = arena.findOrThrow("ei_x_encode_list");
            this.eiXEncodeTuple = arena.findOrThrow("ei_x_encode_tuple");
            this.eiDecodeAtom = arena.findOrThrow("ei_decode_atom");
            this.eiDecodeTupleHeader = arena.findOrThrow("ei_decode_tuple_header");
            this.eiDecodeListHeader = arena.findOrThrow("ei_decode_list_header");
            this.eiDecodeBinary = arena.findOrThrow("ei_decode_binary");
            this.eiDecodeTuple = arena.findOrThrow("ei_decode_tuple");
            this.eiDecodeList = arena.findOrThrow("ei_decode_list");
        }
    }

    /**
     * Initialize ei connection parameters
     *
     * @param cookie the Erlang cookie string
     * @param creation creation number
     * @param thisProcess pointer to current process (NULL for default)
     * @return 0 on success, negative error code on failure
     */
    public static int erlConnectInit(byte[] cookie, int creation, MemorySegment thisProcess) {
        ensureNativeAvailable();

        MemorySegment cookieSeg = MemorySegment.ofArray(cookie);
        return (int) libHandle.get().erlConnectInit.invoke(
            cookieSeg,
            creation,
            thisProcess != null ? thisProcess : MemorySegment.NULL
        );
    }

    /**
     * Connect to an Erlang node
     *
     * @param thisProcess the process handle (from erl_connect_init)
     * @param addr Erlang node address (struct in_addr)
     * @return 0 on success, negative error code on failure
     */
    public static int erlConnect(MemorySegment thisProcess, MemorySegment addr) {
        ensureNativeAvailable();

        return (int) libHandle.get().erlConnect.invoke(
            thisProcess != null ? thisProcess : MemorySegment.NULL,
            addr != null ? addr : MemorySegment.NULL
        );
    }

    /**
     * Execute remote procedure call
     *
     * @param thisProcess the process handle
     * @param serverNode server node name
     * @param module module name
     * @param function function name
     * @param arity number of arguments
     * @param x arguments buffer
     * @param x reply buffer (will be allocated by native code)
     * @param timeout timeout in milliseconds (0 = immediate, -1 = infinity)
     * @return 0 on success, negative error code on failure
     */
    public static int erlRpc(MemorySegment thisProcess,
                            MemorySegment serverNode,
                            MemorySegment module,
                            MemorySegment function,
                            int arity,
                            MemorySegment x,
                            MemorySegment xReply,
                            long timeout) {
        ensureNativeAvailable();

        return (int) libHandle.get().erlRpc.invoke(
            thisProcess != null ? thisProcess : MemorySegment.NULL,
            serverNode != null ? serverNode : MemorySegment.NULL,
            module != null ? module : MemorySegment.NULL,
            function != null ? function : MemorySegment.NULL,
            arity,
            x != null ? x : MemorySegment.NULL,
            xReply != null ? xReply : MemorySegment.NULL,
            timeout
        );
    }

    /**
     * Allocate a new ei_x_buff buffer
     *
     * @param size initial buffer size
     * @return pointer to new buffer, NULL on failure
     */
    public static MemorySegment eiXNew(int size) {
        ensureNativeAvailable();

        return (MemorySegment) libHandle.get().eiXNew.invoke(size);
    }

    /**
     * Free an ei_x_buff buffer
     *
     * @param x pointer to buffer to free
     */
    public static void eiXFree(MemorySegment x) {
        ensureNativeAvailable();

        libHandle.get().eiXFree.invoke(
            x != null ? x : MemorySegment.NULL
        );
    }

    /**
     * Encode an atom into ei buffer
     *
     * @param x output buffer
     * @param s atom string (null-terminated)
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeAtom(MemorySegment x, byte[] s) {
        ensureNativeAvailable();

        MemorySegment sSeg = MemorySegment.ofArray(s);
        return (int) libHandle.get().eiXEncodeAtom.invoke(
            x != null ? x : MemorySegment.NULL,
            sSeg
        );
    }

    /**
     * Encode tuple header into ei buffer
     *
     * @param x output buffer
     * @param arity number of elements in tuple
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeTupleHeader(MemorySegment x, int arity) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiXEncodeTupleHeader.invoke(
            x != null ? x : MemorySegment.NULL,
            arity
        );
    }

    /**
     * Encode list header into ei buffer
     *
     * @param x output buffer
     * @param arity number of elements in list
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeListHeader(MemorySegment x, int arity) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiXEncodeListHeader.invoke(
            x != null ? x : MemorySegment.NULL,
            arity
        );
    }

    /**
     * Encode binary data into ei buffer
     *
     * @param x output buffer
     * @param p binary data to encode
     * @param len length of binary data
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeBinary(MemorySegment x, byte[] p, int len) {
        ensureNativeAvailable();

        MemorySegment pSeg = MemorySegment.ofArray(p);
        return (int) libHandle.get().eiXEncodeBinary.invoke(
            x != null ? x : MemorySegment.NULL,
            pSeg,
            len
        );
    }

    /**
     * Encode list into ei buffer (full list, not just header)
     *
     * @param x output buffer
     * @param list list to encode (struct or memory segment)
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeList(MemorySegment x, MemorySegment list) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiXEncodeList.invoke(
            x != null ? x : MemorySegment.NULL,
            list != null ? list : MemorySegment.NULL
        );
    }

    /**
     * Encode tuple into ei buffer (full tuple, not just header)
     *
     * @param x output buffer
     * @param tuple tuple to encode (struct or memory segment)
     * @return 0 on success, negative error code on failure
     */
    public static int eiXEncodeTuple(MemorySegment x, MemorySegment tuple) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiXEncodeTuple.invoke(
            x != null ? x : MemorySegment.NULL,
            tuple != null ? tuple : MemorySegment.NULL
        );
    }

    /**
     * Decode atom from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param atom buffer to store decoded atom (must be large enough)
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeAtom(MemorySegment buf, MemorySegment index, MemorySegment atom) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeAtom.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            atom != null ? atom : MemorySegment.NULL
        );
    }

    /**
     * Decode tuple header from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param arity pointer to store decoded arity
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeTupleHeader(MemorySegment buf, MemorySegment index, MemorySegment arity) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeTupleHeader.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            arity != null ? arity : MemorySegment.NULL
        );
    }

    /**
     * Decode list header from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param arity pointer to store decoded arity
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeListHeader(MemorySegment buf, MemorySegment index, MemorySegment arity) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeListHeader.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            arity != null ? arity : MemorySegment.NULL
        );
    }

    /**
     * Decode binary data from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param p buffer to store decoded binary data
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeBinary(MemorySegment buf, MemorySegment index, MemorySegment p) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeBinary.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            p != null ? p : MemorySegment.NULL
        );
    }

    /**
     * Decode tuple from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param tuple buffer to store decoded tuple
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeTuple(MemorySegment buf, MemorySegment index, MemorySegment tuple) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeTuple.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            tuple != null ? tuple : MemorySegment.NULL
        );
    }

    /**
     * Decode list from ei buffer
     *
     * @param buf input buffer
     * @param index pointer to current index (will be updated)
     * @param list buffer to store decoded list
     * @return 0 on success, negative error code on failure
     */
    public static int eiDecodeList(MemorySegment buf, MemorySegment index, MemorySegment list) {
        ensureNativeAvailable();

        return (int) libHandle.get().eiDecodeList.invoke(
            buf != null ? buf : MemorySegment.NULL,
            index != null ? index : MemorySegment.NULL,
            list != null ? list : MemorySegment.NULL
        );
    }

    /**
     * Ensure native library is available and loaded
     *
     * @throws UnsupportedOperationException if native code is not available
     */
    private static void ensureNativeAvailable() {
        if (libHandle.isEmpty()) {
            try {
                // Try to load the library
                Library lib = new Library();
                libHandle = Optional.of(lib);
            } catch (UnsatisfiedLinkError e) {
                throw new UnsupportedOperationException(
                    "Erlang ei native library not available. " +
                    "Install libei.so and ensure it's in library path. " +
                    "Error: " + e.getMessage(),
                    e
                );
            }
        }
    }
}