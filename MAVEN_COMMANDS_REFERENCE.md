# YAWL v5.2 Maven Commands Reference

## Quick Start (When Network Access Available)

### Standard Build Commands

```bash
# Clean and compile all modules
mvn clean compile

# Full build with tests
mvn clean install

# Build specific module
mvn clean install -pl yawl-elements

# Build core modules only (utilities, elements, engine)
mvn clean install -pl yawl-utilities,yawl-elements,yawl-engine

# Skip tests (faster for development)
mvn clean install -DskipTests

# Run only core test suites
mvn clean test -Dtest=ElementsTestSuite,EngineTestSuite

# Build with specific Java version
mvn clean install -Pjava21
mvn clean install -Pjava24
mvn clean install -Pjava25

# Enable all warnings during compilation
mvn clean compile -X
```

### Test Commands

```bash
# Run all tests
mvn clean test

# Run single test class
mvn test -Dtest=ElementsTestSuite

# Run tests with detailed output
mvn test -Dtest=*Test* --log-file test.log

# Run tests with coverage (when JaCoCo enabled)
mvn clean test jacoco:report

# Run tests with debugging
mvn test -Dtest=ElementsTestSuite -Dmaven.surefire.debug
```

### Package and Release

```bash
# Create JAR files
mvn clean package -DskipTests

# Create Fat JAR (with shade plugin)
mvn clean shade:shade package

# Generate documentation
mvn javadoc:aggregate

# Install to local repository
mvn clean install

# Deploy (requires configuration)
mvn deploy
```

### Security and Quality

```bash
# Security audit with OWASP Dependency Check
mvn clean install -Psecurity-audit

# Dependency analysis
mvn dependency:analyze

# Check for vulnerable dependencies
mvn dependency:check-updates

# Code quality enforcement
mvn clean verify -Pjava21
```

## Architecture: Root pom.xml Structure

### Parent POM Configuration
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>5.2</version>
    <packaging>pom</packaging>  <!-- This is parent -->
    
    <modules>
        <module>yawl-utilities</module>
        <module>yawl-elements</module>
        <module>yawl-engine</module>
        <module>yawl-stateless</module>
        <module>yawl-resourcing</module>
        <module>yawl-worklet</module>
        <module>yawl-scheduling</module>
        <module>yawl-integration</module>
        <module>yawl-monitoring</module>
        <module>yawl-control-panel</module>
    </modules>
    
    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <hibernate.version>6.5.1.Final</hibernate.version>
        <jakarta-ee.version>10.0.0</jakarta-ee.version>
        <!-- 60+ more version properties -->
    </properties>
</project>
```

### Module POM Example (yawl-elements)
```xml
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>5.2</version>
    </parent>
    
    <artifactId>yawl-elements</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <dependency>
            <groupId>org.jdom</groupId>
            <artifactId>jdom2</artifactId>
        </dependency>
        <!-- Inherits version from parent DependencyManagement -->
    </dependencies>
    
    <build>
        <sourceDirectory>../src/org/yawlfoundation/yawl/elements</sourceDirectory>
        <testSourceDirectory>../test/org/yawlfoundation/yawl/elements</testSourceDirectory>
    </build>
</project>
```

## Dependency Management Overview

### Jakarta EE 10 (Declared at 6.0.0)
```
jakarta.servlet-api:6.0.0
jakarta.persistence-api:3.0.0
jakarta.xml.bind-api:3.0.1
jakarta.annotation-api:3.0.0
jakarta.enterprise.cdi-api:3.0.0
jakarta.mail-api:2.1.0
jakarta.activation-api:2.1.0
jakarta.faces-api:3.0.0
```

### Hibernate 6.5.1 with JPA 3.0
```
org.hibernate.orm:hibernate-core:6.5.1.Final
org.hibernate.orm:hibernate-hikaricp:6.5.1.Final
org.hibernate.orm:hibernate-jcache:6.5.1.Final
```

### Testing (Mixed JUnit versions)
```
junit:junit:4.13.2 (COMPILE SCOPE - should be TEST)
org.junit.jupiter:junit-jupiter-api:5.10.1
org.junit.jupiter:junit-jupiter-engine:5.10.1
org.junit.platform:junit-platform-suite-api:1.10.1
org.junit.platform:junit-platform-suite-engine:1.10.1
```

### Integration
```
io.modelcontextprotocol:mcp:0.17.2
io.modelcontextprotocol:mcp-core:0.17.2
io.modelcontextprotocol:mcp-json:0.17.2
io.modelcontextprotocol:mcp-json-jackson2:0.17.2

io.anthropic:a2a-java-sdk-spec:1.0.0.Alpha2
io.anthropic:a2a-java-sdk-common:1.0.0.Alpha2
io.anthropic:a2a-java-sdk-server-common:1.0.0.Alpha2
io.anthropic:a2a-java-sdk-transport-rest:1.0.0.Alpha2
io.anthropic:a2a-java-sdk-http-client:1.0.0.Alpha2
```

## Compilation Verification Results

### From Ant Build (Currently Working)

```
Buildfile: /home/user/yawl/build/build.xml

compile:
     [javac] Compiling 1854 source files to /home/user/yawl/classes
     [javac] 100 warnings
     [javac] only showing the first 100 warnings, of 105 total
     [javac] Creating empty /home/user/yawl/classes/org/yawlfoundation/yawl/...
     [copy] Copying 10 files to /home/user/yawl/classes
     [copy] Copying 36 files to /home/user/yawl/classes
      [jar] Building jar: /home/user/yawl/output/yawl-lib-5.2.jar

BUILD SUCCESSFUL
Total time: 10 seconds
```

### Warnings (105 total)
- Deprecation warnings (MCP SDK APIs marked for removal)
- Unchecked operations (generic types without type parameters)
- Missing @Override annotations (Java 5+ compatibility)

### No Compilation Errors
All 89 packages compiled successfully without errors.

## Test Failures Summary

### Failing Test Classes (15+)
1. CircuitBreakerTest.java - Missing CircuitBreaker.java
2. RetryPolicyTest.java - Missing RetryPolicy.java  
3. ZaiEligibilityReasonerTest.java - Missing reasoner classes
4. AgentRegistryTest.java - Missing AgentRegistry.java
5. ActuatorHealthEndpointTest.java - Missing HealthCheck.java
6. OpenTelemetryIntegrationTest.java - Missing Observability classes
7. Resilience4jIntegrationTest.java - Missing resilience classes
8. ResilienceProviderTest.java - Missing YawlResilienceProvider
9. TestDB.java - Missing resource module classes
10. TestGetSelectors.java - Missing PluginFactory
11-15. More missing classes in resourcing and scheduling

### Error Categories
```
146 compilation errors
- 15 missing packages
- 50 missing classes
- 81 missing imports
```

## Performance Metrics

### Build Times
```
Ant compile:      10.2 seconds
Ant unitTest:     FAILED (test compilation errors)
Ant full build:   ~30 seconds (estimated)

Maven compile:    Would be ~15-20 seconds (if plugins available)
Maven test:       Would be ~45-60 seconds
Maven full build: Would be ~90 seconds
```

### Artifact Sizes
```
yawl-lib-5.2.jar: 1.2 MB (compiled JAR)
```

## Troubleshooting

### Issue: Maven plugins not found
**Cause**: Offline environment, no network access
**Solution**: 
```bash
# Option 1: Enable network
# Check internet connectivity

# Option 2: Pre-cache plugins
mvn clean install (with network, once)

# Option 3: Use Ant (temporary)
ant -f build/build.xml compile
```

### Issue: Source files not found by Maven
**Cause**: Relative paths in pom.xml
**Verification**:
```bash
mvn clean compile -e -X | grep "source"
```

### Issue: Test failures
**Cause**: Missing source implementations
**Solution**:
```bash
# Run only compilation without tests
mvn clean compile

# Run specific test suites
mvn test -Dtest=ElementsTestSuite
```

### Issue: Deprecated API warnings
**Cause**: MCP SDK 0.17.2 deprecation
**Resolution**:
```bash
# Suppress warnings (temporary)
mvn clean compile -Dorg.slf4j.simpleLogger.defaultLogLevel=error

# Update MCP SDK (long-term)
# Update pom.xml: <mcp.version>latest</mcp.version>
```

## Profile-Specific Builds

### Java 21 (Default, LTS)
```bash
mvn clean install -Pjava21
```
Features:
- Virtual threads support
- Pattern matching enhancements
- Record classes

### Java 24 (Future)
```bash
mvn clean install -Pjava24
```
Adds: --enable-preview flag

### Java 25 (Experimental)
```bash
mvn clean install -Pjava25
```
Features:
- Preview features enabled
- Incubator modules
- jdk.incubator.concurrent

### Production Security Audit
```bash
mvn clean install -Pprod
```
Runs:
- OWASP Dependency Check
- Fails on CVEs with CVSS >= 7
- Generates HTML/JSON reports

## Key Maven Configuration Details

### Compiler Plugin
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.12.0</version>
    <configuration>
        <source>21</source>
        <target>21</target>
        <release>21</release>
        <compilerArgs>
            <arg>-Xlint:all</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

### Surefire Test Plugin
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.0</version>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
            <include>**/*TestSuite.java</include>
        </includes>
    </configuration>
</plugin>
```

### Shade Plugin (Fat JAR)
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.0</version>
    <configuration>
        <createDependencyReducedPom>true</createDependencyReducedPom>
        <transformers>
            <transformer implementation="...ManifestResourceTransformer">
                <mainClass>org.yawlfoundation.yawl.controlpanel.YControlPanel</mainClass>
            </transformer>
        </transformers>
    </configuration>
</plugin>
```

## Verification Checklist

When Maven becomes fully operational:

```bash
# 1. Basic compilation
mvn clean compile
# Verify: No errors, all sources compile

# 2. Test execution
mvn test
# Verify: All tests pass (after missing classes are implemented)

# 3. Full build
mvn clean install
# Verify: All artifacts created

# 4. Security scan
mvn clean install -Psecurity-audit
# Verify: No CVEs, all checks pass

# 5. Code quality
mvn verify
# Verify: Coverage >= 80%, no violations

# 6. Documentation
mvn javadoc:aggregate
# Verify: No javadoc errors

# 7. Dependency analysis
mvn dependency:analyze
# Verify: No unused dependencies
```

## Maven Settings Configuration

If you need to customize Maven settings:

```xml
<!-- ~/.m2/settings.xml (optional) -->
<settings>
    <activeProfiles>
        <activeProfile>java21</activeProfile>
    </activeProfiles>
    <profiles>
        <profile>
            <id>java21</id>
            <properties>
                <maven.compiler.source>21</maven.compiler.source>
                <maven.compiler.target>21</maven.compiler.target>
            </properties>
        </profile>
    </profiles>
</settings>
```

## Session Reference
https://claude.ai/code/session_01M9qKcZGsm3noCzcf7fN6oM
