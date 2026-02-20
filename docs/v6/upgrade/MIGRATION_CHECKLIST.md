# Java 25 Migration Checklist for YAWL Extensions

**Version:** 1.0 | **Date:** February 2026 | **Target:** Extension & Plugin Authors

---

## Introduction

This checklist guides extension authors through adopting Java 25 features when updating custom YAWL components.

**Time Estimate**: 2-5 days depending on complexity.

---

## Phase 1: Pre-Migration Assessment (Day 1)

### Environment Check

- [ ] Verify Java version: `java -version` (should show 25.x)
- [ ] Check Maven version: `mvn --version` (Maven 3.9+)
- [ ] Review pom.xml for Java version property:
  ```xml
  <java.version>25</java.version>
  <maven.compiler.source>25</maven.compiler.source>
  <maven.compiler.target>25</maven.compiler.target>
  ```

### Codebase Assessment

- [ ] Count custom data classes (events, DTOs):
  ```bash
  find src -name "*.java" -type f | wc -l
  ```
- [ ] Identify all ThreadLocal usage:
  ```bash
  grep -r "ThreadLocal" src/
  ```
- [ ] Find all synchronized methods:
  ```bash
  grep -r "synchronized " src/
  ```
- [ ] Check for deprecated API usage:
  ```bash
  jdeprscan --for-removal target/extension.jar
  ```

### Dependency Check

- [ ] YAWL dependencies compatible with Java 25:
  ```bash
  mvn dependency:tree | grep -i "yawl"
  ```
- [ ] Third-party libraries that might not support Java 25:
  ```bash
  mvn dependency:tree | head -20
  ```

---

## Phase 2: Quick Wins (Day 1-2)

### Enable Compact Object Headers (5 minutes)

- [ ] Update Dockerfile:
  ```dockerfile
  ENV JAVA_OPTS="-XX:+UseCompactObjectHeaders"
  ```
- [ ] Or update application.yml:
  ```yaml
  # Not applicable for extensions, but verify in YAWL runtime
  ```
- [ ] Test build: `mvn clean package`

**Expected**: Build succeeds; no code changes required.

---

### Replace Mutable Event Classes with Records (2-3 days)

#### Identify Event Classes

- [ ] List all custom event classes:
  ```bash
  find src -name "*Event.java" -o -name "*Listener.java"
  ```
- [ ] For each event class, check if it:
  - [ ] Has only immutable fields (no setters)
  - [ ] Has custom equals/hashCode/toString
  - [ ] Is used in multi-threaded context

#### Convert to Records

**Example Before:**
```java
public class CustomWorkItemEvent {
    private final String workItemID;
    private final Instant timestamp;
    private final String status;

    public CustomWorkItemEvent(String workItemID, Instant timestamp, String status) {
        this.workItemID = workItemID;
        this.timestamp = timestamp;
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomWorkItemEvent that = (CustomWorkItemEvent) o;
        return Objects.equals(workItemID, that.workItemID) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workItemID, timestamp, status);
    }

    @Override
    public String toString() {
        return "CustomWorkItemEvent{" +
               "workItemID='" + workItemID + '\'' +
               ", timestamp=" + timestamp +
               ", status='" + status + '\'' +
               '}';
    }

    // Getters
    public String getWorkItemID() { return workItemID; }
    public Instant getTimestamp() { return timestamp; }
    public String getStatus() { return status; }
}
```

**Example After:**
```java
public record CustomWorkItemEvent(
    String workItemID,
    Instant timestamp,
    String status
) {
    // Compact constructor for validation
    public CustomWorkItemEvent {
        Objects.requireNonNull(workItemID, "workItemID required");
        Objects.requireNonNull(timestamp, "timestamp required");
        Objects.requireNonNull(status, "status required");
    }

    // No need for equals, hashCode, toString - auto-generated!
    // No need for getters - use workItemID(), timestamp(), status()
}
```

#### Update Usage

- [ ] Replace constructor calls:
  ```java
  // Before
  CustomWorkItemEvent event = new CustomWorkItemEvent(id, now, "COMPLETED");
  event.getWorkItemID();

  // After
  CustomWorkItemEvent event = new CustomWorkItemEvent(id, now, "COMPLETED");
  event.workItemID();
  ```

- [ ] Replace getter calls:
  ```java
  // Before: event.getWorkItemID()
  // After: event.workItemID()
  ```

#### Test Each Conversion

- [ ] Run tests for each converted class:
  ```bash
  mvn -Dtest=CustomWorkItemEventTest test
  ```

**Checklist per Event Class:**
- [ ] Constructor works
- [ ] Accessors work (no get prefix)
- [ ] Equals/hashCode work
- [ ] toString works
- [ ] All tests pass
- [ ] No compilation errors

---

## Phase 3: ThreadLocal → ScopedValue (Day 2-3)

### Find All ThreadLocal Usage

```bash
grep -rn "new ThreadLocal" src/main/java/
grep -rn "ThreadLocal.withInitial" src/main/java/
grep -rn "\.get()" src/main/java/  # May be ThreadLocal.get()
grep -rn "\.set(" src/main/java/  # May be ThreadLocal.set()
grep -rn "\.remove()" src/main/java/  # May be ThreadLocal.remove()
```

### For Each ThreadLocal

**Example Before:**
```java
public class AgentContext {
    private static final ThreadLocal<String> agentID = new ThreadLocal<>();
    private static final ThreadLocal<SecurityContext> security = new ThreadLocal<>();

    public static void setAgentID(String id) {
        agentID.set(id);
    }

    public static String getAgentID() {
        String id = agentID.get();
        if (id == null) throw new IllegalStateException("Agent ID not set");
        return id;
    }

    public static void cleanup() {
        agentID.remove();
        security.remove();
    }
}
```

**Example After:**
```java
public class AgentContext {
    public static final ScopedValue<String> AGENT_ID = ScopedValue.newInstance();
    public static final ScopedValue<SecurityContext> SECURITY = ScopedValue.newInstance();

    // Static methods no longer needed - use ScopedValue.where() directly
    // No cleanup needed - scope handles it
}

// Usage changes from:
AgentContext.setAgentID("agent-1");
String id = AgentContext.getAgentID();
// ... code ...
AgentContext.cleanup();

// To:
ScopedValue.where(AgentContext.AGENT_ID, "agent-1")
    .where(AgentContext.SECURITY, secContext)
    .run(() -> {
        String id = AgentContext.AGENT_ID.get();
        // ... code ...
        // Automatic cleanup when scope exits
    });
```

### Migration Steps for Each ThreadLocal

1. [ ] Define ScopedValue constant:
   ```java
   public static final ScopedValue<Type> NAME = ScopedValue.newInstance();
   ```

2. [ ] Find all set() calls:
   ```bash
   grep -n "threadLocal.set(" src/
   ```

3. [ ] Wrap with ScopedValue.where():
   ```java
   // Before
   threadLocal.set(value);
   doWork();
   threadLocal.remove();

   // After
   ScopedValue.where(THREAD_LOCAL, value)
       .run(this::doWork);
   ```

4. [ ] Find all get() calls:
   ```java
   // Before
   String value = threadLocal.get();

   // After
   String value = MY_SCOPED_VALUE.get();  // May throw NoSuchElementException if not bound
   ```

5. [ ] Handle missing values:
   ```java
   // If value might not be set:
   String value = null;
   try {
       value = MY_SCOPED_VALUE.get();
   } catch (NoSuchElementException e) {
       value = defaultValue;
   }
   ```

6. [ ] Test with virtual threads:
   ```java
   @Test
   void testScopedValueInVirtualThread() throws InterruptedException {
       String expected = "test-value";
       AtomicReference<String> actual = new AtomicReference<>();

       ScopedValue.where(MY_VALUE, expected).run(() -> {
           Thread vt = Thread.ofVirtual().start(() -> {
               actual.set(MY_VALUE.get());  // Should inherit!
           });
           vt.join();
       });

       assertEquals(expected, actual.get());
   }
   ```

7. [ ] Test multiple inheritance:
   ```java
   @Test
   void testMultipleScopedValues() {
       ScopedValue.where(VALUE_1, "a")
           .where(VALUE_2, "b")
           .where(VALUE_3, "c")
           .run(() -> {
               assertEquals("a", VALUE_1.get());
               assertEquals("b", VALUE_2.get());
               assertEquals("c", VALUE_3.get());
           });
   }
   ```

**Checklist per ThreadLocal:**
- [ ] ScopedValue defined
- [ ] All set() calls replaced with ScopedValue.where()
- [ ] All get() calls updated
- [ ] All remove() calls removed
- [ ] Tests pass
- [ ] Virtual thread inheritance verified

---

## Phase 4: Sealed Classes & Pattern Matching (Day 3)

### Identify Hierarchies

- [ ] Find extension hierarchies:
  ```bash
  grep -rn "extends\|implements" src/ | grep -v "implements Serializable"
  ```
- [ ] List abstract classes/interfaces with multiple implementations:
  ```bash
  find src -name "Abstract*.java" -o -name "*Interface.java"
  ```

### Convert to Sealed

**Example Before:**
```java
public interface TaskHandler {
    void handle(YTask task);
}

// Unknown number of implementations in codebase
public class CustomTaskHandler implements TaskHandler { /* ... */ }
public class LegacyTaskHandler implements TaskHandler { /* ... */ }
```

**Example After:**
```java
public sealed interface TaskHandler
    permits CustomTaskHandler, LegacyTaskHandler {
    void handle(YTask task);
}

public non-sealed class CustomTaskHandler implements TaskHandler {
    /* ... */
}

public non-sealed class LegacyTaskHandler implements TaskHandler {
    /* ... */
}
```

### Add Pattern Matching

- [ ] Find all type checks:
  ```bash
  grep -rn "instanceof" src/
  ```

- [ ] Convert if-else chains to switch:
  ```java
  // Before
  if (handler instanceof CustomTaskHandler) {
      CustomTaskHandler h = (CustomTaskHandler) handler;
      h.handle(task);
  } else if (handler instanceof LegacyTaskHandler) {
      LegacyTaskHandler h = (LegacyTaskHandler) handler;
      h.handle(task);
  }

  // After
  switch (handler) {
      case CustomTaskHandler h -> h.handle(task);
      case LegacyTaskHandler h -> h.handle(task);
  }
  ```

**Checklist per Hierarchy:**
- [ ] Sealed interface/abstract class created
- [ ] All implementations listed in permits
- [ ] All isinstance checks converted to pattern matching
- [ ] Compiler verifies exhaustiveness
- [ ] Tests pass

---

## Phase 5: Virtual Threads (Day 4)

### Find Thread Creation

```bash
grep -rn "new Thread(" src/
grep -rn "\.start()" src/
grep -rn "Executors.newFixed" src/
grep -rn "Executors.newCached" src/
```

### Convert to Virtual Threads

**Example Before:**
```java
public class CustomAgent implements AbstractAutonomousAgent {
    private Thread discoveryThread;

    @Override
    public void start() {
        running.set(true);
        discoveryThread = new Thread(this::runDiscoveryLoop);
        discoveryThread.setName("custom-agent-discovery");
        discoveryThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        discoveryThread.interrupt();
    }
}
```

**Example After:**
```java
public class CustomAgent implements AbstractAutonomousAgent {
    private Thread discoveryThread;

    @Override
    public void start() {
        running.set(true);
        discoveryThread = Thread.ofVirtual()
            .name("custom-agent-discovery")
            .start(this::runDiscoveryLoop);  // Virtual thread (~1KB instead of 2MB)
    }

    @Override
    public void stop() {
        running.set(false);
        // Virtual thread terminates naturally when loop exits
    }
}
```

### Check for Synchronized Blocks

- [ ] Find synchronized methods:
  ```bash
  grep -rn "public synchronized\|private synchronized" src/
  ```

- [ ] Audit each one:
  - [ ] Is I/O happening while holding lock?
  - [ ] If yes, replace with ReentrantLock

**Example:**
```java
// BAD: Synchronized with DB call
public synchronized void saveItem(YWorkItem item) {
    db.save(item);  // 100ms while holding lock
}

// GOOD: Lock for critical section only
private final ReentrantLock lock = new ReentrantLock();

public void saveItem(YWorkItem item) {
    lock.lock();
    try {
        item.validate();
        registry.put(item.getId(), item);
    } finally {
        lock.unlock();
    }
    db.save(item);  // Outside lock - carrier can handle other work
}
```

**Checklist:**
- [ ] New Thread() → Thread.ofVirtual()
- [ ] synchronized blocks reviewed
- [ ] I/O under locks moved outside
- [ ] Tests pass with virtual threads
- [ ] No pinning detected: `-Djdk.tracePinnedThreads=short`

---

## Phase 6: Testing & Validation (Day 4-5)

### Build Verification

```bash
# Clean build with Java 25
mvn clean verify

# Check for deprecated API usage
jdeprscan --for-removal target/extension.jar

# Check for YAWL compatibility
mvn dependency:tree | grep yawl

# Run with Java 25 flag
mvn -Dorg.slf4j.simpleLogger.defaultLogLevel=debug clean verify
```

- [ ] mvn clean verify succeeds
- [ ] No deprecated API warnings
- [ ] No compilation errors
- [ ] All unit tests pass

### Runtime Testing

```bash
# Start YAWL with extension
java -XX:+UseCompactObjectHeaders \
     -Djdk.tracePinnedThreads=short \
     -jar yawl-engine.jar

# Run extension tests
curl -X POST http://localhost:8080/extension-test
```

- [ ] Extension loads without errors
- [ ] No pinning warnings
- [ ] Custom code executes correctly
- [ ] Virtual thread context inheritance works

### Performance Baseline

- [ ] Measure before Java 25 migration
- [ ] Measure after Java 25 migration
- [ ] Expected improvement: 5-10% throughput

```bash
# JFR recording
jcmd <pid> JFR.start settings=profile filename=recording.jfr duration=60s

# Analyze
jfr dump --json recording.jfr > metrics.json
```

---

## Phase 7: Documentation (Day 5)

- [ ] Update README with Java 25 requirement:
  ```markdown
  ## Requirements
  - Java 25 or later
  - YAWL v6.0.0+
  ```

- [ ] Document new sealed interfaces/classes:
  ```markdown
  ## Sealed Class Hierarchies

  `TaskHandler` is a sealed interface...
  Permitted implementations: CustomTaskHandler, LegacyTaskHandler
  ```

- [ ] Document ScopedValue usage:
  ```markdown
  ## Context Management

  This extension uses ScopedValue for workflow context...
  See ADR-030 for details.
  ```

- [ ] Document virtual thread compatibility:
  ```markdown
  ## Virtual Thread Support

  This extension is fully compatible with Java 25 virtual threads.
  All blocking operations properly yield to carrier threads.
  ```

---

## Phase 8: Release & Deployment (Post-Checklist)

- [ ] Bump version number
- [ ] Update pom.xml with Java 25 requirement
- [ ] Tag release in git
- [ ] Build and publish to Maven repository
- [ ] Update extension documentation
- [ ] Announce Java 25 compatibility

---

## Common Issues & Solutions

### Issue: NoSuchElementException on ScopedValue.get()

**Cause**: Accessing ScopedValue outside its scope.

**Solution**:
```java
// Wrap in try-catch
try {
    String value = MY_VALUE.get();
} catch (NoSuchElementException e) {
    // Provide default or re-throw
    throw new YDataStateException("Context not set");
}

// Or check before access
if (MY_VALUE.isBound()) {
    String value = MY_VALUE.get();
}
```

### Issue: Virtual Thread Pinning

**Symptom**: "Virtual thread pinned for X ms" in logs.

**Solution**:
```bash
# Enable detection
-Djdk.tracePinnedThreads=short

# Find pinning culprits (usually synchronized blocks)
grep -rn "synchronized" src/

# Replace with ReentrantLock
private final ReentrantLock lock = new ReentrantLock();
```

### Issue: Sealed Interface Implementation Rejected

**Symptom**: "class X is not allowed to extend sealed class Y"

**Solution**: Add implementation to permits clause:
```java
public sealed interface TaskHandler
    permits CustomTaskHandler, LegacyTaskHandler, YourNewHandler {  // Add here
}
```

### Issue: Pattern Matching Incomplete

**Symptom**: Compiler error "switch expression does not cover all possible input values"

**Solution**: Cover all sealed types:
```java
String result = handler switch {
    CustomTaskHandler h -> "custom",
    LegacyTaskHandler h -> "legacy",
    YourNewHandler h -> "new",
    // All cases covered!
};
```

---

## Verification Checklist

Before considering migration complete:

- [ ] mvn clean verify succeeds
- [ ] No compiler warnings
- [ ] No deprecated API usage
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No virtual thread pinning warnings
- [ ] Documentation updated
- [ ] Performance baseline measured
- [ ] Code reviewed by 1+ team member
- [ ] Ready for production release

---

## Support & Questions

- **Official Docs**: Java 25 Upgrade Guide, ADRs 026-030
- **Developer Guide**: DEVELOPER_GUIDE_JAVA25.md
- **Performance Tuning**: PERFORMANCE_TUNING_JAVA25.md
- **YAWL Team**: Contact YAWL architecture team

---

**Estimated Total Time**: 2-5 days depending on extension complexity.

**Questions?** Refer to DEVELOPER_GUIDE_JAVA25.md or related ADRs.
