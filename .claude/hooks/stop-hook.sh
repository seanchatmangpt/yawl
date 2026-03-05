#!/bin/bash
# Enhanced Stop-Hook — Ralph Loop Orchestration & Autonomous Control
#
# Purpose:
#   Intercept exit attempts, detect ralph-loop context, and orchestrate the complete
#   autonomous workflow: validation → error analysis → remediation → loop continuation.
#
# Workflow:
#   1. Detect if ralph-loop is active (check .claude/.ralph-state/status)
#   2. If active: capture exit context, run validations (dx.sh)
#   3. Analyze any errors detected (analyze-errors.sh)
#   4. Auto-remediate violations (remediate-violations.sh)
#   5. Query decision-engine for loop continuation
#   6. Check implicit success criteria
#   7. If continue: re-inject prompt to Claude; if exit: allow clean exit

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLAUDE_DIR="${PROJECT_ROOT}/.claude"
SCRIPTS_DIR="${CLAUDE_DIR}/scripts"
RECEIPTS_DIR="${CLAUDE_DIR}/receipts"
STATE_DIR="${CLAUDE_DIR}/.ralph-state"
LOGS_DIR="${CLAUDE_DIR}/logs"

# Get environment variables (set by ralph-loop.sh or callers)
RALPH_LOOP_ACTIVE="${RALPH_LOOP_ACTIVE:-false}"
RALPH_LOOP_ID="${RALPH_LOOP_ID:-}"
RALPH_LOOP_MAX_ITERATIONS="${RALPH_LOOP_MAX_ITERATIONS:-50}"
RALPH_LOOP_MIN_ITERATIONS="${RALPH_LOOP_MIN_ITERATIONS:-1}"
RALPH_LOOP_ITERATION="${RALPH_LOOP_ITERATION:-0}"

# Colors
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
GREEN='\033[0;32m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Create directories
mkdir -p "${STATE_DIR}" "${RECEIPTS_DIR}" "${LOGS_DIR}"

log_info() {
    echo -e "${BLUE}[stop-hook]${NC} $*" | tee -a "${LOGS_DIR}/stop-hook.log" 2>/dev/null || echo -e "${BLUE}[stop-hook]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[stop-hook]${NC} $*" | tee -a "${LOGS_DIR}/stop-hook.log" 2>/dev/null || echo -e "${YELLOW}[stop-hook]${NC} $*"
}

log_error() {
    echo -e "${RED}[stop-hook]${NC} $*" | tee -a "${LOGS_DIR}/stop-hook.log" 2>/dev/null || echo -e "${RED}[stop-hook]${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}[stop-hook]${NC} $*" | tee -a "${LOGS_DIR}/stop-hook.log" 2>/dev/null || echo -e "${GREEN}[stop-hook]${NC} $*"
}

log_decision() {
    echo -e "${MAGENTA}[stop-hook]${NC} DECISION: $*" | tee -a "${LOGS_DIR}/stop-hook.log" 2>/dev/null || echo -e "${MAGENTA}[stop-hook]${NC} DECISION: $*"
}

# Detect loop context
detect_loop_context() {
    log_info "Detecting loop context..."

    if [[ ! -d "${STATE_DIR}" ]]; then
        log_info "  No active loop (state directory not found)"
        RALPH_LOOP_ACTIVE="false"
        return 0
    fi

    if [[ -f "${STATE_DIR}/status" ]]; then
        local status=$(cat "${STATE_DIR}/status")
        if [[ "${status}" == "active" ]]; then
            RALPH_LOOP_ACTIVE="true"
            log_success "  Loop detected: ACTIVE"

            [[ -f "${STATE_DIR}/loop-id" ]] && RALPH_LOOP_ID=$(cat "${STATE_DIR}/loop-id")
            [[ -f "${STATE_DIR}/iteration" ]] && RALPH_LOOP_ITERATION=$(cat "${STATE_DIR}/iteration")
            [[ -f "${STATE_DIR}/max-iterations" ]] && RALPH_LOOP_MAX_ITERATIONS=$(cat "${STATE_DIR}/max-iterations")
            [[ -f "${STATE_DIR}/min-iterations" ]] && RALPH_LOOP_MIN_ITERATIONS=$(cat "${STATE_DIR}/min-iterations")

            log_info "    Iteration: ${RALPH_LOOP_ITERATION}/${RALPH_LOOP_MAX_ITERATIONS} (min: ${RALPH_LOOP_MIN_ITERATIONS})"
            return 0
        fi
    fi

    RALPH_LOOP_ACTIVE="false"
    return 0
}

# Run validation pipeline
run_validation() {
    log_info "Running validation pipeline..."

    if [[ "${RALPH_LOOP_ACTIVE}" != "true" ]]; then
        log_info "  Skipping (loop not active)"
        return 0
    fi

    if [[ ! -f "${PROJECT_ROOT}/scripts/dx.sh" ]]; then
        log_warn "  dx.sh not found, skipping"
        return 0
    fi

    cd "${PROJECT_ROOT}"
    if bash scripts/dx.sh all >"${LOGS_DIR}/validation.log" 2>&1; then
        log_success "  Validation PASSED"
        echo "GREEN" > "${STATE_DIR}/validation-status"
        return 0
    else
        log_warn "  Validation FAILED"
        echo "RED" > "${STATE_DIR}/validation-status"
        return 1
    fi
}

# Analyze errors
analyze_errors() {
    log_info "Analyzing errors..."

    if [[ ! -f "${SCRIPTS_DIR}/analyze-errors.sh" ]]; then
        log_warn "  analyze-errors.sh not found"
        return 0
    fi

    bash "${SCRIPTS_DIR}/analyze-errors.sh" >"${LOGS_DIR}/error-analysis.log" 2>&1 || true
    log_success "  Error analysis complete"
}

# Auto-remediate
remediate_errors() {
    log_info "Attempting auto-remediation..."

    if [[ ! -f "${SCRIPTS_DIR}/remediate-violations.sh" ]]; then
        log_warn "  remediate-violations.sh not found"
        return 0
    fi

    if [[ ! -f "${RECEIPTS_DIR}/error-analysis-receipt.json" ]]; then
        log_info "  No error analysis found, skipping"
        return 0
    fi

    bash "${SCRIPTS_DIR}/remediate-violations.sh" >"${LOGS_DIR}/remediation.log" 2>&1 || true
    log_success "  Auto-remediation attempt complete"
}

# Check if completion promise was found in output
check_completion_promise() {
    local promise_file="${STATE_DIR}/completion-promise"
    local promise_found_file="${STATE_DIR}/promise-found"

    if [[ ! -f "${promise_file}" ]]; then
        return 1  # No promise to check
    fi

    local completion_promise=$(cat "${promise_file}")

    # Check if promise was output in this session
    # This is set by a hook that detects the promise in output
    if [[ -f "${promise_found_file}" ]]; then
        log_success "Completion promise detected in output"
        return 0
    fi

    return 1
}

# Decide continuation
decide_continuation() {
    log_info "Deciding loop continuation..."

    # Check max iterations
    if [[ ${RALPH_LOOP_ITERATION} -ge ${RALPH_LOOP_MAX_ITERATIONS} ]]; then
        log_decision "MAX ITERATIONS REACHED (${RALPH_LOOP_ITERATION}/${RALPH_LOOP_MAX_ITERATIONS})"
        return 0  # Exit
    fi

    # Check minimum iterations — force continue until min is met
    if [[ ${RALPH_LOOP_ITERATION} -lt ${RALPH_LOOP_MIN_ITERATIONS} ]]; then
        log_decision "MINIMUM ITERATIONS NOT MET (${RALPH_LOOP_ITERATION}/${RALPH_LOOP_MIN_ITERATIONS}) - forcing continue"
        return 1  # Continue
    fi

    # Check validation
    local validation_status="UNKNOWN"
    if [[ -f "${STATE_DIR}/validation-status" ]]; then
        validation_status=$(cat "${STATE_DIR}/validation-status")
    fi

    if [[ "${validation_status}" == "GREEN" ]]; then
        log_decision "VALIDATION PASSED"

        # If validation passed AND promise found, exit successfully
        if check_completion_promise; then
            log_success "EXITING - promise satisfied and validation green"
            return 0
        fi
    fi

    # Validation failed or promise not found: continue loop
    log_decision "CONTINUING LOOP (iteration $((RALPH_LOOP_ITERATION + 1)))"
    return 1
}

# Re-inject prompt for next iteration
reinject_prompt() {
    local next_iteration=$((RALPH_LOOP_ITERATION + 1))
    local task_description=""
    local completion_promise=""

    if [[ -f "${STATE_DIR}/loop-state.json" ]]; then
        task_description=$(jq -r '.task_description // ""' "${STATE_DIR}/loop-state.json" 2>/dev/null || echo "")
        completion_promise=$(jq -r '.completion_promise // ""' "${STATE_DIR}/loop-state.json" 2>/dev/null || echo "")
    fi

    echo ""
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo "🔄 Ralph Loop — Iteration ${next_iteration}/${RALPH_LOOP_MAX_ITERATIONS} (min: ${RALPH_LOOP_MIN_ITERATIONS})"
    echo "═══════════════════════════════════════════════════════════════════════════"
    echo ""
    echo "**Task**: ${task_description}"
    echo ""
    echo "**Status**: Validation detected issues. Please fix them and continue."
    echo ""

    # Show validation errors if they exist
    if [[ -f "${LOGS_DIR}/validation.log" ]]; then
        echo "**Validation Output**:"
        echo "\`\`\`"
        tail -20 "${LOGS_DIR}/validation.log" 2>/dev/null || echo "(validation log not available)"
        echo "\`\`\`"
        echo ""
    fi

    echo "**Completion Promise**: When this iteration is complete, output this text:"
    echo "\`\`\`"
    echo "${completion_promise}"
    echo "\`\`\`"
    echo ""
    echo "Continue working on the task above."
    echo ""
}

# Main
main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════════╗"
    echo "║                      STOP-HOOK: AUTONOMOUS CONTROL                        ║"
    echo "╚════════════════════════════════════════════════════════════════════════════╝"
    echo ""

    detect_loop_context

    if [[ "${RALPH_LOOP_ACTIVE}" != "true" ]]; then
        log_info "Exiting (no active loop)"
        return 0
    fi

    echo ""
    run_validation
    analyze_errors
    remediate_errors

    if decide_continuation; then
        log_success "Stop-hook complete - allowing normal exit"
        echo "completed" > "${STATE_DIR}/status"
        # Clean up promise marker
        rm -f "${STATE_DIR}/promise-found" "${STATE_DIR}/promise-found-at"
        return 0
    else
        # Update iteration counter for next iteration
        local next_iteration=$((RALPH_LOOP_ITERATION + 1))
        echo "${next_iteration}" > "${STATE_DIR}/iteration"

        echo ""
        log_decision "CONTINUING LOOP (iteration ${next_iteration}/${RALPH_LOOP_MAX_ITERATIONS})"
        echo ""

        # Re-inject the prompt for the next iteration
        reinject_prompt

        # Signal to Claude Code to continue the session
        # Return code 127 is our marker for "continue loop"
        return 127
    fi
}

# Execute
main "$@"
