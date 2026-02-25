# Export Pipeline Implementation Specification
**Detailed Technical Guide for Multi-Format Export Service**

**Status**: Implementation Ready | Reference Architecture
**Target**: src/org/yawlfoundation/yawl/integration/export/
**Integration Points**: ggen, MCP, A2A, REST API

---

## 1. Core Architecture: Plugin Pattern

### 1.1 Export Adapter Interface (Plugin Contract)

```java
// src/org/yawlfoundation/yawl/integration/export/ExportAdapter.java
package org.yawlfoundation.yawl.integration.export;

import java.util.Map;

/**
 * Plugin interface for workflow export formats.
 * Implementations adapt YAWL RDF model to format-specific AST.
 */
public interface ExportAdapter {

    /**
     * Canonical format identifier (e.g., "yaml", "mermaid", "turtle")
     */
    String format();

    /**
     * MIME type for HTTP responses (e.g., "text/yaml", "text/plain")
     */
    String mimeType();

    /**
     * File extension (e.g., "yaml", "mmd", "ttl")
     */
    String fileExtension();

    /**
     * Convert RDF model to format-specific AST (Abstract Syntax Tree)
     *
     * @param rdf      RDF model (Petri net representation)
     * @param metadata Additional metadata (timestamps, export options)
     * @return Format-specific AST ready for template rendering
     * @throws ExportException if adaptation fails
     */
    FormatAST adapt(RDFModel rdf, Map<String, Object> metadata)
        throws ExportException;

    /**
     * Analyze semantic coverage for this format.
     *
     * @param rdf RDF model
     * @return Coverage report with loss analysis
     */
    SemanticCoverageReport coverage(RDFModel rdf);

    /**
     * Validate exported output against format schema.
     *
     * @param content Generated content (text)
     * @return Validation result with errors/warnings
     */
    ValidationResult validate(String content);
}
```

### 1.2 Format AST Hierarchy

```java
// src/org/yawlfoundation/yawl/integration/export/FormatAST.java
public sealed class FormatAST permits YAMLSpecAST, MermaidAST, TurtleAST {
    public final String specId;
    public final String specName;
    public final String version;
    public final long generatedAt;

    protected FormatAST(String specId, String specName, String version) {
        this.specId = specId;
        this.specName = specName;
        this.version = version;
        this.generatedAt = System.currentTimeMillis();
    }
}

// Subclass example
public final class YAMLSpecAST extends FormatAST {
    public final String documentation;
    public final List<YAMLTask> tasks;
    public final List<YAMLFlow> flows;
    public final List<YAMLDecomposition> decompositions;

    public YAMLSpecAST(String specId, String specName, String version,
                       String documentation, List<YAMLTask> tasks,
                       List<YAMLFlow> flows, List<YAMLDecomposition> decomps) {
        super(specId, specName, version);
        this.documentation = documentation;
        this.tasks = tasks;
        this.flows = flows;
        this.decompositions = decomps;
    }
}

public record YAMLTask(
    String id,
    String name,
    String type,  // "atomic", "composite"
    String documentation,
    String guard,  // Optional guard expression
    String decomposition  // Optional decomposition ID
) {}

public record YAMLFlow(
    String sourceId,
    String targetId,
    String condition,  // Optional condition
    String predicate  // Optional predicate
) {}
```

### 1.3 Registry for Dynamic Plugin Discovery

```java
// src/org/yawlfoundation/yawl/integration/export/ExportAdapterRegistry.java
@Component
public class ExportAdapterRegistry {
    private final Map<String, ExportAdapter> adapters = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(ExportAdapterRegistry.class);

    /**
     * Register a new export adapter at startup.
     */
    public void register(String format, ExportAdapter adapter) {
        if (adapters.containsKey(format)) {
            logger.warn("Overwriting adapter for format: {}", format);
        }
        adapters.put(format, adapter);
        logger.info("Registered export adapter for format: {} ({})", format, adapter.getClass());
    }

    /**
     * Get adapter for format, or throw.
     */
    public ExportAdapter get(String format) throws UnsupportedOperationException {
        return adapters.getOrDefault(format,
            () -> { throw new UnsupportedOperationException(
                "Unsupported export format: " + format); });
    }

    /**
     * List available formats (for discovery).
     */
    public Set<String> availableFormats() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    /**
     * Auto-register all adapters via Spring component scan.
     * Adapters marked @Component with @ExportFormat("formatName") auto-register.
     */
    @PostConstruct
    public void autoRegisterAdapters() {
        // Spring will inject all ExportAdapter beans
    }
}
```

---

## 2. RDF Foundation (Week 1)

### 2.1 RDF Extraction from YAWL

```java
// src/org/yawlfoundation/yawl/integration/export/rdf/YAWLtoRDF.java
public class YAWLtoRDF {

    private final Model model;
    private final YAWLOntology ontology;

    public YAWLtoRDF() {
        this.model = ModelFactory.createDefaultModel();
        this.ontology = new YAWLOntology(model);
    }

    /**
     * Convert YSpecification to RDF model (canonical form).
     *
     * @param spec YAWL specification
     * @return RDF model with full Petri net representation
     */
    public Model convert(YSpecification spec) {
        // Create specification resource
        Resource specResource = model.createResource(
            ontology.getURI("Specification", spec.getID()),
            ontology.SPECIFICATION);

        specResource.addProperty(ontology.SPEC_ID, spec.getID());
        specResource.addProperty(ontology.SPEC_NAME, spec.getName());
        specResource.addProperty(ontology.VERSION, spec.getVersion());

        // Convert root net
        Resource rootNetResource = convertNet(spec.getRootNet());
        specResource.addProperty(ontology.ROOT_NET, rootNetResource);

        // Convert decompositions
        for (YDecomposition decomp : spec.getDecompositions()) {
            Resource decompResource = convertDecomposition(decomp);
            specResource.addProperty(ontology.HAS_DECOMPOSITION, decompResource);
        }

        // Validate RDF against SHACL shapes
        validateRDF();

        return model;
    }

    private Resource convertNet(YNet net) {
        Resource netResource = model.createResource(
            ontology.getURI("Net", net.getID()),
            ontology.WORKFLOW_NET);

        netResource.addProperty(ontology.NET_ID, net.getID());
        netResource.addProperty(ontology.NET_NAME, net.getName());
        netResource.addProperty(ontology.IS_ROOT_NET,
            model.createTypedLiteral(net.isRootNet()));

        // Convert tasks (transitions)
        for (YTask task : net.getTasks()) {
            Resource taskResource = convertTask(task);
            netResource.addProperty(ontology.HAS_TASK, taskResource);
        }

        // Convert conditions (places)
        for (YCondition condition : net.getConditions()) {
            Resource condResource = convertCondition(condition);
            netResource.addProperty(ontology.HAS_CONDITION, condResource);
        }

        // Convert flows (arcs)
        for (YFlow flow : net.getFlows()) {
            Resource flowResource = convertFlow(flow);
            netResource.addProperty(ontology.HAS_FLOW, flowResource);
        }

        return netResource;
    }

    private Resource convertTask(YTask task) {
        Resource taskResource = model.createResource(
            ontology.getURI("Task", task.getID()),
            task instanceof YCompositeTask ? ontology.COMPOSITE_TASK : ontology.ATOMIC_TASK);

        taskResource.addProperty(ontology.TASK_ID, task.getID());
        taskResource.addProperty(ontology.TASK_NAME, task.getName());
        taskResource.addProperty(ontology.SPLIT_TYPE, task.getSplitType().toString());
        taskResource.addProperty(ontology.JOIN_TYPE, task.getJoinType().toString());

        if (task.getDocumentation() != null) {
            taskResource.addProperty(RDFS.comment, task.getDocumentation());
        }

        if (task instanceof YCompositeTask composite) {
            taskResource.addProperty(ontology.DECOMPOSES_TO,
                model.createResource(ontology.getURI("Decomposition",
                    composite.getDecompositionID())));
        }

        return taskResource;
    }

    private Resource convertCondition(YCondition condition) {
        Resource condResource = model.createResource(
            ontology.getURI("Condition", condition.getID()),
            ontology.CONDITION);

        condResource.addProperty(ontology.CONDITION_ID, condition.getID());
        condResource.addProperty(ontology.CONDITION_NAME, condition.getName());

        if (condition == condition.getNet().getInputCondition()) {
            condResource.addProperty(RDF.type, ontology.INPUT_CONDITION);
        }
        if (condition == condition.getNet().getOutputCondition()) {
            condResource.addProperty(RDF.type, ontology.OUTPUT_CONDITION);
        }

        return condResource;
    }

    private Resource convertFlow(YFlow flow) {
        Resource flowResource = model.createResource(
            ontology.getURI("Flow", flow.getID()),
            ontology.FLOW);

        flowResource.addProperty(ontology.SOURCE_ID, flow.getSourceID());
        flowResource.addProperty(ontology.TARGET_ID, flow.getTargetID());
        flowResource.addProperty(ontology.PREDICATE, flow.getPredicate());

        if (flow.getGuard() != null) {
            flowResource.addProperty(ontology.GUARD, flow.getGuard());
        }

        return flowResource;
    }

    private void validateRDF() {
        // Load SHACL shapes (yawl-shapes.ttl)
        Model shapesModel = ModelFactory.createDefaultModel();
        shapesModel.read(getClass().getResourceAsStream("/yawl-shapes.ttl"), null, "TURTLE");

        // Run SHACL validation
        ShaclValidator validator = new ShaclValidator();
        ValidationReport report = validator.validate(model, shapesModel);

        if (!report.conforms()) {
            throw new ExportException("RDF model violates SHACL shapes: " +
                report.getConformanceModel());
        }
    }
}
```

### 2.2 YAWL RDF Ontology

```turtle
# src/main/resources/yawl-ontology.ttl
# YAWL RDF Ontology - Petri Net Semantics

@prefix yawl: <http://yawlfoundation.org/yawl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

# Root ontology
<http://yawlfoundation.org/yawl#> a owl:Ontology ;
    rdfs:label "YAWL RDF Ontology" ;
    owl:versionInfo "1.0" .

# Classes
yawl:Specification a owl:Class ;
    rdfs:label "Workflow Specification" ;
    rdfs:comment "Root specification containing one or more workflow nets" .

yawl:WorkflowNet a owl:Class ;
    rdfs:label "Workflow Net" ;
    rdfs:comment "Petri net representing a workflow" .

yawl:Task a owl:Class ;
    rdfs:label "Task (Transition)" ;
    rdfs:comment "Petri net transition representing work" .

yawl:AtomicTask a owl:Class ;
    rdfs:subClassOf yawl:Task ;
    rdfs:label "Atomic Task" .

yawl:CompositeTask a owl:Class ;
    rdfs:subClassOf yawl:Task ;
    rdfs:label "Composite Task" ;
    rdfs:comment "Task that decomposes to another net" .

yawl:Condition a owl:Class ;
    rdfs:label "Condition (Place)" ;
    rdfs:comment "Petri net place holding tokens" .

yawl:InputCondition a owl:Class ;
    rdfs:subClassOf yawl:Condition ;
    rdfs:label "Input Condition" ;
    rdfs:comment "Starting point of workflow" .

yawl:OutputCondition a owl:Class ;
    rdfs:subClassOf yawl:Condition ;
    rdfs:label "Output Condition" ;
    rdfs:comment "Ending point of workflow" .

yawl:Flow a owl:Class ;
    rdfs:label "Flow (Arc)" ;
    rdfs:comment "Connection between place and transition (Petri arc)" .

yawl:Decomposition a owl:Class ;
    rdfs:label "Decomposition" ;
    rdfs:comment "Sub-net referenced by composite task" .

# Properties
yawl:specId a owl:DatatypeProperty ;
    rdfs:domain yawl:Specification ;
    rdfs:range xsd:string .

yawl:specName a owl:DatatypeProperty ;
    rdfs:domain yawl:Specification ;
    rdfs:range xsd:string .

yawl:version a owl:DatatypeProperty ;
    rdfs:domain yawl:Specification ;
    rdfs:range xsd:string .

yawl:rootNet a owl:ObjectProperty ;
    rdfs:domain yawl:Specification ;
    rdfs:range yawl:WorkflowNet .

yawl:hasTask a owl:ObjectProperty ;
    rdfs:domain yawl:WorkflowNet ;
    rdfs:range yawl:Task .

yawl:hasCondition a owl:ObjectProperty ;
    rdfs:domain yawl:WorkflowNet ;
    rdfs:range yawl:Condition .

yawl:hasFlow a owl:ObjectProperty ;
    rdfs:domain yawl:WorkflowNet ;
    rdfs:range yawl:Flow .

yawl:sourceId a owl:DatatypeProperty ;
    rdfs:domain yawl:Flow ;
    rdfs:range xsd:string .

yawl:targetId a owl:DatatypeProperty ;
    rdfs:domain yawl:Flow ;
    rdfs:range xsd:string .

yawl:guard a owl:DatatypeProperty ;
    rdfs:label "Guard Condition" ;
    rdfs:comment "Boolean expression constraining flow" ;
    rdfs:range xsd:string .

yawl:taskId a owl:DatatypeProperty ;
    rdfs:domain yawl:Task ;
    rdfs:range xsd:string .

yawl:splitType a owl:DatatypeProperty ;
    rdfs:domain yawl:Task ;
    rdfs:range xsd:string ;  # "AND", "OR", "XOR"
    rdfs:label "Split Type (Petri net semantics)" .

yawl:joinType a owl:DatatypeProperty ;
    rdfs:domain yawl:Task ;
    rdfs:range xsd:string ;  # "AND", "OR", "XOR"
    rdfs:label "Join Type (Petri net semantics)" .

yawl:decomposesTo a owl:ObjectProperty ;
    rdfs:domain yawl:CompositeTask ;
    rdfs:range yawl:Decomposition ;
    rdfs:label "Decomposes To" ;
    rdfs:comment "Composite task references sub-net" .
```

---

## 3. Format Adapters (Week 2-3)

### 3.1 YAML Adapter

```java
// src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoYAML.java
@Component
@ExportFormat("yaml")
public class YAWLtoYAML implements ExportAdapter {

    @Override
    public String format() { return "yaml"; }

    @Override
    public String mimeType() { return "text/yaml"; }

    @Override
    public String fileExtension() { return "yaml"; }

    @Override
    public FormatAST adapt(RDFModel rdf, Map<String, Object> metadata)
        throws ExportException {
        try {
            // Extract specification resource
            Resource specResource = rdf.getResource(getSpecURI());

            String specId = specResource.getProperty(ONTOLOGY.SPEC_ID).getString();
            String specName = specResource.getProperty(ONTOLOGY.SPEC_NAME).getString();
            String version = specResource.getProperty(ONTOLOGY.VERSION).getString();
            String documentation = specResource.getProperty(RDFS.comment).getString();

            // Extract root net
            Resource netResource = specResource.getProperty(ONTOLOGY.ROOT_NET).getResource();
            List<YAMLTask> tasks = extractTasks(netResource);
            List<YAMLFlow> flows = extractFlows(netResource);
            List<YAMLDecomposition> decompositions = extractDecompositions(specResource);

            return new YAMLSpecAST(specId, specName, version, documentation,
                                   tasks, flows, decompositions);
        } catch (Exception e) {
            throw new ExportException("Failed to adapt RDF to YAML AST", e);
        }
    }

    @Override
    public SemanticCoverageReport coverage(RDFModel rdf) {
        return new SemanticCoverageReport(
            "YAML",
            0.95,
            List.of(
                new Loss("decomposition", "YAML flattens nested nets", "info", true)
            ),
            List.of(
                "All tasks and flows preserve identity",
                "Guards map directly to YAML strings"
            )
        );
    }

    @Override
    public ValidationResult validate(String content) {
        try {
            new Yaml().load(content);
            return new ValidationResult(true, List.of());
        } catch (Exception e) {
            return new ValidationResult(false, List.of(e.getMessage()));
        }
    }

    private List<YAMLTask> extractTasks(Resource netResource) {
        List<YAMLTask> tasks = new ArrayList<>();
        StmtIterator taskIter = netResource.listProperties(ONTOLOGY.HAS_TASK);

        while (taskIter.hasNext()) {
            Resource taskResource = taskIter.next().getResource();
            String id = taskResource.getProperty(ONTOLOGY.TASK_ID).getString();
            String name = taskResource.getProperty(ONTOLOGY.TASK_NAME).getString();
            String type = taskResource.hasProperty(RDF.type, ONTOLOGY.COMPOSITE_TASK)
                ? "composite" : "atomic";
            String doc = taskResource.getProperty(RDFS.comment) != null
                ? taskResource.getProperty(RDFS.comment).getString() : "";
            String guard = taskResource.getProperty(ONTOLOGY.GUARD) != null
                ? taskResource.getProperty(ONTOLOGY.GUARD).getString() : null;
            String decomp = taskResource.getProperty(ONTOLOGY.DECOMPOSES_TO) != null
                ? taskResource.getProperty(ONTOLOGY.DECOMPOSES_TO).getResource().getLocalName() : null;

            tasks.add(new YAMLTask(id, name, type, doc, guard, decomp));
        }

        return tasks;
    }

    private List<YAMLFlow> extractFlows(Resource netResource) {
        List<YAMLFlow> flows = new ArrayList<>();
        StmtIterator flowIter = netResource.listProperties(ONTOLOGY.HAS_FLOW);

        while (flowIter.hasNext()) {
            Resource flowResource = flowIter.next().getResource();
            String source = flowResource.getProperty(ONTOLOGY.SOURCE_ID).getString();
            String target = flowResource.getProperty(ONTOLOGY.TARGET_ID).getString();
            String predicate = flowResource.getProperty(ONTOLOGY.PREDICATE).getString();
            String condition = flowResource.getProperty(ONTOLOGY.GUARD) != null
                ? flowResource.getProperty(ONTOLOGY.GUARD).getString() : null;

            flows.add(new YAMLFlow(source, target, condition, predicate));
        }

        return flows;
    }

    private List<YAMLDecomposition> extractDecompositions(Resource specResource) {
        List<YAMLDecomposition> decomps = new ArrayList<>();
        StmtIterator decompIter = specResource.listProperties(ONTOLOGY.HAS_DECOMPOSITION);

        while (decompIter.hasNext()) {
            Resource decompResource = decompIter.next().getResource();
            String id = decompResource.getProperty(ONTOLOGY.DECOMP_ID).getString();
            String type = decompResource.hasProperty(RDF.type, ONTOLOGY.NET_DECOMPOSITION)
                ? "net" : "external";

            decomps.add(new YAMLDecomposition(id, type));
        }

        return decomps;
    }
}
```

### 3.2 Mermaid Adapter

```java
// src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoMermaid.java
@Component
@ExportFormat("mermaid")
public class YAWLtoMermaid implements ExportAdapter {

    @Override
    public String format() { return "mermaid"; }

    @Override
    public String mimeType() { return "text/plain"; }  // Mermaid is plain text

    @Override
    public String fileExtension() { return "mmd"; }

    @Override
    public FormatAST adapt(RDFModel rdf, Map<String, Object> metadata)
        throws ExportException {
        try {
            Resource specResource = rdf.getResource(getSpecURI());
            Resource netResource = specResource.getProperty(ONTOLOGY.ROOT_NET).getResource();

            String specName = specResource.getProperty(ONTOLOGY.SPEC_NAME).getString();
            List<MermaidNode> nodes = extractNodes(netResource);
            List<MermaidEdge> edges = extractEdges(netResource);

            return new MermaidAST(specName, nodes, edges);
        } catch (Exception e) {
            throw new ExportException("Failed to adapt RDF to Mermaid AST", e);
        }
    }

    @Override
    public SemanticCoverageReport coverage(RDFModel rdf) {
        return new SemanticCoverageReport(
            "Mermaid",
            0.85,
            List.of(
                new Loss("decomposition", "Rendered as subgraph", "info", true),
                new Loss("multi-input-semantics", "Not expressible in Mermaid", "warning", false)
            ),
            List.of(
                "All task types (atomic, composite) visualize perfectly",
                "All flow types (sequence, split, join, loop) supported",
                "Guards map to decision diamonds"
            )
        );
    }

    @Override
    public ValidationResult validate(String content) {
        try {
            // Basic mermaid syntax validation
            if (!content.contains("graph") && !content.contains("flowchart")) {
                return new ValidationResult(false,
                    List.of("Missing 'graph' or 'flowchart' declaration"));
            }
            // More sophisticated validation can use mermaid-js via JNI or REST
            return new ValidationResult(true, List.of());
        } catch (Exception e) {
            return new ValidationResult(false, List.of(e.getMessage()));
        }
    }

    private List<MermaidNode> extractNodes(Resource netResource) {
        List<MermaidNode> nodes = new ArrayList<>();

        // Input conditions
        Resource inputCond = netResource.getProperty(ONTOLOGY.INPUT_CONDITION).getResource();
        nodes.add(new MermaidNode(inputCond.getLocalName(),
            "🔵 " + getName(inputCond), MermaidNodeType.START));

        // Tasks
        StmtIterator taskIter = netResource.listProperties(ONTOLOGY.HAS_TASK);
        while (taskIter.hasNext()) {
            Resource task = taskIter.next().getResource();
            String type = task.hasProperty(RDF.type, ONTOLOGY.COMPOSITE_TASK) ? "📦" : "📌";
            nodes.add(new MermaidNode(task.getLocalName(),
                type + " " + getName(task), MermaidNodeType.TASK));
        }

        // Output conditions
        Resource outputCond = netResource.getProperty(ONTOLOGY.OUTPUT_CONDITION).getResource();
        nodes.add(new MermaidNode(outputCond.getLocalName(),
            "🏁 " + getName(outputCond), MermaidNodeType.END));

        return nodes;
    }

    private List<MermaidEdge> extractEdges(Resource netResource) {
        List<MermaidEdge> edges = new ArrayList<>();
        StmtIterator flowIter = netResource.listProperties(ONTOLOGY.HAS_FLOW);

        while (flowIter.hasNext()) {
            Resource flow = flowIter.next().getResource();
            String source = flow.getProperty(ONTOLOGY.SOURCE_ID).getString();
            String target = flow.getProperty(ONTOLOGY.TARGET_ID).getString();
            String label = flow.getProperty(ONTOLOGY.GUARD) != null
                ? truncate(flow.getProperty(ONTOLOGY.GUARD).getString(), 20) : null;

            edges.add(new MermaidEdge(source, target, label));
        }

        return edges;
    }

    private String getName(Resource resource) {
        return resource.hasProperty(ONTOLOGY.TASK_NAME)
            ? resource.getProperty(ONTOLOGY.TASK_NAME).getString()
            : resource.hasProperty(ONTOLOGY.CONDITION_NAME)
            ? resource.getProperty(ONTOLOGY.CONDITION_NAME).getString()
            : resource.getLocalName();
    }

    private String truncate(String s, int len) {
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}

record MermaidNode(String id, String label, MermaidNodeType type) {}
record MermaidEdge(String from, String to, String label) {}
enum MermaidNodeType { START, TASK, DECISION, END }
```

### 3.3 Turtle/RDF Adapter

```java
// src/org/yawlfoundation/yawl/integration/export/adapters/YAWLtoTurtle.java
@Component
@ExportFormat("turtle")
public class YAWLtoTurtle implements ExportAdapter {

    @Override
    public String format() { return "turtle"; }

    @Override
    public String mimeType() { return "text/turtle"; }

    @Override
    public String fileExtension() { return "ttl"; }

    /**
     * Turtle export is identity mapping (RDF IS canonical form).
     * This adapter simply serializes the RDF model in Turtle syntax.
     */
    @Override
    public FormatAST adapt(RDFModel rdf, Map<String, Object> metadata)
        throws ExportException {
        // No transformation needed; RDF is already canonical form
        return new TurtleAST(rdf.getModel());
    }

    @Override
    public SemanticCoverageReport coverage(RDFModel rdf) {
        return new SemanticCoverageReport(
            "Turtle/RDF",
            1.0,  // 100% coverage (canonical form)
            List.of(),  // No losses
            List.of(
                "RDF is canonical representation",
                "Petri net semantics fully preserved",
                "Supports SPARQL queries",
                "Federation-ready"
            )
        );
    }

    @Override
    public ValidationResult validate(String content) {
        try {
            Model testModel = ModelFactory.createDefaultModel();
            testModel.read(new StringReader(content), null, "TURTLE");
            return new ValidationResult(true, List.of());
        } catch (Exception e) {
            return new ValidationResult(false, List.of(e.getMessage()));
        }
    }
}

record TurtleAST(Model rdfModel) extends FormatAST {
    public TurtleAST(Model model) {
        super(extractSpecId(model),
              extractSpecName(model),
              extractVersion(model));
        // ... initialization
    }
}
```

---

## 4. REST API Controller (Week 4)

```java
// src/org/yawlfoundation/yawl/integration/export/api/ExportController.java
@RestController
@RequestMapping("/api/specs")
@RequiredArgsConstructor
public class ExportController {

    private final YSpecificationService specService;
    private final ExportAdapterRegistry adapterRegistry;
    private final ExportCache exportCache;
    private final GgenService ggenService;

    /**
     * GET /api/specs/{specId}/export?format=yaml|mermaid|turtle|puml
     */
    @GetMapping("/{specId}/export")
    public ResponseEntity<String> exportSpecification(
        @PathVariable String specId,
        @RequestParam String format,
        @RequestParam(defaultValue = "false") boolean noCache,
        HttpServletResponse response) {

        try {
            YSpecification spec = specService.getSpecification(specId);
            if (spec == null) {
                return ResponseEntity.notFound().build();
            }

            String content = noCache
                ? performExport(spec, format)
                : exportCache.getOrExport(spec, format,
                    () -> performExport(spec, format));

            response.setContentType(adapterRegistry.get(format).mimeType());
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + specId + "." +
                adapterRegistry.get(format).fileExtension() + "\"");

            return ResponseEntity.ok(content);
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.badRequest()
                .body(jsonError("unsupported_format",
                    "Supported formats: " + adapterRegistry.availableFormats()));
        } catch (ExportException e) {
            return ResponseEntity.status(500)
                .body(jsonError("export_failed", e.getMessage()));
        }
    }

    private String performExport(YSpecification spec, String format) throws ExportException {
        RDFModel rdf = YAWLtoRDF.convert(spec);
        ExportAdapter adapter = adapterRegistry.get(format);
        FormatAST ast = adapter.adapt(rdf, Map.of("specId", spec.getID()));
        return ggenService.render(format, ast);
    }

    /**
     * POST /api/specs/{specId}/validate-export
     */
    @PostMapping("/{specId}/validate-export")
    public ResponseEntity<?> validateExport(
        @PathVariable String specId,
        @RequestBody ValidateExportRequest request) {

        try {
            ExportAdapter adapter = adapterRegistry.get(request.format());
            ValidationResult result = adapter.validate(request.content());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                jsonError("validation_error", e.getMessage()));
        }
    }

    /**
     * GET /api/specs/{specId}/export-coverage?format=mermaid
     */
    @GetMapping("/{specId}/export-coverage")
    public ResponseEntity<?> exportCoverage(
        @PathVariable String specId,
        @RequestParam String format) {

        try {
            YSpecification spec = specService.getSpecification(specId);
            RDFModel rdf = YAWLtoRDF.convert(spec);
            SemanticCoverageReport coverage =
                adapterRegistry.get(format).coverage(rdf);
            return ResponseEntity.ok(coverage);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                jsonError("coverage_error", e.getMessage()));
        }
    }

    /**
     * GET /api/specs - list available export formats
     */
    @GetMapping("/export-formats")
    public ResponseEntity<?> listFormats() {
        return ResponseEntity.ok(Map.of(
            "formats", adapterRegistry.availableFormats()
        ));
    }

    private String jsonError(String code, String message) {
        return String.format("{\"error\": \"%s\", \"message\": \"%s\"}", code, message);
    }
}

record ValidateExportRequest(String format, String content) {}
```

---

## 5. ggen Template Integration

### 5.1 Updated ggen.toml Entry

```toml
# Add to [generation.rules] section

[[generation.rules]]
name = "export-yaml"
description = "Generate YAML export via plugin"
query = { inline = """
PREFIX yawl: <http://yawlfoundation.org/yawl#>
SELECT ?specId ?specName ?version
WHERE {
  ?spec a yawl:Specification ;
        yawl:specId ?specId ;
        yawl:specName ?specName ;
        yawl:version ?version .
}
""" }
template = { file = "templates/export/yaml.tera" }
output_file = "exports/{{ specId }}.yaml"
mode = "Overwrite"

[[generation.rules]]
name = "export-mermaid"
description = "Generate Mermaid export via plugin"
query = { inline = "..." }
template = { file = "templates/export/mermaid.tera" }
output_file = "exports/{{ specId }}.mmd"
mode = "Overwrite"

# ... similar for turtle, puml, jsonschema, graphql
```

---

## 6. MCP Tool Registration

```java
// Extend YawlMcpServer to register export tools
@MCPTool
public class ExportWorkflowMCPTool {
    @MCPInput(description = "Workflow specification ID")
    String specId;

    @MCPInput(description = "Export format: yaml, mermaid, turtle, puml")
    String format;

    @MCPOutput(description = "Exported workflow content")
    String content;

    @Override
    public void execute() throws MCPException {
        // Invokes ExportController.exportSpecification()
    }
}
```

---

## 7. Testing Strategy

```java
// src/test/java/org/yawlfoundation/yawl/integration/export/ExportAdapterTest.java
@SpringBootTest
public class ExportAdapterTest {

    @Autowired
    private ExportAdapterRegistry registry;

    @Test
    void testYAMLAdapterCompleteness() throws Exception {
        YSpecification spec = loadLoanApprovalWorkflow();
        RDFModel rdf = YAWLtoRDF.convert(spec);

        ExportAdapter yamlAdapter = registry.get("yaml");
        FormatAST ast = yamlAdapter.adapt(rdf, Map.of());

        assertNotNull(ast);
        assertEquals("yaml", yamlAdapter.format());
    }

    @Test
    void testMermaidDiagramValidity() throws Exception {
        YSpecification spec = loadLoanApprovalWorkflow();
        RDFModel rdf = YAWLtoRDF.convert(spec);

        ExportAdapter mermaidAdapter = registry.get("mermaid");
        // ... render via ggen

        ValidationResult result = mermaidAdapter.validate(mermaidContent);
        assertTrue(result.valid());
    }

    @Test
    void testRDFRoundTrip() throws Exception {
        YSpecification original = loadLoanApprovalWorkflow();
        Model rdfModel = YAWLtoRDF.convert(original).getModel();
        YSpecification roundTripped = RDFtoYAWL.convert(rdfModel);

        assertEquals(original.getID(), roundTripped.getID());
        assertEquals(original.getRootNet().getTasks().size(),
                     roundTripped.getRootNet().getTasks().size());
    }
}
```

---

**Status**: Implementation Specification Complete
**Next**: Week 1 RDF Foundation, Week 2-3 Format Adapters, Week 4 API

