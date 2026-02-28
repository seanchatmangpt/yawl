# How-To: Optimize YAWL Performance Using Benchmarks

Learn to use benchmark results to identify and fix performance bottlenecks.

## Identify Performance Bottlenecks

**Goal**: Find which components are limiting throughput.

### Steps

1. **Run comprehensive benchmark suite**
```bash
# Run all benchmarks and collect results
java -Xms4g -Xmx8g -jar target/benchmarks.jar \
    -n 50 -w 20 -f 5 \
    -rf json -rff full-results.json
```

2. **Analyze results**
```bash
# Extract slowest benchmarks
cat full-results.json | jq '.results | sort_by(.primaryMetric.score) | .[0:5]'

# Result might show:
# {
#   "benchmark": "WorkflowPatternBenchmarks.parallelSplitSyncPerformance",
#   "primaryMetric": {
#     "score": 45.3,
#     "unit": "ops/ms"
#   }
# }
```

3. **Compare against baseline**
```bash
python3 - <<'EOF'
import json

with open('full-results.json') as f:
    current = json.load(f)

with open('baseline-v6.0.0.json') as f:
    baseline = json.load(f)

print("Performance Analysis")
print("=" * 60)

for result in current['results']:
    bench_name = result['benchmark'].split('.')[-1]
    current_score = result['primaryMetric']['score']

    baseline_result = next(
        (b for b in baseline['results']
         if b['benchmark'] == result['benchmark']),
        None
    )

    if baseline_result:
        baseline_score = baseline_result['primaryMetric']['score']
        regression = ((baseline_score - current_score) / baseline_score) * 100

        status = "⚠️ " if regression > 15 else "✓"
        print(f"{status} {bench_name:40} {current_score:8.1f} "
              f"(baseline: {baseline_score:8.1f}, "
              f"change: {regression:+6.1f}%)")
EOF
```

## Optimize Case Creation Throughput

**Goal**: Increase the number of workflow cases created per second.

### Steps

1. **Batch case creation**
```java
// Before: Creating cases one at a time
for (int i = 0; i < 1000; i++) {
    engine.createCase(specID, null);  // SLOW
}

// After: Batch with connection pooling
ExecutorService executor = Executors.newFixedThreadPool(8);
List<Future<String>> futures = new ArrayList<>();

for (int i = 0; i < 1000; i++) {
    futures.add(executor.submit(() -> {
        return engine.createCase(specID, null);
    }));
}

List<String> caseIDs = futures.stream()
    .map(f -> {
        try {
            return f.get();
        } catch (Exception e) {
            return null;
        }
    })
    .filter(Objects::nonNull)
    .collect(Collectors.toList());

executor.shutdown();
```

2. **Configure connection pooling**
```xml
<!-- hibernate.cfg.xml -->
<property name="hibernate.hikaricp.maximumPoolSize">32</property>
<property name="hibernate.hikaricp.minimumIdle">8</property>
<property name="hibernate.hikaricp.idleTimeout">300000</property>
<property name="hibernate.hikaricp.maxLifetime">1800000</property>
<property name="hibernate.hikaricp.connectionTimeout">30000</property>
```

3. **Measure improvement**
```bash
# Before optimization
java -jar benchmarks.jar YAWLEngineBenchmarks.caseCreationThroughput \
    -n 50 -w 20 -f 5

# After optimization
java -jar benchmarks.jar YAWLEngineBenchmarks.caseCreationThroughput \
    -n 50 -w 20 -f 5

# Compare: target 3-4× throughput improvement
```

## Optimize Memory Usage

**Goal**: Reduce memory consumption and GC pressure.

### Steps

1. **Enable virtual threads**
```bash
# JVM options for production
export JAVA_OPTS="
  -XX:+UseZGC                         # Low-latency garbage collector
  -XX:+ZUncommit                      # Reclaim unused memory
  -XX:ZStatisticsInterval=0           # Reduce GC overhead
  -Djdk.virtualThreadScheduler.maxPoolSize=256
  -Djdk.virtualThreadScheduler.parallelism=8
  -Xms2g -Xmx4g                      # Heap sizing
"

java $JAVA_OPTS -jar application.jar
```

2. **Use ScopedValues instead of ThreadLocal**
```java
// Before: ThreadLocal (not ideal for virtual threads)
static final ThreadLocal<WorkflowContext> contextTL =
    new ThreadLocal<>();

// After: ScopedValue (virtual thread safe)
static final ScopedValue<WorkflowContext> context =
    ScopedValue.newInstance();

// Usage in virtual thread:
ScopedValue.callWhere(context, workflowContext, () -> {
    // Code here can access context
    return executeWorkflow();
});
```

3. **Measure memory improvements**
```bash
# Before optimization
java -Xms2g -Xmx4g -jar benchmarks.jar \
    MemoryBenchmarks.heapUsageDuringWorkflowExecution

# After optimization (should show lower peak memory)
java -Xms2g -Xmx4g -XX:+UseZGC -jar benchmarks.jar \
    MemoryBenchmarks.heapUsageDuringWorkflowExecution
```

## Optimize Parallel Execution

**Goal**: Improve performance of parallel workflow patterns.

### Steps

1. **Compare threading models**
```bash
# Measure platform vs virtual thread performance
java -jar benchmarks.jar \
    ConcurrencyBenchmarks.virtualVsPlatformThreadPerformance \
    -n 50 -w 20 -f 5

# Expected: Virtual threads 5-6× faster for context switching
```

2. **Optimize AND-split/AND-join performance**
```java
// Use structured concurrency for parallel tasks
public void executeParallelTasks(List<Runnable> tasks)
        throws ExecutionException, InterruptedException {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        // Submit all tasks
        List<Subtask<Void>> subtasks = tasks.stream()
            .map(task -> scope.fork(() -> {
                task.run();
                return null;
            }))
            .collect(Collectors.toList());

        // Wait for all to complete
        scope.join();

        // If any failed, exception is thrown
        scope.throwIfFailed();

        System.out.println("All tasks completed successfully");
    }
}
```

3. **Test parallel pattern scaling**
```bash
# Run with different thread counts
java -jar benchmarks.jar \
    ConcurrencyBenchmarks.threadScalingPerformance \
    -n 50 -w 20 -f 5

# Look for linear scaling up to core count
# Expect 80%+ efficiency at thread count = core count
```

## Reduce Work Item Latency

**Goal**: Decrease time from task enablement to completion.

### Steps

1. **Profile work item operations**
```bash
# Measure checkout latency
java -jar benchmarks.jar \
    YAWLEngineBenchmarks.workItemCheckoutLatency \
    -n 100 -w 20 -f 5

# Measure task transition time
java -jar benchmarks.jar \
    YAWLEngineBenchmarks.taskTransitionPerformance \
    -n 100 -w 20 -f 5

# Target: < 200ms checkout, < 100ms transition
```

2. **Optimize database queries**
```xml
<!-- hibernate.cfg.xml -->
<property name="hibernate.jdbc.batch_size">20</property>
<property name="hibernate.jdbc.fetch_size">50</property>
<property name="hibernate.order_inserts">true</property>
<property name="hibernate.order_updates">true</property>

<!-- Enable query caching -->
<property name="hibernate.cache.use_query_cache">true</property>
<property name="hibernate.cache.use_second_level_cache">true</property>
<property name="hibernate.cache.region.factory_class">
    org.hibernate.cache.jcache.JCacheRegionFactory
</property>
```

3. **Implement caching for specification metadata**
```java
private static final Map<String, YSpecification> specCache =
    new ConcurrentHashMap<>();

public YSpecification loadSpecificationCached(String specID)
        throws Exception {
    return specCache.computeIfAbsent(specID, id -> {
        try {
            return loadSpecification(id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });
}
```

## Tune Database Configuration

**Goal**: Optimize database performance for YAWL workloads.

### Steps

1. **Configure PostgreSQL for optimal performance**
```sql
-- postgresql.conf settings for YAWL workloads

-- Memory allocation
shared_buffers = '8GB'               # 1/4 of available RAM
effective_cache_size = '32GB'        # 3/4 of available RAM
work_mem = '512MB'                   # Per operation memory

-- Query optimization
random_page_cost = 1.1               # For SSD storage
effective_io_concurrency = 200       # For modern disks

-- Connection pooling
max_connections = 200
idle_in_transaction_session_timeout = '300s'

-- Logging
log_min_duration_statement = 500     # Log slow queries
log_statement = 'mod'                # Log DML only
```

2. **Add strategic indexes**
```sql
-- Case lookup
CREATE INDEX idx_cases_status ON yawl_cases(status) WHERE status != 'Completed';

-- Work item queries
CREATE INDEX idx_work_items_case ON yawl_work_items(case_id, status);

-- Task execution history
CREATE INDEX idx_task_history_case ON yawl_task_history(case_id, task_id);
```

3. **Validate index effectiveness**
```bash
# Before optimization
java -jar benchmarks.jar YAWLEngineBenchmarks \
    -n 50 -w 20 -f 5 > before.json

# After adding indexes
java -jar benchmarks.jar YAWLEngineBenchmarks \
    -n 50 -w 20 -f 5 > after.json

# Expected: 10-30% throughput improvement
```

## Create Custom Performance Tests

**Goal**: Benchmark your specific workflow patterns.

### Steps

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 3)
@Warmup(iterations = 20)
@Measurement(iterations = 50)
@Threads(8)
public class CustomWorkflowBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public YEngine engine;
        public String approvalSpecID;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            engine = new YEngine();
            engine.startup();

            // Load your specific specification
            YSpecification spec = loadApprovalWorkflow();
            engine.loadSpecification(spec);
            approvalSpecID = spec.getSpecificationID();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            engine.shutdown();
        }
    }

    @Benchmark
    public String createAndCompleteApprovalCase(BenchmarkState state)
            throws Exception {
        // Create case
        String caseID = state.engine.createCase(
            state.approvalSpecID,
            null
        );

        // Complete all work items
        Set<YWorkItem> items = state.engine.getEnabledWorkItems(caseID);
        for (YWorkItem item : items) {
            YWorkItem checked = state.engine.checkoutWorkItem(
                caseID,
                item.getID()
            );
            state.engine.completeWorkItem(checked, null, null, true);
        }

        return caseID;
    }
}
```

Compile and run:
```bash
# Compile with JMH annotations
javac -cp benchmarks.jar CustomWorkflowBenchmark.java

# Run the custom benchmark
java -jar benchmarks.jar CustomWorkflowBenchmark
```

## Document Performance Improvements

**Goal**: Track performance changes over time.

### Steps

1. **Create performance report**
```bash
#!/bin/bash
# generate-performance-report.sh

DATE=$(date +%Y-%m-%d_%H-%M-%S)
RESULTS_DIR="performance-results/$DATE"
mkdir -p "$RESULTS_DIR"

echo "Running performance benchmarks..."
java -jar benchmarks.jar \
    -n 50 -w 20 -f 3 \
    -rf json -rff "$RESULTS_DIR/results.json"

echo "Benchmarks complete: $RESULTS_DIR/results.json"

# Generate comparison if baseline exists
if [ -f "baseline.json" ]; then
    python3 compare-results.py \
        baseline.json \
        "$RESULTS_DIR/results.json" \
        > "$RESULTS_DIR/comparison.txt"
    cat "$RESULTS_DIR/comparison.txt"
fi
```

2. **Maintain baseline file**
```bash
# After optimizations are verified
cp results.json baseline-v6.1.0.json

# Track multiple versions
ls -la baseline-*.json
# baseline-v6.0.0.json
# baseline-v6.0.1.json
# baseline-v6.1.0.json
```

---

For detailed metrics, see:
- [Performance Baselines](../reference/performance-baselines.md)
- [Performance Targets](../reference/performance-targets.md)
- [Capacity Planning](../reference/capacity-planning.md)
