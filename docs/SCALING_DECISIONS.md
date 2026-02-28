# YAWL Scaling Decision Tree & Configuration Guide

**Status**: Production Ready | **Last Updated**: February 2026 | **Java 25 Optimized**

Data-driven guide for diagnosing bottlenecks and scaling YAWL to meet your throughput and latency requirements.

---

## 1. Quick Decision Tree

```
START: What's your current situation?
│
├─ "Cases are backing up" (P99 > 500ms)
│  │
│  ├─ Check: Is CPU >80%?
│  │  ├─ YES → Go to Section 3.1 (CPU Bottleneck)
│  │  └─ NO → Go to Section 3.2 (Database Bottleneck)
│  │
│  ├─ Check: Is memory >85%?
│  │  ├─ YES → Go to Section 3.3 (Memory Bottleneck)
│  │  └─ NO → Continue to next check
│  │
│  └─ Check: Is disk I/O > 90%?
│     ├─ YES → Go to Section 3.4 (Disk I/O Bottleneck)
│     └─ NO → Go to Section 3.5 (Network Bottleneck)
│
├─ "Users getting timeouts" (P99 > 10sec)
│  │
│  ├─ Check: How many cases in flight?
│  │  ├─ <10K → Single node saturation (add CPU/RAM)
│  │  ├─ 10K-100K → Database saturation (add replicas)
│  │  └─ >100K → Architectural bottleneck (go stateless)
│
├─ "Want to handle 10x more load"
│  │
│  ├─ Budget available?
│  │  ├─ <$50K → Optimize single instance (Java 25)
│  │  ├─ $50K-200K → Add database replicas or stateless
│  │  └─ >$200K → Go full cloud-native
│
└─ "Need 99.99% uptime"
   │
   └─ Go to Section 2.3 (HA/DR Scaling)
```

---

## 2. Current State Assessment

### 2.1 Measure Your Baseline

Before scaling, establish baseline metrics:

```bash
#!/bin/bash
# Capture baseline metrics

# 1. Throughput (cases/sec)
THROUGHPUT=$(curl -s http://localhost:8080/api/metrics/cases-processed \
  | jq '.value / 60')  # Divide by 60 for per-second rate
echo "Throughput: $THROUGHPUT cases/sec"

# 2. P50 & P99 latencies
P50=$(curl -s http://localhost:8080/api/metrics/latency-p50 | jq '.value')
P99=$(curl -s http://localhost:8080/api/metrics/latency-p99 | jq '.value')
echo "P50 Latency: ${P50}ms, P99 Latency: ${P99}ms"

# 3. Resource utilization
CPU=$(top -bn1 | grep "Cpu(s)" | awk '{print 100-$8}' | cut -d'.' -f1)
MEM=$(free | grep Mem | awk '{print int($3/$2*100)}')
echo "CPU: ${CPU}%, Memory: ${MEM}%"

# 4. Database metrics
DB_CONN=$(curl -s http://localhost:8080/api/metrics/db-active-connections | jq '.value')
DB_LAG=$(curl -s http://localhost:8080/api/metrics/db-query-time-p99 | jq '.value')
echo "DB Connections: $DB_CONN, DB P99 Latency: ${DB_LAG}ms"

# 5. Queue depth
QUEUE=$(curl -s http://localhost:8080/api/metrics/pending-items | jq '.value')
echo "Work items pending: $QUEUE"

# Store baseline
cat > baseline_$(date +%Y%m%d).txt << EOF
Date: $(date)
Throughput: $THROUGHPUT cases/sec
P50 Latency: ${P50}ms
P99 Latency: ${P99}ms
CPU Usage: ${CPU}%
Memory Usage: ${MEM}%
DB Connections: $DB_CONN
DB Query Time P99: ${DB_LAG}ms
Pending Items: $QUEUE
EOF
```

### 2.2 Target State Definition

```yaml
# Define your scaling target
Scaling Goal:
  Target Throughput: 1000 cases/sec      # Cases to process per second
  Target P50 Latency: 50ms               # Median response time
  Target P99 Latency: 200ms              # 99th percentile
  Availability SLA: 99.95%               # Uptime requirement
  Max Case Volume: 500K                  # Total cases in system
  Budget: $100K                          # Annual scaling budget
```

---

## 3. Bottleneck Diagnosis & Solutions

### 3.1 CPU Bottleneck

**Symptom**: CPU utilization >80%, case processing slowing down

```
Diagnosis Flow:
         CPU > 80%?
            │
            ├─ Check: Is it sustained or spiky?
            │  ├─ Spiky (peaks then drops) → See 3.1.1 (GC pressure)
            │  └─ Sustained → See 3.1.2 (Task processing)
            │
            ├─ Check: Which thread consuming CPU?
            │  ├─ "yawl-engine" thread → Engine bottleneck
            │  ├─ "gc-thread" → GC pressure (3.1.1)
            │  └─ "async-executor" → Task processing (3.1.2)
            │
            └─ Check: Single core maxed or all cores?
               ├─ Single core → See 3.1.3 (Lock contention)
               └─ All cores → See 3.1.2 (Task processing)
```

#### 3.1.1 GC Pressure (CPU spikes every 30-60 sec)

**Diagnosis**:
```bash
# Verify GC is causing spikes
jstat -gc -h20 <pid> 1000

# Look for:
# - FGC (Full GC count) increasing frequently
# - GCT (GC time) > 10% of total time

# Example output (problematic):
#  S0C    S1C    S0U    S1U      EC       EU        OC         OU       PC       PU    FGC    FGCT    GCT
# 2560.0 2560.0 2560.0  0.0   40960.0 40960.0   102400.0  102400.0   8192.0  7680.0   23    1.234   3.456
#                          ↑ EC nearly full            ↑ OC nearly full              ↑ 23 FGCs!
```

**Solution**:

```yaml
# Option 1: Increase heap size (Quick fix)
# application.yml
server:
  tomcat:
    max-threads: 200
  servlet:
    session:
      timeout: 30m

# jvm.options or pom.xml <argLine>
-Xms8g -Xmx16g            # Increase heap 8GB → 16GB
-XX:+UseZGC               # Switch to ZGC (if not already)
-XX:InitiatingHeapOccupancyPercent=35  # GC tuning

# Option 2: Use Java 25 optimizations (Better long-term)
-XX:+UseCompactObjectHeaders      # 17% less memory per object
-XX:+UseStringDeduplication       # Reduce string memory
-XX:+UseCompactStrings            # Pack strings tighter

# Option 3: Reduce cache size (Balances memory vs. throughput)
engine:
  case:
    caching:
      max-size: 5000  # Reduce from 10000 if heap limited
```

**Expected Result**:
```
Before:  GC pause: 50-100ms, FGC every 30s, CPU spikes to 95%
After:   GC pause: <1ms (ZGC), FGC rare, CPU steady 60-70%
```

#### 3.1.2 Task Processing Bottleneck (CPU sustained >80%)

**Diagnosis**:
```bash
# Check which tasks are CPU-intensive
jcmd <pid> Thread.print | grep "yawl-engine\|task-executor"

# Profile hot methods
async-profiler.sh record -d 30 -f cpu.html <pid>

# Look for:
# - ValidateOrderCodelet taking 20% of CPU
# - ComplexJoinEvaluation taking 15% of CPU
# - ExternalServiceCall taking 25% of CPU
```

**Solution**:

```java
// Solution 1: Offload to async processing
// Before: Synchronous task execution
public void executeTask(YWorkItem item) {
    // Complex validation takes 100ms
    String result = validateOrder(item.getDataValue("order"));
    item.setDataValue("validationResult", result);  // Blocks engine
}

// After: Asynchronous with virtual threads
public void executeTask(YWorkItem item) {
    // Schedule on virtual thread (doesn't block carrier thread)
    Thread.ofVirtual().start(() -> {
        String result = validateOrder(item.getDataValue("order"));
        item.setDataValue("validationResult", result);
        notifyCompletion(item);
    });
    // Return immediately
}
```

**Configuration**:
```yaml
# application.yml
spring:
  task:
    execution:
      pool:
        core-size: auto              # Match CPU cores
        max-size: 2x                 # 2x for spiky load
        queue-capacity: 1000         # Buffer queue
    scheduling:
      pool:
        size: auto
      thread-name-prefix: yawl-async-

# Or increase Java executor threads
engine:
  executor:
    type: virtual-thread-pool
    parallelism: auto
    queue-size: 5000
```

**Expected Result**:
```
Before:  CPU: 95%, Throughput: 300 cases/sec
After:   CPU: 60%, Throughput: 800 cases/sec (2.7x improvement)
```

#### 3.1.3 Lock Contention (Single core maxed, others idle)

**Diagnosis**:
```bash
# Detect lock contention
jcmd <pid> Thread.print | grep -A5 "waiting to lock\|locked\|blocked"

# More detailed analysis
jcmd <pid> GC.heap_dump filename=heap.hprof
# Open heap.hprof in Eclipse MAT, look for:
# - Synchronization hotspots
# - YNetRunner.lock held for long time
# - YWorkItem.state accessed frequently
```

**Solution**:

```java
// Problem: All threads contend on single lock
public class YNetRunner {
    private synchronized void executeTask(YWorkItem item) {
        // All tasks wait for this lock!
        // At 1000 cases/sec, lock contention becomes bottleneck
    }
}

// Solution: Use ReentrantReadWriteLock or ConcurrentHashMap
public class YNetRunner {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void executeTask(YWorkItem item) {
        lock.readLock().lock();
        try {
            // Read operations (most common) don't block each other
            processWorkItem(item);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updateCaseState(CaseState state) {
        lock.writeLock().lock();
        try {
            // Write operations block everything (acceptable, rare)
            persistCaseState(state);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

**Expected Result**:
```
Before:  1 core at 100%, others idle, Throughput: 100 cases/sec
After:   4 cores at 60%, Throughput: 600 cases/sec (6x improvement)
```

---

### 3.2 Database Bottleneck

**Symptom**: Database query time > 50ms, connection pool full, lock waits

```
Diagnosis Flow:
      DB Latency > 50ms?
              │
              ├─ Check: Connection pool utilization
              │  ├─ >80% full → Add more connections (see 3.2.1)
              │  └─ <50% full → See 3.2.2 (Slow queries)
              │
              ├─ Check: Which queries are slow?
              │  ├─ SELECT (reporting) → Add read replicas (3.2.3)
              │  ├─ UPDATE (case state) → Denormalize (3.2.4)
              │  └─ JOIN (complex) → Rewrite query (3.2.2)
              │
              └─ Check: Disk I/O on database server
                 ├─ >90% → Upgrade storage (3.2.5)
                 └─ <50% → Query optimization needed (3.2.2)
```

#### 3.2.1 Connection Pool Saturation

**Diagnosis**:
```bash
# Check pool status
curl -s http://localhost:8080/api/metrics/db-pool-utilization | jq '.'
# Output: { "name": "HikariCP", "idle": 2, "active": 18, "size": 20 }
# Problem: Only 2 idle connections, pool is saturated!

# Check how long connections are held
curl -s http://localhost:8080/api/metrics/db-connection-time-p99
# Output: { "value": 150 }  # Waiting 150ms for connection!
```

**Solution**:

```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50        # Increase from 20
      minimum-idle: 10             # Keep some idle
      connection-timeout: 30000    # Timeout if can't get conn
      idle-timeout: 600000         # 10 min (from 5 min)
      max-lifetime: 1800000        # 30 min (from 10 min)
      leak-detection-threshold: 60000  # Warn if held >1 min
```

**Validation**:
```bash
# Monitor pool health
watch -n 1 'curl -s http://localhost:8080/api/metrics/db-pool-utilization | jq .'

# Expected: Idle connections always >5, Active <80% of pool size
```

#### 3.2.2 Slow Query Bottleneck

**Diagnosis**:
```bash
# Enable slow query log (PostgreSQL)
ALTER SYSTEM SET log_min_duration_statement = 100;  # 100ms
ALTER SYSTEM SET log_statement = 'all';
SELECT pg_reload_conf();

# Enable slow query log (MySQL)
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;  # 1 second

# Find slow queries
mysql -u root -e "SELECT * FROM mysql.slow_log WHERE query_time > 1;"
```

**Top slow queries in YAWL**:

```sql
-- Slow Query 1: Case history fetch (without indexes)
SELECT * FROM YWORKITEM
WHERE CASEID = ?
ORDER BY ITEMID DESC;
-- Fix: Add index
CREATE INDEX idx_workitem_caseid ON YWORKITEM(CASEID);

-- Slow Query 2: Resource allocation (full table scan)
SELECT * FROM YRESOURCE
WHERE STATUS = 'available'
AND CAPABILITY IN (?, ?, ?);
-- Fix: Add composite index
CREATE INDEX idx_resource_status_capability ON YRESOURCE(STATUS, CAPABILITY);

-- Slow Query 3: Case state check (complex join)
SELECT c.*, COUNT(w.itemid) as pending_count
FROM YNET c
LEFT JOIN YWORKITEM w ON c.netid = w.caseid
WHERE c.STATUS = 'running'
GROUP BY c.NETID;
-- Fix: Use materialized view or denormalize
CREATE MATERIALIZED VIEW v_case_pending_count AS
SELECT caseid, COUNT(*) as pending_count
FROM YWORKITEM
WHERE status = 'EXECUTING'
GROUP BY caseid;
```

**Solution**:

```bash
# Option 1: Add indexes
ALTER TABLE YWORKITEM ADD INDEX idx_caseid (CASEID);
ALTER TABLE YRESOURCE ADD INDEX idx_status_capability (STATUS, CAPABILITY);
ALTER TABLE YNET ADD INDEX idx_status (STATUS);

# Option 2: Partition large tables
ALTER TABLE YWORKITEM PARTITION BY RANGE (YEAR(CREATED_DATE)) (
  PARTITION p_2024 VALUES LESS THAN (2025),
  PARTITION p_2025 VALUES LESS THAN (2026),
  PARTITION p_future VALUES LESS THAN MAXVALUE
);

# Option 3: Archive old data
DELETE FROM YWORKITEM WHERE CREATED_DATE < DATE_SUB(NOW(), INTERVAL 1 YEAR);

# Verify improvements
ANALYZE TABLE YWORKITEM;
ANALYZE TABLE YNET;
```

#### 3.2.3 Read Bottleneck (Analytics/Reporting)

**Diagnosis**:
```bash
# Check read/write ratio
curl -s http://localhost:8080/api/metrics/db-read-write-ratio
# Output: { "reads": 800, "writes": 200 }  # 80% reads!

# Slow analytics queries
curl -s http://localhost:8080/api/metrics/slow-queries | jq '.[] | select(.type == "SELECT")'
```

**Solution**:

```yaml
# Set up read replicas
spring:
  datasource:
    primary:
      url: jdbc:postgresql://primary-db:5432/yawl
      username: yawl_user
      password: secret
    replica:
      url: jdbc:postgresql://replica-db:5432/yawl
      username: yawl_user
      password: secret

# Route reads to replica
# In your data access layer:
@Service
public class CaseRepository {

    @Transactional(readOnly = true)
    public Case findById(UUID id) {
        // Automatically routed to replica
        return db.queryOne("SELECT * FROM yawl_case WHERE id = ?", id);
    }

    @Transactional
    public void save(Case case) {
        // Routed to primary
        db.execute("UPDATE yawl_case SET ... WHERE id = ?", case.id);
    }
}
```

**Expected Improvement**:
```
Before:  Primary DB CPU: 80% (read + write)
After:   Primary DB CPU: 20% (write only), Replica CPU: 50% (read only)
Result:  3x more throughput on reporting queries
```

#### 3.2.4 High Update Frequency (Lock Contention)

**Diagnosis**:
```bash
# Check for row-level lock waits
SHOW OPEN TABLES WHERE in_use > 1;  # MySQL
# Output: Many tables with in_use=1 (locked)

# Check for update storm
SELECT SUM(rows_inserted + rows_updated) FROM mysql.innodb_trx;
```

**Solution**:

```sql
-- Denormalize frequently-updated fields
-- Before: Every case update requires 3 table updates
UPDATE yawl_case SET status = 'completed' WHERE id = ?;
UPDATE yawl_case_events SET status = 'completed' WHERE case_id = ?;
UPDATE yawl_resource_allocation SET status = 'idle' WHERE case_id = ?;

-- After: Single update on denormalized view
UPDATE yawl_case_summary SET status = 'completed' WHERE id = ?;

-- Use time-series database for high-frequency updates
-- Instead of: UPDATE yawl_case SET execution_time = execution_time + 1
-- Use TimescaleDB (PostgreSQL extension):
INSERT INTO yawl_case_metrics (case_id, metric_name, value)
VALUES (?, 'execution_time', 1)
ON CONFLICT (case_id, metric_name) DO UPDATE SET value = value + 1;
```

---

### 3.3 Memory Bottleneck

**Symptom**: OOM errors, GC taking >10% of CPU time, heap fragmentation

```
Diagnosis Flow:
      Memory > 85%?
          │
          ├─ Check: What's consuming memory?
          │  ├─ Case cache → Reduce cache size (3.3.1)
          │  ├─ Pending work items → Use stateless engine (3.3.2)
          │  └─ Logs/Buffers → Tune allocations (3.3.3)
          │
          └─ Check: Is GC pause > 50ms?
             ├─ YES → Switch to ZGC (3.3.4)
             └─ NO → Just increase heap slightly
```

#### 3.3.1 Case Cache Bloat

**Diagnosis**:
```bash
# Check cache hit rate
curl -s http://localhost:8080/api/metrics/case-cache-hit-rate | jq '.'
# Output: { "hit_rate": 0.45 }  # Only 45% hit rate!

# Check cache size
curl -s http://localhost:8080/api/metrics/case-cache-size | jq '.'
# Output: { "entries": 10000, "memory_mb": 1024 }  # 1GB for 10K cases!

# Identify frequently accessed cases (should be cached)
# vs rarely accessed cases (waste of memory)
```

**Solution**:

```yaml
# application.yml
engine:
  case:
    caching:
      type: lru              # Least Recently Used
      max-size: 5000         # Reduce from 10000
      max-weight-mb: 512     # Cap at 512MB
      expire-after-write-minutes: 5  # Evict old entries
      expire-after-access-minutes: 2

# Or use distributed cache (Redis)
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
  redis:
    host: localhost
    port: 6379
```

**Result**:
```
Before: Case cache: 1GB (10K entries), Hit rate: 45%
After:  Case cache: 256MB (2K entries), Hit rate: 72%
        → More memory available, higher hit rate on hot cases
```

#### 3.3.2 Pending Work Item Accumulation (Stateful)

**Diagnosis**:
```bash
# Check pending work items
curl -s http://localhost:8080/api/metrics/pending-work-items | jq '.'
# Output: { "count": 50000, "oldest_age_sec": 3600 }
# Problem: 50K items queued, oldest is 1 hour old!

# Check if input rate > processing rate
INPUT_RATE=$(curl -s http://localhost:8080/api/metrics/case-creation-rate | jq '.value')
PROC_RATE=$(curl -s http://localhost:8080/api/metrics/case-completion-rate | jq '.value')
echo "Input: $INPUT_RATE, Process: $PROC_RATE"
# If Input > Process, you have a processing bottleneck
```

**Solution**:

```yaml
# Option 1: Use Stateless Engine (recommended for high throughput)
spring:
  profiles:
    active: stateless

# Option 2: Add work queue sharding
engine:
  executor:
    work-queue:
      num-shards: 4           # Divide work across 4 queues
      queue-size: 10000       # Per shard (40K total)
      thread-pool-size: 8     # 2 threads per shard

# Option 3: Implement backpressure
engine:
  case:
    creation:
      rate-limit: 500         # Max 500 cases/sec
      queue-size: 10000       # Block if queue full
```

---

### 3.4 Disk I/O Bottleneck

**Symptom**: Disk queue depth >10, I/O wait time >50%, read/write latency >100ms

**Diagnosis**:
```bash
# Check disk I/O
iostat -x 1 5
# Look for:
# %util > 90%      (disk is saturated)
# r_await > 100ms  (read latency high)
# w_await > 50ms   (write latency high)

# Check which files are being accessed
lsof -p <yawl-pid> | grep -E "\.dat|\.log|\.sql" | wc -l
```

**Solution**:

```yaml
# Option 1: Move database to faster storage
# Before: HDD (7,200 RPM)
# After: SSD (NVMe) - 10-100x faster

# Option 2: Enable log buffering
spring:
  jpa:
    hibernate:
      jdbc:
        batch-size: 50         # Batch writes
        fetch-size: 50

# Option 3: Archive old data
# Move completed cases to archive storage
SELECT * FROM yawl_case
WHERE status = 'completed'
AND completed_date < DATE_SUB(NOW(), INTERVAL 1 YEAR)
INTO OUTFILE '/archive/old_cases.sql';

DELETE FROM yawl_case WHERE id IN (
  SELECT id FROM yawl_case
  WHERE status = 'completed'
  AND completed_date < DATE_SUB(NOW(), INTERVAL 1 YEAR)
);
```

---

### 3.5 Network Bottleneck (Distributed Deployments)

**Symptom**: RPC latency >100ms, packet loss, connection timeouts

**Diagnosis**:
```bash
# Check network latency between nodes
ping -c 10 node2.internal
# Should be <5ms for local network

# Check bandwidth utilization
iftop -i eth0

# Check for packet loss
mtr node2.internal

# Check TCP connection queue
netstat -s | grep "tcp.*dropped"
```

**Solution**:

```yaml
# Option 1: Use local cache + eventual consistency
# Reduce remote calls by caching aggressively
spring:
  cache:
    type: caffeine
    caffeine:
      spec: "maximumSize=10000,expireAfterWrite=5m"

# Option 2: Batch remote calls
engine:
  rpc:
    batch-size: 100            # Batch 100 requests together
    batch-timeout-ms: 100      # Or wait 100ms, whichever first

# Option 3: Use direct database connections (vs going through primary)
# If multiple data centers, replicate database locally
spring:
  datasource:
    url: jdbc:postgresql://local-replica:5432/yawl
```

---

## 4. Scaling Strategies by Bottleneck

### 4.1 CPU Scaling

```
Strategy 1: Vertical (Add cores to single instance)
  Cost: $2K per 4 cores
  Effort: Low (just resize VM)
  Limit: Max server size (64 cores ~$20K)
  Good for: <1K cases/sec

Strategy 2: Horizontal (Add instances)
  Cost: $500 per instance
  Effort: Medium (load balancer setup)
  Limit: Unlimited
  Good for: 1K-10K cases/sec

Strategy 3: Stateless (Event-driven)
  Cost: $500 per instance
  Effort: High (architecture change)
  Limit: Unlimited
  Good for: >1K cases/sec
```

### 4.2 Database Scaling

```
Strategy 1: Vertical (Bigger database server)
  Cost: $5K for 256GB memory
  Effort: Low (just resize)
  Limit: Practical ~1K cases/sec
  Good for: <100K cases

Strategy 2: Read Replicas
  Cost: $500 per replica
  Effort: Medium (setup replication)
  Improvement: 2-3x for read-heavy workload
  Good for: Heavy analytics/reporting

Strategy 3: Sharding (Partition data)
  Cost: $500 per shard
  Effort: Very High (application changes)
  Improvement: Linear with shards (N shards = N x throughput)
  Good for: >1M cases

Strategy 4: Event Sourcing (Stateless)
  Cost: $500 per instance + messaging
  Effort: Very High (architecture change)
  Improvement: 10-100x
  Good for: >10K cases/sec
```

---

## 5. Capacity Planning Examples

### Example 1: 1K Cases/Sec Growth Target

**Current**: 100 cases/sec, single instance
**Target**: 1,000 cases/sec, 6 months

**Plan**:
```
Month 1:   Optimize single instance
           - Enable Java 25 optimizations
           - Add indexes to database
           - Tune GC (ZGC)
           Expected: 150 cases/sec (+50%)

Month 2-3: Add database replicas
           - Set up PostgreSQL streaming replication
           - Route analytics to replica
           Expected: 250 cases/sec (+100%)

Month 4:   Add application instances
           - Set up load balancer
           - Add 2 more YAWL instances
           Expected: 600 cases/sec (+240%)

Month 5-6: Consider stateless engine
           - Prototype stateless engine
           - Set up event stream (Kafka)
           Expected: 1000+ cases/sec
```

**Cost**: ~$15K (infra) + $40K (engineering)

### Example 2: Reduce Latency from 500ms to 100ms

**Current**: P99 = 500ms, memory bloat, CPU spikes
**Target**: P99 = 100ms, stable performance

**Plan**:
```
Week 1:    GC optimization
           - Switch from G1GC to ZGC
           - Enable compact headers
           - Monitor GC pause times
           Expected: P99 = 300ms

Week 2:    Database optimization
           - Add missing indexes
           - Enable query caching
           Expected: P99 = 200ms

Week 3:    Application tuning
           - Reduce cache size (remove bloat)
           - Enable virtual threads
           - Parallel processing
           Expected: P99 = 100ms
```

**Cost**: ~$0 (configuration only)

---

## 6. Scaling Configuration Reference

### Java 25 Optimizations (Quick Wins)

```bash
# In pom.xml <argLine> or application start script
JAVA_OPTS="-XX:+UseCompactObjectHeaders \
           -XX:+UseZGC \
           -XX:InitiatingHeapOccupancyPercent=35 \
           -Djdk.virtualThreadScheduler.parallelism=auto \
           -XX:+UseStringDeduplication"
```

**Expected Impact**:
```
Memory:        -17%
GC Pause:      50-100ms → <1ms
Throughput:    +5-10%
```

### Database Tuning (PostgreSQL)

```sql
-- For 1K cases/sec, 100K total cases
ALTER SYSTEM SET shared_buffers = '16GB';
ALTER SYSTEM SET effective_cache_size = '48GB';
ALTER SYSTEM SET maintenance_work_mem = '4GB';
ALTER SYSTEM SET max_parallel_workers = 8;

-- Apply
SELECT pg_reload_conf();

-- Vacuum to update stats
VACUUM ANALYZE;
```

### Load Balancer Configuration (Nginx)

```nginx
upstream yawl_backend {
    least_conn;  # Use least connection algorithm
    server node1:8080 weight=1;
    server node2:8080 weight=1;
    server node3:8080 weight=1;
    keepalive 32;
}

server {
    listen 80;
    location / {
        proxy_pass http://yawl_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
}
```

---

## 7. Monitoring During Scaling

```bash
#!/bin/bash
# Monitor metrics during scaling test

# Create dashboard
cat > metrics.sh << 'EOF'
while true; do
  clear
  echo "=== YAWL Performance Dashboard ==="
  echo "Time: $(date)"
  echo ""

  # Throughput
  echo "Throughput: $(curl -s http://localhost:8080/api/metrics/case-throughput | jq '.value') cases/sec"

  # Latencies
  echo "P50 Latency: $(curl -s http://localhost:8080/api/metrics/latency-p50 | jq '.value') ms"
  echo "P99 Latency: $(curl -s http://localhost:8080/api/metrics/latency-p99 | jq '.value') ms"

  # Resource usage
  echo "CPU: $(top -bn1 | grep "Cpu(s)" | awk '{print 100-$8}')%"
  echo "Memory: $(free | grep Mem | awk '{print int($3/$2*100)}')%"

  # Database
  echo "DB Query Time P99: $(curl -s http://localhost:8080/api/metrics/db-latency-p99 | jq '.value') ms"
  echo "DB Pool Utilization: $(curl -s http://localhost:8080/api/metrics/db-pool-utilization | jq '.active / .size * 100') %"

  echo ""
  echo "GC Time: $(jstat -gc <pid> | tail -1 | awk '{print $NF}') sec"
  echo ""

  sleep 5
done
EOF

bash metrics.sh
```

---

## 8. Scaling Checklist

### Before Scaling

- [ ] Establish baseline metrics (throughput, latency, resource usage)
- [ ] Define target metrics (goal state)
- [ ] Identify bottleneck (CPU, DB, memory, disk, network)
- [ ] Choose scaling strategy (vertical, horizontal, architectural)
- [ ] Plan capacity and budget
- [ ] Schedule maintenance window (if needed)

### During Scaling

- [ ] Deploy changes incrementally (one at a time)
- [ ] Monitor metrics in real-time
- [ ] Be ready to rollback
- [ ] Document what works and what doesn't
- [ ] Communicate progress to stakeholders

### After Scaling

- [ ] Validate metrics meet targets
- [ ] Run load tests to confirm improvements
- [ ] Update runbooks and monitoring
- [ ] Train ops team on new configuration
- [ ] Plan next scaling phase

---

## References

- [Performance Matrix](PERFORMANCE_MATRIX.md)
- [Java 25 Optimization Guide](how-to/configure-zgc-compact-headers.md)
- [Database Tuning](reference/database-tuning.md)
- [Monitoring & Observability](how-to/operations/monitoring.md)
- [Cloud Deployment](how-to/deployment/cloud-native.md)

---

**Scaling is a data-driven process. Measure first, then optimize. Repeat until target is met.**
