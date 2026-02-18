# How to Enable Stateless Engine Persistence

## Prerequisites

- `YStatelessEngine` in use (see ADR-001 for when to choose stateless over stateful)
- A persistent store accessible from your application (database, object store, or
  distributed cache — the engine exports plain XML, so any string-capable store works)
- Understanding of the case export/restore lifecycle: `unloadCase()` → store → load →
  `restoreCase()`

## When to Use This

The stateless engine (`YStatelessEngine`) keeps all case state in memory. When the JVM
exits, all running cases are lost. If you need crash-recovery semantics without the full
overhead of the stateful engine (`YEngine` + Hibernate + PostgreSQL), you can implement
lightweight persistence by:

1. Enabling case monitoring so the engine detects idle cases and emits
   `CASE_IDLE_TIMEOUT` events.
2. Responding to those events by calling `unloadCase()` to export case state as XML and
   writing it to durable storage.
3. On restart (or on a new engine instance), reading the XML back and calling
   `restoreCase()`.

This pattern gives you recovery after crash without a database, at the cost of
potentially losing in-progress work items (those that were not yet idle when the crash
occurred). For zero-loss guarantees, use the stateful engine.

## Steps

### 1. Enable Case Monitoring with an Idle Timeout

Pass an idle timeout (milliseconds) to the `YStatelessEngine` constructor. When no work
item activity is recorded for that duration, the engine fires a `CASE_IDLE_TIMEOUT`
event:

```java
// 30-second idle timeout — adjust to your workflow's expected think time
long idleTimeoutMs = 30_000L;
YStatelessEngine engine = new YStatelessEngine(idleTimeoutMs);
```

If you already have an engine instance, activate monitoring without recreating it:

```java
engine.setIdleCaseTimer(30_000L);  // enable or update the idle timeout
```

To disable idle monitoring without discarding the engine:

```java
engine.setIdleCaseTimer(-1L);  // non-positive value disables monitoring
```

Case monitoring also tracks the total set of active cases, which you need to enumerate
during a graceful shutdown (see step 4).

### 2. Implement the Persistence Listener

Register a `YCaseEventListener` that responds to `CASE_IDLE_TIMEOUT` and `CASE_UNLOADED`
events by writing the marshalled case XML to durable storage:

```java
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.exceptions.YStateException;

public class CasePersistenceListener implements YCaseEventListener {

    private final YStatelessEngine engine;
    private final CaseStore store;  // your persistence adapter

    public CasePersistenceListener(YStatelessEngine engine, CaseStore store) {
        this.engine = engine;
        this.store = store;
        engine.addCaseEventListener(this);
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        switch (event.getEventType()) {
            case CASE_IDLE_TIMEOUT -> persistAndUnload(event);
            case CASE_COMPLETED, CASE_CANCELLED -> store.delete(event.getCaseID().toString());
            default -> { /* no action needed for other events */ }
        }
    }

    private void persistAndUnload(YCaseEvent event) {
        try {
            // unloadCase() both serialises the case AND removes it from the engine
            String caseXml = engine.unloadCase(event.getCaseID());
            store.save(event.getCaseID().toString(), caseXml);
        } catch (YStateException e) {
            // Case was already completed or cancelled between the timeout firing
            // and this handler running — not an error condition.
            if (!e.getMessage().contains("Unknown case")) {
                throw new RuntimeException("Unexpected unload failure", e);
            }
        }
    }
}
```

`CaseStore` is a contract you implement. A minimal file-system version:

```java
public class FilesystemCaseStore implements CaseStore {

    private final Path storeDir;

    public FilesystemCaseStore(Path storeDir) throws IOException {
        this.storeDir = storeDir;
        Files.createDirectories(storeDir);
    }

    @Override
    public void save(String caseId, String caseXml) throws IOException {
        Files.writeString(storeDir.resolve(caseId + ".xml"), caseXml,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public String load(String caseId) throws IOException {
        return Files.readString(storeDir.resolve(caseId + ".xml"));
    }

    @Override
    public void delete(String caseId) {
        storeDir.resolve(caseId + ".xml").toFile().delete();
    }

    @Override
    public List<String> listAll() throws IOException {
        try (Stream<Path> paths = Files.list(storeDir)) {
            return paths
                .filter(p -> p.toString().endsWith(".xml"))
                .map(p -> {
                    String name = p.getFileName().toString();
                    return name.substring(0, name.length() - 4);
                })
                .collect(Collectors.toList());
        }
    }
}
```

For production, replace `FilesystemCaseStore` with a JDBC store, Redis, or S3 depending
on your durability requirements.

### 3. Restore Cases on Startup

When the application starts (or a new engine instance is created after a crash), load
all previously persisted cases back into the engine:

```java
public void restoreAllCases(YStatelessEngine engine, CaseStore store) {
    for (String caseId : store.listAll()) {
        try {
            String caseXml = store.load(caseId);
            YNetRunner runner = engine.restoreCase(caseXml);
            // restoreCase fires CASE_RESTORED and re-announces all enabled work items
            // so your work item handler will receive ITEM_ENABLED_REANNOUNCE events
        } catch (Exception e) {
            // Log and skip — a corrupt or incompatible case XML should not
            // prevent other cases from being restored
            LogManager.getLogger(getClass())
                .error("Failed to restore case {}", caseId, e);
        }
    }
}
```

`restoreCase()` internally calls `YCaseImporter.unmarshal()` which rebuilds the full
net runner hierarchy from the XML, including any pending timers which are restarted
automatically.

### 4. Persist All Active Cases on Graceful Shutdown

During a planned shutdown, call `marshalCase()` on every active case to save state
without removing it from the engine (unlike `unloadCase()` which both saves and removes):

```java
public void gracefulShutdown(YStatelessEngine engine, CaseStore store) {
    // marshalCase serialises without unloading; safe to call while work items are active
    if (engine.isCaseMonitoringEnabled()) {
        for (YCase yCase : engine.getCaseMonitor().getAllCases()) {
            try {
                String caseXml = engine.marshalCase(yCase.getRunner());
                store.save(yCase.getRunner().getCaseID().toString(), caseXml);
            } catch (YStateException e) {
                LogManager.getLogger(getClass())
                    .error("Failed to marshal case during shutdown", e);
            }
        }
    }
    // Now safely stop the engine / JVM
}
```

Register this as a JVM shutdown hook or Spring `@PreDestroy` method.

### 5. Configure Multi-Engine Handoff (optional)

The stateless engine's persistence model also supports moving a case between separate
engine instances (e.g. load-balancing). `YSRestoreTest` in the source tree shows the
canonical pattern:

```java
// Engine 1: case becomes idle
String caseXml = engine1.unloadCase(event.getCaseID());

// Engine 2: restore and continue
YNetRunner runner = engine2.restoreCase(caseXml);
// engine2 will now fire ITEM_ENABLED_REANNOUNCE for all outstanding work items
```

This is the same mechanism as crash recovery — the XML is the complete, self-contained
case state.

## Verify

**Confirm case monitoring is active:**

```java
assert engine.isCaseMonitoringEnabled() : "Case monitoring must be enabled for persistence";
```

**Confirm a case round-trips through XML:**

```java
YNetRunner runner = engine.launchCase(spec, "test-case-1", null);

// Simulate idle timeout by manually marshalling
String caseXml = engine.marshalCase(runner);
assert caseXml != null && caseXml.contains("test-case-1") : "Marshalled XML must contain caseID";

// Restore to a second engine
YStatelessEngine engine2 = new YStatelessEngine(30_000L);
YNetRunner restored = engine2.restoreCase(caseXml);
assert restored != null : "Restore must return a non-null runner";
assert restored.getCaseID().toString().equals("test-case-1") : "CaseID must be preserved";
```

**Confirm ITEM_ENABLED_REANNOUNCE fires on restore:**

Add a `YWorkItemEventListener` to `engine2` before calling `restoreCase()`. Verify that
`event.getEventType() == YEventType.ITEM_ENABLED_REANNOUNCE` is received for each work
item that was enabled at the time of `unloadCase()`.

## Troubleshooting

**`YStateException: This engine is not monitoring idle cases`**
`unloadCase()` requires case monitoring to be enabled. Call `setIdleCaseTimer(msecs)`
or pass a positive value to the `YStatelessEngine(long)` constructor before calling
`unloadCase()`.

**Restored case immediately idles again without progressing**
`restoreCase()` re-announces work items but does not automatically start them. Your
`YWorkItemEventListener` must handle `ITEM_ENABLED_REANNOUNCE` the same way it handles
`ITEM_ENABLED` — by calling `engine.startWorkItem(item)`.

**Timers not firing after restore**
Timers are cancelled when a case is unloaded and restarted when it is restored. If
the idle timeout on the new engine is shorter than the remaining timer duration at
unload time, the case may idle-out again before the workflow progresses. Set the idle
timeout to be longer than the longest expected timer in the specification.

**Case XML rejected on restore with `YSyntaxException`**
The specification embedded in the case XML must match the version registered with the
new engine. If you have upgraded the specification between unload and restore, the
parser will reject the case. Maintain specification version compatibility across
deployments that share persisted case state.
