#!/bin/bash

# Script to run YAWL GraalPy performance benchmarks

set -e

echo "🚀 Starting YAWL GraalPy Performance Benchmark Suite"
echo "=================================================="

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: This script must be run from the YAWL project root"
    exit 1
fi

# Build the project
echo "📦 Building project..."
mvn clean compile -q

# Set up test environment
export JAVA_OPTS="-XX:+UseCompactObjectHeaders -XX:+UseZGC -Xms2g -Xmx4g -XX:MaxGCPauseMillis=10"

echo "🧪 Running Performance Baseline Tests..."
echo "========================================"

# Change to the test directory
cd test/org/yawlfoundation/yawl/graalpy/performance

# Run the benchmarks
if [ "$1" = "--quick" ]; then
    echo "Running quick test (2 warmup, 3 measurement iterations)..."
    java -cp "$(find ../../../target -name "*.jar" | tr '\n' ':'):/usr/share/java/jmh.jar" \
         $JAVA_OPTS \
         org.openjdk.jmh.Main \
         PerformanceBaselinesTest \
         -rf json \
         -wi 2 \
         -i 3 \
         -f 1 \
         -o quick-benchmark-results.json
else
    echo "Running full benchmark suite..."
    java -cp "$(find ../../../target -name "*.jar" | tr '\n' ':'):/usr/share/java/jmh.jar" \
         $JAVA_OPTS \
         org.openjdk.jmh.Main \
         PerformanceBaselinesTest \
         -rf json \
         -wi 5 \
         -i 10 \
         -f 3 \
         -o benchmark-results.json
fi

# Move results to parent directory
cd ../..
mv performance/benchmark-results.json . 2>/dev/null || mv performance/quick-benchmark-results.json . 2>/dev/null

echo "✅ Benchmark completed!"
echo "📊 Results saved to: benchmark-results.json"

# Show summary if JSON file exists
if [ -f "benchmark-results.json" ]; then
    echo ""
    echo "=== Benchmark Results Summary ==="
    
    # Extract key metrics using jq (if available)
    if command -v jq &> /dev/null; then
        echo "Total operations benchmarked:"
        jq '. | length' benchmark-results.json
        
        echo ""
        echo "Benchmark modes used:"
        jq '. | .[].benchmark.mode' benchmark-results.json | sort | uniq | wc -l
        
        echo ""
        echo "Results (ms):"
        jq '.[] | "\(.benchmark): \(.primaryMetric.score)"' benchmark-results.json | head -20
    fi
fi

echo ""
echo "🔍 For detailed analysis, see benchmark-results.json"
echo "📖 For documentation, see test/org/yawlfoundation/yawl/graalpy/performance/README.md"
