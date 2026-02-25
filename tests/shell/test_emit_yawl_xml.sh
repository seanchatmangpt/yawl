#!/usr/bin/env bash
# ==========================================================================
# test_emit_yawl_xml.sh - Unit tests for observatory/lib/emit-yawl-xml.sh
#
# Tests YAWL XML emission:
# - emit_yawl_xml
# - emit_yawl_xml_all
#
# Run with: bash test_emit_yawl_xml.sh
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
YAWL_XML_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/emit-yawl-xml.sh"
UTIL_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/util.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up emit-yawl-xml.sh test environment..."
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

    # Initialize git
    cd "${TEMP_DIR}"
    git init -q
    git config user.email "test@example.com"
    git config user.name "Test User"
    git add -A
    git commit -qm "Initial commit" 2>/dev/null || true
}

oneTimeTearDown() {
    echo "Tearing down emit-yawl-xml.sh test environment..."
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

    # Source emit-yawl-xml functions
    source "${YAWL_XML_SCRIPT}"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Test: emit_yawl_xml basic structure
# ---------------------------------------------------------------------------

test_emit_yawl_xml_creates_file() {
    emit_yawl_xml

    assertTrue "YAWL XML file should exist" "[[ -f ${YAWL_DIR}/build-and-test.yawl.xml ]]"
}

test_emit_yawl_xml_has_xml_declaration() {
    emit_yawl_xml

    local content
    content=$(head -1 "${YAWL_DIR}/build-and-test.yawl.xml")

    assertEquals "Should have XML declaration" '<?xml version="1.0" encoding="UTF-8"?>' "${content}"
}

test_emit_yawl_xml_includes_run_id() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should include run_id" "${content}" "20250101T120000Z"
}

# ---------------------------------------------------------------------------
# Test: specificationSet element
# ---------------------------------------------------------------------------

test_emit_yawl_xml_has_specification_set() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have specificationSet" "${content}" "<specificationSet"
    assertContains "Should close specificationSet" "${content}" "</specificationSet>"
}

test_emit_yawl_xml_has_yawl_namespace() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have YAWL namespace" "${content}" "yawlfoundation.org/yawlschema"
}

test_emit_yawl_xml_has_schema_location() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have schemaLocation" "${content}" "schemaLocation"
    assertContains "Should reference YAWL_Schema4.0.xsd" "${content}" "YAWL_Schema4.0.xsd"
}

# ---------------------------------------------------------------------------
# Test: specification element
# ---------------------------------------------------------------------------

test_emit_yawl_xml_has_specification() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have specification" "${content}" "<specification"
    assertContains "Should have uri" "${content}" 'uri="BuildAndTest"'
}

test_emit_yawl_xml_has_metadata() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have metaData" "${content}" "<metaData>"
    assertContains "Should have title" "${content}" "<title>"
    assertContains "Should have creator" "${content}" "<creator>"
}

# ---------------------------------------------------------------------------
# Test: Root net decomposition
# ---------------------------------------------------------------------------

test_emit_yawl_xml_has_root_net() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have root decomposition" "${content}" 'isRootNet="true"'
}

test_emit_yawl_xml_has_process_control_elements() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have processControlElements" "${content}" "<processControlElements>"
}

# ---------------------------------------------------------------------------
# Test: Input/Output conditions
# ---------------------------------------------------------------------------

test_emit_yawl_xml_has_input_condition() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have inputCondition" "${content}" "<inputCondition"
}

test_emit_yawl_xml_has_output_condition() {
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    assertContains "Should have outputCondition" "${content}" "<outputCondition"
}

# ---------------------------------------------------------------------------
# Test: emit_yawl_xml_all function
# ---------------------------------------------------------------------------

test_emit_yawl_xml_all_creates_file() {
    emit_yawl_xml_all

    assertTrue "YAWL XML should exist" "[[ -f ${YAWL_DIR}/build-and-test.yawl.xml ]]"
}

test_emit_yawl_xml_all_sets_timing() {
    emit_yawl_xml_all

    assertNotNull "YAWL_XML_ELAPSED should be set" "${YAWL_XML_ELAPSED}"
    assertTrue "YAWL_XML_ELAPSED should be >= 0" "[[ ${YAWL_XML_ELAPSED:-0} -ge 0 ]]"
}

# ---------------------------------------------------------------------------
# Test: XML well-formedness
# ---------------------------------------------------------------------------

test_emit_yawl_xml_is_well_formed() {
    emit_yawl_xml

    # Use xmllint if available to check well-formedness
    if command -v xmllint >/dev/null 2>&1; then
        xmllint --noout "${YAWL_DIR}/build-and-test.yawl.xml" 2>&1
        local rc=$?
        assertEquals "XML should be well-formed" 0 "${rc}"
    else
        # Skip if xmllint not available
        echo "Skipping well-formedness check (xmllint not available)"
    fi
}

# ---------------------------------------------------------------------------
# Test: Edge cases
# ---------------------------------------------------------------------------

test_emit_yawl_xml_handles_missing_run_id() {
    RUN_ID=""

    emit_yawl_xml

    assertTrue "Should still create file" "[[ -f ${YAWL_DIR}/build-and-test.yawl.xml ]]"
}

test_emit_yawl_xml_overwrites_existing() {
    emit_yawl_xml

    # Modify file
    echo "<!-- modified -->" >> "${YAWL_DIR}/build-and-test.yawl.xml"

    # Run again
    emit_yawl_xml

    local content
    content=$(cat "${YAWL_DIR}/build-and-test.yawl.xml")

    # Should not contain the modification
    assertTrue "Should not contain modification" "! grep -q '<!-- modified -->' ${YAWL_DIR}/build-and-test.yawl.xml"
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
