#!/usr/bin/env bash
# Ralph Loop Orchestrator — Autonomous validation loop with agent spawning
set -euo pipefail

RALPH_HOME="${RALPH_HOME:-.}"

source "${RALPH_HOME}/scripts/ralph/utils.sh"
source "${RALPH_HOME}/scripts/ralph/loop-state.sh"

# Main loop iteration
run_iteration() {
    local iteration="$1"

    log_info "=== Ralph Loop Iteration ${iteration} ==="

    # Run validation
    log_info "Running validation: dx.sh all"
    if check_dx_status; then
        log_info "Validation GREEN ✓"
        set_validation_status "GREEN"
        return 0
    else
        local dx_exit=$?
        log_error "Validation FAILED (exit code: ${dx_exit})"
        set_validation_status "RED"

        # RED validation - spawn agents to fix
        if [[ ${dx_exit} -eq 2 ]]; then
            log_info "Spawning agent team to fix validation failures..."

            # This outputs Task() syntax that Claude Code will parse
            bash "${RALPH_HOME}/scripts/ralph/agent-spawner.sh" \
                "Fix validation failures in iteration ${iteration}" \
                "$(jq -r '.loop_id' "${RALPH_STATE_DIR}/current-loop.json" 2>/dev/null || echo 'unknown')"

            # After agent output, agents will run in parallel
            # Wait briefly for them to start, then continue to next iteration
            log_info "Agents spawned. Continuing to next iteration..."
            sleep 5

            return 1  # Continue looping
        else
            log_error "Validation failed with transient error (${dx_exit})"
            return 1  # Retry
        fi
    fi
}

# Main orchestration loop
main() {
    local description="$1"
    local max_iterations="${2:-10}"
    local timeout_mins="${3:-120}"

    # Initialize loop state
    local loop_id
    loop_id=$(bash "${RALPH_HOME}/scripts/ralph/loop-state.sh" init \
        "${description}" "${max_iterations}" "${timeout_mins}")

    log_info "Started Ralph Loop: ${loop_id}"
    log_info "Description: ${description}"
    log_info "Max iterations: ${max_iterations}"

    local iteration_count=0
    local max=$((max_iterations))

    while true; do
        iteration_count=$((iteration_count + 1))

        # Check if should continue
        if ! bash "${RALPH_HOME}/scripts/ralph/loop-state.sh" should-continue; then
            log_info "Loop condition met - exiting"
            break
        fi

        if [[ ${iteration_count} -gt ${max} ]]; then
            log_error "Maximum iterations (${max}) reached without GREEN validation"
            break
        fi

        # Update to next iteration
        bash "${RALPH_HOME}/scripts/ralph/loop-state.sh" next > /dev/null

        # Run iteration
        if run_iteration "${iteration_count}"; then
            # Validation GREEN - exit successfully
            log_info "Ralph Loop completed successfully!"
            bash "${RALPH_HOME}/scripts/ralph/loop-state.sh" summary
            return 0
        fi

        # Check for timeout
        local created_at
        created_at=$(jq -r '.created_at' "${RALPH_STATE_DIR}/current-loop.json")
        local timeout_secs=$((timeout_mins * 60))
        local elapsed
        elapsed=$(( $(date +%s) - $(date -d "${created_at}" +%s 2>/dev/null || echo 0) ))

        if [[ ${elapsed} -gt ${timeout_secs} ]]; then
            log_error "Ralph Loop timeout (${timeout_mins} minutes exceeded)"
            return 1
        fi

        # Wait before next iteration (agents may still be running)
        sleep 10
    done

    log_error "Ralph Loop exited without GREEN validation"
    bash "${RALPH_HOME}/scripts/ralph/loop-state.sh" summary
    return 1
}

# Entry point
if [[ $# -lt 1 ]]; then
    echo "Usage: ralph-loop.sh <description> [max-iterations] [timeout-mins]"
    exit 1
fi

main "$@"
