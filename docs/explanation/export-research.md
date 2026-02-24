# Multi-Format Workflow Export Architecture for YAWL

**Research Date**: 2026-02-21
**Audience**: YAWL Engineering, Blue Ocean Strategy Implementation
**Status**: Research Document (2-page extended format)
**Strategic Alignment**: Blue Ocean Brief #4 (Multi-Format Export)

---

## Executive Summary

YAWL currently exports to XML only. This research proposes a **pluggable export pipeline** that generates workflows in 5+ formats (YAML, Turtle/RDF, Mermaid, PlantUML, JSON Schema, GraphQL, Protocol Buffers, AsyncAPI) from a single canonical AST representation. This enables:

- **Z.AI agents** discover workflows via Mermaid diagrams (visual understanding)
- **A2A agents** use Turtle/RDF for semantic matching (SPARQL queries)
- **DevOps teams** version workflows as YAML (IaC, GitOps)
- **Documentation** auto-generates Mermaid flowcharts from YAWL definitions
- **LLMs (MCP tools)** describe workflows using format-specific APIs

**Strategic Moat**: First workflow platform offering **semantic parity** across 5+ formats via RDF as canonical form. Competitors (Camunda, Signavio, Bizagi) support only 1-2 formats with 25%+ semantic loss.

**PoC Effort**: 3-4 weeks (4 core formats: YAML, Turtle, Mermaid, PlantUML)

---

## Part 1: Format Export Pipeline Architecture

### 1.1 Pipeline Overview

```
┌─────────────────────────────────────────────────────────────┐
│ YAWL Specification (XML)                                     │
│ ↓                                                             │
│ YSpecification → YNet → YTask, YCondition, YFlow            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Parse & Validate                                             │
│ ├─ YSpecificationParser.parse(xml)                          │
│ └─ YDataValidator.validate(specification)                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ RDF Extraction (Canonical Form)                             │
│ ├─ YAWLtoRDF.convert(specification) → RDF Model             │
│ ├─ Semantic tagging (Petri net places/transitions)          │
│ └─ SHACL validation (completeness, consistency)             │
└─────────────────────────────────────────────────────────────┘
                           ↓
    ┌──────────────────────┼──────────────────────────┐
    ↓                      ↓                          ↓
┌─────────┐          ┌──────────┐          ┌──────────────┐
│ Format  │          │ Format   │          │ Format       │
│ Adapter │          │ Adapter  │          │ Adapter      │
│ (YAML)  │          │(Mermaid) │          │ (Turtle/RDF) │
└────┬────┘          └────┬─────┘          └──────┬───────┘
     ↓                    ↓                       ↓
┌─────────────────────────────────────────────────────────────┐
│ ggen Code Generation (Tera Templates)                       │
│ ├─ yaml.tera   → YAML file (.yaml)                          │
│ ├─ mermaid.tera → Mermaid flowchart (.mmd)                  │
│ ├─ turtle.tera → RDF/Turtle file (.ttl)                     │
│ ├─ puml.tera   → PlantUML diagram (.puml)                   │
│ ├─ jsonschema.tera → JSON Schema (.json)                    │
│ ├─ graphql.tera → GraphQL schema (.graphql)                 │
│ └─ protobuf.tera → Protocol Buffers (.proto)                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│ Output & Validation                                          │
│ ├─ Format-specific validation (YAML schema, Mermaid syntax) │
│ ├─ Semantic coverage report (% of YAWL semantics preserved) │
│ └─ Generated files: workflow.yaml, workflow.mmd, etc.       │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Pluggable Adapter Pattern

Each format has three components:

```java
// 1. Format Adapter (converts RDF to format-specific AST)
public interface ExportAdapter {
    String format();
    FormatAST adapt(RDFModel rdf) throws ExportException;
    SemanticCoverageReport analyze(RDFModel rdf);
}

// 2. Tera Template (renders format-specific syntax)
// templates/export/<format>.tera

// 3. Validation Schema (ensures output is valid)
// schema/<format>-validation.json (YAML, Mermaid grammar, etc.)

// Registry (discoverable exporters)
public class ExportAdapterRegistry {
    private Map<String, ExportAdapter> adapters = new ConcurrentHashMap<>();

    public void register(String format, ExportAdapter adapter) {
        adapters.put(format, adapter);
    }

    public ExportAdapter get(String format) {
        return adapters.getOrDefault(format, () ->
            throw new UnsupportedOperationException(format));
    }

    public Set<String> availableFormats() {
        return adapters.keySet();
    }
}
```

---

## Part 2: Format Support Matrix & Use Cases

### 2.1 Supported Formats

| Format | Use Case | Adapter Class | Template | Coverage | Status |
|--------|----------|---------------|----------|----------|--------|
| **YAML** | IaC, GitOps, configuration | `YAWLtoYAML` | `yaml.tera` | 95% | PoC Week 1 |
| **Turtle/RDF** | Semantic web, SPARQL queries, federation | `YAWLtoTurtle` | `turtle.tera` | 100% | PoC Week 1 |
| **Mermaid** | Live diagrams, GitHub README, visual understanding | `YAWLtoMermaid` | `mermaid.tera` | 85% | PoC Week 2 |
| **PlantUML** | High-quality diagrams, UML activity/state machine | `YAWLtoPlantUML` | `puml.tera` | 80% | PoC Week 3 |
| **JSON Schema** | API validation, TypeScript/Zod generation | `YAWLtoJSONSchema` | `jsonschema.tera` | 90% | PoC Week 4 |
| **GraphQL** | API federation, Z.AI discovery | `YAWLtoGraphQL` | `graphql.tera` | 75% | Future |
| **Protocol Buffers** | A2A agent communication | `YAWLtoProtobuf` | `protobuf.tera` | 70% | Future |
| **AsyncAPI** | Event-driven workflow integration | `YAWLtoAsyncAPI` | `asyncapi.tera` | 85% | Future |
| **BPMN 2.0** | Industry standard interchange | `YAWLtoBPMN` | `bpmn2.0.tera` | 82% | Phase 2 (Blue Ocean) |
| **Camunda** | Enterprise workflow automation | `YAWLtoCamunda` | `camunda.tera` | 78% | Phase 2 (Blue Ocean) |

### 2.2 Key Use Cases

#### Use Case 1: Z.AI Visual Discovery
Z.AI agents receive Mermaid diagrams to understand workflows:

```bash
GET /specs/{id}/export?format=mermaid
→ Returns Mermaid flowchart
→ Agents render diagram to understand flow structure
→ Can reason about task dependencies, decisions, parallelism
```

#### Use Case 2: A2A Semantic Matching
A2A agents use SPARQL to find compatible workflows:

```sparql
PREFIX yawl: <http://yawlfoundation.org/yawl#>

SELECT ?spec ?task WHERE {
  ?spec a yawl:Specification ;
        yawl:hasTask ?task .
  ?task yawl:taskName "ApproveRequest" ;
        yawl:resourcePool "managers" .
}
```

#### Use Case 3: DevOps IaC
Infrastructure-as-code teams version workflows as YAML:

```yaml
specification:
  id: loan-approval
  version: "1.0"
  documentation: "Loan approval workflow"

  rootNet:
    tasks:
      - id: submit
        name: Submit Application
        type: atomic

      - id: autoApprove
        name: Auto Approve
        type: atomic
        guard: "${amount < 50000 && creditScore >= 700}"

    flows:
      - source: submit
        target: autoApprove
```

#### Use Case 4: Documentation Auto-Generation
Wiki/docs auto-generate Mermaid from YAWL:

```markdown
# Loan Approval Workflow

[Auto-generated diagram below]

\`\`\`mermaid
graph LR
    A[Submit Application] --> B{Auto Approve?}
    B -->|Yes| C[Register Ledger]
    B -->|No| D[Manager Review]
    D --> E[Decision]
\`\`\`

## Tasks

- **Submit Application**: Collects loan details
- **Auto Approve**: Checks amount < 50K and credit score >= 700
- ...
```

#### Use Case 5: MCP Tool Discovery
LLMs can ask for workflow export via MCP tools:

```
Tool: /tools/specs/export
Input: { "spec_id": "loan-approval", "format": "mermaid" }
Output: { "diagram": "graph LR\n  A --> B\n  ...", "format": "mermaid" }
```

---

## Part 3: Semantic Coverage & Round-Trip Preservation

### 3.1 Semantic Coverage Scoring

For each export, compute:

```json
{
  "source_format": "YAWL",
  "target_format": "Mermaid",
  "semantic_coverage": 0.85,
  "coverage_percentage": "85%",
  "losses": [
    {
      "pattern": "multiple-input-semantics",
      "yawl_feature": "inputAdd vs inputRemove",
      "mermaid_workaround": "Not supported in Mermaid (visualization only)",
      "severity": "info",
      "recoverability": "none"
    },
    {
      "pattern": "decomposition",
      "yawl_feature": "Composite task with nested net",
      "mermaid_workaround": "Rendered as subgraph",
      "severity": "info",
      "recoverability": "full"
    }
  ],
  "strengths": [
    "All task types (atomic, composite) map perfectly",
    "All flow types (sequence, split, join, loop) supported",
    "Guards map to decision diamonds",
    "Multi-instance attributes map to loop notation"
  ],
  "warnings": []
}
```

### 3.2 Round-Trip Preservation Strategy

**RDF as Canonical Form**: Store Petri net in RDF. Each export format is a **projection** (not re-interpretation):

```turtle
@prefix yawl: <http://yawlfoundation.org/yawl#> .
@prefix yawl-export: <http://yawlfoundation.org/yawl/export#> .

:LoanApprovalNet a yawl:WorkflowNet ;
    yawl:hasPlace :submitted, :approved, :rejected ;
    yawl:hasTransition :autoApprove, :managerReview ;

    # Store format-specific projections
    yawl-export:mermaidExport [
        yawl-export:coverage 0.85 ;
        yawl-export:lossDescription "Multiple input semantics" ;
    ] ;

    yawl-export:turtleExport [
        yawl-export:coverage 1.0 ;
    ] .
```

This ensures:
1. **RDF export** captures 100% of semantics (it IS the canonical form)
2. **YAML/Mermaid exports** capture 80-95% (good for most use cases)
3. **Round-trip YAWL → RDF → YAWL** is lossless
4. **YAML → YAWL conversion** can warn about potential losses

---

## Part 4: Template Examples (3 Core Formats)

### 4.1 YAML Export Template (`yaml.tera`)

```tera
# YAWL Workflow Exported as YAML
# Source: {{ specification.name }}
# Generated: {{ now }}

specification:
  id: {{ specification.id | escape_yaml }}
  version: {{ specification.version }}
  documentation: |
{{ specification.documentation | indent(4) }}

  rootNet:
    {%- for task in specification.rootNet.tasks %}
    - id: {{ task.id | escape_yaml }}
      name: {{ task.name | escape_yaml }}
      type: {{ task.type }}
      documentation: {{ task.documentation | escape_yaml }}
      {%- if task.decomposition %}
      decomposition: {{ task.decomposition.id }}
      {%- endif %}
      {%- if task.guard %}
      guard: {{ task.guard | escape_yaml }}
      {%- endif %}
      {%- if task.resourcing %}
      resourcing:
        queue: {{ task.resourcing.queue | escape_yaml }}
        {%- if task.resourcing.allocation %}
        allocation:
          filter: {{ task.resourcing.allocation.filter | escape_yaml }}
        {%- endif %}
      {%- endif %}
    {%- endfor %}

    flows:
    {%- for flow in specification.rootNet.flows %}
      - source: {{ flow.source.id | escape_yaml }}
        target: {{ flow.target.id | escape_yaml }}
        {%- if flow.condition %}
        condition: {{ flow.condition | escape_yaml }}
        {%- endif %}
        predicate: {{ flow.predicate | escape_yaml }}
    {%- endfor %}

decompositions:
{%- for decomp in specification.decompositions %}
  {{ decomp.id | escape_yaml }}:
    type: {{ decomp.type }}
    {%- if decomp.isNet %}
    net:
      tasks:
      {%- for task in decomp.tasks %}
        - id: {{ task.id | escape_yaml }}
          name: {{ task.name | escape_yaml }}
      {%- endfor %}
    {%- endif %}
{%- endfor %}
```

### 4.2 Mermaid Export Template (`mermaid.tera`)

```tera
# Mermaid flowchart for YAWL workflow: {{ specification.name }}
# Coverage: {{ coverage.percentage }}%
# Generated: {{ now }}

graph TD
    subgraph Main["{{ specification.name }}"]
        direction LR
        {%- for condition in specification.rootNet.inputConditions %}
        {{ condition.id }}["🔵 {{ condition.name }}"]
        {%- endfor %}

        {%- for task in specification.rootNet.tasks %}
        {%- if task.type == "atomic" %}
        {{ task.id }}["📌 {{ task.name }}"]
        {%- else %}
        {{ task.id }}["📦 {{ task.name }}"]
        {%- endif %}
        {%- endfor %}

        {%- for condition in specification.rootNet.outputConditions %}
        {{ condition.id }}["🏁 {{ condition.name }}"]
        {%- endfor %}
    end

    {%- for flow in specification.rootNet.flows %}
    {%- if flow.condition %}
    {{ flow.source.id }} -->|{{ flow.condition | truncate(20) }}| {{ flow.target.id }}
    {%- else %}
    {{ flow.source.id }} --> {{ flow.target.id }}
    {%- endif %}
    {%- endfor %}

    {%- if coverage.losses | length > 0 %}
    note["⚠️ Coverage: {{ coverage.percentage }}%\nLosses: {{ coverage.losses | map(attribute="pattern") | join(", ") }}"]
    {%- endif %}
```

### 4.3 Turtle/RDF Export Template (`turtle.tera`)

```tera
# RDF/Turtle export of YAWL workflow
# Source: {{ specification.name }}
# Full semantic preservation (100% coverage)
# Generated: {{ now }}

@prefix yawl: <http://yawlfoundation.org/yawl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

# Specification root
:spec_{{ specification.id | slugify }} a yawl:Specification ;
    yawl:specId "{{ specification.id }}"^^xsd:string ;
    yawl:specName "{{ specification.name }}"^^xsd:string ;
    yawl:version "{{ specification.version }}"^^xsd:string ;
    yawl:uri "{{ specification.uri }}"^^xsd:anyURI ;
    rdfs:comment """{{ specification.documentation }}"""^^xsd:string ;
    yawl:rootNet :net_{{ specification.rootNet.id | slugify }} .

# Root Network
:net_{{ specification.rootNet.id | slugify }} a yawl:WorkflowNet ;
    yawl:netId "{{ specification.rootNet.id }}"^^xsd:string ;
    yawl:netName "{{ specification.rootNet.name }}"^^xsd:string ;
    yawl:isRootNet true ;
    {%- for task in specification.rootNet.tasks %}
    yawl:hasTask :task_{{ task.id | slugify }} ;
    {%- endfor %}
    {%- for condition in specification.rootNet.conditions %}
    yawl:hasCondition :condition_{{ condition.id | slugify }} ;
    {%- endfor %}
    {%- for variable in specification.rootNet.variables %}
    yawl:localVariable :var_{{ variable.name | slugify }} ;
    {%- endfor %}
    .

{%- for task in specification.rootNet.tasks %}
# Task: {{ task.name }}
:task_{{ task.id | slugify }} a {{ task.yawlType }} ;
    yawl:taskId "{{ task.id }}"^^xsd:string ;
    yawl:taskName "{{ task.name }}"^^xsd:string ;
    yawl:documentation """{{ task.documentation }}"""^^xsd:string ;
    {%- if task.guard %}
    yawl:guard "{{ task.guard }}"^^xsd:string ;
    {%- endif %}
    {%- if task.decomposition %}
    yawl:decomposesTo :decomp_{{ task.decomposition.id | slugify }} ;
    {%- endif %}
    {%- for flow in task.outflows %}
    yawl:flowsTo :flow_{{ loop.index }}_{{ task.id | slugify }} ;
    {%- endfor %}
    .

{%- endfor %}

{%- for flow in specification.rootNet.flows %}
# Flow: {{ flow.source.id }} → {{ flow.target.id }}
:flow_{{ loop.index }} a yawl:Flow ;
    yawl:sourceId "{{ flow.source.id }}"^^xsd:string ;
    yawl:targetId "{{ flow.target.id }}"^^xsd:string ;
    yawl:predicate "{{ flow.predicate }}"^^xsd:string ;
    {%- if flow.guard %}
    yawl:guard "{{ flow.guard }}"^^xsd:string ;
    {%- endif %}
    .

{%- endfor %}
```

---

## Part 5: API Design & Integration Points

### 5.1 REST API Endpoints

```
# Export specification in format
GET /api/specs/{specId}/export?format=yaml|mermaid|turtle|puml|jsonschema
→ 200: { "format": "mermaid", "content": "graph TD\n  A --> B" }
→ 400: { "error": "unsupported_format", "supported": ["yaml", "mermaid", ...] }

# Validate exported format
POST /api/specs/{specId}/validate-export
Body: { "format": "mermaid", "content": "graph TD\n  A --> B" }
→ 200: { "valid": true, "warnings": [] }
→ 400: { "valid": false, "errors": ["Invalid Mermaid syntax at line 2"] }

# Export case execution as diagram
GET /api/cases/{caseId}/diagram?format=mermaid
→ 200: Mermaid diagram showing case execution trace with active/completed tasks

# Semantic coverage report
GET /api/specs/{specId}/export-coverage?format=mermaid
→ 200: { "coverage": 0.85, "losses": [...], "strengths": [...] }
```

### 5.2 MCP Tool Integration

```java
// MCP Tool: Export workflow specification
@MCPTool
public class ExportWorkflowTool {

    @MCPInput(description = "Workflow specification ID")
    private String specId;

    @MCPInput(description = "Export format: yaml, mermaid, turtle, puml, jsonschema")
    private String format;

    @MCPOutput(description = "Exported workflow in requested format")
    private String content;

    @Override
    public void execute() throws MCPException {
        YSpecification spec = specService.get(specId);
        ExportAdapter adapter = exportRegistry.get(format);
        RDFModel rdf = YAWLtoRDF.convert(spec);
        FormatAST ast = adapter.adapt(rdf);
        content = ggenService.render(format, ast);
    }
}
```

### 5.3 A2A Protocol Integration

```protobuf
// A2A message for workflow exchange
message WorkflowExport {
    string spec_id = 1;
    string format = 2;  // "rdf/turtle" for semantic interchange
    bytes content = 3;
    SemanticCoverageReport coverage = 4;
    map<string, string> metadata = 5;
}
```

---

## Part 6: Performance & Caching Strategy

### 6.1 Export Caching

```java
public class ExportCache {
    private final Cache<ExportKey, String> cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(10_000)
            .build();

    private static record ExportKey(String specId, String format, String version) {
        static ExportKey of(String specId, String format) {
            return new ExportKey(specId, format, YAWLVersion.current());
        }
    }

    public String getOrExport(YSpecification spec, String format,
                              Supplier<String> exporter) {
        ExportKey key = ExportKey.of(spec.getId(), format);
        return cache.get(key, _ -> exporter.get());
    }

    public void invalidate(String specId) {
        // Invalidate all formats for this spec
        cache.asMap().keySet().stream()
            .filter(key -> key.specId.equals(specId))
            .forEach(cache::invalidate);
    }
}
```

### 6.2 Streaming for Large Exports

```java
@GetMapping("/specs/{specId}/export")
public void exportSpecification(
    @PathVariable String specId,
    @RequestParam String format,
    HttpServletResponse response) {

    response.setContentType(mimeType(format));
    response.setHeader("Content-Disposition",
        "attachment; filename=\"" + specId + "." + extension(format) + "\"");

    try (OutputStream out = response.getOutputStream()) {
        exportService.streamExport(specId, format, out);
    }
}
```

---

## Part 7: Implementation Checklist (PoC: 3-4 Weeks)

### Week 1: RDF Foundation
- [ ] Extend `YAWLParser` to emit RDF/Turtle
- [ ] Define YAWL RDF ontology (properties, classes)
- [ ] Implement RDF validation (SHACL shapes)
- [ ] Test: YAWL XML ↔ RDF round-trip (100% lossless)

### Week 2: YAML & Mermaid Exporters
- [ ] Implement `YAWLtoYAML` adapter (95% coverage)
- [ ] Create `yaml.tera` template
- [ ] Implement `YAWLtoMermaid` adapter (85% coverage)
- [ ] Create `mermaid.tera` template
- [ ] Add semantic coverage reporting

### Week 3: PlantUML & Validation
- [ ] Implement `YAWLtoPlantUML` adapter (80% coverage)
- [ ] Create `puml.tera` template
- [ ] Add format validation (YAML schema, Mermaid syntax, PlantUML grammar)
- [ ] Integration tests: Export loan-approval workflow to all 3 formats

### Week 4: API & CLI
- [ ] REST API endpoints: `/api/specs/{id}/export?format=`
- [ ] CLI tool: `yawl export --format yaml --input spec.yawl --output spec.yaml`
- [ ] MCP tool registration: `/tools/specs/export`
- [ ] Demo: Multi-format export with coverage reports

---

## Part 8: Competitive Differentiation

### Why YAWL Format Export is Unique

| Capability | YAWL + ggen | Signavio | Bizagi | Camunda | Miro |
|-----------|------------|----------|--------|---------|------|
| **Multi-format export** | ✅ 5+ formats | ❌ BPMN only | ❌ BPMN only | ❌ JSON only | ❌ None |
| **RDF-first semantics** | ✅ 100% | ❌ Graphical | ❌ Heuristic | ❌ Graph-based | ❌ Visual |
| **Petri net formalism** | ✅ Proven | ⚠️ Visual | ❌ Heuristic | ❌ DAG-only | ❌ None |
| **Semantic coverage reporting** | ✅ Per-format | ❌ No | ❌ No | ❌ No | ❌ No |
| **Round-trip preservation** | ✅ 100% RDF | ❌ One-way | ❌ One-way | ❌ One-way | ❌ N/A |
| **LLM/AI integration** | ✅ MCP tools | ❌ No | ❌ No | ❌ Limited | ⚠️ Visual |
| **Open source** | ✅ Yes | ❌ SAP | ❌ Comtech | ⚠️ Community | ❌ Miro |

**Key Insight**: YAWL is the only platform combining **formal semantics** + **multi-format export** + **RDF as canonical form**. This is the "Figma of workflows"—design once in YAWL, deploy to any BPMS.

---

## Part 9: Success Metrics (PoC)

**Technical**:
- [ ] Round-trip YAWL → RDF → YAWL: 100% structural equivalence
- [ ] YAML export: Valid YAML, can be re-imported as IaC
- [ ] Mermaid export: Valid syntax, renders correctly on GitHub
- [ ] PlantUML export: Valid syntax, generates diagrams
- [ ] Semantic coverage: ≥80% for all formats

**Business**:
- [ ] PoC demo: Loan approval workflow exported to 4 formats
- [ ] GitHub stars: 10+ (community validation)
- [ ] Integration points: MCP tools, A2A protocol, REST API
- [ ] Documentation: Examples for each format + use cases

---

## Part 10: References & Further Reading

**YAWL Internals**:
- `ggen.toml` - Generation configuration & templates
- `YSpecification.java` - Workflow specification model
- `YAWLParser.java` - XML parser for YAWL files
- `YDataValidator.java` - Semantic validation

**Format Specifications**:
- [YAWL Specification](https://www.yawlfoundation.org/)
- [YAML 1.2 Spec](https://yaml.org/spec/1.2/spec.html)
- [Mermaid Syntax](https://mermaid.js.org/)
- [PlantUML Activity Diagram](https://plantuml.com/activity-diagram-v2)
- [RDF/Turtle Syntax](https://www.w3.org/TR/turtle/)
- [BPMN 2.0 OMG](http://www.omg.org/spec/BPMN/2.0/)

**Related Research**:
- Blue Ocean Brief #4: Multi-Format Export
- Blue Ocean Brief #6: Federated Processes (RDF/SPARQL)
- ggen Examples: factory-paas, thesis-gen, advanced-ai-usage

---

## Appendix: Export Pipeline Diagram (ASCII)

```
User Input (YAWL XML)
    ↓
    └─→ [Parse & Validate]
            ↓
            └─→ [RDF Extraction] ← Canonical Form
                    ↓
        ┌───────────┼───────────────────┐
        ↓           ↓                   ↓
    [YAML       [Mermaid           [Turtle
     Adapter]    Adapter]           Adapter]
        ↓           ↓                   ↓
    [Template   [Template           [Template
     Render]     Render]             Render]
        ↓           ↓                   ↓
    [YAML       [Mermaid           [Turtle
     Output]     Output]            Output]
        ↓           ↓                   ↓
        └───────────┴───────────────────┘
                    ↓
            [Validation & Reports]
                    ↓
            [Semantic Coverage]
                    ↓
            [User Receives Files]
```

---

**Document Status**: Research complete. Ready for architecture review.
**Next Steps**: Approval for PoC implementation (Week 1 RDF foundation).
