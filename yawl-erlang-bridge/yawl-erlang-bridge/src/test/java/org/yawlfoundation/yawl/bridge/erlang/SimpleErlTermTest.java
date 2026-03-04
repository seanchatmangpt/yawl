package org.yawlfoundation.yawl.bridge.erlang;

/**
 * Simple test class for ErlTerm encoding and decoding without external dependencies.
 *
 * @since 1.0.0
 */
public class SimpleErlTermTest {

    public static void main(String[] args) {
        try {
            testErlAtomEncodingDecoding();
            System.out.println("✓ ErlAtom test passed");

            testErlLongEncodingDecoding();
            System.out.println("✓ ErlLong test passed");

            testErlDoubleEncodingDecoding();
            System.out.println("✓ ErlDouble test passed");

            testErlBinaryEncodingDecoding();
            System.out.println("✓ ErlBinary test passed");

            testErlNilEncodingDecoding();
            System.out.println("✓ ErlNil test passed");

            testErlListEncodingDecoding();
            System.out.println("✓ ErlList test passed");

            testErlTupleEncodingDecoding();
            System.out.println("✓ ErlTuple test passed");

            testErlMapEncodingDecoding();
            System.out.println("✓ ErlMap test passed");

            testEmptyListEncodingDecoding();
            System.out.println("✓ Empty list test passed");

            testEmptyMapEncodingDecoding();
            System.out.println("✓ Empty map test passed");

            testComplexNestedStructure();
            System.out.println("✓ Complex nested structure test passed");

            testRoundTripConsistency();
            System.out.println("✓ Round trip consistency test passed");

            System.out.println("\n🎉 All tests passed successfully!");

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void testErlAtomEncodingDecoding() throws ErlangException {
        ErlAtom original = ErlAtom.of("test_atom");
        byte[] encoded = original.encodeETF();

        // Debug: print encoded bytes
        System.out.print("Encoded atom bytes: ");
        for (byte b : encoded) {
            System.out.print(String.format("%02X ", b));
        }
        System.out.println();
        System.out.println("Encoded length: " + encoded.length);

        ErlAtom decoded = (ErlAtom) ErlTerm.decodeETF(encoded);

        if (!original.getValue().equals(decoded.getValue())) {
            throw new AssertionError("Atom values don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Atom string representations don't match");
        }
    }

    public static void testErlLongEncodingDecoding() throws ErlangException {
        ErlLong original = ErlLong.of(42);
        byte[] encoded = original.encodeETF();
        ErlLong decoded = (ErlLong) ErlTerm.decodeETF(encoded);

        if (original.getValue() != decoded.getValue()) {
            throw new AssertionError("Long values don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Long string representations don't match");
        }
    }

    public static void testErlDoubleEncodingDecoding() throws ErlangException {
        ErlDouble original = ErlDouble.of(3.14159);
        byte[] encoded = original.encodeETF();
        ErlDouble decoded = (ErlDouble) ErlTerm.decodeETF(encoded);

        if (original.getValue() != decoded.getValue()) {
            throw new AssertionError("Double values don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Double string representations don't match");
        }
    }

    public static void testErlBinaryEncodingDecoding() throws ErlangException {
        byte[] data = {72, 101, 108, 108, 111}; // "Hello"
        ErlBinary original = ErlBinary.of(data);
        byte[] encoded = original.encodeETF();
        ErlBinary decoded = (ErlBinary) ErlTerm.decodeETF(encoded);

        if (!original.equals(decoded)) {
            throw new AssertionError("Binary values don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Binary string representations don't match");
        }
    }

    public static void testErlNilEncodingDecoding() throws ErlangException {
        ErlNil original = ErlNil.nil();
        byte[] encoded = original.encodeETF();
        ErlNil decoded = (ErlNil) ErlTerm.decodeETF(encoded);

        if (original != decoded) { // Should be the same instance
            throw new AssertionError("Nil instances don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Nil string representations don't match");
        }
    }

    public static void testErlListEncodingDecoding() throws ErlangException {
        ErlList original = ErlList.of(
            ErlAtom.of("a"),
            ErlLong.of(1),
            ErlDouble.of(2.5)
        );
        byte[] encoded = original.encodeETF();
        ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);

        if (original.getElements().size() != decoded.getElements().size()) {
            throw new AssertionError("List sizes don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("List string representations don't match");
        }
    }

    public static void testErlTupleEncodingDecoding() throws ErlangException {
        ErlTuple original = ErlTuple.of(
            ErlAtom.of("ok"),
            ErlLong.of(42),
            ErlList.of(ErlAtom.of("item1"), ErlAtom.of("item2"))
        );
        byte[] encoded = original.encodeETF();
        ErlTuple decoded = (ErlTuple) ErlTerm.decodeETF(encoded);

        if (original.getElements().size() != decoded.getElements().size()) {
            throw new AssertionError("Tuple sizes don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Tuple string representations don't match");
        }
    }

    public static void testErlMapEncodingDecoding() throws ErlangException {
        java.util.Map<ErlTerm, ErlTerm> entries = new java.util.TreeMap<>();
        entries.put(ErlAtom.of("name"), ErlAtom.of("John"));
        entries.put(ErlAtom.of("age"), ErlLong.of(30));
        entries.put(ErlAtom.of("score"), ErlDouble.of(95.5));

        ErlMap original = ErlMap.of(entries);
        byte[] encoded = original.encodeETF();
        ErlMap decoded = (ErlMap) ErlTerm.decodeETF(encoded);

        if (original.size() != decoded.size()) {
            throw new AssertionError("Map sizes don't match");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Map string representations don't match");
        }
        if (!((ErlAtom)original.get(ErlAtom.of("name"))).asString().equals(
            ((ErlAtom)decoded.get(ErlAtom.of("name"))).asString())) {
            throw new AssertionError("Map values don't match");
        }
    }

    public static void testEmptyListEncodingDecoding() throws ErlangException {
        ErlList original = ErlList.of();
        byte[] encoded = original.encodeETF();
        ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);

        if (!decoded.isEmpty()) {
            throw new AssertionError("Empty list should be empty");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Empty list string representations don't match");
        }
    }

    public static void testEmptyMapEncodingDecoding() throws ErlangException {
        ErlMap original = ErlMap.of();
        byte[] encoded = original.encodeETF();
        ErlMap decoded = (ErlMap) ErlTerm.decodeETF(encoded);

        if (!decoded.isEmpty()) {
            throw new AssertionError("Empty map should be empty");
        }
        if (!original.asString().equals(decoded.asString())) {
            throw new AssertionError("Empty map string representations don't match");
        }
    }

    public static void testComplexNestedStructure() throws ErlangException {
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

        if (!complex.asString().equals(decoded.asString())) {
            throw new AssertionError("Complex structure string representations don't match");
        }
        if (!complex.type().equals(decoded.type())) {
            throw new AssertionError("Complex structure types don't match");
        }
    }

    public static void testRoundTripConsistency() throws ErlangException {
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

            if (!original.type().equals(decoded.type())) {
                throw new AssertionError("Type mismatch for " + original.type());
            }
            if (!original.asString().equals(decoded.asString())) {
                throw new AssertionError("String representation mismatch for " + original.type());
            }
        }
    }
}