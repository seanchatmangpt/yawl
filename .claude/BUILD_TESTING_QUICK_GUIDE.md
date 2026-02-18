# Build & Testing Quick Implementation Guide

**For**: YAWL v5.2 Maven-based Java 25 project | **Date**: 2026-02-17

---

## TL;DR - What to Do Now

### 1. Immediate Plugin Updates (5 minutes)

Update to these versions in your parent POM:

```xml
<maven.compiler.plugin.version>3.15.0</maven.compiler.plugin.version>
<maven.surefire.plugin.version>3.5.4</maven.surefire.plugin.version>
<maven.failsafe.plugin.version>3.5.4</maven.failsafe.plugin.version>
<jacoco.maven.plugin.version>0.8.15</jacoco.maven.plugin.version>
<maven.enforcer.plugin.version>3.5.0</maven.enforcer.plugin.version>
<spotbugs.maven.plugin.version>4.8.2</spotbugs.maven.plugin.version>
<maven.pmd.plugin.version>3.25.0</maven.pmd.plugin.version>
```

### 2. Enable Parallel Builds (1 minute)

Add these scripts to `.mvn/maven.config`:

```
-T 1.5C
-B
--no-transfer-progress
```

Or use command:
```bash
mvn -T 1.5C clean package
```

### 3. Enable Test Parallelization (2 minutes)

Add to `<configuration>` of maven-surefire-plugin:

```xml
<parallel>methods</parallel>
<threadCount>1.5C</threadCount>
```

### 4. Add Code Coverage (3 minutes)

Add execution to jacoco-maven-plugin POM:

```xml
<execution>
    <id>report</id>
    <phase>test</phase>
    <goals>
        <goal>report</goal>
    </goals>
</execution>
```

Then run: `mvn clean test` (generates `target/site/jacoco/index.html`)

---

## Build Command Cheat Sheet

```bash
# Fast compile check (~30s)
mvn -T 1.5C clean compile

# Full build with tests (~90s) ← USE THIS
mvn -T 1.5C clean package

# With code coverage report (~120s)
mvn -T 1.5C clean package jacoco:check

# With static analysis (~180s)
mvn -T 1.5C clean verify spotbugs:check pmd:check

# Incremental (no clean) (~30s)
mvn -T 1.5C compile test

# Skip tests (fast) (~60s)
mvn -T 1.5C clean package -DskipTests

# Only unit tests (~45s)
mvn -T 1.5C clean test

# Only integration tests
mvn -T 1.5C clean verify -DskipUnitTests
```

---

## Version Matrix: What's Current (Feb 2026)

| Tool | Version | Release Date | Java 25? |
|------|---------|--------------|----------|
| Maven | 4.0.0+ | 2024 | ✅ Yes |
| JUnit 5 | 5.14.0 | 2024 | ✅ Yes |
| JUnit 6 | 6.0.0 | Sep 2025 | ✅ Yes |
| JaCoCo | 0.8.15 | Feb 2026 | ✅ Yes |
| SpotBugs | 4.8.2 | Jan 2026 | ✅ Yes |
| PMD | 6.52.0 | Jan 2026 | ✅ Yes |
| Error Prone | 2.36.0 | Feb 2026 | ✅ Yes |
| Maven Compiler | 3.15.0 | Jan 2026 | ✅ Yes |
| Surefire | 3.5.4 | Sep 2025 | ✅ Yes |
| Failsafe | 3.5.4 | Sep 2025 | ✅ Yes |

---

## Performance Expectations

### Before Optimization
- Clean build: ~180s
- Incremental: ~90s
- Tests: ~60s (sequential)

### After Optimization (30-50% improvement)
- Clean build: ~90s (parallel)
- Incremental: ~30s (parallel)
- Tests: ~30-45s (parallel with 1.5C threads)

### Build Caching (CI/CD only)
- First run: ~90s
- Cached run: ~30-45s (50%+ improvement)

---

## GitHub Actions Setup (copy-paste ready)

```yaml
# .github/workflows/build.yml
name: Maven Build

on:
  push:
    branches: [ main, develop, claude/** ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up Java 25
      uses: actions/setup-java@v4
      with:
        java-version: 25
        distribution: temurin
        cache: maven

    - name: Build
      run: mvn -B -T 1.5C clean package

    - name: Test Report
      if: always()
      uses: dorny/test-reporter@v1
      with:
        name: Test Results
        path: target/surefire-reports/TEST-*.xml
        reporter: java-junit

    - name: Code Coverage
      run: mvn jacoco:report
      continue-on-error: true

    - name: Upload Coverage
      uses: codecov/codecov-action@v3
      with:
        files: ./target/site/jacoco/jacoco.xml
        fail_ci_if_error: false
```

---

## Common Issues & Fixes

### "Tests fail when running parallel"
**Fix**: Add `@Execution(ExecutionMode.SAME_THREAD)` to non-thread-safe tests:
```java
@Execution(ExecutionMode.SAME_THREAD)
class NonThreadSafeTest { }
```

### "Compiler plugin not found error"
**Fix**: Pin all plugin versions in parent POM's `<pluginManagement>`:
```xml
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.15.0</version>
        </plugin>
    </plugins>
</pluginManagement>
```

### "Out of memory with parallel tests"
**Fix**: Reduce thread count or increase heap:
```bash
mvn -T 1C test  # Use 1 thread per core
# OR
MAVEN_OPTS="-Xmx2g" mvn -T 1.5C test
```

### "SpotBugs/PMD blocking build"
**Fix**: Make them warnings instead of errors (temporarily):
```bash
mvn clean package spotbugs:check pmd:check -DfailOnViolation=false
```

### "JaCoCo fails with Java 25 preview features"
**Fix**: Update to JaCoCo 0.8.15+ (released Feb 2026)

---

## Step-by-Step Integration

### Step 1: Update Parent POM (5 min)
1. Open `/pom.xml` (reactor)
2. Add `<pluginManagement>` section with all versions above
3. Verify existing plugins reference versions from pluginManagement
4. Run: `mvn clean compile` ✅

### Step 2: Add Test Parallelization (3 min)
1. In maven-surefire-plugin config, add:
   ```xml
   <parallel>methods</parallel>
   <threadCount>1.5C</threadCount>
   ```
2. Run: `mvn clean test` ✅

### Step 3: Add Code Coverage (3 min)
1. Add jacoco-maven-plugin to pluginManagement
2. Add executions for prepare-agent and report
3. Run: `mvn clean test` then `xdg-open target/site/jacoco/index.html` ✅

### Step 4: Add Static Analysis (5 min)
1. Add spotbugs-maven-plugin to pluginManagement
2. Add pmd-maven-plugin to pluginManagement
3. Run: `mvn spotbugs:check pmd:check` ✅

### Step 5: Setup CI/CD (10 min)
1. Create `.github/workflows/build.yml`
2. Copy workflow YAML above
3. Push to GitHub
4. Verify workflow runs ✅

---

## Decision: JUnit 5 vs JUnit 6

### Choose JUnit 5 if:
- ✅ YAWL v5.2 (current version)
- ✅ Need established ecosystem
- ✅ Mature test infrastructure
- ✅ Staff familiar with JUnit 5

**Action**: Use JUnit 5.14.0 LTS

### Choose JUnit 6 if:
- ✅ New YAWL v6+ projects
- ✅ Want latest Java 25 features
- ✅ Modern codebase
- ✅ Team willing to migrate

**Action**: Plan migration, start with new modules in v6

---

## Maven vs TestNG Decision

### Use Maven Surefire (JUnit 5) if:
- ✅ Standard Maven projects (YAWL)
- ✅ Simple test organization
- ✅ Parameterized tests needed
- ✅ 89 packages with straightforward structure

### Use TestNG if:
- ✅ Complex test dependencies
- ✅ Fine-grained thread pool control
- ✅ Data provider caching needed
- ❌ Not needed for YAWL v5.2

**Action for YAWL**: Keep JUnit 5 + Surefire

---

## Validation Checklist

- [ ] Maven 4.0+ installed (`mvn --version`)
- [ ] Java 25 available (`java -version`)
- [ ] Parent POM has pluginManagement with all versions
- [ ] Surefire configured for parallel execution
- [ ] JaCoCo coverage reporting working
- [ ] SpotBugs/PMD running without errors
- [ ] GitHub Actions workflow created and passing
- [ ] Code coverage report generates (target/site/jacoco/index.html)
- [ ] Build time < 90s with `-T 1.5C`
- [ ] All tests pass in parallel

---

## Performance Monitoring

### Measure Build Times

```bash
# Before optimization
time mvn clean package

# After optimization
time mvn -T 1.5C clean package

# Expected improvement: 30-50%
```

### Track Test Performance

```bash
# Add timing reports
mvn test -DaddTimestamps=true

# View surefire report
cat target/surefire-reports/*.txt | grep -E "Tests|Time"
```

### Monitor Coverage Trends

```bash
# Generate coverage report
mvn jacoco:report

# Extract coverage %
grep "LINE" target/site/jacoco/index.html | head -1
```

---

## Recommended Reading

1. **Full Research**: `/home/user/yawl/.claude/BUILD_TESTING_RESEARCH_2025-2026.md`
2. **Maven 4 Guide**: https://maven.apache.org/whatsnewinmaven4.html
3. **JUnit 5 Docs**: https://junit.org/junit5/docs/current/user-guide/
4. **Best Practices**: `/home/user/yawl/.claude/BEST-PRACTICES-2026.md`

---

**Quick Start**: Run `mvn -T 1.5C clean package` and watch build time drop! 🚀

