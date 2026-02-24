# Multi-Format Export Quick Reference Guide
**Fast Implementation Checklist & Architecture Summary**

---

## Architecture at a Glance

```
YAWL XML
   ↓
YAWLtoRDF (Canonical Form)
   ↓
┌──────────────────────────────────────────┐
│ ExportAdapterRegistry                    │
├──────────────────────────────────────────┤
│ @Component ExportAdapters:               │
│ ├─ YAWLtoYAML (95% coverage)             │
│ ├─ YAWLtoMermaid (85% coverage)          │
│ ├─ YAWLtoTurtle (100% coverage, RDF)    │
│ ├─ YAWLtoPlantUML (80% coverage)         │
│ └─ [Future: GraphQL, Protobuf, AsyncAPI]│
└──────────────────────────────────────────┘
   ↓
ggen Tera Templates
   ↓
Output Files (.yaml, .mmd, .ttl, .puml)
```

---

## 4-Week PoC Implementation Path

### Week 1: RDF Foundation
**Deliverable**: YAWL ↔ RDF round-trip (100% lossless)

**Files to Create**:
- `src/org/yawlfoundation/yawl/integration/export/rdf/YAWLtoRDF.java`
- `src/main/resources/yawl-ontology.ttl` (YAWL RDF ontology)
- `src/main/resources/yawl-shapes.ttl` (SHACL validation shapes)

**Key Classes**:
```java
public class YAWLtoRDF {
    public Model convert(YSpecification spec) { ... }
    private Resource convertNet(YNet net) { ... }
    private Resource convertTask(YTask task) { ... }
    private void validateRDF() { ... }
}
```

**Test**: `RDFRoundTripTest.java`

---

### Week 2: YAML & Mermaid Exporters
**Deliverable**: 2 working adapters + semantic coverage reports

**Files to Create**:
- `src/org/yawlfoundation/yawl/integration/export/ExportAdapter.java` (Interface)
- `src/org/yawlfoundation/yawl/integration/export/ExportAdapterRegistry.java` (Registry)
- `src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoYAML.java`
- `src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoMermaid.java`
- `templates/export/yaml.tera`
- `templates/export/mermaid.tera`

**Key Methods**:
```java
public interface ExportAdapter {
    String format();
    FormatAST adapt(RDFModel rdf, Map<String, Object> metadata);
    SemanticCoverageReport coverage(RDFModel rdf);
    ValidationResult validate(String content);
}
```

**Tests**: `YAMLAdapterTest.java`, `MermaidAdapterTest.java`

---

### Week 3: PlantUML + Validation
**Deliverable**: 3 exporters working + format validators

**Files to Create**:
- `src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoPlantUML.java`
- `src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoTurtle.java`
- `src/org/yawlfoundation/yawl/integration/export/validation/MermaidValidator.java`
- `src/org/yawlfoundation/yawl/integration/export/validation/YAMLValidator.java`
- `templates/export/puml.tera`
- `templates/export/turtle.tera`

**Tests**: `PlantUMLAdapterTest.java`, `TurtleAdapterTest.java`

---

### Week 4: API + MCP + CLI
**Deliverable**: REST API + MCP tool + CLI command + documentation

**Files to Create**:
- `src/org/yawlfoundation/yawl/integration/export/api/ExportController.java`
- `src/org/yawlfoundation/yawl/integration/export/api/ExportCache.java`
- `src/org/yawlfoundation/yawl/integration/mcp/ExportWorkflowMCPTool.java`
- `src/org/yawlfoundation/yawl/integration/export/cli/ExportCommand.java`

**Endpoints**:
```
GET  /api/specs/{specId}/export?format=yaml|mermaid|turtle|puml
POST /api/specs/{specId}/validate-export
GET  /api/specs/{specId}/export-coverage?format=mermaid
GET  /api/specs/export-formats
```

**CLI**:
```bash
yawl export --format yaml --input spec.yawl --output spec.yaml
yawl export --format mermaid --input spec.yawl --output spec.mmd
```

**MCP Tool**:
```
Tool: /tools/specs/export
Inputs: specId, format
Output: content
```

---

## File Structure (Reference)

```
src/org/yawlfoundation/yawl/integration/export/
├─ ExportAdapter.java                      # Interface
├─ ExportAdapterRegistry.java               # Plugin registry
├─ FormatAST.java                           # Abstract syntax tree base
├─ SemanticCoverageReport.java              # Coverage analysis
├─ ValidationResult.java                    # Validation results
│
├─ adapters/
│  ├─ YAWLtoYAML.java
│  ├─ YAWLtoMermaid.java
│  ├─ YAWLtoPlantUML.java
│  ├─ YAWLtoTurtle.java
│  └─ [future: GraphQL, Protobuf, AsyncAPI]
│
├─ rdf/
│  ├─ YAWLtoRDF.java                       # YAWL → RDF converter
│  ├─ YAWLOntology.java                    # Ontology definitions
│  └─ ShaclValidator.java                  # RDF validation
│
├─ validation/
│  ├─ MermaidValidator.java
│  ├─ YAMLValidator.java
│  ├─ PlantUMLValidator.java
│  └─ TurtleValidator.java
│
├─ api/
│  ├─ ExportController.java                # REST endpoints
│  ├─ ExportCache.java                     # Caching strategy
│  └─ ExportRequest.java                   # API DTOs
│
└─ cli/
   └─ ExportCommand.java                   # CLI integration

templates/export/
├─ yaml.tera                               # YAML template
├─ mermaid.tera                            # Mermaid template
├─ turtle.tera                             # RDF/Turtle template
├─ puml.tera                               # PlantUML template
├─ jsonschema.tera                         # JSON Schema (future)
└─ graphql.tera                            # GraphQL (future)

src/main/resources/
├─ yawl-ontology.ttl                       # RDF ontology
├─ yawl-shapes.ttl                         # SHACL validation shapes
└─ export-schemas/
   ├─ mermaid-grammar.ebnf
   ├─ yaml-schema.json
   └─ puml-grammar.ebnf

src/test/java/...
├─ RDFRoundTripTest.java
├─ YAMLAdapterTest.java
├─ MermaidAdapterTest.java
├─ PlantUMLAdapterTest.java
├─ TurtleAdapterTest.java
├─ ExportControllerTest.java
└─ ExportCacheTest.java
```

---

## Key Integration Points

### 1. Spring Auto-Registration
```java
@Component
@ExportFormat("yaml")
public class YAWLtoYAML implements ExportAdapter {
    // Auto-discovered by ExportAdapterRegistry
}
```

### 2. ggen Integration (ggen.toml)
```toml
[[generation.rules]]
name = "export-yaml"
template = { file = "templates/export/yaml.tera" }
output_file = "exports/{{ specId }}.yaml"
```

### 3. MCP Tool Registration
```java
@MCPTool
public class ExportWorkflowMCPTool {
    @MCPInput String specId;
    @MCPInput String format;
    @MCPOutput String content;
}
```

### 4. A2A Protocol Message
```protobuf
message WorkflowExport {
    string spec_id = 1;
    string format = 2;      // "rdf/turtle" for semantic interchange
    bytes content = 3;
    CoverageReport coverage = 4;
}
```

---

## Dependencies (Maven)

```xml
<!-- Already in pom.xml -->
<dependency>
    <groupId>org.apache.jena</groupId>
    <artifactId>jena-core</artifactId>
    <version>4.8.0</version>
</dependency>

<!-- Already in pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>

<!-- Already used by ggen -->
<dependency>
    <groupId>com.google.code.tera-java</groupId>
    <artifactId>tera</artifactId>
    <version>1.0</version>
</dependency>

<!-- Optional: Format validation -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.0</version>
</dependency>
```

---

## Testing Strategy (Quick)

```java
// Week 1: RDF Foundation
@Test
void testYAWLtoRDFRoundTrip() {
    YSpecification original = loadLoanApprovalWorkflow();
    Model rdfModel = YAWLtoRDF.convert(original).getModel();
    YSpecification roundTripped = RDFtoYAWL.convert(rdfModel);
    assertEquals(original.getID(), roundTripped.getID());
    assertEquals(original.getRootNet().getTasks().size(),
                 roundTripped.getRootNet().getTasks().size());
}

// Week 2: Adapters
@Test
void testYAMLAdapterCompleteness() {
    YSpecification spec = loadLoanApprovalWorkflow();
    RDFModel rdf = YAWLtoRDF.convert(spec);
    YAMLSpecAST ast = (YAMLSpecAST) new YAWLtoYAML().adapt(rdf, Map.of());
    assertNotNull(ast);
    assertEquals(spec.getRootNet().getTasks().size(), ast.tasks.size());
}

// Week 3: Validation
@Test
void testMermaidDiagramIsValid() {
    String mermaidContent = "graph TD\n  A --> B\n  B --> C";
    MermaidValidator validator = new MermaidValidator();
    ValidationResult result = validator.validate(mermaidContent);
    assertTrue(result.valid());
}

// Week 4: API
@Test
void testExportEndpoint() {
    mockMvc.perform(get("/api/specs/loan-approval/export?format=yaml"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/yaml"));
}
```

---

## Performance Targets

| Operation | Target | Method |
|-----------|--------|--------|
| Single export | <1 sec | Streaming output, caching |
| Batch export (5 formats) | <5 sec | Parallel template rendering |
| RDF extraction | <500 ms | Optimize traversal, lazy load |
| API response | <2 sec | Cache + CDN for static exports |
| Mermaid validation | <100 ms | Local grammar check |

---

## Deployment Checklist

- [ ] All adapters marked @Component with @ExportFormat
- [ ] RDF ontology + SHACL shapes bundled in JAR
- [ ] Templates in templates/export/ directory
- [ ] REST API documented in OpenAPI 3.0
- [ ] MCP tools registered in YawlMcpServer
- [ ] CLI command integrated in yawl-cli module
- [ ] Caching layer (Caffeine) configured
- [ ] Error handling + validation comprehensive
- [ ] Performance benchmarks <1 sec per export
- [ ] Unit + integration tests >90% coverage

---

## Success Criteria (PoC Definition)

**Must-Have**:
- ✅ YAML export (95% coverage, valid syntax)
- ✅ Mermaid export (85% coverage, valid syntax)
- ✅ Turtle/RDF export (100% coverage, canonical form)
- ✅ PlantUML export (80% coverage, valid syntax)
- ✅ Semantic coverage reports (JSON format)
- ✅ REST API endpoints working
- ✅ MCP tool registration
- ✅ Unit tests >85% coverage

**Nice-to-Have**:
- ✅ CLI command line tool
- ✅ Export cache (1 hour TTL)
- ✅ Streaming for large exports
- ✅ JSON Schema export (planned)
- ✅ Mermaid diagram rendering (server-side)

---

## What NOT to Do

❌ **Don't**: Start with GraphQL or Protobuf (high complexity, low ROI in PoC)
❌ **Don't**: Implement RDF without SHACL validation (bugs accumulate)
❌ **Don't**: Export BPMN in PoC (save for Phase 2, enterprise focus)
❌ **Don't**: Skip semantic coverage reporting (key differentiator)
❌ **Don't**: Use simple XSLT/XPath for adaptation (plug into ggen templates instead)
❌ **Don't**: Lock-in to single cache backend (abstract, support Redis/Memcached)

---

## Key Insight: Why This Works

YAWL's multi-format export succeeds because:

1. **RDF is canonical** → No semantic loss for YAWL → RDF export
2. **ggen templates are flexible** → Any format in 20-30 lines of Tera
3. **Adapters are pluggable** → New formats don't break old ones
4. **Semantic coverage is measurable** → Enterprises understand trade-offs
5. **No vendor lock-in** → Competitive advantage that compounds

This is **NOT** a quick hack. It's a **architectural advantage** that compounds over time.

---

**Document Status**: Ready for Implementation
**Next**: Assign engineers to Week 1 RDF foundation

