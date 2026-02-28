# Getting Started with YAWL Elements

Master the core domain model of YAWL by building workflow specifications programmatically using the elements API.

## What You'll Learn

By the end of this tutorial, you'll understand:
- YAWL's Petri net-based domain model
- How `YSpecification`, `YNet`, `YTask`, and `YCondition` relate to each other
- How to construct specifications programmatically
- How flows and decompositions connect tasks
- How to validate and serialize specifications

## Prerequisites

- Java 25 or higher
- Maven 3.9 or later
- Basic understanding of Petri nets (not required but helpful)
- 15-20 minutes

## Step 1: Understanding YAWL's Domain Model

YAWL is built on formal Petri net theory. The element hierarchy represents:

```
YSpecification (top-level document)
├── YNet (workflow graph)
│   ├── YInputCondition (entry point)
│   ├── YTask (atomic or composite work)
│   ├── YCondition (control nodes)
│   └── YFlow (directed arcs)
└── YDecomposition (task subprocesses)
```

### Key Concepts

- **Specification**: Container for one or more nets
- **Net**: A workflow graph with tasks and conditions
- **Task**: Unit of work (atomic or decomposed into a subnet)
- **Condition**: Control node connecting tasks (represents Petri net place)
- **Flow**: Directed edge from task to condition or condition to task

## Step 2: Build Your First Specification Programmatically

Create a simple approval workflow:

```java
import org.yawlfoundation.yawl.elements.*;

public class ApprovalWorkflowBuilder {
    public static void main(String[] args) throws Exception {
        // Create specification
        YSpecification spec = new YSpecification("Approval");
        spec.setDocumentation("Simple approval workflow");
        spec.setVersion("0.1");

        // Create root net
        YNet net = new YNet("ApprovalNet");
        spec.addNet(net);
        net.setIsRootNet(true);

        // Add input condition (entry point)
        YInputCondition inputCondition = new YInputCondition("Input");
        net.addNetElement(inputCondition);

        // Create "Submit Request" task
        YTask submitTask = new YTask("SubmitRequest", YTask._AND, YTask._AND);
        submitTask.setName("Submit Request");
        net.addNetElement(submitTask);

        // Create intermediate condition
        YCondition condition1 = new YCondition("c1");
        net.addNetElement(condition1);

        // Create "Approve" task
        YTask approveTask = new YTask("Approve", YTask._AND, YTask._AND);
        approveTask.setName("Approve or Reject");
        net.addNetElement(approveTask);

        // Create output condition (exit point)
        YOutputCondition outputCondition = new YOutputCondition("Output");
        net.addNetElement(outputCondition);

        // Add flows
        net.addFlow(new YFlow(inputCondition, submitTask));
        net.addFlow(new YFlow(submitTask, condition1));
        net.addFlow(new YFlow(condition1, approveTask));
        net.addFlow(new YFlow(approveTask, outputCondition));

        // Serialize to XML
        String xml = spec.toXML();
        System.out.println(xml);

        // Validate the specification
        boolean valid = net.validate();
        System.out.println("Specification valid: " + valid);
    }
}
```

## Step 3: Add Data and Parameters

Enhance your workflow with data attributes:

```java
// Define task-level data attributes
YTask submitTask = new YTask("SubmitRequest", YTask._AND, YTask._AND);

// Add input parameters (what the task receives)
YMultiInstanceAttributes miAttrs = new YMultiInstanceAttributes();
miAttrs.setCreationMode(YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);
submitTask.setMultiInstanceAttributes(miAttrs);

// Define data type for the task
Document dataSchema = buildRequestDataSchema();
submitTask.setDataInput(dataSchema);

// Define what data flows out
submitTask.setDataOutput(buildApprovalDecisionSchema());
```

## Step 4: Create Decomposed Tasks

Tasks can be decomposed into subprocesses:

```java
// Create a sub-net for the approval process
YNet approvalSubnet = new YNet("ApprovalSubnet");
spec.addNet(approvalSubnet);

// Create decomposition that connects task to subnet
YCompositeTask approveTask = new YCompositeTask("ApprovalTask",
                                                 YTask._AND,
                                                 YTask._AND);
approveTask.setName("Multi-Step Approval");

// Link the task to the subnet
YDecomposition decomp = new YDecomposition("ApprovalDecomposition");
decomp.setNet(approvalSubnet);
spec.addDecomposition(decomp);

approveTask.setDecompositionPrototype(decomp);
mainNet.addNetElement(approveTask);
```

## Step 5: Handle Control Flow Patterns

YAWL supports rich control flow patterns:

### AND-Join / AND-Split
Wait for all incoming branches and create parallel branches:

```java
YTask parallelTask = new YTask("ParallelTask",
                               YTask._AND,  // AND-join
                               YTask._AND); // AND-split
parallelTask.setName("Parallel Processing");
```

### OR-Join / OR-Split
Merge some or all branches and route conditionally:

```java
YTask conditionalTask = new YTask("ConditionalTask",
                                  YTask._OR,   // OR-join (non-local sync)
                                  YTask._OR);  // OR-split
```

### Multi-Instance Tasks
Execute a task multiple times:

```java
YTask miTask = new YTask("ProcessItems", YTask._AND, YTask._AND);
YMultiInstanceAttributes mi = new YMultiInstanceAttributes();
mi.setCreationMode(YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);
mi.setMinInstancesThreshold("1");
miTask.setMultiInstanceAttributes(mi);
mainNet.addNetElement(miTask);
```

## Step 6: Validate and Serialize

Ensure your specification is well-formed:

```java
// Validate the entire specification
boolean isValid = spec.isValid();
if (!isValid) {
    // Get validation messages
    List<String> errors = spec.getValidationErrors();
    for (String error : errors) {
        System.out.println("ERROR: " + error);
    }
} else {
    // Serialize to XML
    String xml = spec.toXML();

    // Write to file
    Files.write(Paths.get("workflow.yawl"), xml.getBytes());
    System.out.println("Specification saved successfully");
}
```

## Key Takeaways

1. **YSpecification** is the root container for your workflow
2. **YNet** represents the workflow graph (Petri net)
3. **YTask** and **YCondition** are the nodes
4. **YFlow** defines the control flow edges
5. **YDecomposition** enables hierarchical task refinement
6. All elements are validated against the YAWL schema

## Element Relationships

```java
// Navigate the element hierarchy
YSpecification spec = ...;
for (YNet net : spec.getNets()) {
    System.out.println("Net: " + net.getID());

    for (YNetElement elem : net.getNetElements()) {
        if (elem instanceof YTask) {
            YTask task = (YTask) elem;
            System.out.println("  Task: " + task.getName());

            // Get successor elements
            for (YFlow flow : task.getFlowsInto()) {
                System.out.println("    → " + flow.getNextElement().getName());
            }
        }
    }
}
```

## Common Patterns

### Sequential Workflow
```java
net.addFlow(inputCondition, task1);
net.addFlow(task1, condition1);
net.addFlow(condition1, task2);
net.addFlow(task2, outputCondition);
```

### Parallel Branches
```java
YTask splitter = new YTask("Split", YTask._AND, YTask._AND);
splitter.setSplitType(YTask._AND);

// Create branches
net.addFlow(splitter, task1);
net.addFlow(splitter, task2);
net.addFlow(splitter, task3);

// Synchronize with a join task
YTask joiner = new YTask("Join", YTask._AND, YTask._AND);
joiner.setJoinType(YTask._AND);

net.addFlow(task1, joiner);
net.addFlow(task2, joiner);
net.addFlow(task3, joiner);
```

### Conditional Routing
```java
YTask decision = new YTask("Decision", YTask._AND, YTask._OR);
decision.setSplitType(YTask._OR);

// Create conditions for each path
YCondition yes = new YCondition("yes");
YCondition no = new YCondition("no");

net.addFlow(new YFlow(decision, yes));
net.addFlow(new YFlow(decision, no));

// Route based on conditions
net.addFlow(yes, approveTask);
net.addFlow(no, rejectTask);
```

## Troubleshooting

**Validation fails with "Nets must be sound":**
- Ensure every task has incoming and outgoing flows
- Check that all paths lead to the output condition
- Verify OR-joins have explicit enabling conditions

**Serialization fails:**
- All elements must have unique IDs within their scope
- Check for circular decomposition references
- Verify data type schemas are well-formed XML

**Flow validation fails:**
- Cannot add flow from condition to condition
- Cannot add flow from task to task (must go through condition)
- Source and target must be in the same net

## Next Steps

- Explore [Workflow Patterns](../reference/workflow-patterns.md) for advanced control flows
- Learn [YAWL Schema Validation](../reference/yawl-schema.md)
- Build [Custom Task Handlers](./06-write-a-custom-work-item-handler.md)
- Set up [Specification Versioning](../reference/api/migration-5x-to-6.md)

---

**Ready to run your workflow?** Continue with [YAWL Engine Getting Started](./yawl-engine-getting-started.md).
