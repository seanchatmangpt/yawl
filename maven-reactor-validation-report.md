# YAWL v6.0.0-GA Maven Reactor Validation Report

Generated on: 2026-02-26
Validation Status: FAILED (Multiple Issues Found)

---

## 1. Modules Section Analysis

### Declared Modules (13)
The parent pom.xml declares the following modules:
- yawl-utilities
- yawl-elements
- yawl-authentication
- yawl-engine
- yawl-stateless
- yawl-resourcing
- yawl-worklet
- yawl-scheduling
- yawl-integration
- yawl-monitoring
- yawl-webapps
- yawl-control-panel
- yawl-benchmark

### Actual Modules (20)
Found 20 directories with "yawl-" prefix in the root directory:
- yawl-authentication ✓
- yawl-benchmark ✓
- yawl-control-panel ✓
- yawl-elements ✓
- yawl-engine ✓
- yawl-ggen ❌
- yawl-graalpy ❌
- yawl-gregverse ❌
- yawl-hooks ❌
- yawl-integration ✓
- yawl-mcp-a2a-app ❌
- yawl-monitoring ✓
- yawl-pi ❌
- yawl-resourcing ✓
- yawl-scheduling ✓
- yawl-security ❌
- yawl-stateless ✓
- yawl-utilities ✓
- yawl-webapps ✓
- yawl-worklet ✓

### Missing Modules (7)
The following modules exist in the filesystem but are **NOT** declared in the parent pom.xml:
1. **yawl-ggen** - Workflow generation module
2. **yawl-graalpy** - GraalPy integration module
3. **yawl-gregverse** - GregVerse integration module
4. **yawl-hooks** - Hook implementation module
5. **yawl-mcp-a2a-app** - MCP/A2A application module
6. **yawl-pi** - Process integration module
7. **yawl-security** - Security enhancement module

---

## 2. Dependency Management Analysis

### OpenTelemetry Version Configuration
✅ **Correctly Configured**
- `<opentelemetry.version>1.59.0</opentelemetry.version>` ✓
- `<opentelemetry-instrumentation.version>2.10.0</opentelemetry-instrumentation.version>` ✓

### JUnit Jupiter Version Configuration
✅ **Correctly Configured**
- `<junit.jupiter.version>5.11.3</junit.jupiter.version>` ✓
- `<junit.version>5.11.3</junit.version>` ✓
- `<junit.platform.version>1.11.3</junit.platform.version>` ✓

---

## 3. Duplicate Dependencies Found

The following dependencies are declared multiple times in `dependencyManagement` section:

| Count | Dependency |
|-------|------------|
| 2 | `org.springframework.boot:spring-boot-starter-web` |
| 2 | `org.springframework.boot:spring-boot-starter-actuator` |
| 2 | `org.slf4j:slf4j-api` |
| 2 | `org.simplejavamail:simple-java-mail` |
| 2 | `org.postgresql:postgresql` |
| 2 | `org.openjdk.jmh:jmh-generator-annprocess` |
| 2 | `org.openjdk.jmh:jmh-core` |
| 2 | `org.junit.platform:junit-platform-suite-engine` |
| 2 | `org.junit.platform:junit-platform-suite-api` |
| 2 | `org.junit.jupiter:junit-jupiter-engine` |
| 2 | `org.junit.jupiter:junit-jupiter-api` |
| 2 | `org.jspecify:jspecify` |
| 2 | `org.jdom:jdom2` |
| 2 | `org.jboss.logging:jboss-logging` |
| 2 | `org.jboss:jandex` |
| 2 | `org.hsqldb:hsqldb` |
| 2 | `org.hibernate.orm:hibernate-jcache` |
| 2 | `org.hibernate.orm:hibernate-hikaricp` |
| 2 | `org.hibernate.orm:hibernate-core` |
| 2 | `xmlunit:xmlunit` |

### Impact
These duplicates don't cause functional issues as Maven will use the version declared in `dependencyManagement`, but they clutter the configuration and may cause confusion.

---

## 4. Version Consistency Issues

### OpenTelemetry Prometheus Exporter
❌ **Inconsistent Versioning**
- `opentelemetry-exporter-prometheus` uses version `1.59.0-alpha`
- Other OpenTelemetry components use `1.59.0` (stable)
- **Recommendation**: Either use `1.59.0` for all or clearly document why the alpha version is needed

### OpenTelemetry Semantic Conventions
⚠️ **Potential Version Mismatch**
- `opentelemetry-semconv:1.39.0`
- `opentelemetry-semconv-incubating:1.39.0-alpha`
- **Note**: These are BOM versions for semantic conventions, not library dependencies

---

## 5. Critical Issues Summary

### High Priority (Functional Impact)
1. **Missing Module Declarations** - 7 modules not included in reactor
   - This will cause build failures when running `mvn clean install`
   - Modules will be skipped during multi-module builds

### Medium Priority (Code Quality)
2. **Duplicate Dependencies** - 20 duplicate dependency declarations
   - No functional impact, but reduces maintainability
   - Can be cleaned up to improve readability

### Low Priority (Configuration)
3. **Version Inconsistencies** - Minor versioning issues
   - Prometheus exporter using alpha version
   - Some build comment regarding BOM removal

---

## 6. Recommended Actions

### Immediate Actions Required
1. **Add Missing Modules to parent pom.xml**:
```xml
<modules>
    <!-- Existing modules -->
    <module>yawl-ggen</module>
    <module>yawl-graalpy</module>
    <module>yawl-gregverse</module>
    <module>yawl-hooks</module>
    <module>yawl-mcp-a2a-app</module>
    <module>yawl-pi</module>
    <module>yawl-security</module>
</modules>
```

### Optional Improvements
2. **Clean up Duplicate Dependencies**:
   - Remove duplicate declarations from `dependencyManagement`
   - Keep only one declaration per dependency

3. **Standardize OpenTelemetry Versions**:
   - Consider using stable version `1.59.0` for all OpenTelemetry components
   - Document if alpha version is intentionally required

---

## 7. Build Validation Commands

To test the reactor after fixes:

```bash
# Validate reactor structure
mvn validate

# Check for dependency resolution issues
mvn dependency:tree

# Test building all modules
mvn clean compile -DskipTests

# Run full build
mvn clean verify
```

---

## 8. Conclusion

The Maven reactor has significant issues that will prevent proper multi-module builds:

- **❌ FAIL**: Missing module declarations (critical)
- ⚠️ **WARN**: Duplicate dependencies (quality issue)
- ⚠️ **WARN**: Version inconsistencies (configuration issue)

The reactor **cannot** build successfully without adding the missing 7 modules to the parent pom.xml.

---
Generated by production-validator agent
End of Report
