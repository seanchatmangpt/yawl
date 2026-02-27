# How to Subscribe to Workflow Events for Audit and Monitoring

## Problem

The YAWL engine processes workflow events (case start, work item completion, case termination) but your monitoring system, audit log, or external messaging layer needs to be notified in real-time without blocking the engine's case execution. The `WorkflowEventBus` SPI allows you to subscribe to these events asynchronously and react to them independently.

## Prerequisites

- YAWL v6.0+ (WorkflowEventBus introduced in v6.0)
- Java 21+ (virtual threads used by the event bus)
- Understanding of Java's `Flow.Subscriber` interface and `Consumer` functional interface
- A logging or monitoring destination (file, Kafka, database, HTTP endpoint)

## Steps

### 1. Review the WorkflowEventBus Interface

The event bus provides a simple subscription model:

```java
package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import java.util.function.Consumer;

/**
 * SPI interface for the YAWL workflow event bus.
 *
 * The event bus decouples the engine's internal processing from downstream consumers
 * (resource manager, event log, monitoring) without blocking the hot execution path.
 * Publishing is non-blocking; back-pressure is applied if subscribers are slow.
 *
 * Default implementation: FlowWorkflowEventBus uses the Java 21+
 * java.util.concurrent.Flow API (SubmissionPublisher) with one publisher
 * per event type. No external infrastructure required.
 *
 * Optional adapters (opt-in via Maven dependency + ServiceLoader):
 * - KafkaWorkflowEventBus — publishes to Kafka topic yawl.workflow.events
 */
public interface WorkflowEventBus extends AutoCloseable {

    /**
     * Publishes a workflow event to all registered subscribers for the event's type.
     *
     * This method is non-blocking: it enqueues the event in the subscriber's buffer.
     * If the buffer is full, the publisher applies back-pressure (drops or waits,
     * depending on the implementation).
     *
     * @param event the event to publish; must not be null
     */
    void publish(WorkflowEvent event);

    /**
     * Registers a handler that will be called for all events of the specified type.
     *
     * Handlers are invoked asynchronously on virtual threads. Multiple handlers
     * may be registered for the same event type.
     *
     * @param type    the event type to subscribe to; must not be null
     * @param handler the handler to invoke on each matching event; must not be null
     */
    void subscribe(YEventType type, Consumer<WorkflowEvent> handler);

    /**
     * Shuts down the event bus, releasing all resources.
     * After this call, publish and subscribe have undefined behaviour.
     */
    @Override
    void close();

    /**
     * Returns the default in-JVM event bus instance, loading it via ServiceLoader.
     * Falls back to FlowWorkflowEventBus if no provider is registered.
     *
     * @return the default WorkflowEventBus for the current JVM
     */
    static WorkflowEventBus defaultBus() {
        return java.util.ServiceLoader.load(WorkflowEventBus.class)
                .findFirst()
                .orElseGet(FlowWorkflowEventBus::new);
    }
}
```

### 2. Understand WorkflowEvent and YEventType

The `WorkflowEvent` record contains the event data:

```java
public record WorkflowEvent(
    YEventType type,        // CASE_STARTING, WORKITEM_COMPLETING, etc.
    String caseId,          // UUID of the case
    String tenantId,        // Owning tenant
    String workItemId,      // For work item events
    String data,            // JSON payload with event-specific details
    long timestamp          // milliseconds since epoch
) {}
```

`YEventType` is an enum of event types:

```java
public enum YEventType {
    CASE_STARTING,
    CASE_STARTED,
    CASE_SUSPENDING,
    CASE_SUSPENDED,
    CASE_RESUMING,
    CASE_RESUMED,
    CASE_COMPLETING,
    CASE_COMPLETED,
    CASE_CANCELLING,
    CASE_CANCELLED,
    WORKITEM_ENABLING,
    WORKITEM_ENABLED,
    WORKITEM_EXECUTING,
    WORKITEM_EXECUTING_INTERACTIVE,
    WORKITEM_EXECUTED,
    WORKITEM_COMPLETING,
    WORKITEM_COMPLETED,
    WORKITEM_FAILING,
    WORKITEM_FAILED,
    WORKITEM_SKIPPING,
    WORKITEM_SKIPPED,
    // ... more types
}
```

### 3. Create an Event Subscriber Implementation

Create a class that implements the subscription logic. Here's a complete audit log example:

```java
package com.example.yawl.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.spi.WorkflowEvent;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Audit log subscriber that logs all workflow events to a structured log.
 *
 * Subscribers are invoked asynchronously on virtual threads, so logging
 * I/O does not block the engine's case execution path.
 */
@Component
public class WorkflowAuditLogSubscriber {

    private static final Logger auditLog = LogManager.getLogger("audit");
    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    private final WorkflowEventBus eventBus;

    /**
     * Constructor injected with the default event bus from Spring context.
     * Subscribes to all critical case lifecycle events.
     */
    public WorkflowAuditLogSubscriber(WorkflowEventBus eventBus) {
        this.eventBus = eventBus;
        subscribeToEvents();
    }

    private void subscribeToEvents() {
        // Subscribe to case lifecycle events
        eventBus.subscribe(YEventType.CASE_STARTING, this::onCaseStarting);
        eventBus.subscribe(YEventType.CASE_STARTED, this::onCaseStarted);
        eventBus.subscribe(YEventType.CASE_COMPLETING, this::onCaseCompleting);
        eventBus.subscribe(YEventType.CASE_COMPLETED, this::onCaseCompleted);
        eventBus.subscribe(YEventType.CASE_CANCELLING, this::onCaseCancelling);
        eventBus.subscribe(YEventType.CASE_CANCELLED, this::onCaseCancelled);

        // Subscribe to work item events
        eventBus.subscribe(YEventType.WORKITEM_ENABLED, this::onWorkItemEnabled);
        eventBus.subscribe(YEventType.WORKITEM_COMPLETED, this::onWorkItemCompleted);
        eventBus.subscribe(YEventType.WORKITEM_FAILED, this::onWorkItemFailed);

        auditLog.info("WorkflowAuditLogSubscriber initialized. Listening for events.");
    }

    // Case event handlers

    private void onCaseStarting(WorkflowEvent event) {
        auditLog.info("AUDIT_CASE_STARTING: caseID={}, tenantID={}, timestamp={}",
            event.caseId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onCaseStarted(WorkflowEvent event) {
        auditLog.info("AUDIT_CASE_STARTED: caseID={}, tenantID={}, timestamp={}",
            event.caseId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onCaseCompleting(WorkflowEvent event) {
        auditLog.info("AUDIT_CASE_COMPLETING: caseID={}, tenantID={}, timestamp={}",
            event.caseId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onCaseCompleted(WorkflowEvent event) {
        auditLog.info("AUDIT_CASE_COMPLETED: caseID={}, tenantID={}, data={}, timestamp={}",
            event.caseId(), event.tenantId(), event.data(), formatTime(event.timestamp()));
    }

    private void onCaseCancelling(WorkflowEvent event) {
        auditLog.warn("AUDIT_CASE_CANCELLING: caseID={}, tenantID={}, timestamp={}",
            event.caseId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onCaseCancelled(WorkflowEvent event) {
        auditLog.warn("AUDIT_CASE_CANCELLED: caseID={}, tenantID={}, timestamp={}",
            event.caseId(), event.tenantId(), formatTime(event.timestamp()));
    }

    // Work item event handlers

    private void onWorkItemEnabled(WorkflowEvent event) {
        auditLog.debug("AUDIT_WORKITEM_ENABLED: caseID={}, workItemID={}, tenantID={}, timestamp={}",
            event.caseId(), event.workItemId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onWorkItemCompleted(WorkflowEvent event) {
        auditLog.info("AUDIT_WORKITEM_COMPLETED: caseID={}, workItemID={}, tenantID={}, timestamp={}",
            event.caseId(), event.workItemId(), event.tenantId(), formatTime(event.timestamp()));
    }

    private void onWorkItemFailed(WorkflowEvent event) {
        auditLog.error("AUDIT_WORKITEM_FAILED: caseID={}, workItemID={}, tenantID={}, data={}, timestamp={}",
            event.caseId(), event.workItemId(), event.tenantId(), event.data(), formatTime(event.timestamp()));
    }

    private String formatTime(long millis) {
        return ISO_FORMATTER.format(Instant.ofEpochMilli(millis));
    }
}
```

### 4. Register the Subscriber in Spring

Ensure the subscriber class is instantiated by Spring (via `@Component` or explicit `@Bean`). The constructor will subscribe to the event bus on startup.

If you prefer explicit configuration, use `@Configuration`:

```java
package com.example.yawl.config;

import com.example.yawl.audit.WorkflowAuditLogSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;

@Configuration
public class EventBusConfiguration {

    /**
     * Conditionally enable audit logging if the property is set.
     * In application.properties: yawl.audit.enabled=true
     */
    @Bean
    @ConditionalOnProperty(name = "yawl.audit.enabled", havingValue = "true")
    public WorkflowAuditLogSubscriber auditLogSubscriber(WorkflowEventBus eventBus) {
        return new WorkflowAuditLogSubscriber(eventBus);
    }
}
```

Add to `application.properties`:

```properties
yawl.audit.enabled=true
```

### 5. Create a Custom Subscriber for External Systems

For integration with Kafka, HTTP webhooks, or databases, extend the pattern:

```java
package com.example.yawl.integration;

import org.yawlfoundation.yawl.engine.spi.WorkflowEvent;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Publishes workflow events to Kafka topic "yawl.workflow.events".
 *
 * Each event is serialized to JSON and sent asynchronously.
 */
public class KafkaWorkflowEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaWorkflowEventPublisher(
            WorkflowEventBus eventBus,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        subscribeToAllEvents(eventBus);
    }

    private void subscribeToAllEvents(WorkflowEventBus eventBus) {
        // Subscribe to all event types
        for (YEventType type : YEventType.values()) {
            eventBus.subscribe(type, this::publishToKafka);
        }
    }

    private void publishToKafka(WorkflowEvent event) {
        try {
            String json = serializeEvent(event);
            kafkaTemplate.send("yawl.workflow.events", event.caseId(), json);
        } catch (Exception e) {
            // Log but don't rethrow — misbehaving handler shouldn't block the event stream
            System.err.println("Failed to publish to Kafka: " + e.getMessage());
        }
    }

    private String serializeEvent(WorkflowEvent event) throws Exception {
        Map<String, Object> map = Map.of(
            "type", event.type().name(),
            "caseId", event.caseId(),
            "tenantId", event.tenantId(),
            "workItemId", event.workItemId() != null ? event.workItemId() : "",
            "timestamp", event.timestamp(),
            "data", event.data() != null ? event.data() : ""
        );
        return objectMapper.writeValueAsString(map);
    }
}
```

### 6. Get the Event Bus Instance

In your application startup, ensure the event bus is accessible. It can be injected via Spring:

```java
// Via Spring autowiring
@Autowired
private WorkflowEventBus eventBus;

// Or via static accessor (if Spring is not available)
WorkflowEventBus bus = WorkflowEventBus.defaultBus();
```

Subscribe at application startup:

```java
@SpringBootApplication
public class YawlEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(YawlEngineApplication.class, args);
    }

    @Bean
    public CommandLineRunner subscribeToEvents(WorkflowEventBus eventBus) {
        return args -> {
            eventBus.subscribe(YEventType.CASE_COMPLETED, event ->
                System.out.println("Case completed: " + event.caseId())
            );
        };
    }
}
```

## Verification

### 1. Confirm Subscriber Is Registered

Check engine logs for subscription confirmation:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "WorkflowAuditLogSubscriber"
```

Expected output:
```
[INFO] com.example.yawl.audit.WorkflowAuditLogSubscriber: WorkflowAuditLogSubscriber initialized. Listening for events.
```

### 2. Launch a Case and Verify Events Are Logged

Launch a test case:

```bash
SPEC_ID=$(curl -s "http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib?action=listSpecifications" | grep -o '"specID":"[^"]*' | head -1 | cut -d'"' -f4)

CASE=$(curl -s "http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib?action=launchCase&specID=$SPEC_ID&sessionHandle=...")
echo "Launched case: $CASE"
```

Then check the audit log:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "audit_case_starting\|audit_case_started"
```

Expected output (from the audit log):
```
[INFO] audit: AUDIT_CASE_STARTING: caseID=550e8400-e29b-41d4-a716-446655440000, tenantID=default, timestamp=2026-02-27T14:32:15.123Z
[INFO] audit: AUDIT_CASE_STARTED: caseID=550e8400-e29b-41d4-a716-446655440000, tenantID=default, timestamp=2026-02-27T14:32:15.234Z
```

### 3. Complete a Work Item and Verify Work Item Events

Find an enabled work item:

```bash
curl -s "http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib?action=getWorkItems&sessionHandle=..." | grep workItemID
```

Complete it:

```bash
curl -s "http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib?action=completeWorkItem&workItemID=..." \
  -d "data=<workItemData/>" \
  -d "sessionHandle=..."
```

Then check logs for work item events:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "audit_workitem"
```

## Troubleshooting

### Subscriber Not Being Called

Check that the subscriber is registered:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "listening for events"
```

If not present, verify Spring is creating the bean:

```java
@Component
public class DebugEventBusSubscriber {
    public DebugEventBusSubscriber(WorkflowEventBus eventBus) {
        System.out.println("DebugEventBusSubscriber created. EventBus type: " +
            eventBus.getClass().getName());
    }
}
```

### Events Are Not Appearing in Logs or Kafka

Check that the handler is not throwing exceptions (exceptions are caught and logged):

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "exception\|error" | head -10
```

Add logging to the handler to confirm it's being invoked:

```java
private void onCaseStarting(WorkflowEvent event) {
    System.out.println("onCaseStarting called for case: " + event.caseId());
    auditLog.info("AUDIT_CASE_STARTING: ...");
}
```

### Kafka Publishing Slow or Timing Out

Kafka topic or broker is unreachable. Check connectivity:

```bash
kubectl exec -it deployment/yawl-engine -- \
  nc -zv kafka.yawl.svc.cluster.local 9092
```

If timeout, verify Kafka service is running:

```bash
kubectl get svc kafka -n yawl
kubectl logs statefulset/kafka -n yawl
```

### High Memory Usage from Event Backlog

If subscribers are slow (e.g., writing to a slow database), events queue up in the publisher's buffer. Increase buffer size or optimize the subscriber:

```java
// In the event bus configuration
eventBus.setMaxBufferCapacity(10000);  // Default is 1000
```

Or optimize the subscriber (e.g., batch writes to database instead of per-event writes).
