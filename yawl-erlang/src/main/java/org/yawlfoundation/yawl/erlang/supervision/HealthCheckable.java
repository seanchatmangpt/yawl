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
package org.yawlfoundation.yawl.erlang.supervision;

/**
 * Implemented by Java services that can be health-checked by
 * {@link YawlServiceSupervisor}.
 *
 * <p>The supervisor calls {@link #ping()} at a configurable interval
 * (default: every 5 seconds). Three consecutive {@code false} returns
 * trigger an autonomous restart sequence.</p>
 *
 * <p>Implementations must not throw from {@link #ping()} — return {@code false}
 * instead. A thrown exception is treated as {@code false} and logged.</p>
 */
public interface HealthCheckable {

    /**
     * Returns true if this service is healthy and ready to process requests.
     *
     * @return {@code true} if healthy, {@code false} if unhealthy
     */
    boolean ping();
}
