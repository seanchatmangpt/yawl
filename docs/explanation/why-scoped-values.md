# Why YAWL Uses ScopedValue Instead of ThreadLocal

YAWL's tenant context management switched from `ThreadLocal<TenantContext>` to `java.lang.ScopedValue<TenantContext>` in v6.0. This document explains why: the fundamental incompatibility between ThreadLocal semantics and Java 21+ virtual threads, the advantages of ScopedValue, and when ThreadLocal remains the right choice.

---

## The Problem: ThreadLocal on Virtual Threads

Java 21 introduced virtual threads — a lightweight abstraction where millions of threads can run on a single JVM. Virtual threads are cheap (microseconds to create, no OS kernel allocation), but they expose a critical problem with ThreadLocal: **carrier thread pinning**.

### Carrier Thread Pinning

Virtual threads are implemented by the JVM as lightweight objects multiplexed onto OS kernel threads called _carrier threads_. When a virtual thread calls code that uses ThreadLocal, the JVM must park the virtual thread on a specific carrier thread to ensure that all subsequent ThreadLocal accesses see the same value. This is a safety guarantee inherited from the ThreadLocal contract: a thread always sees its own value.

But on a carrier thread pinning, the JVM cannot unmount the virtual thread from its carrier thread to schedule other virtual threads on that carrier. The result is **blocking** — a single virtual thread holding a carrier thread hostage, preventing other virtual threads from running. At scale (millions of cases, each running in a virtual thread), this creates contention that defeats the purpose of virtual threads.

Example: YAWL's `YAnnouncer.announceEnabledWorkItems()` ran in a virtual thread, called `ScopedTenantContext.getTenantContext()` inside a callback, and unexpectedly pinned its carrier thread for microseconds. Multiply by 1M cases, and the system loses all parallelism benefits.

### Memory Leaks and Manual Cleanup

ThreadLocal values are stored in a thread-local map inside the `Thread` object itself. When the thread dies, the map is garbage collected. But on virtual threads, which are created and destroyed at high frequency, this creates two problems:

1. **Memory leak risk**: If a virtual thread's context is not manually cleared before it exits, the ThreadLocal entry persists in the carrier thread's inherited ThreadLocal map indefinitely. With millions of short-lived virtual threads, orphaned entries accumulate.

2. **Manual cleanup burden**: Developers must remember to call `clearTenantContext()` in finally blocks or after-completion handlers. This is easy to forget, and the mistake is silent — no exception until memory runs out.

### Non-Inheritance by Default

ThreadLocal values are not inherited by child threads by default. `InheritableThreadLocal` exists to fix this, but it still pinning virtual threads and offers no immutability guarantee — child threads can overwrite parent values, risking cross-tenant contamination.

---

## How ScopedValue Works

`ScopedValue` (JEP 429, Java 21) is a new scoped binding mechanism designed for virtual threads. A ScopedValue represents a lexically scoped binding, not thread-wide state.

### Binding Scope

```java
ScopedValue<TenantContext> CURRENT_TENANT = ScopedValue.newInstance();

// Bind and run:
TenantContext ctx = new TenantContext("customer-123");
ScopedValue.where(CURRENT_TENANT, ctx).run(() -> {
    // Inside this lambda, CURRENT_TENANT.get() returns ctx
    TenantContext current = CURRENT_TENANT.get();
    // Context is automatically released when lambda exits
});
// Outside the lambda, CURRENT_TENANT.isBound() returns false
```

No thread-local map, no inheritance ambiguity, no manual cleanup. The scope is explicit: you see where the binding starts and where it ends.

### Automatic Inheritance by Virtual Threads

When a virtual thread is created within a ScopedValue scope, the binding is inherited automatically:

```java
ScopedValue.where(CURRENT_TENANT, ctx).run(() -> {
    // Create a new virtual thread inside this scope
    Thread.ofVirtual().start(() -> {
        // This virtual thread automatically inherits CURRENT_TENANT = ctx
        TenantContext inherited = CURRENT_TENANT.get();
        assert inherited == ctx; // true
    });
});
```

Inheritance is automatic, immutable (child cannot modify), and scope-aware: when the parent scope exits, the binding is released everywhere.

### Integration with StructuredTaskScope

YAWL's `ScopedTenantContext.runParallel()` method demonstrates the power of combining ScopedValues with structured concurrency:

```java
public static String[] runParallel(TenantContext ctx, Callable<?>[] tasks) {
    return runWithTenant(ctx, () -> {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<String>awaitAllSuccessfulOrThrow())) {
            List<StructuredTaskScope.Subtask<String>> subtasks = Arrays.stream(tasks)
                .map(t -> scope.fork(() -> (String) ((Callable) t).call()))
                .toList();
            scope.join();
            return subtasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toArray(String[]::new);
        }
    });
}
```

All forked subtasks automatically inherit the tenant context without explicit parameter passing. If any task fails, the scope cancels the others and propagates the exception. The tenant context is released when the scope exits.

---

## Trade-Offs Considered

### InheritableThreadLocal

Why not just use `InheritableThreadLocal` instead of rewriting to ScopedValue?

**Rejected**. InheritableThreadLocal solves the inheritance problem but still pins virtual threads and leaks memory on high-frequency thread creation. It also allows child threads to write to the parent's context via `set()`, which violates the immutability principle needed for multi-tenancy (one tenant's code must never be able to peek at another tenant's data).

### Explicit Parameter Passing

Why not pass the `TenantContext` as a parameter to every method?

**Rejected**. This would require changing the entire YAWL API — every engine method, every workflow task handler, every event listener would need to accept an additional `TenantContext` parameter. The effort is enormous, the API becomes verbose, and it breaks backward compatibility. Implicit context via scoping is more ergonomic and matches how other frameworks handle request scope (e.g., servlet request attributes, Spring's `RequestContextHolder`).

### CDI or Spring Request Scope

Why not delegate to the container (Spring, CDI)?

**Rejected**. The YAWL engine core (`org.yawlfoundation.yawl.engine`) must not depend on Spring or CDI. These are deployment-time concerns, not engine semantics. ScopedValue is built into the standard library and works in any execution environment: standalone JVMs, containers, serverless, embedded.

### ContextVar (JEP 429 Alternative)

Java also offers `InheritableThreadLocal.copy()` in virtual threads, and frameworks like Spring use the older `ContextVar` pattern (now superseded by ScopedValue).

**Rejected**. ContextVar is more complex and less efficient than ScopedValue for this use case. ScopedValue is the modern approach, standardized, and optimized for exactly this pattern.

---

## When ThreadLocal Is Still Correct

ScopedValue is not a universal replacement for ThreadLocal. ThreadLocal remains the right choice for:

- **Stateful non-virtual-thread scenarios**: If code runs only on platform threads (e.g., blocking JDBC drivers that must not be wrapped in virtual threads), ThreadLocal avoids the overhead of scope binding.
- **Inherited initialization patterns**: Code that relies on `ThreadLocal.initialValue()` or subclass initialization may be easier to migrate to ThreadLocal than ScopedValue.
- **Legacy systems**: Large codebases where replacing ThreadLocal would require widespread refactoring.

YAWL uses ThreadLocal only in `YEngine.setTenantContext()` and `clearTenantContext()` for backward compatibility. New code uses ScopedValue via `ScopedTenantContext`.

---

## The Benefit Summary

| Aspect | ThreadLocal | ScopedValue |
|--------|------------|------------|
| **Virtual thread pinning** | Yes (bottleneck) | No (automatic unmount) |
| **Memory safety** | Manual cleanup required | Automatic (scope exit) |
| **Inheritance** | Optional (risky) | Automatic (immutable) |
| **API clarity** | Implicit (thread-wide) | Explicit (scoped block) |
| **Structured concurrency** | Incompatible | Native support |
| **Requires library** | Built-in | Built-in (Java 21+) |

---

## Next Steps

Code targeting YAWL 6.0+ should use:

```java
ScopedTenantContext.runWithTenant(ctx, () -> {
    // All engine operations here see the tenant context
    YEngine.getInstance().launchCase(...);
});
```

The old ThreadLocal-based API remains available for source compatibility but is deprecated. See `docs/explanation/decisions/ADR-030-scoped-values-context.md` for the full architectural decision.
