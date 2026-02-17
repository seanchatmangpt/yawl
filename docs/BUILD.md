# YAWL v6.0.0 Maven Build System Guide

## Architecture Overview

YAWL uses **Maven 3.9.11** with a multi-module structure designed for parallel compilation, modular testing, and flexible deployment.

```
YAWL Parent (6.0.0-Alpha)
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
   ↓ (runs Java compilation with Java 21 preview features)
3. test
   ↓ (runs JUnit 5 in parallel, 4 threads)
4. package
   ↓ (creates JAR/WAR artifacts)
5. verify
   ↓ (SpotBugs static analysis - optional)
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

### Profile 1: `java25` (DEFAULT)
```
Activation: Default
Java version: 21 with preview features
Use case: Standard development and production builds
```

### Profile 2: `java24`
```
Activation: Manual (-Pjava24)
Java version: 24 (future compatibility testing)
Use case: Testing against Java 24 preview features
```

### Profile 3: `prod`
```
Activation: Manual (-Pprod)
Features: OWASP Dependency Check (CVSS >= 7)
Use case: Production release security scanning
```

### Profile 4: `security-audit`
```
Activation: Manual (-Psecurity-audit)
Features: Comprehensive OWASP Dependency Check (all CVSS)
Use case: Full security audit before release
```

## Build Commands

### Fast Development Cycle (~45 seconds)
```bash
# Compile only (no tests)
mvn clean compile

# Why fast: Skips test execution, packaging, artifact download
```

### Full Testing (~2-3 minutes)
```bash
# Compile and run all tests
mvn clean test

# Key: Parallel test execution (4 threads)
```

### Production Build (~5-10 minutes)
```bash
# Full build with tests and packaging
mvn clean install

# Includes:
# - Compilation
# - Test execution
# - JAR/WAR packaging
# - Installation to ~/.m2
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

### Estimated Times (Network-enabled environment)

| Command | Time | Notes |
|---------|------|-------|
| `mvn clean compile` | 45-60 sec | Compile only |
| `mvn clean test` | 2-3 min | Includes parallel tests (4 threads) |
| `mvn clean install` | 5-10 min | Full build with packaging |
| `mvn clean install -DskipTests` | 3-5 min | Skips test execution |
| `mvn -Psecurity-audit clean install` | 10-15 min | Includes security scan |

### First-Time Build (Offline Environment)

**Issue**: Dependencies not cached
**Solution**: Ensure network access for first build

```bash
# First build: Downloads ~200MB of dependencies
mvn clean install  # 10-15 minutes on broadband

# Subsequent builds: Use cached dependencies
mvn clean install  # 5-10 minutes offline
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

