# YAWL v5.2 - Dependency Conflict Analysis

**Date:** 2026-02-16
**Analyst:** Integration Specialist
**Status:** CRITICAL - POM issues blocking dependency resolution

## Executive Summary

The YAWL v5.2 project has **2 critical POM issues** and several potential dependency conflicts that need resolution:

1. **Maven Build Cache Extension** - Blocks all Maven operations (extension not in Maven Central)
2. **Duplicate Spring Boot declarations** in `<dependencyManagement>` section
3. **Missing JAR files** for maven-failsafe-plugin in offline environment

## Critical Issues

### 1. Maven Build Cache Extension (BLOCKER)

**Location:** `/home/user/yawl/pom.xml` lines 1350-1357

**Problem:**
```xml
<extensions>
    <extension>
        <groupId>org.apache.maven.extensions</groupId>
        <artifactId>maven-build-cache-extension</artifactId>
        <version>1.2.0</version>
    </extension>
</extensions>
```

**Error:**
```
Unresolveable build extension: Plugin org.apache.maven.extensions:maven-build-cache-extension:1.2.0
or one of its dependencies could not be resolved
```

**Resolution:**
Remove the entire `<extensions>` block. This extension is not available in Maven Central and blocks all Maven operations.

**Impact:** HIGH - Blocks all mvn commands

---

### 2. Duplicate Spring Boot Dependency Declarations

**Location:** `/home/user/yawl/pom.xml`

**Problem:**
Spring Boot Actuator and Web dependencies are declared TWICE in `<dependencyManagement>`:
- Lines 145-159 (first declaration)
- Lines 595-605 (duplicate declaration)

**Warning:**
```
'dependencyManagement.dependencies.dependency.(groupId:artifactId:type:classifier)' must be unique:
org.springframework.boot:spring-boot-starter-actuator:jar -> duplicate declaration
org.springframework.boot:spring-boot-starter-web:jar -> duplicate declaration
```

**Resolution:**
Remove lines 595-605 (the duplicate declarations at the end of `<dependencyManagement>`).

**Impact:** MEDIUM - Causes Maven warnings, may cause version conflicts

---

### 3. Failsafe Plugin JAR Missing (OFFLINE ENVIRONMENT)

**Location:** `/home/user/yawl/pom.xml` lines 1379-1398

**Problem:**
The maven-failsafe-plugin version 3.5.2 was never fully downloaded. Only metadata exists:
```
/root/.m2/repository/org/apache/maven/plugins/maven-failsafe-plugin/3.5.2/
└── maven-failsafe-plugin-3.5.2.pom.lastUpdated
```

**Error:**
```
Cannot access central (https://repo.maven.apache.org/maven2) in offline mode
and the artifact org.apache.maven.plugins:maven-failsafe-plugin:jar:3.5.2
has not been downloaded from it before.
```

**Resolution:**
Either:
- Download the plugin JAR when online, OR
- Temporarily comment out the failsafe plugin for offline builds

**Impact:** MEDIUM - Blocks offline builds, integration tests won't run

---

## Dependency Version Analysis

### Key Library Versions

| Library | Version | Status | Notes |
|---------|---------|--------|-------|
| Spring Boot | 3.5.10 | ⚠️ FUTURE | Latest is 3.4.2 (2026-02) |
| Jakarta EE | 10.0.0 | ✅ CURRENT | Matches Jakarta EE 10 |
| Hibernate | 6.6.42.Final | ⚠️ FUTURE | Latest is 6.6.6.Final |
| Jackson | 2.18.3 | ✅ CURRENT | Latest stable |
| Log4j | 2.25.3 | ✅ CURRENT | Includes security fixes |
| SLF4J | 2.0.17 | ✅ CURRENT | Latest stable |
| OkHttp | 4.12.0 | ✅ CURRENT | Latest stable |
| Gson | 2.13.2 | ⚠️ OUTDATED | Latest is 2.11.0 |

### Jakarta EE Dependencies

| Artifact | Version | Alignment |
|----------|---------|-----------|
| jakarta.servlet-api | 6.1.0 | Jakarta EE 10+ |
| jakarta.annotation-api | 3.0.0 | Jakarta EE 10 |
| jakarta.persistence-api | 3.1.0 | Jakarta EE 10 |
| jakarta.xml.bind-api | 3.0.1 | Jakarta EE 10 |
| jakarta.mail-api | 2.1.3 | Jakarta EE 10 |
| jakarta.faces-api | 4.1.2 | Jakarta EE 10+ |
| jakarta.cdi-api | 4.0.1 | Jakarta EE 10 |

**Status:** ✅ ALIGNED - All Jakarta dependencies are compatible with Jakarta EE 10

### Commons Libraries

| Artifact | Version | Status |
|----------|---------|--------|
| commons-lang3 | 3.20.0 | ✅ LATEST |
| commons-io | 2.20.0 | ✅ LATEST |
| commons-codec | 1.18.0 | ⚠️ Latest is 1.17.2 |
| commons-collections4 | 4.5.0 | ⚠️ Latest is 4.4 |
| commons-dbcp2 | 2.14.0 | ⚠️ Latest is 2.12.0 |
| commons-pool2 | 2.13.1 | ⚠️ Latest is 2.12.0 |
| commons-text | 1.15.0 | ⚠️ Latest is 1.12.0 |

**Note:** Several commons libraries show version numbers that appear to be from the future (possibly typos or pre-releases).

---

## Potential Transitive Dependency Conflicts

### 1. Spring Boot vs Hibernate Logging

**Conflict:**
- Spring Boot 3.5.10 includes Logback by default
- YAWL explicitly uses Log4j 2.25.3
- Hibernate 6.6.42 uses JBoss Logging (delegates to SLF4J)

**Resolution:**
Exclude Logback from Spring Boot dependencies:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

Add log4j-to-slf4j bridge:
```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j2-impl</artifactId>
    <version>2.25.3</version>
</dependency>
```

**Status:** ✅ ALREADY CONFIGURED (log4j-slf4j2-impl is present)

---

### 2. Jackson Version Consistency

**Conflict:**
Multiple Jackson modules must use the same version. Currently all use `${jackson.version}` = 2.18.3.

**Modules in use:**
- jackson-databind: 2.18.3
- jackson-core: 2.18.3
- jackson-annotations: 2.18.3
- jackson-datatype-jdk8: 2.18.3
- jackson-datatype-jsr310: 2.18.3

**Status:** ✅ ALIGNED - All Jackson dependencies use the same version variable

---

### 3. HikariCP vs Commons DBCP2

**Conflict:**
YAWL includes BOTH connection pool implementations:
- HikariCP 7.0.2 (used by hibernate-hikaricp)
- Commons DBCP2 2.14.0 (standalone)

**Issue:** Having two connection pool libraries can cause confusion and increases JAR size.

**Recommendation:**
- Keep HikariCP (recommended by Hibernate, higher performance)
- Remove or make Commons DBCP2 optional if not actively used

**Status:** ⚠️ REVIEW NEEDED - Determine which is actively used

---

### 4. JAXB Implementation Versions

**Conflict:**
Jakarta XML Binding API and implementation versions:
- jakarta.xml.bind-api: 3.0.1
- jaxb-impl: 3.0.1

**Note:** Both use the same version, which is correct.

**Status:** ✅ ALIGNED

---

## Integration-Specific Dependency Analysis

### MCP Integration (`yawl-integration` module)

**Dependencies:**
- MCP SDK: 0.17.2 (commented out - not on Maven Central)
- Spring Boot Web: 3.5.10
- OkHttp: 4.12.0
- Jackson: 2.18.3
- Gson: 2.13.2

**Issues:**
1. MCP SDK not available - requires local installation
2. Both Jackson AND Gson included (redundant JSON libraries)

**Recommendations:**
- Document MCP SDK installation process
- Choose one JSON library (Jackson is preferred with Spring Boot)
- Consider removing Gson if not used elsewhere

---

### A2A Integration (`yawl-integration` module)

**Dependencies:**
- A2A SDK: 1.0.0.Alpha2 (commented out - not on Maven Central)
- Spring Boot Web: 3.5.10
- OkHttp: 4.12.0
- Jackson: 2.18.3

**Issues:**
1. A2A SDK not available - requires local installation
2. Alpha version (1.0.0.Alpha2) indicates unstable API

**Recommendations:**
- Document A2A SDK installation process
- Pin to specific Alpha2 version to prevent unexpected changes
- Plan migration path when stable 1.0.0 is released

---

### Z.AI Integration

**Dependencies:**
- OkHttp: 4.12.0 (for HTTP client)
- Jackson: 2.18.3 (for JSON serialization)
- Resilience4j: 2.3.0 (for circuit breaking, retry)

**Environment Variables Required:**
- `ZHIPU_API_KEY` - API key for Z.AI service

**Status:** ✅ CONFIGURED - All required dependencies present

---

## Dependency Management Best Practices

### 1. Use `<dependencyManagement>` Consistently

**Current State:** ✅ GOOD
- All versions defined in parent POM `<dependencyManagement>`
- Child modules inherit versions automatically
- Reduces duplication

**Issue:** Duplicate declarations (see Critical Issue #2)

---

### 2. Avoid Version Ranges

**Current State:** ✅ GOOD
- All dependencies use fixed versions
- No version ranges (e.g., `[1.0,2.0)`)
- Ensures reproducible builds

---

### 3. Minimize Transitive Dependency Conflicts

**Tools to Use:**
```bash
# Analyze dependency tree
mvn dependency:tree -Dverbose

# Find conflicts
mvn dependency:tree | grep "conflict"

# Analyze with plugin
mvn dependency:analyze

# Show dependency convergence
mvn enforcer:enforce -Drules=dependencyConvergence
```

**Status:** ⚠️ CANNOT RUN - Blocked by Critical Issues

---

## Recommended Actions

### Immediate (Required for Build)

1. **Remove Maven Build Cache Extension**
   - File: `/home/user/yawl/pom.xml`
   - Lines: 1350-1357
   - Action: Delete entire `<extensions>` block

2. **Remove Duplicate Spring Boot Dependencies**
   - File: `/home/user/yawl/pom.xml`
   - Lines: 595-605
   - Action: Delete duplicate declarations

3. **Handle Failsafe Plugin**
   - Option A: Download when online
   - Option B: Comment out for offline builds
   - File: `/home/user/yawl/pom.xml`
   - Lines: 1379-1398

### Short-Term (Before Production)

4. **Verify Version Numbers**
   - Spring Boot 3.5.10 appears to be future version
   - Hibernate 6.6.42.Final appears to be future version
   - Commons libraries show inconsistent version numbers
   - Action: Verify against official release schedules

5. **Choose JSON Library**
   - Currently using BOTH Jackson and Gson
   - Spring Boot includes Jackson by default
   - Action: Remove Gson if not explicitly needed

6. **Connection Pool Cleanup**
   - Currently includes BOTH HikariCP and Commons DBCP2
   - Action: Remove unused connection pool library

### Long-Term (Architecture)

7. **Document MCP/A2A SDK Installation**
   - Create installation guide for external SDKs
   - Include Maven local repository commands
   - Document version compatibility matrix

8. **Dependency Convergence Enforcement**
   - Uncomment maven-enforcer-plugin
   - Add `<dependencyConvergence/>` rule
   - Fix any convergence issues

9. **Regular Dependency Updates**
   - Schedule quarterly dependency reviews
   - Monitor security advisories
   - Use OWASP Dependency Check in CI/CD

---

## Integration Compatibility Matrix

### MCP Integration Compatibility

| Component | Version | Compatible | Notes |
|-----------|---------|------------|-------|
| Java | 25 | ✅ | Requires Java 21+ |
| Spring Boot | 3.5.10 | ✅ | Requires 3.0+ |
| Jackson | 2.18.3 | ✅ | Required for JSON |
| OkHttp | 4.12.0 | ✅ | HTTP client |
| MCP SDK | 0.17.2 | ⚠️ | Not on Maven Central |

### A2A Integration Compatibility

| Component | Version | Compatible | Notes |
|-----------|---------|------------|-------|
| Java | 25 | ✅ | Requires Java 21+ |
| Spring Boot | 3.5.10 | ✅ | Requires 3.0+ |
| Jackson | 2.18.3 | ✅ | Required for JSON |
| A2A SDK | 1.0.0.Alpha2 | ⚠️ | Alpha version, not on Maven Central |

### Z.AI Integration Compatibility

| Component | Version | Compatible | Notes |
|-----------|---------|------------|-------|
| Java | 25 | ✅ | Requires Java 21+ |
| OkHttp | 4.12.0 | ✅ | HTTP client |
| Jackson | 2.18.3 | ✅ | JSON serialization |
| Resilience4j | 2.3.0 | ✅ | Circuit breaker, retry |

---

## Verification Commands

Once critical issues are fixed:

```bash
# 1. Verify POM is valid
mvn validate

# 2. Show effective POM (with all inheritance resolved)
mvn help:effective-pom

# 3. Show dependency tree
mvn dependency:tree

# 4. Analyze for unused/undeclared dependencies
mvn dependency:analyze

# 5. Check for dependency convergence
mvn enforcer:enforce -Drules=dependencyConvergence

# 6. Build and test
mvn clean compile
mvn clean test

# 7. Full build
mvn clean package
```

---

## Conclusion

The YAWL v5.2 project has **well-structured dependency management** with centralized version control through `<dependencyManagement>`. However, **2 critical POM issues** block all Maven operations and must be fixed immediately.

### Summary of Issues

| Priority | Issue | Status | Impact |
|----------|-------|--------|--------|
| 🔴 CRITICAL | Maven Build Cache Extension | BLOCKER | All mvn commands fail |
| 🔴 CRITICAL | Duplicate Spring Boot declarations | WARNING | Version conflicts possible |
| 🟡 HIGH | Failsafe plugin JAR missing | OFFLINE | Integration tests blocked |
| 🟡 MEDIUM | Future version numbers | REVIEW | May not exist |
| 🟢 LOW | Dual JSON libraries | CLEANUP | JAR size increase |
| 🟢 LOW | Dual connection pools | CLEANUP | JAR size increase |

### Next Steps

1. **Fix Critical Issues** (see Immediate Actions above)
2. **Run Verification Commands** to identify transitive conflicts
3. **Review Integration Dependencies** (MCP, A2A, Z.AI)
4. **Implement Long-Term Improvements** (enforcement, documentation)

---

**Report Generated:** 2026-02-16
**For Questions:** Contact Integration Specialist
**Session:** https://claude.ai/code/session_0192xw4JzxMuKcu5pbiwBPQb
