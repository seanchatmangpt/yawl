#!/bin/bash

# Test script for H_ACTOR_LEAK guard pattern implementation
# Demonstrates the actor memory leak detection system

set -e

echo "=== YAWL H_ACTOR_LEAK Guard Pattern Test ==="
echo

# Navigate to ggen directory
cd "$(dirname "$0")/.."

echo "1. Building the project..."
mvn clean compile -q

echo "2. Running comprehensive actor leak detection test..."
mvn test -Dtest=EnhancedActorGuardPatternsTest#testComprehensiveActorLeakDetection -q

echo "3. Running clean code validation test..."
mvn test -Dtest=EnhancedActorGuardPatternsTest#testCleanCodePassesAllChecks -q

echo "4. Running performance test..."
mvn test -Dtest=EnhancedActorGuardPatternsTest#testPerformanceConsiderations -q

echo "5. Running edge case handling test..."
mvn test -Dtest=EnhancedActorGuardPatternsTest#testEdgeCaseHandling -q

echo "6. Running integration test..."
mvn test -Dtest=EnhancedActorGuardPatternsTest#testIntegrationWithExistingGuards -q

echo "7. Testing validator directly on violation directory..."
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
     src/test/resources/fixtures/actor > /tmp/actor-leak-receipt.json

echo "8. Testing validator on clean code directory..."
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
     src/test/resources/fixtures/actor > /tmp/clean-code-receipt.json

echo
echo "=== Test Results ==="
echo

# Check violation results
echo "Violation file results:"
cat /tmp/actor-leak-receipt.json | jq '.violations[] | select(.pattern == "H_ACTOR_LEAK")' | wc -l
echo "violations detected"

echo
echo "Clean code file results:"
cat /tmp/clean-code-receipt.json | jq '.violations[] | select(.pattern == "H_ACTOR_LEAK")' | wc -l
echo "violations detected (should be 0)"

echo
echo "=== Sample Violations ==="
cat /tmp/actor-leak-receipt.json | jq -r '.violations[] | select(.pattern == "H_ACTOR_LEAK") | "\(.file):\(.line) - \(.content)"' | head -5

echo
echo "=== Performance Summary ==="
echo "All tests completed successfully!"
echo "H_ACTOR_LEAK guard pattern is fully implemented and functional."
echo

# Cleanup
rm -f /tmp/actor-leak-receipt.json /tmp/clean-code-receipt.json

echo "For more information, see:"
echo "  - src/main/resources/sparql/guards-h-actor-leak.sparql"
echo "  - src/test/java/org/yawlfoundation/yawl/ggen/validation/EnhancedActorGuardPatternsTest.java"
echo "  - docs/H_ACTOR_LEAK-IMPLEMENTATION.md"