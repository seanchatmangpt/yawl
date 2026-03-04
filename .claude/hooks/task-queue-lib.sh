#!/bin/bash
# Task Queue Library v1.0
# Manages background task execution with explicit queue management
# Replaces implicit spawning (yawl-scout fetch --async &) with explicit queueing
#
# This solves the intelligence loss problem: background tasks are no longer
# spawned without guarantee they'll complete before session ends.
#
# Usage:
#   source "$HOOKS_DIR/task-queue-lib.sh"
#   enqueue_task "fetch_intelligence" "yawl-scout fetch --async"
#   wait_for_all_tasks 30  # Wait 30 seconds for completion

set -eo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

STATE_DIR="${REPO_ROOT:-.}/.claude/state"
TASKS_FILE="$STATE_DIR/tasks.json"
TASK_TIMEOUT="${TASK_TIMEOUT:-30}"  # Default 30s wait at session end

# ─────────────────────────────────────────────────────────────────────────────
# Task Queue Schema (tasks.json)
# ─────────────────────────────────────────────────────────────────────────────

# {
#   "version": "1.0",
#   "tasks": [
#     {
#       "id": "task-20260304-053849-001",
#       "name": "fetch_intelligence",
#       "command": "yawl-scout fetch --async",
#       "enqueued_at": "2026-03-04T05:38:49Z",
#       "status": "pending|running|completed|failed",
#       "pid": null,
#       "exit_code": null,
#       "started_at": null,
#       "completed_at": null,
#       "error": null
#     }
#   ]
# }

# ─────────────────────────────────────────────────────────────────────────────
# Initialize Tasks File
# ─────────────────────────────────────────────────────────────────────────────

init_tasks_file() {
    if [ -f "$TASKS_FILE" ]; then
        return 0
    fi

    mkdir -p "$STATE_DIR"

    cat > "$TASKS_FILE" <<'EOF'
{
  "version": "1.0",
  "tasks": []
}
EOF
}

# ─────────────────────────────────────────────────────────────────────────────
# Enqueue a Background Task
# ─────────────────────────────────────────────────────────────────────────────

enqueue_task() {
    local task_name="$1"
    local command="$2"
    local description="${3:-}"

    init_tasks_file

    local task_id="task-$(date +%Y%m%d-%H%M%S)-$(printf '%03d' $RANDOM)"
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    local tmp_file="$TASKS_FILE.tmp"

    jq \
        --arg id "$task_id" \
        --arg name "$task_name" \
        --arg command "$command" \
        --arg desc "$description" \
        --arg timestamp "$timestamp" \
        '.tasks += [{
            "id": $id,
            "name": $name,
            "command": $command,
            "description": $desc,
            "enqueued_at": $timestamp,
            "status": "pending",
            "pid": null,
            "exit_code": null,
            "started_at": null,
            "completed_at": null,
            "error": null
        }]' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"
    echo "$task_id"
}

# ─────────────────────────────────────────────────────────────────────────────
# Execute a Task (spawns in background)
# ─────────────────────────────────────────────────────────────────────────────

execute_task() {
    local task_id="$1"

    # Extract command from tasks.json
    local command=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .command" "$TASKS_FILE" 2>/dev/null)

    if [ -z "$command" ]; then
        echo "ERROR: Task $task_id not found" >&2
        return 1
    fi

    # Mark as RUNNING and capture PID
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local tmp_file="$TASKS_FILE.tmp"

    jq \
        --arg task_id "$task_id" \
        --arg timestamp "$timestamp" \
        '.tasks[] |= if .id == $task_id then .status = "running" | .started_at = $timestamp else . end' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"

    # Spawn in background and record PID
    bash -c "$command" >/dev/null 2>&1 &
    local pid=$!

    # Update with PID
    tmp_file="$TASKS_FILE.tmp"
    jq \
        --arg task_id "$task_id" \
        --arg pid "$pid" \
        '.tasks[] |= if .id == $task_id then .pid = $pid else . end' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"

    echo "$pid"
}

# ─────────────────────────────────────────────────────────────────────────────
# Wait for a Task to Complete (with timeout)
# ─────────────────────────────────────────────────────────────────────────────

wait_for_task() {
    local task_id="$1"
    local timeout="${2:-30}"

    local start_time=$(date +%s)
    local elapsed=0

    while [ $elapsed -lt $timeout ]; do
        local status=$(jq -r ".tasks[] | select(.id == \"$task_id\") | .status" "$TASKS_FILE" 2>/dev/null)

        if [ "$status" = "completed" ] || [ "$status" = "failed" ]; then
            return 0
        fi

        sleep 1
        elapsed=$(($(date +%s) - start_time))
    done

    # Timeout: mark as timed out
    mark_task_timeout "$task_id"
    return 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Wait for ALL Tasks to Complete (with timeout)
# ─────────────────────────────────────────────────────────────────────────────

wait_for_all_tasks() {
    local timeout="${1:-30}"

    if [ ! -f "$TASKS_FILE" ]; then
        return 0
    fi

    local pending_count=$(jq '[.tasks[] | select(.status == "pending" or .status == "running")] | length' "$TASKS_FILE" 2>/dev/null || echo 0)

    if [ "$pending_count" -eq 0 ]; then
        return 0
    fi

    echo "Waiting for $pending_count background tasks to complete (timeout: ${timeout}s)..." >&2

    local start_time=$(date +%s)
    local elapsed=0

    while [ $elapsed -lt $timeout ]; do
        local pending_count=$(jq '[.tasks[] | select(.status == "pending" or .status == "running")] | length' "$TASKS_FILE" 2>/dev/null || echo 0)

        if [ "$pending_count" -eq 0 ]; then
            echo "✓ All background tasks completed" >&2
            return 0
        fi

        sleep 1
        elapsed=$(($(date +%s) - start_time))
    done

    # Timeout: warn but don't fail (graceful degradation)
    local still_pending=$(jq '[.tasks[] | select(.status == "pending" or .status == "running")] | length' "$TASKS_FILE" 2>/dev/null || echo 0)
    echo "⚠ Timeout: $still_pending tasks still pending (will resume next session)" >&2

    return 1
}

# ─────────────────────────────────────────────────────────────────────────────
# Mark Task as Completed
# ─────────────────────────────────────────────────────────────────────────────

mark_task_completed() {
    local task_id="$1"
    local exit_code="${2:-0}"

    if [ ! -f "$TASKS_FILE" ]; then
        return 0
    fi

    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local tmp_file="$TASKS_FILE.tmp"

    jq \
        --arg task_id "$task_id" \
        --arg exit_code "$exit_code" \
        --arg timestamp "$timestamp" \
        '.tasks[] |= if .id == $task_id then .status = "completed" | .exit_code = ($exit_code | tonumber) | .completed_at = $timestamp else . end' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"
}

# ─────────────────────────────────────────────────────────────────────────────
# Mark Task as Failed
# ─────────────────────────────────────────────────────────────────────────────

mark_task_failed() {
    local task_id="$1"
    local error_message="$2"

    if [ ! -f "$TASKS_FILE" ]; then
        return 0
    fi

    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local tmp_file="$TASKS_FILE.tmp"

    jq \
        --arg task_id "$task_id" \
        --arg error "$error_message" \
        --arg timestamp "$timestamp" \
        '.tasks[] |= if .id == $task_id then .status = "failed" | .error = $error | .completed_at = $timestamp else . end' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"
}

# ─────────────────────────────────────────────────────────────────────────────
# Mark Task as Timed Out
# ─────────────────────────────────────────────────────────────────────────────

mark_task_timeout() {
    local task_id="$1"

    if [ ! -f "$TASKS_FILE" ]; then
        return 0
    fi

    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local tmp_file="$TASKS_FILE.tmp"

    jq \
        --arg task_id "$task_id" \
        --arg timestamp "$timestamp" \
        '.tasks[] |= if .id == $task_id then .status = "pending" | .error = "timeout: will retry next session" | .updated_at = $timestamp else . end' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"
}

# ─────────────────────────────────────────────────────────────────────────────
# List All Tasks
# ─────────────────────────────────────────────────────────────────────────────

list_tasks() {
    if [ ! -f "$TASKS_FILE" ]; then
        echo "No tasks"
        return 0
    fi

    jq '.tasks | .[] | "\(.id): \(.name) [\(.status)]"' "$TASKS_FILE" 2>/dev/null | tr -d '"'
}

# ─────────────────────────────────────────────────────────────────────────────
# Clean up Completed Tasks (optional)
# ─────────────────────────────────────────────────────────────────────────────

cleanup_completed_tasks() {
    if [ ! -f "$TASKS_FILE" ]; then
        return 0
    fi

    local tmp_file="$TASKS_FILE.tmp"

    jq '.tasks |= map(select(.status != "completed"))' \
        "$TASKS_FILE" > "$tmp_file" || return 1

    mv "$tmp_file" "$TASKS_FILE"
}
