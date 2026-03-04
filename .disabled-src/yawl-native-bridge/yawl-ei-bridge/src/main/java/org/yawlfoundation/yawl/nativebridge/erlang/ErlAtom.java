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

import java.util.Objects;

/**
 * Record implementation representing an Erlang atom.
 * Atoms are symbolic names that start with a lowercase letter or are quoted.
 */
public final class ErlAtom implements ErlTerm {

    private final String value;

    /**
     * Creates a new ErlAtom with the given value.
     * The value must be a valid Erlang atom (no spaces unless quoted).
     *
     * @param value The atom value
     * @throws IllegalArgumentException if value is null or empty
     */
    public ErlAtom(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Atom value cannot be null or empty");
        }
        this.value = value.trim();
    }

    /**
     * Gets the atom value.
     *
     * @return The atom value
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toErlString() {
        return "\"" + value + "\"";
    }

    @Override
    public int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException {
        try {
            int result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_atom(buffer, value);
            if (result == -1) {
                throw new ErlangException("Failed to encode atom: " + value);
            }
            return result;
        } catch (Exception e) {
            throw new ErlangException("Encoding error for atom: " + value, e);
        }
    }

    @Override
    public int getType() {
        return org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_ATOM;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlAtom erlAtom = (ErlAtom) o;
        return Objects.equals(value, erlAtom.value);
    }

    @Override
    public boolean equals(ErlTerm other) {
        if (this == other) return true;
        if (other instanceof ErlAtom) {
            return Objects.equals(value, ((ErlAtom) other).value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ErlAtom{" + value + "}";
    }

    /**
     * Factory method to create an ErlAtom from a string.
     *
     * @param value The atom value
     * @return A new ErlAtom instance
     */
    public static ErlAtom atom(String value) {
        return new ErlAtom(value);
    }

    /**
     * Common atom instances for frequent use.
     */
    public static final ErlAtom ATOM_TRUE = new ErlAtom("true");
    public static final ErlAtom ATOM_FALSE = new ErlAtom("false");
    public static final ErlAtom ATOM_OK = new ErlAtom("ok");
    public static final ErlAtom ATOM_ERROR = new ErlAtom("error");
    public static final ErlAtom ATOM_NIL = new ErlAtom("nil");
    public static final ErlAtom ATOM_UNDEFINED = new ErlAtom("undefined");
}