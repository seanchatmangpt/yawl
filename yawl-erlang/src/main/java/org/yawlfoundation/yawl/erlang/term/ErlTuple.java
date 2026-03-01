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

import java.util.List;

/**
 * Erlang tuple ({@code {A, B, C}}). An immutable, fixed-length sequence of terms.
 *
 * <p>Zero-arity tuples ({@code {}}) are valid. The codec chooses between SMALL_TUPLE_EXT
 * (arity ≤ 255) and LARGE_TUPLE_EXT (arity > 255) based on element count.</p>
 *
 * <p>Tuples are often used as return values (e.g., {@code {ok, Result}}) or in record-like
 * structures in Erlang.</p>
 */
public record ErlTuple(List<ErlTerm> elements) implements ErlTerm {

    /**
     * Constructs an ErlTuple with the given elements.
     *
     * @param elements non-null list of terms (defensively copied)
     * @throws IllegalArgumentException if elements is null
     */
    public ErlTuple {
        if (elements == null) {
            throw new IllegalArgumentException("ErlTuple elements must not be null");
        }
        elements = List.copyOf(elements);
    }
}
