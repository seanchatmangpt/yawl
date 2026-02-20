# YAWL v6.0.0 Architecture Guide

**Formal Designation**: A = μ(O)
**Date**: 2026-02-20
**Status**: AUTHORITATIVE — supersedes ARCHITECTURE_AND_OPERATIONS_INDEX.md (v5.2.0)
**Maintained by**: YAWL Architecture Team

---

## Table of Contents

1. [Architecture Decision Matrix](#1-architecture-decision-matrix)
2. [System Architecture](#2-system-architecture)
3. [Service Architecture](#3-service-architecture)
4. [Interface Contracts (A/B/X/E)](#4-interface-contracts)
5. [Database Schema and Persistence](#5-database-schema-and-persistence)
6. [Multi-Cloud Deployment Patterns](#6-multi-cloud-deployment-patterns)
7. [Java 25 Architectural Patterns](#7-java-25-architectural-patterns)
8. [Agent and Integration Architecture](#8-agent-and-integration-architecture)
9. [Security Architecture](#9-security-architecture)
10. [Document Lifecycle: Upgrade, Archive, Supersede](#10-document-lifecycle)

---

## 1. Architecture Decision Matrix

This matrix is the canonical entry point for all YAWL v6.0.0 architectural decisions.
Each ADR in `docs/architecture/decisions/` maps to one cell. Status is current as of
2026-02-20.

### 1.1 Core Architecture

| ID | Title | Status | v6.0 | v6.1 | Supersedes |
|----|-------|--------|------|------|------------|
| ADR-001 | Dual Engine Architecture (Stateful + Stateless) | ACCEPTED | Complete | — | — |
| ADR-002 | Singleton vs Instance-based YEngine | ACCEPTED with CAVEATS | Partial | Full DI | — |
| ADR-021 | Automatic Engine Selection | ACCEPTED | Complete | — | — |

**Summary**: YAWL v6.0.0 provides two execution engines sharing the same `YNetRunner`
core. `YEngine` (stateful) persists all case state to PostgreSQL. `YStatelessEngine`
holds state in memory per invocation, enabling serverless and FaaS deployments.
The v6.0 engine selection mechanism chooses automatically based on specification hints.

Engine selection decision flowchart:
```
Duration > 5 min?           → Stateful
Human tasks required?       → Stateful
Audit trail mandatory?      → Stateful
Throughput > 100 req/sec?   → Stateless
Default (safest)            → Stateful
```

### 1.2 Platform

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-003 | Maven Primary, Ant Deprecated | ACCEPTED | Complete | Ant removed |
| ADR-004 | Spring Boot 3.4 + Java 25 | ACCEPTED | Complete | — |
| ADR-011 | Jakarta EE 10 Migration | APPROVED | Baseline done | Full migration |

**Technology Stack**:

| Component | v5.x | v6.0.0 |
|-----------|------|--------|
| Java | 11 | 25 (LTS) |
| Spring Boot | — | 3.4.x |
| Jakarta EE | Java EE 8 (`javax.*`) | 10 (`jakarta.*`) |
| ORM | Hibernate 5 / `javax.persistence` | Hibernate 6.6 / `jakarta.persistence` |
| Build | Ant (primary) | Maven (primary), Ant deprecated |
| Concurrency | Platform threads | Virtual threads (Project Loom, JEP 444) |
| GC | G1 | Generational ZGC |

### 1.3 Security and Identity

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-005 | SPIFFE/SPIRE Zero-Trust Identity | ACCEPTED | Complete | — |
| ADR-017 | JWT Authentication and Session Management | ACCEPTED | Complete | — |

**Zero-trust security chain**:
```
Service-to-service: SPIFFE SVID (mTLS x.509) via SPIRE
External API calls:  JWT HMAC-SHA256 session handles
Agent-to-agent:      JWT Bearer with 60s TTL handoff tokens
API keys:            For development tooling (30-day rotation)
```

### 1.4 Observability and Resilience

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-006 | OpenTelemetry Observability | ACCEPTED | Complete | — |
| ADR-007 | Repository Pattern and Caffeine Caching | ACCEPTED | Complete | — |
| ADR-008 | Resilience4j Circuit Breaking | ACCEPTED | Complete | — |

**Observability stack**: OpenTelemetry (traces) → Jaeger/Tempo | Prometheus (metrics)
→ Grafana | Loki (structured logs) | Alertmanager (alerting).

### 1.5 Cloud and Deployment

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-009 | Multi-Cloud Strategy (GCP/AWS/Azure/Oracle) | ACCEPTED | Complete | — |
| ADR-010 | Virtual Threads for Scalability | ACCEPTED | Complete | — |
| ADR-014 | Clustering and Horizontal Scaling | ACCEPTED | — | Redis lease protocol |
| ADR-023 | MCP/A2A CI/CD Deployment | ACCEPTED | Complete | — |
| ADR-024 | Multi-Cloud Agent Deployment Topology | ACCEPTED | — | CockroachDB federation |

### 1.6 API and Schema

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-012 | OpenAPI-First API Design | ACCEPTED | Complete | — |
| ADR-013 | YAWL Schema Versioning Strategy | ACCEPTED | Complete | — |
| ADR-016 | API Changelog and Deprecation Policy | ACCEPTED | Complete | — |
| ADR-018 | JavaDoc-to-OpenAPI Generation | ACCEPTED | Complete | — |
| ADR-020 | Workflow Pattern Library | ACCEPTED | Complete | — |

### 1.7 Persistence

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-015 | Persistence Layer (HikariCP, Flyway, Envers, Multi-Tenancy) | ACCEPTED | Complete | — |

### 1.8 Agent Architecture

| ID | Title | Status | v6.0 | v6.1 |
|----|-------|--------|------|------|
| ADR-019 | Autonomous Agent Framework | ACCEPTED | Complete | — |
| ADR-025 | Agent Coordination Protocol and Conflict Resolution | ACCEPTED | Complete | Full federation |

---

## 2. System Architecture

### 2.1 Layered Architecture Overview

```
┌───────────────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                                  │
│  YAWL Editor (Swing)   Custom Services   AI Agents (MCP/A2A)      │
└────────────────────────────┬──────────────────────────────────────┘
                             │
┌────────────────────────────▼──────────────────────────────────────┐
│                     INTERFACE LAYER                                │
│  Interface A (Design)   Interface B (Runtime)   Interface E/X      │
│  /yawl/ia               /yawl/ib                Events/Exception   │
└────────────────────────────┬──────────────────────────────────────┘
                             │
┌────────────────────────────▼──────────────────────────────────────┐
│                      ENGINE LAYER                                  │
│  ┌─────────────────────┐       ┌─────────────────────┐            │
│  │  YEngine (Stateful)  │       │ YStatelessEngine     │            │
│  │  Hibernate ORM       │       │ In-memory only       │            │
│  │  HikariCP pool       │       │ Serializable state   │            │
│  └──────────┬──────────┘       └──────────┬──────────┘            │
│             └─────────────┬───────────────┘                        │
│                           │                                         │
│              ┌────────────▼────────────┐                           │
│              │  YNetRunner (Shared)     │                           │
│              │  Petri net semantics     │                           │
│              │  89 workflow patterns    │                           │
│              │  OR-join synchronization │                           │
│              └─────────────────────────┘                           │
└────────────────────────────┬──────────────────────────────────────┘
                             │
┌────────────────────────────▼──────────────────────────────────────┐
│                     PERSISTENCE LAYER                              │
│  HikariCP (pool)   Hibernate 6.6   Flyway (migrations)             │
│  Envers (audit)    Multi-tenancy via schema separation             │
│  PostgreSQL 13+    MySQL 8.0+      Oracle 19c+                     │
└───────────────────────────────────────────────────────────────────┘
```

### 2.2 Module Map

YAWL v6.0.0 is a Maven multi-module project. Modules are governed by ADR-003.

| Module | Description | Key Types |
|--------|-------------|-----------|
| `yawl-engine` | Stateful engine core | `YEngine`, `YNetRunner`, `YWorkItem`, `YCase` |
| `yawl-elements` | Workflow element model | `YSpecification`, `YNet`, `YTask`, `YFlow` |
| `yawl-stateless` | Stateless engine | `YStatelessEngine`, `YCaseMonitor`, `YCaseImporter` |
| `yawl-interfaces` | Interface definitions | Interface A, B, E, X clients and servers |
| `yawl-resourcing` | Resource service | `ResourceService`, work queues, participant mgmt |
| `yawl-worklet` | Worklet service | `WorkletService`, exception handling, RDR trees |
| `yawl-integration` | MCP + A2A + A2A client | `YawlMcpServer`, `YawlA2AServer` |
| `yawl-schema` | XSD specifications | YAWL XML schema, version management |
| `yawl-observability` | Metrics + tracing | OpenTelemetry, Prometheus, health checks |
| `yawl-authentication` | Security | JWT, SPIFFE/SPIRE, session management |

---

## 3. Service Architecture

### 3.1 Engine Service (YEngine)

**Entry point**: `org.yawlfoundation.yawl.engine.YEngine`
**Role**: Stateful workflow execution with full persistence
**Interfaces implemented**: `InterfaceADesign`, `InterfaceAManagement`, `InterfaceBClient`, `InterfaceBInterop`

**v6.0.0 design constraints** (from ADR-002):
- The existing static `YEngine.getInstance()` Singleton is preserved in v6.0 for
  backward compatibility with existing servlet integrations.
- v6.1 will complete the constructor-injection migration (`YawlEngineConfiguration` Spring `@Bean`).
- `YNetRunner` must receive `YEngine` and `YAnnouncer` via constructor; the internal
  `YEngine.getInstance()` call in `YNetRunner` is a known ADR-002 caveat.

**Key operations**:
```
launchCase(specID, caseParams, completionObserver, logData) → caseId
startWorkItem(workItem, client) → YWorkItem
completeWorkItem(workItem, data, completionType, logPredicate) → void
cancelCase(caseID) → void
loadSpecification(spec) → void
unloadSpecification(specID) → void
```

**Concurrency model** (ADR-010): Virtual threads (`Thread.ofVirtual()`) per work item
execution. One virtual thread per active work item; each blocks on I/O cheaply.
Target: 10,000+ concurrent cases per engine instance.

### 3.2 Resource Service

**Entry point**: `org.yawlfoundation.yawl.resourcing.ResourceService`
**Role**: Human resource allocation, work queues, participant management
**Interfaces consumed**: Interface B (runtime), Interface A (design-time queries)

Resource allocation follows YAWL's four-phase resource lifecycle:
1. **Offer** — specification declares eligible resources (roles, capabilities, positions)
2. **Allocate** — ResourceService assigns to specific participants per allocation strategy
3. **Start** — participant checks out work item
4. **Complete** — participant checks in completed item

**Work queue types**: offered, allocated, started, suspended, unofficial
**Allocation strategies**: Round-robin, most-available, least-busy, capability-match
**Participant model**: Participants → Positions → Roles → Capabilities (hierarchical)

### 3.3 Worklet Service

**Entry point**: `org.yawlfoundation.yawl.worklet.WorkletService`
**Role**: Exception handling via Ripple-Down Rules (RDR); dynamic sub-workflow substitution
**Interfaces consumed**: Interface B (runtime), Interface X (exception)
**Interfaces published**: Exception notifications back to engine

**RDR tree structure**: Each task can have an associated RDR tree. On exception
(item failure, constraint violation, timeout), the worklet service walks the RDR
tree, selects a matching case, and spawns a replacement worklet workflow. This
provides workflow-level exception handling without modifying the original specification.

**Exception types handled**:
- `itemAbort`: work item aborted before completion
- `constraintViolation`: pre/post-condition not met
- `timeout`: timer expiry with no completion
- `resourceUnavailable`: no eligible resource found

---

## 4. Interface Contracts (A/B/X/E)

These interfaces are the primary extension points and integration contracts.
The backward-compatibility rule (from `engine/interfaces.md`) is absolute:
**existing method signatures are never modified; new operations extend interfaces only**.

### 4.1 Interface A — Design-Time (Administration)

**Package**: `org.yawlfoundation.yawl.engine.interfce.interfaceA`
**URL pattern**: `/yawl/ia`
**Used by**: YAWL Editor, administrative tools, CI/CD specification loaders

**Contract surfaces**:

| Interface | Methods | Description |
|-----------|---------|-------------|
| `InterfaceADesign` | `loadSpecification`, `unloadSpecification`, `getSpecifications` | Spec lifecycle |
| `InterfaceAManagement` | `getCases`, `getCase`, `cancelCase`, `suspendCase` | Runtime admin |
| `InterfaceA_EnvironmentBasedClient` | All IA HTTP calls | HTTP client to engine's IA endpoint |

**Wire format**: XML (YAWL's native wire format). All responses are XML-encoded.
JSON wrapping is provided by the MCP/A2A integration layer, not at the interface level.

**Backward compatibility guarantee**: Any YAWL specification uploaded via Interface A
in v5.x must load and execute identically in v6.0.0. Schema versioning (ADR-013)
provides the formal mechanism for this guarantee.

### 4.2 Interface B — Runtime (Client)

**Package**: `org.yawlfoundation.yawl.engine.interfce.interfaceB`
**URL pattern**: `/yawl/ib`
**Used by**: Custom services, ResourceService, WorkletService, autonomous agents, MCP Server, A2A Server

**Contract surfaces**:

| Interface | Methods | Description |
|-----------|---------|-------------|
| `InterfaceBClient` | `launchCase`, `startWorkItem`, `completeWorkItem`, `rollbackWorkItem` | Commands (mutations) |
| `InterfaceBClient` | `getAvailableWorkItems`, `getAllWorkItems`, `getWorkItem`, `getCaseData` | Queries (reads) |
| `InterfaceBInterop` | External service registration, work item check-in from services | Interoperability |
| `InterfaceB_EnvironmentBasedClient` | All IB HTTP calls | HTTP client to engine's IB endpoint |

**CQRS candidate** (from ARCHITECTURE-PATTERNS-JAVA25.md Pattern 4): The read and write
methods can be split into `InterfaceBCommands` and `InterfaceBQueries` sub-interfaces
while keeping `InterfaceBClient` as the backward-compatible composite. This split
is planned but not yet merged into v6.0.0.

**Session authentication**: Every Interface B call requires a session handle obtained
via `connect(user, password)`. Session handles are JWT HMAC-SHA256 tokens (ADR-017).

### 4.3 Interface E — Events/Logging

**Package**: `org.yawlfoundation.yawl.engine.interfce.interfaceE` (listeners)
**Used by**: Monitoring systems, audit loggers, external event consumers

Interface E is the event notification contract. Services register as listeners and
receive callbacks on case and work item lifecycle events.

**Event types published**:
- `caseStarted`, `caseCompleted`, `caseCancelled`, `caseSuspended`, `caseResumed`
- `workItemEnabled`, `workItemFired`, `workItemStarted`, `workItemCompleted`
- `workItemCancelled`, `workItemFailed`, `workItemDeadlocked`
- `timerExpired`

**Implementation note**: The stateless engine event system uses sealed records
(`YWorkflowEvent`, `YCaseLifecycleEvent`, `YWorkItemLifecycleEvent`) for
compiler-verified exhaustive pattern matching (ARCHITECTURE-PATTERNS-JAVA25.md Pattern 5).
The stateful engine still uses the mutable `YEvent` abstract class; sealed record
migration is in ADR-002 roadmap for v6.1.

### 4.4 Interface X — Extended (Exception Handling)

**Package**: `org.yawlfoundation.yawl.engine.interfce.interfaceX`
**Used by**: WorkletService, custom exception handlers

Interface X provides the exception-handling extension point. Services register
to receive exception notifications and can inject replacement worklets, modify
case data, or escalate to human handlers.

**Contract surface**:

| Interface | Methods | Description |
|-----------|---------|-------------|
| `InterfaceX_EnvBasedClient` | `handleException`, `getExceptionHandlers` | Exception dispatch |
| Exception types | `WorkItemException`, `CaseException`, `ConstraintViolation` | Typed exceptions |

**Worklet mechanism**: When an exception event arrives, the WorkletService walks
its RDR tree, selects a matching worklet specification, launches it via Interface B,
and reports the substitution back to the engine via Interface X completion.

---

## 5. Database Schema and Persistence

### 5.1 Persistence Architecture (ADR-015)

YAWL v6.0.0 restructures persistence around five principles:

**1. Connection Pooling (HikariCP)**
```properties
hibernate.hikari.maximumPoolSize=50
hibernate.hikari.minimumIdle=10
hibernate.hikari.idleTimeout=300000
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.leakDetectionThreshold=60000
```
The `leakDetectionThreshold` detects v5.x-era session leaks that caused production
connection exhaustion.

**2. Schema Migrations (Flyway)**
Migration path: `src/main/resources/db/migration/`
```
V6__initial_schema.sql          ← v6.0.0 baseline
V6_1__add_agent_binding.sql     ← agent task annotations
V6_2__add_audit_tables.sql      ← Hibernate Envers tables
V6_3__add_cluster_nodes.sql     ← cluster topology (v6.1)
```
Convention: `V{major}_{minor}__{description}.sql`
Zero-downtime migrations: all DDL changes are additive (no DROP without deprecation cycle).

**3. Audit Trail (Hibernate Envers)**
All mutable engine entities carry `@Audited`. Every state change produces a revision
entry in `{entity}_AUD` tables with `who`, `when`, `revtype` (ADD/MOD/DEL).
Envers satisfies ISO 9001 and SOX audit requirements from enterprise customers.

**4. Multi-Tenancy (Schema Separation)**
Each tenant operates in an isolated PostgreSQL schema. The `hibernate.default_schema`
property is set per-request from the JWT `tenant_id` claim. Flyway applies migrations
to each tenant schema independently.

**5. Read/Write Query Separation**
Heavy reporting queries route to a read replica via a secondary `DataSource` bean.
The `@Transactional(readOnly=true)` annotation is the routing signal.

### 5.2 Core Schema Tables

| Table | Description | Key Columns |
|-------|-------------|-------------|
| `YCase` | Active workflow cases | `case_id`, `spec_id`, `status`, `start_time`, `data` |
| `YWorkItem` | Work items (tasks in progress) | `item_id`, `case_id`, `task_id`, `status`, `data` |
| `YSpecification` | Loaded workflow specifications | `spec_id`, `version`, `uri`, `xml_body` |
| `YNet` | Net instances within cases | `net_id`, `case_id`, `spec_id`, `marking` |
| `YIdentifier` | Case and item identifiers | `id_string`, `parent_id` |
| `YNetElement` | Net elements (places, transitions) | `element_id`, `net_id`, `type`, `marking` |
| `YExternalNetElement` | External net elements | `element_id`, `decomposition_id` |
| `YLogEvent` | Workflow log (append-only) | `event_id`, `case_id`, `task_id`, `type`, `timestamp` |
| `YClusterNode` | Cluster topology (v6.1) | `node_id`, `host`, `port`, `status`, `lease_expiry` |

**Audit tables** (generated by Envers):
`YCase_AUD`, `YWorkItem_AUD`, `YSpecification_AUD` — each with `REV`, `REVTYPE` columns.

### 5.3 Supported Databases

| Database | Minimum Version | Dialect Class |
|----------|-----------------|---------------|
| PostgreSQL | 13 | `PostgreSQLDialect` |
| MySQL | 8.0 | `MySQLDialect` |
| Oracle | 19c | `OracleDialect` |
| H2 | 2.x | `H2Dialect` (testing only) |

**Production recommendation**: PostgreSQL 13+ for optimal JSONB support and
advisory lock support (used by the v6.1 clustering protocol).

---

## 6. Multi-Cloud Deployment Patterns

### 6.1 Three-Tier Cloud Strategy (ADR-009)

```
Tier 1: Cloud-Agnostic Core
  - YAWL Engine, Stateless Engine, Interface A/B/X/E, MCP, A2A
  - Deployed identically across AWS, Azure, GCP, on-premise
  - No cloud SDK dependencies in yawl-engine or yawl-elements

Tier 2: Kubernetes Abstraction
  - Helm charts for all YAWL components
  - Service mesh (Istio/Linkerd) for mTLS and traffic management
  - Ingress controllers (Kong/Traefik/ALB) for JWT validation and rate limiting

Tier 3: Cloud-Native Agent Extensions
  - AWS:   aws-document-agent  (Textract + Bedrock)
  - Azure: azure-cognitive-agent (Form Recognizer + OpenAI)
  - GCP:   gcp-document-agent  (Document AI + Vertex AI)
  - Oracle: oracle-cloud-agent  (OCI services)
```

### 6.2 Agent Deployment Topology (ADR-024)

**Three tiers of agent deployment**:

**Tier 1 — Cloud-Agnostic Agents**: Deployed in every cloud region. No cloud API
dependencies. Scaled 2–10 replicas via HPA. Examples: `generic-workflow-agent`,
`email-notification-agent`.

**Tier 2 — Cloud-Native Agents**: Deployed only in the matching cloud. Co-located
with their cloud infrastructure. Examples: `aws-document-agent` in AWS us-east-1,
`azure-cognitive-agent` in Azure eastus.

**Tier 3 — LLM/ZAI Agents**: Deployed in the primary LLM region. Controlled by
`ZAI_API_KEY` availability and API rate limits.

**Global Registry Federation** (v6.1): In-memory `AgentRegistry` is replaced by
CockroachDB-backed federated registry with one shard per cloud. The existing
`AgentRegistryClient` REST API is unchanged at client call sites — only the
backend endpoint URL changes in production:

```properties
yawl.agent.registry.url=https://registry.yawl.cloud
yawl.agent.registry.region=aws-us-east-1
yawl.agent.registry.prefer-local=true
```

**GeoDNS failover**: `a2a.yawl.cloud` routes to nearest healthy region (30s TTL).
Health probe: `GET /.well-known/agent.json`. Failed regions removed from DNS within 30s.

### 6.3 Kubernetes Deployment Architecture

**StatefulSet**: YAWL Engine — requires stable network identity for clustering (v6.1)
**Deployment**: MCP Server, A2A Server, ResourceService — stateless, freely scalable
**PodDisruptionBudget**: Minimum 1 engine replica available during rollouts
**HPA**: MCP Server (2–10 replicas, CPU 70% / Memory 80%), A2A Server (2–10 replicas)

**Resource specifications**:

| Component | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----------|-------------|-----------|----------------|--------------|
| YAWL Engine | 1 core | 2 cores | 2 GB | 4 GB |
| MCP Server | 100m | 500m | 256 MB | 512 MB |
| A2A Server | 100m | 500m | 256 MB | 512 MB |
| Resource Service | 500m | 1 core | 1 GB | 2 GB |
| PostgreSQL | 1 core | 2 cores | 2 GB | 4 GB |
| Redis | 100m | 250m | 256 MB | 512 MB |

### 6.4 Clustering and Horizontal Scaling (ADR-014, v6.1)

**Clustering is planned for v6.1.** v6.0.0 supports multiple engine instances behind
a load balancer with sticky sessions (session affinity). True active-active clustering
requires the Redis lease protocol specified in ADR-014.

**v6.0.0 current state**: Single active engine, read replicas for query traffic,
stateless services freely scaled horizontally.

**v6.1 target state**: Redis-based distributed lease for case ownership. Any engine
node can handle any case. `YClusterNode` table tracks topology. 30-second lease TTL
with automatic failover.

---

## 7. Java 25 Architectural Patterns

These patterns are documented in full in `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`.
This section provides the decision matrix governing adoption priority and status.

### 7.1 Pattern Adoption Status

| Pattern | ADR / Authority | v6.0 Status | v6.1 Target |
|---------|-----------------|-------------|-------------|
| Virtual Threads (Pattern 1) | ADR-010 | Deployed | Expand to all async |
| Structured Concurrency (Pattern 2) | ADR-010 | Partial | Full adoption |
| Sealed State Machine (Pattern 3) | ADR-002 | Planned | Complete |
| CQRS for Interface B (Pattern 4) | engine/interfaces.md | Planned | Complete |
| Sealed Records for Events (Pattern 5) | ADR-019 | Stateless only | Stateful too |
| Java Module System (Pattern 6) | ADR-003 | Not started | module-info.java |
| Reactive Event Filtering (Pattern 7) | ADR-006 | Not started | YAnnouncer |
| Constructor Injection (Pattern 8) | ADR-002 | Partial (Spring) | Full migration |

### 7.2 Backward Compatibility Constraints

The following Java 25 patterns are **blocked** from adoption in v6.0.0 because
they would break existing API contracts preserved by the interface rules:

- **Removing `YEngine.getInstance()`**: External code calling this static method
  would break. The instance is preserved; Spring wraps it as a `@Bean`.
- **Sealing `InterfaceBClient`**: Custom services that extend this interface
  externally would fail to compile. Sealed enhancements are additive sub-interfaces.
- **Changing XML wire format**: Interface A and B XML response schemas are frozen
  per ADR-013. JSON wrappers are additive only.

---

## 8. Agent and Integration Architecture

### 8.1 MCP Server (YawlMcpServer)

**Entry point**: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer`
**Transport**: STDIO (local/development), SSE HTTP (production/cloud)
**Protocol**: MCP (Model Context Protocol), JSON-RPC 2.0

**Tools exposed** (15 total):
- Case management: `launch_case`, `cancel_case`, `suspend_case`, `resume_case`
- Work item operations: `get_workitems`, `checkout_workitem`, `checkin_workitem`, `skip_workitem`
- Specification management: `list_specifications`, `upload_specification`, `unload_specification`
- Query: `get_case_state`, `get_specification_data`, `get_running_cases`

**Resources exposed** (6 total):
`yawl://cases`, `yawl://cases/{id}`, `yawl://workitems`, `yawl://workitems/{id}`,
`yawl://specifications`, `yawl://cases/{id}/data`

**CI/CD integration** (ADR-023, Pattern 11): The MCP server runs as a managed
child process in CI. The test harness pipes JSON-RPC to stdin, reads stdout.
Production uses SSE transport behind an API gateway.

### 8.2 A2A Server (YawlA2AServer)

**Entry point**: `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer`
**Protocol**: A2A (Agent-to-Agent), HTTP REST, JSON-RPC 2.0
**Authentication**: JWT Bearer, SPIFFE mTLS, API key (CompositeAuthenticationProvider)

**Skills exposed** (5 total):

| Skill ID | Required Permission |
|----------|---------------------|
| `launch_workflow` | `workflow:launch` |
| `query_workflows` | `workflow:query` |
| `manage_workitems` | `workitem:manage` |
| `cancel_workflow` | `workflow:cancel` |
| `handoff_workitem` | `workitem:manage` |

**Agent card discovery**: `GET /.well-known/agent.json` (no authentication required)

### 8.3 Handoff Protocol

When an autonomous agent checks out a work item and determines it cannot complete it,
the handoff protocol transfers ownership to a capable agent:

```
Source Agent                HandoffRequestService              Target Agent
     │                              │                                │
     │ 1. initiateHandoff(itemId)   │                                │
     │─────────────────────────────>│                                │
     │                              │ 2. Validate work item          │
     │                              │ 3. Find capable agents         │
     │                              │    (agentInfoStore.by-capability)
     │                              │ 4. Generate JWT (60s TTL)      │
     │                              │                                │
     │                              │ 5. POST /handoff (JWT)         │
     │                              │───────────────────────────────>│
     │                              │                                │
     │                              │ 6. 200 OK                      │
     │                              │<───────────────────────────────│
     │ 7. Rollback checkout         │                                │
     │<─────────────────────────────│                                │
     │                              │                                │
     │                              │ 8. Target checkout + complete  │
     │                              │<───────────────────────────────│
```

**Handoff JWT claims**:
```json
{
  "sub": "handoff",
  "workItemId": "WI-42",
  "fromAgent": "agent-A",
  "toAgent":   "agent-B",
  "engineSession": "<session-handle>",
  "exp": 1740000060
}
```

### 8.4 Agent Coordination Protocol (ADR-025)

**Three-layer coordination** eliminates the 75% checkout failure rate in multi-agent polling:

**Layer 1: Partition Strategy** — Consistent hash assigns each work item to exactly one agent:
```java
int hash = Math.abs(item.getID().hashCode());
boolean isMyItem = (hash % totalAgents) == agentIndex;
```
`agentIndex` and `totalAgents` are read from `AgentRegistry`, refreshed every 5 discovery cycles.

**Layer 2: Handoff Protocol** — Agent-to-agent work item transfer when an agent
discovers after checkout that it lacks the required capability.

**Layer 3: Conflict Resolution** — Configured per-task via `<agentBinding>` in
the workflow specification:
```xml
<agentBinding>
  <reviewQuorum>3</reviewQuorum>
  <conflictResolution>MAJORITY_VOTE</conflictResolution>
  <conflictArbiter>supervisor-agent</conflictArbiter>
  <fallbackToHuman>true</fallbackToHuman>
</agentBinding>
```
Resolution tiers: `MAJORITY_VOTE` → `ESCALATE` → human fallback.

### 8.5 Claude Agent SDK Integration

Two integration paths:

**Path 1 (MCP tool use)**: Claude Desktop / Claude Code connects to `YawlMcpServer`
via STDIO transport. Tools like `checkout_workitem` and `checkin_workitem` are called
directly as MCP tool invocations.

**Path 2 (A2A orchestration)**: Claude orchestrator sends natural-language messages
to `YawlA2AServer` via HTTP REST with JWT authentication. The A2A server translates
messages to Interface B operations.

**Cross-boundary context**: The `WorkflowEventStore` (event sourcing) provides
a durable, cross-agent shared log visible to both MCP and A2A clients.

---

## 9. Security Architecture

### 9.1 Authentication Chain

```
Request → canHandle()? → authenticate() → Principal → Authorize
          │
          CompositeAuthenticationProvider (chain of responsibility)
          │
          ├── 1. SPIFFE/mTLS (service mesh identity)
          ├── 2. JWT Bearer   (external API calls)
          ├── 3. API Key      (development tooling)
          └── 4. Handoff JWT  (agent-to-agent work item transfer)
```

### 9.2 Network Security Zones

```
PUBLIC ZONE (DMZ)
  - Ingress Load Balancer (TLS 1.3 termination)
  - WAF rules (OWASP Top 10), DDoS protection
          │
APPLICATION ZONE
  - API Gateway (JWT validation, rate limiting: 100 req/s per agent)
  - MCP Server pods, A2A Server pods
  - Service mesh mTLS (pod-to-pod)
          │
DATA ZONE
  - YAWL Engine (StatefulSet)
  - PostgreSQL (private subnets)
  - Redis (private subnets)
  - Object Storage (private endpoints, spec archives, logs)
```

### 9.3 Secret Rotation Policy

| Secret | Storage | Rotation |
|--------|---------|----------|
| `YAWL_PASSWORD` | Kubernetes Secret + Vault | 90 days |
| `A2A_JWT_SECRET` | Vault (dynamic) | 24 hours |
| `A2A_API_KEY_MASTER` | Vault | 30 days |
| Database credentials | Vault (dynamic leases) | 1 hour |
| TLS certificates | cert-manager | 90 days |
| SPIFFE SVIDs | SPIRE (automatic) | 1 hour |

---

## 10. Document Lifecycle: Upgrade, Archive, Supersede

This section is the audit record for all documentation in `/home/user/yawl/docs/`.
It was produced by the architecture audit of 2026-02-20.

### 10.1 Document Classification Key

| Classification | Meaning | Action |
|----------------|---------|--------|
| CURRENT | Accurate, aligned with v6.0.0 | No action needed |
| UPGRADED | Modernized in this audit | See notes |
| ARCHIVE | Superseded or obsolete | Moved to `docs/archived/` |
| SCOPE-LIMITED | Accurate for a narrow scope | Retain with scope annotation |

### 10.2 Architecture and Design Documents

| File | Classification | Notes |
|------|----------------|-------|
| `docs/architecture/decisions/ADR-001` through `ADR-025` | CURRENT | All ADRs are aligned with v6.0.0; index in `decisions/README.md` is authoritative |
| `docs/architecture/INDEX.md` | SCOPE-LIMITED | Covers Maven build architecture only; accurate for that scope |
| `docs/MVP_ARCHITECTURE.md` | CURRENT | Complete MCP/A2A deployment architecture for v6.0.0 |
| `docs/ARCHITECTURE-REFACTORING-PATTERNS.md` | ARCHIVE | References `/Users/sac/cre/vendors/yawl/src/` — wrong path; patterns now in `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` |
| `docs/ARCHITECTURE_AND_OPERATIONS_INDEX.md` | UPGRADED | Version header said "5.2.0"; superseded by this guide for architecture sections; retain for operations index |

### 10.3 CICD and Deployment Documents

| File | Classification | Notes |
|------|----------------|-------|
| `docs/CICD_V6_ARCHITECTURE.md` | CURRENT | CI/CD pipeline architecture for v6.0.0 |
| `docs/CLOUD_DEPLOYMENT_RUNBOOKS.md` | CURRENT | Production runbooks |
| `docs/DEPLOY-DOCKER.md`, `DEPLOY-JETTY.md`, `DEPLOY-TOMCAT.md`, `DEPLOY-WILDFLY.md` | SCOPE-LIMITED | Accurate for their target deployment mode |
| `docs/DEPLOYMENT-CHECKLIST.md`, `docs/DEPLOYMENT-READINESS-CHECKLIST.md` | SCOPE-LIMITED | Retain; verify against v6.0.0 release |

### 10.4 Migration Documents

| File | Classification | Notes |
|------|----------------|-------|
| `docs/MIGRATION-v5.2-to-v6.0.md` | CURRENT | Authoritative migration guide |
| `docs/api/MIGRATION-5x-to-6.md` | CURRENT | API-specific migration guide |
| `docs/LIBRARY-MIGRATION-GUIDE.md` | SCOPE-LIMITED | Library-level migration; accurate for Jakarta EE migration |
| `docs/MAVEN_MIGRATION_STATUS.md` | SCOPE-LIMITED | Historical migration status; retain as record |
| `docs/IMPORT_MIGRATION_CHECKLIST.md` | SCOPE-LIMITED | Jakarta EE import migration; accurate |

### 10.5 Documents for Archival

The following documents are candidates for archival. They contain outdated paths,
version numbers, or have been superseded by the v6.0.0 documents:

| File | Reason for Archival |
|------|---------------------|
| `docs/ARCHITECTURE-REFACTORING-PATTERNS.md` | References hardcoded `/Users/sac/cre/vendors/yawl/src/` path; content superseded by `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` |
| `docs/FINAL-IMPLEMENTATION-PLAN.md` | Historical planning document; implementation complete |
| `docs/MVP_IMPLEMENTATION_SUMMARY.md` | Superseded by `docs/v6/V6.0.0-FINAL-INTEGRATION-REPORT.md` |

### 10.6 Preserved Backward Compatibility Documentation

The following documents must be preserved indefinitely for backward compatibility:

| File | Preservation Reason |
|------|---------------------|
| `docs/api/CHANGELOG.md` | API changelog per ADR-016; never deleted |
| `docs/api/MIGRATION-5x-to-6.md` | Migration guide; must remain accessible for v5.x users |
| `docs/MIGRATION-v5.2-to-v6.0.md` | Migration guide; must remain accessible |
| `docs/schema/` (XSD files) | Schema versions frozen per ADR-013 |
| All `ADR-*.md` files | Architectural decisions are permanent records |

---

## References

**Authoritative source documents** (read these before this guide):

| Document | Location | Scope |
|----------|----------|-------|
| CLAUDE.md | `/home/user/yawl/CLAUDE.md` | Project invariants and build rules |
| Architecture Patterns (Java 25) | `/home/user/yawl/.claude/ARCHITECTURE-PATTERNS-JAVA25.md` | 13 Java 25 patterns with code examples |
| Interface Rules | `/home/user/yawl/.claude/rules/engine/interfaces.md` | Interface contract rules |
| ADR Index | `/home/user/yawl/docs/architecture/decisions/README.md` | All architectural decisions |
| Workflow Engine Rules | `/home/user/yawl/.claude/rules/engine/workflow-patterns.md` | Engine design rules |
| MVP Architecture | `/home/user/yawl/docs/MVP_ARCHITECTURE.md` | MCP/A2A deployment architecture |
| Persistence ADR | `/home/user/yawl/docs/architecture/decisions/ADR-015-persistence-layer-v6.md` | Database schema decisions |
| Multi-Cloud ADR | `/home/user/yawl/docs/architecture/decisions/ADR-009-multi-cloud-strategy.md` | Cloud deployment decisions |
| Agent Coordination ADR | `/home/user/yawl/docs/architecture/decisions/ADR-025-agent-coordination-protocol.md` | Agent coordination |

---

**Last Updated**: 2026-02-20
**Produced by**: Architecture Audit — Session daK6J
**Next Review**: 2026-08-20 (6 months)
**Branch**: `claude/launch-doc-upgrade-agents-daK6J`
