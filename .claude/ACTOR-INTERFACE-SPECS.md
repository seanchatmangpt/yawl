# Actor Model Interface Specifications — Technical Reference

**Status**: FINAL SPECIFICATION
**Date**: 2026-02-28
**Version**: 6.0.0
**Module**: yawl-engine
**Package**: `org.yawlfoundation.yawl.engine.agent.core`

---

## Overview

This document provides detailed interface specifications for the Actor model, including:
- Method signatures with type constraints
- Thread-safety guarantees
- Performance characteristics
- Error handling behavior
- Invariant contracts

---

## Table of Contents

1. [ActorRef Specification](#actorref-specification)
2. [Msg Specification](#msg-specification)
3. [ActorRuntime Specification](#actorruntime-specification)
4. [Supervisor Specification](#supervisor-specification)
5. [Internal Classes](#internal-classes)
6. [Thread Safety & Concurrency](#thread-safety--concurrency)
7. [Error Handling](#error-handling)
8. [Memory & Performance](#memory--performance)

---

## ActorRef Specification

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.ActorRef`

**Visibility**: PUBLIC

**Modifiers**: `final`

**Extends/Implements**: None

### Constructor

```java
// Package-private (only Runtime.spawn() creates ActorRef instances)
ActorRef(int id, ActorRuntime runtime)
```

**Parameters**:
- `id`: Actor identity (4B int, 0-2^31-1)
- `runtime`: Reference to ActorRuntime (typically VirtualThreadRuntime)

**Preconditions**:
- `runtime != null` (enforced by constructor)

**Invariants**:
- Fields are final: `private final int id`
- Fields are final: `private final ActorRuntime runtime`
- Instance is immutable: no setters, no state modifications

### Method: tell()

```java
public void tell(Object msg)
```

**Signature**:
- Returns: `void`
- Parameters:
  - `msg`: Message to send (any Object, may be null)

**Behavior**:
- Sends message to this actor's mailbox (fire-and-forget)
- Does not wait for processing or confirmation
- No-op if actor is dead (message silently dropped)
- No-op if runtime is closed

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Lock-free: Uses atomic ConcurrentHashMap + LinkedTransferQueue
- No blocking: Returns immediately

**Performance**:
- Expected latency: 50-150 nanoseconds (lock-free path)
- Expected throughput: >10M messages/sec on single core
- No GC pressure: no allocations (unless msg creates new objects)

**Error handling**:
- No exceptions thrown (intentionally)
- Dead actor: message dropped silently
- Closed runtime: message dropped silently

**Design rationale**:
- Fire-and-forget semantic matches message-driven concurrency
- Null-safe behavior prevents blocking on dead actors
- No exception throwing keeps hot path fast

**Optimization hints**:
- Marked with `@ForceInline` for JIT inlining
- Expected to inline to 2-3 instructions in hot path

### Method: ask()

```java
public CompletableFuture<Object> ask(Object query, Duration timeout)
```

**Signature**:
- Returns: `CompletableFuture<Object>` (never null)
- Parameters:
  - `query`: Message to send (typically Msg.Query)
  - `timeout`: Duration to wait for reply (must be positive)

**Behavior**:
- Sends query and returns CompletableFuture for reply
- Blocks until reply arrives, timeout expires, or thread interrupted
- Receiver must reply using Msg.Reply with matching correlationId
- CompletableFuture completes exceptionally on timeout

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Async: Returns immediately with CompletableFuture
- No blocking in calling thread (unless .get() called)

**Performance**:
- Expected latency: request send (100ns) + processing (app-dependent) + reply dispatch (500ns)
- Expected round-trip: 1-10 microseconds (application-dependent)

**Exceptions**:
- Throws: `TimeoutException` if no reply within timeout
- Throws: `InterruptedException` if thread interrupted while waiting
- Throws: `IllegalArgumentException` if timeout is negative/zero

**Design rationale**:
- Request-reply pattern for synchronous queries
- CompletableFuture for non-blocking async waits
- Correlation IDs prevent reply confusion

**Example usage**:
```java
ActorRef service = /* ... */;
long cid = System.nanoTime();
Msg.Query query = new Msg.Query(cid, selfRef, "GET_STATUS", null);
CompletableFuture<Object> reply = service.ask(query, Duration.ofSeconds(5));

reply.whenComplete((result, error) -> {
    if (error == null) {
        System.out.println("Got: " + result);
    } else {
        System.out.println("Error: " + error);
    }
});
```

### Method: stop()

```java
public void stop()
```

**Signature**:
- Returns: `void`
- Parameters: None

**Behavior**:
- Gracefully stops this actor
- Interrupts the actor's virtual thread
- Actor's message loop exits on next iteration
- No-op if actor already stopped

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Non-blocking: Returns immediately

**Performance**:
- Expected latency: < 1 millisecond

**Design rationale**:
- Virtual thread interruption is non-blocking (unlike OS threads)
- Allows actor to clean up resources before exit

### Method: id()

```java
public int id()
```

**Signature**:
- Returns: `int` (actor identity, 0-2^31-1)
- Parameters: None

**Behavior**:
- Returns the actor's ID
- For logging/debugging only (do NOT use for messaging)
- Do not use to construct new ActorRef (only Runtime.spawn() does this)

**Thread-safety**:
- **Thread-safe**: Reading final field, no synchronization needed

**Performance**:
- Expected latency: < 1 nanosecond (constant time)

### Method: equals()

```java
@Override
public boolean equals(Object o)
```

**Signature**:
- Returns: `boolean`
- Parameters:
  - `o`: Object to compare

**Behavior**:
- Returns true iff both ActorRefs refer to same actor in same runtime
- Equality by identity: `id == o.id && runtime == o.runtime`
- Reflexive: `ref.equals(ref)` = true
- Symmetric: `a.equals(b)` = `b.equals(a)`
- Transitive: if `a.equals(b)` and `b.equals(c)`, then `a.equals(c)`

**Performance**:
- Expected latency: < 10 nanoseconds (two field comparisons)

### Method: hashCode()

```java
@Override
public int hashCode()
```

**Signature**:
- Returns: `int`
- Parameters: None

**Behavior**:
- Returns hash code based on id (ignores runtime)
- Consistent: calling twice returns same value
- Equals objects have equal hash codes: if `a.equals(b)`, then `a.hashCode() == b.hashCode()`

**Rationale**:
- HashMap/HashSet performance: distributed by actor ID
- Runtime ref not included (same actor in different runtimes is still same ID)

**Performance**:
- Expected latency: < 10 nanoseconds (single field read)

### Method: toString()

```java
@Override
public String toString()
```

**Signature**:
- Returns: `String`
- Parameters: None

**Behavior**:
- Returns human-readable representation: `ActorRef(id=N, runtime=0xHEX)`
- Useful for logging and debugging

**Format**: `ActorRef(id=<id>, runtime=<identityHashCode>)`

**Example**: `ActorRef(id=42, runtime=0x7f1234ab)`

---

## Msg Specification

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.Msg`

**Visibility**: PUBLIC

**Modifiers**: `sealed`

**Extends/Implements**: None

**Permitted subtypes**:
1. `Msg.Command` (record)
2. `Msg.Event` (record)
3. `Msg.Query` (record)
4. `Msg.Reply` (record)
5. `Msg.Internal` (record)

### Record: Command

```java
public record Command(String action, Object payload) implements Msg {
    public Command {
        if (action == null) {
            throw new NullPointerException("action cannot be null");
        }
    }
}
```

**Fields**:
- `action`: String (not null, descriptive name of command)
- `payload`: Object (may be null, arbitrary data)

**Usage**:
- Fire-and-forget instructions
- No reply expected
- No correlation ID needed

**Example**:
```java
new Msg.Command("start-worker", workerConfig)
new Msg.Command("shutdown", null)
```

### Record: Event

```java
public record Event(String type, long timestamp, int source, Object data) implements Msg {
    public Event {
        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }
    }
}
```

**Fields**:
- `type`: String (not null, event classification)
- `timestamp`: long (System.nanoTime() when event occurred)
- `source`: int (actor ID that emitted event, for tracing)
- `data`: Object (may be null, event payload)

**Usage**:
- Notifications of state changes
- Broadcast to multiple listeners (eventually)
- No correlation ID needed

**Example**:
```java
new Msg.Event("WORK_COMPLETED", System.nanoTime(), actorId, result)
new Msg.Event("AGENT_READY", System.nanoTime(), actorId, null)
```

### Record: Query

```java
public record Query(long correlationId, ActorRef sender, String question, Object params) implements Msg {
    public Query {
        if (sender == null) {
            throw new NullPointerException("sender cannot be null");
        }
        if (question == null) {
            throw new NullPointerException("question cannot be null");
        }
    }
}
```

**Fields**:
- `correlationId`: long (unique ID for this query, typically System.nanoTime())
- `sender`: ActorRef (who sent this query, for reply routing)
- `question`: String (descriptive name of query)
- `params`: Object (may be null, query parameters)

**Invariants**:
- `sender` must not be null
- `question` must not be null
- `correlationId` should be unique per sender

**Usage**:
- Request for information or computation
- Receiver expected to reply with matching `Msg.Reply(correlationId, ...)`
- Sender blocks on ask() until reply or timeout

**Example**:
```java
long cid = System.nanoTime();
new Msg.Query(cid, selfRef, "GET_STATUS", null)
new Msg.Query(cid, selfRef, "COMPUTE", paramsObj)
```

### Record: Reply

```java
public record Reply(long correlationId, Object result, Throwable error) implements Msg {
    // Invariant: at least one of result or error should be non-null
}
```

**Fields**:
- `correlationId`: long (matches Query.correlationId)
- `result`: Object (may be null, result value)
- `error`: Throwable (may be null, error if query failed)

**Invariants**:
- Exactly one of `result` or `error` should be non-null (convention, not enforced)
- `correlationId` must match original `Query.correlationId`

**Usage**:
- Response to a Query
- Receiver of Query must send Reply with matching correlationId
- Reply dispatcher looks up correlationId in registry

**Example**:
```java
// Successful reply
new Msg.Reply(cid, "success", null)

// Error reply
new Msg.Reply(cid, null, new TimeoutException("No response"))
```

### Record: Internal

```java
public record Internal(String type, String reason, ActorRef supervisor) implements Msg {
    public Internal {
        if (type == null) {
            throw new NullPointerException("type cannot be null");
        }
    }

    public static final String RESTART = "RESTART";
    public static final String SHUTDOWN = "SHUTDOWN";
    public static final String PAUSE = "PAUSE";
    public static final String RESUME = "RESUME";
}
```

**Fields**:
- `type`: String (not null, control message type)
- `reason`: String (may be null, human-readable reason)
- `supervisor`: ActorRef (may be null, supervisor that initiated)

**Constants**:
- `RESTART`: Actor restart signal
- `SHUTDOWN`: Actor shutdown signal
- `PAUSE`: Actor pause signal (implementation-specific)
- `RESUME`: Actor resume signal (implementation-specific)

**Usage**:
- Control messages from supervisor to actor
- Internal use only (not for user application code)
- Supervision, restart, shutdown coordination

**Example**:
```java
new Msg.Internal("RESTART", "Restart after failure", supervisorRef)
new Msg.Internal("SHUTDOWN", "Graceful shutdown", supervisorRef)
```

---

## ActorRuntime Specification

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.ActorRuntime`

**Visibility**: PUBLIC

**Modifiers**: `interface`

**Extends**: `Closeable`

### Method: spawn()

```java
ActorRef spawn(java.util.function.Consumer<ActorRef> behavior)
```

**Signature**:
- Returns: `ActorRef` (never null)
- Parameters:
  - `behavior`: Function that receives ActorRef for self-reference

**Behavior**:
- Creates new actor with given behavior
- Behavior runs on dedicated virtual thread
- Returns immediately with ActorRef (async spawn)
- Behavior receives ActorRef for self-reference (to call tell/ask)

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Non-blocking: Returns immediately

**Performance**:
- Expected latency: < 1 millisecond (virtual thread creation is cheap)
- Expected throughput: > 100K actors/sec

**Exceptions**:
- Throws: `RejectedExecutionException` if executor is shutdown
- Throws: Other unchecked exceptions from behavior (caught internally)

**Design rationale**:
- Behavior receives ActorRef (not raw Object)
- To receive messages, behavior must read from mailbox (via ScopedValue.get())
- One virtual thread per actor (simpler model)

**Example**:
```java
ActorRef ref = runtime.spawn(selfRef -> {
    Agent self = Runtime.CURRENT.get();
    while (!Thread.interrupted()) {
        Object msg = self.q.take();
        // Process msg
    }
});
```

### Method: send()

```java
void send(int targetId, Object msg)
```

**Signature**:
- Returns: `void`
- Parameters:
  - `targetId`: Actor ID (int)
  - `msg`: Message to send

**Behavior**:
- Sends message to actor by ID
- No-op if actor not found (already dead)
- No-op if runtime closed

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Lock-free: Uses concurrent registry + atomic queue

**Performance**:
- Expected latency: 50-150 nanoseconds
- Lock-free path: single ConcurrentHashMap lookup + LinkedTransferQueue.offer()

**Design rationale**:
- Internal method (use ActorRef.tell() instead)
- Called by ActorRef.tell() internally

### Method: ask()

```java
CompletableFuture<Object> ask(int targetId, Object query, Duration timeout)
```

**Signature**:
- Returns: `CompletableFuture<Object>` (never null)
- Parameters:
  - `targetId`: Actor ID (int)
  - `query`: Message to send (typically Msg.Query)
  - `timeout`: Duration to wait

**Behavior**:
- Sends query and returns CompletableFuture
- Completes when reply arrives (within timeout) or timeout expires
- Requires receiving actor to send matching Msg.Reply

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Non-blocking: Returns CompletableFuture (doesn't block)

**Exceptions**:
- Throws: `TimeoutException` if no reply within duration
- Throws: `InterruptedException` if thread interrupted

**Design rationale**:
- Internal method (use ActorRef.ask() instead)

### Method: stop()

```java
void stop(int targetId)
```

**Signature**:
- Returns: `void`
- Parameters:
  - `targetId`: Actor ID to stop

**Behavior**:
- Gracefully stops actor by ID
- Interrupts virtual thread
- No-op if actor not found

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Non-blocking: Returns immediately

**Design rationale**:
- Called by ActorRef.stop() internally
- Virtual thread interruption is non-blocking

### Method: size()

```java
int size()
```

**Signature**:
- Returns: `int` (number of live actors)
- Parameters: None

**Behavior**:
- Returns current count of live actors
- Used for monitoring/debugging

**Thread-safety**:
- **Thread-safe**: Uses concurrent registry

**Performance**:
- Expected latency: < 1 microsecond (registry size query)

### Method: close()

```java
@Override
void close()
```

**Signature**:
- Returns: `void`
- Parameters: None
- Throws: `IOException` (from Closeable interface)

**Behavior**:
- Shuts down entire runtime
- Stops all actors
- Blocks until all actors are stopped (or timeout)
- Clears registry
- Shuts down virtual thread executor

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Blocking: Waits for shutdown to complete

**Performance**:
- Expected latency: depends on number of actors
- Worst case: 100ms per 1M actors (graceful shutdown)

**Design rationale**:
- Implements Closeable for try-with-resources
- Graceful shutdown (allows actors to clean up)

---

## Supervisor Specification

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.Supervisor`

**Visibility**: PUBLIC

**Modifiers**: `final`

**Extends/Implements**: None

### Constructor

```java
public Supervisor(ActorRuntime runtime, SupervisorStrategy strategy,
                  Duration restartDelay, int maxRestarts, Duration restartWindow)
```

**Parameters**:
- `runtime`: Underlying ActorRuntime
- `strategy`: Restart strategy (ONE_FOR_ONE, ONE_FOR_ALL, REST_FOR_ONE)
- `restartDelay`: Delay before respawning failed actor
- `maxRestarts`: Max restart count before escalation
- `restartWindow`: Time window for counting restarts

**Invariants**:
- `restartDelay` must be positive
- `maxRestarts` must be > 0
- `restartWindow` must be positive

**Example**:
```java
Supervisor supervisor = new Supervisor(
    runtime,
    SupervisorStrategy.ONE_FOR_ONE,
    Duration.ofMillis(500),
    3,
    Duration.ofSeconds(10)
);
```

### Method: spawn()

```java
public ActorRef spawn(String name, java.util.function.Consumer<ActorRef> behavior)
```

**Signature**:
- Returns: `ActorRef` (never null)
- Parameters**:
  - `name`: Descriptive name for logging (may not be unique)
  - `behavior`: Function implementing actor behavior

**Behavior**:
- Spawns actor with restart policy
- Wraps behavior to catch exceptions
- On failure: calls handleActorFailure() for restart logic
- Returns ActorRef immediately

**Thread-safety**:
- **Thread-safe**: Can be called from any thread
- Non-blocking: Returns immediately

**Design rationale**:
- Supervisor owns the spawn() — user code never calls runtime.spawn() directly
- Behavior receives ActorRef for self-reference

**Example**:
```java
ActorRef worker = supervisor.spawn("worker-1", selfRef -> {
    // Behavior implementation
});
```

### Method: start()

```java
public void start()
```

**Signature**:
- Returns: `void`
- Parameters: None

**Behavior**:
- Starts monitoring actors (begin health checks)
- Currently no-op (failures detected via exception)
- Future: heartbeat-based health monitoring

**Design rationale**:
- Separates spawn() (immediate) from monitoring (deferred)
- Allows setup before monitoring begins

### Method: stop()

```java
public void stop(boolean allowRestart)
```

**Signature**:
- Returns: `void`
- Parameters:
  - `allowRestart`: If false, prevent automatic restarts

**Behavior**:
- Stops supervising all child actors
- Sets allowRestart flag
- Graceful shutdown (waits for actors to finish)
- Closes underlying runtime

**Design rationale**:
- Control whether crashed actors are restarted during shutdown
- allowRestart=false prevents cascading restarts

### Method: registerDeadLetterHandler()

```java
public void registerDeadLetterHandler(java.util.function.Consumer<Object> deadLetterBehavior)
```

**Signature**:
- Returns: `void`
- Parameters:
  - `deadLetterBehavior`: Handler for undeliverable messages

**Behavior**:
- Creates internal actor to receive dead letter messages
- When message delivery fails, routes to dead letter actor
- Dead letter handler receives Msg.DeadLetter(original, targetId, reason)

**Design rationale**:
- Prevents message loss (undeliverable messages logged/handled)
- Dead letter actor is internal (user doesn't manage it)

### Enum: SupervisorStrategy

```java
public enum SupervisorStrategy {
    ONE_FOR_ONE,   // Restart only failed actor
    ONE_FOR_ALL,   // Restart all children
    REST_FOR_ONE   // Restart failed + all started after
}
```

**Values**:

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| **ONE_FOR_ONE** | Restart only failed actor | Independent workers |
| **ONE_FOR_ALL** | Restart all children | Tightly coupled team |
| **REST_FOR_ONE** | Restart failed + deps | Dependency chains |

---

## Internal Classes

### Agent (Package-Private)

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.Agent`

**Visibility**: Package-private (not part of public API)

**Modifiers**: `final`

```java
final class Agent {
    final int id;
    final LinkedTransferQueue<Object> q;

    Agent(int id) { ... }
    void send(Object msg) { ... }
    Object recv() { ... }
}
```

**Invariants**:
- Never instantiated by user code
- Only created by VirtualThreadRuntime.spawn()
- Never returned to users (wrapped in ActorRef)

**Memory**: 24 bytes per agent

### VirtualThreadRuntime (Public Implementation)

**Fully Qualified Name**: `org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime`

**Visibility**: Public

**Modifiers**: `final`

**Implements**: `ActorRuntime`

```java
final class VirtualThreadRuntime implements ActorRuntime {
    static final ScopedValue<Agent> CURRENT = ScopedValue.newInstance();
    // ... implementation
}
```

**Key features**:
- One virtual thread per actor (via ExecutorService)
- Lock-free registry (ConcurrentHashMap)
- LinkedTransferQueue mailbox (lock-free MPSC/MPMC)
- ScopedValue for context propagation

---

## Thread Safety & Concurrency

### Guarantee Matrix

| Method | Thread-Safe | Blocking | Lock-Free |
|--------|-------------|----------|-----------|
| ActorRef.tell() | ✓ | ✗ | ✓ |
| ActorRef.ask() | ✓ | ✓ (on CF) | ✓ |
| ActorRef.stop() | ✓ | ✗ | ✓ |
| ActorRuntime.spawn() | ✓ | ✗ | ✓ |
| ActorRuntime.send() | ✓ | ✗ | ✓ |
| ActorRuntime.ask() | ✓ | ✓ (on CF) | ✓ |
| ActorRuntime.close() | ✓ | ✓ | ✗ |
| Supervisor.spawn() | ✓ | ✗ | ✓ |
| Supervisor.stop() | ✓ | ✓ | ✗ |

### Data Structure Choices

| Data Structure | Use Case | Properties |
|---|---|---|
| **ConcurrentHashMap<Integer, Agent>** | Actor registry | Lock-free (at low contention), thread-safe |
| **LinkedTransferQueue<Object>** | Mailbox | Lock-free MPSC/MPMC, optimal for virtual threads |
| **ExecutorService (VirtualThread)** | Thread pool | One thread per actor, zero-cost abstraction |
| **ScopedValue<Agent>** | Context propagation | Immutable, thread-local alternative, zero-cost |
| **ConcurrentHashMap<Long, CompletableFuture>** | Reply registry | Lock-free correlation ID lookup |

### Contention Analysis

**Low contention** (< 1% false sharing):
- tell(): Single atomic CAS in LinkedTransferQueue, ~20-50 ns
- spawn(): Atomic increment + HashMap put, ~100-200 ns

**High contention** (> 10% false sharing):
- Actor registry locks (ConcurrentHashMap): minimal impact
- Virtual thread creation bottleneck (not mailbox)

**At 10M actors**:
- Mailbox throughput: 10M+ messages/sec per core
- Registry lookup: < 100 ns (with -XX:+UseCompactObjectHeaders)

---

## Error Handling

### Philosophy: "Real impl ∨ throw UnsupportedOperationException"

All methods must:
1. Implement real behavior, OR
2. Throw exception (never silent failure)

### Error Cases

| Scenario | Behavior | Exception |
|----------|----------|-----------|
| Actor dead | Message dropped silently | None (no-op) |
| Runtime closed | Message dropped silently | None (no-op) |
| Ask timeout | CompletableFuture fails | TimeoutException |
| Interrupted | CompletableFuture fails | InterruptedException |
| Negative timeout | Rejected | IllegalArgumentException |
| Behavior throws | handleActorFailure() called | Caught in wrapper |
| Executor shutdown | spawn() fails | RejectedExecutionException |

### Null-Safety

| Method | Null-Safe | Behavior |
|--------|-----------|----------|
| tell(null) | Yes | Message sent as-is |
| ask(null, ...) | No | Fails upstream (NPE) |
| spawn(null) | No | NPE in constructor |
| Registry.get(missing) | Yes | Returns null (handled) |

---

## Memory & Performance

### Memory Layout

```
per-actor-memory-layout:
  ObjectHeader          12 B
  Agent.id               4 B
  Agent.queue            8 B
  LinkedTransferQueue   40 B
  VirtualThread (idle)  64 B
  ScopedValue (amort)    4 B
  ─────────────────────────
  TOTAL              ~132 B (vs Erlang: 1,856 B)
```

### Performance Targets

| Operation | Target | Expected |
|-----------|--------|----------|
| tell() throughput | > 1M msgs/sec | ~10M msgs/sec |
| ask() latency | < 10 µs | ~1-5 µs |
| spawn() throughput | > 100K actors/sec | ~1M actors/sec |
| Memory per idle actor | < 200 B | ~132 B |

### Optimization Techniques

| Technique | Impact | Status |
|-----------|--------|--------|
| @ForceInline on tell() | 2-3× faster (JIT inlining) | Applied |
| Sealed pattern matching | Zero virtual dispatch | Applied |
| LinkedTransferQueue | Lock-free mailbox | Applied |
| ScopedValue | No ThreadLocal overhead | Applied |
| CompactObjectHeaders | 4-8 B per object saved | -XX:+UseCompactObjectHeaders |
| CompressedOops | 4 B per reference | Default in 64-bit JVM |

---

## Summary Table: Quick Reference

| Interface | Methods | Key Invariant |
|-----------|---------|---|
| **ActorRef** | tell(), ask(), stop(), id(), equals(), hashCode(), toString() | Immutable value type (20 bytes) |
| **Msg (sealed)** | Command, Event, Query, Reply, Internal | Pattern matching exhaustive |
| **ActorRuntime** | spawn(), send(), ask(), stop(), size(), close() | Pluggable implementation |
| **Supervisor** | spawn(), start(), stop(), registerDeadLetterHandler() | OTP restart discipline |

---

**Complete specification ready for implementation**
**Version**: 6.0.0-GA
**Date**: 2026-02-28
