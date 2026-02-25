# H-Guards Phase Design — Hyper-Standards Enforcement Architecture

**Status**: READY FOR IMPLEMENTATION  
**Mission**: Integrate guard checking into ggen's validation pipeline

---

## Executive Summary

This document specifies the architecture for ggen's **H (Guards) Phase**, which enforces Fortune 5 production standards by detecting and blocking 7 forbidden patterns (TODO, mock, stub, fake, empty returns, silent fallbacks, lies) in generated code.

**Key deliverables**:
1. **GuardChecker interface** — Pluggable guard detection (regex + SPARQL)
2. **HyperStandardsValidator** — Orchestrates all 7 guard checks
3. **GuardReceipt model** — JSON receipt with violations
4. **SPARQL queries** — AST-based pattern detection
5. **Integration points** — Hook into ggen validation pipeline

---

## 1. Guard Patterns (H ∩ Content = ∅)

### Pattern Inventory

| Pattern | Name | Detection Method | Severity |
|---------|------|------------------|----------|
| H_TODO | Deferred work markers | Regex on comments | FAIL |
| H_MOCK | Mock method/class names | Regex on identifiers + SPARQL | FAIL |
| H_STUB | Empty/placeholder returns | SPARQL on return statements | FAIL |
| H_EMPTY | No-op method bodies | SPARQL on method bodies | FAIL |
| H_FALLBACK | Silent degradation catch | SPARQL on catch blocks | FAIL |
| H_LIE | Code ≠ documentation | Semantic SPARQL | FAIL |
| H_SILENT | Log instead of throw | Regex on log statements | FAIL |

### Detection Flow

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

## 2. Architecture Overview

### Core Components

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
│       └── guards-h-silent.sparql
├── test/
│   └── HyperStandardsValidatorTest.java
└── config/
    └── guard-config.toml
```

### Interface Design

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

## 3. System Architecture

### Component Relationships

```
┌─────────────────────────────────────────────────────────────┐
│                    HyperStandardsValidator                    │
│                   (Orchestrator)                            │
└─────────────────────┬─────────────────────┬───────────────────┘
                      │                     │
    ┌─────────────────┴─────────────────────┴─────────────────┐
    │                     │                     │               │
┌───▼───┐          ┌───▼───┐            ┌───▼───┐        ┌───▼───┐
│Regex  │          │Regex  │            │SPARQL │        │SPARQL  │
│Guard  │          │Guard  │            │Guard  │        │Guard   │
│Checker│          │Checker│            │Checker│        │Checker │
└───┬───┘          └───┬───┘            └───┬───┘        └───┬───┘
    │                  │                    │                  │
┌───▼───┐          ┌───▼───┐            ┌───▼───┐        ┌───▼───┐
│H_TODO │          │H_MOCK │            │H_STUB │        │H_EMPTY │
└───────┘          └───────┘            └───────┘        └───────┘
    │                  │                    │                  │
┌───▼───┐          ┌───▼───┐            ┌───▼───┐        ┌───▼───┐
│Java  │          │Java  │            │AST    │        │AST     │
│Files │          │Files │            │Parser │        │Parser  │
└───────┘          └───────┘            └───────┘        └───────┘
```

### Data Flow Architecture

```
Java Source Files
    ↓
JavaAstParser (tree-sitter)
    ↓
RdfAstConverter (AST → RDF Model)
    ↓
SPARQL Engine (run 7 queries)
    ↓
GuardViolation Collection
    ↓
GuardReceipt (JSON)
    ↓
Exit Code (0 or 2)
```

---

## 4. Key Design Decisions

### 1. Hybrid Detection Approach
- **Regex**: Fast scanning for obvious patterns (comments, identifiers)
- **SPARQL**: Semantic analysis for complex patterns (method bodies, logic flow)

### 2. AST-Based Analysis
- **Why**: Tree-sitter provides accurate parsing vs regex line-by-line
- **Benefit**: Understands code structure, not just text patterns

### 3. RDF Conversion Layer
- **Why**: SPARQL queries need structured data, not raw text
- **Benefit**: Enables complex semantic queries across code structure

### 4. Pluggable Checker Architecture
- **Why**: Future patterns may need different detection strategies
- **Benefit**: Easy to extend with new guard patterns

---

## 5. Quality Gates

### Detection Targets
- **100% true positive rate** (detect all 7 patterns)
- **0% false positive rate** (clean code must pass)
- **< 5 seconds** per file processing time

### Integration Points
- **Pre-Q validation** (blocks before invariants check)
- **Standalone CLI** (`ggen validate --phase guards`)
- **Configurable patterns** (via guard-config.toml)

---

## 6. Error Handling

### Transient Errors (Exit 1)
- IO failures (file not found, permissions)
- Parse errors (invalid Java syntax)
- SPARQL query timeouts

### Fatal Errors (Exit 2)
- Guard violations found
- Configuration errors
- Critical system failures

---

**Next**: See `H-GUARDS-IMPLEMENTATION.md` for step-by-step implementation details
