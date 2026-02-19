#!/usr/bin/env bash
# ==========================================================================
# locking.sh — Concurrent Run Protection
#
# Prevents concurrent observatory runs from corrupting output.
# Uses flock for atomic lock acquisition.
#
# Usage:
#   source lib/locking.sh
#
#   if acquire_lock; then
#       trap release_lock EXIT
#       # ... do work ...
#   else
#       echo "Another observatory run is in progress"
#       exit 1
#   fi
#
# Environment:
#   OBSERVATORY_LOCK_DIR - Directory for lock files (default: /tmp)
#   OBSERVATORY_LOCK_TIMEOUT - Timeout in seconds (default: 0 = immediate fail)
# ==========================================================================

# Lock file path
OBSERVATORY_LOCK_DIR="${OBSERVATORY_LOCK_DIR:-/tmp}"
OBSERVATORY_LOCK_FILE="${OBSERVATORY_LOCK_DIR}/.observatory.lock"
OBSERVATORY_LOCK_TIMEOUT="${OBSERVATORY_LOCK_TIMEOUT:-0}"
OBSERVATORY_LOCK_FD=200

# Lock status tracking
LOCK_HELD=false
LOCK_PID=""

# ==========================================================================
# LOCK MANAGEMENT
# ==========================================================================

# Attempt to acquire exclusive lock
# Returns: 0 if lock acquired, 1 if lock held by another process
acquire_lock() {
    # Create lock directory if needed
    mkdir -p "$(dirname "$OBSERVATORY_LOCK_FILE")" 2>/dev/null

    # Try to acquire lock non-blocking
    exec 200>"$OBSERVATORY_LOCK_FILE"

    if [[ "$OBSERVATORY_LOCK_TIMEOUT" -gt 0 ]]; then
        # Wait with timeout
        if ! flock -w "$OBSERVATORY_LOCK_TIMEOUT" 200; then
            log_error "Failed to acquire lock after ${OBSERVATORY_LOCK_TIMEOUT}s timeout"
            return 1
        fi
    else
        # Immediate fail if locked
        if ! flock -n 200; then
            log_error "Observatory lock held by another process"
            log_info "Lock file: $OBSERVATORY_LOCK_FILE"
            return 1
        fi
    fi

    # Write PID to lock file
    echo $$ > "$OBSERVATORY_LOCK_FILE"
    LOCK_HELD=true
    LOCK_PID=$$

    log_info "Acquired observatory lock (PID: $$)"
    return 0
}

# Release exclusive lock
release_lock() {
    if [[ "$LOCK_HELD" == "true" ]]; then
        # Remove PID from lock file
        echo "" > "$OBSERVATORY_LOCK_FILE" 2>/dev/null
        flock -u 200 2>/dev/null
        exec 200>&- 2>/dev/null || true
        LOCK_HELD=false
        log_info "Released observatory lock"
    fi
}

# Check if lock is held by current process
is_lock_held() {
    [[ "$LOCK_HELD" == "true" ]]
}

# Check if lock is held by another process
is_locked_by_other() {
    # Try non-blocking acquisition
    exec 201>"${OBSERVATORY_LOCK_FILE}.check" 2>/dev/null
    if flock -n 201 2>/dev/null; then
        flock -u 201 2>/dev/null
        exec 201>&- 2>/dev/null
        return 1  # Not locked
    else
        exec 201>&- 2>/dev/null
        return 0  # Locked by other
    fi
}

# Get PID of lock holder (0 if not locked)
get_lock_holder() {
    if [[ -f "$OBSERVATORY_LOCK_FILE" ]]; then
        local pid
        pid=$(cat "$OBSERVATORY_LOCK_FILE" 2>/dev/null | head -1 | tr -d ' ')
        if [[ -n "$pid" && "$pid" =~ ^[0-9]+$ ]]; then
            # Check if process is still running
            if kill -0 "$pid" 2>/dev/null; then
                echo "$pid"
                return 0
            fi
        fi
    fi
    echo "0"
}

# Wait for lock with optional timeout
# Args: [timeout_seconds]
wait_for_lock() {
    local timeout="${1:-30}"
    local start
    start=$(date +%s)

    while is_locked_by_other; do
        local now
        now=$(date +%s)
        if [[ $((now - start)) -ge $timeout ]]; then
            log_error "Timeout waiting for lock after ${timeout}s"
            return 1
        fi
        sleep 0.5
    done
    return 0
}

# Force release lock (use with caution!)
force_release_lock() {
    log_warn "Force-releasing observatory lock"
    rm -f "$OBSERVATORY_LOCK_FILE" 2>/dev/null
    flock -u 200 2>/dev/null || true
    exec 200>&- 2>/dev/null || true
    LOCK_HELD=false
}

# ==========================================================================
# STALE LOCK DETECTION
# ==========================================================================

# Check if existing lock is stale (process no longer running)
is_stale_lock() {
    if [[ -f "$OBSERVATORY_LOCK_FILE" ]]; then
        local pid
        pid=$(cat "$OBSERVATORY_LOCK_FILE" 2>/dev/null | head -1 | tr -d ' ')
        if [[ -n "$pid" && "$pid" =~ ^[0-9]+$ ]]; then
            # Check if process exists
            if ! kill -0 "$pid" 2>/dev/null; then
                return 0  # Stale
            fi
        fi
    fi
    return 1  # Not stale
}

# Clean up stale lock if present
clean_stale_lock() {
    if is_stale_lock; then
        log_warn "Cleaning up stale lock (dead process)"
        rm -f "$OBSERVATORY_LOCK_FILE" 2>/dev/null
        return 0
    fi
    return 1
}

# ==========================================================================
# LOCK CONTEXT MANAGER
# ==========================================================================

# Run a command with lock (automatically releases on exit)
# Args: command [args...]
with_lock() {
    if acquire_lock; then
        trap release_lock EXIT
        "$@"
        local result=$?
        release_lock
        trap - EXIT
        return $result
    else
        return 1
    fi
}

# ==========================================================================
# INITIALIZATION
# ==========================================================================

# Clean up stale locks on source
clean_stale_lock 2>/dev/null || true
