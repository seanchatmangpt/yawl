---
paths:
  - "**/pom.xml"
  - "scripts/dx.sh"
  - "**/.mvn/**"
---

# Λ BUILD Phase — Orchestration Rules

**Phase ordering (strict)**: Compile ≺ Test ≺ Validate ≺ Deploy. Never skip or reorder.

---

## Phase Sequence

### 1. Λ.1 Compile (gate)
```bash
bash scripts/dx.sh compile          # changed modules only
bash scripts/dx.sh compile all      # all modules
```
- Must exit 0 before any test runs
- Java 25 required: dx.sh auto-enforces via JAVA_HOME override
- Offline mode auto-detected from ~/.m2/repository

### 2. Λ.2 Test (gate)
```bash
bash scripts/dx.sh                  # compile + test changed modules (DEFAULT)
bash scripts/dx.sh all              # compile + test all (pre-commit mandatory)
```
- H Guards (hyper-validate.sh) run as PostToolUse — catch violations before test
- Q Invariants (q-phase-invariants.sh) run standalone or post-test
- Coverage enforced in CI profile only (`mvn -P ci clean verify`)

### 3. Λ.3 Validate — Static Analysis
```bash
mvn clean verify -P analysis        # SpotBugs + PMD + Checkstyle
```
- Run when: adding new public APIs, before PR merge
- SpotBugs catches: null dereferences, thread safety, resource leaks
- PMD catches: code complexity, naming violations, copy-paste
- Not part of fast DX loop — reserved for pre-commit/CI

### 4. Λ.4 Deploy — Not in agent scope
- Handled by CI/CD pipeline, not by agents
- Agents: build → test → validate → stop (never deploy)

---

## Error Recovery

### Compile Failure
1. Read full error: `cat /tmp/dx-build-log.txt | grep "ERROR"`
2. Fix the root cause (never suppress errors with `@SuppressWarnings` to make it compile)
3. If missing dependency: check `pom.xml`, add with explicit version
4. Re-run: `bash scripts/dx.sh compile -pl <failing-module>`

### Test Failure
1. Read test output: `cat /tmp/dx-build-log.txt | grep -A 20 "FAILED"`
2. Run single test: `mvn test -pl <module> -Dtest=<TestClass>#<method>`
3. Never skip failing tests with `@Disabled` without a linked issue
4. Fix root cause — no `//noinspection` or `@SuppressWarnings` to hide

### Static Analysis Failure
1. SpotBugs: fix all HIGH/MEDIUM bugs; EXPERIMENTAL may be suppressed with `@SuppressFBWarnings`
2. PMD: fix violations, or add `// NOPMD: <clear reason>` for legitimate suppressions
3. Never suppress without a reason — reviewers will reject silent suppressions

---

## Pre-Commit Gate (mandatory)

```bash
bash scripts/dx.sh all   # ← must exit 0 before git add
```

After GREEN:
```bash
git add <specific-files>          # never git add .
git commit -m "feat: description

https://claude.ai/code/session_<id>"
```

---

## Parallel Build Tuning

| Cores | Maven flag | Use case |
|-------|-----------|----------|
| 2-4 cores | `-T 1C` | Dev machine |
| 4-8 cores | `-T 1.5C` | CI server |
| 8+ cores | `-T 2C` | High-perf build |

- `mvnd` (Maven Daemon) preferred when available — dx.sh auto-detects
- Do not use `-T` with `surefire` parallel if tests share static state

---

## Build Profiles Quick Ref

| Profile | When | Overhead |
|---------|------|----------|
| `agent-dx` | Agent fast loop | Minimal (~5-15s) |
| `java25` | Default | Low (~30s) |
| `ci` | PR + merge gate | Medium (JaCoCo) |
| `analysis` | Weekly + pre-release | High (SpotBugs/PMD) |
| `security` | Release only | Very high (OWASP) |
| `prod` | Deployment gate | Highest |
