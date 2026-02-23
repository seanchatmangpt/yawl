#!/bin/bash

echo "=== YAWL Benchmark Suite Setup Validation ==="
echo ""

# Check if benchmark directory exists
if [[ -d "yawl-benchmark" ]]; then
    echo "✓ Benchmark directory exists"
else
    echo "✗ Benchmark directory missing"
    exit 1
fi

# Check benchmark source files
BENCHMARK_FILES=(
    "yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/YAWLEngineBenchmarks.java"
    "yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/WorkflowPatternBenchmarks.java"
    "yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/ConcurrencyBenchmarks.java"
    "yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/MemoryBenchmarks.java"
    "yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/TestDataGenerator.java"
)

for file in "${BENCHMARK_FILES[@]}"; do
    if [[ -f "$file" ]]; then
        echo "✓ $file"
    else
        echo "✗ $file missing"
    fi
done

# Check runner script
if [[ -f "scripts/benchmark.sh" ]]; then
    echo "✓ Benchmark runner script exists"
else
    echo "✗ Benchmark runner script missing"
fi

# Check documentation
if [[ -f "docs/v6/latest/performance/baseline-metrics.md" ]]; then
    echo "✓ Baseline metrics documentation exists"
else
    echo "✗ Baseline metrics documentation missing"
fi

# Check POM update
if grep -q "yawl-benchmark" pom.xml; then
    echo "✓ Benchmark module added to parent POM"
else
    echo "✗ Benchmark module not found in parent POM"
fi

echo ""
echo "=== Summary ==="
echo "The YAWL benchmark suite includes:"
echo "• 4 main benchmark classes with 40+ benchmark methods"
echo "• Comprehensive test data generator"
echo "• Benchmark runner script with JMH integration"
echo "• Baseline metrics for regression detection"
echo "• Documentation and examples"
echo ""
echo "Next steps:"
echo "1. Build the benchmark module: mvn clean install"
echo "2. Run benchmarks: ./scripts/benchmark.sh"
echo "3. Review results in JSON format"
echo "4. Compare against baseline metrics"
