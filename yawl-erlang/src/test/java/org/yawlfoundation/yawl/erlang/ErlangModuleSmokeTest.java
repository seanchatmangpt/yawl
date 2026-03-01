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
package org.yawlfoundation.yawl.erlang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.generated.ei_h;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;
import org.yawlfoundation.yawl.erlang.term.*;

import java.lang.foreign.MemoryLayout;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the yawl-erlang module.
 * Validates that the module loads correctly and core APIs are accessible.
 * No OTP connections or network operations.
 */
@Tag("smoke")
@DisplayName("YAWL Erlang module smoke tests")
class ErlangModuleSmokeTest {

    @Test
    @DisplayName("ErlTermCodec class loads successfully")
    void erlTermCodec_loads_without_exception() {
        Class<?> cls = assertDoesNotThrow(
            () -> Class.forName("org.yawlfoundation.yawl.erlang.term.ErlTermCodec")
        );
        assertNotNull(cls);
    }

    @Test
    @DisplayName("ErlTerm interface loads successfully")
    void erlTerm_loads_without_exception() {
        Class<?> cls = assertDoesNotThrow(
            () -> Class.forName("org.yawlfoundation.yawl.erlang.term.ErlTerm")
        );
        assertNotNull(cls);
    }

    @Test
    @DisplayName("ErlangBridge class loads successfully")
    void erlangBridge_loads_without_exception() {
        Class<?> cls = assertDoesNotThrow(
            () -> Class.forName("org.yawlfoundation.yawl.erlang.processmining.ErlangBridge")
        );
        assertNotNull(cls);
    }

    @Test
    @DisplayName("ERL_VERSION_MAGIC constant is 131")
    void erlVersionMagic_is_131() {
        assertEquals((byte) 131, ei_h.ERL_VERSION_MAGIC);
    }

    @Test
    @DisplayName("ERL_MSG constant is 1")
    void erlMsg_constant_is_1() {
        assertEquals(1, ei_h.ERL_MSG);
    }

    @Test
    @DisplayName("ERL_TICK constant is 2")
    void erlTick_constant_is_2() {
        assertEquals(2, ei_h.ERL_TICK);
    }

    @Test
    @DisplayName("encode atom returns bytes starting with version magic")
    void encode_atom_starts_with_versionByte() {
        byte[] encoded = ErlTermCodec.encode(new ErlAtom("test"));
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
        assertEquals((byte) 131, encoded[0]);
    }

    @Test
    @DisplayName("encode-decode roundtrip for atom preserves value")
    void decode_encode_atom_roundtrip() throws Exception {
        ErlAtom original = new ErlAtom("testAtom");
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlAtom.class, decoded);
        ErlAtom decodedAtom = (ErlAtom) decoded;
        assertEquals(original.value(), decodedAtom.value());
    }

    @Test
    @DisplayName("encode-decode roundtrip for integer preserves value")
    void decode_encode_integer_roundtrip() throws Exception {
        ErlInteger original = new ErlInteger(42);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlInteger.class, decoded);
        ErlInteger decodedInt = (ErlInteger) decoded;
        assertEquals(original.value(), decodedInt.value());
    }

    @Test
    @DisplayName("encode-decode roundtrip for list preserves elements")
    void decode_encode_list_roundtrip() throws Exception {
        List<ErlTerm> elements = List.of(
            new ErlAtom("a"),
            new ErlAtom("b"),
            new ErlAtom("c")
        );
        ErlList original = new ErlList(elements);
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlList.class, decoded);
        ErlList decodedList = (ErlList) decoded;
        assertEquals(3, decodedList.elements().size());
    }

    @Test
    @DisplayName("ErlangBridge is final")
    void erlangBridge_is_final() {
        int modifiers = ErlangBridge.class.getModifiers();
        assertTrue(Modifier.isFinal(modifiers));
    }

    @Test
    @DisplayName("ErlangBridge implements AutoCloseable")
    void erlangBridge_implements_autoCloseable() {
        assertTrue(AutoCloseable.class.isAssignableFrom(ErlangBridge.class));
    }

    @Test
    @DisplayName("ErlTerm is sealed interface")
    void erlTerm_is_sealed_interface() {
        assertTrue(ErlTerm.class.isSealed());
    }

    @Test
    @DisplayName("ErlTerm has 13 permitted subclasses")
    void erlTerm_permitted_subtypes_count() {
        int permittedCount = ErlTerm.class.getPermittedSubclasses().length;
        assertEquals(13, permittedCount);
    }

    @Test
    @DisplayName("ErlTermCodec encodeArgs returns non-null for empty list")
    void erlTermCodec_encodeArgs_emptyList_validBytes() {
        byte[] encoded = ErlTermCodec.encodeArgs(List.of());
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    @DisplayName("ErlTermCodec encodeArgs returns bytes for non-empty list")
    void erlTermCodec_encodeArgs_nonEmptyList_validBytes() {
        List<ErlTerm> args = List.of(
            new ErlAtom("arg1"),
            new ErlInteger(42)
        );
        byte[] encoded = ErlTermCodec.encodeArgs(args);
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);
    }

    @Test
    @DisplayName("ErlAtom record stores value correctly")
    void erlAtom_stores_value() {
        String value = "myAtom";
        ErlAtom atom = new ErlAtom(value);
        assertEquals(value, atom.value());
    }

    @Test
    @DisplayName("ErlInteger record stores value correctly")
    void erlInteger_stores_value() {
        int value = 12345;
        ErlInteger integer = new ErlInteger(value);
        assertEquals(value, integer.value().intValueExact());
    }

    @Test
    @DisplayName("ErlFloat record stores value correctly")
    void erlFloat_stores_value() {
        double value = 3.14159;
        ErlFloat floatTerm = new ErlFloat(value);
        assertEquals(value, floatTerm.value());
    }

    @Test
    @DisplayName("ErlBinary record stores data correctly")
    void erlBinary_stores_data() {
        byte[] data = {1, 2, 3, 4, 5};
        ErlBinary binary = new ErlBinary(data);
        assertArrayEquals(data, binary.data());
    }

    @Test
    @DisplayName("ErlTuple record stores elements correctly")
    void erlTuple_stores_elements() {
        List<ErlTerm> elements = List.of(
            new ErlAtom("a"),
            new ErlInteger(1),
            new ErlAtom("b")
        );
        ErlTuple tuple = new ErlTuple(elements);
        assertEquals(3, tuple.elements().size());
        assertEquals(elements, tuple.elements());
    }

    @Test
    @DisplayName("ErlList record stores elements correctly")
    void erlList_stores_elements() {
        List<ErlTerm> elements = List.of(
            new ErlAtom("x"),
            new ErlAtom("y")
        );
        ErlList list = new ErlList(elements);
        assertEquals(2, list.elements().size());
        assertEquals(elements, list.elements());
    }

    @Test
    @DisplayName("XBUFF_LAYOUT exists and has reasonable size")
    void xbuffLayout_byteSize_reasonable() {
        MemoryLayout layout = ei_h.XBUFF_LAYOUT;
        assertNotNull(layout);
        long byteSize = layout.byteSize();
        assertTrue(byteSize > 0);
        assertTrue(byteSize <= 256); // Reasonable upper bound for a buffer structure
    }

    @Test
    @DisplayName("encode multiple terms produces valid bytes")
    void encode_multiple_terms_produces_bytes() {
        ErlAtom atom = new ErlAtom("hello");
        ErlInteger integer = new ErlInteger(123);
        ErlFloat floatVal = new ErlFloat(45.67);

        byte[] atomBytes = ErlTermCodec.encode(atom);
        byte[] intBytes = ErlTermCodec.encode(integer);
        byte[] floatBytes = ErlTermCodec.encode(floatVal);

        assertTrue(atomBytes.length > 0);
        assertTrue(intBytes.length > 0);
        assertTrue(floatBytes.length > 0);

        assertEquals((byte) 131, atomBytes[0]);
        assertEquals((byte) 131, intBytes[0]);
        assertEquals((byte) 131, floatBytes[0]);
    }

    @Test
    @DisplayName("ErlNil singleton instance is accessible")
    void erlNil_singleton_accessible() {
        ErlNil nil = ErlNil.INSTANCE;
        assertNotNull(nil);
    }

    @Test
    @DisplayName("ErlNil roundtrip preserves singleton identity")
    void erlNil_roundtrip_preserves_identity() throws Exception {
        ErlNil original = ErlNil.INSTANCE;
        byte[] encoded = ErlTermCodec.encode(original);
        ErlTerm decoded = ErlTermCodec.decode(encoded);

        assertInstanceOf(ErlNil.class, decoded);
        assertSame(ErlNil.INSTANCE, decoded);
    }

    @Test
    @DisplayName("ErlMap stores entries correctly")
    void erlMap_stores_entries() {
        java.util.Map<ErlTerm, ErlTerm> entries = new java.util.LinkedHashMap<>();
        entries.put(new ErlAtom("key1"), new ErlAtom("val1"));
        entries.put(new ErlAtom("key2"), new ErlInteger(42));

        ErlMap map = new ErlMap(entries);
        assertEquals(2, map.entries().size());
    }

    @Test
    @DisplayName("ErlPid record stores components correctly")
    void erlPid_stores_components() {
        String node = "node@host";
        int id = 1;
        int serial = 2;
        int creation = 3;

        ErlPid pid = new ErlPid(node, id, serial, creation);
        assertEquals(node, pid.node());
        assertEquals(id, pid.id());
        assertEquals(serial, pid.serial());
        assertEquals(creation, pid.creation());
    }

    @Test
    @DisplayName("ErlRef record stores components correctly")
    void erlRef_stores_components() {
        String node = "node@host";
        int[] ids = {1, 2, 3};
        int creation = 4;

        ErlRef ref = new ErlRef(node, ids, creation);
        assertEquals(node, ref.node());
        assertArrayEquals(ids, ref.ids());
        assertEquals(creation, ref.creation());
    }

    @Test
    @DisplayName("ErlPort record stores components correctly")
    void erlPort_stores_components() {
        String node = "node@host";
        long id = 99999L;
        int creation = 5;

        ErlPort port = new ErlPort(node, id, creation);
        assertEquals(node, port.node());
        assertEquals(id, port.id());
        assertEquals(creation, port.creation());
    }
}
