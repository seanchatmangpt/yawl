# H-Guards Architecture & Implementation Plans

**Complete specification suite for the H (Guards) phase of YAWL v6.0.0**

---

## Document Navigation

### Getting Started

**Start here** → [`H-GUARDS-IMPLEMENTATION-SUMMARY.md`](H-GUARDS-IMPLEMENTATION-SUMMARY.md)
- 2-page executive summary
- Timeline, team assignments, success criteria
- Risk mitigation, deliverables checklist
- Best for: Project managers, team leads, quick overview

### Core Architecture

**Main reference** → [`H-GUARDS-ARCHITECTURE.md`](H-GUARDS-ARCHITECTURE.md)
- Complete system design (65+ pages)
- Module structure, component interactions
- Critical interface specifications with contracts
- Data models, integration points, quality gates
- Risk assessment, implementation roadmap
- Best for: Architects, senior engineers, detailed design review

### Quick Reference for Developers

**During implementation** → [`H-GUARDS-CONTRACT-REFERENCE.md`](H-GUARDS-CONTRACT-REFERENCE.md)
- Interface contracts and rules
- Pattern specifications with examples
- Data flow contracts, exit codes
- Exception handling, performance SLAs
- Test requirements
- Best for: Developers coding implementation, quick lookup

### Phase Checklist

**Day-by-day guide** → [`H-GUARDS-QUICK-START.md`](H-GUARDS-QUICK-START.md)
- 9 phases with checklists
- ~30 checkboxes per phase
- Engineer assignments and time estimates
- File structure summary
- Best for: Implementation team, daily progress tracking

---

## Document Index

| Document | Pages | Purpose | Audience | Read Time |
|----------|-------|---------|----------|-----------|
| **IMPLEMENTATION-SUMMARY** | 8 | Overview, timeline, risk | Leads, managers | 10 min |
| **ARCHITECTURE** | 65+ | Complete spec, all details | All engineers | 60 min |
| **CONTRACT-REFERENCE** | 20 | Interface contracts, patterns | Developers | 20 min |
| **QUICK-START** | 15 | Phase checklists, checklist | Implementation team | 15 min |

**Supporting Specifications** (in `.claude/rules/validation-phases/`):
- H-GUARDS-DESIGN.md (design rationale)
- H-GUARDS-IMPLEMENTATION.md (step-by-step guide)
- H-GUARDS-QUERIES.md (SPARQL query reference)

---

## Quick Facts

| Item | Value |
|------|-------|
| **Status** | Architecture complete, ready for implementation |
| **Module** | yawl-ggen (existing) |
| **Package** | org.yawlfoundation.yawl.ggen.validation.guards |
| **Patterns** | 7 (H_TODO, H_MOCK, H_STUB, H_EMPTY, H_FALLBACK, H_LIE, H_SILENT) |
| **Team** | 3 engineers (A, B, C) |
| **Duration** | 3 days (Days 1-3) |
| **Total Effort** | ~13 engineer-hours |
| **Files** | ~52 (code + tests + resources) |
| **Exit Codes** | 0 (GREEN), 1 (transient), 2 (RED) |
| **SLA** | <5 seconds per file |
| **Accuracy** | 100% TP, 0% FP |

---

## Architecture Overview

### The 5-Phase Detection Pipeline

```
Phase 1: PARSE          Java file → AST (tree-sitter)
Phase 2: EXTRACT        AST → Method/Comment metadata
Phase 3: CONVERT        Metadata → RDF Model (Jena)
Phase 4: QUERY          RDF + 7 SPARQL queries → Violations
Phase 5: REPORT         Violations → GuardReceipt.json (exit code)
```

### The 7 Guard Patterns

| Pattern | Detection | Example Violation |
|---------|-----------|-------------------|
| H_TODO | Regex comment | `// TODO: implement this` |
| H_MOCK | Regex identifier | `class MockDataService` |
| H_STUB | SPARQL return | `return "";` (non-void) |
| H_EMPTY | SPARQL body | `public void initialize() { }` |
| H_FALLBACK | SPARQL catch | `catch (Exception e) { return fake; }` |
| H_LIE | SPARQL semantic | `@return never null` but `return null;` |
| H_SILENT | Regex log | `log.error("not implemented")` |

### Core Components

```
HyperStandardsValidator (orchestrator)
    ↓ manages 7 checkers
├── RegexGuardChecker (H_TODO, H_MOCK, H_SILENT)
│   └── GuardChecker interface
├── SparqlGuardChecker (H_STUB, H_EMPTY, H_FALLBACK, H_LIE)
│   ├── SparqlQueryLoader
│   ├── SparqlExecutor
│   └── SparqlResultMapper
├── JavaAstParser (tree-sitter wrapper)
├── GuardRdfConverter (AST → RDF)
└── GuardReceiptWriter (JSON output)
```

---

## Implementation Timeline (3 Days)

### Day 1: Foundations (4 hours)
| Phase | Task | Owner | Time |
|-------|------|-------|------|
| 1 | Core interfaces & models | Engineer A | 1h |
| 2 | AST parser & RDF converter | Engineer B | 1.5h |
| 3a | Regex checkers | Engineer A | 1.5h |

### Day 2: Implementation (4.25 hours)
| Phase | Task | Owner | Time |
|-------|------|-------|------|
| 3b | SPARQL checkers | Engineer B | 2h |
| 4 | Orchestrator & CLI | Engineer A | 1h |
| 5 | Receipt & JSON output | Engineer A | 0.75h |
| 6 | Configuration files | Engineer B | 0.5h |

### Day 3: Quality Assurance (5 hours)
| Phase | Task | Owner | Time |
|-------|------|-------|------|
| 7 | Test fixtures | Tester C | 2h |
| 8 | Unit tests | Tester C | 2h |
| 9 | Documentation | Engineer A | 1h |

---

## Key Design Decisions

1. **Hybrid Detection**: Regex (fast) + SPARQL (semantic)
2. **AST-Based**: Tree-sitter parsing (not line-by-line regex)
3. **RDF Layer**: SPARQL queries on structured RDF facts
4. **Pluggable Checkers**: GuardChecker interface enables extension
5. **100% Accuracy**: No trade-off between true positives and false positives
6. **Exit Code Contract**: 0 (GREEN), 1 (transient), 2 (RED)

---

## Success Criteria

- [x] All 7 patterns designed
- [x] 100% true positive rate required
- [x] 0% false positive rate required
- [ ] <5 seconds per file (implementation TBD)
- [ ] All 52 files created (implementation TBD)
- [ ] >90% code coverage (testing TBD)
- [ ] JSON receipt validation working (testing TBD)
- [ ] CLI integration complete (testing TBD)

---

## File Locations

**Specification Documents**:
```
/home/user/yawl/.claude/plans/
├── H-GUARDS-ARCHITECTURE.md             (MAIN)
├── H-GUARDS-QUICK-START.md              (QUICK REF)
├── H-GUARDS-CONTRACT-REFERENCE.md       (CODING GUIDE)
├── H-GUARDS-IMPLEMENTATION-SUMMARY.md   (OVERVIEW)
└── README.md                             (THIS FILE)

/home/user/yawl/.claude/rules/validation-phases/
├── H-GUARDS-DESIGN.md
├── H-GUARDS-IMPLEMENTATION.md
└── H-GUARDS-QUERIES.md
```

**Implementation Location**:
```
/home/user/yawl/yawl-ggen/
├── src/main/java/org/yawlfoundation/yawl/ggen/validation/guards/
│   ├── (22 Java source files)
├── src/main/resources/
│   ├── sparql/guards/ (7 SPARQL files)
│   ├── config/ (guard-config.toml)
│   └── schema/ (guard-receipt-schema.json)
└── src/test/
    ├── java/org/yawlfoundation/yawl/ggen/validation/guards/ (4 test classes)
    └── resources/fixtures/ (9 Java fixtures)
```

---

## How to Use These Documents

### For Project Leads
1. Read IMPLEMENTATION-SUMMARY (10 min)
2. Review timeline and team assignments
3. Track progress against phase checklist in QUICK-START
4. Escalate any blockers

### For Architects
1. Read ARCHITECTURE (60 min) — complete system design
2. Review interface contracts in CONTRACT-REFERENCE
3. Approve Phase 1 deliverables before proceeding to Phase 2

### For Engineers (Coding Phase)
1. Read assigned phases in QUICK-START
2. Keep CONTRACT-REFERENCE open while coding
3. Refer to ARCHITECTURE for detailed specs
4. Test against acceptance criteria in QUICK-START

### For QA/Testers
1. Read Phase 7-8 in QUICK-START
2. Review test requirements in CONTRACT-REFERENCE
3. Create fixtures following Phase 7 checklist
4. Write tests per Phase 8 checklist
5. Achieve >90% code coverage

---

## Integration Points

### CLI Entry
```bash
ggen validate --phase guards --emit <dir> --receipt-file <path> --verbose
```

### Exit Codes
```
0 = GREEN (no violations, proceed to Q phase)
1 = Transient error (retry safe)
2 = RED (violations found, fix required)
```

### Receipt Location
```
.claude/receipts/guard-receipt.json
```

### Configuration
```
yawl-ggen/src/main/resources/config/guard-config.toml
```

---

## Key Contracts

### GuardChecker Interface
- Deterministic: same input → same output
- Idempotent: no state changes
- Fail fast: exceptions propagate
- Ordered: violations sorted by line number

### GuardViolation Record
- Immutable (record enforces)
- Validation in constructor
- 1-indexed line numbers
- Absolute file paths

### GuardReceipt Record
- Status: GREEN or RED
- Invariant: RED ⟺ violations.size() > 0
- Summary: counts match violation list
- Timestamp: UTC ISO-8601

### Exit Codes
- 0 = GREEN (success)
- 1 = Transient error (retry)
- 2 = RED (fix required)

---

## Common Questions

**Q: Can we start coding before Phase 1 is done?**
A: No. Phase 1 defines contracts that Phases 2-9 depend on. Phase 1 completion gates all other phases.

**Q: What if we find a flaw in the architecture?**
A: Document it in this file or in ARCHITECTURE.md. Create an issue. Discuss with team lead before proceeding.

**Q: How do we handle discovered edge cases?**
A: Add to edge-cases.java test fixture. Update pattern detection if needed. Document in CONTRACT-REFERENCE.

**Q: Can we parallelize Days 1-2 more?**
A: Phases 1-2 have dependencies. Phases 3a and 3b can run parallel on Day 1-2. Phase 4-6 depend on Phase 3 completion.

**Q: What if a pattern is too noisy (too many false positives)?**
A: Cannot disable — violates 0% FP requirement. Must refine pattern. Add to edge case tests.

---

## Support & Escalation

**Issues with Architecture**: Comment in ARCHITECTURE.md or create issue
**Questions on Contracts**: Check CONTRACT-REFERENCE.md first
**Implementation Blockers**: Escalate to team lead
**Phase Completion**: Mark complete in QUICK-START checklist and commit

---

## Metrics & Tracking

### Progress Tracking
- **Daily**: Update phase completion in QUICK-START checklist
- **End of Phase**: Code review, merge, sign-off
- **End of Day**: Team standup, blockers discussion
- **End of Day 3**: Final sign-off, celebrate! 🎉

### Quality Metrics
- **Code Coverage**: Target >90%
- **Test Pass Rate**: Target 100%
- **Pattern Detection**: 100% for all 7 patterns
- **False Positive Rate**: 0%
- **Processing Time**: <5s per file (median)

### Success Definition
All 9 phases complete + all tests pass + >90% coverage + zero false positives

---

## Next Steps

1. **Kickoff**: Team reviews IMPLEMENTATION-SUMMARY (10 min)
2. **Architecture Review**: Architect reviews ARCHITECTURE.md with team (30 min)
3. **Phase 1 Start**: Engineer A begins GuardChecker interface
4. **Phase 2 Start**: Engineer B prepares AST parser code
5. **Daily Standup**: 15 min, track progress
6. **Phase Reviews**: Each phase completion → code review → merge
7. **Integration Test**: After Day 2, test with real generated code
8. **Final Sign-off**: Day 3 EOD, architecture review confirms readiness

---

## Version History

| Version | Date | Status | Notes |
|---------|------|--------|-------|
| 1.0 | 2026-02-28 | DRAFT | Initial architecture design |
| (impl) | TBD | IN PROGRESS | Implementation phase TBD |
| (final) | TBD | READY | Ready for v6.0.0 release |

---

**Status**: ARCHITECTURE COMPLETE ✓
**Next**: PHASE 1 IMPLEMENTATION
**Target Completion**: Day 3 by 5:00 PM

---

## Document Versions

- **IMPLEMENTATION-SUMMARY**: v1.0 (2026-02-28)
- **ARCHITECTURE**: v1.0 (2026-02-28)
- **CONTRACT-REFERENCE**: v1.0 (2026-02-28)
- **QUICK-START**: v1.0 (2026-02-28)
- **README**: v1.0 (2026-02-28)

All documents ready for team distribution and implementation kickoff.

---

**GODSPEED.** ✈️

*Compile ≺ Test ≺ Validate ≺ Deploy*
