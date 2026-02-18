# The Dual-Engine Architecture: Stateful vs Stateless

YAWL has two workflow runtimes: `YEngine` and `YStatelessEngine`. They execute the same workflow specifications using the same Petri-net semantics, but they differ fundamentally in how they handle state. This document explains why both engines exist, what drove the design, and what risks the dual-family structure introduces. For guidance on choosing the right engine for a given deployment, see ADR-001 (`docs/architecture/decisions/ADR-001-dual-engine-architecture.md`), which includes a decision flowchart.

---

## Two Engines, One Codebase

YAWL's source tree contains two complete engine implementations that live at separate Java package prefixes:

- `org.yawlfoundation.yawl.engine.*` — the stateful engine, compiled into the `yawl-engine` Maven module
- `org.yawlfoundation.yawl.stateless.engine.*` — the stateless engine, compiled into the `yawl-stateless` Maven module

Both share the same workflow element definitions from `org.yawlfoundation.yawl.elements.*` (the `yawl-elements` module) and both implement the same Petri-net execution logic via `YNetRunner`. The `YSpecification` class, which represents a parsed workflow definition, is also shared — you load a specification once and can hand it to either engine.

The key distinction is what happens to workflow state between steps. The stateful engine writes every case, work item, and data binding to a relational database (Hibernate 6.5 / PostgreSQL or MySQL). The stateless engine keeps all state in memory for the duration of a single invocation and discards it afterward. No database. No recovery. No cross-invocation memory.

---

## Why Two Engines Exist

The stateful engine is the original YAWL runtime, present since the project's academic roots at Queensland University of Technology. It was designed for long-running workflows where a loan approval might sit in a human's work queue for days, where an order fulfillment case must survive a server restart, and where compliance requires an immutable audit trail of every state transition. For that class of problem, persistent state is not optional — it is the point.

The stateless engine was added in v5.2, driven by three forces that emerged over the preceding decade:

**Testing without infrastructure.** Running the full YAWL test suite against the stateful engine requires a running PostgreSQL instance. This blocked local development for contributors who did not maintain a local database and made CI pipelines slower and more fragile. A stateless engine that keeps everything in memory runs tests with no external dependencies and completes them faster.

**Cloud-native deployment.** Serverless platforms (AWS Lambda, Google Cloud Functions) do not provide persistent storage between invocations, and they start and stop containers on demand. The stateful engine cannot run in this environment because it assumes it can hold a database connection open indefinitely. The stateless engine fits naturally: each invocation receives the current case state as input and returns the new case state as output. The caller is responsible for persisting state between invocations if persistence is needed.

**AI agent and MCP tool integration.** When YAWL exposed workflow execution as tools via the Model Context Protocol (yawl-integration), tool calls are inherently stateless: a tool receives inputs, does work, and returns outputs. The stateless engine maps cleanly to this model. An AI agent can execute a workflow specification to completion in a single MCP tool call without any database infrastructure. The stateful engine, which needs to allocate work items and wait for human responses, is not appropriate for automated, single-shot execution.

ADR-001 records all three of these drivers, along with the alternatives that were rejected: a stateful-only engine (cannot run in serverless or MCP contexts), a stateless-only engine (cannot support human tasks or long-running cases), and an adapter layer (too complex, does not resolve the fundamental tradeoff).

---

## When to Use Which Engine

The choice is straightforward when you understand what each engine gives up.

**Use the stateful engine (`YEngine`) when:**
- Cases run for more than a few minutes.
- Human tasks or work queues are part of the workflow. Human interaction requires work items to persist between the moment they are offered and the moment a person completes them — that gap may be hours or days.
- The system must survive restarts. The stateful engine recovers in-progress cases from the database on startup.
- Audit trails are required. Every state transition is logged to the persistence layer with a timestamp.
- The workflow involves OR-join synchronization across long time windows, where joined branches may complete hours apart.

**Use the stateless engine (`YStatelessEngine`) when:**
- Cases complete in seconds or a few minutes.
- There are no human tasks — every step is automated.
- The deployment environment cannot host a database (serverless, FaaS, MCP tool context).
- Throughput matters more than durability. The stateless engine can execute hundreds of short cases per second on a single JVM because there is no database overhead.
- You are writing tests. Stateless execution is the right choice for unit and integration tests that verify workflow logic.
- You are implementing an AI agent tool. The MCP and A2A integration layers in `yawl-integration` use the stateless engine for single-shot case execution.

ADR-001 also provides a decision flowchart: start with "Is workflow duration greater than 5 minutes?" and follow the branches. When in doubt, prefer stateful — it is the safer choice because it does not lose state.

---

## The FM2 Risk: Dual-Family Class Confusion

The dual-engine structure introduces the most consequential risk in the codebase: dual-family class confusion, rated FM2 in the FMEA table (RPN 224, the highest single risk score in the project).

The problem is that many class names appear in both engines. `YEngine`, `YNetRunner`, `YWorkItem`, `YNet`, `YTask`, `YAtomicTask` — all of these exist twice, under different package prefixes. A developer who opens `YEngine.java` in an IDE may be editing the stateful version or the stateless version depending on which file the IDE resolved. The wrong edit in the wrong file produces a bug that is hard to diagnose because the class name gives no indication of which engine it belongs to.

The `dual-family.json` fact file (`docs/v6/latest/facts/dual-family.json`) catalogues all 51 mirror pairs with their `MIRROR_REQUIRED` policy. The `16-dual-family-map.mmd` Mermaid diagram visualizes the mirror relationships. Both resources exist so that contributors and automated tooling can quickly determine which version of a class they are looking at.

The Maven module boundary is the primary structural defense against this confusion. At the Maven level, `yawl-engine` and `yawl-stateless` are separate artifacts. Code in `yawl-engine` cannot accidentally import the stateless `YEngine` without an explicit module dependency (which the pom does not declare). This containment does not help within an IDE that shows both modules simultaneously, but it does mean that compiled artifacts are never confused — the bytecode in `yawl-engine.jar` is definitively the stateful family, and the bytecode in `yawl-stateless.jar` is definitively the stateless family.

The practical discipline for developers: always verify the fully-qualified class name before editing a file. `org.yawlfoundation.yawl.engine.YNetRunner` is stateful. `org.yawlfoundation.yawl.stateless.engine.YNetRunner` is stateless. If the stateless prefix appears in a file, you are in the stateless engine.

---

## The Integration Layer

`yawl-integration` is the module that bridges both engines to the outside world. It declares dependencies on both `yawl-engine` and `yawl-stateless` in its pom.xml. Within this module, the MCP server and A2A server explicitly choose which engine to use based on the nature of the operation.

For v6.0, ADR-021 formalizes this selection logic as the `EngineSelector` component. When a case launch arrives at the API, the selector inspects the specification's `<executionProfile>` element (if present) and applies a series of checks: Does the specification contain human tasks? Does it declare a `maxDuration` hint that fits within the stateless window? Is the stateless engine available and healthy? Based on this evaluation, the selector routes the case to the appropriate engine, records the decision in the audit log, and returns the `engineUsed` field in the launch response.

This automatic selection is Phase 3 of the ADR-001 migration plan, which originally required operators to choose the engine at deployment time by deploying either the stateful WAR or the stateless JAR. Phase 3 allows both engines to run simultaneously in the same deployment, with the selector making the per-case decision transparently. A unified `GET /ib/cases/{caseId}` endpoint works regardless of which engine is running the case.

The `yawl-integration` module is the only module in the reactor that sees both engine families at compile time. All other modules depend on exactly one: `yawl-engine` depends on the stateful family, `yawl-stateless` is the stateless family, and higher-level modules (`yawl-resourcing`, `yawl-worklet`, `yawl-scheduling`) depend on `yawl-engine`. This asymmetry means that the dual-family confusion risk is primarily concentrated in `yawl-integration` and in any code written directly against the integration layer.

---

## The Longer Arc: Toward a Unified Engine

The current dual-engine structure is explicitly a transitional architecture. ADR-001 phases the migration plan through v6.0 (automatic selection, Phase 3). The v7.0 roadmap — not yet formalized in an ADR — targets a unified `YNetRunner` with a pluggable persistence backend, where "stateless" becomes a configuration of the single engine rather than a separate implementation.

That unification requires the same physical source restructuring that the shared-src strategy deliberately avoided. When the Java Module System adoption happens (part of the v7.0 work), `module-info.java` boundaries will enforce the separation that currently relies on include patterns and developer discipline. At that point, the two engine families can either be formally merged (with persistence abstracted away) or formally separated into independent Maven artifacts with no shared source. Either outcome is cleaner than the current arrangement.

Until then, the dual-engine architecture is the right tradeoff: it provides genuine value for testing, serverless deployment, and AI agent integration while preserving full functionality for the long-running, human-in-the-loop workflows that are YAWL's core use case.
