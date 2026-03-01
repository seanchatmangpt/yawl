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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link ei_h} — verifies struct layouts, constants, and graceful degradation.
 * Chicago TDD: real structs only, no mocks. Tests that require libei.so are skipped
 * (not failed) when the library is absent.
 */
@Tag("unit")
class EiHTest {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    @Test
    void libPathPropConstant_isCorrectValue() {
        assertEquals("erlang.library.path", ei_h.LIB_PATH_PROP);
    }

    @Test
    void erlVersionMagic_is131() {
        assertEquals((byte) 131, ei_h.ERL_VERSION_MAGIC);
    }

    @Test
    void etfAtomTag_is118() {
        assertEquals(118, ei_h.ERL_ATOM_UTF8_EXT);
    }

    @Test
    void etfSmallAtomTag_is119() {
        assertEquals(119, ei_h.ERL_SMALL_ATOM_UTF8_EXT);
    }

    @Test
    void etfSmallIntTag_is97() {
        assertEquals(97, ei_h.ERL_SMALL_INTEGER_EXT);
    }

    @Test
    void etfIntTag_is98() {
        assertEquals(98, ei_h.ERL_INTEGER_EXT);
    }

    @Test
    void etfNewFloatTag_is70() {
        assertEquals(70, ei_h.NEW_FLOAT_EXT);
    }

    @Test
    void etfBinaryTag_is109() {
        assertEquals(109, ei_h.BINARY_EXT);
    }

    @Test
    void etfListTag_is108() {
        assertEquals(108, ei_h.LIST_EXT);
    }

    @Test
    void etfNilTag_is106() {
        assertEquals(106, ei_h.NIL_EXT);
    }

    @Test
    void etfSmallTupleTag_is104() {
        assertEquals(104, ei_h.SMALL_TUPLE_EXT);
    }

    @Test
    void etfMapTag_is116() {
        assertEquals(116, ei_h.MAP_EXT);
    }

    @Test
    void erlMsgReturnCode_is1() {
        assertEquals(1, ei_h.ERL_MSG);
    }

    @Test
    void erlTickReturnCode_is2() {
        assertEquals(2, ei_h.ERL_TICK);
    }

    @Test
    void erlErrorReturnCode_isNeg1() {
        assertEquals(-1, ei_h.ERL_ERROR);
    }

    // -----------------------------------------------------------------------
    // Struct layout sizes — verifiable WITHOUT loading libei.so
    // -----------------------------------------------------------------------

    @Test
    void eiXBuffLayout_byteSize_is16() {
        // ei_x_buff: char*(8) + int(4) + int(4) = 16 bytes
        assertEquals(16L, ei_h.EI_X_BUFF_LAYOUT.byteSize());
    }

    @Test
    void eiXBuffLayout_hasThreeMembers() {
        // buff, index, buffsz
        assertEquals(3, ei_h.EI_X_BUFF_LAYOUT.memberLayouts().size());
    }

    @Test
    void erlangMsgLayout_byteSize_is64() {
        // erlang_msg opaque blob: msgtype(4) + padding(60) = 64 bytes
        assertEquals(64L, ei_h.ERLANG_MSG_LAYOUT.byteSize());
    }

    @Test
    void eiCnodeSize_isAtLeast200() {
        // ei_cnode is ~240 bytes on 64-bit Linux OTP 28
        assertTrue(ei_h.EI_CNODE_SIZE >= 200,
            "EI_CNODE_SIZE=" + ei_h.EI_CNODE_SIZE + " seems too small for OTP 28 ei_cnode");
    }

    @Test
    void erlangMsgSize_isAtLeast32() {
        assertTrue(ei_h.ERLANG_MSG_SIZE >= 32,
            "ERLANG_MSG_SIZE=" + ei_h.ERLANG_MSG_SIZE + " seems too small for erlang_msg");
    }

    // -----------------------------------------------------------------------
    // VarHandle field access — usable WITHOUT libei.so
    // -----------------------------------------------------------------------

    @Test
    void eiXBuffIndexVarHandle_readWrite() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ei_h.EI_X_BUFF_LAYOUT);
            ei_h.EI_X_BUFF$index.set(seg, 0L, 42);
            int val = (int) ei_h.EI_X_BUFF$index.get(seg, 0L);
            assertEquals(42, val);
        }
    }

    @Test
    void eiXBuffBuffszVarHandle_readWrite() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ei_h.EI_X_BUFF_LAYOUT);
            ei_h.EI_X_BUFF$buffsz.set(seg, 0L, 1024);
            int val = (int) ei_h.EI_X_BUFF$buffsz.get(seg, 0L);
            assertEquals(1024, val);
        }
    }

    @Test
    void erlangMsgMsgTypeVarHandle_readWrite() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(ei_h.ERLANG_MSG_LAYOUT);
            ei_h.ERLANG_MSG$msgtype.set(seg, 0L, ei_h.ERL_MSG);
            int val = (int) ei_h.ERLANG_MSG$msgtype.get(seg, 0L);
            assertEquals(ei_h.ERL_MSG, val);
        }
    }

    // -----------------------------------------------------------------------
    // Graceful degradation — all methods throw UnsupportedOperationException
    // when libei.so is not loaded (which is the default in CI without OTP)
    // -----------------------------------------------------------------------

    @Test
    void eiConnectInit_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_connect_init(MemorySegment.NULL, MemorySegment.NULL,
                                  MemorySegment.NULL, (short) 0));
    }

    @Test
    void eiConnect_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_connect(MemorySegment.NULL, MemorySegment.NULL));
    }

    @Test
    void eiConnectHostPort_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_connect_host_port(MemorySegment.NULL, MemorySegment.NULL, 4369));
    }

    @Test
    void eiRpc_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_rpc(MemorySegment.NULL, -1, MemorySegment.NULL, MemorySegment.NULL,
                         MemorySegment.NULL, 0, MemorySegment.NULL));
    }

    @Test
    void eiRegSend_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_reg_send(MemorySegment.NULL, -1, MemorySegment.NULL,
                              MemorySegment.NULL, 0));
    }

    @Test
    void eiXreceiveMsg_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_xreceive_msg(-1, MemorySegment.NULL, MemorySegment.NULL));
    }

    @Test
    void eiXNew_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_x_new(MemorySegment.NULL));
    }

    @Test
    void eiXNewWithVersion_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_x_new_with_version(MemorySegment.NULL));
    }

    @Test
    void eiXFree_throwsUnsupportedOperationException_whenLibraryAbsent() {
        assumeTrue(ei_h.LIBRARY.isEmpty(), "Skipped: libei.so is loaded");
        assertThrows(UnsupportedOperationException.class, () ->
            ei_h.ei_x_free(MemorySegment.NULL));
    }

    // -----------------------------------------------------------------------
    // Library loaded tests — only run when -Derlang.library.path is set
    // -----------------------------------------------------------------------

    @Test
    void eiXNew_succeeds_whenLibraryPresent() {
        assumeTrue(ei_h.LIBRARY.isPresent(), "Skipped: libei.so not loaded");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xbuff = arena.allocate(ei_h.EI_X_BUFF_LAYOUT);
            int rc = ei_h.ei_x_new(xbuff);
            assertEquals(0, rc, "ei_x_new should return 0 on success");
            int buffsz = (int) ei_h.EI_X_BUFF$buffsz.get(xbuff, 0L);
            assertTrue(buffsz > 0, "buffsz should be > 0 after ei_x_new");
            ei_h.ei_x_free(xbuff);
        }
    }

    @Test
    void eiXNewWithVersion_writesVersionByte_whenLibraryPresent() {
        assumeTrue(ei_h.LIBRARY.isPresent(), "Skipped: libei.so not loaded");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment xbuff = arena.allocate(ei_h.EI_X_BUFF_LAYOUT);
            int rc = ei_h.ei_x_new_with_version(xbuff);
            assertEquals(0, rc, "ei_x_new_with_version should return 0 on success");
            int index = (int) ei_h.EI_X_BUFF$index.get(xbuff, 0L);
            assertEquals(1, index, "index should be 1 after writing version byte");
            // Verify version byte via buff pointer
            MemorySegment buffPtr = (MemorySegment) ei_h.EI_X_BUFF$buff.get(xbuff, 0L);
            byte versionByte = buffPtr.reinterpret(1).get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0);
            assertEquals((byte) 131, versionByte, "First byte should be ETF version 131");
            ei_h.ei_x_free(xbuff);
        }
    }
}
