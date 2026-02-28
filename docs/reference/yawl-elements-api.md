# YAWL Elements API Reference

Complete API documentation for the YAWL elements domain model.

## Core Classes

### YSpecification

Top-level container for workflow definitions.

**Constructor & Lifecycle:**
```java
YSpecification spec = new YSpecification("MyWorkflow");
spec.setVersion("1.0");
spec.setDocumentation("Description of workflow");
spec.setMetaData("author", "Jane Doe");

// Add nets and decompositions
spec.addNet(net);
spec.addDecomposition(decomposition);

// Serialize
String xml = spec.toXML();

// Validate
boolean valid = spec.isValid();
if (!valid) {
    List<String> errors = spec.getValidationErrors();
}
```

**Key Methods:**
| Method | Returns | Description |
|--------|---------|-------------|
| `addNet(YNet net)` | void | Register workflow net |
| `getNets()` | Collection<YNet> | Get all nets |
| `getNet(String id)` | YNet | Get specific net |
| `addDecomposition(YDecomposition decomp)` | void | Register decomposition |
| `getDecompositions()` | Collection<YDecomposition> | Get all decompositions |
| `isValid()` | boolean | Check specification is sound |
| `toXML()` | String | Serialize to YAWL XML |

### YNet

Workflow graph (Petri net).

**Creation:**
```java
YNet net = new YNet("ProcessName");
net.setIsRootNet(true);  // Set as main net
```

**Adding Elements:**
```java
YInputCondition input = new YInputCondition("Input");
YOutputCondition output = new YOutputCondition("Output");
YTask task = new YTask("TaskID", YTask._AND, YTask._AND);
YCondition condition = new YCondition("Condition1");

net.addNetElement(input);
net.addNetElement(task);
net.addNetElement(condition);
net.addNetElement(output);
```

**Control Flow:**
```java
// Add flows (directed edges)
net.addFlow(new YFlow(input, task));
net.addFlow(new YFlow(task, condition));
net.addFlow(new YFlow(condition, output));

// Query flows
Set<YFlow> incoming = task.getPresetFlows();
Set<YFlow> outgoing = task.getPostsetFlows();
```

**Key Methods:**
| Method | Returns | Description |
|--------|---------|-------------|
| `addNetElement(YNetElement elem)` | void | Add task/condition |
| `addFlow(YFlow flow)` | void | Add control flow edge |
| `getNetElements()` | Collection<YNetElement> | All nodes |
| `getFlows()` | Collection<YFlow> | All edges |
| `getInputCondition()` | YInputCondition | Entry point |
| `getOutputCondition()` | YOutputCondition | Exit point |
| `validate()` | boolean | Check net is sound |
| `getMarking()` | YMarking | Current Petri net state |

### YTask

Represents a unit of work (atomic or composite).

**Creation - Atomic Task:**
```java
YTask atomicTask = new YTask("TaskID", YTask._AND, YTask._AND);
atomicTask.setName("Human-readable name");
```

**Creation - Composite Task:**
```java
YCompositeTask compositeTask = new YCompositeTask("TaskID",
                                                   YTask._AND,
                                                   YTask._AND);
compositeTask.setDecompositionPrototype(decomposition);
```

**Join/Split Types:**
```java
YTask._AND      // AND join/split (synchronize all branches)
YTask._OR       // OR join/split (non-local synchronization)
YTask._XOR      // XOR join/split (exclusive choice)
```

**Data Flow:**
```java
Document inputSchema = ...;
Document outputSchema = ...;

task.setDataInput(inputSchema);
task.setDataOutput(outputSchema);
```

**Multi-Instance:**
```java
YMultiInstanceAttributes mi = new YMultiInstanceAttributes();
mi.setCreationMode(YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);
mi.setMinInstancesThreshold("1");
mi.setMaxInstancesThreshold("10");
mi.setCompletionMode(YMultiInstanceAttributes.COMPLETION_MODE_ALL);

task.setMultiInstanceAttributes(mi);
```

**Key Methods:**
| Method | Returns | Description |
|--------|---------|-------------|
| `setJoinType(int type)` | void | Set incoming synchronization |
| `setSplitType(int type)` | void | Set outgoing routing |
| `setDataInput(Document schema)` | void | Define input type |
| `setDataOutput(Document schema)` | void | Define output type |
| `setDecompositionPrototype(YDecomposition d)` | void | Link to subprocess |
| `setMultiInstanceAttributes(YMultiInstanceAttributes a)` | void | Enable MI mode |
| `getFlowsInto()` | Collection<YFlow> | Successor nodes |
| `getPresetElements()` | Collection<YNetElement> | Predecessor nodes |

### YCondition

Control node in Petri net (place).

**Input Condition:**
```java
YInputCondition input = new YInputCondition("InputConditionID");
```

**Output Condition:**
```java
YOutputCondition output = new YOutputCondition("OutputConditionID");
```

**Internal Condition:**
```java
YCondition internal = new YCondition("ConditionID");
```

**Key Methods:**
| Method | Returns | Description |
|--------|---------|-------------|
| `getPresetElements()` | Collection<YNetElement> | Sources |
| `getPostsetElements()` | Collection<YNetElement> | Targets |
| `getTokens()` | Set<YToken> | Current tokens |
| `addToken()` | void | Place token |
| `removeToken()` | void | Remove token |

### YFlow

Directed edge in Petri net.

**Creation:**
```java
YFlow flow = new YFlow(sourceElement, targetElement);
flow.setIsDefaultFlow(true);  // Optional: default branch
flow.setEvaluation("condition expression");  // Optional: routing condition

net.addFlow(flow);
```

**Properties:**
| Property | Type | Description |
|----------|------|-------------|
| `NextElement` | YNetElement | Target node |
| `PreviousElement` | YNetElement | Source node |
| `IsDefaultFlow` | boolean | Route when no guard matches |
| `Evaluation` | String | Guard condition (XPath) |

### YDecomposition

Links task to sub-workflow.

**Creation:**
```java
YDecomposition decomp = new YDecomposition("DecompID");
decomp.setNet(subNet);  // Link to subnet

task.setDecompositionPrototype(decomp);
```

**Types:**
| Type | Usage |
|------|-------|
| `YNet` | Subprocess net |
| `YWebService` | External service call |
| `YEbService` | Event-based service |
| `YCustomForm` | External form/UI |

## Element Hierarchy

```
YNetElement (abstract)
├── YTask
│   ├── YAtomicTask
│   └── YCompositeTask
├── YCondition
│   ├── YInputCondition
│   └── YOutputCondition
└── YDecomposition
```

## Key Enum/Constants

**Task Type:**
```java
YTask._AND      // Synchronization type
YTask._OR       // Non-local sync
YTask._XOR      // Exclusive choice
```

**Multi-Instance Modes:**
```java
YMultiInstanceAttributes.CREATION_MODE_STATIC    // Fixed count
YMultiInstanceAttributes.CREATION_MODE_DYNAMIC   // Variable count
YMultiInstanceAttributes.COMPLETION_MODE_ALL     // All instances complete
YMultiInstanceAttributes.COMPLETION_MODE_ANY     // Any instance complete
YMultiInstanceAttributes.COMPLETION_MODE_THRESHOLD  // N instances
```

**Work Item Status:**
```java
YWorkItemStatus.statusEnabled      // Ready
YWorkItemStatus.statusExecuting    // In progress
YWorkItemStatus.statusCompleted    // Done
YWorkItemStatus.statusFailed       // Error
```

## Validation Rules

**Specification must have:**
- At least one net marked `isRootNet=true`
- All nets must be sound (reachable output)
- No duplicate element IDs

**Net must have:**
- Exactly one input condition
- Exactly one output condition
- At least one task
- All tasks must have incoming and outgoing flows
- No direct flow from condition to condition
- No direct flow from task to task

**Task rules:**
- Cannot have same ID as another element in same net
- Composite tasks must reference valid decomposition
- Multi-instance tasks must have completion condition

## Common Patterns

### Sequential Workflow
```java
net.addFlow(input, task1);
net.addFlow(task1, task2);
net.addFlow(task2, output);
```

### Parallel AND-Split
```java
YTask split = new YTask("Split", YTask._AND, YTask._AND);
net.addFlow(split, taskA);
net.addFlow(split, taskB);

YTask join = new YTask("Join", YTask._AND, YTask._AND);
net.addFlow(taskA, join);
net.addFlow(taskB, join);
net.addFlow(join, output);
```

### Conditional XOR-Split
```java
YTask decision = new YTask("Decision", YTask._AND, YTask._XOR);
YCondition condA = new YCondition("TypeA");
YCondition condB = new YCondition("TypeB");

YFlow flowA = new YFlow(decision, condA);
flowA.setEvaluation("[/type='A']");
flowA.setIsDefaultFlow(false);

YFlow flowB = new YFlow(decision, condB);
flowB.setEvaluation("[/type='B']");

net.addFlow(flowA);
net.addFlow(flowB);
```

## Performance Characteristics

| Operation | Time |
|-----------|------|
| Create net | < 1ms |
| Add element | < 1ms |
| Add flow | < 1ms |
| Validate | < 100ms for simple specs, up to 5s for complex |
| Serialize to XML | < 50ms |

## Dependencies

**Required:**
- `yawl-utilities` — Exception types, schema handling

**Optional:**
- `jdom2` — XML parsing
- `commons-lang3` — String utilities

---

See also:
- [Elements Getting Started](../tutorials/yawl-elements-getting-started.md)
- [Schema Design How-To](../how-to/yawl-elements-schema-design.md)
- [Workflow Patterns](./workflow-patterns.md)
