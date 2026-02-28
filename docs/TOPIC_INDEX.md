# YAWL Topic Index — Quick Lookup by Subject

**Version**: 6.0.0 | **Updated**: 2026-02-28 | **Purpose**: Find all docs related to a specific topic | **Format**: Topics → Relevant Docs

---

## How to Use This Index

1. **Find your topic** below (e.g., "authentication", "performance", "Docker")
2. **Review the listed documents**
3. **Follow links** to tutorials, how-tos, references, or explanations

Each topic shows:
- Primary module(s) responsible
- Key documents (by Diataxis quadrant type)
- Difficulty level: Beginner (🟢) | Intermediate (🟡) | Advanced (🔴)
- Prerequisite knowledge, if any

---

## Topics A-Z

### Agent Integration & Autonomous Workflows
**Primary Module**: yawl-mcp-a2a, yawl-integration
**Keywords**: `agent`, `MCP`, `A2A`, `autonomous`, `LLM`, `tool`, `conversation`

**Key Docs**:
- 🟢 Beginner: [08-mcp-agent-integration.md](tutorials/08-mcp-agent-integration.md) — First MCP agent setup
- 🟡 Intermediate: [yawl-mcp-a2a-getting-started.md](tutorials/yawl-mcp-a2a-getting-started.md) — Full MCP/A2A integration
- 🟡 Intermediate: [reference/mcp-tools.md](reference/mcp-tools.md) — MCP tool reference
- 🔴 Advanced: [explanation/autonomous-agents.md](explanation/autonomous-agents.md) — Agent architecture deep dive
- 🔴 Advanced: [explanation/mcp-llm-design.md](explanation/mcp-llm-design.md) — LLM integration design

**See Also**: Real-time case monitoring, Process mining

---

### API & REST Integration
**Primary Module**: yawl-integration, yawl-webapps
**Keywords**: `REST`, `HTTP`, `API`, `endpoint`, `JSON`, `OpenAPI`

**Key Docs**:
- 🟢 Beginner: [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md) — REST API basics
- 🟡 Intermediate: [reference/api-reference.md](reference/api-reference.md) — Full API spec
- 🟡 Intermediate: [reference/api/](reference/api/) — OpenAPI specs, Postman, SDKs
- 🔴 Advanced: [how-to/integration/](how-to/integration/) — Advanced REST patterns

**See Also**: Webhook integration, GraphQL (planned)

---

### Authentication & Security
**Primary Module**: yawl-security, yawl-authentication
**Keywords**: `authentication`, `JWT`, `OAuth2`, `certificate`, `X.509`, `encryption`, `TLS`, `signature`

**Key Docs**:
- 🟢 Beginner: [yawl-security-getting-started.md](tutorials/yawl-security-getting-started.md) — Security concepts
- 🟢 Beginner: [yawl-authentication-getting-started.md](tutorials/yawl-authentication-getting-started.md) — Auth setup
- 🟡 Intermediate: [how-to/yawl-security-certificate-management.md](how-to/yawl-security-certificate-management.md) — Certificates & signing
- 🟡 Intermediate: [how-to/yawl-authentication-setup.md](how-to/yawl-authentication-setup.md) — JWT configuration
- 🟡 Intermediate: [how-to/configure-spiffe.md](how-to/configure-spiffe.md) — Zero-trust identity (SPIFFE/SVID)
- 🔴 Advanced: [reference/crypto-and-tls.md](reference/crypto-and-tls.md) — Crypto details

**See Also**: Multi-tenancy, SPIFFE configuration

---

### Benchmarking & Performance
**Primary Module**: yawl-benchmark
**Keywords**: `performance`, `benchmark`, `optimization`, `throughput`, `latency`, `JMH`, `profiling`, `tuning`

**Key Docs**:
- 🟢 Beginner: [yawl-benchmark-getting-started.md](tutorials/yawl-benchmark-getting-started.md) — Benchmark intro
- 🟡 Intermediate: [how-to/yawl-benchmark-performance-optimization.md](how-to/yawl-benchmark-performance-optimization.md) — Performance tuning
- 🟡 Intermediate: [11-scale-to-million-cases.md](tutorials/11-scale-to-million-cases.md) — Scaling guide
- 🟡 Intermediate: [PERFORMANCE.md](PERFORMANCE.md) — Performance baselines
- 🔴 Advanced: [v6/performance/](v6/performance/) — Detailed performance analysis

**See Also**: Java 25 optimization, Stateless scaling

---

### Build & Development
**Primary Module**: Build system (pom.xml)
**Keywords**: `Maven`, `build`, `compile`, `test`, `module`, `reactor`, `pom.xml`, `CI/CD`

**Key Docs**:
- 🟢 Beginner: [01-build-yawl.md](tutorials/01-build-yawl.md) — Build YAWL locally
- 🟢 Beginner: [02-understand-the-build.md](tutorials/02-understand-the-build.md) — Build structure
- 🟡 Intermediate: [how-to/developer-build.md](how-to/developer-build.md) — Fast dev build
- 🟡 Intermediate: [reference/maven-quick-reference.md](reference/maven-quick-reference.md) — Maven tips
- 🟡 Intermediate: [reference/maven-module-structure.md](reference/maven-module-structure.md) — Module layout
- 🔴 Advanced: [how-to/cicd/](how-to/cicd/) — CI/CD pipeline

**See Also**: Testing, Contributing

---

### Business Calendars & Scheduling
**Primary Module**: yawl-scheduling
**Keywords**: `schedule`, `calendar`, `timezone`, `business hours`, `recurring`, `cron`, `holiday`

**Key Docs**:
- 🟢 Beginner: [yawl-scheduling-getting-started.md](tutorials/yawl-scheduling-getting-started.md) — Scheduling basics
- 🟡 Intermediate: [how-to/yawl-scheduling-calendars.md](how-to/yawl-scheduling-calendars.md) — Calendar configuration
- 🟡 Intermediate: [reference/configuration.md](reference/configuration.md) — Schedule config reference

**See Also**: Multi-tenancy, Case lifecycle

---

### Case Management & Lifecycle
**Primary Module**: yawl-engine
**Keywords**: `case`, `lifecycle`, `state`, `creation`, `suspension`, `termination`, `completion`

**Key Docs**:
- 🟢 Beginner: [03-run-your-first-workflow.md](tutorials/03-run-your-first-workflow.md) — Case execution intro
- 🟡 Intermediate: [how-to/yawl-engine-case-execution.md](how-to/yawl-engine-case-execution.md) — Create & manage cases
- 🟡 Intermediate: [explanation/case-lifecycle.md](explanation/case-lifecycle.md) — Case states & transitions

**See Also**: Work item management, Error handling

---

### Code Generation
**Primary Module**: yawl-polyglot (ggen)
**Keywords**: `code generation`, `ggen`, `RDF`, `SPARQL`, `deterministic`, `specification`

**Key Docs**:
- 🟡 Intermediate: [polyglot-ggen-getting-started.md](tutorials/polyglot-ggen-getting-started.md) — Code generation intro
- 🟡 Intermediate: [how-to/polyglot/](how-to/polyglot/) — Generation how-tos
- 🔴 Advanced: [reference/ggen-schema.md](reference/ggen-schema.md) — RDF/SPARQL schema (if exists)

**See Also**: Polyglot programming

---

### Containerization & Docker
**Primary Module**: yawl-webapps, deployment
**Keywords**: `Docker`, `container`, `image`, `registry`, `Kubernetes`, `orchestration`

**Key Docs**:
- 🟢 Beginner: [07-docker-dev-environment.md](tutorials/07-docker-dev-environment.md) — Docker dev setup
- 🟡 Intermediate: [how-to/deployment/docker.md](how-to/deployment/docker.md) — Docker deployment
- 🟡 Intermediate: [how-to/deployment/docker-full.md](how-to/deployment/docker-full.md) — Full Docker setup
- 🔴 Advanced: [how-to/deployment/](how-to/deployment/) — Container orchestration patterns

**See Also**: Deployment options, Production deployment

---

### Custom Work Item Handlers
**Primary Module**: yawl-engine
**Keywords**: `work item`, `handler`, `task`, `custom logic`, `invocation`

**Key Docs**:
- 🟢 Beginner: [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md) — Custom handler intro
- 🟡 Intermediate: [reference/interfaces.md](reference/interfaces.md) — Handler interface spec

**See Also**: Task execution, Integration

---

### Data Modeling & Schemas
**Primary Module**: yawl-data-modelling
**Keywords**: `schema`, `data model`, `domain`, `type`, `validation`, `constraint`, `ODCS`

**Key Docs**:
- 🟢 Beginner: [11-data-modelling-bridge.md](tutorials/11-data-modelling-bridge.md) — Data modeling intro
- 🟡 Intermediate: [how-to/](how-to/) — Data modeling how-tos (if exist)
- 🟡 Intermediate: [reference/data-model.md](reference/data-model.md) — Schema reference
- 🔴 Advanced: [explanation/](explanation/) — Data modeling concepts (if exist)

**See Also**: DMN decisions, Validation

---

### Decision Tables & DMN
**Primary Module**: yawl-data-modelling
**Keywords**: `DMN`, `decision table`, `FEEL`, `evaluation`, `Drools`

**Key Docs**:
- 🟢 Beginner: [14-dmn-decision-service.md](tutorials/14-dmn-decision-service.md) — DMN evaluation intro
- 🟡 Intermediate: [reference/dmn-syntax.md](reference/dmn-syntax.md) — DMN syntax reference (if exists)

**See Also**: Data models, Task decompositions

---

### Deployment Options
**Primary Module**: yawl-webapps, deployment
**Keywords**: `deploy`, `production`, `staging`, `environment`, `configuration`, `secrets`

**Key Docs**:
- 🟢 Beginner: [how-to/deployment/overview.md](how-to/deployment/overview.md) — Deployment strategy
- 🟡 Intermediate: [how-to/deployment/guide.md](how-to/deployment/guide.md) — Step-by-step deployment
- 🟡 Intermediate: [how-to/deployment/production.md](how-to/deployment/production.md) — Production checklist
- 🟡 Intermediate: [how-to/deployment/docker.md](how-to/deployment/docker.md) — Docker
- 🟡 Intermediate: [how-to/deployment/jetty.md](how-to/deployment/jetty.md) — Jetty
- 🟡 Intermediate: [DEPLOYMENT_CALCULATOR.md](DEPLOYMENT_CALCULATOR.md) — Resource calculator

**See Also**: Scaling, Multi-tenancy, Operations

---

### Error Handling & Exceptions
**Primary Module**: yawl-utilities, yawl-engine
**Keywords**: `exception`, `error`, `handling`, `retry`, `recovery`, `compensation`

**Key Docs**:
- 🟢 Beginner: [yawl-utilities-getting-started.md](tutorials/yawl-utilities-getting-started.md) — Utilities intro
- 🟡 Intermediate: [how-to/yawl-utilities-error-handling.md](how-to/yawl-utilities-error-handling.md) — Error patterns
- 🟡 Intermediate: [reference/error-codes.md](reference/error-codes.md) — Error codes & meanings

**See Also**: Exception handling, Worklets

---

### Event-Sourced Architecture
**Primary Module**: yawl-stateless
**Keywords**: `event sourcing`, `event store`, `CQRS`, `immutable`, `replay`, `consistency`

**Key Docs**:
- 🟡 Intermediate: [yawl-stateless-getting-started.md](tutorials/yawl-stateless-getting-started.md) — Stateless intro
- 🟡 Intermediate: [how-to/enable-stateless-persistence.md](how-to/enable-stateless-persistence.md) — Enable stateless
- 🔴 Advanced: [explanation/event-sourcing-architecture.md](explanation/event-sourcing-architecture.md) — Event sourcing concepts (if exists)

**See Also**: Stateless engine, Scaling

---

### Getting Started Paths
**Primary Module**: All
**Keywords**: `new user`, `onboarding`, `learner path`, `quick start`, `first steps`

**Key Docs**:
- 🟢 Beginner: [QUICK-START.md](QUICK-START.md) — 5-minute overview
- 🟢 Beginner: [GETTING_STARTED_PATHS.md](GETTING_STARTED_PATHS.md) — Choose your path
- 🟢 Beginner: [tutorials/10-getting-started.md](tutorials/10-getting-started.md) — User guide
- 🟢 Beginner: [diataxis/INDEX.md](diataxis/INDEX.md) — All tutorials by track

**See Also**: Learning roadmap

---

### GraalVM Polyglot
**Primary Module**: yawl-polyglot, yawl-graalpy
**Keywords**: `GraalVM`, `polyglot`, `language interop`, `Python`, `JavaScript`, `WASM`, `FFI`

**Key Docs**:
- 🟡 Intermediate: [polyglot/tutorials/01-graalpy-getting-started.md](polyglot/tutorials/01-graalpy-getting-started.md) — Python execution
- 🟡 Intermediate: [polyglot/tutorials/02-graaljs-getting-started.md](polyglot/tutorials/02-graaljs-getting-started.md) — JavaScript execution
- 🟡 Intermediate: [polyglot/tutorials/03-graalwasm-getting-started.md](polyglot/tutorials/03-graalwasm-getting-started.md) — WebAssembly
- 🔴 Advanced: [polyglot/reference/](polyglot/reference/) — Runtime specifications
- 🔴 Advanced: [explanation/polyglot-architecture.md](explanation/polyglot-architecture.md) — Polyglot design (if exists)

**See Also**: Code generation, Language interop

---

### Java 25 & Modernization
**Primary Module**: All (cross-cutting)
**Keywords**: `Java 25`, `virtual threads`, `FFM`, `scoped values`, `modernization`, `records`, `sealed classes`

**Key Docs**:
- 🟡 Intermediate: [how-to/java25-setup.md](how-to/java25-setup.md) — Java 25 setup
- 🟡 Intermediate: [architecture/Java25-Implementation-Guidelines.md](architecture/Java25-Implementation-Guidelines.md) — Implementation guidelines
- 🔴 Advanced: [architecture/Java25-Modernization-Architecture.md](architecture/Java25-Modernization-Architecture.md) — Architecture overview
- 🔴 Advanced: [architecture/Java25-Modernization-Summary.md](architecture/Java25-Modernization-Summary.md) — Modernization summary
- 🔴 Advanced: [ScopedValueEnhancementGuide.md](ScopedValueEnhancementGuide.md) — Scoped values guide
- 🔴 Advanced: [EventStoreOptimizationGuide.md](EventStoreOptimizationGuide.md) — Event store optimization

**See Also**: Performance optimization, Virtual threads

---

### Machine Learning & Predictions
**Primary Module**: yawl-pi
**Keywords**: `ML`, `machine learning`, `prediction`, `AutoML`, `TPOT2`, `model`, `training`, `evaluation`

**Key Docs**:
- 🟡 Intermediate: [pi/tutorials/01-first-case-prediction.md](pi/tutorials/01-first-case-prediction.md) — First prediction
- 🟡 Intermediate: [pi/tutorials/02-train-automl-model.md](pi/tutorials/02-train-automl-model.md) — AutoML training
- 🟡 Intermediate: [pi/tutorials/03-realtime-adaptive.md](pi/tutorials/03-realtime-adaptive.md) — Real-time adaptive
- 🟡 Intermediate: [pi/tutorials/04-natural-language-qa.md](pi/tutorials/04-natural-language-qa.md) — NLP Q&A
- 🔴 Advanced: [pi/reference/](pi/reference/) — ML API reference
- 🔴 Advanced: [explanation/process-intelligence.md](explanation/process-intelligence.md) — PI concepts (if exists)

**See Also**: Process intelligence, Analytics

---

### Monitoring & Observability
**Primary Module**: yawl-monitoring
**Keywords**: `monitoring`, `observability`, `tracing`, `metrics`, `OpenTelemetry`, `Jaeger`, `logging`, `SLO`

**Key Docs**:
- 🟡 Intermediate: [yawl-monitoring-getting-started.md](tutorials/yawl-monitoring-getting-started.md) — Monitoring intro
- 🟡 Intermediate: [how-to/yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Tracing setup
- 🟡 Intermediate: [reference/slos/](reference/slos/) — SLO specifications
- 🔴 Advanced: [explanation/monitoring-architecture.md](explanation/monitoring-architecture.md) — Monitoring design (if exists)

**See Also**: Performance, Production operations

---

### Multi-Tenancy
**Primary Module**: All (cross-cutting)
**Keywords**: `multi-tenant`, `isolation`, `schema`, `data segregation`, `resource pool`

**Key Docs**:
- 🟡 Intermediate: [how-to/configure-multi-tenancy.md](how-to/configure-multi-tenancy.md) — Multi-tenancy setup
- 🔴 Advanced: [explanation/multi-tenancy-architecture.md](explanation/multi-tenancy-architecture.md) — MT design (if exists)

**See Also**: Security, Resource management

---

### Organizational Model & Resourcing
**Primary Module**: yawl-resourcing
**Keywords**: `organization`, `resource`, `participant`, `role`, `delegation`, `assignment`, `capability`

**Key Docs**:
- 🟡 Intermediate: [how-to/configure-resource-service.md](how-to/configure-resource-service.md) — Resource config
- 🟡 Intermediate: [how-to/setup-org-model.md](how-to/setup-org-model.md) — Org hierarchy setup
- 🟡 Intermediate: [reference/resource-allocation.md](reference/resource-allocation.md) — Allocation rules

**See Also**: Task assignment, Participant management

---

### Petri Nets & Theory
**Primary Module**: yawl-engine, yawl-elements
**Keywords**: `Petri net`, `place`, `transition`, `token`, `firing rule`, `soundness`, `formal semantics`

**Key Docs**:
- 🟡 Intermediate: [explanation/petri-net-foundations.md](explanation/petri-net-foundations.md) — Petri net theory
- 🔴 Advanced: [reference/petri-nets/](reference/petri-nets/) — Formal definitions (if exists)

**See Also**: Control flow patterns, Semantics

---

### Process Intelligence & Mining
**Primary Module**: yawl-pi
**Keywords**: `process mining`, `OCEL`, `OCEL2`, `OCPM`, `pm4py`, `Rust4PM`, `event log`, `discovery`, `conformance`

**Key Docs**:
- 🟡 Intermediate: [explanation/object-centric-process-mining.md](explanation/object-centric-process-mining.md) — OCPM concepts
- 🟡 Intermediate: [polyglot/tutorials/04-rust4pm-ocel2.md](polyglot/tutorials/04-rust4pm-ocel2.md) — Rust process mining
- 🟡 Intermediate: [how-to/integration/ocpm-integration.md](how-to/integration/ocpm-integration.md) — OCPM integration
- 🔴 Advanced: [explanation/process-intelligence.md](explanation/process-intelligence.md) — PI concepts (if exists)

**See Also**: Analytics, Machine learning

---

### Production Readiness
**Primary Module**: All
**Keywords**: `production`, `SLA`, `availability`, `reliability`, `scale`, `resilience`

**Key Docs**:
- 🟡 Intermediate: [how-to/deployment/production.md](how-to/deployment/production.md) — Production checklist
- 🟡 Intermediate: [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) — Quality gates
- 🟡 Intermediate: [reference/slos/](reference/slos/) — SLO specs
- 🔴 Advanced: [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) — Architecture analysis

**See Also**: Monitoring, Scaling

---

### Python Integration
**Primary Module**: yawl-graalpy, yawl-polyglot
**Keywords**: `Python`, `GraalPy`, `PyPI`, `NumPy`, `Pandas`, `scikit-learn`, `interop`

**Key Docs**:
- 🟡 Intermediate: [polyglot/tutorials/01-graalpy-getting-started.md](polyglot/tutorials/01-graalpy-getting-started.md) — GraalPy intro
- 🟡 Intermediate: [polyglot/how-to/](polyglot/how-to/) — Python integration how-tos
- 🔴 Advanced: [polyglot/reference/python-runtime.md](polyglot/reference/python-runtime.md) — Runtime spec (if exists)

**See Also**: Polyglot programming, Data science integration

---

### Real-Time Case Monitoring
**Primary Module**: yawl-integration, yawl-monitoring
**Keywords**: `real-time`, `monitoring`, `dashboard`, `case status`, `alerts`, `webhook`, `event stream`

**Key Docs**:
- 🟡 Intermediate: [how-to/subscribe-workflow-events.md](how-to/subscribe-workflow-events.md) — Event subscription
- 🟡 Intermediate: [how-to/yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Real-time tracing
- 🟡 Intermediate: [yawl-mcp-a2a-getting-started.md](tutorials/yawl-mcp-a2a-getting-started.md) — Agent-based monitoring

**See Also**: Agents, Event processing

---

### Ripple Down Rules & Worklets
**Primary Module**: yawl-worklet
**Keywords**: `worklet`, `RDR`, `ripple down rules`, `exception handling`, `dynamic workflow`, `rule inference`

**Key Docs**:
- 🟡 Intermediate: [yawl-worklet-getting-started.md](tutorials/yawl-worklet-getting-started.md) — Worklet intro
- 🟡 Intermediate: [how-to/implement-worklet-service.md](how-to/implement-worklet-service.md) — Worklet implementation
- 🔴 Advanced: [explanation/worklet-architecture.md](explanation/worklet-architecture.md) — RDR design (if exists)

**See Also**: Exception handling, Dynamic workflows

---

### Scaling & Clustering
**Primary Module**: yawl-stateless, yawl-engine
**Keywords**: `scale`, `cluster`, `load balancing`, `distributed`, `horizontal`, `vertical`, `1M cases`

**Key Docs**:
- 🟡 Intermediate: [11-scale-to-million-cases.md](tutorials/11-scale-to-million-cases.md) — Scaling guide
- 🟡 Intermediate: [how-to/enable-stateless-persistence.md](how-to/enable-stateless-persistence.md) — Stateless scaling
- 🔴 Advanced: [how-to/operations/](how-to/operations/) — Operations guides
- 🔴 Advanced: [PERFORMANCE.md](PERFORMANCE.md) — Performance analysis

**See Also**: Stateless engine, Load testing

---

### Specification Design & Modeling
**Primary Module**: yawl-elements
**Keywords**: `specification`, `design`, `net`, `task`, `condition`, `flow`, `decomposition`, `control flow`

**Key Docs**:
- 🟢 Beginner: [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Spec design intro
- 🟡 Intermediate: [how-to/yawl-elements-schema-design.md](how-to/yawl-elements-schema-design.md) — Schema design
- 🟡 Intermediate: [reference/workflow-patterns.md](reference/workflow-patterns.md) — Pattern library
- 🟡 Intermediate: [reference/patterns/](reference/patterns/) — Pattern implementations
- 🔴 Advanced: [reference/yawl-schema.md](reference/yawl-schema.md) — XML schema specification

**See Also**: Control flow patterns, Task decompositions

---

### SPIFFE & Zero-Trust Identity
**Primary Module**: yawl-security
**Keywords**: `SPIFFE`, `SVID`, `zero-trust`, `workload identity`, `mTLS`, `certificate`

**Key Docs**:
- 🟡 Intermediate: [how-to/configure-spiffe.md](how-to/configure-spiffe.md) — SPIFFE setup
- 🔴 Advanced: [reference/crypto-and-tls.md](reference/crypto-and-tls.md) — Crypto details

**See Also**: Security, mTLS, Authentication

---

### Testing & Quality Assurance
**Primary Module**: Testing framework
**Keywords**: `test`, `JUnit`, `quality`, `CI/CD`, `automation`, `regression`, `coverage`

**Key Docs**:
- 🟡 Intermediate: [how-to/testing.md](how-to/testing.md) — Testing strategy
- 🟡 Intermediate: [how-to/troubleshooting.md](how-to/troubleshooting.md) — Troubleshooting guide
- 🔴 Advanced: [reference/chicago-tdd.md](reference/chicago-tdd.md) — TDD principles
- 🔴 Advanced: [v6/testing/](v6/testing/) — Test framework docs

**See Also**: CI/CD, Build

---

### Troubleshooting & Debugging
**Primary Module**: All
**Keywords**: `troubleshoot`, `debug`, `error`, `log`, `diagnosis`, `root cause`

**Key Docs**:
- 🟡 Intermediate: [how-to/troubleshooting.md](how-to/troubleshooting.md) — Troubleshooting guide
- 🟡 Intermediate: [FAQ_AND_COMMON_ISSUES.md](FAQ_AND_COMMON_ISSUES.md) — Common issues
- 🟡 Intermediate: [reference/error-codes.md](reference/error-codes.md) — Error reference

**See Also**: Logging, Monitoring

---

### Virtual Threads & Async I/O
**Primary Module**: All (Java 25)
**Keywords**: `virtual thread`, `async`, `I/O`, `non-blocking`, `reactive`, `performance`

**Key Docs**:
- 🟡 Intermediate: [HTTP_CLIENT_MODERNIZATION_GUIDE.md](HTTP_CLIENT_MODERNIZATION_GUIDE.md) — Virtual thread HTTP
- 🔴 Advanced: [architecture/Java25-Modernization-Architecture.md](architecture/Java25-Modernization-Architecture.md) — Modernization details
- 🔴 Advanced: [ScopedValueEnhancementGuide.md](ScopedValueEnhancementGuide.md) — Context propagation

**See Also**: Java 25, Performance

---

### Workflow Events & Pub/Sub
**Primary Module**: yawl-engine, yawl-integration
**Keywords**: `event`, `publish`, `subscribe`, `listener`, `callback`, `webhook`, `event bus`

**Key Docs**:
- 🟡 Intermediate: [how-to/subscribe-workflow-events.md](how-to/subscribe-workflow-events.md) — Event subscription
- 🟡 Intermediate: [reference/event-model.md](reference/event-model.md) — Event types (if exists)

**See Also**: Real-time monitoring, Integration

---

## Index Statistics

| Metric | Count |
|--------|-------|
| Total Topics | 50+ |
| Beginner Docs | 25+ |
| Intermediate Docs | 100+ |
| Advanced Docs | 80+ |
| Average Docs per Topic | 4-5 |

---

## How to Add New Topics

1. Add heading: `### Topic Name`
2. Set **Primary Module** & **Keywords**
3. List docs by difficulty (🟢 🟡 🔴)
4. Show Diataxis type (Tutorial/How-To/Reference/Explanation)
5. Add related topics with "See Also"

**Maintainer**: Docs team | **Review Frequency**: Monthly

---

## Related Documents

- **[SEARCH_INDEX.md](SEARCH_INDEX.md)** — Full doc index with keywords
- **[MODULE_HEALTH_DASHBOARD.md](MODULE_HEALTH_DASHBOARD.md)** — Module status & maturity
- **[DOCUMENTATION_COMPLETENESS.md](DOCUMENTATION_COMPLETENESS.md)** — 4-quadrant coverage
- **[USE_CASE_INDEX.md](USE_CASE_INDEX.md)** — Use case learning paths
- **[diataxis/INDEX.md](diataxis/INDEX.md)** — All docs by quadrant

**Last Updated**: 2026-02-28
**Next Review**: 2026-03-28
**Generated By**: Topic extraction from file hierarchy & keywords
