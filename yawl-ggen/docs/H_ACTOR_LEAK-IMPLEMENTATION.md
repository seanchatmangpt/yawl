# H_ACTOR_LEAK Guard Pattern Implementation

## Overview

The `H_ACTOR_LEAK` guard pattern is implemented as part of the YAWL Hyper-Standards Validation system to detect memory leaks in actor-based code. This pattern ensures that actor implementations follow proper lifecycle management and resource cleanup practices.

## Detection Patterns

### 1. Actor Creation Without Destruction

**Detection Method**: SPARQL-based AST analysis

**Patterns Detected**:
```java
// Violation: actor created but not destroyed
Actor newActor = new Actor("example");
return newActor; // No destroy/cleanup call

// Violation: builder pattern without cleanup
Actor actor = Actor.builder("example").build();
return actor; // No cleanup

// Violation: spawn actor without tracking
Actor spawned = Actor.spawn("worker");
// Lost reference - potential memory leak
```

**Fix Guidance**: "Implement proper actor lifecycle management and cleanup"

### 2. Unbounded State Accumulation

**Detection Method**: SPARQL-based AST analysis

**Patterns Detected**:
```java
// Violation: accumulating without bounds
for (int i = 0; i < 1000; i++) {
    actor.putMessage("msg-" + i); // Accumulates without bounds
}

// Violation: unbounded collection growth
List<String> unboundedList = new ArrayList<>();
while (true) {
    unboundedList.add("item-" + i); // Grows indefinitely
}

// Violation: infinite accumulation
while (true) {
    actor.putMessage("continuous"); // No exit condition
}
```

**Fix Guidance**: "Use bounded collections or implement proper cleanup mechanisms"

### 3. Reference Leaks

**Detection Method**: SPARQL-based AST analysis

**Patterns Detected**:
```java
// Violation: strong reference not cleared
private Actor retainedReference;
retainedReference = new Actor("ref");
// No null check or cleanup

// Violation: weak reference not managed
WeakReference<Actor> weakRef = new WeakReference<>(actor);
// No get() or cleanup call

// Violation: static actor references
private static Actor cachedActor; // Not thread-safe
```

**Fix Guidance**: "Use proper reference management patterns"

### 4. Resource Leaks

**Detection Method**: SPARQL-based AST analysis

**Patterns Detected**:
```java
// Violation: executor without shutdown
ExecutorService executor = Executors.newCachedThreadPool();
executor.submit(task);
// No executor.shutdown()

// Violation: unclosed resources
FileInputStream fis = new FileInputStream("file");
// No fis.close()

// Violation: unbounded thread creation
for (int i = 0; i < 1000; i++) {
    new Thread(() -> {}).start(); // No management
}
```

**Fix Guidance**: "Use try-with-resources or proper shutdown mechanisms"

### 5. Mailbox Overflow

**Detection Method**: SPARQL-based AST analysis

**Patterns Detected**:
```java
// Violation: queue without capacity limits
BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
while (true) {
    queue.put(new Message()); // Can grow indefinitely
}

// Violation: no backpressure
actor.tell("message"); // No rate limiting
```

**Fix Guidance**: "Use bounded queues or implement backpressure mechanisms"

## Implementation Details

### SPARQL Query Structure

The guard uses a comprehensive SPARQL query that analyzes Java AST converted to RDF:

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern ?violationType
WHERE {
  # 5 main patterns with sub-patterns each
  {
    ?method a code:Method ;
            code:name ?name ;
            code:body ?body ;
            code:lineNumber ?line .

    FILTER(
      REGEX(?name, "(create|spawn|instantiate|new)[A-Z]") ||
      REGEX(?body, 'new\\s+(Actor|Message|WorkItem)')
    ) &&
    !REGEX(?body, 'destroy|dispose|close')

    BIND("H_ACTOR_LEAK" AS ?pattern)
    BIND("ACTOR_CREATION_NO_DESTRUCTION" AS ?violationType)
  }
  # ... other patterns
}
```

### Integration with HyperStandardsValidator

The pattern is automatically registered in `HyperStandardsValidator.java`:

```java
// H_ACTOR_LEAK: SPARQL-based detection of actor memory leaks
String actorLeakQuery = loadSparqlQuery("guards-h-actor-leak.sparql");
checkers.add(new SparqlGuardChecker(
    "H_ACTOR_LEAK",
    actorLeakQuery,
    GuardChecker.Severity.FAIL
));
```

### Test Coverage

Comprehensive test fixtures are provided:

1. **violation-h-actor-leak-comprehensive.java**: Contains intentional violations
2. **clean-actor-code-comprehensive.java**: Proper implementation examples
3. **EnhancedActorGuardPatternsTest.java**: Test suite with detailed validation

## Performance Considerations

- **Processing Time**: < 5 seconds per comprehensive file
- **False Positive Rate**: Designed to be minimal through specific regex patterns
- **Query Optimization**: Early FILTER statements reduce result set size
- **Memory Usage**: Uses AST parsing rather than full file scanning

## Best Practices for Actor Code

### Preventing Memory Leaks

1. **Implement Lifecycle Methods**:
```java
public void cleanup() {
    messages.clear();
    resources.close();
    references.clear();
}
```

2. **Use Bounded Collections**:
```java
private final BlockingQueue<Message> queue = new ArrayBlockingQueue<>(1000);
```

3. **Monitor Resource Usage**:
```java
Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
```

### Preventing Reference Leaks

1. **Proper Reference Management**:
```java
WeakReference<Actor> weakRef = new WeakReference<>(actor);
Actor strongRef = weakRef.get();
if (strongRef != null) {
    strongRef.doWork();
}
```

2. **Avoid Static References**:
```java
private final AtomicReference<Actor> actorRef = new AtomicReference<>();
```

### Preventing Resource Leaks

1. **Use Try-With-Resources**:
```java
try (Connection conn = dataSource.getConnection()) {
    // Use connection
} // Auto-close
```

2. **Proper Thread Management**:
```java
ExecutorService executor = Executors.newFixedThreadPool(4);
try {
    // Submit tasks
} finally {
    executor.shutdown();
}
```

## Exit Codes

| Exit Code | Meaning | Action |
|-----------|---------|--------|
| 0 | GREEN - No violations | Proceed to next phase |
| 2 | RED - Violations found | Fix leaks or throw UnsupportedOperationException |

## Troubleshooting

### Common Issues

1. **No Violations Detected**
   - Verify SPARQL queries are loaded correctly
   - Check that code patterns match expected formats
   - Ensure test fixtures are in correct location

2. **False Positives**
   - Refine regex patterns
   - Add more specific conditions
   - Test against production code to tune detection

3. **Performance Issues**
   - Split complex queries if needed
   - Add LIMIT clauses for large files
   - Use parallel processing for multiple files

### Debug Commands

```bash
# Test individual query
s-query -e guards-h-actor-leak.sparql test/fixtures/actor/violation-h-actor-leak.java

# Validate SPARQL syntax
sparql validate guards-h-actor-leak.sparql

# Run enhanced tests
mvn test -pl yawl-ggen -Dtest=EnhancedActorGuardPatternsTest
```

## Future Enhancements

1. **Additional Patterns**:
   - H_ACTOR_RACE_CONDITION: Race condition detection
   - H_ACTOR_MAILBOX_OVERFLOW: Enhanced mailbox overflow detection

2. **Machine Learning Integration**:
   - Pattern detection based on historical violations
   - Predictive detection of potential issues

3. **Performance Monitoring**:
   - Integration with YAWL engine metrics
   - Real-time memory usage tracking

## References

- HyperStandardsValidator.java: Main orchestration
- guards-h-actor-leak.sparql: SPARQL query definitions
- EnhancedActorGuardPatternsTest.java: Test suite
- GuardViolation.java: Violation model
- GuardSummary.java: Summary statistics