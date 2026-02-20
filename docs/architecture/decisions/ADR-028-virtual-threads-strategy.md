# ADR-028: Virtual Threads Deployment Strategy

## Status

**ACCEPTED**

## Date

2026-02-20

## Context

YAWL v6.0.0 already requires Java 25 (ADR-004), which includes production-ready virtual threads (Project Loom, JEP 444). However, the specific deployment strategy for virtual threads across YAWL's architecture has not been formally documented.

### Current Thread Usage

1. **Autonomous Agents**: `GenericPartyAgent.discoveryThread` uses platform threads (~2MB each)
   - 1000 agents = ~2GB memory overhead
   - Platform thread context switches are expensive
   - Thread pool limits to ~10,000 concurrent threads per JVM

2. **Event Notification**: `MultiThreadEventNotifier` already uses `Executors.newVirtualThreadPerTaskExecutor()` ✅

3. **Servlet Container**: InterfaceB servlet requests run on Tomcat thread pool (configurable, typically 200 threads)

4. **Workflow Execution**: Each case execution may spawn sub-threads for parallel work item processing

### Scaling Problem

At 10,000 concurrent workflow cases + 1000 agents:
- **Memory**: ~20GB+ for thread stacks alone
- **Context Switch Cost**: OS scheduler overwhelmed
- **Virtual Memory Pressure**: Leads to GC pauses, performance degradation

**Virtual threads solve this**: 10,000 concurrent operations in ~10MB heap.

## Decision

**YAWL v6.0.0 adopts a three-tier virtual thread strategy:**

### Tier 1: Agent Discovery Loops (Immediate)

**Scope**: All long-running agent polling loops

```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent
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
    // Virtual thread terminates naturally when loop exits
}

private void runDiscoveryLoop() {
    while (running.get()) {
        try {
            discoverAndProcessWorkItems();
            Thread.sleep(config.getPollInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

**Benefits:**
- 1000 agents: 2GB → ~1MB memory
- No thread pool configuration required
- Automatic cleanup on stop()

**Effort**: 1 day | **Risk**: Low

---

### Tier 2: Work Item Processing (Structured Concurrency)

**Scope**: Parallel processing of work items discovered in batches

```java
// In DiscoveryStrategy or GenericPartyAgent
public void processWorkItemsInParallel(List<YWorkItem> items)
        throws InterruptedException, ExecutionException {

    // Use structured concurrency for bounded parallelism
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<YWorkItem>> tasks = items.stream()
            .map(item -> scope.fork(() -> processWorkItem(item)))
            .toList();

        scope.join();           // Wait for all tasks
        scope.throwIfFailed();  // Propagate first failure, cancel others

        // All tasks completed successfully
        tasks.forEach(task -> completeItem(task.resultNow()));
    }
}

private YWorkItem processWorkItem(YWorkItem item)
        throws YStateException, YDataStateException {
    try {
        // Check eligibility
        if (!reasoner.canHandle(item, capability)) {
            // Handoff to capable agent
            handoffService.initiateHandoff(item.getId(), config.getAgentId());
            return item;  // Rolled back
        }

        // Reason about decision
        YDecision decision = reasoner.reason(item, item.getData(), context);

        // Generate output
        Document output = generator.generate(decision, item.getSchema());

        // Complete work item
        engine.completeWorkItem(item.getId(), output, null);

        return item;
    } catch (Exception e) {
        throw new YDataStateException("Work item processing failed", e);
    }
}
```

**Benefits:**
- Fan-out to 10-100 items per discovery cycle
- Automatic cancellation on first failure
- Parent-child visibility in thread dumps
- No manual Future management

**Execution Flow:**
```
Discovery Loop (Virtual Thread)
    ↓
GET /workitems?status=Enabled (50 items)
    ↓
Structured Task Scope (50 virtual threads)
    ├── Virtual Thread: Process item 1
    ├── Virtual Thread: Process item 2
    │   (DB call: 100ms, yields carrier)
    ├── Virtual Thread: Process item 3
    │   (HTTP call: 200ms, yields carrier)
    └── ...
    ↓
All items completed (or first failure cancels rest)
    ↓
Loop continues with next discovery cycle
```

**Effort**: 2 days | **Risk**: Low (new feature, no breaking changes)

---

### Tier 3: Case Execution Threading (Forward-Looking)

**Scope**: Per-case execution threads (optional in v6.0.0, planned for v6.1)

```java
// Future enhancement: Thread-per-case model
// org.yawlfoundation.yawl.engine.YEngine

public String launchCase(YSpecificationID specID, String caseParams, ...)
        throws YStateException {

    YCase caseObj = new YCase(specID, caseParams);
    String caseID = caseObj.getID();

    // Spin up virtual thread for case execution
    Thread.ofVirtual()
        .name("yawl-case-" + caseID)
        .start(() -> {
            try {
                YNetRunner runner = new YNetRunner(caseObj);
                runner.continueIfPossible();
            } catch (Exception e) {
                announcer.announceCaseEvent(
                    caseID,
                    YEventType.CASE_FAILED,
                    e
                );
            }
        });

    return caseID;
}
```

**Benefits:**
- Each case runs isolated on its own virtual thread
- No global thread pool; scales to 10,000+ concurrent cases
- Blocking I/O (DB, HTTP) yields carrier; doesn't block other cases

**Effort**: Not planned for v6.0.0 (requires architectural changes)

**Risks**: Medium (large refactoring)

---

## Virtual Thread Pinning: What to Avoid

### Problem: Synchronized Blocks

Virtual threads can be "pinned" to carrier threads when executing synchronized blocks. If the virtual thread waits for I/O while holding a lock, the carrier thread is blocked.

```java
// BAD: Pins virtual thread during I/O
public synchronized void saveWorkItem(YWorkItem item) {
    db.save(item);  // 100ms I/O; carrier thread blocked!
}

// GOOD: Lock released before I/O
private final ReentrantLock lock = new ReentrantLock();

public void saveWorkItem(YWorkItem item) {
    lock.lock();
    try {
        // Short critical section: validate state, update in-memory structures
        item.setState(newState);
        registry.update(item);
    } finally {
        lock.unlock();
    }
    // I/O happens outside the lock
    db.save(item);
}
```

### Detection: Trace Pinned Threads

```bash
# JVM flag to detect and log pinning events
-Djdk.tracePinnedThreads=short

# Output: "Virtual thread pinned for 100.5ms"
# Helps identify problematic synchronized blocks
```

### Audit: Find Synchronized Methods

```bash
grep -r "public synchronized" src/main/java/org/yawlfoundation/
```

**Action**: Replace with ReentrantLock in:
- `org.yawlfoundation.yawl.engine.YNetRunner`
- `org.yawlfoundation.yawl.stateless.engine.YCaseMonitor`
- Any persistent state mutations

---

## Database Connection Pooling

Virtual threads change thread pool requirements:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # Pool sized for CPU cores, NOT concurrent threads
      maximum-pool-size: 10    # Typically 2-3x CPU cores
      minimum-idle: 5
      connection-timeout: 30000

      # Virtual threads wait efficiently on connection acquisition
      # No need for massive pools like platform threads require
```

**Rationale:**
- 10,000 concurrent virtual threads can share 10 database connections
- When a virtual thread waits for a connection, it yields the carrier thread
- Carrier threads (1 per CPU core) continue executing other virtual threads
- Result: High concurrency with minimal connection overhead

---

## ScopedValues for Context Inheritance

Virtual threads require **ScopedValue** instead of ThreadLocal for context propagation:

```java
// Global context values
public class WorkflowContext {
    public static final ScopedValue<String> WORKFLOW_ID = ScopedValue.newInstance();
    public static final ScopedValue<SecurityContext> SECURITY = ScopedValue.newInstance();
    public static final ScopedValue<AuditLog> AUDIT = ScopedValue.newInstance();
}

// Usage: Context is automatically inherited by forked virtual threads
ScopedValue.where(WorkflowContext.WORKFLOW_ID, "wf-123")
    .where(WorkflowContext.SECURITY, securityContext)
    .where(WorkflowContext.AUDIT, auditLog)
    .run(() -> {
        // Virtual threads forked here automatically see these values
        agentProcessWorkItems();
    });
```

**See ADR-030** for detailed ScopedValue strategy.

---

## JVM Configuration

### Production Baseline

```bash
# Virtual thread scheduler tuning
-Djdk.virtualThreadScheduler.parallelism=8     # Default: CPU cores

# Thread pinning detection (development)
-Djdk.tracePinnedThreads=short

# For high-concurrency deployments (10,000+ virtual threads)
-Djdk.virtualThreadScheduler.maxCacheSize=50000

# Container support
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
```

### Kubernetes Deployment

```yaml
# deployment.yaml
spec:
  containers:
    - name: yawl-engine
      env:
        - name: JAVA_OPTS
          value: |
            -XX:+UseCompactObjectHeaders
            -XX:+UseZGC
            -XX:+UseContainerSupport
            -XX:MaxRAMPercentage=75.0
            -Djdk.virtualThreadScheduler.parallelism=4
```

---

## Monitoring Virtual Threads

### JFR (Java Flight Recorder)

```bash
# Start JFR recording
jcmd <pid> JFR.start settings=default filename=recording.jfr duration=60s

# Check virtual thread metrics
jfr dump --json --events "jdk.VirtualThreadStart,jdk.VirtualThreadEnd" recording.jfr
```

### Micrometer Metrics

```java
@Component
public class VirtualThreadMetrics {
    private final MeterRegistry registry;

    public VirtualThreadMetrics(MeterRegistry registry) {
        this.registry = registry;

        // Track active virtual threads
        Gauge.builder("yawl.virtual.threads.active", () -> {
            // Count active virtual threads in discovery loops
            return GenericPartyAgent.getActiveThreadCount();
        })
            .description("Active virtual threads (agents)")
            .register(registry);

        // Track task scope completions
        Counter.builder("yawl.structured.scope.completions")
            .description("Completed structured task scopes")
            .register(registry);
    }
}
```

### Thread Dumps

```bash
# Show virtual thread state
jcmd <pid> Thread.print

# Output includes:
# "yawl-agent-discovery-1" #1234 virtual
# java.lang.VirtualThread State: TIMED_WAITING
# Carrier thread: "ForkJoinPool-..." #15
```

---

## Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Thread pinning in synchronized blocks | MEDIUM | HIGH | Replace synchronized with ReentrantLock (Tier 1) |
| Excessive virtual thread creation | LOW | MEDIUM | Rate limiting on case launch; monitor with JFR |
| Database connection pool exhaustion | LOW | MEDIUM | Pool sizing rule: cores × 2-3, not threads |
| Carrier thread starvation | LOW | MEDIUM | Monitor via JFR; tune parallelism if needed |

---

## Implementation Timeline

| Phase | Duration | Scope | Effort |
|-------|----------|-------|--------|
| **Phase 1** | Week 1 | Agent discovery loops (virtual threads) | 1 day |
| **Phase 2** | Week 2 | Work item batch processing (structured concurrency) | 2 days |
| **Phase 3** | Week 3-4 | Thread audits: remove synchronized; fix pinning | 2 days |
| **Phase 4** | Week 5 | Testing & performance validation | 2 days |

---

## Alternatives Considered

### Alternative 1: Stay with Platform Threads

**Rejected.** Limits scalability to 10,000 concurrent operations; wastes memory.

### Alternative 2: Reactive (WebFlux / Project Reactor)

```java
// Reactive approach (WebFlux)
return engine.getWorkItemsAsync()
    .flatMap(item -> processItemAsync(item))
    .subscribeOn(Schedulers.boundedElastic())
    .toList();
```

**Rejected for v6.0.0.** Virtual threads provide similar benefits without API overhaul.

### Alternative 3: Actor Model (Akka/Pekko)

**Rejected.** Requires architectural redesign; virtual threads sufficient.

### Alternative 4: Coroutines (Kotlin)

**Rejected.** YAWL is Java-native; virtual threads are native Java solution.

---

## Related ADRs

- **ADR-004**: Spring Boot 3.4 + Java 25 (enables virtual threads)
- **ADR-029**: Structured Concurrency Patterns
- **ADR-030**: Scoped Values Context Management
- **ADR-010**: Virtual Threads Scalability (existing pre-ADR)

---

## References

- JEP 444: Virtual Threads (finalized Java 21, production-ready Java 25)
- JEP 505: Structured Concurrency
- JEP 506: Scoped Values
- Virtual Threads Guide: https://openjdk.org/jeps/444
- "Practical Virtual Threads": https://inside.java/2023/10/09/what-are-virtual-threads/

---

## Approval

**Approved by:** YAWL Architecture Team, Performance Team
**Date:** 2026-02-20
**Implementation Status:** PLANNED (v6.0.0, Tier 1-2)
**Review Date:** 2026-08-20

---

**Revision History:**
- 2026-02-20: Initial ADR (Tier 1-2 strategy)
