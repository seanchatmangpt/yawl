# YAWL Maven Multi-Module Build Guide

## Module Structure

YAWL v6.0.0 uses a Maven multi-module build with the following structure:

```
yawl-parent (pom)
├── yawl-utilities        # Common utilities, authentication, logging
├── yawl-elements         # Workflow element definitions
├── yawl-engine           # Stateful workflow engine (YEngine)
├── yawl-stateless        # Stateless workflow engine
├── yawl-resourcing       # Resource management service
├── yawl-worklet          # Worklet dynamic selection service
├── yawl-scheduling       # Scheduling and timer service
├── yawl-integration      # MCP, A2A, and integration adapters
├── yawl-monitoring       # Process monitoring and visualization
└── yawl-control-panel    # Desktop control panel (Swing)
```

## Module Dependencies

```
yawl-utilities (base)
    ↓
yawl-elements
    ↓
yawl-engine ← yawl-stateless
    ↓          ↓
yawl-resourcing
    ↓
yawl-worklet
    ↓
yawl-integration (MCP/A2A)
yawl-scheduling
yawl-monitoring
yawl-control-panel
```

## Build Commands

### Full Multi-Module Build
```bash
mvn clean install
```

### Build Specific Module
```bash
cd yawl-engine
mvn clean install
```

### Build with Profiles
```bash
# Development (default - Java 21)
mvn clean install

# Production with security scanning
mvn clean install -Pprod

# Java 24 compatibility testing
mvn clean install -Pjava24

# Java 25 preview features
mvn clean install -Pjava25

# Docker fat JAR build
mvn clean package -Pdocker
```

### Skip Tests
```bash
mvn clean install -DskipTests
```

### Run Tests Only
```bash
mvn test
```

## Build Profiles

### Development Profiles

#### `java21` (Default)
- **Purpose**: LTS production build with Java 21
- **Features**: Preview features enabled, all linting
- **Activation**: Active by default
- **Usage**: `mvn clean install`

#### `java24`
- **Purpose**: Future compatibility testing
- **Features**: Java 24 preview features
- **Usage**: `mvn clean install -Pjava24`

#### `java25`
- **Purpose**: Experimental preview features
- **Features**: Virtual threads, structured concurrency, scoped values
- **Modules**: Includes `jdk.incubator.concurrent`
- **Usage**: `mvn clean install -Pjava25`

### Production Profiles

#### `prod`
- **Purpose**: Production build with security scanning
- **Features**:
  - OWASP Dependency Check
  - CVSS threshold: 7.0
  - Fail build on high-severity vulnerabilities
  - HTML + JSON reports
- **Usage**: `mvn clean install -Pprod`
- **Environment Variables**:
  - `NVD_API_KEY`: Optional NVD API key for faster scans

#### `security-audit`
- **Purpose**: Comprehensive security audit
- **Features**:
  - Full dependency analysis
  - No CVSS threshold (report all)
  - HTML + JSON + XML reports
- **Usage**: `mvn clean verify -Psecurity-audit`

### Docker Profile

#### `docker`
- **Purpose**: Build Docker-optimized fat JARs
- **Features**:
  - Shaded JAR with all dependencies
  - Optimized for containerization
  - Service resource transformers
- **Output**: `target/*-shaded.jar`
- **Usage**: `mvn clean package -Pdocker`

## Assembly Descriptors

### Distribution Assembly (`maven-assembly-descriptor.xml`)
Creates full distribution packages:
- **Formats**: `tar.gz`, `zip`
- **Contents**:
  - All module JARs
  - Dependencies in `lib/`
  - Schema files in `schema/`
  - Configuration in `config/`
  - Example specifications in `examples/`
  - Scripts in `bin/`
  - Documentation
  - License

**Usage**:
```bash
mvn clean package assembly:single -Ddescriptor=maven-assembly-descriptor.xml
```

### Web Application Assembly (`maven-webapp-assembly.xml`)
Creates WAR files for web applications:
- **Format**: `war`
- **Contents**:
  - JSP/XHTML pages
  - Static resources (CSS, JS, images)
  - WEB-INF/classes (compiled code)
  - WEB-INF/lib (dependencies)

**Usage**:
```bash
mvn clean package assembly:single -Ddescriptor=maven-webapp-assembly.xml
```

## Plugin Configuration

All modules inherit plugin configuration from parent POM:

### maven-compiler-plugin (3.12.0)
- **Source/Target**: Java 21
- **Release**: 21
- **Compiler Args**: `-Xlint:all`

### maven-surefire-plugin (3.2.0)
- **Test Patterns**:
  - `**/*Test.java`
  - `**/*Tests.java`
  - `**/*TestSuite.java`

### maven-jar-plugin (3.3.0)
- **Manifest**: Default implementation entries
- **Main Class**: Module-specific (defined in module POM)

### jacoco-maven-plugin (0.8.11)
- **Code Coverage**: Enabled for all modules
- **Reports**: Generated in `target/site/jacoco/`

### maven-shade-plugin (3.5.0)
- **Fat JAR**: Available in parent
- **Transformers**:
  - Manifest transformer
  - Service resource transformer
  - Apache license transformer
- **Filters**: Excludes signature files

### maven-enforcer-plugin (3.4.1)
- **Maven Version**: 3.9.0+
- **Java Version**: 21+
- **Rules**:
  - Dependency convergence
  - Ban duplicate dependency versions
  - Require upper bound dependencies

### maven-dependency-plugin (3.6.1)
- **Analysis**: Enabled in verify phase
- **Fail on Warning**: false (informational only)

## Dependency Management

All dependency versions are managed in the parent POM through:

1. **Spring Boot BOM** (3.2.5)
   - Manages 200+ dependencies
   - Spring Framework, Jackson, logging

2. **Jakarta EE BOM** (10.0.0)
   - Unified Jakarta dependencies
   - Servlet, Mail, XML, CDI

3. **OpenTelemetry BOM** (1.40.0)
   - Observability stack
   - Tracing, metrics, logs

4. **OpenTelemetry Instrumentation BOM** (2.6.0)
   - Auto-instrumentation
   - Annotations

5. **Resilience4j BOM** (2.2.0)
   - Circuit breaker, retry, rate limiter

6. **TestContainers BOM** (1.19.7)
   - Containerized integration testing

## Module-Specific Notes

### yawl-utilities
- **Source Directory**: `../src` (multi-package)
- **Compiler Includes**: Specific packages only
  - `authentication/**`
  - `logging/**`
  - `schema/**`
  - `unmarshal/**`
  - `util/**`
  - `exceptions/**`
- **Resources**: Schema files from `../schema`

### yawl-engine, yawl-resourcing, yawl-worklet
- **Resources**: Include Hibernate mapping files (`*.hbm.xml`)
- **Databases**: H2 (embedded), PostgreSQL, MySQL (runtime scope)

### yawl-integration
- **Dependencies**: MCP SDK, A2A SDK, Spring Boot
- **Special**: OkHttp client, JSpecify annotations

### yawl-control-panel
- **Main Class**: `org.yawlfoundation.yawl.controlpanel.YControlPanel`
- **Resources**: Images (`*.png`, `*.gif`, `*.jpg`)

### yawl-monitoring
- **Dependencies**: Jakarta Faces (JSF)
- **Scope**: Servlet API is `provided` (container-supplied)

## Continuous Integration

### GitHub Actions
```yaml
mvn clean install -Pprod
```

### Docker Build
```bash
mvn clean package -Pdocker
docker build -t yawl:5.2 .
```

### Staging Deployment
```bash
mvn clean deploy -Pstaging
```

## Migration from Ant

The Ant build system (`build/build.xml`) is being deprecated in favor of Maven. Key differences:

| Ant Target | Maven Command |
|------------|---------------|
| `ant compile` | `mvn compile` |
| `ant buildAll` | `mvn clean install` |
| `ant unitTest` | `mvn test` |
| `ant clean` | `mvn clean` |
| `ant jar` | `mvn package` |

## Troubleshooting

### Network Issues
If you encounter network issues downloading dependencies:
```bash
# Use offline mode (requires prior download)
mvn clean install -o
```

### Dependency Conflicts
```bash
# Analyze dependency tree
mvn dependency:tree

# Check for convergence issues
mvn enforcer:enforce
```

### Clean Build
```bash
# Remove all build artifacts
mvn clean
rm -rf ~/.m2/repository/org/yawlfoundation
mvn clean install
```

### Module-Only Build
```bash
# Build just one module and its dependencies
mvn clean install -pl yawl-engine -am
```

## IDE Integration

### IntelliJ IDEA
1. File → Open → Select `pom.xml`
2. Import as Maven project
3. Enable auto-import

### Eclipse
1. File → Import → Maven → Existing Maven Projects
2. Select root directory
3. Import all modules

### VS Code
1. Install "Extension Pack for Java"
2. Open folder containing `pom.xml`
3. Maven will auto-detect

## Performance Optimization

### Parallel Builds
```bash
# Use 4 threads
mvn clean install -T 4

# Use 1 thread per CPU core
mvn clean install -T 1C
```

### Incremental Compilation
```bash
# Skip unchanged modules
mvn install -Dmaven.compiler.useIncrementalCompilation=true
```

## Release Process

1. **Update Version**:
   ```bash
   mvn versions:set -DnewVersion=5.3
   ```

2. **Build and Test**:
   ```bash
   mvn clean install -Pprod
   ```

3. **Create Distribution**:
   ```bash
   mvn package assembly:single -Ddescriptor=maven-assembly-descriptor.xml
   ```

4. **Tag Release**:
   ```bash
   git tag -a v5.3 -m "Release 5.3"
   git push origin v5.3
   ```

## References

- [Maven Central](https://search.maven.org/)
- [Maven Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/)
- [Maven Assembly Plugin](https://maven.apache.org/plugins/maven-assembly-plugin/)
- [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/maven-plugin/)
