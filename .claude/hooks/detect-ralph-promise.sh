#!/bin/bash
# Detect Ralph Loop Completion Promise
#
# Monitors Claude Code output to detect when the agent/user outputs the completion promise.
# When detected, marks the loop as promise-satisfied so stop-hook can exit.
#
# This hook is called by Claude Code's output processing to check for the promise.
# The INPUT is the assistant's current response/output.
#
# Exit codes:
#   0 = success (promise may or may not have been found)
#   1 = transient error
#   2 = fatal error

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${PROJECT_ROOT}/.claude/.ralph-state"

# Only run if ralph loop is active
if [[ ! -f "${STATE_DIR}/status" ]] || [[ "$(cat "${STATE_DIR}/status")" != "active" ]]; then
    exit 0
fi

# Get the completion promise we're looking for
if [[ ! -f "${STATE_DIR}/loop-state.json" ]]; then
    exit 0
fi

COMPLETION_PROMISE=$(jq -r '.completion_promise // empty' "${STATE_DIR}/loop-state.json" 2>/dev/null || echo "")

if [[ -z "${COMPLETION_PROMISE}" ]]; then
    exit 0
fi

# Read assistant output (from stdin or environment variable set by hook harness)
OUTPUT="${CLAUDE_ASSISTANT_OUTPUT:-}"

# If no output in env, try stdin (with timeout to avoid hanging)
if [[ -z "${OUTPUT}" ]]; then
    OUTPUT=$(timeout 1 cat 2>/dev/null || true)
fi

# Check if completion promise appears in output
# Use case-insensitive match since formatting may vary
if echo "${OUTPUT}" | grep -iF "${COMPLETION_PROMISE}" > /dev/null 2>&1; then
    echo "[✓] Ralph: Completion promise detected" >&2
    # Create marker file that stop-hook will check
    mkdir -p "${STATE_DIR}"
    touch "${STATE_DIR}/promise-found"
    # Store detection timestamp
    date -u +%Y-%m-%dT%H:%M:%SZ > "${STATE_DIR}/promise-found-at"
    exit 0
fi

exit 0
