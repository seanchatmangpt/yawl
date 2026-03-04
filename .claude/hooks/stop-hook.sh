#!/bin/bash
# Stop Hook - Ralph Loop Mechanism & Smart Validation
#
# This hook intercepts Claude Code's exit attempts and decides whether to:
# 1. Allow the exit (no active ralph-loop)
# 2. Re-inject the prompt with validation errors (YAWL smart validation failed)
# 3. Check for completion promise and continue loop (not satisfied yet)
# 4. Exit successfully (promise satisfied or max iterations reached)
#
# Called by Claude Code when user tries to exit the session.
# Must return 0 to allow exit, or non-zero to block and re-inject.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RALPH_STATE_DIR="${REPO_ROOT}/.claude/.ralph-state"

# ──────────────────────────────────────────────────────────────────────────────
# HELPER: Check if ralph loop is active
# ──────────────────────────────────────────────────────────────────────────────

is_loop_active() {
    [[ "${RALPH_LOOP_ACTIVE:-false}" == "true" ]] || [[ -f "${RALPH_STATE_DIR}/loop-state.json" ]]
}

# ──────────────────────────────────────────────────────────────────────────────
# HELPER: Parse JSON value from state file
# ──────────────────────────────────────────────────────────────────────────────

get_json_value() {
    local file="$1"
    local key="$2"
    jq -r ".${key} // \"\"" "${file}" 2>/dev/null || echo ""
}

# ──────────────────────────────────────────────────────────────────────────────
# MAIN LOGIC
# ──────────────────────────────────────────────────────────────────────────────

# If no active loop, allow normal exit
if ! is_loop_active; then
    exit 0
fi

STATE_FILE="${RALPH_STATE_DIR}/loop-state.json"
EDIT_TRACKER="${RALPH_STATE_DIR}/loop-edits.txt"

if [[ ! -f "${STATE_FILE}" ]]; then
    exit 0
fi

# Read loop state
CURRENT_ITERATION=$(get_json_value "${STATE_FILE}" "current_iteration")
MAX_ITERATIONS=$(get_json_value "${STATE_FILE}" "max_iterations")
COMPLETION_PROMISE=$(get_json_value "${STATE_FILE}" "completion_promise")
TASK_DESCRIPTION=$(get_json_value "${STATE_FILE}" "task_description")
ENABLE_SMART_VALIDATION=$(get_json_value "${STATE_FILE}" "enable_smart_validation")
DX_BASELINE_GREEN=$(get_json_value "${STATE_FILE}" "dx_baseline_green")

CURRENT_ITERATION=${CURRENT_ITERATION:-1}
MAX_ITERATIONS=${MAX_ITERATIONS:-50}

# ──────────────────────────────────────────────────────────────────────────────
# CHECK FOR COMPLETION PROMISE IN RECENT OUTPUT
# ──────────────────────────────────────────────────────────────────────────────

# TODO: In real implementation, extract promise from stdout/stderr
# For now, we'll require explicit detection via environment variable
# This would be populated by Claude Code's output analysis
PROMISE_FOUND="${RALPH_PROMISE_FOUND:-false}"

# ──────────────────────────────────────────────────────────────────────────────
# SMART VALIDATION (YAWL CONTEXT)
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${ENABLE_SMART_VALIDATION}" == "true" ]]; then
    # Check if this is iteration 1 and baseline was GREEN
    if [[ "${CURRENT_ITERATION}" == "1" ]] && [[ "${DX_BASELINE_GREEN}" == "true" ]]; then
        # Skip validation on iteration 1 if already GREEN
        echo "✅ Iteration 1: Skipping validation (baseline GREEN)"
    else
        # Check if files were edited since last iteration
        FILES_EDITED=false
        if [[ -f "${EDIT_TRACKER}" ]] && [[ -s "${EDIT_TRACKER}" ]]; then
            FILES_EDITED=true
        fi

        if [[ "${FILES_EDITED}" == "true" ]]; then
            echo "📝 Files edited detected - running validation..."

            # Run dx.sh validation
            if bash "${REPO_ROOT}/scripts/dx.sh" all > /tmp/ralph-dx-validate.log 2>&1; then
                echo "✅ Validation GREEN - continuing to completion check"
                # Clear edit tracker for next iteration
                > "${EDIT_TRACKER}"
            else
                # Validation failed - re-inject prompt with errors
                echo ""
                echo "❌ VALIDATION FAILED - Iteration ${CURRENT_ITERATION}/${MAX_ITERATIONS}"
                echo "───────────────────────────────────────────────────────────────"
                tail -20 /tmp/ralph-dx-validate.log 2>/dev/null || true
                echo ""
                echo "📝 Fix the validation errors above and try again."
                echo ""

                # Increment iteration counter
                NEW_ITERATION=$((CURRENT_ITERATION + 1))
                jq ".current_iteration = ${NEW_ITERATION}" "${STATE_FILE}" > "${STATE_FILE}.tmp"
                mv "${STATE_FILE}.tmp" "${STATE_FILE}"

                # Re-inject prompt - return 1 to block exit
                exit 1
            fi
        fi
    fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# CHECK COMPLETION PROMISE
# ──────────────────────────────────────────────────────────────────────────────

if [[ "${PROMISE_FOUND}" == "true" ]]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo "🎉 Ralph Loop COMPLETE"
    echo "───────────────────────────────────────────────────────────────────────────"
    echo "✅ Completion promise found: ${COMPLETION_PROMISE}"
    echo "✅ Total iterations: ${CURRENT_ITERATION}"
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo ""

    # Clean up state
    rm -f "${STATE_FILE}" "${EDIT_TRACKER}"
    unset RALPH_LOOP_ACTIVE
    unset RALPH_STATE_FILE
    unset RALPH_COMPLETION_PROMISE

    # Allow exit
    exit 0
fi

# ──────────────────────────────────────────────────────────────────────────────
# CHECK MAX ITERATIONS
# ──────────────────────────────────────────────────────────────────────────────

if (( CURRENT_ITERATION >= MAX_ITERATIONS )); then
    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo "⚠️  Ralph Loop TIMEOUT"
    echo "───────────────────────────────────────────────────────────────────────────"
    echo "🔄 Max iterations (${MAX_ITERATIONS}) reached without completion promise"
    echo "📋 Task: ${TASK_DESCRIPTION}"
    echo "🎯 Expected: ${COMPLETION_PROMISE}"
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo ""
    echo "Please review your work and ensure it meets the completion criteria."
    echo ""

    # Clean up state
    rm -f "${STATE_FILE}" "${EDIT_TRACKER}"
    unset RALPH_LOOP_ACTIVE

    # Allow exit
    exit 0
fi

# ──────────────────────────────────────────────────────────────────────────────
# CONTINUE LOOP - RE-INJECT PROMPT
# ──────────────────────────────────────────────────────────────────────────────

NEXT_ITERATION=$((CURRENT_ITERATION + 1))

# Update iteration counter
jq ".current_iteration = ${NEXT_ITERATION}" "${STATE_FILE}" > "${STATE_FILE}.tmp"
mv "${STATE_FILE}.tmp" "${STATE_FILE}"

# Clear edit tracker for next iteration
> "${EDIT_TRACKER}"

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "🔄 Ralph Loop Iteration ${NEXT_ITERATION}/${MAX_ITERATIONS}"
echo "───────────────────────────────────────────────────────────────────────────"
echo ""

# Re-inject the prompt for next iteration
cat << PROMPT

## Ralph Loop Iteration ${NEXT_ITERATION}

**Task**: ${TASK_DESCRIPTION}

**Completion Promise**: When ready, output:
\`\`\`
${COMPLETION_PROMISE}
\`\`\`

**Progress**: Iteration ${NEXT_ITERATION} of ${MAX_ITERATIONS}

**What to do**: Continue refining your work. The validation system will check your changes and give feedback.

PROMPT

# Return 1 to block exit and re-inject prompt
exit 1
