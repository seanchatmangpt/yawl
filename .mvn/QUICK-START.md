# Maven Quick Start Guide - YAWL v6.0.0

## Build Now ✓ (Everything Configured)

```bash
# Compile all modules (parallel, 2 threads per core)
./mvnw clean compile

# Run tests
./mvnw clean test

# Full build with tests
./mvnw clean package

# Build specific module
./mvnw -pl yawl-engine clean compile

# Skip tests for faster builds
./mvnw clean compile -DskipTests
```

## Faster Builds (Optional)

Install Maven Daemon for 40% speed improvement:

```bash
# One-time installation (choose one method):
sdk install maven-mvnd          # SDKMAN (recommended)
# OR
curl -s https://github.com/apache/maven-mvnd/releases/download/0.9.1/maven-mvnd-0.9.1-linux-x86_64.tar.gz | tar xz -C ~/.local/bin

# Then use mvnd instead of mvn:
mvnd clean package              # Much faster for incremental builds
mvnd --status                   # Check daemon status
mvnd --stop                     # Stop daemon if needed
```

## Current Setup

| Component | Version | Status |
|-----------|---------|--------|
| Maven | 3.9.11 | ✓ Working |
| Java | 21.0.10 | ✓ Compatible |
| mvnd | Not installed | Optional |
| Maven 4.0.0 | Not released | Pending |

## Build Commands by Scenario

### Local Development
```bash
# Fast incremental compile (cache enabled)
./mvnw -T 2C compile

# Run specific test class
./mvnw test -Dtest=YNetRunnerTest

# Build and test one module
./mvnw -pl yawl-engine clean test
```

### Continuous Integration
```bash
# Full validation (all checks)
./mvnw clean verify -B

# Skip tests (run separately in CI)
./mvnw clean package -DskipTests -B

# Strict mode (fail on warnings)
./mvnw clean verify -B --strict-checksums
```

### Performance Testing
```bash
# Measure compile time
time ./mvnw clean compile

# With mvnd (after installation)
time mvnd clean compile        # Should be 40% faster

# Profile module dependencies
./mvnw dependency:tree -pl yawl-engine
```

## Troubleshooting

### "Cannot download dependencies"
- Normal in isolated networks
- No action needed - use `-DskipTests` if offline
- Maven cache persists between builds

### "Build slower than expected"
- JVM warmup time (runs faster on 2nd build)
- Install mvnd for persistent JVM

### "UseCompactObjectHeaders error"
- Already fixed in this version
- No action needed

## What's Configured

✓ **JVM**: ZGC garbage collection, 8GB heap, virtual threads
✓ **Maven**: Parallel builds, 8 artifact threads, build cache
✓ **Modules**: 18 packages, optimized dependency order
✓ **mvnd**: Ready for installation, configuration complete
✓ **Maven 4**: Configuration prepared, awaiting release

## Full Documentation

See `.mvn/` directory:
- `MAVEN-SETUP.md` - Complete setup and troubleshooting
- `MAVEN4-READINESS.md` - Maven 4 migration timeline
- `IMPLEMENTATION-SUMMARY.md` - Technical summary

## CI/CD Integration

### GitHub Actions
```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'openjdk'

- run: ./mvnw clean verify -B
```

### Docker
```dockerfile
FROM openjdk:21
COPY . /app
WORKDIR /app
RUN ./mvnw clean package -DskipTests
```

---

**Next Steps**:
1. Run `./mvnw --version` to verify setup
2. Run `./mvnw clean compile` to test build
3. (Optional) Install mvnd for 40% faster builds
4. Watch maven.apache.org for Maven 4.0.0 release

Questions? See `.mvn/MAVEN-SETUP.md` for detailed documentation.
