# Getting Started with YAWL Engine

Learn the fundamentals of the YAWL workflow engine by building and running your first workflow case from scratch.

## What You'll Learn

By the end of this tutorial, you'll understand:
- Core YAWL engine architecture and lifecycle
- How to create workflow specifications with Petri nets
- How to instantiate and execute a case
- How to interact with work items through the work item life cycle
- The role of `YEngine`, `YNetRunner`, and `YWorkItem` in workflow execution

## Prerequisites

- Java 25 or higher
- Maven 3.9 or later
- Basic familiarity with YAWL specifications (XML format)
- 10-15 minutes

## Step 1: Understanding the Engine Architecture

The YAWL Engine is built around three core concepts:

### YEngine
The orchestrator that manages the entire workflow system:
- Case creation and lifecycle management
- Work item routing and state transitions
- Interface B/E/X protocol handlers
- Event logging and persistence

### YNetRunner
The Petri net execution engine that handles:
- Token propagation through the workflow graph
- Firing rules and transition conditions
- AND-splits, OR-joins, and other control flow patterns
- Multi-instance task expansion

### YWorkItem
Represents a unit of work in the system:
- Maintains its own state machine (enabled → executing → completed)
- Carries input/output data
- Tracks execution resources and deadlines

```java
// Simplified architecture:
YEngine engine = new YEngine();           // Singleton orchestrator
YNetRunner runner = engine.createRunner(netSpec); // Per-specification executor
YWorkItem item = runner.enableTask(...);  // Per-task unit of work
```

## Step 2: Create a Simple Workflow Specification

Create a minimal YAWL specification with a single task:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<yawl version="4.0">
  <specification uri="HelloWorld" name="Hello World Workflow">
    <net id="HelloNet" isRootNet="true">
      <!-- Input condition: entry point -->
      <inputCondition id="InputCondition"/>

      <!-- Single task -->
      <task id="SayHello">
        <name>Greeting Task</name>
        <flowsInto>
          <nextElementRef id="OutputCondition"/>
        </flowsInto>
      </task>

      <!-- Output condition: exit point -->
      <outputCondition id="OutputCondition"/>

      <!-- Define control flows -->
      <flow source="InputCondition" target="SayHello"/>
      <flow source="SayHello" target="OutputCondition"/>
    </net>
  </specification>
</yawl>
```

## Step 3: Build and Run Your First Case

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YSpecification;

public class HelloWorldWorkflow {
    public static void main(String[] args) throws Exception {
        // 1. Initialize the engine
        YEngine engine = new YEngine();
        engine.startup();

        // 2. Load the specification
        String specPath = "hello-world.yawl";
        YSpecification spec = engine.loadSpecification(specPath);

        // 3. Create a new case
        String caseId = engine.createCase(spec, null);
        System.out.println("Case created: " + caseId);

        // 4. Get enabled work items for the input condition
        Set<YWorkItem> items = engine.getEnabledWorkItems(caseId);

        // 5. Complete the SayHello task
        for (YWorkItem item : items) {
            if (item.getTaskName().equals("SayHello")) {
                System.out.println("Completing task: " + item.getTaskName());
                engine.completeWorkItem(item, null, null, true);
            }
        }

        // 6. Check case completion
        String caseStatus = engine.getCaseStatus(caseId);
        System.out.println("Case status: " + caseStatus);

        engine.shutdown();
    }
}
```

## Step 4: Understanding Work Item Lifecycle

Every work item progresses through states:

```
┌─────────────┐
│   Enabled   │  ← Created, ready for execution
└──────┬──────┘
       │ checkout()
       ↓
┌─────────────┐
│ Executing   │  ← Being worked on
└──────┬──────┘
       │ complete() or fail()
       ↓
┌─────────────┐
│ Completed   │  ← Done, result recorded
└─────────────┘
```

Complete a work item and see how it triggers downstream activities:

```java
// Checkout and execute
YWorkItem item = engine.checkoutWorkItem(caseId, taskId);
System.out.println("Checked out: " + item.getStatus());

// Simulate work (in real apps, user would do this)
Thread.sleep(1000);

// Complete the work
engine.completeWorkItem(item, outputData, null, true);
System.out.println("Completed: " + item.getTaskName());
```

## Step 5: Handling Parallel Execution

YAWL supports AND-splits and OR-joins for complex control flows:

```xml
<task id="ParallelTask">
  <join code="and"/>  <!-- Wait for all branches -->
  <split code="and"/>  <!-- Create parallel branches -->
  <!-- ... -->
</task>
```

The engine automatically:
- Expands parallel branches into multiple work items
- Synchronizes join points
- Manages token flow through the Petri net

## Key Takeaways

1. **YEngine** orchestrates the workflow lifecycle from case creation to completion
2. **Specifications** are XML documents describing your workflow structure (Petri nets)
3. **Cases** are runtime instances of specifications
4. **Work items** represent individual tasks to be performed
5. **YNetRunner** handles the formal Petri net execution semantics

## Next Steps

- Read the [YAWL Engine Reference](../reference/yawl-engine-api.md) for detailed API documentation
- Explore [Workflow Patterns](../reference/workflow-patterns.md) for complex control flows
- Learn how to [Write a Custom Work Item Handler](./06-write-a-custom-work-item-handler.md)
- Set up [Event Logging and Monitoring](../how-to/subscribe-workflow-events.md)

## Common Patterns

### Check Multiple Conditions
```java
// Get all enabled work items (may span multiple tasks)
Set<YWorkItem> items = engine.getEnabledWorkItems(caseId);
for (YWorkItem item : items) {
    if (item.getTaskName().equals("Review")) {
        // Handle review task
    }
}
```

### Handle Data Flow
```java
// Pass input data when creating a case
Document data = buildCaseData();
String caseId = engine.createCase(spec, data);

// Retrieve output data when completing
Document outputData = buildWorkItemOutput();
engine.completeWorkItem(item, outputData, null, true);
```

## Troubleshooting

**Case creation fails:**
- Verify specification is valid XML and valid YAWL schema
- Check that `isRootNet="true"` is set on the root net
- Ensure input and output conditions exist

**Work items not appearing:**
- Check task flow definitions (does task have a target?)
- Verify task is not in a disabled net
- Check for join condition failures (AND-joins waiting for multiple paths)

**Case gets stuck:**
- Look for deadlock in control flow (e.g., missing flow arcs)
- Check OR-join logic (may need explicit enabling)
- Review work item states with `engine.getLoggedWorkItems()`

---

**Ready to dive deeper?** Continue with [Engine API Reference](../reference/yawl-engine-api.md) or explore [Workflow Patterns](../reference/workflow-patterns.md).
