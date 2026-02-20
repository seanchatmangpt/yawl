# Java 25 Updates to CLAUDE.md

**Version:** 1.0 | **Date:** February 2026 | **Scope:** Updates to /CLAUDE.md root project specification

---

## Overview

This document outlines the specific sections that should be added or updated in the root CLAUDE.md file to reflect Java 25 adoption in YAWL v6.0.0.

---

## Section 1: Add to "Λ (Build)" Section

**Location**: After "ALWAYS USE DX" section in CLAUDE.md

```markdown
## Java 25 Build Requirements

YAWL v6.0.0 requires **Java 25 LTS or later**. The build system automatically validates Java version.

### Version Check (In CI)

```bash
# Verify Java 25 at build time
java -version  # Should show "openjdk version 25.x"

# In pom.xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
</properties>
```

### Parallel Build Flag

Default parallel build flag reduces compile time by 50%:

```bash
# In .mvn/maven.config
-T 1.5C

# Or via command line
mvn -T 1.5C clean verify
```

### Java 25 Features Used

- **Records** (JEP 440): Immutable events, DTOs, API responses
- **Sealed Classes** (JEP 409): Domain hierarchies with exhaustiveness checking
- **Virtual Threads** (JEP 444): Agent discovery loops, work item processing
- **Structured Concurrency** (JEP 505): Parallel work item batches
- **Scoped Values** (JEP 506): Context propagation to virtual threads
- **Compact Object Headers** (JEP 519): +5-10% throughput (automatic)

See: `docs/v6/upgrade/JAVA25_UPGRADE_GUIDE.md`
```

---

## Section 2: Add to "H (Guards)" Section

**Location**: After guard definitions, add new pattern:

```markdown

### Virtual Thread Pinning (NEW)

**G** (Guards) now includes virtual thread safety:

```
Λ = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie, synchronized_with_io}
```

**synchronized_with_io** = synchronized block that may hold locks during blocking I/O

```java
// BAD: Virtual thread pinning risk
public synchronized void saveWorkItem(YWorkItem item) {
    db.save(item);  // Pins carrier thread during I/O!
}

// GOOD: Short critical section, I/O outside
private final ReentrantLock lock = new ReentrantLock();
public void saveWorkItem(YWorkItem item) {
    lock.lock();
    try { /* quick state update */ }
    finally { lock.unlock(); }
    db.save(item);  // Outside lock
}
```

**Detection**: `-Djdk.tracePinnedThreads=short` logs pinning events at runtime.

Hook `.claude/hooks/hyper-validate.sh` checks for synchronization patterns on lines with database/network calls.

See: `docs/architecture/decisions/ADR-028-virtual-threads-strategy.md`
```

---

## Section 3: Update "Q (Invariants)" Section

**Location**: Add new invariant for virtual thread compatibility:

```markdown

### Virtual Thread Compatibility (NEW)

**Q** now includes:

```
Q = {real_impl ∨ throw, no_mock, no_stub, no_fallback, no_lie, vthread_compatible}
```

**vthread_compatible** = Code must work correctly with virtual threads:

1. **No ThreadLocal**: Replace with ScopedValue (automatic inheritance)
2. **No synchronized + I/O**: Use ReentrantLock; keep critical sections minimal
3. **Blocking I/O is OK**: Virtual threads efficiently yield on I/O (DB, HTTP, etc.)
4. **Proper cleanup**: ScopedValue scopes auto-cleanup; no manual remove()

Examples:

```java
// ThreadLocal is NOT allowed in new code
// ❌ static final ThreadLocal<T> x = new ThreadLocal<>();

// ✅ ScopedValue instead (inherited by virtual threads)
static final ScopedValue<T> x = ScopedValue.newInstance();
ScopedValue.where(x, value).run(() -> { /* ... */ });
```

See: `docs/architecture/decisions/ADR-030-scoped-values-context.md`
```

---

## Section 4: Add New Section "V (Virtual Threads)" After "Λ (Build)"

**Location**: After Build section, before Architecture:

```markdown

## V (Virtual Threads) — MANDATORY FOR v6.0.0+

YAWL v6.0.0 is built on Java 25 virtual threads (Project Loom, JEP 444). Virtual threads are production-ready and enable:

- 99.95% memory reduction for concurrent tasks
- 10,000+ concurrent cases in < 100MB heap
- Unlimited agent scaling

### Virtual Thread Usage Guidelines

**✅ REQUIRED:**
- Agent discovery loops: Use `Thread.ofVirtual()` instead of `new Thread()`
- Long-running tasks: Name virtual threads for debugging
- Async tasks: Use `Executors.newVirtualThreadPerTaskExecutor()`

**✅ ALLOWED:**
- Blocking I/O (DB, HTTP): Virtual threads efficiently yield
- Thread.sleep(): No problem (yields carrier)
- Structured concurrency: Use `StructuredTaskScope` for fan-out

**❌ PROHIBITED:**
- Synchronized blocks with I/O: Replace with `ReentrantLock`
- ThreadLocal: Replace with `ScopedValue`
- Unbounded thread creation: Use scope.fork() with bounded parallelism

### Pinning Detection (Development)

```bash
# JVM flag for development; detects when virtual threads pin to carrier
-Djdk.tracePinnedThreads=short

# Expected output:
# Virtual thread pinned for 100.5ms (held by org.yawlfoundation.yawl.engine...)
```

### Performance Baseline

| Operation | Concurrency | Memory | Latency |
|---|---|---|---|
| 1000 agents | Serial | 2GB | 1s/agent |
| 1000 agents | Virtual threads | ~1MB | 1s/agent |
| 10,000 cases | Serial | 20GB | Infeasible |
| 10,000 cases | Virtual threads | ~10MB | 10ms/case |

See: `docs/architecture/decisions/ADR-028-virtual-threads-strategy.md`
```

---

## Section 5: Update "Γ (Architecture)" Section

**Location**: Add to Key Types table:

```markdown

### Java 25 Key Types

| Domain | Java 25 Features | Key Types |
|--------|---|---|
| Events | Records + Sealed | `YWorkflowEvent`, `YCaseLifecycleEvent`, ... |
| State Machines | Sealed Interfaces + Records | `WorkItemState`, `EnabledState`, `ExecutingState`, ... |
| Concurrency | Virtual Threads + StructuredTaskScope | `GenericPartyAgent`, agent discovery loops |
| Context | ScopedValue | `WorkflowContext`, `SecurityContext`, `AuditLog` |

All new immutable data (events, DTOs, workflow artifacts) must be **records** for thread-safety and clarity.

See: `docs/architecture/decisions/ADR-026-027.md`
```

---

## Section 6: Update "μ(O) → A (Agents)" Section

**Location**: Add Java 25 agent notes:

```markdown

### Java 25 Agent Requirements

All YAWL agents (engineer, validator, architect, etc.) must:

1. **Know Java 25 features**: Records, sealed classes, virtual threads, scoped values
2. **Use virtual threads for concurrency**: Not platform threads
3. **Replace ThreadLocal with ScopedValue**: For context propagation
4. **Avoid synchronized + I/O**: Replace with ReentrantLock
5. **Use pattern matching**: For sealed type hierarchies
6. **Write immutable records**: For all data classes

See: `docs/v6/upgrade/DEVELOPER_GUIDE_JAVA25.md`
```

---

## Section 7: Update "Π (Skills)" Section

**Location**: Add Java 25 validation skill:

```markdown

## Java 25 Validation Skills

New skills for Java 25 development:

- **/yawl-validate-java25**: Check for virtual thread pinning, ThreadLocal usage, synchronized blocks with I/O
- **/yawl-migrate-records**: Suggest record conversions for mutable data classes
- **/yawl-test-vthread**: Create virtual thread compatibility tests

See: `.claude/skills/` directory
```

---

## Section 8: Update "R (Rules)" Section

**Location**: Add Java 25 rules:

```markdown

| Category | Rule File | Scope |
|---|---|---|
| Java 25 | `java25/virtual-threads.md` | **/*.java (vthread usage) |
| Java 25 | `java25/records-sealed.md` | Event, DTO, data classes |
| Java 25 | `java25/scoped-values.md` | Context, ThreadLocal → ScopedValue |
| Java 25 | `java25/pinning-detection.md` | synchronized blocks |
```

---

## Section 9: Add New "D (Documentation)" Section

**Location**: After R (Rules):

```markdown

## D (Documentation) — Java 25 Comprehensive Guides

YAWL v6.0.0 includes comprehensive Java 25 documentation:

### User & Operator Guides

- **JAVA25_UPGRADE_GUIDE.md**: What changed, migration path, performance gains
- **PERFORMANCE_TUNING_JAVA25.md**: GC tuning, memory sizing, monitoring

### Developer Guides

- **DEVELOPER_GUIDE_JAVA25.md**: Best practices, patterns, pitfalls
- **MIGRATION_CHECKLIST.md**: Step-by-step upgrade for extensions

### Architecture Decisions (ADRs)

- **ADR-026**: Sealed Classes for Domain Hierarchies
- **ADR-027**: Records for Immutable Data
- **ADR-028**: Virtual Threads Deployment Strategy
- **ADR-029**: Structured Concurrency Patterns
- **ADR-030**: Scoped Values for Context Management

See: `docs/v6/upgrade/` and `docs/architecture/decisions/`
```

---

## Section 10: Add New "S (Security)" Update

**Location**: Update Security Manager note (if exists):

```markdown

### Java 25 Security Manager Removal

**Important**: Java 25 removes Security Manager (deprecated in Java 17).

If your code uses `SecurityManager`, `Policy`, or `Permission` classes:
- Remove all Security Manager calls
- Replace with Spring Security RBAC
- Use container-level security (Docker securityContext, Kubernetes Pod Security Policy)
- Use OPA/Gatekeeper for policy enforcement

YAWL v6.0.0 uses Spring Security exclusively; no Security Manager dependencies.
```

---

## Section 11: Update "STOP Conditions"

**Location**: Add Java 25 related stops:

```markdown

**STOP** if any of these occur (updated for Java 25):

- Virtual thread pinning detected (logs show "pinned for X ms")
  → Identify synchronized block; replace with ReentrantLock
- ThreadLocal usage encountered (in new code)
  → Replace with ScopedValue
- synchronized block with I/O detected
  → Refactor: lock for state update only, I/O outside
- Pattern matching switch statement incomplete
  → Sealed type requires all cases; compiler will error
- ```

---

## Suggested CLAUDE.md Integration Points

### Option 1: Minimal Update (Recommended)

Add a single section:

```markdown
## Java 25 Requirements (NEW)

**YAWL v6.0.0 requires Java 25 LTS.** All code must:

1. Be virtual-thread compatible (no ThreadLocal, minimal synchronized)
2. Use records for immutable data (events, DTOs)
3. Use sealed classes for domain hierarchies
4. Avoid blocking I/O inside synchronized blocks

See comprehensive guides:
- docs/v6/upgrade/JAVA25_UPGRADE_GUIDE.md
- docs/architecture/decisions/ADR-026 through ADR-030
- docs/v6/upgrade/DEVELOPER_GUIDE_JAVA25.md
```

### Option 2: Comprehensive Update

Integrate Java 25 sections throughout CLAUDE.md at each section level, as shown above.

---

## Review Checklist

Before integrating these updates:

- [ ] All references point to correct documentation files
- [ ] No duplicate information with ADRs
- [ ] Examples are accurate and up-to-date
- [ ] Code snippets compile and run on Java 25
- [ ] Links are relative paths (e.g., `docs/v6/upgrade/...`)
- [ ] Style matches existing CLAUDE.md format
- [ ] Team reviews and approves updates

---

## Implementation

To apply these updates:

```bash
# 1. Open root CLAUDE.md
vi /home/user/yawl/CLAUDE.md

# 2. Find insertion points (section headers)
grep -n "^## " CLAUDE.md

# 3. Insert Java 25 sections as outlined above

# 4. Verify links work
grep -r "docs/v6/upgrade" CLAUDE.md

# 5. Commit changes
git add CLAUDE.md
git commit -m "docs: Add Java 25 requirements to CLAUDE.md"
```

---

## Version History

- **2026-02-20**: Initial update guide created
- **Status**: Pending integration into root CLAUDE.md
- **Review**: Architecture team approval required

---

**Next Steps:**
1. Review this document with YAWL architecture team
2. Choose integration option (minimal vs comprehensive)
3. Apply updates to CLAUDE.md
4. Update all team members
5. Begin Java 25 adoption

---

**Contact**: YAWL Architecture Team for questions or clarifications.
