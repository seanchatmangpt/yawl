#!/usr/bin/env bash
# ==========================================================================
# new-module.sh — Scaffold a new YAWL module in <2 minutes
#
# Automates the creation of a complete Maven module following YAWL v6
# conventions. Includes directory structure, pom.xml, package-info.java,
# and parent POM integration.
#
# Usage:
#   bash scripts/new-module.sh yawl-cache-plugin
#   bash scripts/new-module.sh yawl-my-feature "Feature Description"
#   bash scripts/new-module.sh --help
#
# Arguments:
#   MODULE_NAME      Module artifact ID (must start with 'yawl-' prefix)
#   DESCRIPTION      Optional: Module description for pom.xml (default: auto)
#
# Environment:
#   NO_PARENT_POM=1  Skip parent pom.xml modification (manual integration)
#
# Output: New module directory structure ready for:
#   1. Add dependencies to pom.xml (if needed)
#   2. Implement first class(es)
#   3. Add to version control: git add <module>/
#   4. Run: bash scripts/dx.sh compile
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Constants ──────────────────────────────────────────────────────────────
readonly YAWL_VERSION="6.0.0-Alpha"
readonly YAWL_GROUP_ID="org.yawlfoundation"
readonly YAWL_BASE_PACKAGE="org.yawlfoundation.yawl"

# YAWL License header (reused in all generated files)
readonly LICENSE_HEADER="/*
 * Copyright (c) 2004-$(date +%Y) The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */"

# ── Print help ─────────────────────────────────────────────────────────────
print_help() {
    sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
}

# ── Utility functions ──────────────────────────────────────────────────────
error() {
    echo "[new-module] ERROR: $*" >&2
    exit 1
}

info() {
    echo "[new-module] INFO: $*" >&2
}

warn() {
    echo "[new-module] WARN: $*" >&2
}

success() {
    echo "[new-module] SUCCESS: $*" >&2
}

# Validate module name follows YAWL convention
validate_module_name() {
    local name="$1"

    if [[ ! "$name" =~ ^yawl- ]]; then
        error "Module name must start with 'yawl-' prefix (got: $name)"
    fi

    if [[ ! "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
        error "Module name must contain only lowercase letters, numbers, hyphens (got: $name)"
    fi

    if [[ -d "${REPO_ROOT}/${name}" ]]; then
        error "Module directory already exists: ${REPO_ROOT}/${name}"
    fi
}

# Convert yawl-cache-plugin -> cache_plugin (for package names)
module_name_to_package_suffix() {
    local name="$1"
    # Remove 'yawl-' prefix, replace '-' with '_'
    echo "${name#yawl-}" | tr '-' '_'
}

# Parse module name for description
generate_description() {
    local name="$1"
    local user_desc="$2"

    if [[ -n "$user_desc" ]]; then
        echo "$user_desc"
        return
    fi

    # Auto-generate from module name: yawl-cache-plugin -> Cache Plugin
    local suffix="${name#yawl-}"
    local capitalized=$(echo "$suffix" | tr '-' ' ' | sed 's/\b\(.\)/\U\1/g')
    echo "$capitalized"
}

# ── Generate pom.xml ──────────────────────────────────────────────────────
generate_pom_xml() {
    local module_name="$1"
    local description="$2"

    cat > "${REPO_ROOT}/${module_name}/pom.xml" << 'POMEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.yawlfoundation</groupId>
        <artifactId>yawl-parent</artifactId>
        <version>6.0.0-Alpha</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>MODULE_ARTIFACT_ID</artifactId>
    <packaging>jar</packaging>

    <name>YAWL MODULE_DISPLAY_NAME</name>
    <description>MODULE_DESCRIPTION</description>

    <dependencies>
        <!-- YAWL core -->
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-utilities</artifactId>
            <version>${project.version}</version>
        </dependency>

        <!-- JUnit 5 (Jupiter) for testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.hamcrest</groupId>
            <artifactId>hamcrest</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Java 25 compiler -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${maven.compiler.plugin.version}</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                    <enablePreview>true</enablePreview>
                </configuration>
            </plugin>

            <!-- Surefire for unit tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${maven.surefire.plugin.version}</version>
                <configuration>
                    <includes>
                        <include>**/*Test.java</include>
                        <include>**/*Tests.java</include>
                    </includes>
                    <excludes>
                        <exclude>**/*IT.java</exclude>
                    </excludes>
                </configuration>
            </plugin>

            <!-- JaCoCo for code coverage -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.plugin.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
POMEOF

    # Substitute placeholders
    sed -i "s/MODULE_ARTIFACT_ID/${module_name}/g" "${REPO_ROOT}/${module_name}/pom.xml"
    sed -i "s/MODULE_DISPLAY_NAME/$(module_name_to_package_suffix "$module_name" | tr '_' ' ' | sed 's/\b\(.\)/\U\1/g')/g" "${REPO_ROOT}/${module_name}/pom.xml"
    sed -i "s|MODULE_DESCRIPTION|${description}|g" "${REPO_ROOT}/${module_name}/pom.xml"
}

# ── Generate package-info.java ─────────────────────────────────────────────
generate_package_info() {
    local module_name="$1"
    local description="$2"
    local suffix=$(module_name_to_package_suffix "$module_name")
    local base_package="${YAWL_BASE_PACKAGE}.${suffix}"

    local package_info_dir="${REPO_ROOT}/${module_name}/src/main/java/org/yawlfoundation/yawl/${suffix}"
    mkdir -p "$package_info_dir"

    cat > "${package_info_dir}/package-info.java" << PKGINFOEOF
${LICENSE_HEADER}

/**
 * ${description}
 *
 * This module provides functionality for:
 * - TODO: Add specific capabilities here
 * - TODO: Document key classes and their roles
 * - TODO: Reference any architectural patterns or interfaces used
 *
 * Example usage:
 * <pre>
 * // TODO: Add example code demonstrating typical use case
 * </pre>
 *
 * @see org.yawlfoundation.yawl.engine.YEngine
 * @see org.yawlfoundation.yawl.elements.YSpecification
 */
package ${base_package};
PKGINFOEOF
}

# ── Generate stub test class ───────────────────────────────────────────────
generate_test_class() {
    local module_name="$1"
    local suffix=$(module_name_to_package_suffix "$module_name")
    local base_package="${YAWL_BASE_PACKAGE}.${suffix}"

    local test_dir="${REPO_ROOT}/${module_name}/src/test/java/org/yawlfoundation/yawl/${suffix}"
    mkdir -p "$test_dir"

    cat > "${test_dir}/ModuleTest.java" << TESTEOF
${LICENSE_HEADER}

package ${base_package};

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@DisplayName("Module tests for ${suffix}")
class ModuleTest {

    @Test
    @DisplayName("Module should initialize without errors")
    void testModuleInitialization() {
        // TODO: Replace with real test
        assertThat(ModuleTest.class.getPackageName(), notNullValue());
    }
}
TESTEOF
}

# ── Add module to parent pom.xml ───────────────────────────────────────────
add_to_parent_pom() {
    local module_name="$1"
    local parent_pom="${REPO_ROOT}/pom.xml"

    if [[ ! -f "$parent_pom" ]]; then
        error "Parent pom.xml not found at ${parent_pom}"
    fi

    # Check if module already listed
    if grep -q "<module>${module_name}</module>" "$parent_pom"; then
        warn "Module already listed in parent pom.xml (skipping)"
        return
    fi

    # Find </modules> closing tag and insert before it
    # Using a temp file for safety
    local temp_file="${parent_pom}.tmp"

    awk "
        /<\/modules>/ && !done {
            print \"        <module>${module_name}</module>\"
            done=1
        }
        { print }
    " "$parent_pom" > "$temp_file"

    if grep -q "${module_name}" "$temp_file"; then
        mv "$temp_file" "$parent_pom"
        info "Added module to parent pom.xml"
    else
        rm -f "$temp_file"
        error "Failed to add module to parent pom.xml"
    fi
}

# ── Generate manual integration checklist ──────────────────────────────────
print_checklist() {
    local module_name="$1"
    local module_path="${REPO_ROOT}/${module_name}"

    cat << CHECKLISTEOF

${module_name} scaffolding COMPLETE!

Module location: ${module_path}
Base package: ${YAWL_BASE_PACKAGE}.$(module_name_to_package_suffix "$module_name")

Next steps:

  1. REVIEW GENERATED FILES
     - Read: ${module_name}/pom.xml (verify parent POM reference)
     - Read: src/main/java/.../package-info.java (update documentation)
     - Read: src/test/java/.../ModuleTest.java (understand test structure)

  2. ADD DEPENDENCIES (if needed)
     Edit: ${module_name}/pom.xml
     - Add domain-specific dependencies (e.g., database, HTTP client, event bus)
     - Keep versions inherited from parent <dependencyManagement>
     - NEVER redeclare versions in child modules

  3. IMPLEMENT YOUR MODULE
     Create: src/main/java/.../YourClassName.java
     - Follow Java 25 conventions (records, sealed classes, virtual threads)
     - All public methods must be real implementations (no stubs/mocks)
     - Reference CLAUDE.md and .claude/rules/ for coding standards

  4. WRITE TESTS
     Create: src/test/java/.../YourClassTest.java
     - Extend ModuleTest.java with actual test cases
     - Use JUnit 5 (Jupiter) with @Test, @DisplayName
     - Target 80% line coverage, 70% branch coverage (JaCoCo)

  5. INTEGRATION POINTS
     IF your module integrates with:
     - Engine: add dependency on yawl-engine, implement YEngine interfaces
     - ResourceService: add dependency on yawl-resourcing
     - Workflow patterns: read .claude/rules/engine/workflow-patterns.md
     - MCP/A2A: add dependency on yawl-integration, read MCP conventions
     - Monitoring: add dependency on yawl-monitoring

  6. BUILD & TEST
     bash scripts/dx.sh compile           # Compile (fast feedback)
     bash scripts/dx.sh -pl ${module_name} # Test just this module
     bash scripts/dx.sh all               # Full build before commit

  7. VERSION CONTROL
     git add ${module_name}/
     git commit -m "feat: scaffold ${module_name} module

     - Generated Maven structure with parent POM integration
     - Added package-info.java with YAWL license header
     - Skeleton test class (ModuleTest.java)

     Session: <insert Claude session URL>"

  8. DOCUMENTATION
     Create: ${module_name}/README.md (optional but recommended)
     - Brief module purpose and scope
     - Architecture diagram or ASCII art
     - Key classes and their relationships
     - Integration examples

RESOURCES:
  - Parent POM: ${REPO_ROOT}/pom.xml (view to understand dependency management)
  - Java 25 Rules: .claude/rules/java25/modern-java.md
  - Engine Rules: .claude/rules/engine/interfaces.md
  - Build System: .claude/rules/build/dx-workflow.md
  - Testing: .claude/rules/testing/chicago-tdd.md

CHECKLISTEOF
}

# ============================================================================
# MAIN LOGIC
# ============================================================================

# Parse arguments
if [[ $# -eq 0 ]]; then
    error "Missing MODULE_NAME argument. Use -h for help."
fi

case "$1" in
    -h|--help)
        print_help
        exit 0
        ;;
    *)
        MODULE_NAME="$1"
        DESCRIPTION="${2:-}"
        ;;
esac

# Validate
validate_module_name "$MODULE_NAME"

# Generate description if not provided
DESCRIPTION=$(generate_description "$MODULE_NAME" "$DESCRIPTION")

info "Scaffolding module: ${MODULE_NAME}"
info "Description: ${DESCRIPTION}"
info ""

# Create directory structure
info "Creating directory structure..."
mkdir -p "${REPO_ROOT}/${MODULE_NAME}/src/main/java/org/yawlfoundation/yawl/$(module_name_to_package_suffix "$MODULE_NAME")"
mkdir -p "${REPO_ROOT}/${MODULE_NAME}/src/test/java/org/yawlfoundation/yawl/$(module_name_to_package_suffix "$MODULE_NAME")"
mkdir -p "${REPO_ROOT}/${MODULE_NAME}/src/main/resources"
mkdir -p "${REPO_ROOT}/${MODULE_NAME}/src/test/resources"

# Generate POM
info "Generating pom.xml..."
generate_pom_xml "$MODULE_NAME" "$DESCRIPTION"

# Generate package-info.java
info "Generating package-info.java..."
generate_package_info "$MODULE_NAME" "$DESCRIPTION"

# Generate test class
info "Generating test skeleton..."
generate_test_class "$MODULE_NAME"

# Add to parent POM (unless disabled)
if [[ "${NO_PARENT_POM:-0}" != "1" ]]; then
    info "Adding module to parent pom.xml..."
    add_to_parent_pom "$MODULE_NAME"
else
    warn "Skipped parent POM modification (NO_PARENT_POM=1)"
    warn "Remember to manually add: <module>${MODULE_NAME}</module> to pom.xml"
fi

# Create .gitkeep files to preserve directory structure
touch "${REPO_ROOT}/${MODULE_NAME}/src/main/resources/.gitkeep"
touch "${REPO_ROOT}/${MODULE_NAME}/src/test/resources/.gitkeep"

success "Module scaffolding complete in $(date +%s%N | tail -c 4)ms"
echo ""
print_checklist "$MODULE_NAME"
