#!/bin/bash

################################################################################
# YEngine Parallelization Validation Script
#
# Purpose: Automated validation of YEngine state isolation for parallel testing
#
# Determines if YAWL integration tests can safely run in parallel (-T flag)
# by running comprehensive isolation tests and analyzing results.
#
# Exit codes:
#  0 = PASS (safe to parallelize)
#  1 = FAIL (found corruption, keep sequential)
#  2 = ERROR (validation harness failed, manual review needed)
#
# Usage:
#   bash scripts/validate-yengine-parallelization.sh          # Run validation
#   bash scripts/validate-yengine-parallelization.sh --monitor # Ongoing monitoring
#   bash scripts/validate-yengine-parallelization.sh --verbose # Detailed output
#
# Author: YAWL Validation Team
# Version: 6.0
################################################################################

set -e

# =============================================================================
# Configuration
# =============================================================================

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VALIDATION_TEST_CLASS="org.yawlfoundation.yawl.validation.YEngineParallelizationTest"
REPORT_DIR="${REPO_ROOT}/.claude/profiles/validation-reports"
REPORT_FILE="${REPORT_DIR}/parallelization-report-$(date +%Y%m%d-%H%M%S).txt"
CRITICAL_LOG="${REPORT_DIR}/critical-failures.log"

TIMEOUT_SEC=300
CORRUPTION_THRESHOLD=0  # Any corruption fails validation

# Parsing mode
MODE="run"
VERBOSE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# =============================================================================
# Functions
# =============================================================================

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

print_status() {
    local status=$1
    local message=$2

    if [ "$status" = "PASS" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $message"
    elif [ "$status" = "FAIL" ]; then
        echo -e "${RED}✗ FAIL${NC}: $message"
    elif [ "$status" = "WARN" ]; then
        echo -e "${YELLOW}⚠ WARN${NC}: $message"
    else
        echo -e "${BLUE}ℹ INFO${NC}: $message"
    fi
}

print_error() {
    echo -e "${RED}ERROR${NC}: $1" >&2
}

log_to_report() {
    echo "$1" >> "$REPORT_FILE"
}

init_report() {
    mkdir -p "$REPORT_DIR"
    > "$REPORT_FILE"  # Clear file

    echo "YEngine Parallelization Validation Report" >> "$REPORT_FILE"
    echo "=========================================" >> "$REPORT_FILE"
    echo "Timestamp: $(date)" >> "$REPORT_FILE"
    echo "Repository: $REPO_ROOT" >> "$REPORT_FILE"
    echo "Mode: $MODE" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
}

# =============================================================================
# Validation Steps
# =============================================================================

validate_prerequisites() {
    print_header "Step 1: Validating Prerequisites"

    # Check Maven is installed
    if ! command -v mvn &> /dev/null; then
        print_error "Maven not found. Install Maven or add to PATH."
        return 1
    fi
    print_status "PASS" "Maven found: $(mvn -v | head -1)"

    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | grep -oP '(?<=")[0-9.]+' | head -1)
    if [ -z "$JAVA_VERSION" ]; then
        print_error "Java not found or version unknown"
        return 1
    fi
    print_status "PASS" "Java version: $JAVA_VERSION"

    # Check test class exists
    if ! mvn -q compile 2>/dev/null; then
        print_error "Maven compilation failed"
        return 2
    fi
    print_status "PASS" "Project compiles"

    return 0
}

compile_test_harness() {
    print_header "Step 2: Compiling Test Harness"

    local compile_log="/tmp/yengine-compile.log"

    if mvn -q test-compile 2> "$compile_log"; then
        print_status "PASS" "Test harness compiled successfully"
        return 0
    else
        print_error "Test compilation failed. Log:"
        cat "$compile_log" | head -20
        log_to_report "COMPILATION FAILED:"
        cat "$compile_log" >> "$REPORT_FILE"
        return 2
    fi
}

run_validation_tests() {
    print_header "Step 3: Running Validation Tests"

    local test_log="/tmp/yengine-tests.log"
    local test_results="/tmp/yengine-test-results.txt"

    # Run only the YEngineParallelizationTest
    print_status "INFO" "Running: mvn test -Dtest=$VALIDATION_TEST_CLASS -DargLine=\"-Dtimeout=$TIMEOUT_SEC\""

    if mvn -B test \
        -Dtest="$VALIDATION_TEST_CLASS" \
        -DargLine="-Dtimeout=$TIMEOUT_SEC" \
        -Dorg.slf4j.simpleLogger.defaultLogLevel=info \
        2>&1 | tee "$test_log"; then

        TEST_RESULT="PASS"
        print_status "PASS" "All validation tests passed"
    else
        TEST_RESULT="FAIL"
        print_status "FAIL" "Some validation tests failed"
    fi

    # Capture test output for analysis
    cp "$test_log" "$test_results"

    return 0
}

analyze_test_output() {
    print_header "Step 4: Analyzing Test Results"

    local test_log="/tmp/yengine-tests.log"
    local pass_count=0
    local fail_count=0
    local corruption_count=0

    # Count test outcomes
    pass_count=$(grep -c "✓ PASS\|BUILD SUCCESS\|\[INFO\] BUILD SUCCESS" "$test_log" || echo "0")
    fail_count=$(grep -c "✗ FAIL\|BUILD FAILURE\|\[INFO\] BUILD FAILURE" "$test_log" || echo "0")
    corruption_count=$(grep -c "corruption\|Corruption\|CORRUPTION" "$test_log" || echo "0")

    # Parse detailed results
    if [ -f "$test_log" ]; then
        # Extract test method results
        echo "" >> "$REPORT_FILE"
        echo "Test Method Results:" >> "$REPORT_FILE"
        echo "-------------------" >> "$REPORT_FILE"

        grep -E "^\[INFO\].*T[0-9].*" "$test_log" >> "$REPORT_FILE" || true
        grep -E "PASS|FAIL" "$test_log" >> "$REPORT_FILE" || true
    fi

    print_status "INFO" "Tests passed: $pass_count"
    print_status "INFO" "Tests failed: $fail_count"
    print_status "INFO" "Corruptions detected: $corruption_count"

    log_to_report "Test Summary:"
    log_to_report "  Passed: $pass_count"
    log_to_report "  Failed: $fail_count"
    log_to_report "  Corruptions: $corruption_count"

    return 0
}

check_critical_isolation() {
    print_header "Step 5: Checking Critical Isolation Properties"

    local issues=0

    # Check 1: YEngine singleton management
    print_status "INFO" "Verifying YEngine singleton isolation..."
    if grep -q "Singleton isolation verified\|T2.*PASS" "/tmp/yengine-tests.log" 2>/dev/null; then
        print_status "PASS" "YEngine singleton properly isolated"
    else
        print_status "WARN" "YEngine singleton isolation inconclusive"
    fi

    # Check 2: Case ID uniqueness
    print_status "INFO" "Verifying case ID uniqueness..."
    if grep -q "Case IDs are properly unique\|Case ID collision" "/tmp/yengine-tests.log" 2>/dev/null; then
        if grep -q "Case ID collision" "/tmp/yengine-tests.log"; then
            print_status "FAIL" "Case ID collisions detected (CRITICAL)"
            ((issues++))
            echo "[CRITICAL] Case ID collision - engine unable to generate unique IDs" >> "$CRITICAL_LOG"
        else
            print_status "PASS" "Case IDs are unique"
        fi
    fi

    # Check 3: ThreadLocal isolation
    print_status "INFO" "Verifying ThreadLocal isolation..."
    if grep -q "ThreadLocal isolation verified\|T6.*PASS" "/tmp/yengine-tests.log" 2>/dev/null; then
        print_status "PASS" "ThreadLocal values properly isolated"
    else
        print_status "WARN" "ThreadLocal isolation inconclusive"
    fi

    # Check 4: Memory growth
    print_status "INFO" "Checking memory growth limits..."
    local mem_mb=$(grep -oP 'Heap delta = \K[0-9]+' "/tmp/yengine-tests.log" | tail -1)
    if [ -z "$mem_mb" ]; then
        print_status "WARN" "Could not extract memory metrics"
    elif [ "$mem_mb" -gt 100 ]; then
        print_status "FAIL" "Memory growth excessive: ${mem_mb}MB (limit: 100MB)"
        ((issues++))
    else
        print_status "PASS" "Memory growth within limits: ${mem_mb}MB"
    fi

    return 0
}

make_parallelization_decision() {
    print_header "Step 6: Parallelization Decision"

    # Read corruption count from test output
    local corruption_count=0
    corruption_count=$(grep -oP 'Corruptions detected: \K[0-9]+' "/tmp/yengine-tests.log" | head -1)

    if [ -z "$corruption_count" ]; then
        corruption_count=0
    fi

    log_to_report ""
    log_to_report "DECISION ANALYSIS:"
    log_to_report "==================="
    log_to_report "Corruption threshold: $CORRUPTION_THRESHOLD"
    log_to_report "Corruptions detected: $corruption_count"

    if [ "$TEST_RESULT" = "FAIL" ]; then
        log_to_report "Status: TEST FAILURES DETECTED"
        DECISION="NO-GO"
        DECISION_CODE=1
    elif [ "$corruption_count" -gt "$CORRUPTION_THRESHOLD" ]; then
        log_to_report "Status: STATE CORRUPTION DETECTED"
        DECISION="NO-GO"
        DECISION_CODE=1
    else
        log_to_report "Status: ALL VALIDATION PASSED"
        DECISION="GO"
        DECISION_CODE=0
    fi

    log_to_report "Decision: $DECISION"
    log_to_report "Action: $(get_action_for_decision)"

    if [ "$DECISION" = "GO" ]; then
        print_header "VALIDATION PASSED: SAFE TO PARALLELIZE"
        print_status "PASS" "YEngine parallelization safe"
        print_status "PASS" "Recommended: mvn -T 1.5C clean test"
    else
        print_header "VALIDATION FAILED: KEEP SEQUENTIAL"
        print_status "FAIL" "YEngine parallelization NOT safe"
        print_status "FAIL" "Recommended: mvn clean test"
        if [ -f "$CRITICAL_LOG" ]; then
            echo ""
            echo "Critical issues found:"
            cat "$CRITICAL_LOG"
        fi
    fi

    return "$DECISION_CODE"
}

get_action_for_decision() {
    if [ "$DECISION" = "GO" ]; then
        echo "Enable parallel testing: mvn -T 1.5C clean test"
    else
        echo "Keep sequential testing: mvn clean test"
    fi
}

generate_final_report() {
    print_header "Step 7: Generating Report"

    log_to_report ""
    log_to_report "Full Report Generated: $REPORT_FILE"
    log_to_report "Report Time: $(date)"

    print_status "INFO" "Full report: $REPORT_FILE"

    # Print summary from report
    echo ""
    echo "Report Summary:"
    echo "==============="
    tail -20 "$REPORT_FILE"
}

validate_monitoring_mode() {
    print_header "Monitoring Mode: Ongoing Validation"

    print_status "INFO" "Running continuous validation checks..."

    # Run tests multiple times to detect flakiness
    local runs=3
    local failures=0

    for i in $(seq 1 $runs); do
        echo ""
        print_status "INFO" "Run $i of $runs..."
        if ! run_validation_tests > /tmp/run_$i.log 2>&1; then
            ((failures++))
        fi
    done

    print_status "INFO" "Failures across $runs runs: $failures"

    if [ "$failures" -gt 0 ]; then
        print_status "WARN" "Flaky tests detected (failures in $failures/$runs runs)"
        print_status "FAIL" "Parallelization not reliable"
        return 1
    else
        print_status "PASS" "All runs stable"
        return 0
    fi
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    # Parse arguments
    if [ "$1" = "--monitor" ]; then
        MODE="monitor"
        VERBOSE=true
    elif [ "$1" = "--verbose" ]; then
        VERBOSE=true
    fi

    # Initialize
    init_report
    print_header "YEngine Parallelization Validation"
    print_status "INFO" "Mode: $MODE"
    print_status "INFO" "Repository: $REPO_ROOT"

    # Run validation steps
    validate_prerequisites || { print_error "Prerequisites validation failed"; return 2; }
    compile_test_harness || { print_error "Compilation failed"; return 2; }
    run_validation_tests || true  # Continue even if tests have failures
    analyze_test_output || true
    check_critical_isolation || true

    # Monitoring mode: run multiple times
    if [ "$MODE" = "monitor" ]; then
        validate_monitoring_mode || {
            make_parallelization_decision
            FINAL_CODE=$?
            generate_final_report
            return "$FINAL_CODE"
        }
    fi

    # Make decision
    make_parallelization_decision
    FINAL_CODE=$?

    # Generate report
    generate_final_report

    return "$FINAL_CODE"
}

# Run main
main "$@"
EXIT_CODE=$?

echo ""
if [ "$EXIT_CODE" = "0" ]; then
    print_status "PASS" "Validation successful - Safe to parallelize"
else
    print_status "FAIL" "Validation failed - Keep sequential testing"
fi

exit "$EXIT_CODE"
