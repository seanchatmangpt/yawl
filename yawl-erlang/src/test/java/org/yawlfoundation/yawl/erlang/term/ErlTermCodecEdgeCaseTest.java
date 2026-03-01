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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD edge case tests for ErlTermCodec.
 *
 * Tests focus on breaking points: truncated buffers, unknown tags,
 * boundary conditions, malformed structures, and deep nesting.
 * All tests use real ETF encode/decode with no mocks.
 */
@Tag("unit")
class ErlTermCodecEdgeCaseTest {

    // ===== Test 1: Truncated ETF - version byte only =====

    @Test
    void testDecode_VersionByteOnly_ThrowsException() {
        byte[] justVersion = new byte[]{(byte) 131};
        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(justVersion),
                "decode should throw ErlangReceiveException when only version byte is present");
    }

    // ===== Test 2: Unknown ETF tag =====

    @Test
    void testDecode_UnknownTag_ThrowsException() {
        byte[] unknownTag = new byte[]{(byte) 131, (byte) 0};
        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(unknownTag),
                "decode should throw ErlangReceiveException for unknown tag (0)");
    }

    // ===== Test 3: Empty byte array =====

    @Test
    void testDecode_EmptyArray_ThrowsException() {
        byte[] empty = new byte[0];
        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(empty),
                "decode should throw ErlangReceiveException for empty array");
    }

    // ===== Test 4: Wrong version byte =====

    @Test
    void testDecode_WrongVersionByte_130_ThrowsException() {
        byte[] wrongVersion = new byte[]{(byte) 130, (byte) 106};
        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(wrongVersion),
                "decode should throw ErlangReceiveException for version byte 130 instead of 131");
    }

    // ===== Test 5: Version byte with nil tag (valid nil encoding) =====

    @Test
    void testDecode_VersionByte_NilTag_ReturnsNil() throws ErlangReceiveException {
        byte[] nilEncoded = new byte[]{(byte) 131, (byte) 106};
        ErlTerm decoded = ErlTermCodec.decode(nilEncoded);
        assertSame(ErlNil.INSTANCE, decoded,
                "decode of {131, 106} (nil tag) should return ErlNil.INSTANCE");
    }

    // ===== Test 6: encode(null) throws NullPointerException =====

    @Test
    void testEncode_Null_ThrowsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> ErlTermCodec.encode(null),
                "encode(null) should throw NullPointerException");
    }

    // ===== Test 7: encodeArgs with empty list roundtrip =====

    @Test
    void testEncodeArgs_EmptyList_ValidBytes() {
        byte[] encoded = ErlTermCodec.encodeArgs(List.of());
        assertNotNull(encoded);
        assertGreater(encoded.length, 0, "encodeArgs should produce non-empty bytes");
        assertNotEquals(131, encoded[0] & 0xFF, "encodeArgs should NOT include version byte");
    }

    // ===== Test 8: decodeRpcResult with nested badrpc =====

    @Test
    void testDecodeRpcResult_BadrpcNested_ThrowsException() throws ErlangReceiveException {
        // Create {rex, {badrpc, {error, something}}}
        List<ErlTerm> errorTuple = List.of(
                new ErlAtom("error"),
                new ErlAtom("something")
        );
        List<ErlTerm> badRpcTuple = List.of(
                new ErlAtom("badrpc"),
                new ErlTuple(errorTuple)
        );
        List<ErlTerm> rpcResponse = List.of(
                new ErlAtom("rex"),
                new ErlTuple(badRpcTuple)
        );
        byte[] encoded = ErlTermCodec.encode(new ErlTuple(rpcResponse));

        assertThrows(ErlangRpcException.class,
                () -> ErlTermCodec.decodeRpcResult(encoded),
                "decodeRpcResult should throw ErlangRpcException for nested badrpc");
    }

    // ===== Test 9: Deeply nested list (1000 levels) =====

    @Test
    void testDecode_DeeplyNested_1000Levels_NoStackOverflow() {
        // Build a 1000-level nested list: [[[[... (nil) ...]]]]
        ErlTerm nested = ErlNil.INSTANCE;
        for (int i = 0; i < 1000; i++) {
            nested = new ErlList(List.of(new ErlAtom("x")), nested);
        }
        byte[] encoded = ErlTermCodec.encode(nested);
        assertDoesNotThrow(() -> ErlTermCodec.decode(encoded),
                "decode should handle 1000-level nesting without StackOverflowError");
    }

    // ===== Test 10: Large map with 50 entries roundtrip =====

    @Test
    void testEncode_Decode_LargeMap_50Entries_Roundtrip() throws ErlangReceiveException {
        Map<ErlTerm, ErlTerm> entries = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            entries.put(new ErlAtom("key_" + i), new ErlInteger(i));
        }
        ErlMap original = new ErlMap(entries);

        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlMap.class, decoded);
        ErlMap decodedMap = (ErlMap) decoded;
        assertEquals(50, decodedMap.entries().size(),
                "Decoded map should have 50 entries");

        for (int i = 0; i < 50; i++) {
            ErlAtom key = new ErlAtom("key_" + i);
            assertTrue(decodedMap.entries().containsKey(key),
                    "Decoded map should contain key_" + i);
            ErlInteger val = (ErlInteger) decodedMap.entries().get(key);
            assertEquals(BigInteger.valueOf(i), val.value());
        }
    }

    // ===== Test 11: Truncated list - arity claims 10 but only 2 elements provided =====

    @Test
    void testDecode_TruncatedList_UnderflowOnRead() {
        // Manually construct: version + LIST_EXT tag + arity=10 (big-endian) + 2 atoms + nil
        // This should trigger buffer underflow when decoder expects 10 elements
        byte[] truncated = new byte[]{
                (byte) 131,                          // version
                (byte) 108,                          // LIST_EXT tag
                (byte) 0, (byte) 0, (byte) 0, (byte) 10, // arity = 10 (big-endian)
                (byte) 119, (byte) 1, (byte) 'a',   // SMALL_ATOM_UTF8_EXT, length 1, 'a'
                (byte) 119, (byte) 1, (byte) 'b',   // SMALL_ATOM_UTF8_EXT, length 1, 'b'
                (byte) 106                          // NIL_EXT (tail)
                // Missing 8 more elements - decoder should throw
        };

        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(truncated),
                "decode should throw ErlangReceiveException when list is truncated");
    }

    // ===== Test 12: Improper list (non-nil tail) roundtrip =====

    @Test
    void testEncode_Decode_ImproperList_RoundtripSuccess() throws ErlangReceiveException {
        // Create [a | b] (improper list)
        ErlList improper = new ErlList(
                List.of(new ErlAtom("a")),
                new ErlAtom("b")
        );

        byte[] encoded = ErlTermCodec.encode(improper);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(1, decodedList.elements().size());
        assertEquals("a", ((ErlAtom) decodedList.elements().get(0)).value());
        assertInstanceOf(ErlAtom.class, decodedList.tail());
        assertEquals("b", ((ErlAtom) decodedList.tail()).value());
    }

    // ===== Test 13: Nil list (empty list as []) =====

    @Test
    void testDecode_NilList_EmptyListRoundtrip() throws ErlangReceiveException {
        // [131, 106] = version + nil tag (represents the empty list [])
        byte[] nilBytes = new byte[]{(byte) 131, (byte) 106};
        ErlTerm decoded = ErlTermCodec.decode(nilBytes);
        assertSame(ErlNil.INSTANCE, decoded);
    }

    // ===== Test 14: Atom with 1 character roundtrip =====

    @Test
    void testEncode_Decode_Atom_SingleCharacter() throws ErlangReceiveException {
        ErlAtom singleChar = new ErlAtom("a");
        byte[] encoded = ErlTermCodec.encode(singleChar);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals("a", ((ErlAtom) decoded).value());
    }

    // ===== Test 15: Atom at 255 UTF-8 byte limit (uses SMALL_ATOM_UTF8_EXT) =====

    @Test
    void testEncode_Decode_Atom_255UTF8Bytes_Boundary() throws ErlangReceiveException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 255; i++) {
            sb.append('x');
        }
        ErlAtom atom255 = new ErlAtom(sb.toString());
        byte[] encoded = ErlTermCodec.encode(atom255);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals(sb.toString(), ((ErlAtom) decoded).value());
    }

    // ===== Test 16: Atom over 255 UTF-8 bytes (uses ATOM_UTF8_EXT) =====

    @Test
    void testEncode_Decode_Atom_256UTF8Bytes_LargeFormat() throws ErlangReceiveException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            sb.append('y');
        }
        // Note: ErlAtom validates max 256 UTF-8 bytes, so we're at the boundary
        // This should still work if the limit is exactly 256
        // If the constructor rejects 256, adjust the test
        assertDoesNotThrow(() -> {
            ErlAtom atom256 = new ErlAtom(sb.toString());
            byte[] encoded = ErlTermCodec.encode(atom256);
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertInstanceOf(ErlAtom.class, decoded);
            assertEquals(sb.toString(), ((ErlAtom) decoded).value());
        });
    }

    // ===== Test 17: decodeRpcResult with ok atom =====

    @Test
    void testDecodeRpcResult_Ok_ReturnsOkAtom() throws ErlangReceiveException, ErlangRpcException {
        // {rex, ok}
        List<ErlTerm> rpc = List.of(
                new ErlAtom("rex"),
                new ErlAtom("ok")
        );
        byte[] encoded = ErlTermCodec.encode(new ErlTuple(rpc));
        ErlTerm result = ErlTermCodec.decodeRpcResult(encoded);

        assertInstanceOf(ErlAtom.class, result);
        assertEquals("ok", ((ErlAtom) result).value());
    }

    // ===== Test 18: Empty binary roundtrip =====

    @Test
    void testEncode_Decode_EmptyBinary() throws ErlangReceiveException {
        ErlBinary empty = new ErlBinary(new byte[0]);
        byte[] encoded = ErlTermCodec.encode(empty);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlBinary.class, decoded);
        assertArrayEquals(new byte[0], ((ErlBinary) decoded).data());
    }

    // ===== Test 19: Negative big integer roundtrip =====

    @Test
    void testEncode_Decode_NegativeBigInteger_Roundtrip() throws ErlangReceiveException {
        BigInteger negBig = BigInteger.TWO.pow(256).negate();
        ErlInteger original = new ErlInteger(negBig);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(negBig, ((ErlInteger) decoded).value());
    }

    // ===== Test 20: List with truncated integer in middle =====

    @Test
    void testDecode_ListWithTruncatedInteger_BufferUnderflow() {
        // Manually construct list with truncated INTEGER_EXT in the middle
        byte[] truncatedInt = new byte[]{
                (byte) 131,                          // version
                (byte) 108,                          // LIST_EXT
                (byte) 0, (byte) 0, (byte) 0, (byte) 2, // arity = 2
                (byte) 119, (byte) 1, (byte) 'a',   // SMALL_ATOM_UTF8_EXT, 'a'
                (byte) 98,                          // INTEGER_EXT tag
                (byte) 0, (byte) 0                  // Only 2 bytes of 4 needed
                // Missing 2 bytes - decoder should throw
        };

        assertThrows(ErlangReceiveException.class,
                () -> ErlTermCodec.decode(truncatedInt),
                "decode should throw when integer is truncated mid-list");
    }

    // ===== Test 21: encodeArgs roundtrip through decode =====

    @Test
    void testEncodeArgs_Decode_ArgumentList() throws ErlangReceiveException {
        List<ErlTerm> args = List.of(
                new ErlAtom("hello"),
                new ErlInteger(42),
                new ErlBinary(new byte[]{1, 2, 3})
        );
        byte[] encoded = ErlTermCodec.encodeArgs(args);

        // Prepend version byte to decode it
        byte[] withVersion = new byte[encoded.length + 1];
        withVersion[0] = (byte) 131;
        System.arraycopy(encoded, 0, withVersion, 1, encoded.length);

        ErlTerm decoded = ErlTermCodec.decode(withVersion);
        assertInstanceOf(ErlList.class, decoded);

        ErlList decodedList = (ErlList) decoded;
        assertEquals(3, decodedList.elements().size());
    }

    // ===== Test 22: Tuple at 255 arity boundary (SMALL_TUPLE_EXT) =====

    @Test
    void testEncode_Decode_Tuple_255Arity_SmallFormat() throws ErlangReceiveException {
        List<ErlTerm> elems = new ArrayList<>();
        for (int i = 0; i < 255; i++) {
            elems.add(new ErlInteger(i));
        }
        ErlTuple tuple255 = new ErlTuple(elems);
        byte[] encoded = ErlTermCodec.encode(tuple255);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlTuple.class, decoded);
        assertEquals(255, ((ErlTuple) decoded).elements().size());
    }

    // ===== Test 23: Tuple at 256 arity (LARGE_TUPLE_EXT) =====

    @Test
    void testEncode_Decode_Tuple_256Arity_LargeFormat() throws ErlangReceiveException {
        List<ErlTerm> elems = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            elems.add(new ErlInteger(i));
        }
        ErlTuple tuple256 = new ErlTuple(elems);
        byte[] encoded = ErlTermCodec.encode(tuple256);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlTuple.class, decoded);
        assertEquals(256, ((ErlTuple) decoded).elements().size());
    }

    // ===== Test 24: Integer at 255 boundary (SMALL_INTEGER_EXT) =====

    @Test
    void testEncode_Decode_Integer_255Boundary() throws ErlangReceiveException {
        ErlInteger int255 = new ErlInteger(255);
        byte[] encoded = ErlTermCodec.encode(int255);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(255), ((ErlInteger) decoded).value());
    }

    // ===== Test 25: Integer at 256 (INTEGER_EXT territory) =====

    @Test
    void testEncode_Decode_Integer_256_LargerFormat() throws ErlangReceiveException {
        ErlInteger int256 = new ErlInteger(256);
        byte[] encoded = ErlTermCodec.encode(int256);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(256), ((ErlInteger) decoded).value());
    }

    // ===== Utility method for assertions =====

    private static <T> void assertGreater(T actual, T expected, String message) {
        if (actual instanceof Integer a && expected instanceof Integer e) {
            assertTrue(a > e, message);
        }
    }
}
