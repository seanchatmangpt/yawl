# YAWL v6.0 - Development Guide

**Version:** 6.0.0-Beta
**Platform:** Java 21 + Maven 3.9 + Jakarta EE 10 + Hibernate 6

This guide covers everything a new contributor needs to go from a fresh checkout to a running build, passing tests, and a submitted pull request.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [First-Time Setup](#first-time-setup)
3. [Project Layout](#project-layout)
4. [Building](#building)
5. [Running Tests](#running-tests)
6. [Code Standards](#code-standards)
7. [Git Workflow](#git-workflow)
8. [IDE Setup](#ide-setup)
9. [Common Tasks (Make targets)](#common-tasks)
10. [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Tool | Minimum Version | Install |
|------|----------------|---------|
| Java JDK | 21 (LTS) | https://adoptium.net or SDKMAN |
| Maven | 3.9 | https://maven.apache.org or SDKMAN |
| Git | 2.30 | https://git-scm.com |
| xmllint | any | `apt-get install libxml2-utils` |
| Docker (optional) | 24 | https://www.docker.com |

SDKMAN is the recommended way to manage Java and Maven versions:

```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.7-tem
sdk install maven 3.9.11
```

---

## First-Time Setup

```bash
git clone <repo-url>
cd yawl
./setup-dev.sh
```

`setup-dev.sh` does the following in one pass:

1. Verifies Java 21+ is on `PATH` and `JAVA_HOME` is set.
2. Verifies Maven 3.9+ is on `PATH`.
3. Installs the YAWL git pre-commit hook (enforces coding standards).
4. Creates `.env` from `.env.example` if no `.env` exists.
5. Warms the Maven dependency cache (`mvn dependency:resolve`).
6. Runs a fast compile to confirm the environment works (`mvn clean compile`).

If any step fails it prints the exact remediation command.

To verify an existing setup without changing anything:

```bash
./setup-dev.sh --verify
```

---

## Project Layout

```
yawl/
  yawl-utilities/        Shared utilities (logging, XML, collections)
  yawl-elements/         Core workflow elements (YTask, YNet, YSpecification)
  yawl-authentication/   User auth and session management
  yawl-engine/           Stateful workflow engine (YEngine, YNetRunner)
  yawl-stateless/        Stateless engine (YStatelessEngine, YCaseMonitor)
  yawl-resourcing/       Resource allocation and workqueues
  yawl-worklet/          Worklet selection and substitution
  yawl-scheduling/       Temporal and resource scheduling
  yawl-security/         SPIFFE/SVID-based mutual TLS
  yawl-integration/      MCP server, A2A server, REST API bridges
  yawl-monitoring/       Metrics, health checks, observability
  yawl-webapps/          Jakarta Faces web front-end
  yawl-control-panel/    Administrative control panel
  src/                   Legacy monolithic source tree (migration in progress)
  schema/                YAWL_Schema4.0.xsd and supporting XSD files
  exampleSpecs/          Example .yawl workflow specifications
  test/                  Integration and shell-based tests
  .claude/               Claude Code agent scripts and hooks
```

Key entry points:

- `org.yawlfoundation.yawl.engine.YEngine` - stateful singleton engine
- `org.yawlfoundation.yawl.stateless.YStatelessEngine` - stateless engine
- `org.yawlfoundation.yawl.elements.YSpecification` - workflow specification graph
- `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer` - MCP protocol server
- `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer` - A2A agent server

---

## Building

### Fast compile (recommended during development)

```bash
./build.sh
# or
mvn clean compile
```

### Full build with tests and packaging

```bash
./build.sh --package
# or
mvn clean package
```

### Parallel build (faster on multi-core machines)

```bash
./build.sh --parallel
# or
mvn clean compile -T 1C
```

### Single-module build

```bash
./build.sh --module=yawl-engine
```

### Clean only

```bash
./build.sh --clean
# or
mvn clean
```

### SpotBugs static analysis

```bash
./build.sh --spotbugs
```

---

## Running Tests

### Unit tests (fast, H2 in-memory database)

```bash
./test.sh
# or
mvn test
```

### Tests with JaCoCo coverage report

```bash
./test.sh --coverage
# Report: target/site/jacoco/index.html
```

### Single test class

```bash
./test.sh --class=TestYEngineInit
./test.sh --class=org.yawlfoundation.yawl.engine.TestYEngineLifecycle
```

### Engine tests only

```bash
./test.sh --engine
```

### Integration tests (requires running database)

```bash
# Using H2 (no external service needed):
./test.sh --integration

# Using PostgreSQL:
DB_TYPE=postgresql DB_HOST=localhost DB_NAME=yawl_test DB_USER=yawl ./test.sh --integration
```

### Schema validation

Validates all `.yawl` example specifications against `YAWL_Schema4.0.xsd`:

```bash
./test.sh --schema
# or
xmllint --schema schema/YAWL_Schema4.0.xsd exampleSpecs/OrderFulfillment.yawl
```

### Lint (HYPER_STANDARDS stub/mock scanner)

```bash
./test.sh --lint
```

### Shell-based integration test suite (Makefile)

```bash
make test-quick     # Schema + stub detection + build (no server required)
make test-schema    # Phase 01: Schema validation
make test-stub      # Phase 02: Stub/mock detection
make test-build     # Phase 03: Build verification
make test           # All 8 phases (requires running engine)
make help           # Full list of make targets
```

---

## Code Standards

YAWL enforces **HYPER_STANDARDS** — a zero-tolerance policy against deferred work, mocks, and stubs.

### The 14 forbidden patterns

| Pattern | Example | Why forbidden |
|---------|---------|---------------|
| TODO / FIXME / XXX / HACK | `// TODO: implement` | Deferred work |
| Mock method names | `getMockData()` | Fake behaviour |
| Mock class names | `class MockService` | Fake behaviour |
| Mock mode flags | `boolean useMock = true` | Dual-mode code |
| Empty string returns | `return "";` | Stub |
| Null with stub comment | `return null; // stub` | Stub |
| No-op method bodies | `public void run() { }` | Stub |
| Placeholder constants | `DUMMY_CONFIG = "x"` | Placeholder |
| Silent catch fallbacks | `catch (e) { return fake(); }` | Silent failure |
| Conditional mocks | `if (testMode) return fake()` | Dual-mode code |
| Suspicious getOrDefault | `.getOrDefault(k, "test")` | Fake default |
| Logic skip (`if true`) | `if (true) return;` | Logic skip |
| Log instead of throw | `log.warn("not implemented")` | Silent failure |
| Mockito imports in src/ | `import org.mockito.*` | Test pollution |

### The two valid choices for unimplemented code

```java
// Choice 1: Implement the real feature.
public String getRealData(String key) {
    return persistenceLayer.fetchById(key);
}

// Choice 2: Declare explicitly unsupported.
public String getFeatureX() {
    throw new UnsupportedOperationException(
        "Feature X is not yet supported in this engine version. " +
        "See GitHub issue #123."
    );
}
```

### Enforcement

The pre-commit hook runs `validate-no-mocks.sh` before every commit. It blocks the commit and prints the exact violation lines if any forbidden pattern is detected. Install it with:

```bash
./install-hooks.sh
```

The `hyper-validate.sh` hook also runs after each file write/edit by Claude Code itself.

---

## Git Workflow

### Branch naming

```
main          Production-ready code
develop       Integration branch
claude/<desc>-<sessionId>   Claude Code automated branches
feature/<desc>              Manual feature branches
bugfix/<desc>               Bug fix branches
release/<version>           Release preparation
```

### Pre-commit checklist

Before every commit:

```bash
mvn clean compile     # Must succeed
mvn clean test        # Must pass 100%
./test.sh --lint      # Must show zero violations
```

The pre-commit hook enforces the first two automatically; it does not block on lint violations but does print a warning.

### Commit message format

```
<type>(<scope>): <short summary>

<optional body>

https://claude.ai/code/<session-id>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`.

Example:

```
feat(engine): add case-level priority scheduling

YNetRunner now respects YCasePriority when selecting the next enabled task
in a multi-case scenario. Priorities 1-10 are supported, with 1 being
highest. Cases with equal priority are processed FIFO.

https://claude.ai/code/session_abc123
```

---

## IDE Setup

### IntelliJ IDEA

1. Open the project root as a Maven project (`File > Open`).
2. IDEA will detect `.idea/codeStyles/Project.xml` and `.idea/inspectionProfiles/Project_Default.xml` automatically. Accept the project settings.
3. Set the Project SDK to Java 21+ (`File > Project Structure > Project > SDK`).
4. Enable annotation processing (`File > Settings > Build > Compiler > Annotation Processors > Enable`).
5. IDEA will respect `.editorconfig` for indentation and line endings.

Recommended plugins:

- **Maven Helper** — visualise dependency trees
- **SonarLint** — real-time code quality feedback
- **GitToolBox** — enriched git information inline

### VS Code

Install the recommended extensions listed in `.devcontainer/devcontainer.json`:

- `redhat.java` (Language Support for Java)
- `vscjava.vscode-java-test` (Test Runner for Java)
- `vscjava.vscode-maven` (Maven for Java)

VS Code respects `.editorconfig` through the EditorConfig plugin or natively in recent versions.

### Eclipse / STS

1. Import as an existing Maven project.
2. Install the EditorConfig plugin from the Eclipse Marketplace.
3. Import the code style from `.idea/codeStyles/Project.xml` (manually via `Window > Preferences > Java > Code Style`).

---

## Common Tasks

All `make` targets are documented in the `Makefile`. Key targets:

```bash
make help             # Show all targets with descriptions
make test-quick       # Schema + stub + build (no server)
make test             # Full 8-phase test suite
make test-schema      # Validate .yawl files against XSD
make test-stub        # Scan for forbidden patterns
make check-deps       # Verify CLI tool dependencies
make clean            # Remove test artefacts

# Resilience4j operations (requires running engine)
make resilience-health     # Check circuit breaker health
make resilience-metrics    # View all resilience metrics
make resilience-dashboard  # Full resilience dashboard
```

---

## Troubleshooting

### "JAVA_HOME is not set"

```bash
# Find where your JDK lives:
java -XshowSettings:property -version 2>&1 | grep java.home
# Set JAVA_HOME (add to ~/.bashrc or ~/.zshrc):
export JAVA_HOME=/path/to/jdk
export PATH=$JAVA_HOME/bin:$PATH
```

### "Compilation failed: preview feature requires --enable-preview"

The `.mvn/jvm.config` file includes `--enable-preview`. If running Maven directly without this file:

```bash
mvn clean compile -Djvm.args="--enable-preview"
```

### "Could not resolve dependency"

```bash
mvn dependency:resolve --batch-mode
```

If the dependency is not in Maven Central, check `pom.xml` for custom repository declarations.

### "Tests fail with H2 schema errors"

The in-memory H2 dialect is `org.hibernate.dialect.H2Dialect`. If tests run against a PostgreSQL schema, set:

```bash
DB_TYPE=h2 ./test.sh
```

### "Pre-commit hook blocks my commit"

The hook found a forbidden pattern. Run:

```bash
./test.sh --lint
```

The output lists the exact file and line. Fix the violation — either implement the real feature or throw `UnsupportedOperationException`. Never delete the check or bypass the hook.

### "xmllint not found" (schema validation)

```bash
# Debian/Ubuntu:
apt-get install libxml2-utils

# macOS:
brew install libxml2
```

### SpotBugs reports a false positive

Add an exclusion to `spotbugs-exclude.xml` with a `<BugPattern>` entry explaining why it is a false positive. Do not suppress all checks for a class unless absolutely necessary.
