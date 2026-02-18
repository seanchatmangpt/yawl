# Worklet Service

> The Worklet Service is a YAWL v4 concept. It is NOT implemented in YAWL v6.

## What Was the Worklet Service (YAWL v4)

In YAWL v4, the Worklet Service was a separate web application that intercepted task
firings and substituted dynamically selected workflow sub-specifications — called
"worklets" — chosen by evaluating Ripple Down Rule (RDR) trees against live case data.

Static YAWL specifications express the full control-flow structure of a business process
at design time. That works well when the process is predictable. In practice, many
processes have variation points: the exact procedure for handling a loan application
depends on the applicant's credit tier, the regulatory jurisdiction, and the product
type. Encoding every combination as branches inside a single net produces an unmanageable
specification.

In v4, worklets solved this by separating the main process structure from the variant
sub-processes. The main specification contained placeholder tasks. At runtime, when a
placeholder task fired, the Worklet Service selected the appropriate sub-specification
(the worklet) by evaluating an RDR tree against live case data, launched it as a sub-case,
waited for it to complete, and then let the main case resume. The main specification never
needed to change as business rules evolved — only the RDR trees changed.

### RDR Basis: What a Ripple Down Rule Tree Was

An RDR tree was a binary decision tree where:

- Each **node** contained a condition expressed as an XPath-like predicate evaluated
  against case data (net variables).
- A node that evaluated to `true` selected its **true child** (if present) or fired its
  **conclusion** — the worklet specification to launch.
- A node that evaluated to `false` selected its **false child** (if present) or
  propagated up to the parent's false branch.
- The root node's false branch held the **default worklet** (the fallback when no
  condition matched).

The "ripple down" name came from how the tree grew. When a domain expert wanted to add
a new rule, they attached it as a refinement on a leaf that previously gave the wrong
answer. This meant rules never needed to be restructured — new exceptions rippled down
from where the current logic failed.

## Status in YAWL v6

The `yawl-worklet` Maven module exists in the repository as an empty stub (no Java source
files). The Worklet Service has not been ported to YAWL v6.

Searching for `**/worklet/**/*.java` in the v6 codebase returns zero results. There is no
`WorkletService` class, no `RdrNode`, no `RdrSet`, no `HandlerRunner`, and no RDR
evaluation engine in v6.

## What to Use Instead in YAWL v6

YAWL v6 provides two mechanisms that together cover the use cases the Worklet Service
addressed.

### MCP Tools for Conditional Logic

The MCP server (`YawlMcpServer`) exposes 15 tools to AI agents via the Model Context
Protocol. An agent invoking `yawl_checkout_work_item` and `yawl_checkin_work_item` can
apply any conditional logic it chooses — including rules equivalent to an RDR tree —
before deciding how to complete a work item or what data to write back.

When a work item requires dynamic routing, the AI agent evaluates the case data (available
via `yawl_get_case_data`) and then either:

- Checks the item in with output data that drives the net's own XOR branches, or
- Launches a separate case for the chosen sub-process via `yawl_launch_case`, monitors it
  via `yawl_get_case_status`, and checks in the original item after the sub-case completes.

This is the direct functional equivalent of what the Worklet Service did, implemented
in the agent's reasoning layer rather than in a separate rule-evaluation service.

### A2A Skills for Agent-Driven Decisions

The A2A protocol (`YawlA2AServer`) lets registered agent skills be invoked by name when
a work item reaches a particular task. An A2A skill can apply arbitrary logic — database
lookups, external API calls, ML model inference — to decide what the task should do and
then interact with the YAWL engine via the same MCP tools to carry out that decision.

A2A skills are the v6 analogue of the RDR-selected worklet: a named, reusable unit of
logic that runs in place of a static task implementation.

### Comparison

| Concern | v4 Worklet Service | v6 Alternative |
|---|---|---|
| Conditional sub-process selection | RDR tree evaluated by WorkletService | Agent/skill evaluates case data and selects path |
| Reusing logic across multiple tasks | Shared worklet specification | Shared MCP tool or A2A skill registered once |
| Runtime adaptation without spec changes | Update RDR tree | Update agent reasoning or A2A skill implementation |
| Separation of main process and variants | Worklet specs separate from parent spec | Sub-cases launched via `yawl_launch_case` |

## References

- MCP tools: `src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java`
- MCP server: `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`
- A2A server: `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`
- How to add a custom MCP tool: `docs/how-to/implement-worklet-service.md`
