# YAWL v6.0.0-Alpha: Comprehensive Upgrade Strategy

**Document Version:** 1.0
**Date:** 2026-02-17
**Status:** Active - Guides all 9 implementing agents
**Owner:** Architecture Lead (claude-sonnet-4-5)

---

## 1. Executive Summary

YAWL v6.0.0-Alpha represents a full-stack modernization of the YAWL workflow engine from its legacy Java EE foundations to a contemporary Java 25 / Jakarta EE 10 / Hibernate 6.x platform. This upgrade preserves all four interface contracts (A, B, E, X), adds first-class MCP and A2A protocol support, and restructures the multi-module build for horizontal scalability.

This document is the authoritative reference for all agents (engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench) executing the v6 initiative.

---

## 2. Scope and Baseline

### 2.1 Current State (v5.2 baseline)

The codebase is already at `6.0.0-Alpha` in the parent POM, reflecting that the dependency declarations and module structure have been lifted to v6 targets. However, a significant portion of source code retains legacy patterns and several modules have compilation exclusions in place because upstream SDKs (MCP, A2A) are not yet on Maven Central.

**Multi-Module Structure (12 modules):**

```
yawl-parent (pom.xml)
├── yawl-utilities        - Shared utility classes
├── yawl-elements         - YSpecification, YTask, YNet, YCondition (Petri net model)
├── yawl-authentication   - YExternalClient, YSessionCache, JWT
├── yawl-engine           - YEngine, YNetRunner, YWorkItem, YPersistenceManager
├── yawl-stateless        - YStatelessEngine, YCaseMonitor (no-persistence variant)
├── yawl-resourcing       - Resource allocation and worklist management
├── yawl-worklet          - Worklet service (dynamic sub-process invocation)
├── yawl-scheduling       - Time-based scheduling (YTimer, YWorkItemTimer)
├── yawl-integration      - MCP server, A2A server, Agent Registry, Zai, SPIFFE
├── yawl-monitoring       - Spring Boot Actuator health/metrics endpoints
├── yawl-webapps          - Legacy servlet-based web applications
└── yawl-control-panel    - Swing-based admin GUI
```

**Interface Layer (engine/interfce/):**

| Interface | Purpose                           | WfMC Mapping    | Transport       |
|-----------|-----------------------------------|-----------------|-----------------|
| A         | Specification management          | Interface 1+5   | HTTP Servlet    |
| B         | Work item and case execution      | Interface 2+3   | HTTP Servlet    |
| E         | Process log/event gateway         | N/A (YAWL ext.) | HTTP Servlet    |
| X         | Exception handling                | N/A (YAWL ext.) | HTTP Servlet    |

**Persistence:** 36 Hibernate mapping files (`.hbm.xml`) across engine, logging, authentication, elements, time, and procletService packages.

**Known compilation exclusions in `yawl-integration/pom.xml`:**
- `a2a/**` - A2A SDK not on Maven Central
- `orderfulfillment/**` - depends on A2A
- `autonomous/**` - depends on A2A
- `zai/**` - Zai AI service
- `processmining/**` - process mining
- `SpiffeEnabledZaiService.java` - depends on Zai
- `SelfPlayTest.java` - depends on A2A
- `mcp/YawlMcpClient.java` - depends on MCP client SDK

### 2.2 Target State (v6.0.0 GA)

- Java 25 source/target with `--enable-preview`
- Jakarta EE 10 namespace throughout (no `javax.*` imports)
- Hibernate 6.6.x with annotation-based mappings (zero `.hbm.xml` files)
- Spring Boot 3.5.x for integration module
- MCP SDK 0.17.x fully integrated and compiling
- A2A SDK 1.0.0.Alpha2 fully integrated and compiling
- JUnit 5 (Jupiter 6.0.x) as primary test framework
- OpenTelemetry 1.52.x for distributed tracing
- Zero compilation exclusions in any module

---

## 3. Architectural Changes: v5.2 to v6.0.0

### 3.1 Jakarta EE 10 Migration (javax → jakarta)

The single most pervasive change is the namespace migration from `javax.*` to `jakarta.*`.

**Affected namespaces:**

| Legacy (javax)                  | Modern (jakarta)                 | Modules Affected          |
|---------------------------------|----------------------------------|---------------------------|
| `javax.servlet.*`               | `jakarta.servlet.*`              | engine, webapps, all HTTP |
| `javax.persistence.*`           | `jakarta.persistence.*`          | engine, elements, logging |
| `javax.xml.bind.*`              | `jakarta.xml.bind.*`             | elements, unmarshal       |
| `javax.mail.*`                  | `jakarta.mail.*`                 | mailSender, resourcing    |
| `javax.activation.*`            | `jakarta.activation.*`           | mailSender                |
| `javax.faces.*`                 | `jakarta.faces.*`                | webapps                   |
| `javax.enterprise.*`            | `jakarta.enterprise.*`           | integration               |
| `javax.ws.rs.*`                 | `jakarta.ws.rs.*`                | interfce/rest             |

**Evidence that InterfaceX_Service is already migrated** (line 24-26 of `InterfaceX_Service.java`):
```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
```

The REST layer (`engine/interfce/rest/`) is likewise already on Jakarta.
**Agent action:** Audit remaining `javax.*` imports across all 89 packages and migrate any stragglers.

### 3.2 Hibernate 6.x Migration (XML → Annotations)

The current engine uses 36 `.hbm.xml` mapping files. Hibernate 6.x still supports `.hbm.xml` but the preferred path is JPA annotations (`@Entity`, `@Table`, `@Column`, etc.).

**Strategic decision:** Maintain `.hbm.xml` for the Alpha release to reduce risk, but annotate all new entity classes. The migration roadmap from XML to annotations is a v6.1 task, documented here as a known item.

**Required changes for Hibernate 6.6.x compatibility:**
1. Replace deprecated `org.hibernate.Criteria` API with `jakarta.persistence.criteria.CriteriaQuery`
2. Replace deprecated `Session.createQuery(String)` with typed `Session.createQuery(String, Class<T>)`
3. Replace `SchemaExport` usage with `SchemaManagementTool` (already partially done in `YPersistenceManager`)
4. Update HQL date functions: `year()`, `month()`, `day()` syntax changes in Hibernate 6
5. `@Type(type="string")` replaced by `@JdbcTypeCode(SqlTypes.VARCHAR)` or simply removed

**HikariCP upgrade:** The engine already declares `hibernate-hikaricp` dependency. Confirm `HikariCP 7.0.x` is compatible (breaking changes in pool configuration property names in HikariCP 6+).

### 3.3 Dual REST Surface: Legacy + Modern

The `engine/interfce/rest/` package documents a deliberate dual-surface strategy:

```
Legacy (servlet):  /yawl/ib, /yawl/ia, /yawl/ie, /yawl/ix
Modern (JAX-RS):   /yawl/api/ib, /yawl/api/ia, /yawl/api/ie, /yawl/api/ix
```

For v6.0.0, the pattern is: **InterfaceBRestResource is fully implemented; A, E, X REST resources forward to legacy servlet implementations.** The v6 goal is to complete the REST implementations for all four interfaces and deprecate the servlet-based transports. The legacy servlet path must remain functional for the full v6.x lifecycle.

### 3.4 MCP and A2A Protocol Integration

These are currently **excluded from compilation** due to SDK availability constraints. The v6.0.0-Alpha integration plan:

**MCP (Model Context Protocol) - `yawl-integration/mcp/`:**
- Server: `YawlMcpServer` exposes YAWL over STDIO transport
- Tools: launch/cancel cases, checkout/complete work items, query specs
- Resources: specification listings, case state, work item queues
- Prompts: workflow design assistance
- Client: `YawlMcpClient` (currently excluded, requires MCP client SDK)

**Resolution path:** Install `io.modelcontextprotocol:mcp:0.17.2` to local Maven repository using `mvn install:install-file` before enabling.

**A2A (Agent-to-Agent) - `yawl-integration/a2a/`:**
- Server: `YawlA2AServer` exposes YAWL as an A2A agent over HTTP REST
- Skills: `LaunchWorkflow`, `QuerySpecifications`, `ManageWorkItems`, `CancelCase`
- Agent Card: capability advertisement for discovery by other agents
- Registry: `AgentRegistry` (port 9090) for multi-agent coordination

**Resolution path:** Install `io.anthropic:a2a-java-sdk-*:1.0.0.Alpha2` to local Maven repository.

**Agent enabling sequence:**
1. Install MCP SDK JAR to local repo
2. Remove MCP exclusions from `yawl-integration/pom.xml`
3. Install A2A SDK JARs to local repo
4. Remove A2A exclusions from `yawl-integration/pom.xml`
5. Verify `mvn clean compile` passes for `yawl-integration`
6. Run integration tests

### 3.5 Java 25 Language Features

The parent POM specifies `--enable-preview` for both compile and test. This enables adoption of Java 25 preview features:

**Available for use in v6.0.0:**
- Records (`record YSpecificationID(String uri, String version, String key) {}`)
- Sealed classes and interfaces (`sealed interface YWorkItemState permits ...`)
- Pattern matching for `instanceof` and `switch`
- Text blocks for XML/JSON templates in tests
- Virtual threads (Project Loom) for work item dispatch

**Recommended adoption pattern:** Convert value-holder classes (e.g., `YSpecificationID`, `YWorkItemID`) to records first. Apply sealed class hierarchy to `YWorkItemStatus`. Use virtual threads in `YAnnouncer` for non-blocking event dispatch.

---

## 4. Module Prioritization

The upgrade proceeds in three tiers, each tier having agent assignments:

### Tier 1: Foundation (Blocks all other work)

**Order:** yawl-utilities → yawl-elements → yawl-authentication

These modules have no YAWL dependencies. They must compile cleanly before any other module can proceed.

**yawl-utilities:**
- Verify all utility classes compile under Java 25
- Replace any remaining `javax.*` with `jakarta.*`
- Ensure no deprecated Apache Commons APIs (commons-lang3 3.20.x breaking changes)

**yawl-elements:**
- `YSpecification`, `YNet`, `YTask`, `YCondition`, `YFlow` - the Petri net model
- 5 `.hbm.xml` files: `YSpecification.hbm.xml`, `YAWLServiceReference.hbm.xml`, `YIdentifier.hbm.xml`, `GroupedMIOutputData.hbm.xml`
- JDOM 2.0.6.1 and Jaxen 1.2.0 for XML processing - verify API compatibility
- Saxon 12.9 for XQuery/XSLT execution

**yawl-authentication:**
- `YExternalClient.hbm.xml` - single Hibernate mapping
- JWT integration (`jjwt 0.13.x`) - new API surface vs. older versions

### Tier 2: Core Engine (Sequential)

**Order:** yawl-engine → yawl-stateless

**yawl-engine** is the highest-risk module. It contains:
- `YEngine` (implements all four interface contracts)
- `YNetRunner` (Petri net execution)
- `YPersistenceManager` (Hibernate session factory)
- `YAnnouncer` / `ObserverGatewayController` (event dispatch)
- `YDefClientsLoader` (service registration)
- 15 `.hbm.xml` mapping files
- Complete logging subsystem (`YEventLogger`, 10 logging table mappings)

**Critical Hibernate 6.6.x changes in YPersistenceManager:**
```java
// BEFORE (Hibernate 5.x / legacy):
session.createCriteria(YWorkItem.class).list();

// AFTER (Hibernate 6.x / JPA Criteria):
CriteriaBuilder cb = session.getCriteriaBuilder();
CriteriaQuery<YWorkItem> cq = cb.createQuery(YWorkItem.class);
cq.from(YWorkItem.class);
session.createQuery(cq).getResultList();
```

**yawl-stateless:**
- Lightweight, no Hibernate dependency
- Mirrors the elements package structure for stateless execution
- `YStatelessEngine`, `YCaseMonitor`, `YCaseImporter`, `YCaseExporter`
- Must remain completely independent of `yawl-engine` (no circular deps)

### Tier 3: Services (Parallel execution possible)

Once Tier 2 compiles, the following modules can be upgraded in parallel:

| Module              | Key Concern                                    | Agent       |
|---------------------|------------------------------------------------|-------------|
| yawl-resourcing     | Jakarta Faces migration, resource schema       | engineer    |
| yawl-worklet        | Worklet composition service, XML mapping       | engineer    |
| yawl-scheduling     | Timer service, workdays holiday calendar       | engineer    |
| yawl-monitoring     | Spring Boot Actuator 3.5.x compatibility       | engineer    |
| yawl-webapps        | Servlet 6.x migration, JSF 4.x migration       | engineer    |
| yawl-control-panel  | Swing GUI, minimal dependencies                | engineer    |

### Tier 4: Integration (Depends on SDK availability)

**yawl-integration** depends on all Tier 1-3 modules being stable plus external SDKs being available.

**Parallel sub-tasks once SDKs are available:**
- MCP server completion and testing
- A2A server completion and testing
- Agent Registry enabling
- SPIFFE identity integration
- Process mining integration
- Zai AI service integration

---

## 5. Dependency Modernization Requirements

### 5.1 Hard Requirements (Breaking changes that must be addressed)

| Dependency            | From      | To           | Breaking Change                                |
|-----------------------|-----------|--------------|------------------------------------------------|
| Java                  | 17        | 25           | preview features, module system enforcement    |
| Jakarta Servlet       | 4.x       | 6.1.0        | namespace: javax.servlet → jakarta.servlet     |
| Jakarta Persistence   | 2.x       | 3.2.0        | namespace: javax.persistence → jakarta.persistence |
| Hibernate ORM         | 5.x       | 6.6.42.Final | Criteria API removed, HQL syntax changes       |
| Jakarta XML Bind      | 2.x       | 4.0.5        | namespace: javax.xml.bind → jakarta.xml.bind   |
| Jakarta Mail          | 1.x       | 2.1.5        | namespace: javax.mail → jakarta.mail           |
| Log4j                 | 2.x       | 2.25.3       | configuration format changes (minor)           |
| JUnit                 | 4.13.2    | Jupiter 6.x  | annotation changes, lifecycle model            |
| HikariCP              | 4.x       | 7.0.2        | configuration property renames (pool sizes)    |
| commons-fileupload    | 1.5.x     | 1.6.0        | API changes in servlet integration             |

### 5.2 New Dependencies (Not in v5.2)

| Dependency               | Version       | Purpose                                   |
|--------------------------|---------------|-------------------------------------------|
| Spring Boot Starter Web  | 3.5.10        | Integration module HTTP server             |
| Spring Boot Actuator     | 3.5.10        | Health/metrics endpoints                  |
| OpenTelemetry API+SDK    | 1.52.0        | Distributed tracing                       |
| Resilience4j             | 2.3.0         | Circuit breaker, retry, rate limiter      |
| MCP SDK                  | 0.17.2        | Model Context Protocol server             |
| A2A SDK                  | 1.0.0.Alpha2  | Agent-to-Agent protocol                   |
| JSpecify                 | 1.0.0         | Null-safety annotations                   |
| OkHttp                   | 5.1.0         | HTTP client for integration layer         |
| JJWT                     | 0.13.0        | JWT for authentication                    |
| Micrometer               | 1.16.3        | Metrics abstraction                       |
| JMH                      | 1.37          | Performance benchmarking                  |

### 5.3 Retained Dependencies (Stable, version-bumped)

| Dependency       | v5.2 Version  | v6.0 Version   | Notes                                     |
|------------------|---------------|----------------|-------------------------------------------|
| JDOM2            | 2.0.6         | 2.0.6.1        | Minor patch, XML processing               |
| Jaxen            | 1.2.0         | 1.2.0          | Unchanged, XPath for JDOM                 |
| Saxon-HE         | 9.x           | 12.9           | Major version, XSLT/XQuery processor      |
| Jackson          | 2.14.x        | 2.19.4         | JSON serialization                        |
| Gson             | 2.9.x         | 2.13.2         | Secondary JSON library                    |
| Commons-lang3    | 3.12.x        | 3.20.0         | Utility library                           |
| H2               | 2.1.x         | 2.4.240        | In-memory test database                   |
| PostgreSQL       | 42.5.x        | 42.7.7         | Production database driver                |

### 5.4 Removed Dependencies

| Dependency               | Reason for Removal                              |
|--------------------------|-------------------------------------------------|
| `javax.xml.soap-api`     | Replaced by `jakarta.xml.soap` where needed     |
| Legacy `commons-codec`   | Replaced by `org.apache.commons:commons-codec`  |
| Legacy `commons-io`      | Replaced by `org.apache.commons:commons-io`     |

Note: The POM currently retains both old and new groupIds for commons-codec and commons-io as transition shims. These dual declarations should be resolved to single groupIds at v6.0 GA.

---

## 6. Backward Compatibility and Migration Path

### 6.1 Interface Contract Preservation

The four interface contracts are the primary backward compatibility guarantee. They define what YAWL services, the YAWL Designer, and third-party integrations depend on.

**InterfaceAManagement (design-time contract):**
```java
// ALL of the following method signatures must be preserved at v6.0:
List<YSpecificationID> addSpecifications(String specificationStr,
    boolean ignoreErrors, YVerificationHandler errorMessages)
    throws JDOMException, IOException, YPersistenceException;

void unloadSpecification(YSpecificationID specID)
    throws YStateException, YPersistenceException;

Set<YIdentifier> getCasesForSpecification(YSpecificationID specID);

void cancelCase(YIdentifier id)
    throws YPersistenceException, YEngineStateException;
```

**InterfaceBClient (runtime contract):**
```java
// ALL of the following must be preserved:
String launchCase(YSpecificationID specID, String caseParams,
    URI completionObserver, YLogDataItemList logData)
    throws YStateException, YDataStateException, YPersistenceException,
           YEngineStateException, YLogException, YQueryException;

YWorkItem startWorkItem(YWorkItem workItem, YClient client)
    throws YStateException, YDataStateException, YQueryException,
           YPersistenceException, YEngineStateException;

void completeWorkItem(YWorkItem workItem, String data,
    String logPredicate, WorkItemCompletion flag)
    throws YStateException, YDataStateException, YQueryException,
           YPersistenceException, YEngineStateException;
```

**InterfaceX_Service (exception handling contract):**
```java
// ALL exception event handlers must be preserved:
void handleCheckCaseConstraintEvent(YSpecificationID specID,
    String caseID, String data, boolean precheck);

void handleCheckWorkItemConstraintEvent(WorkItemRecord wir,
    String data, boolean precheck);

String handleWorkItemAbortException(WorkItemRecord wir, String caseData);
void handleTimeoutEvent(WorkItemRecord wir, String taskList);
void handleResourceUnavailableException(String resourceID,
    WorkItemRecord wir, String caseData, boolean primary);
String handleConstraintViolationException(WorkItemRecord wir, String caseData);
void handleCaseCancellationEvent(String caseID);
```

**InterfaceE (event/log contract):**
- `YLogGateway` and `YLogGatewayClient` method signatures preserved
- Log table schema extensions only (no column removals)

### 6.2 HTTP API Backward Compatibility

The legacy servlet endpoints are the external HTTP API. The following URL patterns must continue to function in v6.0.0:

```
/yawl/ia       - InterfaceA servlet
/yawl/ib       - InterfaceB servlet
/yawl/ie       - InterfaceE servlet
/yawl/ix       - InterfaceX servlet
```

The new REST endpoints are additive:
```
/yawl/api/ia   - InterfaceA JAX-RS resource (new in v6)
/yawl/api/ib   - InterfaceB JAX-RS resource (new in v6)
/yawl/api/ie   - InterfaceE JAX-RS resource (new in v6)
/yawl/api/ix   - InterfaceX JAX-RS resource (new in v6)
```

### 6.3 XML Specification Format

YAWL specification files (`.yawl`, `.ywl`) are versioned XML. The schema is defined in `schema/YAWL_Schema4.0.xsd`.

**Compatibility guarantee:** v6.0.0 must load and execute all v5.2-compatible specification files without modification. The schema version is preserved at 4.0. New schema versions (5.0) may be introduced in v6.1+ for new language features.

**Validation:** `xmllint --schema schema/YAWL_Schema4.0.xsd spec.xml` must continue to pass for all existing specifications.

### 6.4 Database Schema Migration

The Hibernate `.hbm.xml` files define the physical schema. The upgrade from Hibernate 5.x to 6.x must produce an identical or superset schema when using `hbm2ddl.auto = update`.

**Schema verification protocol:**
1. Export schema from v5.2 instance using `SchemaExport`
2. Initialize v6.0.0 Hibernate with an empty database
3. Diff the two schemas
4. Any column removals or type changes require explicit migration scripts
5. Test with `mvn clean test -Ddatabase.type=h2` (H2 in-memory) and against PostgreSQL 17

**Known schema concerns:**
- Hibernate 6.x changes default column naming strategy
- Boolean type mapping changes (`bit` vs `boolean`)
- `@Lob` annotation behavior changes for `String` fields

### 6.5 Session Token Compatibility

The engine session token format (used in servlet authentication) must remain compatible. If `YSessionCache` is migrated to JWT, existing session tokens should be invalidated cleanly with appropriate HTTP 401 responses rather than causing NullPointerExceptions.

---

## 7. Interface Contract Preservation: Detailed Analysis

### 7.1 YEngine as Central Contract Implementor

`YEngine` implements all four interfaces:
```java
public class YEngine implements InterfaceADesign,
                                InterfaceAManagement,
                                InterfaceBClient,
                                InterfaceBInterop {
```

`InterfaceADesign` is intentionally empty (WfMC Interface 1 placeholder). The other three carry substantive contracts.

**Risk:** Any change to `YEngine` that breaks an implemented interface method signature will break compilation of `EngineGatewayImpl` and all registered YAWL services. Changes to `YEngine` must be checked against the full set of callers.

### 7.2 REST Layer Completion (v6 Milestone)

The current state (`engine/interfce/rest/`):
- `InterfaceBRestResource` - fully implemented
- `InterfaceARestResource` - stub, delegates to legacy servlet
- `InterfaceERestResource` - stub, delegates to legacy servlet
- `InterfaceXRestResource` - stub, delegates to legacy servlet

**v6.0.0 deliverable:** Complete `InterfaceARestResource` implementation using `EngineGatewayImpl` directly. The E and X REST resources remain delegating stubs (v6.1 deliverable).

### 7.3 Protocol Extension Points

**MCP Tool Registration Pattern:**
```
YawlMcpServer
  → McpServerFeatures.SyncSpec
    → Tool("launch_case", handler)
    → Tool("cancel_case", handler)
    → Tool("checkout_workitem", handler)
    → Tool("complete_workitem", handler)
    → Resource("specifications", supplier)
    → Resource("cases", supplier)
    → Resource("workitems", supplier)
```

**A2A Skill Registration Pattern:**
```
YawlA2AServer
  → TaskHandlerRegistry
    → Skill("LaunchWorkflow", handler)
    → Skill("QuerySpecifications", handler)
    → Skill("ManageWorkItems", handler)
    → Skill("CancelCase", handler)
```

Both protocol integrations route through `InterfaceBClient` and `InterfaceAManagement` contracts. The integration layer must never bypass the interface contracts to call `YEngine` directly.

---

## 8. Integration with MCP and A2A Protocols

### 8.1 MCP Architecture

```
AI Assistant (MCP Client)
        |
        | STDIO / SSE transport
        v
YawlMcpServer (io.modelcontextprotocol.server.McpServer)
        |
        | calls
        v
InterfaceB_EnvironmentBasedClient  (HTTP client to engine)
        |
        | HTTP POST
        v
InterfaceB_EngineBasedServer (Servlet @ /yawl/ib)
        |
        | calls
        v
EngineGatewayImpl → YEngine
```

**Key design invariant:** The MCP server is a thin adapter. All business logic remains in the engine. The MCP server only translates between MCP tool calls and YAWL interface operations.

### 8.2 A2A Architecture

```
A2A Client (external agent)
        |
        | HTTP POST /a2a/tasks/send
        v
YawlA2AServer (Spring Boot)
        |
        | routes to skill handler
        v
YawlSkill (LaunchWorkflow / QuerySpecs / etc.)
        |
        | calls
        v
YStatelessEngine  (for stateless operations)
        or
InterfaceB_EnvironmentBasedClient  (for stateful engine operations)
```

**Agent discovery:** The `AgentRegistry` (port 9090) provides service discovery. The A2A server registers its capabilities on startup and sends heartbeats. Other A2A agents discover YAWL capabilities via `GET /agents/by-capability?domain=Workflow`.

### 8.3 Enabling MCP and A2A in pom.xml

The `yawl-integration/pom.xml` contains commented-out SDK dependencies and build exclusions. The exact XML changes required when SDKs become available:

**Step 1: Uncomment in `yawl-integration/pom.xml`:**
```xml
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp</artifactId>
</dependency>
<!-- ... other MCP artifacts -->

<dependency>
    <groupId>io.anthropic</groupId>
    <artifactId>a2a-java-sdk-spec</artifactId>
</dependency>
<!-- ... other A2A artifacts -->
```

**Step 2: Remove compiler exclusions:**
```xml
<!-- Remove these from maven-compiler-plugin excludes: -->
<exclude>**/a2a/**</exclude>
<exclude>**/orderfulfillment/**</exclude>
<exclude>**/autonomous/**</exclude>
<exclude>**/zai/**</exclude>
<exclude>**/processmining/**</exclude>
<exclude>**/SpiffeEnabledZaiService.java</exclude>
<exclude>**/SelfPlayTest.java</exclude>
<exclude>**/mcp/YawlMcpClient.java</exclude>
```

**Step 3: Verify compilation and run integration tests.**

---

## 9. Risk Mitigation and Rollback Strategy

### 9.1 Risk Register

| Risk                                  | Probability | Impact   | Mitigation                                         |
|---------------------------------------|-------------|----------|----------------------------------------------------|
| Hibernate 6 schema migration breaks   | High        | Critical | Schema diff + migration script before deploy       |
| MCP/A2A SDKs API incompatibility      | Medium      | High     | SDK pinned to exact versions; adapter pattern      |
| Java 25 preview features instability  | Low         | Medium   | Preview features opt-in; core code avoids them     |
| Legacy spec files fail to load        | Medium      | Critical | Integration test suite with 50+ reference specs    |
| Interface contract breakage           | Low         | Critical | Compile-time checks + regression test suite        |
| HikariCP 7.x config breakage          | Medium      | High     | Test matrix: H2 + PostgreSQL + MySQL               |
| Spring Boot 3.5 breaking changes      | Low         | Medium   | Spring Boot integration limited to yawl-integration |
| Commons-fileupload servlet API change | High        | Low      | Migrate to Jakarta servlet multipart API directly  |

### 9.2 Rollback Strategy

**Trigger conditions for rollback:**
- Any test in `YEngineTest`, `YNetRunnerTest`, or `YWorkItemTest` fails after migration
- Schema diff shows column removals or type changes that cannot be forward-migrated
- Specification files from the reference corpus fail to load or execute

**Rollback procedure:**
1. Revert to the last known-good git commit on `main`
2. The parent POM version `6.0.0-Alpha` is preserved (it is a label, not a barrier)
3. Document the failure mode and create a targeted fix branch
4. Re-apply migration incrementally, verifying each step

**Branch strategy:**
```
main                     - stable, always compilable
feature/v6-tier1-foundations   - utilities + elements + authentication
feature/v6-tier2-engine        - engine + stateless
feature/v6-tier3-services      - parallel service modules
feature/v6-tier4-integration   - MCP + A2A (gated on SDK availability)
```

Each feature branch is merged to main only after:
1. `mvn clean compile` passes
2. `mvn clean test` passes (100%)
3. Architecture review sign-off

### 9.3 Incremental Verification Gates

**Gate 1 - Tier 1 complete:**
```bash
mvn -pl yawl-utilities,yawl-elements,yawl-authentication clean test
```
Expected: 0 compilation errors, 100% test pass

**Gate 2 - Tier 2 complete:**
```bash
mvn -pl yawl-engine,yawl-stateless clean test -Ddatabase.type=h2
```
Expected: 0 compilation errors, 100% test pass, schema initializes correctly

**Gate 3 - Tier 3 complete:**
```bash
mvn clean test -Ddatabase.type=h2
```
Expected: All 12 modules compile and test successfully

**Gate 4 - Full integration:**
```bash
mvn clean package -Ddatabase.type=postgresql
xmllint --schema schema/YAWL_Schema4.0.xsd test/fixtures/order_fulfillment.yawl
```
Expected: Package succeeds, all reference specs validate

---

## 10. Agent Task Assignments

### 10.1 Agent Responsibility Matrix

| Module                     | Primary Agent  | Secondary Agent | Gate   |
|----------------------------|----------------|-----------------|--------|
| yawl-utilities             | engineer       | validator       | Gate 1 |
| yawl-elements              | engineer       | reviewer        | Gate 1 |
| yawl-authentication        | engineer       | reviewer        | Gate 1 |
| yawl-engine                | engineer       | architect       | Gate 2 |
| yawl-stateless             | engineer       | tester          | Gate 2 |
| yawl-resourcing            | engineer       | reviewer        | Gate 3 |
| yawl-worklet               | engineer       | tester          | Gate 3 |
| yawl-scheduling            | engineer       | tester          | Gate 3 |
| yawl-monitoring            | engineer       | prod-val        | Gate 3 |
| yawl-webapps               | engineer       | reviewer        | Gate 3 |
| yawl-control-panel         | engineer       | reviewer        | Gate 3 |
| yawl-integration           | integrator     | architect       | Gate 4 |
| Schema migration scripts   | integrator     | prod-val        | Gate 2 |
| Test suite migration (JUnit5)| tester       | validator       | Gate 3 |
| Performance benchmarking   | perf-bench     | prod-val        | Gate 4 |
| Production validation      | prod-val       | validator       | Gate 4 |

### 10.2 Cross-Cutting Concerns (All Agents)

Every agent operating on any source file must:

1. **No `javax.*` imports** - Replace with `jakarta.*` counterparts
2. **No raw types** - `Set` → `Set<YWorkItem>`, `List` → `List<YSpecificationID>`
3. **No deprecated Hibernate APIs** - Use JPA Criteria, typed queries
4. **JSpecify annotations** - Add `@Nullable` / `@NonNull` to public method signatures
5. **Log4j over System.out** - No `System.out.println` in production code
6. **No mock/stub code** - Per CLAUDE.md H guards; real implementations only

### 10.3 Test Strategy

**Existing tests (JUnit 4 format):**
- Maintain JUnit 4 compatibility via `junit-vintage-engine` bridge
- New tests written in JUnit 5 (Jupiter)
- Migration of existing test classes to JUnit 5 is a separate, tracked effort

**Test categories:**
```
Unit:        YSpecificationTest, YWorkItemTest, YIdentifierTest
Integration: YEngineTest, YNetRunnerTest (requires H2 database)
System:      YSystemTest (full stack, PostgreSQL)
Performance: YWorkflowBenchmark (JMH)
```

**Coverage target:** 80% line coverage (currently waived in POM with comment; enable at Gate 3).

---

## 11. Hibernate Mapping Migration Reference

### 11.1 Current Mapping Files by Module

**yawl-elements (5 files):**
- `YSpecification.hbm.xml`
- `YAWLServiceReference.hbm.xml`
- `GroupedMIOutputData.hbm.xml`
- `elements/state/YIdentifier.hbm.xml`

**yawl-engine (10 files):**
- `YWorkItem.hbm.xml`
- `YNetRunner.hbm.xml`
- `YNetData.hbm.xml`
- `YCaseNbrStore.hbm.xml`
- `interfce/WorkItemRecord.hbm.xml`
- `time/YWorkItemTimer.hbm.xml`
- `time/YLaunchDelayer.hbm.xml`
- `time/workdays/Holiday.hbm.xml`
- `time/workdays/HolidayRegion.hbm.xml`
- `exceptions/Problem.hbm.xml`

**yawl-authentication (1 file):**
- `YExternalClient.hbm.xml`

**yawl-monitoring/logging (10 files):**
- `logging/table/YAuditEvent.hbm.xml`
- `logging/table/YLogDataItemInstance.hbm.xml`
- `logging/table/YLogDataType.hbm.xml`
- `logging/table/YLogEvent.hbm.xml`
- `logging/table/YLogNet.hbm.xml`
- `logging/table/YLogNetInstance.hbm.xml`
- `logging/table/YLogService.hbm.xml`
- `logging/table/YLogSpecification.hbm.xml`
- `logging/table/YLogTask.hbm.xml`
- `logging/table/YLogTaskInstance.hbm.xml`

**procletService (10 files):**
- Various `StoredXxx.hbm.xml` files for the proclet collaboration service

**Total: 36 mapping files** - All functional in Hibernate 6.6.x with `.hbm.xml` support. Annotation migration is v6.1+.

### 11.2 Hibernate 6 Configuration Update

`YPersistenceManager` already uses `StandardServiceRegistryBuilder` and `MetadataSources`. The following configuration property names change in Hibernate 6:

| Hibernate 5 Property           | Hibernate 6 Property              |
|--------------------------------|-----------------------------------|
| `hibernate.hbm2ddl.auto`       | `jakarta.persistence.schema-generation.database.action` |
| `hibernate.dialect`            | `hibernate.dialect` (unchanged)   |
| `hibernate.connection.driver_class` | `jakarta.persistence.jdbc.driver` |
| `hibernate.connection.url`     | `jakarta.persistence.jdbc.url`    |
| `hibernate.connection.username`| `jakarta.persistence.jdbc.user`   |
| `hibernate.connection.password`| `jakarta.persistence.jdbc.password` |

The legacy property names still work in Hibernate 6.6.x via backward-compatibility shims, but should be migrated for clarity.

---

## 12. Java 25 Feature Adoption Plan

### Phase 1: Alpha (v6.0.0) - Safe Adopting

Adopt features that have been stable for multiple releases:

| Feature                        | Application                                     |
|--------------------------------|-------------------------------------------------|
| Records                        | `YSpecificationID`, `YWorkItemID`, `YWorkItemStatus` value types |
| Text blocks                    | XML templates in test fixtures                  |
| Pattern matching instanceof    | Type narrowing in `YEngine`, `YNetRunner`       |
| Enhanced switch expressions    | `YWorkItemStatus` state transitions             |
| `java.util.concurrent` updates | `StructuredTaskScope` for work item batches     |

### Phase 2: Beta (v6.1.0) - Virtual Threads

| Feature                        | Application                                     |
|--------------------------------|-------------------------------------------------|
| Virtual threads (Project Loom) | `YAnnouncer` event dispatch                    |
| Structured concurrency         | Multi-workitem batch operations                 |
| Scoped values                  | Request-scoped session handles                  |

### Phase 3: GA (v6.2.0) - Sealed Hierarchies

| Feature                        | Application                                     |
|--------------------------------|-------------------------------------------------|
| Sealed classes/interfaces      | `sealed interface YWorkItemStatus`              |
| String templates (if finalized)| XML spec generation                             |

---

## 13. Quick Reference for Agents

### 13.1 File Locations

| Component                    | Path                                                                    |
|------------------------------|-------------------------------------------------------------------------|
| Parent POM                   | `/home/user/yawl/pom.xml`                                               |
| Engine module POM            | `/home/user/yawl/yawl-engine/pom.xml`                                  |
| Integration module POM       | `/home/user/yawl/yawl-integration/pom.xml`                             |
| InterfaceA contracts         | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceA/` |
| InterfaceB contracts         | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/` |
| InterfaceE contracts         | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceE/` |
| InterfaceX contracts         | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceX/` |
| REST resources               | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/rest/`    |
| YEngine                      | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`      |
| YPersistenceManager          | `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java` |
| MCP Server                   | `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/`        |
| A2A Server                   | `/home/user/yawl/src/org/yawlfoundation/yawl/integration/a2a/`        |
| Agent Registry               | `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/registry/` |
| YAWL Schema                  | `/home/user/yawl/schema/YAWL_Schema4.0.xsd`                           |
| Hibernate mapping files      | Distributed across `src/` - see Section 11.1                          |

### 13.2 Build Commands

```bash
# Verify a single module
mvn -pl yawl-engine clean compile

# Verify with upstream dependencies
mvn -pl yawl-engine -am clean compile

# Run tests for engine
mvn -pl yawl-engine clean test -Ddatabase.type=h2

# Full build
mvn clean package

# Schema validation
xmllint --schema schema/YAWL_Schema4.0.xsd <spec-file>
```

### 13.3 Non-Negotiable Constraints

1. `YEngine` implements `InterfaceADesign`, `InterfaceAManagement`, `InterfaceBClient`, `InterfaceBInterop` - these four declarations must never be removed.
2. All method signatures in `InterfaceAManagement.java`, `InterfaceBClient.java`, `InterfaceX_Service.java`, and `YLogGateway.java` are frozen contracts. No signature changes without a versioned deprecation cycle.
3. The legacy servlet URLs (`/yawl/ib`, `/yawl/ia`, `/yawl/ie`, `/yawl/ix`) must remain functional.
4. `YAWL_Schema4.0.xsd` must validate existing specification files unchanged.
5. All 36 `.hbm.xml` mapping files must produce the same schema structure as v5.2 in Hibernate 6.6.x.
6. The `yawl-stateless` module must compile with zero dependency on `yawl-engine`.

---

## 14. Success Criteria

### v6.0.0-Alpha Exit Criteria

- [ ] All 12 modules compile without errors or exclusions under Java 25
- [ ] `mvn clean test` passes 100% across all modules
- [ ] Zero `javax.*` imports remain in any source file
- [ ] All 36 `.hbm.xml` files produce correct schema under Hibernate 6.6.x
- [ ] InterfaceA, B, E, X servlet URLs return valid responses
- [ ] InterfaceA and B JAX-RS REST resources are fully implemented
- [ ] YAWL Schema 4.0 validates existing reference specifications
- [ ] MCP server compiles and responds to `tools/list` with correct tool definitions
- [ ] A2A server compiles and responds to `/.well-known/agent.json` with capability card

### v6.0.0 GA Exit Criteria (beyond Alpha)

- [ ] MCP SDK and A2A SDK available on Maven Central (or published to company repo)
- [ ] All compilation exclusions removed from integration module
- [ ] Agent Registry functional with health monitoring
- [ ] JaCoCo coverage threshold re-enabled at 80% minimum
- [ ] OWASP dependency-check passes with no CVSS >= 7 findings
- [ ] Performance benchmarks (JMH) establish regression baselines
- [ ] Production validation on PostgreSQL 17 with real specification corpus
- [ ] Security audit profile passes

---

*This document was authored by the YAWL Architecture Lead agent on 2026-02-17 based on direct analysis of all 89 packages, 36 Hibernate mapping files, 12 module POMs, and 4 interface contracts in the `/home/user/yawl` repository.*
