---
paths:
  - "**/pom.xml"
  - "**/scripts/**"
  - "**/.mvn/**"
---

# Build System Rules

## Agent DX Fast Loop (Preferred)
- `bash scripts/dx.sh` - Compile + test CHANGED modules only (~5-15s)
- `bash scripts/dx.sh compile` - Compile changed modules only (fastest)
- `bash scripts/dx.sh test` - Test changed modules (assumes compiled)
- `bash scripts/dx.sh all` - Full 6-phase pipeline: observe(Ψ)→compile→test→guards(H)→invariants(Q)→report
- `bash scripts/dx.sh -pl yawl-engine` - Target specific module

## dx.sh all — Phase Pipeline

| Phase | Symbol | Description | Skip condition |
|-------|--------|-------------|----------------|
| observe | Ψ | Check observatory facts; auto-refresh if pom.xml changed | `DX_SKIP_OBSERVE=1` or `--offline` |
| compile | Λ | Compile changed (or all) modules | — |
| test | Λ | Run JUnit tests | — |
| guards | H | hyper-validate.sh: 7 forbidden patterns | `--skip-validate` |
| invariants | Q | real_impl ∨ throw UnsupportedOperationException | `--skip-validate` |
| report | Ω | Emit phase-status.json summary | `--skip-validate` |

```bash
# Full pre-commit gate
bash scripts/dx.sh all

# Skip validation phases (fast compile+test only)
bash scripts/dx.sh all --skip-validate

# Skip observe phase (CI — session-start.sh pre-generates facts)
DX_SKIP_OBSERVE=1 bash scripts/dx.sh all
```

## Maven Commands
- Always use `-T 1.5C` for parallel execution (1.5x CPU cores)
- `mvn -T 1.5C clean compile` - Full parallel compile (~45s)
- `mvn -T 1.5C clean test` - Full parallel tests
- `mvn -T 1.5C clean package` - Full build (~90s)

## Build Profiles
- `java25` (default) - Java 25 features, no JaCoCo
- `agent-dx` - Max speed: 2C parallelism, fail-fast, no overhead
- `ci` - JaCoCo + SpotBugs
- `analysis` - JaCoCo + SpotBugs + Checkstyle + PMD
- `security` - SBOM + OWASP dependency check
- `prod` - Full production validation (fails >= CVSS 7)

## Before Committing
- Run `bash scripts/dx.sh all` (fast) or full Maven compile + test
- Stage specific files with `git add <files>` (never `git add .`)
- Include session URL in commit message

## Enforcer Rules (Build Fails If)
- Maven < 3.9 or Java < 25
- Duplicate dependency declarations in any POM
- Any plugin lacks explicit version
