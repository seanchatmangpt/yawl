# How-To: Execute Workflow Cases with YAWL Engine

Complete guide for creating, managing, and executing workflow cases in YAWL.

## Create a Workflow Case

**Goal**: Instantiate a workflow specification as an executable case.

### Prerequisites
- YAWL Engine running and initialized
- Valid workflow specification loaded

### Steps

1. **Obtain the engine instance**
```java
YEngine engine = YEngine.getInstance();
```

2. **Prepare input data (optional)**
```java
Document caseData = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .newDocument();

Element root = caseData.createElement("case");
caseData.appendChild(root);

Element requestElement = caseData.createElement("request");
requestElement.setTextContent("Approve new client account");
root.appendChild(requestElement);
```

3. **Create the case**
```java
String specID = "approval-workflow:4.0";
String caseID = engine.createCase(specID, caseData);

if (caseID != null) {
    System.out.println("Case created: " + caseID);
} else {
    System.out.println("Failed to create case");
}
```

### Error Handling
```java
try {
    String caseID = engine.createCase(specID, caseData);
} catch (YEngineException e) {
    if (e.getMessage().contains("not found")) {
        System.out.println("Specification not loaded");
    } else if (e.getMessage().contains("invalid")) {
        System.out.println("Input data schema mismatch");
    }
}
```

## Get Enabled Work Items for a Case

**Goal**: Retrieve all tasks ready for execution in a case.

### Steps

1. **Get all enabled items**
```java
Set<YWorkItem> enabledItems = engine.getEnabledWorkItems(caseID);

System.out.println("Available tasks:");
for (YWorkItem item : enabledItems) {
    System.out.println("  - " + item.getTaskName());
    System.out.println("    ID: " + item.getID());
    System.out.println("    Status: " + item.getStatus());
}
```

2. **Filter by task name**
```java
Set<YWorkItem> reviewItems = enabledItems.stream()
    .filter(item -> item.getTaskName().equals("ReviewRequest"))
    .collect(Collectors.toSet());
```

3. **Get specific work item**
```java
String workItemID = "unique-task-id";
YWorkItem item = engine.getWorkItem(workItemID);

if (item != null && item.getStatus() == YWorkItemStatus.statusEnabled) {
    // Ready for checkout
}
```

## Complete a Work Item

**Goal**: Execute a work item and advance the workflow.

### Steps

1. **Checkout the work item (lock it)**
```java
YWorkItem item = engine.checkoutWorkItem(caseID, taskID);

if (item != null) {
    System.out.println("Checked out: " + item.getTaskName());
    System.out.println("Status: " + item.getStatus());
} else {
    System.out.println("Cannot checkout - already locked");
}
```

2. **Prepare output data**
```java
Document outputData = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .newDocument();

Element root = outputData.createElement("result");
outputData.appendChild(root);

Element decision = outputData.createElement("decision");
decision.setTextContent("APPROVED");
root.appendChild(decision);

Element notes = outputData.createElement("notes");
notes.setTextContent("Meets all requirements");
root.appendChild(notes);
```

3. **Complete the work item**
```java
boolean success = engine.completeWorkItem(
    item,           // the work item
    outputData,     // result data
    null,           // logged-in user (can be null)
    true            // force complete if necessary
);

if (success) {
    System.out.println("Work item completed successfully");
} else {
    System.out.println("Failed to complete work item");
}
```

## Handle Parallel Work Items (AND-Split)

**Goal**: Execute multiple parallel tasks in a case.

### Steps

1. **Identify parallel tasks**
```java
Set<YWorkItem> enabledItems = engine.getEnabledWorkItems(caseID);

// After AND-split, multiple items will be enabled
System.out.println("Parallel tasks available: " + enabledItems.size());

Map<String, YWorkItem> tasksByName = enabledItems.stream()
    .collect(Collectors.toMap(
        YWorkItem::getTaskName,
        Function.identity()
    ));
```

2. **Execute in parallel (simulated)**
```java
ExecutorService executor = Executors.newFixedThreadPool(4);

List<Future<Boolean>> futures = enabledItems.stream()
    .map(item -> executor.submit(() -> {
        YWorkItem checked = engine.checkoutWorkItem(
            caseID,
            item.getID()
        );

        // Simulate work
        Thread.sleep(1000);

        return engine.completeWorkItem(
            checked,
            buildOutputForTask(item.getTaskName()),
            null,
            true
        );
    }))
    .collect(Collectors.toList());

// Wait for all to complete
for (Future<Boolean> future : futures) {
    if (!future.get()) {
        System.out.println("One or more tasks failed");
    }
}

executor.shutdown();
```

## Check Case Status and Progress

**Goal**: Monitor case execution progress.

### Steps

1. **Get case status**
```java
String status = engine.getCaseStatus(caseID);

switch(status) {
    case "Running":
        System.out.println("Case is executing");
        break;
    case "Suspended":
        System.out.println("Case is paused");
        break;
    case "Completed":
        System.out.println("Case finished successfully");
        break;
    case "Failed":
        System.out.println("Case execution failed");
        break;
}
```

2. **Get case data at any point**
```java
Document caseData = engine.getCaseData(caseID);

if (caseData != null) {
    String xml = YXMLHelper.formatXML(caseData);
    System.out.println("Current case data:");
    System.out.println(xml);
} else {
    System.out.println("No data available");
}
```

3. **Track execution history**
```java
List<YWorkItem> completedItems = engine.getCompletedWorkItems(caseID);

System.out.println("Completed tasks (" + completedItems.size() + "):");
for (YWorkItem item : completedItems) {
    System.out.println("  - " + item.getTaskName());
    System.out.println("    Completion time: " + item.getCompletionTime());
    System.out.println("    Duration: " + calculateDuration(item));
}
```

## Cancel or Abort a Case

**Goal**: Stop execution and cleanup a case.

### Steps

1. **Cancel gracefully**
```java
boolean cancelled = engine.cancelCase(caseID);

if (cancelled) {
    System.out.println("Case cancelled successfully");
} else {
    System.out.println("Cannot cancel case - may already be completing");
}
```

2. **Force abort if necessary**
```java
engine.removeCaseFromEngine(caseID);
System.out.println("Case forcefully removed from engine");
```

3. **Verify cancellation**
```java
String status = engine.getCaseStatus(caseID);
if (status == null || status.equals("Cancelled")) {
    System.out.println("Case is no longer executing");
}
```

## Handle Exceptions During Execution

**Goal**: Gracefully handle errors during case execution.

### Steps

1. **Catch engine exceptions**
```java
try {
    engine.completeWorkItem(item, outputData, null, true);
} catch (YEngineException e) {
    System.err.println("Engine error: " + e.getMessage());

    // Log error and potentially rollback
    logWorkItemFailure(item, e);

    // Optionally fail the entire case
    engine.removeCaseFromEngine(caseID);

} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
    e.printStackTrace();
}
```

2. **Validate input before submission**
```java
if (!validateCaseData(caseData)) {
    throw new YException("Case data validation failed");
}

if (!validateWorkItemOutput(item, outputData)) {
    throw new YException("Output data does not match expected schema");
}

engine.completeWorkItem(item, outputData, null, true);
```

3. **Implement retry logic**
```java
int maxRetries = 3;
int retryCount = 0;

while (retryCount < maxRetries) {
    try {
        engine.completeWorkItem(item, outputData, null, true);
        System.out.println("Success on attempt " + (retryCount + 1));
        break;

    } catch (YEngineException e) {
        retryCount++;

        if (retryCount >= maxRetries) {
            System.err.println("Failed after " + maxRetries + " attempts");
            throw e;
        }

        // Exponential backoff
        Thread.sleep(1000 * (long) Math.pow(2, retryCount));
    }
}
```

## Performance Optimization Tips

### Batch Operations
```java
// Instead of creating cases one at a time
for (String userId : userList) {
    engine.createCase(specID, null);  // DON'T do this in a loop
}

// Batch them where possible
List<String> caseIDs = userList.stream()
    .map(user -> engine.createCase(specID, buildDataFor(user)))
    .collect(Collectors.toList());
```

### Connection Pooling
```java
// Ensure Hibernate is configured for connection pooling
// In hibernate.cfg.xml:
// <property name="hibernate.hikaricp.maximumPoolSize">20</property>
// <property name="hibernate.hikaricp.minimumIdle">5</property>
```

### Lazy Loading Work Items
```java
// Don't load all work items if you only need one type
Set<YWorkItem> allItems = engine.getEnabledWorkItems(caseID);  // Expensive
Set<YWorkItem> reviewItems = allItems.stream()
    .filter(item -> item.getTaskName().equals("ReviewRequest"))
    .collect(Collectors.toSet());  // Filter in memory

// Better: load only specific items if API supports it
```

## Common Patterns and Examples

### Complete Sequential Workflow
```java
public void executeSequentialWorkflow(String specID) throws Exception {
    // Create case
    String caseID = engine.createCase(specID, null);
    System.out.println("Case: " + caseID);

    // Execute sequentially
    while (!engine.getCaseStatus(caseID).equals("Completed")) {
        Set<YWorkItem> items = engine.getEnabledWorkItems(caseID);

        if (items.isEmpty()) {
            System.out.println("Waiting for work items...");
            Thread.sleep(1000);
            continue;
        }

        for (YWorkItem item : items) {
            YWorkItem checked = engine.checkoutWorkItem(caseID, item.getID());

            // Simulate work
            System.out.println("Executing: " + item.getTaskName());
            Thread.sleep(500);

            engine.completeWorkItem(checked, null, null, true);
        }
    }

    System.out.println("Case completed: " + caseID);
}
```

### Monitor Multiple Cases
```java
public void monitorMultipleCases(List<String> caseIDs) throws Exception {
    Map<String, String> lastStatus = new HashMap<>();

    while (true) {
        for (String caseID : caseIDs) {
            String status = engine.getCaseStatus(caseID);
            String previous = lastStatus.getOrDefault(caseID, "unknown");

            if (!status.equals(previous)) {
                System.out.println(caseID + ": " + previous + " → " + status);
                lastStatus.put(caseID, status);
            }
        }

        Thread.sleep(5000);  // Check every 5 seconds
    }
}
```

---

For more advanced topics, see:
- [Workflow Patterns Reference](../reference/workflow-patterns.md)
- [Event Logging and Monitoring](./subscribe-workflow-events.md)
- [Performance Optimization](../how-to/performance-testing.md)
