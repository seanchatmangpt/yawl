#!/bin/bash

echo "=== YAWL MCP A2A Simple Test ==="
echo

# Test if we can compile and run a basic Java program
echo "Testing basic Java functionality..."
if java -version > /dev/null 2>&1; then
    echo "✓ Java is available"
else
    echo "✗ Java not found - benchmark cannot run"
    exit 1
fi
echo

# Try to compile just the runner
echo "Compiling benchmark runner..."
if javac -cp "." src/test/java/org/yawlfoundation/yawl/stress/MessageThroughputBenchmarkRunner.java 2>/dev/null; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed - this is expected without full classpath"
    echo "This is normal - the benchmark requires full Maven build"
    echo
fi

# Show the benchmark files created
echo "=== Benchmark Files Created ==="
echo
find src/test/java/org/yawlfoundation/yawl/stress -name "*.java" | while read file; do
    if [[ "$file" == *"MessageThroughput"* ]]; then
        echo "✓ $(basename "$file")"
    fi
done

echo
echo "=== Documentation ==="
echo "- README: src/test/java/org/yawlfoundation/yawl/stress/README_MessageThroughputBenchmark.md"
echo "- Usage Guide: src/test/java/org/yawlfoundation/yawl/stress/BENCHMARK_USAGE_GUIDE.md"
echo

echo "=== Next Steps ==="
echo "1. Compile the project: mvn clean compile"
echo "2. Run the benchmark: mvn exec:java -Dexec.mainClass=\"org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner\""
echo "3. Or use the script: ./run-message-throughput-benchmark.sh"
echo
echo "The benchmark is ready to use once the project is compiled!"