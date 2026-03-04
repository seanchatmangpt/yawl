#!/bin/bash
# Session End Hook v2.0
# Runs when Claude session ends (SessionEnd event)
# Ensures all background tasks complete before session closes
#
# This prevents intelligence loss: background tasks (yawl-scout fetch)
# are guaranteed to complete or are explicitly marked for next session.

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${CLAUDE_PROJECT_DIR:-.}"
HOOKS_DIR="$SCRIPT_DIR"

# Load libraries
if [ -f "$HOOKS_DIR/task-queue-lib.sh" ]; then
    source "$HOOKS_DIR/task-queue-lib.sh"
fi

if [ -f "$HOOKS_DIR/state-machine-lib.sh" ]; then
    source "$HOOKS_DIR/state-machine-lib.sh"
fi

# ─────────────────────────────────────────────────────────────────────────────
# PHASE 1: Wait for Background Tasks
# ─────────────────────────────────────────────────────────────────────────────

echo "🔄 Waiting for background tasks to complete..." >&2

TASK_TIMEOUT=30  # Wait max 30 seconds
if ! wait_for_all_tasks "$TASK_TIMEOUT"; then
    echo "⚠ Some background tasks are still pending" >&2
    echo "  They will resume on next session start" >&2
fi

# ─────────────────────────────────────────────────────────────────────────────
# PHASE 2: Flush Session State
# ─────────────────────────────────────────────────────────────────────────────

echo "💾 Flushing session state..." >&2

if declare -f audit_log >/dev/null 2>&1; then
    audit_log "session_event" "event=session_end"
fi

# ─────────────────────────────────────────────────────────────────────────────
# PHASE 3: Report Final Status
# ─────────────────────────────────────────────────────────────────────────────

echo "📊 Final session state:" >&2

if declare -f report_phase_status >/dev/null 2>&1; then
    report_phase_status
fi

echo "✨ Session closed successfully" >&2

exit 0
