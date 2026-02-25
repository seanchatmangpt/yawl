# ADR-030: Scoped Values for Context Management

## Status

**ACCEPTED**

## Date

2026-02-20

## Context

YAWL propagates request context (workflow ID, security principal, audit trail) throughout execution using ThreadLocal:

```java
// Current: ThreadLocal (problematic with virtual threads)
static final ThreadLocal<String> workflowId = new ThreadLocal<>();
static final ThreadLocal<SecurityContext> securityContext = new ThreadLocal<>();

// Usage
workflowId.set("wf-123");
securityContext.set(context);

try {
    // workflowId and securityContext accessible here
    engine.processCase();
} finally {
    workflowId.remove();
    securityContext.remove();  // Easy to forget!
}
```

**Problems with ThreadLocal:**

1. **No Virtual Thread Inheritance**: Virtual threads don't automatically inherit ThreadLocal values from parent threads
2. **Memory Leaks**: Forgetting `remove()` leaves entry in thread-local map
3. **Implicit Dependencies**: Calling code doesn't see what context is required
4. **Not Type-Safe**: Access via `get()` returns `null` if not set; runtime errors possible

Java 25 introduces **ScopedValue** (JEP 506), designed specifically for virtual threads:
- Automatically inherited by child virtual threads
- Immutable; no explicit cleanup needed
- Type-safe; set and access via strongly-typed variables
- Explicit scope boundaries

## Decision

**YAWL v6.0.0 replaces all ThreadLocal usage with ScopedValue for virtual thread compatibility and safety.**

### 1. Workflow Context

**File**: `org.yawlfoundation.yawl.engine.context.WorkflowContext`

```java
// Define context values globally
public class WorkflowContext {
    // Core workflow context
    public static final ScopedValue<String> WORKFLOW_ID = ScopedValue.newInstance();
    public static final ScopedValue<YIdentifier> CASE_ID = ScopedValue.newInstance();
    public static final ScopedValue<YSpecificationID> SPECIFICATION_ID = ScopedValue.newInstance();

    // Security context
    public static final ScopedValue<SecurityContext> SECURITY = ScopedValue.newInstance();
    public static final ScopedValue<String> PRINCIPAL_ID = ScopedValue.newInstance();

    // Audit context
    public static final ScopedValue<AuditLog> AUDIT_LOG = ScopedValue.newInstance();
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    // Execution context
    public static final ScopedValue<YEngine> ENGINE = ScopedValue.newInstance();
    public static final ScopedValue<LocalDateTime> EXECUTION_START = ScopedValue.newInstance();

    // Private constructor to prevent instantiation
    private WorkflowContext() {}

    // Convenience method to set multiple values at once
    public static ScopedValues with(
        String workflowId,
        SecurityContext security,
        AuditLog auditLog) {
        return new ScopedValues(workflowId, security, auditLog);
    }
}

// Builder for convenient scoped value setup
public class ScopedValues {
    private final String workflowId;
    private final SecurityContext security;
    private final AuditLog auditLog;

    public ScopedValues(String workflowId, SecurityContext security, AuditLog auditLog) {
        this.workflowId = workflowId;
        this.security = security;
        this.auditLog = auditLog;
    }

    public void runWithContext(Runnable task) {
        ScopedValue.where(WorkflowContext.WORKFLOW_ID, workflowId)
            .where(WorkflowContext.SECURITY, security)
            .where(WorkflowContext.AUDIT_LOG, auditLog)
            .run(task);
    }

    public <T> T callWithContext(Callable<T> task) throws Exception {
        return ScopedValue.where(WorkflowContext.WORKFLOW_ID, workflowId)
            .where(WorkflowContext.SECURITY, security)
            .where(WorkflowContext.AUDIT_LOG, auditLog)
            .call(task);
    }
}
```

### Usage in Engine Execution

```java
// org.yawlfoundation.yawl.engine.YEngine
public String launchCase(YSpecificationID specID, String caseParams, ...)
        throws YStateException {

    // Create case
    YCase caseObj = new YCase(specID, caseParams);
    String caseID = caseObj.getID();
    YIdentifier yid = new YIdentifier(caseID);

    // Get security context from request
    SecurityContext secContext = getCurrentSecurityContext();
    AuditLog auditLog = AuditLog.forUser(secContext.getUserId());

    // Setup context for entire case execution
    // Context is automatically inherited by virtual threads forked during execution
    ScopedValue.where(WorkflowContext.CASE_ID, yid)
        .where(WorkflowContext.SPECIFICATION_ID, specID)
        .where(WorkflowContext.SECURITY, secContext)
        .where(WorkflowContext.AUDIT_LOG, auditLog)
        .where(WorkflowContext.ENGINE, this)
        .where(WorkflowContext.EXECUTION_START, LocalDateTime.now())
        .run(() -> {
            try {
                // Entire case execution happens within this scope
                // All nested code sees the context
                YNetRunner runner = new YNetRunner(caseObj, this);
                runner.continueIfPossible();

                // Audit the launch
                auditLog.log("Case launched: " + caseID);
            } catch (Exception e) {
                auditLog.log("Case launch failed: " + e.getMessage());
                throw e;
            }
        });

    return caseID;
}
```

---

### 2. Virtual Thread Context Inheritance

```java
// Any virtual thread forked during case execution automatically inherits context
// org.yawlfoundation.yawl.engine.YNetRunner

public void executeTaskInParallel(YTask task, List<Object> instances) {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        // These virtual threads automatically inherit:
        // - CASE_ID
        // - SECURITY
        // - AUDIT_LOG
        // - ENGINE
        // - etc.
        List<Subtask<Void>> tasks = instances.stream()
            .map(instance -> scope.fork(() -> executeInstance(task, instance)))
            .toList();

        scope.join();
        scope.throwIfFailed();
    }
}

private void executeInstance(YTask task, Object instance) throws YStateException {
    // Context is available here without re-binding!
    String caseID = WorkflowContext.CASE_ID.get().getValue();
    SecurityContext sec = WorkflowContext.SECURITY.get();
    AuditLog audit = WorkflowContext.AUDIT_LOG.get();

    // Do work...
    completeInstance(task, instance);

    // Log completion
    audit.log("Instance completed for " + caseID);
}
```

---

### 3. Agent Discovery Loop with Context

```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent

@Override
public void start() {
    running.set(true);
    discoveryThread = Thread.ofVirtual()
        .name("yawl-agent-discovery-" + config.getAgentId())
        .start(this::runDiscoveryLoop);
}

private void runDiscoveryLoop() {
    while (running.get()) {
        try {
            // Get available work items from engine
            Set<YWorkItem> items = engine.getAvailableWorkItems();

            // Process each item with structured concurrency
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                List<Subtask<Void>> tasks = items.stream()
                    .map(item -> scope.fork(() -> discoverAndProcess(item)))
                    .toList();

                scope.join();
                scope.throwIfFailed();
            }

            Thread.sleep(config.getPollInterval());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}

private void discoverAndProcess(YWorkItem item) throws YStateException {
    // Retrieve context (would normally be set by engine)
    // For agents, context may be from incoming request
    AuditLog audit = WorkflowContext.AUDIT_LOG.get();
    if (audit != null) {
        audit.log("Agent processing: " + item.getId());
    }

    // Process...
}
```

---

### 4. Servlet/Spring Request Context

```java
// org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBController
// Spring REST controller

@RestController
@RequestMapping("/ib")
public class InterfaceBController {

    private final YEngine engine;

    @PostMapping("/launch")
    public LaunchCaseResponse launchCase(
            @RequestBody LaunchCaseRequest request,
            HttpServletRequest httpRequest) throws YStateException {

        // Extract security context from request
        SecurityContext secContext = extractSecurityContext(httpRequest);

        // Create audit log for this request
        String requestId = UUID.randomUUID().toString();
        AuditLog auditLog = AuditLog.forRequest(requestId, httpRequest.getRemoteUser());

        // Setup context for entire request
        String caseID = ScopedValue.where(WorkflowContext.PRINCIPAL_ID, httpRequest.getRemoteUser())
            .where(WorkflowContext.SECURITY, secContext)
            .where(WorkflowContext.AUDIT_LOG, auditLog)
            .where(WorkflowContext.REQUEST_ID, requestId)
            .call(() -> engine.launchCase(
                request.specID(),
                request.caseParams(),
                request.logData()
            ));

        return new LaunchCaseResponse(caseID, /* ... */);
    }

    @PostMapping("/workitems/{id}/complete")
    public CompleteWorkItemResponse completeWorkItem(
            @PathVariable String id,
            @RequestBody CompleteWorkItemRequest request,
            HttpServletRequest httpRequest) throws YStateException {

        SecurityContext secContext = extractSecurityContext(httpRequest);
        AuditLog auditLog = AuditLog.forRequest(
            UUID.randomUUID().toString(),
            httpRequest.getRemoteUser()
        );

        ScopedValue.where(WorkflowContext.SECURITY, secContext)
            .where(WorkflowContext.AUDIT_LOG, auditLog)
            .run(() -> {
                try {
                    engine.completeWorkItem(id, request.outputData(), httpRequest.getRemoteUser());
                } catch (YStateException e) {
                    auditLog.log("Work item completion failed: " + e.getMessage());
                    throw e;
                }
            });

        return new CompleteWorkItemResponse(id, "COMPLETED");
    }

    private SecurityContext extractSecurityContext(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new SecurityContext(
            auth.getName(),
            auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList()
        );
    }
}

// Security context DTO
public record SecurityContext(
    String userId,
    List<String> roles
) {}
```

---

### 5. Audit Logging with Context

```java
// org.yawlfoundation.yawl.engine.audit.AuditLog

public class AuditLog {
    private final String logId;
    private final String userId;
    private final LocalDateTime startTime;
    private final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());

    public static AuditLog forUser(String userId) {
        return new AuditLog(UUID.randomUUID().toString(), userId);
    }

    public static AuditLog forRequest(String requestId, String userId) {
        AuditLog log = new AuditLog(requestId, userId);
        log.log("Request started");
        return log;
    }

    private AuditLog(String logId, String userId) {
        this.logId = logId;
        this.userId = userId;
        this.startTime = LocalDateTime.now();
    }

    public void log(String message) {
        entries.add(new AuditEntry(
            LocalDateTime.now(),
            message,
            WorkflowContext.CASE_ID.isBound() ? WorkflowContext.CASE_ID.get().getValue() : null
        ));
    }

    public void logException(Exception e) {
        log("Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }

    public List<AuditEntry> getEntries() {
        return List.copyOf(entries);
    }

    record AuditEntry(LocalDateTime timestamp, String message, @Nullable String caseId) {}
}
```

---

## Pattern: Optional Context Values

When context values may not be set:

```java
public class WorkflowContextHelper {
    // Safe access to context with defaults
    public static String getCaseIdOrDefault(String defaultValue) {
        try {
            return WorkflowContext.CASE_ID.get().getValue();
        } catch (NoSuchElementException e) {
            return defaultValue;
        }
    }

    public static SecurityContext getSecurityContextOrGuest() {
        try {
            return WorkflowContext.SECURITY.get();
        } catch (NoSuchElementException e) {
            return SecurityContext.GUEST;
        }
    }

    // Check if context is bound
    public static boolean isCaseContextSet() {
        return WorkflowContext.CASE_ID.isBound();
    }

    // Usage
    String caseId = getCaseIdOrDefault("unknown-case");
    SecurityContext sec = getSecurityContextOrGuest();
}
```

---

## Pattern: Context Propagation Across Async Boundaries

Virtual threads automatically inherit context:

```java
// Original thread
ScopedValue.where(WorkflowContext.SECURITY, securityContext)
    .run(() -> {
        // Fork virtual threads
        Thread.ofVirtual()
            .name("worker-1")
            .start(() -> {
                // securityContext is automatically available here!
                SecurityContext sec = WorkflowContext.SECURITY.get();
            });
    });
```

But explicit propagation is needed for callback-based code:

```java
// Callback-based code (not recommended with virtual threads)
Future<Result> future = executor.submit(() -> {
    // securityContext NOT available here (different binding)
    try {
        SecurityContext sec = WorkflowContext.SECURITY.get();  // May throw
    } catch (NoSuchElementException e) {
        // Need to handle missing context
    }
});

// Better: Bind context before passing to executor
SecurityContext sec = WorkflowContext.SECURITY.get();
Future<Result> future = executor.submit(() -> {
    ScopedValue.where(WorkflowContext.SECURITY, sec)
        .call(() -> doWork());
});
```

---

## Testing with ScopedValues

```java
@Test
void testWithScopedValues() {
    YIdentifier caseID = new YIdentifier("test-case-1");
    SecurityContext secContext = new SecurityContext("testuser", List.of("WORKFLOW_USER"));

    ScopedValue.where(WorkflowContext.CASE_ID, caseID)
        .where(WorkflowContext.SECURITY, secContext)
        .run(() -> {
            // Inside scope, values are accessible
            assertEquals(caseID, WorkflowContext.CASE_ID.get());
            assertEquals(secContext, WorkflowContext.SECURITY.get());

            // Test code that depends on context
            testCaseExecution();
        });

    // Outside scope, values are no longer bound
    assertThrows(NoSuchElementException.class, () ->
        WorkflowContext.CASE_ID.get()
    );
}

@Test
void testVirtualThreadInheritance() throws InterruptedException {
    YIdentifier caseID = new YIdentifier("test-case");
    AtomicReference<YIdentifier> inheritedCaseID = new AtomicReference<>();

    ScopedValue.where(WorkflowContext.CASE_ID, caseID)
        .run(() -> {
            Thread virtualThread = Thread.ofVirtual()
                .start(() -> {
                    // Virtual thread inherits context
                    inheritedCaseID.set(WorkflowContext.CASE_ID.get());
                });

            virtualThread.join();
        });

    assertEquals(caseID, inheritedCaseID.get());
}
```

---

## Migration Path

### Step 1: Define ScopedValue Constants

Create `WorkflowContext.java` with all context variables.

### Step 2: Update Request Entry Points

Replace ThreadLocal setup with ScopedValue.where() binding in:
- REST controllers (Spring)
- Servlet filters
- Agent discovery entry points

### Step 3: Audit ThreadLocal Usage

```bash
grep -r "ThreadLocal" src/main/java/org/yawlfoundation/
grep -r "threadLocal" src/main/java/org/yawlfoundation/
```

Convert all ThreadLocal to ScopedValue.

### Step 4: Test Virtual Thread Inheritance

Verify context propagates to forked virtual threads:
```java
Thread.ofVirtual().start(() -> {
    // Verify context is available
    WorkflowContext.CASE_ID.get();
});
```

### Step 5: Remove ThreadLocal

Clean up old ThreadLocal code.

---

## Consequences

### Positive

1. **Virtual Thread Compatible**: Context automatically inherited by child threads
2. **Memory Safe**: No remove() needed; scope defines lifetime
3. **Type-Safe**: Compile-time type checking for context access
4. **Explicit Scope**: Clear where context is set/used
5. **Performance**: Immutable; no synchronization overhead

### Negative

1. **JDK 21+ Requirement**: ScopedValue is new API
2. **Learning Curve**: Different from ThreadLocal mental model
3. **No Late Binding**: Context must be set before forking threads

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Missing context access | MEDIUM | LOW | Use Optional access helpers; test thoroughly |
| Context leaks | LOW | MEDIUM | Scope boundaries are explicit; review at entry points |

---

## Alternatives Considered

### Alternative 1: Keep ThreadLocal

**Rejected.** Doesn't work well with virtual threads; memory leak risk.

### Alternative 2: Pass Context as Parameters

```java
public void executeCase(String caseID, SecurityContext sec) { /* ... */ }
```

**Rejected.** Clutters method signatures; not feasible for deep call chains.

### Alternative 3: Use Spring's RequestContextHolder

**Acceptable**, but ScopedValue is more explicit and doesn't require Spring.

---

## Related ADRs

- **ADR-028**: Virtual Threads Deployment Strategy
- **ADR-029**: Structured Concurrency Patterns

---

## References

- JEP 506: Scoped Values (finalized Java 21, Java 25)
- Virtual Threads Guide: https://openjdk.org/jeps/444
- "Scoped Values": https://openjdk.org/jeps/506

---

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-20
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-20

---

**Revision History:**
- 2026-02-20: Initial ADR
