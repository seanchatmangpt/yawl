# YAWL Roadmap

**System**: YAWL v5.2 — Yet Another Workflow Language
**Semantics**: Petri net-based BPM engine | **Platform**: Java 25 + Maven
**Date**: February 2026 | **Horizon**: 24 months

---

## Current State: v5.2 (February 2026)

### Module Inventory (13 modules)

| Module | Tests (scoped) | Source Strategy | Status |
|--------|---------------|-----------------|--------|
| yawl-utilities | 2 | full_shared | Active |
| yawl-elements | 3 | full_shared | Active |
| yawl-authentication | 0 | package_scoped | Active |
| yawl-engine | 11 | full_shared | Active |
| yawl-stateless | 6 | full_shared | Active |
| yawl-resourcing | 1 | package_scoped | Active |
| yawl-worklet | 0 | package_scoped | Active |
| yawl-scheduling | 0 | package_scoped | Active |
| yawl-security | 0 | full_shared | Active |
| yawl-integration | 15 | package_scoped | Active |
| yawl-monitoring | 1 | package_scoped | Active |
| yawl-webapps | 0 | standard | Active |
| yawl-control-panel | 0 | package_scoped | Active |

**Total scoped tests**: 39 across 7 modules. Six modules have zero scoped tests.

### Integration Layer (yawl-integration)

Active subsystems: `mcp`, `a2a`, `autonomous`, `zai`, `gateway`, `oauth2`, `webhook`, `messagequeue`, `eventsourcing`, `processmining`, `orchestration`, `orderfulfillment`, `dedup`, `observability`, `spiffe`, `credential`, `scheduling`

Entry points:
- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` — Model Context Protocol server
- `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer` — Agent-to-Agent server

### Architecture: Dual-Family (51 Mirror Pairs)

All engine and element classes exist in stateful (`org.yawlfoundation.yawl.*`) and stateless (`org.yawlfoundation.yawl.stateless.*`) variants. Policy: `MIRROR_REQUIRED` on all 51 families.

### Known Risks (FMEA, ranked by RPN)

| ID | Failure Mode | RPN | Mitigation |
|----|-------------|-----|------------|
| FM2 | Dual-Family Class Confusion | 224 | dual-family.json + 16-dual-family-map.mmd |
| FM1 | Shared Source Path Confusion | 216 | shared-src.json + 15-shared-src-map.mmd |
| FM3 | Dependency Version Skew | 210 | deps-conflicts.json + 17-deps-conflicts.mmd |
| FM6 | Gate Bypass via Skip Flags | 144 | gates.json + 40-ci-gates.mmd |
| FM7 | Reactor Order Violation | 105 | reactor.json + 10-maven-reactor.mmd |
| FM5 | Test Selection Ambiguity | 84 | tests.json + 30-test-topology.mmd |
| FM4 | Maven Cached Missing Artifacts | 60 | maven-hazards.json |

### Quality Gates

| Gate | Default Active | Profiles |
|------|---------------|----------|
| maven-enforcer | Yes | (always) |
| jacoco | Yes | ci, prod, analysis, sonar |
| spotbugs | No | ci, prod, analysis |
| pmd | No | analysis |
| checkstyle | No | analysis |
| owasp-dependency-check | No | prod, security-audit |

---

## Phase 1: Java 25 Core Patterns (Q1 2026)

**Goal**: Adopt finalized Java 25 features for immutability, type safety, and concurrency.

### 1.1 Records for Events and Immutable Data

Replace the mutable `YEvent` abstract class hierarchy with sealed record types.

**Target**:
```java
public sealed interface YWorkflowEvent
    permits YCaseLifecycleEvent, YWorkItemLifecycleEvent,
            YTimerEvent, YConstraintEvent {}

public record YCaseLifecycleEvent(
    Instant timestamp, YEventType type,
    YIdentifier caseID, YSpecificationID specID, int engineNbr
) implements YWorkflowEvent {}
```

**Files**: `org.yawlfoundation.yawl.stateless.listener.event.YEvent`, `org.yawlfoundation.yawl.engine.announcement.YEngineEvent`

**Benefit**: Eliminates post-construction mutation; compiler enforces exhaustive handling.

### 1.2 Sealed Classes for Domain Hierarchies

Convert `YWorkItemStatus` (13-value flat enum) and `YElement` hierarchy to sealed classes, enabling exhaustive `switch` expressions.

**Files**: `org.yawlfoundation.yawl.engine.YWorkItemStatus`, `org.yawlfoundation.yawl.elements.YExternalNetElement`

### 1.3 Virtual Threads for Agent Discovery

Replace platform threads in `GenericPartyAgent.discoveryThread` with named virtual threads.

**Before**:
```java
private Thread discoveryThread; // platform thread — 2MB each
```

**After**:
```java
discoveryThread = Thread.ofVirtual()
    .name("yawl-agent-discovery-" + config.getAgentId())
    .start(this::runDiscoveryLoop);
```

**Impact**: 1000 agents: 2 GB → ~1 MB memory footprint.

**Files**: `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`

### 1.4 Structured Concurrency for Work Item Batches

Replace sequential work item processing in `DiscoveryStrategy.discoverWorkItems()` with `StructuredTaskScope.ShutdownOnFailure`, enabling parallel fan-out with automatic cancellation on first failure.

**Files**: `org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy`

### 1.5 Scoped Values for Workflow Context

Replace `ThreadLocal<WorkflowContext>` with `ScopedValue<WorkflowContext>` to propagate workflow ID and security context safely across virtual thread boundaries.

**Files**: Engine context propagation in `org.yawlfoundation.yawl.engine.YEngine`

### 1.6 Compact Object Headers (JVM flag)

Enable `-XX:+UseCompactObjectHeaders` in all deployment configs. No code changes required; yields 4–8 bytes per object and 5–10% throughput improvement.

### Deliverables
- Sealed record event hierarchy replacing `YEvent`
- Virtual threads in `GenericPartyAgent`
- Structured concurrency in `DiscoveryStrategy`
- Scoped values for workflow context propagation
- Compact object headers in all JVM configs

---

## Phase 2: Module Architecture and Build (Q2 2026)

**Goal**: Resolve shared-source ambiguity, improve test isolation, and establish Java module boundaries.

### 2.1 Resolve Shared Source Strategy Ambiguity (FM1, FM2 — RPN 216/224)

The four `full_shared` modules (yawl-utilities, yawl-elements, yawl-engine, yawl-stateless) all point to `../src` as their source directory with no include filters. The build relies on Maven compiler plugin `includes` directives per-module.

**Goal**: Move each module to `package_scoped` strategy by extracting source into per-module `src/main/java` directories.

**Priority order** (by FMEA impact):
1. `yawl-engine` (FM2 = dual-family risk, RPN 224)
2. `yawl-elements` (FM1 = shared source risk, RPN 216)
3. `yawl-stateless` (FM3 = dependency skew risk, RPN 210)
4. `yawl-utilities`

### 2.2 Expand Test Coverage to Zero-Test Modules

Six modules currently have zero scoped tests: `yawl-authentication`, `yawl-worklet`, `yawl-scheduling`, `yawl-security`, `yawl-webapps`, `yawl-control-panel`.

**Target**: Minimum 5 scoped tests per module covering primary entry points.

**Priority**: `yawl-authentication` (identity-critical), `yawl-security` (security-critical).

### 2.3 Java Module System (JPMS) Boundaries

Define `module-info.java` for each of the 13 modules, establishing explicit `exports`, `requires`, and `uses`/`provides` declarations.

**Benefits**:
- Compiler-enforced module boundaries (eliminates FM1/FM2 at compile time)
- Strong encapsulation of internal packages
- Enables AOT compilation improvements

**Constraint**: Dual-family mirror requirement (51 pairs) must be reflected in module declarations.

### 2.4 CQRS Split for Interface B

Split `InterfaceBClient` into:
- `InterfaceBCommandClient` — mutations (launch case, complete work item, cancel case)
- `InterfaceBQueryClient` — reads (get cases, get work items, get status)

**Benefit**: Clear contract separation; enables read replicas without write-path changes.

### 2.5 CI Gate Hardening (FM6 — RPN 144)

Enable SpotBugs in `ci` profile (currently config-only). Block builds that pass `-DskipTests=true` or `-Denforcer.skip=true` in CI without documented override.

### Deliverables
- Per-module source directories for engine and elements
- 5+ tests per zero-test module
- `module-info.java` skeleton for all 13 modules
- `InterfaceBCommandClient` / `InterfaceBQueryClient` split
- SpotBugs enforced in CI profile

---

## Phase 3: Security Hardening and Observability (Q3 2026)

**Goal**: Full production security compliance (Java 25 standards) and operational observability.

### 3.1 TLS 1.3 Enforcement

Disable TLS 1.2 in all production deployments via:
```
jdk.tls.disabledAlgorithms=TLSv1, TLSv1.1, TLSv1.2
```

Enforce cipher suite allowlist: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`, `TLS_CHACHA20_POLY1305_SHA256`.

Configure mTLS for all service-to-service communication within the integration layer.

### 3.2 Cryptography Compliance

Audit and replace all non-compliant cryptographic usage:
- Replace MD5/SHA-1 with SHA-256+
- Replace AES-CBC with AES-GCM
- Require RSA-3072+ or ECDSA P-256/P-384
- Adopt Java 25 Key Derivation Functions API (HKDF/Argon2) for credential handling
- Adopt Java 25 PEM Encoding API for certificate management in `spiffe` and `oauth2` subsystems

**Verification**:
```bash
jdeprscan --for-removal build/libs/yawl.jar
grep -r "MD5\|SHA-1\|DES" src/
```

### 3.3 RBAC Authorization Layer

Replace `SecurityManager`-based checks (removed in JDK 24+) with explicit RBAC:
- Define role hierarchy: `ADMIN > MANAGER > PARTICIPANT > OBSERVER`
- Integrate with `yawl-authentication` module
- Protect all `YEngine` mutating operations with role check

### 3.4 SBOM Integration

Integrate `mvn cyclonedx:makeBom` into CI pipeline for supply chain security.
Generate SBOM artifact on every release build.

### 3.5 Structured Observability

Expand `yawl-monitoring` from 1 scoped test and 6 source files to a full observability layer:
- Metrics export (Prometheus-compatible via existing `observability` integration subsystem)
- Structured logging (JSON) for all case lifecycle events
- Distributed trace propagation (W3C TraceContext headers) across MCP and A2A boundaries
- Health endpoints for Kubernetes liveness/readiness probes

### 3.6 AOT Method Profiling for Container Startup

Enable `-XX:+UseAOTCache` in container images to reduce startup latency:
```bash
# Profile generation (CI)
java -XX:+UseAOTCache -XX:AOTCacheOutput=yawl.aot -jar yawl-engine.jar --profile

# Production startup
java -XX:+UseAOTCache -XX:AOTCacheInput=yawl.aot -jar yawl-engine.jar
```

### Deliverables
- TLS 1.3 + cipher suite enforcement in all deployment configs
- Cryptographic compliance audit report + remediation commits
- RBAC authorization layer in yawl-authentication/yawl-security
- SBOM in CI pipeline
- Prometheus metrics from yawl-monitoring
- AOT cache generation in container build pipeline

---

## Phase 4: v6.0 — Architecture Consolidation (Q4 2026)

**Goal**: Eliminate structural duplication, establish stable public API surface, prepare for v6.0 release.

### 4.1 Stateful/Stateless Engine Consolidation

The dual-family (51 mirror pairs, all `MIRROR_REQUIRED`) represents ~50% codebase duplication. Evaluate consolidation path:

**Option A**: Single parametric engine — `YEngine<S extends YEngineState>` where `S` is either `YPersistentState` or `YEphemeralState`. Eliminates all 51 mirror classes.

**Option B**: Shared core with thin adapters — extract shared logic into `yawl-core` module, keep thin stateful/stateless adapters. Reduces duplication to adapter code only.

**Decision criteria**: Option A preferred if pattern matching on `YEngineState` is sufficient for divergence points. Observatory FMEA re-run required before deciding.

### 4.2 Public API Surface (Interface A Stabilization)

Define and document the stable public API:
- `YEngine` — stateful execution API
- `YStatelessEngine` — stateless execution API
- `YawlMcpServer` — MCP integration API
- `YawlA2AServer` — A2A integration API

Mark internal packages with `@Internal` and enforce via enforcer rule. Publish API as `yawl-api` artifact.

### 4.3 Integration Layer — Event Sourcing and Process Mining

Promote `eventsourcing` and `processmining` subsystems from experimental to production:
- `eventsourcing`: Append-only audit log of all workflow state transitions (XES format)
- `processmining`: Online conformance checking against Petri net model

These subsystems replace ad-hoc logging with structured, queryable event streams.

### 4.4 Worklet and Scheduling Test Coverage

`yawl-worklet` and `yawl-scheduling` both have zero tests. Before v6.0, require minimum coverage:
- `yawl-worklet`: Worklet selection logic, worklet invocation, worklet result handling
- `yawl-scheduling`: Timer firing, deadline enforcement, schedule CRUD

### 4.5 Dependency Version Lockdown

Current state: 0 version conflicts (`deps-conflicts.json` reports clean). Maintain this by:
- Enforcing all child POMs use parent BOM property references (no inline versions)
- Adding enforcer rule to fail build on any direct child version declaration

### Deliverables
- Engine consolidation decision (Option A or B) with prototype
- `yawl-api` artifact published to Maven Central
- `eventsourcing` and `processmining` promoted to stable
- Worklet and scheduling test suites (20+ tests each)
- Enforcer rule blocking child POM version declarations

---

## Phase 5: v7.0 — Scale and Ecosystem (2027)

**Goal**: Horizontal scalability, ecosystem integrations, and AI-native workflow patterns.

### 5.1 Distributed Case Store

Replace in-memory `YNetRunnerRepository` with pluggable distributed store:
- Default: embedded H2 (existing, backward compatible)
- Plugin: Redis (case state as sorted sets, sub-millisecond lookup)
- Plugin: PostgreSQL (JSONB case state, full SQL query support)

Virtual threads (Phase 1) make async I/O to external stores practical without thread pool exhaustion.

### 5.2 Horizontal Scaling via Case Partitioning

Partition cases across engine instances by `YSpecificationID`. Each partition owns a subset of active cases. Coordination via `messagequeue` subsystem (already present in yawl-integration).

**Target**: 10,000 concurrent cases across 4 engine nodes (2,500 cases/node).

### 5.3 AI-Native Workflow Patterns

The `mcp` and `a2a` subsystems establish the foundation for AI agent orchestration. Extend:
- **MCP Tool Definitions**: Expose `launch_case`, `complete_work_item`, `get_case_state` as typed MCP tools
- **A2A Task Delegation**: Route work items to autonomous agents via A2A protocol
- **LLM-as-Participant**: Allow language model agents to complete work items via structured output
- **Workflow Generation**: Accept natural language workflow descriptions; generate YAWL XML specifications

### 5.4 YAWL Schema 5.0

Current schema: `YAWL_Schema4.0.xsd`. Schema 5.0 additions:
- Native AI task type (`<ai-task>` element with model, prompt, output schema attributes)
- Versioned specification references (semver-based spec IDs)
- Structured exception handling declarations (compensating transaction patterns)

### 5.5 Ecosystem Connectors

Promote existing connector stubs to production:
- `webhook` — outbound HTTP callbacks on case/task events (replace ad-hoc InterfaceB calls)
- `oauth2` — OAuth 2.0 + PKCE for external service authentication in task execution
- `spiffe` — SPIFFE/SPIRE workload identity for mTLS in Kubernetes deployments
- `orderfulfillment` — Reference implementation of order-to-cash workflow pattern

### Deliverables
- Pluggable distributed case store with Redis adapter
- 4-node horizontal scaling demonstrated (10,000 concurrent cases)
- MCP tools for case lifecycle operations
- YAWL Schema 5.0 XSD + updated marshaler
- `webhook`, `oauth2`, `spiffe` promoted to stable

---

## Build and Tooling (Ongoing)

### Build Performance Targets

| Metric | Current | Target |
|--------|---------|--------|
| Compile time | ~45s | ~30s (AOT warm cache) |
| Test time | ~90s | ~60s (parallel + VM reuse) |
| Full package | ~90s | ~60s |
| CI total | ~180s | ~90s (layer caching) |

### Observatory Maintenance

The Ψ Observatory (`docs/v6/latest/`) must stay current. Re-run after any structural change:

```bash
bash scripts/observatory/observatory.sh
```

Verify with `receipts/observatory.json` SHA256 hashes. Stale facts are worse than no facts.

Add new instruments when >3 manual grep operations are needed for the same question.

### Dependency Management

All versions managed via parent POM `dependencyManagement`. Current conflicts: **0**.

Enforce on every commit:
```bash
mvn dependency:analyze -DfailOnWarning=true
```

---

## Milestone Summary

| Phase | Quarter | Version | Key Outcome |
|-------|---------|---------|-------------|
| 1 | Q1 2026 | v5.3 | Virtual threads, sealed events, structured concurrency |
| 2 | Q2 2026 | v5.4 | Module boundaries, JPMS, CI gate hardening |
| 3 | Q3 2026 | v5.5 | TLS 1.3, RBAC, SBOM, Prometheus metrics |
| 4 | Q4 2026 | v6.0 | Engine consolidation, stable API surface |
| 5 | 2027 | v7.0 | Distributed cases, AI-native patterns, Schema 5.0 |

---

## Non-Goals

The following are explicitly out of scope for this roadmap:

- **GUI workflow designer**: YAWL Editor remains a separate project outside this engine repository
- **Legacy YAWL 2.x compatibility**: The schema 4.0 migration is complete; no 2.x backward compat shims will be added
- **Runtime container (servlet engine)**: YAWL engine runs embedded or standalone; WAR/EAR packaging is not a roadmap item
- **BPEL/BPMN import**: YAWL uses its own Petri net semantics; no cross-language import is planned

---

*Roadmap reflects codebase state as of `docs/v6/latest/INDEX.md` run `20260217T230032Z`. Re-validate against observatory after major structural changes.*
