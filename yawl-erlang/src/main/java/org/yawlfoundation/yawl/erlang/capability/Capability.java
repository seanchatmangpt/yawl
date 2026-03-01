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
package org.yawlfoundation.yawl.erlang.capability;

/**
 * All native bridge capabilities declared for the YAWL Erlang/OTP module.
 *
 * <p>Each value (except {@link #TOTAL}) represents a capability that crosses
 * the Java ↔ OTP boundary. Every capability must have:
 * <ol>
 *   <li>A Layer 2 implementation annotated {@code @MapsToCapability(layer="L2")}</li>
 *   <li>A Layer 3 implementation annotated {@code @MapsToCapability(layer="L3")}</li>
 *   <li>At least one test method annotated {@code @CapabilityTest(CAPABILITY)}</li>
 * </ol>
 *
 * <p>Pure-Java components (schema validation, event bus, supervision) are
 * excluded — their correctness is verified by compilation and unit tests,
 * not by the bridge registry. Only capabilities that involve the OTP boundary
 * require registry verification.</p>
 *
 * @see CapabilityRegistry
 * @see MapsToCapability
 * @see CapabilityTest
 */
public enum Capability {

    // -----------------------------------------------------------------------
    // Group PM: Process Mining (OTP RPC to yawl_* gen_servers)
    // -----------------------------------------------------------------------

    /** Launch a workflow case on the OTP node (calls yawl_workflow:launch_case/1). */
    LAUNCH_CASE,

    /** Check conformance of an event log (calls yawl_process_mining:conformance/1). */
    CHECK_CONFORMANCE,

    /** Subscribe to workflow lifecycle events from the OTP event relay. */
    SUBSCRIBE_TO_EVENTS,

    // -----------------------------------------------------------------------
    // Group HR: Hot Code Reload (OTP code server RPC)
    // -----------------------------------------------------------------------

    /** Reload a module from its file path via code:purge + code:load_file. */
    RELOAD_MODULE,

    /** Load a module from raw .beam bytecode via code:load_binary/3. */
    LOAD_BINARY_MODULE,

    /** Roll back to the previous .beam version via code:load_binary/3. */
    ROLLBACK_MODULE,

    // -----------------------------------------------------------------------
    // Group BR: Bridge Utility
    // -----------------------------------------------------------------------

    /**
     * Expose the bridge's RPC channel as an {@link org.yawlfoundation.yawl.erlang.resilience.OtpCircuitBreaker.ErlRpcCallable}.
     *
     * <p>Used by {@link org.yawlfoundation.yawl.erlang.hotreload.HotReloadServiceImpl}
     * and {@link org.yawlfoundation.yawl.erlang.supervision.YawlServiceSupervisor}
     * to make OTP calls without taking a hard dependency on {@code ErlangBridge}.</p>
     */
    AS_RPC_CALLABLE,

    // -----------------------------------------------------------------------
    // Sentinel — must remain last
    // -----------------------------------------------------------------------

    /**
     * Sentinel value used to count declared capabilities.
     *
     * <p>Never assigned to any implementation — it is excluded from all
     * registry completeness checks. Use {@code Capability.TOTAL.ordinal()}
     * to get the number of real capabilities.</p>
     */
    TOTAL
}
