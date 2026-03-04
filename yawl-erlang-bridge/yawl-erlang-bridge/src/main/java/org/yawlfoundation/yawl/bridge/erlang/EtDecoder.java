package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Decoder for Erlang External Term Format (ETF).
 *
 * <p>This class decodes ETF byte arrays into ErlTerm instances,
 * supporting the full range of Erlang data types.</p>
 *
 * @since 1.0.0
 */
public final class EtDecoder {

    private final ByteBuffer buffer;

    /**
     * Creates a new EtDecoder with the given ETF data.
     *
     * @param data The ETF encoded byte array
     */
    public EtDecoder(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
    }

    /**
     * Decodes the entire ETF byte array into an ErlTerm.
     *
     * @return The decoded ErlTerm
     * @throws ErlangException if decoding fails
     */
    public ErlTerm decode() throws ErlangException {
        try {
            // Check for external term format magic number
            if (buffer.remaining() < 1) {
                throw new ErlangException("Invalid ETF data: too short");
            }

            // Always starts with 131 (EXTERNAL_TERM_TAG)
            if (buffer.get() != 131) {
                throw new ErlangException("Invalid ETF data: missing external term tag");
            }

            return decodeTerm();
        } catch (IOException e) {
            throw new ErlangException("Failed to decode ETF", e);
        }
    }

    /**
     * Decodes a single Erlang term from the buffer.
     *
     * @return The decoded ErlTerm
     * @throws IOException if decoding fails
     */
    private ErlTerm decodeTerm() throws IOException {
        if (!buffer.hasRemaining()) {
            throw new IOException("Unexpected end of buffer");
        }

        byte tag = buffer.get();
        switch (tag) {
            // Small integers (0-255)
            case 97: // SMALL_INTEGER_EXT
                return ErlLong.of(buffer.get() & 0xFF);

            // Integers (-2147483648 to 2147483647)
            case 98: // INTEGER_EXT
                return ErlLong.of(buffer.getInt());

            // Small atoms (<= 255 bytes)
            case 100: // ATOM_EXT
                return decodeAtom();

            // Boolean atoms (true/false)
            case 99: // SMALL_ATOM_EXT
                return decodeSmallAtom();

            // Binary
            case 109: // BINARY_EXT
                return decodeBinary();

            // String
            case 107: // STRING_EXT
                return decodeString();

            // List
            case 108: // LIST_EXT
                return decodeList();

            // Nil (empty list)
            case 106: // NIL_EXT
                return ErlNil.nil();

            // Small tuple (< 256 elements)
            case 104: // SMALL_TUPLE_EXT
                return decodeSmallTuple();

            // Large tuple (>= 256 elements)
            case 105: // LARGE_TUPLE_EXT
                return decodeLargeTuple();

            // Map
            case 116: // MAP_EXT
                return decodeMap();

            // New float (IEEE 754 double)
            case 70: // NEW_FLOAT_EXT
                return decodeNewFloat();

            default:
                throw new IOException("Unknown ETF tag: " + tag);
        }
    }

    /**
     * Decodes an atom from the buffer.
     *
     * @return The decoded ErlAtom
     * @throws IOException if decoding fails
     */
    private ErlAtom decodeAtom() throws IOException {
        int length = buffer.getShort() & 0xFFFF;
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return ErlAtom.of(new String(bytes, StandardCharsets.ISO_8859_1));
    }

    /**
     * Decodes a small atom from the buffer.
     *
     * @return The decoded ErlAtom
     * @throws IOException if decoding fails
     */
    private ErlAtom decodeSmallAtom() throws IOException {
        int length = buffer.get() & 0xFF;
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return ErlAtom.of(new String(bytes, StandardCharsets.ISO_8859_1));
    }

    /**
     * Decodes a binary from the buffer.
     *
     * @return The decoded ErlBinary
     * @throws IOException if decoding fails
     */
    private ErlBinary decodeBinary() throws IOException {
        int length = buffer.getInt();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return ErlBinary.of(bytes);
    }

    /**
     * Decodes a string from the buffer.
     *
     * @return The decoded ErlList of characters
     * @throws IOException if decoding fails
     */
    private ErlList decodeString() throws IOException {
        int length = buffer.getShort() & 0xFFFF;
        if (length == 0) {
            return ErlList.of();
        }

        ErlTerm[] chars = new ErlTerm[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ErlLong.of(buffer.get() & 0xFF);
        }
        return ErlList.of(chars);
    }

    /**
     * Decodes a list from the buffer.
     *
     * @return The decoded ErlList
     * @throws IOException if decoding fails
     */
    private ErlList decodeList() throws IOException {
        int length = buffer.getInt();
        ErlTerm[] elements = new ErlTerm[length];

        for (int i = 0; i < length; i++) {
            elements[i] = decodeTerm();
        }

        // Consume trailing NIL_EXT
        if (!buffer.hasRemaining() || buffer.get() != 106) {
            throw new IOException("List missing terminating NIL_EXT");
        }

        return ErlList.of(elements);
    }

    /**
     * Decodes a small tuple from the buffer.
     *
     * @return The decoded ErlTuple
     * @throws IOException if decoding fails
     */
    private ErlTuple decodeSmallTuple() throws IOException {
        int arity = buffer.get() & 0xFF;
        ErlTerm[] elements = new ErlTerm[arity];

        for (int i = 0; i < arity; i++) {
            elements[i] = decodeTerm();
        }

        return ErlTuple.of(elements);
    }

    /**
     * Decodes a large tuple from the buffer.
     *
     * @return The decoded ErlTuple
     * @throws IOException if decoding fails
     */
    private ErlTuple decodeLargeTuple() throws IOException {
        int arity = buffer.getInt();
        ErlTerm[] elements = new ErlTerm[arity];

        for (int i = 0; i < arity; i++) {
            elements[i] = decodeTerm();
        }

        return ErlTuple.of(elements);
    }

    /**
     * Decodes a map from the buffer.
     *
     * @return The decoded ErlMap
     * @throws IOException if decoding fails
     */
    private ErlMap decodeMap() throws IOException {
        int arity = buffer.getInt();

        if (arity == 0) {
            return ErlMap.of();
        }

        java.util.Map<ErlTerm, ErlTerm> entries = new java.util.TreeMap<>();
        for (int i = 0; i < arity; i++) {
            ErlTerm key = decodeTerm();
            ErlTerm value = decodeTerm();
            entries.put(key, value);
        }

        return ErlMap.of(entries);
    }

    /**
     * Decodes a new float (IEEE 754 double) from the buffer.
     *
     * @return The decoded ErlDouble
     * @throws IOException if decoding fails
     */
    private ErlDouble decodeNewFloat() throws IOException {
        long bits = buffer.getLong();
        return ErlDouble.of(Double.longBitsToDouble(bits));
    }
}