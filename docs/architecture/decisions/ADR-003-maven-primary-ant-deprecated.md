# ADR-003: Maven Primary, Ant Deprecated

## Status
**ACCEPTED**

**Current:** Maven and Ant both functional, Maven recommended
**Target:** Ant deprecated in documentation (v5.3), removed (v6.0)

## Context

YAWL has used Apache Ant as its build system since inception (2004). While Ant has served well for 20+ years, the Java ecosystem has evolved:

### Problems with Ant

1. **Dependency Management**
   - Manual JAR management in `lib/` directory
   - No automatic dependency resolution
   - No transitive dependency handling
   - Difficult version upgrades

2. **Modern Java Ecosystem**
   - Maven Central is the standard repository
   - Most libraries publish Maven coordinates
   - Gradle/Maven are industry standard
   - Limited IDE integration for Ant

3. **Cloud-Native Tooling**
   - Docker image builds require Maven/Gradle plugins
   - Kubernetes deployment tools expect Maven structure
   - CI/CD platforms prefer Maven conventions
   - Dependency vulnerability scanning uses Maven coordinates

4. **Developer Experience**
   - New developers expect Maven/Gradle
   - Ant build scripts are custom and complex
   - No standard project structure
   - Difficult to onboard new team members

### Current Ant Build

```xml
<project name="yawl" basedir="." default="buildAll">
    <!-- 1200+ lines of custom build logic -->
    <target name="compile">
        <!-- Manual classpath management -->
    </target>
    <target name="buildWebApps">
        <!-- Custom WAR creation -->
    </target>
</project>
```

Problems:
- 1200 lines of custom XML
- Brittle classpath configurations
- No dependency resolution
- Difficult to extend

### Benefits of Maven

1. **Standard Structure**
   - `src/main/java`, `src/test/java`
   - `pom.xml` for dependencies
   - Convention over configuration

2. **Dependency Management**
   - Automatic transitive dependencies
   - Central repository (Maven Central)
   - Version conflict resolution
   - Dependency vulnerability scanning

3. **Plugin Ecosystem**
   - Docker image build (Jib)
   - Code coverage (JaCoCo)
   - Static analysis (SpotBugs)
   - Security scanning (OWASP)

4. **CI/CD Integration**
   - GitHub Actions native Maven support
   - GitLab CI Maven templates
   - Jenkins Maven integration
   - Cloud build systems (Google Cloud Build, AWS CodeBuild)

## Decision

**We will transition to Maven as the primary build system with the following timeline:**

### Migration Timeline

#### Phase 1: Parallel Builds (v5.2 - Current)
- **Duration:** February 2026 - May 2026 (3 months)
- **Status:** Maven build functional, Ant still primary
- **Actions:**
  - Maven build verified and tested
  - CI/CD runs both Ant and Maven
  - Documentation mentions both
  - Developers can choose either

#### Phase 2: Maven Primary (v5.3)
- **Duration:** June 2026 - August 2026 (3 months)
- **Status:** Maven primary, Ant deprecated
- **Actions:**
  - CI/CD switches to Maven primary
  - Documentation uses Maven commands
  - Ant marked as deprecated
  - Migration guide published

#### Phase 3: Ant Removal (v6.0)
- **Duration:** September 2026+
- **Status:** Maven only
- **Actions:**
  - Remove `build/` directory
  - Remove Ant build files
  - Archive Ant documentation
  - Breaking change, major version

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   YAWL v5.2 (Current)                   │
│                                                          │
│   Ant (Primary) ────────┐                               │
│                         ├───> Compile, Test, Package    │
│   Maven (Secondary) ────┘                               │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                   YAWL v5.3 (Target)                    │
│                                                          │
│   Maven (Primary) ──────┐                               │
│                         ├───> Compile, Test, Package    │
│   Ant (Deprecated) ─────┘                               │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                   YAWL v6.0 (Future)                    │
│                                                          │
│   Maven (Only) ──────────────> Compile, Test, Package   │
└─────────────────────────────────────────────────────────┘
```

### Implementation Strategy

#### Multi-Module Maven Structure

```
yawl/
├── pom.xml (root aggregator)
├── yawl-engine/
│   └── pom.xml
├── yawl-stateless/
│   └── pom.xml
├── yawl-resource-service/
│   └── pom.xml
├── yawl-integration/
│   ├── yawl-integration-mcp/
│   └── yawl-integration-a2a/
├── yawl-autonomous-agents/
│   └── pom.xml
└── yawl-build-tools/
    └── pom.xml
```

#### Root POM Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>5.2.0</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>25</java.version>
        <spring-boot.version>3.4.2</spring-boot.version>
        <hibernate.version>6.5.0.Final</hibernate.version>
    </properties>

    <dependencyManagement>
        <!-- BOM imports -->
    </dependencyManagement>

    <modules>
        <module>yawl-engine</module>
        <module>yawl-stateless</module>
        <module>yawl-resource-service</module>
        <module>yawl-integration</module>
        <module>yawl-autonomous-agents</module>
    </modules>

    <build>
        <pluginManagement>
            <!-- Plugin versions -->
        </pluginManagement>
    </build>
</project>
```

#### Key Maven Features

1. **Dependency Management**
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter</artifactId>
       <version>${spring-boot.version}</version>
   </dependency>
   ```

2. **Docker Image Build (Jib)**
   ```bash
   mvn jib:build
   # No Dockerfile needed!
   ```

3. **Multi-JDK Testing**
   ```bash
   mvn test -Djava.version=11
   mvn test -Djava.version=17
   mvn test -Djava.version=21
   ```

4. **Profiles**
   ```bash
   mvn clean install -P production,postgresql
   ```

## Consequences

### Positive

1. **Modern Tooling**
   - IDE integration (IntelliJ, Eclipse, VS Code)
   - Dependency vulnerability scanning (OWASP, Snyk)
   - Automated dependency updates (Dependabot)
   - Cloud build systems

2. **Developer Experience**
   - Standard project structure
   - Familiar commands (`mvn clean install`)
   - Better documentation
   - Easier onboarding

3. **Dependency Management**
   - Automatic transitive dependencies
   - Version conflict resolution
   - Maven Central ecosystem
   - Bill of Materials (BOM) support

4. **CI/CD Integration**
   - Native GitHub Actions support
   - GitLab CI Maven templates
   - Jenkins Maven integration
   - Cloud Build compatibility

5. **Plugin Ecosystem**
   - Docker (Jib)
   - Coverage (JaCoCo)
   - Static analysis (SpotBugs, Checkstyle)
   - Security (OWASP Dependency Check)

### Negative

1. **Learning Curve**
   - Team must learn Maven
   - New project structure
   - Different commands

2. **Migration Effort**
   - Restructure project
   - Move source files
   - Update CI/CD pipelines
   - Rewrite build scripts

3. **Build Time**
   - Maven slightly slower than Ant for simple projects
   - Dependency resolution overhead
   - Plugin execution overhead

4. **Backward Compatibility**
   - Breaking change for build process
   - Update all documentation
   - Customer impact (build-from-source users)

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Team resistance | MEDIUM | MEDIUM | Training, migration guide |
| Build failures | HIGH | HIGH | Parallel builds, thorough testing |
| Dependency conflicts | MEDIUM | MEDIUM | Explicit dependency management |
| Performance regression | LOW | LOW | Benchmark, optimize profiles |

## Alternatives Considered

### Alternative 1: Keep Ant
**Rejected:** Blocks modern tooling and cloud-native adoption.

**Pros:**
- No migration effort
- Team familiar with Ant
- Existing build stable

**Cons:**
- No dependency management
- Limited tooling
- Developer experience poor

### Alternative 2: Gradle Instead of Maven
**Rejected:** Maven more widely adopted in enterprise, better YAWL fit.

**Pros:**
- More flexible than Maven
- Better performance
- Groovy/Kotlin DSL

**Cons:**
- Less standard than Maven
- Steeper learning curve
- Fewer enterprise examples

### Alternative 3: Both Ant and Maven Forever
**Rejected:** Dual maintenance burden too high.

**Pros:**
- No forced migration
- User choice

**Cons:**
- Dual maintenance
- Inconsistent documentation
- Technical debt

## Related ADRs

- ADR-004: Spring Boot 3.4 + Java 25 (requires Maven for dependency management)
- ADR-006: OpenTelemetry for Observability (Maven plugin integration)
- ADR-009: Multi-Cloud Strategy (Docker images via Maven Jib plugin)

## Implementation Notes

### Migration Commands

**Before (Ant):**
```bash
ant compile
ant unitTest
ant buildAll
ant clean
```

**After (Maven):**
```bash
mvn compile
mvn test
mvn clean install
mvn clean
```

### Maven Wrapper

Install Maven wrapper (no Maven installation required):
```bash
mvn wrapper:wrapper
./mvnw clean install
```

### Common Maven Commands

```bash
# Compile only
./mvnw compile

# Run tests
./mvnw test

# Package (JAR/WAR)
./mvnw package

# Install to local repo
./mvnw install

# Clean build
./mvnw clean install

# Skip tests
./mvnw install -DskipTests

# Build Docker image
./mvnw jib:dockerBuild

# Run specific test
./mvnw test -Dtest=YEngineTest

# Multi-module build
./mvnw clean install -pl yawl-engine -am
```

### CI/CD Example (GitHub Actions)

```yaml
name: Maven Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [21, 24, 25]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'
      - name: Build with Maven
        run: ./mvnw clean install
      - name: Run tests
        run: ./mvnw test
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-10
**Implementation Status:** PHASE 1 (Parallel builds functional)
**Next Review:** 2026-05-01 (Phase 2 transition planning)

---

**Revision History:**
- 2026-02-10: Initial version approved
- 2026-02-16: Updated timeline and migration guide
