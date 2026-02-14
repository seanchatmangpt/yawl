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

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;
import org.jdom2.Document;
import org.jdom2.output.XMLOutputter;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.ObserverGateway;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event notification system for YAWL MCP and A2A integration.
 *
 * Implements real event publishing for workflow lifecycle events with multiple
 * delivery mechanisms:
 * - HTTP Webhooks (POST notifications)
 * - Server-Sent Events (SSE) push streams
 * - WebSocket push notifications (when WebSocket libraries are available)
 * - In-memory event queue (for polling)
 *
 * Integrates with YAWL's ObserverGateway (Interface E) for event sourcing.
 *
 * Usage:
 * <pre>
 * YawlEventNotifier notifier = new YawlEventNotifier();
 *
 * // Register webhook endpoint
 * notifier.registerWebhook("http://localhost:9000/yawl/events");
 *
 * // Register SSE client
 * notifier.registerSseClient(clientId, responseEmitter);
 *
 * // Integrate with YAWL engine
 * engine.registerObserverGateway(notifier);
 *
 * // Events will be automatically published to all registered channels
 * </pre>
 *
 * Event Types Published:
 * 1. Case started
 * 2. Case completed
 * 3. Case cancelled
 * 4. Work item enabled
 * 5. Work item started
 * 6. Work item completed
 * 7. Work item cancelled
 * 8. Task deadline approaching (timer expiry)
 * 9. Exception raised (via deadlock detection)
 * 10. Resource allocated (via work item status change)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlEventNotifier implements ObserverGateway {

    private static final Logger logger = Logger.getLogger(YawlEventNotifier.class.getName());
    private static final String SCHEME = "yawl-events";
    private static final int MAX_QUEUE_SIZE = 10000;
    private static final int WEBHOOK_TIMEOUT_SECONDS = 30;

    // JSON serialization
    private final ObjectMapper objectMapper;

    // HTTP client for webhook delivery
    private final OkHttpClient httpClient;

    // Webhook endpoints (HTTP POST targets)
    private final Set<String> webhookEndpoints = new CopyOnWriteArraySet<>();

    // SSE clients (Server-Sent Events)
    private final Map<String, SseEmitter> sseClients = new ConcurrentHashMap<>();

    // WebSocket sessions (when WebSocket support is available)
    private final Map<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

    // In-memory event queue for polling
    private final BlockingQueue<YawlEvent> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    // Event ID generator
    private final AtomicLong eventIdGenerator = new AtomicLong(0);

    // Async event delivery executor
    private final ExecutorService executorService = Executors.newFixedThreadPool(10,
        new ThreadFactory() {
            private final AtomicLong threadCount = new AtomicLong(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "YawlEventNotifier-" + threadCount.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

    // Metrics
    private final AtomicLong eventsPublished = new AtomicLong(0);
    private final AtomicLong webhookFailures = new AtomicLong(0);
    private final AtomicLong sseFailures = new AtomicLong(0);

    /**
     * Constructs a new YawlEventNotifier with default HTTP client configuration.
     */
    public YawlEventNotifier() {
        // Initialize Jackson ObjectMapper with Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Initialize HTTP client
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(WEBHOOK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        logger.info("YawlEventNotifier initialized with HTTP webhook support");
    }

    /**
     * Constructs a YawlEventNotifier with custom HTTP client.
     *
     * @param httpClient custom OkHttpClient for webhook delivery
     */
    public YawlEventNotifier(OkHttpClient httpClient) {
        // Initialize Jackson ObjectMapper with Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.httpClient = httpClient;
        logger.info("YawlEventNotifier initialized with custom HTTP client");
    }

    // ==================== Public API ====================

    /**
     * Registers a webhook endpoint to receive event notifications via HTTP POST.
     *
     * @param webhookUrl the HTTP/HTTPS URL to POST events to
     * @throws IllegalArgumentException if URL is invalid
     */
    public void registerWebhook(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL cannot be null or empty");
        }

        if (!webhookUrl.startsWith("http://") && !webhookUrl.startsWith("https://")) {
            throw new IllegalArgumentException("Webhook URL must start with http:// or https://");
        }

        webhookEndpoints.add(webhookUrl);
        logger.info("Registered webhook endpoint: " + webhookUrl);
    }

    /**
     * Unregisters a webhook endpoint.
     *
     * @param webhookUrl the webhook URL to remove
     * @return true if the webhook was removed, false if it wasn't registered
     */
    public boolean unregisterWebhook(String webhookUrl) {
        boolean removed = webhookEndpoints.remove(webhookUrl);
        if (removed) {
            logger.info("Unregistered webhook endpoint: " + webhookUrl);
        }
        return removed;
    }

    /**
     * Registers an SSE client to receive push notifications.
     *
     * @param clientId unique identifier for the SSE client
     * @param emitter the SSE emitter to send events to
     */
    public void registerSseClient(String clientId, SseEmitter emitter) {
        if (clientId == null || emitter == null) {
            throw new IllegalArgumentException("Client ID and emitter cannot be null");
        }

        sseClients.put(clientId, emitter);
        logger.info("Registered SSE client: " + clientId);
    }

    /**
     * Unregisters an SSE client.
     *
     * @param clientId the client ID to remove
     * @return the removed emitter, or null if not found
     */
    public SseEmitter unregisterSseClient(String clientId) {
        SseEmitter removed = sseClients.remove(clientId);
        if (removed != null) {
            logger.info("Unregistered SSE client: " + clientId);
        }
        return removed;
    }

    /**
     * Registers a WebSocket session to receive push notifications.
     *
     * @param sessionId unique identifier for the WebSocket session
     * @param session the WebSocket session to send events to
     */
    public void registerWebSocket(String sessionId, WebSocketSession session) {
        if (sessionId == null || session == null) {
            throw new IllegalArgumentException("Session ID and session cannot be null");
        }

        webSocketSessions.put(sessionId, session);
        logger.info("Registered WebSocket session: " + sessionId);
    }

    /**
     * Unregisters a WebSocket session.
     *
     * @param sessionId the session ID to remove
     * @return the removed session, or null if not found
     */
    public WebSocketSession unregisterWebSocket(String sessionId) {
        WebSocketSession removed = webSocketSessions.remove(sessionId);
        if (removed != null) {
            logger.info("Unregistered WebSocket session: " + sessionId);
        }
        return removed;
    }

    /**
     * Polls the next event from the in-memory queue.
     *
     * @param timeout maximum time to wait for an event
     * @param unit time unit for the timeout
     * @return the next event, or null if timeout expires
     * @throws InterruptedException if interrupted while waiting
     */
    public YawlEvent pollEvent(long timeout, TimeUnit unit) throws InterruptedException {
        return eventQueue.poll(timeout, unit);
    }

    /**
     * Gets all pending events from the queue without blocking.
     *
     * @return list of pending events (may be empty)
     */
    public List<YawlEvent> getPendingEvents() {
        List<YawlEvent> events = new ArrayList<>();
        eventQueue.drainTo(events);
        return events;
    }

    /**
     * Gets the count of pending events in the queue.
     *
     * @return number of events waiting in queue
     */
    public int getPendingEventCount() {
        return eventQueue.size();
    }

    /**
     * Gets metrics about event delivery.
     *
     * @return map of metric names to values
     */
    public Map<String, Long> getMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("eventsPublished", eventsPublished.get());
        metrics.put("webhookFailures", webhookFailures.get());
        metrics.put("sseFailures", sseFailures.get());
        metrics.put("pendingEvents", (long) eventQueue.size());
        metrics.put("webhookEndpoints", (long) webhookEndpoints.size());
        metrics.put("sseClients", (long) sseClients.size());
        metrics.put("webSocketSessions", (long) webSocketSessions.size());
        return metrics;
    }

    // ==================== ObserverGateway Implementation ====================

    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    public void announceFiredWorkItem(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "workitem.enabled",
            Instant.now(),
            item != null ? item.getCaseID().toString() : null,
            item != null ? item.getSpecificationID() : null,
            item
        );

        event.setDescription("Work item enabled and ready for execution");
        publishEvent(event);
    }

    @Override
    public void announceCancelledWorkItem(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "workitem.cancelled",
            Instant.now(),
            item != null ? item.getCaseID().toString() : null,
            item != null ? item.getSpecificationID() : null,
            item
        );

        event.setDescription("Work item cancelled before completion");
        publishEvent(event);
    }

    @Override
    public void announceTimerExpiry(YAnnouncement announcement) {
        YWorkItem item = announcement.getItem();
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "timer.expired",
            Instant.now(),
            item != null ? item.getCaseID().toString() : null,
            item != null ? item.getSpecificationID() : null,
            item
        );

        event.setDescription("Task deadline approaching - timer expired");
        publishEvent(event);
    }

    @Override
    public void announceCaseCompletion(YAWLServiceReference yawlService,
                                      YIdentifier caseID, Document caseData) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.completed",
            Instant.now(),
            caseID.toString(),
            null,
            null
        );

        event.setDescription("Case completed successfully");
        event.setServiceReference(yawlService);
        event.setCaseData(caseData);
        publishEvent(event);
    }

    @Override
    public void announceCaseStarted(Set<YAWLServiceReference> services,
                                   YSpecificationID specID, YIdentifier caseID,
                                   String launchingService, boolean delayed) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.started",
            Instant.now(),
            caseID.toString(),
            specID,
            null
        );

        event.setDescription(delayed ? "Case started (delayed launch)" : "Case started");
        event.setLaunchingService(launchingService);
        event.setDelayed(delayed);
        publishEvent(event);
    }

    @Override
    public void announceCaseCompletion(Set<YAWLServiceReference> services,
                                      YIdentifier caseID, Document caseData) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.completed",
            Instant.now(),
            caseID.toString(),
            null,
            null
        );

        event.setDescription("Case completed successfully");
        event.setCaseData(caseData);
        publishEvent(event);
    }

    @Override
    public void announceCaseSuspended(Set<YAWLServiceReference> services, YIdentifier caseID) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.suspended",
            Instant.now(),
            caseID.toString(),
            null,
            null
        );

        event.setDescription("Case suspended");
        publishEvent(event);
    }

    @Override
    public void announceCaseSuspending(Set<YAWLServiceReference> services, YIdentifier caseID) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.suspending",
            Instant.now(),
            caseID.toString(),
            null,
            null
        );

        event.setDescription("Case entering suspended state");
        publishEvent(event);
    }

    @Override
    public void announceCaseResumption(Set<YAWLServiceReference> services, YIdentifier caseID) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.resumed",
            Instant.now(),
            caseID.toString(),
            null,
            null
        );

        event.setDescription("Case resumed from suspended state");
        publishEvent(event);
    }

    @Override
    public void announceWorkItemStatusChange(Set<YAWLServiceReference> services,
                                            YWorkItem workItem,
                                            YWorkItemStatus oldStatus,
                                            YWorkItemStatus newStatus) {
        String eventType;
        String description;

        if (newStatus == YWorkItemStatus.statusExecuting &&
            oldStatus != YWorkItemStatus.statusExecuting) {
            eventType = "workitem.started";
            description = "Work item started execution";
        } else if (newStatus == YWorkItemStatus.statusComplete) {
            eventType = "workitem.completed";
            description = "Work item completed";
        } else if (newStatus == YWorkItemStatus.statusIsParent) {
            eventType = "resource.allocated";
            description = "Resource allocated to work item";
        } else {
            eventType = "workitem.status_changed";
            description = "Work item status changed from " + oldStatus + " to " + newStatus;
        }

        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            eventType,
            Instant.now(),
            workItem.getCaseID().toString(),
            workItem.getSpecificationID(),
            workItem
        );

        event.setDescription(description);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        publishEvent(event);
    }

    @Override
    public void announceEngineInitialised(Set<YAWLServiceReference> services, int maxWaitSeconds) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "engine.initialized",
            Instant.now(),
            null,
            null,
            null
        );

        event.setDescription("YAWL Engine initialized and ready");
        publishEvent(event);
    }

    @Override
    public void announceCaseCancellation(Set<YAWLServiceReference> services, YIdentifier id) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "case.cancelled",
            Instant.now(),
            id.toString(),
            null,
            null
        );

        event.setDescription("Case cancelled");
        publishEvent(event);
    }

    @Override
    public void announceDeadlock(Set<YAWLServiceReference> services, YIdentifier id,
                                Set<YTask> tasks) {
        YawlEvent event = new YawlEvent(
            eventIdGenerator.incrementAndGet(),
            "exception.deadlock",
            Instant.now(),
            id.toString(),
            null,
            null
        );

        event.setDescription("Exception raised: Case deadlocked");
        event.setDeadlockedTasks(tasks);
        publishEvent(event);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down YawlEventNotifier...");

        // Close all SSE clients
        for (Map.Entry<String, SseEmitter> entry : sseClients.entrySet()) {
            try {
                entry.getValue().complete();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing SSE client " + entry.getKey(), e);
            }
        }
        sseClients.clear();

        // Close all WebSocket sessions
        for (Map.Entry<String, WebSocketSession> entry : webSocketSessions.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing WebSocket session " + entry.getKey(), e);
            }
        }
        webSocketSessions.clear();

        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear queue
        eventQueue.clear();

        logger.info("YawlEventNotifier shutdown complete");
    }

    // ==================== Event Publishing ====================

    /**
     * Publishes an event to all registered delivery mechanisms.
     *
     * @param event the event to publish
     */
    private void publishEvent(YawlEvent event) {
        eventsPublished.incrementAndGet();

        // Add to in-memory queue (for polling)
        if (!eventQueue.offer(event)) {
            logger.warning("Event queue full, dropping event: " + event.getEventType());
        }

        // Deliver to webhooks asynchronously
        for (String webhookUrl : webhookEndpoints) {
            executorService.submit(() -> deliverWebhook(webhookUrl, event));
        }

        // Deliver to SSE clients asynchronously
        for (Map.Entry<String, SseEmitter> entry : sseClients.entrySet()) {
            executorService.submit(() -> deliverSse(entry.getKey(), entry.getValue(), event));
        }

        // Deliver to WebSocket sessions asynchronously
        for (Map.Entry<String, WebSocketSession> entry : webSocketSessions.entrySet()) {
            executorService.submit(() -> deliverWebSocket(entry.getKey(), entry.getValue(), event));
        }

        logger.fine("Published event: " + event.getEventType() + " (ID: " + event.getEventId() + ")");
    }

    /**
     * Delivers an event to a webhook endpoint via HTTP POST.
     *
     * @param webhookUrl the webhook URL
     * @param event the event to deliver
     */
    private void deliverWebhook(String webhookUrl, YawlEvent event) {
        try {
            String jsonPayload = event.toJson(objectMapper);

            RequestBody body = RequestBody.create(
                jsonPayload,
                MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .header("X-YAWL-Event-ID", String.valueOf(event.getEventId()))
                .header("X-YAWL-Event-Type", event.getEventType())
                .header("X-YAWL-Timestamp", event.getTimestamp().toString())
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    webhookFailures.incrementAndGet();
                    logger.warning("Webhook delivery failed: " + webhookUrl +
                                 " (status: " + response.code() + ")");
                } else {
                    logger.fine("Webhook delivered: " + webhookUrl);
                }
            }
        } catch (IOException e) {
            webhookFailures.incrementAndGet();
            logger.log(Level.WARNING, "Webhook delivery error: " + webhookUrl, e);
        }
    }

    /**
     * Delivers an event to an SSE client.
     *
     * @param clientId the client ID
     * @param emitter the SSE emitter
     * @param event the event to deliver
     */
    private void deliverSse(String clientId, SseEmitter emitter, YawlEvent event) {
        try {
            emitter.send(event.toJson(objectMapper), event.getEventType());
            logger.fine("SSE delivered to client: " + clientId);
        } catch (Exception e) {
            sseFailures.incrementAndGet();
            logger.log(Level.WARNING, "SSE delivery failed for client: " + clientId, e);

            // Remove failed client
            sseClients.remove(clientId);
        }
    }

    /**
     * Delivers an event to a WebSocket session.
     *
     * @param sessionId the session ID
     * @param session the WebSocket session
     * @param event the event to deliver
     */
    private void deliverWebSocket(String sessionId, WebSocketSession session, YawlEvent event) {
        try {
            if (session.isOpen()) {
                session.send(event.toJson(objectMapper));
                logger.fine("WebSocket delivered to session: " + sessionId);
            } else {
                webSocketSessions.remove(sessionId);
                logger.info("Removed closed WebSocket session: " + sessionId);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "WebSocket delivery failed for session: " + sessionId, e);

            // Remove failed session
            webSocketSessions.remove(sessionId);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Represents a YAWL workflow event.
     */
    public static class YawlEvent {
        private final long eventId;
        private final String eventType;
        private final Instant timestamp;
        private final String caseId;
        private final YSpecificationID specificationId;
        private final YWorkItem workItem;

        private String description;
        private YAWLServiceReference serviceReference;
        private Document caseData;
        private String launchingService;
        private boolean delayed;
        private YWorkItemStatus oldStatus;
        private YWorkItemStatus newStatus;
        private Set<YTask> deadlockedTasks;

        public YawlEvent(long eventId, String eventType, Instant timestamp,
                        String caseId, YSpecificationID specificationId, YWorkItem workItem) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.caseId = caseId;
            this.specificationId = specificationId;
            this.workItem = workItem;
        }

        public long getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public Instant getTimestamp() { return timestamp; }
        public String getCaseId() { return caseId; }
        public YSpecificationID getSpecificationId() { return specificationId; }
        public YWorkItem getWorkItem() { return workItem; }
        public String getDescription() { return description; }

        public void setDescription(String description) { this.description = description; }
        public void setServiceReference(YAWLServiceReference ref) { this.serviceReference = ref; }
        public void setCaseData(Document data) { this.caseData = data; }
        public void setLaunchingService(String service) { this.launchingService = service; }
        public void setDelayed(boolean delayed) { this.delayed = delayed; }
        public void setOldStatus(YWorkItemStatus status) { this.oldStatus = status; }
        public void setNewStatus(YWorkItemStatus status) { this.newStatus = status; }
        public void setDeadlockedTasks(Set<YTask> tasks) { this.deadlockedTasks = tasks; }

        /**
         * Converts the event to JSON format for delivery using Jackson ObjectMapper.
         *
         * @return JSON representation of the event
         * @throws RuntimeException if JSON serialization fails
         */
        public String toJson(ObjectMapper mapper) {
            try {
                ObjectNode json = mapper.createObjectNode();

                json.put("eventId", eventId);
                json.put("eventType", eventType);
                json.put("timestamp", timestamp.toString());

                if (caseId != null) {
                    json.put("caseId", caseId);
                }

                if (specificationId != null) {
                    ObjectNode specNode = json.putObject("specificationId");
                    specNode.put("uri", specificationId.getUri());
                    specNode.put("version", specificationId.getVersionAsString());
                }

                if (workItem != null) {
                    ObjectNode workItemNode = json.putObject("workItem");
                    workItemNode.put("id", workItem.getIDString());
                    workItemNode.put("taskId", workItem.getTaskID());
                    workItemNode.put("status", workItem.getStatus().toString());
                }

                if (description != null) {
                    json.put("description", description);
                }

                if (launchingService != null) {
                    json.put("launchingService", launchingService);
                    json.put("delayed", delayed);
                }

                if (oldStatus != null && newStatus != null) {
                    ObjectNode statusNode = json.putObject("statusChange");
                    statusNode.put("from", oldStatus.toString());
                    statusNode.put("to", newStatus.toString());
                }

                if (caseData != null) {
                    XMLOutputter outputter = new XMLOutputter();
                    String xmlData = outputter.outputString(caseData);
                    json.put("caseData", xmlData);
                }

                if (deadlockedTasks != null && !deadlockedTasks.isEmpty()) {
                    ArrayNode tasksArray = json.putArray("deadlockedTasks");
                    for (YTask task : deadlockedTasks) {
                        tasksArray.add(task.getID());
                    }
                }

                return mapper.writeValueAsString(json);
            } catch (IOException e) {
                throw new RuntimeException("Failed to serialize event to JSON: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Placeholder interface for SSE emitter functionality.
     * In production, this would be implemented by framework-specific SSE classes
     * (e.g., Spring's SseEmitter, Jakarta's SseEventSink).
     */
    public interface SseEmitter {
        void send(String data, String eventType) throws IOException;
        void complete();
    }

    /**
     * Placeholder interface for WebSocket session functionality.
     * In production, this would be implemented by framework-specific WebSocket classes
     * (e.g., javax.websocket.Session, Spring WebSocketSession).
     */
    public interface WebSocketSession {
        void send(String message) throws IOException;
        boolean isOpen();
        void close() throws IOException;
    }
}
