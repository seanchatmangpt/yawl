# Plan: Rust Library Integration Framework for YAWL

## Executive Summary

Create a **reusable pattern and toolkit** for integrating Rust libraries into YAWL using Panama Foreign Function & Memory (FFM) API, based on the Rust4PM reference implementation. This enables future Rust library adoptions (e.g., process mining, cryptography, graph processing) without reinventing the integration approach.

**Scope**: Documentation + Pattern Kit (no full H-Guards validation)
**Timeline**: This week (~12 hours)
**Branch**: `claude/java-otp-rust4pm-fluent-ymTZR`

---

## Objectives

1. **Document Rust4PM as Reference Pattern** (2h)
   - Extract the integration approach from Rust4PM implementation
   - Document each layer: FFM bindings → bridge → domain API
   - Create before/after comparison (raw bindings vs. fluent API)

2. **Audit Codebase for Integration Opportunities** (3h)
   - Identify Rust libraries suitable for YAWL's use cases
   - Map current pain points (algorithmic, performance, crypto)
   - Create an integration opportunity matrix

3. **Build Rust Integration Kit** (7h)
   - Create reusable templates for common patterns
   - Document the jextract workflow (Rust .so → Java bindings)
   - Develop a decision tree for choosing integration strategy
   - Build example starter projects for future integrations

---

## Deliverables

### 1. Rust4PM Integration Pattern Document
**File**: `.claude/docs/RUST-INTEGRATION-PATTERN.md`
**Contents**:
- Layer-by-layer breakdown of Rust4PM FFM architecture
- jextract configuration and command-line usage
- Arena management and resource cleanup patterns
- Error handling (Rust Result → Java Exception)
- Performance considerations (FFI call overhead, native memory)
- Security best practices (buffer overflow prevention, input validation)

### 2. Rust Library Integration Audit
**File**: `.claude/docs/RUST-INTEGRATION-OPPORTUNITIES.md`
**Contents**:
- Matrix: Library Name | Use Case | Integration Effort | ROI | Recommendation
- Candidates to evaluate:
  - `ndarray` / `nalgebra` → Linear algebra for process mining
  - `serde` → Serialization/deserialization performance
  - `tokio` → Async runtime for task queue optimization
  - `ring` / `dalek-cryptography` → Cryptographic functions
  - `petgraph` / `rustworkx` → Graph algorithms
  - `polars` / `arrow` → Data processing pipelines
- Decision criteria: performance gain, maintenance burden, security risk

### 3. Rust Integration Kit (Templates + Tools)
**Directory**: `.claude/rust-integration-kit/`
**Contents**:
- **template-cdylib-project/** - Minimal Rust library with cdylib target
- **template-java-bridge/** - Spring Boot parent Maven module
- **template-ffi-bindings/** - jextract automation scripts
- **template-integration-tests/** - End-to-end test patterns
- **decision-tree.md** - When to use FFM vs. gRPC vs. embedded
- **checklist.md** - Pre-integration readiness assessment

### 4. jextract Workflow Guide
**File**: `.claude/docs/RUST-JEXTRACT-WORKFLOW.md`
**Contents**:
- Step-by-step guide to generate Java bindings from Rust FFI
- Example: `rustc --crate-type cdylib` → header generation → jextract
- Configuration for common scenarios (single struct, arrays, callbacks)
- Troubleshooting: alignment issues, platform-specific code

### 5. Future Integration Examples
**Directory**: `.claude/examples/rust-integration/`
**Contents**:
- Example 1: "Minimal Crypto" (ring library)
- Example 2: "Graph Processing" (petgraph)
- Example 3: "Data Pipeline" (arrow)

---

## Implementation Steps

### Phase 1: Document Rust4PM Pattern (2h)

**Task 1.1**: Extract Rust4PM Architecture
- Read: `yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/`
- Read: `docs/rust4pm/reference-rust-api-mapping.md`
- Create: `.claude/docs/RUST-INTEGRATION-PATTERN.md`

**Task 1.2**: Document Layer Breakdown
```
Layer 1: FFM Bindings (rust4pm_h.java)
  - Generated via jextract from Rust header
  - Unsafe native calls, no validation
  - Used only by Bridge layer

Layer 2: Bridge (Rust4pmBridge.java)
  - Arena-scoped memory management
  - Error handling (Result → Exception)
  - Resource cleanup (try-with-resources)
  - Safe wrapper around Layer 1

Layer 3: Domain API (ProcessMiningEngine.java)
  - Type-safe, high-level API
  - Business logic composition
  - Could use fluent builders here

Layer 4: Application Code
  - Uses only Layer 3
  - Never touches FFM directly
```

**Task 1.3**: Document jextract Workflow
- How Rust source → cdylib .so → C header → Java bindings
- Command: `jextract -l rust4pm -d src/main/java/... rust4pm.h`

### Phase 2: Audit Integration Opportunities (3h)

**Task 2.1**: Identify Candidate Libraries
- Search GitHub/crates.io for Rust libs in these domains:
  - Cryptography (ring, dalek, libsodium-sys)
  - Process mining (py-pm4py equivalent)
  - Graph algorithms (petgraph, rustworkx)
  - Data structures (dashmap, im-rs for concurrent collections)
  - Compression (zstd-sys, brotli-sys)

**Task 2.2**: Create Opportunity Matrix
- Columns: Library | Use Case | Effort | ROI | Security | Recommendation
- Score each on: integration complexity, maintenance burden, security risk
- Example rows:
  ```
  | ring | HMAC/AES/RSA crypto | 3h | High (10×) | Medium | RECOMMENDED
  | petgraph | Graph algorithms | 4h | Medium (5×) | Low | OPTIONAL
  | tokio | Async runtime | 6h | Medium | Medium | DEFERRED
  ```

**Task 2.3**: Document Decision Criteria
- When FFM is appropriate (low-level, CPU-bound, safety critical)
- When gRPC is better (high-level services, independent deployment)
- When Java libraries are sufficient (avoid Rust if equivalent exists)

### Phase 3: Build Integration Kit (7h)

**Task 3.1**: Create Decision Tree (1h)
```
Do you need Rust library integration?
├─ Need to call Rust code from Java?
│  ├─ Is it performance-critical (>100 calls/sec)?
│  │  └─ → Consider FFM (Rust4PM approach) ✓
│  └─ Is it autonomous/independent?
│     └─ → Consider gRPC (separate service) ✓
├─ Equiv. Java library exists?
│  └─ → Use Java library instead
└─ Security-sensitive code?
   └─ → FFM (in-process) vs. gRPC (isolated) trade-off
```

**Task 3.2**: Create Reusable Templates (3h)
- **template-cdylib-project/** - Minimal Rust Cargo.toml with cdylib target
- **template-java-bridge/** - Maven parent, pom.xml structure
- **template-ffi-bindings/** - jextract script to automate binding generation
- **template-integration-tests/** - JUnit tests for FFM calls

**Task 3.3**: Build Integration Checklist (1h)
```
Pre-Integration Readiness
- [ ] Rust library compiles to cdylib (cargo build --lib)
- [ ] C header file generated (cbindgen or manual)
- [ ] Java bindings generated (jextract)
- [ ] Bridge layer wraps FFM calls safely
- [ ] Arena management tested (leak detection)
- [ ] Error handling strategy defined (Result → Exception)
- [ ] Performance benchmarked (FFI overhead acceptable?)
- [ ] Security review passed (buffer overflow, input validation)
- [ ] Tests passing (unit + integration)
```

**Task 3.4**: Create jextract Automation (1h)
```bash
#!/bin/bash
# scripts/rust-integration/jextract-bindings.sh
# Usage: ./jextract-bindings.sh --lib myrust --header myrust.h --output src/main/java

# Generates Java FFM bindings from C header
jextract \
  -t org.yawlfoundation.yawl.rust.bindings \
  -l "$LIB_NAME" \
  -d "$OUTPUT_DIR" \
  "$HEADER_FILE"
```

**Task 3.5**: Create Example Integration Guides (1h)
- Example 1: "Adding Ring Crypto Library"
- Example 2: "Adding Petgraph Library"
- Example 3: "Adding Arrow/Polars Library"
Each with: motivation, integration steps, code samples, tests

---

## Validation Strategy

**Minimal Validation** (per user request):
- ✅ All markdown documents are well-formed and readable
- ✅ Code templates compile (if applicable)
- ✅ Scripts have `#!/usr/bin/env bash` and `set -euo pipefail`
- ✅ No H-Guards violations in new code (TODO, mock, stub checks)
- ❌ Full H-Guards + Q-Invariants validation (deferred)
- ❌ Unit tests for all templates (deferred)

**Verification**:
```bash
# Markdown lint
markdownlint .claude/docs/RUST-*.md

# Shell script validation
bash -n .claude/rust-integration-kit/scripts/*.sh

# No H-Guards violations
grep -r "TODO\|FIXME\|mock\|stub" .claude/rust-integration-kit/ || echo "✓ No violations"
```

---

## Success Criteria

1. ✅ Rust4PM integration pattern clearly documented with diagrams
2. ✅ 10+ candidate Rust libraries evaluated with scorecards
3. ✅ Decision tree helps future developers choose integration approach
4. ✅ Reusable templates reduce future integration time by 50%
5. ✅ jextract automation script works out-of-the-box for common cases
6. ✅ All documentation is discoverable from `.claude/RUST-INTEGRATION.md` hub

---

## Files to Create/Modify

| File | Type | Status | Size |
|------|------|--------|------|
| `.claude/docs/RUST-INTEGRATION-PATTERN.md` | Doc | New | ~3 KB |
| `.claude/docs/RUST-INTEGRATION-OPPORTUNITIES.md` | Doc | New | ~4 KB |
| `.claude/docs/RUST-JEXTRACT-WORKFLOW.md` | Doc | New | ~2 KB |
| `.claude/rust-integration-kit/README.md` | Doc | New | ~2 KB |
| `.claude/rust-integration-kit/template-cdylib-project/Cargo.toml` | Template | New | ~0.5 KB |
| `.claude/rust-integration-kit/template-java-bridge/pom.xml` | Template | New | ~1 KB |
| `.claude/rust-integration-kit/scripts/jextract-bindings.sh` | Script | New | ~0.5 KB |
| `.claude/rust-integration-kit/checklist.md` | Doc | New | ~2 KB |
| `.claude/rust-integration-kit/decision-tree.md` | Doc | New | ~1.5 KB |
| `.claude/examples/rust-integration/01-minimal-crypto.md` | Example | New | ~2 KB |
| `.claude/examples/rust-integration/02-graph-processing.md` | Example | New | ~2 KB |
| `.claude/RUST-INTEGRATION.md` | Hub Doc | New | ~1 KB |

**Total new content**: ~22 KB (lightweight, documentation-focused)

---

## Git Workflow

1. **Create/checkout branch** (if not exists):
   ```bash
   git fetch origin claude/java-otp-rust4pm-fluent-ymTZR
   git checkout -b claude/java-otp-rust4pm-fluent-ymTZR origin/claude/java-otp-rust4pm-fluent-ymTZR
   ```

2. **Implement plan** (per phases above):
   - Phase 1 (docs) → commit
   - Phase 2 (audit) → commit
   - Phase 3 (kit) → commit

3. **Validation before final push**:
   ```bash
   # Check shell scripts
   bash -n .claude/rust-integration-kit/scripts/*.sh

   # No H-Guards violations
   grep -r "TODO\|FIXME\|mock\|stub\|empty_return\|silent_fallback" .claude/

   # Markdown well-formed (optional: markdownlint if available)
   ```

4. **Final commit**:
   ```bash
   git add .claude/docs/ .claude/rust-integration-kit/ .claude/examples/ .claude/RUST-INTEGRATION.md
   git commit -m "docs: Rust library integration framework and Rust4PM reference pattern

   - Document Rust4PM FFM integration as reference pattern (Layer 1-4 architecture)
   - Audit 15+ Rust libraries for integration opportunities with scoring matrix
   - Create reusable integration kit with decision tree, templates, and jextract automation
   - Build starter guides for common Rust library integration scenarios

   Enables future Rust library adoptions (crypto, graphs, data processing) without
   reinventing the integration approach. Pattern reduces integration time by ~50%.

   https://claude.ai/code/session_0195szBgbJTNid7br9LTcwqu"
   ```

5. **Push to branch**:
   ```bash
   git push -u origin claude/java-otp-rust4pm-fluent-ymTZR
   ```

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| **Rust4PM pattern unclear** | Use concrete code excerpts, layer diagrams, data flow |
| **jextract automation fragile** | Test on 2-3 different Rust libraries, provide troubleshooting |
| **Opportunity matrix incomplete** | Focus on top 10-15 candidates; mark as "v1, extensible" |
| **Templates too rigid** | Provide decision tree first, then customize examples per use case |
| **Documentation drift** | Link examples to actual codebase; use relative paths |

---

## Assumptions & Dependencies

- ✅ Rust4PM is production-ready reference implementation
- ✅ jextract is available in JDK 21+ (confirmed in CLAUDE.md)
- ✅ Maven can build Java modules with native dependencies
- ✅ Developers can install Rust toolchain (cargo, rustc) locally
- ❓ Will need to test jextract with actual Rust library (runtime risk)

---

## Next Steps (Post-Approval)

1. User reviews and approves this plan
2. Implement Phase 1-3 per timeline (parallel where possible)
3. Commit and push to branch
4. Create PR with summary of integration framework
5. Future work: Apply framework to cryptography library (ring) as PoC

---

**Status**: READY FOR APPROVAL
**Estimated Time**: 10-12 hours this week
**Context**: Session initiated 2026-03-06 01:29 UTC
