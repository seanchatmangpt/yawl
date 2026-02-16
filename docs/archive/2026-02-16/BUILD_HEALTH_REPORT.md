# YAWL Build Health Report
**Generated:** 2026-02-16  
**Java Version:** 25  
**Build System:** Ant 1.10.x  
**Status:** FAILING

---

## Executive Summary

**Build Status:** FAILED
- **Compilation Errors:** 100+ (1157 total)
- **Compilation Warnings:** 105
- **Critical Issues:** 4
- **Blocking Issues:** Yes

The build currently fails due to incomplete migration to Jakarta EE 10 and Hibernate 6. While many JAR dependencies have been updated, critical source code changes remain incomplete.

---

## Critical Issues

### 1. Jakarta Faces Dependency Mismatch

**Severity:** CRITICAL  
**Impact:** Compilation failure for all JSF-based components

**Problem:**
```
build.xml references:
  - jakarta.faces-api-4.0.0.jar (MISSING)
  - jakarta.faces-4.0.0.jar (MISSING)

Actual JARs available:
  - jakarta.faces-api-4.0.1.jar
  - jakarta.faces-4.0.5.jar
```

**Files Affected:**
- `/home/user/yawl/build/build.xml` (lines 298, 310)

**Fix Required:**
Update build.xml properties:
```xml
<property name="jsf-api" value="jakarta.faces-api-4.0.1.jar"/>
<property name="jsf-impl" value="jakarta.faces-4.0.5.jar"/>
```

---

### 2. Hibernate 6 API Migration Incomplete

**Severity:** CRITICAL  
**Impact:** 15+ compilation errors in persistence layer

**Problem:**
Source code still uses deprecated Hibernate 5 APIs that were removed in Hibernate 6:

1. **org.hibernate.Query** → Removed in Hibernate 6
   - Replacement: `jakarta.persistence.Query`
   - Files affected: 7

2. **org.hibernate.tool.hbm2ddl.SchemaUpdate** → Removed in Hibernate 6
   - Replacement: `org.hibernate.tool.schema.spi.SchemaManagementTool`
   - Files affected: 2

3. **org.hibernate.criterion.Criterion** → Removed in Hibernate 6
   - Replacement: JPA Criteria API
   - Files affected: 2

**Files Requiring Migration:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java
/home/user/yawl/src/org/yawlfoundation/yawl/logging/YEventLogger.java
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngineRestorer.java
/home/user/yawl/src/org/yawlfoundation/yawl/logging/YLogServer.java
/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java
/home/user/yawl/src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java
```

**Fix Required:**
Systematic migration of Hibernate 5 → Hibernate 6 APIs in all persistence code.

---

### 3. Missing Bouncy Castle CMS Dependency

**Severity:** HIGH  
**Impact:** Digital signature functionality fails to compile

**Problem:**
```
error: package org.bouncycastle.cms does not exist
```

**Available:**
- bcprov-jdk18on-1.77.jar (Bouncy Castle Provider)
- bcmail-jdk18on-1.77.jar (Bouncy Castle Mail)

**Missing:**
- bcpkix-jdk18on-1.77.jar (Bouncy Castle PKIX/CMS)

**Files Affected:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/digitalSignature/DigitalSignature.java
```

**Fix Required:**
Add bcpkix-jdk18on-1.77.jar to dependencies.

---

### 4. JSF Component Classes Not Found

**Severity:** HIGH  
**Impact:** 60+ compilation errors in Resource Service and Monitor Service

**Problem:**
```
error: cannot access UIComponentBase
error: cannot access UIOutput
error: cannot find symbol: class HtmlDataTable
error: cannot find symbol: class UIColumn
```

**Root Cause:**
Missing Jakarta Faces implementation JAR in compilation classpath, or version mismatch preventing proper resolution.

**Files Affected:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/resourcing/jsf/MessagePanel.java
/home/user/yawl/src/org/yawlfoundation/yawl/resourcing/jsf/dynform/StaticTextBlock.java
/home/user/yawl/src/org/yawlfoundation/yawl/monitor/jsf/ActiveCases.java
/home/user/yawl/src/org/yawlfoundation/yawl/monitor/jsf/SessionBean.java
/home/user/yawl/src/org/yawlfoundation/yawl/monitor/jsf/ApplicationBean.java
/home/user/yawl/src/org/yawlfoundation/yawl/monitor/jsf/CachePhaseListener.java
/home/user/yawl/src/org/yawlfoundation/yawl/monitor/jsf/CaseItems.java
```

**Fix Required:**
1. Update build.xml JAR versions (Issue #1)
2. Verify Jakarta Faces implementation JAR contains required classes
3. May need to add explicit dependency on Jakarta Faces component library

---

## Compilation Statistics

**Source Files:** 996  
**Compilation Time:** 12 seconds  
**Errors:** 100+ displayed (1157 total)  
**Warnings:** 100+ displayed (105 total)

### Error Breakdown

| Category | Count | Severity |
|----------|-------|----------|
| Jakarta Faces missing classes | 60+ | CRITICAL |
| Hibernate API removed | 15+ | CRITICAL |
| Bouncy Castle CMS missing | 5 | HIGH |
| Other dependency issues | 20+ | MEDIUM |

### Warning Breakdown

| Category | Count | Action Needed |
|----------|-------|---------------|
| Deprecated Hibernate API usage | 30+ | Migrate to new API |
| Missing @Deprecated annotation | 30+ | Add annotations |
| Deprecation warnings | 20+ | Review and update |
| Unchecked operations | 10+ | Add generics |
| Other warnings | 15+ | Review |

---

## Dependency Inventory

**Total JARs:** 221  
**Total Size:** ~150 MB (estimated)

### Key Dependencies Status

| Dependency | Required Version | Available Version | Status |
|------------|------------------|-------------------|---------|
| Jakarta Servlet API | 6.0.0 | 6.0.0 | OK |
| Jakarta Faces API | 4.0.1 | 4.0.1 | OK (config WRONG) |
| Jakarta Faces Impl | 4.0.5 | 4.0.5 | OK (config WRONG) |
| Jakarta Persistence | 3.1.0 | 3.1.0 | OK |
| Hibernate Core | 6.4.4 | 5.6.14 + 6.4.4 | BOTH (conflict) |
| Hibernate HikariCP | 6.4.4 | 6.4.4 | OK |
| Hibernate Dialects | 6.4.4 | 6.4.4 | OK |
| PostgreSQL Driver | 42.7.4 | 42.7.4 | OK |
| MySQL Driver | 8.3.0 | 8.3.0 | OK |
| H2 Database | 2.2.224 | 2.2.224 | OK |
| HikariCP | 5.1.0 | 5.1.0 | OK |
| Bouncy Castle Provider | 1.77 | 1.77 | OK |
| Bouncy Castle PKIX/CMS | 1.77 | MISSING | FAIL |
| Log4j2 | 2.23.1 | 2.23.1 | OK |
| JUnit Jupiter | 5.10.2 | 5.10.2 | OK |

### Duplicate Dependencies Detected

| Library | Old Version | New Version | Action |
|---------|-------------|-------------|--------|
| hibernate-core | 5.6.14.Final | 6.4.4.Final | Remove old |
| hibernate-commons-annotations | 5.1.2.Final | 6.0.6.Final | Remove old |
| bcprov | jdk15-139 | jdk18on-1.77 | Remove old |
| bcmail | jdk15-139 | jdk18on-1.77 | Remove old |
| commons-codec | 1.9 | 1.16.1 | Remove old |
| commons-lang3 | 3.6 | 3.14.0 | Remove old |

**Estimated cleanup savings:** ~15-20 MB

---

## HYPER_STANDARDS Compliance

**Status:** NOT SCANNED (build must succeed first)

HYPER_STANDARDS validation requires successful compilation. Once build is fixed, run:
```bash
.claude/hooks/hyper-validate.sh
```

---

## Build Configuration Issues

### build.xml Problems

1. **Line 64:** `hibernate.version` property = "5.6.14.Final" (should be 6.4.4.Final)
2. **Line 298:** `jsf-api` = "jakarta.faces-api-4.0.0.jar" (should be 4.0.1)
3. **Line 310:** `jsf-impl` = "jakarta.faces-4.0.0.jar" (should be 4.0.5)
4. **Missing:** bcpkix-jdk18on-1.77.jar in dependencies

---

## Recommended Action Plan

### Phase 1: Immediate Fixes (Required for Compilation)

1. **Update build.xml JAR versions:**
   ```xml
   <property name="hibernate.version" value="6.4.4.Final"/>
   <property name="jsf-api" value="jakarta.faces-api-4.0.1.jar"/>
   <property name="jsf-impl" value="jakarta.faces-4.0.5.jar"/>
   ```

2. **Add missing Bouncy Castle dependency:**
   - Download bcpkix-jdk18on-1.77.jar
   - Add to build/3rdParty/lib/
   - Add property and classpath entry in build.xml

3. **Migrate Hibernate 5 → 6 APIs:**
   - Replace `org.hibernate.Query` with `jakarta.persistence.Query`
   - Replace `SchemaUpdate` with `SchemaManagementTool`
   - Replace Criteria API usage with JPA Criteria

### Phase 2: Cleanup (Post-Compilation)

4. **Remove duplicate old dependencies:**
   ```bash
   rm build/3rdParty/lib/hibernate-core-5.6.14.Final.jar
   rm build/3rdParty/lib/hibernate-ehcache-5.6.14.Final.jar
   rm build/3rdParty/lib/hibernate-c3p0-5.6.14.Final.jar
   rm build/3rdParty/lib/bcprov-jdk15-139.jar
   rm build/3rdParty/lib/bcmail-jdk15-139.jar
   # ... (see duplicate list above)
   ```

5. **Address deprecation warnings:**
   - Add @Deprecated annotations where missing
   - Review Session.save/delete/update usage
   - Migrate to persist/remove/merge APIs

### Phase 3: Testing & Validation

6. **Run full test suite:**
   ```bash
   ant clean
   ant compile
   ant unitTest
   ```

7. **Run HYPER_STANDARDS validation:**
   ```bash
   .claude/hooks/hyper-validate.sh
   ```

8. **Performance testing:**
   - Verify Hibernate 6 performance
   - Check connection pool behavior
   - Test transaction management

---

## Code Quality Metrics

**NOTE:** Cannot calculate until compilation succeeds.

Pending metrics:
- Total lines of code
- Test coverage percentage
- Cyclomatic complexity
- Technical debt score

---

## Blockers for Production

1. Build must compile without errors
2. All tests must pass
3. HYPER_STANDARDS validation must pass
4. Migration testing required for:
   - Hibernate 6 persistence layer
   - Jakarta Faces web applications
   - Digital signature functionality

---

## Timeline Estimate

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1 | Fix build.xml + add bcpkix | 1 hour |
| Phase 1 | Migrate Hibernate APIs | 4-8 hours |
| Phase 2 | Remove duplicates + warnings | 2 hours |
| Phase 3 | Testing & validation | 4 hours |
| **TOTAL** | **Complete build fix** | **11-15 hours** |

---

## Conclusion

The YAWL build is **NOT production-ready**. Critical migration work remains incomplete despite having updated JAR dependencies. The build.xml configuration errors and incomplete Hibernate 6 API migration must be resolved before proceeding with any deployment.

**Recommended Next Steps:**
1. Fix build.xml JAR version mismatches (30 minutes)
2. Add bcpkix JAR dependency (15 minutes)
3. Systematically migrate Hibernate 5 → 6 APIs (4-8 hours)
4. Clean up duplicate dependencies (30 minutes)
5. Run full test suite and fix failures (2-4 hours)

**Do not attempt deployment until all compilation errors are resolved and tests pass.**

---

## Appendix: Sample Error Output

```
[javac] /home/user/yawl/src/org/yawlfoundation/yawl/resourcing/jsf/MessagePanel.java:43: error: cannot access UIComponentBase
[javac] public class MessagePanel extends PanelLayout {
[javac]                                   ^
[javac]   class file for javax.faces.component.UIComponentBase not found

[javac] /home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java:29: error: package org.hibernate.tool.hbm2ddl does not exist
[javac] import org.hibernate.tool.hbm2ddl.SchemaUpdate;

[javac] /home/user/yawl/src/org/yawlfoundation/yawl/logging/YEventLogger.java:23: error: cannot find symbol
[javac] import org.hibernate.Query;
[javac]                     ^
[javac]   symbol:   class Query
[javac]   location: package org.hibernate
```

---

**Report Generated:** 2026-02-16  
**Build System:** Apache Ant  
**Java Version:** 25  
**YAWL Version:** 5.2
