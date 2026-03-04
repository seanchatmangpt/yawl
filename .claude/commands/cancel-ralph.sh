#!/bin/bash
# Cancel Ralph Loop Command
#
# Terminates the active ralph-loop and cleans up state.
#
# Usage:
#   /cancel-ralph

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RALPH_STATE_DIR="${REPO_ROOT}/.claude/.ralph-state"

if [[ ! -d "${RALPH_STATE_DIR}" ]]; then
    echo "ℹ️  No active ralph-loop found"
    exit 0
fi

STATE_FILE="${RALPH_STATE_DIR}/loop-state.json"
EDIT_TRACKER="${RALPH_STATE_DIR}/loop-edits.txt"

if [[ ! -f "${STATE_FILE}" ]]; then
    echo "ℹ️  No active ralph-loop found"
    exit 0
fi

# Extract loop info
CURRENT_ITERATION=$(jq -r '.current_iteration // "?"' "${STATE_FILE}" 2>/dev/null)
TASK=$(jq -r '.task_description // "?"' "${STATE_FILE}" 2>/dev/null)

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "❌ Ralph Loop CANCELLED"
echo "───────────────────────────────────────────────────────────────────────────"
echo "📋 Task: ${TASK}"
echo "🔄 Iterations completed: ${CURRENT_ITERATION}"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Clean up state
rm -f "${STATE_FILE}" "${EDIT_TRACKER}"

# Unset environment variables
unset RALPH_LOOP_ACTIVE
unset RALPH_STATE_FILE
unset RALPH_COMPLETION_PROMISE
unset RALPH_PROMISE_FOUND

echo "✅ Loop state cleaned up"
exit 0
