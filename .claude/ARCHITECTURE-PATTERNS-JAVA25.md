# Java 25 Architectural Patterns for YAWL v6.0.0

**Date**: Feb 2026 | **Status**: Research + Implementation Guide

This document details 8 architectural patterns for Java 25 adoption in YAWL, each with specific file references to the codebase.

---

## Navigation

**Related Documentation**:
- **[JAVA-25-FEATURES.md](JAVA-25-FEATURES.md)** - Feature overview and adoption roadmap
- **[BUILD-PERFORMANCE.md](BUILD-PERFORMANCE.md)** - Build system optimization
- **[SECURITY-CHECKLIST-JAVA25.md](SECURITY-CHECKLIST-JAVA25.md)** - Security requirements
- **[BEST-PRACTICES-2026.md](BEST-PRACTICES-2026.md)** - 12 best practices sections
- **[../CLAUDE.md](../CLAUDE.md)** - Project specification (architecture section Γ)

**In This Document**:
- [8 Java 25 Patterns](#pattern-1-virtual-threads-for-workflow-concurrency) - Each with code examples
- [3 MCP/A2A Deployment Patterns](#pattern-9-mcp-server-cicd-integration) - ADR-023/024/025 implementations
- [Priority Matrix](#implementation-priority) - Effort vs benefit
- [Files to Update](#implementation-priority) - Specific locations in codebase

---

## Pattern 1: Virtual Threads for Workflow Concurrency

### Problem
`GenericPartyAgent.discoveryThread` uses platform threads (2MB each). With 1000 agents, memory usage explodes to 2GB+.

**Current Code**:
```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent (line 52)
private Thread discoveryThread;
private final AtomicBoolean running = new AtomicBoolean(false);
```

### Solution
Replace with named virtual threads:

```java
// Recommended replacement
private Thread discoveryThread;

@Override
public void start() {
    running.set(true);
    discoveryThread = Thread.ofVirtual()
        .name("yawl-agent-discovery-" + config.getAgentId())
        .start(this::runDiscoveryLoop);
}

@Override
public void stop() {
    running.set(false);
    // Virtual thread automatically deallocates
}
```

### Benefits
- 1000 agents: 2GB -> ~1MB memory
- Unlimited horizontal scaling
- Automatic cleanup on completion

### Files to Update
- `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`
- `org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy`

### Effort: 1 day

---

## Pattern 2: Structured Concurrency for Work Item Batches

### Problem
When `DiscoveryStrategy.discoverWorkItems()` finds multiple eligible items, they're processed sequentially. If one external call hangs, all others block.

### Solution
Use `StructuredTaskScope` for fan-out with automatic cancellation:

```java
public List<WorkItem> processDiscoveredItems(List<WorkItem> discovered)
        throws InterruptedException, ExecutionException {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<WorkItem>> tasks = discovered.stream()
            .map(item -> scope.fork(() -> processWorkItem(item)))
            .toList();

        // Wait for all tasks; cancel others on first failure
        scope.join();
        scope.throwIfFailed();

        return tasks.stream()
            .map(Subtask::resultNow)
            .toList();
    }
}
```

### Benefits
- Parallel processing: 10 items in 1s instead of 10s
- Automatic cancellation: One failure stops all
- Observable: Thread dumps show parent-child relationships

### Files to Update
- `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`
- All `DiscoveryStrategy` implementations

### Effort: 2 days

---

## Pattern 3: Domain-Driven Design Alignment

### Problem
`YWorkItemStatus` is a flat 13-value enum. State transitions are distributed across `YNetRunner`, `YWorkItem`, and `YEngine`. No central state validator.

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.YWorkItemStatus
public enum YWorkItemStatus {
    statusEnabled,      // 0
    statusFired,        // 1
    statusExecuting,    // 2
    statusComplete,     // 3
    // ... 9 more ...
}
```

### Solution: Sealed State Machine

```java
// Immutable, exhaustive state hierarchy
public sealed interface WorkItemState
    permits EnabledState, FiredState, ExecutingState, SuspendedState, TerminalState {}

public record EnabledState(Instant enabledAt) implements WorkItemState {}
public record FiredState(Instant enabledAt, Instant firedAt) implements WorkItemState {}
public record ExecutingState(
    Instant enabledAt,
    Instant firedAt,
    Instant startedAt,
    String executingParticipant
) implements WorkItemState {}

public record SuspendedState(WorkItemState suspendedFrom) implements WorkItemState {}

public sealed interface TerminalState extends WorkItemState
    permits CompletedState, FailedState, CancelledState, DeadlockedState {}

public record CompletedState(Instant completedAt, WorkItemCompletion completionType)
    implements TerminalState {}
public record FailedState(Instant failedAt, String reason) implements TerminalState {}
public record CancelledState(Instant cancelledAt, CancellationReason reason)
    implements TerminalState {}
```

### Compiler-Verified Exhaustiveness

```java
// In YCaseMonitor.handleCaseEvent() (line ~500)
String status = workItem switch {
    EnabledState _ -> "Waiting",
    FiredState _ -> "Fired",
    ExecutingState e -> "Executing: " + e.executingParticipant(),
    SuspendedState s -> "Suspended",
    CompletedState _ -> "Completed",
    FailedState f -> "Failed: " + f.reason(),
    CancelledState c -> "Cancelled: " + c.reason(),
    DeadlockedState _ -> "Deadlocked",
    // Compiler error if any case missing!
};
```

### Benefits
- Compiler catches missed state transitions
- `_prevStatus` field becomes unnecessary (embedded in `SuspendedState`)
- Type-safe state queries

### Files to Update
- `org.yawlfoundation.yawl.engine.YWorkItem` (line 72: `_status` field)
- `org.yawlfoundation.yawl.engine.YWorkItemStatus` (replace enum)
- `org.yawlfoundation.yawl.engine.YNetRunner` (all state transitions)

### Effort: 3 days

---

## Pattern 4: CQRS for Interface B (Commands + Queries)

### Problem
`InterfaceBClient` mixes commands (mutations) with queries (reads). A service that only polls work items must depend on the same interface that handles case launch and item completion.

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient
public interface InterfaceBClient {
    // Commands (mutations)
    String launchCase(...);
    YWorkItem startWorkItem(...);
    void completeWorkItem(...);
    void rollbackWorkItem(...);

    // Queries (reads)
    Set<YWorkItem> getAvailableWorkItems();
    Set<YWorkItem> getAllWorkItems();
    YWorkItem getWorkItem(...);
    String getCaseData(...);
}
```

### Solution: CQRS Split

```java
// Command interface (write side)
public interface InterfaceBCommands {
    String launchCase(YSpecificationID specID, String caseParams, ...)
        throws YStateException, YDataStateException, ...;
    YWorkItem startWorkItem(YWorkItem workItem, YClient client)
        throws YStateException, YDataStateException, ...;
    void completeWorkItem(YWorkItem workItem, String data, ...)
        throws YStateException, YDataStateException, ...;
    void rollbackWorkItem(String workItemID)
        throws YStateException, YPersistenceException, ...;
}

// Query interface (read side)
public interface InterfaceBQueries {
    Set<YWorkItem> getAvailableWorkItems();
    Set<YWorkItem> getAllWorkItems();
    YWorkItem getWorkItem(String workItemID);
    String getCaseData(String caseID) throws YStateException;
    YTask getTaskDefinition(YSpecificationID specID, String taskID);
}

// Backward-compatible composite
public interface InterfaceBClient extends InterfaceBCommands, InterfaceBQueries {
    // ... legacy methods for compatibility ...
}
```

### Usage in Agent

```java
// Discovery strategy now only depends on queries
public class PollingDiscoveryStrategy implements DiscoveryStrategy {
    private InterfaceBQueries engine;  // Immutable reference to read side

    @Override
    public List<WorkItem> discoverWorkItems() {
        return engine.getAvailableWorkItems().stream()
            .filter(this::isEligible)
            .toList();
    }
}
```

### Benefits
- Separation of concerns (CQRS pattern)
- Independent scaling of read and write paths
- Clearer API contracts

### Files to Update
- `org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient` (split interface)
- `org.yawlfoundation.yawl.engine.YEngine` (implements both)
- All implementations of `DiscoveryStrategy`

### Effort: 2 days

---

## Pattern 5: Records for Event Hierarchy

### Problem
`YEvent` is mutable abstract class with post-construction setters. Multi-threaded event dispatch in `MultiThreadEventNotifier` cannot guarantee immutability.

**Current Code**:
```java
// org.yawlfoundation.yawl.stateless.listener.event.YEvent
public abstract class YEvent {
    private final Instant _timeStamp;
    private YSpecificationID _specID;      // Mutable, set after
    private YWorkItem _item;               // Mutable, set after
    private Document _dataDoc;             // Mutable, set after
    private int _engineNbr;                // Mutable, set after

    public void setSpecID(YSpecificationID specID) { this._specID = specID; }
    public void setWorkItem(YWorkItem item) { this._item = item; }
    // ... more setters ...
}
```

### Solution: Sealed Record Hierarchy

```java
// Sealed interface for all events
public sealed interface YWorkflowEvent
    permits YCaseLifecycleEvent, YWorkItemLifecycleEvent,
            YTimerEvent, YConstraintEvent, YExceptionEvent,
            YAgentServiceEvent {}

// Case events
public record YCaseLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    int engineNbr
) implements YWorkflowEvent {}

// Work item events
public record YWorkItemLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    YWorkItem workItem,
    Document data,
    int engineNbr
) implements YWorkflowEvent {}

// Timer events
public record YTimerEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    String taskID,
    int engineNbr
) implements YWorkflowEvent {}
```

### Thread-Safe Event Dispatch

```java
// In MultiThreadEventNotifier (already using virtual threads)
// Exhaustive pattern matching
for (YWorkflowEvent event : eventQueue) {
    switch (event) {
        case YCaseLifecycleEvent e ->
            handleCaseEvent(e.caseID(), e.type());
        case YWorkItemLifecycleEvent e ->
            handleWorkItemEvent(e.workItem(), e.type());
        case YTimerEvent e ->
            handleTimerEvent(e.taskID(), e.timestamp());
        // Compiler error if missing case
    }
}
```

### Testing Benefit: No Builders

```java
// Test setup without mock infrastructure
@Test
void testCaseStartEvent() {
    YCaseLifecycleEvent event = new YCaseLifecycleEvent(
        Instant.now(),
        YEventType.CASE_STARTED,
        new YIdentifier("test-case"),
        new YSpecificationID("Process", "1.0", "uri"),
        1
    );

    engine.announceEvent(event);

    assertTrue(monitor.getCaseState(event.caseID()).isRunning());
}
```

### Files to Update
- `org.yawlfoundation.yawl.stateless.listener.event.YEvent` (seal + records)
- `org.yawlfoundation.yawl.engine.announcement.YEngineEvent` (seal + records)
- All event listener implementations

### Effort: 3 days

---

## Pattern 6: Module System for Boundaries

### Problem
Both `org.yawlfoundation.yawl.engine.YNetRunner` and `org.yawlfoundation.yawl.stateless.engine.YNetRunner` exist with near-identical logic. The Singleton `YEngine` pattern prevents code reuse.

**Current**:
- Monolithic Maven project, no module-info.java
- Package boundaries not enforced by compiler
- Duplication across engine and stateless.engine packages

### Solution: Java 9+ Module System

```java
// module-info.java for stateless execution core
module yawl.execution.core {
    exports org.yawlfoundation.yawl.stateless;
    exports org.yawlfoundation.yawl.stateless.engine;
    exports org.yawlfoundation.yawl.stateless.elements;
    exports org.yawlfoundation.yawl.stateless.listener;
    exports org.yawlfoundation.yawl.stateless.monitor;

    requires org.jdom2;
    requires net.sf.saxon;
    requires org.apache.logging.log4j;
    requires java.persistence;
    requires org.hibernate.orm.core;
}

// module-info.java for stateful engine (depends on core)
module yawl.engine {
    requires yawl.execution.core;
    requires java.servlet;
    requires jakarta.xml.bind;

    exports org.yawlfoundation.yawl.engine;
    exports org.yawlfoundation.yawl.engine.interfce;
    exports org.yawlfoundation.yawl.engine.interfce.interfaceA;
    exports org.yawlfoundation.yawl.engine.interfce.interfaceB;

    // Prevents reflection access to internals
    // does not export org.yawlfoundation.yawl.engine.internal;
}

// module-info.java for agent framework
module yawl.agents {
    requires yawl.execution.core;
    requires yawl.engine;

    exports org.yawlfoundation.yawl.integration.autonomous;
    exports org.yawlfoundation.yawl.integration.autonomous.strategies;
}
```

### Bounded Contexts

```
Bounded Context: WorkflowDefinition
    -> org.yawlfoundation.yawl.specification module
    -> YSpecification, YNet, YTask, YFlow, YDecomposition

Bounded Context: CaseExecution (Core)
    -> yawl.execution.core module
    -> YNetRunner, YMarkings, YIdentifier, YCaseMonitor

Bounded Context: Stateful Engine
    -> yawl.engine module
    -> YEngine, Interfaces A/B, Persistence

Bounded Context: AgentCoordination
    -> yawl.agents module
    -> AutonomousAgent, DiscoveryStrategy, DecisionReasoner
```

### Benefits
- Explicit dependency graph
- Compile-time enforcement of boundaries
- `-XX:+UnlockDiagnosticVMOptions -XX:+PrintModuleNativeLookup` for runtime analysis

### Files to Create/Update
- Create `src/org/yawlfoundation/yawl/stateless/module-info.java`
- Create `src/org/yawlfoundation/yawl/engine/module-info.java`
- Create `src/org/yawlfoundation/yawl/integration/module-info.java`

### Effort: 1 week

---

## Pattern 7: Reactive Event Pipeline with Predicates

### Problem
`YAnnouncer` dispatches all events to all listeners. Listeners that filter events (e.g., "only case start events") receive all events and discard them, wasting CPU.

### Solution: Event Predicate Filtering

```java
// Event filter interface
public interface YEventFilter<E extends YWorkflowEvent> {
    boolean test(E event);

    default YEventFilter<E> and(YEventFilter<E> other) {
        return event -> this.test(event) && other.test(event);
    }

    default YEventFilter<E> or(YEventFilter<E> other) {
        return event -> this.test(event) || other.test(event);
    }
}

// Built-in filters
public class YEventFilters {
    public static YEventFilter<YCaseLifecycleEvent> caseType(YEventType type) {
        return e -> e.type() == type;
    }

    public static YEventFilter<YCaseLifecycleEvent> caseId(YIdentifier id) {
        return e -> e.caseID().equals(id);
    }

    public static YEventFilter<YWorkItemLifecycleEvent> taskId(String taskId) {
        return e -> e.workItem().getTaskID().equals(taskId);
    }
}

// Announcer accepts filters
public void addWorkItemEventListener(
        YWorkItemEventListener listener,
        YEventFilter<YWorkItemLifecycleEvent> filter) {
    _workItemListeners.put(listener, filter);
}
```

### Usage

```java
// Only receive completion events for specific case
announcer.addWorkItemEventListener(
    listener,
    YEventFilters.caseId(caseID)
        .and(YEventFilters.taskId("review"))
        .and(e -> e.type() == YEventType.ITEM_COMPLETED)
);
```

### Benefit: Reduced Event Processing

- Without filters: 1000 events/sec -> all processed
- With filters: 1000 events/sec -> 50 match predicate -> only 50 processed
- 95% reduction in listener callback overhead

### Files to Update
- `org.yawlfoundation.yawl.stateless.listener.YAnnouncer`
- All listener registrations

### Effort: 2 days

---

## Pattern 8: Constructor Injection (Dependency Inversion)

### Problem
`YEngine.getInstance()` uses Singleton anti-pattern with static field. `YNetRunner` calls `YEngine.getInstance()` in its default constructor (circular dependency).

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.YEngine (line 81)
protected static YEngine _thisInstance;

public static YEngine getInstance(boolean persisting) throws YPersistenceException {
    if (_thisInstance == null) {
        _thisInstance = new YEngine();
        initialise(null, persisting, false, false);
    }
    return _thisInstance;
}

// org.yawlfoundation.yawl.engine.YNetRunner (line 96)
private YEngine _engine = YEngine.getInstance();  // Hidden dependency
```

### Solution: Constructor Injection

```java
// YEngine without static singleton
public class YEngine implements InterfaceAManagement, InterfaceBClient {
    private final YPersistenceManager persistenceManager;
    private final YAnnouncer announcer;
    private final YWorkItemRepository workItemRepository;

    public YEngine(YPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.announcer = new YAnnouncer(this);
        this.workItemRepository = new YWorkItemRepository();
    }
}

// YNetRunner with injected engine
public class YNetRunner {
    private final YEngine engine;
    private final YAnnouncer announcer;

    public YNetRunner(YEngine engine, YAnnouncer announcer) {
        this.engine = engine;
        this.announcer = announcer;
    }
}

// Spring Boot context (singleton lifetime managed by Spring)
@Configuration
public class YawlEngineConfiguration {
    @Bean
    @Singleton
    public YPersistenceManager persistenceManager() {
        return new YPersistenceManager();
    }

    @Bean
    @Singleton
    public YEngine yawlEngine(YPersistenceManager persistenceManager) {
        return new YEngine(persistenceManager);
    }

    @Bean
    public YNetRunner yNetRunner(YEngine engine, YAnnouncer announcer) {
        return new YNetRunner(engine, announcer);
    }
}
```

### Benefits
- Testable: Mock engine in tests
- Observable: Dependency graph explicit
- Thread-safe: Spring manages singleton lifetime

### Files to Update
- `org.yawlfoundation.yawl.engine.YEngine` (remove getInstance)
- `org.yawlfoundation.yawl.engine.YNetRunner` (inject engine)
- All places calling `YEngine.getInstance()`

### Effort: 1 week (high-risk refactoring)

---

## Implementation Priority

| Pattern | Phase | Effort | Risk | Benefit |
|---------|-------|--------|------|---------|
| Virtual Threads (1) | 1 | 1 day | Low | High |
| Sealed Records (5) | 1 | 3 days | Low | High |
| Sealed State Machine (3) | 1 | 3 days | Medium | High |
| Structured Concurrency (2) | 1 | 2 days | Low | High |
| CQRS (4) | 2 | 2 days | Low | Medium |
| Reactive Events (7) | 2 | 2 days | Low | Medium |
| Module System (6) | 3 | 1 week | High | High |
| Constructor Injection (8) | 4 | 1 week | High | Medium |
| MCP CI/CD Integration (9) | 1 | 2 days | Low | High |
| Multi-Cloud Topology (10) | 3 | 2 weeks | Medium | High |
| Agent Coordination Protocol (11) | 2 | 1 week | Low | High |

**Recommended start**: Patterns 1, 3, 5 in parallel (weeks 1-2)

---

## References

- Pattern 1-2: Project Loom / Virtual Threads - https://openjdk.org/jeps/444
- Pattern 3: Sealed Classes - https://openjdk.org/jeps/409
- Pattern 4: CQRS Pattern - https://martinfowler.com/bliki/CQRS.html
- Pattern 5: Records - https://openjdk.org/jeps/440
- Pattern 6: Java Modules - https://openjdk.org/projects/jigsaw/
- Pattern 7: Reactive Streams - https://www.reactive-streams.org/
- Pattern 8: Dependency Inversion - https://en.wikipedia.org/wiki/Dependency_inversion_principle
- Pattern 9-11: ADR-023, ADR-024, ADR-025 in docs/architecture/decisions/

---

## Pattern 9: MCP Server CI/CD Integration

**ADR**: [ADR-023](../docs/architecture/decisions/ADR-023-mcp-a2a-cicd-deployment.md)

### Problem

`YawlMcpServer` uses STDIO transport — it is a process that communicates over
stdin/stdout, not a network service. Standard CI/CD pipelines cannot health-check
a STDIO process. The existing CI/CD pipeline in `CICD_V6_ARCHITECTURE.md` has no
stage for MCP integration testing.

### Solution: Managed Child Process in CI/CD

The MCP server is launched as a managed child process by the test harness. The CI
job pipes JSON-RPC messages to the process stdin and reads responses from stdout.

```
CI Job
  │
  ├── Start YAWL Engine (Docker service, port 8080)
  ├── Wait for /admin/health
  │
  ├── Launch MCP Server process (java -cp yawl.jar YawlMcpServer)
  │   Environment: YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD
  │   Wait for stderr: "started on STDIO transport"
  │
  ├── MCP Test Client
  │   Writes JSON-RPC to process stdin
  │   Reads JSON-RPC from process stdout
  │   Tests: initialize, list_tools, call_tool("launch_case"), list_resources
  │
  └── Teardown: kill process, stop Docker service
```

**GitHub Actions fragment:**

```yaml
jobs:
  mcp-integration:
    runs-on: ubuntu-latest
    services:
      yawl-engine:
        image: ghcr.io/yawlfoundation/yawl-engine:${{ env.VERSION }}
        ports: ["8080:8080"]
        options: --health-cmd="curl -f http://localhost:8080/admin/health"
    steps:
      - name: Launch MCP Server
        run: |
          java -cp target/yawl.jar \
            org.yawlfoundation.yawl.integration.mcp.YawlMcpServer &
          echo $! > /tmp/mcp.pid && sleep 3

      - name: Run MCP tests
        run: mvn -Dtest.group=mcp-integration verify

      - name: Stop MCP Server
        if: always()
        run: kill $(cat /tmp/mcp.pid) || true
```

**Production alternative (SSE transport):** In production, replace `StdioServerTransportProvider`
with `SseServerTransportProvider` to expose MCP over HTTP for multi-client use.
The production deployment uses a Kubernetes Deployment behind an API gateway
(Kong/Traefik/AWS ALB).

### Stage Ordering (CI DAG)

```
compile
  -> unit-test
  -> build-images
  -> start-ci-services     (engine + AgentRegistry + A2A server)
  -> seed-agent-registry   (POST /agents/register)
  -> integration-test      (MCP process tests + A2A HTTP tests)
  -> teardown-ci-services
  -> performance-test      (develop/main branches only)
  -> deploy-staging
  -> deploy-production     (manual gate)
```

### Files Involved

- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` (entry point)
- `org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSpringApplication` (Spring variant)
- CI: `.github/workflows/mcp-integration.yml` (to be created)
- Helm: `charts/yawl-mcp-gateway/` (SSE transport production deployment, to be created)

### Effort: 2 days

---

## Pattern 10: Multi-Cloud Agent Deployment Topology

**ADR**: [ADR-024](../docs/architecture/decisions/ADR-024-multi-cloud-agent-deployment.md)

### Problem

YAWL's `AgentRegistry` is in-memory and single-node. Autonomous agents deployed across
AWS, Azure, and GCP cannot discover each other. The A2A server fleet has no cross-region
failover, and cloud-native agents (using AWS Textract, Azure Cognitive Services, etc.)
must be co-located with their cloud infrastructure.

### Solution: Three-Tier Agent Distribution

**Tier 1: Cloud-Agnostic Agents** — deployed in every region (AWS, Azure, GCP).
No cloud API dependencies. 2-10 replicas per region via HPA.

**Tier 2: Cloud-Native Agents** — deployed only in the matching cloud. Examples:
- `aws-document-agent` (AWS: Textract + Bedrock)
- `azure-cognitive-agent` (Azure: Form Recognizer)
- `gcp-document-agent` (GCP: Document AI + Vertex AI)

**Tier 3: LLM/ZAI Agents** — deployed in the primary LLM processing region,
controlled by `ZAI_API_KEY` availability and API rate limits.

### Global Registry Federation

The in-memory `AgentRegistry` is replaced by a CockroachDB-backed federated registry
with one shard per cloud. The existing `AgentRegistryClient` REST API is unchanged —
only the backend endpoint URL changes in production:

```properties
# application-production.properties
yawl.agent.registry.url=https://registry.yawl.cloud
yawl.agent.registry.region=aws-us-east-1
yawl.agent.registry.prefer-local=true
```

### Region Failover

GeoDNS routes `a2a.yawl.cloud` to the nearest healthy region (30s TTL). Health
probe: `GET /.well-known/agent.json`. Failed regions are removed from DNS rotation
within 30 seconds. YAWL Engine cases held by the failed region recover within the
Redis lease TTL (30 seconds, ADR-014).

### Helm Values Pattern (cloud-specific overrides)

```yaml
# values-aws.yaml (AWS us-east-1)
cloud:
  provider: aws
  region: us-east-1
  agentTiers:
    cloudNative:
      enabled: true
      agents:
        - name: aws-document-agent
          image: ghcr.io/yawlfoundation/yawl-aws-agent:latest
          replicas: 2
```

```yaml
# values-azure.yaml (Azure eastus)
cloud:
  provider: azure
  region: eastus
  agentTiers:
    cloudNative:
      enabled: true
      agents:
        - name: azure-cognitive-agent
          image: ghcr.io/yawlfoundation/yawl-azure-agent:latest
          replicas: 2
```

### Files Involved

- `org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry` (local)
- `org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient` (federation client)
- Helm: `charts/yawl-agents/values-aws.yaml`, `values-azure.yaml`, `values-gcp.yaml` (to be created)
- ArgoCD: `gitops/apps/yawl-agents-aws.yaml`, etc. (to be created)

### Effort: 2 weeks

---

## Pattern 11: Agent Coordination Protocol

**ADR**: [ADR-025](../docs/architecture/decisions/ADR-025-agent-coordination-protocol.md)

### Problem

Multiple autonomous agents polling for the same work items waste 75% of checkout
attempts (N-1 out of N agents fail with 409 on each item). There is no protocol for
agents to hand off work items they cannot complete, and no conflict resolution when
multiple agents produce different decisions for the same task.

### Solution: Three-Layer Coordination

**Layer 1: Partition Strategy (eliminates redundant checkouts)**

Before attempting checkout, each agent computes whether it is the "owner" of
a given work item using consistent hashing:

```java
// In PollingDiscoveryStrategy
private boolean isAssignedToThisAgent(WorkItemRecord item,
                                       int agentIndex,
                                       int totalAgents) {
    int hash = Math.abs(item.getID().hashCode());
    return (hash % totalAgents) == agentIndex;
}
// agentIndex and totalAgents read from AgentRegistry
// Refreshed every 5 discovery cycles to handle agent churn
```

Result: each work item is attempted by exactly 1 agent. Checkout failures drop
from 75% to near zero.

**Layer 2: Work Handoff (A2A message)**

When an agent discovers after checkout that it cannot complete a work item:

```
1. Query registry: GET /agents/by-capability?domain={required}
2. Send A2A message to capable agent:
   POST http://agent-b:8091/
   Body: YAWL_HANDOFF:{workItemId}:{signedJWT}
3. Roll back checkout via Interface B: POST /ib/workitems/{id}/rollback
4. Target agent checks out from Enabled state and completes
```

The handoff JWT has 60s TTL, signed with `A2A_API_KEY_MASTER`. If the target
agent does not acknowledge within 30s, the item returns to Enabled state for
any agent to pick up.

**Layer 3: Decision Conflict Resolution**

Configured per-task via `<agentBinding>` in the workflow specification:

```xml
<agentBinding>
  <reviewQuorum>3</reviewQuorum>
  <conflictResolution>MAJORITY_VOTE</conflictResolution>
  <conflictArbiter>supervisor-agent</conflictArbiter>
  <fallbackToHuman>true</fallbackToHuman>
</agentBinding>
```

Resolution tiers:
1. `MAJORITY_VOTE`: multi-instance task, arbiter collects votes
2. `ESCALATE`: arbiter agent receives all decisions and produces a resolution
3. Human fallback: work item returned to Enabled, offered to human participants

**Claude Agent SDK Integration**

Claude agents connect via two paths:

Path 1 (MCP tool use): Claude Desktop / Claude Code uses `YawlMcpServer`
tools (`checkout_work_item`, `complete_work_item`) directly via STDIO.

Path 2 (A2A orchestration): Claude orchestrator delegates to `YawlA2AServer`
using natural language: "Launch InvoiceProcessing workflow for invoice #42."

Context sharing across the boundary uses the `WorkflowEventStore`
(event sourcing module) as the durable, cross-agent shared log.

### Files to Create/Update

- `org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy`
  (add `partitionFilter()`)
- `org.yawlfoundation.yawl.integration.autonomous.strategies.HandoffProtocol` (new)
- `org.yawlfoundation.yawl.integration.autonomous.conflict.ConflictResolver` (new interface)
- `org.yawlfoundation.yawl.integration.autonomous.conflict.MajorityVoteConflictResolver` (new)
- `org.yawlfoundation.yawl.integration.autonomous.conflict.EscalatingConflictResolver` (new)
- `org.yawlfoundation.yawl.integration.autonomous.conflict.HumanFallbackConflictResolver` (new)

### Effort: 1 week

---

## MCP/A2A Architecture Diagrams

### MCP Server Deployment Flow

```
Developer / AI Client
        |
        | (1) STDIO: write JSON-RPC to stdin
        |
YawlMcpServer process
        |
        | (2) Parse tool call / resource read
        |
        | (3) Interface B: POST /ib/<operation>
        |
YAWL Engine (localhost:8080)
        |
        | (4) Return XML result
        |
YawlMcpServer
        |
        | (5) Write JSON-RPC response to stdout
        |
Developer / AI Client

-- Production variant (SSE transport) --

AI Agent (cloud)
        |
        | HTTPS + JWT
        |
API Gateway (Kong/Traefik/ALB)
        |
        | HTTP (internal cluster)
        |
yawl-mcp-gateway (Kubernetes, 2+ pods)
SSE transport: GET /mcp/sse, POST /mcp/messages
Session state: Redis
        |
        | Interface B (HTTP)
        |
YAWL Engine cluster (2+ pods, ADR-014)
```

### Agent Orchestration Topology

```
+--------------------+      A2A (HTTPS)     +---------------------+
|  Claude Orchestrator|--------------------->|  YawlA2AServer      |
|  (external agent)  |                      |  (Kubernetes, 3 pods)|
+--------------------+                      +----------+----------+
                                                        |
                                               Interface B (HTTP)
                                                        |
                                            +-----------v-----------+
                                            |  YAWL Engine cluster  |
                                            |  (ADR-014)            |
                                            +-----------+-----------+
                                                        |
                              +-------------------------+---------+
                              |                         |         |
                    +---------v--------+     +----------v--+  +---v-------+
                    | GenericAgent-1   |     | GenericAgent|  |AwsAgent   |
                    | (poll + execute) |     | (poll+exec) |  |(Textract) |
                    +------------------+     +-------------+  +-----------+
                              |
                         AgentRegistry
                    (federation via CockroachDB)
```

### Agent Discovery and Coordination Data Flow

```
Agent startup
     |
     v
POST /agents/register (AgentRegistry)
     |
     v
Discovery loop (virtual thread, Pattern 1)
     |
     v
GET /ib/workitems?status=Enabled (Interface B)
     |
     v
GET /agents/by-capability?domain=X (AgentRegistry)
     |
     v
Hash partition: item.id.hashCode() % totalAgents == myIndex?
  - YES: attempt checkout
  - NO:  skip this item
     |
     v
POST /ib/workitems/{id}/checkout
  - 200: proceed to EligibilityReasoner
  - 409: already taken, next item
  - 404: item gone, next item
     |
     v
EligibilityReasoner.canHandle(item, capability)
  - false: HandoffProtocol.handoff(item, registry) -> rollback
  - true:  DecisionReasoner.reason(item, data, context)
     |
     v
OutputGenerator.generate(decision, schema)
     |
     v
POST /ib/workitems/{id}/complete (Interface B)
     |
     v
WorkflowEventStore.append(AgentDecisionEvent) (audit trail)
```
