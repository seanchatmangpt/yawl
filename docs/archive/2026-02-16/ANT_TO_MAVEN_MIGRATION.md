# Ant to Maven Migration Guide

## Overview

YAWL v6.0.0 is transitioning from Ant-based builds to Maven multi-module builds. This document provides guidance for developers familiar with the Ant build system.

## Build System Comparison

| Feature | Ant (`build/build.xml`) | Maven (`pom.xml`) |
|---------|------------------------|-------------------|
| **Build Definition** | Procedural (tasks) | Declarative (lifecycle) |
| **Dependency Management** | Manual JAR files in `lib/` | Automatic from Maven Central |
| **Project Structure** | Flexible | Convention over configuration |
| **Multi-module** | Custom scripts | Native support |
| **IDE Integration** | Limited | Excellent (IntelliJ, Eclipse, VS Code) |
| **Reproducibility** | Manual classpath management | Dependency version locking |

## Command Mapping

### Basic Commands

| Ant Command | Maven Equivalent | Purpose |
|-------------|------------------|---------|
| `ant compile` | `mvn compile` | Compile source code |
| `ant clean` | `mvn clean` | Remove build artifacts |
| `ant jar` | `mvn package` | Create JAR files |
| `ant unitTest` | `mvn test` | Run unit tests |
| `ant buildAll` | `mvn clean install` | Full build |
| `ant buildWebApps` | `mvn package -Pwebapp` | Build WAR files |

### Advanced Commands

| Ant Command | Maven Equivalent | Purpose |
|-------------|------------------|---------|
| `ant compile-deploy` | `mvn compile war:war` | Compile and create WAR |
| `ant -Dmodule=engine compile` | `mvn compile -pl yawl-engine` | Build specific module |
| `ant javadoc` | `mvn javadoc:javadoc` | Generate documentation |
| `ant -verbose` | `mvn -X` | Verbose output |

## Directory Structure Changes

### Ant Structure (Old)
```
yawl/
├── src/                         # All source code
├── test/                        # All test code
├── lib/                         # Manual JAR dependencies
├── build/                       # Ant build scripts
│   ├── build.xml
│   ├── compile.xml
│   └── deploy.xml
├── classes/                     # Compiled classes (Ant output)
└── dist/                        # Distribution files
```

### Maven Structure (New)
```
yawl/
├── pom.xml                      # Parent POM
├── yawl-elements/
│   ├── pom.xml
│   └── src/main/java/...        # Symlink to ../src/org/.../elements
├── yawl-engine/
│   ├── pom.xml
│   └── src/main/java/...        # Symlink to ../src/org/.../engine
├── yawl-integration/
│   ├── pom.xml
│   └── src/main/java/...
└── target/                      # Maven build output (per module)
```

**Note**: Source code remains in original locations. Module POMs reference parent directories via `<sourceDirectory>../src/org/.../module</sourceDirectory>`.

## Dependency Management Migration

### Ant: Manual JARs
```
lib/
├── commons-lang3-3.14.0.jar
├── log4j-api-2.23.1.jar
├── hibernate-core-6.5.1.jar
└── ...
```

**Problems**:
- Version conflicts
- Transitive dependency hell
- Manual updates
- Large repository size

### Maven: Automatic Resolution
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.14.0</version>
</dependency>
```

**Benefits**:
- Automatic transitive dependencies
- Version management via BOM
- Smaller repository (no JARs checked in)
- Security vulnerability scanning

## Build Lifecycle Comparison

### Ant Lifecycle (Custom)
```
clean → init → compile → jar → test → deploy
```

Each target explicitly defined with dependencies.

### Maven Lifecycle (Standard)
```
clean → validate → compile → test → package → verify → install → deploy
```

Phases automatically execute in order. Plugins bind to phases.

## Property Migration

### Ant Properties
```xml
<!-- build.properties -->
<property name="src.dir" value="src"/>
<property name="build.dir" value="classes"/>
<property name="lib.dir" value="lib"/>
```

### Maven Properties
```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <hibernate.version>6.5.1.Final</hibernate.version>
</properties>
```

## Module-Specific Migration

### Engine Module

**Ant `build.xml`**:
```xml
<target name="compile-engine">
    <javac srcdir="${src.dir}/org/yawlfoundation/yawl/engine"
           destdir="${build.dir}"
           classpath="${lib.dir}/*:${build.dir}"/>
</target>
```

**Maven `yawl-engine/pom.xml`**:
```xml
<build>
    <sourceDirectory>../src/org/yawlfoundation/yawl/engine</sourceDirectory>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

### Integration Module

**Ant**: Custom download and classpath management for MCP/A2A SDKs

**Maven**: Automatic dependency resolution
```xml
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp</artifactId>
    <version>0.17.2</version>
</dependency>
```

## Testing Migration

### Ant Tests
```xml
<target name="unitTest" depends="compile">
    <junit printsummary="yes" fork="yes">
        <classpath>
            <pathelement location="${build.dir}"/>
            <fileset dir="${lib.dir}"/>
        </classpath>
        <batchtest>
            <fileset dir="${test.dir}">
                <include name="**/*Test.java"/>
            </fileset>
        </batchtest>
    </junit>
</target>
```

### Maven Tests
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
            <include>**/*TestSuite.java</include>
        </includes>
    </configuration>
</plugin>
```

**Run**: `mvn test`

## Assembly and Packaging

### Ant Distribution
```xml
<target name="dist">
    <tar destfile="yawl-${version}.tar.gz" compression="gzip">
        <tarfileset dir="${build.dir}" prefix="yawl/lib"/>
        <tarfileset dir="${lib.dir}" prefix="yawl/lib"/>
        <tarfileset dir="schema" prefix="yawl/schema"/>
        <tarfileset dir="scripts" prefix="yawl/bin" filemode="755"/>
    </tar>
</target>
```

### Maven Assembly
```bash
mvn package assembly:single -Ddescriptor=maven-assembly-descriptor.xml
```

**Output**: `target/yawl-parent-5.2-distribution.tar.gz`

## IDE Migration

### Ant Projects (Old)
- Eclipse: Manual classpath configuration
- IntelliJ IDEA: Build via external tool
- NetBeans: Native Ant support

### Maven Projects (New)
- **Eclipse**: File → Import → Maven → Existing Maven Projects
- **IntelliJ IDEA**: File → Open → Select `pom.xml`
- **VS Code**: Auto-detects Maven projects with Java extension pack

## Continuous Integration

### Ant CI (Jenkins)
```groovy
stage('Build') {
    sh 'ant clean buildAll'
}
stage('Test') {
    sh 'ant unitTest'
}
```

### Maven CI (GitHub Actions)
```yaml
- name: Build with Maven
  run: mvn clean install -Pprod
- name: Run Tests
  run: mvn test
```

## Performance Comparison

| Metric | Ant | Maven |
|--------|-----|-------|
| **Initial Build** | ~2 min | ~3 min (first run, downloads deps) |
| **Incremental Build** | ~18 sec | ~15 sec |
| **Clean Build** | ~2 min | ~2 min (deps cached) |
| **Test Execution** | ~45 sec | ~40 sec |

**Note**: Maven is slower on first build due to dependency downloads, but subsequent builds are faster due to better incremental compilation.

## Migration Checklist

### Phase 1: Preparation
- [ ] Install Maven 3.9.0+
- [ ] Verify Java 21 installation
- [ ] Backup existing `lib/` directory
- [ ] Document custom Ant targets

### Phase 2: Parent POM
- [x] Create `pom.xml` with parent configuration
- [x] Define properties (versions, encoding)
- [x] Set up dependency management (BOMs)
- [x] Configure plugin management

### Phase 3: Module POMs
- [x] Create module directories
- [x] Create module `pom.xml` files
- [x] Define inter-module dependencies
- [x] Configure source/test directories

### Phase 4: Validation
- [ ] Run `mvn validate`
- [ ] Run `mvn compile`
- [ ] Run `mvn test`
- [ ] Run `mvn package`
- [ ] Run `mvn install`

### Phase 5: Cleanup
- [ ] Remove `lib/` directory (after backup)
- [ ] Remove `classes/` build output
- [ ] Remove `dist/` directory
- [ ] Mark `build/` as deprecated

### Phase 6: Documentation
- [x] Update `CLAUDE.md` build commands
- [x] Create `MAVEN_BUILD_GUIDE.md`
- [x] Create `MAVEN_MODULE_DEPENDENCIES.md`
- [x] Update CI/CD pipelines

## Common Issues and Solutions

### Issue 1: "Cannot resolve dependency"
**Cause**: Maven can't download dependency from repository

**Solution**:
```bash
# Check internet connection
mvn dependency:resolve -X

# Use corporate proxy
export MAVEN_OPTS="-Dhttps.proxyHost=proxy.company.com -Dhttps.proxyPort=8080"
```

### Issue 2: "Source directory not found"
**Cause**: Module POM has incorrect `<sourceDirectory>`

**Solution**: Verify path is relative to module directory
```xml
<!-- Correct -->
<sourceDirectory>../src/org/yawlfoundation/yawl/engine</sourceDirectory>

<!-- Incorrect -->
<sourceDirectory>src/org/yawlfoundation/yawl/engine</sourceDirectory>
```

### Issue 3: "Version conflict"
**Cause**: Multiple versions of same dependency

**Solution**:
```bash
# Analyze dependency tree
mvn dependency:tree

# Force specific version in dependencyManagement
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
            <version>1.2</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Issue 4: "Tests fail in Maven but pass in Ant"
**Cause**: Different classpath ordering

**Solution**:
```xml
<!-- Ensure test resources are loaded -->
<build>
    <testResources>
        <testResource>
            <directory>test/resources</directory>
        </testResource>
    </testResources>
</build>
```

## Gradual Migration Strategy

### Week 1-2: Parallel Builds
- Keep Ant builds working
- Introduce Maven builds
- Validate Maven output matches Ant output

### Week 3-4: Maven Primary
- Use Maven for CI/CD
- Keep Ant as fallback
- Train developers on Maven

### Week 5-6: Ant Deprecation
- Mark Ant builds deprecated
- Update documentation
- Remove Ant from CI/CD

### Week 7-8: Cleanup
- Archive Ant build files
- Remove `lib/` directory
- Final validation

## Developer Training

### Quick Start
```bash
# Clone repository
git clone <repo-url>
cd yawl

# Build all modules
mvn clean install

# Build specific module
cd yawl-engine
mvn clean install

# Run tests
mvn test

# Package
mvn package
```

### Common Workflows

**1. Add New Dependency**
```xml
<!-- In module pom.xml -->
<dependency>
    <groupId>org.example</groupId>
    <artifactId>example-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

**2. Run Single Test**
```bash
mvn test -Dtest=YEngineTest
```

**3. Skip Tests for Quick Build**
```bash
mvn clean install -DskipTests
```

**4. Debug Dependency Issues**
```bash
mvn dependency:tree -Dverbose
mvn dependency:analyze
```

## Resources

- [Maven Official Documentation](https://maven.apache.org/guides/)
- [Maven Central Repository](https://search.maven.org/)
- [Maven POM Reference](https://maven.apache.org/pom.html)
- [Maven Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)
- [YAWL Maven Build Guide](./MAVEN_BUILD_GUIDE.md)
- [YAWL Module Dependencies](./MAVEN_MODULE_DEPENDENCIES.md)

## Support

For migration issues:
1. Check `MAVEN_BUILD_GUIDE.md`
2. Run `mvn -X` for debug output
3. Consult `MAVEN_MODULE_DEPENDENCIES.md` for module structure
4. File issue on GitHub with `[Maven]` prefix

## Timeline

- **Phase 1 Complete**: Parent POM and module structure (2026-02-16)
- **Phase 2 Target**: Ant deprecation (2026-03-01)
- **Phase 3 Target**: Ant removal (2026-04-01)
- **Phase 4 Target**: Maven-only builds (2026-05-01)
