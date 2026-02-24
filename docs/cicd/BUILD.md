# YAWL v6.0.0 Maven Build System Guide

## Quick Start - Agent DX Loop (RECOMMENDED)

For iterative development and code agent workflows, use the fast build-test loop:

```bash
# Auto-detect changed modules, compile + test them (~5-15s)
bash scripts/dx.sh

# Compile only (fastest possible feedback, ~3-5s per module)
bash scripts/dx.sh compile

# Test only (assumes already compiled)
bash scripts/dx.sh test

# All modules (pre-commit verification, ~30-60s)
bash scripts/dx.sh all

# Target specific module
bash scripts/dx.sh -pl yawl-engine

# Environment overrides
DX_VERBOSE=1 bash scripts/dx.sh     # Show Maven output
DX_CLEAN=1 bash scripts/dx.sh       # Force clean build
```

**Why dx.sh is faster:**
- Module targeting: Only builds modules with uncommitted changes
- Incremental compilation: No `clean` phase by default
- Zero overhead: `agent-dx` profile disables JaCoCo, javadoc, analysis

**Performance comparison** (16-core machine):

| Command | Scope | Time |
|---------|-------|------|
| `bash scripts/dx.sh compile` | 1 changed module | ~3-5s |
| `bash scripts/dx.sh` | 1 changed module | ~5-15s |
| `bash scripts/dx.sh all` | all 13 modules | ~30-60s |
| `mvn -T 1.5C clean compile && mvn -T 1.5C test` | all modules | ~90-120s |

See [Phase 0: Agent DX Fast Loop](#phase-0-agent-dx-fast-loop-scriptsdxsh) for details.

---

## Architecture Overview

YAWL uses **Maven 3.9.11** with **Java 25** and a multi-module structure designed for parallel compilation, modular testing, and flexible deployment.

```
YAWL Parent (6.0.0)
├── yawl-utilities (foundation)
├── yawl-elements (core elements)
├── yawl-authentication
├── yawl-engine (stateful)
├── yawl-stateless (cloud-ready)
├── yawl-resourcing
├── yawl-worklet
├── yawl-scheduling
├── yawl-security (PKI/crypto)
├── yawl-integration (MCP/A2A)
├── yawl-monitoring
├── yawl-webapps (war deployment)
└── yawl-control-panel
```

## Build Lifecycle

### Standard Maven Phases
```
clean → validate → compile → test → package → verify → install → deploy
```

### YAWL Custom Build Sequence
```
1. clean
   ↓ (removes target/)
2. compile
   ↓ (runs Java compilation with Java 25 features)
3. test
   ↓ (runs JUnit 5.14.0 LTS in parallel, method-level concurrency)
4. package
   ↓ (creates JAR/WAR artifacts)
5. verify
   ↓ (SpotBugs static analysis - optional, via -P analysis)
6. install
   ↓ (copies to local ~/.m2 repository)
```

## Configuration Files

### 1. Root POM (`pom.xml`)

**Location**: `/home/user/yawl/pom.xml`

**Key Sections**:

```xml
<groupId>org.yawlfoundation</groupId>
<artifactId>yawl-parent</artifactId>
<version>6.0.0-Alpha</version>
<packaging>pom</packaging>
```

**Modules**: 15 child modules (see above)

**Properties**:
```
- maven.compiler.source/target: 21
- Java version: 21.0.10
- Compiler args: --enable-preview, -Xlint:all
```

### 2. .mvn Configuration Files

#### `.mvn/maven.config`
```bash
-Dmaven.artifact.threads=8    # 8 parallel artifact resolution threads
-Dmaven.build.cache.enabled=false  # Disabled for offline mode
-B                             # Batch mode (no interaction)
```

#### `.mvn/jvm.config`
```bash
-Xms512m          # Min heap: 512MB
-Xmx2g            # Max heap: 2GB
-XX:+UseZGC       # Use low-latency garbage collector
--enable-preview  # Java preview features
```

#### `.mvn/extensions.xml`
```xml
<!-- DISABLED: Build cache extension incompatible with offline mode -->
<!-- Re-enable for online builds if desired -->
```

### 3. Module POMs

Each module has its own `pom.xml`:

**Structure**:
```xml
<parent>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Alpha</version>
</parent>

<artifactId>yawl-{module}</artifactId>
<name>YAWL {Description}</name>
<description>...</description>

<dependencies>
    <!-- Inherit from parent dependencyManagement -->
</dependencies>
```

## Dependency Management

### Explicit Versioning Strategy

All 150+ dependencies are **explicitly versioned** (no BOMs) for:
- Offline compatibility
- Clear version tracking
- Dependency graph visibility

### Core Dependency Versions

| Category | Library | Version |
|----------|---------|---------|
| **Language** | Java | 21.0.10 |
| **Build** | Maven | 3.9.11 |
| **EE Framework** | Jakarta EE | 10.0.0 |
| **ORM** | Hibernate | 6.6.42.Final |
| **Web Framework** | Spring Boot | 3.5.10 |
| **Logging** | Log4j2 | 2.25.3 |
| **Logging** | SLF4J | 2.0.17 |
| **JSON** | Jackson | 2.19.4 |
| **XML** | JDOM2 | 2.0.6.1 |
| **Database** | H2 | 2.4.240 |
| **Database** | PostgreSQL | 42.7.7 |
| **Database** | MySQL | 9.4.0 |
| **Testing** | JUnit 5 | 6.0.3 |
| **Testing** | Hamcrest | 3.0 |
| **Observability** | OpenTelemetry | 1.52.0 |
| **Resilience** | Resilience4j | 2.3.0 |

### Database Support Matrix

```
Supported:
  - H2 2.4.240 (in-memory, embedded - default for testing)
  - PostgreSQL 42.7.7 (recommended for production)
  - MySQL 9.4.0 (supported)
  - Derby 10.17.1.0 (Java embedded)
  - HSQLDB 2.7.4 (in-memory)

Connection Pooling:
  - HikariCP 7.0.2 (default, high performance)
```

## Build Plugins

### Compiler Plugin
**Purpose**: Compile Java 21 code with preview features

```
Configuration:
  - Release: 21
  - Source: 21
  - Target: 21
  - Args: --enable-preview, -Xlint:all
```

### Surefire Test Plugin
**Purpose**: Execute JUnit 5 tests in parallel

```
Configuration:
  - Test includes: **/*Test.java, **/*Tests.java, **/*TestSuite.java
  - Parallel mode: classes
  - Thread count: 4
  - System properties:
    * database.type: h2
    * hibernate.dialect: H2Dialect
```

### JAR Plugin
**Purpose**: Package compiled classes into executable JAR

```
Configuration:
  - Main-Class: org.yawlfoundation.yawl.controlpanel.YControlPanel
  - Include manifest with classpath
```

### Unused Plugins (Network-Dependent)

The following plugins are disabled in offline mode:
- JaCoCo (code coverage - requires plugin download)
- OWASP Dependency Check (security scanning)
- SpotBugs (static analysis)
- Maven Enforcer (dependency rules)
- Maven Shade (fat JAR creation)

## Build Profiles

### Profile 1: `agent-dx` (DEFAULT for dx.sh)
```
Activation: Auto-activated by scripts/dx.sh
Java version: 25
Features:
  - 2C test parallelism (doubled)
  - Fail-fast on first test failure
  - JaCoCo disabled
  - Javadoc disabled
  - Static analysis disabled
  - Integration tests excluded
Use case: Fast inner-loop development, code agent workflows
```

### Profile 2: `fast`
```
Activation: Manual (-Pfast)
Java version: 25
Features:
  - Standard test parallelism
  - JaCoCo disabled
  - Javadoc disabled
Use case: Quick verification builds
```

### Profile 3: `analysis`
```
Activation: Manual (-P analysis)
Java version: 25
Features:
  - SpotBugs static analysis
  - PMD code smells
  - JaCoCo code coverage (75% threshold)
  - Error Prone compile-time checks
Use case: CI/CD quality gates, pre-release validation
```

### Profile 4: `security`
```
Activation: Manual (-Psecurity)
Features:
  - OWASP Dependency Check (CVSS >= 7)
  - SBOM generation (CycloneDX)
  - Container image scanning
Use case: Production release security scanning
```

### Profile 5: `security-audit`
```
Activation: Manual (-Psecurity-audit)
Features: Comprehensive OWASP Dependency Check (all CVSS)
Use case: Full security audit before release
```

## Build Commands

### Phase 0: Agent DX Fast Loop (scripts/dx.sh)

The fastest feedback path for code agents and iterative development:

```bash
# Auto-detect changed modules, compile + test them
bash scripts/dx.sh

# Compile only (fastest possible feedback)
bash scripts/dx.sh compile

# Test only (assumes already compiled)
bash scripts/dx.sh test

# All modules (pre-commit verification)
bash scripts/dx.sh all

# Target specific module(s)
bash scripts/dx.sh -pl yawl-engine,yawl-stateless

# Environment overrides
DX_VERBOSE=1 bash scripts/dx.sh     # Show Maven output
DX_CLEAN=1 bash scripts/dx.sh       # Force clean build
DX_OFFLINE=0 bash scripts/dx.sh     # Force online mode
DX_FAIL_AT=end bash scripts/dx.sh   # Don't stop on first failure
```

**Key optimizations:**
- **Module targeting**: Only compile/test modules with uncommitted changes
- **Incremental compilation**: Skip `clean` - only recompile changed files
- **Zero overhead**: `agent-dx` profile disables JaCoCo, javadoc, analysis, enforcer

**Maven profile `agent-dx`:**
- `surefire.threadCount=2C` (double default parallelism)
- `skipAfterFailureCount=1` (fail on first test failure)
- All overhead plugins disabled

### Phase 1: Fast Development Cycle (~45 seconds)
```bash
# Parallel compile (no tests)
mvn -T 1.5C clean compile

# Why fast: Parallel execution at 1.5x CPU cores
```

### Phase 2: Full Testing (~60-90 seconds)
```bash
# Compile and run all tests in parallel
mvn -T 1.5C clean test

# Key: Parallel test execution (method-level with JUnit 5)
```

### Phase 3: Production Build (~90-120 seconds)
```bash
# Full build with tests and packaging
mvn -T 1.5C clean package

# Includes:
# - Compilation
# - Test execution (parallel)
# - JAR/WAR packaging
```

### Phase 4: Analysis Build (~2-3 minutes)
```bash
# Full build with static analysis
mvn -T 1.5C clean verify -P analysis

# Includes SpotBugs, PMD, JaCoCo coverage
```

### Offline Build
```bash
# After dependencies cached (first network-enabled build)
mvn -o clean compile  # Offline mode with cached dependencies
```

### Specific Operations

```bash
# Compile only (useful during active development)
mvn compile

# Run tests without recompilation
mvn test

# Run specific test class
mvn test -Dtest=YWorkItemTest

# Run specific test method
mvn test -Dtest=YWorkItemTest#testInitialization

# Skip tests entirely
mvn clean install -DskipTests

# Parallel test execution (override default 4 threads)
mvn test -T 2  # Use 2 threads instead

# Generate JavaDoc
mvn javadoc:javadoc

# Check dependency tree
mvn dependency:tree

# Find unused dependencies
mvn dependency:analyze

# Find outdated dependency versions
mvn versions:display-dependency-updates

# Security scanning (requires network)
mvn -Psecurity-audit clean install
```

## Build Performance

### Performance Metrics (Java 25 + Parallel Maven)

| Command | Before Optimization | After Optimization | Improvement |
|---------|---------------------|-------------------|-------------|
| `bash scripts/dx.sh compile` | N/A | ~3-5s | New feature |
| `bash scripts/dx.sh` (1 module) | N/A | ~5-15s | New feature |
| `bash scripts/dx.sh all` | N/A | ~30-60s | New feature |
| `mvn -T 1.5C clean compile` | ~90s | ~45s | **-50%** |
| `mvn -T 1.5C clean test` | ~180s | ~60-90s | **-50%** |
| `mvn -T 1.5C clean package` | ~240s | ~90-120s | **-50%** |

### Estimated Times (Network-enabled environment)

| Command | Time | Notes |
|---------|------|-------|
| `bash scripts/dx.sh compile` | 3-5s | 1 changed module |
| `bash scripts/dx.sh` | 5-15s | 1 changed module |
| `bash scripts/dx.sh all` | 30-60s | All 13 modules |
| `mvn -T 1.5C clean compile` | 45s | Parallel compile |
| `mvn -T 1.5C clean test` | 60-90s | Parallel tests |
| `mvn -T 1.5C clean package` | 90-120s | Full build with packaging |
| `mvn clean verify -P analysis` | 2-3 min | With SpotBugs, PMD, JaCoCo |
| `mvn -Psecurity-audit clean verify` | 3-5 min | Includes security scan |

### Java 25 Performance Features

| Feature | JVM Flag | Benefit |
|---------|----------|---------|
| **Compact Object Headers** | `-XX:+UseCompactObjectHeaders` | 5-10% throughput, 10-20% memory reduction |
| **Virtual Threads** | Automatic (Project Loom) | 99.95% memory reduction for concurrent tasks |
| **Generational ZGC** | `-XX:+UseZGC -XX:ZGenerational=true` | 0.1-0.5ms GC pauses (large heaps) |
| **Shenandoah GC** | `-XX:+UseShenandoahGC` | 1-10ms GC pauses (medium heaps) |
| **AOT Cache** | `-XX:+UseAOTCache` | 25% faster container startup |

### First-Time Build (Offline Environment)

**Issue**: Dependencies not cached
**Solution**: Ensure network access for first build

```bash
# First build: Downloads ~200MB of dependencies
mvn clean install  # 5-10 minutes on broadband

# Subsequent builds: Use cached dependencies
mvn -o clean install  # 2-3 minutes offline
```

### Optimization Tips

1. **Parallel Compilation**
   ```bash
   mvn -T 1C clean install  # 1 thread per core
   ```

2. **Skip Tests**
   ```bash
   mvn clean install -DskipTests  # ~3-5 min
   ```

3. **Offline Mode**
   ```bash
   mvn -o clean install  # Uses cached deps only
   ```

4. **Parallel Test Execution**
   - Default: 4 threads
   - Configure: `-DthreadCount=8` in Surefire plugin

## Observatory Integration

The YAWL Observatory provides pre-computed facts about the codebase, reducing context consumption by 100x compared to ad-hoc exploration.

### Quick Reference

```bash
# Refresh all facts and diagrams
bash scripts/observatory/observatory.sh          # Full run (~17s)

# Facts only (faster)
bash scripts/observatory/observatory.sh --facts  # Facts only (~13s)
```

### Question → Fact File Mapping

| Question | Read This | NOT This |
|----------|-----------|----------|
| What modules exist? | `docs/v6/latest/facts/modules.json` | `grep '<module>' pom.xml` |
| Build order? Dependencies? | `docs/v6/latest/facts/reactor.json` | `mvn dependency:tree` |
| Who owns which source files? | `docs/v6/latest/facts/shared-src.json` | `find src/ -name '*.java'` |
| Stateful ↔ stateless mapping? | `docs/v6/latest/facts/dual-family.json` | `grep -r 'class Y' src/` |
| Duplicate classes? | `docs/v6/latest/facts/duplicates.json` | `find . -name '*.java' \| sort` |
| Tests per module? | `docs/v6/latest/facts/tests.json` | `find test/ -name '*Test.java'` |
| Quality gates active? | `docs/v6/latest/facts/gates.json` | reading 1700-line pom.xml |

### Usage Pattern

```bash
# 1. Read the index
cat docs/v6/latest/INDEX.md

# 2. Read specific fact
cat docs/v6/latest/facts/modules.json

# 3. If facts are stale, refresh
bash scripts/observatory/observatory.sh
```

**Token savings**: 1 fact file (~50 tokens) vs grepping (~5000 tokens) = **100x compression**

See `.claude/OBSERVATORY.md` for full documentation.

## Repository Configuration

### Local Repository
- **Location**: `~/.m2/repository`
- **Purpose**: Cache downloaded dependencies
- **First build**: ~200MB download
- **Subsequent builds**: Use cached artifacts

### Remote Repository
- **Central**: `https://repo.maven.apache.org/maven2`
- **Failover**: None configured (single-source)

### Offline-Mode Strategy

For offline builds (after first download):

```bash
# Enable offline mode
mvn -o clean compile

# Or set environment variable
export MAVEN_OFFLINE=true
mvn clean compile
```

## Troubleshooting Build Issues

### Issue: "Cannot resolve dependencies"

**Cause**: Network unavailable on first build

**Solution**:
```bash
# Ensure network is available
ping repo.maven.apache.org

# Force dependency download
mvn clean install -U  # Update snapshots

# If still failing, check Maven settings
mvn help:describe -Dplugin=help
```

### Issue: "Out of memory"

**Cause**: Heap too small (default: 2GB)

**Solution**:
```bash
# Increase heap in .mvn/jvm.config
# Change: -Xmx2g  →  -Xmx4g

# Or use environment variable
export MAVEN_OPTS="-Xmx4g"
mvn clean install
```

### Issue: "Tests fail with timeout"

**Cause**: Tests too slow or resource-limited

**Solution**:
```bash
# Increase test timeout
mvn test -DargLine="-Dtimeout=300000"

# Run single test
mvn test -Dtest=SlowTest

# Skip long-running tests
mvn test -DexcludedGroups=slow
```

### Issue: "Compilation fails on Java version"

**Cause**: Java version mismatch

**Solution**:
```bash
# Verify Java version
java -version      # Should be 21.0.10

# Verify compiler
javac -version

# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean compile
```

### Issue: "Maven wrapper broken"

**Cause**: mvnw script requires .mvn/wrapper/maven-wrapper.jar

**Solution**:
```bash
# Use system Maven instead
mvn clean install

# Or fix wrapper with network access
mvn -N io.takari:maven:wrapper -Dmaven=3.9.11
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn clean install
```

### GitLab CI Example

```yaml
stages:
  - compile
  - test
  - package

compile:
  image: maven:3.9-eclipse-temurin-21
  stage: compile
  script: mvn clean compile

test:
  image: maven:3.9-eclipse-temurin-21
  stage: test
  script: mvn clean test

package:
  image: maven:3.9-eclipse-temurin-21
  stage: package
  script: mvn clean install
```

## Best Practices

1. **Always compile before testing**
   ```bash
   mvn clean compile test  # Not just mvn test
   ```

2. **Use offline mode when possible**
   ```bash
   mvn -o clean compile  # After first download
   ```

3. **Check dependency versions regularly**
   ```bash
   mvn dependency:analyze
   ```

4. **Update dependencies carefully**
   ```bash
   mvn versions:display-dependency-updates
   ```

5. **Profile long builds**
   ```bash
   mvn clean install -X  # Enable debug logging
   ```

## Module Dependencies

### Dependency Chain
```
yawl-utilities (base)
    ↓
yawl-elements (depends on utilities)
    ↓
yawl-authentication (depends on elements)
yawl-engine (depends on elements)
yawl-stateless (depends on engine)
    ↓
yawl-resourcing (depends on engine)
yawl-integration (depends on engine)
yawl-worklet (depends on engine)
    ↓
yawl-webapps (depends on engine)
yawl-control-panel (depends on engine)
```

### Build Order
Maven automatically resolves dependencies and builds in correct order.

## Performance Baseline

### Target Performance Metrics
- **Compilation**: < 45 seconds
- **Unit tests**: < 2 minutes (parallel)
- **Full build**: < 5 minutes (with tests)
- **Dependency cache**: ~200MB disk

### Actual Performance (with network)
- **First build**: 5-10 minutes (includes dependency download)
- **Subsequent builds**: 3-5 minutes (cached dependencies)

## Next Steps

1. Review **TESTING.md** for test execution
2. Check **QUICK-START.md** for initial setup
3. See **TROUBLESHOOTING.md** for common issues
4. Read **CONTRIBUTING.md** for contribution workflow

