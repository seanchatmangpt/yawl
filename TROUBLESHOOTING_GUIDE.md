# YAWL v6.0.0 Production Troubleshooting Guide

**Version**: 6.0.0-GA  
**Last Updated**: February 2026  
**Target Audience**: DevOps engineers, on-call support

---

## Quick Diagnosis Tool

```bash
#!/bin/bash
# Save as: /usr/local/bin/yawl-diagnose

ENDPOINT="${1:-http://localhost:8080}"

echo "YAWL Diagnostics Report"
echo "======================="
echo "Timestamp: $(date -Iseconds)"
echo "Endpoint: $ENDPOINT"
echo ""

# Health checks
echo "1. HEALTH STATUS"
curl -s $ENDPOINT/actuator/health | jq '.' 2>/dev/null || echo "ERROR: Cannot reach endpoint"

echo ""
echo "2. JVM METRICS"
curl -s $ENDPOINT/actuator/metrics/jvm.memory.used | jq '.measurements[0]' 2>/dev/null || echo "ERROR"

echo ""
echo "3. GC PAUSE ANALYSIS"
curl -s $ENDPOINT/actuator/metrics/jvm.gc.pause | jq '.measurements | sort_by(-.value)[0:3]' 2>/dev/null

echo ""
echo "4. THREAD POOL"
curl -s $ENDPOINT/actuator/metrics/jvm.threads.live | jq '.measurements[0]' 2>/dev/null

echo ""
echo "5. DATABASE POOL"
curl -s $ENDPOINT/actuator/metrics/hikaricp.connections.active | jq '.measurements[0]' 2>/dev/null

echo ""
echo "Diagnosis complete. Check logs: tail -100 /app/logs/yawl.log"
```

---

## Problem 1: High GC Pause Time (>100ms)

### Symptoms
- Application appears frozen for 100ms+ periods
- User-facing requests timeout during pauses
- Logs show: `GCLocker: Stalled for <time>ms`
- Metrics show spikes in `jvm.gc.pause`

### Diagnosis Steps

```bash
# Step 1: Check current GC pause stats
curl -s http://localhost:9090/actuator/metrics/jvm.gc.pause | jq '.measurements | sort_by(-.value) | .[0:5]'

# Step 2: Identify heap utilization
curl -s http://localhost:9090/actuator/metrics/jvm.memory.used | \
    jq '.measurements[] | select(.description | contains("heap"))'

# Step 3: Check GC logs
tail -100 /app/logs/gc.log | grep -E "pause|duration|phase"

# Step 4: Identify memory pressure
docker stats --no-stream yawl-engine-prod | tail -1
# or on bare metal:
free -h && ps aux | grep java | grep -v grep
```

### Root Causes & Solutions

#### A. Heap Too Small (Most Common)

```bash
# Check current heap settings
ps aux | grep java | grep -v grep | tr ' ' '\n' | grep -E "^-Xm"

# Current output should show:
# -Xms4g  (initial)
# -Xmx8g  (maximum)

# If max heap < 8GB for production, increase:
# Edit: JAVA_OPTS in docker-compose or pod spec

# Restart with new settings
docker restart yawl-engine-prod
# or
kubectl rollout restart deployment/yawl-engine -n yawl-prod
```

#### B. Suboptimal GC Algorithm

```bash
# Check current GC
ps aux | grep java | grep -v grep | tr ' ' '\n' | grep -i gc

# Current: -XX:+UseShenandoahGC (good)
# Or: -XX:+UseZGC (better for ultra-low-latency)

# Switch to ZGC for high-performance scenario
export JAVA_OPTS="
  -Xms8g -Xmx16g
  -XX:+UseZGC
  -XX:ZUncommitDelay=300
  -XX:+ExitOnOutOfMemoryError
  -Xlog:gc*:file=/app/logs/gc.log
"

# Restart and monitor
docker restart yawl-engine-prod
sleep 30
curl http://localhost:9090/actuator/metrics/jvm.gc.pause
```

#### C. Full GC Triggered by Low Memory

```bash
# Check allocation rate
curl -s http://localhost:9090/actuator/metrics/jvm.gc.memory.allocated | jq '.measurements[0]'

# If allocation_rate > 1GB/s, you're allocating too many objects
# Common causes:
#  1. Excessive log output
#  2. Large result sets not paginated
#  3. Memory leak in event processing

# Mitigation: Reduce logging
# Edit application.yml:
logging:
  level:
    org.yawlfoundation.yawl: WARN  # Down from DEBUG
    org.springframework: ERROR
    org.hibernate: ERROR
```

#### D. Max GC Pause Target Too Low

```bash
# Check current target
ps aux | grep java | grep MaxGCPauseMillis

# If set to 10ms, increase to 50ms for better throughput
export JAVA_OPTS="
  $JAVA_OPTS
  -XX:MaxGCPauseMillis=50
"

# Rebuild container or pod
docker-compose up -d --force-recreate
```

### Prevention

```bash
# Permanent fix in docker-compose.prod.yml:
environment:
  JAVA_OPTS: >
    -Xms8g
    -Xmx16g
    -XX:+UseZGC
    -XX:MaxGCPauseMillis=50
    -Xlog:gc*:file=/app/logs/gc.log:uptime:level,tag:filecount=5:filesize=100m

# Monitor ongoing
curl http://localhost:9090/actuator/metrics/jvm.gc.pause | jq '.measurements | max_by(.value)'
# Should be <100ms consistently
```

---

## Problem 2: Database Bottleneck (Slow Queries)

### Symptoms
- Case creation latency >500ms
- Connection pool exhausted (all 30 connections in use)
- Logs show: `Connection is closed`
- Query execution takes >1s

### Diagnosis Steps

```bash
# Step 1: Check connection pool health
curl -s http://localhost:9090/actuator/metrics/hikaricp.connections | jq '.measurements'

# Output should show:
# active: <10 (if <30 OK)
# idle: >0
# pending: 0 (if >0, pool is exhausted)

# Step 2: Check database directly
psql -U yawl_user -d yawl_prod << 'DBEOF'
-- Check active connections
SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;

-- Check long-running queries
SELECT pid, now() - query_start as elapsed, query 
FROM pg_stat_activity 
WHERE state != 'idle' 
ORDER BY elapsed DESC 
LIMIT 10;

-- Check table sizes
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) 
FROM pg_tables 
WHERE schemaname NOT IN ('pg_catalog', 'information_schema') 
ORDER BY pg_total_relation_size DESC 
LIMIT 10;
DBEOF
```

### Root Causes & Solutions

#### A. N+1 Query Problem (Hibernate)

```bash
# Enable query logging to detect N+1
cat > /tmp/application-debug.yml << 'DEBUGEOF'
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
  jpa.show-sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
DEBUGEOF

# Look for repetitive queries in logs:
tail -1000 /app/logs/yawl.log | grep -E "^SELECT.*FROM.*WHERE.*id\s*=" | sort | uniq -c | sort -rn | head -10

# If same query runs >100 times for one operation, it's N+1
# Fix in code: Use @EntityGraph or JOIN FETCH
```

#### B. Missing Database Indexes

```bash
# Find slow queries in PostgreSQL logs
tail -1000 /var/log/postgresql/postgresql.log | grep "duration: " | sort -t: -k4 -rn | head -20

# Analyze with pg_stat_statements
psql -U yawl_user -d yawl_prod << 'INDEXEOF'
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Find slowest queries
SELECT query, calls, mean_time, max_time 
FROM pg_stat_statements 
ORDER BY mean_time DESC 
LIMIT 10;

-- Create missing indexes
-- Example: If query filters by status frequently
CREATE INDEX idx_cases_status ON yawl.cases(status) WHERE status != 'COMPLETED';

-- Analyze impact
ANALYZE yawl.cases;
INDEXEOF
```

#### C. Connection Pool Misconfiguration

```bash
# Current settings:
psql -U yawl_user -d yawl_prod -c "SHOW max_connections;"

# Recommended:
# For 3-pod K8s deployment: max_connections = 100 (30 per pod × 3 + buffer)
# For large on-prem: max_connections = 200

# Update PostgreSQL config:
cat >> /etc/postgresql/16/main/postgresql.conf << 'CONNEOF'
# Connection pool settings
max_connections = 200
superuser_reserved_connections = 10
CONNEOF

systemctl reload postgresql

# Update YAWL connection pool
cat > config/application.yml << 'POOLEOF'
spring:
  datasource:
    hikari:
      maximum-pool-size: 30      # Per-pod connection limit
      minimum-idle: 10           # Always keep 10 open
      connection-timeout: 30000  # Fail fast after 30s
      idle-timeout: 600000       # Close after 10 min idle
      max-lifetime: 1800000      # Recycle after 30 min
POOLEOF

docker restart yawl-engine-prod
```

#### D. Lock Contention

```bash
# Check for lock waits
psql -U yawl_user -d yawl_prod << 'LOCKEOF'
-- Find blocked queries
SELECT blocked_locks.pid, blocked_locks.usename, blocking_locks.pid,
       blocking_locks.usename, blocked_locks.query, blocking_locks.query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_stm ON blocked_stm.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
  AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
  AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
  AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
  AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
  AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
  AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
  AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
  AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
  AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
  AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_stm ON blocking_stm.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
LOCKEOF

# Mitigation: Use shorter transactions, SKIP LOCKED
-- In problematic query, add:
FOR UPDATE SKIP LOCKED  -- Don't wait for locks
```

### Prevention

```bash
# Monitor database health continuously
cat > /tmp/monitor-db.sh << 'MONEOF'
#!/bin/bash
while true; do
    echo "=== $(date) ==="
    
    # Check pool status
    curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.active | \
        jq '.measurements[0] | "\(.value) connections active"'
    
    # Check slow queries
    psql -U yawl_user -d yawl_prod -c \
        "SELECT count(*) FROM pg_stat_activity WHERE state != 'idle' AND query_start < now() - interval '5s';" | \
        tail -1 | xargs echo "Slow queries (>5s):"
    
    sleep 30
done
MONEOF

chmod +x /tmp/monitor-db.sh
/tmp/monitor-db.sh &
```

---

## Problem 3: Lock Contention (Case State Conflicts)

### Symptoms
- Logs show: `Optimistic lock exception`
- Case updates fail with version mismatch
- High retry rate in metrics
- Throughput decreases under load

### Diagnosis

```bash
# Check retry metrics
curl -s http://localhost:9090/actuator/metrics/yawl.case.update.retries | jq '.measurements'

# Look for version conflict patterns in logs
tail -500 /app/logs/yawl.log | grep -i "optimistic\|version\|conflict" | head -20
```

### Solutions

#### A. Increase Retry Backoff

```bash
cat > config/application.yml << 'RETRYEOF'
yawl:
  concurrency:
    optimistic-lock-retries: 5  # Default 3
    retry-backoff-ms: 100       # Start at 100ms
    retry-backoff-multiplier: 2 # Exponential: 100, 200, 400ms
RETRYEOF

docker restart yawl-engine-prod
```

#### B. Use Pessimistic Locking for Critical Operations

```java
// In Java code (reference, not actual fix)
// @Lock(LockModeType.PESSIMISTIC_WRITE)
// public Case updateCase(String caseId) { ... }

# Monitor for deadlocks
tail -100 /app/logs/yawl.log | grep -i "deadlock"
```

#### C. Reduce Contention with Jitter

```bash
cat > config/application.yml << 'JITTEREOF'
yawl:
  concurrency:
    task-processing-jitter-ms: 50  # Random 0-50ms delay before processing
    case-state-cache-ttl: 1000      # Cache case state 1 second
JITTEREOF
```

---

## Problem 4: Memory Growth / Leak Detection

### Symptoms
- Memory usage grows from 4GB → 6GB → 8GB over days
- GC frequency increases (every 30s instead of 5min)
- Eventually OutOfMemory exception
- Heap dump shows accumulated objects

### Diagnosis

```bash
# Check memory trend
curl -s http://localhost:9090/actuator/metrics/jvm.memory.used | jq '.measurements[] | select(.statistic=="VALUE") | .value' | \
    awk '{print NR, $0}' | tail -5

# Generate heap dump
jmap -dump:live,format=b,file=/tmp/heap.bin $(pgrep -f yawl-engine)

# Analyze with Eclipse MAT or jhat
jhat /tmp/heap.bin

# Or use heapy (simpler)
# Check for large object graphs retaining memory
```

### Root Causes & Solutions

#### A. Event Store Growing Unbounded

```bash
# Check event store size
curl -s http://localhost:8080/api/v1/diagnostics/event-store | jq '.event_count, .size_bytes'

# Implement archival policy
cat > config/application.yml << 'ARCHIVEEOF'
yawl:
  event-store:
    archive-after-days: 90        # Archive events older than 90 days
    archive-location: /var/backups/yawl/archived-events
    keep-recent-count: 1000000    # Always keep last 1M events
ARCHIVEEOF

# Manual cleanup (careful!)
psql -U yawl_user -d yawl_prod << 'CLEANEOF'
-- Backup old events
CREATE TABLE yawl.events_archived AS
SELECT * FROM yawl.events 
WHERE timestamp < now() - interval '90 days';

-- Delete archived events
DELETE FROM yawl.events 
WHERE timestamp < now() - interval '90 days';

-- Vacuum to reclaim disk space
VACUUM ANALYZE yawl.events;
CLEANEOF
```

#### B. Logger Holding References

```bash
# Check logger configuration
grep -r "DEBUG\|TRACE" config/ | grep -v production

# Production should use:
logging:
  level:
    root: WARN
    org.yawlfoundation.yawl: INFO
    org.springframework: WARN
    org.hibernate: WARN
```

#### C. Connection Leaks

```bash
# Check if connections are being returned to pool
curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.idle | jq '.measurements[0]'

# If always 0, connections not being closed
# Look for unclosed ResultSets/Statements in logs

# Enable connection tracking
cat >> config/application.yml << 'CONNTRACKEOF'
spring:
  datasource:
    hikari:
      connection-test-query: "SELECT 1"
      leak-detection-threshold: 30000  # 30s threshold for leak detection
CONNTRACKEOF
```

### Prevention

```bash
# Implement memory monitoring
cat > /usr/local/bin/monitor-memory.sh << 'MEMMONEOF'
#!/bin/bash
while true; do
    USED=$(curl -s http://localhost:9090/actuator/metrics/jvm.memory.used | \
        jq '.measurements[] | select(.statistic=="VALUE") | .value' | head -1)
    USED_GB=$(echo "scale=2; $USED / 1024 / 1024 / 1024" | bc)
    
    TIMESTAMP=$(date -Iseconds)
    echo "$TIMESTAMP: Heap used: ${USED_GB}GB"
    
    # Alert if growing >100MB/hour
    sleep 3600
done >> /var/log/yawl-memory-monitor.log
MEMMONEOF

# Create alert in Prometheus for sustained growth
# expr: |
#   (jvm_memory_used_bytes{instance="localhost:9090"} - 
#    jvm_memory_used_bytes{instance="localhost:9090"} offset 1h) 
#   / 1024 / 1024 > 100  # 100MB/hour
```

---

## Problem 5: Throughput Degradation

### Symptoms
- Performance was good, now declining
- Requests/sec dropping from 1000 to 500
- No obvious errors in logs
- CPU usage not maxed

### Diagnosis

```bash
# Check request rate
curl -s http://localhost:9090/actuator/metrics/http.requests | jq '.measurements[] | select(.statistic=="COUNT")'

# Check request distribution
curl -s http://localhost:9090/actuator/metrics/http.requests.duration | \
    jq '.measurements[] | select(.statistic=="TOTAL") | .tags'

# Identify slow endpoints
curl -s http://localhost:9090/actuator/metrics/http.requests.duration.seconds | \
    jq '.measurements[] | select(.value > 1.0) | .tags'
```

### Root Causes & Solutions

#### A. Resource Exhaustion (CPU/Memory/Disk)

```bash
# Check system resources
top -b -n 1 | head -20
free -h
df -h /

# If CPU maxed: Optimize hot-path code or add replicas
# If memory maxed: See Problem 4 (Memory Growth)
# If disk maxed: Archive old data or expand volume
```

#### B. Database Connection Pool Exhausted

```bash
# Already covered in Problem 2: Database Bottleneck
# Quick check:
curl -s http://localhost:9090/actuator/metrics/hikaricp.connections.pending | jq '.measurements[0]'
# Should be 0. If >0, pool is starved.
```

#### C. Event Processing Backlog

```bash
# Check event queue depth
curl -s http://localhost:8080/api/v1/diagnostics/event-queue | jq '.pending_events'

# If backlog growing, increase processing threads
cat > config/application.yml << 'THREADEOF'
yawl:
  event-processing:
    thread-pool-size: 16  # Increase from default 8
    queue-capacity: 10000  # Allow larger queue
THREADEOF
```

#### D. Garbage Collection Pausing Execution

```bash
# Check GC frequency trend
curl -s http://localhost:9090/actuator/metrics/jvm.gc.count | jq '.measurements | length'

# If >3 GCs per minute, see Problem 1
```

---

## Problem 6: API Returns 500 / Service Errors

### Symptoms
- Random 500 errors from API
- Logs show exceptions
- Specific endpoints affected or all endpoints?
- Reproducible or intermittent?

### Diagnosis

```bash
# Get recent errors
tail -100 /app/logs/yawl.log | grep -i "exception\|error\|500"

# Enable request tracing
curl -H "X-Trace-Id: test-123" http://localhost:8080/api/v1/cases

# Check for specific endpoint issues
for i in {1..10}; do
    curl -w "%{http_code}\n" http://localhost:8080/api/v1/cases
done

# Monitor error rate
curl -s http://localhost:9090/actuator/metrics/http.requests | \
    jq '.measurements[] | select(.tags.status=="500")'
```

### Solutions

#### Immediate: Restart Service

```bash
docker restart yawl-engine-prod
# or
kubectl rollout restart deployment/yawl-engine -n yawl-prod

# Monitor restart
kubectl logs -f deployment/yawl-engine -n yawl-prod
```

#### Investigate: Check Error Logs

```bash
# Find root cause
grep -B5 -A10 "Exception" /app/logs/yawl.log | tail -50

# Common causes:
# - NullPointerException: Null data in API request
# - SQLException: Database connectivity issue
# - OutOfMemoryError: See Problem 4
# - TimeoutException: Database or external service slow
```

#### Fix: Apply Hotfix or Rollback

```bash
# Hotfix: Restart specific pod (if intermittent)
kubectl delete pod <pod-name> -n yawl-prod

# Rollback: Use previous image version
kubectl set image deployment/yawl-engine \
    yawl-engine=yawl-engine:6.0.0-previous \
    -n yawl-prod

kubectl rollout status deployment/yawl-engine -n yawl-prod
```

---

## Escalation Decision Tree

```
Issue Detected
    ↓
Can it be fixed by restarting? (75% of issues)
├─ YES: Restart service
│   └─ Issue resolved? → Done
│   └─ Not resolved? → Continue diagnosis
└─ NO: Continue
    ↓
Is database connectivity broken?
├─ YES: Check DB server / firewall / credentials
│   └─ Failover to read replica if available
└─ NO: Continue
    ↓
Is memory exhausted?
├─ YES: Check for leaks (Problem 4) / Restart
│   └─ Escalate if restart doesn't help
└─ NO: Continue
    ↓
Is there sustained high error rate (>1%)?
├─ YES: Trigger incident protocol
│   ├─ Notify on-call team
│   ├─ Start war room
│   └─ Prepare rollback
└─ NO: Monitor and document
```

---

## Key Metrics to Monitor

| Metric | Good | Warning | Critical |
|--------|------|---------|----------|
| Case creation latency | <300ms | 300-500ms | >500ms |
| GC pause time (avg) | <30ms | 30-100ms | >100ms |
| Error rate | <0.01% | 0.01-1% | >1% |
| Memory usage | <6GB | 6-8GB | >8GB |
| DB connections active | <10 | 10-25 | >25 |
| CPU usage | <40% | 40-70% | >70% |
| Throughput | >100 req/s | 50-100 | <50 |

---

**Still stuck?** Enable DEBUG logging and collect:
1. Last 500 lines of `/app/logs/yawl.log`
2. Heap dump: `jmap -dump:live,format=b,file=heap.bin <pid>`
3. Thread dump: `kill -3 <pid>` or `jstack <pid>`
4. Metrics snapshot: `curl http://localhost:9090/actuator/prometheus`

