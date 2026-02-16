# Ant to Maven Migration - Archive Notes

**Status**: ✅ **COMPLETE** - YAWL v5.2 has been fully migrated from Apache Ant to Maven

**Migration Date**: 2026-02-16
**Completion**: Maven is now the exclusive build system

## What Was Archived

This directory (`legacy/ant-build/`) contains the archived Ant build infrastructure from YAWL v5.2.

### Archived Files

1. **build.xml** (3,713 lines)
   - Primary Apache Ant build file
   - Defined 13 WAR modules and multiple JAR targets
   - Managed 228+ third-party dependencies via classpath definitions
   - **Replaced by**: Maven pom.xml with proper dependency management

2. **build.properties.* files**
   - `build.properties.local` - Local development configuration
   - `build.properties.remote` - Remote/ephemeral environment configuration
   - Contained database connection settings, logging levels, build metadata
   - **Replaced by**: Maven properties in pom.xml + environment variables

3. **3rdParty/lib/** directory (228 JAR files)
   - Static JAR files previously included in Ant build
   - **Replaced by**: Maven Central Repository dependencies declared in pom.xml
   - Versions: All updated to latest stable versions with security patches

4. **installer.xml**
   - Ant-based installer configuration
   - **Status**: Not migrated (legacy artifact)

5. **ivy.xml**
   - Ivy dependency management configuration (Ant ecosystem)
   - **Replaced by**: Maven dependencyManagement in pom.xml

## Why This Migration

### Benefits of Maven

| Aspect | Ant | Maven |
|--------|-----|-------|
| Dependency Management | Manual classpath definitions | Central repository integration |
| Build Standardization | Custom build.xml logic | Standard lifecycle (clean, compile, test, package) |
| Maintenance | 3,713 line build.xml | pom.xml with clear structure |
| Multi-module Support | AntCall cascading | Native module support with parent/child POMs |
| IDE Integration | Limited | Excellent (IntelliJ, Eclipse, VS Code) |
| CI/CD | Script-based invocation | Native Maven plugin support |

### Deprecation Timeline

- **2026-02-15**: Maven becomes primary build system
- **2026-02-16**: Ant build files archived
- **2026-06-01**: Any remaining Ant scripts removed
- **2027-01-01**: Complete Ant removal (target date)

## How to Build Now

**All builds now use Maven standard commands:**

```bash
# Compile
mvn clean compile

# Run tests
mvn clean test

# Full package build
mvn clean package

# Specific module
mvn -pl yawl-engine clean package

# Rebuild everything
mvn clean install
```

## Finding Build Information

| Information | Old Location | New Location |
|-------------|--------------|--------------|
| Compile paths | build.xml line 174+ | pom.xml sourceDirectory |
| Dependencies | build.xml classpath definitions | pom.xml dependencyManagement |
| Test configuration | build.xml compile-test target | Maven Surefire plugin config |
| Database settings | build.properties | pom.xml properties + env vars |
| Version info | build.properties.remote | pom.xml version + pom.properties |

## If You Need Ant Again

⚠️ **Not recommended** - Ant build is archived and not maintained

If absolutely necessary:
1. Check `legacy/ant-build/build.xml` for historical reference
2. **Do NOT use for production builds** - dependencies are outdated
3. Restore files to `build/` directory if needed for investigation
4. **Strongly recommend** using Maven instead

## Architecture Reference

**Maven Module Structure** (See root pom.xml):
```
yawl-parent (pom)
├── yawl-utilities       → yawl-utilities-5.2.jar
├── yawl-elements        → yawl-elements-5.2.jar
├── yawl-engine          → yawl-engine-5.2.jar
├── yawl-stateless       → yawl-stateless-5.2.jar
├── yawl-resourcing      → yawl-resourcing-5.2.jar
├── yawl-worklet         → yawl-worklet-5.2.jar
├── yawl-scheduling      → yawl-scheduling-5.2.jar
├── yawl-integration     → yawl-integration-5.2.jar
├── yawl-monitoring      → yawl-monitoring-5.2.jar
└── yawl-control-panel   → yawl-control-panel-5.2.jar
```

## Questions or Issues?

- See CLAUDE.md for build system documentation
- Check .claude/agents/yawl-validator.md for validation procedures
- Review pom.xml for complete dependency and plugin configuration

---

**Archive Status**: ✅ Complete | **Maintenance**: None | **Reference**: Historical only
