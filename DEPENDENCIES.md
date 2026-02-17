# YAWL v6.0.0-Alpha - Dependency Catalog

This document describes every major dependency group used by YAWL, the rationale for
its inclusion, and the version strategy applied.

All version numbers are managed centrally in `/pom.xml` under `<properties>`. Child
modules declare dependencies **without version numbers** and inherit them from there.

---

## Dependency Management Strategy

### Bill of Materials (BOM) Imports

Six upstream BOMs are imported when the `online` Maven profile is active
(`mvn -P online ...`). Each BOM manages a family of co-released artifacts so that
their internal versions are always mutually compatible:

| BOM Artifact | Version Property | What It Manages |
|---|---|---|
| `org.springframework.boot:spring-boot-dependencies` | `spring-boot.version` | All `spring-*` artifacts plus many transitive dependencies (Jackson, SLF4J, etc.) |
| `io.opentelemetry:opentelemetry-bom` | `opentelemetry.version` | All `opentelemetry-*` SDK artifacts |
| `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha` | `opentelemetry-instrumentation.version` | OTel auto-instrumentation agents and annotations |
| `io.github.resilience4j:resilience4j-bom` | `resilience4j.version` | All `resilience4j-*` modules |
| `org.testcontainers:testcontainers-bom` | `testcontainers.version` | All `testcontainers-*` database and service modules |
| `com.fasterxml.jackson:jackson-bom` | `jackson.version` | All `com.fasterxml.jackson.*` modules |

When the `online` profile is **not** active (e.g., in air-gapped CI or offline
development), explicit version pins in `<dependencyManagement>` provide the same
guarantees. Both paths produce identical resolved versions.

---

## Dependency Groups

### Jakarta EE APIs

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `jakarta.servlet:jakarta.servlet-api` | `jakarta.servlet.version` | provided | HTTP request/response handling in servlet containers (Tomcat, Catalina) |
| `jakarta.annotation:jakarta.annotation-api` | `jakarta.annotation.version` | compile | `@PostConstruct`, `@PreDestroy`, `@Resource` annotations |
| `jakarta.persistence:jakarta.persistence-api` | `jakarta.persistence.version` | compile | JPA annotations (`@Entity`, `@Table`, `@Column`) used with Hibernate |
| `jakarta.xml.bind:jakarta.xml.bind-api` | `jakarta.xml.bind.version` | compile | JAXB API for XML-to-Java marshalling |
| `com.sun.xml.bind:jaxb-impl` | `jakarta.xml.bind.version` | runtime | JAXB reference implementation |
| `jakarta.mail:jakarta.mail-api` | `jakarta.mail.version` | compile | Email sending from workflow notifications |
| `org.eclipse.angus:angus-mail` | `angus.mail.version` | runtime | Eclipse Angus (Jakarta Mail implementation replacing JavaMail) |
| `jakarta.activation:jakarta.activation-api` | `jakarta.activation.version` | compile | Data type handlers required by Jakarta Mail |
| `org.eclipse.angus:angus-activation` | `angus.activation.version` | runtime | Angus Activation implementation |
| `jakarta.faces:jakarta.faces-api` | `jakarta.faces.version` | provided | JSF API for web-form based YAWL UIs |
| `org.glassfish:jakarta.faces` | `jakarta.faces.version` | runtime | Mojarra JSF implementation |
| `jakarta.enterprise:jakarta.enterprise.cdi-api` | `jakarta.cdi.version` | compile | CDI dependency injection API |
| `jakarta.ws.rs:jakarta.ws.rs-api` | `jakarta.ws.rs.version` | provided | JAX-RS REST API (implemented by Jersey) |

**Why Jakarta EE and not Spring alone?** YAWL's web layer predates Spring Boot and uses
servlet-based JSF pages alongside REST endpoints. Migration to pure Spring MVC would
require replacing significant UI infrastructure, so both Jakarta EE and Spring coexist.

---

### ORM - Hibernate

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `org.hibernate.orm:hibernate-core` | `hibernate.version` | compile | JPA implementation; maps `YWorkItem`, `YCase`, `YSpecification` to RDBMS |
| `org.hibernate.orm:hibernate-hikaricp` | `hibernate.version` | runtime | Integrates Hibernate session factory with HikariCP connection pool |
| `org.hibernate.orm:hibernate-jcache` | `hibernate.version` | runtime | Second-level cache via JCache (used in high-throughput deployments) |

YAWL stores workflow state (running cases, work items, audit logs) in a relational
database. Hibernate provides the ORM layer, allowing YAWL to support H2 (tests),
PostgreSQL, MySQL, and Derby in production without code changes.

---

### Apache Commons

| Artifact | Version Property | Purpose |
|---|---|---|
| `org.apache.commons:commons-lang3` | `commons.lang3.version` | String manipulation, reflection, date utilities (`StringUtils`, `ReflectionUtils`) |
| `commons-io:commons-io` | `commons.io.version` | File I/O, stream utilities (legacy groupId - many transitive deps still use it) |
| `org.apache.commons:commons-io` | `commons.io.version` | Same library, modern groupId |
| `commons-codec:commons-codec` | `commons.codec.version` | Base64, Hex, MD5 encoding for YAWL specification checksums |
| `org.apache.commons:commons-vfs2` | `commons.vfs2.version` | Virtual file system - accesses workflow specs from local, FTP, SFTP, HTTP sources |
| `org.apache.commons:commons-collections4` | `commons.collections4.version` | Enhanced collections (`MultiValuedMap`, `BidiMap`) used in workflow routing |
| `org.apache.commons:commons-dbcp2` | `commons.dbcp2.version` | Legacy JDBC connection pooling (used by Hibernate in some configurations) |
| `commons-fileupload:commons-fileupload` | `commons.fileupload.version` | Multipart file upload handling for specification imports |
| `org.apache.commons:commons-pool2` | `commons.pool2.version` | Object pooling, required transitively by commons-dbcp2 |
| `org.apache.commons:commons-text` | `commons.text.version` | String similarity, interpolation - used in process mining report generation |
| `commons-beanutils:commons-beanutils` | `commons.beanutils.version` | JavaBean property access via reflection |

---

### Database Drivers

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `com.h2database:h2` | `h2.version` | test | In-memory database for unit and integration tests (no external DB required) |
| `org.postgresql:postgresql` | `postgresql.version` | runtime | PostgreSQL JDBC driver (recommended production database) |
| `com.mysql:mysql-connector-j` | `mysql.version` | runtime | MySQL/MariaDB JDBC driver |
| `org.apache.derby:derbyclient` | `derby.version` | runtime | Apache Derby embedded/network client (legacy installations) |
| `org.hsqldb:hsqldb` | `hsqldb.version` | runtime | HyperSQL (legacy lightweight alternative to H2) |
| `com.zaxxer:HikariCP` | `hikaricp.version` | compile | HikariCP connection pool - lowest latency JDBC pool for high-frequency work item operations |

---

### Logging

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `org.apache.logging.log4j:log4j-api` | `log4j.version` | compile | Log4j2 API - logging facade used in all production code |
| `org.apache.logging.log4j:log4j-core` | `log4j.version` | runtime | Log4j2 implementation (only declared in application entry-point modules) |
| `org.apache.logging.log4j:log4j-slf4j2-impl` | `log4j.version` | runtime | Routes SLF4J calls to Log4j2 |
| `org.slf4j:slf4j-api` | `slf4j.version` | compile | SLF4J API - used by all third-party libraries; provides a unified logging interface |
| `org.jboss.logging:jboss-logging` | `jboss.logging.version` | runtime | JBoss Logging (required by Hibernate) |

**Strategy**: All library code uses SLF4J API (`LoggerFactory`). The application entry
point binds SLF4J to Log4j2. This pattern ensures no logging implementation leaks
into libraries.

**Important**: Do NOT add both `log4j-to-slf4j` and `log4j-slf4j2-impl` to the same
classpath. Spring Boot pulls in `log4j-to-slf4j` transitively; modules using
Log4j2-native must exclude it with:
```xml
<exclusion>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-to-slf4j</artifactId>
</exclusion>
```

---

### JSON and Serialisation

| Artifact | Version Property | Purpose |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` | `jackson.version` | JSON serialisation/deserialisation for REST APIs and MCP/A2A payloads |
| `com.fasterxml.jackson.core:jackson-core` | `jackson.version` | Low-level Jackson streaming API |
| `com.fasterxml.jackson.core:jackson-annotations` | `jackson.version` | `@JsonProperty`, `@JsonIgnore`, etc. |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | `jackson.version` | Support for `Optional`, `OptionalInt`, etc. |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `jackson.version` | Support for `java.time.*` (LocalDate, Instant, etc.) |
| `com.google.code.gson:gson` | `gson.version` | GSON is used in legacy YAWL services that predate the Jackson migration; retained for backwards compatibility |

---

### XML Processing

| Artifact | Version Property | Purpose |
|---|---|---|
| `org.jdom:jdom2` | `jdom.version` | YAWL's primary XML library; used throughout the engine to parse and generate YAWL specification XML documents |
| `jaxen:jaxen` | `jaxen.version` | XPath evaluation engine for JDOM2 (required for data mapping in workflow tasks) |
| `net.sf.saxon:Saxon-HE` | `saxon.version` | XSLT 2.0/3.0 and XQuery processor used in YAWL's data transformation layer |
| `javax.xml.soap:javax.xml.soap-api` | `saaj.api.version` | SOAP API (legacy javax namespace, retained for backwards compatibility with existing service integrations) |
| `com.sun.xml.messaging.saaj:saaj-impl` | `saaj.impl.version` | SAAJ implementation for SOAP message processing |

JDOM2 and Saxon are central to YAWL's architecture: the YAWL Specification Format is
XML-based, and task data mappings use XPath/XSLT to transform data between tasks.

---

### HTTP Clients

| Artifact | Version Property | Purpose |
|---|---|---|
| `com.squareup.okhttp3:okhttp` | `okhttp.version` | HTTP client for Z.AI API calls (`ZaiService`), MCP server communication, and A2A protocol messages. OkHttp5 supports HTTP/2 and provides a clean async API |

The JDK 21 `java.net.http.HttpClient` is used for simpler fire-and-forget requests
within the engine. OkHttp is used where connection pooling, interceptors, and
sophisticated retry/timeout handling are needed.

---

### MCP and A2A Integration SDKs

| Artifact | Version Property | Purpose |
|---|---|---|
| `io.modelcontextprotocol.sdk:mcp` | `mcp.version` | Model Context Protocol Java SDK - core server and client |
| `io.modelcontextprotocol.sdk:mcp-core` | `mcp.version` | MCP protocol types and message formats |
| `io.modelcontextprotocol.sdk:mcp-json` | `mcp.version` | MCP JSON serialisation |
| `io.modelcontextprotocol.sdk:mcp-json-jackson2` | `mcp.version` | Jackson-backed JSON codec for MCP |
| `io.anthropic:a2a-java-sdk-spec` | `a2a.version` | Agent-to-Agent protocol specification types |
| `io.anthropic:a2a-java-sdk-common` | `a2a.version` | A2A common utilities |
| `io.anthropic:a2a-java-sdk-server-common` | `a2a.version` | A2A server-side abstractions |
| `io.anthropic:a2a-java-sdk-transport-rest` | `a2a.version` | A2A REST transport layer |
| `io.anthropic:a2a-java-sdk-http-client` | `a2a.version` | A2A HTTP client for inter-agent communication |

These SDKs are **not available on Maven Central** and must be installed into the local
Maven repository from local JARs:
```bash
mvn install:install-file -Dfile=mcp-0.17.2.jar \
    -DgroupId=io.modelcontextprotocol.sdk -DartifactId=mcp -Dversion=0.17.2 -Dpackaging=jar
```
See `docs/DEVELOPER_DOCUMENTATION_SUMMARY.md` for full installation instructions.

---

### Security and Authentication

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `io.jsonwebtoken:jjwt-api` | `jjwt.version` | compile | JWT creation and validation API (YAWL REST API authentication) |
| `io.jsonwebtoken:jjwt-impl` | `jjwt.version` | runtime | JJWT implementation (deliberately runtime-only to prevent API coupling) |
| `io.jsonwebtoken:jjwt-jackson` | `jjwt.version` | runtime | Jackson-backed JWT JSON processing |
| `com.microsoft.graph:microsoft-graph` | `microsoft.graph.version` | compile | Microsoft Graph SDK for Outlook/Teams integration in `yawl-integration` |
| `com.azure:azure-identity` | `azure.identity.version` | compile | Azure AD authentication for Microsoft Graph |

---

### REST Framework - Jersey

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `org.glassfish.jersey.core:jersey-server` | `jersey.version` | compile | JAX-RS server implementation for YAWL REST endpoints |
| `org.glassfish.jersey.core:jersey-client` | `jersey.version` | compile | Jersey client for inter-service REST calls (used in `InterfaceB_EnvironmentBasedClient`) |
| `org.glassfish.jersey.containers:jersey-container-servlet` | `jersey.version` | compile | Jersey servlet integration |
| `org.glassfish.jersey.inject:jersey-hk2` | `jersey.version` | runtime | HK2 dependency injection for Jersey |

---

### Observability

| Artifact | Version Property | Purpose |
|---|---|---|
| `io.opentelemetry:opentelemetry-api` | managed by `opentelemetry-bom` | OTel API for traces, metrics, and logs in workflow operations |
| `io.opentelemetry:opentelemetry-sdk` | managed by `opentelemetry-bom` | OTel SDK implementation |
| `io.opentelemetry:opentelemetry-exporter-otlp` | managed by `opentelemetry-bom` | OTLP exporter for sending telemetry to collectors (Jaeger, Grafana Tempo) |
| `io.opentelemetry:opentelemetry-exporter-prometheus` | `opentelemetry.version`-alpha | Prometheus metrics scraping endpoint |
| `io.opentelemetry.semconv:opentelemetry-semconv` | `opentelemetry.semconv.version` | Stable semantic convention constants |
| `io.opentelemetry.semconv:opentelemetry-semconv-incubating` | `opentelemetry.semconv.version`-alpha | Incubating semantic conventions |
| `io.micrometer:micrometer-core` | `micrometer.version` | Application metrics API (JVM, thread pool, custom counters) |
| `io.micrometer:micrometer-registry-prometheus` | `micrometer.version` | Exports Micrometer metrics in Prometheus format |

---

### Graph Algorithms - JUNG

| Artifact | Version Property | Purpose |
|---|---|---|
| `net.sf.jung:jung-api` | `jung.version` | Graph abstraction API |
| `net.sf.jung:jung-graph-impl` | `jung.version` | Graph data structures |
| `net.sf.jung:jung-algorithms` | `jung.version` | Graph traversal and analysis algorithms (soundness verification of Petri net-based workflow definitions) |

JUNG is used in the YAWL validator to check structural properties of workflow nets,
such as reachability and liveness.

---

### Resilience4j

| Artifact | Managed by | Purpose |
|---|---|---|
| `io.github.resilience4j:resilience4j-circuitbreaker` | `resilience4j-bom` | Circuit breaker for Z.AI API calls - prevents cascade failures when the AI service is unavailable |
| `io.github.resilience4j:resilience4j-retry` | `resilience4j-bom` | Exponential backoff retry for transient failures in MCP and A2A communications |
| `io.github.resilience4j:resilience4j-ratelimiter` | `resilience4j-bom` | Rate limiting on outbound Z.AI API calls to stay within quota |

---

### Support Libraries

| Artifact | Version Property | Purpose |
|---|---|---|
| `org.jspecify:jspecify` | `jspecify.version` | `@Nullable` / `@NonNull` annotations for static null-safety analysis (no runtime overhead) |
| `io.smallrye:jandex` | `jandex.version` | Fast annotation index scanning (required by Hibernate for entity discovery) |
| `net.bytebuddy:byte-buddy` | `byte.buddy.version` | Bytecode manipulation (required by Hibernate for proxy generation) |
| `com.fasterxml:classmate` | `classmate.version` | Generic type resolution (required by Hibernate) |
| `com.sun.istack:istack-commons-runtime` | `istack.version` | Runtime support for istack (required by JAXB/SAAJ) |
| `org.simplejavamail:simple-java-mail` | `simple-java-mail.version` | Simplified email API for workflow notification delivery |
| `org.apache.ant:ant` | `ant.version` | ANT task support for legacy YAWL build utilities and specification packaging |

---

### Testing

| Artifact | Version Property | Scope | Purpose |
|---|---|---|---|
| `junit:junit` | `junit.version` | test | JUnit 4 (legacy tests throughout the codebase; still the majority) |
| `org.junit.jupiter:junit-jupiter-api` | `junit.jupiter.version` | test | JUnit 5 API for new tests |
| `org.junit.jupiter:junit-jupiter-engine` | `junit.jupiter.version` | test | JUnit 5 execution engine |
| `org.junit.jupiter:junit-jupiter-params` | `junit.jupiter.version` | test | Parameterized test support |
| `org.junit.platform:junit-platform-suite-api` | `junit.platform.version` | test | JUnit Platform test suite API |
| `org.hamcrest:hamcrest-core` | `hamcrest.version` | test | Hamcrest matchers for expressive assertions |
| `xmlunit:xmlunit` | `xmlunit.version` | test | XML document comparison for specification round-trip tests |
| `com.h2database:h2` | `h2.version` | test | In-memory database for persistence tests without external DB infrastructure |
| `org.testcontainers:testcontainers` | managed by `testcontainers-bom` | test | Spin up real databases (PostgreSQL, MySQL) in Docker for integration tests |
| `org.openjdk.jmh:jmh-core` | `jmh.version` | test | JMH microbenchmarks for engine throughput testing |
| `org.openjdk.jmh:jmh-generator-annprocess` | `jmh.version` | test | JMH annotation processor |

---

## Build Plugins

| Plugin | Version Property | Purpose |
|---|---|---|
| `maven-compiler-plugin` | `maven.compiler.plugin.version` | Compiles Java 21 source with `--enable-preview` |
| `maven-surefire-plugin` | `maven.surefire.plugin.version` | Runs unit tests in parallel (4 threads, by class) |
| `maven-failsafe-plugin` | `maven.failsafe.plugin.version` | Runs integration tests (`*IT.java`) after package phase |
| `maven-jar-plugin` | `maven.jar.plugin.version` | JAR packaging with manifest entries |
| `maven-war-plugin` | `maven.war.plugin.version` | WAR packaging for web application modules |
| `maven-clean-plugin` | `maven.clean.plugin.version` | Clean build outputs |
| `maven-enforcer-plugin` | `maven.enforcer.plugin.version` | Enforces build environment constraints (Java 21+, Maven 3.9+, no duplicate deps). Active in `ci` and `prod` profiles |
| `versions-maven-plugin` | `versions.maven.plugin.version` | Dependency and plugin upgrade analysis. Usage: `mvn versions:display-dependency-updates` |
| `jacoco-maven-plugin` | `jacoco.plugin.version` | Code coverage. Active in `ci` and `prod` profiles (`-Djacoco.skip=false` to enable manually) |
| `spotbugs-maven-plugin` | `spotbugs.plugin.version` | Static bytecode analysis for bug patterns. Active in `ci` and `prod` profiles |
| `maven-checkstyle-plugin` | `checkstyle.plugin.version` | Code style enforcement. Active in `analysis` profile |
| `maven-pmd-plugin` | `pmd.plugin.version` | PMD static analysis and duplicate code detection. Active in `analysis` profile |
| `dependency-check-maven` | `owasp.dependency.check.version` | OWASP NVD CVE scanning. Active in `security-audit` and `prod` profiles. Requires `NVD_API_KEY` env var in `prod` mode |
| `sonar-maven-plugin` | `sonar.plugin.version` | SonarQube/SonarCloud integration. Active in `sonar` profile |

---

## Version Pinning Policy

1. **All versions are properties** in the parent POM `<properties>` section.
   No child module declares a `<version>` element for managed dependencies.

2. **BOM-managed artifacts do not need version entries** when the `online` profile
   is active. Explicit pins in `<dependencyManagement>` provide offline fallback.

3. **Test dependencies are always `<scope>test`** in `<dependencyManagement>`. Child
   modules do not override this scope.

4. **No SNAPSHOT versions** in production code. The `versions-maven-plugin` is
   configured to exclude snapshots from upgrade suggestions.

5. **Security-sensitive libraries** (log4j, jackson, commons-codec, commons-fileupload)
   must be kept at the latest patch release. Run `mvn -P security-audit
   dependency-check:aggregate` to audit current CVE exposure.

---

## How to Check for Updates

```bash
# Show outdated dependencies (stable releases only)
mvn versions:display-dependency-updates

# Show outdated plugins
mvn versions:display-plugin-updates

# Run OWASP CVE scan (requires network + NVD API key)
export NVD_API_KEY=your-api-key-here
mvn -P security-audit dependency-check:aggregate

# Show full dependency tree for a specific module
mvn dependency:tree -pl yawl-integration

# Analyze unused and undeclared dependencies
mvn dependency:analyze -pl yawl-engine
```
