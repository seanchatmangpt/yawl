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

/**
 * Erlang distribution protocol constants and magic numbers
 *
 * These constants are derived from ei.h and erl_driver.h in the Erlang/OTP distribution.
 * They define the binary format for Erlang term encoding.
 */
public final class EiConstants {
    private EiConstants() {
        throw new UnsupportedOperationException("EiConstants is a utility class - do not instantiate");
    }

    // Magic number at the start of all Erlang distribution messages
    public static final int ERLANG_MAGIC_NUMBER = 131;

    // External term format tags (from ei.h enum)
    public static final byte ERL_SMALL_INTEGER = 97;      // ERL_SMALL_INTEGER_EXT
    public static final byte ERL_INTEGER = 98;            // INTEGER_EXT
    public static final byte ERL_ATOM = 100;              // ATOM_EXT
    public static final byte ERL_REFERENCE = 101;         // REFERENCE_EXT
    public static final byte ERL_PORT = 102;              // PORT_EXT
    public static final byte ERL_PID = 103;               // PID_EXT
    public static final byte ERL_SMALL_TUPLE = 104;       // SMALL_TUPLE_EXT
    public static final byte ERL_LARGE_TUPLE = 106;       // LARGE_TUPLE_EXT
    public static final byte ERL_NIL = 106;               // NIL_EXT
    public static final byte ERL_STRING = 107;            // STRING_EXT
    public static final byte ERL_LIST = 108;              // LIST_EXT
    public static final byte ERL_BINARY = 109;            // BINARY_EXT
    public static final byte ERL_BIT_BINARY = 110;        // BIT_BINARY_EXT
    public static final byte ERL_FLOAT = 70;              // FLOAT_EXT
    public static final byte ERL_NEW_FLOAT = 70;          // NEW_FLOAT_EXT

    // Legacy compatibility aliases
    public static final byte SMALL_INTEGER_EXT = ERL_SMALL_INTEGER;
    public static final byte INTEGER_EXT = ERL_INTEGER;
    public static final byte FLOAT_EXT = ERL_FLOAT;
    public static final byte ATOM_EXT = ERL_ATOM;
    public static final byte SMALL_ATOM_EXT = 115;
    public static final byte BINARY_EXT = ERL_BINARY;
    public static final byte SMALL_TUPLE_EXT = ERL_SMALL_TUPLE;
    public static final byte LARGE_TUPLE_EXT = ERL_LARGE_TUPLE;
    public static final byte NIL_EXT = ERL_NIL;
    public static final byte STRING_EXT = ERL_STRING;
    public static final byte LIST_EXT = ERL_LIST;
    public static final byte BINARY_EXT_V4 = 106;
    public static final byte SMALL_BIG_EXT = 110;
    public static final byte LARGE_BIG_EXT = 111;

    // Status codes from ei.h
    public static final int ERL_ERROR = -1;
    public static final int ERL_NO_DAEMON = -2;
    public static final int ERL_CONNECT_FAIL = -3;
    public static final int ERL_TIMEOUT = -4;
    public static final int ERL_NO_TIMEOUT = -5;
    public static final int ERL_DRV_ERR_UNKNOWN = -6;
    public static final int ERL_DRV_ERR_BUSY = -7;
    public static final int ERL_DRV_ERR_NOMEM = -8;
    public static final int ERL_DRV_ERR_NODRIVER = -9;
    public static final int ERL_MSG = 1;
    public static final int ERL_TICK = 2;

    // Constants from ei.h
    public static final int EI_CNODE_SIZE = 80;
    public static final int ERLANG_MSG_SIZE = 4*4 + 2*4 + 8;
    public static final int EI_X_BUFF_SIZE = 1024 * 1024;

    // Length constants
    public static final int MAXATOMLEN = 255;
    public static final int MAXATOMLEN_EXT = 255;
    public static final int SMALLATOMLEN = 255;
    public static final int MAX_PID_NAME_LEN = 255;
    public static final int MAX_REG_NAME_LEN = 255;

    // Default buffer sizes
    public static final int DEFAULT_XBUFF_SIZE = 512;
    public static final int MAX_XBUFF_SIZE = 1024 * 1024; // 1MB max

    // Node name constants
    public static final int NODENAME_SIZE = MAX_REG_NAME_LEN;
    public static final int COOKIE_SIZE = MAXATOMLEN;

    // Connection flags
    public static final int ERL_USE_NAME = 1;
    public static final int ERL_NO_DAEMON = 2;
    public static final int ERL_PACKETS_ON = 4;
    public static final int ERL_PACKETS_OFF = 8;

    // Default Erlang port
    public static final int ERLANG_DIST_PORT = 4369;

    // Protocol versions
    public static final int ERLANG_DIST_VERSION = 5;
    public static final int ERLANG_DIST_VSN_LEN = 2;

    // Header sizes
    public static final int EXTERNAL_TERM_HEADER_SIZE = 1;
    public static final int TUPLE_HEADER_SIZE = 1; // 1 byte for arity
    public static final int LIST_HEADER_SIZE = 3; // 1 byte + 4 bytes (arity + nil)

    // Timeout values
    public static final long TIMEOUT_IMMEDIATE = 0L;
    public static final long TIMEOUT_INFINITE = -1L;
}