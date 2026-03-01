# H-Guards Actor Patterns — Memory Leak & Deadlock Detection

**Status**: IMPLEMENTED  
**Phase**: 4 of YAWL Actor Model Validation Plan  
**Patterns**: H_ACTOR_LEAK, H_ACTOR_DEADLOCK

---

## Overview

This document specifies two actor-specific guard patterns that extend the existing H-Guards system to detect critical issues in actor-based code:

1. **H_ACTOR_LEAK** - Detects memory leaks and improper resource management in actor code
2. **H_ACTOR_DEADLOCK** - Detects potential deadlock scenarios in concurrent actor systems

These patterns are integrated with the existing HyperStandardsValidator and provide comprehensive validation for YAWL actor implementations.

---

## Pattern Details

### H_ACTOR_LEAK - Actor Memory Leak Detection

**Mission**: Prevent memory leaks in actor-based systems caused by improper lifecycle management.

**Detection Methods**:
1. **SPARQL-based AST analysis** - Identifies patterns of resource accumulation without cleanup

**Patterns Detected**:
- **Actor Creation Without Cleanup**: Creating actors without proper destruction
- **State Accumulation Without Cleanup**: Growing queues/collections without periodic cleanup
- **Unmanaged Weak References**: Holding weak references without proper cleanup or access

**SPARQL Query**: `guards-h-actor-leak.sparql`

**Example Violations**:
```java
// Violation: Actor created but not destroyed
public Actor createActor() {
    Actor actor = new Actor("example");
    return actor; // No cleanup call
}

// Violation: Accumulating state without cleanup
public void accumulateMessages(Actor actor) {
    while (true) {
        actor.putMessage("msg"); // Keeps growing
    }
    // No clearing mechanism
}

// Violation: Unmanaged weak reference
public void holdReference(Actor actor) {
    WeakReference<Actor> ref = new WeakReference<>(actor);
    // No get() or cleanup
}
```

**Fix Guidance**: "Implement proper actor lifecycle management and cleanup"

---

### H_ACTOR_DEADLOCK - Actor Deadlock Detection

**Mission**: Prevent deadlock scenarios in concurrent actor systems.

**Detection Methods**:
1. **SPARQL-based AST analysis** - Identifies patterns that can lead to deadlocks

**Patterns Detected**:
- **Circular Waiting**: Synchronized blocks with wait/notify potential
- **Nested Locking**: Lock acquisition in inconsistent orders
- **Unbounded Blocking**: Operations without timeout mechanisms
- **Indefinite Sleep**: Long sleep operations while holding actor resources
- **Resource Ordering Violations**: Inconsistent lock acquisition sequences

**SPARQL Query**: `guards-h-actor-deadlock.sparql`

**Example Violations**:
```java
// Violation: Circular waiting with synchronized
public void deadlockRisk(Actor actor) {
    synchronized (actor) {
        actor.wait(); // Can cause deadlock
    }
}

// Violation: Nested locking
public void nestedLock() {
    lockA.lock();
    synchronized (lockB) { // Deadlock risk
        // Critical section
    }
}

// Violation: Unbounded blocking
public void unboundedBlocking(Queue<Message> queue) {
    Message msg = queue.poll(); // Can block indefinitely
}
```

**Fix Guidance**: "Use async messaging, avoid blocking operations, or implement timeout mechanisms"

---

## Integration with Existing H-Guards

### HyperStandardsValidator Extension

The `HyperStandardsValidator` has been extended to include the two new actor guard patterns:

```java
// Added to registerDefaultCheckers()
// H_ACTOR_LEAK: SPARQL-based detection of actor memory leaks
String actorLeakQuery = loadSparqlQuery("guards-h-actor-leak.sparql");
checkers.add(new SparqlGuardChecker(
    "H_ACTOR_LEAK",
    actorLeakQuery,
    GuardChecker.Severity.FAIL
));

// H_ACTOR_DEADLOCK: SPARQL-based detection of actor deadlock risks
String actorDeadlockQuery = loadSparqlQuery("guards-h-actor-deadlock.sparql");
checkers.add(new SparqlGuardChecker(
    "H_ACTOR_DEADLOCK",
    actorDeadlockQuery,
    GuardChecker.Severity.FAIL
));
```

### Model Updates

#### GuardViolation.java
Extended to include actor-specific fix guidance:

```java
case "H_ACTOR_LEAK" ->
    "Implement proper actor lifecycle management and cleanup";
case "H_ACTOR_DEADLOCK" ->
    "Use async messaging, avoid blocking operations, or implement timeout mechanisms";
```

#### GuardSummary.java
Added fields for actor pattern tracking:

```java
private int h_actor_leak_count = 0;
private int h_actor_deadlock_count = 0;
```

---

## Usage

### Command Line Interface

```bash
# Run all guards including actor patterns
ggen validate --phase guards --emit /path/to/actor/code

# Check specific actor patterns
ggen validate --phase guards --emit /path/to/actor/code --verbose

# Generate receipt with actor violation details
ggen validate --phase guards --emit /path/to/actor/code \
  --receipt-file guard-receipt.json
```

### Exit Codes

| Exit | Meaning | Action |
|------|---------|--------|
| 0 | No violations | Proceed to next phase |
| 1 | Transient error (IO, parse) | Retry |
| 2 | Violations found (including actor patterns) | Developer must fix |

### Programmatic Usage

```java
HyperStandardsValidator validator = new HyperStandardsValidator();
GuardReceipt receipt = validator.validateEmitDir(Paths.get("src/main/java"));

// Check actor-specific violations
List<GuardViolation> actorLeaks = receipt.getViolations().stream()
    .filter(v -> v.getPattern().equals("H_ACTOR_LEAK"))
    .toList();

List<GuardViolation> actorDeadlocks = receipt.getViolations().stream()
    .filter(v -> v.getPattern().equals("H_ACTOR_DEADLOCK"))
    .toList();

// Get summary statistics
GuardSummary summary = receipt.getSummary();
int leakCount = summary.getH_actor_leak_count();
int deadlockCount = summary.getH_actor_deadlock_count();
```

---

## Test Coverage

### Test Fixtures

The following test fixtures are provided:

1. **`violation-h-actor-leak.java`** - Code with intentional memory leak patterns
2. **`violation-h-actor-deadlock.java`** - Code with intentional deadlock patterns
3. **`clean-actor-code.java`** - Code that should pass all actor guard checks

### Test Cases

Comprehensive test suite in `ActorGuardPatternsTest.java`:

- Detection of specific actor leak patterns
- Detection of specific actor deadlock patterns
- Validation that clean code passes
- Integration with existing guard patterns
- Summary statistics verification
- Guard checker registration verification

---

## Best Practices for Actor Code

### Preventing Memory Leaks

1. **Implement Proper Lifecycle Methods**:
```java
public void cleanup() {
    // Clear all resources
    messages.clear();
    references.clear();
}
```

2. **Use Bounded Collections**:
```java
// Good: Bounded queue
private final BlockingQueue<Message> queue = new ArrayBlockingQueue<>(1000);
```

3. **Monitor Resource Usage**:
```java
// Add periodic cleanup
@Scheduled(fixedRate = 60000)
public void periodicCleanup() {
    // Clean up unused resources
}
```

### Preventing Deadlocks

1. **Use Async Messaging**:
```java
// Good: Non-blocking async operations
actor.tellAsync(message, timeout);
```

2. **Implement Timeouts**:
```java
// Good: Timeout for blocking operations
Message msg = queue.poll(5, TimeUnit.SECONDS);
```

3. **Follow Consistent Lock Ordering**:
```java
// Good: Always acquire locks in same order
lockA.lock();
try {
    lockB.lock();
    try {
        // Critical section
    } finally {
        lockB.unlock();
    }
} finally {
    lockA.unlock();
}
```

4. **Use Virtual Threads for Non-Blocking Operations**:
```java
// Good: Virtual threads for async processing
Thread.ofVirtual().start(() -> {
    processAsync();
});
```

---

## Performance Considerations

### Query Optimization

1. **Selective Execution**: Actor guard queries only run when actor-related code is detected
2. **Early Termination**: Queries stop at first match for each pattern
3. **Caching**: RDF models cached across queries for same file

### Processing Time

- **Actor Leak Detection**: < 2 seconds per file
- **Actor Deadlock Detection**: < 3 seconds per file
- **Total with existing guards**: < 5 seconds per file

---

## Troubleshooting

### Common Issues

1. **No Violations Detected**:
   - Verify SPARQL queries are loaded correctly
   - Check that code patterns match expected formats
   - Ensure test fixtures are in correct location

2. **False Positives**:
   - Refine regex patterns in SPARQL queries
   - Add more specific conditions to reduce matches
   - Test against production code to tune detection

3. **Performance Issues**:
   - Split complex queries into smaller ones
   - Add LIMIT clauses to result sets
   - Use parallel processing for multiple files

### Debug Commands

```bash
# Test individual actor leak query
s-query -e guards-h-actor-leak.sparql test/fixtures/actor/violation-h-actor-leak.java

# Test individual actor deadlock query
s-query -e guards-h-actor-deadlock.sparql test/fixtures/actor/violation-h-actor-deadlock.java

# Validate SPARQL syntax
sparql validate guards-h-actor-leak.sparql
```

---

## Evolution Path

### Future Enhancements

1. **Additional Actor Patterns**:
   - H_ACTOR_RACE_CONDITION: Detect race conditions in actor state
   - H_ACTOR_MAILBOX_OVERFLOW: Detect unbounded message accumulation

2. **Performance Monitoring**:
   - Integration with YAWL engine metrics
   - Real-time memory usage tracking
   - Performance impact analysis

3. **Machine Learning Integration**:
   - Pattern detection based on historical violations
   - Predictive detection of potential issues
   - Automated fix suggestions

### Version Control

Track query evolution for reproducibility:

```toml
[sparql_versions]
h_actor_leak = "1.0"
h_actor_deadlock = "1.0"
```

---

**Complete**: Actor guard patterns implemented and integrated with existing H-Guards system
