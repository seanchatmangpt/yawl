#!/bin/bash
# ==========================================================================
# pre-commit-validation.sh - Git pre-commit hook for validation
#
# This hook runs validation checks before allowing a commit to proceed.
# It only validates files that have changed (incremental mode).
#
# Installation:
#   cp scripts/hooks/pre-commit-validation.sh .git/hooks/pre-commit
#   chmod +x .git/hooks/pre-commit
#
# Or use with Husky/pre-commit framework:
#   ln -s ../../scripts/hooks/pre-commit-validation.sh .git/hooks/pre-commit
#
# Bypass:
#   git commit --no-verify
#   SKIP_VALIDATION=1 git commit
#
# Exit codes:
#   0 - Validation passed, commit allowed
#   1 - Validation failed, commit blocked
# ==========================================================================
set -euo pipefail

# Allow bypassing
if [[ "${SKIP_VALIDATION:-0}" == "1" ]]; then
    echo "[pre-commit] Validation skipped (SKIP_VALIDATION=1)"
    exit 0
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo ".")"

# Temporary files for results
TEMP_DIR=$(mktemp -d)
trap "rm -rf ${TEMP_DIR}" EXIT

# Validation results
ERRORS=0
WARNINGS=0

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  Pre-Commit Validation${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# -------------------------------------------------------------------------
# Get changed files
# -------------------------------------------------------------------------
CHANGED_JAVA=$(git diff --cached --name-only --diff-filter=ACMR -- '*.java' 2>/dev/null | head -50 || true)
CHANGED_XML=$(git diff --cached --name-only --diff-filter=ACMR -- '*.xml' '*.xsd' 2>/dev/null | head -50 || true)
CHANGED_MD=$(git diff --cached --name-only --diff-filter=ACMR -- '*.md' 2>/dev/null | head -50 || true)
CHANGED_SH=$(git diff --cached --name-only --diff-filter=ACMR -- '*.sh' 2>/dev/null | head -50 || true)
CHANGED_YML=$(git diff --cached --name-only --diff-filter=ACMR -- '*.yml' '*.yaml' 2>/dev/null | head -50 || true)
CHANGED_POM=$(git diff --cached --name-only --diff-filter=ACMR -- 'pom.xml' '**/pom.xml' 2>/dev/null | head -20 || true)

# -------------------------------------------------------------------------
# Check 1: HYPER_STANDARDS (for Java files)
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_JAVA}" ]]; then
    echo -e "${BLUE}[1/6] Checking HYPER_STANDARDS...${NC}"

    HYPER_VIOLATIONS=0
    while IFS= read -r file; do
        if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
            # Check for forbidden patterns (subset of hyper-validate.sh)
            VIOLATIONS=$(
                grep -n -E \
                    '//\s*(TODO|FIXME|XXX|HACK|placeholder|not\s+implemented\s+yet)' \
                    "${PROJECT_ROOT}/${file}" 2>/dev/null | head -5 || true
            )

            if [[ -n "${VIOLATIONS}" ]]; then
                echo -e "  ${RED}VIOLATION in ${file}:${NC}"
                echo "${VIOLATIONS}" | head -3 | sed 's/^/    /'
                HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
            fi

            # Check for mock/stub patterns in production code
            if [[ ! "${file}" =~ /test/ ]]; then
                MOCK_VIOLATIONS=$(
                    grep -n -E \
                        '(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]' \
                        "${PROJECT_ROOT}/${file}" 2>/dev/null | head -5 || true
                )

                if [[ -n "${MOCK_VIOLATIONS}" ]]; then
                    echo -e "  ${RED}MOCK/STUB in production: ${file}:${NC}"
                    echo "${MOCK_VIOLATIONS}" | head -3 | sed 's/^/    /'
                    HYPER_VIOLATIONS=$((HYPER_VIOLATIONS + 1))
                fi
            fi
        fi
    done <<< "${CHANGED_JAVA}"

    if [[ $HYPER_VIOLATIONS -gt 0 ]]; then
        echo -e "  ${RED}FAILED: ${HYPER_VIOLATIONS} HYPER_STANDARDS violations${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: No HYPER_STANDARDS violations${NC}"
    fi
else
    echo -e "${BLUE}[1/6] SKIPPED: No Java files changed${NC}"
fi

# -------------------------------------------------------------------------
# Check 2: Shell script syntax
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_SH}" ]]; then
    echo -e "${BLUE}[2/6] Checking shell scripts...${NC}"

    SHELL_ERRORS=0
    while IFS= read -r file; do
        if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
            if ! bash -n "${PROJECT_ROOT}/${file}" 2>"${TEMP_DIR}/shell-error.txt"; then
                echo -e "  ${RED}SYNTAX ERROR in ${file}:${NC}"
                cat "${TEMP_DIR}/shell-error.txt" | sed 's/^/    /'
                SHELL_ERRORS=$((SHELL_ERRORS + 1))
            fi

            # Check for executable permission
            if [[ ! -x "${PROJECT_ROOT}/${file}" ]]; then
                echo -e "  ${YELLOW}WARNING: ${file} is not executable${NC}"
                WARNINGS=$((WARNINGS + 1))
            fi
        fi
    done <<< "${CHANGED_SH}"

    if [[ $SHELL_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}FAILED: ${SHELL_ERRORS} shell script errors${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: Shell scripts valid${NC}"
    fi
else
    echo -e "${BLUE}[2/6] SKIPPED: No shell scripts changed${NC}"
fi

# -------------------------------------------------------------------------
# Check 3: XML/XSD well-formedness
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_XML}" ]]; then
    echo -e "${BLUE}[3/6] Checking XML files...${NC}"

    XML_ERRORS=0
    while IFS= read -r file; do
        if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
            if ! xmllint --noout "${PROJECT_ROOT}/${file}" 2>"${TEMP_DIR}/xml-error.txt"; then
                echo -e "  ${RED}INVALID XML: ${file}:${NC}"
                cat "${TEMP_DIR}/xml-error.txt" | head -5 | sed 's/^/    /'
                XML_ERRORS=$((XML_ERRORS + 1))
            fi
        fi
    done <<< "${CHANGED_XML}"

    if [[ $XML_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}FAILED: ${XML_ERRORS} XML validation errors${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: XML files well-formed${NC}"
    fi
else
    echo -e "${BLUE}[3/6] SKIPPED: No XML files changed${NC}"
fi

# -------------------------------------------------------------------------
# Check 4: YAML syntax
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_YML}" ]]; then
    echo -e "${BLUE}[4/6] Checking YAML files...${NC}"

    YAML_ERRORS=0
    if command -v yamllint &> /dev/null; then
        while IFS= read -r file; do
            if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
                if ! yamllint -d relaxed "${PROJECT_ROOT}/${file}" 2>"${TEMP_DIR}/yaml-error.txt"; then
                    echo -e "  ${YELLOW}YAML LINT: ${file}${NC}"
                    cat "${TEMP_DIR}/yaml-error.txt" | head -5 | sed 's/^/    /'
                    # Warnings only, don't fail
                    WARNINGS=$((WARNINGS + 1))
                fi
            fi
        done <<< "${CHANGED_YML}"
    else
        # Try Python YAML parser
        while IFS= read -r file; do
            if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
                if ! python3 -c "import yaml; yaml.safe_load(open('${PROJECT_ROOT}/${file}'))" 2>"${TEMP_DIR}/yaml-error.txt"; then
                    echo -e "  ${RED}INVALID YAML: ${file}:${NC}"
                    cat "${TEMP_DIR}/yaml-error.txt" | head -5 | sed 's/^/    /'
                    YAML_ERRORS=$((YAML_ERRORS + 1))
                fi
            fi
        done <<< "${CHANGED_YML}"
    fi

    if [[ $YAML_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}FAILED: ${YAML_ERRORS} YAML validation errors${NC}"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "  ${GREEN}PASSED: YAML files valid${NC}"
    fi
else
    echo -e "${BLUE}[4/6] SKIPPED: No YAML files changed${NC}"
fi

# -------------------------------------------------------------------------
# Check 5: Markdown links (if markdown-link-check available)
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_MD}" ]]; then
    echo -e "${BLUE}[5/6] Checking markdown files...${NC}"

    if command -v markdown-link-check &> /dev/null; then
        MD_ERRORS=0
        while IFS= read -r file; do
            if [[ -n "${file}" ]] && [[ -f "${PROJECT_ROOT}/${file}" ]]; then
                if ! markdown-link-check -q "${PROJECT_ROOT}/${file}" 2>"${TEMP_DIR}/md-error.txt"; then
                    echo -e "  ${YELLOW}BROKEN LINKS in ${file}${NC}"
                    cat "${TEMP_DIR}/md-error.txt" | head -5 | sed 's/^/    /'
                    # Warnings only for markdown links
                    WARNINGS=$((WARNINGS + 1))
                fi
            fi
        done <<< "${CHANGED_MD}"

        if [[ $MD_ERRORS -gt 0 ]]; then
            echo -e "  ${YELLOW}WARNING: ${MD_ERRORS} markdown files with broken links${NC}"
        else
            echo -e "  ${GREEN}PASSED: Markdown files valid${NC}"
        fi
    else
        echo -e "  ${YELLOW}SKIPPED: markdown-link-check not installed${NC}"
    fi
else
    echo -e "${BLUE}[5/6] SKIPPED: No markdown files changed${NC}"
fi

# -------------------------------------------------------------------------
# Check 6: Java compilation (incremental)
# -------------------------------------------------------------------------
if [[ -n "${CHANGED_JAVA}" ]] || [[ -n "${CHANGED_POM}" ]]; then
    echo -e "${BLUE}[6/6] Quick compile check...${NC}"

    # Only compile changed modules if possible
    if command -v mvn &> /dev/null; then
        COMPILE_START=$(date +%s)
        if mvn compile -q -DskipTests 2>"${TEMP_DIR}/compile-error.txt"; then
            COMPILE_END=$(date +%s)
            COMPILE_TIME=$((COMPILE_END - COMPILE_START))
            echo -e "  ${GREEN}PASSED: Compilation successful (${COMPILE_TIME}s)${NC}"
        else
            echo -e "  ${RED}FAILED: Compilation errors${NC}"
            cat "${TEMP_DIR}/compile-error.txt" | head -20 | sed 's/^/    /'
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo -e "  ${YELLOW}SKIPPED: Maven not available${NC}"
    fi
else
    echo -e "${BLUE}[6/6] SKIPPED: No Java/POM changes${NC}"
fi

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}  Pre-Commit Summary${NC}"
echo -e "${BLUE}=========================================${NC}"
echo -e "  Errors:   ${RED}${ERRORS}${NC}"
echo -e "  Warnings: ${YELLOW}${WARNINGS}${NC}"
echo -e "${BLUE}=========================================${NC}"

if [[ $ERRORS -gt 0 ]]; then
    echo ""
    echo -e "${RED}COMMIT BLOCKED: Fix errors before committing${NC}"
    echo ""
    echo "Bypass with: git commit --no-verify"
    echo "Or:          SKIP_VALIDATION=1 git commit"
    exit 1
elif [[ $WARNINGS -gt 0 ]]; then
    echo ""
    echo -e "${YELLOW}COMMIT ALLOWED with warnings${NC}"
    exit 0
else
    echo ""
    echo -e "${GREEN}COMMIT ALLOWED: All checks passed${NC}"
    exit 0
fi
