# GODSPEED-ggen Quick Reference

**One-pager for developers implementing GODSPEED-ggen integration.**

---

## The 5-Phase Circuit

```
Ψ → Λ → H → Q → Ω
```

| Phase | Name | Executes | Output | Halts If |
|-------|------|----------|--------|----------|
| **Ψ** | Observatory | `scripts/observatory/observatory.sh` | facts.json + SHA256 | Facts stale (hash mismatch) |
| **Λ** | Build | `bash scripts/dx.sh compile` | compiled JARs | Build RED (exit ≠ 0) |
| **H** | Guards | `hyper-validate.sh` + SPARQL | guard-violations.json | Violations found (length > 0) |
| **Q** | Invariants | SHACL shapes + SPARQL | invariant-violations.json | Violations found (length > 0) |
| **Ω** | Git | `git add` + `git commit` | commit hash | Git push fails |

---

## Guard Patterns (H Gate) — Forbidden in Code

```
❌ TODO, FIXME, XXX, HACK, LATER           (deferred work)
❌ class MockXxx, class FakeXxx            (mock classes, test/ only)
❌ return "";                              (empty string stub)
❌ return null; // TODO                    (null with stub comment)
❌ public void method() { }                (empty method)
❌ DUMMY_*, PLACEHOLDER_*                  (placeholder constants)
❌ catch (E) { return mock(); }            (silent fallback)
❌ if (...) return mockData();             (conditional mock)
❌ .getOrDefault(k, "test_value")          (fake default)
❌ mockFetch(), stubValidate()             (mock method names)
❌ useMockData = true;                     (mock mode flag)
❌ return demoResponse();                  (demo return)
❌ @Deprecated … return                    (deprecated code returning)
❌ // Lie (comment ≠ behavior)             (misleading comment)
```

**Detection**: Regex patterns via hyper-validate.sh + SPARQL queries.

**Enforcement**: If ANY pattern found → block commit, exit 2.

---

## Invariant Rules (Q Gate) — Enforce Real Implementation

**Core Rule**: Every method must be EITHER:
- **Real**: Does actual work (computes, stores, mutates, etc.)
- **Throws**: `throw new UnsupportedOperationException();`

```sparql
# SPARQL Query: Find all violations
SELECT ?method WHERE {
  ?method a yawl:Method .
  FILTER NOT EXISTS { ?method yawl:hasRealImplementation ?x }
  FILTER NOT EXISTS { ?method yawl:throwsException ?x }
}
```

**Sub-rules**:
1. No @Mock in src/ (only in test/)
2. No silent catch (must throw or propagate)
3. No fake data returns
4. No conditional fallbacks

**Detection**: SHACL shapes from invariants.ttl + SPARQL validation.

**Enforcement**: If ANY violation found → block commit, exit 2.

---

## Configuration: ggen.toml (GODSPEED Section)

```toml
[godspeed]
enabled = true
phases = ["observation", "build", "guards", "invariants", "git"]
fail_fast = true
emit_channel = ["src/", "test/", ".claude/", "generated/"]

[observation]
scripts = ["scripts/observatory/observatory.sh"]
output = ".specify/facts/"
verify_checksums = true

[build]
command = "bash scripts/dx.sh compile"
modules_from_facts = true
require_success = true

[guards]
enabled = true
validator_script = ".claude/hooks/hyper-validate.sh"
rules = ["no_TODO", "no_mock_class", "no_empty_return", ...]

[invariants]
enabled = true
shapes_file = ".specify/invariants.ttl"
validation_queries = ["queries/real-impl-or-throw.rq", ...]

[git]
enabled = true
branch_prefix = "claude/"
message_format = "GODSPEED PASS: Ψ→Λ→H→Q from {session_id}"
```

---

## Receipt Structure (Durable Audit Trail)

**Main Receipt**: `receipts/godspeed-<sessionId>.json`

```json
{
  "session_id": "claude-20260220-abc123",
  "circuit": "Ψ→Λ→H→Q→Ω",
  "started_at": "2026-02-20T14:32:00Z",
  "ended_at": "2026-02-20T14:35:45Z",
  "total_duration_ms": 225000,
  "phases": {
    "Ψ": {
      "status": "GREEN",
      "facts_hash": "abc123def456",
      "checksum_matches": true,
      "drift_detected": false
    },
    "Λ": {
      "status": "GREEN",
      "exit_code": 0,
      "compile_time_ms": 45000,
      "modules_compiled": 12
    },
    "H": {
      "status": "GREEN",
      "violations": [],
      "patterns_checked": 14
    },
    "Q": {
      "status": "GREEN",
      "violations": [],
      "methods_validated": 892,
      "real_impl_count": 887,
      "throw_count": 5
    },
    "Ω": {
      "status": "GREEN",
      "commit_hash": "def456abc",
      "files_staged": 23,
      "branch": "claude/fix-deadlock-abc123"
    }
  },
  "drift_report": {
    "detected": false,
    "previous_hash": "xyz789",
    "current_hash": "xyz789"
  }
}
```

---

## Drift Detection (Σ → 0 Principle)

**What it means**: Code state = facts state. No surprise divergences.

**How it works**:
1. Ψ phase compares facts.json SHA256 vs previous receipt
2. If hash matches → facts fresh ✓
3. If hash differs → facts stale → HALT phase Ψ
4. Error message: "DRIFT DETECTED: facts changed. Run: bash scripts/observatory/observatory.sh"

**Example**:
```bash
# Previous run (week 1)
godspeed-circuit → Ψ-receipt: facts_hash = "abc123..."
→ Stored in receipts/godspeed-<old-sessionId>.json

# Current run (week 2)
ggen-godspeed --phase observation
→ Loads facts.json, computes SHA256 = "xyz789..."
→ Compares to previous: "abc123..." ≠ "xyz789..."
→ DRIFT DETECTED: 3 files changed
→ HALT Ψ phase, exit 1
→ Require: bash scripts/observatory/observatory.sh
```

---

## Exit Codes

| Exit | Meaning | Action |
|------|---------|--------|
| **0** | ✓ All phases GREEN, commit pushed | Success. Check receipt chain. |
| **1** | ✗ Phase RED (build failed, git push failed, etc) | Read error logs, fix code, retry. |
| **2** | ✗ Phase BLOCKED (H/Q violations found) | Fix forbidden patterns, re-run circuit. |

---

## Common Errors & Fixes

### Error: "DRIFT DETECTED: facts changed"
```bash
# Cause: facts.json SHA256 ≠ previous receipt
# Fix:
bash scripts/observatory/observatory.sh
git add .specify/facts/
git commit -m "Update facts"
# Then retry GODSPEED circuit
```

### Error: "BUILD FAILED"
```bash
# Cause: Maven compilation error
# Fix:
bash scripts/dx.sh -pl <module>  # Identify failing module
# Fix code
bash scripts/dx.sh all           # Verify all green
# Then retry GODSPEED circuit
```

### Error: "GUARD VIOLATION: TODO at line 427"
```bash
# Cause: Found TODO/mock/stub/fake/lie in code
# Fix: Implement real logic
public void advance() {
    // Real implementation here (not TODO)
    ...
}
# Then retry GODSPEED circuit
```

### Error: "INVARIANT VIOLATION: Method without real impl and without throw"
```bash
# Cause: Method has no implementation AND doesn't throw
# Fix: Either implement real logic OR throw:
public void notImplemented() {
    throw new UnsupportedOperationException("Not yet implemented");
}
# Then retry GODSPEED circuit
```

---

## Integration with YAWL Hooks

**SessionStart**: Set session ID
```bash
export CLAUDE_SESSION_ID="claude-$(date +%s)-$(uuidgen | cut -c1-8)"
```

**PreToolUse** (before code write): Run Ψ phase
```bash
ggen-godspeed --phase observation --config ggen.toml
```

**PostToolUse** (after code write): Run H phase
```bash
ggen-godspeed --phase guards --config ggen.toml
```

**PreCommit** (before push): Run full circuit
```bash
ggen-godspeed --phases observation,build,guards,invariants,git --config ggen.toml
```

---

## Testing Individual Phases

```bash
# Test Ψ alone (facts + drift detection)
ggen-godspeed --phase observation --config ggen.toml

# Test Λ alone (compile)
ggen-godspeed --phase build --config ggen.toml

# Test H alone (guard patterns)
ggen-godspeed --phase guards --config ggen.toml

# Test Q alone (invariants)
ggen-godspeed --phase invariants --config ggen.toml

# Test Ω alone (git commit)
ggen-godspeed --phase git --config ggen.toml

# Full circuit
ggen-godspeed --phases observation,build,guards,invariants,git --config ggen.toml
```

---

## Receipt Chain (Audit Trail)

Every GODSPEED circuit run produces:

```
receipts/godspeed/
├─ godspeed-claude-20260220-abc123.json    (main receipt)
├─ observation-receipt-abc123.json         (Ψ phase details)
├─ build-receipt-abc123.json               (Λ phase details)
├─ guards-receipt-abc123.json              (H phase details)
├─ invariants-receipt-abc123.json          (Q phase details)
├─ git-receipt-abc123.json                 (Ω phase details)
└─ drift-report-abc123.json                (Drift detection report)
```

**Use cases**:
- Audit: Who ran what, when, with what results?
- Rollback: What was the state before this circuit run?
- Debugging: What violations were found?
- Compliance: Prove code passed all gates before deploy.

---

## Key Files to Understand

| File | Purpose |
|------|---------|
| `ggen.toml` | Main configuration (ontologies, phases, rules) |
| `.specify/yawl-ontology.ttl` | RDF ontology (source of truth for facts) |
| `.specify/invariants.ttl` | SHACL shapes (Q invariant rules) |
| `.claude/hooks/hyper-validate.sh` | Guard pattern detection (H gate) |
| `scripts/observatory/observatory.sh` | Fact discovery (Ψ phase) |
| `scripts/dx.sh` | Build command (Λ phase) |
| `receipts/godspeed/` | Receipt storage (durable audit trail) |

---

## Glossary

| Term | Definition |
|------|-----------|
| **Ψ (Psi)** | Observatory phase: observe facts from codebase |
| **Λ (Lambda)** | Build phase: compile code |
| **H (Guards)** | Guard patterns: forbidden anti-patterns (TODO, mock, etc.) |
| **Q (Invariants)** | Invariant rules: real impl ∨ throw |
| **Ω (Omega)** | Git phase: atomic commit + push |
| **Receipt** | Durable JSON record of phase execution (SHA256-signed) |
| **Drift (Σ)** | Code divergence from design (detected via hash mismatch) |
| **Emit Channel** | Git files to stage (src/, test/, .claude/, generated/) |
| **Gate** | Phase validation checkpoint (blocks if RED/BLOCKED) |
| **SPARQL** | RDF query language (for finding patterns in ontology) |
| **SHACL** | RDF constraint language (for validating invariants) |

---

## For Implementation Teams

**Engineer A** (Ψ phase): Load observatory.sh output, integrate facts freshness check, emit receipt.

**Engineer B** (Λ phase): Wrap dx.sh, capture Maven output, generate test stubs from Tera, emit receipt.

**Engineer C** (H phase): Integrate hyper-validate.sh + SPARQL queries, report violations, emit receipt.

**Engineer D** (Q phase): Load SHACL shapes, execute SPARQL validation, report invariant violations, emit receipt.

**Engineer E** (Ω phase): Implement git add (emit_channel only), commit message format, push logic, emit receipt.

**Lead**: Orchestrate 5 phases in order, aggregate receipts, drift detection, error recovery.

---

## Quick Start

```bash
# 1. Create ggen.toml with [godspeed] section
cp ggen.toml ggen.toml.bak
# (add sections above)

# 2. Create godspeed.ttl ontology
touch .specify/godspeed.ttl
# (populate with guard + invariant definitions)

# 3. Run full circuit
ggen-godspeed --config ggen.toml

# 4. Check receipt
jq . receipts/godspeed-<sessionId>.json

# 5. If any phase RED/BLOCKED, fix + retry
# (circuit is idempotent, safe to re-run)
```

---

**Document**: GODSPEED Quick Reference
**Version**: 1.0
**Date**: 2026-02-21
