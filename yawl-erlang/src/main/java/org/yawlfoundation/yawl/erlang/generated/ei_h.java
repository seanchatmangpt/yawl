/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.generated;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Hand-written Panama FFM bindings for {@code libei.so} (Erlang/OTP 28 erl_interface).
 * Simulates {@code jextract} output from OTP 28.3.1 {@code ei.h}.
 *
 * <p>All operations gracefully degrade when {@code libei.so} is not loaded.
 * Set {@code -Derlang.library.path=/path/to/libei.so} to enable native operations.
 * Run {@code bash scripts/build-erlang.sh} to discover the correct path for OTP 28.3.1.</p>
 *
 * <h3>Struct layouts</h3>
 * <ul>
 *   <li>{@code ei_x_buff} — 16 bytes: {char* buff, int index, int buffsz}</li>
 *   <li>{@code erlang_msg} — 64 bytes opaque: msgtype + from/to pids + serial</li>
 *   <li>{@code ei_cnode} — 240 bytes opaque blob (OTP internal)</li>
 * </ul>
 *
 * @see <a href="https://www.erlang.org/doc/man/ei.html">ei.h documentation (OTP 28)</a>
 */
public final class ei_h {

    private ei_h() {}

    // -----------------------------------------------------------------------
    // Library loading — optional, graceful degradation when absent
    // -----------------------------------------------------------------------

    /**
     * System property for the absolute path to {@code libei.so}.
     * Example: {@code -Derlang.library.path=/opt/otp-28/lib/erl_interface-5.5/lib/libei.so}
     */
    public static final String LIB_PATH_PROP = "erlang.library.path";

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIB_ARENA = Arena.ofAuto();

    /**
     * Loaded {@code libei.so} symbol table. Empty when the library path was not provided
     * or the library failed to load. All public methods guard on this field.
     */
    public static final Optional<SymbolLookup> LIBRARY;

    static {
        String path = System.getProperty(LIB_PATH_PROP);
        Optional<SymbolLookup> lib = Optional.empty();
        if (path != null && !path.isBlank()) {
            try {
                lib = Optional.of(SymbolLookup.libraryLookup(Path.of(path), LIB_ARENA));
            } catch (Exception e) {
                // libei.so not loadable at the specified path — graceful degradation
            }
        }
        LIBRARY = lib;
    }

    // -----------------------------------------------------------------------
    // ETF version + tag constants (from ei.h / erts/emulator/beam/external.h)
    // -----------------------------------------------------------------------

    /** External Term Format version magic byte (always 131 = 0x83). */
    public static final byte ERL_VERSION_MAGIC = (byte) 131;

    /** ETF tag: UTF-8 atom (OTP 26+ default). */
    public static final int ERL_ATOM_UTF8_EXT        = 118;
    /** ETF tag: small UTF-8 atom (≤255 bytes). */
    public static final int ERL_SMALL_ATOM_UTF8_EXT  = 119;
    /** ETF tag: small integer 0..255. */
    public static final int ERL_SMALL_INTEGER_EXT    = 97;
    /** ETF tag: signed 32-bit integer. */
    public static final int ERL_INTEGER_EXT          = 98;
    /** ETF tag: IEEE 754 double (8 bytes, big-endian). */
    public static final int NEW_FLOAT_EXT            = 70;
    /** ETF tag: binary (4-byte length + bytes). */
    public static final int BINARY_EXT               = 109;
    /** ETF tag: bitstring (partial last byte). */
    public static final int BIT_BINARY_EXT           = 77;
    /** ETF tag: list with arity + tail. */
    public static final int LIST_EXT                 = 108;
    /** ETF tag: empty list (nil). */
    public static final int NIL_EXT                  = 106;
    /** ETF tag: tuple arity ≤255. */
    public static final int SMALL_TUPLE_EXT          = 104;
    /** ETF tag: tuple arity >255. */
    public static final int LARGE_TUPLE_EXT          = 105;
    /** ETF tag: map (key-value pairs). */
    public static final int MAP_EXT                  = 116;
    /** ETF tag: new-style PID (OTP 23+). */
    public static final int NEW_PID_EXT              = 88;
    /** ETF tag: newer reference (OTP 23+). */
    public static final int NEWER_REFERENCE_EXT      = 90;
    /** ETF tag: v4 port (OTP 26+). */
    public static final int V4_PORT_EXT              = 120;
    /** ETF tag: exported function (Module:Function/Arity). */
    public static final int EXPORT_EXT               = 113;
    /** ETF tag: lambda/closure. */
    public static final int NEW_FUN_EXT              = 112;
    /** ETF tag: big integer ≤255 digits. */
    public static final int SMALL_BIG_EXT            = 110;
    /** ETF tag: big integer >255 digits. */
    public static final int LARGE_BIG_EXT            = 111;

    /** Return code from {@link #ei_xreceive_msg}: a real message arrived. */
    public static final int ERL_MSG   = 1;
    /** Return code from {@link #ei_xreceive_msg}: a distribution tick arrived (keepalive). */
    public static final int ERL_TICK  = 2;
    /** Return code from ei functions indicating an error (check {@code erl_errno}). */
    public static final int ERL_ERROR = -1;

    // -----------------------------------------------------------------------
    // Struct: ei_x_buff (16 bytes on 64-bit Linux)
    // -----------------------------------------------------------------------

    /**
     * Memory layout for {@code ei_x_buff}:
     * <pre>
     *   struct ei_x_buff {
     *     char *buff;    // 8 bytes (pointer, 64-bit)
     *     int   index;   // 4 bytes (current encode/decode position)
     *     int   buffsz;  // 4 bytes (allocated buffer size)
     *   };              // total: 16 bytes
     * </pre>
     */
    public static final StructLayout EI_X_BUFF_LAYOUT = MemoryLayout.structLayout(
        ADDRESS.withName("buff"),
        JAVA_INT.withName("index"),
        JAVA_INT.withName("buffsz")
    ).withName("ei_x_buff");

    /** VarHandle for {@code ei_x_buff.buff} (the char* data pointer). */
    public static final VarHandle EI_X_BUFF$buff =
        EI_X_BUFF_LAYOUT.varHandle(groupElement("buff"));

    /** VarHandle for {@code ei_x_buff.index} (current read/write offset). */
    public static final VarHandle EI_X_BUFF$index =
        EI_X_BUFF_LAYOUT.varHandle(groupElement("index"));

    /** VarHandle for {@code ei_x_buff.buffsz} (allocated buffer capacity in bytes). */
    public static final VarHandle EI_X_BUFF$buffsz =
        EI_X_BUFF_LAYOUT.varHandle(groupElement("buffsz"));

    // -----------------------------------------------------------------------
    // Struct: erlang_msg (64 bytes opaque)
    // -----------------------------------------------------------------------

    /**
     * Opaque size of {@code erlang_msg} on 64-bit Linux OTP 28.
     * Structure contains: int msgtype (4) + erlang_pid from (~20) + erlang_pid to (~20)
     * + int serial (4) + padding to alignment boundary = 64 bytes total.
     * Allocate as: {@code arena.allocate(ERLANG_MSG_SIZE, 8L)}.
     */
    public static final long ERLANG_MSG_SIZE = 64L;

    /**
     * Partial layout for {@code erlang_msg}: only {@code msgtype} is accessible.
     * The remaining 60 bytes are opaque (pids + serial).
     */
    public static final StructLayout ERLANG_MSG_LAYOUT = MemoryLayout.structLayout(
        JAVA_INT.withName("msgtype"),
        MemoryLayout.paddingLayout(60)
    ).withName("erlang_msg");

    /** VarHandle for {@code erlang_msg.msgtype} (ERL_MSG=1, ERL_TICK=2, etc.). */
    public static final VarHandle ERLANG_MSG$msgtype =
        ERLANG_MSG_LAYOUT.varHandle(groupElement("msgtype"));

    // -----------------------------------------------------------------------
    // Struct: ei_cnode (opaque blob, ~240 bytes on 64-bit Linux OTP 28)
    // -----------------------------------------------------------------------

    /**
     * Opaque allocation size for {@code ei_cnode} on 64-bit Linux OTP 28.
     * The struct internals are not part of the public erl_interface ABI.
     * Allocate as: {@code arena.allocate(EI_CNODE_SIZE, 8L)}.
     */
    public static final long EI_CNODE_SIZE = 240L;

    // -----------------------------------------------------------------------
    // MethodHandles (9 ei.h functions)
    // -----------------------------------------------------------------------

    /**
     * {@code int ei_connect_init(ei_cnode* ec, const char* this_node_name,
     *                             const char* cookie, short creation)}
     * Initialises the {@code ei_cnode} struct with node identity and cookie.
     * Must be called once before any {@code ei_connect*} call.
     */
    public static final MethodHandle EI_CONNECT_INIT;

    /**
     * {@code int ei_connect(ei_cnode* ec, char* nodename)}
     * Connects to an Erlang node via EPMD on port 4369.
     *
     * @return file descriptor ≥ 0 on success, -1 on error
     */
    public static final MethodHandle EI_CONNECT;

    /**
     * {@code int ei_connect_host_port(ei_cnode* ec, char* hostname, int port)}
     * Connects directly to an Erlang node at the given host and port, bypassing EPMD.
     * Available since OTP 23.
     *
     * @return file descriptor ≥ 0 on success, -1 on error
     */
    public static final MethodHandle EI_CONNECT_HOST_PORT;

    /**
     * {@code int ei_rpc(ei_cnode* ec, int fd, char* mod, char* fun,
     *                    const char* argbuf, int argbuflen, ei_x_buff* x)}
     * Performs a synchronous RPC to {@code Module:Function(Args)}.
     * The result term is written into {@code x} (must be initialised with
     * {@link #ei_x_new_with_version}).
     *
     * @return 0 on success, -1 on error
     */
    public static final MethodHandle EI_RPC;

    /**
     * {@code int ei_reg_send(ei_cnode* ec, int fd, char* server_name,
     *                         char* buf, int len)}
     * Sends a message to a registered process name on the connected node.
     *
     * @return 0 on success, -1 on error
     */
    public static final MethodHandle EI_REG_SEND;

    /**
     * {@code int ei_xreceive_msg(int fd, erlang_msg* msg, ei_x_buff* x)}
     * Receives a message from the Erlang distribution protocol.
     * Fills {@code msg} with header and {@code x} with the payload term.
     *
     * @return {@link #ERL_MSG} (1), {@link #ERL_TICK} (2), or {@link #ERL_ERROR} (-1)
     */
    public static final MethodHandle EI_XRECEIVE_MSG;

    /**
     * {@code int ei_x_new(ei_x_buff* x)}
     * Initialises an {@code ei_x_buff} with an empty heap buffer. No version byte written.
     * The buffer must be freed with {@link #ei_x_free}.
     *
     * @return 0 on success
     */
    public static final MethodHandle EI_X_NEW;

    /**
     * {@code int ei_x_new_with_version(ei_x_buff* x)}
     * Initialises an {@code ei_x_buff} and writes the ETF version byte (131) at position 0.
     * Use this for buffers that will be decoded by Erlang as a complete term.
     *
     * @return 0 on success
     */
    public static final MethodHandle EI_X_NEW_WITH_VERSION;

    /**
     * {@code void ei_x_free(ei_x_buff* x)}
     * Releases the heap buffer inside {@code x}. Call for every {@code ei_x_new*} invocation.
     */
    public static final MethodHandle EI_X_FREE;

    // -----------------------------------------------------------------------
    // Static initialiser: resolve symbols from libei.so
    // -----------------------------------------------------------------------

    static {
        MethodHandle eiConnectInit     = null;
        MethodHandle eiConnect         = null;
        MethodHandle eiConnectHostPort = null;
        MethodHandle eiRpc             = null;
        MethodHandle eiRegSend         = null;
        MethodHandle eiXreceiveMsg     = null;
        MethodHandle eiXNew            = null;
        MethodHandle eiXNewWithVersion = null;
        MethodHandle eiXFree           = null;

        if (LIBRARY.isPresent()) {
            SymbolLookup lookup = LIBRARY.get();
            try {
                eiConnectInit = LINKER.downcallHandle(
                    lookup.find("ei_connect_init").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_connect_init not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_SHORT)
                );
                eiConnect = LINKER.downcallHandle(
                    lookup.find("ei_connect").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_connect not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
                );
                eiConnectHostPort = LINKER.downcallHandle(
                    lookup.find("ei_connect_host_port").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_connect_host_port not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)
                );
                eiRpc = LINKER.downcallHandle(
                    lookup.find("ei_rpc").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_rpc not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT,
                                          ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS)
                );
                eiRegSend = LINKER.downcallHandle(
                    lookup.find("ei_reg_send").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_reg_send not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)
                );
                eiXreceiveMsg = LINKER.downcallHandle(
                    lookup.find("ei_xreceive_msg").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_xreceive_msg not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS)
                );
                eiXNew = LINKER.downcallHandle(
                    lookup.find("ei_x_new").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_x_new not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS)
                );
                eiXNewWithVersion = LINKER.downcallHandle(
                    lookup.find("ei_x_new_with_version").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_x_new_with_version not found in libei.so")),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS)
                );
                eiXFree = LINKER.downcallHandle(
                    lookup.find("ei_x_free").orElseThrow(() ->
                        new UnsatisfiedLinkError("ei_x_free not found in libei.so")),
                    FunctionDescriptor.ofVoid(ADDRESS)
                );
            } catch (Exception e) {
                // Library present but symbol binding failed — all handles stay null
                // Public methods use requireLibrary() which checks LIBRARY.isPresent(),
                // but will still throw AssertionError if handle is null despite lib being present.
                // This is correct: a broken library should surface errors, not silently degrade.
            }
        }

        EI_CONNECT_INIT      = eiConnectInit;
        EI_CONNECT           = eiConnect;
        EI_CONNECT_HOST_PORT = eiConnectHostPort;
        EI_RPC               = eiRpc;
        EI_REG_SEND          = eiRegSend;
        EI_XRECEIVE_MSG      = eiXreceiveMsg;
        EI_X_NEW             = eiXNew;
        EI_X_NEW_WITH_VERSION = eiXNewWithVersion;
        EI_X_FREE            = eiXFree;
    }

    // -----------------------------------------------------------------------
    // CbC layout probe — verifies ei_x_buff ABI matches OTP 28 at class load
    // -----------------------------------------------------------------------

    static {
        if (LIBRARY.isPresent() && EI_X_NEW != null && EI_X_FREE != null) {
            try (Arena probe = Arena.ofConfined()) {
                MemorySegment xbuff = probe.allocate(EI_X_BUFF_LAYOUT);
                int rc = (int) EI_X_NEW.invokeExact(xbuff);
                if (rc != 0) {
                    throw new ExceptionInInitializerError(
                        "ei_x_new layout probe returned " + rc +
                        " — ei_x_buff Java layout may not match OTP 28 C ABI");
                }
                int buffsz = (int) EI_X_BUFF$buffsz.get(xbuff, 0L);
                if (buffsz <= 0) {
                    throw new ExceptionInInitializerError(
                        "ei_x_buff.buffsz=" + buffsz + " after ei_x_new — expected > 0. " +
                        "Check EI_X_BUFF_LAYOUT matches OTP 28 struct ei_x_buff.");
                }
                EI_X_FREE.invokeExact(xbuff);
            } catch (ExceptionInInitializerError e) {
                throw e;
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(
                    "ei_h CbC probe failed: " + t.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Guard + public wrappers
    // -----------------------------------------------------------------------

    private static void requireLibrary() {
        if (LIBRARY.isEmpty()) {
            throw new UnsupportedOperationException(
                "libei.so not loaded. Set system property -D" + LIB_PATH_PROP +
                "=/path/to/libei.so — run: bash scripts/build-erlang.sh to discover the path.");
        }
    }

    /**
     * Initialises an {@code ei_cnode} struct.
     *
     * @param ec        allocated as {@code arena.allocate(EI_CNODE_SIZE, 8L)}
     * @param nodeName  null-terminated node name (e.g. "yawl@localhost\0")
     * @param cookie    null-terminated distribution cookie
     * @param creation  node creation number (0..3, typically 0 for C nodes)
     * @return 0 on success, -1 on error
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_connect_init(MemorySegment ec, MemorySegment nodeName,
                                       MemorySegment cookie, short creation) {
        requireLibrary();
        try {
            return (int) EI_CONNECT_INIT.invokeExact(ec, nodeName, cookie, creation);
        } catch (Throwable t) { throw new AssertionError("ei_connect_init invocation failed", t); }
    }

    /**
     * Connects to an Erlang node via EPMD.
     *
     * @param ec       initialised {@code ei_cnode}
     * @param nodeName null-terminated target node name
     * @return file descriptor ≥ 0 on success, -1 on error
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_connect(MemorySegment ec, MemorySegment nodeName) {
        requireLibrary();
        try {
            return (int) EI_CONNECT.invokeExact(ec, nodeName);
        } catch (Throwable t) { throw new AssertionError("ei_connect invocation failed", t); }
    }

    /**
     * Connects to an Erlang node directly (EPMD bypass).
     *
     * @param ec       initialised {@code ei_cnode}
     * @param hostname null-terminated hostname bytes
     * @param port     TCP port of the target Erlang node
     * @return file descriptor ≥ 0 on success, -1 on error
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_connect_host_port(MemorySegment ec, MemorySegment hostname, int port) {
        requireLibrary();
        try {
            return (int) EI_CONNECT_HOST_PORT.invokeExact(ec, hostname, port);
        } catch (Throwable t) { throw new AssertionError("ei_connect_host_port invocation failed", t); }
    }

    /**
     * Performs a synchronous RPC call.
     *
     * @param ec     initialised {@code ei_cnode}
     * @param fd     connected file descriptor
     * @param mod    null-terminated module name
     * @param fun    null-terminated function name
     * @param buf    encoded argument list (ETF without version byte)
     * @param len    byte length of {@code buf} content
     * @param result result buffer (must be initialised with {@link #ei_x_new_with_version})
     * @return 0 on success, -1 on error
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_rpc(MemorySegment ec, int fd, MemorySegment mod, MemorySegment fun,
                               MemorySegment buf, int len, MemorySegment result) {
        requireLibrary();
        try {
            return (int) EI_RPC.invokeExact(ec, fd, mod, fun, buf, len, result);
        } catch (Throwable t) { throw new AssertionError("ei_rpc invocation failed", t); }
    }

    /**
     * Sends a message to a registered process name.
     *
     * @param ec         initialised {@code ei_cnode}
     * @param fd         connected file descriptor
     * @param serverName null-terminated registered process name
     * @param buf        encoded message (ETF with version byte)
     * @param len        byte length of {@code buf} content
     * @return 0 on success, -1 on error
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_reg_send(MemorySegment ec, int fd, MemorySegment serverName,
                                   MemorySegment buf, int len) {
        requireLibrary();
        try {
            return (int) EI_REG_SEND.invokeExact(ec, fd, serverName, buf, len);
        } catch (Throwable t) { throw new AssertionError("ei_reg_send invocation failed", t); }
    }

    /**
     * Receives a message from the Erlang distribution.
     *
     * @param fd  connected file descriptor
     * @param msg allocated as {@code arena.allocate(ERLANG_MSG_SIZE, 8L)}
     * @param x   result buffer (initialised with {@link #ei_x_new})
     * @return {@link #ERL_MSG}, {@link #ERL_TICK}, or {@link #ERL_ERROR}
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_xreceive_msg(int fd, MemorySegment msg, MemorySegment x) {
        requireLibrary();
        try {
            return (int) EI_XRECEIVE_MSG.invokeExact(fd, msg, x);
        } catch (Throwable t) { throw new AssertionError("ei_xreceive_msg invocation failed", t); }
    }

    /**
     * Initialises an {@code ei_x_buff} (no version byte).
     *
     * @param x allocated {@code ei_x_buff} segment
     * @return 0 on success
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_x_new(MemorySegment x) {
        requireLibrary();
        try {
            return (int) EI_X_NEW.invokeExact(x);
        } catch (Throwable t) { throw new AssertionError("ei_x_new invocation failed", t); }
    }

    /**
     * Initialises an {@code ei_x_buff} and writes the ETF version byte 131.
     *
     * @param x allocated {@code ei_x_buff} segment
     * @return 0 on success
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static int ei_x_new_with_version(MemorySegment x) {
        requireLibrary();
        try {
            return (int) EI_X_NEW_WITH_VERSION.invokeExact(x);
        } catch (Throwable t) { throw new AssertionError("ei_x_new_with_version invocation failed", t); }
    }

    /**
     * Frees the internal buffer of an {@code ei_x_buff}.
     *
     * @param x buffer initialised with {@code ei_x_new} or {@code ei_x_new_with_version}
     * @throws UnsupportedOperationException if {@code libei.so} is not loaded
     */
    public static void ei_x_free(MemorySegment x) {
        requireLibrary();
        try {
            EI_X_FREE.invokeExact(x);
        } catch (Throwable t) { throw new AssertionError("ei_x_free invocation failed", t); }
    }
}
