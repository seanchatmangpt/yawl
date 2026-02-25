# Architecture Review: Pending Changes to Master

**Date**: 2026-02-25
**Branch**: `claude/review-pending-architecture-nEVtu`
**Reviewer**: Claude (automated architecture review)
**Verdict**: **BLOCK** — 13 blocking source issues, 4 blocking test issues, 5 blocking build/config issues, 4 blocking deletion impacts

---

## Overview

**465 files changed** | **+23,932 lines** | **-89,845 lines** | **13 non-merge commits**

This branch represents a major modernization effort across three axes:
1. **Java 25 concurrency** — Virtual threads, `ScopedValue`, `StructuredTaskScope`
2. **AI agent integration** — A2A routing, worklet RDR service, capability matching
3. **Codebase hygiene** — 334 files deleted (reports, obsolete scripts, monitoring config)

---

## Architecture Highlights (What's Good)

- **Sealed types**: `WorkletSelection` and `RoutingDecision` use sealed interfaces with record
  implementations, enabling exhaustive `switch` expressions. Textbook modern Java.
- **WorkletService design**: RDR-based rule evaluation with three-way sealed routing
  (`SubCaseSelection` / `A2AAgentSelection` / `NoSelection`), A2A dispatch on virtual threads,
  and proper XXE prevention in `RdrXmlParser.parseXml()`.
- **CapabilityMatcher**: Immutable after construction, clear separation between query building
  and marketplace lookup, sealed result type for agent vs human routing.
- **No-silent-fallback enforcement**: `AgentRoutingException` explicitly propagates dispatch
  failures rather than hiding them.
- **Thread-safe collection migration**: `YNetRunner` correctly migrated `_enabledTasks`,
  `_busyTasks`, `_deadlockedTasks` from `LinkedHashSet` to `ConcurrentHashMap.newKeySet()`.
- **Test exemplars**: `WorkletServiceTest`, `CapabilityMatcherTest`, `AiValidationLoopTest`,
  `OllamaValidationClientTest`, `YawlSpecExporterTest` demonstrate excellent Chicago TDD
  with zero mocks and real objects.

---

## BLOCKING Source Issues

### B1. CRITICAL — Shared YPersistenceManager in parallel virtual threads

**File**: `src/.../engine/YNetRunner.java` — `fireAtomicTasksInParallel()`

`YPersistenceManager` wraps a Hibernate session (not thread-safe). The method passes the same
`pmgr` to concurrent `fireAtomicTask()` calls on virtual threads. Concurrent `storeObject()`/
`updateObject()` calls will corrupt Hibernate session state. Additionally, the executor lifecycle
is broken: `executor.shutdown()` runs in the `finally` block before `allFutures.get()` collects
results outside the try block.

### B2. HIGH — `_announcements` LinkedHashSet accessed from virtual threads

**File**: `src/.../engine/YNetRunner.java:148,267`

While `_enabledTasks` and `_busyTasks` were correctly migrated to concurrent sets,
`_announcements` remains a `LinkedHashSet`. The `fireAtomicTasksInParallel()` method adds to
it from multiple virtual threads — data race.

### B3. HIGH — JSON injection in ResourceManager.executeDispatch()

**File**: `src/.../resourcing/ResourceManager.java:145-147`

No JSON escaping on `workItemId` or `taskId`. A crafted value containing `"` can inject
arbitrary JSON keys. Compare to `WorkletService.buildA2ARequestBody()` which calls `escapeJson()`.

### B4. HIGH — Silent exception swallowing in WorkletService.dispatchA2A()

**File**: `src/.../worklet/WorkletService.java:221-229`

Catch block swallows `IOException`/`InterruptedException` with zero logging (no logger field).
HTTP response status code never checked. Violates H_SILENT and H_FALLBACK guard patterns.

### B5. MEDIUM — Non-atomic volatile counters in VaultCredentialCache

**File**: `src/.../integration/VaultCredentialCache.java:89-94`

`_totalHits++` and `_totalHitNanos += elapsed` on `volatile long` — not atomic.

### B6. MEDIUM — Non-volatile fields in shared A2ACaseMonitor.CaseState

**File**: `src/.../engine/A2ACaseMonitor.java:334-345`

`lastUpdateTime` and `status` written from parallel virtual threads but neither volatile
nor synchronized.

### B7. MEDIUM — VirtualThreadPool.submitAndWaitAll() returns broken FutureTask wrappers

**File**: `src/.../engine/VirtualThreadPool.java:422-425`

`FutureTask` objects created but never `run()`. Calling `.get()` blocks forever.
Fix: return `new ArrayList<>(futures)` since `CompletableFuture<T>` implements `Future<T>`.

### B8. MEDIUM — `synchronized` on `kick()` pins virtual thread carriers

**File**: `src/.../stateless/engine/YNetRunner.java:403`

`kick()` is `synchronized`, which pins the carrier thread. The class already has
`_runnerLock` (ReentrantLock) that should be used instead.

---

## BLOCKING Build/Config Issues

### C1. CRITICAL — Invalid JVM flag in default argLine

**File**: `pom.xml:91`

`-XX:+UseThreadPriorityQueue` is not a valid HotSpot JVM flag. This will cause the JVM to
fail to start for any test execution using the default `argLine`, breaking the entire test suite.

### C2. CRITICAL — Benchmark profile has mutually exclusive GC flags

**File**: `pom.xml` — benchmark profile

`-XX:+UseG1GC` and `-XX:+UseZGC` cannot be enabled simultaneously. The JVM will reject this
with a fatal error. Additional invalid flags: `-XX:+PrintGCApplicationStoppedTime` (unrecognized),
`-XX:+ShowCodeCacheOnCompilation` (unrecognized). The profile also sets
`maven.compiler.release=8` which is incompatible with Java 25 features.

### C3. HIGH — `--enable-preview` removed from Surefire but source uses preview APIs

**File**: `pom.xml`

`--enable-preview` removed from default Surefire `<argLine>`, but the codebase uses
`ScopedValue` and `StructuredTaskScope` (preview APIs in Java 21). Tests will crash at
runtime with "preview features not enabled."

### C4. HIGH — Observatory hooks reference deleted binary

**File**: `.claude/settings.json`

Three active hooks (PreToolUse, PostToolUse, Stop) reference
`scripts/observatory/target/release/yawl-hooks` which no longer exists. Every Write/Edit/Stop
hook invocation will fail silently.

### C5. HIGH — yawl-resourcing depends on yawl-integration (reactor order issue)

**File**: `yawl-resourcing/pom.xml`

In the root pom, `yawl-resourcing` (position 7) now depends on `yawl-integration` (position 11).
Clean builds without local cache will fail.

---

## BLOCKING Test Issues

### T1. H_MOCK violations — 9 test files use Mockito.mock()

Affected: `ObservabilityIntegrationTest`, `SLOTrackerTest`, `SLOAlertManagerTest`,
`SLODashboardTest`, `SLOIntegrationServiceTest`, `SLOPredictiveAnalyticsTest`,
`YWorkItemEventTest`, `WorkflowEventStoreOptimizationTest`, `WorkflowEventStorePerformanceTest`

Correct pattern already exists: `LockContentionTrackerTest.TestableAndonCord` and
`WorkletServiceTest.TestableWorkletService`.

### T2. Compilation errors in test files

- `ConcurrencyBenchmarkSuite.java:263` — Missing closing brace for `for` loop
- `WorkflowEventStoreOptimizationTest.java` — Missing imports (`ExtendWith`, `anyInt`, `anyLong`)
- `ModernYawlEngineAdapterPerformanceTest.java:354` — `@AfterAll` static method references instance field
- `ThroughputBenchmark.java` — Missing `@DisplayName` import

### T3. Broken test helper — createTestEvents() always returns empty list

`WorkflowEventStoreOptimizationTest.java:308-312` — `List.of().stream().map(...)` always
produces an empty list regardless of `count` parameter.

### T4. Flaky Thread.sleep synchronization

Multiple observability tests use `Thread.sleep(2000-10000)` instead of `CountDownLatch` or
awaitility. Will produce intermittent failures under CI load.

---

## BLOCKING Deletion Impacts

### D1. CRITICAL — `scripts/dx.sh` deleted (29 broken references)

The primary build tool. Python CLI (`build.py`, `godspeed.py`), Java A2A skills
(`RunTestsSkill.java`, `ExecuteBuildSkill.java`), and ggen migration config all invoke it.
Without it: `yawl build`, `yawl godspeed`, and agent self-test are non-functional.

### D2. CRITICAL — `scripts/observatory/` deleted (10 broken references)

The entire Rust observatory codebase deleted with no replacement. CLI `yawl observatory generate`,
GODSPEED PSI phase, and MCP coordination tools all broken.

### D3. CRITICAL — `README.md`, `CLAUDE.md`, `LICENSES.md` deleted

- `README.md` (385 lines) — project landing page gone, no onboarding path
- `CLAUDE.md` (78 lines) — root project instructions for Claude Code sessions gone
- `LICENSES.md` (525 lines) + `license.txt` (165 lines) — **no license = legally undistributable**

### D4. HIGH — `scripts/ggen-*.sh` deleted (11 broken CLI references)

`yawl ggen init`, `yawl ggen export`, `yawl ggen generate`, `yawl ggen validate`,
`yawl gregverse import/export/convert` — all broken.

---

## Non-Blocking Concerns

1. `VirtualThreadPool` doesn't actually control carrier threads (observability only)
2. `A2ACaseMonitor.checkSingleCase()` auto-completes cases after 2 minutes (placeholder)
3. `A2ACaseMonitor.getCaseStatus()` always returns RUNNING (stub)
4. Redundant ScopedValue bindings in stateless YNetRunner
5. Log4j vs SLF4J inconsistency across new files
6. Micrometer downgrade 1.16.3 -> 1.15.9 (verify Spring Boot BOM compatibility)
7. BouncyCastle jump 1.77 -> 1.81 (verify API compatibility)
8. 13 `deps.txt` files are stale offline snapshots (informational noise)
9. `RdrSetRepository.loadFromDisk()` silently swallows parse exceptions
10. `OllamaValidationClient.buildRequestBody()` uses manual JSON escaping instead of Gson
11. `ValidationResult` compact constructor doesn't enforce `valid=true -> issues.isEmpty()`

---

## Issue Summary Table

| ID | Category | Severity | File | Issue |
|----|----------|----------|------|-------|
| B1 | Thread safety | CRITICAL | `YNetRunner.java` | Shared Hibernate session in parallel vthreads |
| B2 | Thread safety | HIGH | `YNetRunner.java` | `_announcements` data race |
| B3 | Security | HIGH | `ResourceManager.java` | JSON injection |
| B4 | Guard violation | HIGH | `WorkletService.java` | Silent exception swallowing |
| B5 | Thread safety | MEDIUM | `VaultCredentialCache.java` | Non-atomic volatile counters |
| B6 | Thread safety | MEDIUM | `A2ACaseMonitor.java` | Non-volatile shared fields |
| B7 | Correctness | MEDIUM | `VirtualThreadPool.java` | Broken FutureTask wrappers |
| B8 | Performance | MEDIUM | `YNetRunner.java` | Carrier thread pinning |
| C1 | Build | CRITICAL | `pom.xml` | Invalid JVM flag crashes tests |
| C2 | Build | CRITICAL | `pom.xml` | Mutually exclusive GC flags |
| C3 | Build | HIGH | `pom.xml` | Missing `--enable-preview` |
| C4 | Build | HIGH | `.claude/settings.json` | Dead hook references |
| C5 | Build | HIGH | `yawl-resourcing/pom.xml` | Reactor order violation |
| T1 | Standards | BLOCK | 9 test files | H_MOCK violations |
| T2 | Compilation | BLOCK | 4 test files | Won't compile |
| T3 | Correctness | BLOCK | `WorkflowEventStoreOptimizationTest` | Broken test helper |
| T4 | Reliability | BLOCK | Multiple | Flaky Thread.sleep |
| D1 | Deletion | CRITICAL | `scripts/dx.sh` | 29 broken references |
| D2 | Deletion | CRITICAL | `scripts/observatory/` | 10 broken references |
| D3 | Deletion | CRITICAL | `README.md`, `CLAUDE.md`, `LICENSES.md` | Project identity/legal |
| D4 | Deletion | HIGH | `scripts/ggen-*.sh` | 11 broken CLI references |

---

## Recommendations

### Before merge (mandatory)
1. Fix B1-B8 (thread safety, security, guard violations in source code)
2. Fix C1-C5 (JVM flags, preview mode, reactor order, dead hooks)
3. Fix T1-T4 (test compilation, mock violations, broken helpers)
4. Restore or replace `scripts/dx.sh` (29 live references) and update all callers
5. Restore `README.md` and `LICENSES.md` at minimum (legal requirement)
6. Remove or fix dead observatory hook references in `.claude/settings.json`

### After merge (recommended)
- Replace `A2ACaseMonitor` simulation/stub logic with real engine integration
- Standardize on one logging facade (SLF4J recommended)
- Add `@Tag("performance")` to benchmark tests for CI filtering
- Provide observatory replacement or clean all references
- Restore `CLAUDE.md` or consolidate into `.claude/rules/`
