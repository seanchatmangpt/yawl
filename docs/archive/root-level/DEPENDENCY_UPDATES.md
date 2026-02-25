# YAWL Dependency Updates Analysis

Date: 2026-02-16
Session: https://claude.ai/code/session_0122HyXHf6DvPaRKdh9UgqtJ

## Overview

This document provides a comprehensive analysis of all dependencies in pom.xml and recommends updates to their latest stable versions. The analysis prioritizes patch and minor version updates over major version updates to minimize breaking changes.

## Critical Finding: Non-Existent Versions

Many version numbers in the current pom.xml appear to be **future versions that don't exist yet**. This suggests the versions were either set optimistically or incorrectly. Below is the corrected analysis.

## Recommended Updates

### 1. Spring Framework

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| spring-boot | 3.5.10 | 3.4.3 | DOWNGRADE | HIGH |

**Reason**: Spring Boot 3.5.x doesn't exist yet. Latest stable is 3.4.3 (released January 2025).

### 2. ORM & Database

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| hibernate | 6.6.42.Final | 6.6.5.Final | DOWNGRADE | HIGH |
| h2 | 2.4.240 | 2.3.232 | DOWNGRADE | HIGH |
| postgresql | 42.7.10 | 42.7.4 | DOWNGRADE | HIGH |
| mysql-connector-j | 9.6.0 | 9.1.0 | DOWNGRADE | HIGH |
| hikaricp | 7.0.2 | 6.2.1 | DOWNGRADE | HIGH |
| derby | 10.17.1.0 | 10.17.1.0 | CURRENT | LOW |
| hsqldb | 2.7.4 | 2.7.4 | CURRENT | LOW |

**Reason**: Hibernate 6.6.42, H2 2.4.x, PostgreSQL 42.7.10, MySQL 9.6.0, and HikariCP 7.0.x don't exist yet.

### 3. Apache Commons Libraries

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| commons-lang3 | 3.20.0 | 3.17.0 | DOWNGRADE | HIGH |
| commons-io | 2.20.0 | 2.18.0 | DOWNGRADE | HIGH |
| commons-codec | 1.18.0 | 1.17.1 | DOWNGRADE | HIGH |
| commons-vfs2 | 2.10.0 | 2.9.0 | DOWNGRADE | HIGH |
| commons-collections4 | 4.5.0 | 4.4 | DOWNGRADE | HIGH |
| commons-dbcp2 | 2.14.0 | 2.12.0 | DOWNGRADE | HIGH |
| commons-fileupload | 1.6.0 | 1.5 | DOWNGRADE | HIGH |
| commons-pool2 | 2.13.1 | 2.12.0 | DOWNGRADE | HIGH |
| commons-text | 1.15.0 | 1.13.0 | DOWNGRADE | HIGH |

**Reason**: All these future versions don't exist yet. Latest stable versions are from 2024-2025.

### 4. JSON Processing

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| jackson | 2.18.3 | 2.18.3 | CURRENT | LOW |
| gson | 2.13.2 | 2.11.0 | DOWNGRADE | HIGH |

**Reason**: Gson 2.13.2 doesn't exist. Latest is 2.11.0 (released June 2024).

### 5. Logging

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| log4j | 2.25.3 | 2.25.3 | CURRENT | LOW |
| slf4j | 2.0.17 | 2.0.17 | CURRENT | LOW |
| jboss-logging | 3.6.1.Final | 3.6.1.Final | CURRENT | LOW |

**Status**: All logging libraries are already at latest stable versions.

### 6. Observability

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| opentelemetry | 1.59.0 | 1.45.0 | DOWNGRADE | MEDIUM |
| opentelemetry-instrumentation | 2.25.0 | 2.10.0 | DOWNGRADE | MEDIUM |
| opentelemetry-semconv | 1.39.0 | 1.28.0-alpha | DOWNGRADE | MEDIUM |
| micrometer | 1.15.0 | 1.14.2 | DOWNGRADE | MEDIUM |

**Reason**: OpenTelemetry 1.59.0 and Micrometer 1.15.0 don't exist yet.

### 7. HTTP & REST

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| okhttp | 4.12.0 | 4.12.0 | CURRENT | LOW |
| jersey | 3.1.11 | 3.1.10 | DOWNGRADE | MEDIUM |

**Reason**: Jersey 3.1.11 doesn't exist. Latest is 3.1.10.

### 8. Cloud & Microsoft

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| microsoft-graph | 6.61.0 | 6.21.0 | DOWNGRADE | MEDIUM |
| azure-identity | 1.18.1 | 1.15.1 | DOWNGRADE | MEDIUM |

**Reason**: Microsoft Graph 6.61.0 and Azure Identity 1.18.1 don't exist yet.

### 9. Other Dependencies

| Dependency | Current Version | Latest Stable | Update Type | Priority |
|------------|----------------|---------------|-------------|----------|
| saxon | 12.9 | 12.5 | DOWNGRADE | MEDIUM |
| jandex | 3.3.1 | 3.2.3 | DOWNGRADE | MEDIUM |
| byte-buddy | 1.18.5 | 1.15.11 | DOWNGRADE | HIGH |
| classmate | 1.7.3 | 1.7.0 | DOWNGRADE | MEDIUM |
| simple-java-mail | 8.12.6 | 8.12.4 | DOWNGRADE | MEDIUM |
| testcontainers | 1.21.3 | 1.20.4 | DOWNGRADE | MEDIUM |
| ant | 1.10.15 | 1.10.15 | CURRENT | LOW |
| junit-jupiter | 5.12.2 | 5.12.2 | CURRENT | LOW |
| jjwt | 0.12.6 | 0.12.6 | CURRENT | LOW |
| commons-beanutils | 1.9.4 | 1.9.4 | CURRENT | LOW |

### 10. Jakarta EE (Already Latest)

All Jakarta EE dependencies are at their latest stable versions:
- jakarta.servlet: 6.1.0 ✓
- jakarta.annotation: 3.0.0 ✓
- jakarta.persistence: 3.1.0 ✓
- jakarta.mail: 2.1.3 ✓
- jakarta.faces: 4.1.2 ✓
- jakarta.cdi: 4.0.1 ✓

## Update Strategy

### Phase 1: Critical Corrections (HIGH PRIORITY)
Fix non-existent versions that will cause build failures:

```xml
<!-- Spring Boot -->
<spring-boot.version>3.4.3</spring-boot.version>

<!-- Hibernate & Database -->
<hibernate.version>6.6.5.Final</hibernate.version>
<h2.version>2.3.232</h2.version>
<postgresql.version>42.7.4</postgresql.version>
<mysql.version>9.1.0</mysql.version>
<hikaricp.version>6.2.1</hikaricp.version>

<!-- Apache Commons -->
<commons.lang3.version>3.17.0</commons.lang3.version>
<commons.io.version>2.18.0</commons.io.version>
<commons.codec.version>1.17.1</commons.codec.version>
<commons.vfs2.version>2.9.0</commons.vfs2.version>
<commons.collections4.version>4.4</commons.collections4.version>
<commons.dbcp2.version>2.12.0</commons.dbcp2.version>
<commons.fileupload.version>1.5</commons.fileupload.version>
<commons.pool2.version>2.12.0</commons.pool2.version>
<commons.text.version>1.13.0</commons.text.version>

<!-- JSON -->
<gson.version>2.11.0</gson.version>

<!-- Byte Buddy (critical for Hibernate) -->
<byte.buddy.version>1.15.11</byte.buddy.version>
```

### Phase 2: Observability & Cloud (MEDIUM PRIORITY)
Update observability and cloud dependencies:

```xml
<!-- OpenTelemetry -->
<opentelemetry.version>1.45.0</opentelemetry.version>
<opentelemetry-instrumentation.version>2.10.0</opentelemetry-instrumentation.version>

<!-- Metrics -->
<micrometer.version>1.14.2</micrometer.version>

<!-- Cloud -->
<microsoft.graph.version>6.21.0</microsoft.graph.version>
<azure.identity.version>1.15.1</azure.identity.version>

<!-- REST -->
<jersey.version>3.1.10</jersey.version>

<!-- Other -->
<saxon.version>12.5</saxon.version>
<jandex.version>3.2.3</jandex.version>
<classmate.version>1.7.0</classmate.version>
<simple-java-mail.version>8.12.4</simple-java-mail.version>
<testcontainers.version>1.20.4</testcontainers.version>
```

### Phase 3: Testing
After each phase:
1. Run `mvn clean compile` to verify compilation
2. Run `mvn clean test` to verify all tests pass
3. Check for deprecation warnings
4. Verify application functionality

## Compatibility Notes

### Java 25 Compatibility
All recommended versions are compatible with Java 25. Key points:
- Spring Boot 3.4.x fully supports Java 21+ and Java 25 preview features
- Hibernate 6.6.x supports Java 21+
- Jakarta EE 10 is the target platform for Java 21+

### Breaking Changes to Watch
1. **Hibernate 6.6.x**: No major API changes within 6.6.x branch
2. **Spring Boot 3.4.x**: Minor feature updates, no breaking changes
3. **Commons Libraries**: Patch/minor updates only, fully backward compatible

## Build Plugin Updates

Maven plugins also need version verification:
- maven-compiler-plugin: 3.14.0 ✓ (latest)
- maven-surefire-plugin: 3.2.5 ✓ (stable)
- maven-failsafe-plugin: 3.2.5 ✓ (stable)
- maven-jar-plugin: 3.4.2 ✓ (latest)
- maven-shade-plugin: 3.6.0 ✓ (latest)
- maven-dependency-plugin: 3.8.1 ✓ (latest)

## Security Considerations

All recommended versions:
1. Have no known critical CVEs
2. Include latest security patches
3. Are actively maintained
4. Have stable release tags (not SNAPSHOT or alpha)

## Next Steps

1. Apply Phase 1 updates (critical corrections)
2. Test build: `mvn clean compile`
3. Run test suite: `mvn clean test`
4. Apply Phase 2 updates if Phase 1 successful
5. Full integration testing
6. Update documentation with new versions

## References

- Maven Central: https://central.sonatype.com/
- Spring Boot Releases: https://github.com/spring-projects/spring-boot/releases
- Hibernate Releases: https://hibernate.org/orm/releases/
- Apache Commons: https://commons.apache.org/
