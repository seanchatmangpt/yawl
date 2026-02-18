# Virtual Thread Migration Plan
# YAWL v5.2 — No Capabilities Changed

**Date**: February 2026 | **Branch**: claude/create-roadmap-Ysol8
**Constraint**: Same behavior, same APIs, same error semantics — platform threads → virtual threads only.

---

## Executive Summary

The YAWL codebase is **already largely migrated**. Most infrastructure layers (HTTP clients,
event notifiers, authentication schedulers, MCP/A2A servers, webhook delivery, engine observers)
already use `Thread.ofVirtual()` or `Executors.newVirtualThreadPerTaskExecutor()`.

Remaining platform-thread work falls into three categories:

1. **3 ThreadFactory lambdas** — still use `new Thread(r, name)` inside factory lambdas (Sites T-1, T-2, T-3)
2. **2 Swing worklist sites** — `new Thread()` subclassed and passed to `EventQueue.invokeLater()` (Sites T-5, T-6)
3. **procletService legacy** — `ThreadNotify` and `SingleInstanceClass.InternalRunner` extend Thread with `synchronized run()` + `wait()`/`notify()` (Sites T-7, T-8, P-1 through P-3)

There are also secondary **pinning risks** — `synchronized` blocks that wrap blocking I/O —
that must be addressed to make virtual threads effective:

- procletService rendezvous protocol (HIGH)
- `SMSSender` synchronized HTTP calls (MEDIUM)
- `JavaCPN.send()` synchronized socket I/O (MEDIUM)
- `YPersistenceManager.doPersistAction()` synchronized JDBC (MEDIUM-HIGH)

---

## 1. Complete Thread Creation Inventory

### 1.1 Already on Virtual Threads — NO ACTION NEEDED

| File | Pattern | Notes |
|------|---------|-------|
| `util/Sessions.java:54` | `newScheduledThreadPool(1, r -> Thread.ofVirtual().unstarted(r))` | Reference pattern |
| `authentication/YSessionCache.java:110` | `newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())` | |
| `authentication/YSessionTimer.java:54` | `newScheduledThreadPool(N, Thread.ofVirtual().factory())` | |
| `integration/autonomous/AgentRegistry.java:111` | `newSingleThreadScheduledExecutor(r -> Thread.ofVirtual().name(...).unstarted(r))` | |
| `integration/autonomous/GenericPartyAgent.java:102` | `Thread.ofVirtual().name(...).start(...)` | Referenced in ROADMAP |
| `integration/autonomous/registry/AgentHealthMonitor.java:67` | `Thread.ofVirtual().name("AgentHealthMonitor").start(this)` | |
| `integration/autonomous/registry/AgentRegistry.java:87` | `server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())` | |
| `integration/autonomous/observability/MetricsCollector.java:60` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `integration/autonomous/observability/HealthCheck.java:52` | HttpClient + server executor, both virtual | |
| `integration/webhook/WebhookDeliveryService.java:117` | `deliveryExecutor = Executors.newVirtualThreadPerTaskExecutor()` | Note: retryScheduler NOT yet migrated (T-1) |
| `integration/spiffe/SpiffeWorkloadApiClient.java:182` | `Thread.ofVirtual().name("spiffe-auto-rotation").start(...)` | |
| `integration/spiffe/SpiffeMtlsHttpClient.java:96` | `HttpClient.executor(Executors.newVirtualThreadPerTaskExecutor())` | |
| `integration/a2a/YawlA2AServer.java:157` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `integration/mcp/YawlMcpServer.java:260` | Shutdown hook virtual thread | |
| `integration/mcp/spring/YawlMcpSpringApplication.java:143` | Shutdown hook virtual thread | |
| `integration/orderfulfillment/PartyAgent.java:171,236,368` | All virtual | |
| `engine/ObserverGatewayController.java:66` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `engine/interfce/interfaceB/InterfaceB_EnvironmentBasedServer.java:74` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `engine/interfce/interfaceB/InterfaceB_EngineBasedClient.java:355` | Per-service virtual executor | |
| `engine/interfce/interfaceX/InterfaceX_EngineSideClient.java:78` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `engine/interfce/Interface_Client.java:63` | HttpClient virtual executor | |
| `documentStore/DocumentStoreClient.java:46` | HttpClient virtual executor | |
| `util/HttpURLValidator.java:53` | HttpClient virtual executor | |
| `engine/actuator/health/YExternalServicesHealthIndicator.java:71` | HttpClient + executor, both virtual | |
| `logging/YEventLogger.java:122` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `stateless/engine/SingleThreadEventNotifier.java:19` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `stateless/engine/MultiThreadEventNotifier.java:30` | `Executors.newVirtualThreadPerTaskExecutor()` | |
| `smsModule/SMSSender.java:121` | `Thread.ofVirtual().name("ReplyPoller").start(this)` | Poller VT; body still has synchronized I/O (P-5) |

### 1.2 Still Using Platform Threads — REQUIRES CHANGE

**T-1: WebhookDeliveryService.retryScheduler**
```
File:     src/org/yawlfoundation/yawl/integration/webhook/WebhookDeliveryService.java
Line:     118–125
Current:  Executors.newScheduledThreadPool(2, r -> {
              Thread t = new Thread(r, "yawl-webhook-retry");
              t.setDaemon(true);
              return t;
          });
Proposed: Executors.newScheduledThreadPool(2, r ->
              Thread.ofVirtual().name("yawl-webhook-retry").unstarted(r))
Risk:     LOW
Reason:   Retry callbacks only; no synchronized wrapping I/O. Daemon flag not
          needed for virtual threads (scheduler lifecycle controls termination).
```

**T-2: OAuth2TokenValidator.refreshScheduler**
```
File:     src/org/yawlfoundation/yawl/integration/oauth2/OAuth2TokenValidator.java
Line:     144–148
Current:  Executors.newSingleThreadScheduledExecutor(r -> {
              Thread t = new Thread(r, "yawl-jwks-refresh");
              t.setDaemon(true);
              return t;
          });
Proposed: Executors.newSingleThreadScheduledExecutor(r ->
              Thread.ofVirtual().name("yawl-jwks-refresh").unstarted(r))
Risk:     LOW
Reason:   JWKS refresh does HTTP GET — ideal for VT (unmounts during I/O wait).
          No synchronized wrapping around the HTTP call.
```

**T-3: YawlLanguageServer.executor**
```
File:     src/org/yawlfoundation/yawl/tooling/lsp/YawlLanguageServer.java
Line:     80–84
Current:  Executors.newSingleThreadExecutor(r -> {
              Thread t = new Thread(r, "yawl-lsp-handler");
              t.setDaemon(true);
              return t;
          });
Proposed: Executors.newSingleThreadExecutor(r ->
              Thread.ofVirtual().name("yawl-lsp-handler").unstarted(r))
Risk:     LOW
Reason:   LSP requires sequential message handling; single-thread virtual executor
          preserves that. No synchronized I/O wrapping.
```

**T-4: NamedThreadFactory.newThread()**
```
File:     src/org/yawlfoundation/yawl/util/NamedThreadFactory.java
Line:     23–25
Current:  Thread thread = new Thread(runnable);
          thread.setName(_prefix + _index.getAndIncrement());
          return thread;
Proposed: return Thread.ofVirtual()
              .name(_prefix, _index.getAndIncrement())
              .unstarted(runnable);
Risk:     MEDIUM
Reason:   No internal call sites found in src/. It is a public API; any external
          callers that introspect thread type (instanceof, thread groups) would see
          a change. If backward compat needed, add VirtualNamedThreadFactory companion
          and migrate internal call sites; leave NamedThreadFactory unchanged.
```

**T-5 / T-6: YWorklistTableModel anonymous Thread subclass**
```
File:     src/org/yawlfoundation/yawl/swingWorklist/YWorklistTableModel.java
Lines:    105, 125
Current:  EventQueue.invokeLater(new Thread() {
              public void run() { fireTableRowsInserted(position, position); }
          });
Proposed: EventQueue.invokeLater(() -> fireTableRowsInserted(position, position));
          (and the matching fireTableRowsDeleted call)
Risk:     LOW
Reason:   EventQueue.invokeLater() takes Runnable. Thread is used only as a Runnable
          here (run() called, not start()). No new thread is created either way.
          Pure semantics-preserving lambda conversion.
```

**T-7: ThreadNotify extends Thread**
```
File:     src/org/yawlfoundation/yawl/procletService/util/ThreadNotify.java
Line:     21
Current:  public class ThreadNotify extends Thread { public synchronized void run() { wait(); } }
Proposed: Extract Runnable; launch with Thread.ofVirtual().name("proclet-notify").start(runnable)
Risk:     HIGH
Reason:   synchronized run() + wait() PINS virtual threads. Must convert to
          ReentrantLock + Condition atomically with InternalRunner (see Phase 4).
```

**T-8: SingleInstanceClass.InternalRunner extends Thread**
```
File:     src/org/yawlfoundation/yawl/procletService/SingleInstanceClass.java
Line:     179
Current:  public class InternalRunner extends Thread { ... synchronized(mutex) { tn.notification(true); } }
Proposed: implements Runnable; start with Thread.ofVirtual().name("proclet-internal-runner").start(runnable)
          Replace synchronized(mutex) with ReentrantLock.lock/unlock.
Risk:     HIGH
Reason:   Nested monitor acquisition (mutex + tn monitor) pins under VT. Must be
          converted atomically with ThreadNotify, ThreadTest, and SingleInstanceClass.
```

**T-9: TimeService.InternalRunner extends Thread**
```
File:     src/org/yawlfoundation/yawl/procletService/util/TimeService.java
Lines:    85, 88–89
Current:  new InternalRunner(time, workItemRecord, this, sessionHandle).start()
          (InternalRunner: Thread.sleep(time) then t.finish())
Proposed: Thread.ofVirtual().name("proclet-timer-" + itemRecord.getID()).start(() -> {
              try { Thread.sleep(time); }
              catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
              if (!stopping) t.finish(itemRecord, sessionHandle);
          });
Risk:     MEDIUM
Reason:   Thread.sleep() is fine for VT. finish() is synchronized and calls engine
          HTTP (see P-4 — must convert finish() to ReentrantLock too).
```

---

## 2. Pinning Risks — Synchronized Blocks Wrapping Blocking I/O

Virtual threads are pinned (carrier thread blocked) when `synchronized` wraps blocking I/O.
The following are the actionable sites. Core engine `synchronized` guards over in-memory
Petri-net state (YTask, YNetRunner) are NOT listed here because they contain no blocking I/O
and their critical sections are microsecond-duration pure-Java operations.

### HIGH — Convert to ReentrantLock (Blocks VT Carriers)

**P-1: ThreadNotify.run() — synchronized + wait()**
```
File:  src/org/yawlfoundation/yawl/procletService/util/ThreadNotify.java
Line:  41–57
Fix:   private final ReentrantLock _lock = new ReentrantLock();
       private final Condition _resumed = _lock.newCondition();
       void run() { _lock.lock(); try { while (threadSuspended) { _resumed.await(); } }
                    finally { _lock.unlock(); } }
       void press() { _lock.lock(); try { threadSuspended = false; _resumed.signalAll(); }
                      finally { _lock.unlock(); } }
```

**P-2: ThreadTest.run() — synchronized(this) + wait()**
```
File:  src/org/yawlfoundation/yawl/procletService/util/ThreadTest.java
Line:  37–51
Fix:   Inherits from ThreadNotify — resolved by P-1 fix.
```

**P-3: SingleInstanceClass.InternalRunner — synchronized(mutex) wrapping notification()**
```
File:  src/org/yawlfoundation/yawl/procletService/SingleInstanceClass.java
Lines: 70, 83, 112, 141, 169, 201
Fix:   Replace Object mutex/mutex2/mutex3 with ReentrantLock instances.
       Replace all synchronized(mutexX) blocks with lock.lock()/unlock() in try/finally.
       Replace tn.notification()/wait() with Condition.signalAll()/await().
       Must be done atomically with P-1.
```

**P-4: TimeService.finish() — synchronized method + HTTP call**
```
File:  src/org/yawlfoundation/yawl/procletService/util/TimeService.java
Line:  126
Fix:   private final ReentrantLock _finishLock = new ReentrantLock();
       public void finish(...) { _finishLock.lock(); try { checkInWorkItem(...); }
                                 finally { _finishLock.unlock(); } }
```

### MEDIUM — Convert to ReentrantLock

**P-5: SMSSender.run() — synchronized(this) wrapping HTTP calls**
```
File:  src/org/yawlfoundation/yawl/smsModule/SMSSender.java
Lines: 192, 258
Fix:   private final ReentrantLock _smsLock = new ReentrantLock();
       Replace synchronized(this) { getReplies(...); checkInWorkItem(...); }
       with _smsLock.lock() / _smsLock.unlock() in try/finally.
Note:  SMSSender already starts its poller on VT (see §1.1). Without this fix,
       the VT will pin on every getReplies() HTTP call inside synchronized(this).
```

**P-6: JavaCPN.send() — synchronized method wrapping socket OutputStream.write()**
```
File:  src/org/yawlfoundation/yawl/procletService/connect/JavaCPN.java
Line:  145
Fix:   private final ReentrantLock _socketLock = new ReentrantLock();
       public void send(ByteArrayInputStream bytes) {
           _socketLock.lock(); try { output.write(packet); output.flush(); }
           finally { _socketLock.unlock(); }
       }
Note:  Used only by procletService connect package. Zero core engine impact.
```

### MEDIUM-HIGH — Phase 2 (After Phase 1 Confirms Pattern)

**P-7: YPersistenceManager.doPersistAction() — synchronized + JDBC**
```
File:  src/org/yawlfoundation/yawl/engine/YPersistenceManager.java
Line:  455
Fix:   private final ReentrantLock _persistLock = new ReentrantLock();
       void doPersistAction(Object obj, boolean update) {
           _persistLock.lock(); try { session.persist(obj); ... }
           finally { _persistLock.unlock(); }
       }
Note:  YEventLogger (logging/YEventLogger.java) already uses ReentrantLock and
       has a comment documenting this exact rationale. Follow that pattern.
       YNetRunner already uses _runnerLock (ReentrantLock) for parent-runner coord.
```

### LOW — Monitor Only, No Blocking I/O (Keep as synchronized)

The following files have many `synchronized` methods guarding in-memory Petri-net state.
They contain **no blocking I/O** in their critical sections; critical sections are
microsecond-duration pure-Java state mutations. They will appear in
`-Djdk.tracePinnedThreads` output but cause zero carrier-thread blocking.

**DO NOT convert these in this migration** — the risk/reward ratio is wrong.

- `elements/YTask.java` — all `t_fire`, `t_add`, `t_complete`, `cancel`, etc.
- `elements/YAtomicTask.java`, `YCompositeTask.java`, `YCondition.java`
- `elements/state/YIdentifier.java` — `addLocation`, `clearLocations`, etc.
- `stateless/elements/` mirrors of all the above
- `engine/YNetRunner.java` — `kick`, `continueIfPossible`, `startWorkItemInTask`, etc.
- `stateless/engine/YNetRunner.java` — same mirror
- `engine/YEngine.java` — `synchronized(_pmgr)` blocks (already have `_runnerLock` in YNetRunner)

**Note**: `util/JDOMUtil.java` static synchronized SAXBuilder is similarly low priority.
If high-concurrency stateless parsing becomes a bottleneck, replace with per-call
`new SAXBuilder().build(reader)` (SAXBuilder construction is cheap and thread-safe by construction).

---

## 3. ThreadLocal Audit

**ThreadLocalRandom** — `elements/YEnabledTransitionSet.java:234` and stateless mirror.
**Assessment: SAFE.** ThreadLocalRandom is explicitly designed for virtual threads.

**SLF4J MDC** — Referenced in `integration/autonomous/observability/StructuredLogger.java`.
**Assessment: MONITOR.** MDC context is not propagated across executor submission boundaries.
If `MDC.put()` context needs to flow into child tasks submitted to an executor, capture
`MDC.getCopyOfContextMap()` before submission and restore at task start. Current code does
not cross this boundary, so no immediate action is required.

**No other ThreadLocal field declarations found** in any of the 736 production Java files.

---

## 4. Migration Phases — Sequenced

### Phase 0 — Baseline (1 day)
**Goal**: Record behavior before any changes.

```bash
mvn -T 1.5C clean test   # Capture pass count, timing
```

Enable VT pin tracing for the test JVM (add to Surefire `<argLine>`):
```
-Djdk.tracePinnedThreads=full
```

Capture stderr output. This is the pinning baseline to compare against after each phase.
Run `VirtualThreadPinningTest` (at `test/org/yawlfoundation/yawl/engine/VirtualThreadPinningTest.java`)
to record baseline pin event count.

### Phase 1 — ThreadFactory Lambdas (half-day, independent)
**Sites**: T-1, T-2, T-3
**Order**: T-2 first (lowest risk), then T-1, then T-3.

Each is a 4-line → 1-line change in a factory lambda. No behavioral change to any caller.
The `ScheduledExecutorService` / `ExecutorService` contract is unchanged.

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*WebhookDelivery*,*OAuth2Token*,*LanguageServer*"
mvn -T 1.5C clean test   # full regression
```

### Phase 2 — Swing Worklist Cleanup (1 hour, independent)
**Sites**: T-5, T-6

`EventQueue.invokeLater()` receives a `Runnable`. `Thread` is passed only because it implements
`Runnable` — only `run()` is called, not `start()`. Lambda replacement is semantics-identical.

**Verification**:
```bash
mvn -T 1.5C clean test -pl yawl-control-panel
```

### Phase 3 — NamedThreadFactory (half-day, independent)
**Site**: T-4

Verify zero internal call sites before changing. If external callers exist, add
`VirtualNamedThreadFactory` as a companion and migrate internal uses to it; leave
`NamedThreadFactory` unchanged for backward compatibility.

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*NamedThreadFactory*"
```

### Phase 4 — procletService Rendezvous Refactor (2 days, atomic commit)
**Sites**: T-7, T-8, P-1, P-2, P-3, P-31

**All 5 files must be committed together** — they form a single protocol:

1. `ThreadNotify.java` — `synchronized run()` + `wait()`/`notifyAll()` → ReentrantLock + Condition
2. `ThreadTest.java` — inherits from ThreadNotify; retest after base class change
3. `SingleInstanceClass.java` — `InternalRunner` from `extends Thread` to `implements Runnable`;
   all `synchronized(mutex/mutex2/mutex3)` → ReentrantLock; launch site to `Thread.ofVirtual()...`
4. `BlockPI.java` — verify all `ThreadNotify` creation and `registerAndWait` call sites compile
5. Callers of `SingleInstanceClass.registerAndWait()` — verify no behavior change

**Lock naming conventions**:
```java
// In ThreadNotify
private final ReentrantLock _suspendLock = new ReentrantLock();
private final Condition _resumeCondition = _suspendLock.newCondition();

// In SingleInstanceClass
private final ReentrantLock _mutex = new ReentrantLock();
private final ReentrantLock _mutex2 = new ReentrantLock();
private final ReentrantLock _mutex3 = new ReentrantLock();
```

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*ThreadNotify*,*SingleInstanceClass*,*BlockPI*"
# procletService integration (manual): org.yawlfoundation.yawl.miscellaneousPrograms.TestSMS
```

### Phase 5 — TimeService InternalRunner (half-day)
**Sites**: T-9, P-4

Depends on nothing. `TimeService.InternalRunner` is a separate class from
`SingleInstanceClass.InternalRunner` — can be done independently of Phase 4.

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*TimeService*"
```

### Phase 6 — SMSSender synchronized I/O (half-day)
**Site**: P-5

Depends on Phase 5 (same procletService context) but not Phase 4 (different class).

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*SMS*"
```

### Phase 7 — JavaCPN Socket Lock (half-day)
**Site**: P-6

Independent. CPN bridge only used by procletService connect package.

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*JavaCPN*"
```

### Phase 8 — YPersistenceManager JDBC Lock (half-day, after Phase 0 baseline)
**Site**: P-7

This is the only change touching core engine persistence. Risk is isolated: only changes
the lock type (synchronized → ReentrantLock) with identical mutual exclusion semantics.
Follow the exact pattern in `YEventLogger` (already has the comment explaining why).

**Verification**:
```bash
mvn -T 1.5C clean test -Dtest="*YPersistenceManager*,*TestEngineSystem*,*TestConcurrentCaseExecution*"
mvn -T 1.5C clean test   # full regression required before merge
```

---

## 5. Verification Strategy

### Per-Phase Test Commands

| Phase | Test scope | Full regression required? |
|-------|-----------|--------------------------|
| 1 | Unit tests for changed service classes | After all 3 sites done |
| 2 | yawl-control-panel module | No (Swing UI only) |
| 3 | NamedThreadFactory test | After confirming no callers |
| 4 | ThreadNotify + SingleInstanceClass + BlockPI | Yes (procletService protocol) |
| 5 | TimeService | No |
| 6 | SMSSender | No |
| 7 | JavaCPN | No |
| 8 | YPersistenceManager + engine suite | Yes (core persistence) |

### Pinning Detection

Add to Surefire `<argLine>` for the full verification run:
```xml
<argLine>-Djdk.tracePinnedThreads=full -XX:+UseCompactObjectHeaders</argLine>
```

Check stderr for `PINNED` events. After Phases 4–7, procletService-related pinning must
reach zero. After Phase 8, JDBC pinning in YPersistenceManager must reach zero.

Existing test: `test/org/yawlfoundation/yawl/engine/VirtualThreadPinningTest.java`
(captures stderr, counts PINNED events, asserts zero).

### Concurrency Stress

After Phase 4 and Phase 8:
```bash
mvn -T 1.5C clean test -Dtest="TestConcurrentCaseExecution,VirtualThreadPinningTest,ChaosTestSuite"
```

### Behavioral Assertion to Add (Phase 4)

Add to procletService test suite:
1. Create N ThreadNotify instances concurrently (N = 100).
2. Call `registerAndWait()` from N virtual threads simultaneously.
3. Call `notifyPerformativeListeners()` from a separate virtual thread.
4. Assert all N virtual threads complete within 5 seconds.
5. Assert no `InterruptedException` is swallowed (check for thread interrupt status).

---

## 6. Rollback Plan

Every phase is a single focused commit. Rollback = `git revert <sha>`.

| Phase | Rollback complexity | Notes |
|-------|--------------------|----|
| 1 | Trivial | 3 independent commits, revert any individually |
| 2 | Trivial | Swing UI only |
| 3 | Trivial | 2-line change |
| 4 | Medium | Single atomic commit; revert restores all 5 files together |
| 5 | Low | Self-contained TimeService |
| 6 | Low | Self-contained SMSSender |
| 7 | Low | CPN bridge not used in server-side deployments |
| 8 | Medium | Core persistence; revert tested by full regression |

For Phase 4: procletService connects only through InterfaceB client. The core engine
is unaffected if procletService is offline. The service can be stopped independently
of the engine during rollback.

---

## 7. Summary Matrix

| Site | File | Risk | Phase | Type |
|------|------|------|-------|------|
| T-1 | `integration/webhook/WebhookDeliveryService.java:118` | LOW | 1 | ThreadFactory lambda |
| T-2 | `integration/oauth2/OAuth2TokenValidator.java:144` | LOW | 1 | ThreadFactory lambda |
| T-3 | `tooling/lsp/YawlLanguageServer.java:80` | LOW | 1 | ThreadFactory lambda |
| T-4 | `util/NamedThreadFactory.java:23` | MEDIUM | 3 | Public ThreadFactory |
| T-5 | `swingWorklist/YWorklistTableModel.java:105` | LOW | 2 | Anonymous Thread subclass |
| T-6 | `swingWorklist/YWorklistTableModel.java:125` | LOW | 2 | Anonymous Thread subclass |
| T-7 | `procletService/util/ThreadNotify.java:21` | HIGH | 4 | extends Thread + synchronized run |
| T-8 | `procletService/SingleInstanceClass.java:179` | HIGH | 4 | extends Thread + synchronized mutex |
| T-9 | `procletService/util/TimeService.java:85` | MEDIUM | 5 | extends Thread + sleep |
| P-1 | `procletService/util/ThreadNotify.java:41` | HIGH | 4 | synchronized + wait() |
| P-2 | `procletService/util/ThreadTest.java:37` | HIGH | 4 | synchronized + wait() (inherits) |
| P-3 | `procletService/SingleInstanceClass.java:70` | HIGH | 4 | synchronized(mutex) + notify |
| P-4 | `procletService/util/TimeService.java:126` | MEDIUM | 5 | synchronized + HTTP |
| P-5 | `smsModule/SMSSender.java:192` | MEDIUM | 6 | synchronized + HTTP |
| P-6 | `procletService/connect/JavaCPN.java:145` | MEDIUM | 7 | synchronized + socket I/O |
| P-7 | `engine/YPersistenceManager.java:455` | MEDIUM-HIGH | 8 | synchronized + JDBC |

**Total sites**: 16 (9 thread creation, 7 pinning)
**Already migrated**: 28+ sites (see §1.1)
**Phases**: 9 (0 = baseline, 1–8 = implementation)
**Estimated effort**: 4–5 days total implementation + 1 day baseline + 1 day regression

---

## 8. Key Reference Files

- `YEventLogger.java` — canonical example of synchronized → ReentrantLock migration with
  explicit comment: *"Uses ReentrantLock instead of synchronized to avoid virtual thread pinning."*
- `engine/YNetRunner.java` — uses `_runnerLock = new ReentrantLock()` for parent-runner
  coordination; demonstrates the pattern at the engine layer.
- `stateless/engine/YNetRunner.java` — same mirror with identical ReentrantLock pattern.
- `test/org/yawlfoundation/yawl/engine/VirtualThreadPinningTest.java` — existing test
  that captures stderr and counts PINNED events; used as the verification harness.
