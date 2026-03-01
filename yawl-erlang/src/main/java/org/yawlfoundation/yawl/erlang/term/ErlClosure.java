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
 * Erlang closure (lambda with a captured environment).
 * Represents a function value that may have free variables bound from its creation context.
 *
 * <p>Encoded as NEW_FUN_EXT (tag 112). The closure metadata includes version identifiers
 * for the code, and a list of free variables captured at the time of creation.</p>
 */
public record ErlClosure(String module, int arity, byte[] uniq, int index, int oldIndex,
                         int oldUniq, ErlPid pid, List<ErlTerm> freeVars) implements ErlFun {

    /**
     * Constructs an ErlClosure.
     *
     * @param module non-null module name where the closure was created
     * @param arity function arity (0-255)
     * @param uniq unique hash identifying the closure code version (16 bytes, defensively copied)
     * @param index current code index
     * @param oldIndex old code index (for module reloads)
     * @param oldUniq old code hash
     * @param pid non-null PID of the creating process
     * @param freeVars non-null list of captured free variables (defensively copied)
     * @throws IllegalArgumentException if any non-optional field is null
     */
    public ErlClosure {
        if (module == null) {
            throw new IllegalArgumentException("ErlClosure module must not be null");
        }
        if (uniq == null) {
            throw new IllegalArgumentException("ErlClosure uniq must not be null");
        }
        if (pid == null) {
            throw new IllegalArgumentException("ErlClosure pid must not be null");
        }
        if (freeVars == null) {
            throw new IllegalArgumentException("ErlClosure freeVars must not be null");
        }
        uniq = uniq.clone();
        freeVars = List.copyOf(freeVars);
    }
}
