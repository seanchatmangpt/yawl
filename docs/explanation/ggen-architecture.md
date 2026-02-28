# Explanation: ggen Code Generation Architecture

**Why does ggen exist?** Template-based code generation produces non-deterministic, non-traceable output. **ggen** uses semantic fact extraction (RDF + SPARQL) to ensure every generated line of code is justified by facts and queries.

---

## The Problem with Traditional Code Generators

Traditional code generators (e.g., Apache Velocity, Freemarker) work like this:

```
Source Code → Template → Generated Code
              (string-based)
```

Problems:

1. **Non-deterministic**: Same source code might produce different output if template uses `random()` or timestamps
2. **Non-traceable**: No visibility into why a specific artifact was generated
3. **Difficult to debug**: Hard to find which template statement generated which output
4. **Schema violations**: Easy to accidentally generate invalid code

---

## ggen's Philosophy: Semantic Generation

ggen inverts this:

```
Source Code (RDF) → SPARQL Extraction → Template → Generated Code
  (facts)         (deterministic)      (Tera)      (justified)
```

Key insight: **Code generation should be as deterministic as a SQL query.**

---

## Architecture Layers

### Layer 1: RDF Knowledge Base

All workflow knowledge is represented as **Resource Description Framework (RDF)** triples.

Example workflow in RDF (Turtle format):

```turtle
@prefix : <http://example.com/workflow#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

:LoanWorkflow a :Workflow ;
    rdfs:label "Loan Processing" ;
    :startCondition :StartCondition ;
    :endCondition :EndCondition .

:Receive a :Task ;
    rdfs:label "Receive Application" ;
    :flowsTo :Review .

:Review a :Task ;
    rdfs:label "Review Application" ;
    :flowsTo :Decision .

:Decision a :Condition ;
    rdfs:label "Is Eligible?" ;
    :trueFlow :Approve ;
    :falseFlow :Reject .

:Approve a :Task ;
    rdfs:label "Approve Loan" .

:Reject a :Task ;
    rdfs:label "Reject Loan" .
```

**Advantages**:

- **Fact-based**: Every statement is a fact, not procedural logic
- **Language-agnostic**: RDF can represent any domain model
- **Queryable**: SPARQL can extract any subset of facts
- **Composable**: Multiple RDF files can be merged

### Layer 2: SPARQL Query Execution

SPARQL (**SPARQL Protocol and RDF Query Language**) is like SQL for RDF graphs.

Example: Find all tasks and their successors

```sparql
PREFIX : <http://example.com/workflow#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?taskId ?label ?successor
WHERE {
    ?taskId a :Task ;
            rdfs:label ?label ;
            :flowsTo ?successor .
}
ORDER BY ?taskId
```

Result (JSON):

```json
[
  {"taskId": "Receive", "label": "Receive Application", "successor": "Review"},
  {"taskId": "Review", "label": "Review Application", "successor": "Decision"}
]
```

**Why SPARQL?**

- **Deterministic**: Same RDF + query always produce same results
- **Traceable**: Each generated artifact traces to a specific SPARQL query
- **Standard**: W3C standard, not proprietary
- **Queryable**: Can ask "why was this artifact generated?" → look at query

### Layer 3: Template Rendering with Tera

Tera templates receive structured data from SPARQL and emit code:

```tera
<?xml version="1.0"?>
<specification>
  <name>{{ workflow.label }}</name>

  {% for task in tasks %}
  <task id="{{ task.taskId }}">
    <name>{{ task.label }}</name>
    <successor>{{ task.successor }}</successor>
  </task>
  {% endfor %}
</specification>
```

**Why Tera?**

- **Simple**: No side effects, no loops without bounds
- **Safe**: Can't call arbitrary functions
- **Deterministic**: No randomness or env vars
- **Debuggable**: Easy to trace which template line generated which output

---

## Example: Complete Generation Pipeline

### Step 1: Load RDF Model

```java
RdfKnowledgeBase kb = RdfKnowledgeBase.fromTtlFile(
    Path.of("workflow.ttl")
);
System.out.println("Loaded " + kb.size() + " RDF triples");
```

Output:
```
Loaded 24 RDF triples
```

### Step 2: Execute SPARQL Queries

```java
SparqlQueryExecutor executor = new SparqlQueryExecutor(kb);

String tasksQuery = """
    PREFIX : <http://example.com/workflow#>
    SELECT ?id ?label ?next
    WHERE {
        ?id a :Task ;
            rdfs:label ?label ;
            :flowsTo ?next .
    }
    """;

var tasks = executor.selectToList(tasksQuery);
System.out.println("Found tasks: " + tasks);
```

Output:
```
Found tasks: [
  {id: Receive, label: "Receive Application", next: "Review"},
  {id: Review, label: "Review Application", next: "Decision"}
]
```

### Step 3: Render Template

```java
TeraTemplateRenderer renderer =
    TeraTemplateRenderer.fromFile(Path.of("spec.tera"));

var context = Map.of(
    "workflow", Map.of("label", "Loan Processing"),
    "tasks", tasks
);

String generated = renderer.render(context);
System.out.println(generated);
```

Output:
```xml
<?xml version="1.0"?>
<specification>
  <name>Loan Processing</name>
  <task id="Receive">
    <name>Receive Application</name>
    <successor>Review</successor>
  </task>
  ...
</specification>
```

---

## Design Patterns

### Pattern 1: RDF-as-Schema

Use RDF schema (RDFS) to describe allowed task types:

```turtle
:Task a rdfs:Class ;
    rdfs:subClassOf :WorkflowElement ;
    rdfs:label "YAWL Task" .

:Condition a rdfs:Class ;
    rdfs:subClassOf :WorkflowElement ;
    rdfs:label "YAWL Condition" .
```

SPARQL can then validate:

```sparql
# Find all invalid task types
SELECT ?invalid
WHERE {
    ?invalid a :WorkflowElement .
    FILTER (?invalid != :Task && ?invalid != :Condition)
}
```

### Pattern 2: Separating Intent from Implementation

Intent (RDF):
```turtle
:Receive a :Task ;
    :priority :high ;
    :timeoutMinutes 30 .
```

Template uses intent to control implementation:

```tera
<task id="{{ task.id }}">
    <name>{{ task.label }}</name>
    {% if task.priority == "high" %}
    <priority>high</priority>
    <timeout>{{ task.timeoutMinutes }}000</timeout>
    {% endif %}
</task>
```

### Pattern 3: Semantic Relationships

RDF naturally represents semantic relationships:

```turtle
:Loan a :Domain .
:Customer a :Domain .
:LoanPolicy a :Domain .

:Loan :dependsOn :Customer .
:Loan :refersTo :LoanPolicy .
```

Query to find all dependencies:

```sparql
SELECT ?domain ?dependency
WHERE {
    ?domain :dependsOn ?dependency .
}
```

---

## Traceability & Debugging

Every generated artifact can be traced to its origin:

```
Generated line:
  <task id="Review">

Trace:
  1. SPARQL query extracted: ?id = "Review" from RDF
  2. Template evaluated: <task id="{{ task.id }}"> with task.id = "Review"
  3. Output: <task id="Review">

Rationale:
  The task exists in the RDF model because it was manually defined or
  synthesized from PNML process mining results.
```

### Query Provenance

Every SPARQL query is logged:

```
Query 1: Select all tasks
  PREFIX : <http://example.com/workflow#>
  SELECT ?id WHERE { ?id a :Task }
  Result: [Receive, Review, Approve, Reject]

Query 2: Find successors
  SELECT ?successor WHERE {
    ?task :flowsTo ?successor .
  }
  Result: [Review → Decision, etc.]
```

---

## Integration with Process Mining

ggen integrates **Rust4pmBridge** to synthesize workflows from event logs:

```
Event Log (OCEL2) → Rust4pmBridge → PNML → ggen → YAWL Spec
   (JSON)        (process mining)  (Petri net)  (RDF+SPARQL+Templates)
```

Process:

1. **Mine patterns** from event log using `@aarkue/process_mining_wasm`
2. **Generate PNML** representing discovered process
3. **Load PNML** into ggen's `PnmlSynthesizer`
4. **Synthesize RDF** from PNML tasks and transitions
5. **Query RDF** with SPARQL to extract structure
6. **Render templates** to YAWL XML

---

## Performance Characteristics

### Time Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Load RDF | O(n) | n = number of triples |
| SPARQL SELECT | O(n) | Jena ARQ with indexing |
| Template render | O(m) | m = output size |
| **Total** | **O(n + m)** | Linear in input + output |

### Space Complexity

| Component | Space |
|-----------|-------|
| RDF triple store | O(n) triples × 100 bytes avg |
| SPARQL result set | O(k) rows × 500 bytes avg |
| Template context | O(k) |
| **Total** | **O(n + k)** |

### Practical Numbers

- **100-task workflow**: ~1000 RDF triples, <50ms SPARQL, <100ms generation
- **1000-task workflow**: ~10,000 triples, <200ms SPARQL, <500ms generation
- **Complex synthesis**: With process mining + PNML, ~1-5s total

---

## Comparison: ggen vs Traditional Generators

| Aspect | Traditional | ggen |
|--------|-----------|------|
| **Determinism** | Non-deterministic (may use randomness) | Deterministic (queries + templates only) |
| **Traceability** | Hard to debug | Every line traceable to SPARQL |
| **Validation** | Schema violations possible | Validated by construction (RDF schema) |
| **Reusability** | Hard to compose templates | RDF facts reusable across templates |
| **Change impact** | Update template, regenerate all | Update RDF facts, queries extract changes |
| **Testing** | Test whole generator | Test queries, test templates separately |
| **Learning curve** | Template language | RDF, SPARQL, Tera |

---

## When to Use ggen

**Good fit:**

- Complex domain models with many rules
- Need to generate multiple artifacts from same source
- Traceability and auditability are critical
- Team knows SPARQL or willing to learn
- Need deterministic, reproducible builds

**Not a good fit:**

- Simple templates that don't need validation
- Real-time code generation (compile-time use)
- Non-developer users writing templates

---

## Next Steps

- [Tutorial: ggen Getting Started](../tutorials/polyglot-ggen-getting-started.md)
- [How-To: Write Tera Templates](../how-to/ggen-tera-templates.md)
- [Reference: ggen API](../reference/ggen-api.md)
