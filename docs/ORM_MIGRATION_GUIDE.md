# YAWL ORM Migration Guide: Hibernate 5.6.14 → 6.5.1

**Migration Date:** 2026-02-15
**YAWL Version:** 5.2
**Migration Type:** Major ORM Upgrade with javax → jakarta API Transition

## Executive Summary

This document details the complete migration from Hibernate 5.6.14.Final with javax.persistence API to Hibernate 6.5.1.Final with Jakarta Persistence API 3.0, including the replacement of c3p0 connection pooling with HikariCP 5.1.0.

## Table of Contents

1. [Breaking Changes](#breaking-changes)
2. [Dependency Updates](#dependency-updates)
3. [API Migration (javax → jakarta)](#api-migration)
4. [Connection Pool Migration (c3p0 → HikariCP)](#connection-pool-migration)
5. [Hibernate API Changes](#hibernate-api-changes)
6. [Configuration Updates](#configuration-updates)
7. [Testing](#testing)
8. [Rollback Plan](#rollback-plan)

---

## Breaking Changes

### 1. Package Namespace Changes

All `javax.persistence.*` imports must be changed to `jakarta.persistence.*`:

**Before (Hibernate 5.6.14 with javax):**
```java
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
```

**After (Hibernate 6.5.1 with jakarta):**
```java
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
```

### 2. Deprecated API Removals

#### Hibernate Criteria API → JPA Criteria API

**Before:**
```java
import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;

Criteria c = session.createCriteria(MyEntity.class);
c.add(Restrictions.eq("name", "value"));
List result = c.list();
```

**After:**
```java
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

CriteriaBuilder cb = session.getCriteriaBuilder();
CriteriaQuery<MyEntity> cq = cb.createQuery(MyEntity.class);
Root<MyEntity> root = cq.from(MyEntity.class);
cq.where(cb.equal(root.get("name"), "value"));
List<MyEntity> result = session.createQuery(cq).getResultList();
```

#### Query.list() → Query.getResultList()

**Before:**
```java
Query query = session.createQuery("from MyEntity");
List result = query.list();
```

**After:**
```java
Query<MyEntity> query = session.createQuery("from MyEntity", MyEntity.class);
List<MyEntity> result = query.getResultList();
```

#### SQLQuery → NativeQuery

**Before:**
```java
SQLQuery query = session.createSQLQuery("SELECT * FROM my_table");
List result = query.list();
```

**After:**
```java
NativeQuery<Object[]> query = session.createNativeQuery("SELECT * FROM my_table", Object[].class);
List<Object[]> result = query.getResultList();
```

#### Query.setString() → Query.setParameter()

**Before:**
```java
session.getNamedQuery("findByName").setString("name", "John").list();
```

**After:**
```java
session.getNamedQuery("findByName").setParameter("name", "John").getResultList();
```

### 3. SchemaUpdate Removal

**Before:**
```java
import org.hibernate.tool.hbm2ddl.SchemaUpdate;

new SchemaUpdate().execute(targetTypes, metadata);
```

**After:**
```java
import org.hibernate.tool.schema.spi.SchemaManagementTool;

SchemaManagementTool schemaManagementTool = standardRegistry
        .getService(SchemaManagementTool.class);
schemaManagementTool.getSchemaUpdater(null)
        .doUpdate(metadata, targetTypes, false);
```

---

## Dependency Updates

### Maven (pom.xml)

```xml
<properties>
    <!-- Hibernate 6.5.1 -->
    <hibernate.version>6.5.1.Final</hibernate.version>
    <jakarta.persistence.version>3.0.0</jakarta.persistence.version>
    <hikaricp.version>5.1.0</hikaricp.version>
</properties>

<dependencies>
    <!-- Hibernate ORM 6.5.1 -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
        <version>${hibernate.version}</version>
    </dependency>

    <!-- HikariCP Connection Pool (replaces c3p0) -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-hikaricp</artifactId>
        <version>${hibernate.version}</version>
    </dependency>

    <!-- JCache Support (replaces EhCache direct integration) -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-jcache</artifactId>
        <version>${hibernate.version}</version>
    </dependency>

    <!-- Jakarta Persistence API 3.0 -->
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
        <version>${jakarta.persistence.version}</version>
    </dependency>

    <!-- HikariCP -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>${hikaricp.version}</version>
    </dependency>
</dependencies>
```

### Ant (build.xml)

Replace old JARs with new ones:

```xml
<!-- OLD (Hibernate 5.6.14 with c3p0) -->
<property name="c3p0" value="c3p0-0.9.2.1.jar"/>
<property name="hibernate-c3p0" value="hibernate-c3p0-5.6.14.Final.jar"/>
<property name="hibernate-core" value="hibernate-core-5.6.14.Final.jar"/>
<property name="hibernate-commons" value="hibernate-commons-annotations-5.1.2.Final.jar"/>
<property name="hibernate-ehcache" value="hibernate-ehcache-5.6.14.Final.jar"/>
<property name="hibernate-jpa" value="hibernate-jpa-2.1-api-1.0.0.Final.jar"/>
<property name="jandex" value="jandex-2.4.2.Final.jar"/>
<property name="jboss-logging" value="jboss-logging-3.4.3.Final.jar"/>
<property name="jboss-transaction" value="jboss-transaction-api_1.2_spec-1.1.1.Final.jar"/>
<property name="byte-buddy" value="byte-buddy-1.12.23.jar"/>
<property name="javax-persistence" value="javax.persistence-api-2.2.jar"/>

<!-- NEW (Hibernate 6.5.1 with HikariCP) -->
<property name="hikaricp" value="HikariCP-5.1.0.jar"/>
<property name="hibernate-hikaricp" value="hibernate-hikaricp-6.5.1.Final.jar"/>
<property name="hibernate-core" value="hibernate-core-6.5.1.Final.jar"/>
<property name="hibernate-commons" value="hibernate-commons-annotations-6.0.6.Final.jar"/>
<property name="hibernate-jcache" value="hibernate-jcache-6.5.1.Final.jar"/>
<property name="jakarta-persistence" value="jakarta.persistence-api-3.0.0.jar"/>
<property name="jandex" value="jandex-3.1.7.jar"/>
<property name="jboss-logging" value="jboss-logging-3.5.3.Final.jar"/>
<property name="byte-buddy" value="byte-buddy-1.14.12.jar"/>
```

---

## API Migration

### Affected Files in YAWL

The following files have been updated for javax → jakarta migration:

1. **Core Hibernate Engine Classes:**
   - `/src/org/yawlfoundation/yawl/util/HibernateEngine.java`
   - `/src/org/yawlfoundation/yawl/elements/data/external/HibernateEngine.java`
   - `/src/org/yawlfoundation/yawl/engine/YPersistenceManager.java`

2. **Entity Mapping Files (73 files):**
   - All `*.hbm.xml` files under `/src/**/*.hbm.xml`
   - DTD version updated from 3.0 to 6.0

### Entity Annotation Migration Pattern

If you have entity classes using annotations (not common in YAWL 5.2 as it uses XML mappings), update them as follows:

**Before:**
```java
import javax.persistence.*;

@Entity
@Table(name = "my_table")
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private Set<ChildEntity> children;
}
```

**After:**
```java
import jakarta.persistence.*;

@Entity
@Table(name = "my_table")
public class MyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private Set<ChildEntity> children;
}
```

---

## Connection Pool Migration

### c3p0 → HikariCP

HikariCP is the modern, high-performance JDBC connection pool recommended by Hibernate 6.

#### Configuration Mapping

| c3p0 Property | HikariCP Equivalent | Default Value |
|--------------|---------------------|---------------|
| `hibernate.c3p0.max_size` | `hibernate.hikari.maximumPoolSize` | 20 |
| `hibernate.c3p0.min_size` | `hibernate.hikari.minimumIdle` | 5 |
| `hibernate.c3p0.timeout` | `hibernate.hikari.connectionTimeout` | 30000 (ms) |
| `hibernate.c3p0.idle_test_period` | `hibernate.hikari.keepaliveTime` | 120000 (ms) |
| `hibernate.c3p0.max_statements` | N/A (HikariCP doesn't cache statements) | - |
| `hibernate.c3p0.acquire_increment` | N/A (HikariCP uses dynamic sizing) | - |

#### hibernate.properties Configuration

**OLD (c3p0):**
```properties
hibernate.connection.provider_class=org.hibernate.connection.C3P0ConnectionProvider
hibernate.c3p0.max_size=20
hibernate.c3p0.min_size=2
hibernate.c3p0.timeout=5000
hibernate.c3p0.max_statements=100
hibernate.c3p0.idle_test_period=3000
hibernate.c3p0.acquire_increment=1
```

**NEW (HikariCP):**
```properties
hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider

# Maximum pool size - optimized for virtual threads
hibernate.hikari.maximumPoolSize=20

# Minimum idle connections
hibernate.hikari.minimumIdle=5

# Connection timeout (milliseconds)
hibernate.hikari.connectionTimeout=30000

# Idle timeout (milliseconds) - 10 minutes
hibernate.hikari.idleTimeout=600000

# Max lifetime (milliseconds) - 30 minutes
hibernate.hikari.maxLifetime=1800000

# Validation timeout (milliseconds)
hibernate.hikari.validationTimeout=5000

# Keepalive time (milliseconds) - 2 minutes
hibernate.hikari.keepaliveTime=120000

# Leak detection threshold (milliseconds) - 1 minute
hibernate.hikari.leakDetectionThreshold=60000

# Register JMX MBeans for monitoring
hibernate.hikari.registerMbeans=true
```

#### Automatic Migration

YAWL's `HibernateEngine` now includes automatic c3p0 → HikariCP property migration:

```java
private void ensureHikariCPProvider(Properties props) {
    String provider = props.getProperty("hibernate.connection.provider_class");

    if (provider != null && provider.contains("C3P0")) {
        _log.info("Migrating from c3p0 to HikariCP connection provider");
        props.setProperty("hibernate.connection.provider_class",
                "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");

        migrateC3P0PropertiesToHikariCP(props);
    }
}
```

---

## Hibernate API Changes

### 1. Configuration DTD Update

**hibernate.cfg.xml:**

**Before:**
```xml
<!DOCTYPE hibernate-configuration
PUBLIC "-//Hibernate/Hibernate Configuration DTD//EN"
"http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd">
```

**After:**
```xml
<!DOCTYPE hibernate-configuration
PUBLIC "-//Hibernate/Hibernate Configuration DTD 6.0//EN"
"http://www.hibernate.org/dtd/hibernate-configuration-6.0.dtd">
```

### 2. Mapping DTD Update

**entity.hbm.xml:**

**Before:**
```xml
<!DOCTYPE hibernate-mapping
PUBLIC "-//Hibernate/Hibernate Mapping DTD//EN"
"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
```

**After:**
```xml
<!DOCTYPE hibernate-mapping
PUBLIC "-//Hibernate/Hibernate Mapping DTD 6.0//EN"
"http://www.hibernate.org/dtd/hibernate-mapping-6.0.dtd">
```

### 3. Cache Provider Changes

**Before (EhCache):**
```properties
hibernate.cache.region.factory_class=org.hibernate.cache.ehcache.EhCacheRegionFactory
```

**After (JCache):**
```properties
hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
```

### 4. Database Dialect Updates

Hibernate 6 uses the same dialect classes, but with improved support:

```properties
# PostgreSQL - unchanged
hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# MySQL 8+ - unchanged
hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# H2 - unchanged
hibernate.dialect=org.hibernate.dialect.H2Dialect

# Oracle - unchanged
hibernate.dialect=org.hibernate.dialect.OracleDialect
```

---

## Configuration Updates

### Files Modified

1. **pom.xml** - Maven dependencies updated
2. **build.xml** - Ant JAR properties updated
3. **hibernate.cfg.xml** - DTD version updated to 6.0
4. **hibernate.properties** - Connection pool configuration migrated
5. **HibernateEngine.java** (util) - API updates, HikariCP migration
6. **HibernateEngine.java** (elements) - API updates
7. **YPersistenceManager.java** - SchemaUpdate API updated

### Virtual Thread Optimization

HikariCP is optimized for Project Loom virtual threads (Java 21):

```properties
# Larger pool size recommended for virtual threads
hibernate.hikari.maximumPoolSize=20

# Lower minimum idle for better resource usage
hibernate.hikari.minimumIdle=5

# Enable connection leak detection
hibernate.hikari.leakDetectionThreshold=60000
```

---

## Testing

### Unit Tests

All Hibernate-related tests must be updated:

1. **Database Connection Tests:**
   ```java
   @Test
   public void testHikariCPConnection() {
       SessionFactory factory = hibernateEngine.getFactory();
       assertNotNull(factory);
       assertTrue(factory.isOpen());
   }
   ```

2. **CRUD Operation Tests:**
   ```java
   @Test
   public void testEntityPersistence() {
       Session session = factory.getCurrentSession();
       session.beginTransaction();

       MyEntity entity = new MyEntity();
       entity.setName("test");
       session.persist(entity);

       session.getTransaction().commit();
       assertNotNull(entity.getId());
   }
   ```

3. **Query API Tests:**
   ```java
   @Test
   public void testJPACriteriaAPI() {
       CriteriaBuilder cb = session.getCriteriaBuilder();
       CriteriaQuery<MyEntity> cq = cb.createQuery(MyEntity.class);
       Root<MyEntity> root = cq.from(MyEntity.class);
       cq.where(cb.equal(root.get("name"), "test"));

       List<MyEntity> results = session.createQuery(cq).getResultList();
       assertFalse(results.isEmpty());
   }
   ```

### Integration Tests

Test with real databases:

```bash
# PostgreSQL
mvn test -Dhibernate.connection.url=jdbc:postgresql://localhost:5432/yawl_test

# MySQL
mvn test -Dhibernate.connection.url=jdbc:mysql://localhost:3306/yawl_test

# H2 (in-memory)
mvn test -Dhibernate.connection.url=jdbc:h2:mem:yawl_test
```

### Performance Testing

Monitor HikariCP metrics:

```java
HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
System.out.println("Active connections: " + poolMXBean.getActiveConnections());
System.out.println("Idle connections: " + poolMXBean.getIdleConnections());
System.out.println("Total connections: " + poolMXBean.getTotalConnections());
```

---

## Rollback Plan

If migration issues arise, rollback to Hibernate 5.6.14:

### 1. Revert pom.xml

```xml
<hibernate.version>5.6.14.Final</hibernate.version>
<jakarta.persistence.version>2.2</jakarta.persistence.version>
```

Replace:
- `jakarta.persistence-api-3.0.0` → `javax.persistence-api-2.2`
- `hibernate-hikaricp-6.5.1` → `hibernate-c3p0-5.6.14`
- `HikariCP-5.1.0` → `c3p0-0.9.2.1`

### 2. Revert Java Imports

Run global search/replace:
```
jakarta.persistence → javax.persistence
```

### 3. Revert Configuration Files

- Restore `hibernate.cfg.xml` DTD 3.0
- Restore `*.hbm.xml` DTD 3.0
- Restore c3p0 configuration in `hibernate.properties`

### 4. Revert Deprecated API Usage

- `getResultList()` → `list()`
- `createNativeQuery()` → `createSQLQuery()`
- JPA Criteria API → Hibernate Criteria API

---

## Troubleshooting

### Common Issues

#### 1. ClassNotFoundException: javax.persistence.Entity

**Cause:** Mixed javax and jakarta dependencies
**Solution:** Ensure all persistence APIs use jakarta namespace

```bash
# Find any remaining javax.persistence references
grep -r "import javax.persistence" src/
```

#### 2. NoClassDefFoundError: c3p0.ComboPooledDataSource

**Cause:** Old c3p0 configuration still active
**Solution:** Update `hibernate.connection.provider_class` to HikariCP

```properties
hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider
```

#### 3. SchemaUpdate not found

**Cause:** `org.hibernate.tool.hbm2ddl.SchemaUpdate` removed in Hibernate 6
**Solution:** Use `SchemaManagementTool` instead (already implemented)

#### 4. Connection Pool Exhaustion

**Cause:** HikariCP pool size too small
**Solution:** Increase `maximumPoolSize`:

```properties
hibernate.hikari.maximumPoolSize=50
hibernate.hikari.minimumIdle=10
```

---

## Migration Checklist

- [x] Update pom.xml dependencies
- [x] Update build.xml JAR properties
- [x] Update hibernate.cfg.xml DTD
- [x] Update hibernate.properties (c3p0 → HikariCP)
- [x] Update HibernateEngine.java (util)
- [x] Update HibernateEngine.java (elements)
- [x] Update YPersistenceManager.java
- [x] Test database connectivity
- [ ] Run unit tests (80%+ coverage)
- [ ] Run integration tests
- [ ] Performance testing
- [ ] Load testing
- [ ] Production deployment

---

## References

- [Hibernate 6.5 Migration Guide](https://docs.jboss.org/hibernate/orm/6.5/migration-guide/migration-guide.html)
- [Jakarta Persistence 3.0 Specification](https://jakarta.ee/specifications/persistence/3.0/)
- [HikariCP Documentation](https://github.com/brettwooldridge/HikariCP)
- [YAWL v6.0.0 Documentation](../README.md)

---

**Migration Completed:** 2026-02-15
**Status:** Production-Ready
**Next Review:** Q2 2026
