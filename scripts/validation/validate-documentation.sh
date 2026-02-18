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
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VALIDATION_DIR="${PROJECT_ROOT}/docs/validation"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

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

PACKAGES=$(find "${PROJECT_ROOT}/src" -type d -name "java" -exec find {} -mindepth 1 -type d \; 2>/dev/null | wc -l | tr -d ' ')
PACKAGE_INFOS=$(find "${PROJECT_ROOT}/src" -name "package-info.java" 2>/dev/null | wc -l | tr -d ' ')

if [[ -z "$PACKAGES" || "$PACKAGES" -eq 0 ]]; then
    echo -e "  ${YELLOW}WARNING: No packages found in src/${NC}"
    WARNINGS=$((WARNINGS + 1))
else
    COVERAGE=$((PACKAGE_INFOS * 100 / PACKAGES))
    echo "  Packages found: ${PACKAGES}"
    echo "  package-info.java files: ${PACKAGE_INFOS}"
    echo "  Coverage: ${COVERAGE}%"

    if [[ $COVERAGE -lt 100 ]]; then
        echo -e "  ${RED}ERROR: Coverage below 100%${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: 100% package-info coverage${NC}"
    fi
fi

echo ""

# -------------------------------------------------------------------------
# 2. Markdown Link Validation
# -------------------------------------------------------------------------
echo "[2/4] Validating markdown links..."

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

    if [[ $BROKEN_COUNT -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${BROKEN_COUNT} files have broken links${NC}"
        echo "  See: ${LINK_REPORT}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: All markdown links valid${NC}"
    fi
else
    echo -e "  ${YELLOW}SKIPPED: markdown-link-check not installed${NC}"
    echo "  Install: npm install -g markdown-link-check"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 3. XSD Schema Validation
# -------------------------------------------------------------------------
echo "[3/4] Validating XSD schemas..."

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

    if [[ $SCHEMA_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${SCHEMA_ERRORS} schema validation failures${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: All XSD schemas valid${NC}"
    fi
else
    echo -e "  ${YELLOW}SKIPPED: No schema directory found${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 4. Observatory Receipt Verification
# -------------------------------------------------------------------------
echo "[4/4] Verifying observatory receipt..."

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

        if [[ -n "$ACTUAL" ]]; then
            if [[ "$EXPECTED" == "$ACTUAL" ]]; then
                echo -e "  ${GREEN}PASSED: Observatory receipt valid${NC}"
            else
                echo -e "  ${RED}ERROR: Observatory receipt mismatch${NC}"
                echo "    Expected: ${EXPECTED}"
                echo "    Actual:   ${ACTUAL}"
                echo "    Run: bash scripts/observatory/observatory.sh"
                ERRORS=$((ERRORS + 1))
            fi
        else
            echo -e "  ${YELLOW}SKIPPED: No SHA256 tool available${NC}"
            WARNINGS=$((WARNINGS + 1))
        fi
    else
        echo -e "  ${YELLOW}WARNING: Receipt missing index_sha256${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: Observatory receipt or INDEX.md not found${NC}"
    echo "    Run: bash scripts/observatory/observatory.sh"
    WARNINGS=$((WARNINGS + 1))
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

echo "Report saved to: ${VALIDATION_DIR}/validation-report.json"
echo ""

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}VALIDATION FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}VALIDATION PASSED${NC}"
    exit 0
fi
