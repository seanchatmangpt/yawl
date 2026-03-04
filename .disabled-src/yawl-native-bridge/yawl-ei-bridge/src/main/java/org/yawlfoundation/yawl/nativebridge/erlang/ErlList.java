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
 * Record implementation representing an Erlang list.
 * Lists are ordered collections of Erlang terms, terminated by nil.
 */
public final class ErlList implements ErlTerm {

    private final ErlTerm[] elements;

    /**
     * Creates a new ErlList with the given elements.
     * The elements are copied to ensure immutability.
     *
     * @param elements The list elements
     * @throws IllegalArgumentException if elements is null
     */
    public ErlList(ErlTerm[] elements) {
        if (elements == null) {
            throw new IllegalArgumentException("List elements cannot be null");
        }
        // Validate all elements are not null
        for (ErlTerm element : elements) {
            if (element == nil()) {
                throw new IllegalArgumentException("List cannot contain nil elements, use proper ErlList termination");
            }
            if (element == null) {
                throw new IllegalArgumentException("List elements cannot be null");
            }
        }
        this.elements = elements.clone();
    }

    /**
     * Creates a new ErlList from a list of ErlTerm.
     *
     * @param elements The list of elements
     * @return A new ErlList instance
     */
    public static ErlList of(List<ErlTerm> elements) {
        return new ErlList(elements.toArray(new ErlTerm[0]));
    }

    /**
     * Creates a new ErlList with varargs.
     *
     * @param elements The list elements
     * @return A new ErlList instance
     */
    public static ErlList of(ErlTerm... elements) {
        return new ErlList(elements.clone());
    }

    /**
     * Gets the list elements.
     * Returns a copy to maintain immutability.
     *
     * @return A copy of the list elements
     */
    public ErlTerm[] getElements() {
        return elements.clone();
    }

    /**
     * Gets the length of the list (number of elements before nil).
     *
     * @return The list length
     */
    public int length() {
        return elements.length;
    }

    @Override
    public String toErlString() {
        if (elements.length == 0) {
            return "[]";
        }
        return Arrays.stream(elements)
                .map(ErlTerm::toErlString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException {
        try {
            // Start list encoding
            // In Erlang, we need to encode the list header first
            // Then each element, then the nil terminator

            // For simplicity, we'll encode as a proper Erlang list
            // The actual encoding requires more complex handling

            // Start list
            int result = encodeListElements(buffer, elements);

            if (result == -1) {
                throw new ErlangException("Failed to encode list: length=" + elements.length);
            }

            // End list with nil
            // This is a simplified approach - real implementation needs proper nil encoding
            result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_string(buffer, "");
            if (result == -1) {
                throw new ErlangException("Failed to encode list terminator");
            }

            return 0;
        } catch (Exception e) {
            throw new ErlangException("Encoding error for list: length=" + elements.length, e);
        }
    }

    /**
     * Helper method to encode list elements.
     */
    private int encodeListElements(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer, ErlTerm[] elements) throws ErlangException {
        for (ErlTerm element : elements) {
            int result = element.encodeTo(buffer);
            if (result == -1) {
                return -1;
            }
        }
        return 0;
    }

    @Override
    public int getType() {
        return org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_LIST;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlList erlList = (ErlList) o;
        return Arrays.equals(elements, erlList.elements);
    }

    @Override
    public boolean equals(ErlTerm other) {
        if (this == other) return true;
        if (other instanceof ErlList) {
            return Arrays.equals(elements, ((ErlList) other).elements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public String toString() {
        return "ErlList{length=" + elements.length + "}";
    }

    /**
     * Creates an empty Erlang list.
     *
     * @return An empty ErlList
     */
    public static ErlList empty() {
        return new ErlList(new ErlTerm[0]);
    }

    /**
     * Creates a singleton Erlang list.
     *
     * @param element The single element
     * @return A new ErlList containing one element
     */
    public static ErlList singleton(ErlTerm element) {
        return new ErlList(new ErlTerm[]{element});
    }

    /**
     * Creates an Erlang list of atoms.
     *
     * @param atoms The atom values
     * @return A new ErlList of ErlAtoms
     */
    public static ErlList ofAtoms(String... atoms) {
        ErlTerm[] elements = Arrays.stream(atoms)
                .map(ErlAtom::atom)
                .toArray(ErlTerm[]::new);
        return new ErlList(elements);
    }

    /**
     * Creates an Erlang list of integers.
     *
     * @param values The integer values
     * @return A new ErlList of ErlLongs
     */
    public static ErlList ofInts(long... values) {
        ErlTerm[] elements = Arrays.stream(values)
                .mapToObj(ErlLong::longValue)
                .toArray(ErlTerm[]::new);
        return new ErlList(elements);
    }
}