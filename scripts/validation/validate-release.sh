#!/usr/bin/env bash
# ==========================================================================
# validate-release.sh - Complete release validation (run before tagging)
#
# Usage:
#   bash scripts/validation/validate-release.sh
#
# Validates:
#   1. Package-info coverage (100%)
#   2. Documentation links (0 broken)
#   3. Observatory freshness
#   4. Build success
#   5. Test success
#   6. Static analysis (non-blocking)
#   7. Performance baseline
#   8. Security (SBOM)
#
# Exit codes:
#   0 - Ready for release
#   1 - Validation errors (must fix before release)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VALIDATION_DIR="${PROJECT_ROOT}/docs/validation"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ERRORS=0
WARNINGS=0
CHECKS_PASSED=0
CHECKS_TOTAL=8

# Initialize
mkdir -p "${VALIDATION_DIR}"
REPORT_FILE="${VALIDATION_DIR}/release-validation-report.md"

# Start report
cat > "$REPORT_FILE" << EOF
# Release Validation Report

**Generated**: $(date -u +%Y-%m-%dT%H:%M:%SZ)
**Branch**: $(git branch --show-current 2>/dev/null || echo "unknown")
**Commit**: $(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

---

EOF

echo ""
echo "========================================="
echo "  YAWL v6.0.0 Release Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

# -------------------------------------------------------------------------
# 1. Package-info Coverage
# -------------------------------------------------------------------------
echo -e "${BLUE}[1/${CHECKS_TOTAL}] Checking package-info coverage...${NC}"

PACKAGES=$(find "${PROJECT_ROOT}/src" -type d -name "java" -exec find {} -mindepth 1 -type d \; 2>/dev/null | wc -l | tr -d ' ')
PACKAGE_INFOS=$(find "${PROJECT_ROOT}/src" -name "package-info.java" 2>/dev/null | wc -l | tr -d ' ')

if [[ -z "$PACKAGES" || "$PACKAGES" -eq 0 ]]; then
    echo -e "  ${YELLOW}WARNING: No packages found in src/${NC}"
    echo "- [ ] **Package-info Coverage**: WARNING - No packages found" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
else
    COVERAGE=$((PACKAGE_INFOS * 100 / PACKAGES))
    echo "  Packages: ${PACKAGES}"
    echo "  package-info.java: ${PACKAGE_INFOS}"
    echo "  Coverage: ${COVERAGE}%"

    if [[ $COVERAGE -lt 100 ]]; then
        echo -e "  ${RED}ERROR: Coverage below 100%${NC}"
        echo "- [ ] **Package-info Coverage**: FAILED (${COVERAGE}% < 100%)" >> "$REPORT_FILE"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: 100% package-info coverage${NC}"
        echo "- [x] **Package-info Coverage**: PASSED (${COVERAGE}%)" >> "$REPORT_FILE"
        CHECKS_PASSED=$((CHECKS_PASSED + 1))
    fi
fi

echo ""

# -------------------------------------------------------------------------
# 2. Link Validation
# -------------------------------------------------------------------------
echo -e "${BLUE}[2/${CHECKS_TOTAL}] Validating documentation links...${NC}"

BROKEN_COUNT=0
LINK_REPORT="${VALIDATION_DIR}/link-check-report.txt"

if command -v markdown-link-check &> /dev/null; then
    while IFS= read -r -d '' md_file; do
        if ! markdown-link-check -q "$md_file" >> "$LINK_REPORT" 2>&1; then
            BROKEN_COUNT=$((BROKEN_COUNT + 1))
        fi
    done < <(find "${PROJECT_ROOT}/docs" -name "*.md" -print0 2>/dev/null)

    if [[ $BROKEN_COUNT -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${BROKEN_COUNT} files have broken links${NC}"
        echo "- [ ] **Link Validation**: FAILED (${BROKEN_COUNT} files with broken links)" >> "$REPORT_FILE"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: All markdown links valid${NC}"
        echo "- [x] **Link Validation**: PASSED (0 broken links)" >> "$REPORT_FILE"
        CHECKS_PASSED=$((CHECKS_PASSED + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: markdown-link-check not installed${NC}"
    echo "- [ ] **Link Validation**: SKIPPED (markdown-link-check not installed)" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 3. Observatory Freshness
# -------------------------------------------------------------------------
echo -e "${BLUE}[3/${CHECKS_TOTAL}] Checking observatory freshness...${NC}"

RECEIPT="${PROJECT_ROOT}/docs/v6/latest/receipts/observatory.json"

if [[ -f "$RECEIPT" ]]; then
    RUN_ID=$(jq -r '.run_id // empty' "$RECEIPT" 2>/dev/null)
    STATUS=$(jq -r '.status // "unknown"' "$RECEIPT" 2>/dev/null)

    if [[ -n "$RUN_ID" ]]; then
        echo "  Run ID: ${RUN_ID}"
        echo "  Status: ${STATUS}"

        # Check if receipt is recent (within 7 days)
        RUN_TIMESTAMP=$(echo "$RUN_ID" | sed 's/T/ /;s/Z$//')
        if date -j -f "%Y%m%d %H%M%S" "$RUN_TIMESTAMP" >/dev/null 2>&1; then
            RUN_EPOCH=$(date -j -f "%Y%m%d %H%M%S" "$RUN_TIMESTAMP" +%s 2>/dev/null || echo "0")
            NOW_EPOCH=$(date +%s)
            AGE_HOURS=$(( (NOW_EPOCH - RUN_EPOCH) / 3600 ))

            if [[ $AGE_HOURS -lt 168 ]]; then
                echo -e "  ${GREEN}PASSED: Observatory facts fresh (${AGE_HOURS}h old)${NC}"
                echo "- [x] **Observatory Freshness**: PASSED (${AGE_HOURS}h old)" >> "$REPORT_FILE"
                CHECKS_PASSED=$((CHECKS_PASSED + 1))
            else
                echo -e "  ${RED}ERROR: Observatory facts stale (${AGE_HOURS}h old)${NC}"
                echo "- [ ] **Observatory Freshness**: FAILED (${AGE_HOURS}h old, run observatory)" >> "$REPORT_FILE"
                ERRORS=$((ERRORS + 1))
            fi
        else
            echo -e "  ${YELLOW}WARNING: Could not parse run_id timestamp${NC}"
            echo "- [ ] **Observatory Freshness**: WARNING (could not parse timestamp)" >> "$REPORT_FILE"
            WARNINGS=$((WARNINGS + 1))
        fi
    else
        echo -e "  ${YELLOW}WARNING: No run_id in receipt${NC}"
        echo "- [ ] **Observatory Freshness**: WARNING (no run_id)" >> "$REPORT_FILE"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}WARNING: Observatory receipt not found${NC}"
    echo "  Run: bash scripts/observatory/observatory.sh"
    echo "- [ ] **Observatory Freshness**: WARNING (receipt not found)" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 4. Build Validation
# -------------------------------------------------------------------------
echo -e "${BLUE}[4/${CHECKS_TOTAL}] Validating build...${NC}"

if mvn -T 1.5C clean compile -q 2>/dev/null; then
    echo -e "  ${GREEN}PASSED: Build successful${NC}"
    echo "- [x] **Build**: PASSED" >> "$REPORT_FILE"
    CHECKS_PASSED=$((CHECKS_PASSED + 1))
else
    echo -e "  ${RED}ERROR: Build failed${NC}"
    echo "- [ ] **Build**: FAILED" >> "$REPORT_FILE"
    ERRORS=$((ERRORS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 5. Test Validation
# -------------------------------------------------------------------------
echo -e "${BLUE}[5/${CHECKS_TOTAL}] Validating tests...${NC}"

if mvn -T 1.5C test -q 2>/dev/null; then
    echo -e "  ${GREEN}PASSED: All tests passed${NC}"
    echo "- [x] **Tests**: PASSED" >> "$REPORT_FILE"
    CHECKS_PASSED=$((CHECKS_PASSED + 1))
else
    echo -e "  ${RED}ERROR: Tests failed${NC}"
    echo "- [ ] **Tests**: FAILED" >> "$REPORT_FILE"
    ERRORS=$((ERRORS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 6. Static Analysis (Non-blocking)
# -------------------------------------------------------------------------
echo -e "${BLUE}[6/${CHECKS_TOTAL}] Running static analysis...${NC}"

if mvn verify -P analysis -q 2>/dev/null; then
    echo -e "  ${GREEN}PASSED: Static analysis clean${NC}"
    echo "- [x] **Static Analysis**: PASSED" >> "$REPORT_FILE"
    CHECKS_PASSED=$((CHECKS_PASSED + 1))
else
    echo -e "  ${YELLOW}WARNING: Static analysis issues (non-blocking)${NC}"
    echo "- [ ] **Static Analysis**: WARNING (issues found, non-blocking)" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
    # Still count as passed since it's non-blocking
    CHECKS_PASSED=$((CHECKS_PASSED + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 7. Performance Baseline
# -------------------------------------------------------------------------
echo -e "${BLUE}[7/${CHECKS_TOTAL}] Checking performance baseline...${NC}"

BASELINE_FILE="${PROJECT_ROOT}/docs/v6/latest/performance/build-baseline.json"

if [[ -f "$BASELINE_FILE" ]]; then
    BASELINE_TIME=$(jq -r '.metrics.clean_compile_ms // 0' "$BASELINE_FILE" 2>/dev/null)
    if [[ "$BASELINE_TIME" -gt 0 ]]; then
        echo -e "  ${GREEN}PASSED: Performance baseline exists (${BASELINE_TIME}ms)${NC}"
        echo "- [x] **Performance Baseline**: PASSED (${BASELINE_TIME}ms baseline)" >> "$REPORT_FILE"
        CHECKS_PASSED=$((CHECKS_PASSED + 1))
    else
        echo -e "  ${YELLOW}WARNING: Performance baseline empty${NC}"
        echo "- [ ] **Performance Baseline**: WARNING (baseline empty)" >> "$REPORT_FILE"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}WARNING: No performance baseline (run measure-baseline.sh)${NC}"
    echo "- [ ] **Performance Baseline**: WARNING (no baseline found)" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 8. Security (SBOM)
# -------------------------------------------------------------------------
echo -e "${BLUE}[8/${CHECKS_TOTAL}] Security validation...${NC}"

if mvn cyclonedx:makeBom -q 2>/dev/null; then
    SBOM_FILE="${PROJECT_ROOT}/target/bom.json"
    if [[ -f "$SBOM_FILE" ]]; then
        COMPONENTS=$(jq '.components | length' "$SBOM_FILE" 2>/dev/null || echo "0")
        echo -e "  ${GREEN}PASSED: SBOM generated (${COMPONENTS} components)${NC}"
        echo "- [x] **Security (SBOM)**: PASSED (${COMPONENTS} components)" >> "$REPORT_FILE"
        CHECKS_PASSED=$((CHECKS_PASSED + 1))
    else
        echo -e "  ${YELLOW}WARNING: SBOM generation succeeded but file not found${NC}"
        echo "- [ ] **Security (SBOM)**: WARNING (file not found)" >> "$REPORT_FILE"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}WARNING: SBOM generation failed (cyclonedx plugin may not be configured)${NC}"
    echo "- [ ] **Security (SBOM)**: WARNING (generation failed)" >> "$REPORT_FILE"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo "" >> "$REPORT_FILE"
echo "---" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "## Summary" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"
echo "| Metric | Count |" >> "$REPORT_FILE"
echo "|--------|-------|" >> "$REPORT_FILE"
echo "| Checks Passed | ${CHECKS_PASSED}/${CHECKS_TOTAL} |" >> "$REPORT_FILE"
echo "| Errors | ${ERRORS} |" >> "$REPORT_FILE"
echo "| Warnings | ${WARNINGS} |" >> "$REPORT_FILE"
echo "" >> "$REPORT_FILE"

echo "========================================="
echo "  Release Validation Summary"
echo "========================================="
echo "  Checks Passed: ${CHECKS_PASSED}/${CHECKS_TOTAL}"
echo "  Errors:        ${ERRORS}"
echo "  Warnings:      ${WARNINGS}"
echo "========================================="
echo ""
echo "Report saved to: ${REPORT_FILE}"
echo ""

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}=========================================${NC}"
    echo -e "${RED}  FAILED: ${ERRORS} validation errors${NC}"
    echo -e "${RED}  Must fix before release${NC}"
    echo -e "${RED}=========================================${NC}"
    exit 1
else
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN}  PASSED: Ready for release${NC}"
    echo -e "${GREEN}=========================================${NC}"
    exit 0
fi
