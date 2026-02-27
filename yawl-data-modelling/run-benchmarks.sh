#!/bin/bash

# Script to run DataModelling JMH benchmarks

echo "=== YAWL DataModelling Benchmarks ==="
echo

# Build the project first
echo "Building project..."
mvn clean compile test-compile -pl yawl-data-modelling -q || {
    echo "Error: Build failed"
    exit 1
}
echo "Build completed successfully."
echo

# Option 1: Run benchmark via JMH Maven plugin (if available)
echo "Option 1: Running via JMH Maven plugin (if installed)..."
mvn jmh:benchmark -pl yawl-data-modelling -Dbenchmark=".*DataModellingBenchmark.*" -q || {
    echo "JMH Maven plugin not available, trying direct execution..."
}

# Option 2: Run benchmark directly via Java
echo
echo "Option 2: Running benchmark directly..."
cd /Users/sac/yawl/yawl-data-modelling
java -cp "test/classes:target/classes:$(mvn dependency:build-classpath -pl yawl-data-modelling -q -Dmdep.outputFile=/dev/stdout)" \
    org.yawlfoundation.yawl.datamodelling.BenchmarkRunner

echo
echo "Benchmark execution completed."