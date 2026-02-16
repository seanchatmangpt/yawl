# Missing Dependencies Report
**Date**: 2026-02-16
**Status**: CRITICAL - Required for Compilation
**Priority**: HIGH

---

## Summary

After applying initial dependency fixes (JJWT, Jakarta XML, Bouncycastle, Commons VFS2), the following dependencies are still missing from `/home/user/yawl/build/3rdParty/lib/`:

### Status Overview
- ✅ **Core YAWL**: All dependencies resolved (1061 files)
- ⚠️ **Spring Boot Actuator**: Missing (14 files, optional monitoring)
- ⚠️ **OpenTelemetry**: Missing (7 files, optional observability)
- ❌ **Resilience4j**: MISSING (CRITICAL - required for ZaiService)

---

## 1. Critical Dependencies (Required)

### 1.1 Resilience4j (Circuit Breaker & Retry)

**Status**: ❌ NOT FOUND IN LIB DIRECTORY

**Required For**: Z.AI integration (ZaiService.java)
**Priority**: **CRITICAL**

**Missing JARs**:
```xml
<!-- Resilience4j Core -->
<property name="resilience4j-core" value="resilience4j-core-2.2.0.jar"/>
<property name="resilience4j-circuitbreaker" value="resilience4j-circuitbreaker-2.2.0.jar"/>
<property name="resilience4j-retry" value="resilience4j-retry-2.2.0.jar"/>

<!-- Vavr (Resilience4j dependency) -->
<property name="vavr" value="vavr-0.10.4.jar"/>
```

**Maven Coordinates**:
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
    <version>2.2.0</version>
</dependency>
```

**Download URLs**:
```
https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-circuitbreaker/2.2.0/resilience4j-circuitbreaker-2.2.0.jar
https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-retry/2.2.0/resilience4j-retry-2.2.0.jar
https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-core/2.2.0/resilience4j-core-2.2.0.jar
https://repo1.maven.org/maven2/io/vavr/vavr/0.10.4/vavr-0.10.4.jar
```

**Impact**: ZaiService.java cannot compile (Z.AI integration broken)

---

## 2. Optional Dependencies (Non-Blocking)

### 2.1 Spring Boot 3.x (Actuator Monitoring)

**Status**: ⚠️ NOT FOUND IN LIB DIRECTORY

**Required For**: Spring Boot actuator package (14 files)
**Priority**: MEDIUM (optional feature)

**Affected Files**:
- `src/org/yawlfoundation/yawl/engine/actuator/*.java` (14 files)

**Missing JARs** (Core Set):
```xml
<!-- Spring Boot 3.x -->
<property name="spring-boot" value="spring-boot-3.2.0.jar"/>
<property name="spring-boot-autoconfigure" value="spring-boot-autoconfigure-3.2.0.jar"/>
<property name="spring-boot-actuator" value="spring-boot-actuator-3.2.0.jar"/>
<property name="spring-boot-actuator-autoconfigure" value="spring-boot-actuator-autoconfigure-3.2.0.jar"/>

<!-- Spring Framework 6.x -->
<property name="spring-context" value="spring-context-6.1.0.jar"/>
<property name="spring-core" value="spring-core-6.1.0.jar"/>
<property name="spring-beans" value="spring-beans-6.1.0.jar"/>
<property name="spring-aop" value="spring-aop-6.1.0.jar"/>
<property name="spring-expression" value="spring-expression-6.1.0.jar"/>

<!-- Micrometer (Metrics) -->
<property name="micrometer-core" value="micrometer-core-1.12.0.jar"/>
<property name="micrometer-prometheus" value="micrometer-registry-prometheus-1.12.0.jar"/>
```

**Note**: Spring Boot requires ~20-25 transitive dependencies. Maven handles this automatically.

**Impact**: Actuator endpoints unavailable (health checks, metrics, etc.)

---

### 2.2 OpenTelemetry (Distributed Tracing)

**Status**: ⚠️ NOT FOUND IN LIB DIRECTORY

**Required For**: OpenTelemetry observability (7 files)
**Priority**: LOW (optional feature)

**Affected Files**:
- `src/org/yawlfoundation/yawl/integration/observability/*.java` (7 files)

**Missing JARs**:
```xml
<!-- OpenTelemetry API -->
<property name="opentelemetry-api" value="opentelemetry-api-1.33.0.jar"/>
<property name="opentelemetry-sdk" value="opentelemetry-sdk-1.33.0.jar"/>
<property name="opentelemetry-sdk-trace" value="opentelemetry-sdk-trace-1.33.0.jar"/>
<property name="opentelemetry-sdk-metrics" value="opentelemetry-sdk-metrics-1.33.0.jar"/>
<property name="opentelemetry-exporter-otlp" value="opentelemetry-exporter-otlp-1.33.0.jar"/>
<property name="opentelemetry-exporter-prometheus" value="opentelemetry-exporter-prometheus-1.33.0.jar"/>
```

**Impact**: Distributed tracing unavailable (Jaeger/Zipkin integration)

---

## 3. Recommendations

### 3.1 Immediate Action (CRITICAL)

**Add Resilience4j to build.xml**:

```bash
# Download JARs
cd /home/user/yawl/build/3rdParty/lib/
wget https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-circuitbreaker/2.2.0/resilience4j-circuitbreaker-2.2.0.jar
wget https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-retry/2.2.0/resilience4j-retry-2.2.0.jar
wget https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-core/2.2.0/resilience4j-core-2.2.0.jar
wget https://repo1.maven.org/maven2/io/vavr/vavr/0.10.4/vavr-0.10.4.jar
```

**Add to build.xml properties** (after line 294):
```xml
<!-- Resilience4j (circuit breaker and retry for Z.AI integration) -->
<property name="resilience4j-core" value="resilience4j-core-2.2.0.jar"/>
<property name="resilience4j-circuitbreaker" value="resilience4j-circuitbreaker-2.2.0.jar"/>
<property name="resilience4j-retry" value="resilience4j-retry-2.2.0.jar"/>
<property name="vavr" value="vavr-0.10.4.jar"/>
```

**Add to cp.compile classpath** (after line 727):
```xml
<!-- Resilience4j (fault tolerance for Z.AI integration) -->
<pathelement location="${lib.dir}/${resilience4j-core}"/>
<pathelement location="${lib.dir}/${resilience4j-circuitbreaker}"/>
<pathelement location="${lib.dir}/${resilience4j-retry}"/>
<pathelement location="${lib.dir}/${vavr}"/>
```

---

### 3.2 Short-Term (Optional)

**Option A**: Add Spring Boot 3.x dependencies
- Download ~25 JARs from Maven Central
- Add to build.xml properties and cp.compile
- Enables actuator endpoints

**Option B**: Exclude actuator package from compilation
- Add to build.xml compile task:
  ```xml
  <exclude name="**/engine/actuator/**"/>
  ```
- Faster compilation, no monitoring features

**Option C**: Use Maven build system
- Already configured in `pom.xml`
- Handles transitive dependencies automatically
- Recommended for future development

---

### 3.3 Long-Term (Strategic)

**Migrate to Maven** (recommended):
```bash
# Maven handles all transitive dependencies automatically
mvn clean install

# No manual JAR management
# Built-in dependency conflict resolution
# Better IDE integration
```

---

## 4. Current Compilation Status

### 4.1 Error Breakdown

```
Total Compilation Errors: 724+ (only showing first 100)

Breakdown by Category:
- Resilience4j (ZaiService): ~12 errors (CRITICAL)
- Spring Boot Actuator: ~78 errors (OPTIONAL)
- OpenTelemetry: ~634 errors (OPTIONAL)
```

### 4.2 After Adding Resilience4j

**Expected Status**:
```
Core YAWL: ✅ SUCCESS (0 errors)
ZaiService: ✅ SUCCESS (0 errors)
Spring Boot Actuator: ⚠️ FAILED (78 errors, optional)
OpenTelemetry: ⚠️ FAILED (634 errors, optional)
```

---

## 5. Dependency Graph

```
YAWL Core (✅ COMPILES)
├── Hibernate 6.5.1 (✅)
├── HikariCP 5.1.0 (✅)
├── Jakarta APIs (✅)
├── JJWT 0.12.5 (✅)
└── Bouncycastle 1.77 (✅)

Z.AI Integration (❌ BLOCKED)
├── OkHttp 4.12.0 (✅)
└── Resilience4j 2.2.0 (❌ MISSING)

Spring Boot Actuator (⚠️ OPTIONAL)
├── Spring Boot 3.2.0 (❌ MISSING)
└── Micrometer 1.12.0 (❌ MISSING)

OpenTelemetry (⚠️ OPTIONAL)
└── OpenTelemetry SDK 1.33.0 (❌ MISSING)
```

---

## 6. Download Script

Save as `/home/user/yawl/scripts/download-missing-deps.sh`:

```bash
#!/bin/bash
set -e

LIB_DIR="/home/user/yawl/build/3rdParty/lib"
MAVEN_CENTRAL="https://repo1.maven.org/maven2"

echo "Downloading missing dependencies..."

# Resilience4j (CRITICAL)
echo "Downloading Resilience4j..."
wget -P "$LIB_DIR" "$MAVEN_CENTRAL/io/github/resilience4j/resilience4j-circuitbreaker/2.2.0/resilience4j-circuitbreaker-2.2.0.jar"
wget -P "$LIB_DIR" "$MAVEN_CENTRAL/io/github/resilience4j/resilience4j-retry/2.2.0/resilience4j-retry-2.2.0.jar"
wget -P "$LIB_DIR" "$MAVEN_CENTRAL/io/github/resilience4j/resilience4j-core/2.2.0/resilience4j-core-2.2.0.jar"
wget -P "$LIB_DIR" "$MAVEN_CENTRAL/io/vavr/vavr/0.10.4/vavr-0.10.4.jar"

echo "✅ Resilience4j downloaded successfully"
echo ""
echo "Next steps:"
echo "1. Add Resilience4j properties to build.xml (see section 3.1)"
echo "2. Add Resilience4j to cp.compile classpath"
echo "3. Run: ant compile"
```

Make executable:
```bash
chmod +x /home/user/yawl/scripts/download-missing-deps.sh
```

---

## 7. Verification Commands

### After Adding Resilience4j:
```bash
# Check JARs are present
ls -lh /home/user/yawl/build/3rdParty/lib/resilience4j*.jar
ls -lh /home/user/yawl/build/3rdParty/lib/vavr*.jar

# Verify JAR contents
jar -tf /home/user/yawl/build/3rdParty/lib/resilience4j-circuitbreaker-2.2.0.jar | grep CircuitBreaker.class

# Test compilation (should reduce errors significantly)
ant compile 2>&1 | grep "error:" | wc -l
```

---

## 8. Decision Matrix

| Dependency | Status | Action Required | Priority | Impact |
|------------|--------|-----------------|----------|---------|
| **Resilience4j** | ❌ Missing | Download + add to classpath | **CRITICAL** | Z.AI integration broken |
| **Spring Boot** | ⚠️ Missing | Download OR exclude | MEDIUM | Monitoring unavailable |
| **OpenTelemetry** | ⚠️ Missing | Download OR exclude | LOW | Tracing unavailable |

**Recommendation**:
1. Add Resilience4j immediately (CRITICAL)
2. Exclude Spring Boot Actuator and OpenTelemetry (optional)
3. Migrate to Maven for long-term maintainability

---

## Appendix A: Full Resilience4j Property Definitions

```xml
<!-- Add after line 294 in build.xml -->

<!-- Resilience4j (Circuit Breaker & Retry for fault-tolerant Z.AI integration) -->
<property name="resilience4j-core" value="resilience4j-core-2.2.0.jar"/>
<!-- mul - Core resilience patterns -->
<property name="resilience4j-circuitbreaker" value="resilience4j-circuitbreaker-2.2.0.jar"/>
<!-- mul - Circuit breaker pattern -->
<property name="resilience4j-retry" value="resilience4j-retry-2.2.0.jar"/>
<!-- mul - Retry with exponential backoff -->
<property name="vavr" value="vavr-0.10.4.jar"/>
<!-- mul - Functional library required by Resilience4j -->
```

## Appendix B: Full Classpath Addition

```xml
<!-- Add after line 727 in build.xml (after JSpecify) -->

<!-- Resilience4j (fault tolerance for Z.AI integration) -->
<pathelement location="${lib.dir}/${resilience4j-core}"/>
<pathelement location="${lib.dir}/${resilience4j-circuitbreaker}"/>
<pathelement location="${lib.dir}/${resilience4j-retry}"/>
<pathelement location="${lib.dir}/${vavr}"/>
```

---

**Report Status**: COMPLETE
**Next Action**: Execute download script and add Resilience4j to build.xml
**Expected Outcome**: Core YAWL + Z.AI integration compiles successfully
