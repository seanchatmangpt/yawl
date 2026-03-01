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
package org.yawlfoundation.yawl.erlang.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.capability.Capability;
import org.yawlfoundation.yawl.erlang.capability.CapabilityTest;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkflowEventBus}.
 *
 * <p>Verifies pub/sub dispatch, type filtering, slow-listener isolation,
 * unsubscribe, and graceful close.</p>
 */
class WorkflowEventBusTest {

    private WorkflowEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new WorkflowEventBus();
    }

    @AfterEach
    void tearDown() throws Exception {
        bus.close();
    }

    @CapabilityTest(Capability.LAUNCH_CASE)
    @Test
    void publish_deliversEventToSubscriber() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WorkflowInstanceCreated> received = new AtomicReference<>();

        bus.subscribe(WorkflowInstanceCreated.class, event -> {
            received.set(event);
            latch.countDown();
        });

        WorkflowInstanceCreated evt = new WorkflowInstanceCreated("id-1", "spec-A", Instant.now());
        bus.publish(evt);

        assertTrue(latch.await(2, TimeUnit.SECONDS), "Event must be delivered within 2s");
        assertEquals(evt, received.get());
    }

    @CapabilityTest(Capability.CHECK_CONFORMANCE)
    @Test
    void publish_deliversToAllMatchingSubscribers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        CopyOnWriteArrayList<WorkflowEvent> received = new CopyOnWriteArrayList<>();

        bus.subscribe(WorkflowInstanceCreated.class, event -> { received.add(event); latch.countDown(); });
        bus.subscribe(WorkflowInstanceCreated.class, event -> { received.add(event); latch.countDown(); });
        bus.subscribe(WorkflowInstanceCreated.class, event -> { received.add(event); latch.countDown(); });

        bus.publish(new WorkflowInstanceCreated("id-2", "spec-B", Instant.now()));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "All 3 listeners must be notified");
        assertEquals(3, received.size());
    }

    @CapabilityTest(Capability.SUBSCRIBE_TO_EVENTS)
    @Test
    void publish_doesNotDeliverToWrongEventType() throws InterruptedException {
        CopyOnWriteArrayList<WorkflowEvent> received = new CopyOnWriteArrayList<>();

        bus.subscribe(TaskStarted.class, event -> received.add(event));

        // Publish a different event type
        bus.publish(new WorkflowInstanceCreated("id-3", "spec-C", Instant.now()));

        // Allow time for (non-)delivery
        Thread.sleep(100);
        assertTrue(received.isEmpty(), "TaskStarted listener must not receive WorkflowInstanceCreated");
    }

    @Test
    void publish_slowListenerDoesNotBlockOtherListeners() throws InterruptedException {
        CountDownLatch fastLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();

        // Slow listener: blocks for 200ms
        bus.subscribe(WorkflowInstanceCreated.class, event -> {
            Thread.sleep(200);
            order.add("slow");
        });

        // Fast listener
        bus.subscribe(WorkflowInstanceCreated.class, event -> {
            order.add("fast");
            fastLatch.countDown();
        });

        bus.publish(new WorkflowInstanceCreated("id-4", "spec-D", Instant.now()));

        // Fast listener must finish long before slow listener
        assertTrue(fastLatch.await(1, TimeUnit.SECONDS),
                "Fast listener must not be blocked by slow listener");
    }

    @Test
    void unsubscribe_removesListener() throws InterruptedException {
        CopyOnWriteArrayList<WorkflowEvent> received = new CopyOnWriteArrayList<>();
        String token = bus.subscribe(WorkflowInstanceCreated.class, event -> received.add(event));

        bus.unsubscribe(token);
        bus.publish(new WorkflowInstanceCreated("id-5", "spec-E", Instant.now()));

        Thread.sleep(100);
        assertTrue(received.isEmpty(), "Unsubscribed listener must not receive events");
    }

    @Test
    void publish_workflowEventSubtypeDeliveredToSupertype() throws InterruptedException {
        // Subscribe to the sealed super-interface
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WorkflowEvent> received = new AtomicReference<>();

        bus.subscribe(WorkflowEvent.class, event -> {
            received.set(event);
            latch.countDown();
        });

        bus.publish(new TaskFailed("inst-1", "task-1", "timeout", Instant.now()));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertInstanceOf(TaskFailed.class, received.get());
    }

    @Test
    void publish_serviceRestartEventDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ServiceRestartEvent> received = new AtomicReference<>();

        bus.subscribe(ServiceRestartEvent.class, event -> {
            received.set(event);
            latch.countDown();
        });

        ServiceRestartEvent evt = new ServiceRestartEvent("data-service", Instant.now(), 1, "ping-failure");
        bus.publish(evt);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("data-service", received.get().serviceName());
        assertEquals(1, received.get().restartCount());
    }

    @Test
    void taskSchemaViolation_eventDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe(TaskSchemaViolation.class, event -> latch.countDown());

        bus.publish(new TaskSchemaViolation("inst-1", "ValidateOrderTask",
                "name:schema", "{}", "missing required field 'orderId'"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }
}
