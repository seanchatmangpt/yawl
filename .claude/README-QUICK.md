# YAWL Quick Start - 80/20 Guide

**Get 80% done in 20% of the time.**

---

## 10-Second Start (Maven/Java 25)

```bash
# 1. Fast build-test loop (RECOMMENDED)
bash scripts/dx.sh                # Auto-detect changed modules (~5-15s)

# 2. Compile only (fastest feedback)
bash scripts/dx.sh compile        # ~3-5s per module

# 3. All modules (pre-commit)
bash scripts/dx.sh all            # ~30-60s

# 4. Target specific module
bash scripts/dx.sh -pl yawl-engine
```

**Done.** You're now productive.

---

## The 20% You Need to Know

### 1. Fast Build-Test Loop (dx.sh)

```bash
# Auto-detect changed modules, compile + test
bash scripts/dx.sh

# Compile only (fastest possible feedback)
bash scripts/dx.sh compile

# All modules (pre-commit verification)
bash scripts/dx.sh all

# Target specific modules
bash scripts/dx.sh -pl yawl-engine,yawl-stateless

# Environment overrides
DX_VERBOSE=1 bash scripts/dx.sh     # Show Maven output
DX_CLEAN=1 bash scripts/dx.sh       # Force clean build
```

**Performance comparison:**

| Command | Scope | Time |
|---------|-------|------|
| `bash scripts/dx.sh compile` | 1 module | ~3-5s |
| `bash scripts/dx.sh` | 1 module | ~5-15s |
| `bash scripts/dx.sh all` | all modules | ~30-60s |
| `mvn -T 1.5C clean compile && test` | all modules | ~90-120s |

### 2. Maven Profiles

```bash
# Agent DX (fastest, for development inner-loop)
mvn -T 1.5C clean test -P agent-dx    # 2C parallelism, fail-fast, no overhead

# Fast (minimal analysis overhead)
mvn -T 1.5C clean test -P fast        # Skip JaCoCo, SpotBugs, PMD

# Analysis (full static analysis)
mvn -T 1.5C clean verify -P analysis  # SpotBugs, PMD, JaCoCo, SonarQube

# Security (OWASP dependency check, SBOM)
mvn clean verify -P security          # Security scanning, SBOM generation
```

**Profile comparison:**

| Profile | Use Case | Overhead |
|---------|----------|----------|
| `agent-dx` | Development inner-loop | Zero |
| `fast` | Quick verification | Minimal |
| `analysis` | Pre-commit, CI | Full |
| `security` | Release, deployment | Full + security |

### 3. Module Targeting

```bash
# Build specific module + dependencies
mvn -T 1.5C clean compile -pl yawl-engine -am

# Build specific module + dependents
mvn -T 1.5C clean compile -pl yawl-engine -amd

# Build multiple modules
mvn -T 1.5C clean compile -pl yawl-engine,yawl-stateless,yawl-elements

# dx.sh also supports module targeting
bash scripts/dx.sh -pl yawl-engine
```

### 4. Environment Detection (Most Important)

```java
import org.yawlfoundation.yawl.util.EnvironmentDetector;

// This ONE line determines everything:
if (EnvironmentDetector.isClaudeCodeRemote()) {
    // Remote: H2 database, skip integration tests
} else {
    // Local: PostgreSQL, full testing
}
```

### 5. Standard Maven Commands

```bash
# Parallel compile (~45s)
mvn -T 1.5C clean compile

# Parallel tests (~60-90s)
mvn -T 1.5C clean test

# Full build (~90-120s)
mvn -T 1.5C clean package

# With analysis (~2-3 min)
mvn -T 1.5C clean verify -P analysis

# Install to local repository
mvn -T 1.5C clean install -DskipTests
```

### 6. Configuration (Auto-Managed)

**Don't touch these files:**
- `build/build.properties` -> Auto-symlinked (legacy)
- `pom.xml` -> Maven configuration (primary)

**Just let the SessionStart hook handle it.**

---

## Command Cheat Sheet

| Task | Command | Time |
|------|---------|------|
| Fast build-test (changed) | `bash scripts/dx.sh` | 5-15s |
| Compile only (changed) | `bash scripts/dx.sh compile` | 3-5s |
| All modules | `bash scripts/dx.sh all` | 30-60s |
| Parallel compile | `mvn -T 1.5C clean compile` | 45s |
| Parallel tests | `mvn -T 1.5C clean test` | 60-90s |
| Agent DX profile | `mvn -T 1.5C clean test -P agent-dx` | 30-60s |
| Security scan | `mvn clean verify -P security` | 2-3 min |
| Verify setup | `java -cp classes org.yawlfoundation.yawl.util.QuickTest` | 1s |
| Check env | `./.claude/quick-start.sh env` | 1s |

---

## What NOT to Do

1. NO: Run `mvn clean` every time (use `dx.sh` for incremental builds)
2. NO: Build all modules when you changed one (use `dx.sh` or `-pl`)
3. NO: Use the `fast` profile for CI (use `analysis` or `security`)
4. NO: Commit built JARs (already in .gitignore)
5. NO: Skip the pre-commit verification (`bash scripts/dx.sh all`)

---

## Key Insights

### dx.sh vs Standard Maven

| Feature | dx.sh | Standard Maven |
|---------|-------|----------------|
| **Scope** | Changed modules only | All modules |
| **Incremental** | Yes (no clean) | With `clean`, no |
| **Overhead** | Zero (agent-dx profile) | Full (JaCoCo, javadoc) |
| **Use case** | Inner-loop development | CI/CD, pre-commit |

### Local vs Remote (Auto-Detected)

| Feature | Local (Docker) | Remote (Claude Code Web) |
|---------|----------------|--------------------------|
| **Database** | PostgreSQL | H2 (in-memory) |
| **Setup** | Manual | Auto (SessionStart hook) |
| **Tests** | All tests | Unit tests only |
| **Build Tool** | Pre-installed | Auto-installed |
| **Persistence** | Saved | Ephemeral |

**You don't configure this. `EnvironmentDetector` does.**

---

## Advanced (20% More Knowledge)

### dx.sh Environment Variables

```bash
DX_VERBOSE=1 bash scripts/dx.sh     # Show Maven output
DX_CLEAN=1 bash scripts/dx.sh       # Force clean build
DX_OFFLINE=0 bash scripts/dx.sh     # Force online mode
DX_FAIL_AT=end bash scripts/dx.sh   # Run all modules even on failure
```

### Multi-Module Project Structure

```
yawl/
  pom.xml                 # Parent POM
  yawl-engine/            # Engine module
  yawl-stateless/         # Stateless engine
  yawl-elements/          # Domain elements
  yawl-integration/       # MCP/A2A integration
  yawl-resourcing/        # Resource management
  yawl-worklet/           # Worklet service
  ...
```

### Dependency Management

```bash
# View dependency tree
mvn dependency:tree

# Check for updates
mvn versions:display-dependency-updates

# Analyze dependencies
mvn dependency:analyze

# Generate SBOM
mvn cyclonedx:makeBom
```

### Running Specific Tests

```bash
# Single test class
mvn test -Dtest=YEngineTest

# Single test method
mvn test -Dtest=YEngineTest#testLaunchCase

# Tests matching pattern
mvn test -Dtest=*Pattern*Test

# Specific module tests
mvn test -pl yawl-engine -Dtest=YEngineTest
```

---

## Full Documentation

- **Build system:** `docs/BUILD.md` (dx.sh, Maven, Java 25)
- **Java 25 deployment:** `docs/deployment/JAVA25-GUIDE.md`
- **Observatory:** `.claude/OBSERVATORY.md` (codebase facts)
- **Detailed capabilities:** `.claude/CAPABILITIES.md`
- **YAWL guide:** `CLAUDE.md`
- **Build performance:** `.claude/BUILD-PERFORMANCE.md`
- **Java 25 features:** `.claude/JAVA-25-FEATURES.md`
- **MCP Server:** `docs/integration/MCP-SERVER-GUIDE.md`
- **A2A Server:** `docs/integration/A2A-SERVER-GUIDE.md`

---

## Troubleshooting

### dx.sh shows "no changed modules"

```bash
# Check git status
git status

# Force all modules
bash scripts/dx.sh all
```

### Build fails with "Java version mismatch"

```bash
# Check Java version
java -version  # Should be 25+

# Set JAVA_HOME
export JAVA_HOME=/path/to/jdk-25
```

### Tests fail with database errors

```bash
# Check database configuration:
grep database.type build/build.properties

# Should be:
#   h2 (remote) or postgres (local)
```

### "Module not found" error

```bash
# List available modules
mvn -q -Dexec.executable=echo -Dexec.args='${project.modules}'

# Or check parent pom.xml <modules> section
```

### Maven out of memory

```bash
# Set MAVEN_OPTS
export MAVEN_OPTS="-Xmx2g -XX:+UseZGC"

# Or in .mavenrc
echo '-Xmx2g' > ~/.mavenrc
```

---

## Success Criteria

You're ready when:

```bash
$ bash scripts/dx.sh compile
[INFO] Scanning for projects...
[INFO] Building YAWL Parent 6.0.0-Alpha
[INFO] Compiling 1500 source files...
[INFO] BUILD SUCCESS

$ bash scripts/dx.sh all
[INFO] All modules compiled and tested successfully
[INFO] Total time: 45.23 s
```

---

**That's it. 80% of what you need in 20% of the reading time.**

For the remaining 20%, read `.claude/CAPABILITIES.md`.
