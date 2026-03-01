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
 * Erlang external function reference ({@code fun Module:Function/Arity}).
 * Represents a reference to a named function without a bound environment.
 *
 * <p>Encoded as EXPORT_EXT (tag 113). Example: {@code fun erlang:node/0}</p>
 */
public record ErlExternalFun(String module, String function, int arity) implements ErlFun {

    /**
     * Constructs an ErlExternalFun.
     *
     * @param module non-null module name (e.g., "erlang")
     * @param function non-null function name
     * @param arity function arity (0-255)
     * @throws IllegalArgumentException if module/function is null or arity is outside 0-255
     */
    public ErlExternalFun {
        if (module == null) {
            throw new IllegalArgumentException("ErlExternalFun module must not be null");
        }
        if (function == null) {
            throw new IllegalArgumentException("ErlExternalFun function must not be null");
        }
        if (arity < 0 || arity > 255) {
            throw new IllegalArgumentException(
                "ErlExternalFun arity must be 0..255, got " + arity);
        }
    }
}
