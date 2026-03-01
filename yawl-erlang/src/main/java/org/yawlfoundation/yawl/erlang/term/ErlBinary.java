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
 * Erlang binary ({@code <<...>>}). Represents a sequence of bytes.
 *
 * <p>OTP has a size limit of 2GB per binary, but in practice binaries larger than
 * approximately 1GB may face delivery issues over the distribution protocol.
 * This record enforces a 1GB safe limit to avoid silent failures.</p>
 */
public record ErlBinary(byte[] data) implements ErlTerm {

    /**
     * Constructs an ErlBinary with byte data.
     *
     * @param data non-null byte array
     * @throws IllegalArgumentException if data is null or exceeds 1GB safe limit
     */
    public ErlBinary {
        if (data == null) {
            throw new IllegalArgumentException("ErlBinary data must not be null");
        }
        // OTP limit: binaries > 2GB cannot be sent over distribution.
        // We enforce a 1GB safe limit to be conservative.
        if ((long) data.length > (1L << 30)) {
            throw new IllegalArgumentException(
                "ErlBinary size " + data.length + " exceeds 1GB safe limit");
        }
    }
}
