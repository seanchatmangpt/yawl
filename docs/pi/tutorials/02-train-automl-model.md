# Tutorial 2 — Train an AutoML Model with TPOT2

In this tutorial you will:
1. Populate an event store with historical case data
2. Configure TPOT2 for a quick training run
3. Run `ProcessMiningAutoMl.autoTrainCaseOutcome()`
4. Verify the trained ONNX model is registered
5. Make predictions using the ONNX model instead of the fallback

Time: ~20 minutes. Requires Python 3.9+ with tpot2, skl2onnx, scikit-learn, numpy, pandas.

---

## Prerequisites

Complete [Tutorial 1](01-first-case-prediction.md) first, then verify your Python environment:

```bash
python3 -c "import tpot2, skl2onnx, sklearn, numpy, pandas; print('OK')"
```

If this fails, see [Set up the TPOT2 Python environment](../how-to/setup-tpot2-python.md).

---

## Step 1: Populate historical cases

For TPOT2 to learn meaningful patterns, you need at least 20–50 cases.
Here we generate 60 synthetic cases: 40 completed, 20 failed.

```java
import org.h2.jdbcx.JdbcDataSource;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import java.util.*;

JdbcDataSource ds = new JdbcDataSource();
ds.setURL("jdbc:h2:mem:automl-tutorial;MODE=MySQL;DB_CLOSE_DELAY=-1");
ds.setUser("sa");
ds.setPassword("");
WorkflowEventStore eventStore = new WorkflowEventStore(ds);

YSpecificationID specId = new YSpecificationID("loan-application", "1.0",
    "http://yawl.example.com/loan");
Random rand = new Random(42);

for (int i = 0; i < 60; i++) {
    String caseId = "case-" + String.format("%03d", i);
    boolean willFail = (i >= 40);  // cases 0-39 complete, 40-59 fail

    eventStore.appendNext(new WorkflowEvent(
        WorkflowEvent.EventType.CASE_STARTED,
        specId.getIdentifier(), caseId, "start", Map.of()));

    // Healthy cases: short wait; failing cases: long wait
    int waitMs = willFail
        ? 5000 + rand.nextInt(10000)   // 5–15 seconds wait (at risk)
        : 100 + rand.nextInt(500);     // 100–600 ms wait (healthy)

    eventStore.appendNext(new WorkflowEvent(
        WorkflowEvent.EventType.WORKITEM_ENABLED,
        specId.getIdentifier(), caseId, "credit-check", Map.of()));

    // Simulate the wait by using multiple events with distinct IDs
    eventStore.appendNext(new WorkflowEvent(
        WorkflowEvent.EventType.WORKITEM_STARTED,
        specId.getIdentifier(), caseId, "credit-check", Map.of()));

    if (willFail) {
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_CANCELLED,
            specId.getIdentifier(), caseId, "credit-check", Map.of()));
    } else {
        eventStore.appendNext(new WorkflowEvent(
            WorkflowEvent.EventType.CASE_COMPLETED,
            specId.getIdentifier(), caseId, "credit-check", Map.of()));
    }
}

System.out.println("Inserted 60 historical cases into event store.");
```

---

## Step 2: Extract training data

```java
import org.yawlfoundation.yawl.pi.predictive.ProcessMiningTrainingDataExtractor;
import org.yawlfoundation.yawl.pi.predictive.TrainingDataset;

ProcessMiningTrainingDataExtractor extractor =
    new ProcessMiningTrainingDataExtractor(eventStore);

TrainingDataset dataset = extractor.extractTabular(specId, 100);

System.out.println("Cases extracted: " + dataset.caseCount());
System.out.println("Feature names:   " + dataset.featureNames());
System.out.println("Labels sample:   " + dataset.labels().subList(0, 5));
```

Expected output:
```
Cases extracted: 60
Feature names:   [caseDurationMs, taskCount, distinctWorkItems, hadCancellations, avgTaskWaitMs]
Labels sample:   [completed, completed, completed, completed, completed]
```

---

## Step 3: Configure TPOT2 for a fast training run

For production, use `Tpot2Config.forCaseOutcome()` (5 generations, 50 population, 60 min).
For this tutorial, use a minimal config that completes in under 2 minutes:

```java
import org.yawlfoundation.yawl.pi.automl.*;

Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    2,          // generations — quick search
    10,         // populationSize — small for speed
    5,          // maxTimeMins
    2,          // cvFolds — 2-fold CV (fast; use 5 in production)
    "roc_auc",  // scoring
    1,          // nJobs — 1 CPU (prevents interference in testing)
    "python3"   // or your full Python path
);
```

---

## Step 4: Run AutoML

```java
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;
import java.nio.file.Files;
import java.nio.file.Path;

Path modelDir = Files.createTempDirectory("automl-output");
PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir);

System.out.println("Starting TPOT2 AutoML search...");
long start = System.currentTimeMillis();

Tpot2Result result = ProcessMiningAutoMl.autoTrainCaseOutcome(
    specId, eventStore, registry, config);

System.out.printf("Training complete in %.1f seconds%n",
    (System.currentTimeMillis() - start) / 1000.0);
System.out.println("Best ROC-AUC: " + result.modelMetrics().get("bestScore"));
System.out.println("Pipeline: "    + result.pipelineJson());
```

Expected output:
```
Starting TPOT2 AutoML search...
Training complete in 87.3 seconds
Best ROC-AUC: 0.92
Pipeline: {"estimator": "GradientBoostingClassifier", "params": {...}}
```

---

## Step 5: Verify the model is registered

```java
System.out.println("Model available: " + registry.isAvailable("case_outcome"));
```

```
Model available: true
```

The model file is written to `modelDir/loan-application_1.0_case_outcome.onnx`.

---

## Step 6: Predict using the ONNX model

Rebuild the predictor with the now-populated registry:

```java
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.observatory.spec.XesToYawlSpecGenerator;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePredictor;
import org.yawlfoundation.yawl.pi.PIException;

WorkflowDNAOracle oracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));
CaseOutcomePredictor predictor = new CaseOutcomePredictor(eventStore, oracle, registry);

// Add a new case to predict
eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.CASE_STARTED,
    specId.getIdentifier(), "case-new", "start", Map.of()));
eventStore.appendNext(new WorkflowEvent(
    WorkflowEvent.EventType.WORKITEM_ENABLED,
    specId.getIdentifier(), "case-new", "credit-check", Map.of()));

var prediction = predictor.predict("case-new");

System.out.println("From ONNX model:       " + prediction.fromOnnxModel());  // true
System.out.printf("Completion probability: %.1f%%%n",
    prediction.completionProbability() * 100);
System.out.printf("Risk score:             %.2f%n", prediction.riskScore());
```

`fromOnnxModel=true` confirms TPOT2's trained model is in use.

---

## What you built

- 60 historical cases in the event store
- A `TrainingDataset` with 5 features per case
- A TPOT2 AutoML run that found the best sklearn pipeline
- An ONNX model registered in `PredictiveModelRegistry`
- A case prediction backed by real ML inference

## Next steps

- [Tutorial 3](03-realtime-adaptive.md) — Wire the trained model into real-time engine callbacks
- [How to register an ONNX model](../how-to/register-onnx-model.md) — Load externally trained models
- [Configuration reference — Tpot2Config](../reference/config.md) — All TPOT2 parameters
