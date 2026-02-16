# Build Version Mismatch Fixes

**Date**: 2026-02-16
**Session**: https://claude.ai/code/session_01KDHNeGpU43fqGudyTYv8tM

## Issues Resolved

### 1. Jakarta Faces Version Mismatches

**Problem**: `build.xml` defined a single `jakarta.faces.version` property set to "4.0.0", but the actual JARs in `build/3rdParty/lib/` were:
- `jakarta.faces-api-4.0.1.jar` (API)
- `jakarta.faces-4.0.5.jar` (Implementation)

**Resolution**: Split into separate version properties:
- `jakarta.faces.api.version` = "4.0.1"
- `jakarta.faces.impl.version` = "4.0.5"

**Files Changed**:
- `/home/user/yawl/build/build.xml` (lines 69-70, 306, 308)

### 2. Hibernate Version Mismatches

**Problem**: `build.xml` defined `hibernate.version` = "5.6.14.Final" but all actual JARs were Hibernate 6.4.4.Final:
- `hibernate-core-6.4.4.Final.jar`
- `hibernate-hikaricp-6.4.4.Final.jar`
- `hibernate-jcache-6.4.4.Final.jar`
- `hibernate-community-dialects-6.4.4.Final.jar`
- `hibernate-commons-annotations-6.0.6.Final.jar`

**Resolution**: Updated version properties:
- `hibernate.version` = "6.4.4.Final"
- `hibernate.commons.annotations.version` = "6.0.6.Final" (new property)

**Files Changed**:
- `/home/user/yawl/build/build.xml` (lines 64-65, 230-249)

### 3. Database Driver Consistency

**Problem**: Hard-coded versions in JAR property definitions.

**Resolution**: Updated to use version properties:
- `postgresql-${postgresql.version}.jar` (42.7.4)
- `mysql-connector-j-${mysql.version}.jar` (8.3.0)
- `h2-${h2.version}.jar` (2.2.224)

**Files Changed**:
- `/home/user/yawl/build/build.xml` (lines 230, 391, 404)

## Property Standardization

Introduced centralized version properties in `build.xml` for easier maintenance:

```xml
<!-- Dependency Version Properties -->
<property name="java.version" value="21"/>
<property name="postgresql.version" value="42.7.4"/>
<property name="mysql.version" value="8.3.0"/>
<property name="h2.version" value="2.2.224"/>
<property name="hibernate.version" value="6.4.4.Final"/>
<property name="hibernate.commons.annotations.version" value="6.0.6.Final"/>
<property name="jackson.version" value="2.18.2"/>
<property name="junit.version" value="5.10.2"/>
<property name="jakarta.servlet.version" value="6.0.0"/>
<property name="jakarta.faces.api.version" value="4.0.1"/>
<property name="jakarta.faces.impl.version" value="4.0.5"/>
<property name="jakarta.persistence.version" value="3.1.0"/>
<property name="jdom.version" value="2.0.6.1"/>
<property name="saxon.version" value="12.4"/>
<property name="log4j.version" value="2.23.1"/>
```

## Validation Target

Added `validateVersions` target to prevent future mismatches:

```xml
<target name="validateVersions" description="Validate all JAR versions match actual files">
    <echo message="Validating Jakarta Faces API..."/>
    <available file="${lib.dir}/${jsf-api}" property="faces.api.present"/>
    <fail unless="faces.api.present"
          message="Missing ${jsf-api} - expected jakarta.faces-api-${jakarta.faces.api.version}.jar"/>

    <echo message="Validating Jakarta Faces Implementation..."/>
    <available file="${lib.dir}/${jsf-impl}" property="faces.impl.present"/>
    <fail unless="faces.impl.present"
          message="Missing ${jsf-impl} - expected jakarta.faces-${jakarta.faces.impl.version}.jar"/>

    <!-- ... additional validations for Hibernate, H2, PostgreSQL, MySQL ... -->

    <echo message="✓ All version validations passed"/>
</target>
```

**Usage**: Run validation before compilation:
```bash
ant -f build/build.xml validateVersions
```

The `compile` target now depends on `validateVersions`, so it runs automatically during builds.

## Verification

### Version Validation Test
```bash
$ ant -f build/build.xml validateVersions
Buildfile: /home/user/yawl/build/build.xml

validateVersions:
     [echo] Validating Jakarta Faces API...
     [echo] Validating Jakarta Faces Implementation...
     [echo] Validating Hibernate Core...
     [echo] Validating Hibernate Commons Annotations...
     [echo] Validating H2 Database...
     [echo] Validating PostgreSQL Driver...
     [echo] Validating MySQL Driver...
     [echo] ✓ All version validations passed

BUILD SUCCESSFUL
Total time: 0 seconds
```

### Compilation Test
```bash
$ ant -f build/build.xml clean compile
# validateVersions runs automatically before compile
# No Jakarta Faces version mismatch errors
# Compilation proceeds with correct JAR versions
```

## Impact

### Before Fixes
- Mismatched version references caused confusion
- Hard-coded versions scattered throughout build.xml
- No validation of JAR presence
- Potential for classpath errors

### After Fixes
- All version properties match actual JARs
- Centralized version management
- Automatic validation prevents future mismatches
- Clear error messages if JARs are missing
- Easier version upgrades (change one property)

## Known Issues

The build still fails due to Hibernate API changes (5.x → 6.x migration):
- `org.hibernate.tool.schema.internal.SchemaUpdateImpl` (removed in Hibernate 6)
- `org.hibernate.criterion.*` (replaced by Jakarta Criteria API)
- `org.hibernate.Query` (replaced by `jakarta.persistence.Query`)

These are **code compatibility issues**, not version mismatches. The version properties are now correct.

## Future Maintenance

When updating dependency versions:

1. Update the version property in `build.xml` (lines 60-74)
2. Run `ant -f build/build.xml validateVersions` to verify JAR exists
3. Run `ant -f build/build.xml clean compile` to test compilation

The `validateVersions` target will catch missing JARs immediately.

## Summary

All Jakarta Faces and Hibernate version mismatches have been resolved. The build.xml now correctly references:
- Jakarta Faces API 4.0.1
- Jakarta Faces Implementation 4.0.5
- Hibernate 6.4.4.Final
- Hibernate Commons Annotations 6.0.6.Final

A validation target ensures version properties match actual JAR files.
