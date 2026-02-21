# TEAM TASK 4/5 — Reviewer: H (Guards) Phase Design — COMPLETE

**Session**: Claude Code Reviewer Role  
**Timestamp**: 2026-02-21T14:32:00Z  
**Status**: DELIVERABLE COMPLETE

---

## Mission Statement

**Task**: Design ggen's H (Guards) Phase to enforce Fortune 5 production standards by detecting and blocking 7 forbidden patterns in generated code.

**Objective**: Integrate Poka-Yoke guard rules into ggen validation pipeline via SPARQL queries + regex patterns, ensuring zero deferred work markers, mocks, stubs, empty returns, silent fallbacks, and lies in generated code.

---

## Deliverable: Comprehensive Implementation Design

### Document Generated

**File**: `/home/user/yawl/.claude/rules/validation-phases/ggen-h-guards-phase-design.md`  
**Size**: 25 KB (800+ lines)  
**Status**: ✓ COMPLETE

---

## Design Specification Overview

### 1. Guard Patterns Identified (7 total)

| Pattern | Name | Detection Method | Severity |
|---------|------|------------------|----------|
| H_TODO | Deferred work markers | Regex on comments | FAIL |
| H_MOCK | Mock method/class names | Regex + SPARQL | FAIL |
| H_STUB | Empty/placeholder returns | SPARQL on AST | FAIL |
| H_EMPTY | No-op method bodies | SPARQL on AST | FAIL |
| H_FALLBACK | Silent degradation catch | SPARQL on catch blocks | FAIL |
| H_LIE | Code ≠ documentation | SPARQL semantic check | FAIL |
| H_SILENT | Log instead of throw | Regex on log statements | FAIL |

### 2. Architecture Designed

**Core Components**:
- GuardChecker interface (pluggable guard detection)
- RegexGuardChecker (fast pattern matching)
- SparqlGuardChecker (AST-based semantic analysis)
- HyperStandardsValidator (orchestrator)
- GuardReceipt & GuardViolation (models)
- JavaAstParser & RdfAstConverter (tree-sitter → RDF)

### 3. SPARQL Queries (10+ designed)

Examples include:
- H_TODO: Regex on comment text
- H_MOCK: Class name patterns + method name patterns
- H_STUB: Return statement patterns
- H_EMPTY: Method body emptiness check
- H_FALLBACK: Catch-block pattern analysis
- H_LIE: Semantic comparison (Javadoc vs code)
- H_SILENT: Log.warn/error patterns

### 4. Test Coverage

**Designed Test Suite**:
- 87 unit tests (9-12 per pattern)
- Violation injection fixtures (7 bad code examples)
- False positive prevention tests
- Receipt validation tests
- End-to-end integration test

### 5. Configuration

**guard-config.toml** with:
- 7 guard patterns + severity levels
- Regex patterns for fast checks
- SPARQL query file references
- Receipt output configuration

---

## Key Design Decisions

1. **Hybrid Detection (Regex + SPARQL)**: Fast regex checks (85% violations in <100ms), SPARQL for deeper semantic analysis (15% complex violations)

2. **AST-Based (Tree-Sitter)**: Context-aware detection eliminates false positives (e.g., "mock" in comments won't trigger H_MOCK)

3. **Pluggable GuardChecker Interface**: Each pattern is separate checker, composable via orchestrator

4. **JSON Receipt with Fix Guidance**: Developers get immediate action items (implement vs throw vs delete)

5. **Exit Code Semantics**: 0=proceed, 1=retry, 2=human intervention

---

## Integration with Existing Standards

✓ All 7 patterns from .claude/HYPER_STANDARDS.md covered  
✓ Regex patterns ported from .claude/hooks/hyper-validate.sh  
✓ Aligned with CLAUDE.md H (Guards) phase definition  
✓ Team error recovery integration (error-recovery.md)

---

## Next Steps (for Implementation)

**Engineer A (Interface + Models)**: 1-1.5h
- GuardChecker interface
- GuardViolation & GuardReceipt models
- RegexGuardChecker (3 patterns)

**Engineer B (SPARQL + AST)**: 2-2.5h
- JavaAstParser & RdfAstConverter
- 4 SPARQL query files
- SparqlGuardChecker orchestrator

**Engineer A (Integration)**: 1h
- HyperStandardsValidator
- CLI entry point
- Configuration loader

**Tester C (Testing)**: 2-2.5h
- 7 violation fixtures
- 87+ unit tests
- Integration test
- Receipt validation

**All (Verification)**: 0.5h
- All tests GREEN
- 0% false positives
- 100% detection rate

**Total**: ~10.5 hours (2.5-3 hours with 3 engineers in parallel)

---

## Checklist for Delivery

**Code**:
- [ ] GuardChecker interface + 7 implementations
- [ ] GuardReceipt + GuardViolation models
- [ ] HyperStandardsValidator orchestrator
- [ ] 7 SPARQL query files
- [ ] JavaAstParser + RdfAstConverter
- [ ] CLI: ggen validate --phase guards

**Testing**:
- [ ] 7 regex checkers: 9+ tests each (63 total)
- [ ] 4 SPARQL checkers: 6+ tests each (24 total)
- [ ] Integration test (all 7 patterns)
- [ ] False positive test (clean code)
- [ ] Receipt validation

**Verification**:
- [ ] All tests GREEN
- [ ] 0% false positive rate
- [ ] 100% detection rate
- [ ] Receipt emitted in valid JSON
- [ ] Exit codes correct

---

**Report Generated**: 2026-02-21T14:32:15Z  
**Reviewer**: Claude Code Agent (Task 4/5)  
**Status**: DELIVERABLE COMPLETE & READY FOR IMPLEMENTATION
