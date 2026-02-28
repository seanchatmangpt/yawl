# YAWL Module Quick Reference — Choose Your Component

**TL;DR**: Use this guide to pick the right YAWL module(s) for your use case in 2 minutes.

---

## 1-Minute Decision Tree

```
What are you building?
│
├─ "A workflow engine"
│  └─ Need persistent state & transactions?
│     ├─ YES → yawl-engine (stateful, proven, enterprise)
│     └─ NO  → yawl-stateless (cloud-native, scale 1M+/day)
│
├─ "A scalable cloud app"
│  └─ Need event-driven architecture?
│     ├─ YES → yawl-stateless + yawl-integration (Kafka/MCP)
│     └─ NO  → yawl-engine + Docker
│
├─ "AI/ML case prediction"
│  └─ Use yawl-pi (Predictive Intelligence, van der Aalst 2025)
│
├─ "Resource & team management"
│  └─ Use yawl-resourcing (allocators, queues, participants)
│
├─ "Task scheduling & timers"
│  └─ Use yawl-scheduling (calendar integration, SLA tracking)
│
├─ "External system integration"
│  └─ Use yawl-integration (MCP, A2A, message queues)
│
├─ "Monitoring & observability"
│  └─ Use yawl-monitoring (OpenTelemetry, Prometheus, tracing)
│
├─ "Security & authentication"
│  └─ Use yawl-authentication (JWT, CSRF, session management)
│
├─ "Process mining & analytics"
│  └─ Use yawl-monitoring + yawl-pi (event logs, OCPM)
│
└─ "Workflow specification & validation"
   └─ Use yawl-elements (domain model, YAWL 4.0 syntax)
```

---

## Module Layer Architecture

```
┌───────────────────────────────────────────────────┐
│  Layer 6 (Top-Level Apps)                         │
│  ┌─────────────────────────────────────────────┐  │
│  │ yawl-webapps (Web UI, REST API)             │  │
│  │ yawl-control-panel (Desktop UI)             │  │
│  │ yawl-mcp-a2a-app (Autonomous agents)        │  │
│  └─────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 5 (Services)                              │
│  ┌──────────┐ ┌──────────────┐ ┌──────────────┐ │
│  │yawl-pi   │ │yawl-resourcing│ │yawl-monitoring│
│  │(AI/ML)   │ │(resource mgmt)│ │(observability)│
│  └──────────┘ └──────────────┘ └──────────────┘ │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 4 (Integration & Services)                │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐ │
│  │yawl-auth│ │yawl-sched│ │yawl-integ│ │yawl- │ │
│  │          │ │          │ │          │ │workle│ │
│  └─────────┘ └──────────┘ └──────────┘ └──────┘ │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 3 (Engine Extension)                      │
│  ┌────────────────────────────────────────────┐  │
│  │ yawl-stateless (Stateless variant)         │  │
│  └────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 2 (Core Engine)                           │
│  ┌────────────────────────────────────────────┐  │
│  │ yawl-engine (YEngine, YNetRunner, state)   │  │
│  └────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 1 (Foundation)                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐ │
│  │yawl-     │ │yawl-     │ │yawl-     │ │yawl- │ │
│  │elements  │ │ggen      │ │security  │ │graal*│ │
│  │(domain)  │ │(codegen) │ │(crypto)  │ │(poly)│ │
│  └──────────┘ └──────────┘ └──────────┘ └──────┘ │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────┴─────────────────────────────────┐
│  Layer 0 (Foundation / Utilities)                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐ │
│  │yawl-util │ │yawl-bench│ │yawl-data │ │yawl- │ │
│  │(helpers) │ │(perf)    │ │(modeling)│ │dmn   │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────┘ │
└────────────────────────────────────────────────────┘
```

---

## Module Reference Matrix

| Module | Layer | Purpose | Key Classes | Use When | Skip If |
|--------|-------|---------|-------------|----------|---------|
| **yawl-engine** | 2 | Stateful workflow engine | YEngine, YNetRunner, YWorkItem | Need persistence, ACID transactions, <100K cases/day | Building cloud-native, need to scale 1M+/day |
| **yawl-stateless** | 3 | Stateless engine variant | YStatelessEngine, EventProcessor | Need horizontal scaling, event-driven, cloud-native | Need ACID guarantees, single instance OK |
| **yawl-authentication** | 4 | Session & auth mgmt | SessionManager, JwtHandler, CsrfProtection | Using REST API, need JWT/OAuth, multi-tenant | Building embedded library |
| **yawl-scheduling** | 4 | Workflow timing | TimerService, CalendarService, TaskDueDateService | Need task scheduling, SLA tracking, timers | No time-sensitive work |
| **yawl-monitoring** | 5 | Observability suite | OpenTelemetryExporter, MetricsCollector, EventLogger | Need production metrics, tracing, debugging | Single-machine dev |
| **yawl-pi** | 5 | Predictive intelligence | OCPMAnalyzer, PredictiveModel, RiskAssessment (van der Aalst 2025) | Need case prediction, risk analysis, process mining | No AI/ML requirements |
| **yawl-resourcing** | 5 | Resource management | WorkQueueManager, ParticipantService, WorkloadAllocator | Need task allocation, work queues, team management | No resource constraints |
| **yawl-integration** | 4 | External integrations | MCP handler, A2A server, MessageBridge | Need MCP/A2A, external connectors, message queues | Standalone application |
| **yawl-elements** | 1 | Domain model | YSpecification, YTask, YCondition, YNetContainer | Building core engine, parsing YAWL 4.0 syntax | Using pre-built engine |
| **yawl-ggen** | 1 | Code generation | CodeGenerator, SpecificationParser, TypeFactory | Generating code from specs, validation | Static code, no generation |
| **yawl-security** | 1 | Cryptography | CipherService, EncryptionHandler, SecureRandom | Need encryption, secure storage | No sensitive data |
| **yawl-graalpy** | 1 | Python polyglot | PythonScriptEngine, PyObjectBridge | Executing Python in tasks | Pure Java shop |
| **yawl-graaljs** | 1 | JavaScript polyglot | JsScriptEngine, JsObjectBridge | Executing JS in tasks | Pure Java shop |
| **yawl-graalwasm** | 1 | WebAssembly polyglot | WasmScriptEngine, WasmBridge | Executing WASM in tasks | Pure Java shop |
| **yawl-webapps** | 6 | Web UI + REST API | RestController, WebController, ApiGateway | Need web interface, REST endpoints | Headless only |
| **yawl-control-panel** | 6 | Desktop UI | SwingPanel, WorkflowEditor, SimulationPanel | Need desktop app, offline editing | Web app preferred |
| **yawl-mcp-a2a-app** | 6 | Autonomous agents | MCP handler, A2A server, AgentOrchestrator | Integrating with Claude/agents, autonomous orchestration | Traditional clients |
| **yawl-worklet** | 4 | Dynamic subprocess | WorkletService, WorkletInvoker, DynamicSubnet | Need dynamic subprocesses, plug-in architecture | Static workflow graph |
| **yawl-utilities** | 0 | Common helpers | StringUtil, CollectionUtil, XmlUtil | Dependency for all modules | Direct use rare |
| **yawl-benchmark** | 0 | Performance testing | BenchmarkRunner, PerformanceMeter, LoadGenerator | Running performance tests | Production builds |
| **yawl-data-modelling** | 0 | Data structures | DataType, DataVariable, TypeSystem | Custom data types, validation | Built-in types sufficient |
| **yawl-dmn** | 0 | Decision modeling | DMNEngine, DecisionTable, RuleEngine | Decision automation in workflows | Simple conditional logic |

---

## Common Dependency Combinations

### Minimal: Single-Instance Workflow Engine

```
yawl-engine
  ├─ yawl-elements (domain model)
  ├─ yawl-security (encryption)
  └─ yawl-utilities (helpers)
```

**Use for**: Internal tool, proof-of-concept, dev/test
**Scales to**: 1K cases/day
**Setup**: 10 minutes

### Standard: REST API + Web UI

```
yawl-webapps (includes engine internally)
  ├─ yawl-authentication (session management)
  ├─ yawl-monitoring (basic logging)
  ├─ yawl-security (TLS, encryption)
  └─ yawl-utilities
```

**Use for**: SMB applications, internal workflows
**Scales to**: 50K cases/day
**Setup**: 30 minutes

### Enterprise: HA + Microservices

```
yawl-engine (primary)
  ├─ yawl-authentication (OAuth/JWT)
  ├─ yawl-monitoring (OpenTelemetry, Prometheus)
  ├─ yawl-scheduling (SLA tracking)
  ├─ yawl-resourcing (work queues)
  ├─ yawl-pi (risk analysis)
  ├─ yawl-integration (MCP/A2A)
  └─ yawl-security (FIPS 140-2)
```

**Use for**: Enterprise, 24/7 uptime required
**Scales to**: 200K cases/day (stateful)
**Setup**: 2-4 hours

### Cloud-Native: Kubernetes + Event-Driven

```
yawl-stateless (primary)
  ├─ yawl-integration (Kafka, MCP)
  ├─ yawl-authentication (JWT)
  ├─ yawl-monitoring (distributed tracing)
  ├─ yawl-pi (real-time prediction)
  └─ yawl-security (mTLS)
```

**Use for**: Cloud deployments, auto-scaling, 1M+ cases/day
**Scales to**: 1M+/day with horizontal scaling
**Setup**: 1-2 hours

### AI/ML + Process Mining

```
yawl-stateless (event-sourced)
  ├─ yawl-pi (OCPM, van der Aalst 2025)
  ├─ yawl-monitoring (event logs, metrics)
  ├─ yawl-integration (MCP to Claude)
  ├─ yawl-authentication (service accounts)
  └─ yawl-graal* (polyglot ML models)
```

**Use for**: Process mining, predictive analytics, autonomous agents
**Scales to**: 1M+ events/day + real-time models
**Setup**: 2-3 hours

---

## Module Dependencies (Read-Map)

```
yawl-webapps
  ├─ yawl-engine (stateful execution)
  │  ├─ yawl-elements (domain model)
  │  ├─ yawl-security (encryption)
  │  └─ yawl-utilities
  ├─ yawl-authentication (sessions, JWT)
  │  ├─ yawl-security
  │  └─ yawl-utilities
  ├─ yawl-monitoring (observability)
  │  └─ yawl-utilities
  └─ [OPTIONAL] yawl-resourcing, yawl-scheduling, yawl-integration

yawl-stateless
  ├─ yawl-elements (spec parsing)
  ├─ yawl-security
  ├─ yawl-integration (Kafka/MCP)
  └─ yawl-utilities

yawl-pi
  ├─ yawl-monitoring (event logs)
  ├─ yawl-elements
  └─ yawl-utilities

yawl-mcp-a2a-app
  ├─ yawl-engine or yawl-stateless
  ├─ yawl-integration (MCP/A2A handlers)
  ├─ yawl-authentication
  └─ yawl-monitoring

yawl-graal* (Python/JS/WASM)
  └─ yawl-utilities
```

---

## Feature Matrix: Which Module Provides What?

| Feature | yawl-engine | yawl-stateless | yawl-webapps | yawl-integration | yawl-monitoring | yawl-pi |
|---------|:-----------:|:--------------:|:------------:|:----------------:|:---------------:|:-------:|
| Workflow execution | ✓ | ✓ | ✓ | - | - | - |
| Persistent state | ✓ | - | ✓ | - | - | - |
| Stateless/event-sourced | - | ✓ | - | - | - | - |
| REST API | - | - | ✓ | ✓ | - | - |
| Web UI | - | - | ✓ | - | - | - |
| Session management | - | - | ✓ | - | - | - |
| Authentication (JWT/OAuth) | - | - | ✓ | ✓ | - | - |
| Resource allocation | - | - | - | - | - | - |
| Task scheduling | - | - | - | - | - | - |
| OpenTelemetry tracing | - | - | - | - | ✓ | ✓ |
| Prometheus metrics | - | - | - | - | ✓ | ✓ |
| Structured logging | - | - | - | - | ✓ | ✓ |
| MCP integration | - | - | - | ✓ | - | - |
| A2A (agent-to-agent) | - | - | - | ✓ | - | - |
| Kafka/RabbitMQ support | - | ✓ | - | ✓ | - | - |
| Predictive analytics | - | - | - | - | - | ✓ |
| Process mining (OCPM) | - | - | - | - | ✓ | ✓ |
| Python/JS/WASM tasks | ✓ | ✓ | ✓ | - | - | - |

---

## Build Command Reference

### Build specific module

```bash
# Just one module
mvn clean package -pl yawl-engine -DskipTests

# Multiple modules (compile in order)
mvn clean package -pl yawl-engine,yawl-stateless,yawl-webapps

# Full multi-module build
mvn clean package
```

### Include/exclude modules

```bash
# Include dependent modules
mvn clean package -am -pl yawl-webapps  # Build yawl-webapps + dependencies

# Skip tests for speed
mvn clean package -DskipTests

# Show build order
mvn -pl \! yawl-integration validate -DskipTests  # Build all except yawl-integration
```

---

## Decision Checklist

### Before adding a module, ask:

- [ ] Does this module solve my use case?
- [ ] Do I need its dependencies?
- [ ] Will it increase build time significantly?
- [ ] Does it add security/compliance burden?
- [ ] Can I defer this to Phase 2?

### Modules to include in Phase 1:

- yawl-engine OR yawl-stateless (pick one)
- yawl-authentication (if REST API)
- yawl-monitoring (if production)
- yawl-integration (if external systems)

### Modules to defer to Phase 2:

- yawl-pi (AI/ML)
- yawl-resourcing (resource mgmt)
- yawl-scheduling (advanced timing)
- yawl-worklet (dynamic subprocesses)

---

## Module Status & Maturity

| Module | Status | Maturity | Java 25 | Notes |
|--------|--------|----------|---------|-------|
| yawl-engine | PRODUCTION | GA | ✓ | Core engine, proven, 20+ years |
| yawl-stateless | PRODUCTION | GA | ✓ | Cloud-native, recommended for scale |
| yawl-webapps | PRODUCTION | GA | ✓ | Full REST API + web UI |
| yawl-authentication | PRODUCTION | GA | ✓ | JWT, OAuth, CSRF protection |
| yawl-monitoring | PRODUCTION | GA | ✓ | OTel 1.0, Prometheus 2.0 |
| yawl-integration | PRODUCTION | GA | ✓ | MCP 1.0, A2A 1.0 |
| yawl-pi | PRODUCTION | GA | ✓ | OCPM van der Aalst 2025, new |
| yawl-resourcing | PRODUCTION | GA | ✓ | Mature resource service |
| yawl-scheduling | PRODUCTION | GA | ✓ | Calendar integration, SLA |
| yawl-graalpy | PRODUCTION | GA | ✓ | GraalVM 23.1+, Python 3.11 |
| yawl-graaljs | PRODUCTION | GA | ✓ | GraalVM 23.1+, ECMAScript 2022 |
| yawl-graalwasm | PRODUCTION | GA | ✓ | GraalVM 23.1+, WASM 1.0 |
| yawl-mcp-a2a-app | PRODUCTION | GA | ✓ | Autonomous agents, Claude API |
| yawl-control-panel | PRODUCTION | STABLE | ✓ | Desktop UI, mature |
| yawl-worklet | PRODUCTION | STABLE | ✓ | Dynamic subprocesses |

---

## Quick Answers

**Q: Should I use yawl-engine or yawl-stateless?**
A: Use yawl-engine if you need ACID guarantees or have <100K/day.
Use yawl-stateless if you need cloud-native, auto-scaling, or 1M+/day.

**Q: Do I need yawl-monitoring in dev?**
A: No, but enable it before production. Start with basic logging.

**Q: Can I use yawl-pi without yawl-monitoring?**
A: Not recommended—PI needs event logs. Include yawl-monitoring.

**Q: Is yawl-integration required for REST API?**
A: No, yawl-webapps provides REST API. Use yawl-integration only for external connectors.

**Q: Should I include all graal* modules?**
A: No, only if you need polyglot support. Include just what you use.

**Q: Can I start without yawl-authentication?**
A: For dev/test yes. For any multi-user or cloud, include it immediately.

---

## Next Steps

1. **Identify your use case** from the decision tree
2. **Select modules** from the recommended combinations
3. **Run Maven build**: `mvn clean package -pl <modules>`
4. **Start minimal**, add modules as needed
5. **Refer to module README.md** for specific configuration

---

## Module Documentation Links

- [yawl-engine](../yawl-engine/README.md) - Core stateful engine
- [yawl-stateless](../yawl-stateless/README.md) - Stateless variant
- [yawl-webapps](../yawl-webapps/README.md) - Web UI + REST API
- [yawl-integration](../yawl-integration/README.md) - External integrations
- [yawl-monitoring](../yawl-monitoring/README.md) - Observability
- [yawl-pi](../yawl-pi/README.md) - Predictive intelligence
- [yawl-authentication](../yawl-authentication/README.md) - Auth & session
- [yawl-resourcing](../yawl-resourcing/README.md) - Resource management
- [yawl-scheduling](../yawl-scheduling/README.md) - Task scheduling

---

**Last updated**: February 28, 2026 | **YAWL v6.0.0**
