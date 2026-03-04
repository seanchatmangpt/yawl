/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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

package org.yawlfoundation.yawl.nativebridge.erlang;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Record implementation representing an Erlang tuple.
 * Tuples are ordered collections of Erlang terms.
 */
public final class ErlTuple implements ErlTerm {

    private final ErlTerm[] elements;

    /**
     * Creates a new ErlTuple with the given elements.
     * The elements are copied to ensure immutability.
     *
     * @param elements The tuple elements
     * @throws IllegalArgumentException if elements is null or empty
     */
    public ErlTuple(ErlTerm[] elements) {
        if (elements == null) {
            throw new IllegalArgumentException("Tuple elements cannot be null");
        }
        if (elements.length == 0) {
            throw new IllegalArgumentException("Tuple cannot be empty");
        }
        // Validate all elements are not null
        for (ErlTerm element : elements) {
            if (element == null) {
                throw new IllegalArgumentException("Tuple elements cannot be null");
            }
        }
        this.elements = elements.clone();
    }

    /**
     * Creates a new ErlTuple from a list of ErlTerm.
     *
     * @param elements The list of tuple elements
     * @return A new ErlTuple instance
     */
    public static ErlTuple of(List<ErlTerm> elements) {
        return new ErlTuple(elements.toArray(new ErlTerm[0]));
    }

    /**
     * Creates a new ErlTuple with varargs.
     *
     * @param elements The tuple elements
     * @return A new ErlTuple instance
     */
    public static ErlTuple of(ErlTerm... elements) {
        return new ErlTuple(elements.clone());
    }

    /**
     * Gets the tuple elements.
     * Returns a copy to maintain immutability.
     *
     * @return A copy of the tuple elements
     */
    public ErlTerm[] getElements() {
        return elements.clone();
    }

    /**
     * Gets the arity of the tuple.
     *
     * @return The number of elements in the tuple
     */
    public int getArity() {
        return elements.length;
    }

    /**
     * Gets an element at the specified index.
     *
     * @param index The element index
     * @return The ErlTerm at the index
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public ErlTerm getElement(int index) {
        if (index < 0 || index >= elements.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + elements.length);
        }
        return elements[index];
    }

    @Override
    public String toErlString() {
        return Arrays.stream(elements)
                .map(ErlTerm::toErlString)
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException {
        try {
            // Start tuple encoding
            int result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_tuple_header(buffer, elements.length);
            if (result == -1) {
                throw new ErlangException("Failed to encode tuple header: arity=" + elements.length);
            }

            // Encode each element
            for (ErlTerm element : elements) {
                result = element.encodeTo(buffer);
                if (result == -1) {
                    throw new ErlangException("Failed to encode tuple element: " + element);
                }
            }

            // End tuple encoding
            result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_tuple_end(buffer);
            if (result == -1) {
                throw new ErlangException("Failed to encode tuple end");
            }

            return 0;
        } catch (Exception e) {
            throw new ErlangException("Encoding error for tuple: arity=" + elements.length, e);
        }
    }

    @Override
    public int getType() {
        return org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_SMALL_TUPLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlTuple erlTuple = (ErlTuple) o;
        return Arrays.equals(elements, erlTuple.elements);
    }

    @Override
    public boolean equals(ErlTerm other) {
        if (this == other) return true;
        if (other instanceof ErlTuple) {
            return Arrays.equals(elements, ((ErlTuple) other).elements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public String toString() {
        return "ErlTuple{arity=" + elements.length + "}";
    }
}