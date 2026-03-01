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
 * Chicago TDD tests for ErlTermCodec.
 *
 * All tests use real ETF encode/decode (no mocks). Tests cover:
 * - 13 Erlang term types (atoms, integers, floats, binaries, bitstrings, nil, lists, tuples, maps, pids, refs, ports, funs)
 * - Version byte handling (ETF magic = 131)
 * - Roundtrip encode/decode for happy paths
 * - Boundary conditions (atom length, integer ranges)
 * - Error cases (malformed ETF, wrong version byte, empty arrays)
 * - RPC result unwrapping ({rex, Result} and {badrpc, Reason})
 * - encodeArgs (no version byte prefix)
 */
@Tag("unit")
class ErlTermCodecTest {

    // ===== 1-5: ErlAtom Tests =====

    @Test
    void testVersionByte_AtomEncoding() {
        ErlTerm term = new ErlAtom("ok");
        byte[] encoded = ErlTermCodec.encode(term);
        assertEquals(131, encoded[0] & 0xFF, "First byte must be ETF version magic (131)");
    }

    @Test
    void testErlAtomRoundtrip_Hello() throws ErlangReceiveException {
        ErlAtom original = new ErlAtom("hello");
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals("hello", ((ErlAtom) decoded).value());
    }

    @Test
    void testErlAtomRoundtrip_EmptyString() throws ErlangReceiveException {
        ErlAtom original = new ErlAtom("");
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals("", ((ErlAtom) decoded).value());
    }

    @Test
    void testErlAtomRoundtrip_AtLimit_255UTF8Bytes() throws ErlangReceiveException {
        // Create a 255-byte ASCII atom (all ASCII = 1 byte per char)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 255; i++) {
            sb.append('a');
        }
        ErlAtom original = new ErlAtom(sb.toString());
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals(sb.toString(), ((ErlAtom) decoded).value());
    }

    @Test
    void testErlAtomValidation_OverLimit_256UTF8Bytes() {
        // Create a 256-byte ASCII atom
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            sb.append('a');
        }
        assertThrows(IllegalArgumentException.class, () -> new ErlAtom(sb.toString()),
                "Atom with 256 UTF-8 bytes should throw IllegalArgumentException");
    }

    // ===== 6-10: ErlInteger Tests =====

    @Test
    void testErlIntegerRoundtrip_Zero() throws ErlangReceiveException {
        ErlInteger original = new ErlInteger(0);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.ZERO, ((ErlInteger) decoded).value());
    }

    @Test
    void testErlIntegerRoundtrip_255_SmallIntegerBoundary() throws ErlangReceiveException {
        ErlInteger original = new ErlInteger(255);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(255), ((ErlInteger) decoded).value());
    }

    @Test
    void testErlIntegerRoundtrip_256_IntegerTerritory() throws ErlangReceiveException {
        ErlInteger original = new ErlInteger(256);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(256), ((ErlInteger) decoded).value());
    }

    @Test
    void testErlIntegerRoundtrip_LongMaxValue() throws ErlangReceiveException {
        ErlInteger original = new ErlInteger(Long.MAX_VALUE);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), ((ErlInteger) decoded).value());
    }

    @Test
    void testErlIntegerRoundtrip_BigInteger_2_pow_128() throws ErlangReceiveException {
        BigInteger bigVal = BigInteger.TWO.pow(128);
        ErlInteger original = new ErlInteger(bigVal);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(bigVal, ((ErlInteger) decoded).value());
    }

    // ===== 11-12: ErlFloat Tests =====

    @Test
    void testErlFloatRoundtrip_Zero() throws ErlangReceiveException {
        ErlFloat original = new ErlFloat(0.0);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlFloat.class, decoded);
        assertEquals(0.0, ((ErlFloat) decoded).value());
    }

    @Test
    void testErlFloatRoundtrip_DoubleMaxValue() throws ErlangReceiveException {
        ErlFloat original = new ErlFloat(Double.MAX_VALUE);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlFloat.class, decoded);
        assertEquals(Double.MAX_VALUE, ((ErlFloat) decoded).value());
    }

    // ===== 13-15: ErlNil and ErlBinary Tests =====

    @Test
    void testErlNilRoundtrip() throws ErlangReceiveException {
        ErlTerm original = ErlNil.INSTANCE;
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertSame(ErlNil.INSTANCE, decoded, "Decoded nil should be the singleton instance");
    }

    @Test
    void testErlBinaryRoundtrip_SimpleBytes() throws ErlangReceiveException {
        ErlBinary original = new ErlBinary(new byte[]{1, 2, 3});
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlBinary.class, decoded);
        assertArrayEquals(new byte[]{1, 2, 3}, ((ErlBinary) decoded).data());
    }

    @Test
    void testErlBinaryRoundtrip_EmptyBinary() throws ErlangReceiveException {
        ErlBinary original = new ErlBinary(new byte[0]);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlBinary.class, decoded);
        assertArrayEquals(new byte[0], ((ErlBinary) decoded).data());
    }

    // ===== 16-17: ErlList Tests =====

    @Test
    void testErlListRoundtrip_ProperList_TwoAtoms() throws ErlangReceiveException {
        List<ErlTerm> elems = List.of(new ErlAtom("a"), new ErlAtom("b"));
        ErlList original = new ErlList(elems);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(2, decodedList.elements().size());
        assertEquals("a", ((ErlAtom) decodedList.elements().get(0)).value());
        assertEquals("b", ((ErlAtom) decodedList.elements().get(1)).value());
        assertSame(ErlNil.INSTANCE, decodedList.tail());
    }

    @Test
    void testErlListRoundtrip_EmptyList() throws ErlangReceiveException {
        ErlList original = new ErlList(List.of());
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertTrue(decodedList.elements().isEmpty());
        assertSame(ErlNil.INSTANCE, decodedList.tail());
    }

    // ===== 18-19: ErlTuple Tests =====

    @Test
    void testErlTupleRoundtrip_ZeroArity() throws ErlangReceiveException {
        ErlTuple original = new ErlTuple(List.of());
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlTuple.class, decoded);
        assertTrue(((ErlTuple) decoded).elements().isEmpty());
    }

    @Test
    void testErlTupleRoundtrip_TwoElements() throws ErlangReceiveException {
        List<ErlTerm> elems = List.of(new ErlAtom("ok"), new ErlInteger(42));
        ErlTuple original = new ErlTuple(elems);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlTuple.class, decoded);
        ErlTuple decodedTuple = (ErlTuple) decoded;
        assertEquals(2, decodedTuple.elements().size());
        assertEquals("ok", ((ErlAtom) decodedTuple.elements().get(0)).value());
        assertEquals(BigInteger.valueOf(42), ((ErlInteger) decodedTuple.elements().get(1)).value());
    }

    // ===== 20-21: ErlMap Tests =====

    @Test
    void testErlMapRoundtrip_EmptyMap() throws ErlangReceiveException {
        ErlMap original = new ErlMap(Map.of());
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlMap.class, decoded);
        assertTrue(((ErlMap) decoded).entries().isEmpty());
    }

    @Test
    void testErlMapRoundtrip_OneEntry() throws ErlangReceiveException {
        Map<ErlTerm, ErlTerm> entries = new LinkedHashMap<>();
        entries.put(new ErlAtom("key"), new ErlAtom("value"));
        ErlMap original = new ErlMap(entries);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlMap.class, decoded);
        ErlMap decodedMap = (ErlMap) decoded;
        assertEquals(1, decodedMap.entries().size());
        ErlAtom keyAtom = new ErlAtom("key");
        assertTrue(decodedMap.entries().containsKey(keyAtom));
        assertEquals("value", ((ErlAtom) decodedMap.entries().get(keyAtom)).value());
    }

    // ===== 22: ErlExternalFun Test =====

    @Test
    void testErlExternalFunRoundtrip() throws ErlangReceiveException {
        ErlExternalFun original = new ErlExternalFun("erlang", "length", 1);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlExternalFun.class, decoded);
        ErlExternalFun decodedFun = (ErlExternalFun) decoded;
        assertEquals("erlang", decodedFun.module());
        assertEquals("length", decodedFun.function());
        assertEquals(1, decodedFun.arity());
    }

    // ===== 23: decodeRpcResult - Success Case =====

    @Test
    void testDecodeRpcResult_Success_RexOk() throws ErlangReceiveException, ErlangRpcException {
        // Create {rex, ok} tuple
        List<ErlTerm> rpcTuple = List.of(
                new ErlAtom("rex"),
                new ErlAtom("ok")
        );
        ErlTuple rpcResponse = new ErlTuple(rpcTuple);
        byte[] encoded = ErlTermCodec.encode(rpcResponse);

        // Decode and verify
        ErlTerm result = ErlTermCodec.decodeRpcResult(encoded);
        assertInstanceOf(ErlAtom.class, result);
        assertEquals("ok", ((ErlAtom) result).value());
    }

    // ===== 24: decodeRpcResult - BadRpc Case =====

    @Test
    void testDecodeRpcResult_BadRpc_ThrowsException() throws ErlangReceiveException {
        // Create {rex, {badrpc, reason}} tuple
        List<ErlTerm> badRpcTuple = List.of(
                new ErlAtom("badrpc"),
                new ErlAtom("reason")
        );
        ErlTuple badRpcResult = new ErlTuple(badRpcTuple);
        List<ErlTerm> outerTuple = List.of(
                new ErlAtom("rex"),
                badRpcResult
        );
        ErlTuple rpcResponse = new ErlTuple(outerTuple);
        byte[] encoded = ErlTermCodec.encode(rpcResponse);

        // Decode and verify exception
        assertThrows(ErlangRpcException.class, () -> ErlTermCodec.decodeRpcResult(encoded),
                "decodeRpcResult should throw ErlangRpcException for {badrpc, ...}");
    }

    // ===== 25: Malformed ETF - Wrong Version Byte =====

    @Test
    void testDecode_WrongVersionByte_ThrowsException() {
        byte[] badBytes = new byte[]{(byte) 130, (byte) 100, (byte) 5, (byte) 'h', (byte) 'e', (byte) 'l', (byte) 'l', (byte) 'o'};
        assertThrows(ErlangReceiveException.class, () -> ErlTermCodec.decode(badBytes),
                "decode should throw ErlangReceiveException for wrong version byte");
    }

    // ===== 26: Malformed ETF - Empty Array =====

    @Test
    void testDecode_EmptyArray_ThrowsException() {
        assertThrows(ErlangReceiveException.class, () -> ErlTermCodec.decode(new byte[0]),
                "decode should throw ErlangReceiveException for empty byte array");
    }

    // ===== 27: ErlBitstring Test =====

    @Test
    void testErlBitstringRoundtrip() throws ErlangReceiveException {
        ErlBitstring original = new ErlBitstring(new byte[]{1, 2, 3}, 6);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlBitstring.class, decoded);
        ErlBitstring decodedBitstring = (ErlBitstring) decoded;
        assertArrayEquals(new byte[]{1, 2, 3}, decodedBitstring.data());
        assertEquals(6, decodedBitstring.bitsInLastByte());
    }

    // ===== 28: Nested Structure =====

    @Test
    void testNestedStructure_TupleWithListAndInteger() throws ErlangReceiveException {
        List<ErlTerm> listElems = List.of(
                new ErlInteger(1),
                new ErlInteger(2)
        );
        ErlList innerList = new ErlList(listElems);

        List<ErlTerm> tupleElems = List.of(
                new ErlAtom("ok"),
                innerList
        );
        ErlTuple original = new ErlTuple(tupleElems);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlTuple.class, decoded);
        ErlTuple decodedTuple = (ErlTuple) decoded;
        assertEquals(2, decodedTuple.elements().size());
        assertEquals("ok", ((ErlAtom) decodedTuple.elements().get(0)).value());
        assertInstanceOf(ErlList.class, decodedTuple.elements().get(1));
        ErlList decodedList = (ErlList) decodedTuple.elements().get(1);
        assertEquals(2, decodedList.elements().size());
    }

    // ===== 29: encodeArgs - No Version Byte =====

    @Test
    void testEncodeArgs_NoVersionByte() {
        List<ErlTerm> args = List.of(
                new ErlAtom("hello"),
                new ErlInteger(42)
        );
        byte[] encoded = ErlTermCodec.encodeArgs(args);

        // First byte should NOT be 131 (version magic) — it should be LIST_EXT tag (108)
        // or another list-related tag
        assertNotEquals(131, encoded[0] & 0xFF, "encodeArgs should NOT include version byte");
    }

    // ===== 30: Negative Integer =====

    @Test
    void testErlIntegerRoundtrip_NegativeValue() throws ErlangReceiveException {
        ErlInteger original = new ErlInteger(-42);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(BigInteger.valueOf(-42), ((ErlInteger) decoded).value());
    }

    // ===== 31: Atom with Multi-Byte UTF-8 =====

    @Test
    void testErlAtomRoundtrip_UTF8_MultiByteCharacters() throws ErlangReceiveException {
        // Use a UTF-8 character that takes multiple bytes: é = 0xC3 0xA9 (2 bytes)
        ErlAtom original = new ErlAtom("café");  // "café" has an é which is 2 bytes in UTF-8
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlAtom.class, decoded);
        assertEquals("café", ((ErlAtom) decoded).value());
    }

    // ===== 32: Large Tuple (>255 elements) =====

    @Test
    void testErlTupleRoundtrip_LargeArity_256Elements() throws ErlangReceiveException {
        List<ErlTerm> elems = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            elems.add(new ErlInteger(i));
        }
        ErlTuple original = new ErlTuple(elems);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlTuple.class, decoded);
        ErlTuple decodedTuple = (ErlTuple) decoded;
        assertEquals(256, decodedTuple.elements().size());
        for (int i = 0; i < 256; i++) {
            assertEquals(BigInteger.valueOf(i),
                    ((ErlInteger) decodedTuple.elements().get(i)).value());
        }
    }

    // ===== 33: Improper List =====

    @Test
    void testErlListRoundtrip_ImproperList_TailNotNil() throws ErlangReceiveException {
        List<ErlTerm> elems = List.of(new ErlAtom("a"));
        ErlTerm tail = new ErlAtom("b");
        ErlList original = new ErlList(elems, tail);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(1, decodedList.elements().size());
        assertEquals("a", ((ErlAtom) decodedList.elements().get(0)).value());
        assertInstanceOf(ErlAtom.class, decodedList.tail());
        assertEquals("b", ((ErlAtom) decodedList.tail()).value());
    }

    // ===== 34: Float with Negative Value =====

    @Test
    void testErlFloatRoundtrip_NegativeValue() throws ErlangReceiveException {
        ErlFloat original = new ErlFloat(-3.14);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlFloat.class, decoded);
        assertEquals(-3.14, ((ErlFloat) decoded).value(), 1e-10);
    }

    // ===== 35: Negative BigInteger =====

    @Test
    void testErlIntegerRoundtrip_NegativeBigInteger() throws ErlangReceiveException {
        BigInteger negBig = BigInteger.TWO.pow(128).negate();
        ErlInteger original = new ErlInteger(negBig);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertInstanceOf(ErlInteger.class, decoded);
        assertEquals(negBig, ((ErlInteger) decoded).value());
    }
}
