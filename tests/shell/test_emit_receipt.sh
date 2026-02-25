#!/usr/bin/env bash
# ==========================================================================
# test_emit_receipt.sh - Unit tests for observatory/lib/emit-receipt.sh
#
# Tests receipt and index emission:
# - emit_receipt
# - emit_index
# - emit_receipt_and_index
#
# Run with: bash test_emit_receipt.sh
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
RECEIPT_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/emit-receipt.sh"
UTIL_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/util.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up emit-receipt.sh test environment..."
    TEMP_DIR=$(mktemp -d)

    # Create directory structure
    mkdir -p "${TEMP_DIR}/docs/v6/latest/facts"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/diagrams/yawl"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/receipts"

    # Create minimal pom.xml
    cat > "${TEMP_DIR}/pom.xml" << 'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Beta</version>
    <packaging>pom</packaging>
    <modules>
        <module>yawl-utilities</module>
    </modules>
</project>
POM

    # Create required fact files
    echo '{"test": true}' > "${TEMP_DIR}/docs/v6/latest/facts/modules.json"
    echo '{"test": true}' > "${TEMP_DIR}/docs/v6/latest/facts/reactor.json"
    echo '{"test": true}' > "${TEMP_DIR}/docs/v6/latest/facts/integration.json"

    # Create required diagram files
    echo "graph TD" > "${TEMP_DIR}/docs/v6/latest/diagrams/10-maven-reactor.mmd"
    echo "graph TD" > "${TEMP_DIR}/docs/v6/latest/diagrams/60-mcp-architecture.mmd"
    echo "graph TD" > "${TEMP_DIR}/docs/v6/latest/diagrams/65-a2a-topology.mmd"
    echo "graph TD" > "${TEMP_DIR}/docs/v6/latest/diagrams/70-agent-capabilities.mmd"
    echo "sequenceDiagram" > "${TEMP_DIR}/docs/v6/latest/diagrams/75-protocol-sequences.mmd"

    # Create YAWL XML
    echo '<?xml version="1.0"?><specificationSet/>' > \
        "${TEMP_DIR}/docs/v6/latest/diagrams/yawl/build-and-test.yawl.xml"

    # Create INDEX.md
    echo "# Index" > "${TEMP_DIR}/docs/v6/latest/INDEX.md"

    # Initialize git
    cd "${TEMP_DIR}"
    git init -q
    git config user.email "test@example.com"
    git config user.name "Test User"
    git add -A
    git commit -qm "Initial commit" 2>/dev/null || true
}

oneTimeTearDown() {
    echo "Tearing down emit-receipt.sh test environment..."
    if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
        rm -rf "${TEMP_DIR}"
    fi
}

setUp() {
    # Set up environment variables
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"
    RUN_ID="20250101T120000Z"
    TOTAL_ELAPSED=1000
    FACTS_ELAPSED=200
    DIAGRAMS_ELAPSED=300
    YAWL_XML_ELAPSED=100

    # Reset arrays
    REFUSALS=()
    WARNINGS=()

    # Source utilities first
    source "${UTIL_SCRIPT}"
    # Override REPO_ROOT
    REPO_ROOT="${TEMP_DIR}"
    OUT_DIR="${REPO_ROOT}/docs/v6/latest"
    FACTS_DIR="${OUT_DIR}/facts"
    DIAGRAMS_DIR="${OUT_DIR}/diagrams"
    YAWL_DIR="${DIAGRAMS_DIR}/yawl"
    RECEIPTS_DIR="${OUT_DIR}/receipts"

    # Source emit-receipt functions
    source "${RECEIPT_SCRIPT}"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Test: emit_receipt basic structure
# ---------------------------------------------------------------------------

test_emit_receipt_creates_file() {
    emit_receipt

    assertTrue "observatory.json should exist" "[[ -f ${RECEIPTS_DIR}/observatory.json ]]"
}

test_emit_receipt_valid_json() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    # Check JSON structure
    assertContains "Should have run_id" "${content}" '"run_id"'
    assertContains "Should have status" "${content}" '"status"'
    assertContains "Should have repo" "${content}" '"repo"'
    assertContains "Should have outputs" "${content}" '"outputs"'
    assertContains "Should have timing_ms" "${content}" '"timing_ms"'
}

test_emit_receipt_has_run_id() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have correct run_id" "${content}" "20250101T120000Z"
}

# ---------------------------------------------------------------------------
# Test: Status determination
# ---------------------------------------------------------------------------

test_emit_receipt_status_green_no_issues() {
    REFUSALS=()
    WARNINGS=()
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertTrue "Should have GREEN status" "grep -q '\"status\": \"GREEN\"' ${RECEIPTS_DIR}/observatory.json"
}

test_emit_receipt_status_yellow_with_warnings() {
    REFUSALS=()
    WARNINGS+=("Test warning")
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertTrue "Should have YELLOW status" "grep -q '\"status\": \"YELLOW\"' ${RECEIPTS_DIR}/observatory.json"
}

test_emit_receipt_status_red_with_refusals() {
    REFUSALS+=('{"code":"TEST"}')
    WARNINGS=()
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertTrue "Should have RED status" "grep -q '\"status\": \"RED\"' ${RECEIPTS_DIR}/observatory.json"
}

# ---------------------------------------------------------------------------
# Test: Repo information
# ---------------------------------------------------------------------------

test_emit_receipt_has_repo_path() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have repo path" "${content}" '"path"'
}

test_emit_receipt_has_git_info() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have git.branch" "${content}" '"branch"'
    assertContains "Should have git.commit" "${content}" '"commit"'
    assertContains "Should have git.dirty" "${content}" '"dirty"'
}

# ---------------------------------------------------------------------------
# Test: Output checksums
# ---------------------------------------------------------------------------

test_emit_receipt_has_facts_sha256() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have facts_sha256" "${content}" "facts_sha256"
}

test_emit_receipt_has_diagrams_sha256() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have diagrams_sha256" "${content}" "diagrams_sha256"
}

test_emit_receipt_has_yawl_xml_sha256() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have yawl_xml_sha256" "${content}" "yawl_xml_sha256"
}

# ---------------------------------------------------------------------------
# Test: Refusals and warnings
# ---------------------------------------------------------------------------

test_emit_receipt_has_refusals_array() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have refusals array" "${content}" '"refusals"'
}

test_emit_receipt_has_warnings_array() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have warnings array" "${content}" '"warnings"'
}

# ---------------------------------------------------------------------------
# Test: Timing information
# ---------------------------------------------------------------------------

test_emit_receipt_has_timing_ms() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have timing_ms" "${content}" '"timing_ms"'
    assertContains "Should have total" "${content}" '"total"'
}

test_emit_receipt_timing_values() {
    emit_receipt

    local content
    content=$(cat "${RECEIPTS_DIR}/observatory.json")

    assertContains "Should have total 1000" "${content}" '"total": 1000'
}

# ---------------------------------------------------------------------------
# Test: emit_index
# ---------------------------------------------------------------------------

test_emit_index_creates_file() {
    emit_index

    assertTrue "INDEX.md should exist" "[[ -f ${OUT_DIR}/INDEX.md ]]"
}

test_emit_index_has_header() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should have v6 Observatory header" "${content}" "v6 Observatory"
}

test_emit_index_has_run_info() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should have run_id" "${content}" "20250101T120000Z"
    assertContains "Should have status" "${content}" "Status"
}

test_emit_index_links_receipt() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should link receipt" "${content}" "receipts/observatory.json"
}

test_emit_index_lists_facts() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should list modules.json" "${content}" "facts/modules.json"
    assertContains "Should list reactor.json" "${content}" "facts/reactor.json"
}

test_emit_index_lists_diagrams() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should list reactor diagram" "${content}" "10-maven-reactor.mmd"
}

test_emit_index_links_yawl_workflow() {
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should link YAWL XML" "${content}" "build-and-test.yawl.xml"
}

# ---------------------------------------------------------------------------
# Test: emit_index status colors
# ---------------------------------------------------------------------------

test_emit_index_status_green() {
    REFUSALS=()
    WARNINGS=()
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should show GREEN status" "${content}" "Status: GREEN"
}

test_emit_index_status_yellow() {
    REFUSALS=()
    WARNINGS+=("Test warning")
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should show YELLOW status" "${content}" "Status: YELLOW"
}

test_emit_index_status_red() {
    REFUSALS+=('{"code":"TEST"}')
    emit_index

    local content
    content=$(cat "${OUT_DIR}/INDEX.md")

    assertContains "Should show RED status" "${content}" "Status: RED"
}

# ---------------------------------------------------------------------------
# Test: emit_receipt_and_index
# ---------------------------------------------------------------------------

test_emit_receipt_and_index_creates_both_files() {
    emit_receipt_and_index

    assertTrue "observatory.json should exist" "[[ -f ${RECEIPTS_DIR}/observatory.json ]]"
    assertTrue "INDEX.md should exist" "[[ -f ${OUT_DIR}/INDEX.md ]]"
}

test_emit_receipt_and_index_sets_timing() {
    emit_receipt_and_index

    assertNotNull "RECEIPT_ELAPSED should be set" "${RECEIPT_ELAPSED}"
    assertTrue "RECEIPT_ELAPSED should be >= 0" "[[ ${RECEIPT_ELAPSED:-0} -ge 0 ]]"
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
