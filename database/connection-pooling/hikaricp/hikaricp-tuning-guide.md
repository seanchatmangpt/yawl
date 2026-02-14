# HikariCP Tuning Guide for YAWL

## Overview

This guide provides best practices for tuning HikariCP connection pooling in production YAWL deployments.

## Pool Sizing

### The Formula

The optimal pool size is calculated as:

```
connections = (core_count * 2) + effective_spindle_count
```

### Examples

| Environment | CPU Cores | Disks | Recommended Pool Size |
|------------|-----------|-------|----------------------|
| Development | 2 | 1 | 5 |
| Small Production | 4 | 2 | 10 |
| Medium Production | 8 | 4 | 20 |
| Large Production | 16 | 8 | 40 |

### Key Points

1. **More connections != better performance**
   - Each connection consumes memory and CPU
   - Too many connections cause context switching overhead
   - Database has limited capacity for concurrent operations

2. **Minimum Idle Connections**
   - Set to 10-25% of maximum pool size
   - Ensures connections are ready during traffic spikes
   - Avoid setting too high (wastes resources)

## Timeout Configuration

### Connection Timeout (30000ms default)

```yaml
connection-timeout: 30000
```

Time to wait for a connection from the pool before throwing exception.

**Tuning:**
- Low latency applications: 10000-20000ms
- Normal applications: 30000ms
- Batch processing: 60000ms

### Idle Timeout (600000ms default)

```yaml
idle-timeout: 600000
```

Time before an idle connection is removed from the pool.

**Rules:**
- Must be less than database `wait_timeout`
- Typically 10 minutes for most applications
- Set lower if database has aggressive timeout

### Max Lifetime (1800000ms default)

```yaml
max-lifetime: 1800000
```

Maximum lifetime of a connection in the pool.

**Rules:**
- Should be several seconds less than database `max_lifetime`
- 30 minutes is good default
- Helps with connection drift and DNS changes

### Leak Detection (60000ms default)

```yaml
leak-detection-threshold: 60000
```

Time before a connection is considered "leaked".

**Settings:**
- `0` - Disabled (not recommended)
- `60000` - 1 minute (development)
- `300000` - 5 minutes (production)
- `0` during load testing only

## Performance Tuning

### Prepared Statement Caching

```yaml
data-source-properties:
  cachePrepStmts: true
  prepStmtCacheSize: 250
  prepStmtCacheSqlLimit: 2048
```

**Benefits:**
- Reduces parsing overhead
- Improves query performance
- Caches on both driver and server side

### Session State Caching

```yaml
data-source-properties:
  useLocalSessionState: true
  useLocalTransactionState: true
```

**Benefits:**
- Avoids round trips for session state
- Reduces network overhead

### Metadata Caching

```yaml
data-source-properties:
  cacheResultSetMetadata: true
  cacheDatabaseMetaData: true
  cacheServerConfiguration: true
```

**Benefits:**
- Caches database schema information
- Reduces metadata queries

## Monitoring

### JMX Metrics

Enable JMX for monitoring:

```yaml
register-mbeans: true
```

**Key Metrics:**
- `HikariPool-0.pool.ActiveConnections`
- `HikariPool-0.pool.IdleConnections`
- `HikariPool-0.pool.TotalConnections`
- `HikariPool-0.pool.ConnectionsAwaiting`
- `HikariPool-0.pool.PendingThreads`

### Micrometer Integration

```yaml
metrics-tracker-factory: com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory
```

**Prometheus Metrics:**
- `hikaricp_connections_active`
- `hikaricp_connections_idle`
- `hikaricp_connections_pending`
- `hikaricp_connections_max`
- `hikaricp_connections_min`

### Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  health:
    db:
      enabled: true
```

**Health Endpoint:**
```bash
curl http://localhost:8080/actuator/health
```

## Production Checklist

- [ ] Pool size calculated based on CPU cores
- [ ] Connection timeout set appropriately
- [ ] Max lifetime less than database limit
- [ ] Leak detection enabled
- [ ] JMX/Micrometer metrics enabled
- [ ] Health checks configured
- [ ] Prepared statement caching enabled
- [ ] Session state caching enabled
- [ ] Logging level set to INFO or higher
- [ ] Password stored in secrets manager
- [ ] SSL/TLS enabled for connections

## Troubleshooting

### Connection Leaks

**Symptoms:**
- Pool exhausted errors
- Growing active connections

**Diagnosis:**
```bash
# Check leak detection logs
grep "Connection leak detection" application.log

# Monitor pool metrics
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

**Solutions:**
1. Ensure all connections are closed (try-with-resources)
2. Check for long-running transactions
3. Increase leak detection threshold if legitimate

### Pool Exhaustion

**Symptoms:**
- `Connection is not available` errors
- Timeout waiting for connection

**Diagnosis:**
```bash
# Check pending threads
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

**Solutions:**
1. Increase maximum pool size
2. Reduce connection usage time
3. Check for slow queries
4. Add read replicas for read-heavy workloads

### Connection Timeouts

**Symptoms:**
- `Connection timed out` errors
- Intermittent failures

**Diagnosis:**
```bash
# Check database connectivity
psql -h localhost -U yawl -d yawl -c "SELECT 1"

# Check network latency
ping -c 10 database-host
```

**Solutions:**
1. Increase connection timeout
2. Check network connectivity
3. Check database load
4. Use connection pooling closer to database (PgBouncer)
