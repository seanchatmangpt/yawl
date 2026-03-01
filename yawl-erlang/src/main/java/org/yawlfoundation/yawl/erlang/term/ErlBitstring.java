/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
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
package org.yawlfoundation.yawl.erlang.term;

/**
 * Erlang bitstring ({@code <<Data/bits>>}). Extends binary to support non-byte-aligned data.
 *
 * <p>The last byte of the data array may contain fewer than 8 significant bits.
 * The {@code bitsInLastByte} field (1-8) specifies how many bits in the last byte are valid.</p>
 *
 * <p>Example: {@code <<1,2,3:6>>} (bits 1,2, and 6 bits of 3) is encoded as:
 * <ul>
 *   <li>data = [1, 2, 3]</li>
 *   <li>bitsInLastByte = 6</li>
 * </ul>
 */
public record ErlBitstring(byte[] data, int bitsInLastByte) implements ErlTerm {

    /**
     * Constructs an ErlBitstring with data and bit count for the last byte.
     *
     * @param data non-null byte array
     * @param bitsInLastByte number of valid bits in the last byte (1-8)
     * @throws IllegalArgumentException if data is null or bitsInLastByte is outside 1-8
     */
    public ErlBitstring {
        if (data == null) {
            throw new IllegalArgumentException("ErlBitstring data must not be null");
        }
        if (bitsInLastByte < 1 || bitsInLastByte > 8) {
            throw new IllegalArgumentException(
                "bitsInLastByte must be 1..8, got " + bitsInLastByte);
        }
    }
}
