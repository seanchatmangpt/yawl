# Tutorial 3 — Build a Real-Time Adaptive Process

In this tutorial you will:
1. Attach `PredictiveProcessObserver` to a YAWL engine as an `ObserverGateway`
2. Add an SLA guardian rule that escalates cases breaching a time threshold
3. Add a fraud detection rule for anomaly scores
4. See both rules fire during case execution

Time: ~20 minutes. Requires a working `YEngine` instance.

---

## Prerequisites

Complete [Tutorial 1](01-first-case-prediction.md) and have a running `YEngine`.
This tutorial assumes:

```java
YEngine engine = YEngine.getInstance();                // running YAWL engine
PredictiveModelRegistry registry = /* from Tutorial 2 or empty */;
WorkflowEventStore eventStore = /* your event store */;
```

---

## Step 1: Build the observer

```java
import org.yawlfoundation.yawl.pi.adaptive.PredictiveProcessObserver;
import org.yawlfoundation.yawl.pi.predictive.*;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle;
import org.yawlfoundation.yawl.observatory.spec.XesToYawlSpecGenerator;
import java.util.List;

WorkflowDNAOracle oracle = new WorkflowDNAOracle(new XesToYawlSpecGenerator(1));
CaseOutcomePredictor predictor = new CaseOutcomePredictor(eventStore, oracle, registry);

PredictiveProcessObserver observer = new PredictiveProcessObserver(predictor);
```

---

## Step 2: Build the adaptation engine with rules

```java
import org.yawlfoundation.yawl.pi.adaptive.PredictiveAdaptationRules;
import org.yawlfoundation.yawl.integration.adaptation.*;

// Timer expiry: always escalate immediately (priority 5)
AdaptationRule timerRule = PredictiveAdaptationRules.timerExpiryBreach();

// SLA guardian: escalate if remaining time < 60 minutes (priority 20)
AdaptationRule slaRule = PredictiveAdaptationRules.slaGuardian(60);

// Fraud detection: reject if anomaly score > 0.80 (priority 10)
AdaptationRule fraudRule = PredictiveAdaptationRules.fraudDetector(0.80);

// High-risk escalation: escalate if outcome risk > 0.70 (priority 30)
AdaptationRule riskRule = PredictiveAdaptationRules.highRiskEscalation(0.70);

// Build the engine — evaluated in priority order
EventDrivenAdaptationEngine adaptationEngine = new EventDrivenAdaptationEngine(
    List.of(timerRule, fraudRule, slaRule, riskRule));
```

---

## Step 3: Register action handlers

The adaptation engine fires `AdaptationAction` values. Register handlers for actions
you care about:

```java
adaptationEngine.registerHandler(AdaptationAction.ESCALATE_TO_MANUAL, event -> {
    System.out.printf("[ESCALATION] Case %s escalated. Reason: %s%n",
        event.getCaseId(), event.getPayload().get("rationale"));
    // In production: notify supervisor, send email, update ticket system
});

adaptationEngine.registerHandler(AdaptationAction.REJECT_IMMEDIATELY, event -> {
    System.out.printf("[FRAUD REJECTION] Case %s rejected. Score: %s%n",
        event.getCaseId(), event.getPayload().get("anomalyScore"));
    // In production: halt case, log to audit system
});

adaptationEngine.registerHandler(AdaptationAction.NOTIFY_STAKEHOLDERS, event -> {
    System.out.printf("[NOTIFICATION] %s — %s%n",
        event.getCaseId(), event.getPayload());
    // In production: send webhook / email
});
```

---

## Step 4: Wire observer to the adaptation engine and register with YEngine

```java
// Tell the observer which adaptation engine to route events to
observer.setAdaptationEngine(adaptationEngine);

// Register with the YAWL engine — receives callbacks for all workflow events
engine.addObserver(observer);

System.out.println("PredictiveProcessObserver registered. Rules active:");
System.out.println("  - Timer expiry breach  (priority 5)");
System.out.println("  - Fraud detector 0.80  (priority 10)");
System.out.println("  - SLA guardian 60min   (priority 20)");
System.out.println("  - High-risk escalation 0.70 (priority 30)");
```

---

## Step 5: Start a case and observe rules firing

Start a case through the normal YAWL workflow. The observer receives callbacks automatically:

```java
// Simulate engine callbacks (in real usage, these fire from YEngine internally)
String caseId = engine.launchCase(specId, null, null, null, null);

System.out.println("Case launched: " + caseId);
// The observer.announceCaseStarted() is called by YEngine
// → CaseOutcomePredictor.predict() runs
// → If riskScore > 0.70, highRiskEscalation fires
// → [ESCALATION] case-001 escalated. Reason: high outcome risk
```

For testing without a live engine, simulate the callbacks directly:

```java
import org.yawlfoundation.yawl.engine.YSpecificationID;
import java.net.URI;

YSpecificationID spec = new YSpecificationID("loan-application", "1.0",
    "http://yawl.example.com/loan");

// Simulate CASE_STARTED announcement
observer.announceCaseStarted(spec, caseId, null, null, null, null, null);
```

---

## Step 6: Use a pre-built industry rule set

Instead of composing rules individually, use an industry-specific preset:

```java
// Insurance claims — includes timer + fraud + complexity + anomaly
List<AdaptationRule> insuranceRules =
    PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);

EventDrivenAdaptationEngine engine2 = new EventDrivenAdaptationEngine(insuranceRules);
observer.setAdaptationEngine(engine2);
```

Available presets:
- `insuranceClaimsRuleSet(fraudThreshold, complexityConfidence)`
- `healthcareRuleSet(slaMinutes, riskThreshold)`
- `financialRiskRuleSet(fraudThreshold, riskThreshold)`
- `operationsSlaRuleSet(criticalMinutes, warningMinutes)`

---

## What you built

- A `PredictiveProcessObserver` receiving live YAWL engine callbacks
- Four adaptation rules covering SLA, fraud, and outcome risk
- Action handlers printing escalations and rejections
- The observer registered with the YAWL engine and active for all cases

## Understanding what happens inside

When `YEngine` fires `announceCaseStarted(caseId, ...)`:
1. `PredictiveProcessObserver.announceCaseStarted()` is called **on the engine thread**
2. `CaseOutcomePredictor.predict(caseId)` runs ONNX inference (sub-millisecond)
3. A `ProcessEvent(CASE_OUTCOME_PREDICTION, payload)` is emitted to the adaptation engine
4. Rules are evaluated in priority order
5. Matching actions are dispatched to registered handlers asynchronously
6. Engine callback returns immediately — case execution is unaffected

Any `PIException` is caught and logged; it never propagates to the engine.

## Next steps

- [Tutorial 4](04-natural-language-qa.md) — Query process knowledge with natural language
- [How to add an adaptation rule](../how-to/add-adaptation-rule.md) — Custom rules and combinators
- [Co-located AutoML explanation](../explanation/co-located-automl.md) — Why this architecture matters
