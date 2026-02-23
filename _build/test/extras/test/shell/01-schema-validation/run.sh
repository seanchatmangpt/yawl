#!/bin/bash
#
# Phase 01: Schema Validation
#
# Validates all XML specifications against XSD schemas.
# Uses xmllint for schema validation.
#
# Exit codes:
#   0 - All schemas valid
#   1 - Schema validation failures

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
SCHEMA_DIR="$PROJECT_DIR/schema"
SPECS_DIR="$PROJECT_DIR/exampleSpecs"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

echo "==========================================="
echo "Phase 01: Schema Validation"
echo "==========================================="
echo ""
echo "Schema directory: $SCHEMA_DIR"
echo "Specs directory:  $SPECS_DIR"
echo ""

# Check xmllint availability
if ! command -v xmllint &>/dev/null; then
    echo -e "${RED}ERROR: xmllint not found${NC}"
    echo "Install with: brew install libxml2 (macOS) or apt-get install libxml2-utils (Linux)"
    exit 1
fi

# Test: Schema files exist
echo "--- Test: Schema Files Exist ---"
TESTS_RUN=$((TESTS_RUN + 1))

SCHEMA_FILES=(
    "YAWL_Schema4.0.xsd"
    "YAWL_Schema3.0.xsd"
    "YAWL_Schema2.2.xsd"
    "YAWL_Schema2.1.xsd"
    "YAWL_Schema2.0.xsd"
    "YAWL_Schema.xsd"
)

schema_missing=false
for schema in "${SCHEMA_FILES[@]}"; do
    if [ -f "$SCHEMA_DIR/$schema" ]; then
        echo -e "  ${GREEN}✓${NC} Found: $schema"
    else
        echo -e "  ${RED}✗${NC} Missing: $schema"
        schema_missing=true
    fi
done

if [ "$schema_missing" = "false" ]; then
    echo -e "${GREEN}PASSED: All schema files exist${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAILED: Some schema files missing${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: Schema files are well-formed XML
echo "--- Test: Schema Files Well-Formed ---"
TESTS_RUN=$((TESTS_RUN + 1))

schema_wellformed=true
for schema in "$SCHEMA_DIR"/*.xsd; do
    [ -f "$schema" ] || continue
    schema_name=$(basename "$schema")

    if xmllint --noout "$schema" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} Well-formed: $schema_name"
    else
        echo -e "  ${RED}✗${NC} Malformed: $schema_name"
        schema_wellformed=false
    fi
done

if [ "$schema_wellformed" = "true" ]; then
    echo -e "${GREEN}PASSED: All schema files are well-formed${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAILED: Some schema files are malformed${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: Example specifications validate against latest schema
echo "--- Test: Example Specifications Validate ---"
TESTS_RUN=$((TESTS_RUN + 1))

LATEST_SCHEMA="$SCHEMA_DIR/YAWL_Schema4.0.xsd"
specs_valid=true
specs_checked=0

validate_spec() {
    local spec="$1"
    local spec_name=$(basename "$spec")

    if xmllint --noout --schema "$LATEST_SCHEMA" "$spec" 2>/dev/null; then
        echo -e "  ${GREEN}✓${NC} Valid: $spec_name"
        return 0
    else
        echo -e "  ${RED}✗${NC} Invalid: $spec_name"
        return 1
    fi
}

# Validate XML files in exampleSpecs directories
for dir in "$SPECS_DIR"/*/; do
    [ -d "$dir" ] || continue

    for spec in "$dir"/*.xml; do
        [ -f "$spec" ] || continue
        specs_checked=$((specs_checked + 1))

        if ! validate_spec "$spec"; then
            specs_valid=false
        fi
    done
done

# Also check for XML files directly in xml subdirectory
if [ -d "$SPECS_DIR/xml" ]; then
    for spec in "$SPECS_DIR/xml"/*.xml; do
        [ -f "$spec" ] || continue
        specs_checked=$((specs_checked + 1))

        if ! validate_spec "$spec"; then
            specs_valid=false
        fi
    done
fi

if [ "$specs_checked" -eq 0 ]; then
    echo -e "${YELLOW}WARNING: No XML specifications found to validate${NC}"
fi

if [ "$specs_valid" = "true" ]; then
    echo -e "${GREEN}PASSED: All $specs_checked specifications are valid${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAILED: Some specifications failed validation${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: Schema version compatibility
echo "--- Test: Schema Version Compatibility ---"
TESTS_RUN=$((TESTS_RUN + 1))

# Check that older schema versions still exist for backwards compatibility
compat_ok=true
for version in "2.0" "2.1" "2.2" "3.0"; do
    if [ -f "$SCHEMA_DIR/YAWL_Schema${version}.xsd" ]; then
        echo -e "  ${GREEN}✓${NC} Version $version available"
    else
        echo -e "  ${YELLOW}?${NC} Version $version not found (optional for backwards compat)"
    fi
done

if [ -f "$SCHEMA_DIR/YAWL_Schema4.0.xsd" ]; then
    echo -e "  ${GREEN}✓${NC} Latest version 4.0 available"
else
    echo -e "  ${RED}✗${NC} Latest version 4.0 missing"
    compat_ok=false
fi

if [ "$compat_ok" = "true" ]; then
    echo -e "${GREEN}PASSED: Schema version compatibility OK${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAILED: Schema version compatibility issues${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Summary
echo "==========================================="
echo "Schema Validation Summary"
echo "==========================================="
echo "Tests run:    $TESTS_RUN"
echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests failed: ${RED}$TESTS_FAILED${NC}"
echo "==========================================="

if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Phase 01 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 01 PASSED${NC}"
exit 0
