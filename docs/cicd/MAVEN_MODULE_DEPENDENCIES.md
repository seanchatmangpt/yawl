# YAWL Maven Module Dependency Graph

**Version:** 5.2
**Date:** 2026-02-16
**Related:** MAVEN_MODULE_STRUCTURE.md

## Module Dependency Graph (ASCII)

```
┌─────────────────────────────────────────────────────────────────┐
│                         yawl-parent (pom)                       │
│                    Aggregator & Parent POM                      │
└─────────────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┴────────────────┐
                │                                 │
        ┌───────▼────────┐              ┌────────▼────────┐
        │   yawl-bom     │              │  yawl-common    │
        │     (pom)      │              │     (jar)       │
        │ Dependency Mgmt│              │  Foundation     │
        └────────────────┘              └────────┬────────┘
                                                 │
                                        ┌────────▼────────┐
                                        │  yawl-elements  │
                                        │     (jar)       │
                                        │ Workflow Model  │
                                        └────────┬────────┘
                                                 │
                        ┌────────────────────────┼────────────────────────┐
                        │                        │                        │
              ┌─────────▼─────────┐   ┌──────────▼──────────┐  ┌─────────▼─────────┐
              │ yawl-engine-core  │   │yawl-engine-stateless│  │ yawl-test-utilities│
              │      (jar)        │   │       (jar)         │  │      (jar)         │
              │  Stateful Engine  │   │  Stateless Engine   │  │   Test Support     │
              └─────────┬─────────┘   └─────────────────────┘  └────────────────────┘
                        │
              ┌─────────▼─────────┐
              │ yawl-engine-api   │
              │      (jar)        │
              │ Interfaces A/B/E/X│
              └─────────┬─────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
┌───────▼────────┐            ┌─────────▼─────────┐
│ yawl-integration│            │  yawl-resourcing  │
│      (jar)     │            │       (jar)       │
│  MCP/A2A/AI    │            │  Resource Mgmt    │
└────────────────┘            └─────────┬─────────┘
                                        │
                        ┌───────────────┴───────────────────────┐
                        │                                       │
              ┌─────────▼─────────┐                  ┌──────────▼──────────┐
              │   yawl-services   │                  │   yawl-webapps      │
              │      (pom)        │                  │      (pom)          │
              └─────────┬─────────┘                  └──────────┬──────────┘
                        │                                       │
    ┌──────┬────────────┼────────────┬───────┬───────┐         │
    │      │            │            │       │       │         │
┌───▼───┐ ┌▼────┐  ┌───▼──┐  ┌─────▼─┐ ┌───▼──┐ ┌──▼──┐      │
│worklet│ │sched│  │cost  │  │proclet│ │mail  │ │doc  │      │
│service│ │uling│  │service│ │service│ │service│ │store│      │
└───────┘ └─────┘  └──────┘  └───────┘ └──────┘ └─────┘      │
                                                               │
                        ┌──────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬───────────────┐
        │               │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
│ yawl-engine  │ │yawl-resource│ │yawl-monitor│ │yawl-reporter│
│   webapp     │ │   webapp    │ │  webapp    │ │  webapp     │
│    (war)     │ │    (war)    │ │   (war)    │ │   (war)     │
└──────────────┘ └─────────────┘ └────────────┘ └─────────────┘

┌─────────────────┐              ┌──────────────────┐
│yawl-control-panel│              │yawl-distribution│
│      (jar)      │              │      (pom)      │
│  Desktop Tool   │              │  Assemblies     │
└─────────────────┘              └─────────────────┘
```

## Dependency Matrix

| Module                   | Depends On                                      | Used By                                  |
|--------------------------|------------------------------------------------|------------------------------------------|
| yawl-bom                 | (none)                                         | All modules (via dependencyManagement)   |
| yawl-common              | (external libs only)                           | All other modules                        |
| yawl-elements            | yawl-common                                    | Engines, API, services                   |
| yawl-engine-core         | yawl-elements, yawl-common                     | yawl-engine-api, yawl-engine-webapp      |
| yawl-engine-stateless    | yawl-elements, yawl-common                     | yawl-integration, tests                  |
| yawl-engine-api          | yawl-engine-core, yawl-elements, yawl-common   | Integration, resourcing, all services    |
| yawl-integration         | yawl-engine-api, yawl-common                   | yawl-engine-webapp, external consumers   |
| yawl-resourcing          | yawl-engine-api, yawl-common                   | Services, yawl-resource-webapp           |
| yawl-worklet-service     | yawl-engine-api, yawl-common                   | yawl-services, deployments               |
| yawl-scheduling-service  | yawl-resourcing, yawl-engine-api, yawl-common  | yawl-services, deployments               |
| yawl-cost-service        | yawl-resourcing, yawl-engine-api, yawl-common  | yawl-services, deployments               |
| yawl-proclet-service     | yawl-engine-api, yawl-common                   | yawl-services, deployments               |
| yawl-mail-service        | yawl-engine-api, yawl-common                   | yawl-services, deployments               |
| yawl-document-store      | yawl-engine-api, yawl-common                   | yawl-services, deployments               |
| yawl-digital-signature   | yawl-engine-api, yawl-common                   | yawl-services, deployments               |
| yawl-engine-webapp       | yawl-integration, yawl-engine-api, yawl-engine-core | Tomcat deployments                  |
| yawl-resource-webapp     | yawl-resourcing, yawl-engine-api               | Tomcat deployments                       |
| yawl-monitor-webapp      | yawl-engine-api                                | Tomcat deployments                       |
| yawl-reporter-webapp     | yawl-engine-api                                | Tomcat deployments                       |
| yawl-control-panel       | yawl-engine-api, yawl-common                   | Standalone deployment                    |
| yawl-test-utilities      | yawl-common, JUnit                             | All modules (test scope)                 |
| yawl-distribution        | All modules                                    | Release process                          |

## Layered Architecture View

```
┌─────────────────────────────────────────────────────────────────┐
│                    Layer 7: Distribution                        │
│                      yawl-distribution                          │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│              Layer 6: Presentation & Tools                      │
│    yawl-*-webapp (4x)  │  yawl-control-panel                    │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                  Layer 5: Services                              │
│  worklet│scheduling│cost│proclet│mail│docstore│signature        │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│            Layer 4: Advanced Capabilities                       │
│         yawl-integration  │  yawl-resourcing                    │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│                 Layer 3: Engine API                             │
│                  yawl-engine-api                                │
│              (Interfaces A, B, E, X)                            │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│              Layer 2: Execution Engines                         │
│       yawl-engine-core  │  yawl-engine-stateless                │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│              Layer 1: Domain Model                              │
│                   yawl-elements                                 │
└─────────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────────┐
│              Layer 0: Foundation                                │
│                    yawl-common                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Critical Dependency Rules

### 1. Acyclic Dependencies
No circular dependencies allowed. Maven reactor enforces this.

### 2. Layered Dependencies
Modules can only depend on modules in lower layers.

### 3. API Stability
- `yawl-engine-api`: Stable, semantic versioning
- `yawl-common`: Stable, backward compatible
- `yawl-elements`: Stable, specification model

### 4. Optional Dependencies
- OpenTelemetry in `yawl-integration`: Optional
- Spring Boot Actuator in webapps: Optional
- Resilience4j in `yawl-integration`: Optional (but recommended)

### 5. Test Dependencies
All modules can depend on `yawl-test-utilities` in test scope.

## Build Order Guarantee

Maven reactor builds modules in this order:

1. **yawl-bom** (no dependencies)
2. **yawl-common** (no YAWL dependencies)
3. **yawl-elements** (depends on yawl-common)
4. **Parallel Build Group 1:**
   - yawl-engine-core
   - yawl-engine-stateless
   - yawl-test-utilities
5. **yawl-engine-api** (depends on engines)
6. **Parallel Build Group 2:**
   - yawl-integration
   - yawl-resourcing
7. **Parallel Build Group 3 (Services):**
   - yawl-worklet-service
   - yawl-mail-service
   - yawl-document-store
   - yawl-digital-signature
   - yawl-proclet-service
8. **Parallel Build Group 4 (Services with resourcing):**
   - yawl-scheduling-service
   - yawl-cost-service
9. **Parallel Build Group 5 (Webapps):**
   - yawl-engine-webapp
   - yawl-resource-webapp
   - yawl-monitor-webapp
   - yawl-reporter-webapp
10. **yawl-control-panel**
11. **yawl-distribution**

## External Dependency Categories

### Category 1: Managed by Spring Boot BOM
- Jackson (JSON)
- Logback
- SLF4J
- Commons libraries (some)
- HikariCP

### Category 2: Managed by Jakarta EE BOM
- Jakarta Servlet API
- Jakarta Persistence API
- Jakarta XML Bind
- Jakarta Mail
- Jakarta Faces (JSF)
- Jakarta CDI

### Category 3: Managed by OpenTelemetry BOM
- opentelemetry-api
- opentelemetry-sdk
- opentelemetry-exporter-*

### Category 4: Managed by Resilience4j BOM
- resilience4j-circuitbreaker
- resilience4j-retry
- resilience4j-ratelimiter

### Category 5: Direct Version Properties
- Hibernate 6.5.1.Final
- H2 Database 2.2.224
- PostgreSQL 42.7.2
- MySQL 8.0.36
- Log4j 2.24.1
- JDOM2 2.0.5
- MCP SDK 0.17.2
- A2A SDK 1.0.0.Alpha2

## Scope Strategy

### Compile Scope (default)
Most dependencies, transitive to consumers.

### Provided Scope
- `jakarta.servlet-api` (provided by servlet container)
- Servlet container dependencies in webapps

### Runtime Scope
- Database drivers (when multiple supported)
- Logging implementations

### Test Scope
- JUnit, Hamcrest, XMLUnit
- Test utilities
- H2 database (for tests)

### Optional Scope
- OpenTelemetry (observability)
- Spring Boot Actuator (health endpoints)
- Advanced features not required by all deployments

## Inter-Module Communication Patterns

### Direct Dependency
Module A directly depends on Module B:
```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>module-b</artifactId>
</dependency>
```

### Interface-Based Dependency
Module A depends on API provided by Module B:
```
yawl-integration → yawl-engine-api (not yawl-engine-core)
```

### Service Provider Interface (SPI)
Future: Plugin architecture using Java SPI:
```
yawl-engine-api defines interface
yawl-integration provides implementation
```

## Module Size Estimates

| Module                   | Classes (est.) | LOC (est.) | JAR Size (est.) |
|--------------------------|----------------|------------|-----------------|
| yawl-common              | 50             | 5,000      | 100 KB          |
| yawl-elements            | 80             | 8,000      | 150 KB          |
| yawl-engine-core         | 120            | 15,000     | 300 KB          |
| yawl-engine-stateless    | 60             | 6,000      | 120 KB          |
| yawl-engine-api          | 100            | 12,000     | 250 KB          |
| yawl-integration         | 80             | 10,000     | 200 KB          |
| yawl-resourcing          | 200            | 25,000     | 500 KB          |
| yawl-worklet-service     | 50             | 6,000      | 120 KB          |
| yawl-scheduling-service  | 40             | 5,000      | 100 KB          |
| yawl-cost-service        | 30             | 3,000      | 60 KB           |
| yawl-proclet-service     | 100            | 12,000     | 250 KB          |
| yawl-mail-service        | 20             | 2,000      | 40 KB           |
| yawl-document-store      | 25             | 3,000      | 60 KB           |
| yawl-digital-signature   | 15             | 1,500      | 30 KB           |
| yawl-engine-webapp       | 40             | 4,000      | 20 MB (WAR)     |
| yawl-resource-webapp     | 80             | 10,000     | 15 MB (WAR)     |
| yawl-monitor-webapp      | 30             | 3,000      | 10 MB (WAR)     |
| yawl-reporter-webapp     | 25             | 2,500      | 8 MB (WAR)      |
| yawl-control-panel       | 40             | 4,000      | 100 KB          |

**Total:** ~1,200 classes, ~140,000 LOC

## Dependency Scope Examples

### yawl-common Dependencies
```xml
<dependencies>
    <!-- All compile scope, transitive -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>

    <dependency>
        <groupId>org.jdom</groupId>
        <artifactId>jdom2</artifactId>
    </dependency>

    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
    </dependency>
</dependencies>
```

### yawl-engine-webapp Dependencies
```xml
<dependencies>
    <!-- Compile scope YAWL modules -->
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-integration</artifactId>
    </dependency>

    <!-- Provided scope (container supplies) -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Runtime scope (not needed at compile time) -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### yawl-integration Dependencies
```xml
<dependencies>
    <!-- Required compile dependencies -->
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <dependency>
        <groupId>io.modelcontextprotocol</groupId>
        <artifactId>mcp</artifactId>
    </dependency>

    <!-- Optional dependencies -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Test dependencies -->
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-test-utilities</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Transitive Dependency Management

### Problem: Version Conflicts
Multiple modules depend on different versions of same library.

### Solution: BOM Management
All versions managed in `yawl-bom`, guaranteed consistency.

### Example Scenario
```
yawl-engine-core → Jackson 2.18.2 (via Spring Boot BOM)
yawl-integration → Jackson 2.18.2 (via Spring Boot BOM)
Result: No conflict, both use same version
```

### Dependency Exclusions
Rarely needed, but possible:

```xml
<dependency>
    <groupId>some.library</groupId>
    <artifactId>some-artifact</artifactId>
    <exclusions>
        <exclusion>
            <groupId>unwanted.dep</groupId>
            <artifactId>unwanted-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## Plugin Dependencies

### Common Plugins (inherited from parent)
- maven-compiler-plugin (Java 21)
- maven-surefire-plugin (testing)
- maven-jar-plugin (JAR creation)
- jacoco-maven-plugin (coverage)
- maven-dependency-plugin (analysis)

### WAR-Specific Plugins
- maven-war-plugin (WAR packaging)

### Distribution Plugins
- maven-assembly-plugin (ZIP creation)
- maven-shade-plugin (fat JARs)
- docker-maven-plugin (Docker images)

## Dependency Tree Example

```
yawl-engine-webapp (war)
├── yawl-engine-core (jar)
│   ├── yawl-elements (jar)
│   │   └── yawl-common (jar)
│   │       ├── commons-lang3 (jar)
│   │       ├── jdom2 (jar)
│   │       └── log4j-api (jar)
│   ├── yawl-common (jar) [already listed]
│   └── hibernate-core (jar)
│       ├── jakarta.persistence-api (jar)
│       └── ... (managed by BOM)
├── yawl-integration (jar)
│   ├── yawl-engine-api (jar)
│   │   ├── yawl-engine-core (jar) [already listed]
│   │   └── okhttp (jar)
│   ├── mcp (jar)
│   │   └── mcp-core (jar)
│   └── a2a-java-sdk-* (jars)
└── jakarta.servlet-api (jar, scope=provided)
```

## Circular Dependency Prevention

### Design Rule
Never allow circular dependencies between modules.

### Enforcement
Maven reactor will fail if circular dependency detected.

### Refactoring Pattern
If modules A and B need each other:
1. Extract common interface to module C
2. Both A and B depend on C
3. Use dependency injection or service locator pattern

### Example
```
Before (circular):
yawl-engine-core ←→ yawl-resourcing

After (acyclic):
yawl-engine-core → yawl-engine-api ← yawl-resourcing
```

## Dependency Validation

### Maven Commands

```bash
# Display dependency tree
mvn dependency:tree

# Display effective POM (after inheritance & BOM)
mvn help:effective-pom

# Analyze dependencies
mvn dependency:analyze

# Find duplicates
mvn dependency:tree -Dverbose

# Resolve conflicts
mvn dependency:resolve
```

### CI/CD Checks

Automated checks in build pipeline:
- No SNAPSHOT dependencies in release builds
- All dependencies resolved
- No dependency convergence errors
- OWASP dependency check (no critical CVEs)

## Conclusion

This dependency graph design ensures:
- Clean layered architecture
- No circular dependencies
- Controlled transitive dependencies
- Clear module responsibilities
- Scalable build performance
- Easy external consumption

The structure supports YAWL's evolution toward cloud-native deployments while maintaining backward compatibility with existing interfaces and deployments.
