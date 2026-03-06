#!/bin/bash
# Cancel Ralph Loop Skill
#
# Cancels an active ralph-loop by clearing the state and environment markers.
# Also lifts the tool ban so normal operation can resume.
#
# Usage: /cancel-ralph

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RALPH_STATE_DIR="${REPO_ROOT}/.claude/.ralph-state"

# Clear environment markers
unset RALPH_LOOP_ACTIVE
unset RALPH_STATE_FILE
unset RALPH_COMPLETION_PROMISE

# Clear the status file to lift the tool ban
if [[ -f "${RALPH_STATE_DIR}/status" ]]; then
    echo "cancelled" > "${RALPH_STATE_DIR}/status"
fi

# Update state file if it exists
STATE_FILE="${RALPH_STATE_DIR}/loop-state.json"
if [[ -f "${STATE_FILE}" ]]; then
    jq '.status = "cancelled" | .cancelled_at = "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'"' "${STATE_FILE}" > "${STATE_FILE}.tmp"
    mv "${STATE_FILE}.tmp" "${STATE_FILE}"
    echo "🛑 Ralph loop cancelled"
    echo "   State saved to: ${STATE_FILE}"
    echo "   Tool ban LIFTED - normal operation resumed"
else
    echo "ℹ️  No active ralph loop found"
fi

exit 0
