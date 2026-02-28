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

package org.yawlfoundation.yawl.integration.safe.event;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Event bus for SAFe simulation events.
 *
 * <p>Singleton event bus that publishes all SAFe events to registered listeners.
 * Events include:
 * <ul>
 *   <li>Ceremony lifecycle events (PI Planning started, completed, etc.)</li>
 *   <li>Work item events (task started, completed, blocked, etc.)</li>
 *   <li>Dependency events (dependency declared, resolved, circular detected, etc.)</li>
 *   <li>Decision events (design approved, story accepted, etc.)</li>
 *   <li>Risk events (risk identified, mitigated)</li>
 * </ul>
 *
 * <p>Event publication is asynchronous and non-blocking. Listeners are notified
 * via virtual thread executor to support high-concurrency scenarios.
 *
 * <p>Thread-safe: uses CopyOnWriteArrayList for listener registration.
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class SAFeEventBus {

    private static final Logger logger = LogManager.getLogger(SAFeEventBus.class);
    private static final SAFeEventBus INSTANCE = new SAFeEventBus();

    private final List<SAFeEventListener> listeners = new CopyOnWriteArrayList<>();
    private final var virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Singleton instance of the event bus.
     *
     * @return the event bus singleton
     */
    public static SAFeEventBus getInstance() {
        return INSTANCE;
    }

    private SAFeEventBus() {
        // Private: singleton
    }

    // =========================================================================
    // Event Publication
    // =========================================================================

    /**
     * Publish an event to all registered listeners.
     *
     * <p>Event publication is asynchronous. Listeners are notified via
     * virtual threads to enable high-concurrency event processing.
     *
     * @param event the event to publish
     */
    public void publish(SAFeAgent.SAFeEvent event) {
        if (event == null) {
            logger.warn("Attempted to publish null event");
            return;
        }

        logger.debug("Publishing event: {} (id: {})", event.eventType, event.eventId);

        // Publish asynchronously to all listeners
        listeners.forEach(listener ->
            virtualThreadExecutor.execute(() -> {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    logger.error("Error in event listener: {}", e.getMessage(), e);
                }
            })
        );
    }

    /**
     * Publish a ceremony event.
     *
     * @param ceremonyType the ceremony type (PI_PLANNING, SPRINT_PLANNING, etc.)
     * @param eventType the event type (STARTED, COMPLETED, etc.)
     * @param ceremonyId the ceremony instance ID
     * @param sourceAgentId the agent emitting the event
     * @param context additional context (key-value map)
     */
    public void publishCeremonyEvent(String ceremonyType, String eventType, String ceremonyId,
                                      String sourceAgentId, Map<String, String> context) {
        SAFeAgent.CeremonyEvent event = new SAFeAgent.CeremonyEvent(
            ceremonyType + "_" + eventType,
            sourceAgentId,
            ceremonyId,
            context
        );
        publish(event);
    }

    /**
     * Publish a work item event.
     *
     * @param eventType the event type (STARTED, COMPLETED, BLOCKED, etc.)
     * @param workItemId the work item ID
     * @param sourceAgentId the agent emitting the event
     * @param context additional context
     */
    public void publishWorkItemEvent(String eventType, String workItemId,
                                     String sourceAgentId, Map<String, String> context) {
        SAFeAgent.WorkItemEvent event = new SAFeAgent.WorkItemEvent(
            eventType,
            sourceAgentId,
            workItemId,
            context
        );
        publish(event);
    }

    /**
     * Publish a dependency event.
     *
     * @param eventType the event type (DECLARED, RESOLVED, CIRCULAR_DETECTED, etc.)
     * @param sourceStoryId the source story/epic ID
     * @param targetStoryId the target story/epic ID
     * @param sourceAgentId the agent emitting the event
     * @param context additional context
     */
    public void publishDependencyEvent(String eventType, String sourceStoryId,
                                        String targetStoryId, String sourceAgentId,
                                        Map<String, String> context) {
        SAFeAgent.DependencyEvent event = new SAFeAgent.DependencyEvent(
            eventType,
            sourceAgentId,
            sourceStoryId,
            targetStoryId,
            context
        );
        publish(event);
    }

    /**
     * Publish a risk event.
     *
     * @param eventType the event type (IDENTIFIED, MITIGATED, etc.)
     * @param riskId the risk ID
     * @param sourceAgentId the agent emitting the event
     * @param context additional context
     */
    public void publishRiskEvent(String eventType, String riskId,
                                 String sourceAgentId, Map<String, String> context) {
        SAFeAgent.RiskEvent event = new SAFeAgent.RiskEvent(
            eventType,
            sourceAgentId,
            riskId,
            context
        );
        publish(event);
    }

    // =========================================================================
    // Listener Management
    // =========================================================================

    /**
     * Register a listener to receive all events.
     *
     * @param listener the listener to register
     */
    public void addListener(SAFeEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
        logger.debug("Listener registered: {}", listener.getClass().getSimpleName());
    }

    /**
     * Unregister a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(SAFeEventListener listener) {
        listeners.remove(listener);
        logger.debug("Listener unregistered: {}", listener.getClass().getSimpleName());
    }

    /**
     * Get the number of registered listeners.
     *
     * @return the count of listeners
     */
    public int getListenerCount() {
        return listeners.size();
    }

    // =========================================================================
    // Functional Registration (Lambda-Friendly)
    // =========================================================================

    /**
     * Register a listener for a specific event type using a lambda.
     *
     * @param eventType the event type to filter on
     * @param handler the handler function
     */
    public void onEvent(String eventType, Consumer<SAFeAgent.SAFeEvent> handler) {
        addListener(event -> {
            if (event.eventType.contains(eventType)) {
                handler.accept(event);
            }
        });
    }

    /**
     * Register a listener for ceremony events.
     *
     * @param handler the handler function
     */
    public void onCeremonyEvent(Consumer<SAFeAgent.CeremonyEvent> handler) {
        addListener(event -> {
            if (event instanceof SAFeAgent.CeremonyEvent ce) {
                handler.accept(ce);
            }
        });
    }

    /**
     * Register a listener for work item events.
     *
     * @param handler the handler function
     */
    public void onWorkItemEvent(Consumer<SAFeAgent.WorkItemEvent> handler) {
        addListener(event -> {
            if (event instanceof SAFeAgent.WorkItemEvent we) {
                handler.accept(we);
            }
        });
    }

    /**
     * Register a listener for dependency events.
     *
     * @param handler the handler function
     */
    public void onDependencyEvent(Consumer<SAFeAgent.DependencyEvent> handler) {
        addListener(event -> {
            if (event instanceof SAFeAgent.DependencyEvent de) {
                handler.accept(de);
            }
        });
    }

    /**
     * Register a listener for risk events.
     *
     * @param handler the handler function
     */
    public void onRiskEvent(Consumer<SAFeAgent.RiskEvent> handler) {
        addListener(event -> {
            if (event instanceof SAFeAgent.RiskEvent re) {
                handler.accept(re);
            }
        });
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Shut down the event bus and wait for pending events to complete.
     */
    public void shutdown() {
        logger.info("Shutting down SAFeEventBus");
        try {
            virtualThreadExecutor.shutdown();
            if (!virtualThreadExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                logger.warn("Event bus shutdown timeout; forcing termination");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            virtualThreadExecutor.shutdownNow();
        }
        listeners.clear();
        logger.info("SAFeEventBus shutdown complete");
    }
}
