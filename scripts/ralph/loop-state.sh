#!/usr/bin/env bash
# Loop State Manager — Persist and restore loop progress
set -euo pipefail

RALPH_HOME="${RALPH_HOME:-.}"
RALPH_STATE_DIR="${RALPH_HOME}/.claude/.ralph-state"
LOOP_STATE_FILE="${RALPH_STATE_DIR}/current-loop.json"
RESULTS_FILE="${RALPH_STATE_DIR}/results.jsonl"

source "${RALPH_HOME}/scripts/ralph/utils.sh"

# Initialize new loop state
init_loop() {
    local description="$1"
    local max_iterations="${2:-10}"
    local timeout_mins="${3:-120}"

    local loop_id
    loop_id=$(generate_uuid)

    local state
    state=$(cat <<EOF
{
  "loop_id": "${loop_id}",
  "description": "${description}",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "max_iterations": ${max_iterations},
  "timeout_mins": ${timeout_mins},
  "current_iteration": 0,
  "validation_status": "pending",
  "agents_spawned": false,
  "last_validation_output": "",
  "agent_results": []
}
EOF
)

    echo "$state" | jq . > "${LOOP_STATE_FILE}"
    log_info "Initialized loop: ${loop_id}"
    echo "${loop_id}"
}

# Get current loop state
get_loop_state() {
    if [[ ! -f "${LOOP_STATE_FILE}" ]]; then
        log_error "No active loop state found"
        return 1
    fi
    cat "${LOOP_STATE_FILE}"
}

# Update loop state
update_loop() {
    local key="$1"
    local value="$2"

    if [[ ! -f "${LOOP_STATE_FILE}" ]]; then
        log_error "No active loop to update"
        return 1
    fi

    jq ".${key} = $(echo "$value" | jq -R .)" "${LOOP_STATE_FILE}" > "${LOOP_STATE_FILE}.tmp"
    mv "${LOOP_STATE_FILE}.tmp" "${LOOP_STATE_FILE}"
    log_debug "Updated loop state: ${key} = ${value}"
}

# Increment iteration counter
next_iteration() {
    local current
    current=$(jq -r '.current_iteration' "${LOOP_STATE_FILE}")
    local next=$((current + 1))

    update_loop "current_iteration" "${next}"
    echo "${next}"
}

# Set validation status (GREEN or RED)
set_validation_status() {
    local status="$1"
    local output="${2:-}"

    update_loop "validation_status" "${status}"
    if [[ -n "$output" ]]; then
        update_loop "last_validation_output" "${output}"
    fi

    log_info "Validation status: ${status}"
}

# Record agent spawn
record_agent_spawn() {
    local agent_id="$1"
    local agent_type="$2"

    update_loop "agents_spawned" "true"

    # Append to results log
    local result
    result=$(cat <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "event": "agent_spawn",
  "agent_id": "${agent_id}",
  "agent_type": "${agent_type}",
  "iteration": $(jq -r '.current_iteration' "${LOOP_STATE_FILE}")
}
EOF
)
    echo "$result" >> "${RESULTS_FILE}"
    log_debug "Recorded agent spawn: ${agent_type} (${agent_id})"
}

# Record agent completion
record_agent_complete() {
    local agent_id="$1"
    local status="$2"
    local commit_count="${3:-0}"

    local result
    result=$(cat <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "event": "agent_complete",
  "agent_id": "${agent_id}",
  "status": "${status}",
  "commits": ${commit_count},
  "iteration": $(jq -r '.current_iteration' "${LOOP_STATE_FILE}")
}
EOF
)
    echo "$result" >> "${RESULTS_FILE}"
    log_info "Agent ${agent_id} completed: ${status} (${commit_count} commits)"
}

# Check if should continue looping
should_continue_loop() {
    local current
    current=$(jq -r '.current_iteration' "${LOOP_STATE_FILE}")
    local max
    max=$(jq -r '.max_iterations' "${LOOP_STATE_FILE}")

    if [[ $current -ge $max ]]; then
        log_info "Reached max iterations (${max})"
        return 1
    fi

    local status
    status=$(jq -r '.validation_status' "${LOOP_STATE_FILE}")
    if [[ "$status" == "GREEN" ]]; then
        log_info "Validation GREEN - loop exiting successfully"
        return 1
    fi

    return 0
}

# Get loop summary
get_loop_summary() {
    if [[ ! -f "${LOOP_STATE_FILE}" ]]; then
        echo "No active loop"
        return
    fi

    local loop_state
    loop_state=$(get_loop_state)

    echo "=== Ralph Loop Summary ==="
    echo "Loop ID: $(echo "$loop_state" | jq -r '.loop_id')"
    echo "Description: $(echo "$loop_state" | jq -r '.description')"
    echo "Iteration: $(echo "$loop_state" | jq -r '.current_iteration')/$(echo "$loop_state" | jq -r '.max_iterations')"
    echo "Status: $(echo "$loop_state" | jq -r '.validation_status')"
    echo "Agents Spawned: $(echo "$loop_state" | jq -r '.agents_spawned')"
    echo ""

    if [[ -f "${RESULTS_FILE}" ]]; then
        echo "=== Recent Events ==="
        tail -5 "${RESULTS_FILE}" | jq -r '.event + ": " + .timestamp'
    fi
}

# Clean up loop state
cleanup_loop() {
    if [[ -f "${LOOP_STATE_FILE}" ]]; then
        rm "${LOOP_STATE_FILE}"
        log_info "Cleaned up loop state"
    fi
}

# Main entry point for direct invocation
case "${1:-}" in
    init)
        init_loop "$2" "${3:-10}" "${4:-120}"
        ;;
    get)
        get_loop_state
        ;;
    update)
        update_loop "$2" "$3"
        ;;
    next)
        next_iteration
        ;;
    status)
        set_validation_status "$2" "${3:-}"
        ;;
    spawn)
        record_agent_spawn "$2" "$3"
        ;;
    complete)
        record_agent_complete "$2" "$3" "${4:-0}"
        ;;
    should-continue)
        should_continue_loop
        ;;
    summary)
        get_loop_summary
        ;;
    cleanup)
        cleanup_loop
        ;;
    *)
        echo "Usage: loop-state.sh {init|get|update|next|status|spawn|complete|should-continue|summary|cleanup}"
        exit 1
        ;;
esac
