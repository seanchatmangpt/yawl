# Reference: Process Mining API

Complete API reference for YAWL's process mining module.

---

## Core Classes

### ProcessMiningFacade

**Package:** `org.yawlfoundation.yawl.integration.processmining`

Single entry point for all process mining operations.

#### Constructors

```java
public ProcessMiningFacade(String engineUrl, String username, String password)
    throws IOException
```

Creates a facade connected to a YAWL engine with embedded GraalPy process mining service.

| Parameter | Description |
|-----------|-------------|
| `engineUrl` | Base URL of YAWL engine (e.g., `http://localhost:8080/yawl`) |
| `username` | Username for authentication |
| `password` | Password for authentication |
| **Throws** | `IOException` if connection fails |

```java
public ProcessMiningFacade(String engineUrl, String username, String password,
                           ProcessMiningService service) throws IOException
```

Creates a facade with an externally supplied service for testing.

#### Methods

##### analyze

```java
public ProcessMiningReport analyze(YSpecificationID specId, YNet net, boolean withData)
    throws IOException
```

Runs complete process mining analysis: XES → performance → conformance → variants → OCEL.

| Parameter | Description |
|-----------|-------------|
| `specId` | Specification to analyze (required) |
| `net` | Optional YAWL net for conformance checking |
| `withData` | Include data attributes in XES export |
| **Returns** | Complete analysis report |
| **Throws** | `IOException` if export or analysis fails |

##### analyzePerformance

```java
public ProcessMiningReport analyzePerformance(YSpecificationID specId, boolean withData)
    throws IOException
```

Quick analysis without conformance checking.

##### analyzeFromEventStore

```java
public ProcessMiningReport analyzeFromEventStore(String caseId, WorkflowEventStore eventStore)
    throws WorkflowEventStore.EventStoreException
```

Analyzes events from the event store for a specific case.

##### discoverAlpha

```java
public ProcessDiscoveryResult discoverAlpha(Ocel2EventLog eventLog)
    throws ProcessDiscoveryException
```

Discovers a Petri net using the Alpha Miner algorithm.

##### discoverInductive

```java
public ProcessTree discoverInductive(Ocel2EventLog eventLog)
    throws ProcessDiscoveryException
```

Discovers a process tree using the Inductive Miner algorithm.

##### buildDfg

```java
public DirectlyFollowsGraph buildDfg(List<List<String>> traces)
```

Builds a directly-follows graph from event traces.

##### tokenReplayConformance

```java
public ConformanceResult tokenReplayConformance(YNet net, String xesXml)
    throws IOException
```

Runs token-based replay conformance check.

##### exportPnml

```java
public String exportPnml(YNet net)
```

Exports YAWL net to PNML format.

##### discoverObjectCentric

```java
public OcpmDiscovery.OcpmResult discoverObjectCentric(OcpmInput input)
```

Discovers an Object-Centric Petri Net from OCEL 2.0 data.

##### analyzeFairness

```java
public FairnessAnalyzer.FairnessReport analyzeFairness(
    List<Map<String, String>> caseAttributes,
    List<Map<String, String>> decisions,
    String sensitiveAttribute,
    String positiveOutcome)
```

Analyzes fairness of process outcomes across demographic groups.

##### close

```java
public void close() throws IOException
```

Closes the facade and releases resources.

---

### ProcessMiningReport

**Package:** `org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade`

Immutable result container for process mining analysis.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `xesXml` | `String` | XES event log XML |
| `conformance` | `ConformanceResult` | Token replay fitness (null if no net provided) |
| `performance` | `PerformanceResult` | Flow time, throughput metrics |
| `variantFrequencies` | `Map<String, Long>` | Activity sequences ranked by frequency |
| `variantCount` | `int` | Number of distinct variants |
| `ocelJson` | `String` | OCEL 2.0 JSON format |
| `traceCount` | `int` | Number of traces analyzed |
| `specificationId` | `String` | Specification identifier |
| `analysisTime` | `Instant` | When analysis was performed |

---

### ConformanceResult

**Package:** `org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade`

```java
public record ConformanceResult(double fitness, String rawJson)
```

| Field | Type | Description |
|-------|------|-------------|
| `fitness` | `double` | Log fitness score in [0.0, 1.0] |
| `rawJson` | `String` | Full pm4py token replay JSON |

---

### PerformanceResult

**Package:** `org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade`

```java
public record PerformanceResult(
    int traceCount,
    double avgFlowTimeMs,
    double throughputPerHour,
    Map<String, Integer> activityCounts,
    String rawJson)
```

| Field | Type | Description |
|-------|------|-------------|
| `traceCount` | `int` | Number of traces in the log |
| `avgFlowTimeMs` | `double` | Average case flow time in milliseconds |
| `throughputPerHour` | `double` | Estimated throughput in cases/hour |
| `activityCounts` | `Map<String, Integer>` | Occurrence count per activity |
| `rawJson` | `String` | Full pm4py performance analysis JSON |

---

## Discovery Algorithms

### ProcessDiscoveryAlgorithm

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

Interface for process discovery algorithms.

```java
public interface ProcessDiscoveryAlgorithm {
    ProcessDiscoveryResult discover(ProcessMiningContext context) throws ProcessDiscoveryException;
    String getAlgorithmName();
    AlgorithmType getType();
    default boolean supportsIncrementalDiscovery() { return false; }
}
```

#### AlgorithmType Enum

```java
enum AlgorithmType {
    ALPHA,       // Alpha algorithm
    HEURISTIC,   // Heuristic miner
    INDUCTIVE,   // Inductive miner
    DFGBASED,    // Directly-follows graph based
    ILP          // Integer Linear Programming
}
```

#### ProcessMiningContext

```java
public static class ProcessMiningContext {
    private final Ocel2EventLog eventLog;
    private final AlgorithmType algorithm;
    private final double noiseThreshold;      // Default: 0.1
    private final double frequencyThreshold;  // Default: 0.8
    private final Set<String> activityFilter;
    private final ProcessMiningSettings settings;
}
```

##### Builder

```java
ProcessMiningContext context = new ProcessMiningContext.Builder()
    .eventLog(eventLog)
    .algorithm(AlgorithmType.HEURISTIC)
    .noiseThreshold(0.15)
    .frequencyThreshold(0.75)
    .activityFilter(Set.of("Start", "Review", "Approve"))
    .settings(new ProcessMiningSettings())
    .build();
```

---

### AlphaMiner

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

Implements van der Aalst's α-algorithm (2004) for discovering Petri nets.

#### Algorithm Steps

1. Extract unique activities (T_L)
2. Find start (T_I) and end (T_O) activities
3. Build directly-follows graph (>_L)
4. Derive causal dependencies (→_L)
5. Build parallelism (||_L) and conflict (#_L) relations
6. Find maximal place pairs
7. Construct WF-net

#### Complexity

- **Time:** O(|T|³) for footprint analysis
- **Space:** O(|Y|) for maximal place pairs

#### Usage

```java
AlphaMiner miner = new AlphaMiner();
ProcessDiscoveryResult result = miner.discover(context);

// Alpha guarantees fitness = 1.0 on discovery log
assert result.fitness() == 1.0;
```

---

### HeuristicMiner

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

Discovers process models based on frequency and dependency analysis.

#### Features

- Handles noise and infrequent behavior
- Configurable noise threshold
- Better for real-world logs

```java
HeuristicMiner miner = new HeuristicMiner();
ProcessDiscoveryResult result = miner.discover(
    new ProcessMiningContext.Builder()
        .eventLog(eventLog)
        .algorithm(AlgorithmType.HEURISTIC)
        .noiseThreshold(0.1)
        .build()
);
```

---

### InductiveMiner

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

Discovers hierarchical process trees by finding cuts in the DFG.

#### Features

- Guaranteed sound models (deadlock-free)
- Process tree representation
- Handles complex control flow

---

### DirectlyFollowsGraph

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

Represents activity execution patterns.

#### Methods

```java
public static DirectlyFollowsGraph discover(List<List<String>> traces)
public Set<String> getActivities()
public int getEdgeCount(String from, String to)
public String toJson()
```

---

## Event Export

### Ocel2Exporter

**Package:** `org.yawlfoundation.yawl.integration.processmining`

Exports YAWL events to OCEL 2.0 JSON format.

#### Methods

```java
public String exportWorkflowEvents(List<WorkflowEventRecord> events) throws IOException
public static WorkflowEventRecord fromXesEvent(String caseId, String activity, Instant timestamp)
```

#### WorkflowEventRecord

```java
public record WorkflowEventRecord(
    String eventId,
    String caseId,
    String workItemId,
    String activity,
    String resource,
    Instant timestamp,
    String eventType
)
```

#### Ocel2EventLog

```java
public static final class Ocel2EventLog {
    public Ocel2EventLog(List<Ocel2Event> events)
    public List<Ocel2Event> getEvents()
}
```

#### Ocel2Event

```java
public static final class Ocel2Event {
    public Ocel2Event(String id, String activity, Instant time,
                       Map<String, List<String>> objects, Map<String, Object> properties)
    public String getId()
    public String getActivity()
    public Instant getTime()
    public Map<String, List<String>> getObjects()
    public Map<String, Object> getProperties()
}
```

---

### EventLogExporter

**Package:** `org.yawlfoundation.yawl.integration.processmining`

Exports to XES format.

```java
public String exportSpecificationToXes(YSpecificationID specId, boolean withData)
    throws IOException
```

---

### PnmlExporter

**Package:** `org.yawlfoundation.yawl.integration.processmining`

Exports to PNML format.

```java
public static String netToPnml(YNet net)
```

---

## Synthesis

### YawlSpecSynthesizer

**Package:** `org.yawlfoundation.yawl.integration.processmining.synthesis`

Converts Petri nets to YAWL specifications.

#### Constructors

```java
public YawlSpecSynthesizer(String specUri, String specName)
```

#### Methods

```java
public String synthesize(PnmlProcess process)
public SynthesisResult synthesizeWithConformance(PnmlProcess process)
```

#### SynthesisResult

```java
public record SynthesisResult(
    String yawlXml,
    ConformanceScore conformanceScore,
    PnmlProcess sourceProcess,
    Duration synthesisTime,
    int tasksGenerated,
    int conditionsGenerated
)
```

---

## Conformance

### ConformanceAnalyzer

**Package:** `org.yawlfoundation.yawl.integration.processmining`

Token-based replay conformance checking.

```java
public ConformanceAnalyzer(Set<String> expectedActivities,
                           Set<String> expectedDirectlyFollows)

public ConformanceResult analyze(String xesXml)
```

#### ConformanceResult

```java
public static final class ConformanceResult {
    public final int traceCount;
    public final int fittingTraces;
    public final double fitness;
    public final Set<String> observedActivities;
    public final Set<String> deviatingTraces;
}
```

---

## Object-Centric Mining

### OcpmDiscovery

**Package:** `org.yawlfoundation.yawl.integration.processmining.ocpm`

Discovers Object-Centric Petri Nets.

```java
public OcpmResult discover(OcpmInput input)
```

#### OcpmResult

```java
public record OcpmResult(
    Set<String> objectTypes,
    Map<String, Set<String>> activitiesPerType,
    List<OcpmTransition> transitions,
    List<OcpmPlace> places
)
```

---

## Responsible AI

### FairnessAnalyzer

**Package:** `org.yawlfoundation.yawl.integration.processmining.responsibleai`

Detects demographic bias using the four-fifths rule.

```java
public static FairnessReport analyze(
    List<Map<String, String>> caseAttributes,
    List<Map<String, String>> decisions,
    String sensitiveAttribute,
    String positiveOutcome)
```

#### FairnessReport

```java
public record FairnessReport(
    double disparateImpact,
    boolean isFair,
    List<FairnessViolation> violations,
    Map<String, Double> selectionRates
)
```

---

## ProcessDiscoveryResult

**Package:** `org.yawlfoundation.yawl.integration.processmining`

```java
public class ProcessDiscoveryResult {
    public String algorithmName()
    public String processModelJson()
    public double fitness()
    public double precision()
    public int caseCount()
    public int activityCount()
    public Map<String, Long> activityFrequencies()
    public Instant discoveryTime()
}
```

---

## Error Types

### ProcessDiscoveryException

**Package:** `org.yawlfoundation.yawl.integration.processmining.discovery`

```java
public class ProcessDiscoveryException extends Exception {
    public ProcessDiscoveryException(String message)
    public ProcessDiscoveryException(String message, Throwable cause)
}
```

---

## Configuration

### ProcessMiningSettings

```java
public class ProcessMiningSettings {
    private boolean enablePruning = true;
    private boolean enableNoiseReduction = true;
    private boolean enableParallelProcessing = true;
    private int maxIterations = 100;
    private double convergenceThreshold = 0.001;
    private long timeoutMillis = 30000;
}
```

---

## Related Documentation

- **[Tutorial: Process Discovery Getting Started](../tutorials/process-discovery-getting-started.md)**
- **[How-To: Process Mining Analysis](../how-to/process-mining-analysis.md)**
- **[Explanation: Process Mining Architecture](../explanation/process-mining-architecture.md)**
