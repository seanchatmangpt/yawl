# YAWL Worklet Service

**Dynamic workflow selection via Ripple Down Rules (RDR)**

## Overview

The Worklet Service provides runtime workflow adaptation by substituting tasks with "worklets" -- small workflow fragments selected via a rule-based inference engine based on Ripple Down Rules (RDR). This enables context-sensitive workflow execution without modifying the original specification.

**Version**: 6.0.0-Beta
**Module**: `org.yawlfoundation.yawl.worklet`
**Source**: `src/org/yawlfoundation/yawl/worklet/`
**Tests**: `test/org/yawlfoundation/yawl/worklet/`

## Key Classes

### RdrNode

A single rule in the RDR decision tree.

```java
public class RdrNode {
    private final int id;
    private final RdrCondition condition;
    private final String conclusion;
    private RdrNode trueChild;
    private RdrNode falseChild;
}
```

- **id**: Unique identifier within the tree
- **condition**: Boolean expression evaluated against work item context
- **conclusion**: Worklet specification name to invoke when selected
- **trueChild**: Node to traverse when condition is true (exception case)
- **falseChild**: Node to traverse when condition is false (alternative)

### RdrTree

A binary decision tree of RDR nodes supporting rule selection via path traversal.

```java
public class RdrTree {
    private RdrNode root;

    public String evaluate(Map<String, Object> context) {
        // Traverse tree, return last satisfied conclusion
    }
}
```

### RdrSet

A collection of RDR trees keyed by task name, enabling per-task rule sets.

```java
public class RdrSet {
    private final Map<String, RdrTree> trees;

    public RdrTree getTreeForTask(String taskName) {
        return trees.get(taskName);
    }
}
```

### WorkletRecord

Represents a selected worklet with its specification ID and case binding information.

```java
public class WorkletRecord {
    private final String specId;
    private final String parentCaseId;
    private final String taskId;
    private Instant selectedAt;
}
```

## RDR Algorithm

The Ripple Down Rules algorithm provides incremental knowledge acquisition:

```
1. Start at the root node
2. Evaluate the condition using the current work item context
3. If true, traverse to the true-child (more specific rule)
4. If false, traverse to the false-child (alternative)
5. The last satisfied node provides the selected worklet conclusion
```

### Example

```
Root: orderAmount > 10000 -> "HighValueOrderWorklet"
  |
  +-- True Child: customerType == "VIP" -> "VIPHighValueWorklet"
  |
  +-- False Child: orderAmount > 5000 -> "MediumValueWorklet"
                    |
                    +-- True Child: requiresApproval -> "ApprovalWorkflow"
                    |
                    +-- False Child: -> "StandardOrderWorklet"
```

When evaluating with `{orderAmount: 7500, requiresApproval: true}`:
1. Root (7500 > 10000) = false -> traverse to false child
2. MediumValue (7500 > 5000) = true -> remember conclusion "MediumValueWorklet"
3. Approval (requiresApproval = true) = true -> final conclusion "ApprovalWorkflow"

## Build & Test

### Compile

```bash
# Using dx.sh (fastest)
bash scripts/dx.sh -pl yawl-worklet compile

# Using Maven directly
mvn -pl yawl-worklet compile
```

### Run Tests

```bash
# All worklet tests
bash scripts/dx.sh -pl yawl-worklet test

# Specific test class
mvn -pl yawl-worklet test -Dtest=TestRdrTree

# Individual tests
mvn -pl yawl-worklet test -Dtest=TestRdrNode#testConditionEvaluation
```

### Test Classes

| Test Class | Coverage |
|------------|----------|
| `TestRdrNode` | Node creation, condition evaluation, child linking |
| `TestRdrTree` | Tree traversal, conclusion selection, edge cases |
| `TestRdrSet` | Set management, task-to-tree mapping |
| `TestWorkletRecord` | Record creation, binding validation |

## Configuration

### Spring Boot

```yaml
yawl:
  worklet:
    enabled: true
    rdr-storage: database  # or 'file' or 'memory'
    cache-size: 100
    evaluation-timeout-ms: 5000
```

### Environment Variables

```bash
YAWL_WORKLET_ENABLED=true
YAWL_WORKLET_RDR_STORAGE=database
YAWL_WORKLET_CACHE_SIZE=100
```

## Integration with YAWL Engine

The Worklet Service integrates with YAWL through the worklet service WAR:

1. **Task Selection**: When a task is enabled, the engine consults the Worklet Service
2. **Rule Evaluation**: The service evaluates RDR rules against the work item context
3. **Worklet Launch**: Selected worklet specification is launched as a subcase
4. **Completion**: When the worklet completes, the original task is marked complete

### Worklet Service API

```java
// Check if task has worklet rules
boolean hasWorkletRules(String taskId);

// Select worklet for a work item
WorkletRecord selectWorklet(String taskId, YWorkItem workItem);

// Get running worklets for a case
List<WorkletRecord> getActiveWorklets(String caseId);
```

## Adding New Rules

Rules can be added at runtime without restarting the engine:

```java
RdrSet rdrSet = workletService.getRdrSet();

// Create new rule
RdrNode newRule = new RdrNode(
    42,
    "orderAmount > 10000 && customerType == 'VIP'",
    "VIPHighValueWorklet"
);

// Add to existing tree
RdrTree tree = rdrSet.getTreeForTask("ProcessOrder");
tree.addNode(newRule, parentNode, true);  // true-child of parent
```

## Exception Handling

Worklets also handle exception processing:

| Exception Type | Worklet Selection |
|----------------|-------------------|
| Timeout | Task-specific timeout worklet |
| Constraint Violation | Validation worklet |
| External Service Failure | Compensation worklet |
| Custom Exception | User-defined worklet |

## Performance Considerations

- **Rule Caching**: RDR trees are cached after first load
- **Context Size**: Keep evaluation context minimal for fast rule evaluation
- **Tree Depth**: Avoid deep trees (>10 levels) for performance
- **Concurrent Evaluation**: Thread-safe evaluation for parallel work items

## References

- [Ripple Down Rules Paper](https://www.cse.unsw.edu.au/~compling/rdr/)
- [YAWL Worklet Documentation](https://yawlfoundation.github.io/yawl/v6/worklets/)
- [YAWL Patterns](../docs/patterns/README.md)

## License

GNU Lesser General Public License v3.0
