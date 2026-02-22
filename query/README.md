# YAWL SPARQL Query Suite

This directory contains three SPARQL 1.1 queries for extracting and validating YAWL workflow structure from RDF/Turtle ontologies.

## Queries

### 1. extract-tasks.sparql

**Purpose**: Extract all tasks with their control flow properties.

**Result Fields**:
- `?taskId` - Unique task identifier
- `?taskName` - Human-readable task name
- `?splitType` - Join control type (AND, XOR, OR)
- `?joinType` - Split control type (AND, XOR, OR)
- `?documentation` - Optional task documentation/comments
- `?decomposesTo` - Optional reference to decomposed net

**Usage**: Used by ggen-sync.sh to populate task templates in code generation pipeline.

**Row Count**: Returns one row per task in the workflow net.

---

### 2. extract-flows.sparql

**Purpose**: Extract all control flow edges (task→task and task→condition transitions).

**Result Fields**:
- `?fromTaskId` - Source task/condition identifier
- `?toTaskId` - Target task/condition identifier
- `?predicate` - Optional XPath condition for conditional flows
- `?isDefault` - Boolean flag for default flow edge
- `?evaluationOrder` - Priority/order for flow evaluation

**Usage**: Populates transition definitions and control flow graph in code generation.

**Row Count**: Returns one row per flow edge; multiple edges from single task are separate rows.

**Filter**: Validates that source is Task or Condition, target is a NetElement.

---

### 3. validate-structure.sparql

**Purpose**: Structural validation query (ASK) to detect workflow anomalies.

**Query Type**: ASK (returns true/false)

**Validates**:
1. No orphaned tasks (tasks with no incoming or outgoing flows, excluding I/O conditions)
2. All flows reference valid target elements (no dangling edges)
3. All tasks have join and split control specifications

**Returns**:
- `true` if ANY structural violation is found (workflow is invalid)
- `false` if workflow structure is valid

**Usage**: Pre-generation gate to catch malformed specifications before code generation.

---

## Schema Context

All queries operate on the YAWL ontology defined in `.specify/yawl-ontology.ttl`:

- **Namespace**: `http://www.yawlfoundation.org/yawlschema#`
- **Base Classes**: yawls:Task, yawls:Condition, yawls:WorkflowNet
- **Control Types**: yawls:AND, yawls:XOR, yawls:OR
- **Properties**: yawls:id, yawls:name, yawls:hasSplit, yawls:hasJoin, yawls:hasFlowInto

---

## Integration with ggen

These queries are referenced in `ggen.toml` generation rules:

```toml
[[generation.rules]]
name = "java-task"
query = { file = "query/extract-tasks.sparql" }
template = { file = "templates/yawl-java/task.tera" }
output_file = "java/elements/{{ taskId }}.java"

[[generation.rules]]
name = "java-flow"
query = { file = "query/extract-flows.sparql" }
template = { file = "templates/yawl-java/flow.tera" }
output_file = "java/elements/YFlow.java"

[[gates.structure]]
query = { file = "query/validate-structure.sparql" }
description = "Workflow structure must be valid"
```

---

## Execution Example

**Input**: RDF/Turtle file containing workflow specification:
```turtle
@prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .

:task1 a yawls:Task ;
  yawls:id "ApproveRequest" ;
  yawls:name "Approve Request" ;
  yawls:hasSplit [ yawls:code yawls:XOR ] ;
  yawls:hasJoin [ yawls:code yawls:AND ] .

:task1 yawls:hasFlowInto [
  yawls:nextElement :task2 ;
  yawls:isDefaultFlow true
] .
```

**Query**: `extract-tasks.sparql`

**Output**:
```
taskId              | taskName        | splitType | joinType
=================== | =============== | ========= | =========
ApproveRequest      | Approve Request | XOR       | AND
```

---

## SPARQL 1.1 Compliance

All queries conform to SPARQL 1.1 standard:
- Uses PREFIX declarations for namespace management
- Supports SELECT, ASK, and CONSTRUCT patterns
- Employs OPTIONAL clauses for nullable properties
- Uses FILTER and EXISTS for constraint logic
- Implements ORDER BY for deterministic result ordering

---

## Notes for Implementers

1. **Namespace Alignment**: Ensure RDF source uses `http://www.yawlfoundation.org/yawlschema#` namespace.

2. **Execution Engine**: Tested with Apache Jena, SPARQL 1.1-compliant endpoints (Virtuoso, GraphDB, etc.).

3. **Performance**: No indexes required; reasonable for <10K tasks. For larger workflows, add SPARQL endpoint indexing.

4. **Error Handling**: validate-structure.sparql returns `false` if no violations (safe default for gate). If workflow changes, re-run query.

---

**Generated**: 2026-02-21
**Component**: Query Engineer (YAWL XML Generator Team)
**Status**: Production Ready
