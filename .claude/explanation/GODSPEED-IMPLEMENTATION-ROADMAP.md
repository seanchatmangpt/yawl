# GODSPEED-ggen Implementation Roadmap

**Purpose**: Detailed execution plan for 4-engineer team implementing ggen-godspeed CLI.

**Timeline**: 4 weeks (parallel where possible)

**Outcome**: Executable GODSPEED methodology circuit (Ψ→Λ→H→Q→Ω) with full audit trail + drift detection.

---

## Week 1: Foundation (Parallel)

### Task: CLI + Session Tracking + Receipt Framework

**Owners**: Lead Engineer (orchestration) + all team members (test infrastructure)

**Deliverables**:

1. **CLI Entry Point** (`ggen-godspeed` binary)
   ```bash
   ggen-godspeed --config ggen.toml [--phases ...]
   ggen-godspeed --phase observation
   ggen-godspeed --list-phases
   ggen-godspeed --help
   ```
   **Acceptance**: Binary builds, runs with --help, parses config file.

2. **Session Tracking**
   - Read `CLAUDE_SESSION_ID` env var
   - If missing, generate one (uuidgen-based)
   - Store in `receipts/godspeed/` directory
   - Track in main receipt JSON
   **Acceptance**: Receipt includes session_id, timestamp, duration.

3. **Receipt Framework**
   - Define Receipt struct (JSON serializable)
   - Implement PhaseOutput struct (artifacts, violations, duration)
   - Create receipt storage (append-only to `receipts/godspeed/`)
   - Implement SHA256 hashing for content
   **Acceptance**: Each phase can emit a receipt. Main receipt aggregates all 5.

4. **Configuration Loading**
   - Parse ggen.toml [godspeed] section
   - Validate required fields (phases, emit_channel)
   - Fallback to defaults if missing
   - Report validation errors clearly
   **Acceptance**: Can load complex config, report all errors.

5. **Error Handling + Exit Codes**
   - Exit 0: All phases GREEN
   - Exit 1: Phase RED (execution failed)
   - Exit 2: Phase BLOCKED (violations found)
   - Detailed error messages for each scenario
   **Acceptance**: CLI produces correct exit code + error text for all scenarios.

6. **Test Infrastructure**
   - Unit tests for CLI parsing
   - Unit tests for Receipt serialization
   - Unit tests for config validation
   - Mock/stub phase implementations for early testing
   **Acceptance**: >80% test coverage for core modules.

**Code Structure** (Rust, in `ggen/crates/godspeed/`):
```
src/
├─ lib.rs                      # Main crate (GodspeedPhase trait)
├─ cli.rs                      # Argument parsing + main loop
├─ config.rs                   # ggen.toml loading + validation
├─ receipt.rs                  # Receipt struct + JSON serialization
├─ session.rs                  # Session ID management
├─ phases/
│  ├─ mod.rs                   # Phase trait definition
│  ├─ observation.rs           # Ψ phase (stub)
│  ├─ build.rs                 # Λ phase (stub)
│  ├─ guards.rs                # H phase (stub)
│  ├─ invariants.rs            # Q phase (stub)
│  └─ git.rs                   # Ω phase (stub)
└─ tests/
   ├─ test_cli.rs
   ├─ test_config.rs
   └─ test_receipt.rs
```

**Definition of Done**:
- [ ] `ggen-godspeed --help` works
- [ ] `ggen-godspeed --config ggen.toml --dry-run` prints phase order
- [ ] Receipt JSON matches spec (session_id, phases, drift_report)
- [ ] Exit codes: 0/1/2 correctly returned
- [ ] Unit tests pass (>80% coverage)
- [ ] Can build + run in CI

---

## Week 2: Ψ (Observatory) + Λ (Build) Phases

### Task 2a: Engineer A — Ψ (Observatory) Phase

**Deliverables**:

1. **Load Facts from RDF**
   - Execute `bash scripts/observatory/observatory.sh`
   - Parse facts.json output
   - Extract entities (modules, tests, coverage, etc.)
   **Acceptance**: Facts loaded correctly into memory, structured JSON validated.

2. **Drift Detection (SHA256)**
   - Compute SHA256 hash of facts.json content
   - Load previous Ψ receipt (if exists)
   - Compare current hash vs previous hash
   - Report: "Drift detected: 3 files changed" or "Facts fresh"
   **Acceptance**: Detects hash mismatches, reports correctly.

3. **Ψ Receipt Emission**
   - Create Ψ-receipt-<sessionId>.json
   - Include: status (GREEN/RED), facts_hash, checksum_matches, drift_detected
   - Store in receipts/godspeed/
   - Emit to main receipt
   **Acceptance**: Receipt file exists, JSON valid, hashes correct.

4. **Halt on Drift**
   - If hash mismatch → exit 1
   - Error message: "DRIFT DETECTED: facts changed. Run: bash scripts/observatory/observatory.sh"
   - Print drift report (files changed, hash delta)
   **Acceptance**: Halts correctly on hash mismatch, error message clear.

5. **Integration Test**
   - Mock facts.json
   - Verify hash computation
   - Verify previous receipt comparison
   - Verify halt on drift
   **Acceptance**: Integration test passes, mocks work.

**Code** (Rust, `src/phases/observation.rs`):
```rust
pub struct ObservationPhase {
    config: GodspeedConfig,
    session_id: String,
}

impl GodspeedPhase for ObservationPhase {
    fn phase_name(&self) -> &str { "observation" }

    fn execute(&mut self) -> Result<PhaseOutput> {
        // 1. Run bash scripts/observatory/observatory.sh
        // 2. Load facts.json
        // 3. Compute SHA256
        // 4. Load previous receipt, compare hash
        // 5. If drift: return Err with message + status=BLOCKED
        // 6. If fresh: continue
        // 7. Create output + receipt
    }
}
```

---

### Task 2b: Engineer B — Λ (Build) Phase

**Deliverables**:

1. **Compile Code via dx.sh**
   - Execute `bash scripts/dx.sh compile`
   - Capture Maven output (stdout + stderr)
   - Parse exit code (0 = success, else failure)
   **Acceptance**: Can run dx.sh, capture output correctly.

2. **Generate Test Stubs (Tera)**
   - Load Tera template: `templates/test-stubs.tera`
   - Query facts.json for module list
   - Generate test/**/*Generated.java files
   - Output to emit_channel
   **Acceptance**: Test stubs generated, files in correct location.

3. **Λ Receipt Emission**
   - Create Λ-receipt-<sessionId>.json
   - Include: status, exit_code, compile_time_ms, modules_compiled, test_count
   - Store in receipts/godspeed/
   **Acceptance**: Receipt file exists, metrics correct.

4. **Halt on Build Failure**
   - If Maven exit ≠ 0 → exit 1
   - Print compilation error (from Maven output)
   - Report which module failed
   **Acceptance**: Halts on RED, error message clear.

5. **Integration Test**
   - Mock Maven output (success + failure cases)
   - Verify metrics capture
   - Verify halt on failure
   **Acceptance**: Integration test passes.

**Code** (Rust, `src/phases/build.rs`):
```rust
pub struct BuildPhase {
    config: GodspeedConfig,
    session_id: String,
}

impl GodspeedPhase for BuildPhase {
    fn execute(&mut self) -> Result<PhaseOutput> {
        // 1. Run bash scripts/dx.sh compile
        // 2. Parse Maven output (exit code)
        // 3. If exit != 0: return Err(RED)
        // 4. Load facts.json (modules)
        // 5. Generate test stubs from Tera
        // 6. Output to emit_channel
        // 7. Create receipt + metrics
    }
}
```

---

## Week 3: H (Guards) + Q (Invariants) Phases

### Task 3a: Engineer C — H (Guards) Phase

**Deliverables**:

1. **PokayokeRuleEngine Implementation**
   - Define GuardRule struct: { name, regex, description, severity }
   - Load 14 guard rules from config
   - Implement regex matching on code
   **Acceptance**: Can match TODO, mock, stub patterns in code.

2. **SPARQL Query Integration**
   - Execute SPARQL queries on compiled code graph
   - Query: "Find all TODO | mock | stub | fake | empty_return | lie patterns"
   - Parse SPARQL results (violations)
   **Acceptance**: Can query RDF endpoint, parse results.

3. **Guard Pattern Detection**
   - Run hyper-validate.sh hook on src/ files
   - Capture violations (file, line, pattern)
   - Aggregate violations into guard-violations.json
   **Acceptance**: Violations captured correctly.

4. **H Receipt Emission**
   - Create H-receipt-<sessionId>.json
   - Include: status, violations (array), patterns_checked (count)
   - Store in receipts/godspeed/
   **Acceptance**: Receipt file exists, violation count correct.

5. **Halt on Violations**
   - If guard-violations.json length > 0 → exit 2 (BLOCKED)
   - Print each violation: file, line, pattern, description
   - Print: "Fix forbidden patterns and retry"
   **Acceptance**: Halts on violations, error messages clear.

6. **Integration Test**
   - Inject TODO into test code
   - Run H phase, verify detection
   - Remove TODO, run H phase, verify PASS
   **Acceptance**: Integration test passes.

**Code** (Rust, `src/phases/guards.rs`):
```rust
pub struct GuardsPhase {
    engine: PokayokeRuleEngine,
    session_id: String,
}

pub struct PokayokeRuleEngine {
    rules: Vec<GuardRule>,
}

impl GodspeedPhase for GuardsPhase {
    fn execute(&mut self) -> Result<PhaseOutput> {
        // 1. Load 14 guard rules
        // 2. Run hyper-validate.sh on src/
        // 3. Execute SPARQL guard queries
        // 4. Aggregate violations
        // 5. If violations.len() > 0: return Err(BLOCKED)
        // 6. Create receipt
    }
}
```

---

### Task 3b: Engineer D — Q (Invariants) Phase

**Deliverables**:

1. **SHACL Validator Implementation**
   - Load invariants.ttl (SHACL shapes)
   - Validate code graph against shapes
   - Parse validation results (violations)
   **Acceptance**: Can load SHACL shapes, validate graph.

2. **Invariant Queries (Real Impl ∨ Throw)**
   - SPARQL: Find all methods without real impl AND without throw
   - SPARQL: Find @Mock in src/ (not test/)
   - SPARQL: Find silent exception swallowing
   - Aggregate into invariant-violations.json
   **Acceptance**: Can query invariant patterns, parse results.

3. **Q Receipt Emission**
   - Create Q-receipt-<sessionId>.json
   - Include: status, violations, methods_validated, real_impl_count, throw_count
   - Store in receipts/godspeed/
   **Acceptance**: Receipt file exists, metrics correct.

4. **Halt on Violations**
   - If invariant-violations.json length > 0 → exit 2 (BLOCKED)
   - Print each violation: method, reason (no impl, no throw)
   - Print: "Implement real logic or throw UnsupportedOperationException"
   **Acceptance**: Halts on violations, error messages clear.

5. **Integration Test**
   - Create method with no impl, no throw
   - Run Q phase, verify detection
   - Add throw UnsupportedOperationException, re-run, verify PASS
   **Acceptance**: Integration test passes.

**Code** (Rust, `src/phases/invariants.rs`):
```rust
pub struct InvariantsPhase {
    validator: SHACLValidator,
    session_id: String,
}

pub struct SHACLValidator {
    shapes_graph: RdfGraph,
}

impl GodspeedPhase for InvariantsPhase {
    fn execute(&mut self) -> Result<PhaseOutput> {
        // 1. Load invariants.ttl (SHACL shapes)
        // 2. Execute SPARQL invariant queries
        // 3. Aggregate violations
        // 4. If violations.len() > 0: return Err(BLOCKED)
        // 5. Create receipt
    }
}
```

---

## Week 4: Ω (Git) Phase + Integration Testing + Delivery

### Task 4a: Engineer D (continued) — Ω (Git) Phase

**Deliverables**:

1. **Emit Channel Stage**
   - Read emit_channel from config (["src/", "test/", ".claude/", "generated/"])
   - Collect all modified files in emit_channel
   - Run `git add <files>` (not `git add .`)
   **Acceptance**: Stages only files in emit_channel, verified via `git status`.

2. **Atomic Commit**
   - Create commit message: "GODSPEED PASS: Ψ→Λ→H→Q from {session_id}"
   - Run `git commit -m "..."`
   - Parse commit hash from output
   **Acceptance**: Commit created with correct message + hash.

3. **Git Push**
   - Compute branch name: "claude/<description>-<sessionId>"
   - Run `git push -u origin <branch>`
   - Handle push failures (branch conflicts, permission errors)
   **Acceptance**: Can push successfully, handle common errors.

4. **Ω Receipt Emission**
   - Create Ω-receipt-<sessionId>.json
   - Include: status, commit_hash, files_staged, branch, push_success
   - Store in receipts/godspeed/
   **Acceptance**: Receipt file exists, commit hash correct.

5. **Integration Test**
   - Create test file in src/
   - Run full Ψ→Λ→H→Q→Ω circuit
   - Verify commit created + pushed
   - Verify receipt chain
   **Acceptance**: Full circuit passes.

**Code** (Rust, `src/phases/git.rs`):
```rust
pub struct GitPhase {
    config: GodspeedConfig,
    session_id: String,
}

impl GodspeedPhase for GitPhase {
    fn execute(&mut self) -> Result<PhaseOutput> {
        // 1. Collect files in emit_channel
        // 2. git add <files>
        // 3. git commit -m "GODSPEED PASS: ..."
        // 4. git push -u origin <branch>
        // 5. Parse commit hash
        // 6. Create receipt
    }
}
```

---

### Task 4b: Full Integration Testing (All Engineers)

**Deliverables**:

1. **End-to-End Circuit Test**
   - Create test YAWL codebase (yawl-test/)
   - Run full ggen-godspeed circuit (Ψ→Λ→H→Q→Ω)
   - Verify receipt chain (all 5 receipts + main receipt)
   - Verify commit created + pushed
   **Acceptance**: E2E test passes, all receipts valid.

2. **Negative Testing**
   - Inject TODO, run H phase, verify blocking ✓
   - Inject mock class, run H phase, verify blocking ✓
   - Inject method with no impl/no throw, run Q phase, verify blocking ✓
   - Simulate build failure, run Λ phase, verify halting ✓
   - Simulate git push failure, run Ω phase, verify error handling ✓
   **Acceptance**: All negative tests pass.

3. **Drift Detection Test**
   - Run Ψ phase (create facts.json + receipt)
   - Modify facts.json
   - Run Ψ phase again (hash mismatch detected)
   - Verify halt + error message
   **Acceptance**: Drift detection works correctly.

4. **Phase Independence Test**
   - Run Ψ alone ✓
   - Run Λ alone (assumes Ψ output) ✓
   - Run H alone (assumes Λ output) ✓
   - Run Q alone (assumes H passed) ✓
   - Run Ω alone (assumes Q passed) ✓
   **Acceptance**: Each phase can run independently.

5. **CI/CD Integration**
   - Add ggen-godspeed to CI pipeline
   - Run circuit on every commit
   - Report success/failure in CI logs
   - Archive receipts in CI artifacts
   **Acceptance**: CI integration works, receipts archived.

---

### Task 4c: Documentation + Delivery

**Deliverables**:

1. **API Documentation**
   - Trait definitions (GodspeedPhase, PhaseOutput, Receipt)
   - Error types + exit codes
   - Configuration schema (ggen.toml [godspeed])
   **Acceptance**: Docs complete, examples provided.

2. **User Guide**
   - How to run ggen-godspeed
   - How to configure [godspeed] section
   - Common errors + fixes
   - Example workflows
   **Acceptance**: Guide clear, examples work.

3. **Implementation Notes**
   - Architectural decisions + rationale
   - Known limitations
   - Future improvements
   **Acceptance**: Notes recorded for next iteration.

4. **Delivery Package**
   - Binary: `ggen-godspeed` (ready to use)
   - Docs: API reference + user guide
   - Tests: Full test suite (>85% coverage)
   - Example: Example ggen.toml + godspeed.ttl
   **Acceptance**: Package complete, all components present.

---

## Parallel Work (Weeks 1-4)

**Quality Assurance** (Run continuously):
- Unit tests (all engineers, daily)
- Integration tests (Lead, 3x/week)
- Documentation review (Tech lead, weekly)
- Architecture alignment (Lead, daily standup)

**Risk Management**:
- SPARQL endpoint availability → Fallback to hardcoded patterns
- RDF graph size → Implement streaming + caching
- Maven offline mode → Use DX_OFFLINE flag

---

## Success Criteria (Week 4 Delivery)

### Functionality
- [ ] All 5 phases implemented + tested
- [ ] CLI builds + runs with correct exit codes
- [ ] Receipt chain complete (5 receipts + main receipt)
- [ ] Drift detection works correctly
- [ ] Each phase independently testable

### Quality
- [ ] >85% test coverage
- [ ] All integration tests pass
- [ ] E2E circuit test passes (Ψ→Λ→H→Q→Ω)
- [ ] No memory leaks (valgrind clean)
- [ ] Performance: <300ms total overhead per circuit

### Documentation
- [ ] API docs complete
- [ ] User guide with examples
- [ ] Implementation notes recorded
- [ ] README updated

### Delivery
- [ ] Binary ready for production
- [ ] All tests passing in CI
- [ ] Receipts archived + accessible
- [ ] No breaking changes to existing ggen functionality

---

## Daily Standup Format (Week 1-4)

```
[Engineer Name] Status:
├─ Completed: [feature] + [test count] new tests
├─ In Progress: [feature] ([% complete])
├─ Blockers: [issue] → [owner] → [ETA]
└─ Today's Focus: [next 4h of work]
```

**Decision-making**: Architecture questions → Lead. Config/spec questions → Architect. Code reviews → Peer.

---

## Definition of Done (Each Task)

- [ ] Code written + reviewed
- [ ] All unit tests pass (>80% coverage for module)
- [ ] Integration test with adjacent phases
- [ ] Documentation (docstrings + API doc)
- [ ] E2E test includes this phase
- [ ] Receipt emitted correctly
- [ ] Error handling complete (no panics)
- [ ] Performance acceptable (<50ms for this phase)

---

## Risk Matrix

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| SPARQL endpoint unavailable | Medium | High | Fallback to regex patterns, cache SPARQL results |
| RDF graph too large | Low | Medium | Stream processing, lazy loading |
| Maven offline mode | Low | Medium | Use DX_OFFLINE env var |
| Receipt schema evolution | Low | Low | Version receipts, migration script |
| Git push conflicts | Medium | Low | Detect, report error, user resolves |

---

## Deliverables Checklist

**Week 1**:
- [ ] CLI binary builds + runs
- [ ] Session ID tracking works
- [ ] Receipt framework complete
- [ ] Config loading + validation
- [ ] Unit tests >80%

**Week 2**:
- [ ] Ψ phase: facts loaded, drift detected
- [ ] Λ phase: compile works, test stubs generated
- [ ] Both phases integrated into main circuit
- [ ] Integration tests pass

**Week 3**:
- [ ] H phase: guard patterns detected
- [ ] Q phase: invariant rules enforced
- [ ] Both phases integrated + tested
- [ ] Negative tests (inject violations, verify blocking)

**Week 4**:
- [ ] Ω phase: atomic commit + push
- [ ] Full E2E circuit test (Ψ→Λ→H→Q→Ω)
- [ ] Drift detection test
- [ ] Phase independence test
- [ ] Documentation complete
- [ ] CI/CD integration

---

## Communication Channels

- **Slack**: #godspeed-ggen (daily standup, quick questions)
- **GitHub Issues**: Technical decisions, blockers
- **Weekly Sync**: (Fridays 3pm) - review progress, plan next week
- **Architecture Review**: (Wednesdays 2pm) - design discussions

---

**Roadmap Version**: 1.0
**Date**: 2026-02-21
**Status**: Ready for Implementation
