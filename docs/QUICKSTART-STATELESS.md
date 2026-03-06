# YStatelessEngine Quick Start — 5-Minute Integration Guide

**Time to first case execution**: ~5 minutes
**Complexity**: Beginner
**Target audience**: Developers, cloud-native users

---

## 🚀 Hello World: Launch Your First Case

### Step 1: Create a Simple Workflow (30 seconds)

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.elements.*;

// Load an existing workflow specification
YEngine engine = YEngine.getInstance();
String specXML = """
    <specification uri="HelloWorld" version="1.0">
        <net id="HelloWorldNet">
            <inputCondition id="InputCondition"/>
            <outputCondition id="OutputCondition"/>
            <task id="SayHello">
                <name>Say Hello</name>
                <flowsInto>
                    <nextElementRef id="OutputCondition"/>
                </flowsInto>
            </task>
            <flow source="InputCondition" target="SayHello"/>
            <flow source="SayHello" target="OutputCondition"/>
        </net>
    </specification>
    """;

String specID = engine.uploadSpecification(specXML);
System.out.println("Specification uploaded: " + specID);
```

### Step 2: Launch a Case (20 seconds)

```java
import org.yawlfoundation.yawl.stateless.YStatelessEngine;

// Stateless deployment (cloud-native)
YStatelessEngine engine = new YStatelessEngine();

// Launch a case
String caseID = engine.launchCase(specID, "case-001", "<data/>");
System.out.println("Case launched: " + caseID);
```

### Step 3: Execute a Work Item (2 minutes)

```java
// Get enabled work items
List<YWorkItem> items = engine.getEnabledWorkItems(caseID);
System.out.println("Available tasks: " + items.size());

YWorkItem task = items.get(0);
System.out.println("Task: " + task.getLabel() + " (ID: " + task.getID() + ")");

// Start the work item (allocate to user)
engine.startWorkItem(task, "alice");
System.out.println("Task assigned to alice");

// Complete the work item
String outputData = "<data><result>Hello World!</result></data>";
engine.completeWorkItem(task, outputData, null);
System.out.println("Task completed");
```

### Step 4: Check Case Status (1 minute)

```java
// Get updated enabled work items
List<YWorkItem> remaining = engine.getEnabledWorkItems(caseID);
if (remaining.isEmpty()) {
    System.out.println("Case completed!");
} else {
    System.out.println("Still waiting for: " + remaining.get(0).getLabel());
}
```

---

## 📋 Complete Working Example

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.elements.YWorkItem;
import java.util.List;

public class HelloWorldWorkflow {
    public static void main(String[] args) throws Exception {
        // Step 1: Upload specification
        YEngine designEngine = YEngine.getInstance();
        String specXML = loadWorkflowSpec(); // See Step 1 above
        String specID = designEngine.uploadSpecification(specXML);
        System.out.println("✓ Spec uploaded: " + specID);

        // Step 2: Create stateless engine and launch case
        YStatelessEngine engine = new YStatelessEngine();
        String caseID = engine.launchCase(specID, "case-001", "<data/>");
        System.out.println("✓ Case launched: " + caseID);

        // Step 3: Execute work items in sequence
        while (true) {
            List<YWorkItem> items = engine.getEnabledWorkItems(caseID);
            if (items.isEmpty()) {
                System.out.println("✓ Case completed!");
                break;
            }

            YWorkItem task = items.get(0);
            System.out.println("→ Starting task: " + task.getLabel());

            engine.startWorkItem(task, "alice");
            engine.completeWorkItem(task, "<data/>", null);
            System.out.println("✓ Task completed");
        }

        // Step 4: Export final state (optional, for persistence)
        String caseState = engine.unloadCase(caseID);
        System.out.println("✓ Case state exported (ready for storage)");
    }

    private static String loadWorkflowSpec() {
        return """
            <specification uri="HelloWorld" version="1.0">
                <!-- workflow XML here -->
            </specification>
            """;
    }
}
```

---

## 🎯 Common Patterns

### Pattern 1: Wait for User Input

```java
// Get work items assigned to specific user
List<YWorkItem> userTasks = engine.getEnabledWorkItems(caseID)
    .stream()
    .filter(task -> "alice".equals(task.getResourceStatus()))
    .collect(Collectors.toList());

for (YWorkItem task : userTasks) {
    System.out.println("Task for alice: " + task.getLabel());
    // UI displays task, waits for user to complete...
    engine.completeWorkItem(task, getUserInput(), null);
}
```

### Pattern 2: Automatic Decision Point

```java
// Get work item and complete based on data
YWorkItem approvalTask = engine.getEnabledWorkItems(caseID).get(0);

String decision = "APPROVED"; // or "REJECTED" based on business logic
String output = String.format("<decision>%s</decision>", decision);

engine.startWorkItem(approvalTask, "system");
engine.completeWorkItem(approvalTask, output, null);
```

### Pattern 3: Error Handling

```java
try {
    engine.completeWorkItem(task, outputData, null);
} catch (YStateException e) {
    System.err.println("Cannot complete task: " + e.getMessage());
    System.err.println("Hint: " + e.recoveryHint());
} catch (YDataValidationException e) {
    System.err.println("Data validation failed: " + e.getMessage());
    System.err.println("Check your output data against schema");
}
```

### Pattern 4: Event Monitoring

```java
// Listen for case events
YStatelessEngine engine = new YStatelessEngine();

engine.addCaseEventListener(event -> {
    System.out.println("Case event: " + event.getCaseID() + " -> " + event.getEventType());
});

engine.addWorkItemEventListener(event -> {
    System.out.println("Work item event: " + event.getWorkItem().getLabel()
        + " (" + event.getEventType() + ")");
});

engine.launchCase(specID, caseID, data);
// Events will be printed as they occur
```

---

## 🔧 Configuration

### Stateless with Idle Timeout

```java
// Detect cases that have been idle for 30 minutes
long idleTimeoutMs = 30 * 60 * 1000;
YStatelessEngine engine = new YStatelessEngine(idleTimeoutMs);

engine.addCaseEventListener(event -> {
    if (event.getEventType().equals("CASE_SUSPENDED")) {
        System.out.println("Case idle: " + event.getCaseID());
        // Archive case, send reminder, etc.
    }
});
```

### Stateless with Custom Listeners

```java
YStatelessEngine engine = new YStatelessEngine();

// Custom case listener
engine.addCaseEventListener(event -> {
    switch (event.getEventType()) {
        case "CASE_CREATED" -> handleCaseCreated(event);
        case "CASE_COMPLETED" -> handleCaseCompleted(event);
        case "CASE_SUSPENDED" -> handleCaseSuspended(event);
        case "CASE_TERMINATED" -> handleCaseTerminated(event);
        default -> { }
    }
});

// Custom work item listener
engine.addWorkItemEventListener(event -> {
    switch (event.getEventType()) {
        case "WORK_ITEM_ENABLED" -> assignToQueue(event.getWorkItem());
        case "WORK_ITEM_STARTED" -> logTimeTracking(event.getWorkItem());
        case "WORK_ITEM_COMPLETED" -> handleCompletion(event);
        default -> { }
    }
});
```

---

## 🌐 REST API Integration

If using REST API instead of Java:

```bash
# 1. Upload specification
curl -X POST http://localhost:8080/api/spec \
  -H "Authorization: Bearer $API_KEY" \
  -d @workflow.xml

# 2. Launch case
curl -X POST http://localhost:8080/api/case \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "spec_id": "HelloWorld",
    "case_id": "case-001",
    "initial_data": "<data/>"
  }'

# 3. Get enabled work items
curl http://localhost:8080/api/case/case-001/workitems \
  -H "Authorization: Bearer $SESSION_TOKEN"

# 4. Complete work item
curl -X PUT http://localhost:8080/api/workitem/{workItemId}/complete \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"output_data": "<data/>"}'
```

---

## 📊 Stateful vs Stateless Comparison

| Aspect | Stateful (`YEngine`) | Stateless (`YStatelessEngine`) |
|--------|---|---|
| **Best for** | Traditional deployments | Cloud-native, FaaS |
| **State management** | In-memory + database | Explicit import/export |
| **Persistence** | Automatic (via YPersistenceManager) | Manual (via unloadCase/restoreCase) |
| **Scaling** | Single instance | Horizontal (no shared state) |
| **Use idle timeout** | Via YTimer background thread | Via constructor parameter |
| **Example** | Enterprise application | Microservice, Lambda function |

---

## 🆘 Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| `YEngineStateException`: Engine not initialized | Engine not started | Call `YEngine.getInstance()` first |
| `YStateException`: Cannot complete work item | Work item not in enabled state | Check `getEnabledWorkItems()` before completing |
| `YDataValidationException`: Data doesn't match schema | Output data invalid | Validate against workflow spec schema |
| Case has no enabled work items but not completed | Waiting on OR-join | Check OR-join conditions and branch status |
| No events fired | Listeners not registered | Call `addCaseEventListener()` before launching |

---

## 📚 Next Steps

1. **Load real workflow**: Replace hardcoded XML with `Files.readString(Path.of("workflow.xml"))`
2. **Add logging**: Use SLF4J + Logback for production debugging
3. **Handle async work**: Use virtual threads for non-blocking wait
4. **Implement retry logic**: Catch exceptions and retry with backoff
5. **Monitor performance**: Add timing logs to identify bottlenecks

---

## 🔗 Related Documentation

- **Full API Reference**: See `yawl/engine/YStatelessEngine.java`
- **Event Types**: See `yawl/engine/event/` directory
- **Exception Reference**: See `yawl/exceptions/` directory
- **REST API**: See `/docs/API.md`

---

**Document Version**: 1.0
**Compatible with**: YAWL v6.0.0+
**Last Updated**: 2026-03-06
