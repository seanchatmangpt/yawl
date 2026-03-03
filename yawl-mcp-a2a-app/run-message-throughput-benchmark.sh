#!/bin/bash

# YAWL MCP A2A Message Throughput Benchmark Runner
# This script compiles and runs the MessageThroughputBenchmark

echo "=== YAWL MCP A2A Message Throughput Benchmark ==="
echo

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    echo "Please install Java 17+ and try again"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -1)"
echo

# Compile the benchmark
echo "Compiling MessageThroughputBenchmark..."
if mvn compile test-compile -q; then
    echo "Compilation successful!"
else
    echo "Compilation failed. Trying direct compilation..."
    if javac -cp "target/test-classes" src/test/java/org/yawlfoundation/yawl/stress/MessageThroughputBenchmarkRunner.java 2>/dev/null; then
        echo "Direct compilation successful!"
    else
        echo "Error: Unable to compile the benchmark"
        echo "Make sure Maven is installed and all dependencies are available"
        exit 1
    fi
fi
echo

# Run the benchmark using Maven exec plugin
echo "Running MessageThroughputBenchmark..."
echo "======================================="
if mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner" -q; then
    echo
    echo "=== Benchmark completed successfully ==="
else
    echo
    echo "Error: Failed to run the benchmark"
    echo "Trying direct execution..."
    if java -cp "target/classes:target/test-classes" org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner; then
        echo "Direct execution successful!"
    else
        echo "Direct execution also failed."
        exit 1
    fi
fi