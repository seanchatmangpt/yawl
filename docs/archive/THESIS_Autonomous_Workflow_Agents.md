# Autonomous Workflow Execution Through Decentralized AI Agents: From YAWL to Process Mining and Agent Stress Testing

**A Dissertation in the Van der Aalst Tradition**

---

## Abstract

We present an architecture for fully autonomous workflow execution in which multiple domain-specific agents discover work items, reason about eligibility, and produce valid outputs without a central orchestrator. Using the YAWL workflow engine and its Petri-net-based semantics, we demonstrate that a stateless, capability-driven design—augmented by large language model (LLM) reasoning—achieves end-to-end case completion for the Order Fulfillment workflow. We extend this core with: (1) **MCP (Model Context Protocol)** integration for richer task context via a task_completion_guide prompt supplied by an MCP server; (2) **process mining** over agent-generated event logs—XES export from the YAWL log gateway, conformance analysis (fitness, observed vs. expected activities), and performance analysis (flow time, throughput); (3) **multi-agent coordination** through capacity checks, where agents query peer endpoints before committing to dependent work; (4) **observability** via structured span logging (eligibility, decision, checkout, checkin) compatible with OpenTelemetry; (5) **PM4Py as MCP and A2A**—a Python process-mining agent exposing discovery, conformance, and performance as MCP tools and A2A skills, integrated into the order-fulfillment scenario and callable from ZAI; (6) **scenario permutations** to stress agents (baseline, rapid timeout, concurrent cases, sequential runs). The results show that decentralized agents can execute multi-party workflows, that agent behaviour can be analysed with process mining over XES logs, and that permutation suites systematically challenge routing, latency, throughput, and recovery.

**Keywords:** Workflow management, YAWL, Petri nets, autonomous agents, process mining, conformance, performance, MCP, A2A, OpenTelemetry, PM4Py, agent stress testing, order fulfillment.

---

## 1. Introduction

### 1.1 Motivation

Workflow management systems (WfMS) separate *design-time* (process modeling) from *runtime* (execution and resource allocation). Traditional WfMS rely on human or system resources to complete work items. We address: *Can workflows be executed autonomously by AI agents that discover work, reason about eligibility, and produce valid outputs without a central coordinator?* We further ask: *How can we analyse agent behaviour using process mining, and how can we systematically stress-test agent coordination?*

This sits at the intersection of:

1. **Workflow Management** (van der Aalst, ter Hofstede): Formal process models (Petri nets, workflow nets, YAWL) with clear semantics.
2. **Process Mining**: Event logs, conformance checking, performance analysis, and the gap between model and reality [1].
3. **Autonomous Agents**: Decentralized, capability-based systems and protocols (A2A, MCP) for discovery and interoperability.

We use the YAWL engine as the execution substrate. Its Petri-net foundation gives a precise notion of case completion. We add a process-mining pipeline over engine event logs and a permutation suite to challenge agents under varied load and timing.

### 1.2 Research Questions

1. **RQ1:** Can stateless, capability-described agents execute the Order Fulfillment workflow without a central orchestrator?
2. **RQ2:** What minimal design elements—capability descriptions, eligibility reasoning, output generation—suffice for full case completion?
3. **RQ3:** How does the architecture align with A2A/MCP for discoverability and interoperability?
4. **RQ4:** Can agent-generated execution be analysed with process mining (conformance, performance) over XES logs exported from YAWL?
5. **RQ5:** Can scenario permutations (concurrent cases, short timeouts, sequential runs) systematically stress agent routing, latency, and throughput?

### 1.3 Contributions

- **Decentralized agent architecture** for YAWL: polling, LLM-based eligibility and decision, InterfaceB checkout/checkin.
- **MCP task context**: Optional `McpTaskContextSupplier` supplying task_completion_guide from an MCP server to enrich decision prompts.
- **Process mining pipeline**: XES export (InterfaceE), `ConformanceAnalyzer` (fitness, deviating traces), `PerformanceAnalyzer` (flow time, throughput, activity counts).
- **Multi-agent coordination**: `CapacityChecker` and `GET /capacity` with `AGENT_PEERS` for peer availability before dependent work.
- **Observability**: `AgentTracer` with spans for eligibility, decision, checkout, checkin; structured JSON logging and optional OTLP.
- **PM4Py as MCP and A2A**: Python agent (discovery, conformance, performance) as MCP tools (STDIO) and A2A skills (HTTP); integration with order-fulfillment (docker-compose, ZAI `process_mining_analyze`, XES export and post-simulation analysis).
- **Scenario permutations**: Config-driven suite (baseline, rapid, concurrent_2/3, sequential_3, back_to_back) via `PermutationRunner` and `config/orderfulfillment-permutations.json`.
- **Deployment**: Docker Compose (including PM4Py agent), Kubernetes, and run scripts for simulation and permutations.

### 1.4 Structure

Section 2 gives background on workflow nets, YAWL, Order Fulfillment, event logs, and process mining. Section 3 presents the architecture, including MCP, capacity, observability, and PM4Py. Section 4 describes the implementation. Section 5 evaluates via single-case simulation, permutation suite, and process mining. Section 6 concludes and outlines future work.

---

## 2. Background

### 2.1 Workflow Nets and YAWL

A *workflow net* (WF-net) is a Petri net \( N = (P, T, F) \) with a single source place \( i \), single sink place \( o \), and every node on a path from \( i \) to \( o \) [1]. YAWL extends WF-nets with composite tasks, multiple-instance tasks, cancellation regions, OR-joins, data flows, and resource allocation [3]. The engine maintains *cases* and *work items* (enabled tasks); completion requires output data conforming to the task’s output schema.

### 2.2 Order Fulfillment Workflow

The Order Fulfillment specification [2] models multi-party procurement and logistics:

| Party    | Domain                           | Example Tasks                |
|----------|-----------------------------------|------------------------------|
| Ordering | Procurement, purchase orders     | Approve PO, Create Order     |
| Carrier  | Transportation, quotes           | Request Quote, Pickup        |
| Freight  | In-transit tracking              | Track Shipment, Accept       |
| Payment  | Invoices, remittance             | Create Invoice, Pay Freight  |
| Delivered| Claims, returns                  | Handle Claim, Process Return |

The workflow has parallelism, choice, and data dependencies. A case completes when all tokens reach the sink.

### 2.3 Event Logs and Process Mining

An *event log* \( L \) is a multiset of *traces*; each trace \( \sigma = \langle a_1, a_2, \ldots, a_n \rangle \) is a sequence of activities for a case [1]. Process mining includes:

- **Process discovery**: Derive a model from \( L \) (e.g. directly-follows graph, inductive miner).
- **Conformance checking**: Compare \( L \) to a reference model (fitness, precision).
- **Performance analysis**: Flow time, throughput, bottleneck analysis.

We export XES from YAWL via InterfaceE (log gateway), then apply conformance and performance analysis—either with in-house Java analyzers or with PM4Py.

### 2.4 MCP and A2A

**Model Context Protocol (MCP)** standardises how tools and prompts are exposed to LLM applications. An MCP server can provide a *task_completion_guide* prompt used to enrich agent decision prompts. **Agent-to-Agent (A2A)** defines discovery (e.g. `/.well-known/agent.json`) and message exchange. Our agents expose A2A discovery; the PM4Py agent exposes both MCP tools (STDIO) and A2A skills (HTTP).

---

## 3. Architecture

### 3.1 Design Principles

1. **No central orchestrator**: Agents poll the engine and self-select work via eligibility.
2. **Stateless workflows**: Capability + work-item context + LLM; no persistent task mappings.
3. **Optional MCP context**: Decision workflow can be augmented with MCP-supplied task_completion_guide.
4. **Optional capacity checks**: Before dependent work, agents can query peer `/capacity`.
5. **Observability**: Spans around eligibility, decision, checkout, checkin for tracing and metrics.
6. **Process mining on agent execution**: XES from engine → conformance and performance analysis.
7. **Permutation-based stress testing**: Configurable scenarios (concurrent, sequential, time-bound) to challenge agents.

### 3.2 Agent Model

An agent \( A \) is a tuple \( (C, E, D, M, K, T) \) where:

- \( C \): *Capability* (domain description).
- \( E \): *Eligibility* \( E(w) \rightarrow \{\text{true}, \text{false}\} \).
- \( D \): *Decision* \( D(w) \rightarrow \text{XML} \), optionally using MCP guide \( M \).
- \( K \): *Capacity check* (optional): query peers before committing.
- \( T \): *Tracer* (optional): emit spans for eligibility, decision, checkout, checkin.

The agent loop: poll live items; for each \( w \), optionally check capacity; if \( E(w) \), checkout \( w \), compute \( y = D(w) \), checkin \( w \) with \( y \); sleep; repeat.

### 3.3 MCP Task Context

When `MCP_ENABLED` is set, the decision workflow uses an `McpTaskContextSupplier` that connects to an MCP server (e.g. YawlMcpServer via STDIO), calls `getPrompt("task_completion_guide", args)`, and injects the returned text into the prompt. This enriches output generation with schema and validation guidance from the MCP server.

### 3.4 Multi-Agent Coordination: Capacity

Agents expose `GET /capacity`. The response indicates availability; when `AGENT_PEERS` is set, the server calls peer `/capacity` endpoints (e.g. Ordering checks Carrier before requesting a quote). This supports best-effort coordination without a central scheduler.

### 3.5 Observability

`AgentTracer` provides spans with name, agent, work-item id, and duration. Default implementation writes JSON to stdout (span_start, span_end with attributes); an OTLP implementation can be plugged in when the OpenTelemetry SDK is on the classpath. Spans are created around eligibility, checkout, decision, and checkin in the discovery loop.

### 3.6 Process Mining Pipeline

1. **Export**: After cases complete, XES is obtained from the YAWL log gateway (InterfaceE) for the orderfulfillment specification.
2. **Conformance**: Compare traces to expected activities and directly-follows relations; report fitness, observed activities, deviating trace IDs.
3. **Performance**: Compute flow time, throughput, and per-activity counts over the log.

This pipeline is implemented in Java (`EventLogExporter`, `ConformanceAnalyzer`, `PerformanceAnalyzer`) and optionally delegated to a PM4Py agent for richer discovery and token-based replay.

### 3.7 PM4Py as MCP and A2A

A separate **PM4Py agent** (Python) exposes:

- **MCP (STDIO)**: Tools `pm4py_discover`, `pm4py_conformance`, `pm4py_performance` over XES input.
- **A2A (HTTP)**: Skills process_discovery, conformance_check, performance_analysis; messages are JSON `{skill, xes_input, ...}`.

The order-fulfillment scenario can export XES and call this agent (e.g. after simulation or via ZAI’s `process_mining_analyze` function), integrating process mining into the same ecosystem as the autonomous agents.

### 3.8 Scenario Permutations

A *permutation* is a scenario configuration: number of concurrent cases, sequential repeat count, pause between runs, timeout. Permutations are defined in JSON and run by `PermutationRunner`. Examples:

- **Baseline**: One case, standard timeout (routing challenge).
- **Rapid**: One case, short timeout (latency challenge).
- **Concurrent 2/3**: Multiple cases in parallel (throughput, contention).
- **Sequential 3 / Back-to-back**: Repeated runs with optional pause (sustained throughput, recovery).

This yields a systematic stress test for agent coordination under varying load and time pressure.

---

## 4. Implementation

### 4.1 Core Components

| Component | Role |
|-----------|------|
| `PartyAgent` | HTTP server, discovery loop, optional MCP supplier, capacity endpoint, tracer |
| `AgentCapability` | Parses `AGENT_CAPABILITY` from environment |
| `EligibilityWorkflow` | ZAI-based eligibility |
| `DecisionWorkflow` | ZAI-based output generation; optional `McpTaskContextSupplier` |
| `CapacityChecker` | HTTP GET to `AGENT_PEERS` for capacity |
| `AgentTracer` | Spans (structured log or OTLP) for eligibility, decision, checkout, checkin |
| `OrderfulfillmentLauncher` | Upload spec, launch one case, poll until complete |
| `XesExportLauncher` | Export orderfulfillment XES to file (InterfaceE) |
| `PermutationRunner` | Load config, run baseline/concurrent/sequential permutations |

### 4.2 Process Mining (Java)

| Component | Role |
|-----------|------|
| `EventLogExporter` | InterfaceB + YLogGatewayClient; `exportSpecificationToXes`, `exportToFile` |
| `ConformanceAnalyzer` | Parse XES, expected activities/directly-follows; fitness, deviating traces |
| `PerformanceAnalyzer` | Flow time, throughput, activity counts from XES |

### 4.3 PM4Py Integration

- **Backend** (`scripts/pm4py/pm4py_backend.py`): `discover_process`, `check_conformance`, `analyze_performance` over XES (file or XML string).
- **MCP server** (`mcp_server.py`): STDIO transport; tools `pm4py_discover`, `pm4py_conformance`, `pm4py_performance`.
- **A2A agent** (`a2a_agent.py`): HTTP; skills process_discovery, conformance_check, performance_analysis; message body JSON with `skill`, `xes_input`.
- **YAWL integration**: Docker Compose service `pm4py-agent`; ZaiFunctionService function `process_mining_analyze` (export XES or use path, call PM4Py A2A agent); optional post-simulation step in `run-orderfulfillment-simulation.sh` when `RUN_PROCESS_MINING=true`; `Pm4PyClient` (Java) for HTTP calls to the PM4Py A2A agent.

### 4.4 Permutation Suite

- **Config**: `config/orderfulfillment-permutations.json` — id, name, description, challenge, concurrentCases, repeatCount, pauseBetweenMs, timeoutSec, disabled.
- **Runner**: `PermutationRunner` — single case, sequential (repeat with pause), or concurrent (launch N cases, poll until all complete). Optional filter via `PERMUTATION_IDS`.
- **Script**: `scripts/run-orderfulfillment-permutations.sh`; Ant target `run-orderfulfillment-permutations`.

### 4.5 Deployment

- **Docker Compose**: Five party agents (8091–8095), engine, postgres; profile `simulation`; optional `pm4py-agent` (9092) with project and scripts/pm4py mounted.
- **Kubernetes**: Deployments and Services per agent; ConfigMap and Secrets for engine URL, credentials, ZAI key.
- **Build**: Ant targets for launcher, export-xes, permutations, party-agent; `.dockerignore` to avoid classpath/version mismatch in containers.

---

## 5. Evaluation

### 5.1 Experimental Setup

- **Engine**: YAWL 5.2, PostgreSQL.
- **Agents**: Five party agents (Ordering, Carrier, Freight, Payment, Delivered); optional PM4Py agent.
- **Specification**: Order Fulfillment 1.2.
- **LLM**: ZAI for eligibility and output generation.
- **Optional**: MCP server for task_completion_guide; PM4Py for process mining.

### 5.2 Validation Criteria

**Single-case run (simulation):**

1. Launcher starts a case.
2. All work items completed by agents (no human intervention).
3. Case reaches sink (removed from getAllRunningCases).
4. Launcher exits 0.

**Permutation run:**

- Each permutation is run according to config (single, sequential, or concurrent).
- Success: all cases for that permutation complete within timeout.
- Summary: \( k \) / \( n \) permutations passed.

**Process mining:**

- XES export succeeds for the specification after cases complete.
- Conformance and performance analyses run (Java and/or PM4Py) and produce fitness, flow time, throughput, activity counts.

### 5.3 Results

- **Decentralized execution**: Agents self-select work via eligibility; no central assigner.
- **Stateless design**: Capability + LLM (+ optional MCP guide) suffice for completion.
- **MCP integration**: Task context from MCP improves alignment with schema and validation rules when enabled.
- **Capacity checks**: Peer availability can be queried before dependent work; behaviour remains best-effort.
- **Observability**: Span logs support tracing and future OTLP export.
- **Process mining**: XES from the engine supports conformance and performance analysis; PM4Py extends this with discovery and token-based replay and is callable from the ZAI natural-language tool and from the simulation script.
- **Permutations**: Baseline, rapid, concurrent_2, concurrent_3, sequential_3, back_to_back systematically stress routing, latency, throughput, and recovery; the suite is configurable and filterable.

### 5.4 Limitations

- **LLM output quality**: Malformed XML causes check-in failure; prompts and schema validation mitigate this.
- **Eligibility accuracy**: Wrong YES/NO leads to skipped or wrongly claimed work; capability descriptions and examples need tuning.
- **Latency**: LLM and polling add delay; short timeouts (e.g. rapid permutation) may fail if agents are slow.
- **Concurrency**: Multiple cases compete for the same agents; throughput depends on agent count and LLM latency.
- **Process mining**: Java analyzers are simplified (e.g. directly-follows); PM4Py provides full discovery and token replay but requires a separate Python service.

---

## 6. Conclusion

We have presented an architecture for autonomous workflow execution using decentralized, capability-driven agents and LLM-based reasoning on top of YAWL. The design is extended with MCP task context, capacity checks, observability spans, and a process mining pipeline over agent-generated event logs. The PM4Py agent exposes process mining as MCP tools and A2A skills and is integrated into the order-fulfillment scenario (Docker, ZAI, XES export, optional post-simulation analysis). A permutation suite (baseline, rapid, concurrent, sequential) systematically stresses agent routing, latency, throughput, and recovery. Together, these contributions show that autonomous agents can execute multi-party YAWL workflows, that their behaviour can be analysed with process mining, and that scenario permutations are a practical way to challenge and evaluate agent coordination.

**Future work:** (1) Stronger conformance (e.g. token-based replay) in the Java pipeline; (2) use of capacity checks inside eligibility or decision (e.g. skip work if peers unavailable); (3) full OTLP export when the OpenTelemetry SDK is bundled; (4) more permutation types (e.g. agent subset, fault injection); (5) correlation of span traces with XES for end-to-end process mining over agent actions.

### 6.1 Benchmarks and Stress Tests on the Java Engine: Maximum Numbers and Failure Envelope

For **mission-critical** deployments we must know the **maximum numbers** the engine can sustain and **when it fails**—i.e. the capacity ceiling and the failure envelope under load. Current evaluation stresses *agent* coordination only; the engine itself has no documented limits. The following establishes how to obtain those limits.

**Mission-critical objectives.**

1. **Maximum sustainable throughput:** Largest cases/sec and work items/sec at which the engine maintains acceptable latency (e.g. P99 &lt; SLA) and error rate (e.g. &lt; 0.1%) over a sustained period.
2. **Failure threshold:** The load (RPS, concurrent cases, concurrent sessions) at which the system begins to fail—errors, timeouts, resource exhaustion—so that production stays clearly below that point.
3. **Documented failure envelope:** For a given deployment (JVM, DB, hardware), a concise report: max cases/sec, max work items/sec, max concurrent cases, and the conditions under which failure occurs (e.g. “beyond N concurrent sessions” or “beyond M launches/sec”).

**Failure modes to capture.** Stress runs must record *when* and *how* the engine fails: connection-pool exhaustion, lock contention or timeouts on `_pmgr`, persistence/transaction failures, out-of-memory, HTTP 5xx or client timeouts, and latency blow-up (e.g. P99 &gt; 30s). Each run should log the load at first failure and the dominant failure mode so that limits are actionable.

**Engine API surface.** The engine is exposed via InterfaceB (HTTP, typically `/yawl/ib`). Key operations: `connect`, `launchCase`, `getCompleteListOfLiveWorkItems`, `checkOutWorkItem`, `checkInWorkItem`, `getAllRunningCases`. Internally, `YEngine` uses a single persistence manager (`_pmgr`) with synchronized blocks around `launchCase` and `completeWorkItem`, so throughput is bounded by persistence and serialisation. Benchmarks must target these operations and drive them until failure.

**1. Stress-to-failure driver (InterfaceB).** A dedicated driver (Java or k6) must:

- Connect and obtain a session handle; upload a **minimal specification** (one atomic task per case) so each case has one work item.
- Ramp or step load: increase concurrent threads (or launch RPS) until errors or latency thresholds are exceeded. For each step, record: cases launched/sec, work items completed/sec, P50/P95/P99 latency, error count and type (e.g. timeout, 5xx, persistence exception).
- Continue increasing load until **first sustained failure** (e.g. error rate &gt; 1% or P99 &gt; SLA). Record the load at that point as the **failure threshold** and the dominant failure mode (e.g. “DB connection pool exhausted at 80 concurrent sessions”).

Variants: (a) ramp concurrent sessions to find max concurrent load; (b) ramp launch RPS to find max throughput; (c) burst of N case launches to find max burst size before failure or severe degradation. Output: **maximum numbers** (max cases/sec, max work items/sec, max concurrent cases/sessions) and **failure envelope** (load and conditions at which the engine fails).

**2. In-process microbenchmarks (JMH).** To separate engine logic from HTTP, use JMH: embed the engine, load a minimal spec, and benchmark `YEngine.launchCase` and `completeWorkItem` in a loop. This gives the **theoretical upper bound** (ops/sec) for engine+persistence alone, before connection limits and HTTP overhead. Compare with HTTP-level stress to see how much headroom is lost at the boundary.

**3. Minimal spec for repeatability.** A small YAWL spec (one input, one atomic task, one output, minimal data) ensures one work item per case so throughput is engine-dominated. Version it (e.g. `exampleSpecs/loadtest/minimal.yawl`) and use it for all stress runs so that **maximum numbers** are comparable across environments.

**4. Integration and deliverables.** The repository has a k6 script for generic endpoints and a performance test plan; the gap is a driver that speaks **InterfaceB** and is run in **stress-to-failure** mode. Next steps:

- Add a **Java** `EngineBenchmarkRunner` (or equivalent) using `InterfaceB_EnvironmentBasedClient`: configurable ramp (threads, RPS), run until failure or max duration, output **max cases/sec**, **max work items/sec**, **failure threshold** (load + dominant failure mode), and latency percentiles at each step.
- Optionally extend **k6** to perform InterfaceB actions and run the same ramp-to-failure from CI.

Deliverables for mission-critical use: a **capacity report** per deployment (max sustainable throughput, max concurrent load) and a **failure envelope** (when it fails, at what load, and how). Production can then be sized to stay below the documented limits with a chosen safety margin.

---

## References

[1] W.M.P. van der Aalst. *Process Mining: Data Science in Action*. Springer, 2016.

[2] S. Clemens, M. La Rosa, A.H.M. ter Hofstede. Order Fulfillment Workflow Model. YAWL Foundation, exampleSpecs/orderfulfillment.

[3] W.M.P. van der Aalst, A.H.M. ter Hofstede. YAWL: Yet Another Workflow Language. *Information Systems*, 30(4):245–275, 2005.

[4] W.M.P. van der Aalst. A practitioner's guide to process mining: Limitations of the directly-follows graph. *Procedia Computer Science*, 2019.

[5] YAWL Foundation. YAWL 5.2 Documentation. https://yawlfoundation.github.io/

[6] Model Context Protocol. https://modelcontextprotocol.io/

[7] Agent-to-Agent Protocol. https://a2a-protocol.org/

[8] PM4Py. Process Mining for Python. https://pm4py.fit.fraunhofer.de/

[9] OpenTelemetry. https://opentelemetry.io/

---

*Thesis document in the van der Aalst tradition: process-centric, formal where appropriate, bridging workflow theory, process mining, and autonomous agent implementation.*
