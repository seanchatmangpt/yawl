# Architecture Review — Feedback for Coding Agents

**Branch**: `claude/review-pending-architecture-lu4XR`
**Reviewed**: 2026-02-25
**Scope**: 16 commits above master, 465 files changed (+23,932 / -89,845 lines)

Each issue below is self-contained and can be picked up independently. Fix in severity order.

---

## BLOCKING — Must fix before merge

---

### ISSUE-1: Silent exception loss in `ResourceManager` virtual thread

**File**: `src/org/yawlfoundation/yawl/resourcing/ResourceManager.java:99–101`

**Problem**:
```java
Thread.ofVirtual()
    .name("resourcing-dispatch-" + workItemId)
    .start(() -> executeDispatch(agentRoute.listing(), workItem));
```
`executeDispatch()` declares and throws `AgentRoutingException` (on HTTP 4xx/5xx or network failure). The virtual thread lambda has no catch block, so the exception is delivered to the JVM's default uncaught-exception handler and silently discarded. The caller has no indication the dispatch failed.

**Required fix**: Wrap the lambda body in a try-catch. On `AgentRoutingException`:
1. Increment a failure counter (e.g., `agentDispatchFailureCount.incrementAndGet()`).
2. Record the exception via the observability span if one is active.
3. Optionally re-route to human via `humanRouteCount`.
Do **not** silently discard.

**Acceptance criteria**:
- A unit test injects a mock agent endpoint that returns HTTP 500.
- After `routeWorkItem()` returns, the failure counter is non-zero.
- No `AgentRoutingException` escapes to the JVM uncaught-exception handler.

---

### ISSUE-2: H_LIE — Comments claim `StructuredTaskScope`; code uses `CompletableFuture`

**File**: `src/org/yawlfoundation/yawl/engine/YNetRunner.java:874, 1048, 1670`

**Problem**: Three inline comments read:
```java
// Use StructuredTaskScope for parallel execution of atomic tasks
```
The actual implementation uses `CompletableFuture.supplyAsync()` +
`Executors.newVirtualThreadPerTaskExecutor()`. No `StructuredTaskScope` is imported
or used. Comments that contradict the code are H_LIE violations that mislead future
maintainers.

**Required fix** (choose one):
- **Option A**: Replace all three comments with accurate text, e.g.
  `// Use CompletableFuture + virtual-thread executor for parallel execution`.
- **Option B**: Replace the `CompletableFuture` implementation with a real
  `StructuredTaskScope.ShutdownOnFailure` block (Java 21 preview; Java 24 finalized).

**Acceptance criteria**:
```bash
grep -n "StructuredTaskScope" src/org/yawlfoundation/yawl/engine/YNetRunner.java
# Must return zero results (Option A) OR match an actual import + usage (Option B)
```

---

### ISSUE-3: Executor shutdown race condition in `fireAtomicTasksInParallel`

**File**: `src/org/yawlfoundation/yawl/engine/YNetRunner.java:~962–1000`

**Problem**: The method creates futures inside a `try` block, then the `finally`
block calls `executor.shutdown()` + `awaitTermination(5s)`. After the
try-finally exits, `allFutures.get()` is called. If `awaitTermination` times out
and `shutdownNow()` interrupts threads, futures may be cancelled before
`allFutures.get()` is reached, causing a hang or `CancellationException`.

Additionally, the `// ... rest of the method` comment at line 973 appears to be an
unfinished stub left in production code.

**Required fix**:
1. Move `allFutures.get()` (and announcement collection) **inside** the `try` block,
   before `finally` runs.
2. Remove the `// ... rest of the method` placeholder comment; ensure no logic is
   missing.

**Acceptance criteria**:
- `fireAtomicTasksInParallel` contains no `// ... rest of` placeholder comments.
- `allFutures.get()` is called before `executor.shutdown()` in all code paths.
- An existing or new test that fires 3+ atomic tasks in parallel completes without
  `CancellationException`.

---

## HIGH — Fix before merge (strongly recommended)

---

### ISSUE-4: H_FALLBACK + H_LIE — `WorkletService.dispatchA2A()` promises logging but doesn't log

**File**: `src/org/yawlfoundation/yawl/worklet/WorkletService.java:221–227`

**Problem**:
```java
} catch (IOException | InterruptedException e) {
    if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
    }
    // A2A dispatch failure is non-fatal: log and allow normal YAWL execution to continue
    activeRecords.remove(record.getCompositeKey());
}
```
The comment says "log" but **there is no log statement**. The exception is silently
swallowed (H_FALLBACK) and the comment lies about what the code does (H_LIE).

**Required fix**: Add an explicit log call before removing the record:
```java
log.warn("A2A dispatch failed for case '{}' task '{}': {}",
    record.getHostCaseId(), record.getHostTaskId(), e.getMessage());
```

**Acceptance criteria**:
- A test that mocks `httpClient` to throw `IOException` verifies a WARN-level log
  entry is emitted.
- No catch block comment uses the word "log" unless a `log.*()` call is present.

---

### ISSUE-5: Insertion-order loss — `LinkedHashSet` → `ConcurrentHashMap.newKeySet()`

**File**: `src/org/yawlfoundation/yawl/engine/YNetRunner.java:129–139`

**Problem**: `_enabledTasks`, `_busyTasks`, `_deadlockedTasks` were changed from
`LinkedHashSet` (insertion-ordered) to `ConcurrentHashMap.newKeySet()` (unordered).
If any existing code iterates these sets and relies on FIFO task order for scheduling,
deadlock detection, or diagnostic output, the behaviour is now non-deterministic.

**Required fix**:
1. Search all iteration sites:
   ```bash
   grep -n "_enabledTasks\|_busyTasks\|_deadlockedTasks" \
     src/org/yawlfoundation/yawl/engine/YNetRunner.java
   ```
2. For each `for`, `stream()`, `iterator()`, or `dump()` call, confirm order is not
   relied upon.
3. If order matters anywhere, replace with a concurrent ordered alternative such as
   `Collections.synchronizedSet(new LinkedHashSet<>())` or
   `ConcurrentSkipListSet` (if `YTask` is `Comparable`).

**Acceptance criteria**:
- A comment in the file documents the ordering guarantee (or lack thereof) for each
  concurrent set.
- Existing engine tests that check enabled-task state pass without modification.

---

### ISSUE-6: Java compiler release (`21`) conflicts with Java 25 runtime APIs

**File**: `pom.xml:87`

**Problem**:
```xml
<maven.compiler.release>21</maven.compiler.release>
```
The production codebase uses `ScopedValue` (finalized Java 21) and the in-code
comments reference `StructuredTaskScope` (preview in Java 21, finalized Java 24).
CI runs on Java 25. Compiling with `release=21` while running on Java 25 with
`--enable-preview` only in the test profile creates an inconsistent build contract.

**Required fix**: Set `maven.compiler.release` to `25` to match the CI JVM. If Java
21 is a hard minimum-support requirement, document it explicitly and add a
`<jvmArgs>--enable-preview</jvmArgs>` to the main surefire config as well.

**Acceptance criteria**:
```bash
mvn clean compile -q && echo "BUILD OK"
# Must succeed on Java 25 without --enable-preview in JAVA_OPTS
```

---

## MEDIUM — Address in next sprint

---

### ISSUE-7: H_LIE — `CaseContext.java` Javadoc describes `WorkletService`, not `CaseContext`

**File**: `src/org/yawlfoundation/yawl/engine/CaseContext.java:26–51`

**Problem**: The class-level Javadoc describes "RDR-driven worklet orchestration",
listener patterns, and routing decisions. `CaseContext` is a simple record carrying
`ScopedValue` bindings for case ID, correlation ID, and start time. The documentation
was copied from `WorkletService` and never updated.

**Required fix**: Replace the Javadoc with a single paragraph describing the actual
class: an immutable `ScopedValue` carrier automatically inherited by forked virtual
threads.

**Acceptance criteria**: The Javadoc refers only to `ScopedValue`, case context
propagation, and virtual-thread inheritance — not to RDR trees, worklet routing, or
A2A dispatch.

---

### ISSUE-8: H_EMPTY — Duplicate logger field in `stateless/engine/YNetRunner.java`

**File**: `src/org/yawlfoundation/yawl/stateless/engine/YNetRunner.java:75, 78`

**Problem**: Two logger fields are declared for the same class:
```java
private static final Logger logger  = LogManager.getLogger(YNetRunner.class); // line 75
private static final Logger _logger = LogManager.getLogger(YNetRunner.class); // line 78
```
One is unused and creates a redundant `LogManager` lookup. The naming is also
inconsistent with the project convention (`_` prefix for fields).

**Required fix**: Remove the field at line 75 (`logger`). Keep `_logger` for
consistency with the rest of the codebase.

**Acceptance criteria**: Only one logger field exists in the class; `mvn compile`
produces no unused-variable warnings.

---

### ISSUE-9: Micrometer version regression — unexplained downgrade

**File**: `pom.xml`

**Problem**: Micrometer was downgraded from `1.16.3` → `1.15.9` with no explanation
in commit messages or `pom.xml` comments. This may be an unintentional regression
that loses bug fixes or metrics API surface.

**Required fix** (choose one):
- **Option A**: Restore `1.16.3` if the downgrade was unintentional.
- **Option B**: Add a `<!-- pinned: reason -->` comment explaining why `1.15.9` is
  required (e.g., compatibility with another dependency).

**Acceptance criteria**: The Micrometer version is either `1.16.3` or accompanied by
a comment explaining the pin.

---

### ISSUE-10: Two A2A dispatch paths with no documented ordering or exclusion contract

**Files**:
- `src/org/yawlfoundation/yawl/worklet/WorkletService.java`
- `src/org/yawlfoundation/yawl/resourcing/ResourceManager.java`

**Problem**: Both classes can dispatch the same work item to A2A agents.
`WorkletService` uses RDR-rule-driven selection (fire-and-forget).
`ResourceManager` uses capability-based marketplace routing (throws on failure).
There is no documented contract specifying:
- Which fires first?
- Can both fire for the same work item (double-dispatch)?
- What happens when one succeeds and one fails?

**Required fix**: Add a Javadoc block on each class's dispatch entry point documenting
the listener ordering, exclusivity contract, and failure semantics. If double-dispatch
is possible, add a guard (e.g., a flag on the work item, or a registry of
already-dispatched items).

**Acceptance criteria**: Code review can determine, from Javadoc alone, whether a
work item can be dispatched by both classes simultaneously.

---

### ISSUE-11: Missing test — `AgentRoutingException` path in `ResourceManager`

**Depends on**: ISSUE-1 (fix must exist before this test is meaningful)

**File**: No test file covers the HTTP-failure path in `ResourceManager`.

**Required fix**: Add `ResourceManagerExceptionTest.java` (or extend an existing test
class) with a test that:
1. Injects a mock `CapabilityMatcher` returning `AgentRoute` with a bad endpoint.
2. Calls `routeWorkItem()`.
3. Asserts the failure counter is incremented and no exception escapes the caller.

**Acceptance criteria**: Test class exists, is green, and is included in the default
Surefire run.

---

## Quick Verification Checklist

Run after all fixes are applied:

```bash
# 1. No silent exceptions in ResourceManager virtual thread
grep -A8 "Thread.ofVirtual" \
  src/org/yawlfoundation/yawl/resourcing/ResourceManager.java
# Expected: lambda contains try-catch

# 2. No H_LIE StructuredTaskScope comments
grep -n "StructuredTaskScope" src/org/yawlfoundation/yawl/engine/YNetRunner.java
# Expected: zero lines, or matches an actual import

# 3. WorkletService logs on A2A failure
grep -A8 "catch (IOException" \
  src/org/yawlfoundation/yawl/worklet/WorkletService.java
# Expected: log.warn() call present

# 4. Clean build on Java 25
mvn clean compile -q && echo "BUILD OK"

# 5. Full DX suite green
bash scripts/dx.sh
```
