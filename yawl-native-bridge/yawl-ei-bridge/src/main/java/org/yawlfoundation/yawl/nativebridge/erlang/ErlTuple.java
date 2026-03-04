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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

import java.util.Arrays;

/**
 * Stub implementation of Erlang tuple.
 */
public class ErlTuple implements ErlTerm {

    private final ErlTerm[] elements;

    private ErlTuple(ErlTerm[] elements) {
        this.elements = elements;
    }

    public static ErlTuple of(ErlTerm... elements) {
        return new ErlTuple(elements.clone());
    }

    @Override
    public int encodeTo(Object buffer) {
        throw new UnsupportedOperationException(
            "encodeTo() requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    @Override
    public String toString() {
        return "{" + String.join(", ", Arrays.stream(elements).map(Object::toString).toArray(String[]::new)) + "}";
    }
}