# Database Driver Migration Guide - YAWL 5.2
**Date**: 2026-02-15
**Status**: Production-Ready

## Executive Summary

This guide documents the migration from legacy database drivers and c3p0 connection pooling to modern drivers with HikariCP connection pooling, optimized for Java 21 virtual threads.

### Key Changes

| Component | Old Version | New Version | Status |
|-----------|-------------|-------------|--------|
| MySQL Driver | 5.1.22 | 8.0.36 | ✅ Complete |
| PostgreSQL Driver | 42.2.8 | 42.7.2 | ✅ Complete |
| H2 Database | 1.3.176 | 2.2.224 | ✅ Complete |
| Apache Derby | N/A | 10.17.1.0 | ✅ Complete |
| HSQLDB | 1.x | 2.7.2 | ✅ Complete |
| Oracle JDBC | ojdbc6 | ojdbc11 (23.3.0.23.09) | ✅ Complete |
| Connection Pool | c3p0 0.9.2.1 | HikariCP 5.1.0 | ✅ Complete |

---

## 1. MySQL Migration (5.1.22 → 8.0.36)

### Driver Changes

**Old Configuration:**
```properties
hibernate.connection.driver_class=com.mysql.jdbc.Driver
hibernate.connection.url=jdbc:mysql:///yawl
```

**New Configuration:**
```properties
hibernate.connection.driver_class=com.mysql.cj.jdbc.Driver
hibernate.connection.url=jdbc:mysql://localhost:3306/yawl?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

### Breaking Changes

1. **Driver Class Name**: `com.mysql.jdbc.Driver` → `com.mysql.cj.jdbc.Driver`
2. **URL Format**: Must include host and port explicitly
3. **Removed Parameters**:
   - `autoDeserialize` (security risk, removed)
   - `autoReconnect` (deprecated)
4. **Required Parameters**:
   - `serverTimezone=UTC` - timezone must be explicit
   - `useSSL=false` - or configure SSL properly
   - `allowPublicKeyRetrieval=true` - for caching_sha2_password auth

### SSL Configuration (Production)

For production environments with SSL:
```properties
hibernate.connection.url=jdbc:mysql://localhost:3306/yawl?\
  useSSL=true&\
  requireSSL=true&\
  verifyServerCertificate=true&\
  trustCertificateKeyStoreUrl=file:/path/to/truststore.jks&\
  trustCertificateKeyStorePassword=changeme&\
  serverTimezone=UTC
```

### Compatibility

- MySQL 5.7+ through 8.0+
- MariaDB 10.3+ (use MariaDB driver for optimal performance)

---

## 2. PostgreSQL Migration (42.2.8 → 42.7.2)

### Driver Changes

**Old Configuration:**
```properties
hibernate.connection.driver_class=org.postgresql.Driver
hibernate.connection.url=jdbc:postgresql:yawl
```

**New Configuration:**
```properties
hibernate.connection.driver_class=org.postgresql.Driver
hibernate.connection.url=jdbc:postgresql://localhost:5432/yawl
```

### Enhanced Features

1. **Prepared Statement Caching**: Automatic in driver
2. **Binary Transfer**: Enabled by default for better performance
3. **SSL Support**: Enhanced with SCRAM-SHA-256 authentication

### Connection URL Parameters (Optional)

```properties
# Performance optimizations
jdbc:postgresql://localhost:5432/yawl?\
  prepareThreshold=5&\
  preparedStatementCacheQueries=256&\
  preparedStatementCacheSizeMiB=5&\
  loggerLevel=OFF&\
  ApplicationName=YAWL-Engine
```

### SSL Configuration (Production)

```properties
jdbc:postgresql://localhost:5432/yawl?\
  ssl=true&\
  sslmode=verify-full&\
  sslcert=/path/to/client-cert.pem&\
  sslkey=/path/to/client-key.pk8&\
  sslrootcert=/path/to/server-ca.pem
```

### Compatibility

- PostgreSQL 9.6+ through 16+
- EnterpriseDB Advanced Server 9.6+

---

## 3. H2 Database Migration (1.3.176 → 2.2.224)

### Critical Breaking Changes

H2 2.x introduces significant compatibility breaks from 1.x.

**Old Configuration:**
```properties
hibernate.connection.url=jdbc:h2:file:./webapps/yawl/yawl
```

**New Configuration (File-based):**
```properties
hibernate.connection.url=jdbc:h2:file:./data/yawl;MODE=PostgreSQL;AUTO_SERVER=TRUE
```

**In-Memory (Testing):**
```properties
hibernate.connection.url=jdbc:h2:mem:yawl;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
```

### Migration Steps

1. **Backup H2 1.x Database:**
   ```bash
   java -cp h2-1.3.176.jar org.h2.tools.Script \
     -url jdbc:h2:file:./webapps/yawl/yawl \
     -user sa \
     -script backup.sql
   ```

2. **Import to H2 2.x:**
   ```bash
   java -cp h2-2.2.224.jar org.h2.tools.RunScript \
     -url jdbc:h2:file:./data/yawl \
     -user sa \
     -script backup.sql
   ```

### Compatibility Mode

For maximum compatibility with existing SQL:
```properties
# PostgreSQL compatibility mode
MODE=PostgreSQL;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
```

### URL Parameters

```properties
jdbc:h2:file:./data/yawl;\
  MODE=PostgreSQL;\
  AUTO_SERVER=TRUE;\
  DB_CLOSE_DELAY=-1;\
  TRACE_LEVEL_FILE=0;\
  LOG=0;\
  CACHE_SIZE=65536;\
  LOCK_TIMEOUT=10000
```

---

## 4. Apache Derby Migration (→ 10.17.1.0)

### Configuration

**Client/Server Mode:**
```properties
hibernate.dialect=org.hibernate.dialect.DerbyDialect
hibernate.connection.driver_class=org.apache.derby.jdbc.ClientDriver
hibernate.connection.url=jdbc:derby://localhost:1527/yawl;create=true
hibernate.connection.username=app
hibernate.connection.password=app
```

**Embedded Mode:**
```properties
hibernate.connection.driver_class=org.apache.derby.jdbc.EmbeddedDriver
hibernate.connection.url=jdbc:derby:yawl;create=true
```

### Performance Tuning

```properties
jdbc:derby://localhost:1527/yawl;\
  create=true;\
  upgradeDatabase=true;\
  logDevice=/var/log/derby/derby.log;\
  rollForwardRecoveryFrom=/path/to/backup
```

---

## 5. HSQLDB Migration (→ 2.7.2)

### Configuration

**File-based:**
```properties
hibernate.dialect=org.hibernate.dialect.HSQLDialect
hibernate.connection.driver_class=org.hsqldb.jdbc.JDBCDriver
hibernate.connection.url=jdbc:hsqldb:file:./data/yawl;shutdown=true
hibernate.connection.username=SA
hibernate.connection.password=
```

**In-Memory:**
```properties
hibernate.connection.url=jdbc:hsqldb:mem:yawl
```

**Server Mode:**
```properties
hibernate.connection.url=jdbc:hsqldb:hsql://localhost:9001/yawl
```

### Performance Settings

```properties
jdbc:hsqldb:file:./data/yawl;\
  shutdown=true;\
  hsqldb.default_table_type=cached;\
  hsqldb.cache_scale=14;\
  hsqldb.cache_size_scale=10;\
  hsqldb.log_size=50;\
  hsqldb.nio_data_file=true;\
  hsqldb.write_delay=false
```

---

## 6. Oracle JDBC Migration (ojdbc6 → ojdbc11 23.3.0.23.09)

### Driver Changes

**Old Configuration:**
```properties
hibernate.connection.driver_class=oracle.jdbc.driver.OracleDriver
hibernate.connection.url=jdbc:oracle:thin:@localhost:1521:YAWL
```

**New Configuration:**
```properties
hibernate.connection.driver_class=oracle.jdbc.OracleDriver
hibernate.connection.url=jdbc:oracle:thin:@localhost:1521/YAWL
```

### Dialect Update

```properties
# Oracle 12c+
hibernate.dialect=org.hibernate.dialect.Oracle12cDialect

# Oracle 19c+ (recommended)
hibernate.dialect=org.hibernate.dialect.Oracle19cDialect

# Oracle 21c+
hibernate.dialect=org.hibernate.dialect.Oracle21cDialect
```

### Connection URL Parameters

```properties
jdbc:oracle:thin:@localhost:1521/YAWL?\
  oracle.net.CONNECT_TIMEOUT=10000&\
  oracle.jdbc.ReadTimeout=60000&\
  oracle.net.keepAlive=true&\
  v$session.program=YAWL-Engine
```

### Oracle Wallet (Production)

```properties
jdbc:oracle:thin:@config_name?\
  TNS_ADMIN=/path/to/wallet
```

---

## 7. HikariCP Migration (from c3p0)

### Configuration Mapping

| c3p0 Property | HikariCP Property | Notes |
|---------------|-------------------|-------|
| `c3p0.max_size` | `hikari.maximumPoolSize` | Default: 20 |
| `c3p0.min_size` | `hikari.minimumIdle` | Default: 5 |
| `c3p0.timeout` | `hikari.connectionTimeout` | In milliseconds |
| `c3p0.idle_test_period` | `hikari.keepaliveTime` | Keepalive interval |
| `c3p0.max_statements` | N/A | HikariCP uses driver's PreparedStatement cache |
| `c3p0.acquire_increment` | N/A | Not needed in HikariCP |

### Old c3p0 Configuration

```properties
hibernate.connection.provider_class=org.hibernate.service.jdbc.connections.internal.C3P0ConnectionProvider
hibernate.c3p0.max_size=20
hibernate.c3p0.min_size=2
hibernate.c3p0.timeout=5000
hibernate.c3p0.max_statements=100
hibernate.c3p0.idle_test_period=3000
hibernate.c3p0.acquire_increment=1
```

### New HikariCP Configuration

```properties
hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider

# Pool sizing - optimized for virtual threads
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.minimumIdle=5

# Timeouts (milliseconds)
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.idleTimeout=600000
hibernate.hikari.maxLifetime=1800000
hibernate.hikari.validationTimeout=5000

# Health and monitoring
hibernate.hikari.keepaliveTime=120000
hibernate.hikari.leakDetectionThreshold=60000
hibernate.hikari.registerMbeans=true
```

### Virtual Thread Optimization

For Java 21 virtual threads:
```properties
# Smaller pool sizes work better with virtual threads
hibernate.hikari.maximumPoolSize=10
hibernate.hikari.minimumIdle=2

# Allow more time for virtual thread context switches
hibernate.hikari.connectionTimeout=45000
```

### Programmatic Configuration

```java
Properties props = new Properties();
props.setProperty("hibernate.connection.provider_class",
        "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");
props.setProperty("hibernate.hikari.maximumPoolSize", "20");
props.setProperty("hibernate.hikari.minimumIdle", "5");
props.setProperty("hibernate.hikari.connectionTimeout", "30000");
props.setProperty("hibernate.hikari.idleTimeout", "600000");
props.setProperty("hibernate.hikari.maxLifetime", "1800000");
```

### Monitoring

HikariCP exposes metrics via JMX:
```bash
# JMX monitoring
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

MBeans available:
- `com.zaxxer.hikari:type=Pool (YAWL-HikariCP-Pool)`
  - Active connections
  - Idle connections
  - Total connections
  - Threads awaiting connection
  - Connection acquisition time

---

## 8. Testing

### Test Matrix

Test each database with:
1. Basic CRUD operations
2. Transaction rollback
3. Concurrent connections (50+ threads)
4. Connection leak detection
5. Pool exhaustion recovery
6. Database restart recovery

### Sample Test Code

```java
// Test connection pooling
@Test
public void testConnectionPooling() throws Exception {
    HikariDataSource ds = getHikariDataSource();

    // Verify pool configuration
    assertEquals(20, ds.getMaximumPoolSize());
    assertEquals(5, ds.getMinimumIdle());

    // Test connection acquisition
    try (Connection conn = ds.getConnection()) {
        assertNotNull(conn);
        assertFalse(conn.isClosed());

        // Execute test query
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    // Verify connection returned to pool
    HikariPoolMXBean poolMXBean = ds.getHikariPoolMXBean();
    assertTrue(poolMXBean.getIdleConnections() > 0);
}

// Test leak detection
@Test
public void testLeakDetection() throws Exception {
    Properties props = new Properties();
    props.setProperty("hibernate.hikari.leakDetectionThreshold", "2000");

    // Intentionally leak connection
    Connection conn = ds.getConnection();
    Thread.sleep(3000);  // Should trigger leak detection

    // Verify leak logged
    // Check logs for: "Connection leak detection triggered"
}
```

### Integration Tests

Run against real databases:
```bash
# PostgreSQL
mvn test -Dtest=PostgresIntegrationTest -Ddb.url=jdbc:postgresql://localhost:5432/yawl_test

# MySQL
mvn test -Dtest=MySQLIntegrationTest -Ddb.url=jdbc:mysql://localhost:3306/yawl_test

# H2
mvn test -Dtest=H2IntegrationTest -Ddb.url=jdbc:h2:mem:yawl_test
```

---

## 9. Deployment Checklist

### Pre-Deployment

- [ ] Backup all production databases
- [ ] Test migration on staging environment
- [ ] Verify connection pool sizing for production load
- [ ] Update application server configuration
- [ ] Review database-specific SQL compatibility
- [ ] Test connection failover scenarios

### Deployment

- [ ] Replace old JAR files in `build/3rdParty/lib/`
- [ ] Update `hibernate.properties` with new configuration
- [ ] Clear Hibernate query cache
- [ ] Restart application server
- [ ] Monitor connection pool metrics
- [ ] Verify no connection leaks

### Post-Deployment

- [ ] Monitor database connection pool usage
- [ ] Check application logs for database errors
- [ ] Verify query performance (compare to baseline)
- [ ] Monitor memory usage
- [ ] Check JMX metrics
- [ ] Run smoke tests for all database operations

---

## 10. Rollback Procedure

If issues occur:

1. **Stop Application Server**
   ```bash
   ./catalina.sh stop
   ```

2. **Restore Old JAR Files**
   ```bash
   cp backup/3rdParty/lib/*.jar build/3rdParty/lib/
   ```

3. **Restore Old Configuration**
   ```bash
   cp backup/hibernate.properties build/properties/
   ```

4. **Restart Application Server**
   ```bash
   ./catalina.sh start
   ```

5. **Verify System Health**
   ```bash
   curl http://localhost:8080/yawl/actuator/health
   ```

---

## 11. Performance Tuning

### Connection Pool Sizing

**Formula**: `connections = (core_count * 2) + effective_spindle_count`

For virtual threads (Java 21):
```properties
# Standard hardware (8 cores, SSD)
hibernate.hikari.maximumPoolSize=10

# High-end server (32 cores, NVMe)
hibernate.hikari.maximumPoolSize=20

# Cloud instance (2 vCPUs)
hibernate.hikari.maximumPoolSize=6
```

### Database-Specific Tuning

**PostgreSQL:**
```properties
# Increase prepared statement cache
prepareThreshold=3
preparedStatementCacheQueries=256
preparedStatementCacheSizeMiB=5
```

**MySQL:**
```properties
# Enable server-side prepared statements
useServerPrepStmts=true
cachePrepStmts=true
prepStmtCacheSize=250
prepStmtCacheSqlLimit=2048
```

**Oracle:**
```properties
# Implicit caching
oracle.jdbc.implicitStatementCacheSize=250
oracle.jdbc.maxCachedBufferSize=30
```

---

## 12. Troubleshooting

### Connection Timeout

**Symptom**: `SQLTimeoutException: Connection is not available`

**Solution**:
```properties
# Increase timeout
hibernate.hikari.connectionTimeout=60000

# Or increase pool size
hibernate.hikari.maximumPoolSize=30
```

### Connection Leaks

**Symptom**: Pool exhausted, leak detection warnings

**Solution**:
```properties
# Enable leak detection
hibernate.hikari.leakDetectionThreshold=30000

# Review code for unclosed connections
# Always use try-with-resources
```

### Slow Queries

**Symptom**: High connection wait time

**Solution**:
1. Review slow query logs
2. Add database indexes
3. Enable statement caching
4. Consider connection pool sizing

---

## 13. References

- [HikariCP Documentation](https://github.com/brettwooldridge/HikariCP)
- [MySQL Connector/J 8.0 Guide](https://dev.mysql.com/doc/connector-j/8.0/en/)
- [PostgreSQL JDBC Documentation](https://jdbc.postgresql.org/documentation/)
- [H2 Database Migration Guide](http://www.h2database.com/html/migration-to-v2.html)
- [Oracle JDBC Thin Driver Guide](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/)

---

**Migration Guide Version**: 1.0
**Last Updated**: 2026-02-15
**Maintained By**: YAWL Foundation
