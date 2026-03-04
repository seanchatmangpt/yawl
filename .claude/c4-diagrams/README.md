# YAWL v6.0.0 - C4 Architecture Diagrams

A comprehensive set of C4 diagrams to visualize and organize the YAWL project's architecture, dependencies, and work in flight.

## Diagram Overview

### C1: System Context Diagram
**File**: `C1-SYSTEM-CONTEXT.puml`

Highest-level view showing YAWL's role in the ecosystem:
- **External actors**: End users, administrators, workflow developers
- **External systems**: SAP, Salesforce, monitoring systems, auth providers, databases
- **Key interactions**: Workflow execution, configuration, deployment

**Use case**: Present YAWL's place to stakeholders unfamiliar with internals

---

### C2: Container Architecture
**File**: `C2-CONTAINER-ARCHITECTURE.puml`

The complete 6-layer module organization:

#### **LAYER 0: Foundation** (No YAWL dependencies, parallel build)
- `yawl-utilities` — Common utilities (logging, config, validation)
- `yawl-security` — Cryptography, TLS, auth modules
- `yawl-erlang` — Distributed messaging backend (Erlang/OTP 28)
- `yawl-graalpy` — Python polyglot support (GraalVM)
- `yawl-graaljs` — JavaScript polyglot support (GraalVM)
- `yawl-rust4pm` — Performance-critical modules via FFI

#### **LAYER 1: First Consumers** (Parallel build, depend on Layer 0)
- `yawl-elements` — YAWL domain model (tasks, gateways, nets)
- `yawl-ggen` — Code generation framework (H-guards, Q-invariants)
- `yawl-graalwasm` — WebAssembly support
- `yawl-dmn` — Decision Model & Notation integration
- `yawl-data-modelling` — Data schema definitions

#### **LAYER 2: Core Engine** (Blocking, depends on Layer 1)
- `yawl-engine` — **YNetRunner** (core orchestrator), YWorkItem state machine, task queuing

#### **LAYER 3: Engine Extension** (Blocking, depends on Layer 2)
- `yawl-stateless` — Stateless orchestration variant

#### **LAYER 4: Services** (Parallel build, depend on Layers 2-3)
- `yawl-authentication` — User auth, token management
- `yawl-scheduling` — Task scheduling, timers
- `yawl-monitoring` — Metrics, tracing, health checks
- `yawl-worklet` — Worklet service (dynamic task invocation)
- `yawl-control-panel` — Admin dashboard (React + Spring)
- `yawl-integration` — **MCP/A2A protocol handlers**
- `yawl-benchmark` — Performance testing suite
- `yawl-webapps` — Web applications

#### **LAYER 5: Advanced Services** (Parallel build, depend on Layers 2-4)
- `yawl-pi` — Portfolio & Program management
- `yawl-resourcing` — Resource allocation, work distribution (Rust FFI)
- `yawl-qlever` — Query engine for workflow analytics

#### **LAYER 6: Top-Level Application** (Blocking, depends on all)
- `yawl-mcp-a2a-app` — Main application (MCP/A2A server, orchestrator)

**Use case**: Understand module organization, layer dependencies, communication patterns

---

### C3: Core Engine Components
**File**: `C3-CORE-ENGINE-COMPONENTS.puml`

Deep dive into `yawl-engine` internals (Layer 2):

#### **Net Execution**
- `YNetRunner` — Core workflow orchestrator managing net lifecycle, transitions, task queuing
- `YWorkItem` — Task instance model with state
- `YParameterFactory` — Data type conversion and validation
- `YExecutionObserver` — Event publisher for execution events

#### **Task Management**
- `TaskQueue` — Virtual Thread Pool using `VirtualThreadPerTaskExecutor` (Java 25)
- `TaskAllocator` — Routes tasks to participants
- `TaskController` — Task state machine (Enabled → Fired → Executing → Completed)
- `ManualTask` — Participant interaction handling
- `AutomaticTask` — Service invocation to external systems

#### **Data Management**
- `StateStore` — In-memory cache of YNet state snapshots
- `ParameterData` — Instance variables and workflow data context
- `DataLogger` — Audit trail of all data changes

#### **Control Flow**
- `GatewayLogic` — AND/OR/XOR decision routing
- `ConditionParser` — YAWL condition expression evaluation
- `LoopHandler` — Iteration management (for-each, while loops)

#### **Exception Handling**
- `ExceptionHandler` — Error recovery and exception routing
- `TimeoutManager` — Deadline enforcement and SLA violations
- `CancellationHandler` — Graceful task termination

#### **Persistence**
- `YNetPersistence` — JDBC ORM wrapper
- `CheckpointManager` — Workflow savepoints
- `RecoveryManager` — Fault tolerance and restart

#### **Concurrency Control**
- `LockManager` — Distributed locking (prevents race conditions)
- `EventBus` — Async event dispatch (virtual thread-safe)
- `StructuredConcurrency` — Java 25 scoped value management

#### **Observability**
- `MetricsExporter` — Prometheus metrics (CPU, memory, throughput)
- `TraceExporter` — OpenTelemetry distributed tracing
- `AuditLogger` — Compliance logging (immutable audit trail)

**Use case**: Understand engine internals, task lifecycle, threading model, persistence

---

### C4: Work In Flight (WIP Status)
**File**: `C4-WIP-WORK-IN-FLIGHT.puml`

Current active initiatives and their status:

#### **PHASE H: Guards Validation** (yawl-ggen)
7 forbidden patterns detected via regex + SPARQL:
- `H_TODO` — TODO/FIXME markers (regex)
- `H_MOCK` — Mock implementations (regex + SPARQL)
- `H_STUB` — Empty placeholder returns (SPARQL)
- `H_EMPTY` — No-op method bodies (SPARQL)
- `H_FALLBACK` — Silent catch-and-degrade (SPARQL)
- `H_LIE` — Code ≠ documentation mismatch (semantic SPARQL)
- `H_SILENT` — Log instead of throw (regex)

**Status**: Design ready, implementation sprint upcoming
**Gate**: Pre-Q validation (before invariants)
**Success criteria**: Zero H violations

#### **PHASE FORTUNE5: Enterprise Scale Testing** (2026-02-28)
SAFe enterprise-scale testing suite:
- Main: 13 tests + chaos injection (FortuneFiveScaleTest)
- Cross-ART: 10 dependency coordination tests (CrossARTCoordinationTest)
- Real YAWL engine integration (no mocks)
- 30 parallel ARTs via Java 25 virtual threads
- 5,000+ dependencies managed
- SLA enforcement: 4h PI planning, 30min deps, 15min portfolio

**Status**: Delivered
**Tests**: 65+ combinations
**Coverage**: 80%+ line, 70%+ branch, 100%+ critical paths
**Next**: Production integration, performance tuning

#### **C4 Diagrams WIP** (This Branch: `claude/c4-diagrams-wip-1tk3Y`)
Creating comprehensive architecture diagrams to organize all initiatives:
- C1: System context
- C2: Container architecture (6-layer module organization)
- C3: Core engine components
- C4: WIP tracking (this diagram)
- C4: Integration flows (MCP/A2A patterns)
- C4: Module dependencies and build graph

**Status**: In progress
**Purpose**: Visualize WIP + roadmap + dependencies
**Target**: Complete narrative arc of project

#### **PHASE INTEGRATION: MCP/A2A** (yawl-integration)
Model Context Protocol and Autonomous Agent Protocol integration:
- MCP server for Claude agents
- A2A server for Z.AI agents
- Protocol bridges translating between MCP ↔ A2A ↔ YAWL
- AgentExecutor running agents as workflow tasks
- Context management and result collection

**Status**: Active development
**Gate**: Protocol compliance (<100ms roundtrip)
**Success**: Seamless agent ↔ YAWL communication

#### **PHASE JAVA25: Virtual Thread Migration** (yawl-engine)
Optimizing for Java 25 virtual threads:
- `VirtualThreadExecutor` — Per-task virtual threads
- `StructuredTaskScope` — Scoped value propagation
- `CarrierScheduler` — Tuning carrier thread pinning for I/O
- `BackpressureQueue` — Using `queue.take()` for carrier parking (not polling)

**Status**: Optimization phase
**Gate**: >100 agents without starvation
**Success**: <1ms task latency

#### **PHASE OBSERVABILITY: Monitoring** (yawl-monitoring)
Complete observability stack:
- **Prometheus metrics** — CPU, memory, task throughput
- **OpenTelemetry tracing** — Request flow visualization
- **Immutable audit trail** — SOX 404 compliance logging
- **Health checks** — gRPC health endpoint

**Status**: Active development
**Gate**: <2ms overhead
**Success**: Full observability stack with Grafana dashboards

**Use case**: Track active projects, understand dependencies between phases, identify blockers

---

### C4: Integration Flows
**File**: `C4-INTEGRATION-FLOWS.puml`

MCP and A2A protocol integration patterns:

#### **MCP Protocol Handler**
- `YawlMcpServer` — Claude MCP server (listens on stdio/socket)
- `ResourceHandler` — Workflow/task definitions
- `ToolHandler` — Execute workflow operations
- `PromptHandler` — Workflow guidance

#### **A2A Protocol Handler**
- `YawlA2AServer` — Z.AI A2A server (listens on TCP)
- `MessageCodec` — Protocol buffer / JSON serialization
- `StateSynchronizer` — Push/pull state updates
- `FlowOrchestrator` — Multi-agent choreography

#### **Protocol Bridge**
- `MessageTranslator` — MCP ↔ A2A ↔ YAWL format conversion
- `CapabilityMapper` — Matches agent capabilities to YAWL operations
- `ErrorConverter` — Standard error format transformation

#### **Execution Runtime**
- `AgentExecutor` — Runs agents as YAWL workflow tasks
- `ContextManager` — Agent lifecycle management
- `ResultCollector` — Outcome tracking
- `AuditRecorder` — Compliance recording

**Flow**:
1. Claude agent calls MCP server
2. MCP handler → Message translator → Capability mapper
3. AgentExecutor invokes workflow task
4. YNetRunner executes task
5. Results collected and returned via MCP

**Use case**: Understand agent integration, protocol layers, message flow

---

### C4: Dependency Graph
**File**: `C4-DEPENDENCY-GRAPH.puml`

Complete module dependency graph across all 6 layers:

- **Layer 0** → No dependencies (build first, in parallel)
- **Layer 1** → Depends on Layer 0 (build in parallel)
- **Layer 2** → Depends on Layer 1 (blocking)
- **Layer 3** → Depends on Layer 2 (blocking)
- **Layer 4** → Depends on Layers 2-3 (build in parallel)
- **Layer 5** → Depends on Layers 2-4 (build in parallel)
- **Layer 6** → Depends on all (blocking)

**Build order** (from pom.xml):
```
Layer 0 (parallel) → Layer 1 (parallel) → Layer 2 → Layer 3 → Layer 4 (parallel) → Layer 5 (parallel) → Layer 6
```

**Critical path** (blocking dependencies):
- Engine ← Elements ← Utilities, Security
- Stateless ← Engine
- MCP/A2A App ← Everything (most critical)

**Use case**: Understand build order, identify build bottlenecks, plan parallel optimization

---

## How to Use These Diagrams

### 1. **For Project Overview**: Start with C1 (System Context)
   - Understand YAWL's role in the ecosystem
   - Identify external dependencies and integrations

### 2. **For Architecture Understanding**: Use C2 + C4 Dependency Graph
   - Understand module organization (6-layer architecture)
   - See build order and parallelization opportunities
   - Identify blocking dependencies

### 3. **For Deep Technical Work**: Use C3 (Core Engine) + Integration Flows
   - Understand task lifecycle and threading model
   - See protocol integration patterns
   - Trace data flow through the system

### 4. **For Project Management**: Use C4 Work In Flight
   - Track active initiatives and their status
   - Understand dependencies between phases
   - Identify blockers and critical path items

### 5. **For Integration Work**: Use Integration Flows
   - Understand MCP/A2A protocol structure
   - See agent ↔ YAWL interaction patterns
   - Plan protocol bridge implementation

---

## Rendering Diagrams

All diagrams are in PlantUML format. To render them:

### Option 1: Online PlantUML Editor
1. Visit https://www.plantuml.com/plantuml/uml/
2. Paste the `.puml` file contents
3. View the diagram

### Option 2: Local PlantUML
```bash
# Install PlantUML
brew install plantuml  # macOS
apt install plantuml   # Ubuntu

# Render single diagram
plantuml C1-SYSTEM-CONTEXT.puml -o /tmp/output

# Render all
for file in *.puml; do plantuml "$file" -o /tmp/output; done
```

### Option 3: VS Code Extension
1. Install "PlantUML" extension (jebbs.plantuml)
2. Right-click `.puml` file → "Export Current Diagram"

---

## Integration with Documentation

These C4 diagrams should be cross-referenced in:
- `.claude/INDEX.md` — Main documentation index
- `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` — Architecture patterns
- `.claude/FORTUNE5_DELIVERY_SUMMARY.md` — Fortune 5 deliverables
- Module README.md files — Individual module architecture

---

## Maintenance

As the project evolves:

1. **Update C4 WIP** when new phases start
   - Add new component boundaries
   - Update status notes
   - Link related diagrams

2. **Update Dependency Graph** when:
   - New modules are added
   - Dependencies change
   - Build order optimizations occur

3. **Update Integration Flows** when:
   - New protocols are added
   - Protocol versions change
   - Agent communication patterns evolve

4. **Version control**: Commit diagram changes with code changes that affect architecture

---

## Key Insights from C4 Diagrams

### 1. **6-Layer Architecture is Critical**
   - Clear separation of concerns
   - Layer 0 foundation enables massive parallelization
   - Blocking dependencies are minimal (Layers 2-3, Layer 6)

### 2. **Virtual Threads Enable Scale**
   - Java 25 virtual threads allow >100 concurrent agents
   - Task latency <1ms target
   - Requires careful queue design (`queue.take()` not `poll()`)

### 3. **Integration is Key Differentiator**
   - MCP + A2A protocols enable autonomous agent ecosystem
   - Protocol bridges decouple external systems from core engine
   - Audit trail integration for enterprise compliance

### 4. **Observability is Built-in**
   - Prometheus + OpenTelemetry from the ground up
   - <2ms overhead constraint
   - SLA monitoring on critical paths

### 5. **Fortune 5 Scale Tests Validate Everything**
   - 30 concurrent ARTs on single machine
   - 5,000+ dependency management
   - Real integration (no mocks), not synthetic benchmarks

---

## Related Documentation

- **Build System**: `/home/user/yawl/.claude/rules/build/`
- **Engine Patterns**: `/home/user/yawl/.claude/rules/engine/`
- **Integration Specs**: `/home/user/yawl/.claude/rules/integration/`
- **H-Guards Design**: `/home/user/yawl/.claude/rules/validation-phases/`
- **Fortune 5 Tests**: `/home/user/yawl/.claude/FORTUNE5_INDEX.md`

---

**Created**: 2026-03-04
**Branch**: `claude/c4-diagrams-wip-1tk3Y`
**Purpose**: Organize and visualize YAWL v6.0.0 architecture + WIP initiatives
**Status**: In progress (diagrams 1-5 complete, integration documentation pending)
