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
 * Three-Domain Native Bridge Pattern implementation for YAWL v7.
 * This module manages boundaries between JVM (QLever), BEAM (Erlang), and Rust domains,
 * providing fault isolation and high-performance inter-domain communication.
 *
 * <p>The architecture consists of three domains with precisely characterized boundaries:
 * <ul>
 *   <li><b>JVM Domain</b>: Contains YAWL engine, QLever SPARQL queries, ML optimization
 *       (sub-10ns in-process Panama FFM, no fault isolation needed)</li>
 *   <li><b>BEAM Domain</b>: Capability execution with OTP supervision, Mnesia registry,
 *       fault containment (OS-separated, supervised, hot-reloadable)</li>
 *   <li><b>Rust Domain</b>: Computational execution with memory safety, NIF boundary,
 *       BEAM-contained (sub-millisecond restart capability)</li>
 * </ul>
 *
 * <p>Key properties:
 * <ul>
 *   <li>Boundary A: JVM ↔ BEAM via Unix domain socket (~5-20µs)</li>
 *   <li>Boundary B: BEAM ↔ Rust via NIF boundary (~100ns)</li>
 *   <li>Zero path from rust4pm faults to JVM crashes</li>
 *   <li>Hot reload capabilities for both Erlang and Rust components</li>
 *   <li>Mnesia persistence across BEAM restarts</li>
 * </ul>
 *
 * <p>This module is generated automatically from the bridge ontology using ggen.
 * Manual modifications will be overwritten on regeneration.</p>
 *
 * @see org.yawlfoundation.yawl.nativebridge.qlever QLever domain interfaces
 * @see org.yawlfoundation.yawl.nativebridge.erlang Erlang domain interfaces
 * @see org.yawlfoundation.yawl.nativebridge.router Bridge routing logic
 */
package org.yawlfoundation.yawl.nativebridge;