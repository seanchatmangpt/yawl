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

import java.time.Duration;

/**
 * Implemented by Java services that support graceful shutdown before a supervisor
 * restart.
 *
 * <p>When {@link YawlServiceSupervisor} triggers a restart, it calls {@link #drain}
 * before constructing the replacement service instance. The drain period allows
 * in-flight requests to complete and new requests to be rejected, preventing
 * request loss during restart.</p>
 *
 * <p>Implementations that exceed the {@code timeout} should stop draining and
 * return — the supervisor will proceed regardless. Blocking indefinitely is
 * not permitted.</p>
 */
public interface DrainableService {

    /**
     * Initiates graceful shutdown: completes in-flight requests, rejects new ones,
     * and returns within {@code timeout}.
     *
     * @param timeout maximum time to wait for in-flight requests to complete
     */
    void drain(Duration timeout);
}
