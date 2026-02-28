# YAWL v6 Documentation Search Index

**Version**: 6.0.0 | **Generated**: 2026-02-28 | **Total Docs**: 492 | **Last Updated**: Automated
**Purpose**: Searchable keyword index of all YAWL documentation for rapid discovery.

---

## How to Use This Index

1. **Find by Topic**: Scroll to "TOPIC INDEX" section below to find all docs related to your interest (e.g., "security", "deployment", "testing")
2. **Find by Module**: Use "MODULE DOCUMENTATION MAP" to see all docs for a specific YAWL module
3. **Find by Quadrant**: Use "QUADRANT COVERAGE" to find docs by Diataxis type (tutorial, how-to, reference, explanation)
4. **Quick Search**: Use browser Ctrl+F to search page text for keywords

---

## Topic Index

### Authentication & Security
- **Keywords**: `authentication`, `certificate`, `X.509`, `signature`, `encryption`, `SPIFFE`, `TLS`, `JWT`
- **Primary Module**: yawl-security, yawl-authentication
- **Key Docs**:
  - [yawl-security-certificate-management.md](how-to/yawl-security-certificate-management.md) — Generate & validate certificates
  - [configure-spiffe.md](how-to/configure-spiffe.md) — SPIFFE/SVID zero-trust identity
  - [yawl-authentication-setup.md](how-to/yawl-authentication-setup.md) — JWT configuration
  - [authentication/](reference/authentication/) — Reference docs

### Core Engine & Execution
- **Keywords**: `YEngine`, `YNetRunner`, `case`, `Petri net`, `execution`, `task`, `condition`, `work item`
- **Primary Module**: yawl-engine, yawl-elements
- **Key Docs**:
  - [yawl-engine-getting-started.md](tutorials/yawl-engine-getting-started.md) — Engine basics
  - [yawl-engine-case-execution.md](how-to/yawl-engine-case-execution.md) — Create & execute cases
  - [petri-net-foundations.md](explanation/petri-net-foundations.md) — Petri net concepts
  - [case-lifecycle.md](explanation/case-lifecycle.md) — Case state model
  - [or-join-semantics.md](explanation/or-join-semantics.md) — Complex join semantics

### Deployment & Containerization
- **Keywords**: `Docker`, `Jetty`, `Tomcat`, `WildFly`, `Kubernetes`, `cloud`, `container`, `marketplace`
- **Primary Module**: yawl-webapps, deployment
- **Key Docs**:
  - [deployment/docker.md](how-to/deployment/docker.md) — Docker deployment
  - [deployment/docker-full.md](how-to/deployment/docker-full.md) — Full Docker setup
  - [deployment/jetty.md](how-to/deployment/jetty.md) — Embedded Jetty
  - [09-marketplace-quick-start.md](tutorials/09-marketplace-quick-start.md) — Cloud deployment
  - [DEPLOYMENT_CALCULATOR.md](DEPLOYMENT_CALCULATOR.md) — Cost calculator

### Data Modeling & Schemas
- **Keywords**: `schema`, `data model`, `DMN`, `decision table`, `ODCS`, `XML`, `validation`
- **Primary Module**: yawl-data-modelling
- **Key Docs**:
  - [11-data-modelling-bridge.md](tutorials/11-data-modelling-bridge.md) — Data modeling intro
  - [14-dmn-decision-service.md](tutorials/14-dmn-decision-service.md) — DMN decisions
  - [reference/data-model.md](reference/data-model.md) — Schema reference
  - [reference/yawl-schema.md](reference/yawl-schema.md) — YAWL XML schema

### Integration & APIs
- **Keywords**: `REST API`, `HTTP`, `MCP`, `A2A`, `integration`, `agent`, `interface`, `webhook`
- **Primary Module**: yawl-integration, yawl-mcp-a2a
- **Key Docs**:
  - [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md) — REST API basics
  - [08-mcp-agent-integration.md](tutorials/08-mcp-agent-integration.md) — MCP agent setup
  - [yawl-mcp-a2a-getting-started.md](tutorials/yawl-mcp-a2a-getting-started.md) — MCP/A2A integration
  - [reference/api-reference.md](reference/api-reference.md) — API spec
  - [reference/mcp-tools.md](reference/mcp-tools.md) — MCP tool reference

### Monitoring & Observability
- **Keywords**: `OpenTelemetry`, `metrics`, `tracing`, `logging`, `Jaeger`, `observability`, `SLO`
- **Primary Module**: yawl-monitoring
- **Key Docs**:
  - [yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Distributed tracing
  - [reference/slos/](reference/slos/) — SLO definitions
  - [v6/performance/](v6/performance/) — Performance metrics

### Polyglot & Code Execution
- **Keywords**: `Python`, `JavaScript`, `GraalPy`, `GraalJS`, `WASM`, `WebAssembly`, `polyglot`
- **Primary Module**: yawl-polyglot, yawl-graalpy
- **Key Docs**:
  - [polyglot/tutorials/01-graalpy-getting-started.md](polyglot/tutorials/01-graalpy-getting-started.md) — Python execution
  - [polyglot/tutorials/02-graaljs-getting-started.md](polyglot/tutorials/02-graaljs-getting-started.md) — JavaScript execution
  - [polyglot/tutorials/03-graalwasm-getting-started.md](polyglot/tutorials/03-graalwasm-getting-started.md) — WebAssembly
  - [polyglot-ggen-getting-started.md](tutorials/polyglot-ggen-getting-started.md) — Code generation

### Process Intelligence & Mining
- **Keywords**: `process mining`, `OCEL`, `pm4py`, `Rust4PM`, `process intelligence`, `prediction`
- **Primary Module**: yawl-pi
- **Key Docs**:
  - [pi/tutorials/01-first-case-prediction.md](pi/tutorials/01-first-case-prediction.md) — Predictive models
  - [pi/tutorials/02-train-automl-model.md](pi/tutorials/02-train-automl-model.md) — AutoML training
  - [explanation/object-centric-process-mining.md](explanation/object-centric-process-mining.md) — OCPM concepts
  - [polyglot/tutorials/04-rust4pm-ocel2.md](polyglot/tutorials/04-rust4pm-ocel2.md) — Rust process mining

### Workflow Patterns & Control Flow
- **Keywords**: `pattern`, `control flow`, `decomposition`, `join`, `split`, `worklet`, `synchronization`
- **Primary Modules**: yawl-elements, yawl-worklet
- **Key Docs**:
  - [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Spec design
  - [reference/workflow-patterns.md](reference/workflow-patterns.md) — Pattern catalog
  - [reference/patterns/](reference/patterns/) — Pattern implementations
  - [how-to/implement-worklet-service.md](how-to/implement-worklet-service.md) — Worklets

### Build & Development
- **Keywords**: `Maven`, `pom.xml`, `compile`, `build`, `module`, `reactor`, `gradle`, `java`
- **Primary Module**: Build system
- **Key Docs**:
  - [02-understand-the-build.md](tutorials/02-understand-the-build.md) — Build structure
  - [reference/maven-quick-reference.md](reference/maven-quick-reference.md) — Maven tips
  - [reference/maven-module-structure.md](reference/maven-module-structure.md) — Module layout
  - [how-to/developer-build.md](how-to/developer-build.md) — Dev build workflow

### CI/CD & DevOps
- **Keywords**: `CI/CD`, `GitHub Actions`, `testing`, `quality gate`, `release`, `pipeline`
- **Primary Module**: DevOps, testing
- **Key Docs**:
  - [how-to/cicd/](how-to/cicd/) — CI/CD guides
  - [how-to/testing.md](how-to/testing.md) — Testing strategy
  - [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) — Quality gates

### Performance & Benchmarking
- **Keywords**: `performance`, `benchmark`, `optimization`, `tuning`, `throughput`, `latency`, `JMH`
- **Primary Module**: yawl-benchmark
- **Key Docs**:
  - [yawl-benchmark-performance-optimization.md](how-to/yawl-benchmark-performance-optimization.md) — Optimization
  - [11-scale-to-million-cases.md](tutorials/11-scale-to-million-cases.md) — Scaling guide
  - [PERFORMANCE.md](PERFORMANCE.md) — Performance metrics
  - [v6/performance/](v6/performance/) — Performance analysis

### Resource Management & Resourcing
- **Keywords**: `resource`, `participant`, `role`, `organizational model`, `assignment`, `allocation`
- **Primary Module**: yawl-resourcing
- **Key Docs**:
  - [how-to/configure-resource-service.md](how-to/configure-resource-service.md) — Org model
  - [how-to/setup-org-model.md](how-to/setup-org-model.md) — Org hierarchy
  - [reference/resource-allocation.md](reference/resource-allocation.md) — Allocation rules

### Scheduling & Calendar
- **Keywords**: `schedule`, `calendar`, `timezone`, `business hours`, `recurring`, `cron`
- **Primary Module**: yawl-scheduling
- **Key Docs**:
  - [yawl-scheduling-calendars.md](how-to/yawl-scheduling-calendars.md) — Calendar config
  - [yawl-scheduling-getting-started.md](tutorials/yawl-scheduling-getting-started.md) — Scheduling basics

### Testing & Quality
- **Keywords**: `test`, `JUnit`, `quality`, `validation`, `coverage`, `mutation testing`
- **Primary Module**: Testing framework
- **Key Docs**:
  - [how-to/testing.md](how-to/testing.md) — Testing strategy
  - [chicago-tdd.md](reference/chicago-tdd.md) — TDD principles
  - [v6/testing/](v6/testing/) — Test framework docs

### Java 25 & Modernization
- **Keywords**: `Java 25`, `virtual threads`, `FFM`, `scoped values`, `modernization`, `migration`
- **Primary Module**: Java25 effort
- **Key Docs**:
  - [architecture/Java25-Implementation-Guidelines.md](architecture/Java25-Implementation-Guidelines.md) — Guidelines
  - [architecture/Java25-Modernization-Architecture.md](architecture/Java25-Modernization-Architecture.md) — Architecture
  - [how-to/java25-setup.md](how-to/java25-setup.md) — Setup

### Stateless & Event-Sourced Architecture
- **Keywords**: `stateless`, `event sourcing`, `persistence`, `state`, `immutable`
- **Primary Module**: yawl-stateless
- **Key Docs**:
  - [yawl-stateless-getting-started.md](tutorials/yawl-stateless-getting-started.md) — Stateless intro
  - [how-to/enable-stateless-persistence.md](how-to/enable-stateless-persistence.md) — Enable stateless

### Utilities & Error Handling
- **Keywords**: `utility`, `exception`, `error handling`, `validation`, `retry`, `unmarshalling`
- **Primary Module**: yawl-utilities
- **Key Docs**:
  - [yawl-utilities-error-handling.md](how-to/yawl-utilities-error-handling.md) — Error patterns
  - [yawl-utilities-getting-started.md](tutorials/yawl-utilities-getting-started.md) — Utilities intro
  - [reference/error-codes.md](reference/error-codes.md) — Error reference

---

## Module Documentation Map

| Module | #Docs | Quadrants | Status | Key Docs |
|--------|-------|-----------|--------|----------|
| **yawl-engine** | 8+ | T, H, R, E | Stable | [getting-started](tutorials/yawl-engine-getting-started.md) · [case-execution](how-to/yawl-engine-case-execution.md) · [api](reference/api/) |
| **yawl-elements** | 8+ | T, H, R, E | Stable | [getting-started](tutorials/yawl-elements-getting-started.md) · [schema-design](how-to/yawl-elements-schema-design.md) · [patterns](reference/patterns/) |
| **yawl-security** | 6+ | H, R, E | Stable | [certificate-mgmt](how-to/yawl-security-certificate-management.md) · [spiffe](how-to/configure-spiffe.md) · [auth-setup](how-to/yawl-authentication-setup.md) |
| **yawl-authentication** | 4+ | H, R | Stable | [jwt-setup](how-to/yawl-authentication-setup.md) · [reference](reference/authentication/) |
| **yawl-integration** | 6+ | T, H, R, E | Stable | [mcp-agent](tutorials/08-mcp-agent-integration.md) · [rest-api](tutorials/05-call-yawl-rest-api.md) · [api-ref](reference/api-reference.md) |
| **yawl-mcp-a2a** | 4+ | T, H, R | Beta | [getting-started](tutorials/yawl-mcp-a2a-getting-started.md) · [mcp-tools](reference/mcp-tools.md) |
| **yawl-monitoring** | 5+ | T, H, R | Beta | [tracing-setup](how-to/yawl-monitoring-tracing.md) · [getting-started](tutorials/yawl-monitoring-getting-started.md) |
| **yawl-resourcing** | 4+ | H, R, E | Stable | [resource-config](how-to/configure-resource-service.md) · [org-model](how-to/setup-org-model.md) |
| **yawl-scheduling** | 3+ | T, H, R | Stable | [calendar-config](how-to/yawl-scheduling-calendars.md) · [getting-started](tutorials/yawl-scheduling-getting-started.md) |
| **yawl-data-modelling** | 5+ | T, H, R, E | Beta | [data-modelling](tutorials/11-data-modelling-bridge.md) · [dmn-decisions](tutorials/14-dmn-decision-service.md) |
| **yawl-polyglot** | 4+ | T, H, R | Beta | [graalpy](polyglot/tutorials/01-graalpy-getting-started.md) · [graaljs](polyglot/tutorials/02-graaljs-getting-started.md) · [wasm](polyglot/tutorials/03-graalwasm-getting-started.md) |
| **yawl-pi** | 4+ | T, H, R, E | Alpha | [prediction](pi/tutorials/01-first-case-prediction.md) · [automl](pi/tutorials/02-train-automl-model.md) · [adaptive](pi/tutorials/03-realtime-adaptive.md) |
| **yawl-worklet** | 3+ | H, R, E | Stable | [worklet-service](how-to/implement-worklet-service.md) · [getting-started](tutorials/yawl-worklet-getting-started.md) |
| **yawl-benchmark** | 4+ | T, H, R | Stable | [optimization](how-to/yawl-benchmark-performance-optimization.md) · [getting-started](tutorials/yawl-benchmark-getting-started.md) |
| **yawl-utilities** | 5+ | T, H, R | Stable | [error-handling](how-to/yawl-utilities-error-handling.md) · [getting-started](tutorials/yawl-utilities-getting-started.md) |
| **yawl-stateless** | 3+ | T, H, R | Beta | [getting-started](tutorials/yawl-stateless-getting-started.md) · [persistence](how-to/enable-stateless-persistence.md) |
| **yawl-webapps** | 5+ | T, H, R, E | Stable | [docker](how-to/deployment/docker.md) · [jetty](how-to/deployment/jetty.md) · [deployment](how-to/deployment/overview.md) |

**Legend**: T=Tutorial, H=How-To, R=Reference, E=Explanation | Status = Alpha/Beta/Stable

---

## Quadrant Coverage

### Tutorials (Learning by Doing)
**Purpose**: Step-by-step guided journeys from start to accomplishment
**Count**: 50+ | **Best for**: New users, onboarding, hands-on learning

#### Getting Started Track
- 01-build-yawl.md
- 02-understand-the-build.md
- 03-run-your-first-workflow.md
- 04-write-a-yawl-specification.md
- 05-call-yawl-rest-api.md

#### Foundation Modules (5 Core)
- yawl-engine-getting-started.md
- yawl-elements-getting-started.md
- yawl-utilities-getting-started.md
- yawl-security-getting-started.md
- yawl-benchmark-getting-started.md

#### Service Modules (4)
- yawl-authentication-getting-started.md
- yawl-scheduling-getting-started.md
- yawl-monitoring-getting-started.md
- yawl-worklet-getting-started.md

#### Advanced & Integration
- 06-write-a-custom-work-item-handler.md
- 07-docker-dev-environment.md
- 08-mcp-agent-integration.md
- 09-marketplace-quick-start.md
- 10-getting-started.md (user guide)
- 11-data-modelling-bridge.md
- 11-scale-to-million-cases.md
- 12-fix-hyper-standards-violation.md
- 14-dmn-decision-service.md

#### Deployment Modules (3)
- yawl-stateless-getting-started.md
- yawl-webapps-getting-started.md
- yawl-control-panel-getting-started.md
- yawl-mcp-a2a-getting-started.md

#### Polyglot & PI
- polyglot/tutorials/01-graalpy-getting-started.md
- polyglot/tutorials/02-graaljs-getting-started.md
- polyglot/tutorials/03-graalwasm-getting-started.md
- polyglot/tutorials/04-rust4pm-ocel2.md
- pi/tutorials/01-first-case-prediction.md
- pi/tutorials/02-train-automl-model.md
- pi/tutorials/03-realtime-adaptive.md
- pi/tutorials/04-natural-language-qa.md

### How-To Guides (Task-Focused)
**Purpose**: Accomplish a specific goal with step-by-step directions
**Count**: 80+ | **Best for**: Practitioners with a concrete task

#### Configuration & Setup
- developer-guide.md
- developer-build.md
- development.md
- java25-setup.md
- contributing.md

#### Core Module How-Tos
- yawl-engine-case-execution.md
- yawl-elements-schema-design.md
- yawl-utilities-error-handling.md
- yawl-security-certificate-management.md
- yawl-benchmark-performance-optimization.md

#### Service Configuration
- yawl-authentication-setup.md
- yawl-scheduling-calendars.md
- yawl-monitoring-tracing.md
- configure-resource-service.md
- setup-org-model.md
- implement-worklet-service.md

#### Deployment (6 variants)
- deployment/overview.md
- deployment/docker.md
- deployment/docker-full.md
- deployment/jetty.md
- deployment/tomcat.md
- deployment/wildfly.md

#### Integration & Advanced
- configure-multi-tenancy.md
- configure-spiffe.md
- enable-stateless-persistence.md
- subscribe-workflow-events.md
- validate-spec.md
- ocpm-integration.md

#### Polyglot & PI
- polyglot/how-to/ (multiple guides)
- pi/how-to/ (multiple guides)

### Reference (Technical Details)
**Purpose**: Look up facts, APIs, configurations, patterns
**Count**: 120+ | **Best for**: Implementation, troubleshooting, API lookups

#### Configuration & Schema
- configuration.md
- error-codes.md
- yawl-schema.md

#### Interfaces & APIs
- interface-b.md
- interface-e.md
- interface-x.md
- api-reference.md
- api/ (OpenAPI, Postman, SDKs)
- mcp-tools.md
- mcp-process-mining-tools.md

#### Patterns & Control Flow
- workflow-patterns.md
- patterns/ (implementation details)

#### Build & Maven
- maven-quick-reference.md
- maven-module-structure.md

#### Quality & SLOs
- reference/quality/
- reference/slos/
- reference/templates/

#### Polyglot & PI
- polyglot/reference/
- pi/reference/

### Explanation (Understanding Concepts)
**Purpose**: Deep understanding of why, context, history
**Count**: 80+ | **Best for**: Architects, designers, context seekers

#### Core Concepts
- petri-net-foundations.md
- case-lifecycle.md
- or-join-semantics.md

#### Architecture
- dual-engine-architecture.md
- interface-architecture.md
- shared-src-build-strategy.md

#### Integration & AI
- autonomous-agents.md
- mcp-llm-design.md

#### Process Intelligence
- object-centric-process-mining.md
- process-intelligence.md

#### Decisions (ADRs)
- decisions/ (30+ Architecture Decision Records)

---

## Search by Audience

### New Developer Path
1. [QUICK-START.md](QUICK-START.md) — 5-minute overview
2. [tutorials/01-build-yawl.md](tutorials/01-build-yawl.md) — Build locally
3. [tutorials/02-understand-the-build.md](tutorials/02-understand-the-build.md) — Module structure
4. [tutorials/03-run-your-first-workflow.md](tutorials/03-run-your-first-workflow.md) — Run example
5. [how-to/developer-guide.md](how-to/developer-guide.md) — Setup development

### Contributor Path
1. [how-to/contributing.md](how-to/contributing.md) — Contribution guide
2. [how-to/developer-build.md](how-to/developer-build.md) — Fast build workflow
3. [how-to/testing.md](how-to/testing.md) — Testing strategy
4. [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) — Quality gates

### Operations/DevOps Path
1. [how-to/deployment/overview.md](how-to/deployment/overview.md) — Deployment options
2. [how-to/deployment/docker.md](how-to/deployment/docker.md) — Docker deployment
3. [how-to/deployment/production.md](how-to/deployment/production.md) — Production checklist
4. [how-to/operations/](how-to/operations/) — Operations guides
5. [reference/slos/](reference/slos/) — SLO reference

### Architect Path
1. [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) — Engine design
2. [explanation/decisions/](explanation/decisions/) — 30+ ADRs
3. [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) — Full analysis
4. [reference/configuration.md](reference/configuration.md) — Config reference
5. [explanation/interface-architecture.md](explanation/interface-architecture.md) — Interface design

### End User Path
1. [tutorials/10-getting-started.md](tutorials/10-getting-started.md) — User walkthrough
2. [QUICK-START.md](QUICK-START.md) — Quick intro
3. [how-to/deployment/overview.md](how-to/deployment/overview.md) — Where to deploy
4. [reference/api-reference.md](reference/api-reference.md) — API docs
5. [FAQ_AND_COMMON_ISSUES.md](FAQ_AND_COMMON_ISSUES.md) — Common Q&A

---

## Using This Index

### Programmatic Access
A JSON version of this index exists at [SEARCH_INDEX.json](SEARCH_INDEX.json) for tool integration, search engines, and documentation portals.

```bash
# Extract all docs for a module
jq '.by_module["yawl-engine"]' SEARCH_INDEX.json

# Find all docs tagged with a keyword
jq '.documents[] | select(.keywords[] | contains("security"))' SEARCH_INDEX.json

# List all tutorials
jq '.documents[] | select(.quadrant == "tutorial")' SEARCH_INDEX.json
```

### Maintenance & Updates
This index is generated from directory structure and file headers. Update by:
1. Adding docs to appropriate quadrant directories
2. Ensuring markdown files start with `# Title`
3. Running: `python3 scripts/generate-doc-index.py` (automated on commits)

**Last Updated**: 2026-02-28
**Next Auto-Update**: Daily (or on `docs/` changes)

---

## Related Indices

- **[MODULE_HEALTH_DASHBOARD.md](MODULE_HEALTH_DASHBOARD.md)** — Module maturity, test coverage, known limitations
- **[DOCUMENTATION_COMPLETENESS.md](DOCUMENTATION_COMPLETENESS.md)** — 4-quadrant coverage per module
- **[TOPIC_INDEX.md](TOPIC_INDEX.md)** — Topics → docs mapping
- **[USE_CASE_INDEX.md](USE_CASE_INDEX.md)** — Use cases → learning paths
- **[diataxis/INDEX.md](diataxis/INDEX.md)** — Master 4-quadrant index
