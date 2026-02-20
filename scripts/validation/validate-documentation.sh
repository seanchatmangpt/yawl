#!/usr/bin/env bash
# ==========================================================================
# validate-documentation.sh - Validates all documentation
#
# Usage:
#   bash scripts/validation/validate-documentation.sh
#
# Validates:
#   1. Package-info.java coverage (target: 100%)
#   2. Markdown link integrity
#   3. XSD schema validity
#   4. Observatory receipt verification
#
# Exit codes:
#   0 - All validations passed
#   1 - One or more validations failed
#   2 - Warnings only (no failures)
#
# Output:
#   docs/validation/validation-report.json - JSON results
#   docs/validation/validation-report.xml  - JUnit XML (if requested)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VALIDATION_DIR="${PROJECT_ROOT}/docs/validation"

# Source output aggregation library
source "${SCRIPT_DIR}/lib/output-aggregation.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

# Initialize aggregation
agg_init "documentation" "${VALIDATION_DIR}"

# Initialize validation directory
mkdir -p "${VALIDATION_DIR}"

echo ""
echo "========================================="
echo "  Documentation Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

# -------------------------------------------------------------------------
# 1. Package-info Coverage
# -------------------------------------------------------------------------
echo "[1/4] Checking package-info.java coverage..."

CHECK_START=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))

PACKAGES=$(find "${PROJECT_ROOT}/src" -type d -name "java" -exec find {} -mindepth 1 -type d \; 2>/dev/null | wc -l | tr -d ' ')
PACKAGE_INFOS=$(find "${PROJECT_ROOT}/src" -name "package-info.java" 2>/dev/null | wc -l | tr -d ' ')

CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
CHECK_DURATION=$((CHECK_END - CHECK_START))

if [[ -z "$PACKAGES" || "$PACKAGES" -eq 0 ]]; then
    echo -e "  ${YELLOW}WARNING: No packages found in src/${NC}"
    WARNINGS=$((WARNINGS + 1))
    agg_add_result "package-info-coverage" "WARN" "No packages found in src/" "${CHECK_DURATION}"
else
    COVERAGE=$((PACKAGE_INFOS * 100 / PACKAGES))
    echo "  Packages found: ${PACKAGES}"
    echo "  package-info.java files: ${PACKAGE_INFOS}"
    echo "  Coverage: ${COVERAGE}%"

    if [[ $COVERAGE -lt 100 ]]; then
        echo -e "  ${RED}ERROR: Coverage below 100%${NC}"
        ERRORS=$((ERRORS + 1))
        agg_add_result "package-info-coverage" "FAIL" "Coverage ${COVERAGE}% < 100%" "${CHECK_DURATION}"
    else
        echo -e "  ${GREEN}PASSED: 100% package-info coverage${NC}"
        agg_add_result "package-info-coverage" "PASS" "100% coverage (${PACKAGE_INFOS} files)" "${CHECK_DURATION}"
    fi
fi

echo ""

# -------------------------------------------------------------------------
# 2. Markdown Link Validation
# -------------------------------------------------------------------------
echo "[2/4] Validating markdown links..."

CHECK_START=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))

LINK_REPORT="${VALIDATION_DIR}/link-check-report.txt"
BROKEN_COUNT=0

# Check if markdown-link-check is available
if command -v markdown-link-check &> /dev/null; then
    echo "  Checking all markdown files..."

    # Find all markdown files and check links
    while IFS= read -r -d '' md_file; do
        if ! markdown-link-check -q "$md_file" >> "$LINK_REPORT" 2>&1; then
            BROKEN_COUNT=$((BROKEN_COUNT + 1))
            echo "    ${RED}BROKEN: ${md_file}${NC}"
        fi
    done < <(find "${PROJECT_ROOT}/docs" -name "*.md" -print0 2>/dev/null)

    CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    CHECK_DURATION=$((CHECK_END - CHECK_START))

    if [[ $BROKEN_COUNT -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${BROKEN_COUNT} files have broken links${NC}"
        echo "  See: ${LINK_REPORT}"
        ERRORS=$((ERRORS + 1))
        agg_add_result "markdown-links" "FAIL" "${BROKEN_COUNT} files have broken links" "${CHECK_DURATION}"
    else
        echo -e "  ${GREEN}PASSED: All markdown links valid${NC}"
        agg_add_result "markdown-links" "PASS" "All links valid" "${CHECK_DURATION}"
    fi
else
    CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    CHECK_DURATION=$((CHECK_END - CHECK_START))
    echo -e "  ${YELLOW}SKIPPED: markdown-link-check not installed${NC}"
    echo "  Install: npm install -g markdown-link-check"
    WARNINGS=$((WARNINGS + 1))
    agg_add_result "markdown-links" "SKIP" "markdown-link-check not installed" "${CHECK_DURATION}"
fi

echo ""

# -------------------------------------------------------------------------
# 3. XSD Schema Validation
# -------------------------------------------------------------------------
echo "[3/4] Validating XSD schemas..."

CHECK_START=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))

SCHEMA_DIR="${PROJECT_ROOT}/schema"
SCHEMA_ERRORS=0

if [[ -d "$SCHEMA_DIR" ]]; then
    for xsd_file in "${SCHEMA_DIR}"/*.xsd; do
        if [[ -f "$xsd_file" ]]; then
            if xmllint --schema "$xsd_file" "$xsd_file" --noout 2>/dev/null; then
                echo -e "  ${GREEN}VALID: $(basename "$xsd_file")${NC}"
            else
                echo -e "  ${RED}INVALID: $(basename "$xsd_file")${NC}"
                SCHEMA_ERRORS=$((SCHEMA_ERRORS + 1))
            fi
        fi
    done

    CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    CHECK_DURATION=$((CHECK_END - CHECK_START))

    if [[ $SCHEMA_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${SCHEMA_ERRORS} schema validation failures${NC}"
        ERRORS=$((ERRORS + 1))
        agg_add_result "xsd-schemas" "FAIL" "${SCHEMA_ERRORS} schema validation failures" "${CHECK_DURATION}"
    else
        echo -e "  ${GREEN}PASSED: All XSD schemas valid${NC}"
        agg_add_result "xsd-schemas" "PASS" "All schemas valid" "${CHECK_DURATION}"
    fi
else
    CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    CHECK_DURATION=$((CHECK_END - CHECK_START))
    echo -e "  ${YELLOW}SKIPPED: No schema directory found${NC}"
    WARNINGS=$((WARNINGS + 1))
    agg_add_result "xsd-schemas" "SKIP" "No schema directory found" "${CHECK_DURATION}"
fi

echo ""

# -------------------------------------------------------------------------
# 4. Observatory Receipt Verification
# -------------------------------------------------------------------------
echo "[4/4] Verifying observatory receipt..."

CHECK_START=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))

RECEIPT="${PROJECT_ROOT}/docs/v6/latest/receipts/observatory.json"
INDEX_FILE="${PROJECT_ROOT}/docs/v6/latest/INDEX.md"

if [[ -f "$RECEIPT" && -f "$INDEX_FILE" ]]; then
    EXPECTED=$(jq -r '.outputs.index_sha256 // empty' "$RECEIPT" 2>/dev/null || echo "")

    if [[ -n "$EXPECTED" ]]; then
        # Calculate actual SHA256
        if command -v sha256sum &> /dev/null; then
            ACTUAL="sha256:$(sha256sum "$INDEX_FILE" | cut -d' ' -f1)"
        elif command -v shasum &> /dev/null; then
            ACTUAL="sha256:$(shasum -a 256 "$INDEX_FILE" | cut -d' ' -f1)"
        else
            ACTUAL=""
        fi

        CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
        CHECK_DURATION=$((CHECK_END - CHECK_START))

        if [[ -n "$ACTUAL" ]]; then
            if [[ "$EXPECTED" == "$ACTUAL" ]]; then
                echo -e "  ${GREEN}PASSED: Observatory receipt valid${NC}"
                agg_add_result "observatory-receipt" "PASS" "Receipt hash verified" "${CHECK_DURATION}"
            else
                echo -e "  ${RED}ERROR: Observatory receipt mismatch${NC}"
                echo "    Expected: ${EXPECTED}"
                echo "    Actual:   ${ACTUAL}"
                echo "    Run: bash scripts/observatory/observatory.sh"
                ERRORS=$((ERRORS + 1))
                agg_add_result "observatory-receipt" "FAIL" "Receipt hash mismatch" "${CHECK_DURATION}"
            fi
        else
            echo -e "  ${YELLOW}SKIPPED: No SHA256 tool available${NC}"
            WARNINGS=$((WARNINGS + 1))
            agg_add_result "observatory-receipt" "SKIP" "No SHA256 tool available" "${CHECK_DURATION}"
        fi
    else
        CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
        CHECK_DURATION=$((CHECK_END - CHECK_START))
        echo -e "  ${YELLOW}WARNING: Receipt missing index_sha256${NC}"
        WARNINGS=$((WARNINGS + 1))
        agg_add_result "observatory-receipt" "WARN" "Receipt missing index_sha256" "${CHECK_DURATION}"
    fi
else
    CHECK_END=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    CHECK_DURATION=$((CHECK_END - CHECK_START))
    echo -e "  ${YELLOW}SKIPPED: Observatory receipt or INDEX.md not found${NC}"
    echo "    Run: bash scripts/observatory/observatory.sh"
    WARNINGS=$((WARNINGS + 1))
    agg_add_result "observatory-receipt" "SKIP" "Receipt or INDEX.md not found" "${CHECK_DURATION}"
fi

echo ""

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo "========================================="
echo "  Validation Summary"
echo "========================================="
echo "  Errors:   ${ERRORS}"
echo "  Warnings: ${WARNINGS}"
echo "========================================="
echo ""

# Generate JSON report
cat > "${VALIDATION_DIR}/validation-report.json" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "errors": ${ERRORS},
  "warnings": ${WARNINGS},
  "checks": {
    "package_info_coverage": {
      "packages": ${PACKAGES:-0},
      "package_info_files": ${PACKAGE_INFOS:-0},
      "coverage_pct": ${COVERAGE:-0}
    },
    "markdown_links": {
      "broken_count": ${BROKEN_COUNT}
    },
    "xsd_schemas": {
      "validation_errors": ${SCHEMA_ERRORS:-0}
    },
    "observatory_receipt": {
      "verified": $([[ -f "$RECEIPT" && -f "$INDEX_FILE" && "$EXPECTED" == "$ACTUAL" ]] && echo "true" || echo "false")
    }
  }
}
EOF

# Output aggregated results
agg_output_json "${VALIDATION_DIR}/documentation-results.json" > /dev/null
agg_output_junit "${VALIDATION_DIR}/documentation-junit.xml" > /dev/null

echo "Report saved to: ${VALIDATION_DIR}/validation-report.json"
echo "JSON results:    ${VALIDATION_DIR}/documentation-results.json"
echo "JUnit XML:       ${VALIDATION_DIR}/documentation-junit.xml"
echo ""

# Get exit code from aggregation
agg_get_exit_code
exit_code=$?

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}VALIDATION FAILED${NC}"
    exit 1
elif [[ $WARNINGS -gt 0 ]]; then
    echo -e "${YELLOW}VALIDATION PASSED WITH WARNINGS${NC}"
    exit 2
else
    echo -e "${GREEN}VALIDATION PASSED${NC}"
    exit 0
fi
