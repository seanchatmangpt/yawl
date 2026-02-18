#!/usr/bin/env bash
# ==========================================================================
# observatory_test.sh — Unit tests for observatory shell library
#
# Run with: bash observatory_test.sh
# Or with shunit2: bash observatory_test.sh (if shunit2 is available)
#
# Test coverage:
#   - emit_fact outputs correct JSON format
#   - emit_mermaid_diagram outputs valid mermaid syntax
#   - emit_yawl_xml outputs valid XML structure
#   - util functions work correctly
#   - grep patterns work portably
# ==========================================================================

set -uo pipefail
# Note: We intentionally do NOT use 'set -e' here because we want to
# continue running tests even when individual tests fail

# Determine script and library locations
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$(cd "${SCRIPT_DIR}/../lib" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Source the utility library
# shellcheck source=../lib/util.sh
source "${LIB_DIR}/util.sh"

# Create temp directory for test outputs
TEST_TMP_DIR=""
setUp() {
    TEST_TMP_DIR=$(mktemp -d)
    # Override output directories for testing
    FACTS_DIR="${TEST_TMP_DIR}/facts"
    DIAGRAMS_DIR="${TEST_TMP_DIR}/diagrams"
    YAWL_DIR="${TEST_TMP_DIR}/diagrams/yawl"
    RECEIPTS_DIR="${TEST_TMP_DIR}/receipts"
    PERF_DIR="${TEST_TMP_DIR}/performance"
    PERF_HISTORY_DIR="${TEST_TMP_DIR}/performance-history"
    mkdir -p "$FACTS_DIR" "$DIAGRAMS_DIR" "$YAWL_DIR" "$RECEIPTS_DIR" "$PERF_DIR" "$PERF_HISTORY_DIR"
}

tearDown() {
    if [[ -n "${TEST_TMP_DIR:-}" && -d "${TEST_TMP_DIR:-}" ]]; then
        rm -rf "$TEST_TMP_DIR"
    fi
}

# ==========================================================================
# Utility Function Tests
# ==========================================================================

test_epoch_ms_returns_numeric() {
    local result
    result=$(epoch_ms)
    # Should be a number (all digits)
    if [[ "$result" =~ ^[0-9]+$ ]]; then
        echo "PASS: epoch_ms returns numeric value"
        return 0
    else
        echo "FAIL: epoch_ms returned non-numeric: $result"
        return 1
    fi
}

test_epoch_ms_increases_over_time() {
    local t1 t2
    t1=$(epoch_ms)
    sleep 0.01  # 10ms
    t2=$(epoch_ms)
    if [[ "$t2" -ge "$t1" ]]; then
        echo "PASS: epoch_ms increases over time ($t1 -> $t2)"
        return 0
    else
        echo "FAIL: epoch_ms did not increase ($t1 -> $t2)"
        return 1
    fi
}

test_timer_elapsed_measures_time() {
    timer_start
    sleep 0.05  # 50ms
    local elapsed
    elapsed=$(timer_elapsed_ms)
    # Should be at least 40ms (allowing some variance)
    if [[ "$elapsed" -ge 40 ]]; then
        echo "PASS: timer_elapsed_ms measured ${elapsed}ms"
        return 0
    else
        echo "FAIL: timer_elapsed_ms too short: ${elapsed}ms"
        return 1
    fi
}

test_json_escape_escapes_quotes() {
    local input='He said "hello"'
    local result
    result=$(json_escape "$input")
    if [[ "$result" == 'He said \"hello\"' ]]; then
        echo "PASS: json_escape escapes quotes"
        return 0
    else
        echo "FAIL: json_escape did not escape quotes: $result"
        return 1
    fi
}

test_json_escape_escapes_backslashes() {
    local input='path\to\file'
    local result
    result=$(json_escape "$input")
    if [[ "$result" == 'path\\to\\file' ]]; then
        echo "PASS: json_escape escapes backslashes"
        return 0
    else
        echo "FAIL: json_escape did not escape backslashes: $result"
        return 1
    fi
}

test_json_escape_escapes_newlines() {
    local input=$'line1\nline2'
    local result
    result=$(json_escape "$input")
    if [[ "$result" == 'line1\nline2' ]]; then
        echo "PASS: json_escape escapes newlines"
        return 0
    else
        echo "FAIL: json_escape did not escape newlines: $result"
        return 1
    fi
}

test_json_str_wraps_in_quotes() {
    local result
    result=$(json_str "test")
    if [[ "$result" == '"test"' ]]; then
        echo "PASS: json_str wraps in quotes"
        return 0
    else
        echo "FAIL: json_str did not wrap: $result"
        return 1
    fi
}

test_json_arr_creates_array() {
    local result
    result=$(json_arr "a" "b" "c")
    if [[ "$result" == '["a","b","c"]' ]]; then
        echo "PASS: json_arr creates array"
        return 0
    else
        echo "FAIL: json_arr output: $result"
        return 1
    fi
}

test_json_arr_empty() {
    local result
    result=$(json_arr)
    if [[ "$result" == '[]' ]]; then
        echo "PASS: json_arr creates empty array"
        return 0
    else
        echo "FAIL: json_arr empty output: $result"
        return 1
    fi
}

test_sha256_of_existing_file() {
    setUp
    echo "test content" > "$TEST_TMP_DIR/testfile.txt"
    local result
    result=$(sha256_of_file "$TEST_TMP_DIR/testfile.txt")
    if [[ "$result" == sha256:* ]]; then
        echo "PASS: sha256_of_file returns sha256 prefix"
        tearDown
        return 0
    else
        echo "FAIL: sha256_of_file missing prefix: $result"
        tearDown
        return 1
    fi
}

test_sha256_of_missing_file() {
    local result
    result=$(sha256_of_file "/nonexistent/file.txt")
    if [[ "$result" == "sha256:missing" ]]; then
        echo "PASS: sha256_of_file returns missing for nonexistent"
        return 0
    else
        echo "FAIL: sha256_of_file wrong for missing: $result"
        return 1
    fi
}

test_git_branch_returns_value() {
    local result
    result=$(git_branch)
    # Should return something (not empty or 'unknown' in a git repo)
    if [[ -n "$result" ]]; then
        echo "PASS: git_branch returns: $result"
        return 0
    else
        echo "FAIL: git_branch returned empty"
        return 1
    fi
}

test_git_commit_returns_value() {
    local result
    result=$(git_commit)
    # Should return a short commit hash or 'unknown'
    if [[ -n "$result" ]]; then
        echo "PASS: git_commit returns: $result"
        return 0
    else
        echo "FAIL: git_commit returned empty"
        return 1
    fi
}

test_git_dirty_returns_boolean() {
    local result
    result=$(git_dirty)
    if [[ "$result" == "true" || "$result" == "false" ]]; then
        echo "PASS: git_dirty returns boolean: $result"
        return 0
    else
        echo "FAIL: git_dirty returned: $result"
        return 1
    fi
}

test_generate_run_id_format() {
    local result
    result=$(generate_run_id)
    # Format: YYYYMMDDTHHMMSSZ
    if [[ "$result" =~ ^[0-9]{8}T[0-9]{6}Z$ ]]; then
        echo "PASS: generate_run_id format: $result"
        return 0
    else
        echo "FAIL: generate_run_id wrong format: $result"
        return 1
    fi
}

test_discover_modules_returns_list() {
    local result
    result=$(discover_modules)
    # Should return at least one module (this is a multi-module Maven project)
    if [[ -n "$result" ]]; then
        echo "PASS: discover_modules found modules"
        return 0
    else
        echo "FAIL: discover_modules returned empty"
        return 1
    fi
}

test_pom_value_extracts_content() {
    # Create a test POM file
    setUp
    cat > "$TEST_TMP_DIR/test-pom.xml" << 'EOF'
<?xml version="1.0"?>
<project>
  <artifactId>test-artifact</artifactId>
  <version>1.0.0</version>
</project>
EOF
    local result
    # The pom_value function now uses sed (portable across macOS/Linux/BSD)
    result=$(pom_value "$TEST_TMP_DIR/test-pom.xml" "artifactId")
    if [[ "$result" == "test-artifact" ]]; then
        echo "PASS: pom_value extracts artifactId"
        tearDown
        return 0
    else
        echo "FAIL: pom_value got: '$result' (expected: test-artifact)"
        tearDown
        return 1
    fi
}

# ==========================================================================
# Grep Pattern Portability Tests
# ==========================================================================

test_grep_oP_alternative_with_sed() {
    # Test portable alternative to grep -oP
    local input='<artifactId>yawl-engine</artifactId>'
    local result
    # Using sed instead of grep -oP for portability
    result=$(echo "$input" | sed -n 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/p')
    if [[ "$result" == "yawl-engine" ]]; then
        echo "PASS: sed extracts between tags"
        return 0
    else
        echo "FAIL: sed extraction: $result"
        return 1
    fi
}

test_grep_lookahead_alternative() {
    # Test alternative to lookhead patterns
    local input='VmRSS:      12345 kB'
    local result
    # Alternative to grep -oP '(?<=VmRSS:\s)\d+'
    result=$(echo "$input" | grep "VmRSS" | sed 's/VmRSS:[[:space:]]*//' | sed 's/[[:space:]].*//' | grep -o '[0-9]*' | head -1)
    if [[ "$result" == "12345" ]]; then
        echo "PASS: alternative to lookahead pattern works"
        return 0
    else
        echo "FAIL: lookahead alternative: $result"
        return 1
    fi
}

test_module_extraction_from_pom() {
    # Test the pattern used in discover_modules
    # Note: The actual pattern extracts one module per line (grep outputs multiple lines)
    local input=$'<modules>\n<module>yawl-engine</module>\n<module>yawl-client</module>\n</modules>'
    local result
    # Use tr to convert newlines to spaces for comparison
    result=$(echo "$input" | grep '<module>' | sed 's/.*<module>\(.*\)<\/module>.*/\1/' | tr '\n' ' ' | sed 's/ $//')
    if [[ "$result" == "yawl-engine yawl-client" ]]; then
        echo "PASS: module extraction works"
        return 0
    else
        echo "FAIL: module extraction: $result"
        return 1
    fi
}

# ==========================================================================
# emit-facts.sh Tests
# ==========================================================================

test_emit_modules_creates_valid_json() {
    setUp
    # Source the emit-facts library
    # shellcheck source=../lib/emit-facts.sh
    source "${LIB_DIR}/emit-facts.sh"

    emit_modules

    local outfile="${FACTS_DIR}/modules.json"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_modules did not create file"
        tearDown
        return 1
    fi

    # Check JSON structure
    if grep -q '"modules"' "$outfile" && grep -q '"name"' "$outfile"; then
        echo "PASS: emit_modules creates valid JSON structure"
        tearDown
        return 0
    else
        echo "FAIL: emit_modules JSON missing expected keys"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_emit_reactor_creates_valid_json() {
    setUp
    # shellcheck source=../lib/emit-facts.sh
    source "${LIB_DIR}/emit-facts.sh"

    emit_reactor

    local outfile="${FACTS_DIR}/reactor.json"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_reactor did not create file"
        tearDown
        return 1
    fi

    # Check JSON structure
    if grep -q '"reactor_order"' "$outfile" && grep -q '"module_deps"' "$outfile"; then
        echo "PASS: emit_reactor creates valid JSON structure"
        tearDown
        return 0
    else
        echo "FAIL: emit_reactor JSON missing expected keys"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_emit_integration_creates_valid_json() {
    setUp
    # shellcheck source=../lib/emit-facts.sh
    source "${LIB_DIR}/emit-facts.sh"

    emit_integration

    local outfile="${FACTS_DIR}/integration.json"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_integration did not create file"
        tearDown
        return 1
    fi

    # Check JSON structure - should have mcp, a2a, zai keys
    if grep -q '"mcp"' "$outfile" && grep -q '"a2a"' "$outfile" && grep -q '"zai"' "$outfile"; then
        echo "PASS: emit_integration creates valid JSON structure"
        tearDown
        return 0
    else
        echo "FAIL: emit_integration JSON missing expected keys"
        cat "$outfile"
        tearDown
        return 1
    fi
}

# ==========================================================================
# emit-diagrams.sh Tests
# ==========================================================================

test_emit_reactor_diagram_creates_mermaid() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_reactor_diagram

    local outfile="${DIAGRAMS_DIR}/10-maven-reactor.mmd"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_reactor_diagram did not create file"
        tearDown
        return 1
    fi

    # Check mermaid structure - should have graph TD or similar
    if grep -q "graph TD" "$outfile" && grep -q "PARENT" "$outfile"; then
        echo "PASS: emit_reactor_diagram creates valid mermaid"
        tearDown
        return 0
    else
        echo "FAIL: emit_reactor_diagram missing mermaid elements"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_emit_mcp_architecture_diagram_creates_mermaid() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_mcp_architecture_diagram

    local outfile="${DIAGRAMS_DIR}/60-mcp-architecture.mmd"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_mcp_architecture_diagram did not create file"
        tearDown
        return 1
    fi

    # Check mermaid structure
    if grep -q "graph TB" "$outfile" && grep -q "YawlMcpServer" "$outfile"; then
        echo "PASS: emit_mcp_architecture_diagram creates valid mermaid"
        tearDown
        return 0
    else
        echo "FAIL: emit_mcp_architecture_diagram missing mermaid elements"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_emit_a2a_topology_diagram_creates_mermaid() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_a2a_topology_diagram

    local outfile="${DIAGRAMS_DIR}/65-a2a-topology.mmd"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_a2a_topology_diagram did not create file"
        tearDown
        return 1
    fi

    # Check mermaid structure
    if grep -q "graph TB" "$outfile" && grep -q "YawlA2AServer" "$outfile"; then
        echo "PASS: emit_a2a_topology_diagram creates valid mermaid"
        tearDown
        return 0
    else
        echo "FAIL: emit_a2a_topology_diagram missing mermaid elements"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_emit_protocol_sequences_diagram_creates_sequence() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_protocol_sequences_diagram

    local outfile="${DIAGRAMS_DIR}/75-protocol-sequences.mmd"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_protocol_sequences_diagram did not create file"
        tearDown
        return 1
    fi

    # Check mermaid sequence diagram structure
    if grep -q "sequenceDiagram" "$outfile" && grep -q "participant" "$outfile"; then
        echo "PASS: emit_protocol_sequences_diagram creates valid sequence diagram"
        tearDown
        return 0
    else
        echo "FAIL: emit_protocol_sequences_diagram missing sequence elements"
        cat "$outfile"
        tearDown
        return 1
    fi
}

test_mermaid_diagram_has_valid_init_directive() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_reactor_diagram

    local outfile="${DIAGRAMS_DIR}/10-maven-reactor.mmd"

    # Check for mermaid init directive
    if grep -q "%%{ init:" "$outfile"; then
        echo "PASS: mermaid diagram has valid init directive"
        tearDown
        return 0
    else
        echo "FAIL: mermaid diagram missing init directive"
        tearDown
        return 1
    fi
}

test_mermaid_diagram_has_class_definitions() {
    setUp
    # shellcheck source=../lib/emit-diagrams.sh
    source "${LIB_DIR}/emit-diagrams.sh"

    emit_reactor_diagram

    local outfile="${DIAGRAMS_DIR}/10-maven-reactor.mmd"

    # Check for classDef directives
    if grep -q "classDef" "$outfile"; then
        echo "PASS: mermaid diagram has class definitions"
        tearDown
        return 0
    else
        echo "FAIL: mermaid diagram missing class definitions"
        tearDown
        return 1
    fi
}

# ==========================================================================
# emit-yawl-xml.sh Tests
# ==========================================================================

test_emit_build_test_yawl_creates_xml() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"
    if [[ ! -f "$outfile" ]]; then
        echo "FAIL: emit_build_test_yawl did not create file"
        tearDown
        return 1
    fi

    echo "PASS: emit_build_test_yawl creates XML file"
    tearDown
    return 0
}

test_emit_yawl_xml_has_xml_declaration() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check XML declaration
    if head -1 "$outfile" | grep -q '<?xml version="1.0"'; then
        echo "PASS: YAWL XML has XML declaration"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing XML declaration"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_specification_root() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for specification root element (may have version attribute before xmlns)
    if grep -q '<specification' "$outfile" && grep -q 'xmlns="http://www.yawlfoundation.org/yawlschema"' "$outfile"; then
        echo "PASS: YAWL XML has specification root element"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing specification root"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_metadata() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for metaData section
    if grep -q '<metaData>' "$outfile" && grep -q '<title>' "$outfile"; then
        echo "PASS: YAWL XML has metadata"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing metadata"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_decomposition() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for decomposition element
    if grep -q '<decomposition' "$outfile" && grep -q 'isRootNet="true"' "$outfile"; then
        echo "PASS: YAWL XML has root decomposition"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing root decomposition"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_input_condition() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for inputCondition (start)
    if grep -q '<inputCondition id="start"' "$outfile"; then
        echo "PASS: YAWL XML has input condition"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing input condition"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_output_condition() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for outputCondition (end)
    if grep -q '<outputCondition id="end"' "$outfile"; then
        echo "PASS: YAWL XML has output condition"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing output condition"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_tasks() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for task elements
    if grep -q '<task id="compile"' "$outfile" && grep -q '<task id="unitTests"' "$outfile"; then
        echo "PASS: YAWL XML has task definitions"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing task definitions"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_flows_into() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for flowsInto elements
    if grep -q '<flowsInto>' "$outfile" && grep -q '<nextElementRef' "$outfile"; then
        echo "PASS: YAWL XML has flow definitions"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing flow definitions"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_has_join_split_codes() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for join/split codes (xor, and)
    if grep -q '<join code=' "$outfile" && grep -q '<split code=' "$outfile"; then
        echo "PASS: YAWL XML has join/split codes"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing join/split codes"
        tearDown
        return 1
    fi
}

test_emit_yawl_xml_closing_tag() {
    setUp
    # shellcheck source=../lib/emit-yawl-xml.sh
    source "${LIB_DIR}/emit-yawl-xml.sh"

    emit_build_test_yawl

    local outfile="${YAWL_DIR}/build-and-test.yawl.xml"

    # Check for proper closing
    if grep -q '</specification>' "$outfile"; then
        echo "PASS: YAWL XML has closing specification tag"
        tearDown
        return 0
    else
        echo "FAIL: YAWL XML missing closing tag"
        tearDown
        return 1
    fi
}

# ==========================================================================
# Run Tests
# ==========================================================================

run_tests() {
    local passed=0
    local failed=0
    local failed_tests=()

    echo "========================================"
    echo "Running Observatory Unit Tests"
    echo "========================================"
    echo ""

    # Get all test functions defined in this script
    local tests
    # Use grep on the script file itself to find test functions
    # Match lines like: test_function_name() {
    tests=$(grep -E '^test_[a-zA-Z0-9_]+\(\)' "$0" | sed 's/().*//g' | sort)

    for test_func in $tests; do
        echo "--- Running: $test_func ---"
        # Run test and capture exit code
        set +e
        "$test_func"
        local result=$?
        set -e
        if [[ $result -eq 0 ]]; then
            passed=$((passed + 1))
        else
            failed=$((failed + 1))
            failed_tests+=("$test_func")
        fi
        echo ""
    done

    echo "========================================"
    echo "Test Results"
    echo "========================================"
    echo "Passed: $passed"
    echo "Failed: $failed"
    echo ""

    if [[ $failed -gt 0 ]]; then
        echo "Failed tests:"
        for ft in "${failed_tests[@]}"; do
            echo "  - $ft"
        done
        return 1
    fi

    echo "All tests passed!"
    return 0
}

# Run tests if executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_tests
    exit $?
fi
