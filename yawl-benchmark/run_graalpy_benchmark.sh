#!/bin/bash

# Script to run GraalPy Synthesis Benchmark

echo "=== YAWL GraalPy Synthesis Benchmark ==="
echo "Starting at $(date)"
echo "Java vendor: $(java -version 2>&1 | head -n1)"
echo "Java home: $JAVA_HOME"
echo ""

# Compile the benchmark
echo "Compiling benchmark..."
mvn compile -q
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Run the benchmark
echo "Running benchmark..."
java -cp \
    target/classes:\
    target/lib/*:\
    ~/.m2/repository/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar:\
    ~/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar:\
    ~/.m2/repository/org/openjdk/jmh/jmh-generator-bytecode/1.37/jmh-generator-bytecode-1.37.jar \
    -XX:+UseCompactObjectHeaders \
    -XX:+UseZGC \
    org.yawlfoundation.yawl.integration.a2a.skills.GraalPySynthesisBenchmark

echo ""
echo "Benchmark completed at $(date)"