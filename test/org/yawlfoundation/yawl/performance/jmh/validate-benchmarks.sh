#!/bin/bash

# Script to validate MCP Performance Benchmarks
# This script checks that all components are properly configured

echo "Validating MCP Performance Benchmarks..."
echo "========================================"

# Check if benchmark file exists
if [ ! -f "MCPPerformanceBenchmarks.java" ]; then
    echo "❌ MCPPerformanceBenchmarks.java not found"
    exit 1
else
    echo "✅ MCPPerformanceBenchmarks.java exists"
fi

# Check if test fixtures exist
if [ ! -f "fixtures/McpBenchmarkData.java" ]; then
    echo "❌ Test fixtures not found"
    exit 1
else
    echo "✅ Test fixtures exist"
fi

# Check if unit test exists
if [ ! -f "fixtures/McpPerformanceTest.java" ]; then
    echo "❌ Unit test not found"
    exit 1
else
    echo "✅ Unit test exists"
fi

# Check if README exists
if [ ! -f "README.md" ]; then
    echo "❌ README.md not found"
    exit 1
else
    echo "✅ README.md exists"
fi

# Check if AllBenchmarksRunner includes MCP benchmarks
if grep -q "MCPPerformanceBenchmarks" AllBenchmarksRunner.java; then
    echo "✅ AllBenchmarksRunner includes MCP benchmarks"
else
    echo "❌ AllBenchmarksRunner missing MCP benchmarks"
fi

# Check JMH annotations in benchmark file
if grep -q "@BenchmarkMode" MCPPerformanceBenchmarks.java; then
    echo "✅ JMH annotations present"
else
    echo "❌ Missing JMH annotations"
fi

# Check required benchmarks
required_benchmarks=("toolExecutionLatency" "toolThroughput" "concurrentToolExecution" "toolResultProcessing" "memoryFootprint")
for benchmark in "${required_benchmarks[@]}"; do
    if grep -q "public void $benchmark" MCPPerformanceBenchmarks.java; then
        echo "✅ Benchmark $benchmark implemented"
    else
        echo "❌ Benchmark $benchmark missing"
    fi
done

echo ""
echo "Validation complete. MCP Performance Benchmarks are ready!"
echo ""
echo "To run the benchmarks:"
echo "  mvn exec:java -Dexec.mainClass=\"org.yawlfoundation.yawl.performance.jmh.MCPPerformanceBenchmarks\""
echo ""
echo "To run all benchmarks:"
echo "  mvn exec:java -Dexec.mainClass=\"org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner\""
