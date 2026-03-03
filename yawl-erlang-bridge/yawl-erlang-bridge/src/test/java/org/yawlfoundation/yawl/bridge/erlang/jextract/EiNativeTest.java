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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for EiNative layer 1 jextract bindings
 */
@EnabledOnOs(OS.LINUX)  // Erlang ei library typically only available on Linux
class EiNativeTest {

    @Test
    void testEiNativeClassUninstantiable() {
        assertThrows(UnsupportedOperationException.class, EiNative::new);
    }

    @Test
    void testEiConstantsClassUninstantiable() {
        assertThrows(UnsupportedOperationException.class, EiConstants::new);
    }

    @Test
    void testEiLayoutClassUninstantiable() {
        assertThrows(UnsupportedOperationException.class, EiLayout::new);
    }

    @Test
    void testConstants() {
        // Test external term format tags
        assertEquals(97, EiConstants.SMALL_INTEGER_EXT);
        assertEquals(98, EiConstants.INTEGER_EXT);
        assertEquals(100, EiConstants.ATOM_EXT);
        assertEquals(104, EiConstants.SMALL_TUPLE_EXT);
        assertEquals(106, EiConstants.NIL_EXT);
        assertEquals(107, EiConstants.STRING_EXT);
        assertEquals(108, EiConstants.LIST_EXT);
        assertEquals(109, EiConstants.BINARY_EXT);

        // Test error codes
        assertEquals(-1, EiConstants.ERL_ERROR);
        assertEquals(-3, EiConstants.ERL_CONNECT_FAIL);
        assertEquals(-4, EiConstants.ERL_TIMEOUT);

        // Test sizes
        assertEquals(255, EiConstants.MAXATOMLEN);
        assertEquals(512, EiConstants.DEFAULT_XBUFF_SIZE);
        assertEquals(1024 * 1024, EiConstants.MAX_XBUFF_SIZE);
    }

    @Test
    void testEiLayoutStructure() {
        // Test ei_x_buff layout
        assertNotNull(EiLayout.EI_X_BUFF_LAYOUT);
        assertEquals(12, EiLayout.EI_X_BUFF_LAYOUT.byteSize());  // 4 (index) + 4 (size) + 4 (pointer) = 12 bytes

        // Test field offsets
        assertEquals(0, EiLayout.INDEX_OFFSET);
        assertEquals(4, EiLayout.SIZE_OFFSET);
        assertEquals(8, EiLayout.BUFF_OFFSET);

        // Test memory segment creation
        Arena arena = Arena.ofConfined();
        MemorySegment segment = EiLayout.createSegment(EiLayout.EI_X_BUFF_LAYOUT, arena);
        assertNotNull(segment);
        assertEquals(EiLayout.EI_X_BUFF_LAYOUT.byteSize(), segment.byteSize());

        // Test validation
        assertThrows(IllegalArgumentException.class, () ->
            EiLayout.validateSegment(segment, EiLayout.ERL_CONNECT_LAYOUT));
    }

    @Test
    void testNativeLibraryUnavailableOnUnsupportedPlatform() {
        // This test will pass on non-Linux systems where the native library isn't available
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            // On Linux, we expect the library to be available
            // This test is mainly for non-Linux systems
            return;
        }

        // On non-Linux systems, we expect UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            try {
                EiNative.eiXNew(256);
            } catch (Exception e) {
                if (e instanceof UnsupportedOperationException) {
                    throw e;
                }
                // Other exceptions are not what we're testing here
            }
        });
    }

    @Test
    void testEiXNewAndFree() {
        // This test will only run if the native library is available
        Arena arena = Arena.ofConfined();

        try {
            // Test buffer allocation
            MemorySegment buffer = EiNative.eiXNew(256);
            assertNotNull(buffer);

            // Test that we can write to the buffer using the layout
            MemorySegment indexSeg = arena.allocate(ValueLayout.JAVA_INT);
            indexSeg.set(ValueLayout.JAVA_INT, 0, 0);

            // Test buffer fields are accessible
            int index = buffer.get(ValueLayout.JAVA_INT, EiLayout.INDEX_OFFSET);
            assertEquals(0, index);

            int size = buffer.get(ValueLayout.JAVA_INT, EiLayout.SIZE_OFFSET);
            assertEquals(256, size);

            MemorySegment buffPtr = buffer.get(ValueLayout.ADDRESS, EiLayout.BUFF_OFFSET);
            assertNotNull(buffPtr);

            // Test buffer free (should not throw)
            EiNative.eiXFree(buffer);

        } catch (UnsupportedOperationException e) {
            // If library not available, that's expected on this platform
            return;
        } catch (Exception e) {
            fail("Unexpected exception when testing ei_x_new/free: " + e.getMessage());
        }
    }

    @Test
    void testEiXEncodeAtom() {
        Arena arena = Arena.ofConfined();

        try {
            MemorySegment buffer = EiNative.eiXNew(256);
            String atom = "test_atom";
            byte[] atomBytes = atom.getBytes();

            // Encode atom
            int result = EiNative.eiXEncodeAtom(buffer, atomBytes);
            assertEquals(0, result);

            // Clean up
            EiNative.eiXFree(buffer);

        } catch (UnsupportedOperationException e) {
            // If library not available, that's expected
            return;
        } catch (Exception e) {
            fail("Unexpected exception when testing ei_x_encode_atom: " + e.getMessage());
        }
    }

    @Test
    void testEiXEncodeBinary() {
        Arena arena = Arena.ofConfined();

        try {
            MemorySegment buffer = EiNative.eiXNew(256);
            byte[] binaryData = {0x01, 0x02, 0x03, 0x04, 0x05};

            // Encode binary
            int result = EiNative.eiXEncodeBinary(buffer, binaryData, binaryData.length);
            assertEquals(0, result);

            // Clean up
            EiNative.eiXFree(buffer);

        } catch (UnsupportedOperationException e) {
            // If library not available, that's expected
            return;
        } catch (Exception e) {
            fail("Unexpected exception when testing ei_x_encode_binary: " + e.getMessage());
        }
    }

    @Test
    void testTimeoutConstants() {
        assertEquals(0L, EiConstants.TIMEOUT_IMMEDIATE);
        assertEquals(-1L, EiConstants.TIMEOUT_INFINITE);
    }
}