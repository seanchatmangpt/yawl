#!/usr/bin/env bash
set -euo pipefail

# Edge Case Test Runner for YAWL Conformance Validation
# Runs all edge case tests and generates a comprehensive report

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="/tmp/yawl-test/edge-cases"
RECEIPTS_DIR="$SCRIPT_DIR/receipts"

# Create receipts directory if it doesn't exist
mkdir -p "$RECEIPTS_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track test results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
TEST_RESULTS=()

echo "========================================================"
echo "YAWL Edge Case Test Suite"
echo "========================================================"
echo

# Test case definitions
TEST_MODULES=(
    "edge_case_failed_task"
    "edge_case_timeout"
    "edge_case_resource_conflict"
    "edge_case_malformed_ocel"
    "edge_case_empty_events"
    "edge_case_circular_deps"
    "edge_case_invalid_states"
)

# Run each test case
for MODULE in "${TEST_MODULES[@]}"; do
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    TEST_START_TIME=$(date +%s)

    echo -n "Running $MODULE... "

    # Compile the module
    if erlc -o "$TEST_DIR" "$SCRIPT_DIR/$MODULE.erl" 2>/dev/null; then
        # Run the test
        if erl -pa "$TEST_DIR" -eval "
            case $MODULE:run() of
                {ok, Result} ->
                    io:format(\"ok~n\"),
                    file:write_file(\"$RECEIPTS_DIR/$MODULE.json\",
                        jsx:encode(Result)),
                    halt(0);
                {error, Reason} ->
                    io:format(\"failed: ~p~n\", [Reason]),
                    halt(1)
            end
        " -noshell; then
            # Test passed
            echo -e "${GREEN}ok${NC}"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            TEST_RESULTS+=("$MODULE: PASSED")

            # Move receipt to current directory
            if [ -f "$RECEIPTS_DIR/$MODULE.json" ]; then
                cp "$RECEIPTS_DIR/$MODULE.json" "$SCRIPT_DIR/receipt_${MODULE}.json"
            fi
        else
            # Test failed
            echo -e "${RED}failed${NC}"
            FAILED_TESTS=$((FAILED_TESTS + 1))
            TEST_RESULTS+=("$MODULE: FAILED")
        fi
    else
        # Compilation failed
        echo -e "${RED}compilation failed${NC}"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        TEST_RESULTS+=("$MODULE: COMPILATION FAILED")
    fi

    TEST_END_TIME=$(date +%s)
    DURATION=$((TEST_END_TIME - TEST_START_TIME))
    echo "  Time: ${DURATION}s"
    echo
done

# Generate summary report
SUMMARY_FILE="$RECEIPTS_DIR/edge_case_summary_$(date +%Y%m%d_%H%M%S).json"

SUMMARY_JSON=$(cat <<EOF
{
    "timestamp": "$(date -Iseconds)",
    "total_tests": $TOTAL_TESTS,
    "passed_tests": $PASSED_TESTS,
    "failed_tests": $FAILED_TESTS,
    "success_rate": $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc),
    "test_duration_seconds": $(date +%s),
    "test_results": [
EOF)

# Add individual test results
for i in "${!TEST_RESULTS[@]}"; do
    MODULE_RESULT="${TEST_RESULTS[$i]}"
    MODULE_NAME=$(echo "$MODULE_RESULT" | cut -d: -f1)
    STATUS=$(echo "$MODULE_RESULT" | cut -d: -f2)

    SUMMARY_JSON="$SUMMARY_JSON
        {
            \"module\": \"$MODULE_NAME\",
            \"status\": \"$STATUS\"
        }"

    if [ $i -lt $((${#TEST_RESULTS[@]} - 1)) ]; then
        SUMMARY_JSON="$SUMMARY_JSON,"
    fi
done

SUMMARY_JSON="$SUMMARY_JSON
    ]
}"

echo "$SUMMARY_JSON" | jq . > "$SUMMARY_FILE"

# Print final summary
echo "========================================================"
echo "Test Summary"
echo "========================================================"
echo "Total Tests: $TOTAL_TESTS"
echo "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo "Failed: ${RED}$FAILED_TESTS${NC}"
echo "Success Rate: ${GREEN}$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)%${NC}"

# Print detailed results
echo
echo "Detailed Results:"
echo "-----------------"
for result in "${TEST_RESULTS[@]}"; do
    if echo "$result" | grep -q "PASSED"; then
        echo -e "${GREEN}✓ $result${NC}"
    else
        echo -e "${RED}✗ $result${NC}"
    fi
done

# Print receipt file locations
echo
echo "Receipt Files:"
echo "------------"
for MODULE in "${TEST_MODULES[@]}"; do
    if [ -f "$SCRIPT_DIR/receipt_${MODULE}.json" ]; then
        echo "  $MODULE: $SCRIPT_DIR/receipt_${MODULE}.json"
    fi
done
echo "  Summary: $SUMMARY_FILE"

# Exit with appropriate code
if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "\n${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "\n${RED}Some tests failed!${NC}"
    exit 1
fi