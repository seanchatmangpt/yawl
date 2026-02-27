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

## Unnamed Variables (JEP 456, Java 22+)
- Use `_` for unused catch params, pattern vars, lambda params
- `catch (IOException _) { throw new UncheckedIOException(e); }` — suppress warning
- `var _ = executor.submit(task);` — intentional discard of side-effect result
- Clarifies: this variable is deliberately unused, not accidentally omitted

## Primitive Types in Patterns (JEP 488, Java 23+)
- Pattern matching now works with primitives: `int`, `long`, `double`, `boolean`
- `switch (priority) { case int i when i > 10 -> CRITICAL; case 0 -> IDLE; }`
- Eliminates boxing overhead in dispatch over numeric domain models
- Use for `YWorkItemStatus` ordinal routing, priority dispatch in `YNetRunner`

## Module Import Declarations (JEP 476, Java 23+)
- `import module java.base;` imports all exported packages of a module
- Reduces import boilerplate in utility/integration classes
- `import module org.yawlfoundation.yawl.elements;` imports full elements API

## Gatherers (JEP 473, Java 22+)
- Custom intermediate stream operations via `Stream.gather(Gatherer<T,A,R>)`
- Built-ins: `Gatherers.windowFixed(n)`, `Gatherers.scan()`, `Gatherers.fold()`
- Use for sliding-window work-item batching, running state aggregation:
  ```java
  workItems.stream()
      .gather(Gatherers.windowFixed(10))
      .map(this::processBatch)
      .toList();
  ```
- Prefer over complex `reduce`/`collect` chains for readability

## Flexible Constructor Bodies (JEP 482, Java 22+)
- Statements may precede `super()` / `this()` calls (validation, logging, computation)
- Eliminates static-factory workarounds for pre-construction validation:
  ```java
  YWorkItem(String id) {
      Objects.requireNonNull(id, "id required"); // legal before super()
      super(id);
  }
  ```
