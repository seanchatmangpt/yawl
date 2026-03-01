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

import java.util.Map;

/**
 * Erlang map ({@code #{key => value}}). An immutable key-value data structure.
 *
 * <p>Both keys and values are arbitrary ErlTerms. Maps are encoded as MAP_EXT (tag 116)
 * and preserve insertion order for iteration.</p>
 *
 * <p>Example: {@code #{a => 1, b => 2}}</p>
 */
public record ErlMap(Map<ErlTerm, ErlTerm> entries) implements ErlTerm {

    /**
     * Constructs an ErlMap with the given key-value entries.
     *
     * @param entries non-null map of ErlTerm keys to ErlTerm values (defensively copied)
     * @throws IllegalArgumentException if entries is null
     */
    public ErlMap {
        if (entries == null) {
            throw new IllegalArgumentException("ErlMap entries must not be null");
        }
        entries = Map.copyOf(entries);
    }
}
