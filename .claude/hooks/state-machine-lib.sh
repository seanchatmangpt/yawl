#!/bin/bash
# State Machine Library v1.0
# Shared utilities for managing .claude/state/phases.json
#
# Usage:
#   source "$HOOKS_DIR/state-machine-lib.sh"
#   acquire_state_lock
#   update_phase_status "h" "RUNNING"
#   release_state_lock

set -eo pipefail

# ═══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════════

STATE_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/state"
STATE_FILE="$STATE_DIR/phases.json"
LOCK_FILE="$STATE_DIR/phases.json.lock"
AUDIT_FILE="$STATE_DIR/audit.jsonl"
TASKS_FILE="$STATE_DIR/tasks.json"
LOCK_TIMEOUT=30
LOCK_FD=3

# ═══════════════════════════════════════════════════════════════════════════════
# LOCKING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

acquire_state_lock() {
    mkdir -p "$STATE_DIR"

    # Open file descriptor for locking
    eval "exec $LOCK_FD> '$LOCK_FILE'"

    # Try non-blocking lock first
    if flock -n $LOCK_FD 2>/dev/null; then
        return 0
    fi

    # If busy, wait with timeout
    local elapsed=0
    while [ $elapsed -lt $LOCK_TIMEOUT ]; do
        if flock -n $LOCK_FD 2>/dev/null; then
            return 0
        fi
        sleep 0.5
        elapsed=$((elapsed + 1))
    done

    # Timeout: warn but proceed (prevent deadlock)
    echo "WARN: State lock held >30s, proceeding anyway (possible race condition)" >&2
    flock $LOCK_FD  # Blocking wait
    return 0
}

release_state_lock() {
    # Close FD, implicitly releases flock
    eval "exec $LOCK_FD>&-" 2>/dev/null || true
}

# ═══════════════════════════════════════════════════════════════════════════════
# STATE READING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

get_phase_status() {
    local phase="$1"

    if [ ! -f "$STATE_FILE" ]; then
        echo "PENDING"
        return 0
    fi

    jq -r ".phases.\"$phase\".status" "$STATE_FILE" 2>/dev/null || echo "UNKNOWN"
}

get_phase_exit_code() {
    local phase="$1"

    if [ ! -f "$STATE_FILE" ]; then
        echo "null"
        return 0
    fi

    jq -r ".phases.\"$phase\".exit_code" "$STATE_FILE" 2>/dev/null || echo "null"
}

# ═══════════════════════════════════════════════════════════════════════════════
# STATE WRITING FUNCTIONS (ATOMIC)
# ═══════════════════════════════════════════════════════════════════════════════

update_phase_status() {
    local phase="$1"
    local new_status="$2"
    local exit_code="${3:-null}"

    if [ ! -f "$STATE_FILE" ]; then
        echo "ERROR: phases.json not found at $STATE_FILE" >&2
        return 1
    fi

    # Record audit entry BEFORE updating phases.json
    local old_status=$(get_phase_status "$phase")
    audit_log "phase_status_change" "phase=$phase from=$old_status to=$new_status exit_code=$exit_code"

    # Update phases.json atomically (write to temp, then rename)
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local tmp_file="$STATE_FILE.tmp"

    jq \
        --arg phase "$phase" \
        --arg status "$new_status" \
        --arg timestamp "$timestamp" \
        --arg exit_code "$exit_code" \
        '.phases[$phase].status = $status |
         .phases[$phase].timestamp = $timestamp |
         .phases[$phase].exit_code = ($exit_code | if . == "null" then null else tonumber end) |
         .last_update = $timestamp' \
        "$STATE_FILE" > "$tmp_file" || {
        rm -f "$tmp_file"
        return 1
    }

    mv "$tmp_file" "$STATE_FILE"
    return 0
}

update_phase_metrics() {
    local phase="$1"
    local metrics_json="$2"  # JSON string like '{"modules_compiled": 25}'

    if [ ! -f "$STATE_FILE" ]; then
        echo "ERROR: phases.json not found at $STATE_FILE" >&2
        return 1
    fi

    local tmp_file="$STATE_FILE.tmp"

    # Use raw string merge to add metrics
    jq \
        --arg phase "$phase" \
        --argjson metrics "$metrics_json" \
        '.phases[$phase] += {metrics: $metrics}' \
        "$STATE_FILE" > "$tmp_file" || {
        rm -f "$tmp_file"
        return 1
    }

    mv "$tmp_file" "$STATE_FILE"
    return 0
}

update_phase_duration() {
    local phase="$1"
    local duration_ms="$2"

    if [ ! -f "$STATE_FILE" ]; then
        echo "ERROR: phases.json not found at $STATE_FILE" >&2
        return 1
    fi

    local tmp_file="$STATE_FILE.tmp"

    jq \
        --arg phase "$phase" \
        --arg duration "$duration_ms" \
        '.phases[$phase].duration_ms = ($duration | tonumber)' \
        "$STATE_FILE" > "$tmp_file" || {
        rm -f "$tmp_file"
        return 1
    }

    mv "$tmp_file" "$STATE_FILE"
    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# AUDIT TRAIL FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

audit_log() {
    local event="$1"
    shift
    local event_data="$@"

    mkdir -p "$STATE_DIR"

    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    local session_id="${CLAUDE_SESSION_ID:-session_unknown}"

    # Format: {"timestamp": "...", "event": "...", "session_id": "...", ...}
    printf '{"timestamp":"%s","event":"%s","session_id":"%s",%s}\n' \
        "$timestamp" "$event" "$session_id" "$event_data" >> "$AUDIT_FILE" 2>/dev/null || true
}

# ═══════════════════════════════════════════════════════════════════════════════
# VALIDATION FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

validate_phase_prerequisites() {
    local phase="$1"

    case "$phase" in
        ψ)
            # No prerequisites for Observatory
            return 0
            ;;
        λ)
            # Prerequisite: ψ must be GREEN
            local psi_status=$(get_phase_status "ψ")
            if [ "$psi_status" != "GREEN" ]; then
                echo "ERROR: Cannot run Λ (Build) phase while ψ (Observatory) status = $psi_status" >&2
                return 1
            fi
            ;;
        h)
            # Prerequisite: λ must be GREEN
            local lambda_status=$(get_phase_status "λ")
            if [ "$lambda_status" != "GREEN" ]; then
                echo "ERROR: Cannot run H (Guards) phase while λ (Build) status = $lambda_status" >&2
                return 1
            fi
            ;;
        q)
            # Prerequisite: h must be GREEN
            local h_status=$(get_phase_status "h")
            if [ "$h_status" != "GREEN" ]; then
                echo "ERROR: Cannot run Q (Invariants) phase while H (Guards) status = $h_status" >&2
                return 1
            fi
            ;;
    esac

    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# REPORTING FUNCTIONS
# ═══════════════════════════════════════════════════════════════════════════════

report_phase_status() {
    if [ ! -f "$STATE_FILE" ]; then
        echo "ERROR: phases.json not found" >&2
        return 1
    fi

    echo ""
    echo "=== YAWL Phase Status ==="
    jq '.phases | to_entries[] | "\(.key | ascii_upcase): \(.value.status)"' "$STATE_FILE" | tr -d '"'
    echo ""
}

report_full_state() {
    if [ ! -f "$STATE_FILE" ]; then
        echo "ERROR: phases.json not found" >&2
        return 1
    fi

    jq '.' "$STATE_FILE"
}
