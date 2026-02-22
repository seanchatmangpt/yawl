#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "Round-Trip Test: Turtle → YAWL"
echo "=========================================="
echo ""

# Test 1: Generate YAWL from Turtle
echo "[TEST 1] Generate YAWL from Turtle spec"
python3 scripts/ggen-wrapper.py generate \
  --template templates/yawl-xml/workflow.yawl.tera \
  --input tests/orderfulfillment.ttl \
  --output output/orderfulfillment_gen.yawl

if [ -f output/orderfulfillment_gen.yawl ]; then
    echo "✓ YAWL file generated successfully"
    FILE_SIZE=$(stat -c%s output/orderfulfillment_gen.yawl)
    echo "  File size: $FILE_SIZE bytes"
else
    echo "✗ YAWL file not found"
    exit 1
fi

# Test 2: Validate Turtle spec
echo ""
echo "[TEST 2] Validate Turtle specification"
if bash scripts/validate-turtle-spec.sh tests/orderfulfillment.ttl > /dev/null 2>&1; then
    echo "✓ Turtle validation passed"
else
    echo "✗ Turtle validation failed"
    exit 1
fi

# Test 3: Verify YAWL structure
echo ""
echo "[TEST 3] Verify YAWL structure"
TASK_COUNT=$(grep -c "<task id=" output/orderfulfillment_gen.yawl || echo "0")
echo "  Tasks found: $TASK_COUNT"

if grep -q "<specificationSet" output/orderfulfillment_gen.yawl; then
    echo "✓ YAWL has specificationSet element"
else
    echo "✗ Missing specificationSet element"
    exit 1
fi

if grep -q "<processControlElements" output/orderfulfillment_gen.yawl; then
    echo "✓ YAWL has processControlElements"
else
    echo "✗ Missing processControlElements"
    exit 1
fi

# Test 4: Check for expected tasks
echo ""
echo "[TEST 4] Verify expected tasks"
EXPECTED_TASKS=("check_inventory" "process_payment" "backorder_processing" "ship_order" "notify_customer")
for task in "${EXPECTED_TASKS[@]}"; do
    if grep -q "id=\"$task\"" output/orderfulfillment_gen.yawl; then
        echo "✓ Found task: $task"
    else
        echo "✗ Missing task: $task"
        exit 1
    fi
done

echo ""
echo "=========================================="
echo "All tests passed!"
echo "=========================================="
exit 0
