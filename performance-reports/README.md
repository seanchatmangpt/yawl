# YAWL Performance Monitoring - Library Update Impact Analysis

## Overview

This directory contains performance monitoring tools and reports for tracking the impact of library updates on YAWL workflow engine performance.

## Key Metrics Tracked

### 1. Engine Startup Time
- **Target**: < 60 seconds
- **Critical Path**: JVM initialization → Specification loading → First case launch
- **Library Impact**: Hibernate, Spring Boot, database drivers

### 2. Case Launch Latency
- **Target p95**: < 500ms
- **Measures**: Time from `startCase()` call to case ID return
- **Library Impact**: Hibernate session creation, database connection pooling

### 3. Work Item Throughput
- **Target**: > 100 operations/sec for checkout/checkin cycle
- **Measures**: Complete work item lifecycle performance
- **Library Impact**: Hibernate queries, transaction management

### 4. Concurrent Throughput
- **Target**: > 100 cases/second with 10 concurrent threads
- **Measures**: Engine scalability under load
- **Library Impact**: Thread pool management, database connection pooling

### 5. Memory Usage
- **Target**: < 512MB for 1000 active cases
- **Measures**: Heap usage, memory per case
- **Library Impact**: Hibernate L1/L2 cache, Jackson serialization

### 6. GC Activity
- **Target**: < 5% GC time, < 500ms max pause
- **Measures**: GC collections, pause times
- **Library Impact**: Object allocation patterns, cache sizing

### 7. Thread Efficiency
- **Target**: < 100 platform threads under normal load
- **Measures**: Thread count, virtual thread usage
- **Library Impact**: Executor service configuration

## Usage Workflow

### Before Library Update

```bash
# 1. Compile current codebase
mvn clean compile

# 2. Capture baseline metrics
mvn test -Dtest=LibraryUpdatePerformanceMonitor

# This creates: performance-reports/baseline-YYYYMMDD-HHMMSS.txt
```

### After Library Update

```bash
# 1. Update library versions in pom.xml
# Example: hibernate.version: 6.5.1.Final → 6.6.42.Final

# 2. Clean and recompile
mvn clean compile

# 3. Capture current metrics
mvn test -Dtest=LibraryUpdatePerformanceMonitor

# This creates: performance-reports/current-YYYYMMDD-HHMMSS.txt
```

### Compare Results

```bash
# Run comparison (manual for now)
# Review both baseline and current reports
# Look for regressions > 10%

diff performance-reports/baseline-*.txt performance-reports/current-*.txt
```

## Recent Library Updates Tracked

### 2026-02-16: Java 25 + Hibernate 6.6.42 + Jakarta EE 10

**Libraries Updated:**
- Java: 21 → 25
- Hibernate: 6.5.1.Final → 6.6.42.Final
- Jakarta Servlet: 5.0.0 → 6.1.0
- Log4j: 2.23.1 → 2.25.3
- Jackson: 2.17.0 → 2.18.3
- HikariCP: 5.1.0 → 7.0.2

**Performance Impact:** (To be measured)

## Performance Regression Thresholds

### Critical (> 25% degradation)
- **Action**: Immediate rollback or investigation required
- **Examples**:
  - Engine startup > 75 seconds (from 60s)
  - Case launch p95 > 625ms (from 500ms)
  - Throughput drops below 75 cases/sec (from 100)

### Major (10-25% degradation)
- **Action**: Investigation required before production deployment
- **Examples**:
  - Memory usage increases by 128MB+ (from 512MB)
  - GC pause times > 625ms (from 500ms)

### Moderate (5-10% degradation)
- **Action**: Monitor in staging, investigate if persistent
- **Examples**:
  - Minor latency increases (525-550ms from 500ms)

### Minor (< 5% degradation)
- **Action**: Document and monitor
- **Likely**: Normal variation, measurement noise

## JMH Benchmarks

For more detailed performance analysis, run JMH benchmarks:

```bash
# Run all benchmarks (30-45 minutes)
mvn test -Dtest=AllBenchmarksRunner

# Run specific benchmark
mvn test -Dtest=WorkflowExecutionBenchmark
mvn test -Dtest=InterfaceBClientBenchmark
mvn test -Dtest=MemoryUsageBenchmark

# Results saved to: target/jmh-results.json
```

## Critical Performance Paths

### 1. YNetRunner Execution
- **Code**: `org.yawlfoundation.yawl.engine.YNetRunner`
- **Library Dependencies**: None (pure Java logic)
- **Expected Impact**: Minimal from library updates

### 2. Hibernate Queries
- **Code**: `org.yawlfoundation.yawl.engine.YPersistenceManager`
- **Library Dependencies**: Hibernate, HikariCP, database drivers
- **Expected Impact**: **HIGH** - Version changes affect query generation

### 3. Work Item Repository
- **Code**: `org.yawlfoundation.yawl.engine.YWorkItemRepository`
- **Library Dependencies**: Hibernate, Jakarta Persistence
- **Expected Impact**: **MEDIUM** - Caching and transaction behavior

### 4. Event Logging
- **Code**: `org.yawlfoundation.yawl.logging.*`
- **Library Dependencies**: Log4j, SLF4J
- **Expected Impact**: **MEDIUM** - Logging performance, GC pressure

### 5. JSON Serialization
- **Code**: `org.yawlfoundation.yawl.util.JsonUtil`
- **Library Dependencies**: Jackson
- **Expected Impact**: **LOW** - Usually optimized with each version

## Database Query Performance

### Key Queries to Profile

```sql
-- Work item queries (high frequency)
SELECT * FROM rs_workitem WHERE enabled = true;
SELECT * FROM rs_workitem WHERE case_id = ?;

-- Case queries
SELECT * FROM rs_case WHERE id = ?;

-- Specification queries (cached)
SELECT * FROM rs_specification WHERE spec_id = ?;
```

### Hibernate Query Logging

Enable in `persistence.xml` or JVM args:

```properties
hibernate.show_sql=true
hibernate.format_sql=true
hibernate.use_sql_comments=true
hibernate.generate_statistics=true
```

### Connection Pool Monitoring

HikariCP metrics (enabled via Spring Boot Actuator):

```bash
# If using Spring Boot Actuator
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending
```

## JVM Tuning for Performance Testing

Recommended JVM args for consistent measurements:

```bash
-Xms2g -Xmx4g              # Fixed heap size
-XX:+UseG1GC                # G1 garbage collector
-XX:MaxGCPauseMillis=200    # Target pause time
-XX:+PrintGCDetails         # GC logging
-XX:+PrintGCDateStamps
-Xloggc:logs/gc.log
```

## Automated Performance Testing (Future)

### CI/CD Integration

```yaml
# .github/workflows/performance-regression.yml (future)
name: Performance Regression Tests

on:
  pull_request:
    paths:
      - 'pom.xml'
      - '**/pom.xml'

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
      - name: Run baseline
        run: mvn test -Dtest=EnginePerformanceBaseline
      - name: Check for regressions
        run: bash scripts/performance/regression-test.sh
```

## Report Format

Each performance report includes:

1. **Environment**
   - JVM version
   - OS version
   - Available processors
   - Library versions

2. **Metrics**
   - Engine startup time
   - Case launch latency (p50, p95, p99)
   - Work item throughput
   - Concurrent throughput
   - Memory usage
   - GC activity
   - Thread count

3. **Comparison** (when comparing)
   - Absolute delta
   - Percentage change
   - Severity (CRITICAL/MAJOR/MODERATE/MINOR/IMPROVEMENT)
   - Regression status

## Contact

For questions about performance monitoring:
- Performance Team: performance@yawlfoundation.org
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues

## Version History

- **2026-02-16**: Initial performance monitoring framework
  - Added LibraryUpdatePerformanceMonitor
  - Added JMH benchmarks for virtual threads
  - Added baseline performance tests
