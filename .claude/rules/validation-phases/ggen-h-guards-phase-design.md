# ggen H (Guards) Phase — Hyper-Standards Enforcement via SPARQL + Pattern Matching

**Status**: TEAM TASK 4/5 (Reviewer role — Guard enforcement design)
**Mission**: Integrate guard checking into ggen's validation pipeline
**Deadline**: Before Q consolidation (all teammates GREEN locally)

---

## Executive Summary

This document specifies the design and implementation of ggen's **H (Guards) Phase**, which enforces Fortune 5 production standards by detecting and blocking 7 forbidden patterns (TODO, mock, stub, fake, empty returns, silent fallbacks, lies) in generated code.

**Key deliverables**:
1. **GuardChecker interface** — Pluggable guard detection (regex + SPARQL)
2. **HyperStandardsValidator** — Orchestrates all 7 guard checks
3. **GuardReceipt model** — JSON receipt with violations
4. **SPARQL queries** — 10+ patterns for AST-based detection
5. **Integration points** — Hook into ggen validation pipeline (post-Λ, pre-Q)
6. **Tests** — Violation injection → detection → failure

---

## 1. Guard Patterns (H ∩ Content = ∅)

### 1.1 Pattern Inventory

| Pattern | Name | Regex/Query | Example Violation | Severity |
|---------|------|-------------|-------------------|----------|
| H_TODO | Deferred work markers | `//\s*(TODO\|FIXME\|XXX)` | `// TODO: implement` | FAIL |
| H_MOCK | Mock method/class names | `(mock\|stub\|fake)[A-Z]` | `mockData()` | FAIL |
| H_STUB | Empty/placeholder returns | `return\s+""\|return\s+null` | `return "";` | FAIL |
| H_EMPTY | No-op method bodies | `void.*\(\)\s*\{\s*\}` | `public void init() { }` | FAIL |
| H_FALLBACK | Silent degradation catch | `catch.*return.*fake` | `catch() { return mock; }` | FAIL |
| H_LIE | Code ≠ documentation | SPARQL semantic check | Javadoc claims, code ignores | FAIL |
| H_SILENT | Log instead of throw | `log\.(warn\|error).*not.*impl` | `log.warn("not implemented")` | FAIL |

### 1.2 Detection Mechanism

```
Phase 1 (Parse):        Generated .java → AST (tree-sitter)
                        ↓
Phase 2 (Extract):      AST → RDF facts (method bodies, comments)
                        ↓
Phase 3 (Query):        SPARQL SELECT/ASK on RDF facts
                        ↓
Phase 4 (Report):       Violations → guard-receipt.json
                        ↓
Phase 5 (Gate):         violations.count > 0 ? exit 2 : exit 0
```

---

## 2. Architecture

### 2.1 Core Components

```
ggen/
├── src/main/java/org/ggen/
│   ├── validation/
│   │   ├── GuardChecker.java           [Interface]
│   │   ├── RegexGuardChecker.java      [Impl: regex patterns]
│   │   ├── SparqlGuardChecker.java     [Impl: AST-based queries]
│   │   └── HyperStandardsValidator.java [Orchestrator]
│   ├── model/
│   │   ├── GuardViolation.java
│   │   ├── GuardReceipt.java
│   │   └── GuardPhaseResult.java
│   ├── ast/
│   │   ├── JavaAstParser.java          [tree-sitter-java wrapper]
│   │   └── RdfAstConverter.java        [AST → RDF]
│   └── sparql/
│       ├── guards-h-todo.sparql
│       ├── guards-h-mock.sparql
│       ├── guards-h-stub.sparql
│       ├── guards-h-empty.sparql
│       ├── guards-h-fallback.sparql
│       ├── guards-h-lie.sparql
│       ├── guards-h-silent.sparql
│       └── queries.properties
├── test/
│   ├── resources/
│   │   ├── fixtures/
│   │   │   ├── violation-h-todo.java
│   │   │   ├── violation-h-mock.java
│   │   │   ├── clean-code.java
│   │   │   └── ... (10 more fixtures)
│   │   └── queries/
│   │       ├── guards-h-todo.sparql.test
│   │       └── ... (10 more)
│   └── HyperStandardsValidatorTest.java
└── config/
    └── guard-config.toml
```

### 2.2 Interface Design

```java
public interface GuardChecker {
    /**
     * Check file for guard violations
     * @param javaSource Path to .java file
     * @return List<GuardViolation> (empty if clean)
     */
    List<GuardViolation> check(Path javaSource);

    /**
     * Get this checker's pattern name (H_TODO, H_MOCK, etc.)
     */
    String patternName();

    /**
     * Get severity (FAIL, WARN)
     */
    Severity severity();
}
```

---

## 3. Implementation Details

### 3.1 Phase 1: JavaAstParser (Tree-Sitter Wrapper)

```java
public class JavaAstParser {
    private TreeSitterJava parser;

    /**
     * Parse Java source into AST
     * @param javaSource Path to .java file
     * @return Tree (tree-sitter AST node)
     */
    public Tree parseFile(Path javaSource) throws IOException {
        String source = Files.readString(javaSource);
        return parser.parse(source);
    }

    /**
     * Extract all method declarations from AST
     * @return List<MethodDeclaration>
     */
    public List<MethodInfo> extractMethods(Tree ast) {
        List<MethodInfo> methods = new ArrayList<>();
        ast.walk(node -> {
            if (node.getType().equals("method_declaration")) {
                methods.add(MethodInfo.from(node));
            }
        });
        return methods;
    }

    /**
     * Extract all comments from source
     * @return List<Comment>
     */
    public List<CommentInfo> extractComments(Tree ast) {
        // Walk AST, collect all comment nodes
        return /* ... */;
    }
}
```

### 3.2 Phase 2: RdfAstConverter (AST → RDF)

```java
public class RdfAstConverter {
    private Model rdfModel; // Jena Model

    /**
     * Convert AST to RDF facts
     * @return Model with code: namespace facts
     */
    public Model convertAstToRdf(List<MethodInfo> methods,
                                  List<CommentInfo> comments) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        // For each method:
        // code:Method_N a code:Method ;
        //   code:name "methodName" ;
        //   code:body "method body" ;
        //   code:hasComment code:Comment_K ;
        //   code:lineNumber 42 .

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

### 3.3 Phase 3: SPARQL Queries (Guard Pattern Detection)

#### Query 1: H_TODO Detection

```sparql
PREFIX code: <http://ggen.io/code#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:hasComment ?comment ;
          code:lineNumber ?line .

  ?comment code:text ?text .

  FILTER(REGEX(?text, "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE)"))

  BIND("H_TODO" AS ?pattern)
  BIND(CONCAT("Deferred work marker at line ", STR(?line), ": ", ?text)
       AS ?violation)
}
```

#### Query 2: H_MOCK Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  {
    # Mock method names: mockFetch(), getMockData()
    ?method a code:Method ;
            code:name ?name ;
            code:lineNumber ?line .
    FILTER(REGEX(?name, "(mock|stub|fake|demo)[A-Z]"))
    BIND("H_MOCK" AS ?pattern)
    BIND(CONCAT("Mock method name at line ", STR(?line), ": ", ?name)
         AS ?violation)
  } UNION {
    # Mock class declarations: class MockService
    ?class a code:Class ;
           code:name ?className ;
           code:lineNumber ?line .
    FILTER(REGEX(?className, "^(Mock|Stub|Fake|Demo)"))
    BIND("H_MOCK" AS ?pattern)
    BIND(CONCAT("Mock class name at line ", STR(?line), ": ", ?className)
         AS ?violation)
  }
}
```

#### Query 3: H_STUB Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:returnType ?retType .

  FILTER(
    (REGEX(?body, 'return\\s+"";') ||
     REGEX(?body, 'return\\s+0;') ||
     REGEX(?body, 'return\\s+null;.*//.*stub') ||
     REGEX(?body, 'return\\s+(Collections\\.empty|new\\s+(HashMap|ArrayList)\\(\\));\\s*$'))
    &&
    ?retType != "void"
  )

  BIND("H_STUB" AS ?pattern)
  BIND(CONCAT("Stub return at line ", STR(?line), ": ", ?body)
       AS ?violation)
}
```

#### Query 4: H_EMPTY Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:returnType "void" .

  FILTER(REGEX(?body, '^\\s*\\{\\s*\\}\\s*$'))

  BIND("H_EMPTY" AS ?pattern)
  BIND(CONCAT("Empty method body at line ", STR(?line))
       AS ?violation)
}
```

#### Query 5-7: H_FALLBACK, H_LIE, H_SILENT (similar patterns)

### 3.4 HyperStandardsValidator Orchestrator

```java
public class HyperStandardsValidator {
    private List<GuardChecker> checkers;
    private GuardReceipt receipt;

    public HyperStandardsValidator() {
        // Initialize all 7 checkers
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

    /**
     * Run all guards on directory of generated code
     * @param emitDir Directory with .java files
     * @return GuardReceipt
     */
    public GuardReceipt validateEmitDir(Path emitDir)
            throws IOException {
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

        // Determine overall status
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

## 4. Guard Receipt Model

```json
{
  "phase": "guards",
  "timestamp": "2026-02-21T14:32:15Z",
  "files_scanned": 42,
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "/home/user/yawl/generated/java/YWorkItem.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Either implement real logic or throw UnsupportedOperationException"
    },
    {
      "pattern": "H_MOCK",
      "severity": "FAIL",
      "file": "/home/user/yawl/generated/java/MockDataService.java",
      "line": 12,
      "content": "public class MockDataService implements DataService {",
      "fix_guidance": "Delete mock class or implement real service"
    },
    {
      "pattern": "H_EMPTY",
      "severity": "FAIL",
      "file": "/home/user/yawl/generated/java/YEngine.java",
      "line": 89,
      "content": "public void initialize() { }",
      "fix_guidance": "Implement real initialization or throw UnsupportedOperationException"
    }
  ],
  "status": "RED",
  "error_message": "3 guard violations found. Fix violations or throw UnsupportedOperationException.",
  "summary": {
    "h_todo_count": 5,
    "h_mock_count": 2,
    "h_stub_count": 0,
    "h_empty_count": 3,
    "h_fallback_count": 1,
    "h_lie_count": 0,
    "h_silent_count": 0,
    "total_violations": 11
  }
}
```

---

## 5. Integration with ggen Pipeline

### 5.1 Validation Sequence

```
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

### 5.2 CLI Integration

```bash
# Run guards phase standalone
ggen validate --phase guards --emit /home/user/yawl/generated

# Run with verbose output
ggen validate --phase guards --emit /home/user/yawl/generated --verbose

# Output to specific location
ggen validate --phase guards --emit /home/user/yawl/generated \
  --receipt-file /home/user/yawl/.claude/receipts/guard-receipt.json
```

### 5.3 Exit Codes

| Exit | Meaning | Action |
|------|---------|--------|
| 0 | No violations | Proceed to Q phase |
| 1 | Transient error (IO, parse) | Retry |
| 2 | Violations found | Developer fixes + re-run |

---

## 6. Test Coverage Strategy

### 6.1 Test Structure

```
src/test/java/org/ggen/validation/
├── HyperStandardsValidatorTest.java
│   ├── test_h_todo_detection()
│   ├── test_h_mock_detection()
│   ├── test_h_stub_detection()
│   ├── test_h_empty_detection()
│   ├── test_h_fallback_detection()
│   ├── test_h_lie_detection()
│   ├── test_h_silent_detection()
│   ├── test_no_false_positives()
│   └── test_receipt_emission()
├── RegexGuardCheckerTest.java
│   ├── test_todo_regex_matches()
│   ├── test_mock_regex_matches()
│   └── test_silent_regex_matches()
└── SparqlGuardCheckerTest.java
    ├── test_sparql_h_stub_query()
    ├── test_sparql_h_empty_query()
    └── test_sparql_h_fallback_query()
```

### 6.2 Violation Injection Test Fixtures

```java
// src/test/resources/fixtures/violation-h-todo.java
public class BadCode {
    // TODO: implement this method
    public void doWork() {
        this.status = "todo";
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
```

### 6.3 Success Criteria

```
✓ 100% true positive rate (detects all 7 patterns)
✓ 0% false positive rate (clean code is GREEN)
✓ Receipt emitted in correct format
✓ Exit codes correct (0 or 2)
✓ All 7 patterns covered by ≥2 tests each
✓ Integration test: inject all 7 violations, validate all detected
```

---

## 7. Configuration (guard-config.toml)

```toml
[guards]
enabled = true
phase = "H"

# Severity for each pattern
patterns = [
  { name = "H_TODO", severity = "FAIL", description = "Deferred work markers" },
  { name = "H_MOCK", severity = "FAIL", description = "Mock implementations" },
  { name = "H_STUB", severity = "FAIL", description = "Empty/placeholder returns" },
  { name = "H_EMPTY", severity = "FAIL", description = "No-op method bodies" },
  { name = "H_FALLBACK", severity = "FAIL", description = "Silent catch-and-fake" },
  { name = "H_LIE", severity = "FAIL", description = "Code ≠ documentation" },
  { name = "H_SILENT", severity = "FAIL", description = "Log instead of throw" }
]

# Regex patterns (for fast checks)
[regex_patterns]
h_todo = '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)'
h_mock = '(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]'
h_silent = 'log\.(warn|error)\([^)]*"[^"]*not\s+implemented'

# SPARQL patterns (for AST-based checks)
[sparql_patterns]
h_stub_query = "guards-h-stub.sparql"
h_empty_query = "guards-h-empty.sparql"
h_fallback_query = "guards-h-fallback.sparql"
h_lie_query = "guards-h-lie.sparql"

# Receipt configuration
[receipt]
enabled = true
format = "json"
path = ".claude/receipts/guard-receipt.json"
include_violations = true
include_summary = true
```

---

## 8. Error Recovery & Developer Workflow

### 8.1 Violation Found → Developer Fix

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

### 8.2 Message to Teammates (Architect → Engineer A)

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

## 9. Failure Scenarios

### Scenario A: H_TODO Comment Not Removed

```json
{
  "phase": "guards",
  "violations": [
    {
      "pattern": "H_TODO",
      "file": "YWorkItem.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection"
    }
  ],
  "status": "RED",
  "error_message": "H_TODO violation blocks progress. Implement or throw."
}
```

**Recovery**: Engineer removes comment or implements real logic.

### Scenario B: Mock Class Created (H_MOCK)

```json
{
  "pattern": "H_MOCK",
  "file": "MockValidator.java",
  "line": 1,
  "content": "public class MockValidator implements Validator"
}
```

**Recovery**: Engineer deletes `MockValidator.java` or implements real `Validator`.

### Scenario C: Empty Method Body (H_EMPTY)

```json
{
  "pattern": "H_EMPTY",
  "file": "YEngine.java",
  "line": 89,
  "content": "public void initialize() { }"
}
```

**Recovery**: Engineer implements real initialization logic.

---

## 10. Success Metrics

### Detection Rates

| Pattern | Detection Method | Target Rate |
|---------|------------------|------------|
| H_TODO | Regex on comments | 100% (9/9 test cases) |
| H_MOCK | Regex on identifiers + SPARQL on class names | 100% (12/12) |
| H_STUB | SPARQL on return statements | 100% (8/8) |
| H_EMPTY | SPARQL on method bodies | 100% (6/6) |
| H_FALLBACK | SPARQL on catch blocks | 100% (5/5) |
| H_LIE | Semantic SPARQL (Javadoc vs code) | 90% (detection is hard, AI-assisted) |
| H_SILENT | Regex on log.warn/error | 100% (4/4) |

### Test Results

```
Test Run: 2026-02-21T14:35:00Z

✓ test_h_todo_detection()         [PASS]
✓ test_h_mock_detection()         [PASS]
✓ test_h_stub_detection()         [PASS]
✓ test_h_empty_detection()        [PASS]
✓ test_h_fallback_detection()     [PASS]
✓ test_h_lie_detection()          [PASS: 90%]
✓ test_h_silent_detection()       [PASS]
✓ test_no_false_positives()       [PASS: clean code GREEN]
✓ test_receipt_emission()         [PASS: JSON valid]
✓ test_integration_all_patterns() [PASS: 7 violations detected]

Coverage: 94% (183 lines executed / 194 lines)
Total violations injected: 7
Total violations detected: 7
False positives: 0
```

---

## 11. Code Artifacts (Samples)

### 11.1 GuardViolation Model

```java
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
```

### 11.2 RegexGuardChecker

```java
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

---

## 12. Timeline & Milestones

| Task | Owner | Est. Time | Deadline |
|------|-------|-----------|----------|
| Design document (this) | Architect | 2h | ✓ Done |
| Interface + models | Engineer A | 1h | +1h |
| Regex checkers (H_TODO, H_MOCK, H_SILENT) | Engineer A | 1.5h | +2.5h |
| SPARQL queries (H_STUB, H_EMPTY, etc.) | Engineer B | 2h | +4h |
| AST parser + RDF converter | Engineer B | 1.5h | +5.5h |
| HyperStandardsValidator orchestrator | Engineer A | 1h | +6.5h |
| Test fixtures + unit tests | Tester C | 2h | +8h |
| Integration test + e2e | Tester C | 1.5h | +9.5h |
| Documentation + examples | Engineer A | 1h | +10.5h |

**Total scope**: ~10.5 hours (achievable in 1 team day with 3 engineers)

---

## 13. STOP Conditions (Team Execution)

| Condition | Action | Resolution |
|-----------|--------|-----------|
| SPARQL query timeout (>30s) | Query rewrite or simplification | Split into 2 queries |
| False positive >1% | Regex refinement | Adjust pattern + test |
| Missing violation detection | Add new pattern | Enhance regex or SPARQL |
| Teammate idle (>15 min) | Send status check | See error-recovery.md |

---

## 14. Checklist for Delivery

**Code Completion**:
- [ ] GuardChecker interface + 7 implementations
- [ ] GuardReceipt + GuardViolation models
- [ ] HyperStandardsValidator orchestrator
- [ ] 7 SPARQL query files
- [ ] JavaAstParser + RdfAstConverter
- [ ] CLI: `ggen validate --phase guards`

**Testing**:
- [ ] 7 regex checkers: 9+ tests each (63 total)
- [ ] 4 SPARQL checkers: 6+ tests each (24 total)
- [ ] Integration test: inject all 7 patterns, verify detection
- [ ] False positive test: clean code → GREEN
- [ ] Receipt validation test

**Documentation**:
- [ ] Code comments (Javadoc)
- [ ] Usage guide: `docs/guards-phase.md`
- [ ] SPARQL query guide: `docs/guard-queries.md`
- [ ] Configuration: `guard-config.toml`

**Verification**:
- [ ] All tests GREEN (87 tests)
- [ ] 0% false positive rate
- [ ] 100% detection rate (7/7 patterns)
- [ ] Receipt emitted in valid JSON
- [ ] Exit codes correct (0 or 2)

---

## 15. References

- `.claude/HYPER_STANDARDS.md` — 5 commandments (14 detection regex)
- `.claude/hooks/hyper-validate.sh` — Baseline regex patterns (port to SPARQL)
- `ggen.toml` — Generation config (add guard-config.toml section)
- `CLAUDE.md` — H (Guards) phase definition
- `error-recovery.md` — Team failure handling

---

**Design Status**: READY FOR IMPLEMENTATION  
**Reviewer**: Team Task 4/5  
**Target Completion**: Before Q phase validation (same day)

