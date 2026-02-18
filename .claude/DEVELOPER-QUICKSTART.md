# YAWL v6.0 Developer Quickstart

**2-minute setup. Real workflows. No fluff.**

## 1. First Build

```bash
# From the repo root
cd /home/user/yawl
mvn -T 1.5C compile -o          # ~45s parallel compile
mvn -T 1.5C test -o             # ~90s with tests
```

Requires: Java 25 (minimum Java 21), Maven 3.9+. Network not required—all
dependencies are cached locally. The `-T 1.5C` flag parallelises across 1.5x
CPU cores; omit it only when debugging classpath conflicts.

## 2. Module Map

YAWL uses a **shared-src** layout: all Java source lives in `src/` at the repo
root, but each Maven module compiles a _scoped slice_ of it via
`maven-compiler-plugin` `<includes>` filters. Understanding this prevents 90%
of build confusion. The machine-readable source of truth is
`docs/v6/latest/facts/modules.json`.

| Module | Source Strategy | Owns |
|--------|----------------|------|
| yawl-utilities | full_shared (`../src` + includes) | auth, logging, schema, unmarshal, util, exceptions |
| yawl-elements | full_shared (`../src` + includes) | elements/**, engine core interfaces |
| yawl-authentication | package_scoped (`../src/org/.../authentication`) | authentication package only |
| yawl-engine | full_shared (`../src` + includes) | engine/**, swingWorklist/**, YEventLogger* |
| yawl-stateless | full_shared (`../src` + includes) | stateless/** |
| yawl-resourcing | full_shared (`../src` + includes) | resourcing/** |
| yawl-worklet | full_shared (`../src` + includes) | worklet/** |
| yawl-scheduling | full_shared (`../src` + includes) | scheduling/** |
| yawl-security | full_shared (`../src` + includes) | security/** |
| yawl-integration | package_scoped (`../src/org/.../integration`) | integration/{mcp,spiffe,dedup,observability} |
| yawl-monitoring | package_scoped (`../src/org/.../observability`) | observability/** |
| yawl-control-panel | full_shared (`../src` + includes) | controlpanel/** |
| yawl-webapps | standard (`src/main/java`) | web application resources |

**Golden rule**: Never add a backwards dependency. The reactor order is:
`yawl-utilities` -> `yawl-elements` -> `yawl-engine` -> everything else.
See `docs/v6/latest/facts/reactor.json` for the full dependency graph.

## 3. Adding Code

Every public method must either do real work or throw
`UnsupportedOperationException` with a clear message. The pre-commit hook
(`hyper-validate.sh`) blocks 14 anti-patterns: TODOs, mocks, stubs, empty
returns, silent fallbacks, and more.

```java
// CORRECT: Real implementation with proper exception propagation
public YWorkItem allocateWorkItem(String caseId) throws YStateException {
    return workItemRepository.findByCaseId(caseId)
        .orElseThrow(() -> new YStateException("No work item for case: " + caseId));
}

// BLOCKED by pre-commit hook (14 variants caught):
public YWorkItem allocateWorkItem(String caseId) {
    return null; // stub
}
```

When a feature is genuinely not yet implemented, use:
```java
throw new UnsupportedOperationException("allocateWorkItem not yet supported in stateless mode");
```

## 4. Adding Tests

Tests live in `test/org/yawlfoundation/yawl/<package>/`. Each module scopes its
own tests via `maven-surefire-plugin` `<testIncludes>` configuration—test files
for `yawl-engine` belong in packages under `engine/`, not `elements/`.

```bash
# Run tests for one module only
mvn test -pl yawl-engine -o

# Run a single test class
mvn test -pl yawl-engine -Dtest=YNetRunnerTest -o

# Run with JaCoCo coverage report
mvn test -Djacoco.skip=false -o
bash scripts/coverage-report.sh   # ASCII coverage table
```

Test template (JUnit 5 / YAWL style):
```java
package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("YNetRunner Tests")
class YNetRunnerTest {

    private YNetRunner runner;

    @BeforeEach
    void setUp() {
        // Real objects—no mocks
        runner = new YNetRunner(loadTestSpecification());
    }

    @Test
    @DisplayName("continueIfPossible advances enabled tasks")
    void continueIfPossibleAdvancesEnabledTasks() {
        runner.continueIfPossible();
        assertThat(runner.getEnabledTaskIDs(), is(not(empty())));
    }
}
```

## 5. Quality Gates

Gates are defined in `docs/v6/latest/facts/gates.json`. Active-by-default gates
run on every `mvn verify`; profile-gated checks require `-P <profile>`.

| Gate | Command | Default | Threshold |
|------|---------|---------|-----------|
| Enforcer | `mvn validate -o` | Active | Java/Maven version range |
| Compile | `mvn compile -o` | Active | Must succeed |
| Unit Tests | `mvn test -o` | Active | 100% pass |
| JaCoCo Coverage | `mvn verify -o` | Active | 50% line / 40% branch |
| SpotBugs | `mvn verify -P ci` | Profile-gated | 0 high-priority bugs |
| PMD | `mvn verify -P analysis` | Profile-gated | 0 violations |
| Checkstyle | `mvn verify -P analysis` | Profile-gated | 0 violations |
| OWASP Dep-Check | `mvn verify -P security-audit` | Profile-gated | CVSS < 7 |

**Skip flags carry risk ratings** (`RED`/`YELLOW`). Never use `-DskipTests=true`
or `-Denforcer.skip=true` (both `RED`) in CI or before committing.

```bash
# Full quality check matching CI
mvn -T 1.5C compile test verify -o
```

## 6. Observatory (Codebase Facts)

The Observatory compresses codebase knowledge into small JSON/Mermaid fact files.
Reading a fact file costs ~50 tokens; grepping the same answer costs ~5000 tokens.

```bash
# Refresh all facts (modules, tests, coverage, hazards, diagrams)
bash scripts/observatory/observatory.sh --facts

# Key fact files (read these before exploring source)
cat docs/v6/latest/facts/modules.json       # Module structure and source strategy
cat docs/v6/latest/facts/tests.json         # Test counts per module
cat docs/v6/latest/facts/maven-hazards.json # Build hazards (logging bridge conflicts, etc.)
cat docs/v6/latest/facts/deps-conflicts.json # Dependency version convergence
cat docs/v6/latest/facts/gates.json         # Quality gate definitions and skip-flag risks
cat docs/v6/latest/facts/reactor.json       # Maven reactor order and inter-module deps

# Observatory index
cat docs/v6/latest/INDEX.md
```

When the Observatory is stale, `receipts/observatory.json` SHA256 hashes will
mismatch. Re-run `observatory.sh --facts` to refresh.

## 7. Common Workflows

```bash
# Install git hooks (one-time per clone, blocks guard violations and direct main pushes)
bash scripts/install-git-hooks.sh

# Before committing—compile + test must both pass
mvn -T 1.5C compile test -o
git add <specific-files>        # Never: git add .
git commit -m "feat: description"

# Profile build performance
bash scripts/build-profiler.sh              # Compile timing breakdown
bash scripts/build-profiler.sh --full       # Compile + test timing
bash scripts/build-profiler.sh --trend      # Historical trends (detects regressions)

# View coverage dashboard
bash scripts/coverage-report.sh

# Check for POM hazards before debugging build failures
bash scripts/observatory/observatory.sh --facts
cat docs/v6/latest/facts/maven-hazards.json
```

## 8. When Things Go Wrong

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `H_MISSING_SOURCE_DIR` in hazards | POM `<sourceDirectory>` points to non-existent path | Change to `../src` with `<includes>` filters |
| `ownership_ambiguity` in shared-src.json | Same package in 2+ modules' compiler includes | Remove from one module's `<compilerArgs>` includes |
| Test compile fails on cross-module class | Module test depends on class owned by another module | Add explicit `test` scope dependency or adjust `<testIncludes>` |
| `H_LOGGING_BRIDGE_CONFLICT` | Both `log4j-to-slf4j` and `log4j-slf4j2-impl` on classpath | Add `<classpathDependencyExclude>` to `maven-surefire-plugin` config |
| Guard violation blocks commit | TODO/mock/stub found in staged file | Fix the violation—hook gives exact line numbers |
| Reactor build out of order | Module built before its dependency | Check `docs/v6/latest/facts/reactor.json`, fix `<module>` ordering in root POM |
| `-Denforcer.skip=true` breaks CI | Enforcer gate (`RED` risk) bypassed | Remove skip flag; fix the underlying enforcer violation |

## 9. Branch Convention

```
claude/<description>-<sessionId>   # AI-assisted features (current session)
feature/<description>               # Human-authored features
fix/<description>                   # Bug fixes
docs/<description>                  # Documentation-only changes
```

The pre-push hook blocks direct pushes to `main`/`master`. Always work on a
feature or fix branch and submit a PR.

---
*Last updated: 2026-02-18 | YAWL v6.0.0-Alpha*
*Source of truth for module data: `docs/v6/latest/facts/modules.json`*
