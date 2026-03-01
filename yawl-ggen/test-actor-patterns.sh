#!/bin/bash

# Simple validation script for actor patterns
echo "=== Actor Pattern Validation ==="

# Check if the compiled classes exist
if [ ! -f "target/classes/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.class" ]; then
    echo "Error: HyperStandardsValidator not compiled"
    exit 1
fi

# Check test fixture files
test_files=(
    "src/test/resources/fixtures/actor/violation-h-actor-leak.java"
    "src/test/resources/fixtures/actor/violation-h-actor-deadlock.java"
    "src/test/resources/fixtures/actor/clean-actor-code.java"
)

for file in "${test_files[@]}"; do
    if [ ! -f "$file" ]; then
        echo "Error: Test fixture $file not found"
        exit 1
    fi
done

echo "✓ Test fixtures found"

# Run a basic validation test using the compiled class
java -cp "target/classes" org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator \
    --emit "src/test/resources/fixtures/actor" \
    --receipt-file "target/actor-receipt.json"

if [ $? -eq 0 ]; then
    echo "✓ Validation completed successfully"
    
    # Check if receipt was generated
    if [ -f "target/actor-receipt.json" ]; then
        echo "✓ Receipt file generated"
        cat target/actor-receipt.json | grep -E "(status|violations|h_actor_)"
    else
        echo "✗ Receipt file not generated"
        exit 1
    fi
else
    echo "✗ Validation failed"
    exit 1
fi

echo "=== Manual Validation Complete ==="
