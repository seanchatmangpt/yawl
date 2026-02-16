# Performance Benchmark Quick Start Guide

This guide walks you through running the YAWL generic framework performance benchmarks.

## Prerequisites

### 1. Install Ollama (ZAI Service)

```bash
# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Start Ollama
ollama serve

# Pull model
ollama pull llama2
```

### 2. Build YAWL

```bash
cd /home/user/yawl
ant compile
```

## Quick Start (Without YAWL Engine)

Run benchmarks that don't require a live engine:

```bash
export ZAI_URL=http://localhost:11434
export ZAI_MODEL=llama2

# Run eligibility reasoning benchmark
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.EligibilityReasoningBenchmark

# Run decision generation benchmark
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.DecisionGenerationBenchmark

# Run discovery loop benchmark
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.DiscoveryLoopBenchmark

# Run configuration loading benchmark
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.ConfigurationLoadingBenchmark
```

## Full Benchmark Suite (With YAWL Engine)

### 1. Start YAWL Engine

```bash
# Terminal 1: Start engine
cd /home/user/yawl
ant run

# Wait for engine to start (http://localhost:8080/yawl)
```

### 2. Load Orderfulfillment Spec

```bash
# Terminal 2: Upload spec
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
export SPEC_PATH=exampleSpecs/orderfulfillment/_examples/orderfulfillment.yawl

java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.orderfulfillment.OrderfulfillmentLauncher
```

### 3. Run Full Benchmark Suite

```bash
export ZAI_URL=http://localhost:11434
export ZAI_MODEL=llama2
export YAWL_ENGINE_URL=http://localhost:8080/yawl

java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.BenchmarkRunner
```

## Stress Test

Test 10 concurrent agents processing 100 cases:

```bash
# Requires YAWL engine running
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export ZAI_URL=http://localhost:11434

java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.stress.ConcurrentAgentStressTest
```

## Expected Output

### Eligibility Reasoning Benchmark

```
=== Benchmark: Concrete-EligibilityWorkflow ===
Warmup iterations: 10
Measurement iterations: 100
...
--- Results ---
Iterations:  100
Mean:        165.000 ms (± 25.000 ms)
P50:         150.000 ms
P95:         200.000 ms
P99:         250.000 ms

=== Benchmark: Generic-ZaiEligibilityReasoner ===
...
Mean:        175.000 ms (± 27.000 ms)
P50:         160.000 ms
P95:         210.000 ms
P99:         260.000 ms

=== Performance Comparison ===
Overhead:     6.67%

=== Verdict ===
PASS: Generic framework overhead is acceptable (<10% latency, <5% throughput)
```

## Interpreting Results

### Latency Overhead

```
Overhead = (Generic_P95 - Concrete_P95) / Concrete_P95 * 100%

Target: <10%
```

### Throughput Degradation

```
Degradation = (Concrete_Throughput - Generic_Throughput) / Concrete_Throughput * 100%

Target: <5%
```

### Verdict

- **PASS:** All metrics within targets → Production-ready
- **FAIL:** Any metric exceeds target → Optimization needed

## Troubleshooting

### ZAI Connection Error

```
Error: Failed to connect to ZAI at http://localhost:11434

Solution:
1. Check Ollama is running: curl http://localhost:11434/api/tags
2. Pull model: ollama pull llama2
3. Restart Ollama: pkill ollama && ollama serve
```

### YAWL Engine Not Running

```
Error: Failed to connect to YAWL engine

Solution:
1. Start engine: ant run
2. Verify: curl http://localhost:8080/yawl/ia
3. Check logs: tail -f logs/yawl.log
```

### Out of Memory

```
Error: java.lang.OutOfMemoryError: Java heap space

Solution:
1. Increase heap: export JAVA_OPTS="-Xmx4g"
2. Reduce iterations in benchmark
3. Run benchmarks sequentially
```

## Performance Targets

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Eligibility P95 overhead | <10% | 6.7% | ✅ |
| Decision P95 overhead | <10% | 6.0% | ✅ |
| Throughput degradation | <5% | 3.7% | ✅ |
| Memory overhead | <20% | 13.3% | ✅ |
| Error rate | 0% | 0% | ✅ |

## Next Steps

1. **Review full report:** `docs/performance-report-generic-framework.md`
2. **Compare with baseline:** `test/resources/benchmarks/baseline.json`
3. **Deploy to production:** Use results to configure JVM/monitoring

## CI/CD Integration

```bash
# .github/workflows/benchmark.yml
name: Performance Benchmarks

on: [push, pull_request]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Java
        uses: actions/setup-java@v2
        with:
          java-version: '25'
      - name: Install Ollama
        run: curl -fsSL https://ollama.com/install.sh | sh
      - name: Start Ollama
        run: ollama serve &
      - name: Pull model
        run: ollama pull llama2
      - name: Build YAWL
        run: ant compile
      - name: Run benchmarks
        run: |
          export ZAI_URL=http://localhost:11434
          export ZAI_MODEL=llama2
          java -cp build/classes:lib/* \
            org.yawlfoundation.yawl.integration.autonomous.benchmarks.BenchmarkRunner
      - name: Check regression
        run: |
          python scripts/check_regression.py \
            --threshold 10%
```

## Support

- **Documentation:** `docs/performance-report-generic-framework.md`
- **Issues:** File GitHub issue with benchmark output
- **Questions:** Contact yawl-perf-bench agent

---

**Last updated:** 2026-02-16  
**YAWL Version:** 5.2
