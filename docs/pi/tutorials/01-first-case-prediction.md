# Tutorial 1 — Your First Case Outcome Prediction

In this tutorial you will:
1. Set up an in-memory event store with a few test events
2. Build a `ProcessIntelligenceFacade` with all required dependencies
3. Call `predictOutcome()` on a case
4. Inspect the `CaseOutcomePrediction` result

Time: ~10 minutes. No Python or ONNX models needed — the DNA oracle fallback is used.

---

## Prerequisites

Add `yawl-pi` to your Maven project:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-pi</artifactId>
    <version>6.0.0-GA</version>
</dependency>
<!-- H2 for in-memory testing -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <version>2.2.224</version>
</dependency>
```

---

## Step 1: Create an event store and add case events

```java
import org.h2.jdbcx.JdbcDataSource;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import java.util.Map;

// Create H2 in-memory data source
JdbcDataSource ds = new JdbcDataSource();
ds.setURL("jdbc:h2:mem:pi-tutorial;MODE=MySQL;DB_CLOSE_DELAY=-1");
ds.setUser("sa");
ds.setPassword("");

// WorkflowEventStore auto-creates the schema on first use
WorkflowEventStore eventStore = new WorkflowEventStore(ds);

// Simulate a case with 4 events
String specId = "loan-application/1.0";
String caseId = "case-001";

eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.CASE_STARTED, specId, caseId, "start", Map.of()));

eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.WORKITEM_ENABLED, specId, caseId, "credit-check", Map.of()));

eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.WORKITEM_STARTED, specId, caseId, "credit-check", Map.of()));

eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.WORKITEM_COMPLETED, specId, caseId, "credit-check",
    Map.of("decision", "approved")));
```

You have a case with one completed work item and no cancellations — a healthy case.

---

## Step 2: Build the engines

```java
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.observatory.spec.XesToYawlSpecGenerator;
import org.yawlfoundation.yawl.pi.predictive.*;
import org.yawlfoundation.yawl.pi.prescriptive.*;
import org.yawlfoundation.yawl.pi.optimization.*;
import org.yawlfoundation.yawl.pi.rag.*;
import java.nio.file.Files;
import java.nio.file.Path;

// DNA oracle — fallback predictor when no ONNX model is registered
WorkflowDNAOracle oracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));

// Model registry — empty for now (will use oracle fallback)
Path modelDir = Files.createTempDirectory("pi-models");
PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir);

// Build each engine
CaseOutcomePredictor predictor = new CaseOutcomePredictor(eventStore, oracle, registry);
PrescriptiveEngine   prescriptive = new PrescriptiveEngine(oracle);
ResourceOptimizer    optimizer = new ResourceOptimizer();
ProcessKnowledgeBase kb = new ProcessKnowledgeBase();
NaturalLanguageQueryEngine nlEngine = new NaturalLanguageQueryEngine(kb, null);
```

---

## Step 3: Create the facade

```java
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;

ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
    predictor, prescriptive, optimizer, nlEngine);
```

---

## Step 4: Predict the outcome

```java
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;

try {
    CaseOutcomePrediction prediction = pi.predictOutcome("case-001");

    System.out.printf("Completion probability: %.1f%%%n",
        prediction.completionProbability() * 100);
    System.out.printf("Risk score:             %.2f%n",
        prediction.riskScore());
    System.out.println("Primary risk factor:   " + prediction.primaryRiskFactor());
    System.out.println("From ONNX model:       " + prediction.fromOnnxModel());
    System.out.println("Predicted at:          " + prediction.predictedAt());

} catch (PIException e) {
    System.err.println("Prediction failed on connection: " + e.getConnection());
    System.err.println("Reason: " + e.getMessage());
}
```

Expected output (values depend on DNA oracle heuristics):
```
Completion probability: 82.0%
Risk score:             0.18
Primary risk factor:   low cancellation rate
From ONNX model:       false
Predicted at:          2026-02-27T14:00:00Z
```

`fromOnnxModel=false` confirms the DNA oracle fallback is in use. Once you train
an ONNX model (Tutorial 2), the same call returns `fromOnnxModel=true` with
higher accuracy.

---

## Step 5: Get recommendations

```java
import org.yawlfoundation.yawl.pi.prescriptive.ProcessAction;
import org.yawlfoundation.yawl.pi.prescriptive.NoOpAction;

List<ProcessAction> actions = pi.recommendActions("case-001", prediction);

System.out.println("Top recommendation: " + actions.get(0).getClass().getSimpleName());
System.out.println("Rationale: " + actions.get(0).rationale());

if (actions.get(0) instanceof NoOpAction) {
    System.out.println("Case is healthy — no intervention needed.");
}
```

---

## What you built

- An H2-backed `WorkflowEventStore` with real events
- A full `ProcessIntelligenceFacade` wiring all 5 engines
- Your first successful `predictOutcome()` call
- A `recommendActions()` call showing the prescriptive engine

## Next steps

- [Tutorial 2](02-train-automl-model.md) — Train an ONNX model with TPOT2 so predictions
  are based on historical patterns, not heuristics
- [How to configure the PI facade](../how-to/configure-pi-facade.md) — Production DataSource
  and ZaiService wiring
