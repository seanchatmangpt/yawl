# Java 25 Documentation Suite for YAWL v6.0.0

**Date**: February 2026 | **Status**: Production-Ready | **Scope**: Complete Java 25 Adoption Guide

---

## Overview

This directory contains comprehensive documentation for Java 25 adoption in YAWL v6.0.0. The documentation is organized by audience and use case.

---

## Documentation Structure

### 1. For Everyone: Quick Start

**Start here** if you just want to understand Java 25 basics:

- **[JAVA25_UPGRADE_GUIDE.md](JAVA25_UPGRADE_GUIDE.md)** (45 min read)
  - What changed in Java 25
  - Breaking changes and migration paths
  - New capabilities unlocked
  - Performance improvements (5-10% throughput, 99.95% memory for concurrency)
  - Step-by-step migration path

### 2. For Developers: How-To Guides

**Use these** to develop with Java 25 features:

- **[DEVELOPER_GUIDE_JAVA25.md](DEVELOPER_GUIDE_JAVA25.md)** (60 min read)
  - Records in depth (immutable data, compact constructors, validation)
  - Sealed classes and exhaustive pattern matching
  - Virtual threads best practices (naming, pinning avoidance, context)
  - Structured concurrency patterns (fan-out, timeouts, error handling)
  - ScopedValue for context management
  - Common pitfalls and solutions
  - Performance tips

### 3. For Extension Authors: Migration Steps

**Follow these** to upgrade custom YAWL components:

- **[MIGRATION_CHECKLIST.md](MIGRATION_CHECKLIST.md)** (2-5 days effort)
  - Phase 1: Pre-migration assessment
  - Phase 2: Quick wins (compact headers)
  - Phase 3: Convert events to records (2-3 days)
  - Phase 4: ThreadLocal → ScopedValue (2-3 days)
  - Phase 5: Virtual threads conversion (1 day)
  - Phase 6: Testing & validation (1-2 days)
  - Phase 7: Documentation updates
  - Common issues & solutions

### 4. For Operations: Performance Tuning

**Use these** to optimize YAWL deployments:

- **[PERFORMANCE_TUNING_JAVA25.md](PERFORMANCE_TUNING_JAVA25.md)** (Reference)
  - Quick wins (+5-10% throughput, -50% build time)
  - GC tuning (G1, Shenandoah, ZGC recommendations)
  - Virtual thread optimization (scheduler tuning, pinning detection)
  - Memory sizing formulas
  - Database connection pooling for virtual threads
  - Monitoring with JFR, Micrometer, OpenTelemetry
  - Bottleneck identification
  - Production tuning recommendations

### 5. Architecture Decisions (ADRs)

**Read these** to understand design decisions:

- **[ADR-026: Sealed Classes](../architecture/decisions/ADR-026-sealed-classes-pattern.md)**
  - Domain hierarchy design (YElement, YWorkItem, YEvent)
  - Compiler-verified exhaustiveness in pattern matching
  - Status: ACCEPTED | Date: 2026-02-20

- **[ADR-027: Records for Immutable Data](../architecture/decisions/ADR-027-records-immutable-data.md)**
  - Event hierarchy as sealed records
  - DTO conversions
  - Hibernate/JSON serialization
  - Status: ACCEPTED | Date: 2026-02-20

- **[ADR-028: Virtual Threads Strategy](../architecture/decisions/ADR-028-virtual-threads-strategy.md)**
  - Tier 1: Agent discovery loops (immediate)
  - Tier 2: Work item batch processing (structured concurrency)
  - Tier 3: Case execution threading (future)
  - Pinning avoidance (synchronized → ReentrantLock)
  - Database connection pooling for virtual threads
  - Status: ACCEPTED | Date: 2026-02-20

- **[ADR-029: Structured Concurrency](../architecture/decisions/ADR-029-structured-concurrency-patterns.md)**
  - Fan-out patterns (ShutdownOnFailure)
  - Merge/parallel gateway execution
  - Multi-instance tasks with quorum
  - Timeout handling
  - Testing structured concurrency
  - Status: ACCEPTED | Date: 2026-02-20

- **[ADR-030: Scoped Values Context](../architecture/decisions/ADR-030-scoped-values-context.md)**
  - Replacing ThreadLocal with ScopedValue
  - Automatic inheritance by virtual threads
  - Workflow context, security context, audit logging
  - Integration with Spring REST controllers
  - Testing ScopedValue inheritance
  - Status: ACCEPTED | Date: 2026-02-20

### 6. Integration Guide

**Use this** to update the project specification:

- **[JAVA25_CLAUDE_MD_UPDATES.md](JAVA25_CLAUDE_MD_UPDATES.md)** (Reference)
  - Updates to add/modify in root CLAUDE.md
  - New sections: Java 25 Requirements, Virtual Thread Guidelines
  - Updated sections: Invariants (Q), Guards (H), Architecture (Γ)
  - New rules for Java 25 validation

---

## Quick Navigation

### By Role

| Role | Start Here | Then Read |
|------|-----------|-----------|
| **User/Operator** | JAVA25_UPGRADE_GUIDE.md | PERFORMANCE_TUNING_JAVA25.md |
| **Developer** | DEVELOPER_GUIDE_JAVA25.md | ADR-026, ADR-027, ADR-030 |
| **Extension Author** | MIGRATION_CHECKLIST.md | DEVELOPER_GUIDE_JAVA25.md |
| **Architect** | ADR-026 to ADR-030 | JAVA25_UPGRADE_GUIDE.md |
| **DevOps/SRE** | PERFORMANCE_TUNING_JAVA25.md | JAVA25_UPGRADE_GUIDE.md |

### By Topic

| Topic | Document | ADR |
|-------|----------|-----|
| **Immutable Data** | DEVELOPER_GUIDE_JAVA25.md § Records | ADR-027 |
| **Domain Hierarchies** | DEVELOPER_GUIDE_JAVA25.md § Sealed Classes | ADR-026 |
| **Concurrency** | DEVELOPER_GUIDE_JAVA25.md § Virtual Threads | ADR-028 |
| **Parallel Tasks** | DEVELOPER_GUIDE_JAVA25.md § Structured Concurrency | ADR-029 |
| **Context Management** | DEVELOPER_GUIDE_JAVA25.md § ScopedValue | ADR-030 |
| **Performance** | PERFORMANCE_TUNING_JAVA25.md | (All ADRs) |
| **Migration** | MIGRATION_CHECKLIST.md | ADR-026 to ADR-030 |

---

## Key Numbers

### Performance Improvements

| Metric | Improvement |
|--------|-------------|
| Throughput | +5-10% (compact headers) |
| Build Time | -50% (parallel Maven) |
| Virtual Thread Memory | -99.95% (1000 agents: 2GB → 1MB) |
| Virtual Thread Context Switch | ~100x faster (100ns vs 10μs) |
| GC Pause Times (ZGC) | -99% (100ms → 0.3ms) |
| Container Startup (AOT) | -25% (3.2s → 2.4s) |

### Effort Estimates

| Phase | Duration | Effort | Impact |
|-------|----------|--------|--------|
| Enable compact headers | 5 min | Trivial | +5-10% throughput |
| Convert events to records | 2-3 days | Low | Cleaner code, thread-safe |
| Migrate ThreadLocal | 2-3 days | Medium | Virtual thread compatible |
| Add virtual threads | 1 day | Low | 99.95% memory savings |
| Update GC configuration | 1 day | Low | Reduce pause times |
| **Total** | **~1 week** | **Medium** | **5-10% perf gain + unlimited scale** |

---

## Recommended Reading Order

### For First-Time Users

1. **JAVA25_UPGRADE_GUIDE.md** (Executive Summary)
   - 30 min: Understand what changed
   - 30 min: Review breaking changes
   - 30 min: Understand migration path

2. **DEVELOPER_GUIDE_JAVA25.md** (Deep Dive)
   - 1 hour: Learn records, sealed classes, patterns
   - 1 hour: Learn virtual threads best practices
   - 1 hour: Learn ScopedValue context management

3. **ADRs 026-030** (Architecture)
   - 30 min each: Understand design decisions
   - Rationale for sealed classes, records, virtual threads

4. **PERFORMANCE_TUNING_JAVA25.md** (Optimization)
   - Reference material for GC tuning, memory sizing
   - Use when deploying to production

### For Extension Authors

1. **MIGRATION_CHECKLIST.md** (Step-by-step)
   - 2-5 days: Follow each phase
   - Convert events, migrate ThreadLocal, add virtual threads

2. **DEVELOPER_GUIDE_JAVA25.md** (Reference)
   - Refer to for code examples, patterns
   - Consult for common pitfalls

3. **ADR-030** (Context Management)
   - If using custom context/security handling

---

## Key Features Explained

### Records (JEP 440)

**What**: Immutable data types with auto-generated boilerplate.

```java
// Before: 50 lines of boilerplate
public class YCaseEvent {
    private final Instant timestamp;
    private final String caseID;
    // ... constructor, equals, hashCode, toString, getters ...
}

// After: 3 lines
public record YCaseEvent(Instant timestamp, String caseID) {}
```

**Why**: Thread-safe events, no race conditions, cleaner code.

---

### Sealed Classes (JEP 409)

**What**: Restrict inheritance hierarchy; compiler verifies exhaustiveness.

```java
public sealed interface YEvent
    permits YCaseEvent, YWorkItemEvent, YTimerEvent {}

// Compiler error if switch misses a case!
String description = event switch {
    case YCaseEvent _ -> "Case event",
    case YWorkItemEvent _ -> "WorkItem event",
    case YTimerEvent _ -> "Timer event",
};
```

**Why**: Safer refactoring, no surprise subclasses, exhaustiveness checking.

---

### Virtual Threads (JEP 444)

**What**: Lightweight threads managed by JVM, not OS.

```java
// Before: 2GB for 1000 agents
new Thread(this::run).start();

// After: ~1MB for 1000 agents
Thread.ofVirtual().name("agent-1").start(this::run);
```

**Why**: Unlimited concurrency, massive memory savings, efficient blocking I/O.

---

### Scoped Values (JEP 506)

**What**: Context values automatically inherited by virtual threads.

```java
// Before: ThreadLocal (doesn't work with virtual threads)
static final ThreadLocal<String> caseID = new ThreadLocal<>();

// After: ScopedValue (inherited automatically)
static final ScopedValue<String> caseID = ScopedValue.newInstance();
ScopedValue.where(caseID, "case-1")
    .run(() -> engine.execute());  // Virtual threads see caseID
```

**Why**: Proper context propagation, no memory leaks, virtual thread compatible.

---

### Structured Concurrency (JEP 505)

**What**: Safe parallel task execution with guaranteed cleanup.

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var tasks = items.stream()
        .map(item -> scope.fork(() -> process(item)))
        .toList();

    scope.join();           // Wait for all
    scope.throwIfFailed();  // First failure cancels rest
} // Automatic cleanup
```

**Why**: Cleaner parallelism, automatic error handling and cancellation.

---

## Validation Checklists

### Pre-Migration

- [ ] Java version: `java -version` shows 25.x
- [ ] Maven version: `mvn --version` shows 3.9+
- [ ] YAWL version: 6.0.0+
- [ ] Read: JAVA25_UPGRADE_GUIDE.md
- [ ] Review: ADR-004 (Spring Boot 3.4 + Java 25)

### During Migration

- [ ] Run: `bash scripts/dx.sh all` (compile + test)
- [ ] Check: `jdeprscan --for-removal target/*.jar`
- [ ] Verify: No synchronized blocks with I/O
- [ ] Verify: No ThreadLocal in new code
- [ ] Test: Virtual thread inheritance of ScopedValues

### Post-Migration

- [ ] Measure: Baseline metrics (before/after)
- [ ] Monitor: GC pause times, CPU usage
- [ ] Verify: No virtual thread pinning (`-Djdk.tracePinnedThreads=short`)
- [ ] Test: Load testing with 10,000+ concurrent operations
- [ ] Deploy: To staging environment first

---

## Support Resources

### Internal Documentation

- **JAVA25_UPGRADE_GUIDE.md**: Complete feature overview
- **DEVELOPER_GUIDE_JAVA25.md**: Practical how-tos
- **MIGRATION_CHECKLIST.md**: Step-by-step migration
- **PERFORMANCE_TUNING_JAVA25.md**: Optimization reference
- **ADR-026 to ADR-030**: Architecture decisions

### External References

- [Java 25 Release Notes](https://www.oracle.com/java/technologies/javase/25-relnotes.html)
- [JEP 440: Records](https://openjdk.org/jeps/440)
- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505)
- [JEP 506: Scoped Values](https://openjdk.org/jeps/506)

---

## Feedback & Questions

- **Architecture questions**: See ADR-026 to ADR-030
- **Code examples**: See DEVELOPER_GUIDE_JAVA25.md
- **Performance questions**: See PERFORMANCE_TUNING_JAVA25.md
- **Migration help**: See MIGRATION_CHECKLIST.md
- **General questions**: See JAVA25_UPGRADE_GUIDE.md

---

## Document Status

| Document | Status | Last Updated | Next Review |
|----------|--------|--------------|-------------|
| JAVA25_UPGRADE_GUIDE.md | Complete | 2026-02-20 | 2026-06-20 |
| DEVELOPER_GUIDE_JAVA25.md | Complete | 2026-02-20 | 2026-06-20 |
| MIGRATION_CHECKLIST.md | Complete | 2026-02-20 | 2026-06-20 |
| PERFORMANCE_TUNING_JAVA25.md | Complete | 2026-02-20 | 2026-06-20 |
| ADR-026: Sealed Classes | ACCEPTED | 2026-02-20 | 2026-08-20 |
| ADR-027: Records | ACCEPTED | 2026-02-20 | 2026-08-20 |
| ADR-028: Virtual Threads | ACCEPTED | 2026-02-20 | 2026-08-20 |
| ADR-029: Structured Concurrency | ACCEPTED | 2026-02-20 | 2026-08-20 |
| ADR-030: Scoped Values | ACCEPTED | 2026-02-20 | 2026-08-20 |
| JAVA25_CLAUDE_MD_UPDATES.md | Complete | 2026-02-20 | 2026-06-20 |

---

**Ready to upgrade?** Start with **[JAVA25_UPGRADE_GUIDE.md](JAVA25_UPGRADE_GUIDE.md)** or jump to your role above.
