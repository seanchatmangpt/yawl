#!/bin/bash
#
# Shell Assertion Library - Zero Dependencies
#
# Minimal assertion library for black-box testing.
# No external dependencies beyond standard POSIX utilities.
#
# Usage:
#   source scripts/shell-test/assert.sh
#   assert_equals "expected" "actual" "Values should match"
#
# Exit codes:
#   0 - Success
#   1 - Assertion failure

set -euo pipefail

# Colors (disable with NO_COLOR=1)
if [ "${NO_COLOR:-}" = "1" ] || [ ! -t 1 ]; then
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    NC=""
else
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
fi

# Test counters
ASSERT_TESTS_RUN=0
ASSERT_TESTS_PASSED=0
ASSERT_TESTS_FAILED=0

# Assertion: Exact equality
assert_equals() {
    local expected="$1"
    local actual="$2"
    local message="${3:-Assertion failed}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ "$expected" = "$actual" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Expected: '$expected'" >&2
        echo "  Actual:   '$actual'" >&2
        return 1
    fi
}

# Assertion: Not empty
assert_not_empty() {
    local value="$1"
    local message="${2:-Value should not be empty}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ -n "$value" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Value is empty" >&2
        return 1
    fi
}

# Assertion: Value is true (0 exit code)
assert_true() {
    local message="${1:-Condition should be true}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ $? -eq 0 ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        return 1
    fi
}

# Assertion: Value is false (non-zero exit code)
assert_false() {
    local message="${1:-Condition should be false}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ $? -ne 0 ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        return 1
    fi
}

# Assertion: HTTP status code is 200
assert_http_ok() {
    local url="$1"
    local message="${2:-HTTP request should return 200}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null) || code="000"

    if [ "$code" = "200" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (HTTP $code)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  URL: $url" >&2
        echo "  Expected: 200" >&2
        echo "  Actual:   $code" >&2
        return 1
    fi
}

# Assertion: HTTP status code matches expected
assert_http_code() {
    local expected="$1"
    local url="$2"
    local message="${3:-HTTP status code mismatch}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null) || code="000"

    if [ "$code" = "$expected" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (HTTP $code)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  URL: $url" >&2
        echo "  Expected: $expected" >&2
        echo "  Actual:   $code" >&2
        return 1
    fi
}

# Assertion: Response contains string
assert_response_contains() {
    local url="$1"
    local pattern="$2"
    local message="${3:-Response should contain pattern}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local response
    response=$(curl -s "$url" 2>/dev/null) || response=""

    if echo "$response" | grep -q "$pattern"; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  URL: $url" >&2
        echo "  Pattern not found: '$pattern'" >&2
        echo "  Response (first 200 chars): ${response:0:200}..." >&2
        return 1
    fi
}

# Assertion: JSON field equals expected value
assert_json_field() {
    local json="$1"
    local field="$2"
    local expected="$3"
    local message="${4:-JSON field mismatch}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local actual
    actual=$(echo "$json" | jq -r ".$field" 2>/dev/null) || actual=""

    if [ "$actual" = "$expected" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (.$field = '$actual')"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Field: .$field" >&2
        echo "  Expected: '$expected'" >&2
        echo "  Actual:   '$actual'" >&2
        return 1
    fi
}

# Assertion: JSON field exists
assert_json_has_field() {
    local json="$1"
    local field="$2"
    local message="${3:-JSON should have field}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if echo "$json" | jq -e ".$field" > /dev/null 2>&1; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (.$field exists)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Field: .$field" >&2
        echo "  Field not found in JSON" >&2
        return 1
    fi
}

# Assertion: JSON array length
assert_json_array_length() {
    local json="$1"
    local field="$2"
    local operator="$3"
    local expected="$4"
    local message="${5:-JSON array length mismatch}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local actual
    actual=$(echo "$json" | jq -r ".$field | length" 2>/dev/null) || actual="0"

    local pass=false
    case "$operator" in
        "="| "==")  [ "$actual" -eq "$expected" ] && pass=true ;;
        "!=")       [ "$actual" -ne "$expected" ] && pass=true ;;
        ">")        [ "$actual" -gt "$expected" ] && pass=true ;;
        ">=")       [ "$actual" -ge "$expected" ] && pass=true ;;
        "<")        [ "$actual" -lt "$expected" ] && pass=true ;;
        "<=")       [ "$actual" -le "$expected" ] && pass=true ;;
        *)          echo "Unknown operator: $operator" >&2; return 1 ;;
    esac

    if [ "$pass" = "true" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (.$field length = $actual)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Field: .$field" >&2
        echo "  Condition: length $operator $expected" >&2
        echo "  Actual length: $actual" >&2
        return 1
    fi
}

# Assertion: JSON array not empty
assert_json_array_not_empty() {
    local json="$1"
    local field="$2"
    local message="${3:-JSON array should not be empty}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    local length
    length=$(echo "$json" | jq -r ".$field | length" 2>/dev/null) || length="0"

    if [ "$length" -gt 0 ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (.$field length = $length)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Field: .$field" >&2
        echo "  Array is empty" >&2
        return 1
    fi
}

# Assertion: File exists
assert_file_exists() {
    local filepath="$1"
    local message="${2:-File should exist}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ -f "$filepath" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  File: $filepath" >&2
        echo "  File not found" >&2
        return 1
    fi
}

# Assertion: Directory exists
assert_dir_exists() {
    local dirpath="$1"
    local message="${2:-Directory should exist}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if [ -d "$dirpath" ]; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Directory: $dirpath" >&2
        echo "  Directory not found" >&2
        return 1
    fi
}

# Assertion: Process is running
assert_process_running() {
    local pid="$1"
    local message="${2:-Process should be running}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if kill -0 "$pid" 2>/dev/null; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (PID $pid)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  PID: $pid" >&2
        echo "  Process not running" >&2
        return 1
    fi
}

# Assertion: Process has stopped
assert_process_stopped() {
    local pid="$1"
    local message="${2:-Process should be stopped}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if ! kill -0 "$pid" 2>/dev/null; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message (PID $pid)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  PID: $pid" >&2
        echo "  Process still running" >&2
        return 1
    fi
}

# Assertion: Port is open
assert_port_open() {
    local host="$1"
    local port="$2"
    local message="${3:-Port should be open}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if nc -z "$host" "$port" 2>/dev/null; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message ($host:$port)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Address: $host:$port" >&2
        echo "  Port not open" >&2
        return 1
    fi
}

# Assertion: Port is closed
assert_port_closed() {
    local host="$1"
    local port="$2"
    local message="${3:-Port should be closed}"
    ASSERT_TESTS_RUN=$((ASSERT_TESTS_RUN + 1))

    if ! nc -z "$host" "$port" 2>/dev/null; then
        ASSERT_TESTS_PASSED=$((ASSERT_TESTS_PASSED + 1))
        echo -e "${GREEN}✓${NC} $message ($host:$port)"
        return 0
    else
        ASSERT_TESTS_FAILED=$((ASSERT_TESTS_FAILED + 1))
        echo -e "${RED}✗ FAIL: $message${NC}" >&2
        echo "  Address: $host:$port" >&2
        echo "  Port is open" >&2
        return 1
    fi
}

# Wait for port to become available
wait_for_port() {
    local host="${1:-localhost}"
    local port="$2"
    local timeout="${3:-30}"
    local message="${4:-Waiting for port $port}"

    echo -n "$message"
    local start
    start=$(date +%s)

    while ! nc -z "$host" "$port" 2>/dev/null; do
        if [ $(($(date +%s) - start)) -ge "$timeout" ]; then
            echo -e " ${RED}TIMEOUT${NC}"
            echo "FAIL: Timeout waiting for $host:$port after ${timeout}s" >&2
            return 1
        fi
        echo -n "."
        sleep 1
    done

    echo -e " ${GREEN}OK${NC}"
    return 0
}

# Wait for HTTP endpoint to return 200
wait_for_http() {
    local url="$1"
    local timeout="${2:-30}"
    local message="${3:-Waiting for HTTP endpoint}"

    echo -n "$message"
    local start
    start=$(date +%s)

    while true; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null) || code="000"

        if [ "$code" = "200" ]; then
            echo -e " ${GREEN}OK${NC}"
            return 0
        fi

        if [ $(($(date +%s) - start)) -ge "$timeout" ]; then
            echo -e " ${RED}TIMEOUT${NC}"
            echo "FAIL: Timeout waiting for $url after ${timeout}s (last code: $code)" >&2
            return 1
        fi

        echo -n "."
        sleep 1
    done
}

# Print test summary
print_test_summary() {
    echo ""
    echo "=========================================="
    echo "Test Summary"
    echo "=========================================="
    echo "Tests run:    $ASSERT_TESTS_RUN"
    echo -e "Tests passed: ${GREEN}$ASSERT_TESTS_PASSED${NC}"
    echo -e "Tests failed: ${RED}$ASSERT_TESTS_FAILED${NC}"
    echo "=========================================="

    if [ "$ASSERT_TESTS_FAILED" -gt 0 ]; then
        return 1
    fi
    return 0
}

# Reset counters
reset_test_counters() {
    ASSERT_TESTS_RUN=0
    ASSERT_TESTS_PASSED=0
    ASSERT_TESTS_FAILED=0
}
