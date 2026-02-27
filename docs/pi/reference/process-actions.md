# Process Actions Reference

## ProcessAction (sealed interface)

`org.yawlfoundation.yawl.pi.prescriptive.ProcessAction`

Sealed interface representing a recommended intervention on a workflow case.
Permits exactly four implementations: `RerouteAction`, `EscalateAction`,
`ReallocateResourceAction`, `NoOpAction`.

The sealed hierarchy enables exhaustive pattern matching — the compiler
guarantees no case is missed.

### Common interface

```java
public sealed interface ProcessAction
    permits RerouteAction, EscalateAction, ReallocateResourceAction, NoOpAction {

    String caseId();                    // case this action applies to
    String rationale();                 // human-readable explanation
    double expectedImprovementScore();  // [0.0, 1.0] confidence of improvement
}
```

### Pattern matching

```java
List<ProcessAction> actions = facade.recommendActions(caseId, prediction);
ProcessAction best = actions.get(0);   // highest-scoring action

String description = switch (best) {
    case RerouteAction r ->
        "Reroute from " + r.fromTaskName() + " to " + r.toTaskName();
    case EscalateAction e ->
        "Escalate to " + e.escalationTarget();
    case ReallocateResourceAction rr ->
        "Move work item " + rr.workItemId() + " from " + rr.fromResourceId()
        + " to " + rr.toResourceId();
    case NoOpAction n ->
        "No intervention needed";
};
```

---

## RerouteAction

`org.yawlfoundation.yawl.pi.prescriptive.RerouteAction`

Routes a case from one task to an alternative task, bypassing the current bottleneck
or failed path.

```java
public record RerouteAction(
    String caseId,
    String fromTaskName,              // current task to skip
    String toTaskName,                // alternative task to route to
    String rationale,
    double expectedImprovementScore   // [0.0, 1.0]
) implements ProcessAction
```

### When it is generated

`PrescriptiveEngine` generates `RerouteAction` using `WorkflowDNAOracle.getAlternativePaths()`
when the oracle identifies an alternative task with lower observed failure rate.

### Constraint filtering

`ProcessConstraintModel` checks that `toTaskName` is reachable from the current
workflow position. Actions that would violate task ordering are removed from the
recommendation list.

---

## EscalateAction

`org.yawlfoundation.yawl.pi.prescriptive.EscalateAction`

Escalates the case to a human or a supervisory process for manual intervention.

```java
public record EscalateAction(
    String caseId,
    String escalationTarget,          // human role or queue identifier
    String rationale,
    double expectedImprovementScore
) implements ProcessAction
```

### When it is generated

Generated when `prediction.riskScore() > 0.7`. The escalation target defaults to
`"supervisor"` unless the `ProcessConstraintModel` defines a role override for
the current task.

---

## ReallocateResourceAction

`org.yawlfoundation.yawl.pi.prescriptive.ReallocateResourceAction`

Moves a work item from one resource (staff member, system) to another.

```java
public record ReallocateResourceAction(
    String caseId,
    String workItemId,                // the work item to reassign
    String fromResourceId,            // current assignee
    String toResourceId,              // proposed new assignee
    String rationale,
    double expectedImprovementScore
) implements ProcessAction
```

### When it is generated

Generated when `prediction.riskScore() >= 0.5`. The `ResourceOptimizer` (Hungarian
algorithm) is consulted to identify the lowest-cost alternative resource.

---

## NoOpAction

`org.yawlfoundation.yawl.pi.prescriptive.NoOpAction`

Baseline action representing "no intervention needed". Always present in the
recommendation list.

```java
public record NoOpAction(
    String caseId,
    String rationale,
    double expectedImprovementScore   // typically 0.0
) implements ProcessAction
```

### When it is the top action

When `prediction.riskScore() < 0.5`, `NoOpAction` will be the highest-ranked
recommendation. Callers can check this as a quick guard:

```java
List<ProcessAction> actions = facade.recommendActions(caseId, prediction);
if (actions.get(0) instanceof NoOpAction) {
    return; // case is healthy
}
// handle intervention
```

---

## ActionRecommender scoring

`org.yawlfoundation.yawl.pi.prescriptive.ActionRecommender`

Sorts candidates by score in descending order. The score formula:

```
score = baselineRisk * expectedImprovementScore
```

Where `baselineRisk = prediction.riskScore()`.

A `RerouteAction` with `expectedImprovementScore = 0.8` on a case with
`riskScore = 0.7` scores `0.7 × 0.8 = 0.56`.

---

## ProcessConstraintModel

`org.yawlfoundation.yawl.pi.prescriptive.ProcessConstraintModel`

Validates actions against workflow ordering constraints using Apache Jena RDF.

### What it checks

- `RerouteAction`: `toTaskName` must be reachable from the current state
  (RDF triple: `currentTask yawl:canSucceedBy toTask`)
- `EscalateAction`: escalation is always permitted (no constraint check)
- `ReallocateResourceAction`: `toResourceId` must be available for the work item
  (RDF triple: `workItem yawl:assignableTo resource`)
- `NoOpAction`: always permitted

### Filtering in practice

```java
List<ProcessAction> candidates = buildCandidates(caseId, prediction);
List<ProcessAction> valid = candidates.stream()
    .filter(constraintModel::isValid)
    .sorted(recommender.scoreOrder(prediction.riskScore()))
    .toList();
// valid is never empty — NoOpAction always passes
```
