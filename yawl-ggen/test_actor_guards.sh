#!/bin/bash

# Script to test actor guard patterns integration
# This script runs the actor guard validation tests

echo "Testing YAWL Actor Guard Patterns Integration"
echo "==========================================="

# Navigate to yawl-ggen directory
cd /Users/sac/yawl/yawl-ggen

# Clean and build
echo "Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✅ Build successful"

# Run specific actor tests
echo "Running actor guard pattern tests..."
mvn test -Dtest=ActorGuardPatternsTest -q

if [ $? -eq 0 ]; then
    echo "✅ All actor guard tests passed"
else
    echo "❌ Some actor guard tests failed"
    exit 1
fi

# Test with sample actor code
echo "Testing actor guard patterns on sample code..."
mkdir -p /tmp/test-actors
cp src/test/resources/fixtures/actor/*.java /tmp/test-actors/

# Run validation
java -cp target/classes org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
    /tmp/test-actors \
    /tmp/actor-guard-receipt.json

EXIT_CODE=$?

echo "Validation exit code: $EXIT_CODE"

# Show receipt summary if it was generated
if [ -f "/tmp/actor-guard-receipt.json" ]; then
    echo ""
    echo "Guard Receipt Summary:"
    echo "--------------------"
    cat /tmp/actor-guard-receipt.json | jq '.status, .summary' 2>/dev/null || \
    cat /tmp/actor-guard-receipt.json
fi

# Cleanup
rm -rf /tmp/test-actors /tmp/actor-guard-receipt.json

echo ""
echo "Integration test completed with exit code: $EXIT_CODE"
exit $EXIT_CODE
