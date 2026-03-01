# H-Guards Implementation Quick Start

**Reference**: Full architecture in `H-GUARDS-ARCHITECTURE.md`
**Status**: Ready for Phase 1
**Timeline**: 3 days (Days 1-3) with 3 engineers

---

## Quick Facts

| Item | Value |
|------|-------|
| **Module** | yawl-ggen (existing) |
| **Package** | org.yawlfoundation.yawl.ggen.validation.guards |
| **Patterns** | 7 (H_TODO, H_MOCK, H_STUB, H_EMPTY, H_FALLBACK, H_LIE, H_SILENT) |
| **Detection** | Regex (3 patterns) + SPARQL (4 patterns) |
| **Receipt Path** | .claude/receipts/guard-receipt.json |
| **Exit Codes** | 0 (GREEN), 1 (transient), 2 (RED) |
| **Performance SLA** | <5 seconds per file |
| **Accuracy Target** | 100% TP, 0% FP |

---

## Phase-by-Phase Checklist

### Phase 1: Interfaces & Models (1h, Engineer A)

```
[ ] GuardChecker interface
    - check(Path) → List<GuardViolation>
    - patternName() → String
    - severity() → GuardSeverity
    - validateConfig(GuardPatternConfig) → boolean

[ ] GuardViolation record
    - file, line, pattern, severity, content, fixGuidance
    - Validation: line >= 1, pattern in {H_*}, severity in {FAIL, WARN}

[ ] GuardReceipt record
    - phase, status, timestamp, filesScanned, violations, summary, errorMessage, durationMs
    - Validation: status in {GREEN, RED}, status=RED ⟺ !violations.isEmpty()

[ ] GuardSummary record
    - Per-pattern counts: h_todo_count, h_mock_count, etc.
    - getTotalViolations() method
    - static from(List<GuardViolation>)

[ ] Exception types
    - GuardCheckException (fatal)
    - GuardConfigException (config error)
    - GuardSeverity enum {FAIL, WARN}

[ ] Tests
    - Record construction validation
    - Invalid data rejection
    - Immutability verification
```

### Phase 2: AST & RDF (1.5h, Engineer B)

```
[ ] JavaAstParser (tree-sitter wrapper)
    - parseFile(Path) → Tree
    - extractMethods(Tree) → List<MethodInfo>
    - extractComments(Tree) → List<CommentInfo>

[ ] MethodInfo record
    - id, name, className, lineNumber, returnType, annotations, body, javadoc, comments

[ ] CommentInfo record
    - id, lineNumber, text, type (SINGLE_LINE or BLOCK)

[ ] AstExtractor (AST walker)
    - Walk tree nodes, extract features

[ ] GuardRdfConverter
    - convertAstToRdf(List<MethodInfo>, List<CommentInfo>) → Model
    - Create RDF resources for methods, comments, javadoc

[ ] GuardRdfNamespaces
    - GUARD_NS = "http://ggen.io/guards#"
    - Property constants: HAS_METHOD, METHOD_NAME, METHOD_BODY, etc.
    - Type constants: FILE, METHOD, COMMENT, JAVADOC

[ ] Tests
    - Parse valid .java file
    - Extract all methods
    - Extract all comments
    - Verify RDF model structure
```

### Phase 3a: Regex Checkers (1.5h, Engineer A)

```
[ ] RegexGuardChecker (generic regex implementation)
    - Compile pattern at init
    - Execute against lines
    - Map matches to GuardViolation

[ ] H_TODO implementation
    - Pattern: //\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)
    - Positive tests: // TODO, // FIXME, @incomplete
    - Negative tests: comment with pattern in string

[ ] H_MOCK implementation
    - Pattern: (mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]
    - Positive tests: MockService, mockFetch(), StubData
    - Negative tests: MyMockery (suffix), isOkayMocking

[ ] H_SILENT implementation
    - Pattern: log\.(warn|error)\([^)]*"[^"]*not\s+implemented
    - Positive tests: log.error("not implemented yet")
    - Negative tests: log.error("error occurred")

[ ] Tests
    - All 3 patterns: positive, negative, edge cases
    - Property-based testing for regex completeness
```

### Phase 3b: SPARQL Checkers (2h, Engineer B)

```
[ ] SparqlGuardChecker (generic SPARQL implementation)
    - Load .sparql file
    - Execute with 30s timeout
    - Map SPARQL results to GuardViolation

[ ] SparqlQueryLoader
    - Load from resources/sparql/guards/*.sparql
    - Validate query syntax

[ ] SparqlExecutor
    - Execute query on Model with timeout
    - Handle timeout gracefully
    - Return ResultSet

[ ] SparqlResultMapper
    - Parse SPARQL SELECT results
    - Extract violation, line, pattern columns
    - Create GuardViolation objects

[ ] SPARQL Queries (in resources)
    - guards-h-stub.sparql: return ""; return 0; return null; (non-void methods)
    - guards-h-empty.sparql: empty void method bodies { }
    - guards-h-fallback.sparql: catch() { return fake; }
    - guards-h-lie.sparql: @return never null but returns null

[ ] Tests
    - Query loads and parses
    - Query executes on sample RDF
    - Results map correctly to violations
    - Timeout enforcement works
```

### Phase 4: Orchestrator & CLI (1h, Engineer A)

```
[ ] GuardCheckerRegistry (factory)
    - Instantiate all 7 checkers
    - Load configuration
    - Return List<GuardChecker>

[ ] HyperStandardsValidator (orchestrator)
    - validateEmitDir(Path emitDir, Path receiptPath) → GuardReceipt
    - Discover .java files
    - Run all checkers
    - Aggregate violations
    - Compute summary
    - Build receipt

[ ] GuardPhase (CLI entry point)
    - Parse args: --phase guards --emit <dir> --receipt-file <path>
    - Call HyperStandardsValidator
    - Return exit code (0, 1, or 2)
    - Write receipt.json

[ ] GuardConfig (TOML loader)
    - Load guard-config.toml
    - Parse pattern registry
    - Return GuardPatternConfig per pattern

[ ] Tests
    - Registry instantiates 7 checkers
    - Orchestrator finds all .java files
    - Runs all checkers
    - Aggregates correctly
    - Computes summary
    - Returns correct exit code
```

### Phase 5: Receipt & Output (0.75h, Engineer A)

```
[ ] GuardReceiptWriter
    - Serialize GuardReceipt → JSON
    - Write to .claude/receipts/guard-receipt.json
    - Pretty print (configurable)

[ ] GuardReceiptValidator
    - Validate against JSON schema
    - Check invariants (status RED ⟺ violations)
    - Verify all required fields

[ ] guard-receipt-schema.json
    - JSON Schema for receipt
    - Define violations array structure
    - Define summary object structure

[ ] Tests
    - Receipt serializes to valid JSON
    - Can parse back (round-trip)
    - Timestamps are UTC ISO-8601
    - Summary matches violations
```

### Phase 6: Configuration (0.5h, Engineer B)

```
[ ] guard-config.toml
    - Pattern registry (all 7 patterns enabled by default)
    - Regex patterns (H_TODO, H_MOCK, H_SILENT)
    - SPARQL query file paths
    - Performance settings (timeout, batch size)
    - Receipt output config

[ ] Tests
    - Config loads successfully
    - All patterns configured
    - Query files can be located
```

### Phase 7: Test Fixtures (2h, Tester C)

```
[ ] violation-h-todo.java
    - Contains // TODO, // FIXME, @incomplete comments
    - Multiple violations

[ ] violation-h-mock.java
    - MockService, mockFetch(), StubData names

[ ] violation-h-stub.java
    - return "";, return 0;, return null; in non-void methods

[ ] violation-h-empty.java
    - public void initialize() { } (empty void methods)

[ ] violation-h-fallback.java
    - catch (Exception e) { return Collections.emptyList(); }

[ ] violation-h-lie.java
    - @return never null but returns null
    - @throws IOException but no throw statement

[ ] violation-h-silent.java
    - log.error("not implemented yet")

[ ] clean-code.java
    - Implements everything properly
    - No violations
    - Should pass all checks

[ ] edge-cases.java
    - Boundary conditions
    - Comment in strings
    - Suffix patterns (MyMockery)
    - Legitimate uses of words (mockito testing)
```

### Phase 8: Unit Tests (2h, Tester C)

```
[ ] RegexGuardCheckerTest
    - Test H_TODO: positive, negative, edge cases
    - Test H_MOCK: positive, negative, edge cases
    - Test H_SILENT: positive, negative, edge cases
    - Property-based testing

[ ] SparqlGuardCheckerTest
    - Test H_STUB: positive, negative, edge cases
    - Test H_EMPTY: positive, negative, edge cases
    - Test H_FALLBACK: positive, negative, edge cases
    - Test H_LIE: positive, negative, edge cases
    - Test query loading
    - Test timeout enforcement
    - Test result mapping

[ ] GuardReceiptTest
    - Construct valid receipt
    - Reject invalid receipts
    - Verify invariants

[ ] HyperStandardsValidatorTest
    - Validate with violations
    - Validate with clean code
    - Check exit codes
    - Verify receipt generation
    - Check summary accuracy

[ ] Integration Tests
    - End-to-end: fixture file → violations → receipt
    - All 7 patterns: positive detection
    - Clean code: no false positives
    - Performance: <5s per file
```

### Phase 9: Documentation (1h, Engineer A)

```
[ ] Package-info.java files
    - org.yawlfoundation.yawl.ggen.validation.guards
    - org.yawlfoundation.yawl.ggen.validation.guards.api
    - org.yawlfoundation.yawl.ggen.validation.guards.model
    - org.yawlfoundation.yawl.ggen.validation.guards.checker
    - org.yawlfoundation.yawl.ggen.validation.guards.ast
    - org.yawlfoundation.yawl.ggen.validation.guards.rdf
    - org.yawlfoundation.yawl.ggen.validation.guards.sparql
    - org.yawlfoundation.yawl.ggen.validation.guards.receipt
    - org.yawlfoundation.yawl.ggen.validation.guards.config

[ ] Javadoc
    - All public classes documented
    - Method contracts specified
    - Examples provided

[ ] README (if needed)
    - Usage examples
    - Architecture overview
    - Integration points

[ ] Troubleshooting
    - False positives: how to debug
    - False negatives: how to report
    - Performance: tuning guide
```

---

## Critical Success Factors

1. **Accuracy**: 100% true positive, 0% false positive
2. **Performance**: <5s per file
3. **No Silent Failures**: All errors propagate with context
4. **Clean Contracts**: Interfaces specify expectations
5. **Comprehensive Testing**: All patterns tested positive + negative + edge cases
6. **Exit Code Correctness**: 0 (GREEN), 1 (transient), 2 (RED)

---

## File Structure Summary

```
yawl-ggen/
├── src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/
│   ├── HyperStandardsValidator.java
│   ├── api/
│   │   ├── GuardChecker.java
│   │   ├── GuardCheckerRegistry.java
│   │   └── GuardPhase.java
│   ├── model/
│   │   ├── GuardViolation.java
│   │   ├── GuardReceipt.java
│   │   └── GuardSummary.java
│   ├── checker/
│   │   ├── RegexGuardChecker.java
│   │   └── SparqlGuardChecker.java
│   ├── ast/
│   │   ├── JavaAstParser.java
│   │   ├── MethodInfo.java
│   │   ├── CommentInfo.java
│   │   └── AstExtractor.java
│   ├── rdf/
│   │   ├── GuardRdfConverter.java
│   │   ├── GuardRdfNamespaces.java
│   │   └── RdfPropertyBuilder.java
│   ├── sparql/
│   │   ├── SparqlQueryLoader.java
│   │   ├── SparqlExecutor.java
│   │   └── SparqlResultMapper.java
│   ├── receipt/
│   │   ├── GuardReceiptWriter.java
│   │   ├── GuardReceiptValidator.java
│   │   └── GuardReceiptSchema.java
│   └── config/
│       ├── GuardConfig.java
│       └── GuardPatternConfig.java
├── src/main/resources/
│   ├── sparql/guards/
│   │   ├── guards-h-todo.sparql
│   │   ├── guards-h-mock.sparql
│   │   ├── guards-h-stub.sparql
│   │   ├── guards-h-empty.sparql
│   │   ├── guards-h-fallback.sparql
│   │   ├── guards-h-lie.sparql
│   │   └── guards-h-silent.sparql
│   ├── config/
│   │   └── guard-config.toml
│   └── schema/
│       └── guard-receipt-schema.json
└── src/test/
    ├── java/org/yawlfoundation/yawl/ggen/validation/guards/
    │   ├── checker/
    │   │   ├── RegexGuardCheckerTest.java
    │   │   └── SparqlGuardCheckerTest.java
    │   ├── model/GuardReceiptTest.java
    │   └── HyperStandardsValidatorTest.java
    └── resources/fixtures/
        ├── violation-h-todo.java
        ├── violation-h-mock.java
        ├── violation-h-stub.java
        ├── violation-h-empty.java
        ├── violation-h-fallback.java
        ├── violation-h-lie.java
        ├── violation-h-silent.java
        ├── clean-code.java
        └── edge-cases.java
```

---

## Key Decisions Recap

1. **Hybrid Detection**: Regex (fast) + SPARQL (semantic) balances speed and accuracy
2. **AST-based**: Tree-sitter parsing > line-by-line regex for accuracy
3. **RDF Layer**: SPARQL queries on structured RDF facts (not raw text)
4. **Pluggable Checkers**: GuardChecker interface enables extension
5. **No False Negatives**: 100% true positive rate required
6. **Zero False Positives**: Clean code must pass all checks
7. **Fail Fast**: Errors propagate immediately (no silent failures)
8. **Exit Code Contract**: 0 (GREEN), 1 (transient), 2 (RED)

---

## Proceed to Phase 1

Engineer A: Start with GuardChecker interface and models.
Engineer B: Prepare AST parser and RDF converter code.
Tester C: Prepare test fixture templates.

All phases depend on Phase 1 deliverables (interfaces & models).
