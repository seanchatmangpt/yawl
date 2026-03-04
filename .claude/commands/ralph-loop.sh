#!/bin/bash
# Ralph Loop Command - Self-Referential Iterative Task Completion
#
# This command initiates an iterative task loop that feeds prompts back to Claude
# until a completion promise is satisfied. Works both generically and with YAWL's
# validation pipeline (smart validation mode).
#
# Usage:
#   /ralph-loop "Task description here" --max-iterations 10 --completion-promise "COMPLETE"
#
# Smart Validation (YAWL context):
#   - Auto-detects YAWL environment (pom.xml + scripts/dx.sh + scripts/observatory/)
#   - Skips validation on iteration 1 if dx.sh already GREEN
#   - Runs validation on subsequent iterations if files edited
#   - Re-injects prompt with validation errors if violations found
#
# Generic Mode (non-YAWL):
#   - Runs iterations without forced validation
#   - Just checks for completion promise
#
# Exit codes:
#   0 = Loop exited normally (promise satisfied or max iterations reached)
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
MAX_ITERATIONS=50
COMPLETION_PROMISE=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --max-iterations)
            MAX_ITERATIONS="$2"
            shift 2
            ;;
        --completion-promise)
            COMPLETION_PROMISE="$2"
            shift 2
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
    echo "Usage: /ralph-loop \"Task description\" --max-iterations 10 --completion-promise \"PROMISE\"" >&2
    exit 2
fi

if [[ -z "${COMPLETION_PROMISE}" ]]; then
    echo "Error: No completion promise provided (use --completion-promise \"TEXT\")" >&2
    exit 2
fi

if ! [[ "${MAX_ITERATIONS}" =~ ^[0-9]+$ ]] || (( MAX_ITERATIONS < 1 )); then
    echo "Error: max-iterations must be a positive integer" >&2
    exit 2
fi

# ──────────────────────────────────────────────────────────────────────────────
# YAWL CONTEXT DETECTION
# ──────────────────────────────────────────────────────────────────────────────

is_yawl_context() {
    # Check for YAWL markers: pom.xml, scripts/dx.sh, scripts/observatory/
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

STATE_FILE="${RALPH_STATE_DIR}/loop-state.json"
EDIT_TRACKER="${RALPH_STATE_DIR}/loop-edits.txt"

# Initialize state file with loop metadata
cat > "${STATE_FILE}" << EOF
{
  "task_description": $(printf '%s\n' "${TASK_DESCRIPTION}" | jq -R -s .),
  "completion_promise": $(printf '%s\n' "${COMPLETION_PROMISE}" | jq -R -s .),
  "max_iterations": ${MAX_ITERATIONS},
  "current_iteration": 1,
  "enable_smart_validation": ${ENABLE_SMART_VALIDATION},
  "started_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "status": "active"
}
EOF

# Initialize edit tracker (empty - no files edited yet)
> "${EDIT_TRACKER}"

# ──────────────────────────────────────────────────────────────────────────────
# PRE-ITERATION VALIDATION (YAWL ONLY)
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${ENABLE_SMART_VALIDATION}" == "true" ]]; then
    echo "🔍 Checking initial validation state..."

    # Run dx.sh to establish baseline
    if bash "${REPO_ROOT}/scripts/dx.sh" all > /tmp/ralph-dx-baseline.log 2>&1; then
        DX_BASELINE_GREEN=true
        echo "   ✅ Baseline validation GREEN - smart mode enabled (skip validation on iteration 1)"
    else
        DX_BASELINE_GREEN=false
        echo "   ⚠️  Baseline validation RED - validation will run on all iterations"
    fi

    # Store baseline status in state
    jq --arg baseline "$DX_BASELINE_GREEN" '.dx_baseline_green = ($baseline == "true")' "${STATE_FILE}" > "${STATE_FILE}.tmp"
    mv "${STATE_FILE}.tmp" "${STATE_FILE}"
fi

# ──────────────────────────────────────────────────────────────────────────────
# ACTIVATE STOP HOOK MECHANISM
# ──────────────────────────────────────────────────────────────────────────────

# Mark loop as active so stop-hook.sh will intercept exits
export RALPH_LOOP_ACTIVE=true
export RALPH_STATE_FILE="${STATE_FILE}"
export RALPH_COMPLETION_PROMISE="${COMPLETION_PROMISE}"

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "🚀 Ralph Loop Starting"
echo "───────────────────────────────────────────────────────────────────────────"
echo "📋 Task: ${TASK_DESCRIPTION}"
echo "🎯 Promise: ${COMPLETION_PROMISE}"
echo "🔄 Max iterations: ${MAX_ITERATIONS}"
echo "🧠 Smart validation: $([ "${ENABLE_SMART_VALIDATION}" == "true" ] && echo "ENABLED" || echo "DISABLED")"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# Display initial prompt for Claude
cat << PROMPT

## Ralph Loop Iteration 1

**Task**: ${TASK_DESCRIPTION}

**Completion Promise**: When you are confident the task is complete, output this exact text:
\`\`\`
${COMPLETION_PROMISE}
\`\`\`

**What happens next**: When you output the promise, the loop will check if your work meets validation standards. If issues are found, the prompt will be re-injected with details. Otherwise the loop exits successfully.

**Current status**: Ready for iteration 1. Begin work on the task above.

PROMPT

# Return success - the stop-hook takes over from here
exit 0
