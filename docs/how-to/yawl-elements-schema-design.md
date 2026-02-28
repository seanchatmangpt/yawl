# How-To: Design Workflow Schemas with YAWL Elements

Learn to build complex workflow specifications using the YAWL elements API.

## Design a Multi-Net Workflow Specification

**Goal**: Create a specification with hierarchical nets and decompositions.

### Steps

1. **Create the specification container**
```java
YSpecification spec = new YSpecification("EmployeeOnboarding");
spec.setVersion("1.0");
spec.setDocumentation("Complete employee onboarding process");
```

2. **Create main workflow net**
```java
YNet mainNet = new YNet("OnboardingProcess");
mainNet.setIsRootNet(true);
spec.addNet(mainNet);

// Add network elements
YInputCondition input = new YInputCondition("InputCondition");
YOutputCondition output = new YOutputCondition("OutputCondition");
mainNet.addNetElement(input);
mainNet.addNetElement(output);
```

3. **Create task with decomposition**
```java
// Create the decomposed task
YCompositeTask backgroundCheckTask = new YCompositeTask(
    "BackgroundCheck",
    YTask._AND,
    YTask._AND
);
backgroundCheckTask.setName("Background Check Process");
mainNet.addNetElement(backgroundCheckTask);

// Create the sub-net
YNet bgCheckNet = new YNet("BackgroundCheckSubnet");
spec.addNet(bgCheckNet);

// Add subnet elements
YInputCondition bgInput = new YInputCondition("BGInput");
YOutputCondition bgOutput = new YOutputCondition("BGOutput");
bgCheckNet.addNetElement(bgInput);
bgCheckNet.addNetElement(bgOutput);

// Create decomposition linking
YDecomposition bgDecomp = new YDecomposition("BackgroundCheckDecomp");
bgDecomp.setNet(bgCheckNet);
spec.addDecomposition(bgDecomp);

backgroundCheckTask.setDecompositionPrototype(bgDecomp);
```

## Handle Multi-Instance Task Scenarios

**Goal**: Configure tasks that repeat for multiple items.

### Steps

1. **Enable multi-instance attributes**
```java
YTask processItemsTask = new YTask("ProcessItems", YTask._AND, YTask._AND);
processItemsTask.setName("Process Each Item");

YMultiInstanceAttributes miAttrs = new YMultiInstanceAttributes();

// Set creation mode: instances created dynamically
miAttrs.setCreationMode(YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);

// Set threshold: need at least 1 completed to proceed
miAttrs.setMinInstancesThreshold("1");

// Optional: set maximum instances
miAttrs.setMaxInstancesThreshold("10");

processItemsTask.setMultiInstanceAttributes(miAttrs);
mainNet.addNetElement(processItemsTask);
```

2. **Handle completion modes**
```java
// Option 1: All instances must complete
miAttrs.setCompletionMode(YMultiInstanceAttributes.COMPLETION_MODE_ALL);

// Option 2: Any instance completion triggers join
miAttrs.setCompletionMode(YMultiInstanceAttributes.COMPLETION_MODE_ANY);

// Option 3: Custom threshold met
miAttrs.setCompletionMode(YMultiInstanceAttributes.COMPLETION_MODE_THRESHOLD);
miAttrs.setCompletionThreshold("3");  // 3 completed = task complete
```

## Define Data Schemas and Flow

**Goal**: Attach data types and flow definitions to tasks.

### Steps

1. **Create data schema for task input**
```java
String dataInputXSD = """
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="applicant">
            <xs:complexType>
                <xs:sequence>
                    <xs:element name="firstName" type="xs:string"/>
                    <xs:element name="lastName" type="xs:string"/>
                    <xs:element name="email" type="xs:string"/>
                </xs:sequence>
            </xs:complexType>
        </xs:element>
    </xs:schema>
    """;

Document inputSchema = YXMLHelper.parseXML(dataInputXSD);
task.setDataInput(inputSchema);
```

2. **Define data output schema**
```java
String dataOutputXSD = """
    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <xs:element name="decision">
            <xs:complexType>
                <xs:sequence>
                    <xs:element name="approved" type="xs:boolean"/>
                    <xs:element name="notes" type="xs:string"/>
                </xs:sequence>
            </xs:complexType>
        </xs:element>
    </xs:schema>
    """;

Document outputSchema = YXMLHelper.parseXML(dataOutputXSD);
task.setDataOutput(outputSchema);
```

3. **Add input parameters mapping**
```java
// Link specification-level data to task input
String inputMapping = """
    <input>
        <applicant>
            <firstName>{/applicant/firstName}</firstName>
            <lastName>{/applicant/lastName}</lastName>
            <email>{/applicant/email}</email>
        </applicant>
    </input>
    """;
```

## Build Complex Control Flow Patterns

**Goal**: Implement advanced workflow patterns.

### Steps

1. **OR-Join with multiple incoming paths**
```java
// Create condition node for synchronization
YCondition decisionPoint = new YCondition("DecisionPoint");
mainNet.addNetElement(decisionPoint);

// Create tasks with OR-join
YTask mergeTask = new YTask("MergeResults", YTask._OR, YTask._AND);
mergeTask.setName("Consolidate Results");
mainNet.addNetElement(mergeTask);

// Multiple incoming flows (OR-join)
mainNet.addFlow(new YFlow(taskA, decisionPoint));
mainNet.addFlow(new YFlow(taskB, decisionPoint));
mainNet.addFlow(new YFlow(taskC, decisionPoint));

// Flow to merge task
mainNet.addFlow(new YFlow(decisionPoint, mergeTask));
```

2. **N-Out-Of-M pattern (sync N branches of M)**
```java
// Create intermediate conditions for each branch
List<YCondition> branchConditions = new ArrayList<>();
for (int i = 0; i < 5; i++) {
    YCondition branchCond = new YCondition("Branch" + i);
    mainNet.addNetElement(branchCond);
    branchConditions.add(branchCond);
}

// Create syncing task - will wait for N threads
YTask syncTask = new YTask("SyncN", YTask._AND, YTask._AND);
syncTask.setName("Synchronize N of 5");
mainNet.addNetElement(syncTask);

// Add flows from all branches
for (YCondition cond : branchConditions) {
    mainNet.addFlow(new YFlow(cond, syncTask));
}
```

3. **Cancel Region (deactivate sibling tasks)**
```java
// Task with cancel region
YTask cancellingTask = new YTask("CancelProcess", YTask._AND, YTask._AND);
cancellingTask.setName("Cancel All Other Tasks");

// Mark which tasks are cancelled when this completes
Set<YNetElement> cancelledElements = new HashSet<>();
cancelledElements.add(taskA);
cancelledElements.add(taskB);
cancelledElements.add(taskC);

// Associate cancelled set with task
// (implementation depends on YAWL version)
```

## Validate Your Schema Design

**Goal**: Ensure the specification is well-formed and sound.

### Steps

1. **Check basic validity**
```java
boolean isValid = spec.isValid();

if (!isValid) {
    List<String> errors = spec.getValidationErrors();
    for (String error : errors) {
        System.out.println("ERROR: " + error);
    }
}
```

2. **Verify individual net soundness**
```java
for (YNet net : spec.getNets()) {
    System.out.println("Net: " + net.getID());

    // Check for orphaned elements
    for (YNetElement elem : net.getNetElements()) {
        if (elem instanceof YTask) {
            YTask task = (YTask) elem;

            // Verify has incoming flow
            if (task.getPresetElements().isEmpty() &&
                !task.equals(net.getInputCondition())) {
                System.out.println("  WARNING: " + task.getName() +
                                 " has no incoming flow");
            }

            // Verify has outgoing flow
            if (task.getFlowsInto().isEmpty()) {
                System.out.println("  WARNING: " + task.getName() +
                                 " has no outgoing flow");
            }
        }
    }
}
```

3. **Serialize and validate against schema**
```java
String specXML = spec.toXML();

SchemaHandler schema = new SchemaHandler();
boolean schemaValid = schema.validate(
    new java.io.ByteArrayInputStream(specXML.getBytes())
);

if (schemaValid) {
    System.out.println("✓ Specification conforms to YAWL schema");
} else {
    System.out.println("✗ Schema validation failed:");
    for (String error : schema.getErrors()) {
        System.out.println("  " + error);
    }
}
```

## Common Design Patterns

### Sequential Approval Workflow
```java
YTask submitTask = new YTask("Submit", YTask._AND, YTask._AND);
YCondition approved = new YCondition("Approved");
YCondition rejected = new YCondition("Rejected");
YTask approveTask = new YTask("Approve", YTask._AND, YTask._OR);

mainNet.addNetElement(submitTask);
mainNet.addNetElement(approved);
mainNet.addNetElement(rejected);
mainNet.addNetElement(approveTask);

// Sequential flow: submit → decision → approval
mainNet.addFlow(new YFlow(inputCondition, submitTask));
mainNet.addFlow(new YFlow(submitTask, approveTask));

// Conditional routing based on decision
mainNet.addFlow(new YFlow(approveTask, approved));
mainNet.addFlow(new YFlow(approveTask, rejected));

mainNet.addFlow(new YFlow(approved, outputCondition));
mainNet.addFlow(new YFlow(rejected, outputCondition));
```

### Parallel Processing with Synchronization
```java
// AND-split
YTask parallelTask = new YTask("Split", YTask._AND, YTask._AND);
parallelTask.setSplitType(YTask._AND);

YTask taskA = new YTask("TaskA", YTask._AND, YTask._AND);
YTask taskB = new YTask("TaskB", YTask._AND, YTask._AND);
YTask taskC = new YTask("TaskC", YTask._AND, YTask._AND);

YTask joinTask = new YTask("Join", YTask._AND, YTask._AND);
joinTask.setJoinType(YTask._AND);

// Connect splits
mainNet.addFlow(new YFlow(parallelTask, taskA));
mainNet.addFlow(new YFlow(parallelTask, taskB));
mainNet.addFlow(new YFlow(parallelTask, taskC));

// Synchronize joins
mainNet.addFlow(new YFlow(taskA, joinTask));
mainNet.addFlow(new YFlow(taskB, joinTask));
mainNet.addFlow(new YFlow(taskC, joinTask));
```

---

For additional examples, see:
- [Workflow Patterns Reference](../reference/workflow-patterns.md)
- [Element API Documentation](../reference/yawl-elements-api.md)
- [Specification Validation](./validate-spec.md)
