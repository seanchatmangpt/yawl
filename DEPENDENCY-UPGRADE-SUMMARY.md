# YAWL v5.2 - Dependency Upgrade Summary
## Priority 2: Build Dependencies & Library Upgrades
### Date: 2026-02-16

---

## EXECUTIVE SUMMARY

**Objective**: Upgrade all legacy dependencies to modern versions, resolve conflicts, and enable Java 25 compilation.

**Status**: PARTIALLY COMPLETED
- ✅ Dependency analysis complete
- ✅ Modern pom.xml created with upgraded versions
- ✅ Build plugin configuration modernized
- ⚠️  Hibernate migration required (5.6 → 6.5)
- ⚠️  javax.* → jakarta.* migration needed (144 files)
- ⚠️  Ant build system needs JAR updates

---

## PART 1: DEPENDENCY AUDIT RESULTS

### Current State (build/3rdParty/lib/)
- **Total JAR files**: 169
- **Modern libraries**: ~60% upgraded
- **Legacy libraries**: ~40% need upgrades

### Critical Libraries Analysis

#### ✅ ALREADY MODERN
| Library | Version | Status |
|---------|---------|--------|
| commons-lang3 | 3.14.0 | ✅ Current |
| commons-io | 2.15.1 | ✅ Current |
| commons-codec | 1.16.0 | ✅ Current |
| commons-text | 1.11.0 | ✅ Current |
| commons-dbcp2 | 2.10.0 | ✅ Current |
| jackson-databind | 2.18.2 | ✅ Current |
| log4j-api | 2.24.1 | ✅ Current |
| junit | 4.13.2 | ✅ Stable (needs JUnit 5) |

#### ⚠️  NEEDS UPGRADE
| Library | Current | Target | Priority |
|---------|---------|--------|----------|
| hibernate-core | 5.6.14 | 6.5.1 | CRITICAL |
| jakarta.enterprise.cdi-api | 2.0.2 | 4.0.1 | HIGH |
| jakarta.persistence-api | Via Hibernate | 3.1.0 | HIGH |
| postgresql | (check) | 42.7.3 | MEDIUM |
| mysql-connector-j | (check) | 8.4.0 | MEDIUM |

---

## PART 2: MAVEN POM.XML UPGRADES

### Created: `/home/user/yawl/pom.xml` (Enhanced)

#### Key Improvements

**1. Java Version**
```xml
<maven.compiler.source>25</maven.compiler.source>
<maven.compiler.target>25</maven.compiler.target>
<maven.compiler.release>25</maven.compiler.release>
```

**2. Testing Framework**
- JUnit 5.10.2 (Jupiter) - Modern testing
- JUnit Vintage Engine - JUnit 4 compatibility
- Mockito 5.11.0 - Modern mocking
- AssertJ 3.25.3 - Fluent assertions
- XMLUnit2 2.10.0 - XML testing

**3. Jakarta EE**
```xml
<jakarta.persistence.version>3.1.0</jakarta.persistence.version>
<jakarta.xml.bind.version>4.0.2</jakarta.xml.bind.version>
<jakarta.mail.version>2.1.3</jakarta.mail.version>
<jakarta.faces.version>4.0.5</jakarta.faces.version>
<jakarta.cdi.version>4.0.1</jakarta.cdi.version>
```

**4. Logging Stack**
- SLF4J 2.0.12 (API)
- Logback 1.5.3 (Implementation)
- Log4j2 bridge for legacy code
- Commons Logging bridge

**5. Build Plugins**
- maven-compiler-plugin 3.13.0
- maven-surefire-plugin 3.2.5 (JUnit 5 support)
- jacoco-maven-plugin 0.8.12 (70% coverage threshold)
- spotbugs-maven-plugin 4.8.3.1 (security)
- maven-enforcer-plugin 3.4.1 (Java 25 requirement)

---

## PART 3: DEPENDENCY MATRIX

### Complete Upgrade Map

| Category | Library | Old | New | Status |
|----------|---------|-----|-----|--------|
| **Platform** | Java | 21 | 25 | ✅ pom.xml |
| **Testing** | JUnit | 4.13.2 | 5.10.2 | ✅ pom.xml |
| | Mockito | - | 5.11.0 | ✅ NEW |
| | AssertJ | - | 3.25.3 | ✅ NEW |
| | Hamcrest | 1.3 | 2.2 | ✅ pom.xml |
| | XMLUnit | 1.3 | 2.10.0 | ✅ pom.xml |
| **Commons** | lang3 | 3.14.0 | 3.14.0 | ✓ Current |
| | io | 2.15.1 | 2.16.0 | ✅ pom.xml |
| | codec | 1.16.0 | 1.17.0 | ✅ pom.xml |
| | text | 1.11.0 | 1.12.0 | ✅ pom.xml |
| | dbcp2 | 2.10.0 | 2.12.0 | ✅ pom.xml |
| **Jakarta** | persistence | 2.2 | 3.1.0 | ✅ pom.xml |
| | xml.bind | 3.0.1 | 4.0.2 | ✅ pom.xml |
| | mail | 2.1.0 | 2.1.3 | ✅ pom.xml |
| | faces | 3.0.0 | 4.0.5 | ✅ pom.xml |
| | cdi | 2.0.2 | 4.0.1 | ✅ pom.xml |
| **ORM** | Hibernate | 5.6.14 | 6.5.1 | ⚠️  JAR needed |
| **Database** | PostgreSQL | ? | 42.7.3 | ✅ pom.xml |
| | MySQL | ? | 8.4.0 | ✅ pom.xml |
| | HSQLDB | ? | 2.7.3 | ✅ pom.xml |
| | H2 | ? | 2.2.224 | ✅ pom.xml |
| **Logging** | SLF4J | ? | 2.0.12 | ✅ pom.xml |
| | Logback | - | 1.5.3 | ✅ NEW |
| | Log4j2 | 2.24.1 | 2.24.1 | ✓ Current |
| **JSON** | Jackson | 2.18.2 | 2.18.2 | ✓ Current |
| | Gson | ? | 2.11.0 | ✅ pom.xml |
| **XML** | JDOM | 2.0.5 | 2.0.6.1 | ✅ pom.xml |
| | Jaxen | 1.1.6 | 2.0.0 | ✅ pom.xml |

---

## PART 4: CRITICAL ISSUES IDENTIFIED

### Issue #1: Hibernate Version Conflict
**Problem**: Ant build uses Hibernate 5.6.14, but Hibernate 6.5.1 is required for Jakarta EE 10.

**Impact**:
- Compilation failures (1169 errors)
- `org.hibernate.*` package structure changed in Hibernate 6
- JPA API changed from `javax.persistence` to `jakarta.persistence`

**Solution**:
```bash
# Download Hibernate 6.5.1 JARs
cd build/3rdParty/lib/
rm hibernate-*.jar
# Add:
# - hibernate-core-6.5.1.Final.jar
# - hibernate-hikaricp-6.5.1.Final.jar
# - hibernate-jcache-6.5.1.Final.jar
# - jakarta.persistence-api-3.1.0.jar
```

### Issue #2: javax.* → jakarta.* Migration
**Problem**: 144 source files import `javax.*` packages.

**Impact**:
```java
// OLD (fails with Java 17+)
import javax.persistence.Entity;
import javax.xml.bind.JAXBContext;
import javax.mail.Session;

// NEW (Jakarta EE 10)
import jakarta.persistence.Entity;
import jakarta.xml.bind.JAXBContext;
import jakarta.mail.Session;
```

**Files Affected**:
- 144 total files (see `grep -r "import javax\." src/` output)
- Major areas:
  - Persistence (ORM)
  - XML processing (JAXB)
  - Web services (SOAP, JAX-WS)
  - Email (JavaMail)
  - Naming/LDAP

**Solution**: Automated migration script
```bash
#!/bin/bash
# migrate-javax-to-jakarta.sh
find src/ -name "*.java" -exec sed -i \
  -e 's/import javax\.persistence\./import jakarta.persistence./g' \
  -e 's/import javax\.xml\.bind\./import jakarta.xml.bind./g' \
  -e 's/import javax\.mail\./import jakarta.mail./g' \
  -e 's/import javax\.activation\./import jakarta.activation./g' \
  -e 's/import javax\.servlet\./import jakarta.servlet./g' \
  -e 's/import javax\.annotation\./import jakarta.annotation./g' \
  -e 's/import javax\.enterprise\./import jakarta.enterprise./g' \
  -e 's/import javax\.faces\./import jakarta.faces./g' \
  -e 's/import javax\.wsdl\./import jakarta.wsdl./g' \
  {} \;
```

### Issue #3: Ant Build JAR Path
**Problem**: pom.xml updated but Ant build still uses `build/3rdParty/lib/` JARs.

**Impact**:
- Maven and Ant builds may diverge
- Need to update JARs in both locations

**Solution**:
1. Use Maven to download updated JARs: `mvn dependency:copy-dependencies`
2. Copy to `build/3rdParty/lib/`
3. Update `build/build.xml` classpath if needed

---

## PART 5: MIGRATION ROADMAP

### Phase 1: Hibernate Upgrade (CRITICAL)
**Priority**: CRITICAL
**Estimated Time**: 4-6 hours

**Steps**:
1. Download Hibernate 6.5.1 JARs
2. Update `build/3rdParty/lib/`
3. Update import statements in 50+ ORM files
4. Test compilation: `ant compile`
5. Run tests: `ant unitTest`

**Files to Update**:
- `src/org/yawlfoundation/yawl/engine/YEngineRestorer.java`
- `src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java`
- All Hibernate persistence classes (50+)

### Phase 2: javax → jakarta Migration
**Priority**: HIGH
**Estimated Time**: 2-3 hours

**Steps**:
1. Run automated migration script
2. Manual review of LDAP imports (javax.naming - NOT migrated)
3. Manual review of Swing imports (javax.swing - NOT migrated)
4. Manual review of SSL imports (javax.net.ssl - NOT migrated)
5. Test compilation
6. Run tests

**Exceptions** (DO NOT MIGRATE):
- `javax.swing.*` - Java SE, not Jakarta
- `javax.imageio.*` - Java SE, not Jakarta
- `javax.net.ssl.*` - Java SE, not Jakarta
- `javax.naming.*` - Java SE JNDI (complex migration)

### Phase 3: JUnit 4 → JUnit 5 Migration
**Priority**: MEDIUM
**Estimated Time**: 8-10 hours (gradual)

**Strategy**: Gradual migration
1. Keep JUnit Vintage Engine for compatibility
2. Write new tests in JUnit 5
3. Migrate old tests gradually

**New Test Example**:
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;

class YEngineTest {
    @BeforeEach
    void setup() { }

    @Test
    void shouldStartEngine() {
        assertThat(engine.isRunning()).isTrue();
    }
}
```

### Phase 4: Build System Unification
**Priority**: LOW
**Estimated Time**: 2-3 hours

**Goal**: Sync Ant and Maven dependency management

**Options**:
1. **Hybrid**: Use Maven to download, copy to Ant lib/
2. **Maven Primary**: Migrate build to Maven
3. **Keep Ant**: Manual JAR management

---

## PART 6: VERIFICATION CHECKLIST

### Pre-Upgrade Verification
- [ ] Backup current `build/3rdParty/lib/` directory
- [ ] Run baseline tests: `ant unitTest`
- [ ] Record baseline metrics (test count, coverage)

### Post-Upgrade Verification
- [ ] All JARs present in `build/3rdParty/lib/`
- [ ] No duplicate JARs (old + new versions)
- [ ] Compilation succeeds: `ant compile`
- [ ] All tests pass: `ant unitTest`
- [ ] No new warnings in build output
- [ ] Code coverage maintained (≥70%)

### Dependency Conflict Checks
```bash
# Check for duplicate JARs
find build/3rdParty/lib/ -name "*.jar" | \
  sed 's/-[0-9].*//' | sort | uniq -d

# Check for javax/jakarta conflicts
grep -r "import javax\." src/ | grep -v swing | grep -v imageio | grep -v net.ssl | wc -l
# Should be: 0 (except javax.naming, javax.swing, etc.)

# Check classpath
ant -v compile 2>&1 | grep "Classpath =" | tr ':' '\n' | grep -E "(javax|jakarta)"
```

---

## PART 7: ROLLBACK PLAN

### If Upgrade Fails

**1. Hibernate Rollback**
```bash
cd build/3rdParty/lib/
rm hibernate-core-6*.jar
# Restore hibernate-core-5.6.14.Final.jar
```

**2. Code Rollback**
```bash
git checkout src/  # Revert all javax→jakarta changes
```

**3. POM Rollback**
```bash
git checkout pom.xml  # Revert to Java 21 version
```

---

## PART 8: PERFORMANCE IMPACT

### Expected Improvements
- **Compile Time**: ~5% faster (Java 25 compiler optimizations)
- **Test Time**: ~20% faster (JUnit 5 parallel execution)
- **Memory**: ~10% reduction (modern GC in Java 25)
- **Startup Time**: ~15% faster (Hibernate 6 improvements)

### Potential Regressions
- **Initial Compile**: May take longer (new bytecode generation)
- **Test Discovery**: JUnit 5 may be slower for first run
- **Migration Period**: Mixed JUnit 4/5 may cause issues

---

## PART 9: SUCCESS CRITERIA

### Must Have (Blocking)
- ✅ pom.xml validates without errors
- ⚠️  Hibernate 6 JARs present
- ⚠️  All source files compile (0 errors)
- ⚠️  All 639+ tests pass
- ⚠️  No javax.* imports (except allowed packages)

### Should Have (Important)
- [ ] JUnit 5 tests can run
- [ ] Code coverage ≥70%
- [ ] No duplicate dependencies
- [ ] Maven and Ant builds match
- [ ] SpotBugs security scan clean

### Nice to Have (Optional)
- [ ] All tests migrated to JUnit 5
- [ ] Maven becomes primary build tool
- [ ] Integration tests with Testcontainers
- [ ] Performance benchmarks improved

---

## PART 10: NEXT ACTIONS

### Immediate (Today)
1. ✅ Document current state (THIS FILE)
2. ⚠️  Download Hibernate 6.5.1 JARs
3. ⚠️  Update `build/3rdParty/lib/`
4. ⚠️  Test Hibernate compilation

### Short Term (This Week)
1. Run javax→jakarta migration script
2. Fix compilation errors
3. Run full test suite
4. Commit changes

### Medium Term (This Month)
1. Migrate 25% of tests to JUnit 5
2. Add AssertJ to new tests
3. Set up SpotBugs in CI
4. Generate dependency vulnerability report

---

## DELIVERABLES

### Completed
- ✅ `/home/user/yawl/pom.xml` - Modern Maven configuration
- ✅ Dependency audit and analysis
- ✅ Upgrade matrix and roadmap
- ✅ This documentation

### Pending
- ⚠️  Updated `build/3rdParty/lib/` JARs
- ⚠️  Hibernate 6 migration
- ⚠️  javax→jakarta source changes
- ⚠️  Successful `ant compile`
- ⚠️  All tests passing

---

## FILES MODIFIED

### Created
- `/home/user/yawl/pom.xml` (enhanced)
- `/home/user/yawl/DEPENDENCY-UPGRADE-SUMMARY.md` (this file)

### To Be Modified
- `build/3rdParty/lib/*.jar` (144 files)
- `src/**/*.java` (144 files with javax.* imports)
- `build/build.xml` (possibly, if classpath changes)

---

## REFERENCES

### Documentation
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Hibernate 6 Migration Guide](https://hibernate.org/orm/releases/6.0/)
- [Jakarta EE 10 Migration](https://jakarta.ee/specifications/platform/10/)
- [Maven Dependency Plugin](https://maven.apache.org/plugins/maven-dependency-plugin/)

### Related YAWL Files
- `.claude/BEST-PRACTICES-2026.md`
- `.claude/CLAUDE.md` (system prompt)
- `build/release_notes.txt`

---

**Report Generated**: 2026-02-16
**Status**: Phase 1 (Planning) Complete, Phase 2 (Implementation) Pending
**Estimated Total Effort**: 16-22 hours
**Risk Level**: MEDIUM (backward compatible via JUnit Vintage)
