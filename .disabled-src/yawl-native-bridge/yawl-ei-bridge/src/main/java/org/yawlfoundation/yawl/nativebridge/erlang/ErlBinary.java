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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Record implementation representing an Erlang binary.
 * Binary data is stored as a byte array.
 */
public final class ErlBinary implements ErlTerm {

    private final byte[] data;

    /**
     * Creates a new ErlBinary with the given data.
     * The data is copied to ensure immutability.
     *
     * @param data The binary data
     * @throws IllegalArgumentException if data is null
     */
    public ErlBinary(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Binary data cannot be null");
        }
        this.data = data.clone();
    }

    /**
     * Creates a new ErlBinary from a string using UTF-8 encoding.
     *
     * @param text The text to encode
     * @return A new ErlBinary instance
     */
    public static ErlBinary fromString(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        return new ErlBinary(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the binary data.
     * Returns a copy to maintain immutability.
     *
     * @return A copy of the binary data
     */
    public byte[] getData() {
        return data.clone();
    }

    /**
     * Gets the length of the binary data.
     *
     * @return The length in bytes
     */
    public int length() {
        return data.length;
    }

    @Override
    public String toErlString() {
        return "<<\"" + new String(data, StandardCharsets.UTF_8) + "\">>";
    }

    @Override
    public int encodeTo(org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_buff_t buffer) throws ErlangException {
        try {
            int result = org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_encode_binary(buffer, data, data.length);
            if (result == -1) {
                throw new ErlangException("Failed to encode binary: length=" + data.length);
            }
            return result;
        } catch (Exception e) {
            throw new ErlangException("Encoding error for binary: length=" + data.length, e);
        }
    }

    @Override
    public int getType() {
        return org.yawlfoundation.yawl.nativebridge.erlang.generated.ei_x_type_t.ERL_BINARY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErlBinary erlBinary = (ErlBinary) o;
        return Arrays.equals(data, erlBinary.data);
    }

    @Override
    public boolean equals(ErlTerm other) {
        if (this == other) return true;
        if (other instanceof ErlBinary) {
            return Arrays.equals(data, ((ErlBinary) other).data);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "ErlBinary{length=" + data.length + "}";
    }
}