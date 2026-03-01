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

import java.nio.charset.StandardCharsets;

/**
 * Erlang atom. OTP 28 atoms are UTF-8 encoded, with a maximum of 255 UTF-8 bytes.
 *
 * <p>Examples: {@code true}, {@code false}, {@code error}, {@code ok}</p>
 */
public record ErlAtom(String value) implements ErlTerm {

    /**
     * Constructs an ErlAtom, validating the UTF-8 byte length does not exceed 255 bytes.
     *
     * @param value non-null atom string
     * @throws IllegalArgumentException if value is null or exceeds 255 UTF-8 bytes
     */
    public ErlAtom {
        if (value == null) {
            throw new IllegalArgumentException("ErlAtom value must not be null");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 255) {
            throw new IllegalArgumentException(
                "ErlAtom UTF-8 length " + bytes.length + " exceeds OTP 28 limit of 255 bytes");
        }
    }
}
