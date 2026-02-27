# How to Add an Adaptation Rule

`PredictiveAdaptationRules` provides factory methods for common real-time response rules.
This guide shows how to use the factories, compose rule sets, and write a custom rule.

---

## Using a built-in factory method

```java
import org.yawlfoundation.yawl.pi.adaptive.PredictiveAdaptationRules;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationRule;

// Escalate cases where predicted remaining time drops below 60 minutes
AdaptationRule slaRule = PredictiveAdaptationRules.slaGuardian(60);

// Reject cases with anomaly score above 0.85 (fraud)
AdaptationRule fraudRule = PredictiveAdaptationRules.fraudDetector(0.85);

// Escalate cases with outcome risk > 0.70
AdaptationRule riskRule = PredictiveAdaptationRules.highRiskEscalation(0.70);

// Immediately escalate timer-expired cases
AdaptationRule timerRule = PredictiveAdaptationRules.timerExpiryBreach();
```

All factory methods have a `(threshold)` overload using default priorities and
a `(threshold, priority)` overload for explicit control. Lower priority number = evaluated first.

---

## Available factory methods

| Factory method | Responds to | Action |
|---|---|---|
| `timerExpiryBreach()` | `TIMER_EXPIRY_BREACH` | ESCALATE_TO_MANUAL |
| `slaGuardian(mins)` | `SLA_BREACH_PREDICTED` + remaining < mins | ESCALATE_TO_MANUAL |
| `fraudDetector(threshold)` | `PROCESS_ANOMALY_DETECTED` + score > threshold | REJECT_IMMEDIATELY |
| `anomalyAlert(threshold)` | `PROCESS_ANOMALY_DETECTED` + score > threshold | NOTIFY_STAKEHOLDERS |
| `highRiskEscalation(threshold)` | `CASE_OUTCOME_PREDICTION` + outcome > threshold | ESCALATE_TO_MANUAL |
| `complexityRouter(threshold)` | `CASE_OUTCOME_PREDICTION` + confidence > threshold | REROUTE_TO_SUBPROCESS |
| `nextActivityPriorityBoost(threshold)` | `NEXT_ACTIVITY_SUGGESTION` + confidence > threshold | INCREASE_PRIORITY |

---

## Pre-built industry rule sets

```java
// Insurance claims: timer breach + fraud + complexity + anomaly alert
List<AdaptationRule> insuranceRules =
    PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);

// Healthcare: timer + SLA guardian + high-risk escalation + next-activity boost
List<AdaptationRule> healthcareRules =
    PredictiveAdaptationRules.healthcareRuleSet(30, 0.70);

// Financial risk: timer + fraud + risk escalation + anomaly alert
List<AdaptationRule> financeRules =
    PredictiveAdaptationRules.financialRiskRuleSet(0.90, 0.75);

// Operations SLA: timer + critical SLA + warning SLA + anomaly alert
List<AdaptationRule> opsRules =
    PredictiveAdaptationRules.operationsSlaRuleSet(15, 45);
```

---

## Composing rules into an engine

```java
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;

EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(List.of(
    PredictiveAdaptationRules.timerExpiryBreach(),          // priority 5
    PredictiveAdaptationRules.fraudDetector(0.85),          // priority 10
    PredictiveAdaptationRules.slaGuardian(60),              // priority 20
    PredictiveAdaptationRules.highRiskEscalation(0.70),     // priority 30
    PredictiveAdaptationRules.anomalyAlert(0.60)            // priority 50
));
```

Rules are evaluated in priority order (ascending). The engine stops at the
first rule whose condition matches, then executes its action.

---

## Writing a custom rule

If the built-in factories don't cover your use case:

```java
import org.yawlfoundation.yawl.integration.adaptation.*;

AdaptationRule customRule = new AdaptationRule(
    "rule-vip-fast-track",          // unique rule ID
    "VIP Customer Fast Track",       // display name
    AdaptationCondition.and(
        AdaptationCondition.eventType(PredictiveProcessObserver.CASE_OUTCOME_PREDICTION),
        AdaptationCondition.payloadAbove("confidence", 0.90)
    ),
    AdaptationAction.INCREASE_PRIORITY,
    15,                              // priority (higher than slaGuardian=20)
    "Fast-track cases where prediction confidence exceeds 90%"
);
```

### AdaptationCondition combinators

```java
// Simple event type match
AdaptationCondition.eventType("MY_CUSTOM_EVENT")

// Payload threshold checks
AdaptationCondition.payloadAbove("anomalyScore", 0.8)  // payload > threshold
AdaptationCondition.payloadBelow("remainingMinutes", 30) // payload < threshold

// Logical AND of two conditions
AdaptationCondition.and(cond1, cond2)
```

### AdaptationAction values

| Value | Effect |
|---|---|
| `ESCALATE_TO_MANUAL` | Route case to manual review queue |
| `REJECT_IMMEDIATELY` | Terminate case with rejection outcome |
| `NOTIFY_STAKEHOLDERS` | Send notification (email/webhook) |
| `INCREASE_PRIORITY` | Move case to top of its process queue |
| `REROUTE_TO_SUBPROCESS` | Route to specialist subprocess |

---

## Verifying rules fire

Add a `System.out.println` in your rule action during testing, or inspect the
engine's processed event log. The engine logs at DEBUG level when a rule fires.

To test a rule in isolation without a live engine:

```java
AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(60);
ProcessEvent event = ProcessEvent.slaBreachPredicted("case-001", 45.0);  // 45 min remaining

boolean fires = rule.condition().matches(event);
System.out.println("Rule fires: " + fires);  // true — 45 < 60
```
