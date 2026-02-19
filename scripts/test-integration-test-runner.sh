#!/usr/bin/env bash
# ==========================================================================
# test-integration-test-runner.sh - Test Suite for Integration Test Runner
#
# Validates all functionality of the run-integration-tests.sh script including
# command-line options, report generation, and error handling.
#
# Usage:
#   bash scripts/test-integration-test-runner.sh
#
# Exit codes:
#   0 - All tests passed
#   1 - Some tests failed
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../" && pwd)"
TEST_RUNNER="$SCRIPT_DIR/run-integration-tests.sh"

# Colors
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Logging functions
log_info() {
    echo -e "${BLUE}[TEST]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_section() {
    echo "" >&2
    echo -e "${CYAN}=== $1 ===${NC}" >&2
}

# Record test result
record_test() {
    local test_name="$1"
    local result="$2"

    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    if [[ "$result" == "pass" ]]; then
        TESTS_PASSED=$((TESTS_PASSED + 1))
        log_success "$test_name"
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
        log_error "$test_name"
    fi
}

# ── Test Cases ──────────────────────────────────────────────────────────────

# Test 1: Help message
test_help_message() {
    log_section "Test 1: Help Message"

    if "$TEST_RUNNER" --help >/dev/null 2>&1; then
        # Verify help contains expected content
        if "$TEST_RUNNER" --help 2>&1 | grep -q "Enhanced Integration Test Runner"; then
            record_test "Help message displays correctly" "pass"
        else
            record_test "Help message missing expected content" "fail"
        fi
    else
        record_test "Help message execution failed" "fail"
    fi

    # Test -h short option
    if "$TEST_RUNNER" -h >/dev/null 2>&1; then
        record_test "Short -h option works" "pass"
    else
        record_test "Short -h option failed" "fail"
    fi
}

# Test 2: Module validation
test_module_validation() {
    log_section "Test 2: Module Validation"

    cd "$PROJECT_ROOT"

    # Test invalid module
    if "$TEST_RUNNER" --module=invalid-module-xyz 2>&1 | grep -q "Invalid module"; then
        record_test "Invalid module rejection works" "pass"
    else
        record_test "Invalid module should be rejected" "fail"
    fi

    # Test valid module (yawl-engine)
    if "$TEST_RUNNER" --module=yawl-engine --help >/dev/null 2>&1; then
        record_test "Valid module accepted (yawl-engine)" "pass"
    else
        record_test "Valid module should be accepted" "fail"
    fi

    # Test valid module (yawl-integration)
    if "$TEST_RUNNER" --module=yawl-integration --help >/dev/null 2>&1; then
        record_test "Valid module accepted (yawl-integration)" "pass"
    else
        record_test "Valid module should be accepted" "fail"
    fi
}

# Test 3: Argument parsing
test_argument_parsing() {
    log_section "Test 3: Argument Parsing"

    cd "$PROJECT_ROOT"

    # Test invalid argument
    if "$TEST_RUNNER" --invalid-arg-xyz 2>&1 | grep -q "Unknown option"; then
        record_test "Unknown option rejection works" "pass"
    else
        record_test "Unknown option should be rejected" "fail"
    fi

    # Test parallel argument (valid)
    if "$TEST_RUNNER" --parallel=4 --help >/dev/null 2>&1; then
        record_test "Parallel argument (valid number) works" "pass"
    else
        record_test "Parallel argument failed" "fail"
    fi

    # Test parallel argument (invalid)
    if "$TEST_RUNNER" --parallel=abc 2>&1 | grep -q "Invalid parallel value"; then
        record_test "Parallel argument (invalid) rejected" "pass"
    else
        record_test "Invalid parallel value should be rejected" "fail"
    fi

    # Test timeout argument (valid)
    if "$TEST_RUNNER" --timeout=600 --help >/dev/null 2>&1; then
        record_test "Timeout argument (valid number) works" "pass"
    else
        record_test "Timeout argument failed" "fail"
    fi

    # Test timeout argument (invalid)
    if "$TEST_RUNNER" --timeout=xyz 2>&1 | grep -q "Invalid timeout value"; then
        record_test "Timeout argument (invalid) rejected" "pass"
    else
        record_test "Invalid timeout value should be rejected" "fail"
    fi

    # Test format argument (valid)
    for format in json html both; do
        if "$TEST_RUNNER" --format="$format" --help >/dev/null 2>&1; then
            record_test "Format argument ($format) works" "pass"
        else
            record_test "Format argument ($format) failed" "fail"
        fi
    done

    # Test format argument (invalid)
    if "$TEST_RUNNER" --format=xml 2>&1 | grep -q "Invalid format"; then
        record_test "Format argument (invalid) rejected" "pass"
    else
        record_test "Invalid format should be rejected" "fail"
    fi

    # Test retry argument (valid)
    if "$TEST_RUNNER" --retry=2 --help >/dev/null 2>&1; then
        record_test "Retry argument (valid number) works" "pass"
    else
        record_test "Retry argument failed" "fail"
    fi

    # Test retry argument (invalid)
    if "$TEST_RUNNER" --retry=abc 2>&1 | grep -q "Invalid retry value"; then
        record_test "Retry argument (invalid) rejected" "pass"
    else
        record_test "Invalid retry value should be rejected" "fail"
    fi
}

# Test 4: Boolean flags
test_boolean_flags() {
    log_section "Test 4: Boolean Flags"

    cd "$PROJECT_ROOT"

    # Test verbose flag
    if "$TEST_RUNNER" --verbose --help >/dev/null 2>&1; then
        record_test "Verbose flag works" "pass"
    else
        record_test "Verbose flag failed" "fail"
    fi

    # Test short -v flag
    if "$TEST_RUNNER" -v --help >/dev/null 2>&1; then
        record_test "Short -v flag works" "pass"
    else
        record_test "Short -v flag failed" "fail"
    fi

    # Test skip-build flag
    if "$TEST_RUNNER" --skip-build --help >/dev/null 2>&1; then
        record_test "Skip-build flag works" "pass"
    else
        record_test "Skip-build flag failed" "fail"
    fi

    # Test clean flag
    if "$TEST_RUNNER" --clean --help >/dev/null 2>&1; then
        record_test "Clean flag works" "pass"
    else
        record_test "Clean flag failed" "fail"
    fi

    # Test fail-fast flag
    if "$TEST_RUNNER" --fail-fast --help >/dev/null 2>&1; then
        record_test "Fail-fast flag works" "pass"
    else
        record_test "Fail-fast flag failed" "fail"
    fi

    # Test coverage flag
    if "$TEST_RUNNER" --coverage --help >/dev/null 2>&1; then
        record_test "Coverage flag works" "pass"
    else
        record_test "Coverage flag failed" "fail"
    fi
}

# Test 5: String arguments
test_string_arguments() {
    log_section "Test 5: String Arguments"

    cd "$PROJECT_ROOT"

    # Test test pattern
    if "$TEST_RUNNER" --test="**/*Test" --help >/dev/null 2>&1; then
        record_test "Test pattern argument works" "pass"
    else
        record_test "Test pattern argument failed" "fail"
    fi

    # Test report directory
    if "$TEST_RUNNER" --report-dir=/tmp/test-reports --help >/dev/null 2>&1; then
        record_test "Report directory argument works" "pass"
    else
        record_test "Report directory argument failed" "fail"
    fi

    # Test profile
    if "$TEST_RUNNER" --profile=agent-dx --help >/dev/null 2>&1; then
        record_test "Profile argument works" "pass"
    else
        record_test "Profile argument failed" "fail"
    fi

    # Test tags
    if "$TEST_RUNNER" --tags=integration,unit --help >/dev/null 2>&1; then
        record_test "Tags argument works" "pass"
    else
        record_test "Tags argument failed" "fail"
    fi
}

# Test 6: Environment validation
test_environment_validation() {
    log_section "Test 6: Environment Validation"

    cd "$PROJECT_ROOT"

    # Test from correct directory
    if "$TEST_RUNNER" --skip-build --help >/dev/null 2>&1; then
        record_test "Environment validation from project root" "pass"
    else
        record_test "Environment validation from project root failed" "fail"
    fi

    # Test Java availability
    if command -v java >/dev/null 2>&1; then
        record_test "Java is available" "pass"
    else
        record_test "Java not available" "fail"
    fi

    # Test Maven availability
    if command -v mvn >/dev/null 2>&1; then
        record_test "Maven is available" "pass"
    else
        record_test "Maven not available" "fail"
    fi
}

# Test 7: Combined options
test_combined_options() {
    log_section "Test 7: Combined Options"

    cd "$PROJECT_ROOT"

    # Test multiple options together
    if "$TEST_RUNNER" \
        --module=yawl-engine \
        --test="**/*Test" \
        --parallel=4 \
        --timeout=600 \
        --format=json \
        --fail-fast \
        --verbose \
        --help >/dev/null 2>&1; then
        record_test "Combined options work" "pass"
    else
        record_test "Combined options failed" "fail"
    fi

    # Test all integration flags
    if "$TEST_RUNNER" \
        --skip-build \
        --clean \
        --coverage \
        --retry=2 \
        --tags=integration \
        --help >/dev/null 2>&1; then
        record_test "All integration flags work" "pass"
    else
        record_test "All integration flags failed" "fail"
    fi
}

# Test 8: Exit codes
test_exit_codes() {
    log_section "Test 8: Exit Codes"

    cd "$PROJECT_ROOT"

    # Help should exit with 0
    "$TEST_RUNNER" --help >/dev/null 2>&1
    local help_exit=$?
    if [[ $help_exit -eq 0 ]]; then
        record_test "Help exits with code 0" "pass"
    else
        record_test "Help should exit with code 0" "fail"
    fi

    # Invalid module should exit with 2 (configuration error)
    "$TEST_RUNNER" --module=invalid-module >/dev/null 2>&1 || true
    local invalid_exit=$?
    if [[ $invalid_exit -eq 2 ]]; then
        record_test "Invalid module exits with code 2" "pass"
    else
        record_test "Invalid module should exit with code 2 (got $invalid_exit)" "fail"
    fi

    # Invalid argument should exit with 2
    "$TEST_RUNNER" --invalid-arg >/dev/null 2>&1 || true
    local arg_exit=$?
    if [[ $arg_exit -eq 2 ]]; then
        record_test "Invalid argument exits with code 2" "pass"
    else
        record_test "Invalid argument should exit with code 2 (got $arg_exit)" "fail"
    fi
}

# Test 9: Version display
test_version_display() {
    log_section "Test 9: Version Display"

    cd "$PROJECT_ROOT"

    # Check version in header
    if "$TEST_RUNNER" --help 2>&1 | grep -q "v2.0.0"; then
        record_test "Version displayed in help" "pass"
    else
        record_test "Version should be displayed in help" "fail"
    fi
}

# Test 10: Report directory creation
test_report_directory_creation() {
    log_section "Test 10: Report Directory Creation"

    cd "$PROJECT_ROOT"

    local temp_dir
    temp_dir=$(mktemp -d)

    # Run with custom report directory (will fail due to test execution but should create dir)
    "$TEST_RUNNER" --report-dir="$temp_dir/custom-reports" --skip-build --timeout=5 2>/dev/null || true

    if [[ -d "$temp_dir/custom-reports" ]]; then
        record_test "Custom report directory created" "pass"

        # Check for raw subdirectory
        if [[ -d "$temp_dir/custom-reports/raw" ]]; then
            record_test "Raw subdirectory created" "pass"
        else
            record_test "Raw subdirectory should be created" "fail"
        fi
    else
        record_test "Custom report directory should be created" "fail"
    fi

    # Cleanup
    rm -rf "$temp_dir"
}

# ── Main Test Execution ────────────────────────────────────────────────────

main() {
    echo ""
    echo "========================================"
    echo " Integration Test Runner Test Suite"
    echo " Version 2.0.0"
    echo "========================================"
    echo ""

    # Ensure we're in project root
    cd "$PROJECT_ROOT"

    # Run all test sections
    test_help_message
    test_module_validation
    test_argument_parsing
    test_boolean_flags
    test_string_arguments
    test_environment_validation
    test_combined_options
    test_exit_codes
    test_version_display
    test_report_directory_creation

    # Print summary
    echo ""
    echo "========================================"
    echo "          TEST RESULTS SUMMARY"
    echo "========================================"
    echo ""
    printf "  Total Tests:  %d\n" "$TESTS_TOTAL"
    printf "  Passed:       %s%d%s\n" "$GREEN" "$TESTS_PASSED" "$NC"
    printf "  Failed:       %s%d%s\n" "$RED" "$TESTS_FAILED" "$NC"
    echo ""

    local pass_rate=0
    if [[ $TESTS_TOTAL -gt 0 ]]; then
        pass_rate=$((TESTS_PASSED * 100 / TESTS_TOTAL))
    fi

    printf "  Pass Rate:    %d%%\n" "$pass_rate"
    echo ""
    echo "========================================"

    if [[ $TESTS_FAILED -eq 0 ]]; then
        log_success "All tests passed!"
        exit 0
    else
        log_error "$TESTS_FAILED test(s) failed"
        exit 1
    fi
}

# Run main function
main "$@"
