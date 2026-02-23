# YAWL Extension-Only Validation

**Purpose**: Enforce the architectural constraint that all YAWL workflow specifications must extend public ontologies (Schema.org, PROV-O, Dublin Core, etc.) rather than creating isolated proprietary class hierarchies.

**Principle**: YAWL is middleware for enterprise workflow. Semantic alignment to standard reference models enables interoperability, integration, automated reasoning, and knowledge sharing. Proprietary-only instances and classes impede these goals.

## Files

### 1. extension-only.sparql

SPARQL SELECT query that finds YAWL workflow instances (ABox) that violate the extension-only constraint.

**What it checks**:
- Instances with `rdf:type` pointing to `yawl:` namespace classes
- Violation if instance has NO public ontology types alongside YAWL types
- Public namespaces recognized: schema.org, prov, dcterms, http://www.w3.org/, http://purl.org/

**Expected result**: Empty (zero violations = compliant)

**Example violation**:
```turtle
:myWorkflow a yawl:Specification .     # Only YAWL type
# Missing: :myWorkflow a schema:Event ; or prov:Activity ; etc.
```

**Example compliant**:
```turtle
:myWorkflow a yawl:Specification ;     # YAWL type
            a schema:Event ;           # Public ontology type
            a prov:Activity .          # Another public type
```

### 2. proprietary-root-detector.sparql

SPARQL SELECT query that finds YAWL ontology classes (TBox) that declare no public superclasses.

**What it checks**:
- Classes with `a owl:Class` in YAWL namespace
- Violation if class has NO `rdfs:subClassOf` to public ontology
- Violation if class has no `owl:unionOf` or `owl:intersectionOf` with public types
- Detects "proprietary roots" — classes not grounded in public ontologies

**Expected result**: Empty (zero violations = compliant)

**Example violation**:
```turtle
yawl:MyCustomType a owl:Class .
# Missing: yawl:MyCustomType rdfs:subClassOf schema:Thing ;
```

**Example compliant**:
```turtle
yawl:MyCustomType a owl:Class ;
                  rdfs:subClassOf schema:Thing .

yawl:AnotherType a owl:Class ;
                 owl:unionOf ( schema:Event prov:Activity ) .
```

### 3. extension-coverage.sparql

SPARQL SELECT query that measures the percentage of YAWL classes properly aligned to public ontologies.

**What it reports**:
- `alignedCount`: YAWL classes with public superclass or complex definition
- `unalignedCount`: YAWL classes with no public alignment (proprietary roots)
- `totalCount`: Total YAWL class definitions
- `coverageRatio`: Decimal (0.0-1.0) of aligned / total
- `coveragePercentage`: Percentage (0-100%)

**Expected result**: `coveragePercentage = 100`

**Example output**:
```
alignedCount    unalignedCount  totalCount  coverageRatio  coveragePercentage
15              2               17          0.88           88
```

Interpretation: 15 of 17 YAWL classes are properly aligned (88% coverage). 2 proprietary roots remain.

### 4. extension-only.sh

Bash shell script that orchestrates all three SPARQL validations.

**Usage**:
```bash
bash ontology/validation/extension-only.sh <ttl-file>
bash ontology/validation/extension-only.sh -v ontology/process.ttl   # verbose
bash ontology/validation/extension-only.sh -f json ontology/process.ttl  # JSON output
cat ontology/process.ttl | bash ontology/validation/extension-only.sh -   # stdin
```

**Options**:
- `-h, --help`: Show help
- `-v, --verbose`: Enable debug output
- `-f, --format <fmt>`: Output format (text or json, default: text)

**Exit codes**:
- `0`: Validation passed (compliant)
- `1`: Tool not available (non-blocking, skips validation)
- `2`: Violations found (must fix)
- `3`: Error running validation

**Requirements**:
- Apache Jena `arq` command (preferred), OR
- Redland `sparql` command (fallback)

**Install Jena**:
```bash
# Download from: https://jena.apache.org/download/
# Extract and add to PATH
export PATH=/path/to/apache-jena-X.X.X/bin:$PATH
```

**Install Redland**:
```bash
# Ubuntu/Debian
sudo apt-get install raptor-utils librdf0-utils

# macOS
brew install raptor librdf
```

**Example output (text)**:
```
==========================================
Extension-Only Validation Report
==========================================
File: ontology/process.ttl
Time: 2026-02-23T14:32:15Z

1. Extension-Only Constraint (Instances)
   Checking: All YAWL instances have public ontology types
[OK] PASS: All instances properly extended (0 violations)

2. Proprietary Root Detection (Classes)
   Checking: All YAWL classes have public superclasses
[OK] PASS: All classes properly aligned (0 proprietary roots)

3. Extension Coverage Statistics
   Measuring: Fraction of YAWL classes with public alignments
alignedCount    unalignedCount  totalCount  coverageRatio  coveragePercentage
45              0               45          1.0            100

==========================================
[OK] VALIDATION PASSED
All constraints satisfied. Architecture is extension-only.
```

**Example output (JSON)**:
```json
{
  "validation": "extension-only-constraint",
  "file": "ontology/process.ttl",
  "timestamp": "2026-02-23T14:32:15Z",
  "results": {
    "extension-only-instances": {
      "violations": 0,
      "details": 0
    },
    "proprietary-roots": {
      "violations": 0,
      "details": 0
    },
    "coverage": {
      "alignedCount": 45,
      "unalignedCount": 0,
      "totalCount": 45,
      "coverageRatio": 1.0,
      "coveragePercentage": 100
    }
  },
  "summary": {
    "total_violations": 0,
    "status": "PASS"
  }
}
```

## SHACL Shapes

Two SHACL shapes are defined in `.specify/invariants.ttl`:

### ExtensionOnlyConstraintShape
Validates ABox instances against extension-only requirement. Used for runtime validation of workflow instances.

### ExtensionOnlyTBoxShape
Validates TBox class definitions. Ensures ontology design follows extension-only principle.

Both shapes are integrated into the YAWL validation pipeline (H gate: Guards phase).

## Integration Points

### Pre-Commit Validation
```bash
# In .claude/hooks/pre-commit.sh
bash ontology/validation/extension-only.sh ontology/process.ttl || exit 2
```

### CI/CD Pipeline
```bash
# Run after ontology changes
for ttl_file in ontology/*.ttl; do
  bash ontology/validation/extension-only.sh "$ttl_file" || {
    echo "FAIL: $ttl_file violates extension-only constraint"
    exit 2
  }
done
```

### Development Workflow
```bash
# While editing ontology
bash ontology/validation/extension-only.sh -v ontology/process.ttl

# After importing new ontology modules
bash ontology/validation/extension-only.sh ontology/profiles/manufacturing.ttl
```

## Examples

### Example 1: Compliant Workflow Instance
```turtle
@prefix : <http://example.org/orders/> .
@prefix yawl: <http://www.yawlfoundation.org/yawlschema#> .
@prefix schema: <https://schema.org/> .
@prefix prov: <http://www.w3.org/ns/prov#> .

# Instance declares YAWL type AND public ontology types
:OrderFulfillmentProcess a yawl:Specification ;
    a schema:Event ;          # Public type: Schema.org Event
    a prov:Activity ;         # Public type: PROV Activity
    schema:name "Order Fulfillment" ;
    prov:wasAssociatedWith :OrderSystem .
```

**Validation result**: PASS ✓

### Example 2: Non-Compliant Workflow Instance
```turtle
@prefix : <http://example.org/orders/> .
@prefix yawl: <http://www.yawlfoundation.org/yawlschema#> .

# Instance has ONLY YAWL type, no public alignment
:OrderFulfillmentProcess a yawl:Specification ;
    yawl:specificationID "order-fulfillment" .
    # Missing: a schema:Event ; or a prov:Activity ;
```

**Validation result**: FAIL (violation detected)

**Fix**: Add public ontology type:
```turtle
:OrderFulfillmentProcess a yawl:Specification ;
    a schema:Event ;          # Add this
    yawl:specificationID "order-fulfillment" .
```

### Example 3: Compliant Class Hierarchy
```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawlschema#> .
@prefix schema: <https://schema.org/> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

# YAWL Task class extends Schema.org Thing
yawl:Task a owl:Class ;
    rdfs:subClassOf schema:Thing ;
    rdfs:label "YAWL Task" .

# YAWL Workflow class extends multiple public types
yawl:Workflow a owl:Class ;
    owl:unionOf ( schema:Event prov:Activity ) ;
    rdfs:label "YAWL Workflow" .
```

**Validation result**: PASS ✓

### Example 4: Non-Compliant Class Hierarchy
```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawlschema#> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

# Proprietary root: no public superclass
yawl:CustomElement a owl:Class ;
    rdfs:label "Custom Element" .
    # Missing: rdfs:subClassOf schema:Thing ; or other public class
```

**Validation result**: FAIL (proprietary root detected)

**Fix**: Declare public superclass:
```turtle
yawl:CustomElement a owl:Class ;
    rdfs:subClassOf schema:Thing ;  # Add this
    rdfs:label "Custom Element" .
```

## Performance Notes

- **Small ontologies** (<100 classes): <1 second
- **Medium ontologies** (100-1000 classes): 1-5 seconds
- **Large ontologies** (1000+ classes): 5-30 seconds (depends on tool)

For large ontologies, consider:
1. Using Apache Jena (faster than Redland)
2. Running queries in batch
3. Filtering to specific namespaces with SPARQL FILTER

## Troubleshooting

### "Command not found: arq"
Install Apache Jena or Redland. The script will skip validation if no tool available (exit 0).

### "FAIL: Found N proprietary roots"
Check the violations with verbose output:
```bash
bash ontology/validation/extension-only.sh -v ontology/process.ttl
```

Review the SPARQL results and add `rdfs:subClassOf` declarations to fix.

### "Invalid TTL syntax"
Validate TTL file first:
```bash
arq --data ontology/process.ttl --query /dev/null
```

Fix any syntax errors in the TTL file.

### "Namespace binding not found"
Ensure prefixes are declared in your TTL file:
```turtle
@prefix yawl: <http://www.yawlfoundation.org/yawlschema#> .
@prefix schema: <https://schema.org/> .
@prefix prov: <http://www.w3.org/ns/prov#> .
```

## References

- **YAWL Ontology**: `.specify/yawl-ontology.ttl`
- **SHACL Shapes**: `.specify/yawl-shapes.ttl`
- **Invariants**: `.specify/invariants.ttl` (includes ExtensionOnlyConstraint)
- **SPARQL 1.1 Spec**: https://www.w3.org/TR/sparql11-query/
- **Schema.org**: https://schema.org/
- **PROV-O**: https://www.w3.org/TR/prov-o/
- **Dublin Core**: https://www.dublincore.org/

## Author Notes

The extension-only principle is foundational to YAWL's role as enterprise middleware. By requiring alignment to public ontologies, we ensure:

1. **Semantic Clarity**: Workflow semantics are grounded in standard models, not proprietary abstractions
2. **Interoperability**: Other systems can understand and reuse YAWL definitions
3. **Automated Reasoning**: Standard ontologies enable inference and consistency checking
4. **Long-term Maintainability**: Future systems can inherit and build upon YAWL specifications

This validation enforces that principle at both ABox (instance) and TBox (class) levels.
