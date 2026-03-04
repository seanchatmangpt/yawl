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
 * Record implementation representing an Erlang integer/long.
 * Handles both small integers and large integers (longs).
 */
public final class ErlLong implements ErlTerm {

    private final long value;

    /**
     * Creates a new ErlLong with the given value.
     *
     * @param value The integer value
     */
    public ErlLong(long value) {
        this.value = value;
    }

    /**
     * Gets the long value.
     *
     * @return The long value
     */
    public long getValue() {
        return value;
    }

    @Override
    public String toErlString() {
        return Long.toString(value);
    }

    @Override
    public int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException {
        try {
            // Use ei_x_encode_long for all integers
            int result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_long(buffer, value);
            if (result == -1) {
                throw new ErlangException("Failed to encode long: " + value);
            }
            return result;
        } catch (Exception e) {
            throw new ErlangException("Encoding error for long: " + value, e);
        }
    }

    @Override
    public int getType() {
        // Use ERL_INTEGER for all longs
        return org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_INTEGER;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlLong erlLong = (ErlLong) o;
        return value == erlLong.value;
    }

    @Override
    public boolean equals(ErlTerm other) {
        if (this == other) return true;
        if (other instanceof ErlLong) {
            return value == ((ErlLong) other).value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "ErlLong{" + value + "}";
    }

    /**
     * Factory method to create an ErlLong from a long value.
     *
     * @param value The integer value
     * @return A new ErlLong instance
     */
    public static ErlLong longValue(long value) {
        return new ErlLong(value);
    }

    /**
     * Factory method to create an ErlLong from an int value.
     *
     * @param value The integer value
     * @return A new ErlLong instance
     */
    public static ErlLong longValue(int value) {
        return new ErlLong(value);
    }
}