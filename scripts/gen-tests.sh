#!/usr/bin/env bash
# ==========================================================================
# gen-tests.sh — Generate Chicago TDD Test Skeletons
#
# Auto-generates JUnit 5 test class from Java source file.
# Extracts public methods and creates test stubs with given-when-then structure.
# Enforces YAWL standards: real objects, no mocks, proper assertions.
#
# Usage:
#   ./scripts/gen-tests.sh src/main/java/org/yawl/engine/YEngine.java
#   ./scripts/gen-tests.sh src/main/java/org/yawl/elements/YFlow.java --output custom-test-path
#
# Options:
#   --output PATH    Override default output directory (default: src/test/java)
#   --help           Show this message
#
# Environment:
#   GEN_TESTS_SKIP_CONSTRUCTORS=1   Don't generate tests for constructors
#   GEN_TESTS_MAX_METHODS=30         Skip class if too many public methods
#
# Output:
#   - Generates src/test/java/<same-package>/<ClassName>Test.java
#   - Creates @BeforeEach setup() stub with REAL object initialization
#   - One @Test per public method (given-when-then structure)
#   - Comments link to HYPER_STANDARDS for enforcement awareness
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
OUTPUT_BASE="${GEN_TESTS_OUTPUT_BASE:-src/test/java}"
SKIP_CONSTRUCTORS="${GEN_TESTS_SKIP_CONSTRUCTORS:-0}"
MAX_METHODS="${GEN_TESTS_MAX_METHODS:-30}"
HELP_REQUESTED=0

# ── Parse arguments ───────────────────────────────────────────────────────
SOURCE_FILE=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output)   OUTPUT_BASE="$2"; shift 2 ;;
        --help|-h)  HELP_REQUESTED=1; shift ;;
        --*)        echo "Unknown option: $1"; exit 1 ;;
        *)          SOURCE_FILE="$1"; shift ;;
    esac
done

# ── Show help ─────────────────────────────────────────────────────────────
if [[ $HELP_REQUESTED -eq 1 ]]; then
    sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
    exit 0
fi

# ── Validate input ────────────────────────────────────────────────────────
if [[ -z "$SOURCE_FILE" ]]; then
    echo "Error: Source file required"
    echo "Usage: $0 <source-file> [--output <path>]"
    echo "Try: $0 --help"
    exit 1
fi

if [[ ! -f "$SOURCE_FILE" ]]; then
    echo "Error: File not found: $SOURCE_FILE"
    exit 1
fi

if [[ ! "$SOURCE_FILE" =~ \.java$ ]]; then
    echo "Error: File must be .java: $SOURCE_FILE"
    exit 1
fi

# ── Extract package and class name ────────────────────────────────────────
PACKAGE=$(grep -m1 "^package " "$SOURCE_FILE" | sed 's/^package //;s/;$//')
if [[ -z "$PACKAGE" ]]; then
    echo "Error: Could not find package declaration in $SOURCE_FILE"
    exit 1
fi

CLASS_NAME=$(basename "$SOURCE_FILE" .java)
TEST_CLASS_NAME="${CLASS_NAME}Test"

# ── Compute output path ───────────────────────────────────────────────────
PACKAGE_PATH=$(echo "$PACKAGE" | tr '.' '/')
OUTPUT_DIR="${OUTPUT_BASE}/${PACKAGE_PATH}"
OUTPUT_FILE="${OUTPUT_DIR}/${TEST_CLASS_NAME}.java"

if [[ -f "$OUTPUT_FILE" ]]; then
    echo "⚠ Test class already exists: $OUTPUT_FILE"
    read -p "Overwrite? (y/N) " -r REPLY
    if [[ ! "$REPLY" =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
fi

# ── Extract public methods ────────────────────────────────────────────────
# Uses Python for robust AST-like parsing
# Falls back to grep-based extraction if Python unavailable
extract_methods() {
    local file="$1"

    # Try Python first (more robust)
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$file" <<'PYTHON'
import re
import sys

methods = []
try:
    filepath = sys.argv[1]
    with open(filepath, 'r') as f:
        content = f.read()

    # Remove comments and javadoc
    content = re.sub(r'/\*\*.*?\*/', '', content, flags=re.DOTALL)
    content = re.sub(r'//.*$', '', content, flags=re.MULTILINE)

    # Find public methods: public [modifiers] return-type name(params)
    # Matches: public, public static, public abstract, etc.
    pattern = r'public\s+(?:static\s+)?(?:abstract\s+)?(?:synchronized\s+)?([a-zA-Z_][a-zA-Z0-9_<>?\[\],\s]*?)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)'

    for match in re.finditer(pattern, content):
        return_type = match.group(1).strip()
        method_name = match.group(2)
        params = match.group(3).strip()

        # Skip private/protected (shouldn't happen with public pattern, but safe)
        if 'private' in return_type or 'protected' in return_type:
            continue

        methods.append(f"{return_type} {method_name}|{params}")

    # Remove duplicates and print
    for method in sorted(set(methods)):
        print(method)

except Exception as e:
    print(f"Error parsing {filepath}: {e}", file=sys.stderr)
    sys.exit(1)
PYTHON
        return
    fi

    # Fallback: grep-based extraction
    grep -E "^\s*public\s+.*\(" "$file" | \
        grep -v "^\s*//" | \
        sed 's/^[[:space:]]*//' | \
        while read -r line; do
            # Extract method signature
            if [[ "$line" =~ public[[:space:]]+(abstract[[:space:]]+)?(static[[:space:]]+)?(synchronized[[:space:]]+)?(.*)$ ]]; then
                local signature="${BASH_REMATCH[4]}"
                # Extract return type and method name
                local before_paren="${signature%%(*}"
                local method_name=$(echo "$before_paren" | awk '{print $NF}')
                local return_type=$(echo "$before_paren" | sed 's/ *'$method_name'$//')

                # Extract params
                local params=$(echo "$signature" | sed 's/.*(\([^)]*\).*/\1/')

                echo "$return_type $method_name|$params"
            fi
        done | sort -u
}

METHODS=$(extract_methods "$SOURCE_FILE")
METHOD_COUNT=$(echo "$METHODS" | grep -c "^" || true)

if [[ -z "$METHODS" || "$METHOD_COUNT" -eq 0 ]]; then
    echo "⚠ No public methods found in $SOURCE_FILE"
    exit 0
fi

if [[ $METHOD_COUNT -gt $MAX_METHODS ]]; then
    echo "⚠ Too many public methods ($METHOD_COUNT > $MAX_METHODS)"
    echo "  Increase GEN_TESTS_MAX_METHODS if needed"
    exit 1
fi

# ── Generate test class ───────────────────────────────────────────────────
generate_test_class() {
    local package="$1"
    local class_name="$2"
    local source_file="$3"
    local methods="$4"

    local year=$(date +%Y)
    local year_range="2004-${year}"

    cat <<'EOF'
/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 */

EOF

    echo "package ${package};"
    echo ""
    echo "import org.junit.jupiter.api.BeforeEach;"
    echo "import org.junit.jupiter.api.Test;"
    echo "import org.junit.jupiter.api.DisplayName;"
    echo ""
    echo "import static org.junit.jupiter.api.Assertions.*;"
    echo ""
    echo "/**"
    echo " * Chicago TDD test suite for {@link ${class_name}}."
    echo " *"
    echo " * Test Strategy:"
    echo " *   - Real object initialization in @BeforeEach (no mocks)"
    echo " *   - Given-When-Then structure for each test"
    echo " *   - Happy path, error cases, boundary conditions"
    echo " *   - End-to-end verification with real dependencies"
    echo " *"
    echo " * Enforcement:"
    echo " *   - See .claude/HYPER_STANDARDS.md for coding rules"
    echo " *   - All assertions must verify REAL behavior"
    echo " *   - No @Disabled or mocks permitted"
    echo " * @see ${class_name}"
    echo " */"
    echo "class ${class_name}Test {"
    echo ""
    echo "    private ${class_name} subject;"
    echo ""
    echo "    @BeforeEach"
    echo "    void setup() {"
    echo "        // Real object initialization (no mocks)."
    echo "        // This method must create ACTUAL instances needed for testing."
    echo "        // Remove placeholder and instantiate the real ${class_name}."
    echo "        //"
    echo "        // Example:"
    echo "        //   this.subject = new ${class_name}(realDependency1, realDependency2);"
    echo "        //"
    echo "        // If constructor requires complex setup, see:"
    echo "        //   - Test data: src/test/resources/"
    echo "        //   - Fixtures: src/test/java/.../fixtures/"
    echo "        // - Integration: exampleSpecs/"
    echo "        throw new UnsupportedOperationException("
    echo "            \"Test setup() requires: \\n\" +"
    echo "            \"  1. Identify real dependencies for \" + ${class_name}.class.getSimpleName() + \" \\n\" +"
    echo "            \"  2. Initialize in this.subject field \\n\" +"
    echo "            \"  3. Verify @BeforeEach runs before each @Test \\n\" +"
    echo "            \"\\n\" +"
    echo "            \"Reference: See similar test class for setup pattern\""
    echo "        );"
    echo "    }"
    echo ""

    # Generate test method for each public method
    while IFS='|' read -r signature params; do
        if [[ -z "$signature" ]]; then
            continue
        fi

        # Clean up signature
        signature=$(echo "$signature" | xargs)
        params=$(echo "$params" | xargs)

        # Extract method name (last word in signature)
        local method_name=$(echo "$signature" | awk '{print $NF}')

        # Skip if no method name
        if [[ -z "$method_name" ]]; then
            continue
        fi

        # Generate test method name (camelCase -> pascalCase with "test" prefix)
        local test_name="test$(echo "$method_name" | sed 's/^\(.\)/\U\1/')"

        # Determine if it's a constructor
        if [[ "$method_name" == "$class_name" ]]; then
            test_name="testConstruction"
        fi

        echo "    @Test"
        echo "    @DisplayName(\"${test_name}: verify ${method_name}() behavior\")"
        echo "    void ${test_name}() {"
        echo "        // GIVEN: [describe precondition]"
        echo "        // Example:"
        echo "        //   // subject is initialized in setup()"
        echo "        //   int expectedValue = 42;"
        echo "        //"
        echo "        // WHEN: [describe action]"
        echo "        //   var result = subject.${method_name}(${params});"
        echo "        //"
        echo "        // THEN: [verify outcome]"
        echo "        //   assertEquals(expectedValue, result, \"verify expected behavior\");"
        echo "        //"
        echo "        // See chicago-tdd.md for test pattern guidelines."
        echo "        throw new UnsupportedOperationException("
        echo "            \"Test for ${method_name}() requires: \\n\" +"
        echo "            \"  1. Describe precondition (GIVEN) \\n\" +"
        echo "            \"  2. Call subject.${method_name}(${params}) (WHEN) \\n\" +"
        echo "            \"  3. Assert real outcome (THEN) \\n\" +"
        echo "            \"\\n\" +"
        echo "            \"Enforce: .claude/HYPER_STANDARDS.md - No mocks, no stubs, real behavior\""
        echo "        );"
        echo "    }"
        echo ""
    done <<< "$methods"

    echo "}"
}

# ── Create output directory ───────────────────────────────────────────────
mkdir -p "$OUTPUT_DIR"

# ── Write test class ──────────────────────────────────────────────────────
generate_test_class "$PACKAGE" "$CLASS_NAME" "$SOURCE_FILE" "$METHODS" > "$OUTPUT_FILE"

# ── Report ────────────────────────────────────────────────────────────────
echo "✅ Generated: $OUTPUT_FILE"
echo "   Package: $PACKAGE"
echo "   Test Class: $TEST_CLASS_NAME"
echo "   Methods: $METHOD_COUNT public method(s)"
echo ""
echo "Next steps:"
echo "  1. Edit $OUTPUT_FILE"
echo "  2. Replace setup() with real object initialization"
echo "  3. Implement each test() method with given-when-then structure"
echo "  4. Run: bash scripts/dx.sh test"
echo ""
echo "Standards:"
echo "  - See .claude/HYPER_STANDARDS.md for coding rules"
echo "  - See .claude/rules/testing/chicago-tdd.md for test patterns"
echo "  - Use real objects, never mock/stub/fake"
echo "  - All assertions verify actual behavior"
echo ""
