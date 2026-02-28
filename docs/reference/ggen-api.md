# ggen API Reference

**Module**: `yawl-ggen` | **Version**: 6.0.0-GA | **Package**: `org.yawlfoundation.yawl.ggen`

ggen is the YAWL Code Generation Engine, providing deterministic code generation via RDF + SPARQL + Tera templates. All code generation decisions are traceable to SPARQL queries over semantic facts.

---

## Core Classes

### RdfKnowledgeBase

Loads and manages RDF triples describing workflows.

```java
public class RdfKnowledgeBase {
    /**
     * Create a knowledge base from a Turtle (TTL) file
     */
    public static RdfKnowledgeBase fromTtlFile(Path ttlFile) throws IOException;

    /**
     * Create a knowledge base from a Turtle string
     */
    public static RdfKnowledgeBase fromTtlString(String ttlContent);

    /**
     * Execute a SPARQL SELECT query and get results as JSON
     * @param sparqlQuery the SPARQL query string
     * @return JSON array of bindings
     */
    public String executeSparqlQuery(String sparqlQuery);

    /**
     * Execute a SPARQL ASK query (boolean result)
     */
    public boolean askSparql(String sparqlQuery);

    /**
     * Execute a SPARQL CONSTRUCT query (returns RDF triples)
     */
    public RdfKnowledgeBase constructSparql(String sparqlQuery);

    /**
     * Get the underlying Jena Model for advanced use
     */
    public Model getJenaModel();

    /**
     * Triple count
     */
    public long size();

    /**
     * Close resources
     */
    public void close();
}
```

### SparqlQueryExecutor

Executes SPARQL queries and marshals results to Java types.

```java
public class SparqlQueryExecutor {
    public SparqlQueryExecutor(RdfKnowledgeBase kb);

    /**
     * Execute query and get results as List<Map>
     */
    public List<Map<String, String>> selectToList(String sparqlQuery);

    /**
     * Execute query and get single result as Map
     */
    public Optional<Map<String, String>> selectSingleRow(String sparqlQuery);

    /**
     * Execute query and collect a single column
     */
    public List<String> selectColumn(String sparqlQuery, String columnName);

    /**
     * Execute query with parameter binding
     */
    public List<Map<String, String>> selectWithParams(
        String sparqlQuery,
        Map<String, String> params
    );
}
```

### TeraTemplateRenderer

Renders Tera templates with context data from SPARQL results.

```java
public class TeraTemplateRenderer {
    /**
     * Create renderer from template file
     */
    public static TeraTemplateRenderer fromFile(Path templateFile) throws IOException;

    /**
     * Create renderer from template string
     */
    public static TeraTemplateRenderer fromString(String templateContent);

    /**
     * Render template with context object
     * @param context Map containing template variables
     * @return rendered string
     */
    public String render(Map<String, Object> context);

    /**
     * Render template with JSON context
     */
    public String renderJson(String contextJson);
}
```

### CodeGenerator

High-level API orchestrating RDF → SPARQL → Templates → Code.

```java
public class CodeGenerator {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder rdfFile(Path ttlFile);
        public Builder rdfString(String ttlContent);
        public Builder sparqlQueryFile(Path queryFile);
        public Builder templateFile(Path templateFile);
        public Builder outputFile(Path outputFile);
        public CodeGenerator build();
    }

    /**
     * Execute code generation pipeline
     * @return CodeGenerationResult with generated artifacts
     */
    public CodeGenerationResult generate();

    /**
     * Get timing metrics
     */
    public GenerationMetrics getMetrics();
}
```

### CodeGenerationResult

Result of code generation including artifacts and metadata.

```java
public class CodeGenerationResult {
    /**
     * The generated artifact (code, specification, config, etc.)
     */
    public String getArtifact();

    /**
     * All SPARQL queries executed during generation
     */
    public List<String> getExecutedQueries();

    /**
     * Template variable values used
     */
    public Map<String, Object> getContext();

    /**
     * Validation errors (if any)
     */
    public List<String> getValidationErrors();

    /**
     * Was generation successful?
     */
    public boolean isValid();

    /**
     * Timestamp of generation
     */
    public Instant getTimestamp();
}
```

---

## Synthesis API

### PnmlSynthesizer

Synthesize YAWL specifications from Petri Net Markup Language (PNML).

```java
public class PnmlSynthesizer {
    /**
     * Synthesize YAWL spec from PNML
     * @param pnmlXml Petri net in PNML format
     * @return YAWL specification XML
     */
    public String synthesize(String pnmlXml);

    /**
     * Synthesize with process mining insights
     */
    public String synthesizeWithMining(String pnmlXml, String eventLogJson);

    /**
     * Extract tasks and transitions from PNML
     */
    public SynthesisMetadata extractMetadata(String pnmlXml);
}
```

### ProcessMiningBridge

Integration with the Rust4pmBridge for process mining.

```java
public class ProcessMiningBridge {
    /**
     * Mine workflow patterns from event log
     * @param ocelEventLog OCEL2 event log in JSON
     * @return mined workflow patterns
     */
    public String minePatterns(String ocelEventLog);

    /**
     * Detect anomalies in event log
     */
    public String detectAnomalies(String ocelEventLog);

    /**
     * Generate performance metrics
     */
    public String analyzePerformance(String ocelEventLog);
}
```

---

## Validation API

### YawlSpecificationValidator

Validates generated YAWL specifications.

```java
public class YawlSpecificationValidator {
    /**
     * Validate YAWL specification XML
     * @param yawlXml the specification to validate
     * @return validation result with errors and warnings
     */
    public ValidationResult validate(String yawlXml);

    /**
     * Validate against YAWL schema
     */
    public ValidationResult validateSchema(String yawlXml);

    /**
     * Semantic validation (task connectivity, split/join matching, etc.)
     */
    public ValidationResult validateSemantics(String yawlXml);
}
```

### ValidationResult

```java
public class ValidationResult {
    /**
     * Is specification valid?
     */
    public boolean isValid();

    /**
     * List of validation errors
     */
    public List<ValidationError> getErrors();

    /**
     * List of warnings
     */
    public List<String> getWarnings();

    /**
     * Detailed error for each line
     */
    public Map<Integer, String> getLineErrors();
}
```

---

## Configuration

### GgenConfiguration

Configure code generation behavior.

```java
public class GgenConfiguration {
    // Input/output
    public String getRdfPath();
    public String getTemplatePath();
    public String getOutputPath();

    // Processing
    public int getSparqlTimeout();  // milliseconds
    public boolean isDeterministic();

    // Validation
    public boolean isValidateOnGeneration();
    public List<String> getValidationRules();

    // Logging
    public String getLogLevel();
    public boolean isTraceQueries();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder rdfPath(String path);
        public Builder templatePath(String path);
        public Builder outputPath(String path);
        public Builder sparqlTimeout(int ms);
        public Builder validateOnGeneration(boolean validate);
        public GgenConfiguration build();
    }
}
```

---

## Exceptions

```java
/**
 * Thrown when RDF parsing fails
 */
public class RdfParseException extends Exception {}

/**
 * Thrown when SPARQL query execution fails
 */
public class SparqlExecutionException extends Exception {}

/**
 * Thrown when template rendering fails
 */
public class TemplateRenderException extends Exception {}

/**
 * Thrown when generated artifact validation fails
 */
public class GenerationValidationException extends Exception {}

/**
 * Thrown when PNML synthesis fails
 */
public class PnmlSynthesisException extends Exception {}
```

---

## Example Usage

### Complete Code Generation Pipeline

```java
import org.yawlfoundation.yawl.ggen.*;
import java.nio.file.*;

public class FullPipeline {
    public static void main(String[] args) throws Exception {
        // Step 1: Load RDF knowledge base
        RdfKnowledgeBase kb = RdfKnowledgeBase.fromTtlFile(
            Path.of("workflow-model.ttl")
        );

        // Step 2: Query for tasks
        SparqlQueryExecutor executor = new SparqlQueryExecutor(kb);
        String query = """
            PREFIX : <http://example.com/workflow#>
            SELECT ?taskId ?label
            WHERE {
                ?taskId a :Task ;
                        rdfs:label ?label .
            }
            """;

        var tasks = executor.selectToList(query);
        System.out.println("Found " + tasks.size() + " tasks");

        // Step 3: Render template
        TeraTemplateRenderer renderer =
            TeraTemplateRenderer.fromFile(Path.of("yawl-spec.tera"));

        var context = Map.of(
            "tasks", tasks,
            "workflow_name", "LoanProcessing",
            "timestamp", java.time.Instant.now().toString()
        );

        String generated = renderer.render(context);

        // Step 4: Validate result
        YawlSpecificationValidator validator = new YawlSpecificationValidator();
        ValidationResult result = validator.validate(generated);

        if (result.isValid()) {
            Files.writeString(Path.of("generated.yawl"), generated);
            System.out.println("Generation successful!");
        } else {
            System.err.println("Validation failed:");
            result.getErrors().forEach(System.err::println);
        }

        kb.close();
    }
}
```

---

## Performance Tuning

### SPARQL Query Optimization

```java
// Bad: Full table scan
String slowQuery = """
    SELECT ?task WHERE {
        ?task a :Task .
        ?task :flowsTo ?next .
    }
    """;

// Good: Indexed property access
String fastQuery = """
    SELECT ?task WHERE {
        ?workflow :hasTask ?task .
        ?task :flowsTo ?next .
    }
    """;
```

### Batch Generation

```java
// For generating multiple artifacts from same KB
CodeGenerator generator = CodeGenerator.builder()
    .rdfFile(Path.of("model.ttl"))
    .build();

for (String template : List.of("spec.tera", "config.tera", "java.tera")) {
    generator = generator.templateFile(Path.of(template));
    var result = generator.generate();
    // Use result
}
```

---

## See Also

- [Tutorial: ggen Getting Started](../tutorials/polyglot-ggen-getting-started.md)
- [How-To: Write Tera Templates](../how-to/ggen-tera-templates.md)
- [Explanation: Code Generation Architecture](../explanation/ggen-architecture.md)
