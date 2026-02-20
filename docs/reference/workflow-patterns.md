# YAWL Workflow Patterns Reference

**Document type:** Reference
**Audience:** AI coding agents working on the YAWL codebase
**Purpose:** Quick-reference for workflow patterns and their YAWL implementations. Consult this when designing specifications, implementing pattern support, or mapping requirements to YAWL constructs.

Sources authoritative for this document:
- `/home/user/yawl/docs/patterns/README.md` — pattern library overview
- `/home/user/yawl/docs/patterns/registry.json` — machine-readable pattern registry (version 6.0.0)
- `/home/user/yawl/docs/architecture/decisions/ADR-020-workflow-pattern-library.md` — architecture decisions

---

## Background

The YAWL Workflow Pattern Library is grounded in the formal Workflow Patterns framework by van der Aalst et al. (2003). YAWL's Petri net engine is capable of expressing all 89 patterns catalogued in that framework across six dimensions: control flow, data, resource, exception, service interaction, and cancellation.

The pattern library (v6.0.0) provides 20 documented, executable pattern templates. The library organises patterns into three categories: Control Flow (WCP-), Enterprise (ENT-), and Agent (AGT-). Templates validate against `schema/YAWL_Schema4.0.xsd`.

**Pattern library API:**

```
GET  /yawl/api/admin/patterns                    — list all patterns
GET  /yawl/api/admin/patterns?q=approval         — search by keyword
GET  /yawl/api/admin/patterns/{id}               — get pattern details
GET  /yawl/api/admin/patterns/{id}/template      — download template.yawl
```

---

## Complexity Levels

| Level | Description |
|---|---|
| BASIC | Single join/split type, no timers. Suitable as a starting point. |
| INTERMEDIATE | Multiple join types, timers, or data-driven routing. Standard enterprise workflows. |
| ADVANCED | Multi-instance tasks, critical sections, compensation. Complex orchestration. |

---

## YAWL Petri Net Elements (join/split vocabulary)

The following element types appear in the YAWL Mechanism column:

| Element | Description |
|---|---|
| `SEQUENCE` | Ordered task chain with no branching. One token flows from task to task. |
| `AND_SPLIT` | All outgoing paths are activated simultaneously. Produces concurrent tokens. |
| `AND_JOIN` | Waits for all incoming paths to complete. Consumes all incoming tokens. |
| `XOR_SPLIT` | Exactly one outgoing path is activated based on a guard condition. |
| `XOR_JOIN` | Passes through the first arriving token. Other incoming paths are never active at runtime. |
| `OR_SPLIT` | One or more outgoing paths are activated based on guard conditions. |
| `OR_JOIN` | Waits for all active incoming paths to complete. (Structured form: known statically.) |
| `MULTI_INSTANCE_TASK` | A task instantiated once per element of a collection. Instances run concurrently. |
| `CANCELLATION_SET` | A set of tasks and conditions cancelled when a triggering task completes. |
| `CRITICAL_SECTION` | Region that executes exclusively — only one case instance may be inside it at a time. |
| `TIMER` | A time-based trigger attached to a task. Fires on absolute deadline or relative duration. |
| `LOOP` | A backward arc allowing a task or sub-net to repeat. Guard condition controls exit. |

---

## Control Flow Patterns (WCP-01 to WCP-20 and beyond)

The van der Aalst catalogue defines 43 control flow patterns (WCP-01 through WCP-43, with some subcategories). The table below covers WCP-01 through WCP-20 in full, plus the two additional patterns documented in the YAWL library (WCP-21 and WCP-38). Patterns WCP-08 through WCP-20 not present in the library are annotated with their YAWL support status based on YAWL's Petri net semantics.

| Pattern ID | Pattern Name | Category | YAWL Support | YAWL Mechanism | Notes / Example |
|---|---|---|---|---|---|
| WCP-01 | Sequence | Basic Control Flow | Full | `SEQUENCE` | Tasks A → B → C. Each task enables the next via a place-transition-place chain. Example: order processing pipeline (validate → process → confirm). Library template: `docs/patterns/control-flow/WCP-01-sequence/`. |
| WCP-02 | Parallel Split | Basic Control Flow | Full | `AND_SPLIT` | Task A fans out to B and C concurrently. Modelled as a task with multiple outgoing arcs to separate places. Example: simultaneous document review and background check. Library template: `docs/patterns/control-flow/WCP-02-parallel-split/`. |
| WCP-03 | Synchronisation | Basic Control Flow | Full | `AND_JOIN` | Tasks B and C must both complete before D begins. Modelled as a task with multiple incoming arcs, one from each parallel branch. Example: all department approvals required before contract signing. Library template: `docs/patterns/control-flow/WCP-03-synchronisation/`. |
| WCP-04 | Exclusive Choice | Basic Control Flow | Full | `XOR_SPLIT` | Exactly one of N branches is activated based on a guard expression evaluated against case data. Guard syntax is JEXL. Non-exhaustive guards cause deadlock. Example: loan routing by amount and credit score. Library template: `docs/patterns/control-flow/WCP-04-exclusive-choice/`. |
| WCP-05 | Simple Merge | Basic Control Flow | Full | `XOR_JOIN` | Convergence after an exclusive choice. Only one branch arrives at runtime; the join is passive (no synchronisation). Example: re-entry point after approved/rejected paths converge. Library template: `docs/patterns/control-flow/WCP-05-simple-merge/`. |
| WCP-06 | Multi-Choice | Advanced Branching | Full | `OR_SPLIT` | One or more of N branches activated. Each outgoing flow has an independent guard condition; multiple can be true simultaneously. Example: notify selected departments based on case attributes. Library template: `docs/patterns/control-flow/WCP-06-multi-choice/`. |
| WCP-07 | Structured Synchronising Merge | Advanced Branching | Full | `OR_JOIN` | Convergence after a multi-choice split. Waits for all activated branches (known at design time) to complete. Requires structured pairing with WCP-06. Library template: `docs/patterns/control-flow/WCP-07-structured-sync-merge/`. |
| WCP-08 | Multi-Merge | Advanced Branching | Partial | Multiple `XOR_JOIN` tokens | Convergence that fires once per incoming token. YAWL supports this via multiple XOR_JOIN instances but does not have a dedicated element. Requires explicit handling to avoid duplicate downstream execution. |
| WCP-09 | Structured Discriminator | Structural Patterns | Partial | Subset `OR_JOIN` with threshold | Activates after the first N of M branches complete. YAWL multi-instance tasks with a completion threshold approximate this. No direct discriminator element exists. |
| WCP-10 | Arbitrary Cycles | Repetition | Full | `LOOP` (backward arc) | Unrestricted looping via any backward arc in the net. YAWL supports arbitrary cycles; the loop is expressed as a place connecting a downstream task back to an upstream task or condition. |
| WCP-11 | Implicit Termination | Termination | Full | Implicit (no outgoing arcs) | The case terminates when no enabled tasks remain and all tokens are consumed. YAWL enforces this via the completion condition: case ends when the output condition is marked. |
| WCP-12 | Multiple Instances Without Synchronisation | Multiple Instances | Full | Multiple `MULTI_INSTANCE_TASK` with no join | Multiple task instances run without waiting for each other. Modelled as a multi-instance task with a zero threshold. |
| WCP-13 | Multiple Instances With A Priori Design-Time Knowledge | Multiple Instances | Full | `MULTI_INSTANCE_TASK` (static count) | Fixed number of instances created at design time. YAWL multi-instance tasks configured with static instance count. |
| WCP-14 | Multiple Instances With A Priori Runtime Knowledge | Multiple Instances | Full | `MULTI_INSTANCE_TASK` (dynamic count) | Number of instances determined at runtime from case data. YAWL dynamic multi-instance tasks read the instance count from an XPath expression over case data. Library template: `docs/patterns/enterprise/multi-instance-review/`. |
| WCP-15 | Multiple Instances Without A Priori Runtime Knowledge | Multiple Instances | Partial | `MULTI_INSTANCE_TASK` with continuation | Instances are created dynamically during execution. YAWL supports incremental instance creation within a multi-instance task through the continuation specification. |
| WCP-16 | Deferred Choice | State-Based | Full | Human task selection | Choice is made at runtime by a resource claiming one of several offered work items. YAWL's resource service offers items to eligible participants; claiming one cancels the others via the cancellation set mechanism. |
| WCP-17 | Interleaved Parallel Routing | State-Based | Partial | Interleaved routing task | Tasks in a set execute in any order but not concurrently. YAWL approximates this with sequential task routing with runtime ordering. The `.specify/patterns/java` extension includes `YInterleavedRouterTask`. |
| WCP-18 | Milestone | State-Based | Partial | Guard on condition marking | A task is enabled only when a specified milestone condition is marked. YAWL implements this via guard expressions that check case data state. The `.specify/patterns/java` extension includes `YMilestoneGuardedTask` and `YMilestoneCondition`. |
| WCP-19 | Cancel Task | Cancellation | Full | `CANCELLATION_SET` on a task | A specified task is cancelled (if enabled or executing) by another task's completion. Modelled as a cancellation set containing the target task. |
| WCP-20 | Cancel Case | Cancellation | Full | Engine `cancelCase()` API | The entire case is terminated. Invocable via Interface B `cancelCase(caseId, sessionHandle)` or triggered by a task with a case-scope cancellation set. |

---

## Extended Control Flow Patterns (WCP-21 and WCP-38)

These patterns are explicitly documented in the YAWL Pattern Library:

| Pattern ID | Pattern Name | Category | YAWL Support | YAWL Mechanism | Notes / Example |
|---|---|---|---|---|---|
| WCP-21 | Critical Section | Concurrency | Full | `CRITICAL_SECTION` | A region of the workflow that only one case instance may occupy at a time. Implemented as a critical section element enclosing a set of tasks. Other cases attempting to enter are blocked until the current case exits. Example: serialised database updates, rate-limited API calls. Library template: `docs/patterns/control-flow/WCP-21-critical-section/`. Introduced in YAWL 5.0. |
| WCP-38 | Cancelling Task | Cancellation | Full | `CANCELLATION_SET` | Completion of one task cancels a set of other tasks (and conditions) in the same case. The cancellation set is declared on the triggering task in the specification XML. Example: customer withdrawal cancels all pending review tasks; SLA breach cancels remaining processing steps. Library template: `docs/patterns/control-flow/WCP-38-cancelling-task/`. |

---

## Enterprise Patterns (ENT-)

Pre-built templates for common business workflow scenarios. Each composes one or more WCP patterns.

| Pattern ID | Pattern Name | Category | Complexity | YAWL Mechanism | Composed From | Use Case |
|---|---|---|---|---|---|---|
| ENT-APPROVAL | Single Approver Workflow | Approval | BASIC | `SEQUENCE`, `XOR_SPLIT` | WCP-01, WCP-04 | A single human task routed to a designated approver. On approval, case continues; on rejection, a rejection path activates. Simplest pattern for expense approval, leave requests. |
| ENT-PARALLEL-APPROVAL | Parallel Multi-Approver Workflow | Approval | INTERMEDIATE | `AND_SPLIT`, `AND_JOIN` | WCP-02, WCP-03 | Multiple approvers work concurrently; all must approve before proceeding. Any rejection can optionally cancel remaining approvals via a `CANCELLATION_SET`. Example: purchase orders requiring finance and manager sign-off. |
| ENT-CONDITIONAL-ROUTING | Conditional Routing Workflow | Routing | INTERMEDIATE | `XOR_SPLIT`, `XOR_JOIN` | WCP-04, WCP-05 | Routes a case to different processing paths based on JEXL guard expressions. Includes a convergence merge after the branches rejoin. Example: invoice routing (manager < $10K, director $10K–$100K, VP > $100K). |
| ENT-ESCALATION | Escalation Chain Workflow | Escalation | INTERMEDIATE | `XOR_SPLIT`, `TIMER` | WCP-04, WCP-38 | A task has a timer. If not completed within the deadline, the task is cancelled and escalated to the next approver level. Chains escalation up a hierarchy. Example: overdue approval escalates from manager to director. |
| ENT-SLA-ENFORCEMENT | SLA Timer Enforcement | Timers | INTERMEDIATE | `TIMER`, `CANCELLATION_SET`, `XOR_SPLIT` | WCP-04, WCP-38 | Enforces an absolute deadline. On expiry, all in-progress work within the SLA scope is cancelled and a violation path activates. Example: regulatory compliance windows, customer SLA enforcement. |
| ENT-COMPENSATION | Compensating Transaction Workflow | Exception | ADVANCED | `CANCELLATION_SET`, `XOR_SPLIT`, `SEQUENCE` | WCP-04, WCP-38 | Implements the Saga pattern. Each step has a compensating action. On failure at any step, compensating actions execute in reverse order to undo completed work. Example: order fulfilment with payment reversal on failure. |
| ENT-LOOPING-REVIEW | Looping Review Cycle | Loops | INTERMEDIATE | `XOR_SPLIT`, `LOOP` | WCP-04 | A review task can return a document for revision. The submit–review cycle repeats until approved or a maximum iteration count is reached. The loop is implemented as a backward arc from the review task to the submission task. Example: document revision, code review rework. |
| ENT-MULTI-INSTANCE | Multi-Instance Review | Multi-Instance | ADVANCED | `MULTI_INSTANCE_TASK` | (none) | A task is instantiated once per element in a collection (e.g., line items, candidates). Instances execute concurrently. The workflow continues when a threshold number of instances complete. Example: review each line item in a purchase order. |

---

## Agent Patterns (AGT-)

> **STATUS:** Agent patterns require YAWL Schema 6.0 (PLANNED - not yet implemented).
> These patterns will be available once Schema 6.0 is created.
> See [ADR-013: Schema Versioning Strategy](../../architecture/decisions/ADR-013-schema-versioning-strategy.md)

Patterns for integrating autonomous agents and LLMs into YAWL workflows. When YAWL Schema 6.0 is released, these patterns will use the `<agentBinding>` element.

| Pattern ID | Pattern Name | Category | Complexity | YAWL Mechanism | YAWL Schema Requirement | Use Case |
|---|---|---|---|---|---|---|
| AGT-AGENT-ASSISTED | Agent-Assisted Task | Decision Support | INTERMEDIATE | `SEQUENCE` | YAWL 6.0 (planned), `<agentBinding>` | An autonomous agent performs preliminary work (draft, analysis, recommendation) and writes output to a task's data. A human reviews and either accepts or modifies before completing the task. Example: AI drafts response; human reviews and submits. |
| AGT-LLM-DECISION | LLM Decision Point | Autonomous Routing | ADVANCED | `XOR_SPLIT` | YAWL 6.0 (planned), `<agentBinding>` | An LLM agent analyses task input data and selects one of N routing branches. The agent's decision is written as a structured output value that drives an `XOR_SPLIT` guard. Example: classify incoming support tickets by content and route to specialised teams. |
| AGT-HUMAN-AGENT-HANDOFF | Human-Agent Handoff | Fallback | INTERMEDIATE | `XOR_SPLIT`, `XOR_JOIN` | YAWL 6.0 (planned), `<agentBinding>` | An agent attempts the task. On failure, low confidence, or timeout, the task is automatically handed off to a human participant. Both paths converge at the same completion point via `XOR_JOIN`. Example: agent fails, task assigned to on-call human. |

---

## YAWL Split/Join Configuration Summary

This table maps each YAWL join/split type to its semantic and the XML `@type` attribute value used in a YAWL specification:

| Type Constant | XML Attribute | Semantic |
|---|---|---|
| `AND` (split) | `type="and"` on outgoing flows | All paths activated unconditionally. |
| `AND` (join) | `type="and"` on join | Waits for all incoming tokens. |
| `XOR` (split) | `type="xor"` + `<condition>` on flows | Exactly one path selected by guard. |
| `XOR` (join) | `type="xor"` on join | Passes first arriving token; passive. |
| `OR` (split) | `type="or"` + `<condition>` on flows | One or more paths activated. |
| `OR` (join) | `type="or"` on join | Waits for all tokens that were activated by the corresponding OR split. |

---

## Data Patterns (DP) — Support Overview

YAWL supports the van der Aalst data patterns through its XML data binding mechanism and XPath/JEXL expressions. These patterns are not catalogued with individual library templates but are supported by the engine:

| Pattern | Support | YAWL Mechanism |
|---|---|---|
| Task Data (input/output mapping) | Full | `<inputParam>` / `<outputParam>` elements with XPath mapping expressions. |
| Block Task Data | Full | Composite tasks propagate data through decompositions. |
| Scope Data | Full | Case data persists across the entire case; task data is scoped to a task instance. |
| Multiple Instance Data | Full | Multi-instance task data binds per-instance using split/join expressions. |
| Case Data | Full | Case-level variables accessible via XPath from any task in the case. |

---

## Resource Patterns (RP) — Support Overview

YAWL's resource service implements the van der Aalst resource patterns. These are configured in the `<resourcing>` element of each task in a specification:

| Pattern | Support | YAWL Mechanism |
|---|---|---|
| Direct Allocation | Full | `<offer type="userDistribution">` with explicit user list. |
| Role-Based Distribution | Full | `<offer type="roleDistribution">` assigns work items to a named role. |
| Deferred Allocation | Full | `<allocate>` block with a pluggable allocator (e.g., round-robin, random, capability-based). |
| Authorisation | Full | `<authorise>` block restricts who may complete an offered work item. |
| Separation of Duties | Full | `<constraint>` elements enforce that different participants handle specified tasks. |
| Case Handling | Full | `<retain>` binding routes follow-on tasks in a case to the same participant. |
| Capability-Based Routing | Full | `<offer type="systemInvocation">` with a custom `ResourceAllocator` implementation. |

---

## References

- **Workflow Patterns Website:** https://www.workflowpatterns.com/
- **van der Aalst, W.M.P., et al. (2003):** Workflow Patterns. LNCS 2626.
- **Russell, N., et al. (2006):** Workflow Data Patterns. QUT Technical Report FIT-TR-2006-04.
- **YAWL Book (2009):** Modern Business Process Automation. Springer. ISBN 978-3-642-03120-5.
- **YAWL Schema Status:** See [ADR-013](../../architecture/decisions/ADR-013-schema-versioning-strategy.md) for versioning strategy. Current production schema: YAWL_Schema4.0.xsd. YAWL Schema 6.0 (planned) will introduce agent binding support.
- **Pattern Registry:** `/home/user/yawl/docs/patterns/registry.json`
- **ADR-020:** `/home/user/yawl/docs/architecture/decisions/ADR-020-workflow-pattern-library.md`
