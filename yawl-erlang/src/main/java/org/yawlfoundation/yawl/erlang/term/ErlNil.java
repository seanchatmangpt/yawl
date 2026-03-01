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
 * Erlang empty list ({@code []}). ETF tag: NIL_EXT (106).
 *
 * <p>The unique instance {@link #INSTANCE} should be reused instead of creating new instances.
 * This record type is primarily used as the tail of proper lists.</p>
 */
public record ErlNil() implements ErlTerm {

    /** Singleton constant. Reuse this instance instead of creating new ones. */
    public static final ErlNil INSTANCE = new ErlNil();
}
