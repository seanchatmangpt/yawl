# Petri Net Foundations in YAWL

> YAWL models every workflow as a Petri net extended with advanced routing constructs; understanding places, transitions, tokens, and their Java representations is prerequisite to reasoning about anything that happens at runtime.

## Why Petri Nets

YAWL chose Petri nets because they carry formal mathematical semantics. A Petri net is completely defined by its reachable markings — every possible state the system can reach is computable, which means properties like soundness (does the process always complete?) and deadlock-freedom can be verified statically before any case is ever run. Workflow notations built on top of flowcharts lack this property; they can describe behaviour that is impossible to verify exhaustively.

The foundational paper that underpins YAWL ("Workflow Patterns") catalogued 43 control-flow patterns that practical workflows actually need. Plain Petri nets cannot express all 43 directly without encoding tricks that destroy readability. YAWL solves this by defining YAWLnets, an extended variant that adds OR-joins, multi-instance tasks, and cancellation regions as first-class constructs while preserving the formal verification basis.

## Core Petri Net Concepts

A Petri net has three primitive element kinds:

- **Places** — passive nodes that hold tokens. A place with a token in it means "some condition is currently true."
- **Transitions** — active nodes that fire. A transition fires by consuming tokens from its input places (preset) and producing tokens in its output places (postset).
- **Arcs** — directed edges connecting places to transitions and transitions to places. A place is never directly connected to another place; neither is a transition to another transition. The graph is always bipartite.
- **Tokens** — indistinguishable markers placed inside places. The collection of which places hold how many tokens is the current *marking* of the net.

A transition is *enabled* when every place in its preset contains at least one token. When an enabled transition fires, it removes one token from each preset place and adds one token to each postset place.

## YAWL's Mapping of Petri Net Concepts

### Places become YCondition

`org.yawlfoundation.yawl.elements.YCondition` is the Petri net place. It holds tokens using a `YIdentifierBag` — a multiset keyed by case identifier rather than simple integers, because YAWL supports concurrent cases running the same specification simultaneously.

```
YCondition._bag  — the token holder
YCondition.add(pmgr, identifier)    — place a token
YCondition.removeOne(pmgr)          — consume a token
YCondition.containsIdentifier()     — test if any tokens present
```

Each condition exists inside exactly one `YNet` instance. Two special condition subclasses mark entry and exit:

- `YInputCondition` — the single entry place of every net. `YNetRunner.prepare()` deposits the initial token here at case start.
- `YOutputCondition` — the single exit place. When a token reaches it, `YNetRunner.endOfNetReached()` returns true and the case completes.

The "implicit condition" concept arises when the designer draws a flow arc directly between two tasks with no named condition between them. YAWL materialises a hidden `YCondition` with `isImplicit() == true` for every such arc. Implicit conditions appear in the runtime marking but are omitted from the XML serialisation in `YNet.produceXMLStringForSet()`.

### Transitions become YTask

`org.yawlfoundation.yawl.elements.YTask` is the Petri net transition. The class is abstract; the concrete subclasses are:

- `YAtomicTask` — the transition that interacts with an external service. It fires, creates a work item, waits for a human or a service to complete it, then exits.
- `YCompositeTask` — the transition that decomposes into a sub-net. When it fires it instantiates a child `YNetRunner`; the transition exits only when the sub-net reaches its own output condition.

The key firing operation is `YTask.t_fire()`. It reads tokens from the preset conditions (consuming them according to join semantics), creates child `YIdentifier` objects, and places those children into the task's internal condition `_mi_active`. The task is then "busy" — `t_isBusy()` returns true, which means `_i != null`.

The key exit operation is `YTask.t_exit()`, called from inside `t_complete()` once all required instances have finished. It performs output data assignments, fires any cancellation sets, and places tokens into postset conditions according to split semantics.

### Tokens become YIdentifier

A token in YAWL is not anonymous. It is an `org.yawlfoundation.yawl.elements.state.YIdentifier`. This matters because multiple cases run concurrently, and the engine must distinguish a token belonging to case "1" from a token belonging to case "2" in the same condition.

A `YIdentifier` tracks its current *locations* — the set of `YNetElement` objects (conditions or tasks) where this case's token currently resides:

```java
// YIdentifier._locations — the set of net elements holding this token
identifier.addLocation(pmgr, task);     // token enters a task
identifier.removeLocation(pmgr, task);  // token leaves a task
```

For multi-instance tasks, a parent `YIdentifier` spawns child identifiers. The parent identifier remains in the task's `_mi_active` internal condition while child identifiers exist for individual instances in `_mi_executing`.

### The Current Marking

`org.yawlfoundation.yawl.elements.state.YMarking` (in `org.yawlfoundation.yawl.elements.state`) captures the current marking for a case by reading `identifier.getLocations()`. Every condition's `containsIdentifier()` call walks this same structure. The marking defines the exact Petri net state and is what the OR-join algorithm inspects.

## How Token Flow Drives Case Execution

The execution loop lives in `org.yawlfoundation.yawl.engine.YNetRunner`. The central method is `continueIfPossible()`.

```
YNetRunner.kick()
  -> continueIfPossible()
       -> for each task: check t_enabled()
       -> collect newly-enabled tasks into YEnabledTransitionSet
       -> fireTasks(enabledSet)
            -> for composite tasks:  fireCompositeTask()
            -> for empty/silent:     processEmptyTask()
            -> for atomic tasks:     fireAtomicTask() -- creates YWorkItem, announces to service
       -> return hasActiveTasks()
  -> if !hasActiveTasks() and root net: announce case completion
```

`t_enabled()` (in `YTask`) checks the join type:

- **AND-join**: every preset condition must `containsIdentifier()`.
- **OR-join**: delegates to `YNet.orJoinEnabled()`, which may invoke the E2WFOJ algorithm.
- **XOR-join**: at least one preset condition must `containsIdentifier()`.

`t_fire()` (in `YTask`) removes tokens from preset conditions according to the same join type, then creates child identifiers in `_mi_active`.

`t_exit()` (in `YTask`) places tokens into postset conditions according to split type:

- **AND-split** (`doAndSplit()`): copies the token into every postset condition.
- **OR-split** (`doOrSplit()`): evaluates XPath predicates on flow arcs; sends the token to every condition whose predicate evaluates to true.
- **XOR-split** (`doXORSplit()`): evaluates predicates in priority order; sends the token to the first condition whose predicate evaluates to true, or the default flow if none match.

After `t_exit()` places tokens, `continueIfPossible()` is called again. This recursion (implemented via `kick()`) runs until no tasks can fire, at which point the net is either complete (token in output condition) or deadlocked.

## What Makes YAWLnets Special

### OR-Joins

In a plain Petri net, a join fires when all incoming arcs have tokens. A choice-join that fires when *some* incoming arcs have tokens — and when it can be determined that no more tokens are coming — requires reasoning about the future reachability of the net. This is the OR-join.

YAWL implements this using the Extended Workflow Net OR-Join (E2WFOJ) algorithm from `org.yawlfoundation.yawl.elements.e2wfoj.E2WFOJNet`. The algorithm is invoked from `YNet.orJoinEnabled()`:

```java
// YNet.java
E2WFOJNet e2Net = new E2WFOJNet(this, orJoinTask);
e2Net.restrictNet(actualMarking);
e2Net.restrictNet(orJoinTask);
return e2Net.orJoinEnabled(actualMarking, orJoinTask);
```

The E2WFOJ algorithm restricts the full net to the part still reachable, then determines whether any token currently in the OR-join's preset can be the last token expected. This avoids both premature firing (joining before all tokens arrive) and livelock (waiting forever for tokens that will never arrive).

### Cancellation Regions

Each `YTask` carries a `_removeSet` — a set of `YExternalNetElement` objects. When that task exits (transitions fire its postset), everything in `_removeSet` is cancelled: tasks are cancelled, conditions have all their tokens removed.

```java
// YTask.t_exit()
for (YExternalNetElement netElement : _removeSet) {
    if (netElement instanceof YTask task) {
        task.cancel(pmgr);
    } else if (netElement instanceof YCondition cond) {
        cond.removeAll(pmgr);
    }
}
```

This implements the workflow "cancellation region" pattern: completing one activity forcibly terminates a set of concurrent activities. It is expressed in the XML specification as `<removesTokens id="..."/>` elements on the task.

### Multi-Instance Tasks

A single `YTask` can fire multiple child identifiers simultaneously. `YTask.determineHowManyInstancesToCreate()` evaluates XPath queries against the net's data document to determine the instance count. Each child identifier goes through `_mi_active`, `_mi_entered`, `_mi_executing`, and `_mi_complete` internal conditions independently. The task exits (calls `t_exit()`) only when the threshold number of instances has completed — controlled by `t_isExitEnabled()`:

```java
// YTask.t_isExitEnabled()
_mi_complete.getIdentifiers().size() >= _multiInstAttr.getThreshold()
```

This means the task behaves as a single Petri net transition from the outside but spawns and manages a family of internal identifiers.

### Soundness Verification

`YNet.verify()` enforces structural soundness conditions:

- Exactly one input condition, exactly one output condition.
- Every element is on a directed path from the input condition to the output condition (`verifyDirectedPath()`).
- Split types on OR/XOR tasks have exactly one default flow.

These checks catch specifications that could never complete or could deadlock by construction, before any case is launched.

## Key Package Locations

| Concept | Java Package | Key Classes |
|---------|-------------|-------------|
| Net structure | `org.yawlfoundation.yawl.elements` | `YNet`, `YTask`, `YCondition`, `YAtomicTask`, `YCompositeTask` |
| State/tokens | `org.yawlfoundation.yawl.elements.state` | `YIdentifier`, `YMarking`, `YInternalCondition` |
| Execution | `org.yawlfoundation.yawl.engine` | `YNetRunner`, `YEngine`, `YWorkItem` |
| OR-join algorithm | `org.yawlfoundation.yawl.elements.e2wfoj` | `E2WFOJNet` |
| Flow arcs | `org.yawlfoundation.yawl.elements` | `YFlow`, `YExternalNetElement` |
