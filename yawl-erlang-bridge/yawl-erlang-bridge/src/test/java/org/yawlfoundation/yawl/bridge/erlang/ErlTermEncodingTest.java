package org.yawlfoundation.yawl.bridge.erlang;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ErlTerm encoding and decoding.
 *
 * <p>Verifies that all Erlang term types can be encoded to ETF and decoded back
 * to their original form.</p>
 *
 * @since 1.0.0
 */
public class ErlTermEncodingTest {

    @Test
    public void testErlAtomEncodingDecoding() throws ErlangException {
        ErlAtom original = ErlAtom.of("test_atom");
        byte[] encoded = original.encodeETF();
        ErlAtom decoded = (ErlAtom) ErlTerm.decodeETF(encoded);

        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlLongEncodingDecoding() throws ErlangException {
        ErlLong original = ErlLong.of(42);
        byte[] encoded = original.encodeETF();
        ErlLong decoded = (ErlLong) ErlTerm.decodeETF(encoded);

        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlDoubleEncodingDecoding() throws ErlangException {
        ErlDouble original = ErlDouble.of(3.14159);
        byte[] encoded = original.encodeETF();
        ErlDouble decoded = (ErlDouble) ErlTerm.decodeETF(encoded);

        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlBinaryEncodingDecoding() throws ErlangException {
        byte[] data = {72, 101, 108, 108, 111}; // "Hello"
        ErlBinary original = ErlBinary.of(data);
        byte[] encoded = original.encodeETF();
        ErlBinary decoded = (ErlBinary) ErlTerm.decodeETF(encoded);

        assertArrayEquals(original.getBytes(), decoded.getBytes());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlNilEncodingDecoding() throws ErlangException {
        ErlNil original = ErlNil.nil();
        byte[] encoded = original.encodeETF();
        ErlNil decoded = (ErlNil) ErlTerm.decodeETF(encoded);

        assertSame(original, decoded); // Should be the same instance
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlListEncodingDecoding() throws ErlangException {
        ErlList original = ErlList.of(
            ErlAtom.of("a"),
            ErlLong.of(1),
            ErlDouble.of(2.5)
        );
        byte[] encoded = original.encodeETF();
        ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);

        assertEquals(original.getElements().size(), decoded.getElements().size());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlTupleEncodingDecoding() throws ErlangException {
        ErlTuple original = ErlTuple.of(
            ErlAtom.of("ok"),
            ErlLong.of(42),
            ErlList.of(ErlAtom.of("item1"), ErlAtom.of("item2"))
        );
        byte[] encoded = original.encodeETF();
        ErlTuple decoded = (ErlTuple) ErlTerm.decodeETF(encoded);

        assertEquals(original.getElements().size(), decoded.getElements().size());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testErlMapEncodingDecoding() throws ErlangException {
        java.util.Map<ErlTerm, ErlTerm> entries = new java.util.TreeMap<>();
        entries.put(ErlAtom.of("name"), ErlAtom.of("John"));
        entries.put(ErlAtom.of("age"), ErlLong.of(30));
        entries.put(ErlAtom.of("score"), ErlDouble.of(95.5));

        ErlMap original = ErlMap.of(entries);
        byte[] encoded = original.encodeETF();
        ErlMap decoded = (ErlMap) ErlTerm.decodeETF(encoded);

        assertEquals(original.size(), decoded.size());
        assertEquals(original.asString(), decoded.asString());
        assertEquals(original.get(ErlAtom.of("name")).asString(),
                   decoded.get(ErlAtom.of("name")).asString());
    }

    @Test
    public void testEmptyListEncodingDecoding() throws ErlangException {
        ErlList original = ErlList.of();
        byte[] encoded = original.encodeETF();
        ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);

        assertTrue(decoded.isEmpty());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testEmptyMapEncodingDecoding() throws ErlangException {
        ErlMap original = ErlMap.of();
        byte[] encoded = original.encodeETF();
        ErlMap decoded = (ErlMap) ErlTerm.decodeETF(encoded);

        assertTrue(decoded.isEmpty());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    public void testComplexNestedStructure() throws ErlangException {
        // Create a complex nested structure
        ErlTuple complex = ErlTuple.of(
            ErlAtom.of("result"),
            ErlLong.of(200),
            ErlMap.of(
                ErlAtom.of("data"),
                ErlList.of(
                    ErlAtom.of("item1"),
                    ErlLong.of(1),
                    ErlBinary.of(new byte[]{1, 2, 3})
                )
            ),
            ErlMap.of(
                ErlAtom.of("metadata"),
                ErlTuple.of(
                    ErlAtom.of("timestamp"),
                    ErlDouble.of(System.currentTimeMillis() / 1000.0)
                )
            )
        );

        byte[] encoded = complex.encodeETF();
        ErlTuple decoded = (ErlTuple) ErlTerm.decodeETF(encoded);

        assertEquals(complex.asString(), decoded.asString());
        assertEquals(complex.type(), decoded.type());
    }

    @Test
    public void testRoundTripConsistency() throws ErlangException {
        // Test that encode->decode preserves exact values
        ErlTerm[] terms = {
            ErlAtom.of("test"),
            ErlLong.of(Integer.MAX_VALUE),
            ErlLong.of(Long.MAX_VALUE),
            ErlDouble.of(Double.MAX_VALUE),
            ErlBinary.of("UTF-8 string".getBytes()),
            ErlList.of(ErlAtom.of("nested"), ErlLong.of(123)),
            ErlMap.of(ErlAtom.of("key"), ErlLong.of(42))
        };

        for (ErlTerm original : terms) {
            byte[] encoded = original.encodeETF();
            ErlTerm decoded = ErlTerm.decodeETF(encoded);

            assertEquals(original.type(), decoded.type());
            assertEquals(original.asString(), decoded.asString());
        }
    }
}