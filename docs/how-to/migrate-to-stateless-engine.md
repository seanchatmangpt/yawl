# How to Migrate from Stateful to Stateless YAWL Engine

## Problem Statement

You have an existing YAWL deployment using the stateful `YEngine` with Hibernate persistence and a relational database (PostgreSQL, MySQL, or Oracle). You want to migrate to the stateless `YStatelessEngine` to:

- Reduce infrastructure complexity (no database required)
- Enable horizontal scaling in Kubernetes
- Support cloud-native deployment patterns
- Achieve faster startup times for serverless scenarios

This guide explains the architectural differences, migration strategy, and step-by-step process for transitioning from stateful to stateless engine operation.

## Prerequisites

- Existing YAWL deployment using `YEngine` (stateful mode)
- Understanding of your current persistence requirements (crash recovery vs. graceful shutdown)
- Java 25 runtime environment
- External state store (Redis, S3, database, or filesystem) for case persistence
- Monitoring infrastructure to detect idle cases

## Architectural Differences

### Stateful Engine (YEngine)

| Characteristic | Behavior |
|---|---|
| Persistence | Hibernate to relational database |
| Crash Recovery | Full transaction rollback and replay |
| Scaling | Vertical only (single instance) |
| Startup Time | 30-60 seconds (database connection, schema validation) |
| Dependencies | JDBC driver, Hibernate, connection pool, database |

### Stateless Engine (YStatelessEngine)

| Characteristic | Behavior |
|---|---|
| Persistence | External via case export/import (XML) |
| Crash Recovery | Best-effort (persist on idle timeout or shutdown) |
| Scaling | Horizontal (multiple instances, external state) |
| Startup Time | 1-3 seconds (no database initialization) |
| Dependencies | None (pure in-memory) |

### When to Choose Stateless

**Choose stateless when:**
- Deploying in Kubernetes with horizontal pod autoscaling
- Running in serverless or FaaS environments
- Workflows are short-lived (minutes to hours)
- You can tolerate loss of in-flight work items on crash
- You have an external state store (Redis, S3, database)

**Stay with stateful when:**
- Regulatory requirements mandate ACID transaction guarantees
- Workflows run for days or weeks without interruption
- Zero data loss on crash is required
- You need complex queries over historical case data

## Migration Strategy

### Phase 1: Assess Current Usage

1. **Inventory all Interface B/Interface A calls** in your application code
2. **Identify session management patterns** — stateful engine uses session handles; stateless may use external auth
3. **List all specifications** currently loaded in the stateful engine
4. **Document case duration patterns** — average, maximum, distribution

### Phase 2: Dual-Run Period

Run both engines in parallel during migration:

1. Deploy `YStatelessEngine` alongside existing `YEngine`
2. New cases launch on the stateless engine
3. Existing cases continue on the stateful engine until completion
4. Monitor both engines during transition

### Phase 3: Cutover

Once all stateful cases complete:

1. Decommission the stateful engine
2. Remove database dependencies
3. Update application configuration to use stateless engine exclusively

## Step-by-Step Migration

### Step 1: Add Case Persistence Layer

Create a persistence adapter that works with your external state store:

```java
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.exceptions.YStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Persistence listener that exports idle cases to durable storage.
 *
 * <p>Responds to CASE_IDLE_TIMEOUT by calling unloadCase() to serialize
 * the case and write it to the configured store. On application restart,
 * all persisted cases are restored via restoreCase().
 */
public class CasePersistenceListener implements YCaseEventListener {

    private final YStatelessEngine engine;
    private final CaseStore store;

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
            default -> { }
        }
    }

    private void persistAndUnload(YCaseEvent event) {
        try {
            String caseXml = engine.unloadCase(event.getCaseID());
            store.save(event.getCaseID().toString(), caseXml);
        } catch (YStateException e) {
            if (!e.getMessage().contains("Unknown case")) {
                throw new RuntimeException("Unexpected unload failure", e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Persistence failure", e);
        }
    }
}

/**
 * Contract for case persistence. Implement with Redis, S3, JDBC, or filesystem.
 */
public interface CaseStore {

    void save(String caseId, String caseXml) throws IOException;

    String load(String caseId) throws IOException;

    void delete(String caseId);

    List<String> listAll() throws IOException;
}

/**
 * Filesystem-based implementation for development or simple deployments.
 */
public class FilesystemCaseStore implements CaseStore {

    private final Path storeDir;

    public FilesystemCaseStore(Path storeDir) throws IOException {
        this.storeDir = storeDir;
        Files.createDirectories(storeDir);
    }

    @Override
    public void save(String caseId, String caseXml) throws IOException {
        Files.writeString(
            storeDir.resolve(caseId + ".xml"),
            caseXml,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
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

### Step 2: Replace Engine Instantiation

**Before (Stateful):**

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YPersistenceManager;

// Stateful engine with database persistence
YPersistenceManager pmgr = new YPersistenceManager();
pmgr.configure("hibernate.cfg.xml");
YEngine engine = YEngine.getInstance(pmgr);
```

**After (Stateless):**

```java
import org.yawlfoundation.yawl.stateless.YStatelessEngine;

// Stateless engine with 60-second idle timeout
YStatelessEngine engine = new YStatelessEngine(60_000L);

// Attach persistence listener
CaseStore store = new FilesystemCaseStore(Path.of("/var/yawl/cases"));
CasePersistenceListener listener = new CasePersistenceListener(engine, store);
```

### Step 3: Migrate Specification Loading

The specification loading API is nearly identical:

**Before:**

```java
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.elements.YSpecification;

YSpecification spec = YMarshal.unmarshalSpecifications(specXml).get(0);
engine.loadSpecification(spec);
```

**After:**

```java
import org.yawlfoundation.yawl.stateless.elements.YSpecification;

YSpecification spec = engine.unmarshalSpecification(specXml);
// Specification is ready for case launch; no separate load step required
```

### Step 4: Migrate Case Launch

**Before:**

```java
String caseID = engine.launchCase(specID, caseParams, sessionHandle);
```

**After:**

```java
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

YNetRunner runner = engine.launchCase(spec, caseID, caseParams);
String caseID = runner.getCaseID().toString();
```

The stateless API returns a `YNetRunner` reference directly, giving you immediate access to enabled work items without a separate query.

### Step 5: Migrate Work Item Operations

Work item operations are similar but use the `YNetRunner` context:

**Before:**

```java
engine.checkOutWorkItem(workItemID, sessionHandle);
engine.checkInWorkItem(workItemID, outputData, sessionHandle);
```

**After:**

```java
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

YWorkItem item = runner.getWorkItem(workItemID);
engine.startWorkItem(item);
engine.completeWorkItem(item, outputData, null);
```

### Step 6: Implement Graceful Shutdown

Persist all active cases before shutting down:

```java
import org.yawlfoundation.yawl.stateless.monitor.YCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GracefulShutdownHandler {

    private static final Logger logger = LogManager.getLogger();

    private final YStatelessEngine engine;
    private final CaseStore store;

    public GracefulShutdownHandler(YStatelessEngine engine, CaseStore store) {
        this.engine = engine;
        this.store = store;
    }

    public void shutdown() {
        if (engine.isCaseMonitoringEnabled()) {
            for (YCase yCase : engine.getCaseMonitor().getAllCases()) {
                try {
                    String caseXml = engine.marshalCase(yCase.getRunner());
                    store.save(yCase.getRunner().getCaseID().toString(), caseXml);
                } catch (Exception e) {
                    logger.error("Failed to marshal case during shutdown", e);
                }
            }
        }
    }
}
```

Register as a JVM shutdown hook:

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    shutdownHandler.shutdown();
}));
```

### Step 7: Restore Cases on Startup

```java
public void restoreAllCases(YStatelessEngine engine, CaseStore store) {
    for (String caseId : store.listAll()) {
        try {
            String caseXml = store.load(caseId);
            YNetRunner runner = engine.restoreCase(caseXml);
            logger.info("Restored case: {}", runner.getCaseID());
        } catch (Exception e) {
            logger.error("Failed to restore case {}", caseId, e);
        }
    }
}
```

## Configuration Examples

### Spring Boot Integration

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;

@Configuration
public class YawlEngineConfig {

    @Bean
    public YStatelessEngine yawlEngine(CaseStore caseStore) {
        // 5-minute idle timeout before persisting
        YStatelessEngine engine = new YStatelessEngine(300_000L);

        // Enable multi-threaded event announcements for throughput
        engine.enableMultiThreadedAnnouncements(true);

        // Attach persistence listener
        new CasePersistenceListener(engine, caseStore);

        // Restore persisted cases
        restoreCases(engine, caseStore);

        return engine;
    }

    @Bean
    public CaseStore caseStore() {
        return new FilesystemCaseStore(Path.of("/var/yawl/cases"));
    }

    private void restoreCases(YStatelessEngine engine, CaseStore store) {
        for (String caseId : store.listAll()) {
            try {
                String caseXml = store.load(caseId);
                engine.restoreCase(caseXml);
            } catch (Exception e) {
                // Log and continue; don't prevent startup
            }
        }
    }
}
```

### Kubernetes Deployment with Persistent Volume

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: yawl-cases-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-stateless
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: yawl-engine
          image: yawl/engine:6.0.0
          env:
            - name: YAWL_CASE_STORE_PATH
              value: /app/cases
            - name: YAWL_IDLE_TIMEOUT_MS
              value: "300000"
          volumeMounts:
            - name: cases
              mountPath: /app/cases
      volumes:
        - name: cases
          persistentVolumeClaim:
            claimName: yawl-cases-pvc
```

## Troubleshooting

### `YStateException: This engine is not monitoring idle cases`

**Cause:** `unloadCase()` requires case monitoring.

**Solution:** Enable monitoring with a positive timeout:
```java
engine.setIdleCaseTimer(60_000L);  // 60 seconds
```

### Restored case immediately idles again

**Cause:** Idle timeout is shorter than workflow progress time.

**Solution:** Increase the idle timeout or ensure `YWorkItemEventListener` handles `ITEM_ENABLED_REANNOUNCE`:
```java
@Override
public void handleWorkItemEvent(YWorkItemEvent event) {
    if (event.getEventType() == YEventType.ITEM_ENABLED_REANNOUNCE) {
        engine.startWorkItem(event.getWorkItem());
    }
}
```

### Timers not firing after restore

**Cause:** Idle timeout fires before timer completes.

**Solution:** Set idle timeout longer than the maximum timer duration in your specifications.

### `YSyntaxException` on restore

**Cause:** Specification version mismatch between export and import.

**Solution:** Ensure specification XML embedded in the case matches the version in the new engine. Maintain specification version compatibility across deployments.

## Related Documentation

- [Enable Stateless Persistence](/docs/how-to/enable-stateless-persistence.md) — Detailed persistence patterns
- [Deploy to Kubernetes](/docs/how-to/deploy-to-kubernetes.md) — Kubernetes deployment guide
- [Configure A2A Authentication](/docs/how-to/configure-a2a-authentication.md) — A2A server authentication
- `src/org/yawlfoundation/yawl/stateless/YStatelessEngine.java` — Engine API reference
- `src/org/yawlfoundation/yawl/stateless/monitor/YCaseExporter.java` — Export implementation
- `src/org/yawlfoundation/yawl/stateless/monitor/YCaseImporter.java` — Import implementation
