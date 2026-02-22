# GODSPEED-ggen Integration Architecture

**Version**: 1.0
**Date**: 2026-02-21
**Status**: Design Phase

---

## Executive Summary

This document describes how **ggen** (ontology-driven code generation) becomes the **executable form of GODSPEED methodology**. Each phase of GODSPEED (Ψ→Λ→H→Q→Ω) maps to a ggen pipeline stage, ensuring zero-drift execution through:

1. **Deterministic code generation** from RDF ontologies + Tera templates
2. **Phase-gated validation** (each phase validates previous outputs)
3. **Guard pattern enforcement** (H = {TODO, mock, stub, fake, lie})
4. **Invariant verification** (Q = real impl ∨ throw UnsupportedOperationException)
5. **Durable state tracking** (receipts + checksums for drift detection)

---

## 1. Integration Model: GODSPEED Phases → ggen Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│ GODSPEED Circuit: Ψ → Λ → H → Q → Ω (Deterministic)           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ ggen Pipeline Stages (Each stage feeds next)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Ψ (Observatory Phase)                                            │
│ ├─ Load/validate facts.json (RDF triple store baseline)        │
│ ├─ Compare SHA256 hashes (detect drift)                        │
│ ├─ Generate facts from .specify/ ontologies                    │
│ └─ Emit observatory.json + checksums                           │
│    ↓                                                             │
│ Λ (Build Phase)                                                 │
│ ├─ Run: bash scripts/dx.sh compile                             │
│ ├─ Validate: All changed modules compile green                 │
│ ├─ Generate test stubs from Tera templates                     │
│ └─ Emit: compiled artifacts + logs                             │
│    ↓ (HALT if build RED)                                        │
│ H (Guards Phase)                                                │
│ ├─ Run hyper-validate.sh (14 guard patterns)                   │
│ ├─ SPARQL query: detect TODO, mock, stub, empty_return        │
│ ├─ Emit: guard-violations.json (empty = PASS)                  │
│ └─ HALT if H ∩ content ≠ ∅                                      │
│    ↓                                                             │
│ Q (Invariants Phase)                                            │
│ ├─ SHACL validator: verify real_impl ∨ throw pattern         │
│ ├─ SPARQL query: detect ¬mock, ¬stub, ¬fallback              │
│ ├─ Emit: invariant-violations.json (empty = PASS)             │
│ └─ HALT if Q violated                                           │
│    ↓                                                             │
│ Ω (Git Phase)                                                   │
│ ├─ Emit all generated artifacts to emit channel                │
│ ├─ git add <specific-files> (never add .)                      │
│ ├─ git commit -m "Deterministic ggen output: [phases]"         │
│ └─ git push -u origin claude/godspeed-<sessionId>              │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Phase Gating (Each gate validates previous gate)

| Phase | Input | Output | Validates | Gate |
|-------|-------|--------|-----------|------|
| **Ψ** | codebase + ontologies | facts.json + checksums | RDF validity, facts freshness | SHA256 match |
| **Λ** | facts.json | compiled JARs + test stubs | Build success, no errors | Maven exit 0 |
| **H** | compiled code | guard-violations.json | No forbidden patterns | H.json empty |
| **Q** | validated code | invariant-violations.json | Real impl or throw | Q.json empty |
| **Ω** | all above GREEN | commit hash | Atomic changes | git push success |

---

## 2. Data Flow: From Config to Committed Code

```
ggen.toml (GODSPEED config)
  ↓
[godspeed] section
├─ phases = ["observation", "build", "guards", "invariants", "git"]
├─ emit_channel = "src/, test/, .claude/"
├─ fact_storage = ".specify/facts/"
└─ receipt_path = "receipts/godspeed/"
  ↓
Ψ Phase: Load & Generate Facts
├─ Input:  .specify/yawl-ontology.ttl + patterns/*.ttl
├─ Query:  RDF CONSTRUCT for observable entities
├─ Output: Ψ.facts/modules.json, gates.json, shared-src.json
├─ Check:  SHA256(facts.json) vs previous receipt
└─ RECEIPT: Ψ-receipt-<sessionId>.json (timestamp, hash, query count)
  ↓ [HALT if SHA256 mismatch = stale facts]
Λ Phase: Compile & Test
├─ Input:  Ψ.facts (modules to compile)
├─ Exec:   bash scripts/dx.sh compile
├─ Gtera:  templates/test-stubs.tera → test/**/*Generated.java
├─ Output: target/yawl-*.jar, test output logs
└─ RECEIPT: Λ-receipt-<sessionId>.json (compile time, module list, exit code)
  ↓ [HALT if exit code ≠ 0]
H Phase: Guard Pattern Detection
├─ Input:  compiled code + hyper-validate.sh + SPARQL rules
├─ Query:  "Find all TODO | mock | stub | fake | empty_return | lie patterns"
├─ Output: guard-violations.json (array of violations or empty [])
├─ Format: [{ file, line, pattern, description }, ...]
└─ RECEIPT: H-receipt-<sessionId>.json (violations found, pattern counts)
  ↓ [HALT if violations.length > 0]
Q Phase: Invariant Verification
├─ Input:  compiled code + SHACL shapes (invariants.ttl)
├─ Query:  "Find methods without real impl AND without throw"
├─ Output: invariant-violations.json (empty = all real impl or throw)
├─ Verify: @Mock not in src/, only in test/
├─ Verify: No silent catch blocks (must propagate or throw)
└─ RECEIPT: Q-receipt-<sessionId>.json (violations found, impl count)
  ↓ [HALT if violations.length > 0]
Ω Phase: Deterministic Git Commit
├─ Input:  All artifacts from Ψ-Q phases
├─ Emit:   git add <specific files from emit_channel>
├─ Exec:   git commit -m "GODSPEED PASS: Ψ→Λ→H→Q"
├─ Parse:  session ID from env (CLAUDE_SESSION_ID)
└─ RECEIPT: Ω-receipt-<sessionId>.json (commit hash, files modified)
  ↓
FINAL RECEIPT: godspeed-<sessionId>.json (aggregates all 5 phases)
├─ phases: { Ψ, Λ, H, Q, Ω }
├─ drift_detection: { previous_hash, current_hash, delta }
├─ timeline: { started, phases: { Ψ: duration, Λ: duration, ... } }
└─ artifacts: { facts, jars, violations, commit }
```

---

## 3. ggen Extensions Required

### 3.1 `ggen-godspeed` CLI Entry Point

```bash
# Primary command
ggen-godspeed \
  --config ggen.toml \
  --phases observation,build,guards,invariants,git \
  --session-id $CLAUDE_SESSION_ID \
  --output-dir generated/

# Returns:
# - exit 0 if all phases GREEN (atomic commit pushed)
# - exit 1 if any phase RED (detailed error report)
# - exit 2 if validation failed (guard/invariant violation)
```

### 3.2 Core Traits & Interfaces

```rust
// In ggen/crates/godspeed/src/lib.rs

pub trait GodspeedPhase {
    fn phase_name(&self) -> &str;                    // "observation", "build", etc
    fn validate_input(&self) -> Result<()>;          // Pre-flight checks
    fn execute(&mut self) -> Result<PhaseOutput>;    // Run the phase
    fn emit_receipt(&self) -> Receipt;               // Durable state
}

pub struct PhaseOutput {
    pub phase: String,
    pub artifacts: Vec<String>,          // Generated files
    pub violations: Vec<Violation>,       // H/Q violations (empty = PASS)
    pub duration_ms: u64,
    pub receipt: Receipt,
}

pub struct Receipt {
    pub phase_id: String,
    pub timestamp: DateTime<Utc>,
    pub session_id: String,
    pub content_hash: String,            // SHA256 of all outputs
    pub artifacts: Vec<ArtifactRef>,
    pub status: PhaseStatus,              // GREEN | RED | BLOCKED
}

pub enum PhaseStatus {
    GREEN,                                 // Phase passed all gates
    RED,                                   // Execution failed
    BLOCKED(String),                       // Gate blocked (H/Q violation)
}
```

### 3.3 Guard Pattern Engine (Poka-Yoke)

```rust
pub struct PokayokeRuleEngine {
    rules: Vec<GuardRule>,
}

pub struct GuardRule {
    name: String,                          // "no_TODO", "no_mock_class"
    regex: Regex,                          // Detection pattern
    description: String,
    severity: Severity,                    // ERROR | WARNING
}

impl PokayokeRuleEngine {
    /// Query compiled code via SPARQL for forbidden patterns
    pub fn validate_no_guards(&self, sparql_endpoint: &str) -> Result<Vec<Violation>> {
        let query = r#"
            PREFIX yawl: <http://yawlfoundation.org/yawl#>

            SELECT ?file ?line ?pattern ?violationType
            WHERE {
              ?violation a yawl:GuardViolation ;
                         yawl:file ?file ;
                         yawl:line ?line ;
                         yawl:pattern ?pattern ;
                         yawl:type ?violationType .
            }
        "#;
        // Execute SPARQL, return violations
    }
}
```

**Guard Rules** (mapped to H gates):

```toml
[[guards]]
name = "no_TODO"
regex = "//\\s*TODO|FIXME|XXX|HACK|LATER"
description = "Deferred work markers forbidden in production code"

[[guards]]
name = "no_mock_class"
regex = "(class|interface)\\s+(Mock|Stub|Fake|Demo)[A-Za-z]+"
description = "Mock/stub classes only allowed in test/"

[[guards]]
name = "no_empty_return"
regex = "return\\s+\"\"|return\\s+null\\s*;\\s*//\\s*(stub|demo)"
description = "Empty/stub returns not allowed without real impl"

[[guards]]
name = "no_silent_fallback"
regex = "catch\\s*\\([^)]+\\)\\s*\\{.*return\\s+(mock|fake|test)"
description = "Silent fallbacks to fake data forbidden"

[[guards]]
name = "no_lie"
regex = "@Deprecated.*return"
description = "No returning from deprecated code (must throw)"
```

### 3.4 SHACL Validator (Invariant Enforcement)

```rust
pub struct SHACLValidator {
    shapes_graph: RdfGraph,               // Loaded from invariants.ttl
}

impl SHACLValidator {
    /// Validate code against SHACL shapes (Q invariants)
    pub fn validate_invariants(&self, code_graph: RdfGraph) -> Result<Vec<Violation>> {
        // Execute SHACL validation on code_graph
        // Returns violations for:
        // - Methods without impl AND without throw UnsupportedOperationException
        // - @Mock annotations in src/ (only allowed in test/)
        // - Exception swallowing (catch without propagate/throw)
    }
}
```

**SHACL Shapes** (invariants.ttl):

```turtle
PREFIX sh: <http://www.w3.org/ns/shacl#>
PREFIX yawl: <http://yawlfoundation.org/yawl#>

yawl:MethodShape
    a sh:NodeShape ;
    sh:targetClass yawl:Method ;
    sh:property [
        sh:path yawl:hasBody ;
        sh:minCount 1 ;
        sh:message "Method must have body (real impl or throw)" ;
    ] ;
    sh:or (
        [ sh:path yawl:isThrowingException ; sh:hasValue true ]
        [ sh:path yawl:hasRealImplementation ; sh:hasValue true ]
    ) ;
    sh:message "Q invariant: real_impl ∨ throw violated" .
```

### 3.5 Drift Detector

```rust
pub struct DriftDetector {
    previous_facts: HashMap<String, String>,  // file -> SHA256
    current_facts: HashMap<String, String>,
}

impl DriftDetector {
    /// Compare previous & current facts.json (detect drift = Σ → 0)
    pub fn detect_drift(&self) -> DriftReport {
        let mut delta = Vec::new();
        for (file, prev_hash) in &self.previous_facts {
            if let Some(curr_hash) = self.current_facts.get(file) {
                if prev_hash != curr_hash {
                    delta.push(DriftDelta {
                        file: file.clone(),
                        previous: prev_hash.clone(),
                        current: curr_hash.clone(),
                        status: "CHANGED".to_string(),
                    });
                }
            }
        }
        DriftReport {
            total_files: self.current_facts.len(),
            changed: delta.len(),
            drift_detected: !delta.is_empty(),
            deltas: delta,
        }
    }

    /// Fail if facts are stale (detected via SHA256 mismatch)
    pub fn assert_facts_fresh(&self) -> Result<()> {
        let report = self.detect_drift();
        if report.drift_detected {
            Err(format!(
                "DRIFT DETECTED: {} files changed. Run: bash scripts/observatory/observatory.sh",
                report.changed
            ))
        } else {
            Ok(())
        }
    }
}
```

---

## 4. Configuration Format: ggen.toml + godspeed.toml

### 4.1 GODSPEED Section in ggen.toml

```toml
[godspeed]
# Enable GODSPEED methodology circuit
enabled = true
# Phases to execute (order matters: Ψ→Λ→H→Q→Ω)
phases = ["observation", "build", "guards", "invariants", "git"]
# Fail-fast mode: halt at first RED phase
fail_fast = true
# Emit files to git channel (never git add .)
emit_channel = ["src/", "test/", ".claude/", "generated/"]
# Exclude from emit
exclude = ["**/*.class", "**/.git/"]

[observation]
# Ψ phase: discover & validate facts
scripts = ["scripts/observatory/observatory.sh"]
output = ".specify/facts/"
receipt = "receipts/godspeed/observation-receipt.json"
# Detect drift via SHA256
verify_checksums = true
previous_facts = ".specify/facts/.previous-checksums.json"

[build]
# Λ phase: compile & test
command = "bash scripts/dx.sh compile"
test_command = "bash scripts/dx.sh test"
output = "target/"
receipt = "receipts/godspeed/build-receipt.json"
# Module list from facts
modules_from_facts = true
# Fail if exit code != 0
require_success = true

[guards]
# H phase: guard pattern detection
enabled = true
# Hook that performs validation
validator_script = ".claude/hooks/hyper-validate.sh"
receipt = "receipts/godspeed/guards-receipt.json"
# Report format
report_format = "json"
report_path = "receipts/godspeed/guard-violations.json"
# Rules (14 patterns total)
rules = [
    "no_TODO",
    "no_mock_class",
    "no_empty_return",
    "no_silent_fallback",
    "no_lie",
    # ... 9 more
]

[invariants]
# Q phase: real impl or throw
enabled = true
# SHACL shapes file
shapes_file = ".specify/invariants.ttl"
# SPARQL validation queries
validation_queries = [
    "queries/real-impl-or-throw.rq",
    "queries/no-mock-in-src.rq",
    "queries/no-silent-catch.rq",
]
receipt = "receipts/godspeed/invariants-receipt.json"
report_path = "receipts/godspeed/invariant-violations.json"

[git]
# Ω phase: atomic commit
enabled = true
# Branch prefix
branch_prefix = "claude/"
# Commit message format
message_format = "GODSPEED PASS: Ψ→Λ→H→Q from {session_id}"
# Session tracking
session_id_env = "CLAUDE_SESSION_ID"
# Receipt path
receipt = "receipts/godspeed/git-receipt.json"
# Push configuration
push_enabled = true
push_remote = "origin"

[drift]
# Drift detection (Σ → 0)
enabled = true
# Compare facts between runs
track_facts = true
# Compare build outputs
track_artifacts = true
# Report path
report_path = "receipts/godspeed/drift-report.json"
```

### 4.2 Standalone godspeed.toml (Optional)

For projects without full ggen setup:

```toml
[project]
name = "yawl-v6"
version = "6.0.0"

[godspeed]
phases = ["observation", "build", "guards", "invariants", "git"]

# Minimal Ψ: just validate facts exist
[observation]
facts_path = ".specify/facts/facts.json"
checksums_path = "receipts/checksums.json"

# Minimal Λ: run Maven
[build]
command = "mvn clean compile -q"

# Minimal H: run hyper-validate
[guards]
validator = ".claude/hooks/hyper-validate.sh"
target_files = ["src/**/*.java"]

# Minimal Q: grep for patterns
[invariants]
patterns = [
    "throw UnsupportedOperationException",  # Must find real impl or throw
]

# Minimal Ω: git add + commit
[git]
session_id_env = "CLAUDE_SESSION_ID"
```

---

## 5. Ontology Structure (RDF/OWL + SHACL)

### 5.1 Core GODSPEED Ontology (godspeed.ttl)

```turtle
@prefix gsp: <http://ggen.io/godspeed#> .
@prefix yawl: <http://yawlfoundation.org/yawl#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

# === Phase Definitions ===

gsp:Phase a owl:Class ;
    rdfs:comment "A GODSPEED circuit phase (Ψ, Λ, H, Q, Ω)" .

gsp:ObservationPhase a owl:Class ;
    rdfs:subClassOf gsp:Phase ;
    rdfs:comment "Ψ: Observe facts from codebase" ;
    gsp:order 1 ;
    gsp:validates gsp:FactsFreshness .

gsp:BuildPhase a owl:Class ;
    rdfs:subClassOf gsp:Phase ;
    rdfs:comment "Λ: Compile & test" ;
    gsp:order 2 ;
    gsp:validates gsp:CompileSuccess .

gsp:GuardsPhase a owl:Class ;
    rdfs:subClassOf gsp:Phase ;
    rdfs:comment "H: Enforce guard patterns" ;
    gsp:order 3 ;
    gsp:validates gsp:NoGuardViolations .

gsp:InvariantsPhase a owl:Class ;
    rdfs:subClassOf gsp:Phase ;
    rdfs:comment "Q: Enforce real impl ∨ throw" ;
    gsp:order 4 ;
    gsp:validates gsp:RealImplOrThrow .

gsp:GitPhase a owl:Class ;
    rdfs:subClassOf gsp:Phase ;
    rdfs:comment "Ω: Atomic commit & push" ;
    gsp:order 5 ;
    gsp:validates gsp:AtomicCommit .

# === Guard Definitions ===

gsp:GuardViolation a owl:Class ;
    rdfs:comment "A forbidden pattern found in code" ;
    gsp:severity gsp:ERROR .

gsp:Guard a owl:Class ;
    rdfs:comment "A guard rule (pattern to forbid)" ;
    rdfs:subClassOf [ a owl:Restriction ;
        owl:onProperty gsp:pattern ;
        owl:minCardinality 1 ] ;
    rdfs:subClassOf [ a owl:Restriction ;
        owl:onProperty gsp:description ;
        owl:minCardinality 1 ] .

gsp:TODO_Guard a gsp:Guard ;
    gsp:pattern "TODO|FIXME|XXX|HACK|LATER" ;
    gsp:description "Deferred work markers" .

gsp:MOCK_CLASS_Guard a gsp:Guard ;
    gsp:pattern "(class|interface)\\s+(Mock|Stub|Fake)" ;
    gsp:description "Mock/stub classes (test/ only)" .

gsp:EMPTY_RETURN_Guard a gsp:Guard ;
    gsp:pattern "return\\s+\"\"|return\\s+null" ;
    gsp:description "Empty stub returns" .

# === Invariant Definitions ===

gsp:Invariant a owl:Class ;
    rdfs:comment "A code invariant (Q gate)" .

gsp:RealImplOrThrow a gsp:Invariant ;
    rdfs:comment "Method must have real impl OR throw UnsupportedOperationException" ;
    gsp:sparql_query """
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        SELECT ?method WHERE {
          ?method a yawl:Method .
          FILTER NOT EXISTS { ?method yawl:hasRealImplementation ?x }
          FILTER NOT EXISTS { ?method yawl:throwsException ?x }
        }
    """ .

gsp:NoMockInSrc a gsp:Invariant ;
    rdfs:comment "@Mock annotations only in test/, not src/" ;
    gsp:sparql_query """
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        SELECT ?annotation WHERE {
          ?annotation a yawl:MockAnnotation ;
                      yawl:inFile ?file .
          FILTER NOT CONTAINS(str(?file), "/test/")
        }
    """ .

gsp:NoSilentCatch a gsp:Invariant ;
    rdfs:comment "Catch blocks must propagate or throw, never return fake data" ;
    gsp:sparql_query """
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        SELECT ?catchBlock WHERE {
          ?catchBlock a yawl:CatchBlock ;
                      yawl:returnsFakeData ?x .
        }
    """ .
```

### 5.2 SHACL Shapes (invariants.ttl)

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix gsp: <http://ggen.io/godspeed#> .
@prefix yawl: <http://yawlfoundation.org/yawl#> .

gsp:MethodInvariantShape a sh:NodeShape ;
    sh:targetClass yawl:Method ;
    sh:property [
        sh:path yawl:hasBody ;
        sh:minCount 1 ;
        sh:message "Method must have implementation body" ;
    ] ;
    sh:or (
        [ sh:node [
            sh:targetClass yawl:ThrowStatement ;
            sh:path yawl:exceptionType ;
            sh:hasValue "UnsupportedOperationException" ;
        ] ]
        [ sh:node [
            sh:targetClass yawl:CodeBlock ;
            sh:path yawl:isRealImplementation ;
            sh:hasValue true ;
        ] ]
    ) ;
    sh:severity sh:Violation ;
    sh:message "Q: Real impl ∨ throw violated" .
```

---

## 6. Success Criteria & Validation

### 6.1 Phase Independence (Each phase testable alone)

```bash
# Test Ψ alone
ggen-godspeed --phase observation --config ggen.toml

# Test Λ alone (assumes Ψ output exists)
ggen-godspeed --phase build --config ggen.toml

# Test H alone (assumes Λ output)
ggen-godspeed --phase guards --config ggen.toml

# Test Q alone (assumes H passed)
ggen-godspeed --phase invariants --config ggen.toml

# Test Ω alone (assumes all 4 phases passed)
ggen-godspeed --phase git --config ggen.toml
```

### 6.2 Integration Testing

```bash
# Full circuit
ggen-godspeed --config ggen.toml --phases observation,build,guards,invariants,git

# Returns:
# - exit 0 = all phases GREEN, commit pushed
# - exit 1 = phase RED (build failed, etc)
# - exit 2 = H/Q gate BLOCKED (violations found)

# Check receipts
jq . receipts/godspeed-<sessionId>.json  # Full audit trail
```

### 6.3 Drift Detection Validation

```bash
# Previous run
ggen-godspeed --config ggen.toml
# → receipts/godspeed-abc123.json

# Next run (facts changed)
ggen-godspeed --config ggen.toml
# → Detects Ψ facts stale, fails OBSERVATION phase
# → Reports: "DRIFT DETECTED: 3 files changed. Run: bash scripts/observatory/observatory.sh"
```

---

## 7. Implementation Roadmap

### Phase 1: Core CLI (Week 1)

- [ ] Create `ggen-godspeed` binary entry point
- [ ] Implement `GodspeedPhase` trait + 5 phase implementations
- [ ] Load ggen.toml configuration
- [ ] Add session tracking (CLAUDE_SESSION_ID env var)
- [ ] Implement receipt generation (JSON format)

### Phase 2: Ψ (Observatory) Integration (Week 2)

- [ ] Integrate with `scripts/observatory/observatory.sh`
- [ ] Load facts.json, compare SHA256 checksums
- [ ] Detect drift (Σ → 0 principle)
- [ ] Emit Ψ receipt with facts metadata
- [ ] HALT if facts stale

### Phase 3: Λ (Build) Integration (Week 2)

- [ ] Wrap `bash scripts/dx.sh compile`
- [ ] Parse Maven output for errors
- [ ] Generate test stubs from Tera templates
- [ ] Emit Λ receipt with compile metrics
- [ ] HALT if build RED

### Phase 4: H (Guards) Implementation (Week 3)

- [ ] Integrate hyper-validate.sh hook
- [ ] Implement PokayokeRuleEngine (SPARQL queries)
- [ ] Add 14 guard pattern rules
- [ ] Emit guard-violations.json
- [ ] HALT if violations found

### Phase 5: Q (Invariants) Implementation (Week 3)

- [ ] Load invariants.ttl SHACL shapes
- [ ] Implement SHACLValidator
- [ ] Query for real_impl ∨ throw pattern
- [ ] Emit invariant-violations.json
- [ ] HALT if violations found

### Phase 6: Ω (Git) Integration (Week 4)

- [ ] Implement atomic git add + commit
- [ ] Use emit_channel to stage files
- [ ] Parse session ID from environment
- [ ] Emit Ω receipt with commit metadata
- [ ] Push to origin

### Phase 7: Integration & Testing (Week 4)

- [ ] Full circuit testing (Ψ→Λ→H→Q→Ω)
- [ ] Drift detection validation
- [ ] Receipt chain verification
- [ ] Error recovery scenarios
- [ ] Documentation + CLI help

---

## 8. Key Design Decisions

### 8.1 Why RDF + SPARQL for Validation?

**Rationale**: Facts, code, and rules are all queryable as graphs. A SPARQL query can find "all methods without real impl AND without throw" across any codebase, independent of language.

**Example**:
```sparql
SELECT ?method WHERE {
  ?method a yawl:Method .
  FILTER NOT EXISTS { ?method yawl:hasRealImplementation ?x }
  FILTER NOT EXISTS { ?method yawl:throwsException ?x }
}
```

This scales from YAWL to any ontology-modeled system.

### 8.2 Why Receipts Are Durable

**Rationale**: Each phase emits a signed (SHA256) receipt. If facts change (drift), receipts from previous runs are immutable proof of what was validated. Enables rollback + audit trails.

**Example Receipt Chain**:
```json
{
  "session_id": "claude-abc123",
  "circuit": "Ψ→Λ→H→Q→Ω",
  "phases": {
    "Ψ": { "status": "GREEN", "facts_hash": "abc...", "timestamp": "..." },
    "Λ": { "status": "GREEN", "compile_time_ms": 45000, "timestamp": "..." },
    "H": { "status": "GREEN", "violations": [], "timestamp": "..." },
    "Q": { "status": "GREEN", "violations": [], "timestamp": "..." },
    "Ω": { "status": "GREEN", "commit": "def456...", "timestamp": "..." }
  },
  "drift": { "detected": false, "deltas": [] }
}
```

### 8.3 Why Phase Gates Are Strict

**Rationale**: No silent failures. Each phase either GREEN (proceed) or RED (halt + report). No best-effort fallbacks. If compilation fails, H/Q phases don't run—no point validating code that doesn't compile.

**Gate Order**: Ψ → Λ → H → Q → Ω (each depends on previous GREEN).

### 8.4 Why emit_channel Prevents Drift

**Rationale**: Only files in emit_channel (`src/`, `test/`, `.claude/`, `generated/`) are staged + committed. This prevents accidental commits of build artifacts, IDE files, or secrets. Enforces disciplined gitops.

---

## 9. Integration with YAWL Codebase

### 9.1 Activation Points

1. **SessionStart** (environment setup):
   ```bash
   export CLAUDE_SESSION_ID="claude-$(date +%s)-$(uuidgen | cut -c1-8)"
   export GODSPEED_ENABLED=1
   ```

2. **PreToolUse** (before any write):
   ```bash
   bash .claude/hooks/pre-task.sh  # Calls ggen-godspeed --phase observation
   ```

3. **PostToolUse** (after Write/Edit):
   ```bash
   bash .claude/hooks/post-edit.sh  # Calls ggen-godspeed --phase guards
   ```

4. **PreCommit** (before git push):
   ```bash
   bash scripts/dx.sh all  # Λ phase internally calls ggen-godspeed
   ggen-godspeed --phases build,guards,invariants,git
   ```

### 9.2 Backward Compatibility

- **ggen.toml**: Optional. If missing, single ggen rules still execute (no GODSPEED circuit).
- **godspeed.toml**: Optional. If missing, falls back to default GODSPEED config.
- **Existing hooks**: Continue to work. GODSPEED phases are additive wrappers.

**Example**: Existing `dx.sh all` still works + now also emits GODSPEED receipts.

---

## 10. Metrics & Observability

### 10.1 GODSPEED Circuit Metrics

```json
{
  "godspeed_circuit": {
    "session_id": "claude-abc123",
    "started_at": "2026-02-20T14:32:00Z",
    "ended_at": "2026-02-20T14:35:45Z",
    "total_duration_ms": 225000,
    "phases": {
      "Ψ": {
        "status": "GREEN",
        "duration_ms": 15000,
        "facts_discovered": 347,
        "drift_detected": false,
        "checksum_matches": true
      },
      "Λ": {
        "status": "GREEN",
        "duration_ms": 45000,
        "modules_compiled": 12,
        "tests_passed": 4521,
        "exit_code": 0
      },
      "H": {
        "status": "GREEN",
        "duration_ms": 8000,
        "patterns_checked": 14,
        "violations_found": 0
      },
      "Q": {
        "status": "GREEN",
        "duration_ms": 10000,
        "methods_validated": 892,
        "invariant_violations": 0,
        "real_impl_count": 887,
        "throw_count": 5
      },
      "Ω": {
        "status": "GREEN",
        "duration_ms": 2000,
        "files_staged": 23,
        "commit_hash": "def456abc",
        "branch": "claude/task-engine-001-abc123"
      }
    },
    "drift_report": {
      "detected": false,
      "files_changed": 0,
      "previous_hash": "xyz789",
      "current_hash": "xyz789"
    }
  }
}
```

### 10.2 Observability Integration

- **Metrics**: Prometheus-compatible output (phase durations, violation counts)
- **Traces**: Distributed tracing (OpenTelemetry) for each phase + sub-step
- **Logs**: Structured JSON logs to `logs/godspeed-<sessionId>.log`

---

## 11. Failure Scenarios & Recovery

| Scenario | Detection | Recovery | Severity |
|----------|-----------|----------|----------|
| Ψ: Facts stale | SHA256 mismatch | Run `bash scripts/observatory/observatory.sh`, retry | ERROR |
| Λ: Build fails | Maven exit ≠ 0 | Fix code, re-run `bash scripts/dx.sh compile`, retry phase | ERROR |
| H: Guard violation | Violations.json length > 0 | Fix forbidden pattern, re-run hyper-validate.sh, retry | BLOCKING |
| Q: Invariant violated | Invariant-violations.json length > 0 | Implement real logic or throw, re-run SHACL, retry | BLOCKING |
| Ω: Git push fails | Git exit ≠ 0 | Resolve merge conflict, re-run git phase | ERROR |
| Drift in mid-circuit | Detected in later phase | Halt, report drift, require re-run of Ψ | BLOCKING |

---

## Conclusion

**GODSPEED-ggen integration** makes YAWL's zero-drift methodology executable and composable:

1. **Deterministic**: RDF ontologies → SPARQL queries → Tera code generation → durable receipts
2. **Gated**: Each phase validates previous (Ψ→Λ→H→Q→Ω)
3. **Durable**: Receipts + checksums enable audit trails + rollback
4. **Composable**: Each phase independently testable + pluggable to existing tools
5. **Scalable**: Ontology-based rules apply to any codebase (YAWL + beyond)

**Next step**: Implement Phase 1 (CLI) in ggen/crates/godspeed/, then integration test with existing YAWL hooks.

---

**Document Version**: 1.0
**Author**: Architect (YAWL v6.0.0 Team)
**Status**: Design Complete, Ready for Implementation
