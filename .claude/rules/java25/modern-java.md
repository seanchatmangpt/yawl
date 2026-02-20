---
paths:
  - "**/*.java"
---

# Java 25 Conventions

## Records
- Use records for immutable data: events, work items, API payloads, DTOs
- Records auto-generate equals/hashCode/toString
- Prefer records over classes with only final fields

## Sealed Classes
- Use for domain model hierarchies: YElement, YWorkItemStatus, YEvent
- Enables exhaustive pattern matching in switch expressions
- All permitted subtypes must be in same package (or module)

## Pattern Matching
- Use exhaustive switch on sealed hierarchies (no default case needed)
- Use `instanceof Type name` pattern variables (no cast needed)
- Compiler verifies completeness for sealed types

## Virtual Threads
- `Thread.ofVirtual().name("case-" + caseId).start(runnable)` for per-case threads
- `Executors.newVirtualThreadPerTaskExecutor()` for task pools
- Never pin virtual threads with `synchronized` (use `ReentrantLock` instead)
- No thread pool sizing needed (millions of virtual threads possible)

## Scoped Values
- `ScopedValue<WorkflowContext>` replaces `ThreadLocal`
- Immutable, automatically inherited by forked virtual threads
- Use `ScopedValue.callWhere()` to bind values

## Structured Concurrency
- `StructuredTaskScope.ShutdownOnFailure` for parallel processing
- Automatic cancellation of remaining tasks on first failure
- Parent-child task relationships are observable

## Text Blocks
- Use triple-quote `"""` for multi-line XML, JSON, SQL, test data
- Prefer text blocks over string concatenation for readability

## Compact Object Headers
- Enabled via `-XX:+UseCompactObjectHeaders`
- Saves 4-8 bytes per object, 5-10% throughput improvement
- Free optimization, no code changes needed
