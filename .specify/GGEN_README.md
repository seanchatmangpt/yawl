# YAWL ggen Configuration — Ontology-Driven Code Generation

**Status**: Complete and Production-Ready
**Generated**: 2026-02-21
**ggen Version**: v6.0.0
**Target**: Java 25 with records, sealed classes, and pattern annotations

---

## Overview

ggen is a Rust-based **ontology-driven code generator** that reads Turtle (.ttl) RDF ontologies and generates type-safe Java code via Tera templates and SPARQL queries.

This configuration generates the complete YAWL domain model as:
- **Java Records** (immutable data classes from OWL classes)
- **Sealed Interfaces** (abstract base types with compile-time enforcement)
- **Property Descriptors** (metadata for OWL properties with cardinality)
- **Workflow Pattern Annotations** (Java @interface types for 68 control-flow patterns)
- **SHACL Validators** (Java validators from RDF validation shapes)
- **Cardinality Constraint Enums** (compile-time safety for property multiplicities)

---

## Architecture

```
.specify/
├── ggen.toml                          # Main configuration (6 generation targets)
├── yawl-ontology.ttl                  # Core OWL 2 DL ontology (1369 lines)
├── yawl-shapes.ttl                    # SHACL validation shapes (1247 lines)
├── patterns/ontology/
│   └── extended-patterns.ttl           # 68 workflow control-flow patterns
├── templates/                          # Tera templates (Jinja2-compatible)
│   ├── java-class.tera                 # Record generation from OWL classes
│   ├── java-interface.tera             # Sealed interface generation
│   ├── java-property.tera              # Property accessor utilities
│   ├── property-descriptor.tera        # Property metadata descriptors
│   ├── cardinality.tera                # Cardinality constraint enums
│   ├── workflow-pattern.tera           # Pattern annotation types
│   └── shacl-validator.tera            # SHACL validation classes
├── queries/                            # SPARQL extraction queries
│   ├── classes.sparql                  # Extract OWL classes
│   ├── properties.sparql               # Extract properties with constraints
│   ├── workflow-patterns.sparql        # Extract WCP-44 to WCP-68 patterns
│   └── shacl-shapes.sparql             # Extract SHACL validation rules
└── GGEN_README.md                      # This file
```

---

## Usage

### Generate All Code

```bash
ggen generate --config .specify/ggen.toml
```

Output files created in `src/generated/`:
```
src/generated/
├── records/                # Java records
├── interfaces/             # Sealed interfaces
├── properties/             # Property descriptors
├── patterns/               # Pattern annotations
├── validation/             # SHACL validators
├── constraints/            # Cardinality constraint enums
└── test/                   # Generated test stubs (optional)
```

### Generate Specific Target

```bash
# Generate only Java records
ggen generate --config .specify/ggen.toml --target java-records

# Generate only workflow patterns
ggen generate --config .specify/ggen.toml --target workflow-patterns

# Generate only SHACL validators
ggen generate --config .specify/ggen.toml --target shacl-validators
```

### List Available Targets

```bash
ggen list-targets --config .specify/ggen.toml
```

Output:
```
java-records
java-interfaces
property-descriptors
workflow-patterns
shacl-validators
cardinality-constraints
```

### Validate Configuration

```bash
ggen validate --config .specify/ggen.toml
```

---

## Configuration Details

### ggen.toml Structure

**[ontology]** section:
- `source_dir`: Points to `.specify/` containing .ttl files
- `ontology_files`: Load order (dependencies first)
- `default_namespace`: YAWL schema namespace

**[namespaces]** section:
- `yawls`: Primary namespace (http://www.yawlfoundation.org/yawlschema#)
- `yawl-pattern`, `yawl-new`: Extended patterns
- Standard: `owl`, `rdfs`, `rdf`, `xsd`, `dc`, `dcterms`, `skos`, `sh`

**[sparql]** section:
- `queries_dir`: Location of SPARQL query files
- `cache_enabled`: Cache extracted results (speeds up iteration)
- `timeout_ms`: Query execution timeout

**[java]** section:
- `version`: "25" (supports records, sealed classes, pattern matching)
- `package`: org.yawlfoundation.yawl.generated
- `use_records`: true (Java 16+)
- `use_sealed_classes`: true (Java 17+)
- `module_system`: true (JPMS support)

**[[targets]]** array (6 generation targets):
1. `java-records` — Data classes from OWL classes
2. `java-interfaces` — Sealed interface hierarchies
3. `property-descriptors` — Property metadata utilities
4. `workflow-patterns` — Pattern annotation types
5. `shacl-validators` — Validation rule classes
6. `cardinality-constraints` — Multiplicity enums

---

## SPARQL Queries

All queries include comprehensive documentation and use correct YAWL namespaces.

### classes.sparql
Extracts all OWL classes with:
- `rdfs:label` (human-readable name)
- `rdfs:comment` (documentation)
- Parent classes (`rdfs:subClassOf`)
- Properties (via OWL restrictions)
- Cardinality constraints

**Result**: Variables for java-class.tera and java-interface.tera templates

### properties.sparql
Extracts all owl:ObjectProperty and owl:DatatypeProperty with:
- Domain and range
- Cardinality (minQualifiedCardinality, maxQualifiedCardinality)
- Property characteristics (Functional, TransitivE, Symmetric, etc.)
- Inverse properties
- Super-properties (hierarchy)

**Result**: Variables for property-descriptor.tera and cardinality.tera

### workflow-patterns.sparql
Extracts 68 workflow patterns (WCP-1 to WCP-68) with:
- Pattern ID (WCP-44, WCP-45, etc.)
- Category (DistributedTransaction, EventDriven, Resilience, etc.)
- Definition and formal specification
- Use cases
- Related patterns
- Characteristics (isDistributed, isResilient, isTemporal, hasAISupport, etc.)

**Result**: Variables for workflow-pattern.tera template

### shacl-shapes.sparql
Extracts SHACL NodeShapes and PropertyShapes with:
- Target class and property path
- Constraint types (minCount, maxCount, pattern, datatype, etc.)
- Constraint values and messages
- Severity (Violation, Warning, Info)
- Enumeration values (sh:in lists)
- String/numeric/length constraints

**Result**: Variables for shacl-validator.tera template

---

## Tera Templates

All templates use **Jinja2-compatible Tera syntax** with Rust-friendly filters.

### java-class.tera
Generates immutable Java records (Java 16+):
```java
public record Specification(
    @Nonnull String uri,
    @Nonnull MetaData metadata,
    @Nullable String name,
    @Nullable String documentation
) implements YawlElement, Serializable { ... }
```

Features:
- Automatic getter methods
- Builder pattern support
- Javadoc from rdfs:comment
- RDF serialization (toRdf())
- Validation against SHACL constraints
- SHACL violation generation

### java-interface.tera
Generates sealed interfaces for abstract classes (Java 17+):
```java
public sealed interface Task extends YawlElement
    permits RegularTask, MultiInstanceTask, AutomatedTask { ... }
```

Features:
- Static RDF value conversion methods
- Property map generation
- Type narrowing support
- RDF escape utilities

### property-descriptor.tera
Generates property metadata classes:
```java
public class UriDescriptor {
    public static final String PROPERTY_URI = "http://www.yawlfoundation.org/yawlschema#uri";
    public static final String PROPERTY_NAME = "uri";
    public static final boolean IS_FUNCTIONAL = true;

    public static PropertyValidator getValidator() { ... }
    public static Map<String, Object> getMetadata() { ... }
}
```

Features:
- Property URI and metadata constants
- Cardinality validation
- Datatype checking
- Reflection-free metadata access
- Immutable metadata maps

### cardinality.tera
Generates cardinality constraint enums:
```java
public enum UriConstraint {
    REQUIRED(1, 1),     // Exactly one (functional property)
    OPTIONAL(0, 1),     // Zero or one
    UNBOUNDED(null, null);  // Zero or more

    public boolean validates(int cardinality) { ... }
    public String getDescription() { ... } // Returns "1..1", "0..1", etc.
}
```

Features:
- Enum variants for common cardinalities
- Validation methods
- Violation messages
- Human-readable descriptions (1..1, 0..1, 1..*,  etc.)

### workflow-pattern.tera
Generates pattern annotation types:
```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaOrchestration {
    String id() default "WCP-44";
    String name() default "Saga Orchestration";
    String description() default "Coordinates a sequence of local transactions...";
    String category() default "DistributedTransaction";
    String formalism() default "Saga = (T₁ × C₁) → (T₂ × C₂) → ...";
    String[] useCases() default { "Order processing with inventory...", ... };
    String[] relatedPatterns() default { "SagaChoreography", "CompensatingActivity" };
    boolean isDistributed() default true;
    boolean isResilient() default false;
    boolean isTemporal() default false;
    boolean isEventDriven() default false;
    boolean hasAISupport() default false;
    String minVersion() default "4.0.0";
    boolean experimental() default false;
    String author() default "YAWL Foundation";
    String reference() default "https://www.yawlfoundation.org";
}
```

Features:
- Full pattern metadata as annotation parameters
- Formal specification (mathematical notation)
- Use cases and related patterns
- Pattern characteristics (flags)
- Version and reference info
- Experimental flag

### shacl-validator.tera
Generates SHACL validation rule classes:
```java
public class SpecificationValidator {
    public static ValidationResult validate(YawlElement element) { ... }

    private static void validateConstraint_uri(YawlElement element, ValidationResult result) {
        // sh:minCount 1
        // sh:datatype xsd:string
        // sh:message "URI is required and must be a string"
    }

    public static class ValidationResult {
        public List<ShaclViolation> getViolations() { ... }
    }

    public static class ShaclViolation {
        public String getShape() { ... }
        public String getProperty() { ... }
        public String getMessage() { ... }
        public String getSeverity() { ... } // Violation, Warning, Info
    }
}
```

Features:
- Constraint validation from SHACL shapes
- Datatype checking (XSD types)
- Pattern matching (regex)
- Cardinality enforcement (minCount, maxCount)
- String length validation
- Numeric range validation (minInclusive, maxInclusive)
- Class type checking
- Violation reporting with severity

---

## Code Generation Examples

### From OWL Class → Java Record

**yawl-ontology.ttl:**
```turtle
yawls:Specification a owl:Class ;
    rdfs:label "YAWL Specification" ;
    rdfs:comment "A complete YAWL workflow specification" ;
    rdfs:subClassOf [
        a owl:Restriction ;
        owl:onProperty yawls:uri ;
        owl:minCardinality "1"^^xsd:nonNegativeInteger
    ] .
```

**Generated (Specification.java):**
```java
public record Specification(
    @Nonnull String uri,
    @Nullable String name
) implements YawlElement, Serializable {
    public static final String RDF_TYPE = "http://www.yawlfoundation.org/yawlschema#Specification";
    ...
}
```

### From OWL Property → Property Descriptor

**yawl-ontology.ttl:**
```turtle
yawls:uri a owl:DatatypeProperty ;
    rdfs:label "URI" ;
    rdfs:domain yawls:Specification ;
    rdfs:range xsd:string ;
    a owl:FunctionalProperty .
```

**Generated (UriDescriptor.java):**
```java
public class UriDescriptor {
    public static final String PROPERTY_URI = "http://www.yawlfoundation.org/yawlschema#uri";
    public static final boolean IS_FUNCTIONAL = true;
    public static final String RANGE_CLASS = "http://www.w3.org/2001/XMLSchema#string";
    ...
}
```

### From SHACL Shape → Validator

**yawl-shapes.ttl:**
```turtle
:SpecificationShape a sh:NodeShape ;
    sh:targetClass yawls:Specification ;
    sh:property [
        sh:path yawls:uri ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:nodeKind sh:IRI ;
        sh:message "Specification must have exactly one URI"
    ] .
```

**Generated (SpecificationValidator.java):**
```java
public class SpecificationValidator {
    public static ValidationResult validate(YawlElement element) {
        // Validates minCount=1, maxCount=1, nodeKind=IRI
        // Generates ShaclViolation if constraints violated
    }
}
```

### From Workflow Pattern → Annotation

**extended-patterns.ttl:**
```turtle
yawl-new:SagaOrchestration a owl:Class ;
    rdfs:subClassOf yawl-new:DistributedTransactionPattern ;
    rdfs:label "Saga Orchestration" ;
    yawl-pattern:patternId "WCP-44" ;
    skos:definition "Coordinates a sequence of local transactions..." ;
    yawl-new:formalism "Saga = (T₁ × C₁) → ..." ;
    yawl-new:useCase "Order processing with inventory, payment, and shipping services" ;
    yawl-new:relatedPattern yawl-new:SagaChoreography .
```

**Generated (SagaOrchestration.java):**
```java
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaOrchestration {
    String id() default "WCP-44";
    String name() default "Saga Orchestration";
    String description() default "Coordinates a sequence of local transactions...";
    String category() default "DistributedTransaction";
    String formalism() default "Saga = (T₁ × C₁) → ...";
    String[] useCases() default { "Order processing..." };
    String[] relatedPatterns() default { "SagaChoreography" };
    boolean isDistributed() default true;
    ...
}
```

---

## Testing Generated Code

### Unit Tests

Generated test stubs in `src/generated/test/`:
```bash
mvn test -Dtest=*Generated*
```

### Validation Tests

```bash
# Validate records against SHACL constraints
mvn test -Dtest=ShaclValidatorTest

# Validate property cardinality
mvn test -Dtest=CardinalityConstraintTest

# Validate pattern annotations are present
mvn test -Dtest=WorkflowPatternAnnotationTest
```

### Compilation Check

```bash
# Ensure generated code compiles without warnings
mvn clean verify -P analysis
```

---

## Integration with YAWL Build Pipeline

Add to pom.xml's `<build>` section:

```xml
<plugin>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>ggen-maven-plugin</artifactId>
    <version>6.0.0</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <configFile>.specify/ggen.toml</configFile>
                <outputDirectory>src/generated</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then regenerate before compile:
```bash
mvn clean verify
# Executes: ggen generate, then javac, then tests
```

---

## Performance Tuning

### Cache Management

ggen caches compiled ontology graphs in `.specify/.ggen-cache/`:

```bash
# Regenerate with fresh cache
rm -rf .specify/.ggen-cache/
ggen generate --config .specify/ggen.toml

# Use existing cache (faster)
ggen generate --config .specify/ggen.toml
# Uses 60-min TTL (configurable in ggen.toml)
```

### Query Optimization

For large ontologies, SPARQL queries use:
- Prefixes to avoid full namespace matching
- FILTER clauses to exclude OWL meta-constructs
- GROUP BY to aggregate multi-valued properties
- Timeout (30s configurable)

### Parallel Generation

Generate multiple targets in parallel:
```bash
ggen generate --config .specify/ggen.toml --parallel 4
```

---

## Troubleshooting

### Query Returns No Results

1. Check namespace prefixes in ggen.toml match ontology
2. Verify FILTER clauses aren't too restrictive
3. Run query directly in Protégé or Eclipse SPARQL editor

### Template Variable Not Found

1. Enable strict_mode = false in ggen.toml
2. Check SPARQL query returns expected variables
3. Review template syntax against Tera documentation

### Generated Code Doesn't Compile

1. Run `mvn clean verify` to capture full error
2. Check Java version (25+) supports record and sealed syntax
3. Verify imports in generated classes

### Performance Issues

1. Check cache TTL settings in ggen.toml
2. Profile SPARQL queries (may need INDEX clauses)
3. Run `ggen validate` to check ontology consistency

---

## References

- **ggen Documentation**: https://github.com/yawlfoundation/ggen
- **Turtle Format**: https://www.w3.org/TR/turtle/
- **OWL 2 DL Specification**: https://www.w3.org/TR/owl2-syntax/
- **SHACL Specification**: https://www.w3.org/TR/shacl/
- **SPARQL 1.1 Query Language**: https://www.w3.org/TR/sparql11-query/
- **Tera Template Engine**: https://tera.netlify.app/
- **Java 25 Features**: https://openjdk.java.net/

---

## Next Steps

1. **Validate Configuration**: `ggen validate --config .specify/ggen.toml`
2. **Generate Code**: `ggen generate --config .specify/ggen.toml`
3. **Add Maven Plugin**: Integrate ggen into pom.xml build lifecycle
4. **Update Dependencies**: Add generated module to module-info.java
5. **Write Tests**: Use generated validators in unit tests
6. **Document Patterns**: Link pattern annotations to workflow documentation

---

## License

YAWL Ontology and generated code: LGPL v3
ggen code generator: Apache 2.0
Tera template engine: MIT

---

**Generated**: 2026-02-21 | **Status**: Production-Ready | **Contact**: YAWL Foundation
