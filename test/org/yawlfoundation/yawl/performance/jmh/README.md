# YAWL Virtual Threads JMH Benchmarks

Comprehensive performance benchmarks comparing platform threads vs virtual threads across all YAWL subsystems.

## Quick Start

### 1. Build
```bash
cd /home/user/yawl
mvn clean compile test-compile
```

### 2. Run All Benchmarks
```bash
mvn exec:java \
  -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"
```

**Estimated runtime:** 30-45 minutes

### 3. View Results
```bash
cat target/jmh-results.json
```

---

## Individual Benchmarks

### I/O-Bound Operations
```bash
java -cp test-classes:classes:lib/* \
  org.yawlfoundation.yawl.performance.jmh.IOBoundBenchmark
```

**Tests:** Database queries, file I/O, network calls  
**Expected:** 2-10x throughput improvement with virtual threads

---

### Event Logger
```bash
java -cp test-classes:classes:lib/* \
  org.yawlfoundation.yawl.performance.jmh.EventLoggerBenchmark
```

**Tests:** Concurrent event notifications, fan-out patterns  
**Expected:** 3-5x improvement with 1000+ listeners

---

### InterfaceB HTTP Client
```bash
java -cp test-classes:classes:lib/* \
  org.yawlfoundation.yawl.performance.jmh.InterfaceBClientBenchmark
```

**Tests:** Concurrent HTTP requests, checkout/checkin operations  
**Expected:** 20-30% latency reduction, unlimited concurrency

---

### Structured Concurrency
```bash
java -cp test-classes:classes:lib/* \
  org.yawlfoundation.yawl.performance.jmh.StructuredConcurrencyBenchmark \
  --enable-preview
```

**Tests:** StructuredTaskScope vs CompletableFuture  
**Expected:** 50-80% faster cancellation, cleaner error handling

---

### Memory Usage
```bash
java -cp test-classes:classes:lib/* \
  -Xms2g -Xmx2g -XX:+UseG1GC -verbose:gc \
  org.yawlfoundation.yawl.performance.jmh.MemoryUsageBenchmark
```

**Tests:** Memory per thread, GC pressure  
**Expected:** 100-1000x memory reduction (1MB → 1KB per thread)

---

### Workflow Execution
```bash
java -cp test-classes:classes:lib/* \
  org.yawlfoundation.yawl.performance.jmh.WorkflowExecutionBenchmark
```

**Tests:** Multi-stage workflows, parallel task execution  
**Expected:** 2-4x faster workflow completion

---

## Custom Parameters

### Run with specific task counts
```bash
java -jar target/benchmarks.jar IOBoundBenchmark \
  -p taskCount=100,1000,10000 \
  -p ioDelayMs=10,50
```

### Change iterations
```bash
java -jar target/benchmarks.jar EventLoggerBenchmark \
  -wi 5 \           # Warmup iterations
  -i 10 \           # Measurement iterations
  -f 2              # Forks
```

### Output formats
```bash
# JSON
java -jar target/benchmarks.jar -rf json -rff results.json

# CSV
java -jar target/benchmarks.jar -rf csv -rff results.csv

# Text
java -jar target/benchmarks.jar -rf text -rff results.txt
```

---

## Understanding Results

### Throughput Mode
```
Benchmark                                 Score      Error  Units
IOBoundBenchmark.platformThreads        100.123 ±   5.234  ops/s
IOBoundBenchmark.virtualThreads         532.456 ±  12.345  ops/s
```
**Higher is better.** Virtual threads: 5.3x improvement.

### Average Time Mode
```
Benchmark                                 Score      Error  Units
EventLoggerBenchmark.platformThreads     45.678 ±   2.123  ms/op
EventLoggerBenchmark.virtualThreads      12.345 ±   0.567  ms/op
```
**Lower is better.** Virtual threads: 3.7x faster.

### Memory Mode
```
Platform threads - Total memory: 1024MB, Per thread: 1024KB
Virtual threads  - Total memory: 10MB,   Per thread: 10KB
```
**Memory reduction: 100x**

---

## Troubleshooting

### OutOfMemoryError
```bash
# Increase heap size
export MAVEN_OPTS="-Xms4g -Xmx8g"
```

### Benchmark timeout
```bash
# Increase timeout (default: 60s)
java -jar target/benchmarks.jar \
  -to 120s  # 120 second timeout
```

### Preview features error
```bash
# Add --enable-preview for structured concurrency
java --enable-preview -jar target/benchmarks.jar StructuredConcurrencyBenchmark
```

---

## Benchmark Suite Contents

| Benchmark | Focus | Metrics |
|-----------|-------|---------|
| IOBoundBenchmark | Database, file, network | Throughput, latency |
| EventLoggerBenchmark | Event notifications | Events/sec, fanout |
| InterfaceBClientBenchmark | HTTP client | Requests/sec, p95 latency |
| StructuredConcurrencyBenchmark | Concurrency patterns | Overhead, cancellation |
| MemoryUsageBenchmark | Resource efficiency | MB/thread, GC count |
| WorkflowExecutionBenchmark | Real workflows | Completion time, tasks/sec |

---

## Expected Performance Improvements

| Workload | Platform Threads | Virtual Threads | Improvement |
|----------|-----------------|-----------------|-------------|
| 1000 concurrent I/O ops | ~100 ops/s | ~500 ops/s | **5x** |
| 1000 event listeners | ~200 events/s | ~1000 events/s | **5x** |
| 1000 HTTP requests | ~150 req/s | ~800 req/s | **5.3x** |
| Memory (1000 threads) | ~1 GB | ~1 MB | **1000x** |
| Workflow (100 tasks) | ~5000 ms | ~1500 ms | **3.3x** |

---

## Next Steps

1. ✅ Run benchmarks
2. ✅ Analyze results
3. ⏳ Generate performance report
4. ⏳ Production load testing
5. ⏳ Update documentation

---

**Version:** 1.0  
**Date:** 2026-02-16  
**Contact:** YAWL Performance Team
