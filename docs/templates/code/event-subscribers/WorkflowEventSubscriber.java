package org.yawlfoundation.yawl.examples.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * TEMPLATE: Workflow Event Subscriber
 * PURPOSE: Listen to YAWL workflow events and react to them
 * CUSTOMIZATION: Add event handlers, update filtering logic, integrate with your systems
 * LINK: docs/EVENTS-GUIDE.md#event-subscribers
 *
 * Events published by YAWL Engine:
 * - case.launched: New case started
 * - case.completed: Case finished successfully
 * - case.cancelled: Case was cancelled
 * - workitem.enabled: Task ready for execution
 * - workitem.started: User started a task
 * - workitem.completed: User completed a task
 * - workitem.suspended: Task suspended (waiting for condition)
 * - workflow.error: Error in workflow execution
 *
 * Usage:
 * WorkflowEventSubscriber subscriber = new WorkflowEventSubscriber("localhost:9092");
 * subscriber.subscribe("yawl-workflow-events");
 * subscriber.start();
 */
public class WorkflowEventSubscriber extends Thread {

    private static final Logger log = Logger.getLogger(WorkflowEventSubscriber.class.getSimpleName());

    private final String bootstrapServers;
    private final String groupId;
    private final ObjectMapper objectMapper;
    private KafkaConsumer<String, String> consumer;
    private volatile boolean running = true;

    // Event counters for monitoring
    private long caseStartedCount = 0;
    private long caseCompletedCount = 0;
    private long workItemCompletedCount = 0;
    private long errorCount = 0;

    /**
     * Constructor: Initialize event subscriber
     * @param bootstrapServers Kafka broker addresses (e.g., "localhost:9092")
     */
    public WorkflowEventSubscriber(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = "yawl-event-consumer-" + UUID.randomUUID().toString();
        this.objectMapper = new ObjectMapper();
        this.setName("WorkflowEventSubscriber");
        this.setDaemon(false);
    }

    /**
     * Subscribe to event topic
     * @param topic topic name (e.g., "yawl-workflow-events")
     */
    public void subscribe(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        log.info("Subscribed to topic: " + topic);
    }

    /**
     * Main event processing loop
     * Automatically called when thread is started
     */
    @Override
    public void run() {
        log.info("Event subscriber started");

        while (running) {
            try {
                // Poll for new events (100ms timeout)
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                for (var record : records) {
                    try {
                        processEvent(record.value());
                    } catch (Exception e) {
                        log.severe("Error processing event: " + e.getMessage());
                        errorCount++;
                    }
                }

                // Log metrics periodically
                if (System.currentTimeMillis() % 60000 == 0) {
                    logMetrics();
                }

            } catch (org.apache.kafka.common.errors.WakeupException e) {
                log.info("Consumer woke up");
            } catch (Exception e) {
                log.severe("Error in event processing loop: " + e.getMessage());
            }
        }

        shutdown();
    }

    /**
     * Process individual workflow event
     * @param eventJson JSON string with event data
     */
    private void processEvent(String eventJson) throws Exception {
        // Parse event JSON
        WorkflowEvent event = parseEvent(eventJson);
        log.info("Processing event: " + event.eventType + " - " + event.caseId);

        // Route to appropriate handler based on event type
        switch (event.eventType) {
            case "case.launched":
                handleCaseLaunched(event);
                caseStartedCount++;
                break;

            case "case.completed":
                handleCaseCompleted(event);
                caseCompletedCount++;
                break;

            case "case.cancelled":
                handleCaseCancelled(event);
                break;

            case "workitem.enabled":
                handleWorkItemEnabled(event);
                break;

            case "workitem.started":
                handleWorkItemStarted(event);
                break;

            case "workitem.completed":
                handleWorkItemCompleted(event);
                workItemCompletedCount++;
                break;

            case "workitem.suspended":
                handleWorkItemSuspended(event);
                break;

            case "workflow.error":
                handleWorkflowError(event);
                errorCount++;
                break;

            default:
                log.warning("Unknown event type: " + event.eventType);
        }
    }

    /**
     * Handler: Case started
     */
    private void handleCaseLaunched(WorkflowEvent event) {
        log.info("Case launched: " + event.caseId + " - Spec: " + event.specificationId);

        // Example: Log case start in external system
        logToExternalSystem("CASE_STARTED", event.caseId, event.data);

        // Example: Send notification
        sendNotification("A new case has started: " + event.caseId);

        // Example: Store metadata in data warehouse
        storeMetrics("case_launched", event);
    }

    /**
     * Handler: Case completed successfully
     */
    private void handleCaseCompleted(WorkflowEvent event) {
        log.info("Case completed: " + event.caseId);
        log.info("Duration: " + (event.completedAt - event.launchedAt) + "ms");

        // Example: Update case status in database
        updateCaseStatus(event.caseId, "completed");

        // Example: Trigger post-completion actions
        long durationSeconds = (event.completedAt - event.launchedAt) / 1000;
        logToExternalSystem("CASE_COMPLETED", event.caseId,
            "Duration: " + durationSeconds + " seconds");

        // Example: SLA analysis
        analyzeSLA(event.caseId, durationSeconds);
    }

    /**
     * Handler: Case cancelled/aborted
     */
    private void handleCaseCancelled(WorkflowEvent event) {
        log.warning("Case cancelled: " + event.caseId);

        // Example: Rollback any side effects
        rollbackCaseChanges(event.caseId);

        // Example: Notify stakeholders
        sendNotification("Case " + event.caseId + " has been cancelled");
    }

    /**
     * Handler: Work item (task) is ready for execution
     */
    private void handleWorkItemEnabled(WorkflowEvent event) {
        log.info("Work item enabled: " + event.taskId + " in case " + event.caseId);

        // Example: Assign task to appropriate resource
        assignTaskToResource(event.caseId, event.taskId, event.taskName);

        // Example: Send notifications to assigned users
        notifyAssignedUsers(event.taskId);

        // Example: Start deadline timer
        startDeadlineTimer(event.taskId, 24 * 60 * 60);  // 24 hour deadline
    }

    /**
     * Handler: Work item started by user
     */
    private void handleWorkItemStarted(WorkflowEvent event) {
        log.info("Work item started: " + event.taskId + " by " + event.userId);

        // Example: Log user interaction
        logUserInteraction(event.userId, event.taskId, "started");

        // Example: Update SLA timer
        updateSLATimer(event.taskId, event.startedAt);

        // Example: Send reminder if long pause
        scheduleIdleReminder(event.taskId, 60 * 60);  // 1 hour
    }

    /**
     * Handler: Work item completed by user
     */
    private void handleWorkItemCompleted(WorkflowEvent event) {
        log.info("Work item completed: " + event.taskId + " by " + event.userId);
        long durationMs = event.completedAt - event.startedAt;

        // Example: Log completion
        logUserInteraction(event.userId, event.taskId, "completed", durationMs);

        // Example: Update metrics
        recordTaskDuration(event.taskName, durationMs);

        // Example: Extract output data for downstream processing
        String outputData = event.outputData;
        if (outputData != null && !outputData.isEmpty()) {
            processTaskOutput(event.taskId, outputData);
        }

        // Example: Trigger next step notifications
        notifyNextTasks(event.caseId);
    }

    /**
     * Handler: Work item suspended (waiting for condition)
     */
    private void handleWorkItemSuspended(WorkflowEvent event) {
        log.info("Work item suspended: " + event.taskId + " reason: " + event.data);

        // Example: Release resources
        releaseAllocatedResources(event.taskId);

        // Example: Log suspension reason
        logToExternalSystem("TASK_SUSPENDED", event.taskId, event.data);
    }

    /**
     * Handler: Workflow error
     */
    private void handleWorkflowError(WorkflowEvent event) {
        log.severe("Workflow error in case: " + event.caseId);
        log.severe("Error: " + event.errorMessage);
        log.severe("Stack trace: " + event.errorStackTrace);

        // Example: Alert operations team
        alertOpsTeam("YAWL Workflow Error", "Case: " + event.caseId + "\nError: " + event.errorMessage);

        // Example: Escalate case to support
        escalateCase(event.caseId, "Technical Error", event.errorMessage);

        // Example: Log to error tracking system (e.g., Sentry, DataDog)
        logErrorToTracking(event);
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        log.info("Shutting down event subscriber");
        running = false;

        if (consumer != null) {
            consumer.close();
        }

        logMetrics();
    }

    /**
     * Helper methods
     */

    private WorkflowEvent parseEvent(String eventJson) throws Exception {
        // Parse event from JSON using ObjectMapper
        com.fasterxml.jackson.databind.node.ObjectNode node =
            (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(eventJson);

        WorkflowEvent event = new WorkflowEvent();
        event.eventType = node.get("eventType").asText();
        event.caseId = node.get("caseId").asText();
        event.taskId = node.path("taskId").asText();
        event.taskName = node.path("taskName").asText();
        event.userId = node.path("userId").asText();
        event.specificationId = node.path("specificationId").asText();
        event.timestamp = node.get("timestamp").asLong();
        event.launchedAt = node.path("launchedAt").asLong(0);
        event.startedAt = node.path("startedAt").asLong(0);
        event.completedAt = node.path("completedAt").asLong(0);
        event.outputData = node.path("outputData").asText();
        event.errorMessage = node.path("errorMessage").asText();
        event.errorStackTrace = node.path("errorStackTrace").asText();
        event.data = node.path("data").asText();

        return event;
    }

    private void logToExternalSystem(String action, String caseId, String details) {
        log.info("External log: " + action + " - " + caseId + " - " + details);
        // TODO: Implement actual logging to external system (data warehouse, audit log, etc.)
    }

    private void sendNotification(String message) {
        log.info("Sending notification: " + message);
        // TODO: Implement notification delivery (email, Slack, SMS, etc.)
    }

    private void storeMetrics(String metric, WorkflowEvent event) {
        log.info("Storing metric: " + metric);
        // TODO: Send metrics to monitoring system (Prometheus, Grafana, etc.)
    }

    private void updateCaseStatus(String caseId, String status) {
        log.info("Updating case status: " + caseId + " -> " + status);
        // TODO: Update database
    }

    private void analyzeSLA(String caseId, long durationSeconds) {
        final long SLA_THRESHOLD = 24 * 60 * 60;  // 24 hours
        if (durationSeconds > SLA_THRESHOLD) {
            log.warning("SLA violation detected: " + caseId + " took " + durationSeconds + "s");
            // TODO: Send SLA violation alert
        }
    }

    private void rollbackCaseChanges(String caseId) {
        log.info("Rolling back case: " + caseId);
        // TODO: Implement rollback logic
    }

    private void assignTaskToResource(String caseId, String taskId, String taskName) {
        log.info("Assigning task: " + taskId + " in case " + caseId);
        // TODO: Call resource allocation system
    }

    private void notifyAssignedUsers(String taskId) {
        log.info("Notifying assigned users for task: " + taskId);
        // TODO: Send notifications to assigned users
    }

    private void startDeadlineTimer(String taskId, long durationSeconds) {
        log.info("Starting deadline timer for task: " + taskId);
        // TODO: Set up task deadline timer
    }

    private void logUserInteraction(String userId, String taskId, String action) {
        logUserInteraction(userId, taskId, action, 0);
    }

    private void logUserInteraction(String userId, String taskId, String action, long durationMs) {
        log.info("User interaction: " + userId + " - " + action + " - " + taskId);
        // TODO: Log to audit trail
    }

    private void updateSLATimer(String taskId, long startedAt) {
        log.info("Updating SLA timer for task: " + taskId);
        // TODO: Update SLA tracking
    }

    private void scheduleIdleReminder(String taskId, long delaySeconds) {
        log.info("Scheduling idle reminder for task: " + taskId);
        // TODO: Schedule reminder
    }

    private void recordTaskDuration(String taskName, long durationMs) {
        log.info("Recording task duration: " + taskName + " - " + durationMs + "ms");
        // TODO: Update task metrics
    }

    private void processTaskOutput(String taskId, String outputData) {
        log.info("Processing output from task: " + taskId);
        // TODO: Process task output data
    }

    private void notifyNextTasks(String caseId) {
        log.info("Notifying next tasks in case: " + caseId);
        // TODO: Send notifications to next task assignees
    }

    private void releaseAllocatedResources(String taskId) {
        log.info("Releasing resources for task: " + taskId);
        // TODO: Release allocated resources
    }

    private void alertOpsTeam(String title, String message) {
        log.severe("ALERT: " + title + " - " + message);
        // TODO: Send alert to ops team (PagerDuty, OpsGenie, etc.)
    }

    private void escalateCase(String caseId, String reason, String details) {
        log.warning("Escalating case: " + caseId + " - " + reason);
        // TODO: Create support ticket or escalate
    }

    private void logErrorToTracking(WorkflowEvent event) {
        log.severe("Logging error to tracking system: " + event.errorMessage);
        // TODO: Send to error tracking service (Sentry, DataDog, etc.)
    }

    private void logMetrics() {
        log.info("=== Event Metrics ===");
        log.info("Cases started: " + caseStartedCount);
        log.info("Cases completed: " + caseCompletedCount);
        log.info("Work items completed: " + workItemCompletedCount);
        log.info("Errors: " + errorCount);
    }

    /**
     * Data classes
     */

    public static class WorkflowEvent {
        public String eventType;
        public String caseId;
        public String taskId;
        public String taskName;
        public String userId;
        public String specificationId;
        public long timestamp;
        public long launchedAt;
        public long startedAt;
        public long completedAt;
        public String outputData;
        public String errorMessage;
        public String errorStackTrace;
        public String data;

        @Override
        public String toString() {
            return "WorkflowEvent{" +
                "eventType='" + eventType + '\'' +
                ", caseId='" + caseId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", timestamp=" + timestamp +
                '}';
        }
    }

    /**
     * Example: Main method to start subscriber
     */
    public static void main(String[] args) {
        WorkflowEventSubscriber subscriber = new WorkflowEventSubscriber("localhost:9092");
        subscriber.subscribe("yawl-workflow-events");
        subscriber.start();

        // Shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down subscriber...");
            subscriber.shutdown();
        }));
    }
}
