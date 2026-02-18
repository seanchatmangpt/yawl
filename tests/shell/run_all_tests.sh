#!/usr/bin/env bash
# ==========================================================================
# run_all_tests.sh - Master test runner for YAWL observatory shell tests
#
# Runs all shell script unit tests using shunit2.
#
# Usage:
#   ./run_all_tests.sh              # Run all tests
#   ./run_all_tests.sh -v           # Run with verbose output
#   ./run_all_tests.sh <test_file>  # Run specific test file
#
# Requirements:
#   shunit2 must be installed. Install with:
#     - Ubuntu/Debian: apt-get install shunit2
#     - macOS: brew install shunit2
#     - Or download from: https://github.com/kward/shunit2
#
# Exit codes:
#   0  All tests passed
#   1  One or more tests failed
#   2  Setup error (shunit2 not found, etc.)
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TESTS_DIR="${SCRIPT_DIR}"

# Colors for output
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}  $*${NC}"
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo ""
}

# ---------------------------------------------------------------------------
# Find shunit2
# ---------------------------------------------------------------------------

find_shunit2() {
    if command -v shunit2 >/dev/null 2>&1; then
        SHUNIT2="$(command -v shunit2)"
        return 0
    elif [[ -f "/usr/share/shunit2/shunit2" ]]; then
        SHUNIT2="/usr/share/shunit2/shunit2"
        return 0
    elif [[ -f "/usr/local/share/shunit2/shunit2" ]]; then
        SHUNIT2="/usr/local/share/shunit2/shunit2"
        return 0
    elif [[ -f "${HOME}/.local/share/shunit2/shunit2" ]]; then
        SHUNIT2="${HOME}/.local/share/shunit2/shunit2"
        return 0
    elif [[ -f "${SCRIPT_DIR}/shunit2" ]]; then
        SHUNIT2="${SCRIPT_DIR}/shunit2"
        return 0
    else
        return 1
    fi
}

# ---------------------------------------------------------------------------
# Run a single test file
# ---------------------------------------------------------------------------

run_test_file() {
    local test_file="$1"
    local test_name
    test_name=$(basename "${test_file}")

    info "Running: ${test_name}"
    echo ""

    # Run the test and capture result
    set +e
    bash "${test_file}"
    local rc=$?
    set -e

    if [[ ${rc} -eq 0 ]]; then
        success "PASSED: ${test_name}"
        PASSED=$((PASSED + 1))
    else
        error "FAILED: ${test_name} (exit code: ${rc})"
        FAILED=$((FAILED + 1))
        FAILED_TESTS+=("${test_name}")
    fi

    echo ""
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
    header "YAWL Observatory Shell Tests"

    # Check for shunit2
    if ! find_shunit2; then
        error "shunit2 not found!"
        echo ""
        echo "Install shunit2 with one of:"
        echo "  - Ubuntu/Debian: apt-get install shunit2"
        echo "  - macOS: brew install shunit2"
        echo "  - Or download from: https://github.com/kward/shunit2"
        exit 2
    fi

    info "Found shunit2 at: ${SHUNIT2}"
    echo ""

    # Initialize counters
    PASSED=0
    FAILED=0
    declare -a FAILED_TESTS=()

    # Find test files
    declare -a TEST_FILES=()

    if [[ $# -gt 0 && "$1" != "-v" ]]; then
        # Run specific test file
        if [[ -f "$1" ]]; then
            TEST_FILES+=("$1")
        else
            error "Test file not found: $1"
            exit 2
        fi
    else
        # Find all test files
        while IFS= read -r -d '' file; do
            TEST_FILES+=("${file}")
        done < <(find "${TESTS_DIR}" -name "test_*.sh" -type f -print0 | sort -z)
    fi

    if [[ ${#TEST_FILES[@]} -eq 0 ]]; then
        warn "No test files found in ${TESTS_DIR}"
        exit 0
    fi

    info "Found ${#TEST_FILES[@]} test file(s)"
    echo ""

    # Run each test file
    for test_file in "${TEST_FILES[@]}"; do
        run_test_file "${test_file}"
    done

    # Print summary
    header "Test Summary"

    echo -e "  ${GREEN}Passed:${NC} ${PASSED}"
    echo -e "  ${RED}Failed:${NC} ${FAILED}"
    echo ""

    if [[ ${FAILED} -gt 0 ]]; then
        error "Failed tests:"
        for test_name in "${FAILED_TESTS[@]}"; do
            echo "  - ${test_name}"
        done
        echo ""
        exit 1
    else
        success "All tests passed!"
        exit 0
    fi
}

# Run main
main "$@"
