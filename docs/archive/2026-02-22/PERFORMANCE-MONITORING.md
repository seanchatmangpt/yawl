# YAWL Performance Monitoring - Library Update Impact Analysis

## Quick Start

```bash
# Interactive menu
./run-performance-monitoring.sh

# Or manual workflow:
# 1. Before library update
mvn test -Dtest=EnginePerformanceBaseline

# 2. Update libraries in pom.xml

# 3. After library update
mvn clean compile
mvn test -Dtest=EnginePerformanceBaseline

# 4. Compare results in performance-reports/
```

## Purpose

Monitor and quantify performance impacts when updating YAWL dependencies:
- **Hibernate ORM** (6.5.1 → 6.6.42) - Database query performance
- **Jakarta EE** (5.0 → 6.1) - Servlet and API changes
- **Log4j** (2.23.1 → 2.25.3) - Logging performance
- **Jackson** (2.17.0 → 2.18.3) - JSON serialization
- **HikariCP** (5.1.0 → 7.0.2) - Connection pooling
- **Java Platform** (21 → 25) - JVM improvements

## Performance Metrics Tracked

### 1. Engine Startup Time
- **Target**: < 60 seconds
- **What**: Time from JVM start to first case launch
- **Libraries Impacted**: Hibernate, Spring Boot, database drivers
- **Critical For**: Production deployments, container startup

### 2. Case Launch Latency
- **Target p95**: < 500ms
- **What**: Time to create new workflow case
- **Libraries Impacted**: Hibernate (entity creation), HikariCP (connections)
- **Critical For**: User-facing operations, throughput

### 3. Work Item Throughput
- **Target**: > 100 ops/sec
- **What**: Checkout → Execute → Checkin cycle
- **Libraries Impacted**: Hibernate (queries), database connection pooling
- **Critical For**: Workflow execution speed

### 4. Concurrent Throughput
- **Target**: > 100 cases/second
- **What**: Multi-threaded case creation
- **Libraries Impacted**: Thread pools, database connection limits
- **Critical For**: High-load scenarios

### 5. Memory Usage
- **Target**: < 512MB for 1000 active cases
- **What**: Heap consumption under load
- **Libraries Impacted**: Hibernate cache, Jackson serialization
- **Critical For**: Scalability, container resource limits

### 6. GC Activity
- **Target**: < 5% GC time, < 500ms pause
- **What**: Garbage collection frequency and pause times
- **Libraries Impacted**: Object allocation patterns
- **Critical For**: Latency predictability

### 7. Thread Efficiency
- **Target**: < 100 platform threads
- **What**: Thread pool usage, virtual thread adoption
- **Libraries Impacted**: Executors, structured concurrency
- **Critical For**: Resource efficiency

## Files and Components

### Core Monitoring Tools

1. **LibraryUpdatePerformanceMonitor.java**
   - Location: `test/org/yawlfoundation/yawl/performance/monitoring/`
   - Purpose: Capture baseline and current performance snapshots
   - Output: Text reports in `performance-reports/`

2. **EnginePerformanceBaseline.java**
   - Location: `test/org/yawlfoundation/yawl/performance/`
   - Purpose: Establish performance targets and run comprehensive tests
   - Metrics: All 7 key performance areas

3. **JMH Benchmarks** (Detailed Analysis)
   - `WorkflowExecutionBenchmark` - Multi-stage workflow patterns
   - `InterfaceBClientBenchmark` - HTTP client performance
   - `MemoryUsageBenchmark` - Platform vs virtual thread memory
   - `IOBoundBenchmark` - I/O-heavy operations
   - `EventLoggerBenchmark` - Event notification throughput

### Helper Scripts

- **run-performance-monitoring.sh** - Interactive menu for common operations
- **performance-reports/README.md** - Detailed documentation

## Workflow: Before/After Library Update

### Phase 1: Capture Baseline (Before Update)

```bash
# 1. Ensure clean state
mvn clean compile

# 2. Run baseline tests
mvn test -Dtest=EnginePerformanceBaseline

# 3. Review output
# Look for: All tests PASS, metrics within targets
# Example output:
#   Case Launch p95: 450ms ✓ PASS (target <500ms)
#   Work Item Throughput: 120 ops/sec ✓ PASS (target >100)
#   Memory Usage: 480MB ✓ PASS (target <512MB)

# 4. Save baseline file
# Automatically saved to: performance-reports/baseline-YYYYMMDD-HHMMSS.txt
```

### Phase 2: Update Libraries

```bash
# Edit pom.xml
# Example changes:
<hibernate.version>6.6.42.Final</hibernate.version>  # was 6.5.1.Final
<log4j.version>2.25.3</log4j.version>                # was 2.23.1
<jackson.version>2.18.3</jackson.version>             # was 2.17.0
<hikaricp.version>7.0.2</hikaricp.version>            # was 5.1.0
```

### Phase 3: Capture Current Metrics (After Update)

```bash
# 1. Clean build with new dependencies
mvn clean compile

# 2. Verify compilation
# If errors: Fix API incompatibilities first

# 3. Run current metrics
mvn test -Dtest=EnginePerformanceBaseline

# 4. Review output
# Compare against baseline targets
# Example output:
#   Case Launch p95: 520ms ⚠ (baseline 450ms, +15.5%)
#   Work Item Throughput: 115 ops/sec ✓ (baseline 120, -4.2%)
#   Memory Usage: 510MB ✓ (baseline 480MB, +6.3%)

# 5. Automatically saved to: performance-reports/current-YYYYMMDD-HHMMSS.txt
```

### Phase 4: Compare and Analyze

```bash
# 1. Review comparison
diff performance-reports/baseline-*.txt performance-reports/current-*.txt

# 2. Identify regressions
#    CRITICAL (>25%): Rollback or immediate investigation
#    MAJOR (10-25%): Investigation required
#    MODERATE (5-10%): Monitor in staging
#    MINOR (<5%): Document and accept

# 3. Investigate specific areas if needed
# For Hibernate changes:
#   - Check query execution plans (EXPLAIN ANALYZE)
#   - Review Hibernate statistics
#   - Profile database connection usage

# For memory issues:
#   - Run heap dump analysis
#   - Check Hibernate L2 cache configuration
#   - Profile object allocations

# For GC issues:
#   - Review GC logs
#   - Adjust heap sizes
#   - Consider different GC algorithm
```

## Regression Severity Levels

### CRITICAL (> 25% degradation)
- **Action**: STOP - Rollback or fix before proceeding
- **Examples**:
  - Case launch p95: 500ms → 650ms (+30%)
  - Throughput: 100 → 70 cases/sec (-30%)
  - Memory: 512MB → 700MB (+37%)
- **Root Cause Areas**:
  - Breaking API changes
  - Inefficient query generation (Hibernate)
  - Connection pool misconfiguration

### MAJOR (10-25% degradation)
- **Action**: Investigation required, do not deploy to production
- **Examples**:
  - Startup time: 60s → 72s (+20%)
  - Work item throughput: 100 → 85 ops/sec (-15%)
- **Root Cause Areas**:
  - Suboptimal default configurations
  - Changed caching behavior
  - Thread pool sizing issues

### MODERATE (5-10% degradation)
- **Action**: Monitor in staging, investigate if persistent
- **Examples**:
  - Case launch p95: 500ms → 540ms (+8%)
  - GC time: 3% → 3.5% (+0.5 percentage points)
- **Acceptable If**:
  - New features justify slight performance cost
  - Security fixes require trade-off
  - Measurement variation

### MINOR (< 5% degradation)
- **Action**: Document and accept
- **Examples**:
  - Memory: 512MB → 530MB (+3.5%)
  - Latency: 450ms → 465ms (+3.3%)
- **Likely Causes**:
  - Measurement noise
  - JVM warmup variations
  - Test data variability

## Performance Optimization Tips

### Hibernate Tuning (Post-Update)

```properties
# In persistence.xml or application.properties

# Query optimization
hibernate.jdbc.batch_size=20
hibernate.order_inserts=true
hibernate.order_updates=true

# Connection pooling (HikariCP via Hibernate)
hibernate.hikari.minimumIdle=5
hibernate.hikari.maximumPoolSize=20
hibernate.hikari.connectionTimeout=30000

# Caching (if L2 cache enabled)
hibernate.cache.use_second_level_cache=true
hibernate.cache.region.factory_class=org.hibernate.cache.jcache.JCacheRegionFactory
```

### JVM Tuning

```bash
# Recommended for performance testing
-Xms2g -Xmx4g              # Fixed heap (easier to measure)
-XX:+UseG1GC                # G1 collector (good default)
-XX:MaxGCPauseMillis=200    # Target pause time

# For production (8GB server)
-Xms2g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
```

### Database Tuning

```sql
-- Ensure indexes exist on foreign keys
CREATE INDEX idx_workitem_caseid ON rs_workitem(case_id);
CREATE INDEX idx_workitem_enabled ON rs_workitem(enabled);

-- Monitor query performance
EXPLAIN ANALYZE SELECT * FROM rs_workitem WHERE enabled = true;

-- Check connection pool usage
SELECT count(*) FROM pg_stat_activity WHERE datname = 'yawl';
```

## Advanced Analysis (JMH Benchmarks)

For deeper performance investigation:

```bash
# Run all benchmarks (30-45 minutes)
mvn test -Dtest=AllBenchmarksRunner

# Run specific benchmark
mvn test -Dtest=WorkflowExecutionBenchmark

# Results in: target/jmh-results.json
```

**When to Use JMH:**
- Investigating specific performance regressions
- Comparing platform vs virtual threads
- Profiling I/O-bound operations
- Memory allocation analysis

**JMH Metrics:**
- **Throughput**: Operations per second (higher is better)
- **Average Time**: Mean execution time (lower is better)
- **Memory**: Heap usage per operation

## Known Performance Impacts by Library

### Hibernate 6.5 → 6.6
- **Query Generation**: Improved SQL for certain joins
- **Batch Fetching**: Enhanced efficiency
- **Expected**: Neutral to slight improvement (1-3%)
- **Watch For**: Changed lazy-loading behavior

### Log4j 2.23 → 2.25
- **Async Logging**: Better throughput
- **GraalVM Support**: Improved startup (if using GraalVM)
- **Expected**: Neutral to improvement (0-2%)
- **Watch For**: Changed logging patterns

### Jackson 2.17 → 2.18
- **Deserialization**: Minor optimizations
- **Virtual Threads**: Better support
- **Expected**: Neutral (0-1%)
- **Watch For**: Changed default settings

### HikariCP 5.1 → 7.0
- **Connection Management**: Refined algorithms
- **Metrics**: Enhanced monitoring
- **Expected**: Neutral to improvement (0-5%)
- **Watch For**: Connection timeout changes

### Java 21 → 25
- **Virtual Threads**: Mature implementation
- **Pattern Matching**: Better compilation
- **G1GC**: Incremental improvements
- **Expected**: Improvement (2-5%)
- **Watch For**: Preview feature deprecations

## Troubleshooting Performance Regressions

### Startup Time Increased

```bash
# 1. Check Hibernate schema validation
# Set: hibernate.hbm2ddl.auto=none (in production)

# 2. Profile class loading
java -Xlog:class+load:file=classload.log ...

# 3. Check database connectivity
# Ensure connection pool initializes lazily
```

### Case Launch Latency Increased

```bash
# 1. Enable Hibernate SQL logging
hibernate.show_sql=true

# 2. Check for N+1 query problems
# Look for: Multiple SELECTs where JOIN expected

# 3. Profile with JProfiler/YourKit
# Focus on: Hibernate session creation, transaction boundaries
```

### Memory Usage Increased

```bash
# 1. Heap dump analysis
jmap -dump:live,format=b,file=heap.bin <pid>

# 2. Analyze with Eclipse MAT
# Look for: Retained size by class, leak suspects

# 3. Check Hibernate cache
# Verify L2 cache eviction policy
```

### GC Pause Time Increased

```bash
# 1. Review GC logs
-Xlog:gc*:file=gc.log

# 2. Analyze pause causes
# Look for: Full GC frequency, old gen size

# 3. Tune heap regions
-XX:G1HeapRegionSize=16m  # Adjust based on pause analysis
```

## Continuous Monitoring (Future)

### CI/CD Integration Planned

```yaml
# Future: Automated performance regression detection
# Trigger: On POM file changes
# Steps:
#   1. Checkout baseline performance data
#   2. Build with new dependencies
#   3. Run performance tests
#   4. Compare metrics
#   5. Fail PR if regression > threshold
```

### Prometheus Metrics (Available)

```bash
# If using Spring Boot Actuator endpoints:
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

## Summary Checklist

Before approving library updates:

- [ ] Baseline metrics captured
- [ ] Clean build successful with new versions
- [ ] Current metrics captured
- [ ] No CRITICAL regressions (>25%)
- [ ] MAJOR regressions investigated and understood
- [ ] Memory usage within limits
- [ ] GC pause times acceptable
- [ ] Startup time within target
- [ ] All performance tests pass
- [ ] Documentation updated

## Contact and Support

**Performance Issues:**
- Create GitHub issue with performance report attached
- Tag: `performance`, `regression`
- Include: Baseline vs current comparison

**Questions:**
- YAWL Performance Team: performance@yawlfoundation.org
- Documentation: https://yawlfoundation.github.io/yawl/

## Version History

- **2026-02-16**: Initial performance monitoring framework
  - Added LibraryUpdatePerformanceMonitor
  - Added EnginePerformanceBaseline tests
  - Added JMH benchmarks for virtual threads
  - Documented library update workflow
