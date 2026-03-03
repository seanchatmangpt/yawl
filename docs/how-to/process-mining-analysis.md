# How-To: Process Mining Analysis

Task-oriented recipes for process mining with YAWL.

---

## Export YAWL Events to OCEL2 Format

Convert workflow events to Object-Centric Event Log format:

```java
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter;
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.WorkflowEventRecord;
import java.time.Instant;
import java.util.List;

Ocel2Exporter exporter = new Ocel2Exporter();

// Build event records
List<WorkflowEventRecord> events = List.of(
    new WorkflowEventRecord("ev-1", "case-001", "wi-1", "Start", "user1", Instant.now(), "TaskCompleted"),
    new WorkflowEventRecord("ev-2", "case-001", "wi-2", "Review", "user2", Instant.now(), "TaskCompleted"),
    new WorkflowEventRecord("ev-3", "case-001", "wi-3", "Approve", "user3", Instant.now(), "TaskCompleted")
);

String ocel2Json = exporter.exportWorkflowEvents(events);
```

### OCEL2 Output Structure

```json
{
  "ocel:version": "2.0",
  "ocel:ordering": "timestamp",
  "ocel:attribute-names": ["org:resource", "case:id"],
  "ocel:object-types": ["Case", "WorkItem"],
  "ocel:events": {
    "ev-1": {
      "ocel:activity": "Start",
      "ocel:timestamp": "2026-03-03T12:00:00Z",
      "ocel:omap": { "Case": ["case-001"], "WorkItem": ["wi-1"] },
      "ocel:vmap": { "org:resource": "user1" }
    }
  },
  "ocel:objects": {
    "case-001": { "ocel:type": "Case", "ocel:ovmap": { "case:id": "case-001" } }
  }
}
```

---

## Run Conformance Checking

Validate process behavior against a reference model:

```java
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade.ConformanceResult;
import org.yawlfoundation.yawl.elements.YNet;

ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, user, password);

// Load reference model
YNet referenceNet = loadSpecificationNet();

// Run token-based replay
String xesXml = report.xesXml;
ConformanceResult conformance = facade.tokenReplayConformance(referenceNet, xesXml);

double fitness = conformance.fitness();
// > 0.9: Process conforms well to model
// 0.7-0.9: Moderate deviations detected
// < 0.7: Significant process drift
```

### Interpreting Fitness Scores

| Fitness Range | Interpretation | Action |
|---------------|----------------|--------|
| 0.95 - 1.0 | Excellent conformance | Continue monitoring |
| 0.85 - 0.95 | Minor deviations | Review edge cases |
| 0.70 - 0.85 | Moderate drift | Update model or process |
| < 0.70 | Major deviations | Investigate root cause |

---

## Detect Bottlenecks

Identify slow activities and waiting times:

```java
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade.ProcessMiningReport;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade.PerformanceResult;

ProcessMiningReport report = facade.analyzePerformance(specId, true);
PerformanceResult perf = report.performance;

// Key metrics
System.out.println("Average flow time: " + perf.avgFlowTimeMs() + " ms");
System.out.println("Throughput: " + perf.throughputPerHour() + " cases/hour");

// Activity frequency (proxy for bottleneck detection)
Map<String, Integer> counts = perf.activityCounts();
counts.entrySet().stream()
    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
    .limit(5)
    .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue() + " occurrences"));
```

### Bottleneck Indicators

1. **High activity count** with **long flow time** = Resource constraint
2. **Low throughput** with **high variant count** = Process complexity
3. **Skewed variant distribution** = Common path bottleneck

---

## Analyze Process Variants

Extract and rank unique activity sequences:

```java
ProcessMiningReport report = facade.analyzePerformance(specId, true);

// Variants sorted by frequency (descending)
Map<String, Long> variants = report.variantFrequencies;

System.out.println("Total variants: " + report.variantCount);
System.out.println("\nTop 5 variants:");
variants.entrySet().stream()
    .limit(5)
    .forEach(e -> {
        String sequence = e.getKey();  // "Start,Review,Approve,End"
        Long count = e.getValue();
        double pct = (count * 100.0) / report.traceCount;
        System.out.printf("  %s: %d (%.1f%%)%n", sequence, count, pct);
    });
```

### Variant Analysis Patterns

| Pattern | Meaning | Recommendation |
|---------|---------|----------------|
| **1 variant = 90%+** | Highly standardized | Look for automation |
| **10+ variants, flat** | Ad-hoc process | Standardize or use worklets |
| **Rare variants exist** | Exception handling | Review worklet rules |

---

## Integrate with Autonomous Agents

Use process mining results for agent decision-making:

```java
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.autonomous.AutonomousAgentContext;

// In your autonomous agent
public class ProcessAwareAgent {
    private final ProcessMiningFacade miningFacade;

    public ProcessAction decideNextAction(String caseId) throws Exception {
        // Get current process state
        ProcessMiningReport report = miningFacade.analyzePerformance(specId, false);

        // Check for process drift
        if (report.performance.throughputPerHour() < baselineThroughput * 0.8) {
            return ProcessAction.escalate("Throughput below threshold");
        }

        // Check variant conformance
        String currentVariant = getCurrentVariant(caseId);
        Long variantFreq = report.variantFrequencies.get(currentVariant);
        if (variantFreq == null || variantFreq < 5) {
            return ProcessAction.requestReview("Unusual variant detected");
        }

        return ProcessAction.continueNormal();
    }
}
```

---

## Use PI Facade for Predictions

Combine process mining with ML predictions:

```java
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.CaseOutcomePrediction;

// PI facade for predictions
ProcessIntelligenceFacade pi = createPiFacade();

// Predict case outcome
CaseOutcomePrediction prediction = pi.predictOutcome("case-001");
System.out.println("Risk score: " + prediction.riskScore());
System.out.println("Predicted outcome: " + prediction.predictedOutcome());

// Combine with process mining
ProcessMiningReport report = miningFacade.analyzePerformance(specId, true);
if (prediction.riskScore() > 0.7 && report.performance.avgFlowTimeMs() > slaThreshold) {
    triggerSlaEscalation("case-001");
}
```

---

## Build Directly-Follows Graph

Visualize activity execution patterns:

```java
import org.yawlfoundation.yawl.integration.processmining.discovery.DirectlyFollowsGraph;
import java.util.List;

// Extract traces from events
List<List<String>> traces = extractTracesFromEvents(events);

// Build DFG
DirectlyFollowsGraph dfg = facade.buildDfg(traces);

// Query graph
Set<String> activities = dfg.getActivities();
int edgeCount = dfg.getEdgeCount("Start", "Review");  // How often Start→Review occurs

// Export for visualization
String dfgJson = dfg.toJson();
```

---

## Object-Centric Process Mining

Analyze processes with multiple object types:

```java
import org.yawlfoundation.yawl.integration.processmining.ocpm.OcpmDiscovery;
import org.yawlfoundation.yawl.integration.processmining.ocpm.OcpmInput;

// Build OCPM input with multiple object types
OcpmInput input = new OcpmInput.Builder()
    .events(ocelEvents)
    .objects(ocelObjects)
    .build();

// Discover object-centric Petri net
OcpmDiscovery.OcpmResult ocpn = facade.discoverObjectCentric(input);

// Per-object-type analysis
for (String objectType : ocpn.objectTypes()) {
    System.out.println("Object type: " + objectType);
    System.out.println("  Activities: " + ocpn.activitiesForType(objectType).size());
}

// Shared transitions (synchronization points)
System.out.println("Shared transitions: " + ocpn.transitions().size());
```

---

## Fairness Analysis

Detect demographic bias in process decisions:

```java
import org.yawlfoundation.yawl.integration.processmining.responsibleai.FairnessAnalyzer;
import org.yawlfoundation.yawl.integration.processmining.responsibleai.FairnessAnalyzer.FairnessReport;
import java.util.List;
import java.util.Map;

// Prepare case attributes and decisions
List<Map<String, String>> caseAttributes = List.of(
    Map.of("caseId", "c1", "department", "Sales", "resource", "Alice"),
    Map.of("caseId", "c2", "department", "Engineering", "resource", "Bob")
);

List<Map<String, String>> decisions = List.of(
    Map.of("caseId", "c1", "outcome", "approved"),
    Map.of("caseId", "c2", "outcome", "rejected")
);

// Run fairness analysis
FairnessReport report = facade.analyzeFairness(
    caseAttributes,
    decisions,
    "department",    // sensitive attribute
    "approved"       // positive outcome
);

System.out.println("Disparate impact: " + report.disparateImpact());
System.out.println("Is fair (4/5 rule): " + report.isFair());
if (!report.isFair()) {
    System.out.println("Violations: " + report.violations());
}
```

### Four-Fifths Rule

The disparate impact ratio must be ≥ 0.80 for fairness:
- Ratio = (selection rate of disadvantaged group) / (selection rate of advantaged group)
- < 0.80 indicates potential discrimination

---

## Export to External Tools

### Export to PNML (Petri Net Markup Language)

```java
import org.yawlfoundation.yawl.integration.processmining.PnmlExporter;
import org.yawlfoundation.yawl.elements.YNet;

YNet net = loadSpecificationNet();
String pnmlXml = facade.exportPnml(net);

// Use with ProM, pm4py, or other tools
Files.writeString(Path.of("workflow.pnml"), pnmlXml);
```

### Export to XES

```java
import org.yawlfoundation.yawl.integration.processmining.EventLogExporter;

EventLogExporter exporter = new EventLogExporter(engineUrl, user, password);
String xesXml = exporter.exportSpecificationToXes(specId, true);

Files.writeString(Path.of("events.xes"), xesXml);
```

---

## Streaming Event Analysis

Process events in real-time:

```java
import org.yawlfoundation.yawl.integration.processmining.streaming.YAWLEventStream;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

YAWLEventStream stream = new YAWLEventStream();

// Subscribe to events
stream.subscribe(event -> {
    // Real-time process mining
    Ocel2Exporter.WorkflowEventRecord record = Ocel2Exporter.fromXesEvent(
        event.getCaseId(),
        deriveActivityName(event),
        event.getTimestamp()
    );

    // Update incremental DFG
    dfg.addEdge(record.activity(), previousActivity);

    // Trigger rules
    if (isAnomalous(record, dfg)) {
        alertAnomaly(record);
    }
});
```

---

## Troubleshooting

### "No events exported"

Verify the specification has completed cases. Check engine connectivity and authentication.

### "Conformance fitness is 0"

The reference model may not match any observed behavior. Verify the PNML export is correct.

### "Variant extraction returns empty"

XES parsing may have failed. Ensure events have valid `concept:name` attributes.

### "OCEL2 export missing objects"

Verify event records have non-null `caseId` and `workItemId` fields.

---

## Related Documentation

- **[Tutorial: Process Discovery Getting Started](../tutorials/process-discovery-getting-started.md)** - Step-by-step introduction
- **[Reference: Process Mining API](../reference/process-mining-api.md)** - Complete API reference
- **[Explanation: Process Mining Architecture](../explanation/process-mining-architecture.md)** - Concepts and design
