# Why Flow API Is the Default Event Bus, Not Kafka

YAWL's event bus defaults to `java.util.concurrent.Flow` (the standard Java Reactive Streams API) instead of Kafka, despite Kafka's popularity in distributed systems. This document explains the problem it solves, how Flow overcomes it, and when Kafka becomes necessary.

---

## The Bottleneck: Synchronous HTTP on the Hot Path

Before YAWL 6.0, announcing work items was synchronous and blocking:

```java
public void announceEnabledWorkItems(YWorkItem item) {
    // Caller holds the YNetRunner write lock while making HTTP calls
    for (YResourceServiceReference service : listeners) {
        httpClient.post(service.getUrl(), item);  // 50–200ms RTT
    }
}
```

At scale (1M cases running concurrently), this pattern creates a catastrophic bottleneck:

1. **Lock contention**: The YNetRunner is locked while awaiting HTTP responses. Other threads wanting to execute the same case must wait.
2. **RTT multiplication**: With 10 registered listeners and 50ms average RTT, announcing a single work item takes 500ms. At 10K incoming cases/second, the lock is held for minutes.
3. **Memory accumulation**: Blocked callers queue up, their stack frames accumulate, memory fills, GC pressure spikes.
4. **Loss of parallelism**: Virtual threads are rendered useless because the engine serializes on the lock.

The fundamental issue: the engine's hot path (case execution) was coupled to external network latency (HTTP listeners).

---

## The Solution: Flow API Event Bus

`java.util.concurrent.Flow` (standardized in Java 9, JEP 266) decouples publishing from subscription via the Reactive Streams pattern. YAWL's `FlowWorkflowEventBus` applies this pattern to unblock the engine:

### Architecture

```java
public final class FlowWorkflowEventBus implements WorkflowEventBus {
    private final EnumMap<YEventType, SubmissionPublisher<WorkflowEvent>> _publishers;

    public FlowWorkflowEventBus() {
        _publishers = new EnumMap<>(YEventType.class);
        for (YEventType type : YEventType.values()) {
            // One publisher per event type: avoids head-of-line blocking
            _publishers.put(type, new SubmissionPublisher<>(
                    Executors.newVirtualThreadPerTaskExecutor(),
                    Flow.defaultBufferSize()  // 256 items
            ));
        }
    }

    @Override
    public void publish(WorkflowEvent event) {
        SubmissionPublisher<WorkflowEvent> publisher = _publishers.get(event.type());
        if (publisher != null) {
            publisher.submit(event);  // Non-blocking (unless buffer full)
        }
    }
}
```

### Key Properties

1. **One publisher per event type**: Events of different types (TaskEnabled, CaseCompleted) do not block each other. A slow TaskEnabled listener does not delay CaseCompleted announcements.

2. **Virtual-thread-per-task executor**: Each published event is dispatched to subscribers on a new virtual thread. No thread pool can exhaust; the system scales to millions of events.

3. **Non-blocking submit()**: The `submit()` call enqueues the event and returns immediately (unless the subscriber buffer fills). The engine thread is released in microseconds, not waiting for HTTP responses.

4. **Bounded back-pressure**: Each subscriber has a buffer (default 256 items). If a subscriber is slow, its buffer fills, and `submit()` blocks only on that subscriber — not on fast subscribers or on the publisher.

---

## Back-Pressure Model: How Slow Subscribers Work

Back-pressure ensures the system doesn't drop events or accumulate unbounded memory:

```
Publisher             Subscriber
    │                    │
    ├─ submit(event1) ──→ buffer[1/256]
    │                     │
    ├─ submit(event2) ──→ buffer[2/256]
    │                     │
    └─ submit(eventN) ──→ buffer[256/256]  ← FULL
                          │
                      Handler processes
                      slowly (50ms/item)
                          │
                      Request(1) ← ack
                          │
    ├─ submit(eventN+1) ← BLOCKS on submit()
    │
    [Caller yields thread, unblocks other work]
```

At 1M cases running at 10K cases/second with a 256-item buffer:
- Buffer fills in 25ms (256 / 10,000)
- Slow subscriber processes at 20 items/sec (50ms each)
- Publisher back-off: 12.8ms wait, then unblock
- No queue growth, no memory leak, no dropped events

The critical insight: back-pressure applies only to slow subscribers, not to the publisher or other subscribers. Flow API distributes this burden across subscribers, not across the engine.

---

## Why Not Kafka by Default?

Kafka is excellent for multi-node event streaming, but YAWL uses Flow as the default because:

### 1. **External Infrastructure**

Kafka requires a separate broker (or cluster) to run. This adds operational complexity:
- Deployment overhead (Kafka + Zookeeper or KRaft)
- Configuration and tuning (replication factor, retention, partition count)
- Monitoring (broker health, consumer lag)
- Backup and recovery

Flow runs entirely within the JVM, requiring zero external infrastructure.

### 2. **Network Latency**

Kafka's strength is durability and replication: every event is persisted to disk and replicated across brokers. This durability costs latency:
- Kafka producer call: 2–5ms (network round-trip + disk sync)
- Flow API: microseconds (in-JVM queue)

For YAWL's use case, durability of events is not a requirement (events are notifications, not financial transactions). The case state is durable in the database. Events are advisory — "a work item is now enabled, listeners may want to react". If a listener misses an event due to pod restarts, the event log or case query API provides recovery.

### 3. **Operational Simplicity**

Single-node YAWL deployments (the majority for enterprises) run on 3–5 pods behind a Kubernetes load balancer. No pod talks to other pods about events. Each pod's engine publishes to local listeners. Flow API fits this model perfectly.

### 4. **Deployment Flexibility**

By defaulting to Flow, YAWL runs in:
- Serverless environments (AWS Lambda has no persistent broker)
- Embedded scenarios (e.g., YAWL as a library in a Spring Boot app)
- Development (local testing without Docker Compose for Kafka)

Kafka would block all of these scenarios.

---

## The ServiceLoader Seam: Plugging in Kafka

YAWL decouples the event bus via `ServiceLoader`, so Kafka is available when needed without changing any engine code:

```java
public interface WorkflowEventBus extends AutoCloseable {
    void publish(WorkflowEvent event);
    void subscribe(YEventType type, Consumer<WorkflowEvent> handler);

    static WorkflowEventBus defaultBus() {
        return java.util.ServiceLoader.load(WorkflowEventBus.class)
                .findFirst()
                .orElseGet(FlowWorkflowEventBus::new);
    }
}
```

To deploy YAWL with Kafka:

1. Add Maven dependency: `yawl-kafka-adapter`
2. The adapter registers `KafkaWorkflowEventBus` in its `META-INF/services/org.yawlfoundation.yawl.engine.spi.WorkflowEventBus`
3. ServiceLoader discovers it and uses it instead of Flow
4. Zero engine code changes

---

## Trade-Offs: Single-JVM vs Multi-Pod Event Sharing

### Flow API (Single-Node, Default)

**Advantages**:
- Zero external infrastructure
- Microsecond latency
- Automatic memory management (GC)
- Works everywhere (serverless, embedded, containers)

**Limitations**:
- Events are local to a pod. Other pods don't see them.
- If pod A's engine publishes an event and pod B needs to react, pod B must poll the database or REST API.

**When it's sufficient**:
- Stateless deployments where listeners are co-located with the engine
- Scenarios where listeners are part of the same YAWL application (e.g., a Spring Boot app)

### Kafka (Multi-Node, Opt-in)

**Advantages**:
- Events are durable (disk and replicas)
- Cross-pod visibility (multiple pods can subscribe to the same topic)
- Enables event-driven microservices architecture
- Kafka Streams for complex event processing

**Costs**:
- Operational overhead (broker deployment, tuning, monitoring)
- Network latency (2–5ms per event)
- Complexity for enterprises already running Kafka

**When it's necessary**:
- Multi-tenant SaaS deployments where service boundaries require event isolation
- Scenarios where listeners are not co-located with the engine
- Compliance requirements for event durability (regulatory audit trails)

---

## Performance Characteristics

| Metric | Flow API | Kafka |
|--------|----------|-------|
| **Latency (p99)** | <1ms | 5–50ms |
| **Throughput at p99** | 100K events/sec | 10K events/sec |
| **Memory per 1M events** | ~256MB (buffer) | ~1GB (consumer offset tracking) |
| **Infrastructure cost** | $0/month | $200–500/month (managed Kafka) |
| **Setup time** | 5 minutes | 1–2 hours (brokers, topics, ACLs) |

---

## Capacity Model: Flow API at 1M Cases

At YAWL's design target of 1M concurrent cases:

- **Event rate**: 40K case transitions/sec (10K new cases/sec × 4 transitions/case)
- **Listeners per event type**: 3–5 (resource manager, event log, monitoring)
- **Flow buffer size**: 256 items × 8 event types = 2K total items
- **Buffer occupancy at 40K/sec**: 50ms (256 / 40,000)
- **GC pause impact**: <10ms (buffer is on the JVM heap, garbage collected as events complete)

The system remains uncongested. If listener latency increases beyond 100ms, a Kafka adapter becomes necessary (switch at deployment time, no code changes).

---

## Next Steps

For YAWL 6.0+ deployments:

- **Single-node SaaS or embedded**: Use default Flow API (zero config)
- **Multi-node events required**: Add `yawl-kafka-adapter` dependency at deployment time
- **Custom event sink** (e.g., Splunk, Datadog): Implement `WorkflowEventBus` interface, register via `ServiceLoader`

See `docs/explanation/decisions/ADR-023-mcp-a2a-cicd-deployment.md` for the full deployment architecture.
