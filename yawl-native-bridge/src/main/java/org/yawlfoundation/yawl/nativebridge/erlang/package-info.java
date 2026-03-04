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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * JVM/BEAM Domain Layer Interfaces.
 *
 * <p>This package contains the Three-Domain Native Bridge interfaces for crossing
 * Boundary A (JVM → BEAM) via Unix domain socket and Erlang distribution protocol.</p>
 *
 * <h3>Layer Structure:</h3>
 * <ul>
 *   <li>Layer 3: {@link org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient} -
 *       High-level process mining API (pure Java 25 interface)</li>
 *   <li>Layer 2: {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlangNode} -
 *       Typed bridge with ei.h bindings and error conversion</li>
 *   <li>Layer 1: jextract-generated bindings from ei.h</li>
 *   <li>Transport: Unix domain socket (-proto_dist local, ~5-20µs)</li>
 * </ul>
 *
 * <h3>ErlTerm Sealed Interface Hierarchy:</h3>
 * <p>All Erlang External Term Format (ETF) types are represented as sealed classes:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ErlTerm} - Base interface</li>
 *   <li>Primitive types: {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlAtom},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlLong},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlBinary}</li>
 *   <li>Compound types: {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlList},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlTuple},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlMap}</li>
 *   <li>Process types: {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlPid},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlRef},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlPort}</li>
 *   <li>Function types: {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlFun},
 *       {@link org.yawlfoundation.yawl.nativebridge.erlang.ErlExternalFun}</li>
 * </ul>
 *
 * <h3>Error Model:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ErlangException} - Base exception</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ErlangConnectionException} - Connection failures</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ErlangRpcException} - RPC call failures</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ErlangExitException} - Process exit signals</li>
 *   <li>Automatic conversion from Erlang error terms ({@code {badrpc, {'EXIT', Reason}}})</li>
 * </ul>
 *
 * <h3>Capability Identifiers:</h3>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient.OcelId} -
 *       UUID handles for OCEL logs in BEAM domain</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient.SlimOcelId} -
 *       UUID handles for slim OCEL representations</li>
 *   <li>{@link org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient.PetriNetId} -
 *       UUID handles for mined Petri nets</li>
 * </ul>
 */
package org.yawlfoundation.yawl.nativebridge.erlang;