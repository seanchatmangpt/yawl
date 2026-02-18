#!/usr/bin/env bash
# ==========================================================================
# test_emit_facts.sh - Unit tests for observatory/lib/emit-facts.sh
#
# Tests fact emission functions:
# - emit_modules
# - emit_reactor
# - emit_integration
# - emit_all_facts
#
# Run with: bash test_emit_facts.sh
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
FACTS_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/emit-facts.sh"
UTIL_SCRIPT="/Users/sac/cre/vendors/yawl/scripts/observatory/lib/util.sh"
PROJECT_DIR="/Users/sac/cre/vendors/yawl"
TEMP_DIR=""

# ---------------------------------------------------------------------------
# Test fixture setup/teardown
# ---------------------------------------------------------------------------

oneTimeSetUp() {
    echo "Setting up emit-facts.sh test environment..."
    TEMP_DIR=$(mktemp -d)

    # Create directory structure
    mkdir -p "${TEMP_DIR}/docs/v6/latest/facts"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/diagrams/yawl"
    mkdir -p "${TEMP_DIR}/docs/v6/latest/receipts"
    mkdir -p "${TEMP_DIR}/yawl-utilities/src/main/java/org/yawlfoundation/yawl/util"
    mkdir -p "${TEMP_DIR}/yawl-engine/src/main/java/org/yawlfoundation/yawl/engine"
    mkdir -p "${TEMP_DIR}/yawl-elements/src/main/java/org/yawlfoundation/yawl/elements"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/util"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/engine"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/elements"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/integration/mcp"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/integration/a2a"
    mkdir -p "${TEMP_DIR}/src/org/yawlfoundation/yawl/integration/zai"

    # Create root pom.xml
    cat > "${TEMP_DIR}/pom.xml" << 'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-parent</artifactId>
    <version>6.0.0-Alpha</version>
    <packaging>pom</packaging>
    <modules>
        <module>yawl-utilities</module>
        <module>yawl-engine</module>
        <module>yawl-elements</module>
    </modules>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.junit</groupId>
                <artifactId>junit-bom</artifactId>
                <version>5.10.0</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
POM

    # Create module pom.xml files
    cat > "${TEMP_DIR}/yawl-utilities/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
    </parent>
    <artifactId>yawl-utilities</artifactId>
    <sourceDirectory>../src</sourceDirectory>
</project>
POM

    cat > "${TEMP_DIR}/yawl-engine/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
    </parent>
    <artifactId>yawl-engine</artifactId>
    <sourceDirectory>../src</sourceDirectory>
    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-utilities</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-elements</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
POM

    cat > "${TEMP_DIR}/yawl-elements/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
    </parent>
    <artifactId>yawl-elements</artifactId>
    <sourceDirectory>../src</sourceDirectory>
    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-utilities</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
POM

    # Create some test Java files
    echo "package org.yawlfoundation.yawl.util; public class Util {}" > \
        "${TEMP_DIR}/src/org/yawlfoundation/yawl/util/Util.java"
    echo "package org.yawlfoundation.yawl.engine; public class Engine {}" > \
        "${TEMP_DIR}/src/org/yawlfoundation/yawl/engine/Engine.java"
    echo "package org.yawlfoundation.yawl.elements; public class YNet {}" > \
        "${TEMP_DIR}/src/org/yawlfoundation/yawl/elements/YNet.java"
}

oneTimeTearDown() {
    echo "Tearing down emit-facts.sh test environment..."
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

    # Source emit-facts functions
    source "${FACTS_SCRIPT}"
}

tearDown() {
    :
}

# ---------------------------------------------------------------------------
# Test: emit_modules
# ---------------------------------------------------------------------------

test_emit_modules_creates_file() {
    emit_modules

    assertTrue "modules.json should exist" "[[ -f ${FACTS_DIR}/modules.json ]]"
}

test_emit_modules_valid_json() {
    emit_modules

    local content
    content=$(cat "${FACTS_DIR}/modules.json")

    # Should be valid JSON structure
    assertContains "Should have modules array" "${content}" '"modules"'
    assertContains "Should have array start" "${content}" '['
    assertContains "Should have array end" "${content}" ']'
}

test_emit_modules_includes_module_names() {
    emit_modules

    local content
    content=$(cat "${FACTS_DIR}/modules.json")

    assertContains "Should have yawl-utilities" "${content}" "yawl-utilities"
    assertContains "Should have yawl-engine" "${content}" "yawl-engine"
    assertContains "Should have yawl-elements" "${content}" "yawl-elements"
}

test_emit_modules_includes_has_pom() {
    emit_modules

    local content
    content=$(cat "${FACTS_DIR}/modules.json")

    assertContains "Should have has_pom field" "${content}" "has_pom"
}

# ---------------------------------------------------------------------------
# Test: emit_reactor
# ---------------------------------------------------------------------------

test_emit_reactor_creates_file() {
    emit_reactor

    assertTrue "reactor.json should exist" "[[ -f ${FACTS_DIR}/reactor.json ]]"
}

test_emit_reactor_valid_json() {
    emit_reactor

    local content
    content=$(cat "${FACTS_DIR}/reactor.json")

    assertContains "Should have reactor_order" "${content}" "reactor_order"
    assertContains "Should have module_deps" "${content}" "module_deps"
}

test_emit_reactor_includes_parent() {
    emit_reactor

    local content
    content=$(cat "${FACTS_DIR}/reactor.json")

    assertContains "Should include yawl-parent" "${content}" "yawl-parent"
}

# ---------------------------------------------------------------------------
# Test: emit_integration
# ---------------------------------------------------------------------------

test_emit_integration_creates_file() {
    emit_integration

    assertTrue "integration.json should exist" "[[ -f ${FACTS_DIR}/integration.json ]]"
}

test_emit_integration_valid_json() {
    emit_integration

    local content
    content=$(cat "${FACTS_DIR}/integration.json")

    assertContains "Should have mcp" "${content}" '"mcp"'
    assertContains "Should have a2a" "${content}" '"a2a"'
    assertContains "Should have zai" "${content}" '"zai"'
}

test_emit_integration_has_mcp_config() {
    emit_integration

    local content
    content=$(cat "${FACTS_DIR}/integration.json")

    assertContains "Should have YawlMcpServer" "${content}" "YawlMcpServer"
    assertContains "Should have STDIO transport" "${content}" "STDIO"
}

test_emit_integration_has_a2a_config() {
    emit_integration

    local content
    content=$(cat "${FACTS_DIR}/integration.json")

    assertContains "Should have YawlA2AServer" "${content}" "YawlA2AServer"
    assertContains "Should have port" "${content}" "port"
}

test_emit_integration_has_zai_config() {
    emit_integration

    local content
    content=$(cat "${FACTS_DIR}/integration.json")

    assertContains "Should have ZaiFunctionService" "${content}" "ZaiFunctionService"
    assertContains "Should have GLM-4 model" "${content}" "GLM-4"
}

# ---------------------------------------------------------------------------
# Test: emit_all_facts
# ---------------------------------------------------------------------------

test_emit_all_facts_creates_all_files() {
    emit_all_facts

    assertTrue "modules.json" "[[ -f ${FACTS_DIR}/modules.json ]]"
    assertTrue "reactor.json" "[[ -f ${FACTS_DIR}/reactor.json ]]"
    assertTrue "integration.json" "[[ -f ${FACTS_DIR}/integration.json ]]"
}

test_emit_all_facts_sets_timing() {
    emit_all_facts

    assertNotNull "FACTS_ELAPSED should be set" "${FACTS_ELAPSED}"
    assertTrue "FACTS_ELAPSED should be >= 0" "[[ ${FACTS_ELAPSED:-0} -ge 0 ]]"
}

# ---------------------------------------------------------------------------
# Test: Edge cases
# ---------------------------------------------------------------------------

test_emit_modules_handles_missing_pom() {
    # Remove one module pom
    rm -f "${TEMP_DIR}/yawl-elements/pom.xml"

    # Should not fail
    emit_modules
    local rc=$?

    assertEquals "Should handle missing pom gracefully" 0 "${rc}"

    # Restore
    cat > "${TEMP_DIR}/yawl-elements/pom.xml" << 'POM'
<?xml version="1.0"?>
<project>
    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
    </parent>
    <artifactId>yawl-elements</artifactId>
    <sourceDirectory>../src</sourceDirectory>
</project>
POM
}

# ---------------------------------------------------------------------------
# Run tests
# ---------------------------------------------------------------------------

# Source shunit2
. "${SHUNIT2}"
