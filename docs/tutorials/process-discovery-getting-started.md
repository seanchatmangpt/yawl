# Tutorial: Process Discovery Getting Started

In this tutorial you will:
1. Export YAWL workflow events to OCEL2 format
2. Run Alpha Miner discovery on event logs
3. Compare Alpha, Heuristic, and Inductive miners
4. Convert discovered Petri nets to YAWL specifications

Time: ~15 minutes.

---

## Prerequisites

Add `yawl-engine` to your Maven project:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-engine</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

Ensure you have a running YAWL engine with completed cases to analyze.

---

## Step 1: Create the ProcessMiningFacade

The `ProcessMiningFacade` is the single entry point for all process mining operations:

```java
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;

// Connect to your YAWL engine
String engineUrl = "http://localhost:8080/yawl";
String username = "admin";
String password = "password";

ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
```

The facade orchestrates all process mining components:
- **XES Export**: Event log in eXtensible Event Stream format
- **Performance Analysis**: Flow time, throughput metrics
- **Conformance Checking**: Token-based replay fitness
- **Variant Extraction**: Unique activity sequences
- **OCEL Conversion**: Object-centric event logs

---

## Step 2: Run Your First Analysis

Analyze a specification to get performance metrics and variants:

```java
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade.ProcessMiningReport;

YSpecificationID specId = new YSpecificationID("MyWorkflow", "1.0", "http://example.com");

// Run full analysis (no conformance without YNet)
ProcessMiningReport report = facade.analyzePerformance(specId, true);

System.out.println("Traces analyzed: " + report.traceCount);
System.out.println("Average flow time: " + report.performance.avgFlowTimeMs() + " ms");
System.out.println("Throughput: " + report.performance.throughputPerHour() + " cases/hour");
System.out.println("Variants found: " + report.variantCount);
```

### Understanding the Report

| Field | Description |
|-------|-------------|
| `xesXml` | Raw XES event log for external tools |
| `performance` | Flow time, throughput, activity counts |
| `variantFrequencies` | Activity sequences ranked by frequency |
| `ocelJson` | Object-centric event log in OCEL 2.0 format |
| `conformance` | Fitness score (null without reference model) |

---

## Step 3: Export to OCEL2 Format

OCEL2 (Object-Centric Event Log) captures relationships between events and multiple object types:

```java
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter;
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.WorkflowEventRecord;
import java.time.Instant;
import java.util.List;

// Create event records from your workflow
List<WorkflowEventRecord> events = List.of(
    new WorkflowEventRecord(
        "ev-001",           // eventId
        "case-123",         // caseId
        "wi-001",           // workItemId
        "SubmitOrder",      // activity
        "user@example.com", // resource
        Instant.now(),      // timestamp
        "TaskCompleted"     // eventType
    )
);

Ocel2Exporter exporter = new Ocel2Exporter();
String ocel2Json = exporter.exportWorkflowEvents(events);

// OCEL2 structure:
// {
//   "ocel:version": "2.0",
//   "ocel:events": { "ev-001": { "ocel:activity": "SubmitOrder", ... } },
//   "ocel:objects": { "case-123": { "ocel:type": "Case", ... } }
// }
```

### Why OCEL2 Over XES?

| Aspect | XES | OCEL2 |
|--------|-----|-------|
| **Data Model** | Single case ID | Many-to-many object relationships |
| **Object Types** | One per log | Multiple (Case, WorkItem, Resource) |
| **Relationships** | Flat | Hierarchical object links |
| **Real-world Fit** | 60% of processes | 95% of processes |

---

## Step 4: Discover Process Models

YAWL provides multiple discovery algorithms:

### Alpha Miner

Discovers Petri nets from footprint analysis. Guarantees perfect fitness:

```java
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.Ocel2EventLog;
import org.yawlfoundation.yawl.integration.processmining.ProcessDiscoveryResult;

// Build OCEL2 event log
Ocel2EventLog eventLog = /* from Step 3 */;

// Run Alpha Miner
ProcessDiscoveryResult alphaResult = facade.discoverAlpha(eventLog);

System.out.println("Fitness: " + alphaResult.fitness());      // Always 1.0
System.out.println("Precision: " + alphaResult.precision());  // Varies
System.out.println("Activities: " + alphaResult.activityCount());
System.out.println("Cases: " + alphaResult.caseCount());
```

### Heuristic Miner

Handles noise and infrequent behavior better:

```java
import org.yawlfoundation.yawl.integration.processmining.discovery.HeuristicMiner;
import org.yawlfoundation.yawl.integration.processmining.discovery.ProcessDiscoveryAlgorithm;

HeuristicMiner heuristicMiner = new HeuristicMiner();
ProcessDiscoveryResult heuristicResult = heuristicMiner.discover(
    new ProcessDiscoveryAlgorithm.ProcessMiningContext.Builder()
        .eventLog(eventLog)
        .algorithm(ProcessDiscoveryAlgorithm.AlgorithmType.HEURISTIC)
        .noiseThreshold(0.1)        // Filter 10% noise
        .frequencyThreshold(0.8)    // Include 80% frequency activities
        .build()
);
```

### Inductive Miner

Discovers sound process trees (guaranteed deadlock-free):

```java
import org.yawlfoundation.yawl.integration.processmining.discovery.InductiveMiner;
import org.yawlfoundation.yawl.integration.processmining.discovery.ProcessTree;

// Note: Full process tree extraction requires additional implementation
// Use Alpha Miner for Petri net discovery
```

---

## Step 5: Compare Algorithm Results

| Algorithm | Fitness | Precision | Soundness | Best For |
|-----------|---------|-----------|-----------|----------|
| **Alpha** | 1.0 | Medium | Not guaranteed | Clean logs, formal analysis |
| **Heuristic** | High | High | Not guaranteed | Noisy logs, real-world data |
| **Inductive** | High | High | Guaranteed | Complex control flow, soundness |

```java
// Compare metrics across algorithms
System.out.println("| Algorithm   | Fitness | Precision |");
System.out.println("|-------------|---------|-----------|");
System.out.printf("| Alpha       | %.2f  | %.2f    |%n",
    alphaResult.fitness(), alphaResult.precision());
System.out.printf("| Heuristic   | %.2f  | %.2f    |%n",
    heuristicResult.fitness(), heuristicResult.precision());
```

---

## Step 6: Convert to YAWL Specification

Transform discovered Petri nets into executable YAWL workflows:

```java
import org.yawlfoundation.yawl.integration.processmining.synthesis.YawlSpecSynthesizer;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlParser;
import org.yawlfoundation.yawl.integration.processmining.synthesis.SynthesisResult;

// Parse discovered model as PNML
String pnmlXml = alphaResult.processModelJson(); // JSON Petri net

// Create synthesizer
YawlSpecSynthesizer synthesizer = new YawlSpecSynthesizer(
    "http://example.com/specs/discovered-workflow",
    "Discovered Workflow"
);

// Synthesize YAWL XML
SynthesisResult result = synthesizer.synthesizeWithConformance(pnmlProcess);

System.out.println("Tasks generated: " + result.tasksGenerated());
System.out.println("Conditions: " + result.conditionsGenerated());
System.out.println("Conformance: " + result.conformanceScore());

// Use the YAWL XML
String yawlXml = result.yawlXml();
// Upload to engine, validate, deploy...
```

---

## Step 7: Conformance Checking

Validate discovered models against actual behavior:

```java
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade.ConformanceResult;
import org.yawlfoundation.yawl.elements.YNet;

// Load your reference YAWL net
YNet referenceNet = /* load from specification */;

// Run token-based replay
ConformanceResult conformance = facade.tokenReplayConformance(referenceNet, report.xesXml);

System.out.println("Fitness: " + conformance.fitness());
// Fitness > 0.9: Good fit
// Fitness 0.7-0.9: Moderate deviations
// Fitness < 0.7: Significant process drift
```

---

## Complete Example

```java
import org.yawlfoundation.yawl.integration.processmining.*;
import org.yawlfoundation.yawl.engine.YSpecificationID;

public class ProcessDiscoveryExample {
    public static void main(String[] args) throws Exception {
        // 1. Create facade
        ProcessMiningFacade facade = new ProcessMiningFacade(
            "http://localhost:8080/yawl", "admin", "password");

        // 2. Analyze specification
        YSpecificationID specId = new YSpecificationID("OrderProcess", "1.0");
        ProcessMiningFacade.ProcessMiningReport report = facade.analyzePerformance(specId, true);

        System.out.println("Analyzed " + report.traceCount + " traces");
        System.out.println("Found " + report.variantCount + " variants");

        // 3. Discover process model
        Ocel2Exporter.Ocel2EventLog eventLog = /* build from report.ocelJson */;
        ProcessDiscoveryResult discovered = facade.discoverAlpha(eventLog);

        System.out.println("Discovered model with " + discovered.activityCount() + " activities");
        System.out.println("Fitness: " + discovered.fitness());
        System.out.println("Precision: " + discovered.precision());

        // 4. Close facade
        facade.close();
    }
}
```

---

## Next Steps

- **[How-To: Process Mining Analysis](../how-to/process-mining-analysis.md)** - Conformance checking, bottleneck detection
- **[Reference: Process Mining API](../reference/process-mining-api.md)** - Complete API reference
- **[Explanation: Process Mining Architecture](../explanation/process-mining-architecture.md)** - OCEL2 vs XES, algorithm trade-offs

---

## Troubleshooting

### "Event log is empty"

Ensure your specification has completed cases. Check the engine logs for XES export errors.

### "Discovery returns no activities"

The event log may have inconsistent case IDs. Verify all events have valid `concept:name` attributes.

### "Fitness is always 1.0"

Alpha Miner guarantees fitness=1.0 on the discovery log. Use conformance checking on held-out data for real evaluation.
