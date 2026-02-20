#!/bin/bash
# Comprehensive test suite for team-recommendation.sh hook
# Tests multi-quantum detection, boundary conditions, edge cases, and prerequisite validation
#
# Usage: bash test-team-recommendation.sh
# Output: TAP (Test Anything Protocol) format with summary

HOOK_PATH="/home/user/yawl/.claude/hooks/team-recommendation.sh"
TEST_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test result tracking
declare -a TEST_RESULTS
declare -a TEST_NAMES

assert_exit_code() {
    local test_name="$1"
    local task_desc="$2"
    local expected_exit="$3"
    local test_num=$((++TEST_COUNT))

    # Run hook and capture exit code
    output=$("$HOOK_PATH" "$task_desc" 2>&1)
    actual_exit=$?

    if [[ $actual_exit -eq $expected_exit ]]; then
        echo -e "${GREEN}✓${NC} Test $test_num: $test_name (exit $actual_exit)"
        ((PASS_COUNT++))
        TEST_RESULTS+=("PASS")
        TEST_NAMES+=("$test_name")
        return 0
    else
        echo -e "${RED}✗${NC} Test $test_num: $test_name"
        echo "  Expected exit code: $expected_exit"
        echo "  Actual exit code: $actual_exit"
        echo "  Output: $(echo "$output" | head -5)"
        ((FAIL_COUNT++))
        TEST_RESULTS+=("FAIL")
        TEST_NAMES+=("$test_name")
        return 1
    fi
}

assert_output_contains() {
    local test_name="$1"
    local task_desc="$2"
    local expected_text="$3"
    local test_num=$((++TEST_COUNT))

    output=$("$HOOK_PATH" "$task_desc" 2>&1)

    if echo "$output" | grep -q "$expected_text"; then
        echo -e "${GREEN}✓${NC} Test $test_num: $test_name"
        ((PASS_COUNT++))
        TEST_RESULTS+=("PASS")
        TEST_NAMES+=("$test_name")
        return 0
    else
        echo -e "${RED}✗${NC} Test $test_num: $test_name"
        echo "  Expected to find: '$expected_text'"
        echo "  Output: $(echo "$output" | head -10)"
        ((FAIL_COUNT++))
        TEST_RESULTS+=("FAIL")
        TEST_NAMES+=("$test_name")
        return 1
    fi
}

assert_quantum_count() {
    local test_name="$1"
    local task_desc="$2"
    local expected_count="$3"
    local test_num=$((++TEST_COUNT))

    output=$("$HOOK_PATH" "$task_desc" 2>&1)

    # Extract quantum count from output
    quantum_count=$(echo "$output" | grep -oP "Detected \K\d+(?= quantums)" || echo "0")

    if [[ "$quantum_count" == "$expected_count" ]]; then
        echo -e "${GREEN}✓${NC} Test $test_num: $test_name (detected $quantum_count quantums)"
        ((PASS_COUNT++))
        TEST_RESULTS+=("PASS")
        TEST_NAMES+=("$test_name")
        return 0
    else
        echo -e "${RED}✗${NC} Test $test_num: $test_name"
        echo "  Expected quantums: $expected_count"
        echo "  Actual quantums: $quantum_count"
        echo "  Output: $(echo "$output" | grep "Detected")"
        ((FAIL_COUNT++))
        TEST_RESULTS+=("FAIL")
        TEST_NAMES+=("$test_name")
        return 1
    fi
}

# ============================================================================
# SECTION 1: Multi-Quantum Detection Tests
# ============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}SECTION 1: Multi-Quantum Detection${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Test 1.1: Explicit keywords with spaces
assert_quantum_count \
    "Multi-quantum: 'engine semantic' + 'schema definition' keywords" \
    "Fix engine semantic issue in task completion and modify schema definition" \
    "2"

# Test 1.2: Hyphenated keywords
assert_quantum_count \
    "Multi-quantum: 'task-completion' and 'type-definition' (hyphenated)" \
    "Improve task-completion logic and adjust type-definition constraints" \
    "2"

# Test 1.3: Case insensitivity - UPPERCASE
assert_quantum_count \
    "Multi-quantum: UPPERCASE keywords" \
    "FIX ENGINE SEMANTIC AND SCHEMA TYPE DEFINITION" \
    "2"

# Test 1.4: Case insensitivity - Mixed case
assert_quantum_count \
    "Multi-quantum: Mixed case keywords" \
    "Improve Engine Semantic and Schema Definition for workflow" \
    "2"

# Test 1.5: Three quantums detected
assert_quantum_count \
    "Multi-quantum: 3 quantums (engine + schema + integration)" \
    "Fix engine deadlock, modify schema definition, and add MCP event publisher endpoint" \
    "3"

# Test 1.6: Four quantums detected
assert_quantum_count \
    "Multi-quantum: 4 quantums (engine + schema + integration + resourcing)" \
    "Implement workflow engine improvements, update schema type definition, add A2A endpoint, and refactor resource allocation pool" \
    "4"

# Test 1.7: Five quantums detected
assert_quantum_count \
    "Multi-quantum: 5 quantums (max team size)" \
    "Fix engine state machine, update schema constraints, add MCP integration, improve resource workqueue, and write integration tests" \
    "5"

# Test 1.8: Engine semantic detection (YNetRunner pattern)
assert_quantum_count \
    "Engine semantic: YNetRunner-specific pattern" \
    "Debug YNetRunner deadlock in workflow execution" \
    "1"

# ============================================================================
# SECTION 2: Single Quantum Rejection (should NOT recommend team)
# ============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}SECTION 2: Single Quantum Rejection${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Test 2.1: Single quantum - engine only
assert_exit_code \
    "Single quantum rejection: engine only" \
    "Fix bug in YNetRunner class" \
    2

# Test 2.2: Single quantum - schema only
assert_exit_code \
    "Single quantum rejection: schema only" \
    "Update workflow type definition in XSD" \
    2

# Test 2.3: Single quantum - report-only (no implementation)
assert_exit_code \
    "Single quantum rejection: report-only analysis" \
    "Analyze performance metrics" \
    2

# Test 2.4: Single quantum - integration only
assert_exit_code \
    "Single quantum rejection: integration only" \
    "Add new MCP event publisher" \
    2

# Test 2.5: Verify warning message for single quantum
assert_output_contains \
    "Single quantum: warns about using single session" \
    "Fix deadlock in engine" \
    "Single quantum"

# ============================================================================
# SECTION 3: Boundary Tests (N=2 min, N=5 max, N=6+ rejection)
# ============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}SECTION 3: Boundary Tests (N=2, N=5, N=6+)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Test 3.1: Minimum team (N=2)
assert_exit_code \
    "Boundary: N=2 (minimum team) recommends team" \
    "Fix engine semantic and modify schema definition" \
    0

# Test 3.2: Verify team recommendation for N=2
assert_output_contains \
    "Boundary: N=2 shows 'TEAM MODE RECOMMENDED'" \
    "Fix engine semantic and modify schema definition" \
    "TEAM MODE RECOMMENDED"

# Test 3.3: Maximum team (N=5)
assert_exit_code \
    "Boundary: N=5 (maximum team) recommends team" \
    "Fix engine, modify schema, add integration endpoint, improve resource allocation, and write tests" \
    0

# Test 3.4: Verify team recommendation for N=5
assert_output_contains \
    "Boundary: N=5 shows 'TEAM MODE RECOMMENDED'" \
    "Fix engine, modify schema, add integration endpoint, improve resource allocation, and write tests" \
    "TEAM MODE RECOMMENDED"

# Test 3.5: Too many quantums (N=6)
assert_exit_code \
    "Boundary: N=6 (too many) rejects team" \
    "Fix engine, modify schema, add integration, improve resourcing, write tests, and add security" \
    2

# Test 3.6: Verify rejection message for N=6
assert_output_contains \
    "Boundary: N=6 shows 'Too many quantums'" \
    "Fix engine, modify schema, add integration, improve resourcing, write tests, and add security" \
    "Too many quantums"

# ============================================================================
# SECTION 4: Edge Cases
# ============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}SECTION 4: Edge Cases${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Test 4.1: Empty task description
assert_exit_code \
    "Edge case: empty task description" \
    "" \
    0

# Test 4.2: Task with ambiguous keywords
assert_quantum_count \
    "Edge case: ambiguous task 'Improve the system'" \
    "Improve the system" \
    "0"

# Test 4.3: Task with unknown keyword
assert_quantum_count \
    "Edge case: unknown keyword 'database'" \
    "Add database optimization layer" \
    "0"

# Test 4.4: Task with only spaces
assert_exit_code \
    "Edge case: whitespace-only description" \
    "   " \
    0

# Test 4.5: Task with special characters
assert_quantum_count \
    "Edge case: special characters in description" \
    "Fix (engine) bug & update [schema] @priority=HIGH" \
    "2"

# Test 4.6: Task with 'test' and 'coverage' keywords
assert_quantum_count \
    "Edge case: 'test' + 'coverage' detection" \
    "Write test coverage for engine" \
    "2"

# Test 4.7: Task with duplicate keywords
assert_quantum_count \
    "Edge case: duplicate keywords counted once" \
    "Fix engine engine deadlock in engine logic" \
    "1"

# Test 4.8: Long task description (100+ chars)
assert_quantum_count \
    "Edge case: very long task description" \
    "Implement a comprehensive solution to fix engine semantic issues, update schema definition constraints, and integrate MCP event publishers for improved workflow reliability and performance" \
    "3"

# ============================================================================
# SECTION 5: Quantum Pattern Detection Details
# ============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}SECTION 5: Quantum Pattern Detection Details${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Test 5.1: Engine pattern - workflow
assert_output_contains \
    "Engine pattern: 'workflow' keyword" \
    "Improve workflow execution logic" \
    "Engine Semantic"

# Test 5.2: Engine pattern - deadlock
assert_output_contains \
    "Engine pattern: 'deadlock' keyword" \
    "Fix deadlock in YNetRunner" \
    "Engine Semantic"

# Test 5.3: Schema pattern - specification
assert_output_contains \
    "Schema pattern: 'specification' keyword" \
    "Update specification constraints" \
    "Schema Definition"

# Test 5.4: Integration pattern - MCP
assert_output_contains \
    "Integration pattern: 'MCP' keyword" \
    "Add MCP server endpoint" \
    "Integration"

# Test 5.5: Integration pattern - event
assert_output_contains \
    "Integration pattern: 'event' keyword" \
    "Publish event notifications" \
    "Integration"

# Test 5.6: Resourcing pattern - workqueue
assert_output_contains \
    "Resourcing pattern: 'workqueue' keyword" \
    "Drain workqueue efficiently" \
    "Resourcing"

# Test 5.7: Testing pattern - e2e
assert_output_contains \
    "Testing pattern: 'e2e' keyword" \
    "Write e2e tests for workflow" \
    "Testing"

# Test 5.8: Security pattern - crypto
assert_output_contains \
    "Security pattern: 'crypto' keyword" \
    "Implement crypto validation" \
    "Security"

# Test 5.9: Stateless pattern - export
assert_output_contains \
    "Stateless pattern: 'export' keyword" \
    "Export case data" \
    "Stateless"

# ============================================================================
# SUMMARY
# ============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}TEST SUMMARY${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

echo ""
echo "Total tests: $TEST_COUNT"
echo -e "Passed: ${GREEN}$PASS_COUNT${NC}"
echo -e "Failed: ${RED}$FAIL_COUNT${NC}"

if [[ $FAIL_COUNT -eq 0 ]]; then
    echo ""
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}✗ $FAIL_COUNT test(s) failed${NC}"
    echo ""
    echo "Failed tests:"
    for i in "${!TEST_RESULTS[@]}"; do
        if [[ "${TEST_RESULTS[$i]}" == "FAIL" ]]; then
            echo -e "  ${RED}•${NC} ${TEST_NAMES[$i]}"
        fi
    done
    exit 1
fi
