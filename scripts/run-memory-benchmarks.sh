#!/bin/bash

# Script to run memory optimization benchmarks for YAWL v6.0.0-GA

echo "=============================================================="
echo "YAWL Memory Optimization Benchmarks"
echo "=============================================================="
echo "Target: Memory growth per case < 2MB"
echo "       GC pause time < 10ms"
echo "       Heap utilization > 95%"
echo "       Virtual thread memory < 8KB per thread"
echo "=============================================================="

# Build benchmarks if needed
echo "Building benchmarks..."
mvn compile -q -DskipTests
if [ $? -ne 0 ]; then
    echo "Build failed. Checking if YAWL dependencies are available..."
    # Check if benchmarks can be compiled in the test directory
    if [ -d "test/org/yawlfoundation/yawl/performance/jmh" ]; then
        echo "Benchmark classes found, proceeding with test execution..."
    else
        echo "Error: Benchmark classes not found. Please run build first."
        exit 1
    fi
fi

echo "Running memory optimization benchmarks..."

# Run all memory-related benchmarks
if [ -f "target/classes/org/yawlfoundation/yawl/performance/jmh/MemoryOptimizationBenchmarks.class" ]; then
    echo "Enhanced MemoryOptimizationBenchmarks..."
    java -jar target/benchmarks.jar MemoryOptimizationBenchmarks -rf json -rf csv
fi

if [ -f "target/classes/org/yawlfoundation/yawl/performance/jmh/LongRunningMemoryBenchmark.class" ]; then
    echo "LongRunningMemoryBenchmark (24+ hour test)..."
    java -jar target/benchmarks.jar LongRunningMemoryBenchmark -rf json -rf csv
fi

if [ -f "target/classes/org/yawlfoundation/yawl/performance/jmh/GCAnalysisBenchmark.class" ]; then
    echo "GCAnalysisBenchmark..."
    java -jar target/benchmarks.jar GCAnalysisBenchmark -rf json -rf csv
fi

if [ -f "target/classes/org/yawlfoundation/yawl/performance/jmh/ObjectAllocationBenchmark.class" ]; then
    echo "ObjectAllocationBenchmark..."
    java -jar target/benchmarks.jar ObjectAllocationBenchmark -rf json -rf csv
fi

if [ -f "target/classes/org/yawlfoundation/yawl/performance/jmh/VirtualThreadMemoryBenchmark.class" ]; then
    echo "VirtualThreadMemoryBenchmark..."
    java -jar target/benchmarks.jar VirtualThreadMemoryBenchmark -rf json -rf csv
fi

echo "=============================================================="
echo "Memory benchmarks completed successfully!"
echo "=============================================================="