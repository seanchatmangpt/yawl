#!/usr/bin/env bash
# ==========================================================================
# test_gen_v6_diagrams.sh - Unit tests for gen_v6_diagrams.sh
#
# Tests the diagram generation script for:
# - Argument parsing
# - Directory setup
# - Diagram generation functions
# - YAWL XML generation
# - Facts generation
# - Error handling and edge cases
#
# Run with: bash test_gen_v6_diagrams.sh
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
SCRIPT_UNDER_TEST="/Users/sac/cre/vendors/yawl/tools/gen_v6_diagrams.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up test environment..."
    TEMP_DIR=$(mktemp -d)
    mkdir -p "${TEMP_DIR}/docs/v6/diagrams/yawl"
    mkdir -p "${TEMP_DIR}/docs/v6/diagrams/facts"
    mkdir -p "${TEMP_DIR}/.github/workflows"
    mkdir -p "${TEMP_DIR}/schema"

    # Create a minimal pom.xml for testing
    cat > "${TEMP_DIR}/pom.xml" << 'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Alpha</version>
    <packaging>pom</packaging>
    <modules>
        <module>yawl-utilities</module>
        <module>yawl-elements</module>
        <module>yawl-engine</module>
    </modules>
</project>
POM

    # Create dummy workflow file
    echo "name: CI" > "${TEMP_DIR}/.github/workflows/ci.yml"
    echo "on: [push]" >> "${TEMP_DIR}/.github/workflows/ci.yml"
    echo "jobs:" >> "${TEMP_DIR}/.github/workflows/ci.yml"
    echo "  build:" >> "${TEMP_DIR}/.github/workflows/ci.yml"
    echo "    runs-on: ubuntu-latest" >> "${TEMP_DIR}/.github/workflows/ci.yml"

    # Create minimal schema
    echo '<?xml version="1.0"?>' > "${TEMP_DIR}/schema/YAWL_Schema4.0.xsd"
    echo '<xs:schema version="4.0"/>' >> "${TEMP_DIR}/schema/YAWL_Schema4.0.xsd"
}

oneTimeTearDown() {
    echo "Tearing down test environment..."
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf "${TEMP_DIR}"
    fi
}

setUp() {
    # Reset environment for each test
    cd "${PROJECT_DIR}" || fail "Cannot cd to project dir"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

# Source the script and capture its functions for testing
source_script_functions() {
    # We need to source the script but prevent main() from running
    # Extract functions by sourcing with BASH_SOURCE trap
    local script_content
    script_content=$(cat "${SCRIPT_UNDER_TEST}")

    # Define colors for non-tty testing
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''

    # Source functions by evaluating without the main call
    (
        # Set up variables that the script expects
        SCRIPT_DIR="${PROJECT_DIR}/tools"
        PROJECT_DIR="${PROJECT_DIR}"
        OUTPUT_DIR="${TEMP_DIR}/docs/v6/diagrams"
        USE_MAVEN=false

        # Source the helper functions
        eval "$(grep -E '^(info|success|warn|error|fatal|header|setup_dirs|generate_)' "${SCRIPT_UNDER_TEST}" | head -200 || true)"
    ) 2>/dev/null || true
}

# ---------------------------------------------------------------------------
# Test: Script exists and is executable
# ---------------------------------------------------------------------------

test_script_exists() {
    assertTrue "Script should exist" "[[ -f ${SCRIPT_UNDER_TEST} ]]"
}

test_script_is_executable_or_readable() {
    assertTrue "Script should be readable" "[[ -r ${SCRIPT_UNDER_TEST} ]]"
}

test_script_has_shebang() {
    local shebang
    shebang=$(head -1 "${SCRIPT_UNDER_TEST}")
    assertContains "Should have bash shebang" "${shebang}" "bash"
}

# ---------------------------------------------------------------------------
# Test: Argument parsing
# ---------------------------------------------------------------------------

test_help_flag_outputs_usage() {
    local output
    output=$("${SCRIPT_UNDER_TEST}" --help 2>&1)
    local rc=$?

    assertEquals "Help should exit 0" 0 "${rc}"
    assertContains "Help should contain usage" "${output}" "Usage:"
    assertContains "Help should mention --no-maven" "${output}" "--no-maven"
    assertContains "Help should mention --output" "${output}" "--output"
}

test_help_short_flag_outputs_usage() {
    local output
    output=$("${SCRIPT_UNDER_TEST}" -h 2>&1)
    local rc=$?

    assertEquals "Help -h should exit 0" 0 "${rc}"
    assertContains "Help -h should contain usage" "${output}" "Usage:"
}

test_unknown_argument_exits_with_error() {
    local output
    output=$("${SCRIPT_UNDER_TEST}" --invalid-arg 2>&1)
    local rc=$?

    assertNotEquals "Unknown arg should fail" 0 "${rc}"
    assertContains "Should mention unknown argument" "${output}" "Unknown argument"
}

test_no_maven_flag_accepted() {
    # Run with --help to prevent full execution, but check that --no-maven is parsed
    local output
    output=$("${SCRIPT_UNDER_TEST}" --no-maven --help 2>&1)
    local rc=$?

    assertEquals "--no-maven with --help should succeed" 0 "${rc}"
}

test_output_flag_accepted() {
    local output
    output=$("${SCRIPT_UNDER_TEST}" --output=/tmp/test --help 2>&1)
    local rc=$?

    assertEquals "--output with --help should succeed" 0 "${rc}"
}

# ---------------------------------------------------------------------------
# Test: Directory setup
# ---------------------------------------------------------------------------

test_setup_dirs_creates_output_structure() {
    # Run the actual script with --no-maven and custom output
    local test_output="${TEMP_DIR}/test_output_dirs"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1
    local rc=$?

    # Check directory structure was created
    assertTrue "Should create yawl dir" "[[ -d ${test_output}/yawl ]]"
    assertTrue "Should create facts dir" "[[ -d ${test_output}/facts ]]"
}

# ---------------------------------------------------------------------------
# Test: Diagram generation
# ---------------------------------------------------------------------------

test_generates_reactor_map_diagram() {
    local test_output="${TEMP_DIR}/test_reactor"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "reactor-map.mmd should exist" "[[ -f ${test_output}/reactor-map.mmd ]]"

    local content
    content=$(cat "${test_output}/reactor-map.mmd")
    assertContains "Should contain Mermaid graph" "${content}" "graph LR"
    assertContains "Should contain Foundation subgraph" "${content}" "Foundation"
    assertContains "Should contain Core subgraph" "${content}" "Core"
}

test_generates_build_lifecycle_diagram() {
    local test_output="${TEMP_DIR}/test_build_lifecycle"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "build-lifecycle.mmd should exist" "[[ -f ${test_output}/build-lifecycle.mmd ]]"

    local content
    content=$(cat "${test_output}/build-lifecycle.mmd")
    assertContains "Should contain flowchart" "${content}" "flowchart TD"
    assertContains "Should contain validate phase" "${content}" "validate"
    assertContains "Should contain compile phase" "${content}" "compile"
    assertContains "Should contain test phase" "${content}" "test"
}

test_generates_test_topology_diagram() {
    local test_output="${TEMP_DIR}/test_topology"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "test-topology.mmd should exist" "[[ -f ${test_output}/test-topology.mmd ]]"

    local content
    content=$(cat "${test_output}/test-topology.mmd")
    assertContains "Should contain flowchart" "${content}" "flowchart TD"
    assertContains "Should mention Unit Tests" "${content}" "Unit Tests"
    assertContains "Should mention Docker" "${content}" "Docker"
}

test_generates_ci_gates_diagram() {
    local test_output="${TEMP_DIR}/test_ci_gates"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "ci-gates.mmd should exist" "[[ -f ${test_output}/ci-gates.mmd ]]"

    local content
    content=$(cat "${test_output}/ci-gates.mmd")
    assertContains "Should contain flowchart" "${content}" "flowchart LR"
    assertContains "Should contain Quality Gate" "${content}" "Quality Gate"
    assertContains "Should mention Hyper Standards" "${content}" "Hyper Standards"
}

test_generates_module_dependencies_diagram() {
    local test_output="${TEMP_DIR}/test_module_deps"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "module-dependencies.mmd should exist" "[[ -f ${test_output}/module-dependencies.mmd ]]"

    local content
    content=$(cat "${test_output}/module-dependencies.mmd")
    assertContains "Should contain graph" "${content}" "graph TD"
    # Should contain static fallback dependencies
    assertContains "Should mention yawl-elements" "${content}" "yawl-elements"
    assertContains "Should mention yawl-engine" "${content}" "yawl-engine"
}

# ---------------------------------------------------------------------------
# Test: YAWL XML generation
# ---------------------------------------------------------------------------

test_generates_yawl_xml_specification() {
    local test_output="${TEMP_DIR}/test_yawl_xml"
    mkdir -p "${test_output}/yawl"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "YAWL XML should exist" "[[ -f ${test_output}/yawl/build-and-test.yawl.xml ]]"

    local content
    content=$(cat "${test_output}/yawl/build-and-test.yawl.xml")
    assertContains "Should be XML" "${content}" '<?xml version="1.0"'
    assertContains "Should have specificationSet" "${content}" "specificationSet"
    assertContains "Should have YAWL namespace" "${content}" "yawlfoundation.org/yawlschema"
    assertContains "Should have Compile task" "${content}" "Compile"
    assertContains "Should have UnitTest task" "${content}" "UnitTest"
    assertContains "Should have inputCondition" "${content}" "inputCondition"
    assertContains "Should have outputCondition" "${content}" "outputCondition"
}

test_yawl_xml_has_correct_version() {
    local test_output="${TEMP_DIR}/test_yawl_version"
    mkdir -p "${test_output}/yawl"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    local content
    content=$(cat "${test_output}/yawl/build-and-test.yawl.xml")
    # Version should come from pom.xml
    assertContains "Should contain version" "${content}" "6.0.0-Alpha"
}

# ---------------------------------------------------------------------------
# Test: Facts generation
# ---------------------------------------------------------------------------

test_generates_modules_fact() {
    local test_output="${TEMP_DIR}/test_facts_modules"
    mkdir -p "${test_output}/facts"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "modules.txt should exist" "[[ -f ${test_output}/facts/modules.txt ]]"
}

test_generates_ci_jobs_fact() {
    local test_output="${TEMP_DIR}/test_facts_jobs"
    mkdir -p "${test_output}/facts"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "ci-jobs.txt should exist" "[[ -f ${test_output}/facts/ci-jobs.txt ]]"
}

test_generates_schema_version_fact() {
    local test_output="${TEMP_DIR}/test_facts_schema"
    mkdir -p "${test_output}/facts"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "schema-version.txt should exist" "[[ -f ${test_output}/facts/schema-version.txt ]]"

    local content
    content=$(cat "${test_output}/facts/schema-version.txt")
    # Should contain some version string
    assertNotNull "Schema version should not be empty" "${content}"
}

# ---------------------------------------------------------------------------
# Test: INDEX.md generation
# ---------------------------------------------------------------------------

test_generates_index_md() {
    local test_output="${TEMP_DIR}/test_index"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    assertTrue "INDEX.md should exist" "[[ -f ${test_output}/INDEX.md ]]"

    local content
    content=$(cat "${test_output}/INDEX.md")
    assertContains "Should have title" "${content}" "YAWL v6 Diagram Index"
    assertContains "Should mention Generated" "${content}" "Generated:"
    assertContains "Should link reactor-map" "${content}" "reactor-map.mmd"
    assertContains "Should link build-lifecycle" "${content}" "build-lifecycle.mmd"
    assertContains "Should link YAWL spec" "${content}" "build-and-test.yawl.xml"
}

test_index_has_generation_timestamp() {
    local test_output="${TEMP_DIR}/test_index_time"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    local content
    content=$(cat "${test_output}/INDEX.md")
    # Should contain UTC timestamp format
    assertTrue "Should have UTC timestamp" "[[ '${content}' =~ [0-9]{4}-[0-9]{2}-[0-9]{2}.*UTC ]]"
}

# ---------------------------------------------------------------------------
# Test: Error handling
# ---------------------------------------------------------------------------

test_missing_pom_exits_with_error() {
    local test_output="${TEMP_DIR}/test_no_pom"
    mkdir -p "${test_output}"
    rm -f "${TEMP_DIR}/missing_pom/pom.xml"

    # Create a temp script that changes PROJECT_DIR
    local temp_script="${TEMP_DIR}/test_script.sh"
    sed "s|PROJECT_DIR=.*|PROJECT_DIR=\"${TEMP_DIR}/missing_pom\"|" "${SCRIPT_UNDER_TEST}" > "${temp_script}"
    chmod +x "${temp_script}"

    local output
    output=$(bash "${temp_script}" --no-maven 2>&1)
    local rc=$?

    assertNotEquals "Missing pom should fail" 0 "${rc}"
    assertContains "Should mention pom.xml" "${output}" "pom.xml"

    rm -f "${temp_script}"
}

test_script_uses_set_e() {
    # Check that script uses set -e (either as combined or separate)
    local result
    result=$(grep -c "set -" "${SCRIPT_UNDER_TEST}" 2>/dev/null || echo "0")
    assertTrue "Should use set command" "[[ ${result} -gt 0 ]]"

    # Also check for -e flag specifically
    result=$(grep "set -" "${SCRIPT_UNDER_TEST}" | grep -c "e" 2>/dev/null || echo "0")
    assertTrue "Should use -e flag" "[[ ${result} -gt 0 ]]"
}

test_script_uses_set_u() {
    # Check that script uses set -u (either as combined or separate)
    local result
    result=$(grep "set -" "${SCRIPT_UNDER_TEST}" | grep -c "u" 2>/dev/null || echo "0")
    assertTrue "Should use -u flag" "[[ ${result} -gt 0 ]]"
}

test_script_uses_set_o_pipefail() {
    # Check that script uses pipefail
    local result
    result=$(grep -c "pipefail" "${SCRIPT_UNDER_TEST}" 2>/dev/null || echo "0")
    assertTrue "Should use pipefail" "[[ ${result} -gt 0 ]]"
}

# ---------------------------------------------------------------------------
# Test: Edge cases
# ---------------------------------------------------------------------------

test_empty_output_directory() {
    local test_output="${TEMP_DIR}/test_empty_output"
    # Don't create the directory - let the script create it

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1
    local rc=$?

    assertEquals "Should handle non-existent output dir" 0 "${rc}"
    assertTrue "Should create output dir" "[[ -d ${test_output} ]]"
}

test_mermaid_diagrams_have_valid_syntax_header() {
    local test_output="${TEMP_DIR}/test_mermaid_syntax"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1

    # All .mmd files should have valid Mermaid start
    for mmd_file in "${test_output}"/*.mmd; do
        assertTrue "Mermaid file should exist: ${mmd_file}" "[[ -f ${mmd_file} ]]"
        local first_line
        first_line=$(head -1 "${mmd_file}")
        assertTrue "Should start with graph, flowchart, or %%{init}" \
            "[[ '${first_line}' =~ ^(graph|flowchart|%%{init) ]]"
    done
}

test_full_run_succeeds() {
    local test_output="${TEMP_DIR}/test_full_run"
    mkdir -p "${test_output}"

    "${SCRIPT_UNDER_TEST}" --no-maven --output="${test_output}" >/dev/null 2>&1
    local rc=$?

    assertEquals "Full run should succeed" 0 "${rc}"

    # Verify all expected files exist
    assertTrue "reactor-map.mmd" "[[ -f ${test_output}/reactor-map.mmd ]]"
    assertTrue "build-lifecycle.mmd" "[[ -f ${test_output}/build-lifecycle.mmd ]]"
    assertTrue "test-topology.mmd" "[[ -f ${test_output}/test-topology.mmd ]]"
    assertTrue "ci-gates.mmd" "[[ -f ${test_output}/ci-gates.mmd ]]"
    assertTrue "module-dependencies.mmd" "[[ -f ${test_output}/module-dependencies.mmd ]]"
    assertTrue "yawl/build-and-test.yawl.xml" "[[ -f ${test_output}/yawl/build-and-test.yawl.xml ]]"
    assertTrue "facts/modules.txt" "[[ -f ${test_output}/facts/modules.txt ]]"
    assertTrue "facts/ci-jobs.txt" "[[ -f ${test_output}/facts/ci-jobs.txt ]]"
    assertTrue "facts/schema-version.txt" "[[ -f ${test_output}/facts/schema-version.txt ]]"
    assertTrue "INDEX.md" "[[ -f ${test_output}/INDEX.md ]]"
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
