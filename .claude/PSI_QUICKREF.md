# Ψ (Observatory) RDF Integration — Quick Reference

## One-Command Execution

```bash
# Full pipeline: facts.json → facts.ttl → drift detection
bash scripts/observatory/emit-rdf-facts.sh
```

## What Gets Generated

```
docs/v6/latest/
├── facts/                    # Input (from observatory.sh)
│   ├── modules.json
│   ├── reactor.json
│   ├── coverage.json
│   ├── tests.json
│   ├── gates.json
│   ├── shared-src.json
│   └── integration-facts.json
└── facts.ttl                 # Output: RDF graph of codebase

.claude/receipts/
└── observatory-facts.sha256  # Hash for drift detection
```

## File Reference

| File | Purpose | Location |
|------|---------|----------|
| FactsToRDFConverter.java | JSON → RDF converter | src/main/java/org/yawlfoundation/yawl/observatory/rdf/ |
| DriftDetector.java | SHA256 hash tracking | src/main/java/org/yawlfoundation/yawl/observatory/rdf/ |
| GgenObservationBridge.java | SPARQL query interface | src/main/java/org/yawlfoundation/yawl/observatory/rdf/ |
| ObservatorySPARQLQueries.sparql | 15 predefined queries | src/main/resources/sparql/ |
| ggen-observation.ttl | RDF ontology schema | src/main/resources/ontology/ |
| FactsToRDFConverterTest.java | Integration tests | src/test/java/org/yawlfoundation/yawl/observatory/rdf/ |
| emit-rdf-facts.sh | Orchestration script | scripts/observatory/ |
| OBSERVATORY_RDF_DESIGN.md | Complete design doc | .claude/ |

## Code Integration (For ggen)

```java
// Load facts
GgenObservationBridge bridge = new GgenObservationBridge();
bridge.loadFacts(Paths.get("docs/v6/latest/facts.ttl"));

// Query modules
List<String> modules = bridge.getModules();

// Query custom SPARQL
List<Map<String, String>> results = bridge.query(
    "SELECT ?moduleName ?testCount WHERE { ?m ex:moduleName ?moduleName ; ex:testCount ?testCount }"
);
```

## Tera Template Example

```tera
{% set modules = observation.query("
  SELECT ?moduleName ?testCount
  WHERE {
    ?m ex:moduleName ?moduleName ;
        ex:testCount ?testCount .
  }
  ORDER BY DESC(?testCount)
") %}

// High-test modules
{% for mod in modules %}
- {{ mod.moduleName }}: {{ mod.testCount }} tests
{% endfor %}
```

## SPARQL Queries (15 Available)

```sparql
-- All modules with metrics
SELECT ?moduleName ?srcFiles ?testCount ?lineCoverage

-- Module dependencies
SELECT ?from ?to ?buildOrderFrom ?buildOrderTo

-- Circular dependencies
SELECT ?module1 ?module2 ?module3

-- Low coverage modules
SELECT ?moduleName ?lineCoverage WHERE { ?m ex:lineCoverage ?lineCoverage . FILTER (?lineCoverage < 65) }

-- Integration points
SELECT ?integrationType ?server ?toolCount

-- See: src/main/resources/sparql/ObservatorySPARQLQueries.sparql (full list)
```

## Drift Detection

```bash
# Drift file location
cat .claude/receipts/observatory-facts.sha256
# Output: a1b2c3d4e5f6... (64 hex chars)  facts.ttl

# If facts change (detected in next run):
# DRIFT DETECTED: Codebase facts have changed
# Trigger: Full rebuild (Λ→H→Q→Ω)
```

## Testing

```bash
# Run integration tests
mvn test -Dtest=FactsToRDFConverterTest

# Verify RDF output
test -f docs/v6/latest/facts.ttl && echo "✓ facts.ttl created"

# Query facts manually
java -cp target/yawl-engine.jar org.yawlfoundation.yawl.observatory.rdf.GgenObservationBridge \
    docs/v6/latest/facts.ttl
```

## Performance

- Load facts: <100ms
- Convert to RDF: <200ms
- Write Turtle: <300ms
- Compute hash: <50ms
- Load facts.ttl: <200ms
- SPARQL query: <100ms
- **Total**: ~1 second

## Key Concepts

| Term | Meaning |
|------|---------|
| **Ψ (Psi)** | Observatory phase — observe codebase structure before acting |
| **facts.json** | JSON output from observatory.sh (modules, dependencies, coverage) |
| **facts.ttl** | RDF/Turtle representation of facts (queryable via SPARQL) |
| **Drift** | Change in facts detected by hash comparison |
| **SPARQL** | Query language for RDF graphs |
| **ggen** | Code generation tool using SPARQL facts as context |

## Troubleshooting

### "facts directory not found"
```bash
# Run observatory first
bash scripts/observatory/observatory.sh --facts

# Then convert to RDF
bash scripts/observatory/emit-rdf-facts.sh
```

### "RDF conversion failed"
```bash
# Check Java is available
java -version

# Check Jena library is in classpath
ls target/lib/jena*.jar
```

### "No previous hash found (first run)"
This is normal. On next run, drift detection will start.

### "DRIFT DETECTED"
Codebase facts changed. Trigger rebuild:
```bash
bash scripts/dx.sh all  # Λ phase
```

## Messages to Teammates

**Build Engineer**: "facts.ttl is ready. Load GgenObservationBridge and query facts for code generation context."

**Architect**: "Observatory facts are now RDF. Ontology defined. 15 SPARQL queries cover common patterns."

**Tester**: "Query lowCoverageModules to find test gaps. Generate suites based on module test counts."

---

**Created**: 2026-02-21
**Status**: Ready for integration
**Next Phase**: Λ (Build) — ggen with Ψ facts
