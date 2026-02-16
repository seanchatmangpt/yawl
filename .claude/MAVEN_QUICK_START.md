# Maven Quick Start Guide for YAWL v5.2

## Essential Commands

### Core Build Operations

```bash
# Compile source code only
mvn clean compile

# Run JUnit test suite (includes compilation)
mvn clean test

# Full build: compile, test, package
mvn clean package

# Build without running tests (faster)
mvn clean package -DskipTests

# Clean build artifacts
mvn clean

# Install to local repository
mvn clean install
```

### Module-Specific Operations

```bash
# Compile specific module only
mvn -pl yawl-engine clean compile

# Test specific module
mvn -pl yawl-engine clean test

# Multiple modules
mvn -pl yawl-engine,yawl-elements clean test

# Build module and its dependencies
mvn -pl yawl-engine -amd clean compile
```

### Documentation & Reports

```bash
# Generate Javadoc API documentation
mvn clean javadoc:javadoc

# Generate test coverage report (JaCoCo)
mvn clean test jacoco:report

# Display dependency tree
mvn dependency:tree

# Check for dependency updates
mvn versions:display-dependency-updates

# Analyze dependency usage
mvn dependency:analyze
```

### Performance & Debugging

```bash
# Parallel builds (1 thread per CPU core)
mvn -T 1C clean test

# Parallel builds with specific thread count
mvn -T 4 clean test

# Debug mode (verbose output)
mvn -X clean compile

# Skip execution, show what would happen
mvn clean compile -am -amd --dry-run

# Profile build time per phase
mvn clean test -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer=warn
```

### Quick Workflows

```bash
# Fast compilation without tests (85% faster)
mvn clean compile

# Compile + test single module (for development)
mvn -pl yawl-engine clean test

# Full verification before commit
mvn clean verify

# Install locally for use in other projects
mvn clean install -DskipTests
```

## Common Workflows

### Development Cycle

```bash
# 1. Change code in yawl-engine
# 2. Quick compile to check syntax
mvn -pl yawl-engine clean compile

# 3. Run module tests
mvn -pl yawl-engine clean test

# 4. Full build to ensure no broken dependencies
mvn clean verify -DskipTests

# 5. Final test run
mvn clean test
```

### Release Build

```bash
# Full verification with coverage
mvn clean verify

# Generate complete documentation
mvn clean javadoc:javadoc

# Check for security vulnerabilities
mvn org.owasp:dependency-check-maven:check

# Final packaging
mvn clean package
```

### CI/CD Pipeline

```bash
# Initial clone / fresh build
mvn clean install

# Incremental build (skips if dependencies unchanged)
mvn test

# With caching (GitHub Actions)
# Cache restored → mvn test runs 5x faster
```

## Useful Flags

| Flag | Purpose |
|------|---------|
| `-pl MODULE` | Build specific module(s) |
| `-am` | Also build modules required by specified modules |
| `-amd` | Also build modules that depend on specified modules |
| `-T 1C` | Parallel builds (1 thread per core) |
| `-X` | Debug mode (verbose) |
| `-q` | Quiet mode (suppress output) |
| `-DskipTests` | Skip test execution |
| `-Dspeed` | Skip slow operations |

## Troubleshooting

### Out of Memory
```bash
# Increase heap size
export MAVEN_OPTS="-Xmx2g"
mvn clean test
```

### Network Issues
```bash
# Offline mode (use local cache)
mvn -o clean compile

# Force update of snapshots
mvn -U clean compile
```

### Clean Build
```bash
# Remove all artifacts
mvn clean

# Also clear local Maven repository (cautious!)
rm -rf ~/.m2/repository
mvn clean install
```

### Dependency Issues
```bash
# Force re-download dependencies
mvn clean -U install

# Show dependency tree (find conflicts)
mvn dependency:tree

# Analyze unused dependencies
mvn dependency:analyze
```

## Performance Tips

1. **Parallel Builds** - Use `-T 1C` for faster compilation
2. **Skip Tests** - Use `-DskipTests` during development
3. **Module Focus** - Build specific modules with `-pl`
4. **Maven Cache** - Leveraged automatically in CI/CD
5. **Watch Mode** - Use `./watch-and-test.sh` for auto-testing

## Module Reference

| Module | Purpose |
|--------|---------|
| `yawl-utilities` | Common utilities |
| `yawl-elements` | Workflow element definitions |
| `yawl-engine` | Core stateful engine |
| `yawl-stateless` | Stateless engine variant |
| `yawl-resourcing` | Resource/user management |
| `yawl-worklet` | Worklet services |
| `yawl-scheduling` | Scheduling services |
| `yawl-integration` | MCP/A2A integrations |
| `yawl-monitoring` | Monitoring services |
| `yawl-control-panel` | Admin control panel |

## Getting Help

```bash
# List available Maven goals
mvn help:describe -Dcmd=compile

# Get plugin information
mvn help:describe -Dplugin=org.apache.maven.plugins:maven-compiler-plugin

# Full Maven help
mvn --help
```

## See Also

- `/yawl-build` - Intelligent build skill with auto-detection
- `/yawl-test` - Test execution with coverage reporting
- `/check-dependencies.sh` - Security and dependency health scanning
- `./watch-and-test.sh` - File watch mode with auto-testing
- `./smart-build.sh` - Intelligent builds based on git changes
