#!/bin/bash

# Script to run TemporalForkBenchmark
# This script runs the benchmark and provides clear output

echo "=========================================="
echo "TemporalForkBenchmark Runner"
echo "=========================================="
echo

# Change to performance module directory
cd /Users/sac/yawl/test/org/yawlfoundation/yawl/performance

echo "1. Compiling TemporalForkBenchmark..."
mvn clean compile -DskipTests -q

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi

echo
echo "2. Running TemporalForkBenchmark with JMH..."

# Run the benchmark using JMH
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main -Dexec.args=".*TemporalForkBenchmark.*" -q

if [ $? -eq 0 ]; then
    echo "✓ Benchmark execution completed"
    echo
    echo "3. Benchmark Results Summary:"
    echo "   - 10 Forks execution time: Should be < 100ms"
    echo "   - 100 Forks execution time: Should be < 500ms"
    echo "   - 1000 Forks execution time: Should be < 2000ms"
    echo "   - XML serialization: Should be < 1ms"
    echo "   - Memory usage: Should be < 100MB overhead"
else
    echo "✗ Benchmark execution failed"
    exit 1
fi

echo
echo "=========================================="
echo "Benchmark runner completed successfully!"
echo "=========================================="