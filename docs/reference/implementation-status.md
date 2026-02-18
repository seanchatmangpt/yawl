# Implementation Status: Aspirational vs. Tested Capabilities

> WARNING: Docs in this repo may describe aspirational features.
> Check this file before trusting any doc to describe working behavior.
> Last verified: 2026-02-18

This document records the precise delta between what YAWL documentation describes
(including ADRs, Diataxis explanation pages, and tutorial content) and what is
verifiably implemented in Java source code and covered by tests as of 2026-02-18.

Methodology: implementation is confirmed by presence of `.java` files in `src/`.
Test coverage is confirmed by presence of test classes in `test/`. Neither the
correctness of individual test assertions nor build pass/fail status is audited here.

---

## 1. Implemented and Tested

These subsystems have Java source files in `src/` and corresponding test classes
in `test/`.

### 1.1 Engine Core (Stateful)

Key classes:
- `src/org/yawlfoundation/yawl/engine/YEngine.java` — singleton workflow engine
- `src/org/yawlfoundation/yawl/engine/YNetRunner.java` — Petri net token execution
- `src/org/yawlfoundation/yawl/engine/YWorkItem.java` — work item lifecycle
- `src/org/yawlfoundation/yawl/engine/YWorkItemStatus.java` — sealed status enum
- `src/org/yawlfoundation/yawl/engine/YWorkItemRepository.java` — in-memory repository
- `src/org/yawlfoundation/yawl/engine/YPersistenceManager.java` — Hibernate persistence
- `src/org/yawlfoundation/yawl/engine/YEngineRestorer.java` — crash recovery
- `src/org/yawlfoundation/yawl/engine/YAnnouncer.java` — observer gateway notifications

Test classes (22 files under `test/org/yawlfoundation/yawl/engine/`):
- `TestYEngineInit`, `TestYNetRunner`, `TestYWorkItem`, `TestYWorkItemID`
- `TestYWorkItemRepository`, `TestYPersistenceManager`, `TestYSpecificationID`
- `TestEngineSystem1`, `TestEngineSystem2` — system-level scenario tests
- `TestCaseCancellation`, `TestConcurrentCaseExecution`, `TestDeadlockingWorkflows`
- `TestOrJoin` — or-join semantics (most subtle Petri net construct)
- `TestPersistence` — Hibernate round-trip
- `TestSimpleExecutionUseCases`, `TestImproperCompletion`
- `TestEngineAgainstABeta4Spec`, `TestEngineAgainstImproperCompletionOfASubnet`
- `EngineIntegrationTest`, `NetRunnerBehavioralTest`, `TaskLifecycleBehavioralTest`
- `HibernateV6ApiTest` — persistence API compatibility
- `VirtualThreadPinningTest` — virtual thread deadlock detection

What is tested: engine initialisation, case launch, work item checkout/complete,
case cancellation, or-join resolution, concurrent execution, deadlock detection,
Hibernate persistence, and crash recovery via restoration.

### 1.2 Workflow Elements

Key classes (`src/org/yawlfoundation/yawl/elements/`):
- `YSpecification`, `YNet`, `YTask`, `YAtomicTask`, `YCompositeTask`
- `YCondition`, `YInputCondition`, `YOutputCondition`, `YFlow`
- `YMultiInstanceAttributes`, `YTimerParameters`, `YDecomposition`

Test classes (15 files under `test/org/yawlfoundation/yawl/elements/`):
- `TestYAtomicTask`, `TestYCompositeTask`, `TestYNet`, `TestYSpecification`
- `TestYExternalTask`, `TestYExternalNetElement`, `TestYFlowsInto`
- `TestYInputCondition`, `TestYOutputCondition`, `TestYExternalCondition`
- `TestYMultiInstanceAttributes`, `TestYTimerParametersParsing`
- `TestDataParsing`, `YSpecVersionTest`
- State sub-package: `TestYIdentifier`, `TestYMarking`, `TestYSetOfMarkings`

What is tested: element construction, flow validation, multi-instance attribute
parsing, timer parameter parsing, specification version comparison.

### 1.3 Stateless Engine

Key classes (`src/org/yawlfoundation/yawl/stateless/`):
- `YStatelessEngine.java` — stateless execution entry point
- `engine/YEngine.java`, `engine/YNetRunner.java`, `engine/YWorkItem.java`
- `monitor/YCaseMonitor.java`, `monitor/YCaseImporter.java`, `monitor/YCaseExporter.java`
- `monitor/YCaseMonitoringService.java`, `monitor/YCaseImportExportService.java`
- `listener/` — event listener interfaces: case, work item, exception, log, timer events
- `listener/event/` — sealed event hierarchy: `YEvent`, `YCaseEvent`, `YWorkItemEvent`, etc.
- `unmarshal/YMarshal.java`, `YSpecificationParser.java`, `YDecompositionParser.java`

Integration test classes (`test/org/yawlfoundation/yawl/integration/`):
- `YStatelessEngineApiIT.java`
- `YSpecificationLoadingIT.java`

Additional: `src/org/yawlfoundation/yawl/stateless/YSRestoreTest.java` is a
developer harness in `src/` (not in `test/`).

What is tested: stateless engine API surface and specification loading. Full
event-driven pipeline (listener chains, timer events) has no dedicated test class.

### 1.4 Interface A (Design-Time) and Interface B (Client/Runtime)

Key classes (`src/org/yawlfoundation/yawl/engine/interfce/`):
- `interfaceA/InterfaceADesign.java`, `InterfaceAManagement.java`
- `interfaceA/InterfaceA_EngineBasedServer.java`, `InterfaceA_EnvironmentBasedClient.java`
- `interfaceB/InterfaceBClient.java`, `InterfaceBWebsideController.java`
- `interfaceB/InterfaceB_EngineBasedServer.java`, `InterfaceB_EnvironmentBasedServer.java`
- `interfaceB/InterfaceB_EnvironmentBasedClient.java`, `InterfaceB_HttpsEngineBasedClient.java`
- `interfaceE/YLogGateway.java`, `YLogGatewayClient.java`
- `interfaceX/ExceptionGateway.java`
- `EngineGateway.java`, `EngineGatewayImpl.java` — gateway facade
- `Marshaller.java`, `WorkItemRecord.java`, `WorkItemIdentity.java`, `WorkItemMetadata.java`

Test classes (`test/org/yawlfoundation/yawl/engine/interfce/`):
- `WorkItemRecordTest.java`, `WorkItemIdentityTest.java`
- `WorkItemMetadataTest.java`, `WorkItemTimingTest.java`
- `Interface_ClientVirtualThreadsTest.java`

What is tested: work item record serialisation/deserialisation, identity equality,
metadata handling, virtual-thread concurrency of the interface client. The servlet
handlers (`InterfaceA_EngineBasedServer`, `InterfaceB_EngineBasedServer`) have no
dedicated unit tests; they are covered indirectly via engine integration tests.

### 1.5 Authentication and Sessions

Key classes (`src/org/yawlfoundation/yawl/authentication/`):
- `JwtManager.java` — JWT generation and validation
- `YSessionCache.java`, `YSession.java`, `YExternalSession.java`, `YServiceSession.java`
- `CsrfProtectionFilter.java`, `CsrfTokenManager.java`
- `YClient.java`, `YExternalClient.java`

Test classes (`test/org/yawlfoundation/yawl/authentication/`):
- `TestJwtManager.java`, `TestConnections.java`, `V6SecurityTest.java`

What is tested: JWT creation, signing, validation, session cache behaviour,
connection management. CSRF filter has no dedicated test.

### 1.6 Autonomous Agent Framework (Core)

Key classes (`src/org/yawlfoundation/yawl/integration/autonomous/`):
- `AutonomousAgent.java` — abstract agent lifecycle
- `AgentRegistry.java`, `AgentFactory.java`, `AgentCapability.java`
- `AgentConfiguration.java`, `GenericPartyAgent.java`
- Strategy interfaces: `DiscoveryStrategy`, `EligibilityReasoner`, `DecisionReasoner`, `OutputGenerator`
- Implementations: `PollingDiscoveryStrategy`, `StaticMappingReasoner`
- `TemplateDecisionReasoner`, `ZaiDecisionReasoner`, `ZaiEligibilityReasoner`
- `TemplateOutputGenerator`, `XmlOutputGenerator`
- Resilience: `CircuitBreaker.java`, `RetryPolicy.java`, `FallbackHandler.java`
- Registry: `AgentHealthMonitor`, `AgentInfo`, `AgentRegistryClient`
- Observability: `HealthCheck`, `MetricsCollector`, `StructuredLogger`
- Config: `AgentConfigLoader.java`
- Launcher: `GenericWorkflowLauncher.java`
- External: `ZaiService.java` (ZAI service integration interface)

Test classes (`test/org/yawlfoundation/yawl/integration/autonomous/`):
- `AgentCapabilityTest.java`, `AgentConfigurationTest.java`, `AgentRegistryTest.java`
- `CircuitBreakerTest.java`, `RetryPolicyTest.java`, `StaticMappingReasonerTest.java`

What is tested: capability matching, agent configuration loading, registry operations,
circuit breaker state transitions, retry policy with backoff, static mapping reasoner
output. LLM-backed reasoners (`ZaiDecisionReasoner`, `ZaiEligibilityReasoner`) are
implemented but have no dedicated tests — they require an external ZAI/LLM endpoint.

### 1.7 MCP Integration (Model Context Protocol)

Key classes (`src/org/yawlfoundation/yawl/integration/mcp/`):
- `YawlMcpServer.java`, `YawlMcpClient.java`
- `spring/YawlMcpSpringApplication.java`, `YawlMcpConfiguration.java`, `YawlMcpProperties.java`
- `spring/YawlMcpTool.java`, `YawlMcpResource.java`, `YawlMcpToolRegistry.java`
- `sdk/McpServer.java`, `McpSyncServer.java`, `McpSyncServerExchange.java`
- `sdk/StdioServerTransportProvider.java`, `ZaiFunctionService.java`
- `spec/YawlToolSpecifications.java`, `YawlPromptSpecifications.java`
- `resource/YawlResourceProvider.java`

Test classes:
- `test/org/yawlfoundation/yawl/integration/mcp/YawlMcpServerTest.java`
- `test/org/yawlfoundation/yawl/integration/YMcpServerAvailabilityIT.java`

What is tested: MCP server instantiation and tool registration. End-to-end
MCP tool invocation against a live engine is tested in the IT class.

### 1.8 A2A Integration (Agent-to-Agent Protocol)

Key classes (`src/org/yawlfoundation/yawl/integration/a2a/`):
- `YawlA2AServer.java`, `YawlA2AClient.java`, `YawlEngineAdapter.java`
- `auth/A2AAuthenticationProvider.java` (interface)
- `auth/ApiKeyAuthenticationProvider.java`, `JwtAuthenticationProvider.java`
- `auth/SpiffeAuthenticationProvider.java`, `CompositeAuthenticationProvider.java`
- `auth/AuthenticatedPrincipal.java`, `A2AAuthenticationException.java`

Test classes:
- `test/org/yawlfoundation/yawl/integration/a2a/YawlA2AServerTest.java`

What is tested: A2A server construction and authentication provider composition.

### 1.9 SPIFFE Workload Identity

Key classes (`src/org/yawlfoundation/yawl/integration/spiffe/`):
- `SpiffeWorkloadIdentity.java`, `SpiffeWorkloadApiClient.java`
- `SpiffeCredentialProvider.java`, `SpiffeMtlsHttpClient.java`
- `SpiffeEnabledZaiService.java`, `SpiffeFederationConfig.java`, `SpiffeException.java`
- `spring/SpiffeConfiguration.java`

Test classes (`test/org/yawlfoundation/yawl/integration/spiffe/`):
- `SpiffeWorkloadIdentityTest.java`, `SpiffeExceptionTest.java`

What is tested: SVID parsing, workload identity construction, exception handling.
The `SpiffeWorkloadApiClient` (which connects to a live SPIRE agent socket) is not
tested without an external SPIRE daemon.

### 1.10 Observability (OpenTelemetry)

Key classes:
- `src/org/yawlfoundation/yawl/engine/observability/OpenTelemetryConfig.java`
- `src/org/yawlfoundation/yawl/engine/observability/YAWLTelemetry.java`
- `src/org/yawlfoundation/yawl/engine/observability/YAWLTracing.java`
- `src/org/yawlfoundation/yawl/integration/observability/OpenTelemetryConfig.java`
- `src/org/yawlfoundation/yawl/observability/OpenTelemetryInitializer.java`
- `src/org/yawlfoundation/yawl/observability/WorkflowSpanBuilder.java`
- `src/org/yawlfoundation/yawl/observability/YawlMetrics.java`
- `src/org/yawlfoundation/yawl/observability/HealthCheckEndpoint.java`
- `src/org/yawlfoundation/yawl/observability/StructuredLogger.java`

Test classes:
- `test/org/yawlfoundation/yawl/integration/observability/OpenTelemetryConfigTest.java`
- `test/org/yawlfoundation/yawl/observability/ObservabilityTest.java`

Note: There are two parallel observability packages (`engine/observability` and
`integration/observability` and the top-level `observability`). This indicates
duplication that has not been resolved.

### 1.11 Event Logging

Key classes (`src/org/yawlfoundation/yawl/logging/`):
- `YEventLogger.java`, `YLogServer.java`, `YXESBuilder.java`
- `table/YLogEvent.java`, `YLogTask.java`, `YLogNet.java`, `YLogSpecification.java`
- `YLogDataItemInstance.java`, `YAuditEvent.java`
- `YLogPredicate.java` with parser variants

Test classes:
- `test/org/yawlfoundation/yawl/logging/YawlServletTestNextIdNew.java`

Note: Logging has minimal test coverage relative to its size.

### 1.12 Exception Handling

Key classes (`src/org/yawlfoundation/yawl/exceptions/`):
- `YAWLException.java`, `YSyntaxException.java`, `YConnectivityException.java`

Test classes:
- `TestYAWLExceptionEnhancements.java`, `TestYSyntaxException.java`
- `TestYConnectivityException.java`

### 1.13 XSD Schema Suite

Files (`schema/`):
- `YAWL_Schema4.0.xsd` — current production schema
- `YAWL_Schema3.0.xsd`, `YAWL_Schema2.2.xsd`, `YAWL_Schema2.1.xsd`, `YAWL_Schema2.0.xsd`
- Historical schemas: Beta3, Beta4, Beta6, Beta7.1

The schema files exist and are referenced in tests. No `<agentBinding>` element
is present in `YAWL_Schema4.0.xsd` (see ADR-019 note in section 3).

### 1.14 Spring Boot Actuator Module (Partial)

Key classes (`src/org/yawlfoundation/yawl/engine/actuator/`):
- `YActuatorApplication.java` — Spring Boot entry point (annotated `@SpringBootApplication`)
- `health/YEngineHealthIndicator.java`, `YDatabaseHealthIndicator.java`
- `health/YExternalServicesHealthIndicator.java`, `YLivenessHealthIndicator.java`
- `health/YReadinessHealthIndicator.java`
- `metrics/YWorkflowMetrics.java`, `YResourceMetrics.java`, `YAgentPerformanceMetrics.java`
- `config/ActuatorConfiguration.java`

No dedicated test classes for the actuator module were found. The actuator module
references Spring Boot APIs but YAWL's primary build (`pom.xml`) does not declare
Spring Boot parent POM as a dependency for the main engine module.

---

## 2. Implemented, No Tests

These subsystems have Java source code but no corresponding test classes.

### 2.1 Resource Service

Status: No Java source files exist under `src/` for the resource service.
The `build/resourceService/` directory contains static UI assets and configuration
files from a legacy Ant build, but no `.java` source. The resource service is
a documented YAWL component (Dockerfiles reference it at `containerization/Dockerfile.resourceService`)
but its Java implementation is absent from `src/`.

**This is a gap, not a categorisation error.** The resource service is a core YAWL
service described in all deployment documentation as required for human task assignment.

### 2.2 Worklet Service

Status: No Java source files exist under `src/` for the worklet service.
`build/workletService/` contains configuration and UI assets from the legacy Ant build.
Multiple Dockerfiles (`containerization/Dockerfile.workletService`,
`ci-cd/oracle-cloud/Dockerfile.worklet`) reference the worklet service, and
`docs/explanation/worklet-service.md` provides a conceptual explanation, but
there is no worklet service Java source in the Maven-managed `src/` tree.

### 2.3 Event Sourcing

Key classes (`src/org/yawlfoundation/yawl/integration/eventsourcing/`):
- `WorkflowEventStore.java`, `CaseSnapshot.java`, `CaseStateView.java`
- `EventReplayer.java`, `SnapshotRepository.java`, `TemporalCaseQuery.java`

No test classes found. The event sourcing module is listed as a deferred
architecture option in ADR-014 (section "Alternatives Considered").

### 2.4 API Gateway Configuration Generators

Key classes (`src/org/yawlfoundation/yawl/integration/gateway/`):
- `GatewayCircuitBreakerConfig.java`, `GatewayRouteDefinition.java`
- `kong/KongConfigurationGenerator.java`
- `traefik/TraefikConfigurationGenerator.java`
- `aws/AwsApiGatewayConfigGenerator.java`

No test classes found.

### 2.5 Deduplication

Key classes (`src/org/yawlfoundation/yawl/integration/dedup/`):
- `EngineDedupPlan.java`

No test classes found.

### 2.6 Credential Manager

Key class:
- `src/org/yawlfoundation/yawl/integration/CredentialManager.java`

No test class found.

### 2.7 Engine GUI (Swing)

Key classes (`src/org/yawlfoundation/yawl/engine/gui/`):
- `TabbedEngineGUI.java`, `YAdminGUI.java`, `YSplash.java`

No test classes. GUI code is not meaningfully unit-testable without a display,
but there are no smoke tests either.

### 2.8 Instance Cache / Case Instance Tracking

Key classes (`src/org/yawlfoundation/yawl/engine/instance/`):
- `InstanceCache.java`, `CaseInstance.java`, `WorkItemInstance.java`
- `ParameterInstance.java`, `YInstance.java`

No dedicated test classes found.

### 2.9 Proclet Service

Source files exist in `src/org/yawlfoundation/yawl/procletService/` (confirmed
by the presence of `build/procletService/` assets and test harnesses at
`test/org/yawlfoundation/yawl/procletService/`), but no substantive test
coverage for proclet execution logic was found.

---

## 3. Aspirational / Documented Only

These features are described in ADRs, documentation pages, or referenced in
configuration files, but no corresponding Java implementation exists in `src/`.

### 3.1 Horizontal Clustering with Redis Lease Protocol

Documented in: `docs/architecture/decisions/ADR-014-clustering-and-horizontal-scaling.md`

ADR-014 status: PLANNED (v6.1.0 — explicitly stated as post-v6.0.0 GA).

What the ADR describes:
- Active-active engine nodes with Redis SETNX lease acquisition per case
- `CaseLease` interface and `RedisCaseLease` implementation
- `yawl_cluster_nodes` database table for node registration
- Configuration properties: `yawl.cluster.enabled`, `yawl.cluster.node-id`

What exists today: zero. No `cluster/` package exists in `src/`. No Redis
dependency is declared. No `CaseLease` interface exists. `YEngine` remains a
singleton (`YEngine.getInstance()`). The `docker-compose.yml` does not
configure Redis.

To implement: create `org.yawlfoundation.yawl.engine.cluster` package, add
Redis (Lettuce or Redisson) dependency, implement `CaseLease`, integrate lease
acquisition into `YEngine.startCase()` and `YNetRunner.continueIfPossible()`.

### 3.2 Full Spring Boot Integration for Engine

Documented in: `docs/architecture/decisions/ADR-004-spring-boot-34-java-25.md`
(status: ACCEPTED, COMPLETE).

The ADR claims "Implementation Status: COMPLETE (v5.2)" but the Java evidence
is partial. Spring Boot is used only in:
- `YActuatorApplication.java` — a standalone health/metrics overlay application
- `YawlMcpSpringApplication.java` — MCP server Spring Boot application

The core `YEngine` is not a Spring-managed bean. There is no
`YawlEngineApplication.java` with `@SpringBootApplication`. The engine does not
use Spring Data JPA or Spring's virtual thread executor. The actuator module
exists but has no test coverage and is not integrated into the primary build.

To implement: create a Spring Boot application class that wraps `YEngine`,
configure `spring.threads.virtual.enabled=true`, wire Spring Data JPA into
`YPersistenceManager`, create `application.yml`.

### 3.3 `<agentBinding>` Schema Element

Documented in: `docs/architecture/decisions/ADR-019-autonomous-agent-framework.md`

ADR-019 describes an `<agentBinding>` XML element in YAWL specifications that
binds tasks to agent types with capability requirements, timeout, and fallback
policy. This requires a schema change to `YAWL_Schema4.0.xsd` (or a new v5.0
schema) and corresponding parser changes in `YSpecificationParser`.

What exists: the `<agentBinding>` element does not appear in any `.xsd` file.
`YSpecificationParser` in the stateless module does not parse agent bindings.
`AgentRegistry` does not read bindings from parsed specifications.

To implement: add `<agentBinding>` to the schema, update `YSpecificationParser`
to populate `YTask` with agent binding metadata, update `AgentRegistry` to
query task metadata during work item routing.

### 3.4 Full SPIRE Daemon Integration

Documented in: `docs/architecture/decisions/ADR-005-spiffe-spire-zero-trust.md`,
`docs/SPIFFE_INTEGRATION.md`, `docs/SPIFFE_INTEGRATION_GUIDE.md`,
`docker-compose.spiffe.yml`

What exists: the `SpiffeWorkloadApiClient.java` models the SPIRE workload API
client and the `SpiffeCredentialProvider` and `SpiffeMtlsHttpClient` exist. The
`docker-compose.spiffe.yml` defines SPIRE server and agent containers.

What is absent: the workload socket path (`/run/spire/sockets/agent.sock`) is
referenced in the implementation but requires an actual SPIRE deployment. The
`SpiffeWorkloadApiClient` cannot be tested without a running SPIRE agent. There
is no integration test that starts SPIRE in a container and verifies SVID
rotation. The `SpiffeConfiguration` Spring bean is not wired into the primary
engine startup path.

### 3.5 OpenAPI-First Code Generation

Documented in:
- `docs/architecture/decisions/ADR-012-openapi-first-design.md`
- `docs/architecture/decisions/ADR-022-openapi-first-design.md`
- `docs/architecture/decisions/ADR-018-javadoc-to-openapi-generation.md`

No OpenAPI specification file (`openapi.yaml` or `openapi.json`) was found in
`src/`, `docs/`, or the project root. No Maven plugin configuration for
`openapi-generator-maven-plugin` or `swagger-maven-plugin` was found. Interface A
and Interface B remain servlet-based (extending `HttpServlet`) with no OpenAPI
annotations or generated client SDKs.

### 3.6 Horizontal Scaling / Multi-Tenant Isolation

Documented in various deployment guides and `docs/SCALING_AND_OBSERVABILITY_GUIDE.md`.

No `tenant` or `Tenant` class exists anywhere in `src/`. No tenant-scoping
annotations, database schema partitioning, or request-level tenant extraction
code exists. YAWL has no multi-tenancy at the Java level.

### 3.7 Kafka / RabbitMQ Event-Driven Integration

Referenced in ADR-004 under "Enterprise Integration" and in several cloud
deployment docs as an integration option.

No Kafka or RabbitMQ producer/consumer code exists in `src/`. No corresponding
Maven dependency. This is a referenced capability with no implementation.

### 3.8 Vector Database and RAG Integration

Referenced in ADR-004 under "Spring AI Integration" and the thesis document
`docs/THESIS_Autonomous_Workflow_Agents.md`.

No vector database client, embedding generation, or RAG retrieval code exists
in `src/`. The `ZaiService` interface models an external AI service but does
not implement retrieval-augmented generation internally.

### 3.9 `<agentBinding>` Fallback-to-Human Routing

Part of ADR-019. When an agent fails and `<fallbackToHuman>` is set in the
specification, the work item should be offered to human participants via the
resource service. This requires both the `<agentBinding>` schema (absent, see 3.3)
and a functioning resource service (absent, see 2.1).

---

## 4. Documentation Sessions Delta (2026-02-18)

The following Diataxis-structured documentation files were committed in this
session or are in parallel authoring as of 2026-02-18.

### Committed in f41e2ef

- `docs/explanation/petri-net-foundations.md` — conceptual explanation of
  Petri net semantics, places/transitions/markings, or-join semantics. Accurate
  for the implemented stateful engine.
- `docs/explanation/worklet-service.md` — conceptual explanation of the worklet
  service. Note: the worklet service Java source does not exist in `src/`
  (see section 2.2). This doc describes intended architecture, not current code.
- `docs/tutorials/03-run-your-first-workflow.md` — step-by-step tutorial for
  launching a case. Requires a running engine instance; steps are plausible but
  not CI-verified as of this date.
- `docs/tutorials/07-docker-dev-environment.md` — Docker Compose development
  setup tutorial. The `docker-compose.yml` and `Dockerfile.*` files exist. The
  tutorial references the resource service container, which has no Java source
  (see section 2.1).

### Previously existing Diataxis files (pre-session)

- `docs/explanation/dual-engine-architecture.md` — accurate; both engines exist.
- `docs/explanation/case-lifecycle.md` — accurate for stateful engine.
- `docs/explanation/execution-profiles.md` — documents both stateful/stateless.
- `docs/explanation/multi-instance-tasks.md` — implementation exists.
- `docs/explanation/shared-src-build-strategy.md` — build system description.
- `docs/tutorials/01-build-yawl.md` — Maven build instructions.
- `docs/tutorials/02-understand-the-build.md` — build system explanation.
- `docs/tutorials/04-write-a-yawl-specification.md` — spec authoring guide.
- `docs/tutorials/05-call-yawl-rest-api.md` — Interface B REST guide.
- `docs/tutorials/08-mcp-agent-integration.md` — MCP integration guide.

### ADRs That Are Partially or Fully Aspirational

| ADR | Status in Doc | Reality |
|-----|--------------|---------|
| ADR-004: Spring Boot 3.4 | "COMPLETE" | Partial — actuator module only; engine not Spring-managed |
| ADR-005: SPIFFE/SPIRE | "ACCEPTED" | Partial — client code exists, no live integration test |
| ADR-014: Clustering | "PLANNED (v6.1.0)" | Not started — zero cluster code in src/ |
| ADR-019: Agent Framework | "IN PROGRESS" | Core framework implemented; `<agentBinding>` schema absent |
| ADR-012/022: OpenAPI-First | "ACCEPTED" | Not started — no openapi.yaml, no generated code |
| ADR-018: Javadoc→OpenAPI | Implied complete | Not started — no OpenAPI tooling in pom.xml |

---

## 5. Summary Table

| Subsystem | Java Source | Tests | Notes |
|-----------|------------|-------|-------|
| Engine Core (stateful YEngine) | Yes | Yes (22 classes) | Well-tested |
| Workflow Elements | Yes | Yes (15 classes) | Well-tested |
| Stateless Engine | Yes | Yes (2 IT classes) | Thin test coverage |
| Interface A (design-time) | Yes | Indirect only | No dedicated servlet tests |
| Interface B (client/runtime) | Yes | Yes (4 classes) | Record types tested; servlet untested |
| Interface E (event logging) | Yes | Minimal (1) | Logging lightly tested |
| Interface X (exceptions) | Yes | Yes (3 classes) | Exception hierarchy tested |
| Authentication / JWT | Yes | Yes (3 classes) | CSRF filter untested |
| Autonomous Agent Framework | Yes | Yes (6 classes) | LLM reasoners untested |
| MCP Integration | Yes | Yes (2 classes) | Spring wiring not end-to-end tested |
| A2A Integration | Yes | Yes (1 class) | Auth composition tested |
| SPIFFE Workload Identity | Yes | Yes (2 classes) | Requires live SPIRE for full test |
| Observability / OpenTelemetry | Yes | Yes (2 classes) | Duplicate packages unresolved |
| Event Logging | Yes | Minimal (1) | Under-tested |
| Event Sourcing | Yes | None | No tests |
| Gateway Config Generators | Yes | None | No tests |
| Spring Boot Actuator | Yes | None | Not integrated into primary build |
| XSD Schema (v4.0) | Yes | N/A | agentBinding element absent |
| Resource Service | No (legacy assets only) | No | Core service absent from src/ |
| Worklet Service | No (legacy assets only) | No | Core service absent from src/ |
| Clustering (Redis leases) | No | No | ADR-014 PLANNED for v6.1.0 |
| OpenAPI Generation | No | No | ADR-012/022 not started |
| Kafka/RabbitMQ Messaging | No | No | Referenced in ADR-004 only |
| Multi-Tenancy | No | No | Not mentioned in any source |
| Vector DB / RAG | No | No | Referenced in ADR-004, thesis |

---

## 6. Guidance for Doc Readers

When reading any YAWL documentation, apply the following rules:

1. If a doc describes the **stateful engine core** (`YEngine`, `YNetRunner`,
   `YWorkItem`, elements, Interface A/B), trust it. This is the oldest and
   best-tested part of the system.

2. If a doc describes the **stateless engine**, trust the API surface. Trust
   the event listener model conceptually. Do not trust performance claims
   without running your own benchmarks.

3. If a doc describes the **autonomous agent framework** or **MCP integration**,
   treat it as "implemented but lightly tested." The strategy interfaces and
   resilience primitives work; end-to-end LLM workflows require an external service.

4. If a doc describes the **resource service** or **worklet service**, understand
   that no Java source for these services exists in the Maven-managed codebase.
   The documentation describes the intended architecture inherited from YAWL v4.x
   or planned for future implementation.

5. If a doc describes **clustering**, **OpenAPI code generation**, **Kafka
   integration**, or **multi-tenancy**, treat it as aspirational. No implementation
   exists.

6. ADR status fields ("COMPLETE", "ACCEPTED") describe architectural decisions,
   not implementation completion. An "ACCEPTED" ADR with no corresponding code
   means the decision was accepted but implementation has not begun.

---

*Document generated by: architectural audit on 2026-02-18.*
*Branch: claude/fix-pom-test-inclusion-yf2FD*
