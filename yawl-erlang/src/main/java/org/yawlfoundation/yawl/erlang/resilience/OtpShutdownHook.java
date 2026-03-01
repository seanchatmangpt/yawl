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
package org.yawlfoundation.yawl.erlang.resilience;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Registers a JVM shutdown hook that gracefully drains in-flight messages
 * and shuts down the OTP node lifecycle manager.
 *
 * <p>The hook:
 * <ol>
 *   <li>Waits up to 5 seconds for in-flight messages to drain (via a CountDownLatch)</li>
 *   <li>Calls the lifecycle manager's close() method</li>
 *   <li>Logs the final OtpHealthEndpoint snapshot</li>
 * </ol>
 */
public final class OtpShutdownHook {

    private OtpShutdownHook() {}

    /**
     * Registers a shutdown hook that drains in-flight messages, shuts down the
     * lifecycle manager, and logs the final health snapshot.
     *
     * @param lifecycle    lifecycle manager to shut down
     * @param healthSource health endpoint to log the final snapshot from
     * @param drainLatch   latch to await before shutdown (countdown when messages are drained)
     */
    public static void register(
            AutoCloseable lifecycle,
            OtpHealthEndpoint healthSource,
            CountDownLatch drainLatch) {
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            // Step 1: Wait up to 5s for in-flight messages to drain
            try {
                boolean drained = drainLatch.await(5, TimeUnit.SECONDS);
                if (!drained) {
                    System.err.println("[OtpShutdownHook] Drain timeout — proceeding with shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Step 2: Shut down lifecycle manager
            try {
                lifecycle.close();
            } catch (Exception e) {
                System.err.println("[OtpShutdownHook] Lifecycle close error: " + e.getMessage());
            }

            // Step 3: Log final health snapshot
            try {
                String json = healthSource.toJsonString();
                System.err.println("[OtpShutdownHook] Final health: " + json);
            } catch (Exception e) {
                System.err.println("[OtpShutdownHook] Could not log final health: " + e.getMessage());
            }
        }));
    }

    /**
     * Convenience overload without a drain latch (proceeds to shutdown immediately).
     *
     * @param lifecycle    lifecycle manager to shut down
     * @param healthSource health endpoint to log the final snapshot from
     */
    public static void register(AutoCloseable lifecycle, OtpHealthEndpoint healthSource) {
        register(lifecycle, healthSource, new CountDownLatch(0));
    }
}
