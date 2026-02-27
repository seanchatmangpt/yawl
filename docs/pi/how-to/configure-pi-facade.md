# How to Configure the PI Facade

This guide shows how to wire `PIFacadeConfig` and construct `ProcessIntelligenceFacade`
with all required and optional dependencies.

## What you need

- A JDBC `DataSource` (H2 for testing; PostgreSQL / MySQL for production)
- A model directory (can be empty initially)
- YAWL engine dependencies on the classpath (`yawl-engine`, `yawl-integration`,
  `yawl-observatory`)

---

## Minimum configuration (no LLM, no ONNX models yet)

```java
import org.h2.jdbcx.JdbcDataSource;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.observatory.spec.XesToYawlSpecGenerator;
import org.yawlfoundation.yawl.pi.*;
import org.yawlfoundation.yawl.pi.predictive.*;
import org.yawlfoundation.yawl.pi.prescriptive.*;
import org.yawlfoundation.yawl.pi.optimization.*;
import org.yawlfoundation.yawl.pi.rag.*;
import java.nio.file.Files;
import java.nio.file.Path;

// 1. Data source
JdbcDataSource ds = new JdbcDataSource();
ds.setURL("jdbc:h2:mem:pi-demo;MODE=MySQL;DB_CLOSE_DELAY=-1");
ds.setUser("sa");
ds.setPassword("");

// 2. Create schema + event store
WorkflowEventStore eventStore = new WorkflowEventStore(ds);

// 3. DNA oracle (fallback predictor)
WorkflowDNAOracle dnaOracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));

// 4. Model directory (empty — will use oracle fallback until ONNX models are trained)
Path modelDir = Files.createTempDirectory("pi-models");
PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir);

// 5. Assemble config
PIFacadeConfig config = new PIFacadeConfig(
    eventStore,
    dnaOracle,
    registry,
    null,       // zaiService — null means no LLM; RAG will return raw facts
    modelDir
);

// 6. Build individual engines from config
CaseOutcomePredictor predictor = new CaseOutcomePredictor(
    config.eventStore(), config.dnaOracle(), config.modelRegistry());

PrescriptiveEngine prescriptive = new PrescriptiveEngine(config.dnaOracle());

ResourceOptimizer optimizer = new ResourceOptimizer();

ProcessKnowledgeBase kb = new ProcessKnowledgeBase();
NaturalLanguageQueryEngine nlEngine = new NaturalLanguageQueryEngine(kb, config.zaiService());

// 7. Facade
ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
    predictor, prescriptive, optimizer, nlEngine);
```

The facade is now ready. With no ONNX models registered, `predictOutcome()` falls back
to `WorkflowDNAOracle`. Once you train and register models, it uses ONNX automatically.

---

## With Z.AI LLM (RAG answers from GLM-4)

If `ZaiService` is available on your deployment:

```java
import org.yawlfoundation.yawl.integration.zai.ZaiService;

ZaiService zai = new ZaiService(
    "https://api.z.ai/v1",
    System.getenv("ZAI_API_KEY")
);

PIFacadeConfig config = new PIFacadeConfig(
    eventStore, dnaOracle, registry,
    zai,        // ← pass ZaiService here
    modelDir
);
```

Natural language questions via `facade.ask()` will now generate LLM-grounded answers.
Without `ZaiService`, the same call still works but returns raw retrieved facts.

---

## With pre-trained ONNX models

If you have an existing ONNX model file:

```java
Path modelDir = Path.of("/opt/yawl/models");
PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir);

// Any .onnx file in modelDir is auto-loaded at construction time.
// Alternatively, register explicitly:
registry.register("case_outcome", Path.of("/opt/yawl/models/case_outcome.onnx"));
```

`CaseOutcomePredictor` will now use the ONNX model instead of the oracle.
`prediction.fromOnnxModel()` returns `true` in that case.

---

## Production datasource (HikariCP)

For production, use a connection pool:

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

HikariConfig hk = new HikariConfig();
hk.setJdbcUrl("jdbc:postgresql://localhost:5432/yawl");
hk.setUsername("yawl_user");
hk.setPassword(System.getenv("DB_PASSWORD"));
hk.setMaximumPoolSize(10);

DataSource ds = new HikariDataSource(hk);
WorkflowEventStore eventStore = new WorkflowEventStore(ds);
```

The `WorkflowEventStore` constructor runs schema initialization (creates the
`workflow_events` table if it does not exist) on first construction.

---

## Sharing a facade across threads

`ProcessIntelligenceFacade` uses an internal `ReentrantLock`. A single instance
is safe to share across threads:

```java
// Create once at application startup
ProcessIntelligenceFacade sharedFacade = new ProcessIntelligenceFacade(...);

// Use from multiple threads
ExecutorService pool = Executors.newFixedThreadPool(8);
for (String caseId : activeCases) {
    pool.submit(() -> {
        try {
            sharedFacade.predictOutcome(caseId);
        } catch (PIException e) { /* ... */ }
    });
}
```
