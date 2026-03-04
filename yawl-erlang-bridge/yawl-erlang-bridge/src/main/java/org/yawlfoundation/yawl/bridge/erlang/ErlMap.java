package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Represents an Erlang map in the YAWL Erlang bridge.
 *
 * <p>An Erlang map is a key-value store introduced in Erlang/OTP 17.
 * Keys must be comparable (atoms, integers, tuples, etc.).</p>
 *
 * <p>Example maps:
 * #{name => "John", age => 30}
 * #{a => 1, b => 2, c => 3}</p>
 *
 * @since 1.0.0
 */
public final class ErlMap implements ErlTerm {

    private final List<ErlTerm> keys;
    private final List<ErlTerm> values;
    private final int size;

    /**
     * Constructs an ErlMap from the given key-value pairs.
     *
     * @param entries The map entries (must not contain null keys or values)
     * @throws IllegalArgumentException if entries contains null keys or values
     */
    public ErlMap(Map<ErlTerm, ErlTerm> entries) {
        Objects.requireNonNull(entries, "Map entries cannot be null");

        // Check for null keys and values
        for (Map.Entry<ErlTerm, ErlTerm> entry : entries.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "Map keys cannot be null");
            Objects.requireNonNull(entry.getValue(), "Map values cannot be null");
        }

        // Sort keys for deterministic encoding (Erlang maps require ordered keys)
        this.keys = new ArrayList<>(entries.keySet());
        keys.sort(null); // Use natural ordering

        this.values = new ArrayList<>(entries.values());
        this.size = entries.size();
    }

    /**
     * Returns the map keys.
     *
     * @return The map keys (unmodifiable)
     */
    public List<ErlTerm> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    /**
     * Returns the map values.
     *
     * @return The map values (unmodifiable)
     */
    public List<ErlTerm> getValues() {
        return Collections.unmodifiableList(values);
    }

    /**
     * Returns the number of entries in the map.
     *
     * @return The map size
     */
    public int size() {
        return size;
    }

    /**
     * Checks if the map is empty.
     *
     * @return true if the map has no entries
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Gets the value for the given key.
     *
     * @param key The key to look up
     * @return The value associated with the key
     * @throws IllegalArgumentException if key is null
     */
    public ErlTerm get(ErlTerm key) {
        Objects.requireNonNull(key, "Map key cannot be null");

        int index = keys.indexOf(key);
        if (index == -1) {
            return null;
        }
        return values.get(index);
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            if (size == 0) {
                // Empty map: 131 (MAP_EXT) + 4 (size=0) + 0 (associations)
                buffer.put((byte) 131); // MAP_EXT
                buffer.putInt(0);
                return;
            }

            // Map header: 131 (MAP_EXT) + 4 (size) + 4 * size * element_size
            buffer.put((byte) 131);
            buffer.putInt(size);

            // Encode key-value pairs in sorted order
            for (int i = 0; i < size; i++) {
                keys.get(i).encodeToEiBuffer(buffer);
                values.get(i).encodeToEiBuffer(buffer);
            }
        } catch (IOException e) {
            throw new ErlangException("Failed to encode map", e);
        }
    }

    @Override
    public byte[] encodeETF() throws ErlangException {
        try {
            EiBuffer buffer = new EiBuffer();
            // Add external term tag
            buffer.put((byte) 131); // EXTERNAL_TERM_TAG

            if (size == 0) {
                // Empty map: 131 (MAP_EXT) + 4 (size=0) + 0 (associations)
                buffer.put((byte) 116); // MAP_EXT
                buffer.putInt(0);
                return buffer.toArray();
            }

            buffer.put((byte) 116); // MAP_EXT
            buffer.putInt(size);

            // Encode key-value pairs in sorted order
            for (int i = 0; i < size; i++) {
                keys.get(i).encodeToEiBuffer(buffer);
                values.get(i).encodeToEiBuffer(buffer);
            }

            return buffer.toArray();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode map to ETF", e);
        }
    }

    @Override
    public String asString() {
        if (isEmpty()) {
            return "#{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#{");

        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(keys.get(i).asString())
              .append(" => ")
              .append(values.get(i).asString());
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public String type() {
        return "map";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlMap erlMap = (ErlMap) o;
        return keys.equals(erlMap.keys) && values.equals(erlMap.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys, values);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a new ErlMap from the given key-value pairs.
     *
     * @param entries The map entries
     * @return A new ErlMap instance
     */
    public static ErlMap of(Map<ErlTerm, ErlTerm> entries) {
        return new ErlMap(entries);
    }

    /**
     * Creates a new ErlMap from the given key-value pairs.
     *
     * @param keyPairs The key-value pairs (must be even length)
     * @return A new ErlMap instance
     * @throws IllegalArgumentException if keyPairs length is odd or contains null
     */
    public static ErlMap of(ErlTerm... keyPairs) {
        if (keyPairs == null || keyPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key pairs must have even length");
        }

        Map<ErlTerm, ErlTerm> map = new TreeMap<>();
        for (int i = 0; i < keyPairs.length; i += 2) {
            ErlTerm key = keyPairs[i];
            ErlTerm value = keyPairs[i + 1];

            if (key == null || value == null) {
                throw new IllegalArgumentException("Key and value cannot be null");
            }

            map.put(key, value);
        }

        return new ErlMap(map);
    }
}