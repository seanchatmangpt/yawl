#!/bin/bash
#
# Common utilities for validation scripts
# Provides consistent logging, output formatting, and platform support
#

# Colors and formatting constants
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly MAGENTA='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly WHITE='\033[1;37m'
readonly RESET='\033[0m'
readonly BOLD='\033[1m'
readonly DIM='\033[2m'

# Cross-platform support
if [[ "$OSTYPE" == "darwin"* ]]; then
    readonly SED="sed -E"
else
    readonly SED="sed -r"
fi

# Global state
declare -a PASSED_TESTS=()
declare -a FAILED_TESTS=()
declare -a WARNINGS=()
declare -i TOTAL_TESTS=0
declare -i PASS_COUNT=0
declare -i FAIL_COUNT=0

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${RESET} $*" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${RESET} $*" >&2
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${RESET} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${RESET} $*" >&2
}

log_header() {
    echo -e "${MAGENTA}${BOLD}$*${RESET}" >&2
}

log_section() {
    echo -e "${CYAN}--- $* ---${RESET}" >&2
}

log_test() {
    local status=$1
    local message=$2
    local test_name=$3

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    case $status in
        "PASS")
            PASSED_TESTS+=("$test_name")
            PASS_COUNT=$((PASS_COUNT + 1))
            echo -e "  ${GREEN}✓${RESET} $message"
            ;;
        "FAIL")
            FAILED_TESTS+=("$test_name")
            FAIL_COUNT=$((FAIL_COUNT + 1))
            echo -e "  ${RED}✗${RESET} $message"
            ;;
        "WARN")
            WARNINGS+=("$test_name: $message")
            echo -e "  ${YELLOW}!${RESET} $message"
            ;;
        "SKIP")
            echo -e "  ${DIM}-${RESET} $message"
            ;;
    esac
}

# File existence check with optional error handling
check_file_exists() {
    local file=$1
    local required=${2:-true}

    if [[ -f "$file" ]]; then
        return 0
    else
        if [[ "$required" == "true" ]]; then
            log_error "Required file not found: $file"
            return 1
        else
            log_warning "Optional file not found: $file"
            return 1
        fi
    fi
}

# Get changed files from git (for incremental validation)
get_changed_files() {
    local pattern=${1:-"."}
    git diff --name-only HEAD~1 | grep "$pattern" || true
}

# JSON output for CI integration
output_json() {
    local output_file=${1:-"validation-results.json"}

    cat > "$output_file" << EOF
{
    "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%SZ')",
    "total_tests": $TOTAL_TESTS,
    "passed": $PASS_COUNT,
    "failed": $FAIL_COUNT,
    "warnings": ${#WARNINGS[@]},
    "passed_tests": [
        $(printf '%s\n' "${PASSED_TESTS[@]}" | sed 's/^/        "/' | sed 's/$/",/' | sed '$s/,$//')
    ],
    "failed_tests": [
        $(printf '%s\n' "${FAILED_TESTS[@]}" | sed 's/^/        "/' | sed 's/$/",/' | sed '$s/,$//')
    ],
    "warnings": [
        $(printf '%s\n' "${WARNINGS[@]}" | sed 's/^/        "/' | sed 's/$"/",/' | sed '$s/$//' | sed '$s/,$//')
    ]
}
EOF
}

# JUnit XML output for GitHub Actions
output_junit() {
    local output_file=${1:-"validation-results.xml"}

    cat > "$output_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
EOF

    # Add passed tests
    for test in "${PASSED_TESTS[@]}"; do
        cat >> "$output_file" << EOF
    <testsuite name="$test" tests="1" failures="0" time="0">
        <testcase name="$test" classname="validation.$test"/>
    </testsuite>
EOF
    done

    # Add failed tests
    for test in "${FAILED_TESTS[@]}"; do
        cat >> "$output_file" << EOF
    <testsuite name="$test" tests="1" failures="1" time="0">
        <testcase name="$test" classname="validation.$test">
            <failure message="$test failed"/>
        </testcase>
    </testsuite>
EOF
    done

    cat >> "$output_file" << EOF
</testsuites>
EOF
}

# Summary output
output_summary() {
    log_header "Validation Summary"
    echo -e "${MAGENTA}Total Tests:${RESET} $TOTAL_TESTS"
    echo -e "${GREEN}Passed:${RESET} $PASS_COUNT"
    echo -e "${RED}Failed:${RESET} $FAIL_COUNT"
    echo -e "${YELLOW}Warnings:${RESET} ${#WARNINGS[@]}"

    if [[ ${#WARNINGS[@]} -gt 0 ]]; then
        echo
        log_section "Warnings"
        for warning in "${WARNINGS[@]}"; do
            echo -e "  ${YELLOW}!${RESET} $warning"
        done
    fi

    if [[ $FAIL_COUNT -gt 0 ]]; then
        echo
        log_section "Failed Tests"
        for fail in "${FAILED_TESTS[@]}"; do
            echo -e "  ${RED}✗${RESET} $fail"
        done
        return 1
    else
        return 0
    fi
}

# Cleanup function
cleanup() {
    # Add any cleanup tasks here
    true
}

# Error handling
trap cleanup EXIT

# Platform detection
get_platform() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "macOS"
    elif [[ "$OSTYPE" == "linux"* ]]; then
        echo "Linux"
    else
        echo "Unknown"
    fi
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}