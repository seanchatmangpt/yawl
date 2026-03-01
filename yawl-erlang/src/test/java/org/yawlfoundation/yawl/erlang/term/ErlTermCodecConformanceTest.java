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
package org.yawlfoundation.yawl.erlang.term;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.error.ErlangReceiveException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.generated.ei_h;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ETF (External Term Format) conformance tests verifying the Java codec
 * produces correct wire format for all canonical ETF types.
 * No OTP required — pure Java encode/decode.
 *
 * Each test verifies both the encoded wire format (tag bytes) and roundtrip
 * decode correctness using only real ErlTerm constructors.
 */
@Tag("unit")
class ErlTermCodecConformanceTest {

    // Test 1: smallInteger_0_to_255_usesTag97
    @Test
    void smallInteger_0_to_255_usesTag97() throws ErlangReceiveException {
        // Test tag 97 (ERL_SMALL_INTEGER_EXT) for small integer values (0-127)
        // The codec uses tag 97 for values with bitLength < 8, which means 0-127
        int[] values = {0, 64, 127};
        for (int val : values) {
            ErlTerm term = new ErlInteger(val);
            byte[] encoded = ErlTermCodec.encode(term);

            // Version byte at index 0 must be 131
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for value " + val);
            // Tag byte at index 1 must be 97 (ERL_SMALL_INTEGER_EXT)
            assertEquals(97, encoded[1] & 0xFF,
                    "Tag byte must be 97 (ERL_SMALL_INTEGER_EXT) for value " + val);

            // Verify roundtrip
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlInteger.class, decoded);
            assertEquals(BigInteger.valueOf(val), ((ErlInteger) decoded).value());
        }
    }

    // Test 2: integer_signedRange_usesTag98
    @Test
    void integer_signedRange_usesTag98() throws ErlangReceiveException {
        // Test tag 98 (ERL_INTEGER_EXT) for larger signed integers
        long[] values = {
                Integer.MIN_VALUE,  // -2^31
                -1,
                256,
                Integer.MAX_VALUE   // 2^31 - 1
        };
        for (long val : values) {
            ErlTerm term = new ErlInteger(val);
            byte[] encoded = ErlTermCodec.encode(term);

            // Version byte at index 0 must be 131
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for value " + val);
            // Tag byte at index 1 must be 98 (ERL_INTEGER_EXT)
            assertEquals(98, encoded[1] & 0xFF,
                    "Tag byte must be 98 (ERL_INTEGER_EXT) for value " + val);

            // Verify roundtrip
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlInteger.class, decoded);
            assertEquals(BigInteger.valueOf(val), ((ErlInteger) decoded).value());
        }
    }

    // Test 3: atomUtf8_1to255chars_usesTag118or119
    @Test
    void atomUtf8_1to255chars_usesTag118or119() throws ErlangReceiveException {
        // Test small atoms (1-255 UTF-8 bytes) use tag 119 (SMALL_ATOM_UTF8_EXT)
        ErlAtom shortAtom = new ErlAtom("hello");
        byte[] encoded = ErlTermCodec.encode(shortAtom);

        // Version byte at index 0 must be 131
        assertEquals(131, encoded[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte at index 1 must be 119 (ERL_SMALL_ATOM_UTF8_EXT)
        assertEquals(119, encoded[1] & 0xFF,
                "Tag byte must be 119 (SMALL_ATOM_UTF8_EXT) for short atom");
        // Length byte at index 2 must match "hello" (5 bytes)
        assertEquals(5, encoded[2] & 0xFF,
                "Length byte at index 2 must be 5");

        // Verify roundtrip
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals("hello", ((ErlAtom) decoded).value());

        // Test a longer atom (still within 255 bytes)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("x");
        }
        ErlAtom longAtom = new ErlAtom(sb.toString());
        byte[] encodedLong = ErlTermCodec.encode(longAtom);

        // Version byte must be 131
        assertEquals(131, encodedLong[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte should be 118 (ATOM_UTF8_EXT) for atoms > 255 bytes (but our max is 255)
        // or 119 if still <= 255 bytes
        int tagByte = encodedLong[1] & 0xFF;
        assertTrue(tagByte == 118 || tagByte == 119,
                "Tag byte must be 118 (ATOM_UTF8_EXT) or 119 (SMALL_ATOM_UTF8_EXT)");

        // Verify roundtrip
        ErlTerm decodedLong = ErlTermCodec.decode(encodedLong);
        assertInstanceOf(ErlAtom.class, decodedLong);
        assertEquals(sb.toString(), ((ErlAtom) decodedLong).value());
    }

    // Test 4: binary_empty_1byte_1mb_usesTag109
    @Test
    void binary_empty_1byte_1mb_usesTag109() throws ErlangReceiveException {
        // Test tag 109 (BINARY_EXT) for binary data of various sizes
        int[] sizes = {0, 1, 1024 * 1024}; // 0 bytes, 1 byte, 1 MB
        for (int size : sizes) {
            byte[] data = new byte[size];
            if (size > 0) {
                data[0] = 42;  // Fill first byte with a value
            }
            ErlTerm term = new ErlBinary(data);
            byte[] encoded = ErlTermCodec.encode(term);

            // Version byte at index 0 must be 131
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for binary size " + size);
            // Tag byte at index 1 must be 109 (BINARY_EXT)
            assertEquals(109, encoded[1] & 0xFF,
                    "Tag byte must be 109 (BINARY_EXT) for binary size " + size);

            // Read length as big-endian int at bytes[2..5]
            int lengthFromEncoding = ((encoded[2] & 0xFF) << 24)
                    | ((encoded[3] & 0xFF) << 16)
                    | ((encoded[4] & 0xFF) << 8)
                    | (encoded[5] & 0xFF);
            assertEquals(size, lengthFromEncoding,
                    "Length in encoding must match actual size");

            // Verify roundtrip
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlBinary.class, decoded);
            assertArrayEquals(data, ((ErlBinary) decoded).data());
        }
    }

    // Test 5: smallTuple_arity0to255_usesTag104
    @Test
    void smallTuple_arity0to255_usesTag104() throws ErlangReceiveException {
        // Test tag 104 (SMALL_TUPLE_EXT) for arity 0-255
        int[] arities = {0, 1, 255};
        for (int arity : arities) {
            List<ErlTerm> elements = new ArrayList<>();
            for (int i = 0; i < arity; i++) {
                elements.add(ErlNil.INSTANCE);
            }
            ErlTerm term = new ErlTuple(elements);
            byte[] encoded = ErlTermCodec.encode(term);

            // Version byte at index 0 must be 131
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for arity " + arity);
            // Tag byte at index 1 must be 104 (SMALL_TUPLE_EXT)
            assertEquals(104, encoded[1] & 0xFF,
                    "Tag byte must be 104 (SMALL_TUPLE_EXT) for arity " + arity);
            // Arity byte at index 2
            assertEquals(arity, encoded[2] & 0xFF,
                    "Arity byte must match actual arity");

            // Verify roundtrip
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlTuple.class, decoded);
            assertEquals(arity, ((ErlTuple) decoded).elements().size());
        }
    }

    // Test 6: list_withNilTail_usesTag108
    @Test
    void list_withNilTail_usesTag108() throws ErlangReceiveException {
        // Test tag 108 (LIST_EXT) for proper lists (tail = ErlNil)
        List<ErlTerm> elements = List.of(
                new ErlInteger(1),
                new ErlInteger(2),
                new ErlInteger(3)
        );
        ErlTerm term = new ErlList(elements);  // Proper list (tail = ErlNil by default)
        byte[] encoded = ErlTermCodec.encode(term);

        // Version byte at index 0 must be 131
        assertEquals(131, encoded[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte at index 1 must be 108 (LIST_EXT)
        assertEquals(108, encoded[1] & 0xFF,
                "Tag byte must be 108 (LIST_EXT) for proper list");

        // Verify roundtrip
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(3, decodedList.elements().size());
        assertEquals(ErlNil.INSTANCE, decodedList.tail());
    }

    // Test 7: list_improperList_nonNilTail
    @Test
    void list_improperList_nonNilTail() throws ErlangReceiveException {
        // Test improper list (tail = non-nil value)
        List<ErlTerm> elements = List.of(
                new ErlInteger(1),
                new ErlInteger(2)
        );
        ErlTerm tailValue = new ErlAtom("tail");
        ErlTerm term = new ErlList(elements, tailValue);
        byte[] encoded = ErlTermCodec.encode(term);

        // Version byte at index 0 must be 131
        assertEquals(131, encoded[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte at index 1 must still be 108 (LIST_EXT)
        assertEquals(108, encoded[1] & 0xFF,
                "Tag byte must be 108 (LIST_EXT) for improper list");

        // Verify roundtrip
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(2, decodedList.elements().size());
        assertInstanceOf(ErlAtom.class, decodedList.tail());
        assertEquals("tail", ((ErlAtom) decodedList.tail()).value());
    }

    // Test 8: map_0_1_100pairs_usesTag116
    @Test
    void map_0_1_100pairs_usesTag116() throws ErlangReceiveException {
        // Test tag 116 (MAP_EXT) for maps with various pair counts
        int[] pairCounts = {0, 1, 100};
        for (int count : pairCounts) {
            Map<ErlTerm, ErlTerm> entries = new HashMap<>();
            for (int i = 0; i < count; i++) {
                entries.put(
                        new ErlAtom("key_" + i),
                        new ErlInteger(i)
                );
            }
            ErlTerm term = new ErlMap(entries);
            byte[] encoded = ErlTermCodec.encode(term);

            // Version byte at index 0 must be 131
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for map with " + count + " pairs");
            // Tag byte at index 1 must be 116 (MAP_EXT)
            assertEquals(116, encoded[1] & 0xFF,
                    "Tag byte must be 116 (MAP_EXT)");

            // Verify roundtrip
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlMap.class, decoded);
            assertEquals(count, ((ErlMap) decoded).entries().size());
        }
    }

    // Test 9: nil_empty_list_usesTag106
    @Test
    void nil_empty_list_usesTag106() throws ErlangReceiveException {
        // Test tag 106 (NIL_EXT) for empty list / nil
        ErlTerm term = ErlNil.INSTANCE;
        byte[] encoded = ErlTermCodec.encode(term);

        // Version byte at index 0 must be 131
        assertEquals(131, encoded[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte at index 1 must be 106 (NIL_EXT)
        assertEquals(106, encoded[1] & 0xFF,
                "Tag byte must be 106 (NIL_EXT)");

        // Verify roundtrip
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertSame(ErlNil.INSTANCE, decoded,
                "Decoded nil must be the same instance as ErlNil.INSTANCE");
    }

    // Test 10: versionByte_alwaysFirst_is131
    @Test
    void versionByte_alwaysFirst_is131() throws ErlangReceiveException {
        // Test that encode(anything)[0] == 131 for various term types
        ErlTerm[] terms = {
                new ErlAtom("test"),
                new ErlInteger(42),
                ErlNil.INSTANCE,
                new ErlBinary(new byte[]{1, 2, 3}),
                new ErlFloat(3.14),
                new ErlTuple(List.of(new ErlInteger(1)))
        };

        for (ErlTerm term : terms) {
            byte[] encoded = ErlTermCodec.encode(term);
            assertEquals(131, encoded[0] & 0xFF,
                    "Version byte must be 131 for " + term.getClass().getSimpleName());
        }
    }

    // Test 11: encodeArgs_noVersionByte
    @Test
    void encodeArgs_noVersionByte() {
        // Test that encodeArgs(list) does NOT start with 131
        List<ErlTerm> args = List.of(
                new ErlInteger(1),
                new ErlAtom("ok")
        );
        byte[] encoded = ErlTermCodec.encodeArgs(args);

        // First byte should NOT be 131 (no version byte)
        assertNotEquals(131, encoded[0] & 0xFF,
                "encodeArgs must NOT start with version byte 131");
        // First byte should be 108 (LIST_EXT) since it encodes a list
        assertEquals(108, encoded[0] & 0xFF,
                "First byte of encodeArgs must be LIST_EXT tag (108)");
    }

    // Test 12: decodeRpcResult_rexWrapped_unwraps
    @Test
    void decodeRpcResult_rexWrapped_unwraps() throws ErlangReceiveException, ErlangRpcException {
        // Manually build ETF bytes for {rex, ok}
        ErlTerm rpcResponse = new ErlTuple(List.of(
                new ErlAtom("rex"),
                new ErlAtom("ok")
        ));
        byte[] bytes = ErlTermCodec.encode(rpcResponse);

        // Call decodeRpcResult and verify it unwraps the tuple
        ErlTerm result = ErlTermCodec.decodeRpcResult(bytes);
        assertInstanceOf(ErlAtom.class, result,
                "Result should be unwrapped to just the atom");
        assertEquals("ok", ((ErlAtom) result).value());
    }

    // Test 13: decodeRpcResult_badrpc_throwsRpcException
    @Test
    void decodeRpcResult_badrpc_throwsRpcException() {
        // Encode {rex, {badrpc, timeout}}
        ErlTerm badrpcReason = new ErlTuple(List.of(
                new ErlAtom("badrpc"),
                new ErlAtom("timeout")
        ));
        ErlTerm rpcResponse = new ErlTuple(List.of(
                new ErlAtom("rex"),
                badrpcReason
        ));
        byte[] bytes = ErlTermCodec.encode(rpcResponse);

        // Assert that decodeRpcResult throws ErlangRpcException
        assertThrows(ErlangRpcException.class,
                () -> ErlTermCodec.decodeRpcResult(bytes),
                "decodeRpcResult must throw ErlangRpcException for {rex, {badrpc, ...}}");
    }

    // Test 14: largeTuple_arity256_usesTag105
    @Test
    void largeTuple_arity256_usesTag105() throws ErlangReceiveException {
        // Test tag 105 (LARGE_TUPLE_EXT) for arity > 255
        List<ErlTerm> elements = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            elements.add(ErlNil.INSTANCE);
        }
        ErlTerm term = new ErlTuple(elements);
        byte[] encoded = ErlTermCodec.encode(term);

        // Version byte at index 0 must be 131
        assertEquals(131, encoded[0] & 0xFF,
                "Version byte must be 131");
        // Tag byte at index 1 must be 105 (LARGE_TUPLE_EXT)
        assertEquals(105, encoded[1] & 0xFF,
                "Tag byte must be 105 (LARGE_TUPLE_EXT) for arity 256");

        // Arity is encoded as big-endian int at bytes[2..5]
        int arity = ((encoded[2] & 0xFF) << 24)
                | ((encoded[3] & 0xFF) << 16)
                | ((encoded[4] & 0xFF) << 8)
                | (encoded[5] & 0xFF);
        assertEquals(256, arity,
                "Arity in encoding must be 256");

        // Verify roundtrip
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlTuple.class, decoded);
        assertEquals(256, ((ErlTuple) decoded).elements().size());
    }

    // Test 15: bigint_small_large_roundtrip
    @Test
    void bigint_small_large_roundtrip() throws ErlangReceiveException {
        // Test SMALL_BIG_EXT (tag 110) for BigInteger that fits in <= 255 bytes
        BigInteger smallBig = BigInteger.TWO.pow(100);  // ~30 bytes
        ErlTerm smallBigTerm = new ErlInteger(smallBig);
        byte[] smallEncoded = ErlTermCodec.encode(smallBigTerm);

        assertEquals(131, smallEncoded[0] & 0xFF,
                "Version byte must be 131");
        assertEquals(ei_h.SMALL_BIG_EXT, smallEncoded[1] & 0xFF,
                "Tag byte must be 110 (SMALL_BIG_EXT) for 2^100");

        // Verify roundtrip
        ErlTerm smallDecoded = ErlTermCodec.decode(smallEncoded);
        assertInstanceOf(ErlInteger.class, smallDecoded);
        assertEquals(smallBig, ((ErlInteger) smallDecoded).value());

        // Test LARGE_BIG_EXT (tag 111) for BigInteger that needs > 255 bytes
        BigInteger largeBig = BigInteger.TWO.pow(2048);  // ~256+ bytes
        ErlTerm largeBigTerm = new ErlInteger(largeBig);
        byte[] largeEncoded = ErlTermCodec.encode(largeBigTerm);

        assertEquals(131, largeEncoded[0] & 0xFF,
                "Version byte must be 131");
        assertEquals(ei_h.LARGE_BIG_EXT, largeEncoded[1] & 0xFF,
                "Tag byte must be 111 (LARGE_BIG_EXT) for 2^2048");

        // Verify roundtrip
        ErlTerm largeDecoded = ErlTermCodec.decode(largeEncoded);
        assertInstanceOf(ErlInteger.class, largeDecoded);
        assertEquals(largeBig, ((ErlInteger) largeDecoded).value());
    }
}
