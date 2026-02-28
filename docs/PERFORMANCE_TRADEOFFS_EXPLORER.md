# YAWL Performance Trade-off Explorer

**Time to Complete**: 10-12 minutes
**Goal**: Make informed trade-off decisions about latency, throughput, cost, and consistency

---

## How to Use This Guide

Performance is about **choices**. You can't have everything: low cost AND low latency AND high throughput AND strong consistency.

Below are **5 targeted questions** about your constraints. Based on your answers, you'll see concrete trade-off scenarios with:
- Metrics (latency, throughput, cost)
- Configuration examples
- When each choice is right
- Tuning parameters for your scenario

---

## SECTION 1: YOUR PRIMARY CONSTRAINT

### Question 1: What's Your #1 Performance Constraint?

Rank what matters most to you (pick ONE):

- [ ] **A** — Latency (response time must be < 100ms per case)
- [ ] **B** — Throughput (need to process millions of cases)
- [ ] **C** — Cost (budget is tight, need to minimize spend)
- [ ] **D** — Consistency (data must always be correct, even if slow)
- [ ] **E** — Availability (uptime > 99.9%, zero errors)

**💡 Help**: Most systems optimize for ONE primary metric. Others are secondary.

---

### Question 2: What's Your Expected Case Volume?

How many cases per day in production?

- [ ] **A** — 1K-10K/day (11-115 per minute)
- [ ] **B** — 10K-100K/day (115-1,000 per minute)
- [ ] **C** — 100K-1M/day (1,000-10,000 per minute)
- [ ] **D** — 1M-10M/day (10K-100K per minute)
- [ ] **E** — 10M+/day (100K+ per minute)

**💡 Help**: Check your current volume + expected growth (12 months).

---

### Question 3: What's Your Peak Concurrent Execution?

How many cases run **simultaneously** at peak?

- [ ] **A** — 10-100 concurrent
- [ ] **B** — 100-1,000 concurrent
- [ ] **C** — 1,000-10,000 concurrent
- [ ] **D** — 10,000-100,000 concurrent
- [ ] **E** — 100,000+ concurrent (mega-scale)

**💡 Help**: Estimate: (cases/day) × (avg case duration in minutes) ÷ 1440 minutes.

---

### Question 4: What's Your Acceptable P95 Latency?

How long can a case take before it's "too slow"?

- [ ] **A** — <50ms (real-time, sub-second)
- [ ] **B** — 50-200ms (fast interactive)
- [ ] **C** — 200-1000ms (acceptable interactive)
- [ ] **D** — 1-10 seconds (background processing)
- [ ] **E** — 10+ seconds (batch jobs)

**💡 Help**: Interactive = immediate feedback to user. Batch = user doesn't wait.

---

### Question 5: How Important Is Data Consistency?

What can you accept if things fail?

- [ ] **A** — Strong consistency (no stale reads, ever)
- [ ] **B** — Read-your-writes (you see your changes immediately)
- [ ] **C** — Eventual consistency (consistent within 1-5 seconds)
- [ ] **D** — Weak consistency (5-60 second lags acceptable)
- [ ] **E** — Don't care (if data is wrong, just replay)

**💡 Help**: Strong = transactional database. Eventual = replicated cache. Weak = approximations OK.

---

---

## YOUR PERSONALIZED PERFORMANCE SCENARIOS

Find your scenario below. Each shows:
- Recommended configuration
- Expected metrics (latency, throughput, cost)
- Tuning parameters
- Trade-offs vs other scenarios
- Example deployment

---

## 🟢 SCENARIO 1: Real-Time Low Latency (< 100ms P95)

**Recommended if you answered:**
- Q1: A (latency is critical)
- Q2: A-B (10K-100K cases/day)
- Q3: A-B (100-1K concurrent)
- Q4: A-B (<200ms)
- Q5: B (read-your-writes)

### Performance Targets

| Metric | Target | How Achieved |
|--------|--------|--------------|
| **P50 Latency** | <30ms | In-memory cache + optimized queries |
| **P95 Latency** | <100ms | No full table scans |
| **P99 Latency** | <200ms | Outliers acceptable |
| **Throughput** | 5K-10K cases/sec | High-concurrency Java 25 |
| **Cost** | $3-8K/month | Compute > storage |

### Recommended Configuration

```yaml
# yawl-engine-low-latency.yaml
engine:
  type: stateless  # No database round-trip per case
  execution_timeout: 5000  # 5 seconds max

cache:
  type: redis
  ttl_seconds: 60
  max_entries: 1000000
  eviction_policy: lru

database:
  connection_pool:
    max_size: 100
    min_idle: 50
    connection_timeout_ms: 500
  query_timeout_ms: 1000
  prepared_statements: true  # Avoid parse time

thread_pool:
  type: virtual_threads  # Java 25 feature
  queue_size: 10000

jvm:
  gc: ZGC  # Sub-millisecond pauses
  heap: 16G
  direct_memory: 8G
```

### Configuration Details

**1. Enable virtual threads (Java 25):**
```java
// YawlEngineConfig.java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// Each case runs on lightweight virtual thread
// 100K concurrent cases = 100K virtual threads (light)
// vs 100K platform threads (impossible, too heavy)
```

**2. Redis caching for hot data:**
```java
// CacheManager.java
@Cacheable(value = "workflows", key = "#spec.id")
public YSpecification getWorkflow(String specId) {
    return repository.findSpec(specId);
}

// Cache hit: <5ms. Miss: <50ms (DB query)
```

**3. Prepared statements to reduce parse time:**
```sql
-- Prepare once, reuse many times
PREPARE get_case AS SELECT * FROM cases WHERE id = $1;
EXECUTE get_case('case-123');  -- 0.5ms
```

**4. Query optimization:**
```sql
-- Index on frequently-queried columns
CREATE INDEX idx_cases_state ON cases(state) WHERE active=true;
CREATE INDEX idx_cases_owner ON cases(assigned_to);

-- Avoid full table scans
-- Use EXPLAIN to verify query plans
EXPLAIN ANALYZE SELECT * FROM cases WHERE state = 'running';
```

**5. Monitor tail latency:**
```bash
# Percentile latencies matter more than average
# P95/P99 > average reveals outliers
prometheus:
  metrics:
    - latency_p50
    - latency_p95
    - latency_p99
    - latency_max
```

### Trade-offs

| vs Scenario | Trade | Worth It? |
|------------|-------|----------|
| vs High Throughput | Limited to 10K cases/day | ✅ Yes, acceptable |
| vs Low Cost | $3-8K/month | ⚠️ Expensive, but necessary |
| vs Strong Consistency | Eventual consistency (1s lag) | ✅ Yes, acceptable |
| vs 99.99% Availability | 99.9% is max | ✅ Fine for most |

### When to Use

- **Real-time dashboards** — User sees updates immediately
- **Mobile apps** — Network latency + server latency must sum < 200ms
- **Algorithmic trading** — Milliseconds = money
- **Payment processing** — Fast feedback critical
- **Chat/messaging** — Users notice >200ms delay

### Example Metrics (Production)

```
Case Volume: 50K cases/day
Peak Concurrent: 500 cases

Latency:
  P50: 18ms
  P95: 87ms
  P99: 145ms
  Max: 312ms

Throughput: 3.5 cases/sec
Cost: $5K/month (3 servers + Redis + monitoring)
```

### Links

- [Performance Tuning Guide](../PERFORMANCE.md)
- [Redis Caching Setup](../how-to/caching-redis.md)
- [Database Query Optimization](../how-to/database-optimization.md)
- [Virtual Threads Guide](../explanation/Java25-virtual-threads.md)

---

## 🔵 SCENARIO 2: High Throughput (Millions of Cases)

**Recommended if you answered:**
- Q1: B (throughput matters most)
- Q2: C-D (100K-1M+ cases/day)
- Q3: C-D (1K-10K+ concurrent)
- Q4: D-E (1-10+ seconds acceptable)
- Q5: D (eventual consistency OK)

### Performance Targets

| Metric | Target | How Achieved |
|--------|--------|--------------|
| **Throughput** | 50K-100K cases/sec | Distributed execution |
| **P50 Latency** | 200-500ms | Batch processing |
| **P95 Latency** | 1-5 seconds | Acceptable for batch |
| **Cost** | $8-20K/month | Storage > compute |
| **Data Consistency** | Eventual (1-10s lag) | Replication delays |

### Recommended Configuration

```yaml
# yawl-engine-high-throughput.yaml
engine:
  type: stateless
  batch_size: 1000  # Process in batches
  execution_timeout: 30000  # 30 seconds

database:
  connection_pool:
    max_size: 200  # Many concurrent connections
    min_idle: 100
  batch_insert_size: 1000
  async_writes: true  # Buffer writes
  wal_buffer: 512MB

partitioning:
  enabled: true
  strategy: by_date  # Partition by date
  partitions: 30  # One per day

index_strategy:
  partial: true  # Only index active cases
  sparse: true   # Skip nulls

cache:
  type: none  # Skip cache for throughput

jvm:
  gc: G1GC  # Low pause time
  heap: 64G
  direct_memory: 16G
  gc_threads: 16  # Parallel GC
```

### Configuration Details

**1. Enable partitioning for throughput:**
```sql
-- Partition by date (cases created on date X go in partition X)
CREATE TABLE cases_2026_02 PARTITION OF cases
  FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- Each partition is independent: faster scans
-- Oldest partitions can be archived/deleted
```

**2. Batch inserts instead of single-row:**
```java
// BAD: 1K inserts = 1K DB round-trips
for (WorkflowCase c : cases) {
    database.insert(c);  // SLOW
}

// GOOD: 1 insert = 1 DB round-trip
List<WorkflowCase> batch = new ArrayList<>();
for (WorkflowCase c : cases) {
    batch.add(c);
    if (batch.size() == 1000) {
        database.insertBatch(batch);  // 1K rows in 1 call
        batch.clear();
    }
}
```

**3. Asynchronous writes:**
```java
// Don't wait for write to complete
// Buffer in memory, flush periodically
executor.submit(() -> {
    database.flushBuffer();  // Async
});
```

**4. Database tuning for high volume:**
```sql
-- Increase buffer size
ALTER SYSTEM SET shared_buffers = '32GB';
ALTER SYSTEM SET effective_cache_size = '96GB';
ALTER SYSTEM SET work_mem = '512MB';

-- Async commit (trade durability for speed)
ALTER SYSTEM SET synchronous_commit = 'off';

-- Reload
SELECT pg_ctl_reload_conf();
```

**5. Monitor throughput metrics:**
```bash
prometheus:
  metrics:
    - cases_per_second
    - batch_latency
    - queue_depth
    - database_write_lag
```

### Trade-offs

| vs Scenario | Trade | Worth It? |
|------------|-------|----------|
| vs Low Latency | 200ms-5s latency (slow) | ✅ Yes, batch OK |
| vs Low Cost | $8-20K/month | ⚠️ Expensive |
| vs Strong Consistency | Eventual consistency | ✅ Acceptable |
| vs 99.99% Availability | Asynchronous = data loss risk | ❌ Trade durability |

### When to Use

- **Batch reporting** — Process 1M records overnight
- **Log ingestion** — Ingest 100K logs/sec
- **Analytics** — Churn through terabytes of data
- **Search indexing** — Crawl and index massive datasets

### Example Metrics (Production)

```
Case Volume: 500K cases/day (5.8 cases/sec average)
Peak Concurrent: 5K cases

Throughput: 75 cases/sec (steady state)
Peak Throughput: 150 cases/sec (burst)

Latency:
  P50: 250ms
  P95: 1.2s
  P99: 3.5s

Cost: $15K/month (large DB, 5 servers, no caching)
```

### Links

- [Partitioning Strategy](../how-to/database-partitioning.md)
- [Batch Processing Guide](../how-to/batch-processing.md)
- [G1GC Tuning](../how-to/gc-tuning-g1.md)

---

## 🟡 SCENARIO 3: Cost-Optimized (Minimize Spend)

**Recommended if you answered:**
- Q1: C (cost is critical)
- Q2: A-B (10K-100K cases/day)
- Q3: A-B (100-1K concurrent)
- Q4: D-E (1-10+ seconds)
- Q5: D-E (eventual/weak consistency)

### Performance Targets

| Metric | Target | How Achieved |
|--------|--------|--------------|
| **Cost** | <$1K/month | Serverless + auto-scale to zero |
| **Throughput** | Scales with traffic | Auto-scale based on load |
| **Latency** | 200ms-5s | Cold starts acceptable |
| **Availability** | 99.5% | Shared infrastructure |

### Recommended Configuration

```yaml
# yawl-engine-cost-optimized.yaml
engine:
  type: stateless  # Serverless
  auto_scale_to_zero: true  # No cost when idle

deployment:
  platform: lambda  # AWS Lambda
  memory: 512MB  # Minimum needed
  timeout: 15*60  # 15 minutes max

database:
  # Use cloud-managed DB (cheaper than self-managed)
  type: rds_serverless_v2
  auto_pause: true  # Pause when idle
  auto_pause_delay_minutes: 5

cache:
  type: none  # Skip cache, increases latency

jvm:
  heap: 256MB  # Minimal heap
  gc: G1GC
  gc_threads: 2  # Single core
```

### Configuration Details

**1. Deploy to AWS Lambda (auto-scale to zero):**
```terraform
# infrastructure/lambda.tf
resource "aws_lambda_function" "yawl" {
  function_name = "yawl-stateless"
  runtime = "java21"
  memory_size = 512  # MB
  timeout = 900  # 15 minutes

  # Cost: $0.0000002 per invocation
  # 1M cases/month = $0.20 compute
}

# Scales from 0 → N automatically
# You pay $0 when not processing
```

**2. Use RDS Serverless (auto-pause):**
```terraform
# infrastructure/rds.tf
resource "aws_rds_cluster" "yawl" {
  engine = "aurora-postgresql"
  serverless_v2_scaling_config {
    max_capacity = 0.5  # 0.5 Aurora Capacity Units
    min_capacity = 0.5
    auto_pause = true
    auto_pause_delay_seconds = 300  # 5 minutes
  }

  # Cost: $0 when paused
  # Cost: ~$100/month when running
}
```

**3. Use S3 for case history (dirt cheap):**
```java
// Store case results in S3 instead of database
S3Object resultObject = new S3Object();
resultObject.setKey("cases/2026/02/case-123.json");
resultObject.setContent(caseResult.toJson());
s3Client.putObject("yawl-results", resultObject);

// S3 cost: $0.023 per GB stored
// 1M cases × 1KB = 1GB = $0.023/month
```

**4. Minimal JVM tuning:**
```bash
# Keep Java GC simple (low memory = fewer options)
java -Xms256m -Xmx256m \
  -XX:+UseG1GC \
  -XX:InitiatingHeapOccupancyPercent=35 \
  -XX:+DisableExplicitGC \
  YawlStatelessFunction
```

**5. Monitor cost vs utilization:**
```bash
# Cost breakdown
cloudwatch:
  metrics:
    - lambda_invocations: "× $0.0000002 = cost"
    - rds_capacity_units: "× $0.06/hour = cost"
    - data_transfer_gb: "× $0.09 = cost"
```

### Trade-offs

| vs Scenario | Trade | Worth It? |
|------------|-------|----------|
| vs Low Latency | 500ms-5s latency | ✅ Yes, acceptable for batch |
| vs High Throughput | Limited to 1K concurrent | ✅ Yes, scales auto |
| vs Strong Consistency | Eventual consistency | ✅ Yes, acceptable |
| vs 99.99% Availability | 99.5% uptime (shared infra) | ✅ Yes, cost worth it |

### When to Use

- **Startup workflows** — Every dollar counts
- **Seasonal spikes** — Pay only during peak season
- **Development/staging** — Non-production workloads
- **Batch jobs** — Run once per day, hibernate otherwise
- **IoT sensor processing** — Bursty, irregular traffic

### Example Costs (Detailed Breakdown)

```
Case Volume: 30K cases/day (11.6 cases/sec)
Peak Concurrent: 50 cases

Lambda:
  Cost: 30K cases/day × $0.0000002 = $6/month
  Memory: 512MB
  Timeout: 15 min

RDS Serverless:
  Cost: 8 hours/day × 0.5 ACU × $0.06/hour = $14.40/day = $432/month
  (Or $0 if database pauses when idle)

S3 Storage:
  Cost: 30K cases × 1KB = 30GB × $0.023 = $0.69/month

Data Transfer:
  Cost: Minimal (internal AWS)

Total: ~$440/month (mostly RDS Serverless)
```

**vs Other Scenarios**:
- Scenario 1 (Low Latency): **10x cheaper** ($500/mo vs $5K/mo)
- Scenario 2 (High Throughput): **20x cheaper** ($440/mo vs $15K/mo)
- Scenario 4 (High Availability): **3x cheaper** ($440/mo vs $1.5K/mo)

### Links

- [AWS Lambda Deployment](../how-to/deployment/aws-lambda.md)
- [RDS Serverless Setup](../how-to/deployment/rds-serverless.md)
- [Cost Optimization Tips](../how-to/cost-optimization.md)
- [S3 Storage Configuration](../how-to/s3-storage-configuration.md)

---

## 🔴 SCENARIO 4: High Consistency (ACID Guarantees)

**Recommended if you answered:**
- Q1: D (consistency is critical)
- Q2: A-B (10K-100K cases/day)
- Q3: A-B (100-1K concurrent)
- Q4: B-C (50-1000ms)
- Q5: A (strong consistency, no stale reads)

### Performance Targets

| Metric | Target | How Achieved |
|--------|--------|--------------|
| **Consistency** | ACID (no data loss) | Synchronous replication |
| **Latency** | 100-500ms | Write-through cache |
| **Throughput** | 1K-5K cases/sec | Limited by sync writes |
| **Availability** | 99.5% | Replicated database |
| **Cost** | $3-8K/month | Multiple DB replicas |

### Recommended Configuration

```yaml
# yawl-engine-strong-consistency.yaml
engine:
  type: stateful  # Must persist to DB
  isolation_level: serializable  # ACID

database:
  replication:
    mode: synchronous  # Wait for replicas to acknowledge
    replicas: 2+
    quorum_size: majority  # Requires 2 out of 3

  transaction:
    isolation: serializable  # ACID level 4
    durability: true  # fsync to disk

  backup:
    type: continuous_archiving
    archive_timeout: 300  # 5 minutes

cache:
  write_through: true  # Write to cache + DB
  invalidation: immediate  # No stale data

jvm:
  gc: ZGC  # Predictable, low pause
  heap: 16G
```

### Configuration Details

**1. Enable synchronous replication:**
```sql
-- PostgreSQL: Primary waits for replicas to acknowledge
ALTER SYSTEM SET synchronous_commit = 'remote_apply';
ALTER SYSTEM SET synchronous_standby_names = 'standby1, standby2';

-- Any write waits for 2 replicas to confirm
-- Trade latency for durability
SELECT pg_ctl_reload_conf();
```

**2. Use serializable isolation:**
```java
// Force serializable isolation (no dirty reads, phantom reads)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void updateCase(WorkflowCase workflowCase) {
    // No two transactions can see inconsistent state
    repository.update(workflowCase);
}
```

**3. Write-through caching:**
```java
// Update both cache and database
public void updateCaseSync(WorkflowCase workflowCase) {
    // 1. Write to database first
    database.update(workflowCase);

    // 2. Update cache
    cache.put(workflowCase.id, workflowCase);

    // Client sees consistent data
}
```

**4. Continuous backup/recovery:**
```bash
# PostgreSQL: Archive WAL continuously (for point-in-time recovery)
wal_level = logical
archive_mode = on
archive_command = 'cp %p /mnt/backup/wal_archive/%f'
archive_timeout = 300  # Force archive every 5 minutes

# Recover to any point in time
pg_recover_to_timeline = latest
recovery_target_timeline = latest
recovery_target_lsn = '0/CAFEBABE'  # Recover to specific LSN
```

**5. Monitor consistency/durability:**
```bash
prometheus:
  metrics:
    - sync_replication_lag  # Must be <100ms
    - transaction_lock_time  # Serialization cost
    - cache_hit_rate  # Write-through overhead
    - wal_archive_delay  # Backup lag
```

### Trade-offs

| vs Scenario | Trade | Worth It? |
|------------|-------|----------|
| vs Low Latency | 100-500ms instead of <100ms | ⚠️ Slower |
| vs High Throughput | 1K/sec instead of 50K/sec | ⚠️ Limited |
| vs Low Cost | $3-8K/month instead of $440 | ❌ Expensive |
| vs 99.99% Availability | 99.5% (sync replication has lag) | ⚠️ Lower |

### When to Use

- **Financial transactions** — No duplicate charges, no lost payments
- **Healthcare records** — Patient data must be accurate
- **Legal documents** — Audit trail must be immutable
- **Inventory management** — No overselling
- **Regulatory compliance** — HIPAA, PCI-DSS, SOX require audit trails

### Example Metrics (Production)

```
Case Volume: 50K cases/day
Peak Concurrent: 300 cases

Throughput: 2K cases/sec (limited by sync replication)

Latency:
  P50: 85ms (write to primary)
  P95: 210ms (wait for replicas)
  P99: 450ms (worst case)

Durability: 100% (no data loss)
  - Write must complete on primary + 2 replicas
  - Continuous backup to archive
  - Point-in-time recovery possible

Cost: $5.5K/month (primary + 2 replicas, 3 servers)
```

### Links

- [PostgreSQL Replication](../how-to/deployment/postgres-replication.md)
- [ACID Transactions Guide](../how-to/acid-transactions.md)
- [Backup & Recovery](../how-to/disaster-recovery.md)

---

## 🟠 SCENARIO 5: Maximum Availability (99.99% Uptime)

**Recommended if you answered:**
- Q1: E (availability is critical)
- Q2: B-C (100K-1M cases/day)
- Q3: C-D (1K-10K concurrent)
- Q4: C-D (200ms-10s)
- Q5: C (eventual consistency OK)

### Performance Targets

| Metric | Target | How Achieved |
|--------|--------|--------------|
| **Availability** | 99.99% (52 min downtime/year) | Multi-region failover |
| **RTO** (Recovery Time) | < 1 minute | Automatic failover |
| **RPO** (Recovery Point) | < 5 seconds | Replication with lag |
| **Latency** | 100-500ms | Trade for availability |
| **Cost** | $8-15K/month | Multiple regions |

### Recommended Configuration

```yaml
# yawl-engine-max-availability.yaml
deployment:
  regions: 3+  # US, EU, APAC
  active_active: true  # All regions process cases

database:
  replication:
    mode: asynchronous  # Fast writes, eventual consistency
    lag_threshold: 5000  # Alert if > 5 seconds

  failover:
    automatic: true
    detect_down_time: 10000  # 10 seconds
    promote_delay: 5000  # 5 seconds

  backup:
    strategy: continuous  # Always have backup ready

load_balancer:
  type: global
  routing: latency  # Route to nearest region
  health_checks: aggressive  # Check every 10s

health_checks:
  primary: 10s  # Check every 10 seconds
  database: 10s
  cache: 10s
  cascade_threshold: 2  # 2 consecutive failures = failover
```

### Configuration Details

**1. Multi-region setup (Terraform):**
```terraform
# Deploy to 3 regions simultaneously
module "us_east" {
  source = "./modules/yawl-region"
  region = "us-east-1"
  primary = true
}

module "us_west" {
  source = "./modules/yawl-region"
  region = "us-west-2"
  primary = false
}

module "eu_west" {
  source = "./modules/yawl-region"
  region = "eu-west-1"
  primary = false
}
```

**2. Global load balancer (Route53):**
```terraform
# Route53 health checks + latency-based routing
resource "aws_route53_health_check" "us_east" {
  ip_address = aws_lb.us_east.dns_name
  port = 443
  type = "HTTPS"
  failure_threshold = 2
  measure_latency = true  # Route to nearest
}

# If us_east fails, automatically route to us_west
resource "aws_route53_record" "yawl" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "yawl.example.com"
  type    = "A"
  set_identifier = "us-east"
  failover_routing_policy {
    type = "PRIMARY"
  }
  alias {
    name = aws_lb.us_east.dns_name
    zone_id = aws_lb.us_east.zone_id
    evaluate_target_health = true
  }
}
```

**3. Database multi-master replication:**
```sql
-- All 3 regions can write
-- Writes replicate asynchronously to other regions
-- Conflict resolution: last-write-wins by timestamp

CREATE PUBLICATION us_east_pub FOR ALL TABLES;
CREATE SUBSCRIPTION us_west_sub CONNECTION
  'dbname=yawl host=us-east-1.db.example.com'
  PUBLICATION us_east_pub;
```

**4. Health check cascade:**
```java
// Check system health, failover if needed
public HealthStatus checkHealth() {
    boolean api = checkApiHealth();  // < 100ms
    boolean db = checkDatabaseHealth();  // < 100ms
    boolean cache = checkCacheHealth();  // < 100ms

    int failures = countBooleans(!api, !db, !cache);

    if (failures >= 2) {
        // 2+ failures = cascade failure, promote replica
        promoteReplica();
        notifyOps();
        return HealthStatus.DEGRADED;
    }
    return HealthStatus.HEALTHY;
}
```

**5. Monitor availability:**
```bash
prometheus:
  metrics:
    - uptime_percent
    - failover_count
    - replication_lag_seconds
    - region_response_time  # Latency per region
    - error_rate_by_region
```

### Trade-offs

| vs Scenario | Trade | Worth It? |
|------------|-------|----------|
| vs Low Latency | 100-500ms instead of <100ms | ⚠️ Slower |
| vs Low Cost | $8-15K/month instead of $440 | ❌ 20x expensive |
| vs Strong Consistency | Eventual consistency | ✅ Acceptable |
| vs High Throughput | Limits to 5K/sec | ✅ Acceptable |

### When to Use

- **Mission-critical systems** — Banks, hospitals
- **Revenue-generating** — Every minute down = money lost
- **High SLA requirements** — 99.99% in contract
- **Global operations** — Customers in multiple regions

### Example Deployment (3-Region)

```
┌─────────────────────────────────────────┐
│        Global Load Balancer             │
│       (Route53, health checks)          │
└──┬──────────────────────────────────────┘
   │
   ├─ US-EAST (Primary)      ├─ US-WEST (Standby)    ├─ EU (Standby)
   │  - YAWL (3 pods)        │  - YAWL (2 pods)      │  - YAWL (2 pods)
   │  - PostgreSQL (master)  │  - PostgreSQL (slave) │  - PostgreSQL (slave)
   │  - Redis (primary)      │  - Redis (replica)    │  - Redis (replica)
   │  Cost: $4K/month        │  Cost: $2.5K/month    │  Cost: $2.5K/month
   │                         │                       │
   └─────────────────────────────────────────────────┘
            Replication lag: 1-5 seconds
            Failover time: 30-60 seconds
            Availability: 99.99% (52 min/year down)
            Total cost: ~$9K/month
```

### Example Metrics (Production)

```
Availability: 99.99% uptime
  - Downtime budget: 52 minutes per year
  - Actual downtime last year: 18 minutes (well within budget)

Failover Events: 2 in last 12 months
  - Avg failover time: 45 seconds
  - Data loss: 0 (RPO met)

Replication Lag: 1-5 seconds
  - Acceptable for eventual consistency
  - Monitored and alerted on

Cost: $9K/month (3 regions)
```

### Links

- [Multi-Region Architecture](../explanation/enterprise-cloud.md)
- [PostgreSQL Multi-Master](../how-to/deployment/postgres-replication.md)
- [Global Load Balancing](../how-to/deployment/global-load-balancer.md)
- [Disaster Recovery Plan](../how-to/disaster-recovery.md)

---

---

## Quick Trade-off Summary Table

| Scenario | Latency | Throughput | Cost | Consistency | Availability |
|----------|---------|-----------|------|-------------|--------------|
| **1. Low Latency** | <100ms ✅ | 10K/sec | $5K | Eventual | 99.5% |
| **2. High Throughput** | 1-5s | 100K/sec ✅ | $15K | Eventual | 99.9% |
| **3. Cost Optimized** | 500ms-5s | Variable | $440 ✅ | Eventual | 99.5% |
| **4. Strong Consistency** | 100-500ms | 1-5K/sec | $5.5K | ACID ✅ | 99.5% |
| **5. Max Availability** | 100-500ms | 5K/sec | $9K | Eventual | 99.99% ✅ |

---

## 🎯 Decision Flowchart

```
Start
  │
  ├─ Which constraint is #1?
  │   ├─ Latency → SCENARIO 1 (Low Latency)
  │   ├─ Throughput → SCENARIO 2 (High Throughput)
  │   ├─ Cost → SCENARIO 3 (Cost Optimized)
  │   ├─ Consistency → SCENARIO 4 (Strong Consistency)
  │   └─ Availability → SCENARIO 5 (Max Availability)
  │
  ├─ What's your case volume?
  │   (Determines if scenario is feasible)
  │
  └─ Check trade-offs in summary table
      (Any deal-breakers?)
```

---

## ⚠️ Common Mistakes

### "We'll start with max availability"
❌ **Problem**: Over-engineered. 3-region setup adds 10x cost. Most systems don't need 99.99%.
✅ **Solution**: Start with Scenario 3 (cost-optimized). Migrate to Scenario 5 when justified by revenue.

### "We'll optimize for all metrics"
❌ **Problem**: Impossible. Low latency + high throughput + low cost = pick 2.
✅ **Solution**: Pick your #1 constraint. Accept trade-offs on others.

### "Replication lag doesn't matter"
❌ **Problem**: Asynchronous replication = data loss on failure.
✅ **Solution**: Use Scenario 4 (sync replication) if loss is unacceptable.

### "We don't need monitoring"
❌ **Problem**: Can't see replication lag, failover, or performance degradation.
✅ **Solution**: Add monitoring from day 1. Set alerts for each scenario's critical metric.

---

## 📊 ROI Example: Choosing the Right Scenario

**Scenario**: E-commerce company, 100K orders/day

| Scenario | Cost/month | Revenue Impact | Why? |
|----------|-----------|-----------------|------|
| **Scenario 2 (High Throughput)** | $15K | +$50K/mo (faster processing) | Bottleneck was order processing |
| **Scenario 1 (Low Latency)** | $5K | +$10K/mo (faster checkout) | Users abandon slow checkout |
| **Scenario 3 (Cost)** | $440 | -$30K/mo (downtime costs) | Can't afford to be down |
| **Scenario 5 (Max Availability)** | $9K | +$50K/mo (zero downtime) | Revenue directly tied to uptime |

**Winner**: Scenario 5 (Max Availability) → ROI = +$41K/month revenue - $9K cost = **+$32K/month net gain**

---

## 📚 Learn More

- **[Performance Tuning Guide](../PERFORMANCE.md)** — Deep dive on each metric
- **[Database Optimization](../how-to/database-optimization.md)** — Query tuning, indexing
- **[JVM Tuning Guide](../how-to/jvm-tuning.md)** — GC, memory, threads
- **[Cost Optimization](../how-to/cost-optimization.md)** — Reduce spend without losing performance
- **[Deployment Calculator](./DEPLOYMENT_CALCULATOR.md)** — Another lens on architecture

---

## 🆘 Need Help Choosing?

1. **Identify your #1 constraint** from Question 1 above
2. **Find matching scenario** (1-5)
3. **Review the trade-offs** — Any deal-breakers?
4. **Check cost estimate** — Within budget?
5. **Try a prototype** — Test recommended config
6. **Monitor key metrics** — Verify actual performance

**Still stuck?** See [FAQ & Common Issues](./FAQ_AND_COMMON_ISSUES.md) or ask the community.
