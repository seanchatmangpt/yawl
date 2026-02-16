# Database Connection Pooling - HikariCP Migration

## Overview

YAWL 5.2 has migrated from c3p0 to HikariCP for database connection pooling. HikariCP provides:

- **Superior Performance**: 10-100x faster than c3p0
- **Lower Latency**: Microsecond-level connection acquisition
- **Virtual Thread Support**: Optimized for Java 21 virtual threads
- **Better Monitoring**: JMX metrics and leak detection
- **Production Stability**: Extensively battle-tested in production

## Quick Start

### 1. Update Dependencies

All required dependencies are already included in `pom.xml`:

```xml
<!-- HikariCP 5.1.0 -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

### 2. Configure Database

Update `build/properties/hibernate.properties`:

```properties
# Use HikariCP provider
hibernate.connection.provider_class=org.yawlfoundation.yawl.util.HikariCPConnectionProvider

# Pool configuration
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.minimumIdle=5
hibernate.hikari.connectionTimeout=30000
hibernate.hikari.idleTimeout=600000
hibernate.hikari.maxLifetime=1800000
```

### 3. Database-Specific Configuration

Choose your database configuration:

#### PostgreSQL (Recommended)
```properties
hibernate.connection.url=jdbc:postgresql://localhost:5432/yawl
hibernate.connection.driver_class=org.postgresql.Driver
```

#### MySQL
```properties
hibernate.connection.url=jdbc:mysql://localhost:3306/yawl?useSSL=false&serverTimezone=UTC
hibernate.connection.driver_class=com.mysql.cj.jdbc.Driver
```

#### H2 (Development/Testing)
```properties
hibernate.connection.url=jdbc:h2:mem:yawl;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
hibernate.connection.driver_class=org.h2.Driver
```

## Configuration Files

### Location

- **General Configuration**: `database/connection-pooling/hikaricp/hikaricp.properties`
- **MySQL Specific**: `database/connection-pooling/hikaricp/hikaricp-mysql.properties`
- **H2 Specific**: `database/connection-pooling/hikaricp/hikaricp-h2.properties`
- **PostgreSQL**: Use general configuration

### Loading Configuration

#### Programmatic Configuration

```java
Properties props = new Properties();
props.load(new FileInputStream("database/connection-pooling/hikaricp/hikaricp.properties"));

HikariCPConnectionProvider provider = new HikariCPConnectionProvider();
provider.configure(props);
```

#### Hibernate Configuration

```java
Properties hibernateProps = new Properties();
hibernateProps.setProperty("hibernate.connection.provider_class",
        "org.yawlfoundation.yawl.util.HikariCPConnectionProvider");
hibernateProps.setProperty("hibernate.connection.url",
        "jdbc:postgresql://localhost:5432/yawl");
hibernateProps.setProperty("hibernate.hikari.maximumPoolSize", "20");

SessionFactory factory = new Configuration()
        .addProperties(hibernateProps)
        .buildSessionFactory();
```

## Pool Sizing Guidelines

### Formula

`connections = (core_count * 2) + effective_spindle_count`

### Recommendations

| Environment | Cores | Disk | Recommended Pool Size |
|-------------|-------|------|----------------------|
| Development | 2-4   | SSD  | 5-10 |
| Staging | 4-8   | SSD  | 10-15 |
| Production (Standard) | 8-16  | SSD  | 15-20 |
| Production (High-end) | 32+   | NVMe | 20-30 |
| Cloud (Small) | 2 vCPU | - | 6-8 |
| Cloud (Large) | 16+ vCPU | - | 20-25 |

### Virtual Thread Optimization (Java 21)

For virtual threads, smaller pools work better:

```properties
# Virtual thread optimized
hibernate.hikari.maximumPoolSize=10
hibernate.hikari.minimumIdle=2
hibernate.hikari.connectionTimeout=45000
```

## Monitoring

### JMX Metrics

Enable JMX monitoring:

```properties
hibernate.hikari.registerMbeans=true
```

Start JVM with:
```bash
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

### Available Metrics

Connect with JConsole or VisualVM to `localhost:9010`:

- `com.zaxxer.hikari:type=Pool (YAWL-HikariCP-Pool)`
  - **ActiveConnections**: Currently in use
  - **IdleConnections**: Available in pool
  - **TotalConnections**: Active + Idle
  - **ThreadsAwaitingConnection**: Waiting for connection
  - **ConnectionCreationTime**: Time to create new connection (ms)
  - **ConnectionAcquisitionTime**: Time to acquire from pool (ms)

### Programmatic Metrics

```java
HikariDataSource ds = provider.unwrap(HikariDataSource.class);
HikariPoolMXBean poolMXBean = ds.getHikariPoolMXBean();

System.out.println("Active: " + poolMXBean.getActiveConnections());
System.out.println("Idle: " + poolMXBean.getIdleConnections());
System.out.println("Total: " + poolMXBean.getTotalConnections());
System.out.println("Waiting: " + poolMXBean.getThreadsAwaitingConnection());
```

## Leak Detection

### Configuration

Enable leak detection:

```properties
# Log warnings for connections held longer than 60 seconds
hibernate.hikari.leakDetectionThreshold=60000
```

### Interpreting Warnings

When a leak is detected:

```
WARN  c.z.h.pool.ProxyLeakTask - Connection leak detection triggered for
      connection, stack trace follows
```

The stack trace shows where the connection was acquired but not closed.

### Prevention

Always use try-with-resources:

```java
// GOOD - auto-closes connection
try (Connection conn = provider.getConnection()) {
    // use connection
}

// BAD - connection may leak
Connection conn = provider.getConnection();
// use connection
conn.close();  // May not execute if exception thrown
```

## Performance Tuning

### Connection Timeout

Adjust based on load:

```properties
# Low traffic - shorter timeout
hibernate.hikari.connectionTimeout=10000

# High traffic - longer timeout
hibernate.hikari.connectionTimeout=60000
```

### Keepalive

For databases with aggressive idle timeouts:

```properties
# MySQL (default wait_timeout = 8 hours)
hibernate.hikari.keepaliveTime=120000  # 2 minutes
hibernate.hikari.maxLifetime=1800000   # 30 minutes

# PostgreSQL (no timeout by default)
hibernate.hikari.keepaliveTime=300000  # 5 minutes
hibernate.hikari.maxLifetime=3600000   # 60 minutes
```

### Database-Specific Tuning

#### PostgreSQL

```properties
jdbcUrl=jdbc:postgresql://localhost:5432/yawl?\
  prepareThreshold=5&\
  preparedStatementCacheQueries=256&\
  preparedStatementCacheSizeMiB=5
```

#### MySQL

```properties
jdbcUrl=jdbc:mysql://localhost:3306/yawl?\
  useServerPrepStmts=true&\
  cachePrepStmts=true&\
  prepStmtCacheSize=250&\
  prepStmtCacheSqlLimit=2048&\
  rewriteBatchedStatements=true
```

## Migration from c3p0

### Automatic Migration

The `HibernateEngine` class automatically migrates c3p0 properties:

| c3p0 Property | HikariCP Property |
|---------------|-------------------|
| `c3p0.max_size` | `hikari.maximumPoolSize` |
| `c3p0.min_size` | `hikari.minimumIdle` |
| `c3p0.timeout` | `hikari.connectionTimeout` |
| `c3p0.idle_test_period` | `hikari.keepaliveTime` |

### Manual Migration

1. Replace c3p0 properties in `hibernate.properties`
2. Update connection provider class
3. Restart application
4. Monitor pool metrics

See `DATABASE_DRIVER_MIGRATION_GUIDE.md` for complete instructions.

## Troubleshooting

### Connection Timeout Errors

**Symptom**: `SQLTimeoutException: Connection is not available`

**Solutions**:
1. Increase pool size: `hibernate.hikari.maximumPoolSize=30`
2. Increase timeout: `hibernate.hikari.connectionTimeout=60000`
3. Check for connection leaks (enable leak detection)
4. Review slow queries

### Pool Exhaustion

**Symptom**: All connections in use, threads waiting

**Solutions**:
1. Enable leak detection to find unclosed connections
2. Review connection usage patterns
3. Increase pool size if legitimate high load
4. Add database indexes for slow queries

### High Connection Creation Time

**Symptom**: High values in `ConnectionCreationTime` metric

**Solutions**:
1. Check database server performance
2. Review network latency
3. Increase `minimumIdle` to pre-create connections
4. Check database connection limits

## Testing

### Unit Tests

Run HikariCP unit tests:

```bash
mvn test -Dtest=HikariCPConnectionProviderTest
```

### Integration Tests

Test with real database:

```bash
# PostgreSQL
mvn test -Dtest=*IntegrationTest -Ddb.url=jdbc:postgresql://localhost:5432/yawl_test

# MySQL
mvn test -Dtest=*IntegrationTest -Ddb.url=jdbc:mysql://localhost:3306/yawl_test

# H2
mvn test -Dtest=*IntegrationTest -Ddb.url=jdbc:h2:mem:yawl_test
```

### Load Testing

Simulate high concurrency:

```bash
mvn test -Dtest=ConnectionPoolLoadTest -Dthreads=100 -Doperations=1000
```

## Best Practices

### 1. Connection Management

✅ **DO**: Use try-with-resources
```java
try (Connection conn = provider.getConnection()) {
    // use connection
}
```

❌ **DON'T**: Manual close without try-catch
```java
Connection conn = provider.getConnection();
conn.close();
```

### 2. Pool Sizing

✅ **DO**: Start conservative and monitor
```properties
maximumPoolSize=20
minimumIdle=5
```

❌ **DON'T**: Over-provision
```properties
maximumPoolSize=500  # Too large!
```

### 3. Timeouts

✅ **DO**: Set realistic timeouts
```properties
connectionTimeout=30000  # 30 seconds
idleTimeout=600000       # 10 minutes
```

❌ **DON'T**: Infinite timeouts
```properties
connectionTimeout=0  # Never timeout
```

### 4. Monitoring

✅ **DO**: Enable JMX and leak detection
```properties
registerMbeans=true
leakDetectionThreshold=60000
```

❌ **DON'T**: Run blind in production
```properties
# No monitoring configured
```

## References

- [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Database Driver Migration Guide](DATABASE_DRIVER_MIGRATION_GUIDE.md)
- [Pool Sizing Article](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)

## Support

For issues or questions:

1. Check [DATABASE_DRIVER_MIGRATION_GUIDE.md](DATABASE_DRIVER_MIGRATION_GUIDE.md)
2. Review HikariCP metrics in JMX
3. Enable DEBUG logging: `com.zaxxer.hikari=DEBUG`
4. Contact YAWL Foundation support

---

**Version**: 1.0
**Last Updated**: 2026-02-15
**Maintained By**: YAWL Foundation
