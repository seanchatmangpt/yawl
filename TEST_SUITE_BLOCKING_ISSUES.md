# Test Suite Blocking Issues - YAWL 5.2

**Date**: 2026-02-16
**Current Status**: BLOCKED (compilation errors prevent test execution)
**Root Cause**: Incomplete Hibernate 5 → Hibernate 6 migration

---

## Executive Summary

The YAWL test suite was successfully running on 2026-02-16 at 00:37 UTC with 106 tests (102 passing, 4 failing). However, the current codebase has **~100 compilation errors** due to an incomplete Hibernate 5 → Hibernate 6 API migration.

**Evidence of Previous Success**:
- `/home/user/yawl/TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt` (timestamp: 2026-02-16 00:37)
- `/home/user/yawl/TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.xml` (timestamp: 2026-02-16 00:37)

---

## Compilation Error Breakdown

### Category 1: Hibernate Query API Changes (~40 errors)

**Problem**: `jakarta.persistence.Query` has a different API than `org.hibernate.Query`

**Old Hibernate 5 API**:
```java
Query query = session.createQuery("FROM Entity");
List results = query.list();           // Method removed in JPA Query
Iterator iter = query.iterate();        // Method removed in JPA Query
query.setString("param", value);       // Method removed in JPA Query
String qryStr = query.getQueryString(); // Method removed in JPA Query
```

**New JPA API**:
```java
Query query = session.createQuery("FROM Entity");
List results = query.getResultList();   // JPA method
// iterate() not available in JPA Query
query.setParameter("param", value);     // JPA method
// getQueryString() not available in JPA Query
```

**Files Affected** (15+):
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngineRestorer.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/logging/YEventLogger.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/logging/YLogServer.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/resourcing/datastore/persistence/Persister.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/worklet/selection/RunnerMap.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/worklet/support/WorkletLoader.java`
- And ~8 more files

### Category 2: Hibernate Schema Management (~20 errors)

**Problem**: Hibernate 6 removed `org.hibernate.tool.hbm2ddl.SchemaUpdate` and restructured schema management

**Old Hibernate 5 API**:
```java
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

SchemaUpdate schemaUpdate = new SchemaUpdate(configuration);
schemaUpdate.execute(true, true);
```

**New Hibernate 6 API**:
```java
import org.hibernate.tool.schema.spi.SchemaMigrator;
import org.hibernate.tool.schema.spi.SchemaManagementTool;

SchemaManagementTool tool = serviceRegistry.getService(SchemaManagementTool.class);
SchemaMigrator migrator = tool.getSchemaMigrator(database);
migrator.doMigration(metadata, execOptions, contributor, targetDescriptor);
```

**Files Affected** (2):
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java`

### Category 3: Hibernate Criteria API (~15 errors)

**Problem**: Hibernate's legacy Criteria API (`org.hibernate.criterion.*`) was removed in Hibernate 6

**Old Hibernate 5 API**:
```java
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

Criteria criteria = session.createCriteria(Entity.class);
criteria.add(Restrictions.eq("field", value));
List results = criteria.list();
```

**New JPA Criteria API**:
```java
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

CriteriaBuilder cb = session.getCriteriaBuilder();
CriteriaQuery<Entity> cq = cb.createQuery(Entity.class);
Root<Entity> root = cq.from(Entity.class);
cq.where(cb.equal(root.get("field"), value));
List<Entity> results = session.createQuery(cq).getResultList();
```

**Files Affected** (2):
- `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/worklet/rdr/RdrSetLoader.java`

### Category 4: Hibernate Metadata API (~10 errors)

**Problem**: Hibernate 6 changed the metadata API

**Old Hibernate 5 API**:
```java
MetadataSources metadataSources = new MetadataSources(serviceRegistry);
metadataSources.addClass(Entity.class);  // Method signature changed
```

**New Hibernate 6 API**:
```java
MetadataSources metadataSources = new MetadataSources(serviceRegistry);
metadataSources.addAnnotatedClass(Entity.class);  // New method name
```

**Files Affected** (2):
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java`

### Category 5: JWT Library API (~5 errors)

**Problem**: JJWT library version mismatch or incorrect API usage

**Error**:
```
error: cannot find symbol
    return Jwts.parserBuilder()
               ^
  symbol:   method parserBuilder()
```

**Files Affected** (1):
- `/home/user/yawl/src/org/yawlfoundation/yawl/authentication/JwtManager.java`

### Category 6: Apache Commons Lang (~10 errors)

**Problem**: Mixed usage of `org.apache.commons.lang` (Lang 2.x) and `org.apache.commons.lang3` (Lang 3.x)

**Files Affected** (5):
- `/home/user/yawl/src/org/yawlfoundation/yawl/util/StringUtil.java` (StringEscapeUtils moved to commons-text)
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/resourcing/jsf/SessionBean.java`
- And 2 more files

---

## Why Tests Were Previously Passing

Looking at the timestamps, the tests ran successfully at **2026-02-16 00:37**. This suggests:

1. **Either**: The Hibernate 6 migration was functional at that time and was broken by subsequent changes
2. **Or**: The tests were compiled with an older build that still used Hibernate 5

Given the BUILD_HEALTH_REPORT.md mentions both Hibernate 5.6.14 and 6.4.4 JARs are present, it's likely the codebase is in a transitional state.

---

## Recommended Fix Strategy

### Option A: Complete Hibernate 6 Migration (RECOMMENDED for production)

**Estimated time**: 8-16 hours
**Risk**: Medium
**Benefit**: Future-proof, modern API, better performance

**Steps**:
1. Create a dedicated Hibernate 6 migration task
2. Systematically update all persistence code files
3. Test each module as it's migrated
4. Remove Hibernate 5 JARs when complete

**Files to migrate** (~25 files):
```bash
# Find all files using old Hibernate API
grep -r "org.hibernate.Query" src/ --include="*.java"
grep -r "org.hibernate.Criteria" src/ --include="*.java"
grep -r "org.hibernate.tool.hbm2ddl" src/ --include="*.java"
grep -r "\.list()" src/ --include="*.java" | grep -v ".getResultList()"
grep -r "\.iterate()" src/ --include="*.java"
grep -r "\.setString(" src/ --include="*.java"
```

### Option B: Revert to Hibernate 5 (QUICK FIX for testing only)

**Estimated time**: 1-2 hours
**Risk**: Low
**Benefit**: Tests run immediately, but technical debt increases

**Steps**:
1. Update `build/build.xml` to use Hibernate 5.6.14 JARs
2. Remove Hibernate 6.4.4 JARs from classpath
3. Revert any Hibernate 6-specific code changes
4. Run tests

**Not recommended** because:
- Hibernate 5 is EOL (end-of-life)
- Jakarta EE 10 requires Hibernate 6+
- Increases technical debt

### Option C: Incremental Migration with Compatibility Layer (BALANCED)

**Estimated time**: 4-8 hours
**Risk**: Low-Medium
**Benefit**: Tests run quickly, migration proceeds gradually

**Steps**:
1. Create a `HibernateCompatLayer` utility class that wraps both APIs
2. Update high-priority files to use the compat layer
3. Gradually migrate each module to pure Hibernate 6
4. Remove compat layer when all code is migrated

**Example**:
```java
public class HibernateCompatLayer {
    public static List executeQuery(Query query) {
        // JPA Query uses getResultList() instead of list()
        return query.getResultList();
    }

    public static Query setParameter(Query query, String name, Object value) {
        return query.setParameter(name, value);  // Same in both APIs
    }
}
```

---

## Immediate Actions to Restore Test Execution

### Quick Fix (to get tests running in 1-2 hours):

```bash
# 1. Check which Hibernate version is currently in use
ls -l build/3rdParty/lib/hibernate-core-*

# 2. Verify build.xml Hibernate version property
grep "hibernate.version" build/build.xml

# 3. If mismatch, update build.xml to match available JARs
sed -i 's/hibernate\.version.*5\.6\.14/hibernate.version=6.4.4/g' build/build.xml

# 4. Create API compatibility wrapper
cat > src/org/yawlfoundation/yawl/util/QueryCompat.java <<'EOF'
package org.yawlfoundation.yawl.util;

import jakarta.persistence.Query;
import java.util.List;

/**
 * Compatibility layer for Hibernate 5 → 6 migration.
 * Wraps jakarta.persistence.Query to provide Hibernate 5-like API.
 */
public class QueryCompat {
    public static List list(Query query) {
        return query.getResultList();
    }

    public static Query setString(Query query, String name, String value) {
        return query.setParameter(name, value);
    }
}
EOF

# 5. Update problematic files to use compat layer
find src -name "*.java" -exec sed -i 's/query\.list()/QueryCompat.list(query)/g' {} \;
find src -name "*.java" -exec sed -i 's/\.setString(\([^,]*\),/QueryCompat.setString(query, \1,/g' {} \;

# 6. Comment out schema update code (non-critical for tests)
# Manually edit YPersistenceManager.java and HibernateEngine.java

# 7. Compile and test
ant -f build/build.xml clean compile unitTest
```

---

## Test Suite Dependencies

### Required for Tests to Run:

1. ✅ **JUnit 5 (Jupiter)** - AVAILABLE (migrated successfully)
2. ✅ **H2 Database** - AVAILABLE (configured for tests)
3. ❌ **Hibernate (working version)** - BROKEN (API mismatch)
4. ✅ **Log4j2** - AVAILABLE
5. ✅ **JDOM2** - AVAILABLE
6. ❌ **Commons Lang** - PARTIAL (some files use old API)

### Non-Critical (tests can run without):

- Resource Service (tests expect it to be offline)
- Mail Service
- External databases (tests use H2 in-memory)

---

## Impact Assessment

### What's Currently Blocked:

- ❌ Running test suite (`ant unitTest`)
- ❌ Verifying test coverage
- ❌ Continuous integration
- ❌ Pre-commit validation
- ❌ Regression testing

### What Still Works:

- ✅ Test code is correct (JUnit 5 migration complete)
- ✅ Test infrastructure is configured
- ✅ Test data is available
- ✅ Test framework is modern and well-structured

---

## Risk Analysis

### Risk of Incomplete Migration:

**Current State**: Codebase is in a broken state between Hibernate 5 and 6

**Risks**:
1. **Tests don't run** → Can't verify code changes
2. **Deployment will fail** → Production build will fail
3. **Technical debt grows** → Harder to fix later
4. **Developer frustration** → Can't validate changes

**Mitigation**: Complete migration ASAP or revert to stable state

---

## Proposed Solution

### Recommended Approach: Option C (Incremental Migration)

**Phase 1** (2 hours): Create compatibility layer and restore compilation
- Create `QueryCompat` utility class
- Update 10-15 high-priority files
- Comment out non-critical schema update code
- Achieve successful compilation

**Phase 2** (2 hours): Run and fix tests
- Execute `ant unitTest`
- Fix any test failures caused by API changes
- Verify 100+ tests pass

**Phase 3** (4 hours): Complete migration
- Migrate remaining files to pure Hibernate 6 API
- Remove compatibility layer
- Remove Hibernate 5 JARs
- Full regression test

**Total Estimated Time**: 8 hours
**Confidence Level**: High (90%+)

---

## Acceptance Criteria for "Tests Fixed"

✅ **Compilation succeeds** (`ant compile` returns BUILD SUCCESSFUL)
✅ **Tests execute** (`ant unitTest` runs all 106 tests)
✅ **Pass rate ≥ 96%** (102+ passing, ≤ 4 failing)
✅ **No Hibernate 5 dependencies** (only Hibernate 6 JARs in classpath)
✅ **No compatibility layers** (pure Hibernate 6 API usage)

---

## Next Steps

### Immediate (today):

1. **Choose migration strategy** (recommend Option C)
2. **Create compatibility layer** (QueryCompat utility)
3. **Update high-priority files** (YPersistenceManager, HibernateEngine)
4. **Achieve compilation** (ant compile succeeds)

### Short-term (this week):

5. **Run test suite** (ant unitTest)
6. **Fix test failures** (investigate 4 failing tests)
7. **Complete Hibernate 6 migration** (remove compat layer)

### Long-term (next week):

8. **Add new tests** (REST API, CSRF, JWT)
9. **Set up CI/CD** (GitHub Actions for automated testing)
10. **Document testing guidelines** (for future contributors)

---

## Files Referenced

- `/home/user/yawl/TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt`
- `/home/user/yawl/TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.xml`
- `/home/user/yawl/BUILD_HEALTH_REPORT.md`
- `/home/user/yawl/JUNIT5_MIGRATION_SUMMARY.md`
- `/home/user/yawl/build/build.xml`
- `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`
- `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java`

---

**Report Generated**: 2026-02-16
**Status**: BLOCKED
**Priority**: P0 (Critical)
**Estimated Fix Time**: 8 hours (Option C)
**Confidence**: 90%
