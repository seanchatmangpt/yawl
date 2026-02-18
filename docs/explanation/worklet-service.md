# Worklet Service

> The Worklet Service is a runtime adapter that intercepts task firings and substitutes dynamically selected workflow sub-specifications (worklets) chosen by evaluating Ripple Down Rule (RDR) trees against live case data.

## What Problem Worklets Solve

Static YAWL specifications express the full control-flow structure of a business process at design time. That works well when the process is predictable. In practice, many processes have variation points: the exact procedure for handling a loan application depends on the applicant's credit tier, the regulatory jurisdiction, and the product type. Encoding every combination as branches inside a single net produces an unmanageable specification.

Worklets solve this by separating the main process structure from the variant sub-processes. The main specification contains placeholder tasks. At runtime, when a placeholder task fires, the Worklet Service selects the appropriate sub-specification (the worklet) by evaluating rules against live case data, launches it as a sub-case, waits for it to complete, and then lets the main case resume. The main specification never needs to change as business rules evolve — only the RDR trees change.

## Architecture: Where the Worklet Service Sits

The Worklet Service is a separate Maven module (`yawl-worklet`, defined in `/home/user/yawl/yawl-worklet/pom.xml`). It depends on `yawl-engine` and `yawl-resourcing`. It registers with the engine as a custom YAWL service. Its default registration entry in `/home/user/yawl/src/org/yawlfoundation/yawl/engine/defaultClients.properties` is:

```
service:workletService,yWorklet,the worklet service,http://localhost:8080/workletService/ib,true
```

The service ID `workletService` and endpoint URI (`/workletService/ib`) are how the engine routes work items to it. The `true` flag marks it as assignable to tasks. When a task's `YAWLServiceGateway` decomposition carries a `yawlService` reference pointing to the worklet service URI, every work item that fires for that task is delivered to the Worklet Service via Interface B.

The engine's `YAnnouncer` (`/home/user/yawl/src/org/yawlfoundation/yawl/engine/YAnnouncer.java`) handles the notification. When a work item transitions to enabled status, `announceWorkItemStatusChange` broadcasts the event to all registered services, including the Worklet Service. The work item carries its `YAWLServiceReference` (resolved from the task's `YAWLServiceGateway` decomposition), so the engine knows which service to route the item to.

## RDR Basis: What a Ripple Down Rule Tree Is

The Worklet Service uses Ripple Down Rules (RDR) as its rule engine. An RDR tree is a binary decision tree where:

- Each **node** contains a condition expressed as an XPath-like predicate evaluated against case data (net variables).
- A node that evaluates to `true` selects its **true child** (if present) or fires its **conclusion** — the worklet specification to launch.
- A node that evaluates to `false** selects its **false child** (if present) or propagates up to the parent's false branch.
- The root node's false branch holds the **default worklet** (the fallback when no condition matches).

The "ripple down" name comes from how the tree grows. When a domain expert wants to add a new rule, they attach it as a refinement on a leaf that previously gave the wrong answer. This means rules never need to be restructured — new exceptions ripple down from where the current logic failed. The structure guarantees that every existing correct case still follows the same path it always did.

Each task in a YAWL specification can have its own RDR tree. The tree for a task is stored as a **RuleSet** that the Worklet Service persists and loads from its database (Hibernate-backed).

## The Lifecycle: From Task Firing to Case Resumption

1. **Task fires in the parent case.** `YNetRunner.createEnabledWorkItem` creates a `YWorkItem` and the engine's announcer notifies all registered services. The Worklet Service receives this notification because the task's decomposition references its service URI.

2. **Worklet Service receives the work item.** The item includes the case data (the net variables of the parent case at the point the task became enabled). This is the data the RDR tree is evaluated against.

3. **RDR tree evaluation.** The service looks up the RuleSet for this task's specification URI and task ID. It walks the tree: the root node's condition is evaluated against the case data. Based on true/false, the traversal continues down the appropriate child branch until it reaches a leaf. The leaf's conclusion identifies a worklet specification (by URI/version).

4. **Worklet specification launched as a sub-case.** The Worklet Service calls back to the engine via Interface B to launch the selected worklet specification as an independent case. The engine assigns it a new `YIdentifier`. This sub-case executes independently, potentially involving human tasks via the Resource Service, automated service calls, or further composite sub-nets.

5. **Parent case suspends.** The parent work item remains in the `Executing` state in the engine's `YWorkItemRepository` while the worklet sub-case runs. The Worklet Service stores the mapping between the parent work item ID and the sub-case ID.

6. **Worklet sub-case completes.** The engine notifies the Worklet Service when the worklet case completes (via Interface B case-completion notification). The service retrieves the output data from the completed sub-case.

7. **Parent work item completed.** The Worklet Service calls Interface B to complete the suspended work item on behalf of the original task, supplying the worklet's output data as the completion data. The engine's `YNetRunner.completeTask` fires, propagates tokens through the parent net, and the case resumes.

## Why Use Worklets Instead of Static Branching

| Concern | Static branching | Worklets |
|---|---|---|
| Adding a new process variant | Modify and re-deploy the specification | Update the RDR tree; no spec change |
| Reusing a sub-process across multiple tasks | Duplicate sub-net or use composite task | One worklet spec referenced from many trees |
| Separating concerns | All variants in one spec | Each variant is its own manageable spec |
| Runtime adaptation | Not possible | Rule conclusions can change between case launches |
| Specification complexity | Grows with variant count | Main spec stays small |

## Key Classes and Paths

The worklet source files reside in `src/org/yawlfoundation/yawl/worklet/` and are compiled by the `yawl-worklet` Maven module (see `/home/user/yawl/yawl-worklet/pom.xml`, which sets `sourceDirectory` to `../src` and includes only `**/org/yawlfoundation/yawl/worklet/**`).

Classes relevant to understanding the architecture (located in the `worklet` package tree under `src/`):

- **WorkletService** — The main servlet and Interface B listener. Receives work item events from the engine, coordinates rule evaluation, and manages the parent-item-to-sub-case mapping. Registers itself with the engine at startup.
- **RdrSet** — Holds the full collection of RDR trees for one workflow specification. One `RdrSet` per loaded specification; keyed by task ID.
- **RdrNode** — A single node in an RDR tree. Contains the condition (as a string XPath/XQuery expression), a true-child reference, a false-child reference, and a conclusion (worklet spec URI).
- **WorkletSelector** — Evaluates an `RdrSet` against case data and returns the winning worklet specification ID.
- **HandlerRunner** — Manages the lifecycle of a single active worklet sub-case: stores the parent work item, the sub-case ID, and handles completion callbacks.

Engine-side supporting classes:

- `src/org/yawlfoundation/yawl/elements/YAWLServiceGateway.java` — The decomposition type that links a task to a named service. The `yawlService` child element in the XSD `WebServiceGatewayFactsType` carries the worklet service URI.
- `src/org/yawlfoundation/yawl/elements/YAWLServiceReference.java` — The runtime object wrapping the service URI. Its `getURI()` is compared against registered services in `YEngine`.
- `src/org/yawlfoundation/yawl/engine/YAnnouncer.java` — Routes `ITEM_ADD` events to registered services when work items become enabled.
- `src/org/yawlfoundation/yawl/engine/defaultClients.properties` — Registers the worklet service endpoint at `http://localhost:8080/workletService/ib`.

## Constraints a Coding Agent Must Know

- A task must decompose to a `YAWLServiceGateway` (XSD type `WebServiceGatewayFactsType`) whose `yawlService id` attribute matches the registered worklet service URI. Tasks that decompose to a `YNet` (composite tasks) are never sent to external services.
- The `externalInteraction` element in the gateway decomposition controls whether the work item requires resourcing decisions (`manual`) or is handled programmatically (`automated`). For worklet-managed tasks this should be `automated` because the Worklet Service completes the item programmatically without user interaction.
- The RDR tree evaluations run synchronously in the Worklet Service. If rule evaluation is expensive (large case data documents, complex XPath) it runs in the service's own thread, not in the engine. This is safe because Interface B is designed for asynchronous service interactions.
- The worklet sub-case runs as a full independent case with its own `YIdentifier`. It has access to no parent case data except what was copied in at launch time by the Worklet Service. Output flows back through the Worklet Service's completion callback, not directly.
