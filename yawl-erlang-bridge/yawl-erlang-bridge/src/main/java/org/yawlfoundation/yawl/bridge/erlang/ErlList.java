package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Erlang list in the YAWL Erlang bridge.
 *
 * <p>An Erlang list is a sequence of elements. The empty list is represented
 * as []. Lists can be heterogenous.</p>
 *
 * @since 1.0.0
 */
public final class ErlList implements ErlTerm {

    private final List<ErlTerm> elements;
    private final boolean isProper; // true for proper lists, false for improper lists

    /**
     * Constructs an ErlList with the given elements.
     *
     * @param elements The list elements (must not contain null)
     * @throws IllegalArgumentException if elements contains null
     */
    public ErlList(ErlTerm... elements) {
        this(Arrays.asList(elements));
    }

    /**
     * Constructs an ErlList with the given elements.
     *
     * @param elements The list elements (must not contain null)
     * @throws IllegalArgumentException if elements contains null
     */
    public ErlList(List<ErlTerm> elements) {
        this(elements, true);
    }

    /**
     * Constructs an ErlList with the given elements and properness flag.
     *
     * @param elements The list elements (must not contain null)
     * @param isProper true for proper lists, false for improper lists
     * @throws IllegalArgumentException if elements contains null
     */
    public ErlList(List<ErlTerm> elements, boolean isProper) {
        Objects.requireNonNull(elements, "List elements cannot be null");

        // Check for null elements
        for (ErlTerm element : elements) {
            Objects.requireNonNull(element, "List elements cannot contain null");
        }

        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
        this.isProper = isProper;
    }

    /**
     * Returns the list elements.
     *
     * @return The list elements (unmodifiable)
     */
    public List<ErlTerm> getElements() {
        return elements;
    }

    /**
     * Returns whether this is a proper list.
     *
     * @return true for proper lists, false for improper lists
     */
    public boolean isProper() {
        return isProper;
    }

    /**
     * Returns whether this list is empty.
     *
     * @return true if the list is empty
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            if (isEmpty()) {
                buffer.encodeEmptyList();
                return;
            }

            if (isProper) {
                // Encode as proper list
                buffer.encodeListHeader(elements.size());
                for (ErlTerm element : elements) {
                    element.encodeToEiBuffer(buffer);
                }
                buffer.encodeEmptyList(); // Terminate with nil
            } else {
                // Encode as improper list - encode all elements except last
                if (elements.size() > 0) {
                    buffer.encodeListHeader(elements.size() - 1);
                    for (int i = 0; i < elements.size() - 1; i++) {
                        elements.get(i).encodeToEiBuffer(buffer);
                    }
                    // Last element is not terminated
                    if (elements.size() > 0) {
                        elements.get(elements.size() - 1).encodeToEiBuffer(buffer);
                    }
                }
            }
        } catch (IOException e) {
            throw new ErlangException("Failed to encode list", e);
        }
    }

    @Override
    public byte[] encodeETF() throws ErlangException {
        try {
            EiBuffer buffer = new EiBuffer();
            // Add external term tag
            buffer.put((byte) 131); // EXTERNAL_TERM_TAG

            if (isEmpty()) {
                buffer.put((byte) 106); // NIL_EXT
            } else {
                // Use LIST_EXT for proper lists
                if (isProper) {
                    buffer.put((byte) 108); // LIST_EXT
                    buffer.putInt(elements.size());
                    for (ErlTerm element : elements) {
                        element.encodeToEiBuffer(buffer);
                    }
                    buffer.put((byte) 106); // NIL_EXT terminator
                } else {
                    // Encode improper list as tuple with tail
                    ErlTerm[] elementsArray = elements.toArray(new ErlTerm[0]);
                    if (elementsArray.length == 0) {
                        buffer.put((byte) 106); // NIL_EXT
                    } else {
                        buffer.put((byte) 104); // SMALL_TUPLE_EXT
                        buffer.put((byte) elementsArray.length);
                        for (ErlTerm element : elementsArray) {
                            element.encodeToEiBuffer(buffer);
                        }
                    }
                }
            }
            return buffer.toArray();
        } catch (IOException e) {
            throw new ErlangException("Failed to encode list to ETF", e);
        }
    }

    @Override
    public String asString() {
        if (isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(elements.get(i).asString());
        }

        sb.append(isProper ? "]" : " | ...]");
        return sb.toString();
    }

    @Override
    public String type() {
        return "list";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlList erlList = (ErlList) o;
        return isProper == erlList.isProper && elements.equals(erlList.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements, isProper);
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a proper list from the given elements.
     *
     * @param elements The list elements
     * @return A new ErlList instance
     */
    public static ErlList of(ErlTerm... elements) {
        return new ErlList(elements);
    }

    /**
     * Creates a proper list from the given element list.
     *
     * @param elements The list elements
     * @return A new ErlList instance
     */
    public static ErlList of(List<ErlTerm> elements) {
        return new ErlList(elements);
    }
}