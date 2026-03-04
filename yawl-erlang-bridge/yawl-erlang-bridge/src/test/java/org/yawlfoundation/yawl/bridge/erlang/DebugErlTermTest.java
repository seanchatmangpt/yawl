package org.yawlfoundation.yawl.bridge.erlang;

/**
 * Debug test class for ErlTerm encoding and decoding.
 *
 * @since 1.0.0
 */
public class DebugErlTermTest {

    public static void main(String[] args) {
        try {
            System.out.println("Testing ErlAtom encoding...");

            ErlAtom original = ErlAtom.of("test_atom");
            System.out.println("Original: " + original.asString());

            // Create a custom EiBuffer to see what's written
            EiBuffer buffer = new EiBuffer();
            buffer.put((byte) 131); // External term tag
            System.out.println("Wrote external term tag: 131");

            byte[] atomBytes = "test_atom".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            System.out.println("Atom length: " + atomBytes.length);

            buffer.put((byte) 119); // SMALL_ATOM_EXT
            System.out.println("Wrote SMALL_ATOM_EXT: 119");

            buffer.put((byte) atomBytes.length);
            System.out.println("Wrote atom length: " + atomBytes.length);

            buffer.put(atomBytes);
            System.out.println("Wrote atom data");

            byte[] encoded = buffer.toArray();
            System.out.println("Total encoded length: " + encoded.length);

            System.out.print("Encoded bytes: ");
            for (byte b : encoded) {
                System.out.print(String.format("%02X ", b));
            }
            System.out.println();

            System.out.println("Trying to decode...");
            ErlTerm decoded = ErlTerm.decodeETF(encoded);
            System.out.println("Decoded: " + decoded.asString());
            System.out.println("Decoded type: " + decoded.type());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}