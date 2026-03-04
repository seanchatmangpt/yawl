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

/**
 * Sealed interface representing an Erlang term in the JVM↔BEAM bridge.
 * This forms the foundation for type-safe Erlang data representation.
 */
public sealed interface ErlTerm
        permits ErlAtom, ErlList, ErlTuple, ErlBinary, ErlLong {

    /**
     * Converts this Erlang term to its string representation.
     * Used for debugging and logging purposes.
     *
     * @return String representation of the Erlang term
     */
    String toErlString();

    /**
     * Encodes this Erlang term into an ei_x_buff buffer.
     * This is the primary mechanism for converting Java objects to Erlang format.
     *
     * @param buffer The ei_x_buff buffer to encode into
     * @return 0 on success, -1 on failure
     * @throws ErlangException if encoding fails
     */
    int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException;

    /**
     * Returns the type of this Erlang term.
     * Corresponds to Erlang term types like atom, list, tuple, etc.
     *
     * @return The Erlang term type
     */
    int getType();

    /**
     * Checks if this term is equal to another Erlang term.
     * Performs deep comparison for complex types like tuples and lists.
     *
     * @param other The Erlang term to compare with
     * @return true if equal, false otherwise
     */
    boolean equals(ErlTerm other);
}