#!/bin/bash
# Post-Edit Hook - Consolidate Edit Recording, Guard Validation, & Intelligence
#
# This hook runs after every Write/Edit operation and:
# 1. Records the edit to session history (memory)
# 2. Tracks edit timestamp for ralph-loop detection
# 3. Runs guard validation (phase H) on modified Java files
# 4. Calls intelligence hook for delta recording (if available)
# 5. Reports any violations found
#
# Merged from:
#   - post-edit.sh (history recording)
#   - hyper-validate.sh (guard validation, extracted to phase-h-guards.sh)
#   - intelligence-post-edit.sh (delta recording)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Arguments
FILE_PATH="${1:-}"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Skip if no file path
if [[ -z "${FILE_PATH}" ]]; then
    exit 0
fi

RELATIVE_PATH="${FILE_PATH#${PROJECT_ROOT}/}"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

echo -e "${BLUE}[post-edit] Processing: ${RELATIVE_PATH}${NC}"

# ──────────────────────────────────────────────────────────────────────────────
# 1. RECORD EDIT TO SESSION HISTORY
# ──────────────────────────────────────────────────────────────────────────────

HISTORY_DIR="${PROJECT_ROOT}/.claude/memory"
mkdir -p "${HISTORY_DIR}"
echo "[${TIMESTAMP}] edit: ${RELATIVE_PATH}" >> "${HISTORY_DIR}/history.log" 2>/dev/null || true

# ──────────────────────────────────────────────────────────────────────────────
# 2. TRACK EDIT FOR RALPH-LOOP DETECTION
# ──────────────────────────────────────────────────────────────────────────────

# If ralph-loop is active, record this file as edited
if [[ "${RALPH_LOOP_ACTIVE:-false}" == "true" ]] && [[ -n "${RALPH_STATE_DIR:-}" ]]; then
    EDIT_TRACKER="${RALPH_STATE_DIR}/loop-edits.txt"
    mkdir -p "$(dirname "${EDIT_TRACKER}")"
    echo "${RELATIVE_PATH}" >> "${EDIT_TRACKER}" 2>/dev/null || true
fi

# ──────────────────────────────────────────────────────────────────────────────
# 3. RUN GUARD VALIDATION ON JAVA FILES
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${FILE_PATH}" == *.java ]]; then
    # Run phase-h-guards.sh on the modified file
    if bash "${PROJECT_ROOT}/.claude/scripts/phase-h-guards.sh" "${FILE_PATH}" 2>/dev/null; then
        echo -e "${GREEN}[post-edit] Guard validation: PASSED${NC}"
    else
        GUARD_EXIT=$?
        if [[ ${GUARD_EXIT} -eq 2 ]]; then
            # Violations found
            if [[ -f "${PROJECT_ROOT}/.claude/receipts/h-guards-receipt.json" ]]; then
                VIOLATIONS=$(jq -r '.violations_found' "${PROJECT_ROOT}/.claude/receipts/h-guards-receipt.json" 2>/dev/null || echo "?")
                echo -e "${YELLOW}[post-edit] Guard validation: WARNINGS (${VIOLATIONS} violations)${NC}"
            fi
        fi
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# 4. CALL INTELLIGENCE HOOK FOR DELTA RECORDING
# ──────────────────────────────────────────────────────────────────────────────

INTELLIGENCE_BIN="${PROJECT_ROOT}/rust/yawl-hooks/target/release/yawl-jira"
if [[ -x "${INTELLIGENCE_BIN}" ]]; then
    SESSION_ID="${CLAUDE_SESSION_ID:-default}"
    mkdir -p "${PROJECT_ROOT}/.claude/context/deltas/${SESSION_ID}"
    if output=$("${INTELLIGENCE_BIN}" post-write "${FILE_PATH}" "${SESSION_ID}" 2>/dev/null); then
        echo -e "${GREEN}[post-edit] Delta recorded${NC}"
    fi
fi

echo -e "${GREEN}[post-edit] Recorded${NC}"
exit 0
