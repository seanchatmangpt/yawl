# GODSPEED Phases Reference — Complete Validation Circuit

**Document Type**: Pure Reference | **Last Updated**: 2026-02-28 | **Framework**: YAWL v6.0.0
**Diataxis Mode**: Reference (specifications, tables, authority)

---

## Executive Summary

The **GODSPEED circuit** is the five-phase validation and compilation flow that transforms user intent into production-ready code. All phases are mandatory, non-skippable, and must complete in order.

**Circuit Formula**: μ(O) = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ

Where:
- **Ψ (Psi)** = Observatory (observe facts, discover state)
- **Λ (Lambda)** = Build (compile, test, validate)
- **H (H-Guards)** = Hard blocks (deception detection)
- **Q (Invariants)** = Real implementation (no mocks, stubs, lies)
- **Ω (Omega)** = Git (atomic commits, immutable history)

**Key principle**: Loss is localizable—each phase has a specific gate where errors stop the circuit. No gate skipping. No workarounds.

---

## 1. Phase Summary Table (Ψ→Λ→H→Q→Ω)

| Phase | Name | Responsibility | Input | Output | Exit Criteria | Timeout |
|-------|------|-----------------|-------|--------|----------------|---------|
| **Ψ** | Observatory | Observe full state (modules, deps, facts, schema) | Working directory | facts/*.json | All facts ≥1 module observed, no stale facts | 60s |
| **Λ** | Build | Compile → Test → Validate per Maven rules | Source + pom.xml | .class files, surefire reports, spotbugs results | dx.sh all GREEN + no analysis warnings | 5m compile, 10m test, 5m verify |
| **H** | H-Guards | Detect 7 forbidden patterns (TODO, mock, stub, etc.) | Generated code (.java) | guard-receipt.json | Zero violations OR violations fixed + re-run | 30s per file |
| **Q** | Invariants | Enforce: real_impl ∨ throw (no third option) | Compiled code + javadoc | invariant-receipt.json | Code matches docs + implements contracts | 2m analysis |
| **Ω** | Git | Atomic commits, immutable history, one change per commit | Staged changes | Commit SHA + git log | Commit pushed + history clean + branch tip recorded | 30s commit + push |

---

## 2. Phase Details

### Ψ OBSERVATORY — Observe Fully Before Acting

**Mission**: Build authoritative fact files about codebase state. No opinions, only observable facts.

#### Responsibilities
- **Module discovery**: Find all pom.xml files, build Maven reactor model
- **Dependency mapping**: Extract all <dependency> tags, resolve transitive closure
- **Schema inspection**: Discover all .xsd files, validate XML structure
- **Package inventory**: List all packages, identify package-info.java files
- **Test discovery**: Find all test classes (JUnit5 fixtures)
- **Observable facts**: Record in `Ψ.facts/` directory with JSON schema

#### Input
- Working directory (`/home/user/yawl` root)
- No pre-existing facts (or stale facts >24h old)

#### Output
- **modules.json**: { id, path, artifactId, version, parent, children[], dependencies[] }
- **gates.json**: Which modules have H/Q/Λ failures
- **deps-conflicts.json**: Transitive dependency conflicts, excludes, overrides
- **reactor.json**: Full Maven build order (topological sort)
- **shared-src.json**: Files shared between modules (error if >0)
- **tests.json**: Test count per module, coverage targets
- **dual-family.json**: Classes/interfaces with multiple implementations
- **duplicates.json**: Duplicate declarations (error if >0)
- **maven-hazards.json**: Deprecated plugins, missing versions, security risks

**Receipt**: `receipts/observatory.json` with SHA256 hash of facts + timestamp

#### Exit Criteria
✓ All fact files exist and are valid JSON
✓ No stale facts (created within last 24h)
✓ Reactor model is acyclic (no circular dependencies)
✓ shared-src.json is empty (no file conflicts)
✓ duplicates.json is empty (no duplicate declarations)

#### Error Recovery
- **Fact file missing**: Run `bash scripts/observatory/observatory.sh`
- **Stale facts (>24h old)**: Re-run observatory script
- **Circular dependency**: Fix pom.xml parents/dependencies, re-run
- **File conflicts**: Refactor module ownership, re-run
- **Parse errors**: Check syntax of pom.xml/xsd files, fix, re-run

#### Timeout
- **Standard**: 60 seconds
- **Large codebase (>100 modules)**: 2 minutes
- **Network fetch (Maven Central)**: 5 minutes total

#### Entry Point
```bash
# Automatic on session start if facts missing or stale
bash scripts/observatory/observatory.sh

# Explicit run
bash scripts/observatory/observatory.sh --force --output receipts/observatory.json
```

---

### Λ BUILD — Compile, Test, Validate

**Mission**: Transform source → bytecode → reports. Enforce Maven discipline: Compile ≺ Test ≺ Validate ≺ Deploy.

#### Responsibilities
- **Compile phase**: `mvn clean compile` per module or all
- **Test phase**: `mvn test` (JUnit5 fixtures)
- **Verify phase**: `mvn verify` (integration tests, analysis tools)
- **Analysis**: SpotBugs (code smells), PMD (style), JaCoCo (coverage)
- **Report generation**: surefire-report.xml, spotbugs-result.html, etc.

#### Input
- Source files (src/main/java, src/test/java)
- pom.xml with maven-compiler-plugin, maven-surefire-plugin configuration
- Facts from Ψ (reactor.json for build order)

#### Output
- **.class bytecode** files in target/classes/
- **Test reports**: target/surefire-reports/ (XML)
- **Analysis reports**: target/spotbugs/, target/pmd/, target/jacoco/
- **Build receipt**: `receipts/build-<module>.json` with metrics

#### Exit Criteria
✓ All modules compile (javac errors = 0)
✓ All tests pass (surefire failures = 0)
✓ Code coverage ≥80% (jacoco)
✓ SpotBugs finds ≤N critical issues (configurable per module)
✓ PMD warnings ≤N (style enforcement)
✓ No duplicate class definitions across modules
✓ dx.sh all returns exit code 0

#### Error Recovery

**Compilation errors** (exit code 1):
- Check javac output: undefined symbols, type mismatches
- Fix source code, re-run `dx.sh -pl <module>`
- If blocking all: `dx.sh -pl <module> -DskipTests` to isolate

**Test failures** (exit code 1):
- Run failing test in isolation: `mvn -Dtest=YNetRunnerTest#testDeadlock test`
- Check test fixture setup (JUnit5 @BeforeEach)
- Fix test or source code, re-run `mvn test`

**Analysis failures** (exit code 1):
- SpotBugs: Review spotbugs.xml, suppress true positives with @SuppressFBWarnings("NP_NULL_DEREFERENCE")
- PMD: Fix style violations or add comments to suppress
- Coverage: Add test cases or @CoverageIgnore annotations
- Re-run: `mvn clean verify -P analysis`

#### Timeout
- **Compile**: 5 minutes per module
- **Test**: 10 minutes (JUnit5 fixtures can be slow)
- **Verify**: 5 minutes (analysis tools)
- **All phases**: 20 minutes total for full build (dx.sh all)

#### Entry Point
```bash
# Fast compile (one module)
dx.sh compile -pl yawl-engine

# Test single module
dx.sh -pl yawl-engine test

# Full validation (pre-commit gate)
dx.sh all

# With static analysis
mvn clean verify -P analysis
```

---

### H GUARDS — Detect Deception in Code

**Mission**: Block 7 forbidden patterns that violate production standards. No TODO, mock, stub, fake, empty return, silent fallback, or lie.

#### Pattern Inventory (H ∩ Content = ∅)

| Pattern | Detection | Example | Severity |
|---------|-----------|---------|----------|
| **H_TODO** | Regex on comments | `// TODO: implement` | FAIL |
| **H_FIXME** | Regex on comments | `// FIXME: broken` | FAIL |
| **H_MOCK** | Regex on identifiers | `class MockDataService` | FAIL |
| **H_STUB** | SPARQL on return statements | `return "";` (non-void) | FAIL |
| **H_EMPTY** | SPARQL on method bodies | `void foo() { }` | FAIL |
| **H_FALLBACK** | SPARQL on catch blocks | `catch(E e) { return empty; }` | FAIL |
| **H_LIE** | Semantic SPARQL | Javadoc says "never null" but returns null | FAIL |

#### Responsibilities
- **AST parsing**: Parse generated .java files (tree-sitter-java)
- **RDF conversion**: AST → RDF facts for semantic queries
- **Pattern detection**: Run 7 SPARQL queries + 2 regex checkers
- **Violation reporting**: Emit guard-receipt.json with line numbers, fix guidance
- **Gate enforcement**: Exit 2 if violations > 0

#### Input
- Generated code (src/main/java/**/*.java after Λ succeeds)
- Guard configuration (guard-config.toml)

#### Output
- **guard-receipt.json**:
  ```json
  {
    "phase": "guards",
    "timestamp": "2026-02-28T14:32:15Z",
    "files_scanned": 42,
    "violations": [
      {
        "pattern": "H_TODO",
        "severity": "FAIL",
        "file": "src/main/java/YWorkItem.java",
        "line": 427,
        "content": "// TODO: implement deadlock detection",
        "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
      }
    ],
    "status": "GREEN|RED",
    "summary": { "h_todo_count": 1, "h_mock_count": 0, ... }
  }
  ```

#### Exit Criteria
✓ guard-receipt.json status = "GREEN"
✓ violations.count = 0
✓ All 42 files scanned without parse errors

#### Error Recovery

**Violations found** (exit code 2):
1. Read guard-receipt.json for details
2. For H_TODO/H_FIXME:
   - Implement real logic: `public void deadlockDetection() { this.detector.start(); }`
   - Or throw: `throw new UnsupportedOperationException("deadlock detection not implemented in this version")`
3. For H_MOCK:
   - Delete mock class or implement real service integration
4. For H_STUB/H_EMPTY:
   - Implement method body or throw exception
5. For H_FALLBACK:
   - Propagate exception: `catch (IOException e) { throw new RuntimeException(e); }`
6. For H_LIE:
   - Align code with javadoc (update either code or docs)
7. Re-run: `ggen validate --phase guards --emit src/main/java`

**Parse errors** (exit code 1):
- Check for invalid Java syntax (unclosed brackets, etc.)
- Fix source file, re-run

#### Timeout
- **Per file**: 30 seconds (AST parse + SPARQL query)
- **Whole phase**: 5 minutes for full codebase
- **Query timeout**: 30 seconds per SPARQL query

#### Entry Point
```bash
# Run guards phase standalone
ggen validate --phase guards --emit src/main/java

# With detailed output
ggen validate --phase guards --emit src/main/java --verbose

# Save to custom receipt location
ggen validate --phase guards --emit src/main/java \
  --receipt-file .claude/receipts/guard-receipt.json
```

---

### Q INVARIANTS — Enforce Real Implementation

**Mission**: Guarantee code matches documentation and implements contracts. No mocks, stubs, silent fallbacks, or lies.

#### Formula
**real_impl ∨ throw** : Every method must either:
1. Implement the contract fully, OR
2. Throw UnsupportedOperationException (never silent failure)

No third option. No "for now", "later", "temporary" code.

#### Responsibilities
- **Contract discovery**: Extract method signatures, parameters, return types
- **Documentation parsing**: Read javadoc @param, @return, @throws tags
- **Implementation verification**: Check method body matches javadoc
- **Signature validation**: Confirm method signature matches interface definition
- **Exception handling**: Verify throws clauses are accurate and complete

#### Input
- Compiled .class bytecode (from Λ)
- Javadoc documentation
- Interface contracts (src/main/java/**/*.java)

#### Output
- **invariant-receipt.json**:
  ```json
  {
    "phase": "invariants",
    "timestamp": "2026-02-28T14:32:45Z",
    "methods_checked": 1247,
    "violations": [
      {
        "type": "MISSING_IMPLEMENTATION",
        "method": "YNetRunner.detectDeadlock()",
        "location": "YNetRunner.java:427",
        "contract": "@return true if deadlock detected, false otherwise",
        "actual": "return false; // stub",
        "fix": "Implement real deadlock detection or throw UnsupportedOperationException"
      },
      {
        "type": "DOCUMENTATION_MISMATCH",
        "method": "YWorkItem.getData()",
        "contract": "@return never null",
        "actual": "return null; // edge case",
        "fix": "Update @return javadoc to indicate nullable, or handle null case"
      }
    ],
    "status": "GREEN|RED"
  }
  ```

#### Exit Criteria
✓ invariant-receipt.json status = "GREEN"
✓ Zero implementation mismatches
✓ Zero documentation mismatches
✓ All non-abstract methods have real implementation (not just return false/empty)

#### Error Recovery

**Implementation mismatch** (exit code 2):
- Implement real logic following the contract
- Example (before):
  ```java
  public boolean detectDeadlock() {
    return false; // stub—should implement real detection
  }
  ```
- Example (after):
  ```java
  public boolean detectDeadlock() {
    List<Thread> threads = getAllThreads();
    return hasCircularWait(threads);
  }
  ```

**Documentation mismatch** (exit code 2):
- Align javadoc with code, or code with javadoc
- Example (before):
  ```java
  /** @return never null */
  public String getStatus() {
    return status == null ? "" : status;
  }
  ```
- Example (after):
  ```java
  /** @return status or empty string if null */
  public String getStatus() {
    return status == null ? "" : status;
  }
  ```

**Silent fallback in catch** (exit code 2):
- Propagate or throw, never silently degrade
- Example (before):
  ```java
  try {
    return client.fetch(url);
  } catch (IOException e) {
    return Collections.emptyList(); // silent fallback—bad!
  }
  ```
- Example (after):
  ```java
  try {
    return client.fetch(url);
  } catch (IOException e) {
    throw new RuntimeException("Failed to fetch: " + url, e);
  }
  ```

#### Timeout
- **Per method**: 100ms (javadoc parse + signature check)
- **Whole phase**: 2 minutes for full codebase (1247 methods)

#### Entry Point
```bash
# Run invariants phase (after H succeeds)
ggen validate --phase invariants --emit src/main/java

# Check specific module
ggen validate --phase invariants --emit src/main/java/yawl-engine
```

---

### Ω GIT — Atomic Commits, Immutable History

**Mission**: Record changes atomically. One logical change per commit. Never force-push. Never rewrite history.

#### Responsibilities
- **Staging**: Add only necessary files (never `git add .`)
- **Commit message**: Clear, one change per commit, include session URL
- **Push**: Non-force push to origin, verify fast-forward
- **Branch management**: Create branch per task (claude/<desc>-<sessionId>)
- **History preservation**: Maintain immutable audit trail

#### Input
- Staged changes (specific files added via `git add <file>`)
- Commit message with clear description

#### Output
- Commit SHA in git log
- Remote branch updated on origin
- Immutable history: `git log --oneline`

#### Exit Criteria
✓ Commit SHA recorded in local and remote
✓ Commit message is clear and includes session context
✓ No force-push (--force / -f forbidden)
✓ No amended pushed commits (rewrite history forbidden)
✓ Branch tip matches commit SHA
✓ History is clean (no merge conflicts, no rebase)

#### Error Recovery

**Branch conflict** (exit code 1):
- Fetch latest: `git fetch origin`
- Rebase or merge: `git rebase origin/main` or `git merge origin/main`
- Resolve conflicts (manual)
- Re-run push: `git push origin <branch>`

**Permission denied** (exit code 1):
- Configure credentials: `git config --global user.name "Name"` + `git config --global user.email "email"`
- Authenticate: Provide token or SSH key
- Re-run push

**Status check failed** (exit code 1):
- Check pre-commit hooks: `cat .git/hooks/pre-commit`
- Fix code violations in H/Q phases
- Re-run: `dx.sh all` (must GREEN before commit)
- Try commit again

#### Commit Message Format
```
<summary, <70 chars>

<body, 1-2 sentences explaining the change and why>

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
```

**Example**:
```
Fix deadlock detection in YNetRunner

Implemented circular wait detection algorithm using depth-first search
on thread dependency graph. Improves production observability by
detecting deadlocks before cascade failures.

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
```

#### Timeout
- **Commit**: 5 seconds (just git add + git commit)
- **Push**: 30 seconds (network I/O)
- **Total Ω phase**: 35 seconds

#### Entry Point
```bash
# Stage specific files (NEVER git add .)
git add src/main/java/YNetRunner.java src/main/java/YWorkItem.java

# Check what will be committed
git diff --cached

# Create commit with clear message
git commit -m "$(cat <<'EOF'
Fix deadlock detection in YNetRunner

Implemented circular wait detection algorithm using depth-first search
on thread dependency graph.

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
EOF
)"

# Push to remote
git push origin claude/deadlock-fix-01Sfdx
```

---

## 3. Phase Transition Decision Gates

### Gate Ψ→Λ: Observatory Complete?
```
Observatory produces valid facts?
├─ YES: modules.json, reactor.json valid + acyclic
│  ├─ Enter Λ Build phase
│  └─ Proceed with full build
├─ NO: Facts invalid or stale
│  ├─ Re-run: bash scripts/observatory/observatory.sh
│  └─ Check: modules.json syntax, pom.xml circular deps
```

### Gate Λ→H: Build Passes All Tests?
```
dx.sh all returns exit code 0?
├─ YES: All modules compile + test + verify pass
│  ├─ Enter H-Guards phase
│  └─ Run guard validation
├─ NO: Compilation/test failures
│  ├─ Fix source code
│  ├─ Re-run: dx.sh all
│  └─ Repeat until GREEN
```

### Gate H→Q: Zero Guard Violations?
```
guard-receipt.json status = "GREEN"?
├─ YES: No forbidden patterns detected
│  ├─ Enter Q Invariants phase
│  └─ Check implementation matches docs
├─ NO: Violations found
│  ├─ Read guard-receipt.json for violations
│  ├─ Fix each violation (implement, don't mock)
│  ├─ Re-run: ggen validate --phase guards
│  └─ Repeat until GREEN
```

### Gate Q→Ω: Implementation Matches Contracts?
```
invariant-receipt.json status = "GREEN"?
├─ YES: All methods match documentation
│  ├─ Enter Ω Git phase
│  └─ Commit changes
├─ NO: Implementation mismatches
│  ├─ Align code with javadoc
│  ├─ Re-implement stubs with real logic
│  ├─ Re-run: ggen validate --phase invariants
│  └─ Repeat until GREEN
```

### Gate Ω→Done: Commit Success?
```
git push returns exit code 0?
├─ YES: Commit on remote
│  ├─ Immutable history recorded
│  ├─ Task complete
│  └─ Verify: git log shows new commit
├─ NO: Push failed (conflicts, permissions)
│  ├─ Resolve: git rebase or git merge
│  ├─ Authenticate: credentials/SSH key
│  ├─ Re-run: git push
│  └─ Repeat until success
```

---

## 4. Error Handling & Recovery Protocols

### Ψ Observatory Failures

| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| **modules.json missing** | observatory.sh not run | `bash scripts/observatory/observatory.sh` |
| **Stale facts (>24h)** | Facts outdated | Re-run observatory.sh --force |
| **Circular dependency** | pom.xml parent loop | Fix pom.xml, re-run |
| **File conflict** (shared-src.json non-empty) | Two modules own same file | Refactor module structure |
| **Parse error** (pom.xml syntax) | Invalid XML | Fix XML syntax, re-run |
| **Network timeout** | Maven Central unreachable | Check network, retry or use proxy |

### Λ Build Failures

| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| **Compilation error** (undefined symbol) | Missing import or typo | Fix source code, re-run dx.sh compile |
| **Test failure** (assertion failure) | Logic bug or test fixture issue | Debug test, fix code, re-run |
| **Coverage < 80%** | Untested code | Add test cases, re-run mvn verify |
| **SpotBugs critical issue** | Code smell detected | Fix or suppress with @SuppressFBWarnings |
| **Timeout (>20 min)** | Slow tests or infinite loop | Profile with jconsole, fix, re-run |
| **Out of memory** | Large heap needed | Increase: `mvn -Xmx4g clean verify` |

### H-Guards Failures

| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| **H_TODO found** | Deferred work in code | Implement real logic or throw UnsupportedOperationException |
| **H_MOCK found** | Mock class/method | Delete mock or implement real service |
| **H_STUB found** | Empty return statement | Implement real logic |
| **H_EMPTY found** | No-op method body | Implement logic or throw exception |
| **H_FALLBACK found** | Silent catch degradation | Throw exception instead |
| **H_LIE found** | Javadoc ≠ code | Align code or update javadoc |
| **Parse error** | Invalid Java syntax | Fix syntax, re-run |

### Q Invariants Failures

| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| **Missing implementation** | Stub or stub-like return | Implement real contract |
| **Documentation mismatch** | Javadoc describes different contract | Align code or javadoc |
| **Silent fallback** | Catch without throw | Throw exception |
| **Signature mismatch** | Method doesn't match interface | Align signatures |

### Ω Git Failures

| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| **Merge conflict** | Remote changed same lines | `git rebase origin/main`, resolve conflicts |
| **Permission denied** | SSH key missing or credentials stale | Configure git credentials |
| **Status check fail** | Pre-commit hook violations | Re-run dx.sh all, fix violations |
| **Branch diverged** | Local ≠ remote | Force-fetch origin, but don't force-push |

---

## 5. Performance Targets & SLAs

### Phase Timeouts (Hard Limits)

| Phase | Timeout | Buffer | Total |
|-------|---------|--------|-------|
| **Ψ Observable** | 60s | 30s | 90s |
| **Λ Build** | 20m | 5m | 25m |
| **H-Guards** | 5m | 2m | 7m |
| **Q Invariants** | 2m | 1m | 3m |
| **Ω Git** | 35s | 10s | 45s |
| **TOTAL CIRCUIT** | 29m | 8m | 37m |

### Resource Targets

| Resource | Target | Baseline | Notes |
|----------|--------|----------|-------|
| **Compile throughput** | 100 modules/min | 50-100 KLOC/s | Java25 javac + incremental |
| **Test throughput** | 10-100 tests/s | JUnit5 fixtures, parallel | Set `-Dparallel.threads=4` |
| **Memory (build)** | <4 GB | Start -Xmx2g, scale to 4g if needed | Maven heap |
| **Disk I/O (artifacts)** | <500 MB | `.m2/repository` cache | Incremental builds faster |
| **Network (Observatory)** | <5s Maven Central | Proxy caching enabled | CLAUDE_CODE_REMOTE=true |

### Quality Metrics

| Metric | Target | Enforcement |
|--------|--------|--------------|
| **Test coverage** | ≥80% | Gate: mvn verify |
| **Code smells (SpotBugs)** | ≤5 per module | Suppressible with annotation |
| **Guard violations** | 0 | Hard block: exit 2 |
| **Invariant violations** | 0 | Hard block: exit 2 |
| **Commit frequency** | 1-5 per hour | One change per commit |

---

## 6. Integration with Hook System

### Hook Orchestration

The GODSPEED circuit integrates with `.claude/hooks/`:

```
User writes or edits file
    ↓
hyper-validate.sh triggered (PRE-WRITE)
    ├─ Check for H-Guard patterns (TODO, mock, etc.)
    ├─ If violations: exit 2 (block write)
    └─ If clean: allow write
    ↓
File written to disk
    ↓
yawl-jira hook (POST-WRITE)
    ├─ Fetch ticket context
    ├─ Record delta in receipts/
    └─ Update .claude/jira/tickets/*.toml
    ↓
User runs: ggen validate --phase guards
    ├─ [H-Guards phase runs here]
    └─ If violations: correct and re-run
    ↓
User runs: dx.sh all
    ├─ [Ψ→Λ→H→Q→Ω full circuit]
    └─ If RED: fix and retry
    ↓
User commits: git commit -m "..."
    ├─ Pre-commit hook: run dx.sh all again (final gate)
    ├─ If RED: commit rejected
    └─ If GREEN: create commit
    ↓
User pushes: git push
    ├─ Status checks run
    └─ If pass: commit immutable on origin
```

### Hook Scripts

| Hook | Trigger | Purpose | Exit Codes |
|------|---------|---------|-----------|
| `hyper-validate.sh` | Write/Edit | Block H violations before persisting | 0 (ok), 2 (violation) |
| `yawl-jira` | POST-WRITE | Update ticket context + deltas | 0 (ok), 1 (transient), 2 (fatal) |
| `pre-commit` | git commit | Final dx.sh all check | 0 (ok), 2 (violation) |
| `pre-push` | git push | Status checks + history validation | 0 (ok), 1 (conflict), 2 (fatal) |

---

## 7. Code Examples: Phase Transitions

### Example 1: Simple Bug Fix (Ψ→Λ→H→Q→Ω)

**Task**: Fix YNetRunner.detectDeadlock() returning false unconditionally

#### Ψ Observatory
```bash
$ bash scripts/observatory/observatory.sh
✓ modules.json: 89 modules discovered
✓ reactor.json: build order = yawl-core → yawl-engine → yawl-integration
✓ deps-conflicts.json: 0 conflicts
✓ shared-src.json: empty (no file conflicts)
```

#### Λ Build
```bash
$ dx.sh -pl yawl-engine compile
✓ YNetRunner.java compiled successfully

$ mvn -pl yawl-engine test
✓ YNetRunnerTest#testDeadlockDetection passed
✓ 87% coverage for YNetRunner
```

#### H-Guards
```bash
$ ggen validate --phase guards --emit src/main/java
✓ 42 files scanned
✓ 0 violations found
Status: GREEN
```

#### Q Invariants
```bash
$ ggen validate --phase invariants
✓ YNetRunner.detectDeadlock() implements contract
✓ Javadoc matches implementation
Status: GREEN
```

#### Ω Git
```bash
$ git add src/main/java/YNetRunner.java src/test/java/YNetRunnerTest.java
$ git commit -m "$(cat <<'EOF'
Fix deadlock detection in YNetRunner

Implemented circular wait detection using depth-first search on
thread dependency graph. Improves production observability.

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
EOF
)"
$ git push origin claude/deadlock-fix-01Sfdx
✓ Commit SHA: a3f5b2c1... recorded on origin
```

---

### Example 2: New Feature with Teams (Ψ→Λ→H→Q→Ω × N)

**Task**: Add SLA tracking (schema + engine + integration—3 engineers)

#### Ψ Observatory (once, shared across team)
```bash
$ bash scripts/observatory/observatory.sh
✓ All facts current
```

#### Team Formation (3 engineers)
```
Engineer A: Schema (defines SlaAttribute)
Engineer B: Engine (implements SLA logic)
Engineer C: Integration (REST API endpoint)

Each runs local Λ→H→Q, then:
  Lead runs Ω (atomic consolidation)
```

#### Engineer A: Schema
```bash
# Λ Build
$ dx.sh -pl yawl-schema compile
$ mvn test
# H-Guards
$ ggen validate --phase guards
# Q Invariants
$ ggen validate --phase invariants
✓ Schema defines SlaAttribute, SlaValue enums
```

#### Engineer B: Engine
```bash
# Λ Build
$ dx.sh -pl yawl-engine compile
$ mvn test (SLA calculation tests pass)
# H-Guards
$ ggen validate --phase guards
# Q Invariants
$ ggen validate --phase invariants
✓ YSlaCalculator implements real SLA logic
```

#### Engineer C: Integration
```bash
# Λ Build
$ dx.sh -pl yawl-integration compile
$ mvn test (REST endpoint tests pass)
# H-Guards
$ ggen validate --phase guards
# Q Invariants
$ ggen validate --phase invariants
✓ YSlaRestResource exposes SLA endpoint
```

#### Lead: Consolidation (Ω)
```bash
$ dx.sh all  # Verify all 3 modules build together
✓ GREEN

$ git add \
    schema/src/main/java/YSlaAttribute.java \
    engine/src/main/java/YSlaCalculator.java \
    integration/src/main/java/YSlaRestResource.java

$ git commit -m "$(cat <<'EOF'
Add SLA tracking feature

Schema: Define SlaAttribute (deadline, severity, status)
Engine: Implement SlaCalculator using time-based triggers
Integration: Expose SLA endpoint via REST API

Coordinated across 3 modules. Tested end-to-end.

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
EOF
)"
$ git push origin claude/sla-tracking-01Sfdx
✓ Atomic commit recorded
```

---

### Example 3: Guard Violation Recovery

**Task**: Developer accidentally added TODO comment

```bash
# Developer commits code with TODO
$ git add src/main/java/YNetRunner.java
$ git commit -m "WIP: attempt deadlock detection"
[claude/deadlock-wip] FIXME: complete this implementation

# Pre-commit hook runs H-Guards
$ hyper-validate.sh

✗ VIOLATION FOUND: H_TODO
  File: YNetRunner.java:427
  Content: // TODO: implement deadlock detection
  Severity: FAIL
  Fix: Implement real logic or throw UnsupportedOperationException

# Commit is rejected! Developer must fix.

# Developer fixes the violation
$ cat src/main/java/YNetRunner.java | head -430 | tail -5
    public boolean detectDeadlock() {
        List<Thread> threads = getAllThreads();
        return hasCircularWait(threads);  // Real implementation
    }

# Re-run guard check
$ ggen validate --phase guards
Status: GREEN

# Now commit succeeds
$ git commit --amend -m "Implement deadlock detection with circular wait algorithm"
$ git push origin claude/deadlock-wip
✓ Success
```

---

### Example 4: Full Circuit Failure & Recovery

**Task**: Developer breaks test → guard violation → failed commit

```
Step 1: Developer edits YWorkItem.java (breaks test)
  ↓
Step 2: dx.sh all fails
  Error: YWorkItemTest#testGetStatusThrowsExpectedException fails
  ↓
Step 3: Developer fixes test, re-runs Λ
  ✓ dx.sh all GREEN
  ↓
Step 4: Developer commits
  ↓
Step 5: Pre-commit hook runs hyper-validate.sh
  ✗ VIOLATION: H_LIE (javadoc says "throws" but code catches silently)
  ↓
Step 6: Developer aligns javadoc with implementation:
  Before:
    /** @throws IOException if fetch fails */
    public List<Item> fetch() {
      try { return client.fetch(); }
      catch (IOException e) { return emptyList(); }
    }

  After:
    /** @throws IOException if fetch fails (propagated) */
    public List<Item> fetch() throws IOException {
      return client.fetch();
    }
  ↓
Step 7: dx.sh all again
  ✓ GREEN
  ↓
Step 8: git commit again
  ✓ Pre-commit hook GREEN
  ✓ Commit created
  ↓
Step 9: git push
  ✓ Immutable on origin
```

---

## 8. Quick Reference: Exit Codes

| Code | Phase | Meaning | Action |
|------|-------|---------|--------|
| **0** | All | Success | Proceed to next phase |
| **1** | Λ | Compile/test failure | Fix code, re-run |
| **1** | H | Parse error | Fix Java syntax |
| **1** | Ω | Transient error (network) | Retry push |
| **2** | Λ | Analysis failure (coverage, SpotBugs) | Fix violations or suppress |
| **2** | H | Guard violations found | Implement real logic |
| **2** | Q | Invariant violations found | Align code with docs |
| **2** | Ω | Hard gate failure (pre-commit hook) | Re-run dx.sh all |

---

## 9. Frequently Asked Questions

### Q: Can I skip a phase?
**A**: No. All phases are mandatory. Priority is H > Q > Ψ > Λ > Ω (enforcement order), but flow is Ψ→Λ→H→Q→Ω (execution order). No gate skipping.

### Q: What if a test is flaky?
**A**: Add @RepeatedTest(3) or use @Timeout(10, unit = SECONDS). Flaky tests must be fixed, not ignored. If timeout is needed, document in test and add @Flaky annotation.

### Q: Can I suppress a guard violation?
**A**: No. H-Guard violations are hard blocks. If you believe a pattern is a false positive, update the pattern definition in guard-config.toml, document the change, and re-run. Never suppress H violations.

### Q: What if I need to commit urgent security fix?
**A**: Still must pass Ψ→Λ→H→Q→Ω. No exceptions for emergency. Hard blocks exist to prevent security debt.

### Q: Can I force-push to origin?
**A**: Never. Force-push is forbidden (Ω-rule). If you must rewrite history (rare), use git reset --soft HEAD~N on local, rewrite, then push fresh branch with different name.

### Q: How do I handle circular dependency in modules?
**A**: Fix pom.xml. Extract shared code to new module or restructure parents. Re-run observatory.sh. Circular dependencies indicate architecture smell.

### Q: Can I add TODO as a comment (not code)?
**A**: No. H_TODO blocks all forms: code comments, TODOs in strings, FIXME markers. Either implement or throw UnsupportedOperationException.

---

## 10. Glossary

| Term | Definition |
|------|-----------|
| **Ψ (Psi)** | Observatory phase: observe facts, build state model |
| **Λ (Lambda)** | Build phase: compile, test, validate |
| **H (H-Guards)** | Hard blocks: detect deception patterns (TODO, mock, stub, etc.) |
| **Q (Invariants)** | Enforce: real_impl ∨ throw (no third option) |
| **Ω (Omega)** | Git: atomic commits, immutable history |
| **Receipt** | JSON output from each phase (facts, violations, metrics) |
| **Gate** | Decision point between phases (must pass to proceed) |
| **Drift** | drift(A) → 0: approach zero error as phases complete |
| **Quantum** | One orthogonal axis (Toolchain, Dependency, Schema, Engine, MCP, Resourcing) |

---

## 11. References

- **CLAUDE.md**: Root axioms and CHATMAN equation
- **.claude/rules/TEAMS-GUIDE.md**: Team coordination and error recovery
- **.claude/rules/validation-phases/H-GUARDS-DESIGN.md**: Guard pattern details
- **scripts/observatory/observatory.sh**: Fact discovery implementation
- **scripts/dx.sh**: Build orchestration (wrapper around mvn)

---

**Document Status**: READY FOR REFERENCE | **Maintained By**: Architecture Team | **Last Verified**: 2026-02-28

