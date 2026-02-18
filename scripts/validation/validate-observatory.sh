#!/usr/bin/env bash
# ==========================================================================
# validate-observatory.sh - Validates observatory facts against codebase
#
# Usage:
#   bash scripts/validation/validate-observatory.sh
#
# Validates:
#   1. Module inventory matches actual POM modules
#   2. SHA256 hashes match for all fact files
#   3. Receipt timestamps are recent
#
# Exit codes:
#   0 - All validations passed
#   1 - One or more validations failed
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
FACTS_DIR="${PROJECT_ROOT}/docs/v6/latest/facts"
RECEIPT="${PROJECT_ROOT}/docs/v6/latest/receipts/observatory.json"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0
WARNINGS=0

echo ""
echo "========================================="
echo "  Observatory Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

# Check if observatory has been run
if [[ ! -f "$RECEIPT" ]]; then
    echo -e "${RED}ERROR: Observatory receipt not found${NC}"
    echo "Run: bash scripts/observatory/observatory.sh"
    exit 1
fi

# -------------------------------------------------------------------------
# 1. Module Inventory Validation
# -------------------------------------------------------------------------
echo "[1/3] Validating module inventory..."

MODULES_FILE="${FACTS_DIR}/modules.json"
POM_FILE="${PROJECT_ROOT}/pom.xml"

if [[ -f "$MODULES_FILE" && -f "$POM_FILE" ]]; then
    # Extract actual modules from POM
    ACTUAL_MODULES=$(grep -o '<module>[^<]*</module>' "$POM_FILE" 2>/dev/null | \
        sed 's/<module>\|<\/module>//g' | sort | jq -R -s -c 'split("\n") | map(select(length > 0))' || echo "[]")

    # Extract stored modules from facts
    STORED_MODULES=$(jq -c '[.modules[].name] | sort' "$MODULES_FILE" 2>/dev/null || echo "[]")

    if [[ "$ACTUAL_MODULES" == "$STORED_MODULES" ]]; then
        MODULE_COUNT=$(echo "$ACTUAL_MODULES" | jq 'length')
        echo -e "  ${GREEN}PASSED: Module inventory valid (${MODULE_COUNT} modules)${NC}"
    else
        echo -e "  ${RED}ERROR: Module inventory stale${NC}"
        echo "  Actual:   ${ACTUAL_MODULES}"
        echo "  Stored:   ${STORED_MODULES}"
        echo "  Run: bash scripts/observatory/observatory.sh"
        ERRORS=$((ERRORS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: modules.json or pom.xml not found${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 2. SHA256 Hash Verification
# -------------------------------------------------------------------------
echo "[2/3] Verifying SHA256 hashes..."

HASH_ERRORS=0
HASH_TOTAL=0

if [[ -f "$RECEIPT" ]]; then
    # Verify fact file hashes
    while IFS= read -r fact_name; do
        FACT_FILE="${FACTS_DIR}/${fact_name}"

        if [[ -f "$FACT_FILE" ]]; then
            EXPECTED=$(jq -r ".outputs.facts_sha256[\"$fact_name\"] // empty" "$RECEIPT" 2>/dev/null)

            if [[ -n "$EXPECTED" ]]; then
                HASH_TOTAL=$((HASH_TOTAL + 1))

                # Calculate actual hash
                if command -v sha256sum &> /dev/null; then
                    ACTUAL="sha256:$(sha256sum "$FACT_FILE" | cut -d' ' -f1)"
                elif command -v shasum &> /dev/null; then
                    ACTUAL="sha256:$(shasum -a 256 "$FACT_FILE" | cut -d' ' -f1)"
                else
                    ACTUAL=""
                fi

                if [[ -n "$ACTUAL" ]]; then
                    if [[ "$EXPECTED" == "$ACTUAL" ]]; then
                        echo -e "  ${GREEN}VALID: ${fact_name}${NC}"
                    else
                        echo -e "  ${RED}STALE: ${fact_name}${NC}"
                        echo "    Expected: ${EXPECTED}"
                        echo "    Actual:   ${ACTUAL}"
                        HASH_ERRORS=$((HASH_ERRORS + 1))
                    fi
                fi
            fi
        fi
    done < <(jq -r '.outputs.facts_sha256 | keys[]' "$RECEIPT" 2>/dev/null)

    if [[ $HASH_ERRORS -gt 0 ]]; then
        echo -e "  ${RED}ERROR: ${HASH_ERRORS}/${HASH_TOTAL} fact files have stale hashes${NC}"
        ERRORS=$((ERRORS + 1))
    elif [[ $HASH_TOTAL -gt 0 ]]; then
        echo -e "  ${GREEN}PASSED: All ${HASH_TOTAL} fact hashes valid${NC}"
    else
        echo -e "  ${YELLOW}WARNING: No fact hashes found in receipt${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: Receipt not found${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# 3. Receipt Freshness Check
# -------------------------------------------------------------------------
echo "[3/3] Checking receipt freshness..."

if [[ -f "$RECEIPT" ]]; then
    RUN_ID=$(jq -r '.run_id // empty' "$RECEIPT" 2>/dev/null)

    if [[ -n "$RUN_ID" ]]; then
        # Parse run timestamp (format: 20260218T192952Z)
        RUN_TIMESTAMP=$(echo "$RUN_ID" | sed 's/T/ /;s/Z$//')
        RUN_EPOCH=$(date -j -f "%Y%m%d %H%M%S" "$RUN_TIMESTAMP" +%s 2>/dev/null || echo "0")

        if [[ "$RUN_EPOCH" != "0" ]]; then
            NOW_EPOCH=$(date +%s)
            AGE_SECONDS=$((NOW_EPOCH - RUN_EPOCH))
            AGE_HOURS=$((AGE_SECONDS / 3600))

            if [[ $AGE_HOURS -lt 24 ]]; then
                echo -e "  ${GREEN}PASSED: Receipt is fresh (${AGE_HOURS}h old)${NC}"
            elif [[ $AGE_HOURS -lt 168 ]]; then
                echo -e "  ${YELLOW}WARNING: Receipt is ${AGE_HOURS}h old (consider refresh)${NC}"
                WARNINGS=$((WARNINGS + 1))
            else
                echo -e "  ${RED}ERROR: Receipt is ${AGE_HOURS}h old (STALE)${NC}"
                echo "  Run: bash scripts/observatory/observatory.sh"
                ERRORS=$((ERRORS + 1))
            fi
        else
            echo -e "  ${YELLOW}WARNING: Could not parse run_id timestamp${NC}"
            WARNINGS=$((WARNINGS + 1))
        fi

        echo "  Run ID: ${RUN_ID}"
        echo "  Status: $(jq -r '.status' "$RECEIPT")"
    else
        echo -e "  ${YELLOW}WARNING: No run_id in receipt${NC}"
        WARNINGS=$((WARNINGS + 1))
    fi
else
    echo -e "  ${YELLOW}SKIPPED: Receipt not found${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

echo ""

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
echo "========================================="
echo "  Observatory Validation Summary"
echo "========================================="
echo "  Errors:   ${ERRORS}"
echo "  Warnings: ${WARNINGS}"
echo "========================================="
echo ""

if [[ $ERRORS -gt 0 ]]; then
    echo -e "${RED}VALIDATION FAILED${NC}"
    echo ""
    echo "To refresh observatory facts:"
    echo "  bash scripts/observatory/observatory.sh"
    exit 1
else
    echo -e "${GREEN}VALIDATION PASSED${NC}"
    exit 0
fi
