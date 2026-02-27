#!/bin/bash
#
# Test script for validate-performance-v2.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(dirname "$SCRIPT_DIR")"
VALIDATION_SCRIPT="${VALIDATION_DIR}/validation-scripts/validate-performance-v2.sh"

echo "=================================================="
echo "Testing YAWL Performance Validation Script"
echo "=================================================="

# Test 1: Help output
echo ""
echo "Test 1: Help output"
echo "-------------------"
if "$VALIDATION_SCRIPT" --help > /dev/null 2>&1; then
    echo "✓ Help command works"
else
    echo "✗ Help command failed"
    exit 1
fi

# Test 2: Invalid option
echo ""
echo "Test 2: Invalid option handling"
echo "-------------------------------"
if "$VALIDATION_SCRIPT" --invalid-option 2>/dev/null; then
    echo "✗ Should have failed with invalid option"
    exit 1
else
    echo "✓ Correctly rejects invalid options"
fi

# Test 3: Quiet mode
echo ""
echo "Test 3: Quiet mode"
echo "-------------------"
if "$VALIDATION_SCRIPT" --quiet --benchmark > /dev/null 2>&1; then
    echo "✓ Quiet mode executes without output"
else
    echo "✗ Quiet mode failed"
    exit 1
fi

# Test 4: Baseline mode (creates baseline file)
echo ""
echo "Test 4: Baseline mode"
echo "---------------------"
baseline_file="${VALIDATION_DIR}/baselines/baseline-test-$(date +%Y%m%d_%H%M%S).json"
"$VALIDATION_SCRIPT" --baseline --quiet
if [[ -f "${VALIDATION_DIR}/baselines/baseline-$(date +%Y%m%d_%H%M%S).json" ]]; then
    echo "✓ Baseline file created"
    rm -f "${VALIDATION_DIR}/baselines/baseline-$(date +%Y%m%d_%H%M%S).json"
else
    echo "✗ Baseline file not created"
    exit 1
fi

# Test 5: Compare mode with invalid baseline
echo ""
echo "Test 5: Compare mode with invalid baseline"
echo "------------------------------------------"
if "$VALIDATION_SCRIPT" --compare "/nonexistent/baseline.json" --quiet 2>/dev/null; then
    echo "✗ Should have failed with invalid baseline"
    exit 1
else
    echo "✓ Correctly rejects invalid baseline file"
fi

# Test 6: Script is executable
echo ""
echo "Test 6: Executable permissions"
echo "-----------------------------"
if [[ -x "$VALIDATION_SCRIPT" ]]; then
    echo "✓ Script is executable"
else
    echo "✗ Script is not executable"
    exit 1
fi

# Test 7: Required directory structure
echo ""
echo "Test 7: Directory structure"
echo "---------------------------"
if [[ -d "${VALIDATION_DIR}/validation-scripts" ]] && [[ -d "${VALIDATION_DIR}/reports" ]]; then
    echo "✓ Required directories exist"
else
    echo "✗ Required directories missing"
    exit 1
fi

# Test 8: All required files exist
echo ""
echo "Test 8: Required files"
echo "-----------------------"
required_files=(
    "$VALIDATION_SCRIPT"
    "${VALIDATION_DIR}/validation-scripts/wrk-post.lua"
)

for file in "${required_files[@]}"; do
    if [[ -f "$file" ]]; then
        echo "✓ $file exists"
    else
        echo "✗ $file missing"
        exit 1
    fi
done

# Test 9: JSON report generation
echo ""
echo "Test 9: JSON report generation"
echo "-----------------------------"
report_file="${VALIDATION_DIR}/reports/performance-report-$(date +%Y%m%d_%H%M%S).json"
"$VALIDATION_SCRIPT" --quiet --benchmark

# Check if recent report was created
recent_report=$(ls -t "${VALIDATION_DIR}/reports/performance-report-$(date +%Y%m%d)*.json" 2>/dev/null | head -1 | tail -1)
if [[ -n "$recent_report" ]]; then
    # Validate JSON syntax
    if python3 -c "import json; json.load(open('$recent_report'))" 2>/dev/null; then
        echo "✓ Valid JSON report generated"

        # Check required fields
        if python3 -c "
import json
with open('$recent_report', 'r') as f:
    data = json.load(f)
    required = ['timestamp', 'script_version', 'total_tests', 'passed', 'failed', 'status']
    for field in required:
        if field not in data:
            print(f'Missing field: {field}')
            exit(1)
print('✓ All required fields present')
" 2>/dev/null; then
            echo "✓ Report has all required fields"
        else
            echo "✗ Report missing required fields"
            exit 1
        fi
    else
        echo "✗ Invalid JSON generated"
        exit 1
    fi
else
    echo "✗ No recent report found"
    exit 1
fi

echo ""
echo "=================================================="
echo "All tests passed! ✓"
echo "=================================================="