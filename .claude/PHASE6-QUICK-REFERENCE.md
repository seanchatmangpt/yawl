# Phase 6: Quick Reference — URIs, Patterns, Queries

**Purpose**: Fast lookup table for namespace URIs, RDF classes, and SPARQL templates
**Audience**: Engineers implementing Phase 6 components

---

## Namespace URIs

```
code:     http://yawl.org/code#           (Workflow, Activity, Variable, Method, GuardViolation)
data:     http://yawl.org/data#           (Table, Column, Database)
lineage:  http://yawl.org/lineage#        (DataAccess, LineageEdge, DataFlow)
exec:     http://yawl.org/execution#      (Case, execution state)
xsd:      http://www.w3.org/2001/XMLSchema#
rdf:      http://www.w3.org/1999/02/22-rdf-syntax-ns#
rdfs:     http://www.w3.org/2000/01/rdf-schema#
owl:      http://www.w3.org/2002/07/owl#
skos:     http://www.w3.org/2004/02/skos/core#
```

---

## RDF Classes Quick Reference

| Class | Prefix | Full URI | Purpose |
|-------|--------|----------|---------|
| Workflow | code: | code:Workflow | YAWL workflow specification |
| Activity | code: | code:Activity | Task/activity in workflow |
| Variable | code: | code:Variable | Workflow variable (case data) |
| Case | exec: | exec:Case | Workflow case instance |
| Table | data: | data:Table | External database table |
| Column | data: | data:Column | Database column |
| DataAccess | lineage: | lineage:DataAccess | Single data read/write |
| LineageEdge | lineage: | lineage:LineageEdge | Data flow producer→consumer |
| GuardViolation | code: | code:GuardViolation | H-Guards violation |
| Method | code: | code:Method | Java method |
| SourceFile | code: | code:SourceFile | Generated source file |
| HyperStandardsReceipt | code: | code:HyperStandardsReceipt | Validation report |

---

## Property Quick Reference

### Core Properties

| Property | Domain | Range | Cardinality | Indexed? |
|----------|--------|-------|-------------|----------|
| code:taskId | Activity | xsd:string | 1..1 | YES |
| code:specId | Workflow | xsd:string | 1..1 | YES |
| data:tableName | Table | xsd:string | 1..1 | YES |
| data:columnName | Column | xsd:string | 1..1 | YES |
| lineage:caseId | DataAccess | xsd:string | 1..1 | **YES** |
| lineage:timestamp | DataAccess | xsd:dateTime | 1..1 | **YES** |
| lineage:table | DataAccess | Table | 1..1 | **YES** |
| lineage:accessType | DataAccess | xsd:string | 1..1 | NO |
| exec:caseId | Case | xsd:string | 1..1 | YES |

---

## Relationship Quick Reference

| Property | Domain | Range | Meaning |
|----------|--------|-------|---------|
| code:reads | Activity | Table | Task reads from table |
| code:writes | Activity | Table | Task writes to table |
| code:executes_in | Activity | Workflow | Task belongs to workflow |
| code:hasTask | Workflow | Activity | Workflow has task |
| code:hasVariable | Workflow | Variable | Workflow has variable |
| data:hasColumn | Table | Column | Table has column |
| data:inTable | Column | Table | Column in table |
| lineage:activity | DataAccess | Activity | Access performed by task |
| lineage:producer | LineageEdge | Activity | Source task |
| lineage:consumer | LineageEdge | Activity | Target task |
| exec:ofWorkflow | Case | Workflow | Case instantiates workflow |

---

## SPARQL Query Templates

### Template 1: Find Workflows Accessing Table

```sparql
PREFIX code: <http://yawl.org/code#>
DATA: <http://yawl.org/data#>

SELECT DISTINCT ?workflow ?workflowId
WHERE {
  ?activity code:reads <data:Table_TABLENAME> .
  ?activity code:executes_in ?workflow .
  ?workflow code:specId ?workflowId .
}
```

**Parameters**: Replace `TABLENAME` with table identifier (e.g., `orders`, `customers`)

---

### Template 2: Case Data Lineage

```sparql
PREFIX lineage: <http://yawl.org/lineage#>
PREFIX data: <http://yawl.org/data#>
PREFIX code: <http://yawl.org/code#>

SELECT ?timestamp ?taskName ?tableAccessed ?accessType
WHERE {
  ?access lineage:caseId "CASEID" .
  ?access lineage:timestamp ?timestamp .
  ?access lineage:table ?table .
  ?table data:tableName ?tableAccessed .
  ?access lineage:accessType ?accessType .

  OPTIONAL {
    ?access lineage:activity ?task .
    ?task code:taskId ?taskName .
  }
}
ORDER BY ?timestamp
```

**Parameters**: Replace `CASEID` with case identifier (e.g., `C001`)

---

### Template 3: PII Access Audit

```sparql
PREFIX lineage: <http://yawl.org/lineage#>
PREFIX data: <http://yawl.org/data#>
PREFIX code: <http://yawl.org/code#>

SELECT ?timestamp ?caseId ?taskName ?columnName
WHERE {
  ?access lineage:timestamp ?timestamp ;
    lineage:caseId ?caseId ;
    lineage:columns ?colList ;
    lineage:activity ?task .

  ?task code:taskId ?taskName .
  ?colList rdf:first ?col .
  ?col data:columnName ?columnName ;
    data:isPII true .

  FILTER(?timestamp > NOW() - "PDURATION"^^xsd:duration)
}
ORDER BY DESC(?timestamp)
```

**Parameters**: Replace `PDURATION` with duration (e.g., `P1D` for 1 day, `P7D` for 7 days)

---

### Template 4: Task Execution Frequency

```sparql
PREFIX lineage: <http://yawl.org/lineage#>
PREFIX code: <http://yawl.org/code#>

SELECT ?taskName (COUNT(?access) as ?execCount)
WHERE {
  ?access lineage:activity ?task ;
    lineage:table <data:Table_TABLENAME> ;
    lineage:accessType "read" .

  ?task code:taskId ?taskName .
}
GROUP BY ?taskName
ORDER BY DESC(?execCount)
```

**Parameters**: Replace `TABLENAME` with table identifier

---

### Template 5: H-Guards Violations by Pattern

```sparql
PREFIX code: <http://yawl.org/code#>

SELECT ?file ?lineNumber ?content ?fixGuidance
WHERE {
  ?violation a code:GuardViolation ;
    code:pattern "PATTERN" ;
    code:file ?file ;
    code:lineNumber ?lineNumber ;
    code:content ?content ;
    code:fixGuidance ?fixGuidance .
}
ORDER BY ?file ?lineNumber
```

**Parameters**: Replace `PATTERN` with pattern name (H_TODO, H_MOCK, H_STUB, H_EMPTY, H_FALLBACK, H_LIE, H_SILENT)

---

## Example RDF Triples

### Workflow Definition

```turtle
@prefix code: <http://yawl.org/code#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

code:OrderProcess a code:Workflow ;
  code:specId "urn:yawl:OrderProcess:0.1" ;
  code:version "0.1" ;
  code:created "2026-02-28T14:00:00Z"^^xsd:dateTime ;
  code:hasTask code:CheckCredit ;
  code:hasTask code:InvoiceGeneration ;
  rdfs:label "Order Processing Workflow" .
```

### Task Definition

```turtle
code:CheckCredit a code:Activity ;
  code:taskId "CheckCredit" ;
  code:taskType "atomic" ;
  code:reads data:Table_orders ;
  code:writes data:Table_invoices ;
  code:executes_in code:OrderProcess ;
  code:documentation "Validate customer credit limit" ;
  code:schema_version "3.1.0" .
```

### Table Definition

```turtle
data:Table_orders a data:Table ;
  data:tableName "orders" ;
  data:database "production_db" ;
  data:hasColumn data:Column_order_id ;
  data:hasColumn data:Column_customer_id ;
  data:rowCount 125478 ;
  data:lastModified "2026-02-28T13:45:22Z"^^xsd:dateTime ;
  rdfs:label "Orders Master Table" .
```

### Column Definition

```turtle
data:Column_customer_id a data:Column ;
  data:columnName "customer_id" ;
  data:inTable data:Table_orders ;
  data:dataType "bigint" ;
  data:isNullable false ;
  data:isPII true ;
  data:description "Unique customer identifier" .
```

### Case Execution

```turtle
exec:Case_C001 a exec:Case ;
  exec:caseId "C001" ;
  exec:ofWorkflow code:OrderProcess ;
  exec:createdAt "2026-02-28T14:15:00Z"^^xsd:dateTime ;
  exec:completedAt "2026-02-28T14:32:45Z"^^xsd:dateTime ;
  exec:status "complete" ;
  exec:accessesTable data:Table_orders ;
  exec:accessesTable data:Table_invoices .
```

### Data Access Event

```turtle
lineage:Access_C001_CheckCredit_read a lineage:DataAccess ;
  lineage:caseId "C001" ;
  lineage:activity code:CheckCredit ;
  lineage:timestamp "2026-02-28T14:20:15Z"^^xsd:dateTime ;
  lineage:accessType "read" ;
  lineage:table data:Table_orders ;
  lineage:rowsAffected 1 ;
  lineage:dataHash "sha256_abc123def456" ;
  lineage:sourceIp "192.168.1.100" .
```

### Guard Violation

```turtle
code:GuardViolation_TODO_y7a9b8 a code:GuardViolation ;
  code:pattern "H_TODO" ;
  code:severity "FAIL" ;
  code:file "/home/user/yawl/generated/YWorkItem.java" ;
  code:lineNumber 427 ;
  code:content "// TODO: Add deadlock detection" ;
  code:fixGuidance "Implement real logic or throw UnsupportedOperationException" ;
  code:discoveredAt "2026-02-28T14:32:15Z"^^xsd:dateTime ;
  code:linkedToMethod code:Method_YWorkItem_getStatus .
```

---

## H-Guards Pattern Mapping

| Pattern | Regex/Detection | Example | Fix Guidance |
|---------|-----------------|---------|--------------|
| H_TODO | `//\s*(TODO\|FIXME\|XXX\|HACK)` | `// TODO: implement` | Implement real logic |
| H_MOCK | `(mock\|stub\|fake\|demo)[A-Z]` | `class MockService` | Delete mock or implement |
| H_STUB | Empty return + non-void | `return null;` | Implement or throw |
| H_EMPTY | Empty body `{}` | `public void init() {}` | Implement or throw |
| H_FALLBACK | Silent catch return | `catch (...) { return []; }` | Propagate exception |
| H_LIE | Code ≠ Javadoc | Doc says void, returns val | Update code/docs |
| H_SILENT | Log instead throw | `log.error("not impl")` | Throw exception |

---

## Data Model Relationships

```
Workflow
├─ code:hasTask → Activity
│  ├─ code:reads → Table
│  └─ code:writes → Table
├─ code:hasVariable → Variable
└─ code:executes_in ← Activity (inverse)

Case
├─ exec:ofWorkflow → Workflow (1:1)
├─ exec:accessesTable → Table (0..*)
└─ (contains) → DataAccess

DataAccess
├─ lineage:activity → Activity
├─ lineage:table → Table
│  └─ data:hasColumn → Column (list)
└─ lineage:timestamp

LineageEdge
├─ lineage:producer → Activity
├─ lineage:consumer → Activity
└─ lineage:dataFlows → DataFlow (list)

Table
├─ data:hasColumn → Column (0..*)
├─ data:readBy → Activity (0..*)
└─ data:writtenBy → Activity (0..*)

Column
├─ data:inTable → Table (1:1)
├─ data:isPII (xsd:boolean)
└─ data:dataType (xsd:string)

GuardViolation
├─ code:linkedToMethod → Method
├─ code:discoveredBy → Validator
└─ code:pattern ∈ {H_TODO, H_MOCK, ...}

Method
├─ code:file
├─ code:lineStart, code:lineEnd
└─ code:contains → GuardViolation (0..*)
```

---

## Index Configuration (RDF4J)

```properties
# Recommended indexes for Phase 6 queries
rdf4j.indexes=spoc,posc,cosp,cspo

# Index optimization for common patterns
#  lineage:caseId → posc (predicate first, then object)
#  lineage:table → posc
#  code:taskId → psoc (predicate first, then subject)
#  lineage:timestamp → spoc (for range queries)

# Query cache configuration
rdf4j.query.cache.size=1000
rdf4j.query.cache.ttl=60000  # 1 minute
```

---

## Common SPARQL Mistakes

### Wrong: Missing PREFIX declaration

```sparql
-- ERROR: lineage is not defined
SELECT * WHERE {
  ?x lineage:caseId "C001" .
}
```

### Correct: Declare PREFIX

```sparql
PREFIX lineage: <http://yawl.org/lineage#>

SELECT * WHERE {
  ?x lineage:caseId "C001" .
}
```

---

### Wrong: Using string instead of URI

```sparql
-- ERROR: "C001" is a string, not a URI
SELECT * WHERE {
  ?case exec:caseId "C001" ;
    exec:ofWorkflow "OrderProcess" .  -- WRONG
}
```

### Correct: Distinguish URIs from literals

```sparql
PREFIX exec: <http://yawl.org/execution#>
PREFIX code: <http://yawl.org/code#>

SELECT * WHERE {
  ?case exec:caseId "C001" ;          -- Literal (quoted)
    exec:ofWorkflow code:OrderProcess . -- URI (unquoted with prefix)
}
```

---

### Wrong: Forgetting OPTIONAL for missing properties

```sparql
-- ERROR: If task is missing, the whole row is skipped
SELECT ?caseId ?taskName WHERE {
  ?access lineage:caseId ?caseId ;
    lineage:activity ?task ;
    lineage:table ?table .
  ?task code:taskId ?taskName .
}
```

### Correct: Use OPTIONAL for optional properties

```sparql
PREFIX lineage: <http://yawl.org/lineage#>
PREFIX code: <http://yawl.org/code#>

SELECT ?caseId ?taskName WHERE {
  ?access lineage:caseId ?caseId ;
    lineage:table ?table .

  OPTIONAL {
    ?access lineage:activity ?task .
    ?task code:taskId ?taskName .
  }
}
```

---

## Java Integration Snippets

### Load Ontology

```java
RdfGraphStore store = new RdfGraphStore();
store.loadOntology("/home/user/yawl/schema/yawl-lineage-ontology.ttl");
```

### Execute Query and Parse Results

```java
String query = """
    PREFIX lineage: <http://yawl.org/lineage#>
    SELECT ?caseId
    WHERE { ?x lineage:caseId ?caseId . }
    """;

List<Map<String, String>> results = store.executeSparql(query);

for (Map<String, String> row : results) {
    String caseId = row.get("caseId");
    System.out.println("Case: " + caseId);
}
```

### Add Triples from Code

```java
String turtle = """
    @prefix lineage: <http://yawl.org/lineage#> .
    @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

    lineage:Access_C001_1 a lineage:DataAccess ;
      lineage:caseId "C001" ;
      lineage:timestamp "2026-02-28T14:20:15Z"^^xsd:dateTime .
    """;

store.addTriples(turtle);
```

### Get Graph Statistics

```java
RdfGraphStore.GraphStatistics stats = store.getStatistics();
System.out.printf("Triples: %d, Memory: %.2f MB%n",
    stats.tripleCount(), stats.memoryMB());
```

---

## Maven Configuration

```xml
<properties>
  <jena.version>5.0.0</jena.version>
</properties>

<dependency>
  <groupId>org.apache.jena</groupId>
  <artifactId>apache-jena-libs</artifactId>
  <version>${jena.version}</version>
  <type>pom</type>
</dependency>

<dependency>
  <groupId>org.apache.jena</groupId>
  <artifactId>jena-arq</artifactId>
  <version>${jena.version}</version>
</dependency>
```

---

## Performance Targets

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Enqueue event | <1μs | Mean latency |
| Batch process | <50ms | Per 100 events |
| SPARQL query | <200ms | P95 latency |
| Index lookup | <20ms | By caseId |
| Store startup | <500ms | Time to load 1M triples |
| Memory overhead | <100MB | For 1M triples |

---

## File Locations

| Component | File | Purpose |
|-----------|------|---------|
| Ontology | `/home/user/yawl/schema/yawl-lineage-ontology.ttl` | RDF schema definition |
| Graph Store | `org.yawlfoundation.yawl.observability.lineage.RdfGraphStore` | In-memory RDF store |
| Event Broker | `org.yawlfoundation.yawl.observability.lineage.LineageEventBroker` | Async batching |
| Tracker | `org.yawlfoundation.yawl.elements.data.contract.DataLineageTrackerImpl` | Integration point |
| Validator | `org.yawlfoundation.yawl.validation.HyperStandardsValidator` | H-Guards receipt export |

---

**Document End**: Phase 6 quick reference complete
