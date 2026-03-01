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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.capability.Capability;
import org.yawlfoundation.yawl.erlang.capability.CapabilityTest;
import org.yawlfoundation.yawl.erlang.workflow.ServiceRestartEvent;
import org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link YawlServiceSupervisor}.
 *
 * <p>No live OTP node is required. Tests use simple lambda factories and
 * in-process service implementations to exercise the supervisor logic.</p>
 */
class YawlServiceSupervisorTest {

    private WorkflowEventBus eventBus;
    private YawlServiceSupervisor supervisor;

    @BeforeEach
    void setUp() {
        eventBus = new WorkflowEventBus();
        // Very short check interval for fast tests
        supervisor = new YawlServiceSupervisor(
                eventBus,
                3,                          // 3 consecutive failures → restart
                Duration.ofMillis(100),     // check every 100ms
                Duration.ofMillis(50));     // drain timeout 50ms
    }

    @AfterEach
    void tearDown() throws Exception {
        supervisor.close();
        eventBus.close();
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    /** A service whose health is controlled by an AtomicBoolean. */
    static ManagedService controllableService(AtomicBoolean healthy, String name) {
        return new ManagedService() {
            @Override public boolean ping() { return healthy.get(); }
            @Override public void drain(Duration timeout) { /* graceful, nothing to drain */ }
            @Override public String serviceName() { return name; }
        };
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @CapabilityTest(Capability.RELOAD_MODULE)
    @Test
    void register_serviceInitiallyRunning() {
        AtomicBoolean healthy = new AtomicBoolean(true);
        supervisor.register("svc-a", () -> controllableService(healthy, "svc-a"));

        assertEquals(ServiceStatus.RUNNING, supervisor.getStatus("svc-a"));
    }

    @CapabilityTest(Capability.LOAD_BINARY_MODULE)
    @Test
    void healthyService_staysRunning() throws InterruptedException {
        AtomicBoolean healthy = new AtomicBoolean(true);
        supervisor.register("svc-b", () -> controllableService(healthy, "svc-b"));

        // Let the watchdog run several cycles
        Thread.sleep(500);

        assertEquals(ServiceStatus.RUNNING, supervisor.getStatus("svc-b"));
        assertEquals(0, supervisor.getRestartCount("svc-b"),
                "Healthy service must have 0 restarts");
    }

    @CapabilityTest(Capability.ROLLBACK_MODULE)
    @Test
    void threeConsecutiveFailures_triggerRestart() throws InterruptedException {
        AtomicBoolean healthy = new AtomicBoolean(false);   // start unhealthy
        AtomicInteger factoryCallCount = new AtomicInteger(0);

        supervisor.register("svc-c", () -> {
            factoryCallCount.incrementAndGet();
            return controllableService(healthy, "svc-c");
        });

        // Wait enough for 3+ health-check cycles (100ms each) + restart
        Thread.sleep(600);

        int restarts = supervisor.getRestartCount("svc-c");
        assertTrue(restarts >= 1, "Service should have been restarted at least once (was: " + restarts + ")");
        // factory called once at registration + once per restart
        assertTrue(factoryCallCount.get() >= 2,
                "Factory must have been called for initial + restart (was: " + factoryCallCount.get() + ")");
    }

    @CapabilityTest(Capability.AS_RPC_CALLABLE)
    @Test
    void restart_publishesServiceRestartEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean receivedRestartEvent = new AtomicBoolean(false);

        eventBus.subscribe(ServiceRestartEvent.class, event -> {
            if ("svc-d".equals(event.serviceName())) {
                receivedRestartEvent.set(true);
                latch.countDown();
            }
        });

        AtomicBoolean healthy = new AtomicBoolean(false);
        supervisor.register("svc-d", () -> controllableService(healthy, "svc-d"));

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "ServiceRestartEvent must be published within 2s of 3 consecutive failures");
        assertTrue(receivedRestartEvent.get());
    }

    @Test
    void deregister_removesService() {
        AtomicBoolean healthy = new AtomicBoolean(true);
        supervisor.register("svc-e", () -> controllableService(healthy, "svc-e"));

        assertTrue(supervisor.deregister("svc-e"));
        assertNull(supervisor.getStatus("svc-e"), "Deregistered service must have null status");
    }

    @Test
    void deregister_returnsFalseForUnknownService() {
        assertFalse(supervisor.deregister("unknown-service"));
    }

    @Test
    void ping_returnsTrueForHealthyService() {
        supervisor.register("svc-f", () -> controllableService(new AtomicBoolean(true), "svc-f"));
        assertTrue(supervisor.ping("svc-f"));
    }

    @Test
    void ping_returnsFalseForUnhealthyService() {
        supervisor.register("svc-g", () -> controllableService(new AtomicBoolean(false), "svc-g"));
        assertFalse(supervisor.ping("svc-g"));
    }

    @Test
    void ping_returnsFalseForUnknownService() {
        assertFalse(supervisor.ping("nonexistent"));
    }

    @Test
    void register_duplicateNameThrows() {
        supervisor.register("svc-h", () -> controllableService(new AtomicBoolean(true), "svc-h"));

        assertThrows(SupervisionException.class,
                () -> supervisor.register("svc-h", () -> controllableService(new AtomicBoolean(true), "svc-h")),
                "Registering a duplicate service name must throw SupervisionException");
    }

    @Test
    void register_blankNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> supervisor.register("  ", () -> controllableService(new AtomicBoolean(true), "x")));
    }

    @Test
    void register_nullFactoryThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> supervisor.register("svc-i", null));
    }

    @Test
    void constructor_rejectsNullEventBus() {
        assertThrows(IllegalArgumentException.class,
                () -> new YawlServiceSupervisor(null, 3));
    }

    @Test
    void constructor_rejectsZeroMaxFailures() {
        assertThrows(IllegalArgumentException.class,
                () -> new YawlServiceSupervisor(eventBus, 0));
    }

    @Test
    void getRestartCount_returnsMinusOneForUnknownService() {
        assertEquals(-1, supervisor.getRestartCount("nonexistent"));
    }

    @Test
    void close_isIdempotent() throws Exception {
        supervisor.register("svc-j", () -> controllableService(new AtomicBoolean(true), "svc-j"));
        supervisor.close();
        // Second close must not throw
        assertDoesNotThrow(supervisor::close);
    }
}
