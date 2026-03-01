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
 * Sealed interface for Erlang External Term Format (ETF) types.
 * All 13 Erlang term types that appear over the distribution protocol are represented.
 *
 * <p>Use exhaustive pattern matching to process all term types:
 * <pre>
 *   switch (term) {
 *       case ErlAtom(var value) -> ...
 *       case ErlInteger(var value) -> ...
 *       case ErlFloat(var value) -> ...
 *       case ErlBinary(var data) -> ...
 *       case ErlBitstring(var data, var bits) -> ...
 *       case ErlNil() -> ...
 *       case ErlList(var elements, var tail) -> ...
 *       case ErlTuple(var elements) -> ...
 *       case ErlMap(var entries) -> ...
 *       case ErlPid(var node, var id, var serial, var creation) -> ...
 *       case ErlRef(var node, var ids, var creation) -> ...
 *       case ErlPort(var node, var id, var creation) -> ...
 *       case ErlFun fun -> ... // ExternalFun or Closure
 *   }
 * </pre>
 *
 * <p>All record types are immutable and support pattern destructuring.</p>
 */
public sealed interface ErlTerm
    permits ErlAtom, ErlInteger, ErlFloat, ErlBinary, ErlBitstring,
            ErlNil, ErlList, ErlTuple, ErlMap,
            ErlPid, ErlRef, ErlPort, ErlFun {
}
