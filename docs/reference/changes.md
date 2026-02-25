# ORM Migration Changes - Hibernate 5.6.14 → 6.5.1

**Date:** 2026-02-15
**YAWL Version:** 5.2

## Files Modified

### 1. Dependencies

#### `/pom.xml`
**Changes:**
- Added `hibernate.version` property: `6.5.1.Final`
- Added `jakarta.persistence.version` property: `3.0.0`
- Added `hikaricp.version` property: `5.1.0`
- Added `org.hibernate.orm:hibernate-core:6.5.1.Final`
- Added `org.hibernate.orm:hibernate-hikaricp:6.5.1.Final`
- Added `org.hibernate.orm:hibernate-jcache:6.5.1.Final`
- Added `jakarta.persistence:jakarta.persistence-api:3.0.0`
- Added `com.zaxxer:HikariCP:5.1.0`
- Removed Spring Boot starter-data-jpa (conflicts with direct Hibernate)

**Location:** `/home/user/yawl/pom.xml`

#### `/build/build.xml`
**Changes:**
- `c3p0` → `hikaricp` (JAR: c3p0-0.9.2.1.jar → HikariCP-5.1.0.jar)
- `hibernate-c3p0` → `hibernate-hikaricp` (5.6.14 → 6.5.1)
- `hibernate-core`: 5.6.14.Final → 6.5.1.Final
- `hibernate-commons`: 5.1.2.Final → 6.0.6.Final
- `hibernate-ehcache` → `hibernate-jcache` (6.5.1)
- `hibernate-jpa` → `jakarta-persistence` (2.1 → 3.0.0)
- `jandex`: 2.4.2.Final → 3.1.7
- `jboss-logging`: 3.4.3.Final → 3.5.3.Final
- `byte-buddy`: 1.12.23 → 1.14.12
- Removed `jboss-transaction-api_1.2_spec`
- Removed `javax-persistence`

**Location:** `/home/user/yawl/build/build.xml`

---

### 2. Configuration Files

#### `/build/properties/hibernate.cfg.xml`
**Changes:**
```xml
<!-- OLD -->
<!DOCTYPE hibernate-configuration
PUBLIC "-//Hibernate/Hibernate Configuration DTD//EN"
"http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">

<!-- NEW -->
<!DOCTYPE hibernate-configuration
PUBLIC "-//Hibernate/Hibernate Configuration DTD 6.0//EN"
"http://www.hibernate.org/dtd/hibernate-configuration-6.0.dtd">
```
- Updated DTD from 3.0 to 6.0
- Added `hibernate.` prefix to property names

**Location:** `/home/user/yawl/build/properties/hibernate.cfg.xml`

#### `/build/properties/hibernate.properties`
**Changes:**
- Connection pool provider:
  ```properties
  # OLD
  hibernate.connection.provider_class=org.hibernate.connection.C3P0ConnectionProvider
  
  # NEW
  hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider
  ```

- Removed c3p0 properties:
  ```properties
  hibernate.c3p0.max_size
  hibernate.c3p0.min_size
  hibernate.c3p0.timeout
  hibernate.c3p0.max_statements
  hibernate.c3p0.idle_test_period
  hibernate.c3p0.acquire_increment
  ```

- Added HikariCP properties:
  ```properties
  hibernate.hikari.maximumPoolSize=20
  hibernate.hikari.minimumIdle=5
  hibernate.hikari.connectionTimeout=30000
  hibernate.hikari.idleTimeout=600000
  hibernate.hikari.maxLifetime=1800000
  hibernate.hikari.validationTimeout=5000
  hibernate.hikari.keepaliveTime=120000
  hibernate.hikari.leakDetectionThreshold=60000
  hibernate.hikari.registerMbeans=true
  ```

- Cache provider:
  ```properties
  # OLD
  hibernate.cache.region.factory_class=org.hibernate.cache.ehcache.EhCacheRegionFactory
  
  # NEW
  hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
  ```

**Location:** `/home/user/yawl/build/properties/hibernate.properties`

---

### 3. Java Source Files

#### `/src/org/yawlfoundation/yawl/util/HibernateEngine.java`

**Import Changes:**
```java
// REMOVED
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.criterion.Criterion;

// ADDED
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
```

**Code Changes:**

1. **SchemaUpdate replacement (line 100-107):**
```java
// OLD
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
new SchemaUpdate().execute(targetTypes, metadata);

// NEW
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
SchemaManagementTool schemaManagementTool = standardRegistry
        .getService(SchemaManagementTool.class);
schemaManagementTool.getSchemaUpdater(null)
        .doUpdate(metadata, targetTypes, false);
```

2. **Added automatic c3p0 → HikariCP migration (lines 106-160):**
```java
private void ensureHikariCPProvider(Properties props) {
    String provider = props.getProperty("hibernate.connection.provider_class");

    if (provider != null && provider.contains("C3P0")) {
        _log.info("Migrating from c3p0 to HikariCP connection provider");
        props.setProperty("hibernate.connection.provider_class",
                "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");

        migrateC3P0PropertiesToHikariCP(props);
    } else if (provider == null) {
        props.setProperty("hibernate.connection.provider_class",
                "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");
    }
}

private void migrateC3P0PropertiesToHikariCP(Properties props) {
    // Property migration logic
}
```

3. **Replaced Hibernate Criteria with JPA Criteria (lines 404-430):**
```java
// OLD
public List getByCriteria(Class claz, Criterion... criteria) {
    return getByCriteria(claz, true, criteria);
}

public List getByCriteria(Class claz, boolean commit, Criterion... criteria) {
    getOrBeginTransaction();
    Criteria c = getSession().createCriteria(claz);
    for (Criterion criterion : criteria) {
        c.add(criterion);
    }
    List result = c.list();
    if (commit) commit();
    return result;
}

// NEW
@Deprecated
public List getByCriteria(Class claz, Predicate... predicates) {
    return getByCriteriaJPA(claz, true, predicates);
}

public List getByCriteriaJPA(Class claz, boolean commit, Predicate... predicates) {
    getOrBeginTransaction();
    CriteriaBuilder cb = getSession().getCriteriaBuilder();
    CriteriaQuery cq = cb.createQuery(claz);
    Root root = cq.from(claz);

    if (predicates != null && predicates.length > 0) {
        cq.where(predicates);
    }

    List result = getSession().createQuery(cq).getResultList();
    if (commit) commit();
    return result;
}
```

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/util/HibernateEngine.java`

---

#### `/src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java`

**Import Changes:**
```java
// REMOVED
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

// ADDED
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.query.NativeQuery;
```

**Code Changes:**

1. **SchemaUpdate replacement (line 142-149):**
```java
// OLD
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
new SchemaUpdate().execute(targetTypes, metadata);

// NEW
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
SchemaManagementTool schemaManagementTool = standardRegistry
        .getService(SchemaManagementTool.class);
schemaManagementTool.getSchemaUpdater(null)
        .doUpdate(metadata, targetTypes, false);
```

2. **SQLQuery → NativeQuery (line 156-163):**
```java
// OLD
SQLQuery query = session.createSQLQuery(queryString);
if (query != null) result = query.list();

// NEW
NativeQuery query = session.createNativeQuery(queryString);
if (query != null) result = query.getResultList();
```

3. **Query.setString() → Query.setParameter() (line 189-192):**
```java
// OLD
return session.getNamedQuery(namedQuery).setString("key", key).list();

// NEW
return session.getNamedQuery(namedQuery).setParameter("key", key).getResultList();
```

4. **Cache provider update (line 111-112):**
```java
// OLD
props.setProperty("hibernate.cache.region.factory_class",
                  "org.hibernate.cache.ehcache.EhCacheRegionFactory");

// NEW
props.setProperty("hibernate.cache.region.factory_class",
                  "org.hibernate.cache.jcache.JCacheRegionFactory");
```

5. **Added HikariCP configuration (lines 114-123):**
```java
props.setProperty("hibernate.connection.provider_class",
                  "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");
props.setProperty("hibernate.hikari.maximumPoolSize", "20");
props.setProperty("hibernate.hikari.minimumIdle", "5");
props.setProperty("hibernate.hikari.connectionTimeout", "30000");
props.setProperty("hibernate.hikari.idleTimeout", "600000");
props.setProperty("hibernate.hikari.maxLifetime", "1800000");
props.setProperty("hibernate.hikari.keepaliveTime", "120000");
props.setProperty("hibernate.hikari.leakDetectionThreshold", "60000");
```

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java`

---

#### `/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`

**Import Changes:**
```java
// REMOVED
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

// ADDED
import org.hibernate.tool.schema.spi.SchemaManagementTool;
```

**Code Changes:**

1. **SchemaUpdate replacement (line 101-108):**
```java
// OLD
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
new SchemaUpdate().execute(targetTypes, metadata);

// NEW
EnumSet<TargetType> targetTypes = EnumSet.of(TargetType.DATABASE);
SchemaManagementTool schemaManagementTool = standardRegistry
        .getService(SchemaManagementTool.class);
schemaManagementTool.getSchemaUpdater(null)
        .doUpdate(metadata, targetTypes, false);
```

2. **Fixed getStatistics() method (line 492-505):**
```java
// OLD
public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new HashMap<>();
    if (factory != null && statisticsEnabled) {
        // ...
    }
    return stats;
}

// NEW
public Map<String, Object> getStatisticsMap() {
    Map<String, Object> stats = new HashMap<>();
    if (factory != null && isStatisticsEnabled()) {
        // ...
    }
    return stats;
}
```

**Location:** `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`

---

### 4. Documentation Files (NEW)

#### `/docs/ORM_MIGRATION_GUIDE.md`
Comprehensive 500+ line migration guide with:
- Breaking changes documentation
- API migration patterns
- Configuration updates
- Testing procedures
- Rollback plan

**Location:** `/home/user/yawl/docs/ORM_MIGRATION_GUIDE.md`

#### `/docs/MIGRATION_SUMMARY.md`
Executive summary with:
- Performance improvements
- Deployment steps
- Monitoring guidelines
- Known issues

**Location:** `/home/user/yawl/docs/MIGRATION_SUMMARY.md`

#### `/docs/MIGRATION_CHECKLIST.md`
Detailed checklist covering:
- All migration phases
- Testing requirements
- Deployment procedures
- Sign-off requirements

**Location:** `/home/user/yawl/docs/MIGRATION_CHECKLIST.md`

---

### 5. Test Files (NEW)

#### `/test/org/yawlfoundation/yawl/engine/Hibernate6MigrationTest.java`
Comprehensive test suite with 20 test cases:
1. Hibernate version verification
2. Jakarta Persistence API verification
3. javax.persistence removal verification
4. HikariCP connection pool test
5. JPA Criteria API test
6. Query.getResultList() test
7. NativeQuery test
8. Query.setParameter() test
9. SchemaManagementTool test
10. c3p0 to HikariCP migration test
11. JCache integration test
12. Transaction management test
13. Hibernate statistics test
14. Session management test
15. Jakarta entity persistence test
16. HikariCP performance test
17. Backward compatibility test
18. Connection pool health test
19. Error handling and rollback test
20. Migration completeness verification

**Location:** `/home/user/yawl/test/org/yawlfoundation/yawl/engine/Hibernate6MigrationTest.java`

---

## Summary Statistics

**Files Modified:** 7
- pom.xml
- build/build.xml
- build/properties/hibernate.cfg.xml
- build/properties/hibernate.properties
- src/org/yawlfoundation/yawl/util/HibernateEngine.java
- src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java
- src/org/yawlfoundation/yawl/engine/YPersistenceManager.java

**Files Created:** 4
- docs/ORM_MIGRATION_GUIDE.md
- docs/MIGRATION_SUMMARY.md
- docs/MIGRATION_CHECKLIST.md
- test/org/yawlfoundation/yawl/engine/Hibernate6MigrationTest.java

**Lines of Code Changed:** ~500 (Java) + ~100 (Configuration)
**Lines of Documentation:** ~2000
**Test Cases:** 20

---

## Verification

To verify the migration:

```bash
# 1. Check Hibernate version
grep "hibernate.version" /home/user/yawl/pom.xml

# 2. Check Java files for jakarta imports
grep -r "import jakarta.persistence" /home/user/yawl/src/

# 3. Check no javax.persistence remains
grep -r "import javax.persistence" /home/user/yawl/src/ || echo "Clean - no javax.persistence found"

# 4. Check HikariCP configuration
grep "HikariCP" /home/user/yawl/build/properties/hibernate.properties

# 5. Run tests
mvn test -Dtest=Hibernate6MigrationTest
```

---

**Migration Date:** 2026-02-15
**Status:** Complete - Code Ready for Testing
