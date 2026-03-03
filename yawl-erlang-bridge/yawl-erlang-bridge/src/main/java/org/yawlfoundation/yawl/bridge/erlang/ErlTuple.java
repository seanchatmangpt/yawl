package org.yawlfoundation.yawl.bridge.erlang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Erlang tuple in the YAWL Erlang bridge.
 *
 * <p>An Erlang tuple is a fixed-size collection of elements, similar to a record.
 * Examples: {hello, world}, {a, b, c}, {ok, Result}</p>
 *
 * @since 1.0.0
 */
public final class ErlTuple implements ErlTerm {

    private final List<ErlTerm> elements;

    /**
     * Constructs an ErlTuple with the given elements.
     *
     * @param elements The tuple elements (must not contain null)
     * @throws IllegalArgumentException if elements contains null
     */
    public ErlTuple(ErlTerm... elements) {
        this(Arrays.asList(elements));
    }

    /**
     * Constructs an ErlTuple with the given elements.
     *
     * @param elements The tuple elements (must not contain null)
     * @throws IllegalArgumentException if elements contains null
     */
    public ErlTuple(List<ErlTerm> elements) {
        Objects.requireNonNull(elements, "Tuple elements cannot be null");

        // Check for null elements
        for (ErlTerm element : elements) {
            Objects.requireNonNull(element, "Tuple elements cannot contain null");
        }

        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /**
     * Returns the tuple elements.
     *
     * @return The tuple elements (unmodifiable)
     */
    public List<ErlTerm> getElements() {
        return elements;
    }

    /**
     * Returns the tuple arity (number of elements).
     *
     * @return The number of elements in the tuple
     */
    public int arity() {
        return elements.size();
    }

    /**
     * Returns the element at the given index.
     *
     * @param index The element index (0-based)
     * @return The element at the index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public ErlTerm get(int index) {
        return elements.get(index);
    }

    /**
     * Returns the first element of the tuple.
     *
     * @return The first element
     * @throws IndexOutOfBoundsException if tuple is empty
     */
    public ErlTerm getFirst() {
        return elements.get(0);
    }

    /**
     * Returns the second element of the tuple.
     *
     * @return The second element
     * @throws IndexOutOfBoundsException if tuple has less than 2 elements
     */
    public ErlTerm getSecond() {
        return elements.get(1);
    }

    /**
     * Returns the third element of the tuple.
     *
     * @return The third element
     * @throws IndexOutOfBoundsException if tuple has less than 3 elements
     */
    public ErlTerm getThird() {
        return elements.get(2);
    }

    /**
     * Checks if this tuple has at least the given arity.
     *
     * @param minArity The minimum arity to check for
     * @return true if tuple arity >= minArity
     */
    @Override
    public boolean hasArity(int minArity) {
        return arity() >= minArity;
    }

    @Override
    public void encodeToEiBuffer(EiBuffer buffer) throws ErlangException {
        try {
            buffer.encodeTupleHeader(arity());
            for (ErlTerm element : elements) {
                element.encodeToEiBuffer(buffer);
            }
        } catch (IOException e) {
            throw new ErlangException("Failed to encode tuple", e);
        }
    }

    @Override
    public String asString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(elements.get(i).asString());
        }

        sb.append("}");
        return sb.toString();
    }

    @Override
    public String type() {
        return "tuple";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlTuple erlTuple = (ErlTuple) o;
        return elements.equals(erlTuple.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public String toString() {
        return asString();
    }

    /**
     * Creates a new tuple from the given elements.
     *
     * @param elements The tuple elements
     * @return A new ErlTuple instance
     */
    public static ErlTuple of(ErlTerm... elements) {
        return new ErlTuple(elements);
    }

    /**
     * Creates a new tuple from the given element list.
     *
     * @param elements The tuple elements
     * @return A new ErlTuple instance
     */
    public static ErlTuple of(List<ErlTerm> elements) {
        return new ErlTuple(elements);
    }

    /**
     * Creates a 1-tuple with the given element.
     *
     * @param element The single element
     * @return A new 1-element tuple
     */
    public static ErlTuple one(ErlTerm element) {
        return new ErlTuple(element);
    }

    /**
     * Creates a 2-tuple (pair) with the given elements.
     *
     * @param first The first element
     * @param second The second element
     * @return A new 2-element tuple
     */
    public static ErlTuple two(ErlTerm first, ErlTerm second) {
        return new ErlTuple(first, second);
    }

    /**
     * Creates a 3-tuple with the given elements.
     *
     * @param first The first element
     * @param second The second element
     * @param third The third element
     * @return A new 3-element tuple
     */
    public static ErlTuple three(ErlTerm first, ErlTerm second, ErlTerm third) {
        return new ErlTuple(first, second, third);
    }
}