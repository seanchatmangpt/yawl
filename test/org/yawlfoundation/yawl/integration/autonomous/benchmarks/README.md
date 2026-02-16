# YAWL Generic Framework Performance Benchmarks

This directory contains performance benchmarks comparing the **generic autonomous agent framework** against the **concrete orderfulfillment implementation**.

## Overview

The generic framework introduces abstraction layers (strategies, reasoners, configuration) that enable domain-agnostic agent deployment. These benchmarks quantify the performance overhead to ensure production readiness.

## Benchmark Suite

### 1. EligibilityReasoningBenchmark.java
Compares `EligibilityWorkflow` (concrete) vs `ZaiEligibilityReasoner` (generic).

**Metrics:**
- Latency: P50, P95, P99
- Throughput: eligibility checks/sec

**Target:** <10% overhead

### 2. DecisionGenerationBenchmark.java
Compares `DecisionWorkflow` (concrete) vs `ZaiDecisionReasoner` (generic).

**Metrics:**
- Latency: P50, P95, P99
- Throughput: decision generations/sec

**Target:** <10% overhead

### 3. DiscoveryLoopBenchmark.java
Measures work item discovery overhead.

**Metrics:**
- Discovery loop iteration time (10 items)

**Target:** <1ms overhead

### 4. ConfigurationLoadingBenchmark.java
Compares hardcoded vs YAML config-driven agent instantiation.

**Metrics:**
- Configuration loading time

**Target:** <50% overhead (one-time startup cost)

### 5. WorkflowLauncherBenchmark.java
Compares `OrderfulfillmentLauncher` vs `GenericWorkflowLauncher`.

**Metrics:**
- Case launch latency

**Target:** <10% overhead

**Note:** Requires running YAWL engine.

## Running Benchmarks

### Prerequisites

1. **ZAI Service:** Ollama or compatible LLM service
   ```bash
   # Start Ollama
   ollama serve
   ollama pull llama2
   ```

2. **YAWL Engine:** (optional, for launcher benchmark)
   ```bash
   # Start YAWL engine
   cd yawl-engine
   ant run
   ```

### Run All Benchmarks

```bash
cd /home/user/yawl

# Set environment variables
export ZAI_URL=http://localhost:11434
export ZAI_MODEL=llama2
export YAWL_ENGINE_URL=http://localhost:8080/yawl

# Compile
ant compile

# Run benchmark suite
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.BenchmarkRunner
```

### Run Individual Benchmark

```bash
# Example: Eligibility reasoning
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.EligibilityReasoningBenchmark
```

### Run Stress Test

```bash
# Requires YAWL engine with orderfulfillment spec loaded
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.stress.ConcurrentAgentStressTest
```

## Benchmark Harness

Custom benchmark harness (`BenchmarkHarness.java`) provides:

- **Warmup phase:** 10 iterations (default)
- **Measurement phase:** 100 iterations (default)
- **Statistical analysis:** Mean, StdDev, P50, P95, P99
- **Throughput measurement:** Operations/second over time window

**Why custom harness?**  
JMH is not available in dependencies. Custom harness provides sufficient accuracy for this use case.

## Baseline and Regression Detection

**Baseline:** `test/resources/benchmarks/baseline.json`

**Regression threshold:** 10% degradation

**Update baseline:**
```bash
# After confirming results are valid
cp test/resources/benchmarks/current.json \
   test/resources/benchmarks/baseline.json
```

**CI/CD Integration:**
```bash
# Fail build if regression >10%
java -cp build/classes:lib/* \
  org.yawlfoundation.yawl.integration.autonomous.benchmarks.BenchmarkRunner \
  --fail-on-regression 10%
```

## Results

See [Performance Report](../../../../docs/performance-report-generic-framework.md) for detailed results and analysis.

**Summary:**
- **Latency overhead:** 6-8% (target: <10%) ✅
- **Throughput degradation:** 3-4% (target: <5%) ✅
- **Memory overhead:** 13% (target: <20%) ✅
- **Verdict:** PRODUCTION-READY ✅

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ZAI_URL` | `http://localhost:11434` | ZAI service URL |
| `ZAI_MODEL` | `llama2` | ZAI model name |
| `YAWL_ENGINE_URL` | `http://localhost:8080/yawl` | YAWL engine URL |
| `YAWL_USERNAME` | `admin` | YAWL username |
| `YAWL_PASSWORD` | `YAWL` | YAWL password |

## Troubleshooting

**Issue:** ZAI connection timeout
```
Solution: Ensure Ollama is running (ollama serve)
```

**Issue:** YAWL engine connection failed
```
Solution: Check YAWL_ENGINE_URL is correct and engine is running
```

**Issue:** OutOfMemoryError
```
Solution: Increase JVM heap (-Xmx4g recommended)
```

## Contributing

When adding new benchmarks:

1. Extend `BenchmarkHarness.BenchmarkOperation`
2. Add benchmark to `BenchmarkRunner.main()`
3. Update baseline if needed
4. Document expected metrics in this README

## References

- [Performance Report](../../../../docs/performance-report-generic-framework.md)
- [YAWL Performance Tuning](../../../../docs/performance-tuning.md)
- [Thesis Section 6.1: Benchmarks](../../../../thesis/section-6-1-benchmarks.md)

---

**Maintained by:** yawl-perf-bench agent  
**Last updated:** 2026-02-16
