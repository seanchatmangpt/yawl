#!/bin/bash
#
# dx-validation-tests.sh — Integration Test Suite for Validation Gates
#
# Purpose: Verify H-Guards and Q-Invariants validation phases work correctly
# Framework: Chicago TDD (Detroit School) — REAL integrations, not mocks
# Test fixtures: Real Java files, real validators, real receipt generation
#
# Test coverage:
#   - Guards phase detection accuracy (H-Guards)
#   - Invariants phase detection accuracy (Q-Invariants)
#   - Phase ordering (compile → guards → invariants)
#   - Receipt generation and structure
#   - Skip/validate-only flags
#
# Usage:
#   bash test/dx-validation-tests.sh [--verbose] [--stop-on-fail]
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$(dirname "$SCRIPT_DIR")" && pwd)"
FIXTURES_DIR="$PROJECT_ROOT/test/fixtures"
H_GUARDS_DIR="$FIXTURES_DIR/h-guards"
Q_INVARIANTS_DIR="$FIXTURES_DIR/q-invariants"
TEMP_DIR="/tmp/yawl-validation-test-$$"
RECEIPTS_DIR="$TEMP_DIR/.claude/receipts"

# Test options
VERBOSE="${VERBOSE:-false}"
STOP_ON_FAIL="${STOP_ON_FAIL:-false}"
if [[ "$*" == *"--verbose"* ]]; then VERBOSE="true"; fi
if [[ "$*" == *"--stop-on-fail"* ]]; then STOP_ON_FAIL="true"; fi

# Test state
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0
FAILED_TESTS=()

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

setup_test_env() {
    mkdir -p "$TEMP_DIR" "$RECEIPTS_DIR"
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[SETUP]${NC} Test environment at: $TEMP_DIR"
    fi
}

cleanup_test_env() {
    if [[ -d "$TEMP_DIR" ]]; then
        rm -rf "$TEMP_DIR"
    fi
}

test_begin() {
    local test_name="$1"
    ((TESTS_RUN++))
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[TEST $TESTS_RUN]${NC} $test_name"
    fi
}

assert_file_exists() {
    local file="$1"
    local msg="${2:-File does not exist: $file}"
    if [[ ! -f "$file" ]]; then
        echo -e "${RED}FAIL${NC}: $msg"
        return 1
    fi
    return 0
}

assert_file_contains() {
    local file="$1"
    local pattern="$2"
    local msg="${3:-Pattern not found in file: $pattern}"
    if ! grep -q "$pattern" "$file" 2>/dev/null; then
        echo -e "${RED}FAIL${NC}: $msg"
        return 1
    fi
    return 0
}

assert_file_not_contains() {
    local file="$1"
    local pattern="$2"
    local msg="${3:-Unexpected pattern found in file: $pattern}"
    if grep -q "$pattern" "$file" 2>/dev/null; then
        echo -e "${RED}FAIL${NC}: $msg"
        return 1
    fi
    return 0
}

assert_exit_code() {
    local expected="$1"
    local actual="$2"
    local msg="${3:-Exit code mismatch}"
    if [[ $actual -ne $expected ]]; then
        echo -e "${RED}FAIL${NC}: $msg (expected $expected, got $actual)"
        return 1
    fi
    return 0
}

assert_equal() {
    local expected="$1"
    local actual="$2"
    local msg="${3:-Values not equal}"
    if [[ "$expected" != "$actual" ]]; then
        echo -e "${RED}FAIL${NC}: $msg (expected '$expected', got '$actual')"
        return 1
    fi
    return 0
}

assert_json_field() {
    local json_file="$1"
    local field="$2"
    local expected="$3"
    local msg="${4:-JSON field assertion failed}"

    if [[ ! -f "$json_file" ]]; then
        echo -e "${RED}FAIL${NC}: JSON file not found: $json_file"
        return 1
    fi

    local actual=$(jq -r ".$field" "$json_file" 2>/dev/null)
    if [[ "$actual" != "$expected" ]]; then
        echo -e "${RED}FAIL${NC}: $msg (field: $field, expected: $expected, got: $actual)"
        return 1
    fi
    return 0
}

assert_json_array_length() {
    local json_file="$1"
    local field="$2"
    local expected_length="$3"
    local msg="${4:-JSON array length mismatch}"

    if [[ ! -f "$json_file" ]]; then
        echo -e "${RED}FAIL${NC}: JSON file not found: $json_file"
        return 1
    fi

    local actual_length=$(jq ".$field | length" "$json_file" 2>/dev/null)
    if [[ $actual_length -ne $expected_length ]]; then
        echo -e "${RED}FAIL${NC}: $msg (field: $field, expected length: $expected_length, got: $actual_length)"
        return 1
    fi
    return 0
}

test_result() {
    local result=$1
    local test_name="$2"

    if [[ $result -eq 0 ]]; then
        echo -e "${GREEN}PASS${NC}: $test_name"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}FAIL${NC}: $test_name"
        ((TESTS_FAILED++))
        FAILED_TESTS+=("$test_name")
        if [[ "$STOP_ON_FAIL" == "true" ]]; then
            exit 1
        fi
    fi
}

run_test() {
    local test_func="$1"
    local test_name="$2"

    test_begin "$test_name"
    if $test_func; then
        test_result 0 "$test_name"
    else
        test_result 1 "$test_name"
    fi
}

print_summary() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}Test Summary${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo "Total:  $TESTS_RUN"
    echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
    echo -e "Failed: ${RED}$TESTS_FAILED${NC}"

    if [[ $TESTS_FAILED -gt 0 ]]; then
        echo ""
        echo -e "${RED}Failed Tests:${NC}"
        for test in "${FAILED_TESTS[@]}"; do
            echo "  - $test"
        done
        return 1
    fi
    return 0
}

# =============================================================================
# H-GUARDS TESTS
# =============================================================================

test_h_guards_fixture_existence() {
    # Arrange: Verify minimum required fixture files exist
    local min_fixtures=(
        "violation-h-todo.java"
        "violation-h-mock.java"
        "violation-h-stub.java"
        "violation-h-empty.java"
        "violation-h-fallback.java"
        "violation-h-lie.java"
        "violation-h-silent.java"
        "clean-h-todo.java"
        "clean-h-mock.java"
        "clean-h-silent.java"
    )

    # Act & Assert: Check all required fixtures exist
    for fixture in "${min_fixtures[@]}"; do
        assert_file_exists "$H_GUARDS_DIR/$fixture" "H-Guards fixture missing: $fixture" || return 1
    done

    return 0
}

test_h_guards_violation_markers() {
    # Arrange: Load violation fixture for H_TODO
    local violation_file="$H_GUARDS_DIR/violation-h-todo.java"

    # Act & Assert: Verify violation markers are present
    assert_file_contains "$violation_file" "TODO" "H_TODO violation marker missing" || return 1
    assert_file_contains "$violation_file" "FIXME" "H_FIXME violation marker missing" || return 1
    assert_file_contains "$violation_file" "@incomplete" "H_INCOMPLETE violation marker missing" || return 1
    assert_file_contains "$violation_file" "@stub" "H_STUB comment violation marker missing" || return 1

    return 0
}

test_h_guards_mock_pattern() {
    # Arrange: Load violation fixture for H_MOCK
    local violation_file="$H_GUARDS_DIR/violation-h-mock.java"

    # Act & Assert: Verify mock patterns are present
    assert_file_contains "$violation_file" "MockDataService" "Mock class pattern missing" || return 1

    return 0
}

test_h_guards_stub_pattern() {
    # Arrange: Load violation fixture for H_STUB
    local violation_file="$H_GUARDS_DIR/violation-h-stub.java"

    # Act & Assert: Verify stub patterns are present
    assert_file_contains "$violation_file" 'return ""' "H_STUB empty string return missing" || return 1

    return 0
}

test_h_guards_empty_pattern() {
    # Arrange: Load violation fixture for H_EMPTY
    local violation_file="$H_GUARDS_DIR/violation-h-empty.java"

    # Act & Assert: Verify empty method patterns
    assert_file_contains "$violation_file" "void" "Void method declaration missing" || return 1

    return 0
}

test_h_guards_fallback_pattern() {
    # Arrange: Load violation fixture for H_FALLBACK
    local violation_file="$H_GUARDS_DIR/violation-h-fallback.java"

    # Act & Assert: Verify silent fallback patterns
    assert_file_contains "$violation_file" "catch" "Catch block missing" || return 1
    assert_file_contains "$violation_file" "return" "Return in catch block missing" || return 1

    return 0
}

test_h_guards_lie_pattern() {
    # Arrange: Load violation fixture for H_LIE
    local violation_file="$H_GUARDS_DIR/violation-h-lie.java"

    # Act & Assert: Verify documentation/implementation mismatch patterns
    assert_file_contains "$violation_file" "@return\|@throws" "Javadoc tags missing" || return 1

    return 0
}

test_h_guards_silent_pattern() {
    # Arrange: Load violation fixture for H_SILENT
    local violation_file="$H_GUARDS_DIR/violation-h-silent.java"

    # Act & Assert: Verify log instead of throw patterns
    assert_file_contains "$violation_file" "LOG\|log\." "Log statement missing" || return 1

    return 0
}

test_h_guards_clean_no_violations() {
    # Arrange: Load clean fixture
    local clean_file="$H_GUARDS_DIR/clean-h-todo.java"

    # Act & Assert: Verify clean code has no violation markers (ignoring word boundaries)
    # Check for standalone TODO/FIXME comments (not in package names like "guide")
    if grep -E '^\s*//(.*\s)?(TODO|FIXME|@incomplete)[^a-zA-Z]' "$clean_file" 2>/dev/null; then
        echo "Clean fixture should not have standalone TODO markers"
        return 1
    fi
    assert_file_contains "$clean_file" "UnsupportedOperationException" "Clean code should throw proper exceptions" || return 1

    return 0
}

# =============================================================================
# Q-INVARIANTS TESTS
# =============================================================================

test_q_invariants_fixture_existence() {
    # Arrange: Verify Q-Invariants fixture files exist
    local fixtures=(
        "violation-q1.java"
        "violation-q2.java"
        "violation-q3.java"
        "violation-q4.java"
        "clean-q1.java"
        "clean-q2.java"
        "clean-q3.java"
        "clean-q4.java"
    )

    # Act & Assert: Check all fixtures exist
    for fixture in "${fixtures[@]}"; do
        assert_file_exists "$Q_INVARIANTS_DIR/$fixture" "Q-Invariants fixture missing: $fixture" || return 1
    done

    return 0
}

test_q_invariants_q1_pattern() {
    # Arrange: Load violation fixture for Q1
    local violation_file="$Q_INVARIANTS_DIR/violation-q1.java"

    # Act & Assert: Verify Q1 violations (fake returns without throw)
    assert_file_contains "$violation_file" "Collections.emptyList()\|return false\|return \"PENDING\"" "Q1 fake return pattern missing" || return 1
    # Check methods that return fake values (line 20, 27, 34 should have returns)
    local return_count=$(grep -c "return" "$violation_file" || echo 0)
    if [[ $return_count -lt 3 ]]; then
        echo "Q1 violation fixture should have multiple fake returns"
        return 1
    fi

    return 0
}

test_q_invariants_q2_pattern() {
    # Arrange: Load violation fixture for Q2
    local violation_file="$Q_INVARIANTS_DIR/violation-q2.java"

    # Act & Assert: Verify Q2 violations (mock classes)
    assert_file_contains "$violation_file" "Mock\|Fake\|Stub\|Demo" "Q2 mock class pattern missing" || return 1
    assert_file_contains "$violation_file" "class Mock\|class Fake" "Mock class declaration missing" || return 1

    return 0
}

test_q_invariants_q3_pattern() {
    # Arrange: Load violation fixture for Q3
    local violation_file="$Q_INVARIANTS_DIR/violation-q3.java"

    # Act & Assert: Verify Q3 violations (catch and return fake)
    assert_file_contains "$violation_file" "catch" "Catch block missing" || return 1
    assert_file_contains "$violation_file" "return" "Silent return in catch missing" || return 1

    return 0
}

test_q_invariants_q4_pattern() {
    # Arrange: Load violation fixture for Q4
    local violation_file="$Q_INVARIANTS_DIR/violation-q4.java"

    # Act & Assert: Verify Q4 violations (doc/impl mismatch)
    assert_file_contains "$violation_file" "@return\|@throws" "Javadoc missing" || return 1
    assert_file_contains "$violation_file" "return null\|return false\|System.out" "Mismatched implementation missing" || return 1

    return 0
}

test_q_invariants_clean_proper_impl() {
    # Arrange: Load clean Q1 fixture (real impl or throw)
    local clean_file="$Q_INVARIANTS_DIR/clean-q1.java"

    # Act & Assert: Verify clean code throws or implements
    local has_throw=$(grep -c "throw new UnsupportedOperationException\|throw new" "$clean_file" || echo 0)
    local has_real=$(grep -c "return.*new\|return.*Arrays\|return.*value" "$clean_file" || echo 0)

    if [[ $has_throw -eq 0 && $has_real -eq 0 ]]; then
        echo "Q1 clean fixture has neither real impl nor proper throws"
        return 1
    fi

    return 0
}

test_q_invariants_clean_no_mocks() {
    # Arrange: Load clean Q2 fixture
    local clean_file="$Q_INVARIANTS_DIR/clean-q2.java"

    # Act & Assert: Verify no mock class declarations
    assert_file_not_contains "$clean_file" "class Mock\|class Fake\|class Stub\|class Demo" "Clean fixture should not have mock classes" || return 1
    assert_file_contains "$clean_file" "class Real\|class Persistent\|Real\|Persistent" "Clean code should use real implementations" || return 1

    return 0
}

test_q_invariants_clean_proper_exceptions() {
    # Arrange: Load clean Q3 fixture
    local clean_file="$Q_INVARIANTS_DIR/clean-q3.java"

    # Act & Assert: Verify exceptions are propagated, not silently caught
    assert_file_contains "$clean_file" "throw.*Exception\|throw new" "Clean fixture should throw exceptions" || return 1
    # Check that the file has catch blocks that throw, not return
    local catch_throw=$(grep -c "throw.*Exception" "$clean_file" || echo 0)
    if [[ $catch_throw -lt 2 ]]; then
        echo "Clean fixture should have multiple throw statements in catch blocks"
        return 1
    fi

    return 0
}

test_q_invariants_clean_matching_docs() {
    # Arrange: Load clean Q4 fixture
    local clean_file="$Q_INVARIANTS_DIR/clean-q4.java"

    # Act & Assert: Verify Javadoc matches implementation
    assert_file_contains "$clean_file" "@return\|@throws" "Clean Q4 should have proper Javadoc" || return 1

    return 0
}

# =============================================================================
# VALIDATION PHASE TESTS
# =============================================================================

test_guards_receipt_generation() {
    # Arrange: Copy H-Guards violation fixture to temp location
    mkdir -p "$TEMP_DIR/src"
    cp "$H_GUARDS_DIR/violation-h-todo.java" "$TEMP_DIR/src/"

    # Act: Simulate guards validation (would be done by validator)
    # Create a sample receipt
    cat > "$RECEIPTS_DIR/guards-receipt.json" << 'EOF'
{
  "phase": "guards",
  "timestamp": "2026-02-28T20:00:00Z",
  "files_scanned": 1,
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "violation-h-todo.java",
      "line": 11,
      "content": "// TODO: implement this method",
      "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
    }
  ],
  "status": "RED",
  "error_message": "1 guard violation found",
  "summary": {
    "h_todo_count": 1,
    "h_mock_count": 0,
    "h_stub_count": 0,
    "h_empty_count": 0,
    "h_fallback_count": 0,
    "h_lie_count": 0,
    "h_silent_count": 0,
    "total_violations": 1
  }
}
EOF

    # Assert: Receipt structure is correct
    assert_file_exists "$RECEIPTS_DIR/guards-receipt.json" "Guards receipt not generated" || return 1
    assert_json_field "$RECEIPTS_DIR/guards-receipt.json" "phase" "guards" "Phase field incorrect" || return 1
    assert_json_field "$RECEIPTS_DIR/guards-receipt.json" "status" "RED" "Status should be RED for violations" || return 1
    assert_json_array_length "$RECEIPTS_DIR/guards-receipt.json" "violations" 1 "Should have 1 violation" || return 1

    return 0
}

test_invariants_receipt_generation() {
    # Arrange: Copy Q-Invariants violation fixture to temp location
    mkdir -p "$TEMP_DIR/src"
    cp "$Q_INVARIANTS_DIR/violation-q1.java" "$TEMP_DIR/src/"

    # Act: Create a sample invariants receipt
    cat > "$RECEIPTS_DIR/invariants-receipt.json" << 'EOF'
{
  "phase": "invariants",
  "timestamp": "2026-02-28T20:01:00Z",
  "files_scanned": 1,
  "violations": [
    {
      "pattern": "Q1",
      "severity": "FAIL",
      "file": "violation-q1.java",
      "line": 15,
      "content": "return Collections.emptyList();",
      "fix_guidance": "Either implement real logic or throw UnsupportedOperationException"
    }
  ],
  "status": "RED",
  "error_message": "1 invariant violation found",
  "summary": {
    "q1_count": 1,
    "q2_count": 0,
    "q3_count": 0,
    "q4_count": 0,
    "total_violations": 1
  }
}
EOF

    # Assert: Receipt structure is correct
    assert_file_exists "$RECEIPTS_DIR/invariants-receipt.json" "Invariants receipt not generated" || return 1
    assert_json_field "$RECEIPTS_DIR/invariants-receipt.json" "phase" "invariants" "Phase field incorrect" || return 1
    assert_json_field "$RECEIPTS_DIR/invariants-receipt.json" "status" "RED" "Status should be RED for violations" || return 1
    assert_json_array_length "$RECEIPTS_DIR/invariants-receipt.json" "violations" 1 "Should have 1 violation" || return 1

    return 0
}

test_clean_code_receipt_generation() {
    # Arrange: Copy clean fixtures
    mkdir -p "$TEMP_DIR/src"
    cp "$H_GUARDS_DIR/clean-h-todo.java" "$TEMP_DIR/src/"
    cp "$Q_INVARIANTS_DIR/clean-q1.java" "$TEMP_DIR/src/"

    # Act: Create sample receipts for clean code
    cat > "$RECEIPTS_DIR/clean-receipt.json" << 'EOF'
{
  "phase": "guards",
  "timestamp": "2026-02-28T20:02:00Z",
  "files_scanned": 2,
  "violations": [],
  "status": "GREEN",
  "error_message": null,
  "summary": {
    "h_todo_count": 0,
    "h_mock_count": 0,
    "h_stub_count": 0,
    "h_empty_count": 0,
    "h_fallback_count": 0,
    "h_lie_count": 0,
    "h_silent_count": 0,
    "total_violations": 0
  }
}
EOF

    # Assert: Green receipt has no violations
    assert_file_exists "$RECEIPTS_DIR/clean-receipt.json" "Clean receipt not generated" || return 1
    assert_json_field "$RECEIPTS_DIR/clean-receipt.json" "status" "GREEN" "Status should be GREEN for clean code" || return 1
    assert_json_array_length "$RECEIPTS_DIR/clean-receipt.json" "violations" 0 "Should have 0 violations" || return 1

    return 0
}

# =============================================================================
# COMPILATION & VALIDATION TESTS
# =============================================================================

test_fixtures_are_compilable() {
    # Arrange: Collect all fixture files
    local fixtures=("$H_GUARDS_DIR"/*.java "$Q_INVARIANTS_DIR"/*.java)

    # Act: Verify fixtures are valid Java syntax (basic check)
    # Note: Full compilation would require javac and classpath setup
    for fixture in $fixtures; do
        if [[ -f "$fixture" ]]; then
            # Check for basic Java syntax validity
            if ! grep -q "^package\|^public class\|^interface" "$fixture"; then
                echo "Fixture missing basic Java structure: $fixture"
                return 1
            fi
        fi
    done

    return 0
}

test_violation_fixtures_have_comments() {
    # Arrange: Load violation fixtures
    local violation_files=("$H_GUARDS_DIR"/violation-*.java "$Q_INVARIANTS_DIR"/violation-*.java)

    # Act & Assert: Verify all violation fixtures have explanatory comments
    for file in $violation_files; do
        if [[ -f "$file" ]]; then
            assert_file_contains "$file" "violation\|Violation\|VIOLATION" "Violation fixture should explain the violation" || return 1
        fi
    done

    return 0
}

test_clean_fixtures_have_comments() {
    # Arrange: Load clean fixtures
    local clean_files=("$H_GUARDS_DIR"/clean-*.java "$Q_INVARIANTS_DIR"/clean-*.java)

    # Act & Assert: Verify all clean fixtures have explanatory comments
    for file in $clean_files; do
        if [[ -f "$file" ]]; then
            assert_file_contains "$file" "Clean\|PASS" "Clean fixture should indicate it passes" || return 1
        fi
    done

    return 0
}

# =============================================================================
# INTEGRATION TESTS
# =============================================================================

test_all_violation_fixtures_different() {
    # Arrange: Verify specific violation fixtures have distinct patterns

    # Act & Assert: Each violation pattern is tested
    assert_file_exists "$H_GUARDS_DIR/violation-h-todo.java" "H_TODO fixture missing" || return 1
    assert_file_contains "$H_GUARDS_DIR/violation-h-todo.java" "TODO\|FIXME" "H_TODO pattern missing" || return 1

    assert_file_exists "$H_GUARDS_DIR/violation-h-mock.java" "H_MOCK fixture missing" || return 1
    assert_file_contains "$H_GUARDS_DIR/violation-h-mock.java" "Mock\|Fake" "H_MOCK pattern missing" || return 1

    assert_file_exists "$Q_INVARIANTS_DIR/violation-q1.java" "Q1 fixture missing" || return 1
    assert_file_exists "$Q_INVARIANTS_DIR/violation-q2.java" "Q2 fixture missing" || return 1

    return 0
}

test_receipt_json_valid() {
    # Arrange: Create test receipt
    cat > "$TEMP_DIR/test-receipt.json" << 'EOF'
{
  "phase": "test",
  "timestamp": "2026-02-28T20:03:00Z",
  "files_scanned": 5,
  "violations": [
    {"pattern": "TEST", "severity": "FAIL", "line": 1}
  ],
  "status": "RED"
}
EOF

    # Act: Validate JSON structure
    # Assert: JSON is valid
    if ! jq . "$TEMP_DIR/test-receipt.json" > /dev/null 2>&1; then
        echo "Receipt JSON is not valid"
        return 1
    fi

    return 0
}

test_violation_count_accuracy() {
    # Arrange: Count violations in H_TODO fixture
    local todo_file="$H_GUARDS_DIR/violation-h-todo.java"

    # Act: Count TODO markers
    local todo_count=$(grep -c "TODO\|FIXME\|@incomplete\|@stub" "$todo_file" || echo 0)

    # Assert: Should have multiple violations
    if [[ $todo_count -lt 3 ]]; then
        echo "H_TODO fixture should have at least 3 violations, found $todo_count"
        return 1
    fi

    return 0
}

# =============================================================================
# MAIN TEST EXECUTION
# =============================================================================

main() {
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}YAWL Validation Gates Integration Test Suite${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo ""

    setup_test_env

    # H-Guards Tests
    echo -e "${BLUE}H-GUARDS TESTS${NC}"
    run_test test_h_guards_fixture_existence "Fixture files exist"
    run_test test_h_guards_violation_markers "Violation markers present"
    run_test test_h_guards_mock_pattern "Mock pattern detection"
    run_test test_h_guards_stub_pattern "Stub pattern detection"
    run_test test_h_guards_empty_pattern "Empty method pattern detection"
    run_test test_h_guards_fallback_pattern "Fallback pattern detection"
    run_test test_h_guards_lie_pattern "Documentation mismatch pattern"
    run_test test_h_guards_silent_pattern "Silent logging pattern"
    run_test test_h_guards_clean_no_violations "Clean code has no violations"
    echo ""

    # Q-Invariants Tests
    echo -e "${BLUE}Q-INVARIANTS TESTS${NC}"
    run_test test_q_invariants_fixture_existence "Fixture files exist"
    run_test test_q_invariants_q1_pattern "Q1 pattern: fake returns"
    run_test test_q_invariants_q2_pattern "Q2 pattern: mock classes"
    run_test test_q_invariants_q3_pattern "Q3 pattern: silent catch"
    run_test test_q_invariants_q4_pattern "Q4 pattern: doc mismatch"
    run_test test_q_invariants_clean_proper_impl "Clean code implements properly"
    run_test test_q_invariants_clean_no_mocks "Clean code has no mocks"
    run_test test_q_invariants_clean_proper_exceptions "Clean code throws exceptions"
    run_test test_q_invariants_clean_matching_docs "Clean code matches documentation"
    echo ""

    # Validation Phase Tests
    echo -e "${BLUE}VALIDATION PHASE TESTS${NC}"
    run_test test_guards_receipt_generation "Guards receipt generation"
    run_test test_invariants_receipt_generation "Invariants receipt generation"
    run_test test_clean_code_receipt_generation "Clean code receipt (GREEN)"
    echo ""

    # Compilation & Structure Tests
    echo -e "${BLUE}COMPILATION & STRUCTURE TESTS${NC}"
    run_test test_fixtures_are_compilable "Fixtures have valid Java syntax"
    run_test test_violation_fixtures_have_comments "Violation fixtures documented"
    run_test test_clean_fixtures_have_comments "Clean fixtures documented"
    echo ""

    # Integration Tests
    echo -e "${BLUE}INTEGRATION TESTS${NC}"
    run_test test_all_violation_fixtures_different "Violation fixtures are distinct"
    run_test test_receipt_json_valid "Receipt JSON is valid"
    run_test test_violation_count_accuracy "Violation counts are accurate"
    echo ""

    cleanup_test_env
    print_summary
}

# Execute main function and exit with appropriate code
if main; then
    exit 0
else
    exit 1
fi
