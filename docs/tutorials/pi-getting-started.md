# Tutorial: Process Intelligence Getting Started

Welcome to YAWL Process Intelligence (PI). By the end of this tutorial, you will have:
- Created your first predictive model for case outcomes
- Deployed a real-time prediction service within a workflow
- Made your first prescriptive recommendation
- Integrated PI with a running YAWL engine

This is a **learning-by-doing** guide. Every code snippet is runnable.

---

## What is Process Intelligence?

Process Intelligence connects five AI capabilities to your workflow engine:

1. **Predictive** — "Will this case complete on time?" (ONNX + DNA oracle)
2. **Prescriptive** — "What should we do now?" (automated recommendations)
3. **Optimization** — "Who should do this task?" (Hungarian algorithm for resources)
4. **RAG** — "Tell me about similar cases" (retrieval-augmented generation)
5. **Data Preparation** — "Convert this CSV to workflow events" (OCEL2 normalization)

You don't need all five. Start with **Predictive** and **Prescriptive**, the most impactful pair.

---

## Prerequisites

- YAWL 6.0.0 built from source (Tutorial 01 completed)
- Java 21+ (Java 25 recommended)
- Maven 3.9+
- Docker (for optional containerized deployment)

### Quick Check

```bash
# Verify YAWL is installed
mvn dependency:tree -pl yawl-pi | head -20

# Verify Java version
java -version  # Must be Java 21+

# Verify Maven
mvn --version
```

---

## Part 1: Set Up the PI Module

### Step 1: Understand the PI Architecture

The PI module is organized into subpackages:

```
yawl-pi/
├── src/main/java/org/yawlfoundation/yawl/pi/
│   ├── predictive/           ← Case outcome prediction (ONNX)
│   ├── prescriptive/         ← Automated recommendations
│   ├── optimization/         ← Resource assignment
│   ├── rag/                  ← Natural language query engine
│   ├── automl/               ← TPOT2-based model training
│   ├── adaptive/             ← Realtime workflow adaptation
│   ├── bridge/               ← OCEL2 data normalization
│   ├── mcp/                  ← MCP tools for AI agents
│   └── ProcessIntelligenceFacade.java  ← Main entry point
```

The **ProcessIntelligenceFacade** is the single entry point for all five connections.

### Step 2: Add PI to Your Project's Dependencies

If you're building a custom application that uses PI, add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-pi</artifactId>
    <version>6.0.0</version>
</dependency>
```

Alternatively, if you're extending YAWL itself, the PI module is already included in the multi-module build:

```bash
# Build the entire YAWL stack including PI
mvn -pl yawl-pi clean package

# Or just PI + its dependencies
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-pi clean package
```

### Step 3: Verify the Installation

Create a minimal test class:

```java
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.PISession;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePredictor;

public class PIQuickTest {
    public static void main(String[] args) {
        System.out.println("ProcessIntelligenceFacade class loaded: " +
            ProcessIntelligenceFacade.class.getSimpleName());
        System.out.println("PI module is ready!");
    }
}
```

Compile and run:

```bash
javac -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):yawl-pi.jar PIQuickTest.java
java -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):yawl-pi.jar PIQuickTest
```

Expected output:
```
ProcessIntelligenceFacade class loaded: ProcessIntelligenceFacade
PI module is ready!
```

---

## Part 2: Build Your First Predictive Model

### Scenario

Your company runs an order approval workflow. You want to predict which orders will be **delayed** (not completed within 2 days) so you can escalate them early.

### Step 1: Prepare Training Data

First, export your workflow event log as OCEL2 format (Object-Centric Event Log 2.0).

If you don't have real data, download a sample event log:

```bash
# Create a sample CSV with 100 historical orders
cat > orders.csv << 'EOF'
case_id,timestamp,activity,actor,duration_hours,amount,priority
1,2025-01-01T09:00:00Z,submit,user_1,0.5,1000,normal
1,2025-01-01T10:00:00Z,review,approver_1,2.0,1000,normal
1,2025-01-01T12:30:00Z,approve,approver_1,0.25,1000,normal
1,2025-01-01T13:00:00Z,ship,warehouse_1,4.0,1000,normal
2,2025-01-02T08:00:00Z,submit,user_2,0.5,5000,high
2,2025-01-02T09:00:00Z,review,approver_2,4.0,5000,high
2,2025-01-02T14:00:00Z,escalate,manager_1,1.0,5000,high
2,2025-01-03T09:00:00Z,approve,manager_1,0.25,5000,high
2,2025-01-03T10:00:00Z,ship,warehouse_2,6.0,5000,high
EOF
```

### Step 2: Convert to OCEL2

Use the **Data Preparation Bridge** to normalize your CSV to OCEL2:

```java
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ConvertToOCEL2 {
    public static void main(String[] args) throws Exception {
        // Define your CSV-to-OCEL2 mapping
        OcedSchema schema = OcedSchema.builder()
            .eventIdColumn("case_id")
            .timestampColumn("timestamp")
            .activityColumn("activity")
            .objectTypeColumn("object_type")
            .addAttribute("actor", String.class)
            .addAttribute("duration_hours", Double.class)
            .addAttribute("amount", Double.class)
            .addAttribute("priority", String.class)
            .build();

        // Create the bridge
        OcedBridge bridge = OcedBridgeFactory.createCsvBridge(schema);

        // Convert
        Path inputCsv = Paths.get("orders.csv");
        Path outputOcel2 = Paths.get("orders.ocel2.json");

        bridge.convertFromCsv(inputCsv, outputOcel2);

        System.out.println("Converted to: " + outputOcel2);
    }
}
```

The output `orders.ocel2.json` is now ready for model training.

### Step 3: Train an AutoML Model

Use **TPOT2** (AutoML for scikit-learn) to automatically generate the best predictive model:

```java
import org.yawlfoundation.yawl.pi.automl.TpotAutomlTrainer;
import org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TrainAutomlModel {
    public static void main(String[] args) throws Exception {
        Path ocel2 = Paths.get("orders.ocel2.json");

        // Train the model (TPOT2 will generate Python code internally)
        TpotAutomlTrainer trainer = new TpotAutomlTrainer();
        Path modelPath = trainer.trainModel(
            ocel2,
            "delayed",  // Target: predict if case is delayed
            "classification"  // Classification task
        );

        System.out.println("Model trained and saved to: " + modelPath);

        // Register it with the PI module
        PredictiveModelRegistry registry = PredictiveModelRegistry.getInstance();
        registry.registerModel("order_delay_predictor", modelPath);

        System.out.println("Model registered with registry!");
    }
}
```

### Step 4: Deploy the Model

Once the model is trained, deploy it as a MCP tool accessible to AI agents:

```java
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.PIFacadeConfig;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePredictor;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;

import java.util.Map;

public class DeployPredictiveModel {
    public static void main(String[] args) throws Exception {
        // Create a PI facade
        PIFacadeConfig config = PIFacadeConfig.builder()
            .predictiveEnabled(true)
            .modelPath("./order_delay_predictor.onnx")
            .build();

        ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
            config.createCaseOutcomePredictor(),
            config.createPrescriptiveEngine(),
            config.createResourceOptimizer(),
            config.createNaturalLanguageQueryEngine()
        );

        // Make your first prediction
        Map<String, Object> caseData = Map.of(
            "amount", 5000.0,
            "priority", "high",
            "actor", "user_2"
        );

        CaseOutcomePrediction result = pi.predictCaseOutcome(
            "case_123",
            caseData
        );

        System.out.println("Case: " + result.caseId());
        System.out.println("Will be delayed: " + result.probability("delayed"));
        System.out.println("Confidence: " + result.confidence());
    }
}
```

---

## Part 3: Implement Prescriptive Recommendations

### Scenario

When a prediction shows high delay risk, you want an automated **recommendation** to:
- Escalate to a senior approver
- Reallocate resources
- Reroute the task

### Step 1: Create a Recommendation Engine

```java
import org.yawlfoundation.yawl.pi.prescriptive.PrescriptiveEngine;
import org.yawlfoundation.yawl.pi.prescriptive.ProcessAction;
import org.yawlfoundation.yawl.pi.prescriptive.RecommendationContext;

import java.util.List;

public class GetRecommendations {
    public static void main(String[] args) throws Exception {
        PrescriptiveEngine engine = new PrescriptiveEngine();

        // Build recommendation context
        RecommendationContext context = RecommendationContext.builder()
            .caseId("case_123")
            .currentTask("review")
            .predictedOutcome("delayed")
            .riskScore(0.85)  // 85% probability of delay
            .availableResources(3)  // 3 reviewers available
            .build();

        // Get recommendations
        List<ProcessAction> recommendations = engine.recommendActions(context);

        // Print recommendations
        for (ProcessAction action : recommendations) {
            System.out.println("Recommendation: " + action.actionType());
            System.out.println("  Target: " + action.targetResource());
            System.out.println("  Confidence: " + action.confidence());
            System.out.println("  Reason: " + action.explanation());
            System.out.println();
        }
    }
}
```

### Step 2: Execute Recommended Actions Automatically

Once you have recommendations, execute them directly:

```java
// Continue from GetRecommendations.java...

public class ExecuteRecommendations {
    public static void main(String[] args) throws Exception {
        // ... (previous code to get recommendations) ...

        // Execute the top recommendation
        if (!recommendations.isEmpty()) {
            ProcessAction action = recommendations.get(0);

            // Execute via the engine
            String result = engine.executeAction(action);

            System.out.println("Action executed: " + result);
        }
    }
}
```

---

## Part 4: Integrate with Your Running Workflow Engine

### Step 1: Wire PI into the Engine

Create a PI-enabled work item handler:

```java
import org.yawlfoundation.yawl.engine.interfce.interfaceX.YawlFactsData;
import org.yawlfoundation.yawl.engine.interfce.worklet.WorkletConstants;
import org.yawlfoundation.yawl.resourcing.resource.Participant;
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction;
import org.yawlfoundation.yawl.pi.prescriptive.ProcessAction;

import java.util.List;

public class PIEnabledWorkItemHandler {

    private final ProcessIntelligenceFacade pi;

    public PIEnabledWorkItemHandler(ProcessIntelligenceFacade pi) {
        this.pi = pi;
    }

    public void handleOrderReview(YawlFactsData caseData) throws Exception {
        String caseId = caseData.getCaseID();

        // Step 1: Predict the outcome
        CaseOutcomePrediction prediction = pi.predictCaseOutcome(
            caseId,
            caseData.toMap()
        );

        System.out.println("Case " + caseId + " delay risk: " +
            prediction.probability("delayed"));

        // Step 2: Get recommendations if risk is high
        if (prediction.probability("delayed") > 0.7) {
            List<ProcessAction> actions = pi.getPrescriptiveEngine()
                .recommendActions(buildContext(caseId, caseData));

            // Step 3: Execute the top recommendation
            ProcessAction action = actions.get(0);
            pi.getPrescriptiveEngine().executeAction(action);

            System.out.println("High risk detected. Executed: " +
                action.actionType());
        }
    }

    private RecommendationContext buildContext(String caseId,
            YawlFactsData data) {
        return RecommendationContext.builder()
            .caseId(caseId)
            .currentTask("review")
            .predictedOutcome("delayed")
            .riskScore(0.75)
            .build();
    }
}
```

### Step 2: Register the Handler with the Engine

In your engine initialization code:

```java
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_EventObserver;

public class EngineWithPI {
    public static void main(String[] args) throws Exception {
        // Initialize the engine
        YEngine engine = new YEngine();

        // Create PI facade
        ProcessIntelligenceFacade pi = createPI();

        // Create and register the PI-enabled handler
        PIEnabledWorkItemHandler piHandler =
            new PIEnabledWorkItemHandler(pi);

        // Subscribe to work item events
        engine.getEventBus().subscribe(event -> {
            try {
                piHandler.handleOrderReview(event.getCaseData());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("PI-enabled engine is ready!");
    }

    private static ProcessIntelligenceFacade createPI() {
        // ... configuration code ...
        return new ProcessIntelligenceFacade(
            // ... dependencies ...
        );
    }
}
```

---

## Part 5: Test Your PI Integration End-to-End

### Step 1: Create a Test Workflow Spec

Create a simple YAWL specification:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <specification uri="OrderApproval_PI" name="Order Approval with PI">
    <documentation>Simple workflow for demonstrating Process Intelligence</documentation>
    <metaData>
      <creator>PI Tutorial</creator>
      <description>Submit → Review → Approve → Ship</description>
    </metaData>
    <net id="OrderNet" isRootNet="true">
      <localVariable>
        <index>1</index>
        <name>order_id</name>
        <type>string</type>
      </localVariable>
      <localVariable>
        <index>2</index>
        <name>delay_risk</name>
        <type>string</type>
      </localVariable>
      <inputOutputCondition id="InputCondition"/>
      <task id="Review" name="Review Order">
        <documentation>Review order and check delay risk</documentation>
      </task>
      <outputCondition id="OutputCondition"/>
      <flow source="InputCondition" target="Review"/>
      <flow source="Review" target="OutputCondition"/>
    </net>
  </specification>
</specificationSet>
```

### Step 2: Deploy and Execute

```bash
# Copy spec to engine resources
cp OrderApproval_PI.yawl /path/to/yawl/engine/specs/

# Launch the engine (if not already running)
java -Xmx2g -jar yawl-engine.jar &

# Wait for engine to start
sleep 5

# Execute your test
java -cp $(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout):yawl-pi.jar \
    EngineWithPI
```

---

## Part 6: Verify Your Setup

Run this comprehensive test:

```java
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.PIFacadeConfig;

public class PIIntegrationTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== PI Integration Test ===\n");

        // 1. Verify PI module is on classpath
        try {
            Class.forName("org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade");
            System.out.println("✓ PI module loaded");
        } catch (ClassNotFoundException e) {
            System.out.println("✗ PI module not found: " + e.getMessage());
            return;
        }

        // 2. Verify dependencies
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            System.out.println("✓ Jackson is available");
        } catch (ClassNotFoundException e) {
            System.out.println("✗ Jackson not found");
        }

        // 3. Try to create a facade
        try {
            PIFacadeConfig config = PIFacadeConfig.builder()
                .predictiveEnabled(true)
                .build();
            ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
                config.createCaseOutcomePredictor(),
                config.createPrescriptiveEngine(),
                config.createResourceOptimizer(),
                config.createNaturalLanguageQueryEngine()
            );
            System.out.println("✓ PI Facade created successfully");
        } catch (Exception e) {
            System.out.println("✗ Failed to create PI Facade: " + e.getMessage());
            return;
        }

        System.out.println("\n=== All checks passed! ===");
        System.out.println("You're ready to use Process Intelligence!");
    }
}
```

---

## Next Steps

You've completed the basics! Now explore:

1. **Advanced Predictive Models** — Read `docs/tutorials/pi-train-automl-model.md` to train more complex models
2. **Realtime Adaptation** — Read `docs/tutorials/pi-realtime-adaptive.md` to adapt workflows in real-time based on predictions
3. **Natural Language Queries** — Read `docs/tutorials/pi-natural-language-qa.md` to query cases in natural language
4. **Configuration Reference** — Read `docs/reference/pi-configuration.md` for all available options
5. **Architecture Deep Dive** — Read `docs/explanation/pi-architecture.md` to understand how PI works internally

---

## Troubleshooting

### "ProcessIntelligenceFacade not found"
**Cause:** PI module not on classpath
**Fix:** Add `yawl-pi` to your Maven dependencies or build it with `mvn -pl yawl-pi package`

### "Model file not found"
**Cause:** Path to trained model is incorrect
**Fix:** Verify the model file exists and use an absolute path

### "No recommendations generated"
**Cause:** Prediction confidence is too low or no matching rules
**Fix:** Check your prediction score and review the prescriptive engine's rule set

---

## Success Criteria

By the end of this tutorial, you should be able to:
- [ ] Build the yawl-pi module without errors
- [ ] Create a ProcessIntelligenceFacade
- [ ] Make a case outcome prediction
- [ ] Get prescriptive recommendations
- [ ] Execute recommendations automatically
- [ ] Integrate PI with a running YAWL engine

Once all checkboxes are complete, you're ready for the advanced tutorials!
