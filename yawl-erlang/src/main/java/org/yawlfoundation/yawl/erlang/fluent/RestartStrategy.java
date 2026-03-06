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
package org.yawlfoundation.yawl.erlang.fluent;

/**
 * OTP-inspired restart strategies for pipeline stage supervision.
 *
 * <p>Maps directly to Erlang/OTP supervisor restart strategies:
 * <ul>
 *   <li>{@link #ONE_FOR_ONE} — restart only the failed stage</li>
 *   <li>{@link #ONE_FOR_ALL} — restart all stages when any fails</li>
 *   <li>{@link #REST_FOR_ONE} — restart the failed stage and all subsequent stages</li>
 * </ul>
 *
 * @see SupervisionSpec
 */
public enum RestartStrategy {

    /**
     * Restart only the failed stage. Other stages continue unaffected.
     * Best for independent stages with no shared state.
     */
    ONE_FOR_ONE,

    /**
     * Restart all stages when any single stage fails.
     * Best when all stages share state or depend on a common resource.
     */
    ONE_FOR_ALL,

    /**
     * Restart the failed stage and all stages defined after it.
     * Best for sequential pipelines where later stages depend on earlier ones.
     */
    REST_FOR_ONE
}
