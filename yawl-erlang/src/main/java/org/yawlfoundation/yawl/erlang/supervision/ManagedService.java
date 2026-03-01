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
 * A Java service that can be health-checked and drained gracefully by
 * {@link YawlServiceSupervisor}.
 *
 * <p>Implementations provide a stable {@link #serviceName()} that identifies
 * them in supervision logs and {@link org.yawlfoundation.yawl.erlang.workflow.ServiceRestartEvent}s.</p>
 *
 * <p>Example:
 * <pre>
 *   public class DataModellingService implements ManagedService {
 *       &#64;Override
 *       public String serviceName() { return "data-modelling"; }
 *
 *       &#64;Override
 *       public boolean ping() {
 *           return bridge != null &amp;&amp; bridge.isConnected();
 *       }
 *
 *       &#64;Override
 *       public void drain(Duration timeout) {
 *           requestGate.set(false);  // stop accepting new requests
 *           // wait up to timeout for in-flight requests
 *       }
 *   }
 * </pre>
 */
public interface ManagedService extends HealthCheckable, DrainableService {

    /**
     * Returns the stable name of this service used in supervision logs and events.
     *
     * <p>Names should be lowercase, hyphenated, unique within a supervisor
     * (e.g., {@code "data-modelling"}, {@code "qlever-engine"}).</p>
     *
     * @return non-blank service name
     */
    String serviceName();
}
