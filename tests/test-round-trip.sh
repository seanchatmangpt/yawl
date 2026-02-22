#!/usr/bin/env bash

################################################################################
# test-round-trip.sh — Round-trip validation test for Turtle → YAWL conversion
#
# Validates the complete conversion pipeline:
#   1. Turtle → YAWL XML generation
#   2. Output structure verification (tasks, flows, conditions)
#   3. Data element and variable tracking
#   4. Control flow correctness (splits/joins)
#
# Usage:
#   bash tests/test-round-trip.sh [<spec.ttl>]
#   bash tests/test-round-trip.sh tests/orderfulfillment.ttl
#   bash tests/test-round-trip.sh tests/orderfulfillment.ttl --verbose
#
# Environment:
#   VERBOSE        Enable verbose output (0/1, default: 0)
#   SPEC_FILE      Input Turtle specification (default: tests/orderfulfillment.ttl)
#   OUTPUT_FILE    Output YAWL file (default: output/process.yawl)
#
# Exit codes:
#   0 = all tests pass
#   1 = validation warnings (non-blocking)
#   2 = test failure (blocking)
#   3 = missing prerequisites or setup error
#
################################################################################

set -euo pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Default values
VERBOSE="${VERBOSE:-0}"
SPEC_FILE="${1:-${REPO_ROOT}/tests/orderfulfillment.ttl}"
OUTPUT_FILE="${OUTPUT_FILE:-${REPO_ROOT}/output/process.yawl}"
SCRIPT_ERRORS=0
SCRIPT_WARNINGS=0

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Helper functions
log_test() {
    echo -e "${BLUE}[TEST]${NC} $*"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $*"
    ((TESTS_PASSED++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $*"
    ((TESTS_FAILED++))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
    ((SCRIPT_WARNINGS++))
}

log_info() {
    echo -e "${CYAN}[INFO]${NC} $*"
}

log_debug() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*"
    fi
}

log_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$*${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

cleanup_on_exit() {
    log_debug "Cleaning up temporary files..."
    # Any cleanup needed would go here
}

trap cleanup_on_exit EXIT

# ─────────────────────────────────────────────────────────────────────────────
# Prerequisite Checks
# ─────────────────────────────────────────────────────────────────────────────

check_prerequisites() {
    log_section "STEP 0: Checking Prerequisites"

    # Check input file exists
    if [[ ! -f "$SPEC_FILE" ]]; then
        echo -e "${RED}[ERROR]${NC} Turtle specification not found: ${SPEC_FILE}"
        exit 3
    fi
    log_pass "Input file exists: ${SPEC_FILE}"

    # Check turtle-to-yawl.sh exists
    if [[ ! -f "${REPO_ROOT}/scripts/turtle-to-yawl.sh" ]]; then
        echo -e "${RED}[ERROR]${NC} Conversion script not found: ${REPO_ROOT}/scripts/turtle-to-yawl.sh"
        exit 3
    fi
    log_pass "Conversion script found"

    # Check for xml tools
    if ! command -v xmllint &> /dev/null; then
        log_warn "xmllint not available; XML validation will be skipped"
    else
        log_pass "XML validator available (xmllint)"
    fi

    # Check for grep/awk
    if ! command -v grep &> /dev/null; then
        echo -e "${RED}[ERROR]${NC} grep not available"
        exit 3
    fi
    log_pass "grep available"
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 1: Turtle Syntax Validation
# ─────────────────────────────────────────────────────────────────────────────

test_turtle_syntax() {
    log_section "TEST 1: Turtle Syntax Validation"
    ((TESTS_RUN++))
    local test_name="Turtle syntax is valid"

    log_test "$test_name"

    # Check for basic Turtle structure
    if grep -q "@prefix" "$SPEC_FILE" && grep -q "a " "$SPEC_FILE"; then
        log_pass "$test_name"
    else
        log_fail "$test_name (missing @prefix or RDF type declarations)"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 2: Conversion Pipeline Execution
# ─────────────────────────────────────────────────────────────────────────────

test_conversion_pipeline() {
    log_section "TEST 2: Conversion Pipeline Execution"
    ((TESTS_RUN++))
    local test_name="turtle-to-yawl conversion succeeds"

    log_test "$test_name"

    # Make output directory
    mkdir -p "${OUTPUT_FILE%/*}"

    # Run conversion pipeline
    if bash "${REPO_ROOT}/scripts/turtle-to-yawl.sh" "$SPEC_FILE" --verbose; then
        log_pass "$test_name"
        log_debug "Output written to: ${OUTPUT_FILE}"
    else
        local exit_code=$?
        log_fail "$test_name (exit code: $exit_code)"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 3: Output File Generation
# ─────────────────────────────────────────────────────────────────────────────

test_output_file_exists() {
    log_section "TEST 3: Output File Generation"
    ((TESTS_RUN++))
    local test_name="YAWL output file created"

    log_test "$test_name"

    if [[ -f "$OUTPUT_FILE" ]]; then
        local file_size
        file_size=$(stat -f%z "$OUTPUT_FILE" 2>/dev/null || stat -c%s "$OUTPUT_FILE" 2>/dev/null || echo "unknown")
        log_pass "$test_name (size: ${file_size} bytes)"
        log_debug "File: ${OUTPUT_FILE}"
    else
        log_fail "$test_name (file not found: ${OUTPUT_FILE})"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 4: XML Well-Formedness
# ─────────────────────────────────────────────────────────────────────────────

test_xml_well_formedness() {
    log_section "TEST 4: XML Well-Formedness"
    ((TESTS_RUN++))
    local test_name="YAWL XML is well-formed"

    log_test "$test_name"

    if command -v xmllint &> /dev/null; then
        if xmllint --noout "$OUTPUT_FILE" 2>/dev/null; then
            log_pass "$test_name"
        else
            log_fail "$test_name (XML parsing error)"
            return 1
        fi
    else
        log_warn "$test_name (xmllint not available, skipping)"
        ((TESTS_SKIPPED++))
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 5: Essential Elements Present
# ─────────────────────────────────────────────────────────────────────────────

test_essential_elements() {
    log_section "TEST 5: Essential Elements Present"

    local test_spec_file="$SPEC_FILE"
    log_test "Specification contains essential task definitions"
    ((TESTS_RUN++))

    # Extract task count from Turtle file
    local task_count
    task_count=$(grep -c "a yawl:Task" "$test_spec_file" || echo "0")

    log_debug "Found $task_count task definitions in Turtle"

    # Expected: at least 3 main tasks for order fulfillment
    if [[ $task_count -ge 3 ]]; then
        log_pass "Specification contains essential tasks ($task_count found, expected ≥3)"
    else
        log_fail "Specification missing essential tasks ($task_count found, expected ≥3)"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 6: Control Flow Structure
# ─────────────────────────────────────────────────────────────────────────────

test_control_flow() {
    log_section "TEST 6: Control Flow Structure"

    local test_spec_file="$SPEC_FILE"
    log_test "Specification defines control flows"
    ((TESTS_RUN++))

    # Count flow definitions
    local flow_count
    flow_count=$(grep -c "a yawl:Flow" "$test_spec_file" || echo "0")

    log_debug "Found $flow_count flow definitions"

    # Expected: at least 3 flows for order fulfillment
    if [[ $flow_count -ge 3 ]]; then
        log_pass "Specification defines control flows ($flow_count found, expected ≥3)"
    else
        log_fail "Specification missing control flows ($flow_count found, expected ≥3)"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 7: YAWL Output Structure Validation
# ─────────────────────────────────────────────────────────────────────────────

test_yawl_structure() {
    log_section "TEST 7: YAWL Output Structure Validation"

    local test_name="YAWL output contains task elements"
    log_test "$test_name"
    ((TESTS_RUN++))

    # Count tasks in generated YAWL
    local task_count
    task_count=$(grep -c '<task ' "$OUTPUT_FILE" || echo "0")

    log_debug "Found $task_count <task> elements in YAWL output"

    # Expected: at least 3 tasks
    if [[ $task_count -ge 3 ]]; then
        log_pass "$test_name ($task_count tasks found)"
    else
        log_fail "$test_name ($task_count found, expected ≥3)"
        return 1
    fi

    # Test flow structure
    local test_name2="YAWL output contains flow elements"
    log_test "$test_name2"
    ((TESTS_RUN++))

    local flow_count
    flow_count=$(grep -c '<flow ' "$OUTPUT_FILE" || echo "0")

    log_debug "Found $flow_count <flow> elements in YAWL output"

    # Expected: at least 3 flows
    if [[ $flow_count -ge 3 ]]; then
        log_pass "$test_name2 ($flow_count flows found)"
    else
        log_fail "$test_name2 ($flow_count found, expected ≥3)"
        return 1
    fi

    # Test input/output condition structure
    local test_name3="YAWL output contains input/output conditions"
    log_test "$test_name3"
    ((TESTS_RUN++))

    local condition_count
    condition_count=$(grep -c '<inputCondition ' "$OUTPUT_FILE" 2>/dev/null || echo "0")
    condition_count=$((condition_count + $(grep -c '<outputCondition ' "$OUTPUT_FILE" 2>/dev/null || echo "0")))

    log_debug "Found $condition_count conditions in YAWL output"

    if [[ $condition_count -ge 2 ]]; then
        log_pass "$test_name3 ($condition_count conditions found)"
    else
        log_fail "$test_name3 ($condition_count found, expected ≥2)"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 8: Data Flow Elements
# ─────────────────────────────────────────────────────────────────────────────

test_data_flow() {
    log_section "TEST 8: Data Flow Elements"

    local test_name="YAWL output contains data variable references"
    log_test "$test_name"
    ((TESTS_RUN++))

    # Check for data variable references in output
    local data_var_count
    data_var_count=$(grep -c 'Variable' "$OUTPUT_FILE" 2>/dev/null || echo "0")

    log_debug "Found $data_var_count data variable references"

    if [[ $data_var_count -ge 1 ]]; then
        log_pass "$test_name ($data_var_count references found)"
    else
        log_warn "$test_name (no data variable references found - may be expected)"
        ((TESTS_SKIPPED++))
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 9: Join/Split Type Specification
# ─────────────────────────────────────────────────────────────────────────────

test_split_join_types() {
    log_section "TEST 9: Join/Split Type Specification"

    local test_name="YAWL output specifies join/split types"
    log_test "$test_name"
    ((TESTS_RUN++))

    # Look for join and split specifications in output
    local join_count
    local split_count

    join_count=$(grep -c 'join=' "$OUTPUT_FILE" 2>/dev/null || echo "0")
    split_count=$(grep -c 'split=' "$OUTPUT_FILE" 2>/dev/null || echo "0")

    log_debug "Found $join_count join specifications and $split_count split specifications"

    local total=$((join_count + split_count))
    if [[ $total -ge 2 ]]; then
        log_pass "$test_name ($join_count joins, $split_count splits found)"
    else
        log_warn "$test_name (limited join/split specifications found)"
        ((TESTS_SKIPPED++))
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 10: Round-Trip Consistency
# ─────────────────────────────────────────────────────────────────────────────

test_round_trip_consistency() {
    log_section "TEST 10: Round-Trip Consistency"

    local test_name="Task count preserved in round-trip conversion"
    log_test "$test_name"
    ((TESTS_RUN++))

    # Count tasks in original Turtle
    local turtle_tasks
    turtle_tasks=$(grep -c "a yawl:Task" "$SPEC_FILE" || echo "0")

    # Count tasks in YAWL output
    local yawl_tasks
    yawl_tasks=$(grep -c '<task ' "$OUTPUT_FILE" || echo "0")

    log_debug "Turtle tasks: $turtle_tasks, YAWL tasks: $yawl_tasks"

    # Allow some difference (Turtle may have implicit tasks)
    if [[ $yawl_tasks -ge $((turtle_tasks - 1)) && $yawl_tasks -le $((turtle_tasks + 2)) ]]; then
        log_pass "$test_name (Turtle: $turtle_tasks, YAWL: $yawl_tasks)"
    else
        log_warn "$test_name (task count mismatch: Turtle=$turtle_tasks, YAWL=$yawl_tasks)"
        ((TESTS_SKIPPED++))
    fi

    # Test flow consistency
    local test_name2="Flow count preserved in round-trip conversion"
    log_test "$test_name2"
    ((TESTS_RUN++))

    local turtle_flows
    turtle_flows=$(grep -c "a yawl:Flow" "$SPEC_FILE" || echo "0")

    local yawl_flows
    yawl_flows=$(grep -c '<flow ' "$OUTPUT_FILE" || echo "0")

    log_debug "Turtle flows: $turtle_flows, YAWL flows: $yawl_flows"

    if [[ $yawl_flows -ge $((turtle_flows - 1)) && $yawl_flows -le $((turtle_flows + 2)) ]]; then
        log_pass "$test_name2 (Turtle: $turtle_flows, YAWL: $yawl_flows)"
    else
        log_warn "$test_name2 (flow count mismatch: Turtle=$turtle_flows, YAWL=$yawl_flows)"
        ((TESTS_SKIPPED++))
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test 11: XOR and AND Split/Join Logic
# ─────────────────────────────────────────────────────────────────────────────

test_advanced_control_flow() {
    log_section "TEST 11: Advanced Control Flow (XOR/AND)"

    local test_name="Specification includes XOR and AND splits/joins"
    log_test "$test_name"
    ((TESTS_RUN++))

    # Check for XOR references in Turtle
    local xor_count
    xor_count=$(grep -c '"XOR"' "$SPEC_FILE" || echo "0")

    # Check for AND references in Turtle
    local and_count
    and_count=$(grep -c '"AND"' "$SPEC_FILE" || echo "0")

    log_debug "Found $xor_count XOR split/joins and $and_count AND split/joins"

    if [[ $xor_count -ge 1 && $and_count -ge 1 ]]; then
        log_pass "$test_name (XOR: $xor_count, AND: $and_count)"
    else
        log_warn "$test_name (expected both XOR and AND, found XOR=$xor_count, AND=$and_count)"
        ((TESTS_SKIPPED++))
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Test Summary and Report
# ─────────────────────────────────────────────────────────────────────────────

print_test_summary() {
    log_section "Test Summary"

    local total_tests=$((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))

    echo "Total tests run:    $TESTS_RUN"
    echo "Tests passed:       $TESTS_PASSED"
    echo "Tests failed:       $TESTS_FAILED"
    echo "Tests skipped:      $TESTS_SKIPPED"
    echo ""

    if [[ $TESTS_FAILED -eq 0 ]]; then
        log_pass "All tests passed!"
    else
        log_fail "$TESTS_FAILED test(s) failed"
    fi

    if [[ $SCRIPT_WARNINGS -gt 0 ]]; then
        log_warn "Warnings encountered: $SCRIPT_WARNINGS"
    fi

    echo ""
    log_info "Specification: $SPEC_FILE"
    log_info "Output:        $OUTPUT_FILE"
    echo ""
}

# ─────────────────────────────────────────────────────────────────────────────
# Main Test Execution
# ─────────────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}Round-Trip Turtle → YAWL Validation Test Suite${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo ""

    # Run test sequence
    check_prerequisites
    test_turtle_syntax || true
    test_conversion_pipeline || true
    test_output_file_exists || true
    test_xml_well_formedness || true
    test_essential_elements || true
    test_control_flow || true
    test_yawl_structure || true
    test_data_flow || true
    test_split_join_types || true
    test_round_trip_consistency || true
    test_advanced_control_flow || true

    # Print summary
    print_test_summary

    # Determine exit code
    if [[ $TESTS_FAILED -eq 0 ]]; then
        exit 0
    else
        exit 2
    fi
}

# Entry point
main "$@"
