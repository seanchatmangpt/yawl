#!/bin/bash
# Phase Writer v1.0
# Helper for dx.sh to write phase status to both old and new systems
# Maintains backward compatibility while transitioning to phases.json
#
# Usage:
#   source "$HOOKS_DIR/phase-writer.sh"
#   write_phase_status "h" "GREEN" 0
#   write_phase_metrics "λ" '{"modules_compiled": 25, "tests_run": 142}'

set -eo pipefail

# ─────────────────────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────────────────────

STATE_DIR="${REPO_ROOT}/.claude/state"
STATE_FILE="$STATE_DIR/phases.json"

# ─────────────────────────────────────────────────────────────────────────────
# Phase Mapping (from dx.sh naming to canonical phase names)
# ─────────────────────────────────────────────────────────────────────────────

map_phase_name() {
    local dx_phase="$1"
    case "$dx_phase" in
        "observe")     echo "ψ" ;;
        "compile")     echo "λ" ;;
        "test")        echo "λ" ;;  # test is part of build phase
        "guards")      echo "h" ;;
        "invariants")  echo "q" ;;
        "report")      echo "ω" ;;
        *)             echo "$dx_phase" ;;
    esac
}

# ─────────────────────────────────────────────────────────────────────────────
# Status Mapping (from dx.sh naming to canonical statuses)
# ─────────────────────────────────────────────────────────────────────────────

map_status() {
    local dx_status="$1"
    case "$dx_status" in
        "running")     echo "RUNNING" ;;
        "green")       echo "GREEN" ;;
        "red")         echo "RED" ;;
        "pending")     echo "PENDING" ;;
        "blocked")     echo "BLOCKED" ;;
        *)             echo "$dx_status" ;;
    esac
}

# ─────────────────────────────────────────────────────────────────────────────
# Main write function (atomic)
# ─────────────────────────────────────────────────────────────────────────────

write_phase_status() {
    local dx_phase="$1"
    local dx_status="$2"
    local exit_code="${3:-null}"

    # Map to canonical names
    local phase=$(map_phase_name "$dx_phase")
    local status=$(map_status "$dx_status")

    # Only write if phases.json exists
    if [ ! -f "$STATE_FILE" ]; then
        return 0
    fi

    mkdir -p "$STATE_DIR"

    # Atomic write (temp file → rename)
    local tmp_file="$STATE_FILE.tmp"
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    jq \
        --arg phase "$phase" \
        --arg status "$status" \
        --arg timestamp "$timestamp" \
        --arg exit_code "$exit_code" \
        '.phases[$phase].status = $status |
         .phases[$phase].timestamp = $timestamp |
         .phases[$phase].exit_code = ($exit_code | if . == "null" then null else tonumber end) |
         .last_update = $timestamp' \
        "$STATE_FILE" > "$tmp_file" 2>/dev/null || return 0

    mv "$tmp_file" "$STATE_FILE" 2>/dev/null || return 0
}

write_phase_metrics() {
    local dx_phase="$1"
    local metrics_json="$2"

    # Map to canonical name
    local phase=$(map_phase_name "$dx_phase")

    # Only write if phases.json exists
    if [ ! -f "$STATE_FILE" ]; then
        return 0
    fi

    mkdir -p "$STATE_DIR"

    local tmp_file="$STATE_FILE.tmp"

    jq \
        --arg phase "$phase" \
        --argjson metrics "$metrics_json" \
        '.phases[$phase].metrics = $metrics |
         .last_update = (now | todate)' \
        "$STATE_FILE" > "$tmp_file" 2>/dev/null || return 0

    mv "$tmp_file" "$STATE_FILE" 2>/dev/null || return 0
}

write_phase_duration() {
    local dx_phase="$1"
    local duration_ms="$2"

    # Map to canonical name
    local phase=$(map_phase_name "$dx_phase")

    # Only write if phases.json exists
    if [ ! -f "$STATE_FILE" ]; then
        return 0
    fi

    mkdir -p "$STATE_DIR"

    local tmp_file="$STATE_FILE.tmp"

    jq \
        --arg phase "$phase" \
        --arg duration "$duration_ms" \
        '.phases[$phase].duration_ms = ($duration | tonumber) |
         .last_update = (now | todate)' \
        "$STATE_FILE" > "$tmp_file" 2>/dev/null || return 0

    mv "$tmp_file" "$STATE_FILE" 2>/dev/null || return 0
}
