#!/bin/bash
set -e

echo "=== YAWL Self-Play Loop v3.0 - Layer 7 Verification ==="
echo "Mission: Create the final verification infrastructure"
echo "Core Invariant: C1 > C0, C2 > C1, C3 > C2"
echo ""

set -e

# Function to run test and extract composition count
run_test_and_get_count() {
    local test_name=$1
    local temp_file=$(mktemp)
    
    # Run the test and capture output
    mvn test -Dtest=V7SelfPlayLoopTest#$test_name -q 2>&1 | tee $temp_file
    
    # Extract the composition count from the output
    local count=$(grep -o "Composition count.*: [0-9]*" $temp_file | tail -1 | awk '{print $3}')
    
    # Clean up
    rm -f $temp_file
    
    if [ -z "$count" ]; then
        echo "ERROR: Could not extract composition count from test output"
        return 1
    fi
    
    echo $count
}

echo "Starting verification..."

# Test 1: Check if test compilation works
echo "1. Compiling test classes..."
if mvn compile test-compile -q; then
    echo "✅ Test compilation successful"
else
    echo "❌ Test compilation failed"
    exit 1
fi

# Test 2: Run single iteration test
echo ""
echo "2. Running single iteration test..."
C0=$(run_test_and_get_count "testSingleIteration" | head -1)
echo "Initial composition count (C0): $C0"

# Test 3: Verify single iteration increased count
if [ -n "$C0" ] && [ "$C0" -gt 0 ]; then
    echo "✅ Single iteration test completed with C0 = $C0"
else
    echo "❌ Single iteration test failed or returned invalid count"
    exit 1
fi

# Test 4: Run three iterations test
echo ""
echo "3. Running three iterations test..."
C0=$(run_test_and_get_count "testThreeIterationsStrictlyIncreasing" | head -1)
echo "Initial composition count (C0): $C0"

C1=$(run_test_and_get_count "testThreeIterationsStrictlyIncreasing" | head -1)
echo "After iteration 1 (C1): $C1"

C2=$(run_test_and_get_count "testThreeIterationsStrictlyIncreasing" | head -1)
echo "After iteration 2 (C2): $C2"

C3=$(run_test_and_get_count "testThreeIterationsStrictlyIncreasing" | head -1)
echo "After iteration 3 (C3): $C3"

# Test 5: Verify the invariant C1 > C0, C2 > C1, C3 > C2
echo ""
echo "4. Verifying the one invariant..."
echo "Composition count trend: $C0 → $C1 → $C2 → $C3"

# Verify each step
if [ "$C1" -gt "$C0" ]; then
    echo "✅ C1 > C0: $C1 > $C0"
else
    echo "❌ C1 > C0 failed: $C1 > $C0"
    exit 1
fi

if [ "$C2" -gt "$C1" ]; then
    echo "✅ C2 > C1: $C2 > $C1"
else
    echo "❌ C2 > C1 failed: $C2 > $C1"
    exit 1
fi

if [ "$C3" -gt "$C2" ]; then
    echo "✅ C3 > C2: $C3 > $C2"
else
    echo "❌ C3 > C2 failed: $C3 > $C2"
    exit 1
fi

# Test 6: Calculate improvements
echo ""
echo "5. Calculating improvements..."
improvement1=$((C1 - C0))
improvement2=$((C2 - C1))
improvement3=$((C3 - C2))
total_improvement=$((C3 - C0))

echo "Improvement analysis:"
echo "  Iteration 1: +$improvement1"
echo "  Iteration 2: +$improvement2"
echo "  Iteration 3: +$improvement3"
echo "  Total: +$total_improvement"

# Test 7: Check final criteria
echo ""
echo "6. Checking final criteria..."
if [ "$C3" -ge 100 ]; then
    echo "✅ Final composition count ($C3) >= 100"
else
    echo "❌ Final composition count ($C3) < 100"
    exit 1
fi

# Test 8: Check for consistent improvement
if [ "$improvement1" -gt 0 ] && [ "$improvement2" -gt 0 ] && [ "$improvement3" -gt 0 ]; then
    echo "✅ All iterations show positive improvement"
else
    echo "❌ Some iterations show no improvement"
    exit 1
fi

echo ""
echo "=== FINAL VERIFICATION RESULTS ==="
echo "✅ THE ONE INVARIANT: C1 > C0, C2 > C1, C3 > C2"
echo "✅ Composition counts: $C0 → $C1 → $C2 → $C3"
echo "✅ Total improvement: +$total_improvement"
echo "✅ Final count meets threshold: $C3 >= 100"
echo ""
echo "YAWL Self-Play Loop v3.0 verification COMPLETE!"
echo "The loop successfully runs three full iterations with strictly increasing composition counts."
echo "Mission accomplished: Layer 7 verification infrastructure operational."
