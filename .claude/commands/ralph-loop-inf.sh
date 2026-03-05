#!/bin/bash
# Ralph Loop Infinite — Test/Iteration Mode
#
# Like ralph-loop.sh but WITHOUT completion promise requirement.
# Runs agents in a loop for N iterations, useful for testing and refinement.
#
# Usage:
#   /ralph-loop-inf "Task description here" --iterations 5
#
# Smart Validation (YAWL context):
#   - Auto-detects YAWL environment
#   - Runs validation on each iteration
#   - Re-injects prompt with validation errors
#   - Exits cleanly after N iterations
#
# Exit codes:
#   0 = Loop completed all iterations
#   1 = Transient error (retry-able)
#   2 = Fatal error (user must fix)

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RALPH_STATE_DIR="${REPO_ROOT}/.claude/.ralph-state"

# Create state directory
mkdir -p "${RALPH_STATE_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# ARGUMENT PARSING
# ──────────────────────────────────────────────────────────────────────────────

TASK_DESCRIPTION=""
ITERATIONS=5
INFINITE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        --infinity)
            INFINITE=true
            ITERATIONS=99999  # Internal max to prevent actual infinite state
            shift
            ;;
        *)
            # First non-flag argument is the task description
            if [[ -z "${TASK_DESCRIPTION}" ]]; then
                TASK_DESCRIPTION="$1"
            fi
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z "${TASK_DESCRIPTION}" ]]; then
    echo "Error: No task description provided" >&2
    echo "Usage: /ralph-loop-inf \"Task description\" --iterations 5" >&2
    echo "       /ralph-loop-inf \"Task description\" --infinity" >&2
    exit 2
fi

if [[ "${INFINITE}" != "true" ]]; then
    if ! [[ "${ITERATIONS}" =~ ^[0-9]+$ ]] || (( ITERATIONS < 1 )); then
        echo "Error: iterations must be a positive integer" >&2
        exit 2
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# YAWL CONTEXT DETECTION
# ──────────────────────────────────────────────────────────────────────────────

is_yawl_context() {
    [[ -f "${REPO_ROOT}/pom.xml" ]] && \
    [[ -f "${REPO_ROOT}/scripts/dx.sh" ]] && \
    [[ -d "${REPO_ROOT}/scripts/observatory" ]]
}

ENABLE_SMART_VALIDATION=false
if is_yawl_context; then
    ENABLE_SMART_VALIDATION=true
    echo "🎯 YAWL context detected - smart validation enabled"
fi

# ──────────────────────────────────────────────────────────────────────────────
# STATE INITIALIZATION
# ──────────────────────────────────────────────────────────────────────────────

STATE_FILE="${RALPH_STATE_DIR}/loop-inf-state.json"
EDIT_TRACKER="${RALPH_STATE_DIR}/loop-inf-edits.txt"
LOOP_ID="ralph-inf-$(date +%s)-${RANDOM}"

# Write state files in format stop-hook.sh expects (with INF mode marker)
echo "active-inf" > "${RALPH_STATE_DIR}/status"
echo "${LOOP_ID}" > "${RALPH_STATE_DIR}/loop-id"
echo "1" > "${RALPH_STATE_DIR}/iteration"
echo "${ITERATIONS}" > "${RALPH_STATE_DIR}/max-iterations"
echo "0" > "${RALPH_STATE_DIR}/min-iterations"
echo "true" > "${RALPH_STATE_DIR}/infinite-mode"

# Mark if truly infinite (--infinity flag)
if [[ "${INFINITE}" == "true" ]]; then
    echo "true" > "${RALPH_STATE_DIR}/truly-infinite"
fi

# Also write JSON for reference and debugging
cat > "${STATE_FILE}" << EOF
{
  "task_description": $(printf '%s\n' "${TASK_DESCRIPTION}" | jq -R -s .),
  "iterations": ${ITERATIONS},
  "truly_infinite": ${INFINITE},
  "current_iteration": 1,
  "loop_id": "${LOOP_ID}",
  "infinite_mode": true,
  "enable_smart_validation": ${ENABLE_SMART_VALIDATION},
  "started_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "active"
}
EOF

# Initialize edit tracker
> "${EDIT_TRACKER}"

# ──────────────────────────────────────────────────────────────────────────────
# PRE-ITERATION VALIDATION (YAWL ONLY)
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${ENABLE_SMART_VALIDATION}" == "true" ]]; then
    echo "🔍 Checking initial validation state..."

    if bash "${REPO_ROOT}/scripts/dx.sh" all > /tmp/ralph-dx-baseline.log 2>&1; then
        DX_BASELINE_GREEN=true
        echo "   ✅ Baseline validation GREEN"
    else
        DX_BASELINE_GREEN=false
        echo "   ⚠️  Baseline validation RED - will iterate"
    fi

    jq --arg baseline "$DX_BASELINE_GREEN" '.dx_baseline_green = ($baseline == "true")' "${STATE_FILE}" > "${STATE_FILE}.tmp"
    mv "${STATE_FILE}.tmp" "${STATE_FILE}"
fi

# ──────────────────────────────────────────────────────────────────────────────
# ACTIVATE STOP HOOK MECHANISM
# ──────────────────────────────────────────────────────────────────────────────

export RALPH_LOOP_ACTIVE=true
export RALPH_STATE_FILE="${STATE_FILE}"
export RALPH_INFINITE_MODE=true

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
if [[ "${INFINITE}" == "true" ]]; then
    echo "🚀 Ralph Loop INFINITE — Starting (∞ iterations)"
else
    echo "🚀 Ralph Loop Infinite — Starting"
fi
echo "───────────────────────────────────────────────────────────────────────────"
echo "📋 Task: ${TASK_DESCRIPTION}"
if [[ "${INFINITE}" == "true" ]]; then
    echo "🔄 Iterations: ∞ (infinite — no stopping condition)"
else
    echo "🔄 Iterations: ${ITERATIONS} (no completion promise required)"
fi
echo "🧠 Smart validation: $([ "${ENABLE_SMART_VALIDATION}" == "true" ] && echo "ENABLED" || echo "DISABLED")"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Display initial prompt for Claude
if [[ "${INFINITE}" == "true" ]]; then
    cat << PROMPT

## Ralph Loop INFINITE — Iteration 1 (∞)

**Task**: ${TASK_DESCRIPTION}

**Mode**: Truly infinite (no max iterations, no completion promise)

**What happens next**: The loop will run forever until you manually stop it. Each iteration will:
1. Run your agents/code changes
2. Validate the work (dx.sh all)
3. Show validation errors if any
4. Loop to next iteration (no exit condition)

**Use case**: Testing whether the loop can sustain itself indefinitely, validate agent behavior under continuous iteration.

**Current status**: Ready for iteration 1. Begin work on the task above.

PROMPT
else
    cat << PROMPT

## Ralph Loop Infinite — Iteration 1 of ${ITERATIONS}

**Task**: ${TASK_DESCRIPTION}

**Mode**: Infinite loop (no completion promise needed)

**What happens next**: The loop will run for ${ITERATIONS} iterations. Each iteration will:
1. Run your agents/code changes
2. Validate the work (dx.sh all)
3. Show validation errors if any
4. Move to next iteration

The loop will exit after ${ITERATIONS} iterations complete.

**Current status**: Ready for iteration 1. Begin work on the task above.

PROMPT
fi

# Return success - the stop-hook takes over from here
exit 0
