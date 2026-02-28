# Phase 6: Implementation Guide — RDF Integration & H-Guards Schema

**Status**: Step-by-step integration guide for architects and engineers
**Date**: 2026-02-28
**Audience**: Backend engineers implementing Phase 6 components

---

## Quick Start: 3-Step Integration

### Step 1: Add RDF Dependencies (Maven)

**File**: `/home/user/yawl/pom.xml` (or `yawl-monitoring/pom.xml`)

```xml
<properties>
  <jena.version>5.0.0</jena.version>
  <rdf4j.version>4.3.1</rdf4j.version>
  <slf4j.version>2.0.11</slf4j.version>
</properties>

<dependencies>
  <!-- Apache Jena for RDF/SPARQL -->
  <dependency>
    <groupId>org.apache.jena</groupId>
    <artifactId>apache-jena-libs</artifactId>
    <version>${jena.version}</version>
    <type>pom</type>
  </dependency>

  <!-- RDF4J for persistent store option -->
  <dependency>
    <groupId>org.eclipse.rdf4j</groupId>
    <artifactId>rdf4j-client</artifactId>
    <version>${rdf4j.version}</version>
  </dependency>

  <!-- Turtle parsing -->
  <dependency>
    <groupId>org.apache.jena</groupId>
    <artifactId>jena-arq</artifactId>
    <version>${jena.version}</version>
  </dependency>
</dependencies>
```

**Verify**: `mvn dependency:tree | grep jena`

---

### Step 2: Create RDF Graph Store Component

**File**: `/home/user/yawl/src/org/yawlfoundation/yawl/observability/lineage/RdfGraphStore.java`

```java
package org.yawlfoundation.yawl.observability.lineage;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory RDF graph store for data lineage.
 *
 * Thread-safe implementation using Jena's Dataset.
 * For production use cases with >1M triples, consider RDF4J persistent store.
 */
public class RdfGraphStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RdfGraphStore.class);

    private final Dataset dataset;
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Map<String, Long> queryCache = new ConcurrentHashMap<>();

    /**
     * Create in-memory RDF store.
     */
    public RdfGraphStore() {
        this.dataset = DatasetFactory.createTxnMem();
        log.info("RDF graph store initialized (in-memory)");
    }

    /**
     * Add RDF triples in Turtle format.
     *
     * @param turtleRdf Turtle-formatted RDF string
     * @throws IOException if parsing fails
     */
    public void addTriples(String turtleRdf) throws IOException {
        rwLock.writeLock().lock();
        try {
            Model model = ModelFactory.createDefaultModel();
            StringReader reader = new StringReader(turtleRdf);
            model.read(reader, null, "TURTLE");

            dataset.getDefaultModel().add(model);
            log.debug("Added {} triples to graph store", model.size());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Execute SPARQL query.
     *
     * @param sparqlQuery SELECT or ASK query
     * @return List of result rows (Map of variable → value)
     */
    public List<Map<String, String>> executeSparql(String sparqlQuery) {
        rwLock.readLock().lock();
        try {
            QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, dataset);
            ResultSet results = qexec.execSelect();

            List<Map<String, String>> rows = new ArrayList<>();
            while (results.hasNext()) {
                QuerySolution soln = results.next();
                Map<String, String> row = new HashMap<>();

                for (String var : results.getResultVars()) {
                    RDFNode node = soln.get(var);
                    if (node != null) {
                        row.put(var, node.toString());
                    }
                }

                rows.add(row);
            }

            queryCache.put(hashQuery(sparqlQuery), System.currentTimeMillis());
            return rows;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get graph statistics.
     */
    public GraphStatistics getStatistics() {
        rwLock.readLock().lock();
        try {
            long tripleCount = dataset.getDefaultModel().size();
            long memUsage = estimateMemoryUsage(tripleCount);

            return new GraphStatistics(tripleCount, memUsage, dataset.getDefaultModel()
                    .listSubjects().toList().size());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Load ontology from Turtle file.
     */
    public void loadOntology(String turtleFilePath) throws IOException {
        String turtleContent = new String(java.nio.file.Files
                .readAllBytes(java.nio.file.Paths.get(turtleFilePath)));
        addTriples(turtleContent);
        log.info("Ontology loaded from {}", turtleFilePath);
    }

    private String hashQuery(String query) {
        return String.valueOf(query.hashCode());
    }

    private long estimateMemoryUsage(long tripleCount) {
        // Rough estimate: ~100 bytes per triple
        return tripleCount * 100;
    }

    @Override
    public void close() throws Exception {
        dataset.close();
        log.info("RDF graph store closed");
    }

    /**
     * Result object for graph statistics.
     */
    public record GraphStatistics(long tripleCount, long memoryBytes, long subjectCount) {
        public double memoryMB() {
            return memoryBytes / (1024.0 * 1024.0);
        }
    }
}
```

**Test**:
```bash
mvn test -Dtest=RdfGraphStoreTest
```

---

### Step 3: Integrate with DataLineageTrackerImpl

**File**: `/home/user/yawl/src/org/yawlfoundation/yawl/elements/data/contract/DataLineageTrackerImpl.java`

**Addition** (around line 50):

```java
public class DataLineageTrackerImpl implements DataLineageTracker {

    private static final Logger log = LoggerFactory.getLogger(DataLineageTrackerImpl.class);

    // ... existing fields ...

    // NEW: RDF graph store
    private final RdfGraphStore rdfStore;
    private final LineageEventBroker eventBroker;

    /**
     * Constructor with RDF integration.
     *
     * @param rdfStore initialized RDF graph store
     * @param schemaService service for schema lookups
     */
    public DataLineageTrackerImpl(RdfGraphStore rdfStore, DataModellingBridge schemaService) {
        this.rdfStore = rdfStore;
        this.eventBroker = new LineageEventBroker(rdfStore, schemaService);
        log.info("DataLineageTracker initialized with RDF backend");
    }

    @Override
    public void recordCaseStart(YSpecificationID specId, String caseId, String sourceTable, Element data) {
        String dataHash = hashElement(data);
        DataLineageRecord record = new DataLineageRecord(
            System.currentTimeMillis(),
            caseId,
            specId.getIdentifier(),
            null,
            sourceTable,
            null,
            dataHash
        );

        addRecord(record);

        // NEW: Async enrich and store in RDF
        eventBroker.enqueueEvent(new LineageEvent(
            "caseStart",
            caseId,
            specId.getIdentifier(),
            null,
            sourceTable,
            null,
            System.currentTimeMillis()
        ));

        log.debug("Recorded case start: caseId={}, sourceTable={}", caseId, sourceTable);
    }

    // ... rest of implementation ...
}
```

---

## Detailed Component Design

### Component 1: LineageEventBroker

**Purpose**: Async batching and schema enrichment of lineage events

**File**: `/home/user/yawl/src/org/yawlfoundation/yawl/observability/lineage/LineageEventBroker.java`

```java
package org.yawlfoundation.yawl.observability.lineage;

import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async event broker for lineage data.
 *
 * Architecture:
 * - Enqueue events: <1μs latency
 * - Batch processing: every 500ms or 100 events
 * - Schema enrichment: lookup ODCS column metadata
 * - RDF generation: convert to Turtle format
 * - Store writes: async to RDF graph
 */
public class LineageEventBroker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LineageEventBroker.class);

    private final RdfGraphStore rdfStore;
    private final DataModellingBridge schemaBridge;
    private final Queue<LineageEvent> eventQueue;
    private final ScheduledExecutorService executor;

    // Configuration
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_TIMEOUT_MS = 500;
    private static final int QUEUE_CAPACITY = 10_000;

    // Metrics
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);

    public LineageEventBroker(RdfGraphStore rdfStore, DataModellingBridge schemaBridge) {
        this.rdfStore = rdfStore;
        this.schemaBridge = schemaBridge;
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newScheduledThreadPool(2);

        // Schedule batch processor
        executor.scheduleAtFixedRate(this::processBatch, BATCH_TIMEOUT_MS,
                BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        log.info("LineageEventBroker initialized (batch size: {}, timeout: {}ms)",
                BATCH_SIZE, BATCH_TIMEOUT_MS);
    }

    /**
     * Enqueue a lineage event.
     *
     * @param event the event to queue
     * @throws IllegalStateException if queue is full
     */
    public void enqueueEvent(LineageEvent event) {
        if (eventQueue.size() >= QUEUE_CAPACITY) {
            throw new IllegalStateException("Lineage event queue full (capacity: " + QUEUE_CAPACITY + ")");
        }

        if (!eventQueue.offer(event)) {
            log.warn("Failed to enqueue lineage event (queue may be full)");
        }
    }

    /**
     * Process batch of queued events.
     * Called periodically by scheduler.
     */
    void processBatch() {
        List<LineageEvent> batch = new ArrayList<>();

        // Drain up to BATCH_SIZE events from queue
        for (int i = 0; i < BATCH_SIZE && !eventQueue.isEmpty(); i++) {
            LineageEvent event = eventQueue.poll();
            if (event != null) {
                batch.add(event);
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        // Process batch asynchronously
        executor.submit(() -> processBatchAsync(batch));
    }

    /**
     * Process batch asynchronously.
     */
    private void processBatchAsync(List<LineageEvent> batch) {
        try {
            // Dedup events (same caseId + table + timestamp within 100ms)
            Map<String, LineageEvent> dedupMap = new LinkedHashMap<>();
            for (LineageEvent event : batch) {
                String key = event.caseId() + ":" + event.sourceTable() + ":" +
                        (event.timestamp() / 100) * 100;  // 100ms buckets
                dedupMap.putIfAbsent(key, event);
            }

            List<LineageEvent> dedupedBatch = new ArrayList<>(dedupMap.values());

            // Enrich with schema metadata
            List<String> rdfTriples = new ArrayList<>();
            for (LineageEvent event : dedupedBatch) {
                String enriched = enrichAndConvertToRdf(event);
                if (enriched != null) {
                    rdfTriples.add(enriched);
                }
            }

            // Write to RDF store
            if (!rdfTriples.isEmpty()) {
                String combinedRdf = String.join("\n", rdfTriples);
                rdfStore.addTriples(combinedRdf);
            }

            eventsProcessed.addAndGet(dedupedBatch.size());
            batchesProcessed.incrementAndGet();

            log.debug("Processed batch: {} events -> {} RDF statements",
                    dedupedBatch.size(), rdfTriples.size());

        } catch (Exception e) {
            log.error("Error processing lineage event batch", e);
        }
    }

    /**
     * Enrich event with schema metadata and convert to RDF.
     */
    private String enrichAndConvertToRdf(LineageEvent event) {
        try {
            StringBuilder rdf = new StringBuilder();
            rdf.append("@prefix lineage: <http://yawl.org/lineage#> .\n");
            rdf.append("@prefix code: <http://yawl.org/code#> .\n");
            rdf.append("@prefix data: <http://yawl.org/data#> .\n");
            rdf.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n");

            String accessId = "lineage:Access_" + event.caseId() + "_" +
                    System.currentTimeMillis() + "_" + UUID.randomUUID();

            rdf.append(accessId).append(" a lineage:DataAccess ;\n");
            rdf.append("  lineage:caseId \"").append(event.caseId()).append("\" ;\n");
            rdf.append("  lineage:timestamp \"").append(event.timestamp())
                    .append("\"^^xsd:long ;\n");
            rdf.append("  lineage:accessType \"").append(event.eventType()).append("\" ;\n");
            rdf.append("  lineage:table data:Table_").append(sanitizeName(event.sourceTable()))
                    .append(" ;\n");

            // TODO: Lookup column metadata from DataModellingBridge
            // columnMetadata = schemaBridge.getColumnMetadata(event.sourceTable(), "");
            // Add to RDF: lineage:columns [...]

            rdf.append("  lineage:specId \"").append(event.specId()).append("\" .\n");

            return rdf.toString();

        } catch (Exception e) {
            log.warn("Failed to enrich and convert lineage event to RDF", e);
            return null;
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Get broker metrics.
     */
    public BrokerMetrics getMetrics() {
        return new BrokerMetrics(
            eventsProcessed.get(),
            batchesProcessed.get(),
            eventQueue.size()
        );
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        log.info("LineageEventBroker closed");
    }

    /**
     * Lineage event record.
     */
    public record LineageEvent(
        String eventType,      // "caseStart", "taskExecution", "caseCompletion"
        String caseId,
        String specId,
        String taskName,       // null for case events
        String sourceTable,
        String targetTable,
        long timestamp
    ) {}

    /**
     * Broker metrics.
     */
    public record BrokerMetrics(
        long eventsProcessed,
        long batchesProcessed,
        int queueSize
    ) {}
}
```

---

## Integration with H-Guards Validation

### Enhanced HyperStandardsValidator

**File**: `/home/user/yawl/src/org/yawlfoundation/yawl/validation/HyperStandardsValidator.java`

**Key additions**:

```java
public class HyperStandardsValidator {

    private final RdfGraphStore rdfStore;
    private final GuardReceipt receipt;

    /**
     * Generate RDF receipt from validation results.
     */
    public GuardReceipt validateAndExportRdf(Path emitDir) throws IOException {
        // ... existing validation logic ...

        // Convert receipt to RDF
        String receiptRdf = convertReceiptToRdf(receipt);

        // Store in RDF graph
        rdfStore.addTriples(receiptRdf);

        return receipt;
    }

    private String convertReceiptToRdf(GuardReceipt receipt) {
        StringBuilder rdf = new StringBuilder();
        rdf.append("@prefix code: <http://yawl.org/code#> .\n");
        rdf.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n\n");

        String receiptId = "code:HyperStandardsReceipt_" + System.currentTimeMillis();

        rdf.append(receiptId).append(" a code:HyperStandardsReceipt ;\n");
        rdf.append("  code:phase \"H-Guards\" ;\n");
        rdf.append("  code:timestamp \"").append(Instant.now()).append("\"^^xsd:dateTime ;\n");
        rdf.append("  code:filesScanned ").append(receipt.getFilesScanned()).append(" ;\n");
        rdf.append("  code:totalViolations ").append(receipt.getViolations().size()).append(" ;\n");
        rdf.append("  code:status \"").append(receipt.getStatus()).append("\" ;\n");

        // Add violations
        if (!receipt.getViolations().isEmpty()) {
            rdf.append("  code:violations [\n");
            boolean first = true;
            for (GuardViolation violation : receipt.getViolations()) {
                if (!first) rdf.append(";\n");
                rdf.append("    rdf:first ").append(violationToRdf(violation));
                first = false;
            }
            rdf.append("\n  ] ;\n");
        }

        // Add summary
        rdf.append("  code:summary [\n");
        rdf.append("    code:h_todo_count ").append(countByPattern(receipt, "H_TODO")).append(" ;\n");
        rdf.append("    code:h_mock_count ").append(countByPattern(receipt, "H_MOCK")).append(" ;\n");
        // ... more patterns ...
        rdf.append("  ] .\n");

        return rdf.toString();
    }

    private String violationToRdf(GuardViolation v) {
        return "code:GuardViolation_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

---

## SPARQL Query Examples for Integration

### Query: Case Lineage Timeline

```sparql
PREFIX lineage: <http://yawl.org/lineage#>
PREFIX data: <http://yawl.org/data#>
PREFIX code: <http://yawl.org/code#>

# Query: Get complete timeline for case C001
SELECT ?timestamp ?eventType ?table ?taskName
WHERE {
  ?access lineage:caseId "C001" ;
    lineage:timestamp ?timestamp ;
    lineage:accessType ?eventType ;
    lineage:table ?tableResource .

  ?tableResource data:tableName ?table .

  OPTIONAL {
    ?access lineage:activity ?task .
    ?task code:taskId ?taskName .
  }
}
ORDER BY ?timestamp
```

### Query: H-Guards Violation Summary

```sparql
PREFIX code: <http://yawl.org/code#>

# Query: Get all H_TODO violations
SELECT ?file ?line ?content ?fixGuidance
WHERE {
  ?violation a code:GuardViolation ;
    code:pattern "H_TODO" ;
    code:file ?file ;
    code:lineNumber ?line ;
    code:content ?content ;
    code:fixGuidance ?fixGuidance .
}
ORDER BY ?file ?line
```

---

## Testing Strategy

### Unit Tests

**File**: `/home/user/yawl/src/test/org/yawlfoundation/yawl/observability/lineage/RdfGraphStoreTest.java`

```java
class RdfGraphStoreTest {

    private RdfGraphStore store;

    @BeforeEach
    void setup() {
        store = new RdfGraphStore();
    }

    @Test
    void testAddTriples() throws IOException {
        String turtle = """
            @prefix ex: <http://example.org/> .
            ex:subject1 a ex:Class ;
              ex:property "value" .
            """;

        store.addTriples(turtle);
        var stats = store.getStatistics();

        assertEquals(2, stats.tripleCount()); // 2 triples
    }

    @Test
    void testSparqlQuery() throws IOException {
        String turtle = """
            @prefix data: <http://yawl.org/data#> .
            data:Table_orders data:tableName "orders" ;
              data:hasColumn data:Column_id .
            """;

        store.addTriples(turtle);

        String query = """
            PREFIX data: <http://yawl.org/data#>
            SELECT ?tableName
            WHERE {
              ?table data:tableName ?tableName .
            }
            """;

        var results = store.executeSparql(query);
        assertEquals(1, results.size());
        assertEquals("orders", results.get(0).get("tableName"));
    }

    @AfterEach
    void cleanup() throws Exception {
        store.close();
    }
}
```

### Integration Tests

**File**: `/home/user/yawl/src/test/org/yawlfoundation/yawl/observability/lineage/LineageIntegrationTest.java`

```java
class LineageIntegrationTest {

    private RdfGraphStore rdfStore;
    private LineageEventBroker broker;
    private DataModellingBridge schema;

    @BeforeEach
    void setup() {
        rdfStore = new RdfGraphStore();
        schema = new DataModellingBridge();
        broker = new LineageEventBroker(rdfStore, schema);
    }

    @Test
    void testCaseStartToCompletion() throws Exception {
        // Enqueue: case start
        broker.enqueueEvent(new LineageEvent(
            "caseStart", "C001", "spec:Order:0.1", null,
            "orders", null, System.currentTimeMillis()
        ));

        // Enqueue: task execution
        broker.enqueueEvent(new LineageEvent(
            "taskExecution", "C001", "spec:Order:0.1", "CheckCredit",
            "orders", "invoices", System.currentTimeMillis() + 1000
        ));

        // Wait for batch processing
        Thread.sleep(1000);

        // Query: Get case timeline
        String query = """
            PREFIX lineage: <http://yawl.org/lineage#>
            SELECT (COUNT(?access) as ?accessCount)
            WHERE {
              ?access lineage:caseId "C001" .
            }
            """;

        var results = rdfStore.executeSparql(query);
        assertEquals(1, results.size());
        assertEquals("2", results.get(0).get("accessCount"));
    }

    @AfterEach
    void cleanup() throws Exception {
        broker.close();
        rdfStore.close();
    }
}
```

---

## Performance Benchmarks

### Expected Results

```
Operation                   Latency      Throughput
────────────────────────────────────────────────────
Enqueue event               <1μs         100K events/sec
Batch process (100 events)  <50ms        20K/sec batches
SPARQL query (impact)       <200ms       5 QPS
H-Guards validation         <5s/file     8 files/sec
RDF store lookup (index)    <20ms        50 lookups/sec
Memory (1M triples)         ~100MB       -
```

### Load Test Script

```bash
# Generate 100K synthetic lineage events
java -cp target/classes \
  org.yawlfoundation.yawl.observability.lineage.BenchmarkTool \
  --events 100000 \
  --batches 100 \
  --output benchmark-results.json

# Parse results
cat benchmark-results.json | jq '.performance'
```

---

## Troubleshooting

### Issue: RDF Store Out of Memory

**Symptom**: `java.lang.OutOfMemoryError: GC overhead limit exceeded`

**Solution**:
```bash
# Increase heap
export JAVA_OPTS="-Xmx2g -XX:+UseG1GC"

# Or switch to persistent store (RDF4J)
# See: yawl-lineage-ontology.ttl loading with RDF4J backend
```

### Issue: SPARQL Query Timeout

**Symptom**: Query hangs for >5 seconds

**Solution**:
```sparql
-- Add LIMIT and use indexed properties
SELECT ?x WHERE {
  ?access lineage:caseId "C001" .  -- indexed property
  ?access lineage:table ?table .
}
LIMIT 1000
```

### Issue: Duplicate Triples

**Symptom**: Query results doubled after restart

**Solution**: Enable transaction support in RdfGraphStore
```java
dataset.begin(ReadWrite.WRITE);
try {
  dataset.getDefaultModel().add(model);
  dataset.commit();
} finally {
  dataset.end();
}
```

---

## Deployment Checklist

- [ ] Add Jena/RDF4J dependencies to pom.xml
- [ ] Create RdfGraphStore class
- [ ] Create LineageEventBroker class
- [ ] Load ontology: `rdfStore.loadOntology("schema/yawl-lineage-ontology.ttl")`
- [ ] Integrate with DataLineageTrackerImpl
- [ ] Write unit tests (RdfGraphStoreTest)
- [ ] Write integration tests (LineageIntegrationTest)
- [ ] Run performance benchmarks
- [ ] Update monitoring: export RDF store stats to Prometheus
- [ ] Document SPARQL queries in API docs
- [ ] Update operational runbooks

---

## References

**Apache Jena**:
- Query API: https://jena.apache.org/documentation/query/
- TDB2 Store: https://jena.apache.org/documentation/tdb2/

**RDF4J**:
- Repository API: https://rdf4j.org/documentation/
- SPARQL Endpoint: https://rdf4j.org/documentation/server/

**W3C Standards**:
- SPARQL 1.1: https://www.w3.org/TR/sparql11-query/
- RDF Turtle: https://www.w3.org/TR/turtle/

**YAWL Codebase**:
- DataLineageTracker: `/home/user/yawl/src/org/yawlfoundation/yawl/elements/data/contract/DataLineageTracker.java`
- DataModellingBridge: `/home/user/yawl/src/org/yawlfoundation/yawl/datamodelling/DataModellingBridge.java`

---

**Next**: Run Phase 6.1 team sprint — RDF Graph Store implementation (Week 1)
