#!/usr/bin/env bash
# ==========================================================================
# test_util.sh - Unit tests for observatory/lib/util.sh
#
# Tests utility functions:
# - epoch_ms, timer_start, timer_elapsed_ms
# - sha256_of_file
# - json_escape, json_str, json_arr, json_num
# - git_branch, git_commit, git_dirty
# - detect_java_version, detect_maven_version
# - ensure_output_dirs
# - generate_run_id
# - Logging functions
# - add_refusal
# - discover_modules
# - pom_value
#
# Run with: bash test_util.sh
# ==========================================================================

# Determine shunit2 location
if command -v shunit2 >/dev/null 2>&1; then
    SHUNIT2="$(command -v shunit2)"
elif [[ -f "/usr/share/shunit2/shunit2" ]]; then
    SHUNIT2="/usr/share/shunit2/shunit2"
elif [[ -f "/usr/local/share/shunit2/shunit2" ]]; then
    SHUNIT2="/usr/local/share/shunit2/shunit2"
elif [[ -f "${HOME}/.local/share/shunit2/shunit2" ]]; then
    SHUNIT2="${HOME}/.local/share/shunit2/shunit2"
elif [[ -f "$(dirname "${BASH_SOURCE[0]}")/shunit2" ]]; then
    SHUNIT2="$(dirname "${BASH_SOURCE[0]}")/shunit2"
else
    echo "ERROR: shunit2 not found. Install with: apt-get install shunit2 or brew install shunit2"
    exit 1
fi

# Test constants
UTIL_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/util.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up util.sh test environment..."
    TEMP_DIR=$(mktemp -d)

    # Create minimal test pom.xml
    mkdir -p "${TEMP_DIR}/scripts/observatory/lib"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/facts"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/diagrams/yawl"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/receipts"

    cat > "${TEMP_DIR}/pom.xml" << 'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Beta</version>
    <packaging>pom</packaging>
    <modules>
        <module>yawl-utilities</module>
        <module>yawl-engine</module>
    </modules>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
</project>
POM

    # Initialize git for git function tests
    cd "${TEMP_DIR}"
    git init -q
    git config user.email "test@example.com"
    git config user.name "Test User"
    git add -A
    git commit -qm "Initial commit" --allow-empty 2>/dev/null || true
}

oneTimeTearDown() {
    echo "Tearing down util.sh test environment..."
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf "${TEMP_DIR}"
    fi
}

setUp() {
    # Source util.sh before each test with modified REPO_ROOT
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"

    # Reset arrays
    REFUSALS=()
    WARNINGS=()

    # Source the utility functions (override REPO_ROOT after sourcing)
    source "${UTIL_SCRIPT}"

    # Override REPO_ROOT to temp directory
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Test: Timing functions
# ---------------------------------------------------------------------------

test_epoch_ms_returns_numeric() {
    local result
    result=$(epoch_ms)

    # Should be a number
    assertTrue "epoch_ms should return numeric" "[[ '${result}' =~ ^[0-9]+$ ]]"
}

test_epoch_ms_returns_reasonable_value() {
    local result
    result=$(epoch_ms)

    # Should be reasonably large (milliseconds since epoch)
    assertTrue "epoch_ms should be > 1 trillion (ms since 1970)" "[[ ${result} -gt 1000000000000 ]]"
}

test_timer_start_sets_variable() {
    timer_start
    assertNotNull "_TIMER_START should be set" "${_TIMER_START}"
}

test_timer_elapsed_ms_returns_positive() {
    timer_start
    sleep 0.01
    local elapsed
    elapsed=$(timer_elapsed_ms)

    assertTrue "Elapsed should be >= 0" "[[ ${elapsed} -ge 0 ]]"
}

test_timer_elapsed_ms_measures_delay() {
    timer_start
    sleep 0.1
    local elapsed
    elapsed=$(timer_elapsed_ms)

    # Should be at least 50ms (allowing for some timing variance)
    assertTrue "Elapsed should be at least 50ms" "[[ ${elapsed} -ge 50 ]]"
}

# ---------------------------------------------------------------------------
# Test: SHA-256 functions
# ---------------------------------------------------------------------------

test_sha256_of_file_returns_hash_format() {
    local test_file="${TEMP_DIR}/test_sha.txt"
    echo "test content" > "${test_file}"

    local result
    result=$(sha256_of_file "${test_file}")

    assertContains "Should have sha256: prefix" "${result}" "sha256:"
}

test_sha256_of_file_returns_consistent_hash() {
    local test_file="${TEMP_DIR}/test_sha_consistent.txt"
    echo "consistent content" > "${test_file}"

    local result1
    result1=$(sha256_of_file "${test_file}")
    local result2
    result2=$(sha256_of_file "${test_file}")

    assertEquals "SHA256 should be consistent" "${result1}" "${result2}"
}

test_sha256_of_missing_file_returns_missing() {
    local result
    result=$(sha256_of_file "/nonexistent/file.txt")

    assertEquals "Missing file should return sha256:missing" "sha256:missing" "${result}"
}

test_sha256_of_file_handles_special_content() {
    local test_file="${TEMP_DIR}/test_special.txt"
    printf 'line1\nline2\ttab\rspecial: chars' > "${test_file}"

    local result
    result=$(sha256_of_file "${test_file}")

    assertContains "Should have sha256: prefix" "${result}" "sha256:"
}

# ---------------------------------------------------------------------------
# Test: JSON helpers
# ---------------------------------------------------------------------------

test_json_escape_handles_quotes() {
    local input='say "hello"'
    local result
    result=$(json_escape "${input}")

    assertEquals "Should escape quotes" 'say \"hello\"' "${result}"
}

test_json_escape_handles_backslashes() {
    local input='path\to\file'
    local result
    result=$(json_escape "${input}")

    assertEquals "Should escape backslashes" 'path\\to\\file' "${result}"
}

test_json_escape_handles_newlines() {
    local input=$'line1\nline2'
    local result
    result=$(json_escape "${input}")

    assertEquals "Should escape newlines" 'line1\nline2' "${result}"
}

test_json_escape_handles_tabs() {
    local input=$'col1\tcol2'
    local result
    result=$(json_escape "${input}")

    assertEquals "Should escape tabs" 'col1\tcol2' "${result}"
}

test_json_escape_plain_text_unchanged() {
    local input="plain text without special chars"
    local result
    result=$(json_escape "${input}")

    assertEquals "Plain text should be unchanged" "${input}" "${result}"
}

test_json_str_wraps_in_quotes() {
    local result
    result=$(json_str "hello")

    assertEquals "Should wrap in quotes" '"hello"' "${result}"
}

test_json_str_escapes_content() {
    local result
    result=$(json_str 'say "hi"')

    assertEquals "Should escape and wrap" '"say \"hi\""' "${result}"
}

test_json_arr_empty() {
    local result
    result=$(json_arr)

    assertEquals "Empty array should be []" "[]" "${result}"
}

test_json_arr_single_item() {
    local result
    result=$(json_arr "item1")

    assertEquals "Single item array" '["item1"]' "${result}"
}

test_json_arr_multiple_items() {
    local result
    result=$(json_arr "item1" "item2" "item3")

    assertEquals "Multiple items array" '["item1","item2","item3"]' "${result}"
}

test_json_arr_escapes_items() {
    local result
    result=$(json_arr 'item"1' 'item2')

    assertEquals "Should escape items in array" '["item\"1","item2"]' "${result}"
}

test_json_num_outputs_integer() {
    local result
    result=$(json_num 42)

    assertEquals "Should output integer" "42" "${result}"
}

test_json_num_handles_zero() {
    local result
    result=$(json_num 0)

    assertEquals "Should handle zero" "0" "${result}"
}

# ---------------------------------------------------------------------------
# Test: Git info functions
# ---------------------------------------------------------------------------

test_git_branch_returns_value() {
    cd "${TEMP_DIR}"
    local result
    result=$(git_branch)

    # Should return something (either branch name or "unknown")
    assertNotNull "git_branch should return value" "${result}"
}

test_git_branch_on_known_branch() {
    cd "${TEMP_DIR}"
    git checkout -b test-branch 2>/dev/null || true

    local result
    result=$(git_branch)

    # Should return the branch name
    assertEquals "Should return branch name" "test-branch" "${result}"

    # Cleanup
    git checkout - 2>/dev/null || true
}

test_git_commit_returns_value() {
    cd "${TEMP_DIR}"
    local result
    result=$(git_commit)

    # Should return a short commit hash or "unknown"
    if [[ "${result}" != "unknown" ]]; then
        assertTrue "Commit should be hex" "[[ '${result}' =~ ^[0-9a-f]+$ ]]"
    fi
}

test_git_dirty_returns_false_for_clean() {
    cd "${TEMP_DIR}"
    local result
    result=$(git_dirty)

    assertEquals "Clean repo should return false" "false" "${result}"
}

test_git_dirty_returns_true_for_dirty() {
    cd "${TEMP_DIR}"
    echo "change" >> pom.xml

    local result
    result=$(git_dirty)

    assertEquals "Dirty repo should return true" "true" "${result}"

    # Cleanup
    git checkout pom.xml 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Test: Directory setup
# ---------------------------------------------------------------------------

test_ensure_output_dirs_creates_all_dirs() {
    rm -rf "${FACTS_DIR}" "${DIAGRAMS_DIR}" "${YAWL_DIR}" "${RECEIPTS_DIR}"

    ensure_output_dirs

    assertTrue "FACTS_DIR should exist" "[[ -d ${FACTS_DIR} ]]"
    assertTrue "DIAGRAMS_DIR should exist" "[[ -d ${DIAGRAMS_DIR} ]]"
    assertTrue "YAWL_DIR should exist" "[[ -d ${YAWL_DIR} ]]"
    assertTrue "RECEIPTS_DIR should exist" "[[ -d ${RECEIPTS_DIR} ]]"
}

test_ensure_output_dirs_idempotent() {
    ensure_output_dirs
    ensure_output_dirs
    ensure_output_dirs

    local rc=$?
    assertEquals "Should be idempotent" 0 "${rc}"
}

# ---------------------------------------------------------------------------
# Test: Run ID generation
# ---------------------------------------------------------------------------

test_generate_run_id_format() {
    local result
    result=$(generate_run_id)

    # Format: YYYYMMDDTHHMMSSZ
    assertTrue "Should match ISO format" "[[ '${result}' =~ ^[0-9]{8}T[0-9]{6}Z$ ]]"
}

test_generate_run_id_unique_over_time() {
    local result1
    result1=$(generate_run_id)
    sleep 1
    local result2
    result2=$(generate_run_id)

    # Very likely to be different after 1 second
    assertNotEquals "Run IDs should be unique over time" "${result1}" "${result2}"
}

# ---------------------------------------------------------------------------
# Test: Logging functions
# ---------------------------------------------------------------------------

test_log_info_outputs_message() {
    local result
    result=$(log_info "test message" 2>&1)

    assertContains "Should contain message" "${result}" "test message"
}

test_log_warn_outputs_message() {
    local result
    result=$(log_warn "warning message" 2>&1)

    assertContains "Should contain warning" "${result}" "warning message"
}

test_log_error_outputs_message() {
    local result
    result=$(log_error "error message" 2>&1)

    assertContains "Should contain error" "${result}" "error message"
}

test_log_ok_outputs_message() {
    local result
    result=$(log_ok "success message" 2>&1)

    assertContains "Should contain success" "${result}" "success message"
}

test_log_warn_adds_to_warnings_array() {
    log_warn "test warning"

    assertEquals "WARNINGS array should have 1 element" 1 "${#WARNINGS[@]}"
    assertContains "WARNINGS should contain message" "${WARNINGS[0]}" "test warning"
}

# ---------------------------------------------------------------------------
# Test: add_refusal function
# ---------------------------------------------------------------------------

test_add_refusal_adds_to_array() {
    add_refusal "TEST_CODE" "Test message" '{"key":"value"}'

    assertEquals "REFUSALS should have 1 element" 1 "${#REFUSALS[@]}"
}

test_add_refusal_format() {
    add_refusal "CODE123" "Refusal message" '{"witness":"data"}'

    local result="${REFUSALS[0]}"
    assertContains "Should contain code" "${result}" "CODE123"
    assertContains "Should contain message" "${result}" "Refusal message"
    assertContains "Should contain witness" "${result}" "witness"
}

test_add_refusal_multiple() {
    add_refusal "CODE1" "Message 1" '{}'
    add_refusal "CODE2" "Message 2" '{}'
    add_refusal "CODE3" "Message 3" '{}'

    assertEquals "Should have 3 refusals" 3 "${#REFUSALS[@]}"
}

# ---------------------------------------------------------------------------
# Test: discover_modules function
# ---------------------------------------------------------------------------

test_discover_modules_returns_modules() {
    local result
    result=$(discover_modules)

    assertContains "Should find yawl-utilities" "${result}" "yawl-utilities"
    assertContains "Should find yawl-engine" "${result}" "yawl-engine"
}

test_discover_modules_one_per_line() {
    local result
    result=$(discover_modules)

    local count
    count=$(echo "${result}" | wc -l | tr -d ' ')
    # Note: wc -l may return 2 because of trailing newline handling
    assertTrue "Should have multiple lines" "[[ ${count} -ge 2 ]]"
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
