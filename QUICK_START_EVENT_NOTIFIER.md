# YAWL Event Notifier - Quick Start

## 5-Minute Integration

```java
import org.yawlfoundation.yawl.integration.YawlEventNotifier;
import org.yawlfoundation.yawl.engine.interfce.EngineGateway;
import org.yawlfoundation.yawl.engine.interfce.EngineGatewayImpl;

// 1. Create notifier
YawlEventNotifier notifier = new YawlEventNotifier();

// 2. Register webhook (HTTP POST endpoint)
notifier.registerWebhook("http://your-server.com:9000/yawl/events");

// 3. Integrate with YAWL Engine
EngineGateway gateway = new EngineGatewayImpl(true);
gateway.registerObserverGateway(notifier);

// Done! All workflow events now sent to your webhook
```

## Events You'll Receive

| Event Type | When It Fires |
|------------|---------------|
| `case.started` | New workflow case launched |
| `case.completed` | Workflow case finished |
| `case.cancelled` | Workflow case terminated |
| `workitem.enabled` | Task ready for work |
| `workitem.started` | Task execution began |
| `workitem.completed` | Task finished |
| `timer.expired` | Deadline approaching |
| `exception.deadlock` | Deadlock detected |
| `resource.allocated` | Resource assigned |

## Webhook Payload (JSON)

```json
{
  "eventId": 123,
  "eventType": "workitem.enabled",
  "timestamp": "2026-02-14T10:30:00Z",
  "caseId": "1.2",
  "specificationId": {
    "uri": "OrderFulfillment",
    "version": "0.3"
  },
  "workItem": {
    "id": "1.2-PackageOrder-3",
    "taskId": "PackageOrder",
    "status": "Enabled"
  },
  "description": "Work item enabled and ready for execution"
}
```

## Alternative: Polling Events

```java
// Poll with timeout
YawlEvent event = notifier.pollEvent(5, TimeUnit.SECONDS);

// Or get all pending
List<YawlEvent> events = notifier.getPendingEvents();
```

## Check Metrics

```java
Map<String, Long> metrics = notifier.getMetrics();
System.out.println("Events published: " + metrics.get("eventsPublished"));
System.out.println("Webhook failures: " + metrics.get("webhookFailures"));
```

## Files

- **Implementation:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/YawlEventNotifier.java`
- **Examples:** `/home/user/yawl/src/org/yawlfoundation/yawl/integration/YawlEventNotifierExample.java`
- **Full Guide:** `/home/user/yawl/EVENT_NOTIFICATION_GUIDE.md`

## Requirements

- OkHttp 5.2.1 ✅ (available)
- Jackson 2.18.2 ✅ (available)
- JDOM2 ✅ (available)

**Status:** Production ready for HTTP webhooks and polling.
