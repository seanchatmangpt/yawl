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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Internal pub/sub bus for YAWL workflow lifecycle events.
 *
 * <p>Publishers call {@link #publish(WorkflowEvent)} to dispatch typed events.
 * The bus delivers each event asynchronously to all registered listeners whose
 * declared event type matches the published event. Delivery is one virtual thread
 * per listener per event, so a slow listener cannot delay other listeners or
 * block the publisher.</p>
 *
 * <p>Thread-safe: subscribers and publishers may operate concurrently.
 * The listener registry uses {@link CopyOnWriteArrayList} for lock-free reads.</p>
 *
 * <p>Error isolation: if a listener throws, the exception is logged at WARNING
 * level but does not propagate to the publisher or other listeners.</p>
 *
 * <p>Usage:
 * <pre>
 *   WorkflowEventBus bus = new WorkflowEventBus();
 *
 *   String token = bus.subscribe(TaskStarted.class, event -> {
 *       System.out.println("Task started: " + event.taskId());
 *   });
 *
 *   bus.publish(new TaskStarted("case-1", "validateOrder", "{}", Instant.now()));
 *
 *   bus.unsubscribe(token);
 *   bus.close();
 * </pre>
 */
public final class WorkflowEventBus implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WorkflowEventBus.class.getName());

    private record ListenerRegistration<T extends WorkflowEvent>(
            String token,
            Class<T> eventType,
            EventListener<T> listener) {}

    private final CopyOnWriteArrayList<ListenerRegistration<?>> registrations =
            new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Registers a listener for a specific event type.
     *
     * <p>The listener will be called for all events whose runtime type is
     * exactly {@code eventType} or a subtype of it.</p>
     *
     * @param eventType the class of the event to listen for
     * @param listener  the listener to invoke on matching events
     * @param <T>       the event type
     * @return a subscription token that can be passed to {@link #unsubscribe(String)}
     * @throws IllegalArgumentException if eventType or listener is null
     */
    public <T extends WorkflowEvent> String subscribe(Class<T> eventType, EventListener<T> listener) {
        if (eventType == null) throw new IllegalArgumentException("eventType must not be null");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");

        String token = UUID.randomUUID().toString();
        registrations.add(new ListenerRegistration<>(token, eventType, listener));
        return token;
    }

    /**
     * Removes a previously registered listener.
     *
     * @param token the subscription token returned by {@link #subscribe}
     * @return true if the listener was found and removed, false if not found
     */
    public boolean unsubscribe(String token) {
        if (token == null) return false;
        return registrations.removeIf(r -> token.equals(r.token()));
    }

    /**
     * Publishes an event to all matching listeners asynchronously.
     *
     * <p>Each matching listener receives the event on a dedicated virtual thread.
     * This method returns immediately without waiting for listeners to complete.
     * Listener exceptions are logged but not propagated.</p>
     *
     * @param event the event to publish (must not be null)
     * @throws IllegalArgumentException if event is null
     */
    @SuppressWarnings("unchecked")
    public void publish(WorkflowEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");

        Class<?> eventClass = event.getClass();
        List<ListenerRegistration<?>> snapshot = List.copyOf(registrations);

        for (ListenerRegistration<?> registration : snapshot) {
            if (registration.eventType().isAssignableFrom(eventClass)) {
                EventListener<WorkflowEvent> typedListener =
                        (EventListener<WorkflowEvent>) registration.listener();
                executor.submit(() -> {
                    try {
                        typedListener.onEvent(event);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING,
                                "Listener for " + eventClass.getSimpleName()
                                + " threw exception: " + e.getMessage(), e);
                    }
                });
            }
        }
    }

    /**
     * Returns the current number of registered listeners.
     */
    public int listenerCount() {
        return registrations.size();
    }

    /**
     * Shuts down the internal virtual thread executor.
     *
     * <p>In-flight listener dispatches are allowed to complete. After closing,
     * {@link #publish} will throw {@link java.util.concurrent.RejectedExecutionException}.</p>
     */
    @Override
    public void close() {
        executor.shutdown();
    }
}
