# Maven and mvnd Configuration Guide - YAWL v6.0.0-GA

## Current Status

- **Maven**: 3.9.11 (via Maven Wrapper)
- **mvnd**: Not yet installed (optional performance enhancement)
- **Java**: 21.0.10 (supports virtual threads and preview features)
- **Target**: Maven 4.0.0 when released

## Maven Configuration Structure

### `.mvn/jvm.config` - JVM Options
Contains Java Virtual Machine tuning for all builds:
- **Memory**: 4GB min / 8GB max
- **GC**: ZGC (low-latency garbage collector)
- **Compiler**: TieredCompilation, level 4
- **Virtual Threads**: Enhanced scheduler for high concurrency
- **Preview Features**: Enabled (Java 21)

**Key Change**: Removed `UseCompactObjectHeaders` (not available in Java 21.0.10)

### `.mvn/maven.config` - Maven Build Flags
Default Maven command-line arguments:
- `-B`: Batch mode (non-interactive)
- `-T 2C`: Parallel build threads (2 per CPU core)
- `-Dmaven.artifact.threads=8`: Parallel dependency resolution
- **Ready for Maven 4**: `-b concurrent` (commented out, will activate when Maven 4.0.0 released)

### `.mvn/mvnd.properties` - Maven Daemon Configuration
Optional daemon process for faster incremental builds:
- **Heap**: 8GB max (matches JVM config)
- **Threads**: Auto-detect (2 per CPU core)
- **Artifact Resolution**: 8 parallel threads
- **Idle Timeout**: 30 minutes
- **Build Cache**: Enabled

### `.mvn/wrapper/maven-wrapper.properties` - Wrapper Bootstrap
Configuration for Maven Wrapper (downloads Maven on first run):
- **Distribution URL**: Maven 3.9.11 (stable release)
- **Wrapper JAR**: Version 3.3.2 (auto-downloaded from Maven Central)
- **Comment**: Shows placeholder for Maven 4.0.0 URL

## Installation and Usage

### Prerequisites
- Java 21+ (Ubuntu 24.04 includes openjdk-21)
- Git (for cloning YAWL)
- 8GB free disk space (for Maven cache and builds)

### Build with Maven Wrapper (Standard)
```bash
# Compile all modules
./mvnw clean compile

# Run tests
./mvnw clean test

# Full build with packaging
./mvnw clean package

# Build specific module
./mvnw -pl yawl-engine clean compile

# Parallel build (2 threads per CPU core)
./mvnw -T 2C clean package
```

### Optional: Install Maven Daemon (mvnd)

mvnd significantly speeds up builds through:
1. **Daemon reuse** (JVM stays warm between builds)
2. **Incremental compilation** (only recompile changed files)
3. **Shared artifact cache** (fast dependency resolution)

#### Installation Methods

**Method 1: SDKMAN (Recommended)**
```bash
sdk install maven-mvnd
```

**Method 2: Manual Download**
```bash
# Download latest version
curl -s https://github.com/apache/maven-mvnd/releases/download/0.9.1/maven-mvnd-0.9.1-linux-x86_64.tar.gz \
  | tar xz -C ~/.local/bin/

# Add to PATH
export PATH=$HOME/.local/bin/maven-mvnd-0.9.1/bin:$PATH
```

**Method 3: Container/CI**
```dockerfile
# In Dockerfile
RUN curl -s https://github.com/apache/maven-mvnd/releases/download/0.9.1/maven-mvnd-0.9.1-linux-x86_64.tar.gz \
  | tar xz -C /opt && \
    ln -s /opt/maven-mvnd-0.9.1/bin/mvnd /usr/local/bin/mvnd
```

#### Using mvnd
```bash
# Build with daemon (auto-starts daemon)
mvnd clean package

# View daemon status
mvnd --status

# Stop daemon
mvnd --stop

# Stop all daemons
mvnd --stop-daemon
```

## Transitioning to Maven 4.0.0

When Apache Maven 4.0.0 is released, follow these steps:

### Step 1: Update Wrapper Properties
```bash
# Edit .mvn/wrapper/maven-wrapper.properties
# Change:
#   distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip
# To:
#   distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0/apache-maven-4.0.0-bin.zip
```

### Step 2: Enable Concurrent Builder
```bash
# Edit .mvn/maven.config
# Uncomment:
#   -b concurrent
```

### Step 3: Test
```bash
./mvnw --version  # Should show Maven 4.0.0
./mvnw clean compile -T 2C
```

### Step 4: Commit and Validate
```bash
git add .mvn/wrapper/maven-wrapper.properties .mvn/maven.config
git commit -m "Upgrade to Maven 4.0.0 with tree-based lifecycle"
./mvnw clean verify -DskipTests
```

## Performance Tuning

### For Local Development
```bash
# Fast incremental compile (with cache)
./mvnw -T 2C compile -DskipTests

# Watch-mode (manual) - keep terminal running
./mvnw -T 2C compile -Dfile.encoding=UTF-8
```

### For CI/CD
```bash
# Full validation build
./mvnw clean verify -B -T 2C --strict-checksums

# Skip tests in CI (run in separate step)
./mvnw clean package -B -T 2C -DskipTests
```

### For Performance Analysis
```bash
# Build with profiling
./mvnw clean compile -T 2C -Dlogging.level=DEBUG

# Check module build order
./mvnw help:describe -Dplugin=maven-reactor-plugin

# View dependency tree
./mvnw dependency:tree -pl yawl-engine
```

## Troubleshooting

### Issue: `UseCompactObjectHeaders` Error
**Cause**: Old jvm.config has unsupported flag for Java 21
**Fix**: Already fixed in this version (flag removed)

### Issue: Maven Wrapper JAR Not Found
**Cause**: First run tries to download JAR
**Fix**: Wrapper automatically downloads on first `./mvnw` command

### Issue: Build Fails with Network Error
**Cause**: Cannot reach Maven Central repository
**Solution**:
- Check internet connection
- Try using local proxy (if configured)
- Pre-populate local Maven cache: `./mvnw dependency:go-offline`

### Issue: mvnd Shows `ZOMBIE` Status
**Cause**: Daemon crashed or hung
**Fix**: `mvnd --stop` then restart

### Issue: Build Slower with mvnd Than mvn
**Cause**: Daemon not warmed up, or JVM compilation overhead
**Fix**: Run 2-3 builds to warm up JVM, then see improvement

## File Manifest

| File | Purpose | Editable |
|------|---------|----------|
| `.mvn/jvm.config` | JVM tuning (heap, GC, threads) | Advanced users only |
| `.mvn/maven.config` | Maven command-line defaults | Yes (flags) |
| `.mvn/mvnd.properties` | Maven Daemon tuning | Yes (heap, threads) |
| `.mvn/wrapper/maven-wrapper.properties` | Maven version + wrapper JAR | When upgrading Maven |
| `.mvn/extensions.xml` | Build extensions (build-cache, etc.) | Advanced only |
| `.mvn/cache-config.sh` | Build cache configuration | Internal |

## References

- **Maven Official**: https://maven.apache.org/
- **Maven Daemon**: https://maven.apache.org/mvnd/
- **Maven 4 Preview**: https://maven.apache.org/blog.html
- **Java 21 Features**: https://www.oracle.com/java/technologies/javase/21-relnotes.html
- **JVM Tuning**: https://docs.oracle.com/en/java/javase/21/gctuning/

## Next Steps

1. **Install mvnd** (optional): Follow "Installation Methods" above
2. **Test build**: `./mvnw clean compile`
3. **Verify all modules**: `./mvnw clean verify -DskipTests`
4. **Monitor performance**: Track build times across several runs
5. **Watch for Maven 4.0.0 release**: Update wrapper when available
