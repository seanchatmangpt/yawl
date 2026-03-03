package org.yawlfoundation.yawl.bridge.erlang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErlTerm encoding and decoding.
 *
 * @since 1.0.0
 */
class ErlTermCodecTest {

    @Test
    @DisplayName("ErlAtom encoding and decoding")
    void testErlAtom() {
        ErlAtom atom = new ErlAtom("test_atom");

        // Test basic properties
        assertEquals("test_atom", atom.getValue());
        assertEquals("atom", atom.type());
        assertEquals("'test_atom'", atom.asString());

        // Test equality
        assertEquals(atom, new ErlAtom("test_atom"));
        assertNotEquals(atom, new ErlAtom("other_atom"));

        // Test null validation
        assertThrows(IllegalArgumentException.class, () -> new ErlAtom(null));
    }

    @Test
    @DisplayName("ErlLong encoding and decoding")
    void testErlLong() {
        ErlLong longValue = new ErlLong(42L);

        // Test basic properties
        assertEquals(42L, longValue.getValue());
        assertEquals("integer", longValue.type());
        assertEquals("42", longValue.asString());

        // Test equality
        assertEquals(longValue, new ErlLong(42L));
        assertNotEquals(longValue, new ErlLong(43L));

        // Test factory methods
        assertEquals(longValue, ErlLong.of(42));
        assertEquals(longValue, ErlLong.of("42"));

        // Test number format exception
        assertThrows(NumberFormatException.class, () -> ErlLong.of("not_a_number"));
    }

    @Test
    @DisplayName("ErlBinary encoding and decoding")
    void testErlBinary() {
        byte[] testData = new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}; // "Hello" in hex
        ErlBinary binary = new ErlBinary(testData);

        // Test basic properties
        assertEquals(5, binary.length());
        assertEquals("binary", binary.type());
        assertEquals("<<48, 65, 6C, 6C, 6F>>", binary.asString());

        // Test equality
        assertEquals(binary, new ErlBinary(testData));
        assertNotEquals(binary, new ErlBinary(new byte[]{0x00}));

        // Test copy constructor
        byte[] copy = binary.getBytes();
        assertArrayEquals(testData, copy);

        // Test from string
        ErlBinary fromString = ErlBinary.ofString("Hello");
        assertEquals(binary, fromString);

        // Test from hex string
        ErlBinary fromHex = ErlBinary.ofHexString("48656C6C6F");
        assertEquals(binary, fromHex);

        // Test validation
        assertThrows(IllegalArgumentException.class, () -> new ErlBinary(null));
        assertThrows(IllegalArgumentException.class, () -> ErlBinary.ofHexString("invalid"));
    }

    @Test
    @DisplayName("ErlTuple encoding and decoding")
    void testErlTuple() {
        ErlTuple tuple = new ErlTuple(
            ErlAtom.of("test"),
            ErlLong.of(42),
            ErlAtom.of("tuple")
        );

        // Test basic properties
        assertEquals(3, tuple.arity());
        assertEquals("tuple", tuple.type());
        assertEquals("{test, 42, 'tuple'}", tuple.asString());

        // Test element access
        assertEquals("test", ((ErlAtom) tuple.get(0)).getValue());
        assertEquals(42L, ((ErlLong) tuple.get(1)).getValue());
        assertEquals("tuple", ((ErlAtom) tuple.get(2)).getValue());

        // Test factory methods
        ErlTuple tuple1 = ErlTuple.of(ErlAtom.of("a"), ErlAtom.of("b"));
        ErlTuple tuple2 = ErlTuple.of(List.of(ErlAtom.of("a"), ErlAtom.of("b")));
        assertEquals(tuple1, tuple2);

        // Test static creation methods
        ErlTuple pair = ErlTuple.two(ErlAtom.of("key"), ErlAtom.of("value"));
        assertEquals("{'key', 'value'}", pair.asString());

        ErlTuple triple = ErlTuple.three(
            ErlAtom.of("a"),
            ErlAtom.of("b"),
            ErlAtom.of("c")
        );
        assertEquals("{a, b, c}", triple.asString());

        // Test validation
        assertThrows(IllegalArgumentException.class, () -> new ErlTuple((ErlTerm[]) null));
        assertThrows(IllegalArgumentException.class, () -> new ErlTuple(ErlAtom.of("valid"), null));

        // Test arithemtic checks
        assertTrue(tuple.hasArity(2));
        assertFalse(tuple.hasArity(4));
    }

    @Test
    @DisplayName("ErlList encoding and decoding")
    void testErlList() {
        ErlList list = new ErlList(
            ErlAtom.of("item1"),
            ErlLong.of(2),
            ErlAtom.of("item3")
        );

        // Test basic properties
        assertEquals(3, list.getElements().size());
        assertEquals("list", list.type());
        assertEquals("[item1, 2, 'item3']", list.asString());
        assertTrue(list.isProper());

        // Test empty list
        ErlList emptyList = ErlList.of();
        assertEquals(0, emptyList.getElements().size());
        assertEquals("[]", emptyList.asString());

        // Test factory methods
        ErlList list1 = ErlList.of(ErlAtom.of("a"), ErlAtom.of("b"));
        ErlList list2 = ErlList.of(List.of(ErlAtom.of("a"), ErlAtom.of("b")));
        assertEquals(list1, list2);

        // Test improper list
        ErlList improperList = new ErlList(
            List.of(ErlAtom.of("head"), ErlAtom.of("tail")),
            false
        );
        assertEquals("[head | ...]", improperList.asString());

        // Test validation
        assertThrows(IllegalArgumentException.class, () -> new ErlList(null));
        assertThrows(IllegalArgumentException.class, () -> new ErlList(ErlAtom.of("valid"), null));
    }

    @Test
    @DisplayName("EiBuffer encoding operations")
    void testEiBufferEncoding() {
        EiBuffer buffer = new EiBuffer(1024);

        try {
            // Test encoding different types
            buffer.encodeAtom("test_atom");
            buffer.encodeLong(42L);
            buffer.encodeString("hello");
            buffer.encodeBoolean(true);

            // Verify buffer size increased
            assertTrue(buffer.size() > 0);
            assertEquals(1024, buffer.capacity());

            // Test binary encoding
            byte[] binaryData = new byte[]{0x00, 0x01, 0x02, 0x03};
            buffer.encodeBinary(binaryData);

            // Test list encoding
            ErlTerm[] listElements = {
                ErlAtom.of("item1"),
                ErlLong.of(2)
            };
            buffer.encodeList(listElements);

            // Test tuple encoding
            ErlTerm[] tupleElements = {
                ErlAtom.of("ok"),
                ErlAtom.of("response")
            };
            buffer.encodeTuple(tupleElements);

            // Verify size after multiple encodings
            assertTrue(buffer.size() > 0);

        } catch (IOException e) {
            fail("IOException during encoding", e);
        }
    }

    @Test
    @DisplayName("EiBuffer capacity expansion")
    void testEiBufferCapacityExpansion() {
        EiBuffer buffer = new EiBuffer(10); // Very small initial capacity

        try {
            // This should trigger capacity expansion
            String longString = "x".repeat(100);
            buffer.encodeString(longString);

            // Buffer should have expanded capacity
            assertTrue(buffer.capacity() > 10);
            assertEquals(longString, new String(buffer.toArray()).substring(0, 100));

        } catch (IOException e) {
            fail("IOException during capacity test", e);
        }
    }

    @ParameterizedTest
    @MethodSource("provideErlTerms")
    @DisplayName("All ErlTerm implementations encode properly")
    void testAllErlTermsEncodeToBuffer(ErlTerm term) {
        EiBuffer buffer = new EiBuffer();

        try {
            term.encodeToEiBuffer(buffer);
            assertTrue(buffer.size() > 0);
        } catch (ErlangException e) {
            fail("Encoding failed for " + term.type(), e);
        }
    }

    @Test
    @DisplayName("ErlTerm toString consistency")
    void testErlTermToStringConsistency() {
        ErlAtom atom = new ErlAtom("test");
        assertEquals(atom.asString(), atom.toString());

        ErlLong longValue = new ErlLong(123);
        assertEquals(longValue.asString(), longValue.toString());

        ErlBinary binary = ErlBinary.of("hello");
        assertEquals(binary.asString(), binary.toString());

        ErlTuple tuple = ErlTuple.of(atom, longValue);
        assertEquals(tuple.asString(), tuple.toString());

        ErlList list = ErlList.of(atom, longValue);
        assertEquals(list.asString(), list.toString());
    }

    @Test
    @DisplayName("ErlTerm hashCode consistency")
    void testErlTermHashCodeConsistency() {
        ErlAtom atom1 = new ErlAtom("test");
        ErlAtom atom2 = new ErlAtom("test");
        assertEquals(atom1.hashCode(), atom2.hashCode());

        ErlLong long1 = new ErlLong(42);
        ErlLong long2 = new ErlLong(42);
        assertEquals(long1.hashCode(), long2.hashCode());

        ErlBinary binary1 = new ErlBinary(new byte[]{0x01, 0x02});
        ErlBinary binary2 = new ErlBinary(new byte[]{0x01, 0x02});
        assertEquals(binary1.hashCode(), binary2.hashCode());
    }

    @Test
    @DisplayName("ErlTerm type values are consistent")
    void testErlTermTypeValues() {
        assertEquals("atom", new ErlAtom("test").type());
        assertEquals("integer", new ErlLong(42).type());
        assertEquals("binary", new ErlBinary(new byte[0]).type());
        assertEquals("tuple", new ErlTuple().type());
        assertEquals("list", new ErlList().type());
    }

    // Helper method for parameterized test
    private static Stream<ErlTerm> provideErlTerms() {
        return Stream.of(
            new ErlAtom("test"),
            new ErlLong(42L),
            new ErlBinary("hello".getBytes()),
            new ErlTuple(ErlAtom.of("a"), ErlLong.of(1)),
            new ErlList(ErlAtom.of("item1"), ErlAtom.of("item2"))
        );
    }
}