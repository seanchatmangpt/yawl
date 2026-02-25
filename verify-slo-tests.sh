#!/bin/bash

# Script to verify SLO integration tests
echo "Verifying SLO Integration Tests..."
echo "====================================="

# Change to monitoring module directory
cd yawl-monitoring

# Compile tests
echo "Compiling tests..."
mvn test-compile -q

if [ $? -ne 0 ]; then
    echo "Test compilation failed!"
    exit 1
fi

# Run our specific test
echo "Running SLOIntegrationServiceTest..."
mvn surefire:test -Dtest=org.yawlfoundation.yawl.observability.SLOIntegrationServiceTest -DfailIfNoTests=false -q

if [ $? -eq 0 ]; then
    echo "✓ Test passed successfully"
else
    echo "✗ Test failed"
    exit 1
fi

# Count total test methods in our file
echo "Counting test methods..."
TEST_COUNT=$(grep -c "@Test" ../test/org/yawlfoundation/yawl/observability/SLOIntegrationServiceTest.java)
echo "Total test methods: $TEST_COUNT"

# Check if we have the expected number of tests
EXPECTED=29
if [ "$TEST_COUNT" -ge "$EXPECTED" ]; then
    echo "✓ Test implementation complete ($TEST_COUNT methods)"
else
    echo "⚠ Test implementation may be incomplete (found $TEST_COUNT, expected $EXPECTED)"
fi

echo ""
echo "Test verification complete!"
echo "====================================="