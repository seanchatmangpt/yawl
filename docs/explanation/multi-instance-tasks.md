# Multi-Instance Tasks

> A multi-instance (MI) task creates N parallel copies of itself from a single token, runs them concurrently, and synchronizes their outputs back into a single result — the YAWL mechanism for structured parallel execution without explicit fork/join patterns.

## What a Multi-Instance Task Is

In a standard YAWL atomic task, one token in produces one work item, which when completed produces one token out. A multi-instance task generalises this: one token in produces N work items (instances), all running concurrently, and when enough instances complete (according to the completion condition) the task produces one token out.

The classic use case is processing a collection of items in parallel: sending a document to N reviewers simultaneously, running N diagnostic tests in parallel, or processing N order lines in a batch. Without MI tasks, a designer must choose between a loop (sequential, one at a time) or an explicit AND-split with a fixed number of parallel branches (inflexible, requires knowing N at design time).

## The Four Cardinality Parameters

MI task cardinality is configured through `YMultiInstanceAttributes` (`src/org/yawlfoundation/yawl/elements/YMultiInstanceAttributes.java`), held as `YTask._multiInstAttr`. The class defines four parameters:

**`minimum`** — The minimum number of instances that must be created when the task fires. The engine will not fire the task with fewer instances than this value. Must be >= 1.

**`maximum`** — The upper bound on instance count. Once `maximum` instances have been created, no more can be added even in dynamic mode. Defaults to `Integer.MAX_VALUE` (effectively unbounded). Must be >= minimum.

**`threshold`** — The number of instances that must complete before the task is considered done (the completion condition). When `threshold` instances complete, the task exits and remaining active instances are cancelled. Must be >= 1 and <= maximum.

**`creationMode`** — Either `"static"` (instances are all created at task fire time, count is fixed) or `"dynamic"` (additional instances may be created during execution up to `maximum`).

The XSD definition for these parameters (`MultipleInstanceExternalTaskFactsType` in `schema/YAWL_Schema4.0.xsd`) shows their structure:

```xml
<xs:element name="minimum" type="yawl:XQueryType"/>
<xs:element name="maximum" type="yawl:XQueryType"/>
<xs:element name="threshold" type="yawl:XQueryType"/>
<xs:element name="creationMode" type="yawl:CreationModeType"/>
```

The `XQueryType` restriction means each cardinality attribute can be either a literal integer string (e.g. `"3"`) or an XQuery expression evaluated against the net's data document at fire time. This is how dynamic cardinality works: `minimum`, `maximum`, and `threshold` can reference net variables whose values are not known until runtime.

In `YMultiInstanceAttributes`, the constructor tries to parse each query as a literal integer first:

```java
try {
    _minInstances = Integer.valueOf(minInstancesQuery);
} catch (NumberFormatException e) {
    _minInstancesQuery = minInstancesQuery;
    _minInstances = null;
}
```

If parsing fails, the value is stored as a query string, and `getMinInstances()` evaluates it against the net's data document (`_task._net.getInternalDataDocument()`) via Saxon XQuery at fire time.

## Static vs Dynamic Creation Mode

**Static mode** (`creationMode code="static"`): All instances are created when the task fires. The engine evaluates `minimum` and `maximum` at fire time and creates exactly `minimum` instances (or up to `maximum` if the input data dictates a specific count). After that, no new instances can be added. This is the default (the no-arg `YMultiInstanceAttributes` constructor uses `CREATION_MODE_STATIC`).

**Dynamic mode** (`creationMode code="dynamic"`): The initial batch of instances is created at fire time (`minimum` instances), but additional instances can be created while the task is executing. A user or service calls the engine to add an instance (up to `maximum`). Dynamic mode requires the task's `YWorkItem` to have `_allowsDynamicCreation = true`. In `YNetRunner.createEnabledWorkItem`:

```java
boolean allowDynamicCreation = atomicTask.getMultiInstanceAttributes() != null &&
        YMultiInstanceAttributes.CREATION_MODE_DYNAMIC.equals(
                atomicTask.getMultiInstanceAttributes().getCreationMode());

YWorkItem workItem = new YWorkItem(pmgr, ..., allowDynamicCreation, false);
```

## Token Flow: One In, N Out, Synchronize Back

The token model for MI tasks uses four internal conditions (`YInternalCondition` instances held in `YTask`):

- `_mi_active` — contains a token for each active instance
- `_mi_entered` — tracks instances that have entered the task
- `_mi_executing` — tracks instances currently executing (started by a service or human)
- `_mi_complete` — accumulates tokens as instances complete

This is YAWL's Petri net encoding of the "N parallel threads with synchronisation" pattern. When the task fires:
1. One token is consumed from the preset condition.
2. N tokens are placed in `_mi_active` (one per instance).
3. Work items are created for each instance.

As each instance completes, its token moves from `_mi_executing` to `_mi_complete`. When the count of tokens in `_mi_complete` reaches `threshold`, the task exits: all remaining tokens in `_mi_active` and `_mi_executing` are withdrawn (cancellation of in-progress instances), and one token is produced in the postset condition. From the perspective of downstream tasks, the MI task looks exactly like a single-instance task — one token arrives.

## Data Flow: Splitting Inputs and Joining Outputs

Parallel instances need individual chunks of data to work on, and their individual outputs must be merged back into a single result. The XSD `miDataInput` and `miDataOutput` blocks define these transforms.

**Input splitting** (`miDataInput` in XSD, fields in `YMultiInstanceAttributes`):

```xml
<miDataInput>
  <expression query="..."/>           <!-- pre-splitting: select from net data -->
  <splittingExpression query="..."/>  <!-- how to split the selected collection -->
  <formalInputParam>itemName</formalInputParam>
</miDataInput>
```

- `expression` (mapped to `YTask.getPreSplittingMIQuery()`): An XQuery that selects the collection from the net's data document to be distributed across instances.
- `splittingExpression` (mapped to `YMultiInstanceAttributes._inputSplittingQuery`, accessed via `getMISplittingQuery()`): An XQuery that, given the collection, extracts the slice for one instance.
- `formalInputParam` (mapped to `_inputVarName`, accessed via `getMIFormalInputParam()`): The name of the input parameter in the task's decomposition that receives each instance's data slice.

**Output joining** (`miDataOutput` in XSD):

```xml
<miDataOutput>
  <formalOutputExpression query="..."/>   <!-- what to extract from each instance's output -->
  <outputJoiningExpression query="..."/> <!-- how to aggregate across instances -->
  <resultAppliedToLocalVariable>netVar</resultAppliedToLocalVariable>
</miDataOutput>
```

- `formalOutputExpression` (mapped to `_remoteOutputQuery`, accessed via `getMIFormalOutputQuery()`): An XQuery applied to each completed instance's output to extract its contribution.
- `outputJoiningExpression` (mapped to `_outputProcessingQuery`, accessed via `getMIJoiningQuery()`): An XQuery that aggregates the per-instance contributions into a single result.
- `resultAppliedToLocalVariable`: The name of the net variable that receives the final aggregated result.

The method `getMIOutputAssignmentVar()` resolves which net variable the joined output targets by calling `YTask.getMIOutputAssignmentVar(_remoteOutputQuery)`.

## Completion Condition: When Enough Instances Have Finished

The `threshold` parameter defines when the task is done:

- **All instances must complete** (AND-style): Set `threshold` equal to `maximum` (or to the exact instance count for static mode). No instance is cancelled early.
- **Threshold subset must complete** (OR-style): Set `threshold` < `maximum`. The first `threshold` completions trigger task exit, cancelling all remaining active instances. This is the MI equivalent of an OR-join completion condition.
- **First completion triggers exit** (XOR-style): Set `threshold = 1`. The task exits as soon as any single instance completes.

This is expressed as pure numeric comparison in `YMultiInstanceAttributes.getThreshold()` at runtime. There is no separate enum for AND/OR/XOR in MI task completion — it is fully encoded by the numeric relationship between `threshold` and `maximum`.

## When to Use MI Tasks vs Multiple Parallel Branches

**Use MI tasks when:**
- The number of parallel instances depends on data (e.g., one instance per item in a list).
- The instances are logically identical — they execute the same decomposition with different input slices.
- You need structured aggregation of results back into a single net variable.
- The cardinality is not known at design time.

**Use explicit AND-split with multiple branches when:**
- The number of parallel activities is fixed and small (e.g., always exactly 3 approvers).
- The parallel branches execute different decompositions or have different routing logic.
- The branches are not data-symmetric: each branch receives different typed inputs rather than slices of the same collection.

**The key tradeoff:** MI tasks express "same task, N times, parallel" with data-driven N. Explicit AND-split branches express "N different things in parallel." MI tasks produce substantially simpler specifications when the parallel work is homogeneous and the count is variable.

## Class and Field Reference

| Concept | Java class/field | Path |
|---|---|---|
| MI attributes container | `YMultiInstanceAttributes` | `src/org/yawlfoundation/yawl/elements/YMultiInstanceAttributes.java` |
| Static creation mode constant | `CREATION_MODE_STATIC = "static"` | `YMultiInstanceAttributes` |
| Dynamic creation mode constant | `CREATION_MODE_DYNAMIC = "dynamic"` | `YMultiInstanceAttributes` |
| Minimum instances (literal or query) | `_minInstances` / `_minInstancesQuery` | `YMultiInstanceAttributes` |
| Maximum instances (literal or query) | `_maxInstances` / `_maxInstancesQuery` | `YMultiInstanceAttributes` |
| Threshold (literal or query) | `_threshold` / `_thresholdQuery` | `YMultiInstanceAttributes` |
| Creation mode | `_creationMode` | `YMultiInstanceAttributes` |
| Input formal param name | `_inputVarName` (via `getMIFormalInputParam()`) | `YMultiInstanceAttributes` |
| Input splitting query | `_inputSplittingQuery` (via `getMISplittingQuery()`) | `YMultiInstanceAttributes` |
| Output extraction query | `_remoteOutputQuery` (via `getMIFormalOutputQuery()`) | `YMultiInstanceAttributes` |
| Output joining query | `_outputProcessingQuery` (via `getMIJoiningQuery()`) | `YMultiInstanceAttributes` |
| Task's MI attribute holder | `_multiInstAttr` | `YTask` (`src/org/yawlfoundation/yawl/elements/YTask.java`) |
| Active instance tracking | `_mi_active`, `_mi_entered`, `_mi_executing`, `_mi_complete` | `YTask` |
| Dynamic creation flag | `_allowsDynamicCreation` | `YWorkItem` (`src/org/yawlfoundation/yawl/engine/YWorkItem.java`) |
| Dynamic creation check | `allowDynamicCreation` local var | `YNetRunner.createEnabledWorkItem` |

## XSD Element Summary

The `MultipleInstanceExternalTaskFactsType` in `schema/YAWL_Schema4.0.xsd` extends `ExternalTaskFactsType` with:

```
minimum        XQueryType   — minimum instance count (literal or XQuery)
maximum        XQueryType   — maximum instance count (literal or XQuery)
threshold      XQueryType   — completion threshold (literal or XQuery)
creationMode   code="static"|"dynamic"
miDataInput
  expression             — pre-splitting XQuery selecting collection from net
  splittingExpression    — per-instance slice extraction XQuery
  formalInputParam       — decomposition input parameter receiving each slice
miDataOutput (optional)
  formalOutputExpression     — per-instance output extraction XQuery
  outputJoiningExpression    — aggregation XQuery across all instances
  resultAppliedToLocalVariable — target net variable for aggregated result
```
