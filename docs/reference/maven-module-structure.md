# YAWL Maven Multi-Module Structure

**Version:** 6.0.0
**Date:** 2026-02-16
**Status:** Design Phase
**Branch:** claude/maven-first-build-kizBd

## Executive Summary

This document defines the Maven multi-module architecture for YAWL v6.0.0, transforming the current monolithic build into a modular, dependency-managed structure. The design follows Maven best practices, leverages BOM-based dependency management, and establishes clear module boundaries aligned with YAWL's logical architecture.

## Design Goals

1. **Modularity**: Separate concerns into independently buildable, testable, and releasable modules
2. **Dependency Clarity**: Make inter-module dependencies explicit and enforce proper layering
3. **Reusability**: Enable external projects to depend on specific YAWL modules without pulling entire codebase
4. **Build Performance**: Support parallel builds and incremental compilation
5. **Backward Compatibility**: Maintain existing Interface A/B/E/X contracts
6. **Cloud-Native**: Facilitate containerization and microservice deployments

## Module Hierarchy

```
yawl-parent (pom)
├── yawl-bom (pom)
├── yawl-common (jar)
├── yawl-elements (jar)
├── yawl-engine-core (jar)
├── yawl-engine-stateless (jar)
├── yawl-engine-api (jar)
├── yawl-integration (jar)
├── yawl-resourcing (jar)
├── yawl-services (pom)
│   ├── yawl-worklet-service (jar)
│   ├── yawl-scheduling-service (jar)
│   ├── yawl-cost-service (jar)
│   ├── yawl-proclet-service (jar)
│   ├── yawl-mail-service (jar)
│   ├── yawl-document-store (jar)
│   └── yawl-digital-signature (jar)
├── yawl-webapps (pom)
│   ├── yawl-engine-webapp (war)
│   ├── yawl-resource-webapp (war)
│   ├── yawl-monitor-webapp (war)
│   └── yawl-reporter-webapp (war)
├── yawl-control-panel (jar)
├── yawl-test-utilities (jar, test scope)
└── yawl-distribution (pom)
```

## Module Definitions

### 1. yawl-parent (Root POM)

**Artifact ID:** `yawl-parent`
**Packaging:** `pom`
**Purpose:** Top-level aggregator and parent POM defining global configuration

**Responsibilities:**
- Aggregate all child modules
- Define global properties (Java version, encoding, etc.)
- Configure shared plugins (compiler, surefire, jacoco)
- Import yawl-bom for dependency management
- Define repository locations
- Configure distribution management

**Key Properties:**
```xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <yawl.version>5.2</yawl.version>
</properties>
```

**Modules:**
```xml
<modules>
    <module>yawl-bom</module>
    <module>yawl-common</module>
    <module>yawl-elements</module>
    <module>yawl-engine-core</module>
    <module>yawl-engine-stateless</module>
    <module>yawl-engine-api</module>
    <module>yawl-integration</module>
    <module>yawl-resourcing</module>
    <module>yawl-services</module>
    <module>yawl-webapps</module>
    <module>yawl-control-panel</module>
    <module>yawl-test-utilities</module>
    <module>yawl-distribution</module>
</modules>
```

### 2. yawl-bom (Bill of Materials)

**Artifact ID:** `yawl-bom`
**Packaging:** `pom`
**Purpose:** Centralized dependency version management

**Responsibilities:**
- Define versions for all external dependencies
- Import third-party BOMs (Spring Boot, Jakarta EE, OpenTelemetry)
- Provide managed versions for all YAWL modules
- Ensure consistent transitive dependency versions

**Dependency Management:**
```xml
<dependencyManagement>
    <dependencies>
        <!-- External BOMs -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <dependency>
            <groupId>jakarta.platform</groupId>
            <artifactId>jakarta.jakartaee-bom</artifactId>
            <version>10.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>1.40.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- YAWL Modules -->
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-common</artifactId>
            <version>${yawl.version}</version>
        </dependency>

        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-elements</artifactId>
            <version>${yawl.version}</version>
        </dependency>

        <!-- ... all other YAWL modules ... -->
    </dependencies>
</dependencyManagement>
```

**Usage Pattern:**
Other YAWL modules and external projects import this BOM:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-bom</artifactId>
            <version>5.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 3. yawl-common (Shared Utilities)

**Artifact ID:** `yawl-common`
**Packaging:** `jar`
**Purpose:** Foundation layer providing utilities used across all modules

**Source Packages:**
- `org.yawlfoundation.yawl.util.*`
- `org.yawlfoundation.yawl.exceptions.*`
- `org.yawlfoundation.yawl.logging.*`
- `org.yawlfoundation.yawl.schema.*`
- `org.yawlfoundation.yawl.unmarshal.*`
- `org.yawlfoundation.yawl.security.*`

**Key Classes:**
- `StringUtil`, `JDOMUtil`, `HttpUtil`, `CheckSummer`
- `HibernateEngine`, `HikariCPConnectionProvider`
- `YAWLException`, `YPersistenceException`, `YStateException`
- `YEventLogger`, `YLogDataItem`
- `YDataSchemaCache`, `XSDType`
- `PasswordEncryptor`, `ObjectInputStreamConfig`

**Dependencies:**
```xml
<dependencies>
    <!-- Jakarta XML Bind -->
    <dependency>
        <groupId>jakarta.xml.bind</groupId>
        <artifactId>jakarta.xml.bind-api</artifactId>
    </dependency>

    <!-- Hibernate Core -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
    </dependency>

    <!-- HikariCP -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
    </dependency>

    <!-- Apache Commons -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
    </dependency>

    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-io</artifactId>
    </dependency>

    <!-- JDOM2 -->
    <dependency>
        <groupId>org.jdom</groupId>
        <artifactId>jdom2</artifactId>
    </dependency>

    <!-- Logging -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
    </dependency>

    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

**Module-Specific Configuration:**
```xml
<build>
    <sourceDirectory>../../src</sourceDirectory>
    <includes>
        <include>org/yawlfoundation/yawl/util/**/*.java</include>
        <include>org/yawlfoundation/yawl/exceptions/**/*.java</include>
        <include>org/yawlfoundation/yawl/logging/**/*.java</include>
        <include>org/yawlfoundation/yawl/schema/**/*.java</include>
        <include>org/yawlfoundation/yawl/unmarshal/**/*.java</include>
        <include>org/yawlfoundation/yawl/security/**/*.java</include>
    </includes>
</build>
```

### 4. yawl-elements (Workflow Elements)

**Artifact ID:** `yawl-elements`
**Packaging:** `jar`
**Purpose:** Workflow specification and element definitions

**Source Packages:**
- `org.yawlfoundation.yawl.elements.*`

**Key Classes:**
- `YSpecification`, `YNet`, `YTask`, `YCondition`
- `YDecomposition`, `YAtomicTask`, `YCompositeTask`
- `YFlow`, `YExternalNetElement`
- `YInputCondition`, `YOutputCondition`
- `state.*` package (YIdentifier, YInternalCondition, etc.)

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <!-- JDOM2 for XML processing -->
    <dependency>
        <groupId>org.jdom</groupId>
        <artifactId>jdom2</artifactId>
    </dependency>
</dependencies>
```

**Description:**
This module contains the core Petri net-based workflow model. It defines all workflow element types and their relationships, independent of any execution engine. This separation allows for specification parsing, validation, and manipulation without requiring the full engine.

### 5. yawl-engine-core (Stateful Engine)

**Artifact ID:** `yawl-engine-core`
**Packaging:** `jar`
**Purpose:** Core stateful workflow execution engine

**Source Packages:**
- `org.yawlfoundation.yawl.engine.*` (excluding interfce.*)
- `org.yawlfoundation.yawl.authentication.*`

**Key Classes:**
- `YEngine`, `YNetRunner`, `YWorkItem`
- `YPersistenceManager`, `YWorkItemRepository`
- `ObserverGatewayController`, `YEventLogger`
- `YTimer`, `YWorkItemTimer`, `YTimerVariable`
- `YClient`, `YExternalClient`, `YSession`

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-elements</artifactId>
    </dependency>

    <!-- Hibernate for persistence -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
    </dependency>

    <!-- Jakarta Persistence API -->
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
    </dependency>
</dependencies>
```

**Build Configuration:**
```xml
<build>
    <resources>
        <resource>
            <directory>../../build/properties</directory>
            <includes>
                <include>hibernate.cfg.xml</include>
                <include>log4j2.xml</include>
            </includes>
        </resource>
    </resources>
</build>
```

### 6. yawl-engine-stateless (Stateless Engine)

**Artifact ID:** `yawl-engine-stateless`
**Packaging:** `jar`
**Purpose:** Lightweight stateless workflow engine for embedded/event-driven scenarios

**Source Packages:**
- `org.yawlfoundation.yawl.stateless.*`

**Key Classes:**
- `YStatelessEngine`, `YNetRunner` (stateless version)
- `YWorkItem` (stateless)
- `YCaseMonitor`, `YCaseMonitoringService`
- `YCaseImportExportService`
- `listener.*` (event listeners)
- `elements.*` (stateless element wrappers)

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-elements</artifactId>
    </dependency>

    <!-- No persistence dependencies - stateless by design -->
</dependencies>
```

**Description:**
The stateless engine provides event-driven workflow execution without database persistence. It's ideal for:
- Embedded applications requiring full control over state management
- Testing and simulation
- Microservice architectures with external state stores
- Event sourcing patterns

### 7. yawl-engine-api (Interface Layer)

**Artifact ID:** `yawl-engine-api`
**Packaging:** `jar`
**Purpose:** Engine interfaces (A, B, E, X) and API implementations

**Source Packages:**
- `org.yawlfoundation.yawl.engine.interfce.*`
- `org.yawlfoundation.yawl.balancer.*`

**Key Interfaces:**
- **Interface A**: Design-time specification management
- **Interface B**: Client/runtime work item management
- **Interface E**: Event and logging access
- **Interface X**: Exception and case management

**Key Classes:**
- `interfaceA.InterfaceA_EngineBasedServer`
- `interfaceA.InterfaceA_EnvironmentBasedClient`
- `interfaceB.InterfaceB_EngineBasedServer`
- `interfaceB.InterfaceB_EnvironmentBasedClient`
- `interfaceE.YLogGateway`, `interfaceE.YLogGatewayClient`
- `interfaceX.InterfaceX_EngineSideServer`
- `rest.*` (RESTful API resources)
- `EngineGateway`, `EngineGatewayImpl`
- `WorkItemRecord`, `SpecificationData`, `TaskInformation`

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-elements</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-core</artifactId>
    </dependency>

    <!-- Jakarta Servlet API -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- JAX-RS for REST endpoints -->
    <dependency>
        <groupId>jakarta.ws.rs</groupId>
        <artifactId>jakarta.ws.rs-api</artifactId>
    </dependency>

    <!-- OkHttp for client implementations -->
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
    </dependency>
</dependencies>
```

**API Contracts:**
This module defines and implements the core YAWL interfaces that external services and applications depend on. Interface stability and backward compatibility are critical.

### 8. yawl-integration (Integration Layer)

**Artifact ID:** `yawl-integration`
**Packaging:** `jar`
**Purpose:** External system integration (MCP, A2A, process mining, autonomous agents)

**Source Packages:**
- `org.yawlfoundation.yawl.integration.*`

**Key Components:**
- **MCP (Model Context Protocol):**
  - `mcp.YawlMcpServer`
  - `mcp.tools.*` (workflow management tools)
  - `mcp.resources.*` (specification/case resources)
  - `mcp.prompts.*` (workflow prompts)

- **A2A (Agent-to-Agent):**
  - `a2a.YawlA2AServer`
  - `a2a.YawlEngineAdapter`
  - `a2a.tasks.*` (agent task definitions)

- **Autonomous Agents:**
  - `autonomous.AutonomousAgent`
  - `autonomous.discovery.*` (work discovery strategies)
  - `autonomous.eligibility.*` (capability reasoning)
  - `autonomous.decision.*` (decision reasoning)
  - `autonomous.registry.*` (agent registry)

- **Process Mining:**
  - `processmining.EventLogExporter`
  - `processmining.PerformanceAnalyzer`

- **Order Fulfillment Example:**
  - `orderfulfillment.*`

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <!-- MCP SDK -->
    <dependency>
        <groupId>io.modelcontextprotocol</groupId>
        <artifactId>mcp</artifactId>
    </dependency>

    <dependency>
        <groupId>io.modelcontextprotocol</groupId>
        <artifactId>mcp-core</artifactId>
    </dependency>

    <dependency>
        <groupId>io.modelcontextprotocol</groupId>
        <artifactId>mcp-json-jackson2</artifactId>
    </dependency>

    <!-- A2A SDK -->
    <dependency>
        <groupId>io.anthropic</groupId>
        <artifactId>a2a-java-sdk-spec</artifactId>
    </dependency>

    <dependency>
        <groupId>io.anthropic</groupId>
        <artifactId>a2a-java-sdk-server-common</artifactId>
    </dependency>

    <dependency>
        <groupId>io.anthropic</groupId>
        <artifactId>a2a-java-sdk-transport-rest</artifactId>
    </dependency>

    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-circuitbreaker</artifactId>
    </dependency>

    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-retry</artifactId>
    </dependency>

    <!-- OpenTelemetry (optional) -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 9. yawl-resourcing (Resource Service Core)

**Artifact ID:** `yawl-resourcing`
**Packaging:** `jar`
**Purpose:** Human resource management, work queues, organizational data

**Source Packages:**
- `org.yawlfoundation.yawl.resourcing.*` (excluding `jsf.*`)

**Key Components:**
- Resource allocation/distribution
- Work queues and filters
- Organizational model
- Calendar and scheduling
- Codelets and constraints

**Key Classes:**
- `ResourceAdministrator`, `ResourceMap`, `WorkQueue`
- `allocators.*` (RoundRobin, ShortestQueue, etc.)
- `filters.*` (CapabilityFilter, OrgFilter)
- `constraints.*` (SeparationOfDuties, PiledExecution)
- `codelets.*` (AbstractCodelet, XQueryEvaluator)
- `calendar.*` (ResourceCalendar, ResourceScheduler)
- `datastore.*` (HibernateEngine, WorkItemCache, event logs)

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <!-- Hibernate -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
    </dependency>

    <!-- Jakarta Persistence -->
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
    </dependency>

    <!-- LDAP support -->
    <dependency>
        <groupId>javax.naming</groupId>
        <artifactId>jndi</artifactId>
    </dependency>
</dependencies>
```

### 10. yawl-services (Service Aggregator)

**Artifact ID:** `yawl-services`
**Packaging:** `pom`
**Purpose:** Parent module for all YAWL custom services

**Modules:**
```xml
<modules>
    <module>yawl-worklet-service</module>
    <module>yawl-scheduling-service</module>
    <module>yawl-cost-service</module>
    <module>yawl-proclet-service</module>
    <module>yawl-mail-service</module>
    <module>yawl-document-store</module>
    <module>yawl-digital-signature</module>
</modules>
```

#### 10.1 yawl-worklet-service

**Source:** `org.yawlfoundation.yawl.worklet.*`
**Purpose:** Dynamic workflow selection and exception handling via ripple-down rules

**Dependencies:** yawl-common, yawl-engine-api

#### 10.2 yawl-scheduling-service

**Source:** `org.yawlfoundation.yawl.scheduling.*`
**Purpose:** Resource-constrained workflow scheduling and planning

**Dependencies:** yawl-common, yawl-engine-api, yawl-resourcing

#### 10.3 yawl-cost-service

**Source:** `org.yawlfoundation.yawl.cost.*`
**Purpose:** Cost modeling and analysis for workflow execution

**Dependencies:** yawl-common, yawl-engine-api, yawl-resourcing

#### 10.4 yawl-proclet-service

**Source:** `org.yawlfoundation.yawl.procletService.*`
**Purpose:** Proclet-based workflow patterns and inter-case communication

**Dependencies:** yawl-common, yawl-engine-api, JUNG graph libraries

#### 10.5 yawl-mail-service

**Source:** `org.yawlfoundation.yawl.mailService.*`, `org.yawlfoundation.yawl.mailSender.*`
**Purpose:** Email notification and communication service

**Dependencies:** yawl-common, yawl-engine-api, Jakarta Mail

#### 10.6 yawl-document-store

**Source:** `org.yawlfoundation.yawl.documentStore.*`
**Purpose:** Document management and storage for workflow cases

**Dependencies:** yawl-common, yawl-engine-api, Apache Commons VFS

#### 10.7 yawl-digital-signature

**Source:** `org.yawlfoundation.yawl.digitalSignature.*`
**Purpose:** Digital signature verification for workflow approvals

**Dependencies:** yawl-common, yawl-engine-api, Apache Commons Codec

### 11. yawl-webapps (Web Application Aggregator)

**Artifact ID:** `yawl-webapps`
**Packaging:** `pom`
**Purpose:** Parent module for all WAR deployables

**Modules:**
```xml
<modules>
    <module>yawl-engine-webapp</module>
    <module>yawl-resource-webapp</module>
    <module>yawl-monitor-webapp</module>
    <module>yawl-reporter-webapp</module>
</modules>
```

#### 11.1 yawl-engine-webapp

**Artifact ID:** `yawl-engine-webapp`
**Packaging:** `war`
**Purpose:** YAWL Engine web application (servlet-based interfaces)

**Contents:**
- Interface servlets (A, B, E, X)
- REST API endpoints
- Admin interface
- Service registration

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-integration</artifactId>
    </dependency>

    <!-- Jakarta Servlet -->
    <dependency>
        <groupId>jakarta.servlet</groupId>
        <artifactId>jakarta.servlet-api</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Build Configuration:**
```xml
<build>
    <finalName>yawl</finalName>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-war-plugin</artifactId>
            <configuration>
                <webResources>
                    <resource>
                        <directory>../../build/engine</directory>
                        <includes>
                            <include>web.xml</include>
                        </includes>
                        <targetPath>WEB-INF</targetPath>
                    </resource>
                </webResources>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### 11.2 yawl-resource-webapp

**Artifact ID:** `yawl-resource-webapp`
**Packaging:** `war`
**Purpose:** Resource Service web application (JSF-based UI)

**Source:** `org.yawlfoundation.yawl.resourcing.jsf.*`

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-resourcing</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <!-- Jakarta Faces (JSF) -->
    <dependency>
        <groupId>jakarta.faces</groupId>
        <artifactId>jakarta.faces-api</artifactId>
    </dependency>

    <dependency>
        <groupId>org.glassfish</groupId>
        <artifactId>jakarta.faces</artifactId>
    </dependency>

    <!-- Jakarta CDI -->
    <dependency>
        <groupId>jakarta.enterprise</groupId>
        <artifactId>jakarta.enterprise.cdi-api</artifactId>
    </dependency>
</dependencies>
```

#### 11.3 yawl-monitor-webapp

**Artifact ID:** `yawl-monitor-webapp`
**Packaging:** `war`
**Purpose:** Real-time case and work item monitoring (JSF)

**Source:** `org.yawlfoundation.yawl.monitor.*`

**Dependencies:** yawl-engine-api, Jakarta Faces

#### 11.4 yawl-reporter-webapp

**Artifact ID:** `yawl-reporter-webapp`
**Packaging:** `war`
**Purpose:** Workflow reporting and analytics

**Source:** `org.yawlfoundation.yawl.reporter.*`

**Dependencies:** yawl-engine-api, Jakarta Servlet

### 12. yawl-control-panel

**Artifact ID:** `yawl-control-panel`
**Packaging:** `jar`
**Purpose:** Desktop control panel for managing YAWL engine and services

**Source:** `org.yawlfoundation.yawl.controlpanel.*`

**Main Class:** `org.yawlfoundation.yawl.controlpanel.YControlPanel`

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-engine-api</artifactId>
    </dependency>

    <!-- Swing/AWT for GUI -->
</dependencies>
```

**Executable JAR Configuration:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>org.yawlfoundation.yawl.controlpanel.YControlPanel</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 13. yawl-test-utilities

**Artifact ID:** `yawl-test-utilities`
**Packaging:** `jar`
**Purpose:** Shared test utilities and base test classes

**Source:** `test/org/yawlfoundation/yawl/util/*` (test utilities)

**Scope:** Test-only dependency for other modules

**Key Classes:**
- Test data generators
- Mock factories
- Test case utilities
- Integration test support

**Dependencies:**
```xml
<dependencies>
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <scope>compile</scope> <!-- compile scope for this module -->
    </dependency>

    <dependency>
        <groupId>org.hamcrest</groupId>
        <artifactId>hamcrest-core</artifactId>
        <scope>compile</scope>
    </dependency>

    <dependency>
        <groupId>xmlunit</groupId>
        <artifactId>xmlunit</artifactId>
        <scope>compile</scope>
    </dependency>

    <dependency>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-common</artifactId>
    </dependency>
</dependencies>
```

### 14. yawl-distribution

**Artifact ID:** `yawl-distribution`
**Packaging:** `pom`
**Purpose:** Create distribution packages (standalone JAR, Docker images, installers)

**Assemblies:**
- Full distribution ZIP
- Standalone executable JAR
- Docker build contexts
- Documentation bundle

**Build Configuration:**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-assembly-plugin</artifactId>
            <configuration>
                <descriptors>
                    <descriptor>src/assembly/distribution.xml</descriptor>
                    <descriptor>src/assembly/standalone.xml</descriptor>
                </descriptors>
            </configuration>
        </plugin>

        <plugin>
            <groupId>io.fabric8</groupId>
            <artifactId>docker-maven-plugin</artifactId>
            <configuration>
                <images>
                    <image>
                        <name>yawlfoundation/yawl:${project.version}</name>
                        <build>
                            <dockerFile>Dockerfile.modernized</dockerFile>
                        </build>
                    </image>
                </images>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Module Dependency Graph

```
Level 0 (Foundation):
yawl-common

Level 1 (Core Models):
yawl-elements → yawl-common

Level 2 (Engines):
yawl-engine-core → yawl-elements, yawl-common
yawl-engine-stateless → yawl-elements, yawl-common

Level 3 (APIs):
yawl-engine-api → yawl-engine-core, yawl-elements, yawl-common

Level 4 (Advanced):
yawl-integration → yawl-engine-api, yawl-common
yawl-resourcing → yawl-engine-api, yawl-common

Level 5 (Services):
yawl-worklet-service → yawl-engine-api, yawl-common
yawl-scheduling-service → yawl-resourcing, yawl-engine-api, yawl-common
yawl-cost-service → yawl-resourcing, yawl-engine-api, yawl-common
yawl-proclet-service → yawl-engine-api, yawl-common
yawl-mail-service → yawl-engine-api, yawl-common
yawl-document-store → yawl-engine-api, yawl-common
yawl-digital-signature → yawl-engine-api, yawl-common

Level 6 (Web Applications):
yawl-engine-webapp → yawl-engine-api, yawl-integration, yawl-engine-core
yawl-resource-webapp → yawl-resourcing, yawl-engine-api
yawl-monitor-webapp → yawl-engine-api
yawl-reporter-webapp → yawl-engine-api

Level 7 (Tools):
yawl-control-panel → yawl-engine-api, yawl-common

Test Support:
yawl-test-utilities → yawl-common (test scope across all modules)
```

## Dependency Management Strategy

### External Dependencies

All external dependency versions are managed in `yawl-bom` through:

1. **Imported BOMs:**
   - `spring-boot-dependencies` (3.2.2)
   - `jakarta.jakartaee-bom` (10.0.0)
   - `opentelemetry-bom` (1.40.0)
   - `resilience4j-bom` (2.2.0)

2. **Direct Version Properties:**
   - Hibernate: 6.5.1.Final
   - Jackson: 2.18.2
   - Log4j: 2.24.1
   - H2: 2.2.224
   - PostgreSQL: 42.7.2
   - MCP: 0.17.2
   - A2A: 1.0.0.Alpha2

### Inter-Module Dependencies

All YAWL modules declare dependencies on other YAWL modules without version:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-common</artifactId>
    <!-- version managed by yawl-bom -->
</dependency>
```

Versions are managed centrally in `yawl-bom`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-common</artifactId>
    <version>${yawl.version}</version>
</dependency>
```

### Optional Dependencies

Some integrations are optional:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <optional>true</optional>
</dependency>
```

Optional dependencies are not transitive and must be explicitly included by consumers.

## Build Lifecycle

### Standard Build Commands

```bash
# Build all modules
mvn clean install

# Build specific module
mvn clean install -pl yawl-engine-core

# Build module and dependents
mvn clean install -pl yawl-engine-core -amd

# Build module and dependencies
mvn clean install -pl yawl-engine-webapp -am

# Parallel build (4 threads)
mvn clean install -T 4

# Skip tests
mvn clean install -DskipTests

# Run tests only
mvn test

# Generate coverage report
mvn clean verify jacoco:report
```

### Build Order

Maven reactor determines build order based on dependencies:

1. yawl-bom
2. yawl-common
3. yawl-elements
4. yawl-engine-core, yawl-engine-stateless (parallel)
5. yawl-engine-api
6. yawl-integration, yawl-resourcing (parallel)
7. All services (parallel)
8. All webapps (parallel)
9. yawl-control-panel
10. yawl-distribution

### Testing Strategy

Each module contains:
- Unit tests: `src/test/java`
- Integration tests: Separate profile or module
- Test resources: `src/test/resources`

Global test configuration in parent POM:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
        </includes>
    </configuration>
</plugin>
```

## Migration Path from Monolithic Build

### Phase 1: Structure Setup (Current)
- Create module directories
- Write parent POM and module POMs
- Configure source directories to reference existing `src/`

### Phase 2: Build Verification
- Ensure all modules compile
- Verify dependency resolution
- Run existing test suite
- Compare artifacts with Ant build

### Phase 3: Source Migration
- Move source files to standard Maven structure
- Update package paths if needed
- Refactor circular dependencies

### Phase 4: Ant Deprecation
- Maven becomes primary build
- Ant marked deprecated
- CI/CD uses Maven exclusively

### Phase 5: Complete Migration
- Remove Ant build files
- Reorganize to standard Maven layout
- Publish modules to Maven Central

## Source Directory Strategy

During transition, modules use non-standard source directories:

```xml
<build>
    <sourceDirectory>../../src</sourceDirectory>
    <testSourceDirectory>../../test</testSourceDirectory>
    <resources>
        <resource>
            <directory>../../build/properties</directory>
        </resource>
    </resources>
</build>
```

After migration, move to standard:

```
module/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
│       ├── java/
│       └── resources/
└── pom.xml
```

## Versioning Strategy

### YAWL Module Versions

All YAWL modules share the same version: `5.2`

Future releases increment all modules together:
- Major version: Breaking API changes
- Minor version: New features, backward compatible
- Patch version: Bug fixes

### External Dependencies

Managed through BOM imports and version properties:
- BOM versions updated as single unit
- Individual library overrides when necessary
- Security updates applied promptly

## Distribution Artifacts

### Maven Artifacts

Published to Maven repositories:

```
org.yawlfoundation:yawl-bom:5.2 (pom)
org.yawlfoundation:yawl-common:5.2 (jar)
org.yawlfoundation:yawl-elements:5.2 (jar)
org.yawlfoundation:yawl-engine-core:5.2 (jar)
org.yawlfoundation:yawl-engine-stateless:5.2 (jar)
org.yawlfoundation:yawl-engine-api:5.2 (jar)
org.yawlfoundation:yawl-integration:5.2 (jar)
org.yawlfoundation:yawl-resourcing:5.2 (jar)
... (all service modules)
org.yawlfoundation:yawl-engine-webapp:5.2 (war)
org.yawlfoundation:yawl-resource-webapp:5.2 (war)
org.yawlfoundation:yawl-monitor-webapp:5.2 (war)
org.yawlfoundation:yawl-reporter-webapp:5.2 (war)
org.yawlfoundation:yawl-control-panel:5.2 (jar)
```

### Docker Images

```
yawlfoundation/yawl-engine:5.2
yawlfoundation/yawl-resource-service:5.2
yawlfoundation/yawl-all-in-one:5.2
```

### Distribution Packages

```
yawl-5.2-distribution.zip (full package)
yawl-5.2-standalone.jar (executable JAR)
yawl-5.2-source.zip (source distribution)
```

## Benefits of Multi-Module Architecture

### 1. Modularity
- Clear separation of concerns
- Independent module development
- Reduced coupling between components

### 2. Reusability
- External projects can depend on specific modules
- Embedded engine use cases: depend on `yawl-engine-stateless` only
- Integration projects: depend on `yawl-integration` only

### 3. Build Performance
- Parallel module builds
- Incremental compilation
- Selective module builds

### 4. Testing
- Module-specific test suites
- Integration test isolation
- Faster test feedback

### 5. Deployment Flexibility
- Deploy only needed services
- Microservice architecture support
- Independent service scaling

### 6. Dependency Management
- BOM-based version management
- Reduced version conflicts
- Easier dependency upgrades

### 7. Cloud-Native
- Lightweight deployments
- Container-optimized modules
- Kubernetes-friendly structure

## Architectural Patterns

### Generic Framework Pattern

Applied to service modules:

```
Interface Layer (yawl-engine-api)
    ↓
Abstract Base Layer (yawl-common)
    ↓
Strategy Layer (individual services)
    ↓
Configuration Layer (external config)
```

### Agent Framework Architecture

In `yawl-integration`:

```
AutonomousAgent (lifecycle)
    → DiscoveryStrategy (how to find work)
    → EligibilityReasoner (can handle work?)
    → DecisionReasoner (produce output)
    → OutputGenerator (format output)
```

### Layered Engine Architecture

```
Web Layer (yawl-*-webapp)
    ↓
API Layer (yawl-engine-api)
    ↓
Service Layer (yawl-engine-core, yawl-resourcing)
    ↓
Domain Layer (yawl-elements)
    ↓
Infrastructure Layer (yawl-common)
```

## Interface Stability Guarantees

### Stable APIs (Semantic Versioning)

**Interface A (Design-time):**
- Specification upload/download
- Process validation
- Version: Major version changes only

**Interface B (Runtime):**
- Work item lifecycle
- Case management
- Version: Major version changes only

**Interface E (Events):**
- Event logging
- Audit trail access
- Version: Major version changes only

**Interface X (Extended):**
- Exception handling
- Case suspension/cancellation
- Version: Minor version allowed

### Module APIs

Public APIs in each module are documented and versioned:
- Breaking changes: Major version increment
- New features: Minor version increment
- Bug fixes: Patch version increment

## External Consumer Usage

### Embedded Engine Example

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-bom</artifactId>
    <version>5.2</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-engine-stateless</artifactId>
</dependency>
```

### Custom Service Example

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-engine-api</artifactId>
</dependency>

<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-common</artifactId>
</dependency>
```

### Integration Example

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-integration</artifactId>
</dependency>
```

## Future Enhancements

### Potential Additional Modules

1. **yawl-engine-observability**
   - OpenTelemetry integration
   - Metrics and tracing
   - Spring Boot Actuator endpoints

2. **yawl-engine-cloud**
   - Cloud-specific adapters
   - Kubernetes operators
   - Service mesh integration

3. **yawl-specification-tools**
   - Specification validation utilities
   - Pattern detection
   - Workflow analysis

4. **yawl-client-sdk**
   - High-level client library
   - Fluent API for engine interaction
   - Language bindings (Python, JavaScript)

5. **yawl-persistence-adapters**
   - NoSQL persistence implementations
   - Event sourcing support
   - CQRS patterns

## Migration Checklist

- [ ] Create module directory structure
- [ ] Write yawl-parent POM
- [ ] Write yawl-bom POM
- [ ] Create individual module POMs
- [ ] Configure source directories
- [ ] Verify compilation
- [ ] Run test suite
- [ ] Build WARs and verify deployment
- [ ] Test Docker builds
- [ ] Update CI/CD pipelines
- [ ] Document module usage
- [ ] Publish to Maven Central (future)

## Conclusion

This multi-module Maven architecture provides:
- Clear module boundaries aligned with YAWL's logical architecture
- Proper dependency management through BOM pattern
- Support for independent module development and deployment
- Foundation for cloud-native and microservice deployments
- Backward compatibility with existing YAWL interfaces
- Smooth migration path from current monolithic build

The design preserves YAWL's proven architecture while enabling modern build practices, better modularity, and improved developer experience.

---

**Next Steps:**
1. Review and approve this design
2. Create module directory structure and POMs
3. Verify builds and tests
4. Begin phased migration from Ant to Maven
