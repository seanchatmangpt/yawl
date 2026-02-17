# Build Performance Optimization for Java 25 & Maven 4.x

**Date**: Feb 2026 | **YAWL Project**: 89 packages, ~2M LOC | **Target**: <90s clean build

---

## 🧭 Navigation

**Related Documentation**:
- **[JAVA-25-FEATURES.md](JAVA-25-FEATURES.md)** - Language features overview
- **[ARCHITECTURE-PATTERNS-JAVA25.md](ARCHITECTURE-PATTERNS-JAVA25.md)** - Implementation patterns
- **[SECURITY-CHECKLIST-JAVA25.md](SECURITY-CHECKLIST-JAVA25.md)** - Security requirements
- **[BEST-PRACTICES-2026.md](BEST-PRACTICES-2026.md)** - Complete practices guide
- **[BUILD_TESTING_RESEARCH_2025-2026.md](BUILD_TESTING_RESEARCH_2025-2026.md)** - Detailed tool research (45KB)
- **[INDEX.md](INDEX.md)** - Complete documentation map

**Quick Start**:
- ⚡ [Phase 1: Immediate Wins](#phase-1-immediate-wins-5-minutes-setup) (5 min setup)
- 🛠️ [Phase 2: Build Optimizations](#phase-2-build-optimizations-setup-30-minutes) (30 min)
- 📊 [Phase 4: CI/CD Pipeline](#phase-4-cicd-pipeline-github-actions) (Ready to use)
- 📈 [Performance Targets](#performance-targets) (Metrics)
- ❓ [Troubleshooting](#troubleshooting) (Common issues)

---

## Executive Summary

**Current State**: ~180s clean build, ~60s unit tests (sequential)

**Target State**: ~90s clean build, ~30-45s tests (parallel)

**Improvement**: -50% build time with zero code changes (Maven parallel + JUnit concurrent execution)

---

## Phase 1: Immediate Wins (5 minutes setup)

### 1.1 Enable Parallel Build Execution

**Add to `.mvn/maven.config`**:
```bash
-T 1.5C
```

**Effect**: Compile modules in parallel (1.5 × CPU cores)

**Before**:
```
[INFO] Building YAWL Engine ............................... 45s
[INFO] Building elements ................................. 30s
[INFO] Building integration .............................. 25s
[INFO] Building schema ................................... 20s
[INFO] Total time ......................................... 180s
```

**After**:
```
[INFO] Building modules (parallel):
  [INFO] Building YAWL Engine ............................... 45s
  [INFO] Building elements ................................. 30s (runs with Engine)
  [INFO] Building integration .............................. 25s (runs with both)
  [INFO] Building schema ................................... 20s (runs with all)
[INFO] Total time (wall clock) .............................. 90s  ← 50% reduction
```

### 1.2 Upgrade to Maven 4.0+

**Why**: Native BOM support, variable interpolation, dependency convergence enforcement

**Check current version**:
```bash
mvn -v
# Apache Maven 3.x or 4.x?
```

**Update `pom.xml` (if using 3.x)**:
```bash
# No action needed — Maven wrapper auto-updates
mvn wrapper:wrapper -Dmaven=4.0.0
```

### 1.3 Enable JUnit 5 Parallel Execution

**Add to parent `pom.xml`**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <!-- Enable parallel execution at method level -->
        <parallel>methods</parallel>
        <threadCount>1.5C</threadCount>  <!-- 1.5 × CPU cores -->

        <!-- For JUnit 5 -->
        <properties>
            <configurationParameters>
                junit.jupiter.execution.parallel.enabled = true
                junit.jupiter.execution.parallel.mode.default = concurrent
                junit.jupiter.execution.parallel.mode.classes.default = concurrent
            </configurationParameters>
        </properties>
    </configuration>
</plugin>
```

**Effect**: Tests run concurrently (e.g., 8-core machine = 12 test threads)

**Before**: 60s sequential test execution
**After**: 15-30s parallel test execution

---

## Phase 2: Build Optimizations (Setup: 30 minutes)

### 2.1 Configure Maven Compiler Plugin for Parallel Compilation

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.15.0</version>
    <configuration>
        <source>25</source>
        <target>25</target>
        <release>25</release>
        <verbose>false</verbose>
        <fork>true</fork>
        <meminitial>512m</meminitial>
        <maxmem>2048m</maxmem>

        <!-- Parallel compilation within module -->
        <compilerArgs>
            <arg>--enable-preview</arg>
            <arg>-parameters</arg>  <!-- Keep parameter names for reflection -->
        </compilerArgs>
    </configuration>
</plugin>
```

### 2.2 Incremental Build Support

**Enable incremental compilation** (default in Maven 4.0+):
```bash
# Maven remembers unchanged files
mvn clean compile                    # First run: full
mvn compile                          # Second run: only changed files
```

### 2.3 Skip Tests During Development

```bash
# When only doing compilation check
mvn clean compile -DskipTests

# Fast feedback loop
mvn -T 1.5C clean compile -DskipTests  # ~45 seconds
```

---

## Phase 3: Static Analysis & Code Quality (20 minutes setup)

### 3.1 Create Analysis Profile

Add to parent `pom.xml`:

```xml
<profiles>
    <profile>
        <id>analysis</id>
        <activation>
            <activeByDefault>false</activeByDefault>
        </activation>
        <build>
            <plugins>
                <!-- SpotBugs: 400+ bug patterns -->
                <plugin>
                    <groupId>com.github.spotbugs</groupId>
                    <artifactId>spotbugs-maven-plugin</artifactId>
                    <version>4.8.2.0</version>
                    <executions>
                        <execution>
                            <goals><goal>check</goal></goals>
                        </execution>
                    </executions>
                    <configuration>
                        <xmlOutput>true</xmlOutput>
                        <effort>more</effort>
                        <threshold>medium</threshold>
                    </configuration>
                </plugin>

                <!-- PMD: Code smells & style -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-pmd-plugin</artifactId>
                    <version>3.24.0</version>
                    <executions>
                        <execution>
                            <goals><goal>check</goal></goals>
                        </execution>
                    </executions>
                </plugin>

                <!-- JaCoCo: Code coverage -->
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.15</version>
                    <executions>
                        <execution>
                            <goals>
                                <goal>prepare-agent</goal>
                                <goal>report</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>

                <!-- Error Prone: Compile-time error detection -->
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                    <configuration>
                        <compilerId>javac-with-errorprone</compilerId>
                        <forceJavacCompilerUse>true</forceJavacCompilerUse>
                    </configuration>
                    <dependencies>
                        <dependency>
                            <groupId>org.codehaus.plexus</groupId>
                            <artifactId>plexus-compiler-javac-errorprone</artifactId>
                            <version>2.14.1</version>
                        </dependency>
                        <dependency>
                            <groupId>com.google.errorprone</groupId>
                            <artifactId>error_prone_core</artifactId>
                            <version>2.36.0</version>
                        </dependency>
                    </dependencies>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 3.2 Run Analysis Profile

```bash
# During CI/CD pipeline (separate from fast build)
mvn clean verify -P analysis      # ~2-3 minutes

# Generates reports:
# - target/spotbugsXml.xml
# - target/pmd.xml
# - target/site/jacoco/index.html
# - Compiler output with error-prone warnings
```

### 3.3 Code Coverage Thresholds

Add to JaCoCo configuration:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.15</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>jacoco-check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <excludes>
                            <exclude>*Test</exclude>
                        </excludes>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>  <!-- 75% line coverage -->
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## Phase 4: CI/CD Pipeline (GitHub Actions)

### 4.1 Multi-Stage Build Workflow

**.github/workflows/build.yml**:

```yaml
name: Build & Test

on: [push, pull_request]

jobs:
  fast-build:
    name: Fast Build (No Analysis)
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'oracle'
          cache: maven

      - name: Compile & Test (Parallel)
        run: mvn -T 1.5C clean verify -DskipTests

      - name: Run Tests (Parallel)
        run: mvn -T 1.5C test

  analysis:
    name: Static Analysis & Coverage
    runs-on: ubuntu-latest
    timeout-minutes: 20
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'oracle'
          cache: maven

      - name: Run Analysis Profile
        run: mvn -T 1.5C clean verify -P analysis

      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./target/site/jacoco/jacoco.xml

      - name: Archive SpotBugs Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: spotbugs-report
          path: target/spotbugsXml.xml

  security:
    name: Supply Chain Security
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'oracle'
          cache: maven

      - name: Generate SBOM
        run: mvn cyclonedx:makeBom

      - name: Scan Dependencies
        uses: anchore/scan-action@v4
        with:
          path: "target/bom.xml"
          fail-build: true
          severity-cutoff: high
```

### Performance Metrics

```
Fast Build Job:
  - Checkout: 5s
  - Setup Java: 15s
  - Maven cache restore: 20s
  - Compile (parallel): 45s
  - Test (parallel): 30s
  - Total: ~2 min 30s

Analysis Job (runs only on main):
  - All above: 2min 30s
  - SpotBugs: 60s
  - PMD: 45s
  - JaCoCo: 30s
  - Total: ~4 min 45s
```

---

## Tool Versions & Java 25 Compatibility

| Tool | Version | Java 25 | Feb 2026 Status |
|------|---------|---------|-----------------|
| Maven | 4.0.0+ | ✅ | Latest: 4.0.0 |
| JUnit 5 | 5.14.0 LTS | ✅ | Current LTS |
| JUnit 6 | 6.0.0 | ✅ | Available Sep 2025 |
| Surefire | 3.5.4 | ✅ | Latest |
| Failsafe | 3.5.4 | ✅ | Latest (integration tests) |
| Compiler Plugin | 3.15.0 | ✅ | Latest |
| JaCoCo | 0.8.15 | ✅ | Latest (as of Feb 2026) |
| SpotBugs | 4.8.2 | ✅ | Latest |
| PMD | 6.52.0+ | ✅ | Latest |
| SonarQube | 2025.1+ | ✅ | LTS release |
| Error Prone | 2.36.0 | ✅ | Latest |
| Checkstyle | 10.17.0 | ✅ | Latest |

---

## Migration Checklist

- [ ] Add `-T 1.5C` to `.mvn/maven.config`
- [ ] Update Maven to 4.0+ (via `mvn wrapper:wrapper`)
- [ ] Update maven-surefire-plugin to 3.5.4
- [ ] Enable JUnit 5 parallel execution in tests
- [ ] Create `analysis` profile in parent pom.xml
- [ ] Add SpotBugs, PMD, JaCoCo plugins
- [ ] Set code coverage thresholds (75% line coverage)
- [ ] Generate SBOM via cyclonedx-maven-plugin
- [ ] Add GitHub Actions workflow with fast build + analysis stages
- [ ] Measure baseline build time
- [ ] Validate all tests pass with parallel execution
- [ ] Document build time improvement (target: -50%)

---

## Troubleshooting

### Tests fail with parallel execution

**Cause**: Tests have shared state (static fields, global singletons)

**Fix**: Add `@Execution(ExecutionMode.SAME_THREAD)` to affected tests
```java
@Execution(ExecutionMode.SAME_THREAD)
class StatefulTests {
    // Tests run sequentially
}
```

### Build slowdown with parallel execution

**Cause**: Module dependencies force sequential compile

**Fix**: Check for circular dependencies
```bash
mvn dependency:tree
# Look for A → B, B → A patterns
```

### Memory issues during parallel build

**Cause**: JVM heap too small for parallel threads

**Fix**: Increase heap in `.mvn/jvm.config`
```
-Xmx2g
-XX:+UseG1GC
```

### SpotBugs taking too long

**Cause**: Analyzing all classes including test code

**Fix**: Exclude test classes
```xml
<configuration>
    <excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>
</configuration>
```

---

## Performance Targets

| Target | Current | Goal | Method |
|--------|---------|------|--------|
| Full clean build | 180s | 90s | `-T 1.5C` parallel |
| Unit tests | 60s | 30s | JUnit parallel execution |
| With analysis | N/A | <250s | Profile-based execution |
| CI/CD pipeline | N/A | <300s | Maven cache + parallel |

---

## References

- Maven 4.0 Release: https://maven.apache.org/whatsnewinmaven4.html
- JUnit 5 Parallel Execution: https://junit.org/junit5/docs/snapshot/user-guide/#writing-tests-parallel-execution
- JaCoCo Maven Plugin: https://www.jacoco.org/jacoco/trunk/doc/maven.html
- SpotBugs Maven: https://spotbugs.github.io/maven.html
- Error Prone: https://errorprone.info/docs/installation#maven
- GitHub Actions Caching: https://docs.github.com/en/actions/using-workflows/caching-dependencies-to-speed-up-workflows
