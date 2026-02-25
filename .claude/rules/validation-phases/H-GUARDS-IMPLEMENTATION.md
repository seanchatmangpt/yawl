# H-Guards Phase Implementation — Step-by-Step Guide

**Status**: READY FOR DEVELOPMENT  
**Scope**: Complete implementation of guard validation system

---

## 1. Implementation Sequence

### Phase 1: Core Interfaces & Models (1h)

```java
// src/main/java/org/ggen/model/GuardViolation.java
public class GuardViolation {
    private String pattern;      // H_TODO, H_MOCK, etc.
    private String severity;     // FAIL, WARN
    private String file;
    private int line;
    private String content;      // Exact code that violates
    private String fixGuidance;

    public GuardViolation(String pattern, String severity,
                          int line, String content) {
        this.pattern = pattern;
        this.severity = severity;
        this.line = line;
        this.content = content;
        this.fixGuidance = getFixGuidanceFor(pattern);
    }

    private String getFixGuidanceFor(String pattern) {
        return switch(pattern) {
            case "H_TODO" -> "Implement real logic or throw UnsupportedOperationException";
            case "H_MOCK" -> "Delete mock or implement real service";
            case "H_STUB" -> "Implement real method or throw exception";
            case "H_EMPTY" -> "Implement real logic or throw exception";
            case "H_FALLBACK" -> "Propagate exception instead of faking data";
            case "H_LIE" -> "Update code to match documentation";
            case "H_SILENT" -> "Throw exception instead of logging";
            default -> "Fix guard violation";
        };
    }
}

// src/main/java/org/ggen/model/GuardReceipt.java
public class GuardReceipt {
    private String phase;
    private Instant timestamp;
    private int filesScanned;
    private List<GuardViolation> violations;
    private String status;
    private String errorMessage;
    private GuardSummary summary;

    // Getters, setters, builders
}
```

### Phase 2: AST Parser & RDF Converter (1.5h)

```java
// src/main/java/org/ggen/ast/JavaAstParser.java
public class JavaAstParser {
    private TreeSitterJava parser;

    public Tree parseFile(Path javaSource) throws IOException {
        String source = Files.readString(javaSource);
        return parser.parse(source);
    }

    public List<MethodInfo> extractMethods(Tree ast) {
        List<MethodInfo> methods = new ArrayList<>();
        ast.walk(node -> {
            if (node.getType().equals("method_declaration")) {
                methods.add(MethodInfo.from(node));
            }
        });
        return methods;
    }

    public List<CommentInfo> extractComments(Tree ast) {
        // Extract comment nodes from AST
        return /* ... */;
    }
}

// src/main/java/org/ggen/ast/RdfAstConverter.java
public class RdfAstConverter {
    public Model convertAstToRdf(List<MethodInfo> methods,
                                  List<CommentInfo> comments) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        for (MethodInfo m : methods) {
            Resource methodRes = model.createResource(
                CODE_NS + "Method_" + m.getId()
            );
            methodRes.addProperty(RDF.type, CODE.Method);
            methodRes.addProperty(CODE.name, m.getName());
            methodRes.addProperty(CODE.body, m.getBody());
            methodRes.addProperty(CODE.lineNumber,
                model.createTypedLiteral(m.getLineNumber()));

            // Link comments
            for (CommentInfo c : m.getComments()) {
                methodRes.addProperty(CODE.hasComment,
                    model.createResource(CODE_NS + "Comment_" + c.getId())
                );
            }
        }
        return model;
    }
}
```

### Phase 3: Guard Checkers (3.5h total)

#### RegexGuardChecker (H_TODO, H_MOCK, H_SILENT)

```java
// src/main/java/org/ggen/validation/RegexGuardChecker.java
public class RegexGuardChecker implements GuardChecker {
    private Pattern pattern;
    private String patternName;

    public RegexGuardChecker(String patternName, String regex) {
        this.patternName = patternName;
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(javaSource);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                violations.add(new GuardViolation(
                    patternName,
                    "FAIL",
                    i + 1,
                    line.trim()
                ));
            }
        }
        return violations;
    }

    @Override
    public String patternName() { return patternName; }

    @Override
    public Severity severity() { return Severity.FAIL; }
}
```

#### SparqlGuardChecker (H_STUB, H_EMPTY, H_FALLBACK, H_LIE)

```java
// src/main/java/org/ggen/validation/SparqlGuardChecker.java
public class SparqlGuardChecker implements GuardChecker {
    private String patternName;
    private String sparqlQuery;

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();
        
        // Parse file to AST
        JavaAstParser parser = new JavaAstParser();
        Tree ast = parser.parseFile(javaSource);
        
        // Convert to RDF
        RdfAstConverter converter = new RdfAstConverter();
        Model rdfModel = converter.convertAstToRdf(
            parser.extractMethods(ast),
            parser.extractComments(ast)
        );
        
        // Execute SPARQL query
        QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, rdfModel);
        ResultSet results = qexec.execSelect();
        
        // Convert results to violations
        while (results.hasNext()) {
            QuerySolution soln = results.next();
            // Extract violation details and create GuardViolation
        }
        
        return violations;
    }
}
```

### Phase 4: HyperStandardsValidator (1h)

```java
// src/main/java/org/ggen/validation/HyperStandardsValidator.java
public class HyperStandardsValidator {
    private List<GuardChecker> checkers;
    private GuardReceipt receipt;

    public HyperStandardsValidator() {
        checkers = List.of(
            new RegexGuardChecker("H_TODO", TODO_REGEX),
            new RegexGuardChecker("H_MOCK", MOCK_REGEX),
            new SparqlGuardChecker("H_STUB", sparqlQueryStub),
            new SparqlGuardChecker("H_EMPTY", sparqlQueryEmpty),
            new SparqlGuardChecker("H_FALLBACK", sparqlQueryFallback),
            new SparqlGuardChecker("H_LIE", sparqlQueryLie),
            new RegexGuardChecker("H_SILENT", SILENT_REGEX)
        );
    }

    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        receipt = new GuardReceipt();
        receipt.setPhase("guards");
        receipt.setTimestamp(Instant.now());

        List<Path> javaFiles = Files.walk(emitDir)
            .filter(p -> p.toString().endsWith(".java"))
            .toList();

        receipt.setFilesScanned(javaFiles.size());

        for (Path javaFile : javaFiles) {
            validateFile(javaFile);
        }

        receipt.setStatus(
            receipt.getViolations().isEmpty()
                ? "GREEN"
                : "RED"
        );

        return receipt;
    }

    private void validateFile(Path javaFile) throws IOException {
        for (GuardChecker checker : checkers) {
            List<GuardViolation> violations = checker.check(javaFile);
            for (GuardViolation v : violations) {
                v.setFile(javaFile.toString());
                receipt.addViolation(v);
            }
        }
    }
}
```

---

## 2. CLI Integration

### Command Line Interface

```bash
# Run guards phase standalone
ggen validate --phase guards --emit /home/user/yawl/generated

# Run with verbose output
ggen validate --phase guards --emit /home/user/yawl/generated --verbose

# Output to specific location
ggen validate --phase guards --emit /home/user/yawl/generated \
  --receipt-file /home/user/yawl/.claude/receipts/guard-receipt.json
```

### Exit Codes

| Exit | Meaning | Action |
|------|---------|--------|
| 0 | No violations | Proceed to Q phase |
| 1 | Transient error (IO, parse) | Retry |
| 2 | Violations found | Developer fixes + re-run |

---

## 3. Integration Pipeline

### Validation Sequence

```bash
ggen generate [Phase 1-5: code generation]
    ↓
bash scripts/dx.sh -pl yawl-generator  [Phase Λ: Compile green]
    ↓
ggen validate --phase guards           [Phase H: ENTRYPOINT]
    ├─ Load generated code from emit/
    ├─ Parse AST (tree-sitter)
    ├─ Convert AST → RDF
    ├─ Run 7 SPARQL queries
    ├─ Collect violations
    ├─ Emit guard-receipt.json
    └─ exit 0 (GREEN) or exit 2 (RED)
    ↓
if [ $? -eq 0 ]; then
    ggen validate --phase invariants   [Phase Q: Real impl ∨ throw]
else
    echo "Guard violations detected. Fix and re-run."
    exit 2
fi
```

### Configuration File

```toml
# config/guard-config.toml
[guards]
enabled = true
phase = "H"

patterns = [
  { name = "H_TODO", severity = "FAIL", description = "Deferred work markers" },
  { name = "H_MOCK", severity = "FAIL", description = "Mock implementations" },
  { name = "H_STUB", severity = "FAIL", description = "Empty/placeholder returns" },
  { name = "H_EMPTY", severity = "FAIL", description = "No-op method bodies" },
  { name = "H_FALLBACK", severity = "FAIL", description = "Silent catch-and-fake" },
  { name = "H_LIE", severity = "FAIL", description = "Code ≠ documentation" },
  { name = "H_SILENT", severity = "FAIL", description = "Log instead of throw" }
]

[regex_patterns]
h_todo = '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)'
h_mock = '(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]'
h_silent = 'log\.(warn|error)\([^)]*"[^"]*not\s+implemented'

[sparql_patterns]
h_stub_query = "guards-h-stub.sparql"
h_empty_query = "guards-h-empty.sparql"
h_fallback_query = "guards-h-fallback.sparql"
h_lie_query = "guards-h-lie.sparql"

[receipt]
enabled = true
format = "json"
path = ".claude/receipts/guard-receipt.json"
include_violations = true
include_summary = true
```

---

## 4. Test Fixtures (Comment Template)

```java
/*
// Test fixture templates (to be extracted to separate files):

// src/test/resources/fixtures/violation-h-todo.java
public class BadCode {
    // TODO: implement this method
    public void doWork() {
        this.status = "todo";
    }
}

// src/test/resources/fixtures/violation-h-mock.java
public class MockDataService implements DataService {
    public String fetchData() {
        return "mock data";
    }
}

// src/test/resources/fixtures/clean-code.java
public class GoodCode {
    public void doWork() {
        throw new UnsupportedOperationException(
            "doWork requires real implementation. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
}

// Test structure:
// src/test/java/org/ggen/validation/
// ├── HyperStandardsValidatorTest.java
// ├── RegexGuardCheckerTest.java
// └── SparqlGuardCheckerTest.java
*/
```

---

## 5. Error Recovery Workflow

### Violation Detection → Developer Fix

```
Teammate (Engineer A):
  ↓
  ggen validate --phase guards --emit generated/
  ↓ RED: 3 violations detected
  ↓
  Read guard-receipt.json for detailed violations
  ↓
  Fix violations in generated code:
    • Remove TODO comments
    • Delete mock classes
    • Implement real methods or throw UnsupportedOperationException
  ↓
  Re-run: ggen validate --phase guards --emit generated/
  ↓ GREEN: No violations
  ↓
  Proceed to Q phase
```

### Message to Teammates

```
Engineer A: "guard-receipt.json shows 3 violations in your generated code.

Violation 1 (H_TODO):
  File: YWorkItem.java:427
  Content: // TODO: Add deadlock detection
  Fix: Implement real deadlock logic or throw UnsupportedOperationException

Violation 2 (H_MOCK):
  File: MockDataService.java:12
  Content: class MockDataService implements DataService
  Fix: Delete this mock class or implement real service

Run: ggen validate --phase guards --emit generated/ after fixes"
```

---

## 6. Timeline & Milestones

| Task | Owner | Est. Time | Deadline |
|------|-------|-----------|----------|
| Interface + models | Engineer A | 1h | Day 1 |
| AST parser + RDF converter | Engineer B | 1.5h | Day 1 |
| Regex checkers (3 patterns) | Engineer A | 1.5h | Day 1 |
| SPARQL checkers (4 patterns) | Engineer B | 2h | Day 2 |
| HyperStandardsValidator | Engineer A | 1h | Day 2 |
| Test fixtures + unit tests | Tester C | 2h | Day 3 |
| Integration test + e2e | Tester C | 1.5h | Day 3 |
| Documentation + examples | Engineer A | 1h | Day 3 |

**Total**: ~10.5 hours (3-day sprint with 3 engineers)

---

**Next**: See `H-GUARDS-QUERIES.md` for SPARQL query reference
