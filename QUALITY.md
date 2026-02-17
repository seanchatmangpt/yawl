# YAWL v5.2 Code Quality Standards

## Overview

This document defines the code quality infrastructure for YAWL v5.2. All production
code must pass every gate described here before it can be merged.

**Standards level:** Fortune 5 / HYPER_STANDARDS
**Enforced by:** Maven analysis profile, pre-commit hook, GitHub Actions CI


## Quick Reference

```bash
# Full analysis suite (SpotBugs + Checkstyle + PMD + JaCoCo)
mvn clean package -P analysis

# Individual tools
mvn spotbugs:check             # SpotBugs only
mvn spotbugs:gui               # SpotBugs with interactive GUI
mvn checkstyle:check           # Checkstyle only
mvn pmd:check                  # PMD violations only
mvn pmd:cpd-check              # PMD copy-paste detector only
mvn test                       # Unit tests only

# SonarQube (requires SONAR_TOKEN and SONAR_HOST_URL env vars)
mvn clean verify sonar:sonar -P sonar

# Install git hooks (one-time after clone)
bash scripts/install-hooks.sh
```


## Tools

### 1. SpotBugs

**Purpose:** Bytecode analysis for common bug patterns (null dereference, resource leaks,
thread safety, insecure operations).

**Configuration:** `spotbugs-exclude.xml`

**Threshold:** HIGH and MEDIUM priority bugs fail the build.

**Running:**
```bash
mvn spotbugs:check          # fail-on-violation
mvn spotbugs:spotbugs       # generate XML report only
mvn spotbugs:gui            # interactive GUI browser
```

**Reports:** `target/spotbugsXml.xml`, `target/site/spotbugs.html`

**Adding exclusions:**
Exclusions require a Javadoc comment in `spotbugs-exclude.xml` explaining why the
false positive is accepted. Exclusions that suppress entire bug categories without
a per-class scope will be rejected in review.


### 2. Checkstyle

**Purpose:** Source code style enforcement based on a customized Google Java Style Guide.

**Configuration:** `checkstyle.xml`
**Suppressions:** `checkstyle-suppressions.xml`

**Key enforced rules:**
- Line length: 120 characters maximum
- No wildcard imports
- All public types and methods require Javadoc
- Naming conventions: `lowerCamelCase` for methods/variables, `UPPER_SNAKE_CASE` for
  constants, `UpperCamelCase` for types
- Braces required on all control structures
- Modifier order: `public protected private abstract static final`
- Cyclomatic complexity: max 15 per method
- Method length: max 80 lines
- Parameter count: max 7

**Running:**
```bash
mvn checkstyle:check            # fail-on-violation
mvn checkstyle:checkstyle       # generate HTML report only
```

**Reports:** `target/checkstyle-result.xml`, `target/site/checkstyle.html`

**Adding suppressions:**
Every entry in `checkstyle-suppressions.xml` must have a comment explaining:
1. Why the violation is accepted
2. Whether it is temporary (with a target date for removal)


### 3. PMD

**Purpose:** Source-code quality rules covering design, correctness, performance, and
multithreading.

**Configuration:** `pmd-ruleset.xml`
**Exclusions:** `pmd-exclusions.properties`

**Key enforced rules:**
- `UseTryWithResources` - Resources (Connection, InputStream, etc.) must use try-with-resources
- `CloseResource` - Database connections must be closed in finally or try-with-resources
- `AvoidCatchingGenericException` - Catch specific exception types
- `PreserveStackTrace` - Never swallow stack traces when rethrowing
- `NonThreadSafeSingleton` - Double-checked locking must use volatile
- `DoubleCheckedLocking` - Detects broken DCL patterns
- `HardCodedCryptographicKey` - Security: no embedded crypto keys
- `InsecureCryptoIv` - Security: no static IVs
- `CyclomaticComplexity` - Max 15 per method, 80 per class
- `TooManyMethods` - Max 30 public methods per class

**Running:**
```bash
mvn pmd:check               # violations only
mvn pmd:cpd-check           # duplicate code only
mvn pmd:pmd                 # generate XML report
```

**Reports:** `target/pmd.xml`, `target/cpd.xml`, `target/site/pmd.html`


### 4. JaCoCo Code Coverage

**Purpose:** Measures test coverage. Fails the build if coverage drops below the minimum.

**Minimum thresholds (enforced in `analysis` profile):**

| Counter  | Minimum |
|----------|---------|
| Line     | 65%     |
| Branch   | 55%     |

**Module-specific targets (from `codecov.yml`):**

| Module           | Line Target |
|------------------|-------------|
| yawl-engine      | 80%         |
| yawl-elements    | 75%         |
| yawl-stateless   | 75%         |
| yawl-integration | 70%         |
| yawl-utilities   | 70%         |
| All others       | 65%         |

**Running:**
```bash
mvn test -Djacoco.skip=false           # generate coverage (tests must pass)
mvn verify -P analysis                 # enforce minimum thresholds
```

**Reports:** `target/site/jacoco/index.html`


### 5. SonarQube / SonarCloud

**Purpose:** Centralized code quality dashboard with trend tracking, technical debt
estimation, and code smell detection.

**Configuration:** Defined in `pom.xml` under the `sonar` profile.

**Running locally (requires a running SonarQube instance):**
```bash
export SONAR_TOKEN=your_token_here
export SONAR_HOST_URL=http://localhost:9000
mvn clean verify sonar:sonar -P sonar
```

**Running against SonarCloud:**
```bash
export SONAR_TOKEN=your_sonarcloud_token
export SONAR_HOST_URL=https://sonarcloud.io
mvn clean verify sonar:sonar -P sonar
```

**Quality Gate thresholds (configured in SonarQube UI, not Maven):**
- New code coverage: 80%
- New duplicated lines: <3%
- New code smells: 0 BLOCKER, 0 CRITICAL
- New bugs: 0
- New security vulnerabilities: 0
- New security hotspots reviewed: 100%


## Pre-Commit Hook

The pre-commit hook at `.git/hooks/pre-commit` runs before every `git commit`:

1. **HYPER_STANDARDS scan** - Checks staged `.java` files for forbidden patterns
2. **Maven compile** - Verifies the change compiles
3. **SpotBugs** - Checks for bugs in the compiled bytecode
4. **Checkstyle** - Checks code style
5. **Unit tests** - Runs the full test suite

**Install (one-time):**
```bash
bash scripts/install-hooks.sh
```

**Skip (emergencies only - never on main/master):**
```bash
git commit --no-verify -m "EMERGENCY: ..."
```

Bypassing the hook on `main` or `master` will be flagged in the CI summary.


## CI/CD Integration

### Workflows

| Workflow | File | Trigger |
|----------|------|---------|
| PR Quality Gates | `.github/workflows/quality-gates.yml` | All PRs, push to main/develop |
| Full CI/CD Pipeline | `.github/workflows/ci.yml` | Push, PR, schedule |
| Maven CI/CD | `.github/workflows/maven-ci-cd.yml` | Push, PR |

### PR Quality Gates (quality-gates.yml)

All of these gates must pass for a PR to be mergeable:

| Gate | Failure Action |
|------|---------------|
| HYPER_STANDARDS scan | Blocks merge immediately |
| Compile | Blocks merge |
| Unit tests | Blocks merge |
| SpotBugs | Blocks merge |
| Checkstyle | Blocks merge |
| PMD | Blocks merge |
| Coverage (65% line) | Warning only (non-blocking) |


## HYPER_STANDARDS

YAWL enforces 14 forbidden patterns in all production source code.
These are checked by:
- `.claude/hooks/hyper-validate.sh` (post-Write/Edit, blocks immediately)
- Pre-commit hook (before `git commit`)
- CI `hyper-standards` job (on every PR)

**Forbidden patterns:**
1. Deferred work markers: `TODO`, `FIXME`, `XXX`, `HACK`, `LATER`, `FUTURE`
2. Mock method names: `mockFetch()`, `stubValidation()`
3. Mock class names: `class MockService`, `class FakeRepository`
4. Mock mode flags: `boolean useMockData = true`
5. Empty returns: `return "";`
6. Null returns with stub comments: `return null; // stub`
7. No-op methods: `public void method() { }`
8. Placeholder constants: `DUMMY_CONFIG`, `PLACEHOLDER_VALUE`
9. Silent fallbacks: `catch (e) { return mockData(); }`
10. Conditional mocks: `if (isTestMode) return mock();`
11. Fake defaults: `.getOrDefault(key, "test_value")`
12. Logic skipping: `if (true) return;`
13. Log instead of throw: `log.warn("not implemented")`
14. Mock imports in src/: `import org.mockito.*`

**Compliant patterns:**
```java
// Good: real implementation
public WorkItem fetchItem(String id) {
    return repository.findById(id)
        .orElseThrow(() -> new WorkItemNotFoundException("No work item: " + id));
}

// Good: explicit not-implemented with clear message
public void legacyMethod() {
    throw new UnsupportedOperationException(
        "legacyMethod() is not supported in YAWL v5.2. Use fetchItem() instead.");
}
```


## Adding New Rules

### SpotBugs
1. Edit `spotbugs-exclude.xml` to add class-scoped exclusions.
2. To add new detectors, add a SpotBugs plugin dependency to the `spotbugs-maven-plugin` config.

### Checkstyle
1. Edit `checkstyle.xml` to add or modify rules.
2. Edit `checkstyle-suppressions.xml` to add file-specific suppressions.
3. Test locally: `mvn checkstyle:check`

### PMD
1. Edit `pmd-ruleset.xml` to add rules.
2. Edit `pmd-exclusions.properties` to suppress known violations.
3. Test locally: `mvn pmd:check`

### Coverage thresholds
1. Edit the `jacoco-check` execution in the `analysis` profile in `pom.xml`.
2. Edit `codecov.yml` for per-module targets.


## Viewing Reports

After running `mvn clean package -P analysis`:

| Report | Location |
|--------|----------|
| SpotBugs HTML | `target/site/spotbugs.html` |
| SpotBugs XML | `target/spotbugsXml.xml` |
| Checkstyle HTML | `target/site/checkstyle.html` |
| Checkstyle XML | `target/checkstyle-result.xml` |
| PMD HTML | `target/site/pmd.html` |
| PMD XML | `target/pmd.xml` |
| CPD HTML | `target/site/cpd.html` |
| JaCoCo HTML | `target/site/jacoco/index.html` |
| JaCoCo XML | `target/site/jacoco/jacoco.xml` |

Open HTML reports:
```bash
open target/site/jacoco/index.html
open target/site/spotbugs.html
open target/site/checkstyle.html
open target/site/pmd.html
```

Or on Linux:
```bash
xdg-open target/site/jacoco/index.html
```


## Configuration Files Reference

| File | Purpose |
|------|---------|
| `checkstyle.xml` | Checkstyle rule definitions |
| `checkstyle-suppressions.xml` | Per-file Checkstyle suppressions |
| `pmd-ruleset.xml` | PMD rule definitions |
| `pmd-exclusions.properties` | Per-file PMD suppressions |
| `spotbugs-exclude.xml` | SpotBugs false-positive exclusions |
| `owasp-suppressions.xml` | OWASP CVE suppressions |
| `codecov.yml` | Codecov coverage thresholds and display |
| `pom.xml` (analysis profile) | Maven plugin versions and binding |
| `.github/workflows/quality-gates.yml` | PR CI quality gate pipeline |
| `scripts/install-hooks.sh` | One-time hook installer |
| `.git/hooks/pre-commit` | Pre-commit quality gate |
| `.claude/hooks/hyper-validate.sh` | Real-time HYPER_STANDARDS enforcement |
