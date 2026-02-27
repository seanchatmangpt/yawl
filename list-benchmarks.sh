#!/bin/bash

echo "YAWL JMH Benchmark List"
echo "===================="
echo ""
echo "Available Benchmark Classes:"
echo ""

# Find all benchmark classes and extract @Benchmark methods
find yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark -name "*.java" | while read file; do
    # Extract class name
    class=$(basename "$file" .java)
    echo "📁 $class"

    # Extract @Benchmark methods
    methods=$(grep -n "@Benchmark" "$file" | grep -v "@BenchmarkMode" | sed 's/.*@Benchmark.*public.*void \([^(]*\).*/    🔸 \1/')

    if [ ! -z "$methods" ]; then
        echo "$methods"
    else
        echo "    📝 No benchmark methods found"
    fi
    echo ""
done

echo ""
echo "Benchmark Categories:"
echo "🔸 Engine Benchmarks - YAWL engine performance tests"
echo "🔸 Workflow Patterns - YAWL pattern execution tests"
echo "🔸 A2A Communication - Agent-to-agent communication tests"
echo "🔸 Memory Optimization - Memory usage analysis"
echo "🔸 Chaos Engineering - Fault injection tests"
echo ""
echo "To run benchmarks:"
echo "mvn compile -pl yawl-benchmark -am"
echo "mvn exec:exec -pl yawl-benchmark -Dexec.executable=java -Dexec.args=\"-classpath %classpath org.openjdk.jmh.Main -l\""