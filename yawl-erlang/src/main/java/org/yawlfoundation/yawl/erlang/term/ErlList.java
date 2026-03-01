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
 * Erlang list. A sequence of elements with a tail.
 *
 * <p>Proper lists have {@code tail = ErlNil.INSTANCE}. Improper lists have a non-nil tail,
 * representing patterns like {@code [a|b]} (list with tail b).</p>
 *
 * <p>Examples:
 * <ul>
 *   <li>Proper list [1,2,3]: elements=[1,2,3], tail=ErlNil.INSTANCE</li>
 *   <li>Improper list [1|2]: elements=[1], tail=2</li>
 * </ul>
 */
public record ErlList(List<ErlTerm> elements, ErlTerm tail) implements ErlTerm {

    /**
     * Constructs an ErlList with elements and tail.
     *
     * @param elements non-null list of terms (defensively copied)
     * @param tail non-null tail term (use ErlNil.INSTANCE for proper lists)
     * @throws IllegalArgumentException if elements or tail is null
     */
    public ErlList {
        if (elements == null) {
            throw new IllegalArgumentException("ErlList elements must not be null");
        }
        if (tail == null) {
            throw new IllegalArgumentException(
                "ErlList tail must not be null (use ErlNil.INSTANCE for proper lists)");
        }
        elements = List.copyOf(elements);
    }

    /**
     * Convenience constructor for proper lists (tail = ErlNil.INSTANCE).
     *
     * @param elements non-null list of terms
     */
    public ErlList(List<ErlTerm> elements) {
        this(elements, ErlNil.INSTANCE);
    }
}
