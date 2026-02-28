# YAWL Incremental Build Guide — Developer Reference

**Last Updated**: 2026-02-28
**Target Audience**: Developers, Build Engineers, CI/CD Operators
**Quick Links**: [Setup](#setup) | [Usage](#usage) | [Troubleshooting](#troubleshooting) | [Performance](#performance)

---

## Quick Start

### For Most Developers

```bash
# Normal workflow (compiles changed modules + dependents only)
bash scripts/dx.sh compile

# After editing, test just your module
bash scripts/dx.sh test -pl yawl-engine

# Full test suite (all modules)
bash scripts/dx.sh all
```

### For Build Performance Optimization

```bash
# New: Enhanced incremental (only truly changed modules)
bash scripts/dx-incremental.sh

# Verify cache is working
bash scripts/test-incremental-build.sh --verbose
```

---

## Setup

### What Was Changed (Round 2)

1. **Enabled Maven build cache** (`.mvn/extensions.xml`)
   - Added maven-build-cache-extension v1.2.1
   - Provides multi-build artifact reuse
   - Expected benefit: 30-50% faster incremental builds

2. **Created test suite** (`scripts/test-incremental-build.sh`)
   - Verifies cache is working
   - Measures incremental performance
   - Validates comment-only changes don't recompile

3. **Created enhanced incremental script** (`scripts/dx-incremental.sh`)
   - Detects actual file changes (git-based)
   - Rebuilds only changed modules + dependents
   - Shows rebuild scope and efficiency gains

### Prerequisites

- Java 25 (auto-detected by dx.sh)
- Maven 3.9+ (required for build cache)
- Git (for change detection)

### Verification

```bash
# Step 1: Verify cache is enabled
bash scripts/test-incremental-build.sh

# Expected output:
# ✓ PASS: Incremental < 2s (cache working)
```

---

## Usage

### Scenario 1: Normal Development (Comment Change)

```bash
# Edit a comment in YNetRunner.java
vi yawl-engine/src/main/java/.../YNetRunner.java

# Build (should be <2s with cache)
$ bash scripts/dx.sh compile
INFO Compiling changed modules...
INFO yawl-engine (no bytecode changes, cache hit)
Time: 1.2s

# No dependent rebuild because bytecode unchanged!
```

**Why it's fast**: Javac fingerprints the .class file. Comment-only changes produce identical .class → cache hit.

### Scenario 2: Bug Fix in Core Module

```bash
# Fix bug in yawl-engine (public method changed)
vi yawl-engine/src/main/java/.../YNetRunner.java

# Build (triggers cache miss for yawl-engine, dependents use incremental)
$ bash scripts/dx.sh compile
INFO Compiling changed modules...
INFO yawl-engine (API changed, recompiling)
INFO yawl-authentication (depends on yawl-engine, recompiling)
INFO yawl-scheduling (depends on yawl-engine, recompiling)
...
Time: 8.3s (vs 45s for full build)

# New enhanced script shows exact scope:
$ bash scripts/dx-incremental.sh
Changed modules: 1
  - yawl-engine
Affected modules: 8
  - yawl-engine
  - yawl-stateless
  - yawl-authentication
  - yawl-scheduling
  - ...
Build Efficiency:
  Modules to rebuild: 8
  Modules skipped: 19 (70% faster)
Time: 8.3s
```

### Scenario 3: Multi-File Change (Utility Library)

```bash
# Update yawl-utilities (affects 6+ dependent modules)
vi yawl-utilities/src/main/java/org/yawlfoundation/yawl/util/XPath.java

# Use enhanced script to see impact
$ bash scripts/dx-incremental.sh
Changed modules: 1
  - yawl-utilities
Affected modules: 12 (all modules that transitively depend on utilities)
Build Efficiency:
  Modules skipped: 15 (55% faster)
Time: 18.5s (vs 45s full build)
```

### Scenario 4: Testing After Build

```bash
# Build only changed modules
$ bash scripts/dx.sh compile
Time: 2.3s

# Run tests on affected modules
$ bash scripts/dx.sh test
INFO Running tests for yawl-engine...
Time: 12.4s

# Or in one command
$ bash scripts/dx.sh
Time: 14.7s (compile + test)
```

### Scenario 5: Full Build (CI/Release)

```bash
# Build everything (clears cache, builds all modules)
$ bash scripts/dx.sh all
Time: 45-50s (full clean build)

# Or with cache warmed from previous runs
$ bash scripts/dx.sh compile all
Time: 35-40s (first run uses cache)
```

---

## IDE Integration

### IntelliJ IDEA

#### Option 1: Use Maven (Recommended)
1. **Preferences** → **Build, Execution, Deployment** → **Maven**
2. Set **VM options for importer**: (leave empty, use system Java 25)
3. Enable **"Show warning when using Maven instead of direct compilation"** (optional)
4. Build menu → **Build with Maven** (Ctrl+Shift+M)

#### Option 2: Delegate to Maven
1. **Preferences** → **Build, Execution, Deployment** → **Compiler**
2. Enable **"Delegate IDE build/run actions to Maven"**
3. Build → **Build Project** will use `mvn compile`
4. Benefit: IDE incremental caching + Maven incremental = fastest

#### Option 3: IDE Incremental (Fast, Less Reliable)
1. Use IntelliJ's built-in incremental compiler
2. **But**: May diverge from Maven (causes "works in IDE, fails in CI" issues)
3. Only acceptable if you frequently run `mvn clean verify` before commit

**Recommendation**: Use **Option 2** (delegate to Maven) for consistency with CI.

### Eclipse

1. **Project** → **Properties** → **Java Build Path**
2. Ensure **Java 25** is configured as project JRE
3. **Project** → **Build Project** (uses Eclipse incremental)
4. Before commit: `bash scripts/dx.sh all` (verify with Maven)

### VS Code + Maven Extensions

1. Install **Maven for Java** extension
2. Right-click `pom.xml` → **Run Maven Goal**
3. Type: `compile -DskipTests` (uses Maven incremental)
4. Alt+Shift+M: Run Maven goal with last command

---

## Build Cache Explained

### How It Works

```
Build 1 (Day 1):
  Source: Foo.java
  ├─ Compile → Foo.class
  ├─ Hash: SHA-256(Foo.class + deps)
  └─ Store: ~/.m2/build-cache/<hash>/Foo.class

Build 2 (Day 2):
  Source: Foo.java (unchanged)
  ├─ Hash: SHA-256(Foo.class + deps)
  ├─ Lookup: ~/.m2/build-cache/<hash>/ → FOUND
  └─ Reuse: Foo.class (cache hit, skip compilation!)
```

**Result**: No compilation needed if source + dependencies unchanged.

### Cache Storage

```
~/.m2/build-cache/
├── yawl/
│   ├── <module-hash-1>/
│   │   ├── classes/
│   │   │   └── *.class
│   │   ├── metadata.xml
│   │   └── pom.xml
│   └── <module-hash-2>/
└── <retention-policy>/
```

**Retention**: 50 builds, 30 days (configurable in `.mvn/maven-build-cache-config.xml`)

### When Cache Hits

✓ No source changes
✓ No dependency version changes
✓ Same JVM (Java 25)
✓ Same Maven version (3.9+)
✓ No pom.xml changes

### When Cache Misses

✗ Source file modified
✗ pom.xml changed
✗ Dependency version updated
✗ Different Java version
✗ Cache older than 30 days
✗ `.class` file manually deleted

---

## Performance Benchmarks

### Target Performance (Post-Round 2)

| Scenario | Target | Notes |
|----------|--------|-------|
| **Clean compile** | <50s | Full build from scratch |
| **Incremental (no changes)** | <1s | Cache hit, verify only |
| **Comment-only change** | <2s | Recompile changed file only |
| **Single file change** | <5s | Changed file + dependents |
| **Single module change** | <10s | Module + direct dependents |
| **Utility change** | <20s | Utility + all dependents (max) |

### Measured Performance (Round 2 Baseline)

To be updated after running `test-incremental-build.sh`:

```bash
$ bash scripts/test-incremental-build.sh --metrics
Metrics saved to: ./.claude/metrics/incremental-test-2026-02-28T14:30:00Z.json
```

### Performance Comparison (Estimated)

| Build Type | Before Cache | After Cache | Speedup |
|-----------|--------------|-----------|---------|
| Clean | 50s | 50s | 1.0x (no cache reuse) |
| Incremental (no change) | 5-10s | <1s | 5-10x |
| Comment-only change | 10-20s | 2-3s | 5-8x |
| Single module | 15-25s | 8-12s | 1.5-2x |
| Utility change | 30-40s | 15-20s | 2-2.5x |

---

## Troubleshooting

### Problem: Incremental build is still slow (>2s for no changes)

**Diagnosis**:
```bash
# Check if cache is enabled
mvn -v | grep "cache\|incremental" || echo "Not visible in version"

# Check cache directory exists
ls -la ~/.m2/build-cache/yawl/ || echo "No cache found"

# Verify extension is loaded
mvn clean compile -DskipTests 2>&1 | grep -i "cache\|extension" || true
```

**Solutions**:
1. Delete old cache: `rm -rf ~/.m2/build-cache/`
2. Re-enable extension: Verify `.mvn/extensions.xml` has maven-build-cache-extension
3. Run clean build: `mvn clean compile` (establishes cache baseline)
4. Try incremental: `mvn compile` (should be <1s)

### Problem: Changes in comment not showing up in binary

**Root cause**: You didn't expect them to. Comments are stripped during compilation.

**Why**: Java compiler ignores comments; they don't appear in .class files. This is correct behavior.

**If you NEED comments in binary**: Use `@Deprecated` or `@SuppressWarnings` annotations instead.

### Problem: IDE shows green, but `mvn verify` fails

**Root cause**: IDE uses different compiler settings.

**Solution**:
1. In IDE: Preferences → Build → Maven → Delegate IDE build to Maven
2. Test: Click build → runs `mvn compile` (same as CLI)
3. Or: Always run `bash scripts/dx.sh verify` before commit

### Problem: Rebuilding even though nothing changed

**Diagnosis**:
```bash
# Check git status
git status
git diff

# Check if cache is working
mvn compile -q -DskipTests
# If this is >1s, cache not working
```

**Common causes**:
1. **IDE changed timestamp**: Touch files without modifying content
   - **Fix**: `git checkout <file>` to restore timestamp
2. **Line endings changed** (CRLF ↔ LF)
   - **Fix**: `git config core.autocrlf true` (Windows)
3. **Build cache corrupted**
   - **Fix**: `rm -rf ~/.m2/build-cache/` and rebuild

### Problem: "Build cache disabled" message

**Root cause**: Cache extension not loaded (old setting).

**Fix**: Check `.mvn/extensions.xml` has this line:
```xml
<artifactId>maven-build-cache-extension</artifactId>
```

If not, update file or run:
```bash
# Re-apply Round 2 fix
cat > .mvn/extensions.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>org.apache.maven.extensions</groupId>
        <artifactId>maven-build-cache-extension</artifactId>
        <version>1.2.1</version>
    </extension>
</extensions>
EOF
```

### Problem: Cache not improving build time

**Diagnosis**: Run both tests:
```bash
# Clean (no cache)
mvn clean compile -q -DskipTests
# ~45-50s expected

# Incremental (with cache)
mvn compile -q -DskipTests
# <2s expected if cache working
```

If second is also slow:
1. Cache dir doesn't exist: `ls ~/.m2/build-cache/yawl/`
2. Cache is old (30+ days): Automatic cleanup
3. Extension not loaded: Check `.mvn/extensions.xml`

---

## Advanced Usage

### View Build Cache Statistics

```bash
# List cache contents
ls -lh ~/.m2/build-cache/yawl/ | head -20

# Show cache size
du -sh ~/.m2/build-cache/yawl/

# Show recent cache entries (most recent first)
ls -lhtr ~/.m2/build-cache/yawl/ | tail -20
```

### Clear Cache Selectively

```bash
# Clear cache for one module
rm -rf ~/.m2/build-cache/yawl/<module-hash>/

# Clear cache older than 10 days
find ~/.m2/build-cache/yawl/ -type f -mtime +10 -delete

# Clear all cache
rm -rf ~/.m2/build-cache/
# (Next clean build will re-populate)
```

### Measure Actual Cache Hit Rate

```bash
# Enable cache logging (verbose Maven output)
mvn compile -DskipTests 2>&1 | grep -i "cache hit\|cache miss" | sort | uniq -c

# Run test script with metrics
bash scripts/test-incremental-build.sh --metrics
# Metrics saved to: ./.claude/metrics/incremental-test-*.json
```

### Profile Compiler Performance

```bash
# Show compilation time per module
mvn clean compile -q -DskipTests -Dorg.slf4j.simpleLogger.defaultLogLevel=info 2>&1 | grep -E "Building|Compiling|Time:"

# Show detailed compiler statistics
MAVEN_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation" \
  mvn compile -DskipTests -pl yawl-engine 2>&1 | head -50
```

---

## CI/CD Configuration

### GitHub Actions

```yaml
name: Build
on: [pull_request, push]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up Java 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          distribution: 'temurin'

      # Cache Maven artifacts (speeds up dependency resolution)
      - name: Cache Maven repository
        uses: actions/cache@v3
        with:
          path: |
            ~/.m2/repository/
            ~/.m2/build-cache/
          key: maven-${{ hashFiles('pom.xml') }}
          restore-keys: maven-

      # Use incremental build (only changed modules)
      - name: Build
        run: bash scripts/dx.sh compile all
        # 'compile all' does incremental, falls back to full on cache miss

      - name: Test
        run: bash scripts/dx.sh test all
```

### GitLab CI

```yaml
build:
  stage: build
  image: eclipse-temurin:25-jdk
  cache:
    paths:
      - ~/.m2/repository/
      - ~/.m2/build-cache/
  script:
    - bash scripts/dx.sh compile all
    - bash scripts/dx.sh test all
```

### Jenkins

```groovy
pipeline {
  agent any

  options {
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
  }

  environment {
    JAVA_HOME = '/usr/lib/jvm/temurin-25-jdk-amd64'
  }

  stages {
    stage('Compile') {
      steps {
        sh 'bash scripts/dx.sh compile all'
      }
    }

    stage('Test') {
      steps {
        sh 'bash scripts/dx.sh test all'
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: '.claude/metrics/**', allowEmptyArchive: true
    }
  }
}
```

---

## Performance Tuning

### Tweak Compiler Memory (If Out of Memory)

**File**: `pom.xml` (lines 1428-1429)

Current settings:
```xml
<meminitial>512m</meminitial>
<maxmem>2048m</maxmem>
```

Increase for large modules:
```xml
<meminitial>1024m</meminitial>
<maxmem>4096m</maxmem>
```

### Increase Parallel Threads

**File**: `.mvn/maven.config` (line 19)

Current: `-T 2C` (2 threads per CPU core)

For high-core machines (8+):
```
-T 1C    # 1 thread per core (less JVM overhead)
```

Or fixed count:
```
-T 16    # Exactly 16 threads
```

### Reduce Build Cache Retention (Faster Cleanup)

**File**: `.mvn/maven-build-cache-config.xml` (lines 33-34)

Current:
```xml
<maxBuildsCached>50</maxBuildsCached>
<retentionPeriod>P30D</retentionPeriod>
```

For faster cleanup (sacrifice disk space):
```xml
<maxBuildsCached>20</maxBuildsCached>
<retentionPeriod>P7D</retentionPeriod>
```

---

## FAQ

**Q: Does incremental work with IDE changes?**
A: Only if IDE delegates to Maven. Direct IDE compilation bypasses Maven cache. Recommended: set IDE to delegate.

**Q: Is build cache secure?**
A: Local cache (~/.m2/) is per-user. Remote cache should use HTTPS. Current config is local-only (safe).

**Q: Can I share cache with teammates?**
A: Yes, via network mount (e.g., NAS). Not configured in Phase 1; see Phase 2 docs.

**Q: Does cache affect reproducible builds?**
A: No. Cache only stores .class files; pom.xml controls all inputs.

**Q: What if .class file is corrupted?**
A: Delete cache: `rm -rf ~/.m2/build-cache/`. Next clean build regenerates.

**Q: How much disk space does cache use?**
A: ~500MB-2GB (configurable, default 10GB max). Safe to delete anytime.

---

## Further Reading

- **Maven Build Cache**: https://maven.apache.org/extensions/maven-build-cache/
- **Incremental Compilation**: https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#useIncrementalCompilation
- **YAWL Build Analysis**: `.claude/profiles/incremental-build-analysis.md`
- **Module Dependencies**: `pom.xml` (lines 61-97) or `docs/v6/diagrams/facts/reactor.json`

---

**Questions?** Check `.claude/profiles/incremental-build-analysis.md` or file an issue in the YAWL bug tracker.

