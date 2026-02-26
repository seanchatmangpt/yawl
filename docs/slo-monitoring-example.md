# YAWL SLO Monitoring Framework - Usage Example

## Overview

This document provides a comprehensive example of how to use the YAWL SLO (Service Level Objective) monitoring framework to track and alert on SLA/SLO compliance.

## SLO Definitions

The framework tracks the following SLOs:

1. **SLO_CASE_COMPLETION**: 99.9% of cases complete within 24 hours
2. **SLO_TASK_EXECUTION**: 99.5% of tasks complete within 1 hour
3. **SLO_QUEUE_RESPONSE**: 99% of queue responses within 5 minutes
4. **SLO_VIRTUAL_THREAD_PINNING**: <0.1% virtual thread pinning
5. **SLO_LOCK_CONTENTION**: <5% lock contention >100ms

## Basic Usage

### 1. Initialize the Service

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.yawlfoundation.yawl.observability.*;
import org.yawlfoundation.yawl.observability.AndonCord;

public class SLOMonitoringExample {
    public static void main(String[] args) {
        // Initialize metrics registry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Initialize AndonCord for alerting
        AndonCord andonCord = new AndonCord(meterRegistry);

        // Create SLO integration service
        SLOIntegrationService sloService = new SLOIntegrationService.Builder()
            .meterRegistry(meterRegistry)
            .andonCord(andonCord)
            .enableDashboard(true)
            .enablePredictiveAnalytics(true)
            .enableAlertManager(true)
            .build();

        // Initialize and start the service
        sloService.initialize();
        sloService.start();

        // Start background components
        if (sloService.getDashboard() != null) {
            sloService.getDashboard().start();
        }
        if (sloService.getPredictiveAnalytics() != null) {
            sloService.getPredictiveAnalytics().start();
        }
        if (sloService.getAlertManager() != null) {
            sloService.getAlertManager().start();
        }
    }
}
```

### 2. Recording Events

```java
// Record case completion
Map<String, String> caseContext = new HashMap<>();
caseContext.put("case_type", "approval_workflow");
caseContext.put("priority", "high");
caseContext.put("requester", "user123");

sloService.recordCaseCompletion("case-12345", 3600000, caseContext); // 1 hour

// Record task execution
Map<String, String> taskContext = new HashMap<>();
taskContext.put("task_type", "manual_review");
taskContext.put("assignee", "john_doe");
taskContext.put("case_id", "case-12345");

sloService.recordTaskExecution("task-67890", "case-12345", 1800000, taskContext); // 30 minutes

// Record queue response
Map<String, String> queueContext = new HashMap<>();
queueContext.put("queue_name", "task_queue");
queueContext.put("service", "workitem_service");

sloService.recordQueueResponse("queue-abc", 120000, queueContext); // 2 minutes

// Record virtual thread pinning
Map<String, String> threadContext = new HashMap<>();
threadContext.put("thread_name", "case-processor-1");
threadContext.put("operation", "task_execution");

sloService.recordVirtualThreadPinning("case-processor-1", false, threadContext);

// Record lock contention
Map<String, String> lockContext = new HashMap<>();
lockContext.put("lock_name", "case_store");
lockContext.put("operation", "update_case");

sloService.recordLockContention("case_store", 75, false, lockContext); // 75ms, not contented
```

### 3. Monitoring Compliance Status

```java
// Get current compliance status
Map<String, SLOTracker.ComplianceStatus> complianceStatus = sloService.getComplianceStatus();

// Display status
complianceStatus.forEach((sloId, status) -> {
    System.out.println("SLO: " + sloId);
    System.out.println("  Description: " + status.getDescription());
    System.out.println("  Compliance: " + String.format("%.1f%%", status.getCompliancePercentage()));
    System.out.println("  Status: " + status.getStatus());
    System.out.println();
});
```

### 4. Using the Dashboard

```java
// Generate HTML dashboard report
String htmlReport = sloService.generateHtmlReport();
Files.write(Paths.get("slo-dashboard.html"), htmlReport.getBytes());

// Generate JSON report for API consumption
Instant from = Instant.now().minusHours(24);
Instant to = Instant.now();
String jsonReport = sloService.generateJsonReport(from, to);
```

### 5. Predictive Analytics

```java
// Get current predictions
Map<String, SLOPredictiveAnalytics.PredictionResult> predictions = sloService.getPredictions();

// Display predictions
predictions.forEach((sloId, prediction) -> {
    System.out.println("SLO: " + sloId);
    System.out.println("  Breach Probability: " + String.format("%.1f%%", prediction.getBreachProbability() * 100));
    System.out.println("  Forecast Period: " + prediction.getForecast().getForecastValues().size() + " periods");
    System.out.println("  Anomaly Detected: " + prediction.getAnomalyResult().isAnomaly());
    System.out.println();
});

// Check specific SLO prediction
SLOPredictiveAnalytics.PredictionResult casePrediction =
    sloService.getPredictions().get(SLOTracker.SLO_CASE_COMPLETION);

if (casePrediction != null && casePrediction.getBreachProbability() > 0.8) {
    System.out.println("Warning: Case completion SLO at high risk of breach!");
}
```

### 6. Alert Management

```java
// Acknowledge an alert
List<SLOAlertManager.Alert> activeAlerts = new ArrayList<>(sloService.getActiveAlerts());
for (SLOAlertManager.Alert alert : activeAlerts) {
    if (alert.getSeverity() == SLOAlertManager.AlertSeverity.CRITICAL) {
        sloService.getAlertManager().acknowledgeAlert(alert.getId(), "system_admin");
    }
}

// Get alert statistics
SLOAlertManager.AlertStats alertStats = sloService.getAlertStats();
System.out.println("Total Alerts: " + alertStats.getTotalAlerts());
System.out.println("Acknowledgment Rate: " + String.format("%.1f%%", alertStats.getAcknowledgmentRate() * 100));

// Mute an alert type
sloService.getAlertManager().muteAlert(SLOTracker.SLO_QUEUE_RESPONSE, SLOAlertManager.AlertSeverity.WARNING);

// Unmute later
sloService.getAlertManager().unmuteAlert(SLOTracker.SLO_QUEUE_RESPONSE, SLOAlertManager.AlertSeverity.WARNING);
```

## Integration with YAWL Engine

### 1. WorkItem Creation Hook

```java
public class WorkItemCreationHook {
    private SLOIntegrationService sloService;

    public void onWorkItemCreated(WorkItem workItem) {
        Map<String, String> context = new HashMap<>();
        context.put("workflow_id", workItem.getWorkflowID());
        context.put("task_name", workItem.getTaskName());
        context.put("case_id", workItem.getCaseID());

        sloService.recordTaskExecution(
            workItem.getID(),
            workItem.getCaseID(),
            0, // Start time tracking
            context
        );
    }
}
```

### 2. WorkItem Completion Hook

```java
public class WorkItemCompletionHook {
    private SLOIntegrationService sloService;

    public void onWorkItemCompleted(WorkItem workItem) {
        long duration = calculateWorkItemDuration(workItem);

        Map<String, String> context = new HashMap<>();
        context.put("workflow_id", workItem.getWorkflowID());
        context.put("task_name", workItem.getTaskName());
        context.put("assignee", workItem.getParticipant());
        context.put("outcome", workItem.getStatus());

        sloService.recordTaskCompletion(
            workItem.getID(),
            workItem.getCaseID(),
            duration,
            context
        );
    }

    private long calculateWorkItemDuration(WorkItem workItem) {
        // Implementation to calculate duration
        return System.currentTimeMillis() - workItem.getTimestamp();
    }
}
```

### 3. Case Lifecycle Tracking

```java
public class CaseLifecycleTracker {
    private SLOIntegrationService sloService;
    private Map<String, Long> caseStartTimes = new ConcurrentHashMap<>();

    public void onCaseStarted(YNetRunner engine, String caseId) {
        caseStartTimes.put(caseId, System.currentTimeMillis());
    }

    public void onCaseCompleted(YNetRunner engine, String caseId) {
        Long startTime = caseStartTimes.remove(caseId);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;

            Map<String, String> context = new HashMap<>();
            context.put("workflow_id", engine.getSpecificationID());
            context.put("priority", "normal");

            sloService.recordCaseCompletion(caseId, duration, context);
        }
    }
}
```

## Configuration

### 1. SLO Thresholds

```java
// Custom SLO thresholds
sloService.getSloTracker().defineSLA(
    "custom.case.completion",
    12 * 60 * 60 * 1000L, // 12 hours
    "Custom case completion target",
    98.5 // 98.5% compliance
);
```

### 2. Alert Configuration

```java
// Configure alert cooldown
SLOAlertManager.AlertManagerConfig config = new SLOAlertManager.AlertManagerConfig();
config.setAlertCooldownMs(120000); // 2 minutes
config.setMaxAlertsPerMinute(20);
config.setEscalationTimeoutMs(600000); // 10 minutes

sloService.getAlertManager().configure(config);
```

## Performance Considerations

1. **Batch Processing**: Events are processed in batches for performance
2. **Memory Management**: Old alerts and data are automatically cleaned up
3. **Thread Safety**: All components are thread-safe for concurrent access
4. **Metrics Overhead**: Minimal performance impact with efficient metrics collection

## Monitoring Dashboard Features

The HTML dashboard provides:

1. **Real-time Compliance**: Current SLO compliance status
2. **Historical Trends**: Time series charts showing compliance over time
3. **Alert Status**: Active alerts and their severity
4. **Predictive Indicators**: Early warning for potential violations
5. **Performance Metrics**: System health and performance data

## Troubleshooting

### Common Issues

1. **Events not appearing in dashboard**
   - Check that service is started
   - Verify event recording with proper context
   - Check for error logs

2. **Alerts not firing**
   - Verify AndonCord is configured
   - Check alert severity thresholds
   - Confirm alert is not muted

3. **High memory usage**
   - Check event queue size
   - Adjust data retention periods
   - Monitor batch processing

### Debug Mode

```java
// Enable debug logging
LoggerFactory.getLogger(SLOIntegrationService.class).setLevel(Level.DEBUG);

// Monitor metrics
System.out.println("Event Queue Size: " + sloService.getEventQueueSize());
System.out.println("Processed Events: " + sloService.getProcessedEventsCount());
System.out.println("Failed Events: " + sloService.getFailedEventsCount());
```

## Best Practices

1. **Context Enrichment**: Always provide meaningful context with events
2. **Error Handling**: Implement proper error handling for event recording
3. **Monitoring**: Monitor service health and metrics
4. **Alert Tuning**: Adjust alert thresholds based on historical data
5. **Regular Review**: Periodically review SLO definitions and compliance

## Integration Testing

```java
@Test
void testSLOIntegration() {
    // Initialize service
    SLOIntegrationService service = createTestService();

    // Record test events
    service.recordCaseCompletion("test-case", 1000, Map.of());

    // Verify compliance tracking
    Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
    assertNotNull(status);
    assertEquals(5, status.size());
}
```

This comprehensive example demonstrates how to effectively use the YAWL SLO monitoring framework to ensure service level objectives are met and to provide early warning of potential violations.