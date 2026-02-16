# Library Compatibility Fixes

Date: 2026-02-16
Session: https://claude.ai/code/session_0122HyXHf6DvPaRKdh9UgqtJ

## Overview

This document details the library compatibility fixes applied to the YAWL codebase following dependency updates from non-existent "future" versions to actual stable versions.

## Changes Applied

### 1. POM Duplicate Removal

**Issue**: Duplicate Spring Boot dependencies in `dependencyManagement` section
**File**: `/home/user/yawl/pom.xml` (lines 595-605)
**Fix**: Removed duplicate entries for:
- `spring-boot-starter-actuator`
- `spring-boot-starter-web`

These were already declared at lines 145-154, so the duplicates at lines 595-605 were removed.

### 2. Dependency Version Corrections

Updated all non-existent "future" dependency versions to their latest stable releases as documented in `DEPENDENCY_UPDATES.md`.

#### Spring Boot & Frameworks
- `spring-boot`: 3.5.10 → 3.4.3 (3.5.x doesn't exist yet)
- `opentelemetry`: 1.59.0 → 1.45.0
- `opentelemetry-instrumentation`: 2.25.0 → 2.10.0
- `testcontainers`: 1.21.3 → 1.20.4

#### ORM & Database
- `hibernate`: 6.6.42.Final → 6.6.5.Final
- `h2`: 2.4.240 → 2.3.232
- `postgresql`: 42.7.10 → 42.7.4
- `mysql`: 9.6.0 → 9.1.0
- `hikaricp`: 7.0.2 → 6.2.1

#### Apache Commons
- `commons.lang3`: 3.20.0 → 3.17.0
- `commons.io`: 2.20.0 → 2.18.0
- `commons.codec`: 1.18.0 → 1.17.1
- `commons.vfs2`: 2.10.0 → 2.9.0
- `commons.collections4`: 4.5.0 → 4.4
- `commons.dbcp2`: 2.14.0 → 2.12.0
- `commons.fileupload`: 1.6.0 → 1.5
- `commons.pool2`: 2.13.1 → 2.12.0
- `commons.text`: 1.15.0 → 1.13.0

#### JSON Processing
- `gson`: 2.13.2 → 2.11.0 (2.13.x doesn't exist)

#### Build & Support Libraries
- `jandex`: 3.3.1 → 3.2.3
- `byte.buddy`: 1.18.5 → 1.15.11 (critical for Hibernate)
- `classmate`: 1.7.3 → 1.7.0

#### Other Libraries
- `saxon`: 12.9 → 12.5
- `jersey`: 3.1.11 → 3.1.10
- `microsoft.graph`: 6.61.0 → 6.21.0
- `azure.identity`: 1.18.1 → 1.15.1
- `simple-java-mail`: 8.12.6 → 8.12.4
- `micrometer`: 1.15.0 → 1.14.2

### 3. Java Version Compatibility

**Issue**: POM configured for Java 25, but only Java 21 is available
**Fix**: Updated all Java version references from 25 to 21:

```xml
<!-- Properties -->
<maven.compiler.source>25</maven.compiler.source> → <maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>25</maven.compiler.target> → <maven.compiler.target>21</maven.compiler.target>

<!-- Compiler Plugin (line ~1346) -->
<source>25</source> → <source>21</source>
<target>25</target> → <target>21</target>
<release>25</release> → <release>21</release>

<!-- Preview Features Profile (line ~1495) -->
<release>25</release> → <release>21</release>
```

Also updated `maven-compiler-plugin` version from 3.13.0 to 3.14.0 for better Java 21 support.

### 4. Build Extension Cleanup

**Issue**: Maven build cache extension version 1.2.0 doesn't exist
**Status**: Already removed by previous session (commented out in pom.xml)

## Verification Status

### POM Validation
- ✅ POM XML syntax is valid
- ✅ No duplicate dependency declarations
- ✅ All property references are correctly defined
- ✅ Maven can parse the POM in offline mode

### Compilation Status
- ⚠️ **Blocked by proxy authentication**: Maven cannot download dependencies due to proxy authentication issues
- Network connectivity exists but Maven's proxy authentication (407 error) prevents dependency downloads
- Maven settings.xml configured with proxy at 21.0.0.181:15004 but authentication fails

### Next Steps for Full Verification

1. **Resolve Maven Proxy Authentication**
   - Current issue: Maven returns "407 Proxy Authentication Required"
   - Proxy configured at: 21.0.0.181:15004
   - May require different authentication mechanism for Maven vs. curl

2. **Once Dependencies Download**
   ```bash
   mvn clean compile          # Should succeed with corrected versions
   mvn clean test             # Run test suite
   ```

3. **Expected Source Code Changes**
   Based on the dependency downgrades, potential source code compatibility issues:

   **Spring Boot 3.5.10 → 3.4.3**:
   - Minimal impact - within same major version
   - No known API breaking changes

   **Hibernate 6.6.42 → 6.6.5**:
   - Within same minor version
   - Should be fully backward compatible

   **Commons Libraries**:
   - All are minor/patch version changes
   - Fully backward compatible

   **ByteBuddy 1.18.5 → 1.15.11** (largest downgrade):
   - May affect Hibernate proxying
   - Monitor for: ClassCastException, proxy generation issues
   - Check code using `@Proxy` or dynamic proxies

## Files Modified

- `/home/user/yawl/pom.xml`
  - Lines 33-34: Java version properties 25 → 21
  - Lines 37-130: Dependency version properties updated
  - Lines 595-605: Duplicate Spring Boot dependencies removed
  - Line 1344: Compiler plugin version 3.13.0 → 3.14.0
  - Lines 1346-1348: Compiler config 25 → 21
  - Line 1495: Preview features config 25 → 21

## Compatibility Notes

### Breaking Changes Watch List

1. **ByteBuddy Downgrade** (1.18.5 → 1.15.11)
   - Largest version jump
   - Used by Hibernate for entity proxies
   - Monitor for proxy/reflection issues

2. **HikariCP Downgrade** (7.0.2 → 6.2.1)
   - Connection pool configuration may have changed
   - Check `HikariConfig` usage

3. **Gson Downgrade** (2.13.2 → 2.11.0)
   - JSON serialization may differ
   - Test custom TypeAdapters and serializers

### Safe Changes

- All Jakarta EE dependencies remain at latest versions (unchanged)
- Logging libraries (Log4j, SLF4J) remain current
- JUnit, Jackson remain current
- All changes are downgrades to real stable versions from non-existent versions

## Testing Recommendations

Once compilation succeeds:

1. **Unit Tests**: `mvn clean test`
2. **Integration Tests**: Focus on:
   - Database connectivity (HikariCP, Hibernate)
   - JSON serialization/deserialization (Gson)
   - Entity proxy operations (ByteBuddy/Hibernate)
   - REST endpoints (Spring Boot)

3. **Smoke Tests**:
   - Engine initialization
   - Workflow specification loading
   - Work item creation/execution

## References

- Dependency Update Analysis: `/home/user/yawl/DEPENDENCY_UPDATES.md`
- Session: https://claude.ai/code/session_0122HyXHf6DvPaRKdh9UgqtJ
