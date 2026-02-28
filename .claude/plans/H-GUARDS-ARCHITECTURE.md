# H-Guards Architecture Plan — Complete System Design

**Status**: ARCHITECTURE DESIGN READY FOR IMPLEMENTATION
**Version**: 1.0
**Created**: 2026-02-28
**Audience**: Implementation team (Engineer A, Engineer B, Tester C)
**Mission**: Design the H (Guards) phase for YAWL v6.0.0 validation pipeline

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Module Structure & Positioning](#module-structure--positioning)
3. [System Architecture](#system-architecture)
4. [Critical Interfaces](#critical-interfaces)
5. [Data Models](#data-models)
6. [Integration Points](#integration-points)
7. [Quality Gates](#quality-gates)
8. [Risk Assessment & Mitigation](#risk-assessment--mitigation)
9. [Implementation Roadmap](#implementation-roadmap)
10. [Detailed Contract Specifications](#detailed-contract-specifications)

---

## Executive Summary

The H (Guards) phase enforces Fortune 5 production standards by detecting and blocking 7 forbidden patterns in generated Java code:

| Pattern | Detection | Severity | Remediation |
|---------|-----------|----------|-------------|
| **H_TODO** | Comment regex | FAIL | Implement or throw |
| **H_MOCK** | Identifier regex | FAIL | Delete or implement |
| **H_STUB** | SPARQL on returns | FAIL | Implement or throw |
| **H_EMPTY** | SPARQL on bodies | FAIL | Implement or throw |
| **H_FALLBACK** | SPARQL on catch | FAIL | Propagate exception |
| **H_LIE** | SPARQL semantic | FAIL | Update code ↔ docs |
| **H_SILENT** | Log regex | FAIL | Throw instead |

**Core Design Principles**:
1. **Hybrid detection**: Regex for fast pattern matching + SPARQL for semantic analysis
2. **AST-based**: Tree-sitter parsing provides accurate code structure (not line-by-line regex)
3. **RDF layer**: SPARQL queries operate on structured RDF facts extracted from AST
4. **Pluggable checkers**: GuardChecker interface enables extension with new patterns
5. **No false negatives**: 100% true positive rate (catch all violations)
6. **Zero false positives**: Clean code must pass with 0% false positive rate

**Success Criteria**:
- All 7 patterns detected with 100% accuracy
- <5 seconds per file processing time
- Zero false positives on clean code
- Receipt integrity tracking via JSON validation
- Exit codes: 0 (GREEN) vs 2 (RED)

---

## Module Structure & Positioning

### 1. Location: yawl-ggen (Code Generation Engine)

**Rationale**:
- H-Guards validates generated code output from ggen
- Guards phase runs after Λ (compile green), before Q (invariants)
- yawl-ggen already has dependencies: Apache Jena 4.8.0, GSON, SLF4J, tree-sitter bindings

**Module**: `yawl-ggen` (existing, verified in modules.json)
- Path: `/home/user/yawl/yawl-ggen`
- Source dir: `src/main/java`
- Test dir: `src/test/java`
- Resources: `src/main/resources`

### 2. Package Structure

```
org.yawlfoundation.yawl.ggen.validation.guards
├── api/
│   ├── GuardChecker.java              [Interface: pluggable detection]
│   ├── GuardCheckerRegistry.java      [Factory: manage checkers]
│   └── GuardPhase.java                [CLI entry point]
├── model/
│   ├── GuardViolation.java            [Single violation record]
│   ├── GuardReceipt.java              [Overall result with violations]
│   └── GuardSummary.java              [Per-pattern counts]
├── checker/
│   ├── RegexGuardChecker.java         [Impl: H_TODO, H_MOCK, H_SILENT]
│   └── SparqlGuardChecker.java        [Impl: H_STUB, H_EMPTY, H_FALLBACK, H_LIE]
├── ast/
│   ├── JavaAstParser.java             [tree-sitter wrapper]
│   ├── MethodInfo.java                [Method metadata record]
│   ├── CommentInfo.java               [Comment metadata record]
│   └── AstExtractor.java              [Extract features from AST]
├── rdf/
│   ├── GuardRdfConverter.java         [AST → RDF Model]
│   ├── GuardRdfNamespaces.java        [Namespace definitions]
│   └── RdfPropertyBuilder.java        [Fluent RDF builder]
├── sparql/
│   ├── SparqlQueryLoader.java         [Load .sparql files]
│   ├── SparqlExecutor.java            [Execute with timeout]
│   └── SparqlResultMapper.java        [Map results → violations]
├── receipt/
│   ├── GuardReceiptWriter.java        [Emit JSON receipt]
│   ├── GuardReceiptValidator.java     [Validate receipt schema]
│   └── GuardReceiptSchema.java        [JSON schema definitions]
├── config/
│   ├── GuardConfig.java               [TOML configuration loader]
│   └── GuardPatternConfig.java        [Per-pattern config record]
└── HyperStandardsValidator.java       [Orchestrator: runs all checks]
```

### 3. Resource Structure

```
src/main/resources/
├── sparql/guards/
│   ├── guards-h-todo.sparql           [Query: deferred work]
│   ├── guards-h-mock.sparql           [Query: mock patterns]
│   ├── guards-h-stub.sparql           [Query: stub returns]
│   ├── guards-h-empty.sparql          [Query: empty bodies]
│   ├── guards-h-fallback.sparql       [Query: silent catch]
│   ├── guards-h-lie.sparql            [Query: doc mismatch]
│   └── guards-h-silent.sparql         [Query: silent logs]
├── config/
│   └── guard-config.toml              [Pattern registry]
└── schema/
    └── guard-receipt-schema.json      [Receipt validation]

src/test/resources/
├── fixtures/
│   ├── violation-h-todo.java          [Test: TODO detection]
│   ├── violation-h-mock.java          [Test: mock detection]
│   ├── violation-h-stub.java          [Test: stub detection]
│   ├── violation-h-empty.java         [Test: empty detection]
│   ├── violation-h-fallback.java      [Test: fallback detection]
│   ├── violation-h-lie.java           [Test: doc mismatch]
│   ├── violation-h-silent.java        [Test: silent log]
│   └── clean-code.java                [Test: no violations]
└── receipts/
    ├── expected-receipt-violations.json
    └── expected-receipt-clean.json
```

---

## System Architecture

### 1. Detection Pipeline (5-Phase Flow)

```
Phase 1: PARSE
  Input: Generated .java files
  ├─ Read file from emit directory
  ├─ Parse with tree-sitter
  └─ Output: Abstract Syntax Tree (AST)

Phase 2: EXTRACT
  Input: AST
  ├─ Walk AST nodes
  ├─ Extract methods, comments, returns, catch blocks
  ├─ Build method info + comment info records
  └─ Output: List<MethodInfo>, List<CommentInfo>

Phase 3: CONVERT
  Input: Method info + comment info
  ├─ Create RDF Model
  ├─ Add namespace prefixes
  ├─ Convert each method to RDF resource
  ├─ Link comments to methods
  └─ Output: RDF Model (Jena)

Phase 4: QUERY
  Input: RDF Model + SPARQL files
  ├─ Load 7 SPARQL query files
  ├─ Execute each query with 30s timeout
  ├─ Map SPARQL results to GuardViolation objects
  └─ Output: List<GuardViolation> (unsorted)

Phase 5: REPORT
  Input: List<GuardViolation>
  ├─ Group violations by pattern
  ├─ Calculate summary statistics
  ├─ Create GuardReceipt
  ├─ Write JSON receipt
  └─ Output: exit 0 (GREEN) or exit 2 (RED)
```

### 2. Component Interaction Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    HyperStandardsValidator                    │
│         (Orchestrates all phases, manages checkers)           │
└──────────────────────────────────────────────────────────────┘
                           ↓ controls ↓
        ┌──────────────────────────────────────────────┐
        │         GuardCheckerRegistry                 │
        │    (Manages 7 guard checkers)               │
        └──────────────────────────────────────────────┘
               ↓      ↓      ↓      ↓      ↓
    ┌────────────┐┌────────────┐┌────────────────┐
    │ Regex      ││ Regex      ││ Regex          │
    │ Checker    ││ Checker    ││ Checker        │
    │ (H_TODO)   ││ (H_MOCK)   ││ (H_SILENT)     │
    └────┬───────┘└────┬───────┘└────┬───────────┘
         │             │             │
    ┌────▼─────────────▼─────────────▼────────────┐
    │  Phase 1: PARSE (tree-sitter)              │
    │  File.java → AST                           │
    └────┬──────────────────────────────────────┘
         │
    ┌────▼──────────────────────────────────────┐
    │  Phase 2: EXTRACT (AST walker)            │
    │  AST → MethodInfo + CommentInfo          │
    └────┬──────────────────────────────────────┘
         │
    ┌────▼──────────────────────────────────────┐
    │  Phase 3: CONVERT (GuardRdfConverter)     │
    │  Method info → RDF Model (Jena)          │
    └────┬──────────────────────────────────────┘
         │
         │              ↓      ↓      ↓      ↓
         │        ┌────────────────────────────────┐
         │        │    SPARQL Checkers            │
         │        │ (H_STUB, H_EMPTY,             │
         │        │  H_FALLBACK, H_LIE)          │
         │        └────────────┬───────────────────┘
         │                     │
         │        ┌────────────▼──────────────┐
         │        │  Phase 4: QUERY           │
         │        │  RDF → SPARQL queries     │
         │        │  → violations             │
         │        └────────────┬──────────────┘
         │                     │
         └─────────────────────┘
                     │
        ┌────────────▼──────────────────┐
        │  Phase 5: REPORT              │
        │  Violations → Receipt.json    │
        │  → exit code (0 or 2)         │
        └───────────────────────────────┘
```

### 3. Data Flow Across Phases

```
File System Input
    ↓
[PARSE] tree-sitter
    ↓ Tree (AST node graph)
[EXTRACT] AST walker
    ↓ MethodInfo[], CommentInfo[]
[CONVERT] GuardRdfConverter
    ↓ Model (Jena RDF)
[QUERY] SparqlExecutor (×7 parallel/sequential)
    ↓ List<GuardViolation>
[REPORT] GuardReceiptWriter
    ↓ JSON file + exit code
```

---

## Critical Interfaces

### 1. GuardChecker Interface

```java
package org.yawlfoundation.yawl.ggen.validation.guards.api;

/**
 * Pluggable guard detection strategy.
 * Implementations provide specific pattern detection logic.
 *
 * Contract:
 *   - check() returns empty list if no violations
 *   - check() returns violations in source file order
 *   - check() MUST NOT modify input file
 *   - check() MUST be idempotent (same input → same output)
 *   - check() SHOULD complete in < 5 seconds per file
 *   - check() SHOULD throw IOException only on file access errors
 *   - check() SHOULD throw GuardCheckException on pattern errors (config, parse)
 */
public interface GuardChecker {

    /**
     * Check a Java source file for guard violations.
     *
     * @param javaSourcePath Absolute path to .java file
     * @return List of violations (empty if no violations found)
     * @throws IOException If file cannot be read (transient error)
     * @throws GuardCheckException If pattern fails (fatal error)
     */
    List<GuardViolation> check(Path javaSourcePath)
        throws IOException, GuardCheckException;

    /**
     * Get this checker's pattern identifier.
     *
     * @return Pattern name (e.g., "H_TODO", "H_MOCK")
     */
    String patternName();

    /**
     * Get this checker's severity level.
     *
     * @return Severity enum (FAIL, WARN)
     */
    GuardSeverity severity();

    /**
     * Validate configuration at startup.
     * Checkers SHOULD NOT accept invalid config silently.
     *
     * @param config Pattern-specific configuration
     * @return true if valid, false otherwise
     */
    boolean validateConfig(GuardPatternConfig config);
}
```

**Key Invariants**:
- No side effects (read-only)
- Idempotent (call multiple times, same result)
- Fail fast on configuration errors
- Return violations in ascending line number order

### 2. HyperStandardsValidator Orchestrator

```java
package org.yawlfoundation.yawl.ggen.validation.guards;

/**
 * Main validator orchestrating all 7 guard checks across emit directory.
 *
 * Responsibilities:
 *   1. Load configuration (guard-config.toml)
 *   2. Initialize all 7 GuardChecker instances
 *   3. Discover all .java files in emit directory
 *   4. Run all checkers against each file
 *   5. Aggregate violations by pattern and file
 *   6. Generate GuardReceipt with summary
 *   7. Write receipt to JSON
 *   8. Return exit code (0 = GREEN, 2 = RED)
 *
 * Contract:
 *   - validateEmitDir() returns GuardReceipt with all violations
 *   - Order of violations in receipt: by file, then by line number
 *   - Summary statistics computed from violations list
 *   - Receipt timestamp uses UTC ISO-8601 format
 *   - Exceptions propagated to caller (no silent failures)
 */
public class HyperStandardsValidator {

    /**
     * Validate all Java files in emit directory.
     *
     * @param emitDir Absolute path to generated code directory
     * @param receiptPath Absolute path where receipt.json will be written
     * @return GuardReceipt with status (RED if violations found)
     * @throws IOException If directory cannot be read
     * @throws GuardConfigException If configuration is invalid
     */
    public GuardReceipt validateEmitDir(Path emitDir, Path receiptPath)
        throws IOException, GuardConfigException;

    /**
     * Get the list of discovered .java files (read-only).
     */
    public List<Path> getDiscoveredFiles();

    /**
     * Get guard checkers in use (read-only).
     */
    public List<GuardChecker> getActiveCheckers();
}
```

**Exit Code Contract**:
- `0` = No violations found (status = "GREEN")
- `1` = Transient error (IO, parse timeout) — retry safe
- `2` = Violations found or fatal error (status = "RED") — fix required

### 3. GuardViolation Record

```java
package org.yawlfoundation.yawl.ggen.validation.guards.model;

/**
 * Immutable representation of a single guard violation.
 *
 * Contract:
 *   - file: Absolute path to source file
 *   - line: 1-indexed line number where violation starts
 *   - pattern: One of {H_TODO, H_MOCK, H_STUB, H_EMPTY, H_FALLBACK, H_LIE, H_SILENT}
 *   - severity: One of {FAIL, WARN} — currently all FAIL
 *   - content: Exact source code line(s) that violate pattern
 *   - fixGuidance: Human-readable fix instructions
 */
public record GuardViolation(
    String file,
    int line,
    String pattern,
    String severity,
    String content,
    String fixGuidance
) {

    public GuardViolation {
        Objects.requireNonNull(file, "file cannot be null");
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(fixGuidance, "fixGuidance cannot be null");

        if (line <= 0) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (!isValidPattern(pattern)) {
            throw new IllegalArgumentException("Unknown pattern: " + pattern);
        }
        if (!isValidSeverity(severity)) {
            throw new IllegalArgumentException("Unknown severity: " + severity);
        }
    }

    private static boolean isValidPattern(String p) {
        return p.matches("H_(TODO|MOCK|STUB|EMPTY|FALLBACK|LIE|SILENT)");
    }

    private static boolean isValidSeverity(String s) {
        return s.equals("FAIL") || s.equals("WARN");
    }

    @Override
    public String toString() {
        return String.format("%s:%d [%s] %s: %s",
            file, line, pattern, severity, fixGuidance);
    }
}
```

### 4. GuardReceipt Record

```java
package org.yawlfoundation.yawl.ggen.validation.guards.model;

/**
 * Complete validation result including all violations and summary.
 *
 * Contract:
 *   - status: "GREEN" (no violations) or "RED" (violations found)
 *   - phase: Always "guards"
 *   - timestamp: ISO-8601 UTC instant when validation completed
 *   - filesScanned: Total number of .java files processed
 *   - violations: Complete list of GuardViolation objects (empty if GREEN)
 *   - summary: Per-pattern violation counts
 *   - errorMessage: Detailed error description (if status = RED)
 *   - duration: Milliseconds to complete validation (perf tracking)
 */
public record GuardReceipt(
    String phase,
    String status,
    Instant timestamp,
    int filesScanned,
    List<GuardViolation> violations,
    GuardSummary summary,
    String errorMessage,
    long durationMs
) {

    public GuardReceipt {
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(violations, "violations cannot be null");
        Objects.requireNonNull(summary, "summary cannot be null");

        if (!status.matches("GREEN|RED")) {
            throw new IllegalArgumentException("status must be GREEN or RED");
        }
        if (!phase.equals("guards")) {
            throw new IllegalArgumentException("phase must be 'guards'");
        }
        if (filesScanned < 0) {
            throw new IllegalArgumentException("filesScanned must be >= 0");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0");
        }
    }

    /**
     * Invariant: status = RED ⟺ violations.isEmpty() = false
     */
    public boolean isValid() {
        if (status.equals("RED")) {
            return !violations.isEmpty() && errorMessage != null && !errorMessage.isBlank();
        } else {
            return violations.isEmpty() && (errorMessage == null || errorMessage.isBlank());
        }
    }
}
```

### 5. GuardSummary Record

```java
package org.yawlfoundation.yawl.ggen.validation.guards.model;

/**
 * Per-pattern violation counts for summary reporting.
 */
public record GuardSummary(
    int h_todo_count,
    int h_mock_count,
    int h_stub_count,
    int h_empty_count,
    int h_fallback_count,
    int h_lie_count,
    int h_silent_count
) {

    public int getTotalViolations() {
        return h_todo_count + h_mock_count + h_stub_count + h_empty_count
             + h_fallback_count + h_lie_count + h_silent_count;
    }

    public static GuardSummary from(List<GuardViolation> violations) {
        int todo = 0, mock = 0, stub = 0, empty = 0, fallback = 0, lie = 0, silent = 0;

        for (GuardViolation v : violations) {
            switch (v.pattern()) {
                case "H_TODO" -> todo++;
                case "H_MOCK" -> mock++;
                case "H_STUB" -> stub++;
                case "H_EMPTY" -> empty++;
                case "H_FALLBACK" -> fallback++;
                case "H_LIE" -> lie++;
                case "H_SILENT" -> silent++;
            }
        }

        return new GuardSummary(todo, mock, stub, empty, fallback, lie, silent);
    }
}
```

---

## Data Models

### 1. AST Extraction Models

```java
package org.yawlfoundation.yawl.ggen.validation.guards.ast;

/**
 * Metadata extracted from a method declaration in AST.
 * Immutable record for AST walker output.
 */
public record MethodInfo(
    String id,                      // Unique ID (e.g., ClassName.methodName)
    String name,                    // Method name
    String className,               // Containing class
    int lineNumber,                 // Line number of method declaration
    String returnType,              // Return type (e.g., "void", "String")
    List<String> annotations,       // Method annotations
    String body,                    // Full method body source (if available)
    String javadoc,                 // Javadoc text (if present)
    List<CommentInfo> comments      // Comments within method
) {}

/**
 * Metadata extracted from a comment in AST.
 */
public record CommentInfo(
    String id,                      // Unique ID
    int lineNumber,                 // Line number of comment start
    String text,                    // Full comment text (including // or /*)
    CommentType type                // SINGLE_LINE or BLOCK
) {
    public enum CommentType {
        SINGLE_LINE,    // // comment
        BLOCK           // /* */ comment
    }
}
```

### 2. RDF Namespace Definitions

```java
package org.yawlfoundation.yawl.ggen.validation.guards.rdf;

/**
 * RDF namespace definitions for guard validation ontology.
 * Enables semantic SPARQL queries on extracted code structure.
 */
public final class GuardRdfNamespaces {
    // Ontology URI for guard validation
    public static final String GUARD_NS = "http://ggen.io/guards#";

    // RDF standard namespaces
    public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";

    // Reserved prefixes for use in SPARQL
    public static final String GUARD_PREFIX = "guard";
    public static final String RDF_PREFIX = "rdf";
    public static final String RDFS_PREFIX = "rdfs";
    public static final String XSD_PREFIX = "xsd";

    // Core RDF properties used in guard detection
    public static final class Property {
        public static final String HAS_METHOD = GUARD_NS + "hasMethod";
        public static final String HAS_COMMENT = GUARD_NS + "hasComment";
        public static final String METHOD_NAME = GUARD_NS + "methodName";
        public static final String METHOD_BODY = GUARD_NS + "methodBody";
        public static final String RETURN_TYPE = GUARD_NS + "returnType";
        public static final String COMMENT_TEXT = GUARD_NS + "commentText";
        public static final String LINE_NUMBER = GUARD_NS + "lineNumber";
        public static final String JAVADOC = GUARD_NS + "javadoc";
        public static final String ANNOTATIONS = GUARD_NS + "hasAnnotation";
    }

    // Core RDF types (classes)
    public static final class Type {
        public static final String FILE = GUARD_NS + "JavaFile";
        public static final String METHOD = GUARD_NS + "Method";
        public static final String COMMENT = GUARD_NS + "Comment";
        public static final String JAVADOC = GUARD_NS + "Javadoc";
    }
}
```

---

## Integration Points

### 1. CLI Entry Point

```bash
# Command structure
ggen validate --phase guards --emit <emit-dir> [--receipt-file <path>] [--verbose]

# Examples
ggen validate --phase guards --emit /home/user/yawl/generated
ggen validate --phase guards --emit generated --receipt-file .claude/receipts/guard-receipt.json --verbose
```

**CLI Implementation**:
- Class: `GuardPhase` (implements ggen's phase interface)
- Location: `org.yawlfoundation.yawl.ggen.validation.guards.api.GuardPhase`
- Responsibilities:
  1. Parse CLI arguments
  2. Resolve emit directory path (validate exists)
  3. Instantiate HyperStandardsValidator
  4. Call validateEmitDir()
  5. Handle exceptions gracefully
  6. Write receipt to disk
  7. Return exit code to ggen

### 2. Pipeline Integration (ggen validate flow)

```bash
# Current: ggen generate → compile → done
# New: ggen generate → compile → GUARDS (H) → INVARIANTS (Q) → done

ggen generate [Phase 1-5: code generation]
  ↓
bash scripts/dx.sh -pl yawl-ggen           [Phase Λ: Compile green]
  ↓ SUCCESS
if ggen validate --phase guards; then      [Phase H: ENTRYPOINT — NEW]
  ↓ EXIT 0 (GREEN)
  ggen validate --phase invariants         [Phase Q: Real impl ∨ throw]
  ↓ EXIT 0 (GREEN)
  echo "All validations passed"
else
  ↓ EXIT 2 (RED)
  echo "Guard violations detected. Fix and re-run."
  exit 2
fi
```

**Integration Points**:
1. **Before Q phase**: H-Guards MUST complete successfully before Q invariants check
2. **After Λ phase**: H-Guards assumes code compiles (no syntax errors)
3. **Exit code contract**: CLI pipeline interprets exit code
4. **Receipt path**: Fixed at `.claude/receipts/guard-receipt.json`

### 3. Configuration Integration (guard-config.toml)

```toml
# Location: yawl-ggen/src/main/resources/config/guard-config.toml
# Loaded at validator startup

[guards]
enabled = true
phase = "H"
max_file_size_kb = 1000
query_timeout_seconds = 30

# Pattern registry: defines which patterns are active
patterns = [
  { name = "H_TODO", severity = "FAIL", enabled = true },
  { name = "H_MOCK", severity = "FAIL", enabled = true },
  { name = "H_STUB", severity = "FAIL", enabled = true },
  { name = "H_EMPTY", severity = "FAIL", enabled = true },
  { name = "H_FALLBACK", severity = "FAIL", enabled = true },
  { name = "H_LIE", severity = "FAIL", enabled = true },
  { name = "H_SILENT", severity = "FAIL", enabled = true }
]

# Regex patterns for simple checkers
[regex_patterns]
h_todo = '//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)'
h_mock = '(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]'
h_silent = 'log\.(warn|error)\([^)]*"[^"]*not\s+implemented'

# SPARQL query files for semantic checkers
[sparql_patterns]
h_stub_query = "sparql/guards/guards-h-stub.sparql"
h_empty_query = "sparql/guards/guards-h-empty.sparql"
h_fallback_query = "sparql/guards/guards-h-fallback.sparql"
h_lie_query = "sparql/guards/guards-h-lie.sparql"

# Receipt output configuration
[receipt]
enabled = true
format = "json"
path = ".claude/receipts/guard-receipt.json"
include_violations = true
include_summary = true
pretty_print = true

# Caching and performance
[performance]
rdf_cache_enabled = false
parallel_checkers = false
batch_size = 1
```

---

## Quality Gates

### 1. Detection Accuracy Requirements

**Pattern Inventory and Requirements**:

| Pattern | Must Detect | Must NOT Detect | Accuracy Target |
|---------|------------|-----------------|-----------------|
| **H_TODO** | `// TODO:`, `// FIXME`, `@incomplete` | Comments in strings | 100% TP, 0% FP |
| **H_MOCK** | `MockService`, `mockFetch()`, `StubData` | `MyMockery` (suffix), non-method | 100% TP, 0% FP |
| **H_STUB** | `return "";`, `return 0;`, `return null;` | Non-stub returns, void methods | 100% TP, 0% FP |
| **H_EMPTY** | Empty void method bodies `{ }` | Methods with content | 100% TP, 0% FP |
| **H_FALLBACK** | `catch() { return fake; }` | Proper exception handling | 100% TP, 0% FP |
| **H_LIE** | `@return never null` but `return null` | Matching code/docs | 100% TP, 0% FP |
| **H_SILENT** | `log.error("not implemented")` | Actual exception throws | 100% TP, 0% FP |

**Quality Gate Metrics**:
- **True Positive Rate**: 100% (catch all violations)
- **False Positive Rate**: 0% (no false alarms)
- **Processing Time**: <5 seconds per file (including all 7 patterns)
- **Precision**: 100% (all reported violations are real)
- **Recall**: 100% (no violations missed)

### 2. Receipt Validation

```java
/**
 * GuardReceipt must satisfy:
 * 1. Status is GREEN or RED
 * 2. If RED: violations.size() > 0 AND errorMessage is not blank
 * 3. If GREEN: violations.isEmpty() AND errorMessage is blank/null
 * 4. All violations have valid patterns (one of 7)
 * 5. All violations have valid severity (FAIL or WARN)
 * 6. All violations have non-empty content
 * 7. Summary counts match violations list
 * 8. Timestamp is valid UTC ISO-8601
 * 9. filesScanned >= 0
 * 10. durationMs >= 0
 */
```

**Receipt JSON Schema** (simplified):
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["phase", "status", "timestamp", "filesScanned"],
  "properties": {
    "phase": { "type": "string", "const": "guards" },
    "status": { "type": "string", "enum": ["GREEN", "RED"] },
    "timestamp": { "type": "string", "format": "date-time" },
    "filesScanned": { "type": "integer", "minimum": 0 },
    "violations": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["file", "line", "pattern", "severity", "content", "fixGuidance"],
        "properties": {
          "file": { "type": "string" },
          "line": { "type": "integer", "minimum": 1 },
          "pattern": { "enum": ["H_TODO", "H_MOCK", "H_STUB", "H_EMPTY", "H_FALLBACK", "H_LIE", "H_SILENT"] },
          "severity": { "enum": ["FAIL", "WARN"] },
          "content": { "type": "string" },
          "fixGuidance": { "type": "string" }
        }
      }
    },
    "summary": {
      "type": "object",
      "properties": {
        "h_todo_count": { "type": "integer" },
        "h_mock_count": { "type": "integer" },
        "h_stub_count": { "type": "integer" },
        "h_empty_count": { "type": "integer" },
        "h_fallback_count": { "type": "integer" },
        "h_lie_count": { "type": "integer" },
        "h_silent_count": { "type": "integer" }
      }
    }
  }
}
```

### 3. Error Handling Classification

**Transient Errors (Exit 1 — retry safe)**:
- File not found in emit directory
- Permission denied reading file
- Out of memory during parsing
- SPARQL query timeout (>30s)
- Thread interrupted

**Fatal Errors (Exit 2 — fix required)**:
- Guard violations found
- Invalid configuration file (guard-config.toml)
- Corrupt receipt file
- Invalid Java syntax (parse error)
- RDF conversion failure

---

## Risk Assessment & Mitigation

### 1. Critical Failure Modes

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| **False Negatives** (miss violations) | MEDIUM | CRITICAL | 100% true positive rate requirement; unit tests for all 7 patterns |
| **False Positives** (flag clean code) | MEDIUM | HIGH | 0% false positive rate requirement; edge case testing |
| **Performance Timeout** | MEDIUM | HIGH | 30s per query timeout; <5s per file SLA |
| **Out of Memory** | LOW | HIGH | Batch file processing; RDF model cleanup; configurable batch size |
| **SPARQL Injection** | LOW | CRITICAL | Load queries from resources only; never user-provided patterns |
| **Concurrent Modification** | LOW | HIGH | No concurrent file modification during validation |
| **Corrupt Receipt** | LOW | MEDIUM | JSON schema validation; checksums optional |

### 2. Design Safeguards

**Guard Against False Negatives**:
1. Two-tier detection: Regex (fast) + SPARQL (semantic)
2. Regex patterns capture simple obvious cases
3. SPARQL queries handle complex semantic violations
4. Comprehensive unit tests per pattern (positive + negative + edge cases)
5. Property-based testing for regex completeness

**Guard Against False Positives**:
1. Conservative regex patterns (avoid over-matching)
2. SPARQL filters restrict to real violations
3. Integration tests with real generated code
4. Manual review of suspicious detections

**Guard Against Performance Issues**:
1. 30-second timeout per SPARQL query (kill slow queries)
2. File size check: skip files >1MB (configurable)
3. Batch processing: don't load all files in memory
4. Optional RDF caching (disabled by default)
5. Performance benchmarks in test suite

**Guard Against Resource Exhaustion**:
1. One RDF model per file (not accumulative)
2. Model cleanup after each file validation
3. Configurable batch size for large codebases
4. Memory monitoring hooks

### 3. Recovery Strategies

**If H-Guards Blocks Valid Code**:
1. Developer reviews guard-receipt.json for violations
2. Analyze false positive (likely edge case)
3. File issue with detailed error case
4. Temporarily disable pattern in guard-config.toml
5. Re-run validation
6. Fix root cause in pattern definition

**If H-Guards Misses Violations**:
1. Implement additional unit test
2. Extend regex or SPARQL query
3. Add to positive test cases
4. Re-run to verify fix

**If H-Guards Crashes**:
1. Check exit code (1 = transient, 2 = fatal)
2. If transient: retry
3. If fatal: check guard-receipt.json for error message
4. File issue with error details

---

## Implementation Roadmap

### Phase 1: Core Interfaces & Models (Day 1, ~1 hour)

**Owner**: Engineer A
**Deliverables**:
- GuardChecker interface
- GuardViolation record
- GuardReceipt record
- GuardSummary record
- GuardSeverity enum
- Exception types (GuardCheckException, GuardConfigException)

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/api/GuardChecker.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/model/GuardViolation.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/model/GuardReceipt.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/model/GuardSummary.java`

**Acceptance Criteria**:
- Records compile without errors
- Javadoc complete with contract descriptions
- Validation logic in record constructors
- All tests pass (model validation tests)

### Phase 2: AST Parser & RDF Converter (Day 1, ~1.5 hours)

**Owner**: Engineer B
**Deliverables**:
- JavaAstParser (tree-sitter wrapper)
- MethodInfo record
- CommentInfo record
- AstExtractor (AST walker)
- GuardRdfConverter (AST → RDF)
- GuardRdfNamespaces

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/ast/JavaAstParser.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/ast/MethodInfo.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/ast/AstExtractor.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/rdf/GuardRdfConverter.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/rdf/GuardRdfNamespaces.java`

**Acceptance Criteria**:
- AST parser can load and parse valid Java files
- Extractor retrieves all methods and comments
- RDF converter produces valid Jena Model
- Unit tests verify extraction accuracy
- Integration test: parsed code → RDF → SPARQL query

### Phase 3: Guard Checkers (Day 1-2, ~3.5 hours total)

**Owner**: Engineer A (Regex), Engineer B (SPARQL)

#### Phase 3a: Regex Checkers (Engineer A, ~1.5 hours)

**Deliverables**:
- RegexGuardChecker (pluggable regex implementation)
- H_TODO pattern and checker
- H_MOCK pattern and checker
- H_SILENT pattern and checker

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/checker/RegexGuardChecker.java`

**Unit Tests**:
- Test positive cases (should detect violations)
- Test negative cases (clean code should pass)
- Test edge cases (regex boundary conditions)

#### Phase 3b: SPARQL Checkers (Engineer B, ~2 hours)

**Deliverables**:
- SparqlGuardChecker (pluggable SPARQL implementation)
- SPARQL query loader
- SPARQL executor with timeout
- Result mapper: SPARQL results → GuardViolation
- H_STUB, H_EMPTY, H_FALLBACK, H_LIE queries

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/checker/SparqlGuardChecker.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/sparql/SparqlQueryLoader.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/sparql/SparqlExecutor.java`
- `/home/user/yawl/yawl-ggen/src/main/resources/sparql/guards/guards-h-stub.sparql`
- `/home/user/yawl/yawl-ggen/src/main/resources/sparql/guards/guards-h-empty.sparql`
- `/home/user/yawl/yawl-ggen/src/main/resources/sparql/guards/guards-h-fallback.sparql`
- `/home/user/yawl/yawl-ggen/src/main/resources/sparql/guards/guards-h-lie.sparql`

**Unit Tests**:
- Verify each query loads and parses
- Test query execution with sample RDF models
- Verify timeout enforcement (30s)
- Test result mapping to GuardViolation

### Phase 4: Orchestrator & CLI (Day 2, ~1 hour)

**Owner**: Engineer A
**Deliverables**:
- HyperStandardsValidator (orchestrator)
- GuardCheckerRegistry (factory + registry)
- GuardPhase (CLI entry point)
- GuardConfig (TOML loader)

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/HyperStandardsValidator.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/api/GuardCheckerRegistry.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/api/GuardPhase.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/config/GuardConfig.java`

**Acceptance Criteria**:
- Registry instantiates all 7 checkers
- Orchestrator discovers all .java files
- Orchestrator runs all checkers
- Exit code contract (0, 1, 2)

### Phase 5: Receipt & JSON Output (Day 2, ~0.75 hours)

**Owner**: Engineer A
**Deliverables**:
- GuardReceiptWriter (JSON serialization)
- GuardReceiptValidator (schema validation)
- JSON schema definition

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/receipt/GuardReceiptWriter.java`
- `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/receipt/GuardReceiptValidator.java`
- `/home/user/yawl/yawl-ggen/src/main/resources/schema/guard-receipt-schema.json`

**Acceptance Criteria**:
- Receipt JSON is valid according to schema
- Receipt can be parsed back (round-trip)
- Timestamps are UTC ISO-8601
- Summary matches violation counts

### Phase 6: Configuration (Day 2, ~0.5 hours)

**Owner**: Engineer B
**Deliverables**:
- guard-config.toml resource file
- Config loader implementation

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/main/resources/config/guard-config.toml`

### Phase 7: Test Fixtures (Day 3, ~2 hours)

**Owner**: Tester C
**Deliverables**:
- Test fixtures for all 7 patterns (positive cases)
- Clean code fixture (negative case)
- Edge case fixtures

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-todo.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-mock.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-stub.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-empty.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-fallback.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-lie.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/violation-h-silent.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/clean-code.java`
- `/home/user/yawl/yawl-ggen/src/test/resources/fixtures/edge-cases.java`

### Phase 8: Unit Tests (Day 3, ~2 hours)

**Owner**: Tester C
**Deliverables**:
- GuardCheckerTests (per pattern)
- GuardReceiptTests
- HyperStandardsValidatorTests
- Integration tests

**Files to Create**:
- `/home/user/yawl/yawl-ggen/src/test/java/org/yawlfoundation/yawl/ggen/validation/guards/checker/RegexGuardCheckerTest.java`
- `/home/user/yawl/yawl-ggen/src/test/java/org/yawlfoundation/yawl/ggen/validation/guards/checker/SparqlGuardCheckerTest.java`
- `/home/user/yawl/yawl-ggen/src/test/java/org/yawlfoundation/yawl/ggen/validation/guards/model/GuardReceiptTest.java`
- `/home/user/yawl/yawl-ggen/src/test/java/org/yawlfoundation/yawl/ggen/validation/guards/HyperStandardsValidatorTest.java`

**Test Coverage**:
- All 7 patterns: positive, negative, edge cases
- Receipt validation logic
- CLI argument parsing
- Error handling (transient vs fatal)

### Phase 9: Documentation & Examples (Day 3, ~1 hour)

**Owner**: Engineer A
**Deliverables**:
- Package-info.java files
- Javadoc on all public classes
- README with usage examples
- Troubleshooting guide

---

## Detailed Contract Specifications

### 1. GuardChecker Implementation Template

```java
public class H_TODO_Checker implements GuardChecker {
    private Pattern todoPattern;

    public H_TODO_Checker() {
        // Compile regex at construction time
        this.todoPattern = Pattern.compile(
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"
        );
    }

    @Override
    public List<GuardViolation> check(Path javaSourcePath)
            throws IOException, GuardCheckException {
        List<GuardViolation> violations = new ArrayList<>();
        List<String> lines = Files.readAllLines(javaSourcePath);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = todoPattern.matcher(line);

            if (matcher.find()) {
                violations.add(new GuardViolation(
                    javaSourcePath.toAbsolutePath().toString(),
                    i + 1,  // 1-indexed
                    "H_TODO",
                    "FAIL",
                    line.trim(),
                    "Implement real logic or throw UnsupportedOperationException"
                ));
            }
        }

        return violations;
    }

    @Override
    public String patternName() {
        return "H_TODO";
    }

    @Override
    public GuardSeverity severity() {
        return GuardSeverity.FAIL;
    }

    @Override
    public boolean validateConfig(GuardPatternConfig config) {
        // Validate pattern config for this checker
        return config != null && config.name().equals("H_TODO");
    }
}
```

### 2. Error Message Standards

**Format**: `[PATTERN] file:line: violation_description`

Examples:
```
[H_TODO] /path/to/YWorkItem.java:427: Deferred work marker: // TODO: Add deadlock detection
[H_MOCK] /path/to/MockDataService.java:12: Mock class name: MockDataService implements DataService
[H_STUB] /path/to/Service.java:89: Stub return: return "";
[H_EMPTY] /path/to/Handler.java:156: Empty method body: public void initialize() { }
[H_FALLBACK] /path/to/Processor.java:203: Silent fallback: catch (Exception e) { return Collections.emptyList(); }
[H_LIE] /path/to/Provider.java:78: Documentation mismatch: @return never null but returns null
[H_SILENT] /path/to/Logger.java:345: Silent logging: log.error("Not implemented yet")
```

### 3. Receipt File Location

**Standard Path**: `.claude/receipts/guard-receipt.json`

Example:
```json
{
  "phase": "guards",
  "status": "RED",
  "timestamp": "2026-02-28T14:32:15.123Z",
  "filesScanned": 42,
  "violations": [
    {
      "file": "/home/user/yawl/generated/java/YWorkItem.java",
      "line": 427,
      "pattern": "H_TODO",
      "severity": "FAIL",
      "content": "// TODO: Add deadlock detection",
      "fixGuidance": "Implement real deadlock logic or throw UnsupportedOperationException"
    }
  ],
  "summary": {
    "h_todo_count": 5,
    "h_mock_count": 2,
    "h_stub_count": 0,
    "h_empty_count": 3,
    "h_fallback_count": 1,
    "h_lie_count": 0,
    "h_silent_count": 0
  },
  "errorMessage": "3 guard violations found. Fix violations or throw UnsupportedOperationException.",
  "durationMs": 2847
}
```

### 4. CLI Exit Codes

| Code | Meaning | Action | Example |
|------|---------|--------|---------|
| `0` | GREEN: no violations | Proceed to Q phase | `ggen validate --phase guards && ggen validate --phase invariants` |
| `1` | Transient error | Retry safe | File I/O error, timeout, OOM |
| `2` | RED: violations found | Fix and re-run | Guard violations detected |

### 5. Logging Standards

**Logger Name**: `org.yawlfoundation.yawl.ggen.validation.guards.HyperStandardsValidator`

**Log Levels**:
- `INFO`: Started validation, completed validation, summary statistics
- `DEBUG`: Checker initialized, file processed, violations found per pattern
- `WARN`: File skipped (too large), query slow (>20s)
- `ERROR`: Fatal error (config invalid, exception), violations found

**Example Logs**:
```
INFO  [guards] Starting H-Guards validation on 42 files
DEBUG [guards] Initialized checker: H_TODO (regex)
DEBUG [guards] Initialized checker: H_MOCK (regex)
DEBUG [guards] Processing: YWorkItem.java
DEBUG [guards] Found 1 H_TODO violation in YWorkItem.java:427
INFO  [guards] H-Guards validation complete: 5 violations in 2.847s
WARN  [guards] 5 H_TODO, 2 H_MOCK, 0 H_STUB, 3 H_EMPTY, 1 H_FALLBACK, 0 H_LIE, 0 H_SILENT
```

---

## Summary

This architecture document specifies the complete H-Guards system ready for implementation. Key design decisions:

1. **Module**: yawl-ggen (existing code generation engine)
2. **Package**: org.yawlfoundation.yawl.ggen.validation.guards
3. **Interfaces**: GuardChecker (pluggable), HyperStandardsValidator (orchestrator)
4. **Data Models**: GuardViolation (record), GuardReceipt (record), GuardSummary (record)
5. **Detection**: Hybrid regex + SPARQL, AST-based parsing, RDF semantic layer
6. **Quality Gates**: 100% true positive, 0% false positive, <5s per file
7. **Integration**: Phase H in ggen validation pipeline (before Q)
8. **Exit Codes**: 0 (GREEN), 1 (transient), 2 (RED)

Implementation team should follow the 9-phase roadmap (Days 1-3) with clear ownership and acceptance criteria per phase.

---

**Next Steps**:
1. Review this architecture document
2. Assign engineers to phases (Engineer A, Engineer B, Tester C)
3. Create implementation tickets per phase
4. Execute Phase 1 (interfaces & models)
5. Parallel execute Phases 2-3 (AST & checkers)
6. Sequential execute Phases 4-9 (orchestrator, tests, docs)
7. Integration testing with real generated code
8. Final review and readiness assessment
