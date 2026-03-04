#!/bin/bash
set -e

echo "=== YAWL Self-Play Loop v3.0 Final Gate Check ==="
echo "Running final verification checks..."

# Gate 1: Check if yawl-native is running
echo "Gate 1: Checking YAWL native service..."
if ps aux | grep -q "yawl-native"; then
    echo "✅ YAWL native service is running"
else
    echo "❌ YAWL native service is not running"
    exit 1
fi

# Gate 2: Check if generated TTL files exist
echo "Gate 2: Checking generated TTL files..."
TTL_COUNT=$(find /Users/sac/yawl -name "*.ttl" -path "*/generated/*" 2>/dev/null | wc -l | tr -d ' ' || echo 0)
echo "Generated TTL files: $TTL_COUNT"
if [ "$TTL_COUNT" -eq 0 ]; then
    echo "❌ No TTL files found in generated/ directory"
    exit 1
fi
echo "✅ TTL files exist"

# Gate 3: Run single iteration test
echo "Gate 3: Running single iteration test..."
cd /Users/sac/yawl
if mvn test -Dtest=V7SelfPlayLoopTest#testSingleIteration -q; then
    echo "✅ Single iteration test passed"
else
    echo "❌ Single iteration test failed"
    exit 1
fi

# Gate 4: Run three iterations test
echo "Gate 4: Running three iterations test..."
if mvn test -Dtest=V7SelfPlayLoopTest#testThreeIterationsStrictlyIncreasing -q; then
    echo "✅ Three iterations test passed"
else
    echo "❌ Three iterations test failed"
    exit 1
fi

# Gate 5: Check if composition count increased
echo "Gate 5: Verifying composition count increase..."
C0=$(mvn test -Dtest=V7SelfPlayLoopTest#testInnerLoopIncreasesCompositionCount -q 2>&1 | grep "Initial composition count:" | awk '{print $4}')
C1=$(mvn test -Dtest=V7SelfPlayLoopTest#testSingleIteration -q 2>&1 | grep "Composition count after iteration:" | awk '{print $4}')

if [ -n "$C0" ] && [ -n "$C1" ] && [ "$C1" -gt "$C0" ]; then
    echo "✅ Composition count increased from $C0 to $C1"
else
    echo "❌ Composition count did not increase properly"
    exit 1
fi

echo ""
echo "=== ALL GATES PASSED ==="
echo "YAWL Self-Play Loop v3.0 verification complete!"
echo ""
echo "Final verification:"
echo "✅ YAWL native service running"
echo "✅ TTL files generated"
echo "✅ Single iteration test passed"
echo "✅ Three iterations test passed"
echo "✅ Composition count increased"
echo ""
echo "=== THE ONE INVARIANT ==="
echo "C1 > C0: $C1 > $C0 ✅"
echo "The self-play loop is self-improving!"
