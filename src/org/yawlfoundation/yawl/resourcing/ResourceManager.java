/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.resourcing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens for YAWL work item events and routes enabled items to AI agents or humans.
 *
 * <p>On each {@link YEventType#ITEM_ENABLED} event, the manager asks the
 * {@link CapabilityMatcher} for a routing decision:</p>
 * <ul>
 *   <li>{@link RoutingDecision.AgentRoute} — dispatches the work item to the selected
 *       agent's A2A endpoint on a virtual thread. If the dispatch fails,
 *       {@link AgentRoutingException} is thrown — there is no silent human fallback.</li>
 *   <li>{@link RoutingDecision.HumanRoute} — no suitable agent found; normal YAWL
 *       human task allocation proceeds uninterrupted.</li>
 * </ul>
 *
 * <p>Monitoring counters ({@link #getAgentDispatchCount()} and
 * {@link #getHumanRouteCount()}) enable operators to observe routing behaviour at
 * runtime without requiring additional instrumentation.</p>
 *
 * <p>Non-{@code ITEM_ENABLED} events are silently ignored, preserving existing
 * YAWL listener contracts.</p>
 *
 * @since YAWL 6.0
 */
public final class ResourceManager implements YWorkItemEventListener {

    private static final Logger log = LoggerFactory.getLogger(ResourceManager.class);

    /** HTTP connect + request timeout for A2A dispatch calls. */
    static final Duration DISPATCH_TIMEOUT = Duration.ofSeconds(30);

    private final CapabilityMatcher capabilityMatcher;
    private final AtomicLong agentDispatchCount = new AtomicLong(0);
    private final AtomicLong humanRouteCount = new AtomicLong(0);

    /**
     * Constructs a resource manager backed by the given capability matcher.
     *
     * @param capabilityMatcher the matcher to consult on each ITEM_ENABLED event;
     *                          must not be null
     * @throws IllegalArgumentException if capabilityMatcher is null
     */
    public ResourceManager(CapabilityMatcher capabilityMatcher) {
        Objects.requireNonNull(capabilityMatcher, "capabilityMatcher must not be null");
        this.capabilityMatcher = capabilityMatcher;
    }

    /**
     * Handles a work item event. Only {@link YEventType#ITEM_ENABLED} events trigger
     * routing; all other event types are ignored.
     *
     * @param event the work item event; must not be null
     */
    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.getEventType() != YEventType.ITEM_ENABLED) {
            return;
        }

        YWorkItem workItem = event.getWorkItem();
        if (workItem == null) {
            return;
        }

        RoutingDecision decision = capabilityMatcher.match(workItem);
        switch (decision) {
            case RoutingDecision.AgentRoute agentRoute -> {
                agentDispatchCount.incrementAndGet();
                String workItemId = workItem.getWorkItemID() != null
                        ? workItem.getWorkItemID().toString()
                        : workItem.getTaskID();
                Thread.ofVirtual()
                        .name("resourcing-dispatch-" + workItemId)
                        .start(() -> executeDispatch(agentRoute.listing(), workItem));
            }
            case RoutingDecision.HumanRoute humanRoute -> {
                humanRouteCount.incrementAndGet();
                log.debug("Work item '{}' routed to human allocation", workItem.getTaskID());
            }
        }
    }

    /**
     * Returns the total number of work items dispatched to agents since this manager
     * was created.
     */
    public long getAgentDispatchCount() {
        return agentDispatchCount.get();
    }

    /**
     * Returns the total number of work items routed to human allocation since this
     * manager was created.
     */
    public long getHumanRouteCount() {
        return humanRouteCount.get();
    }

    /**
     * Executes the HTTP dispatch of a work item to the agent's A2A endpoint.
     *
     * <p>Package-private to allow direct invocation in tests that need to verify the
     * no-silent-fallback invariant synchronously (without spawning a virtual thread).</p>
     *
     * @param listing  the selected agent's marketplace listing
     * @param workItem the work item to dispatch
     * @throws AgentRoutingException if the HTTP call fails or the agent returns 4xx/5xx
     */
    void executeDispatch(AgentMarketplaceListing listing, YWorkItem workItem) {
        String endpoint = listing.agentInfo().getEndpointUrl();
        String taskId = workItem.getTaskID();
        String workItemId = workItem.getWorkItemID() != null
                ? workItem.getWorkItemID().toString()
                : taskId;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(DISPATCH_TIMEOUT)
                    .build();
            String body = """
                    {"workItemId":"%s","taskId":"%s"}
                    """.formatted(workItemId, taskId).strip();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/dispatch"))
                    .timeout(DISPATCH_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new AgentRoutingException(
                        "Agent dispatch rejected: HTTP " + response.statusCode()
                        + " for task '" + taskId + "' → " + endpoint);
            }
            log.debug("Work item '{}' dispatched to agent at {}", taskId, endpoint);
        } catch (AgentRoutingException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            throw new AgentRoutingException(
                    "Agent dispatch failed for task '" + taskId
                    + "' → " + endpoint + ": " + e.getMessage(), e);
        }
    }
}
