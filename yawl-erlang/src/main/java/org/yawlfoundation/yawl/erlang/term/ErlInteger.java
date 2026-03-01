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

import java.math.BigInteger;

/**
 * Erlang integer. Covers three ETF encodings:
 * <ul>
 *   <li>SMALL_INTEGER_EXT: 0..255 (1 byte)</li>
 *   <li>INTEGER_EXT: ±2³¹-1 (4 bytes signed)</li>
 *   <li>SMALL_BIG_EXT / LARGE_BIG_EXT: arbitrary precision (big-endian little-endian magnitude)</li>
 * </ul>
 *
 * <p>The codec chooses the most efficient encoding based on the value's magnitude.</p>
 */
public record ErlInteger(BigInteger value) implements ErlTerm {

    /**
     * Constructs an ErlInteger from a BigInteger.
     *
     * @param value non-null arbitrary-precision integer
     * @throws IllegalArgumentException if value is null
     */
    public ErlInteger {
        if (value == null) {
            throw new IllegalArgumentException("ErlInteger value must not be null");
        }
    }

    /**
     * Convenience constructor for long values. Converts to BigInteger internally.
     *
     * @param value a long value
     */
    public ErlInteger(long value) {
        this(BigInteger.valueOf(value));
    }
}
