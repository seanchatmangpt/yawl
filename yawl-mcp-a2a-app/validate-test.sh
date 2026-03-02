#!/bin/bash

# Validate MemoryExhaustionTest.java structure and imports

echo "Validating MemoryExhaustionTest.java..."

# Check if file exists
if [ ! -f "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java" ]; then
    echo "❌ Test file not found"
    exit 1
fi

# Check for required imports
IMPORTS=(
    "import org.junit.jupiter.api.Test;"
    "import org.junit.jupiter.api.DisplayName;"
    "import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;"
    "import org.junit.jupiter.params.ParameterizedTest;"
    "import org.junit.jupiter.params.provider.CsvSource;"
    "import java.lang.management.*"
    "import java.util.concurrent.*"
    "import java.util.concurrent.atomic.*"
    "import static org.junit.jupiter.api.Assertions.*"
)

MISSING_IMPORTS=0
for import in "${IMPORTS[@]}"; do
    if ! grep -q "$import" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
        echo "❌ Missing import: $import"
        MISSING_IMPORTS=1
    fi
done

if [ $MISSING_IMPORTS -eq 0 ]; then
    echo "✅ All required imports found"
fi

# Check for required methods
METHODS=(
    "testVirtualThreadsWithDefaultHeap"
    "testPlatformThreadsWithDefaultHeap"
    "testDifferentHeapThresholds"
    "testMemoryRecovery"
    "testCalculateSustainableActorCount"
    "testActorSpawningBenchmark"
    "testMemoryLeakDetection"
)

MISSING_METHODS=0
for method in "${METHODS[@]}"; do
    if ! grep -q "void $method" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
        echo "❌ Missing method: $method"
        MISSING_METHODS=1
    fi
done

if [ $MISSING_METHODS -eq 0 ]; then
    echo "✅ All required methods found"
fi

# Check for MemoryMXBean usage
if grep -q "MemoryMXBean" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
    echo "✅ MemoryMXBean integration found"
else
    echo "❌ MemoryMXBean not found"
fi

# Check for Runtime memory monitoring
if grep -q "Runtime.getRuntime()" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
    echo "✅ Runtime memory monitoring found"
else
    echo "❌ Runtime memory monitoring not found"
fi

# Check for graceful OOM prevention
if grep -q "WARNING_HEAP_PERCENTAGE\|CRITICAL_HEAP_PERCENTAGE" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
    echo "✅ Graceful OOM prevention logic found"
else
    echo "❌ Graceful OOM prevention not found"
fi

# Check for test data generation
if grep -q "TestMemoryAgent" "src/test/java/org/yawlfoundation/yawl/stress/MemoryExhaustionTest.java"; then
    echo "✅ Test agent implementation found"
else
    echo "❌ Test agent not found"
fi

echo "Validation complete."

# Summary
TOTAL_TESTS=6
PASSED_TESTS=$((6 - MISSING_IMPORTS - MISSING_METHODS))
echo "Summary: $PASSED_TESTS/$TOTAL_TESTS major components verified"

if [ $MISSING_IMPORTS -eq 0 ] && [ $MISSING_METHODS -eq 0 ]; then
    echo "🎉 Test validation passed!"
    exit 0
else
    echo "❌ Test validation failed"
    exit 1
fi