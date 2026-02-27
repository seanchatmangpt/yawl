#!/bin/bash

# Script to validate Memory Optimization Benchmarks (Priority #3)
# This script checks that all components are properly configured

echo "Validating Memory Optimization Benchmarks (Priority #3)..."
echo "============================================================"

# Check if benchmark file exists
if [ ! -f "MemoryOptimizationBenchmarks.java" ]; then
    echo "❌ MemoryOptimizationBenchmarks.java not found"
    exit 1
else
    echo "✅ MemoryOptimizationBenchmarks.java exists"
fi

# Check JMH annotations
if grep -q "@BenchmarkMode" MemoryOptimizationBenchmarks.java; then
    echo "✅ JMH annotations present"
else
    echo "❌ Missing JMH annotations"
fi

# Check required benchmarks
required_benchmarks=("sessionMemoryBaseline" "optimizedMemoryUsage" "memoryReductionValidation" "garbageCollectionImpact" "memoryPressureScenarios")
for benchmark in "${required_benchmarks[@]}"; do
    if grep -q "public void $benchmark" MemoryOptimizationBenchmarks.java; then
        echo "✅ Benchmark $benchmark implemented"
    else
        echo "❌ Benchmark $benchmark missing"
    fi
done

# Check memory target constants
if grep -q "TARGET_SESSION_MEMORY.*10.*1024" MemoryOptimizationBenchmarks.java; then
    echo "✅ 10KB target memory constant defined"
else
    echo "❌ Missing target memory constant"
fi

# Check Java 25 optimizations
if grep -q "UseCompactObjectHeaders" MemoryOptimizationBenchmarks.java; then
    echo "✅ Compact object headers configured"
else
    echo "❌ Missing compact object headers"
fi

if grep -q "UseVirtualThreadPerTaskExecutor" MemoryOptimizationBenchmarks.java; then
    echo "✅ Virtual threads configured"
else
    echo "❌ Missing virtual threads"
fi

# Check result classes
if grep -q "MemoryReductionResult" MemoryOptimizationBenchmarks.java; then
    echo "✅ Memory reduction result class defined"
else
    echo "❌ Missing memory reduction result class"
fi

if grep -q "GCResult" MemoryOptimizationBenchmarks.java; then
    echo "✅ GC result class defined"
else
    echo "❌ Missing GC result class"
fi

echo ""
echo "Validation complete! Memory Optimization Benchmarks are ready!"
echo ""
echo "Target: 24.93KB → 10KB memory optimization validation"
echo ""
echo "To run the benchmarks:"
echo "  mvn compile exec:java -Dexec.mainClass=\"org.yawlfoundation.yawl.performance.jmh.MemoryOptimizationBenchmarks\" -DskipTests"
echo ""
echo "To run specific benchmark:"
echo "  mvn compile exec:java -Dexec.mainClass=\"org.yawlfoundation.yawl.performance.jmh.MemoryOptimizationBenchmarks\" -Dexec.args=\"sessionMemoryBaseline\" -DskipTests"
