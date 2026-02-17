#!/bin/bash
# =============================================================================
# YAWL Engine Docker Health Check Script
# =============================================================================
# Multi-strategy health check with fallbacks for robust container orchestration.
#
# Check Order (highest to lowest priority):
#   1. Spring Boot Actuator health endpoint (most accurate)
#   2. TCP port connectivity check (network-level health)
#   3. Java process existence check (last resort)
#
# Exit Codes (Docker HEALTHCHECK compatible):
#   0 - Healthy (at least one check passed)
#   1 - Unhealthy (all checks failed)
#
# Usage:
#   ./healthcheck.sh                    # Use defaults
#   TIMEOUT_CONNECT=5 TIMEOUT_TOTAL=15 .//healthcheck.sh  # Custom timeouts
#
# Environment Variables:
#   HEALTH_PORT         - Application port (default: 8080)
#   HEALTH_HOST         - Application host (default: localhost)
#   HEALTH_PATH         - Actuator health path (default: /actuator/health/liveness)
#   HEALTH_READINESS_PATH - Actuator readiness path (default: /actuator/health/readiness)
#   TIMEOUT_CONNECT     - Connection timeout in seconds (default: 3)
#   TIMEOUT_TOTAL       - Total request timeout in seconds (default: 10)
#   LOG_LEVEL           - Log level: DEBUG, INFO, WARN, ERROR (default: INFO)
#   LOG_FILE            - Log file path (default: /app/logs/healthcheck.log)
#   DISABLE_ACTUATOR    - Skip actuator check if set to 'true'
#   DISABLE_PORT_CHECK  - Skip TCP port check if set to 'true'
#   DISABLE_PROCESS_CHECK - Skip process check if set to 'true'
# =============================================================================

set -euo pipefail

# =============================================================================
# Configuration (with environment variable overrides)
# =============================================================================

# Application settings
HEALTH_PORT="${HEALTH_PORT:-8080}"
HEALTH_HOST="${HEALTH_HOST:-localhost}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health/liveness}"
HEALTH_READINESS_PATH="${HEALTH_READINESS_PATH:-/actuator/health/readiness}"

# Timeout settings (in seconds)
TIMEOUT_CONNECT="${TIMEOUT_CONNECT:-3}"
TIMEOUT_TOTAL="${TIMEOUT_TOTAL:-10}"

# Logging settings
LOG_LEVEL="${LOG_LEVEL:-INFO}"
LOG_FILE="${LOG_FILE:-/app/logs/healthcheck.log}"

# Feature toggles
DISABLE_ACTUATOR="${DISABLE_ACTUATOR:-false}"
DISABLE_PORT_CHECK="${DISABLE_PORT_CHECK:-false}"
DISABLE_PROCESS_CHECK="${DISABLE_PROCESS_CHECK:-false}"

# Process pattern to match (YAWL engine JAR)
PROCESS_PATTERN="${PROCESS_PATTERN:-yaw[a-z]*\.jar}"

# =============================================================================
# Logging Functions
# =============================================================================

# Convert log level name to numeric value for comparison
get_log_level_num() {
    case "$1" in
        DEBUG) echo 0 ;;
        INFO)  echo 1 ;;
        WARN)  echo 2 ;;
        ERROR) echo 3 ;;
        *)     echo 1 ;;
    esac
}

log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp
    timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

    # Check if we should log at this level
    local current_level
    current_level=$(get_log_level_num "$LOG_LEVEL")
    local message_level
    message_level=$(get_log_level_num "$level")

    if (( message_level >= current_level )); then
        # Write to log file if writable
        local log_dir
        log_dir=$(dirname "$LOG_FILE")
        if [[ -d "$log_dir" ]] && [[ -w "$log_dir" ]]; then
            echo "[$timestamp] [$level] $message" >> "$LOG_FILE" 2>/dev/null || true
        fi

        # Write to stderr for Docker logs (only WARN and ERROR)
        if (( message_level >= 2 )); then
            echo "[$timestamp] [$level] $message" >&2
        fi
    fi
}

log_debug() { log "DEBUG" "$@"; }
log_info() { log "INFO" "$@"; }
log_warn() { log "WARN" "$@"; }
log_error() { log "ERROR" "$@"; }

# =============================================================================
# Health Check Functions
# =============================================================================

# Check 1: Spring Boot Actuator Health Endpoint
# Returns: 0 if healthy, 1 if unhealthy
check_actuator() {
    log_debug "Checking actuator endpoint: http://${HEALTH_HOST}:${HEALTH_PORT}${HEALTH_PATH}"

    # First try liveness probe
    if curl --silent \
            --fail \
            --connect-timeout "$TIMEOUT_CONNECT" \
            --max-time "$TIMEOUT_TOTAL" \
            "http://${HEALTH_HOST}:${HEALTH_PORT}${HEALTH_PATH}" > /dev/null 2>&1; then
        log_info "Actuator liveness check PASSED"
        return 0
    fi

    # Fallback to readiness probe if liveness fails
    log_debug "Liveness probe failed, trying readiness probe"
    if curl --silent \
            --fail \
            --connect-timeout "$TIMEOUT_CONNECT" \
            --max-time "$TIMEOUT_TOTAL" \
            "http://${HEALTH_HOST}:${HEALTH_PORT}${HEALTH_READINESS_PATH}" > /dev/null 2>&1; then
        log_info "Actuator readiness check PASSED"
        return 0
    fi

    # Try generic health endpoint as last resort
    log_debug "Readiness probe failed, trying generic health endpoint"
    if curl --silent \
            --fail \
            --connect-timeout "$TIMEOUT_CONNECT" \
            --max-time "$TIMEOUT_TOTAL" \
            "http://${HEALTH_HOST}:${HEALTH_PORT}/actuator/health" > /dev/null 2>&1; then
        log_info "Actuator generic health check PASSED"
        return 0
    fi

    log_debug "Actuator check FAILED"
    return 1
}

# Check 2: TCP Port Connectivity
# Returns: 0 if port is open, 1 if port is closed/filtered
check_tcp_port() {
    log_debug "Checking TCP port: ${HEALTH_HOST}:${HEALTH_PORT}"

    # Use timeout command to limit the check duration
    # Try netcat first (most common in containers)
    if command -v nc > /dev/null 2>&1; then
        if timeout "$TIMEOUT_CONNECT" nc -z "$HEALTH_HOST" "$HEALTH_PORT" 2>/dev/null; then
            log_info "TCP port check PASSED (nc): ${HEALTH_HOST}:${HEALTH_PORT}"
            return 0
        fi
    # Fallback to bash /dev/tcp (built-in)
    elif (echo > /dev/tcp/"$HEALTH_HOST"/"$HEALTH_PORT") 2>/dev/null; then
        log_info "TCP port check PASSED (bash): ${HEALTH_HOST}:${HEALTH_PORT}"
        return 0
    # Fallback to curl with HEAD request
    elif command -v curl > /dev/null 2>&1; then
        if curl --silent \
                --head \
                --connect-timeout "$TIMEOUT_CONNECT" \
                --max-time "$TIMEOUT_TOTAL" \
                "http://${HEALTH_HOST}:${HEALTH_PORT}/" > /dev/null 2>&1; then
            log_info "TCP port check PASSED (curl): ${HEALTH_HOST}:${HEALTH_PORT}"
            return 0
        fi
    fi

    log_debug "TCP port check FAILED"
    return 1
}

# Check 3: Java Process Existence
# Returns: 0 if process found, 1 if not found
check_process() {
    log_debug "Checking Java process with pattern: ${PROCESS_PATTERN}"

    # Use pgrep to find Java process
    if command -v pgrep > /dev/null 2>&1; then
        if pgrep -f "$PROCESS_PATTERN" > /dev/null 2>&1; then
            local pid
            pid=$(pgrep -f "$PROCESS_PATTERN" | head -1)
            log_info "Process check PASSED: found PID $pid"
            return 0
        fi
    # Fallback to ps + grep
    elif command -v ps > /dev/null 2>&1; then
        if ps aux 2>/dev/null | grep -E "$PROCESS_PATTERN" | grep -v grep > /dev/null; then
            log_info "Process check PASSED (ps): found matching process"
            return 0
        fi
    fi

    log_debug "Process check FAILED"
    return 1
}

# =============================================================================
# Main Health Check Logic
# =============================================================================

main() {
    log_debug "=========================================="
    log_debug "Starting YAWL health check"
    log_debug "Port: ${HEALTH_PORT}, Host: ${HEALTH_HOST}"
    log_debug "Timeouts: connect=${TIMEOUT_CONNECT}s, total=${TIMEOUT_TOTAL}s"
    log_debug "=========================================="

    local checks_passed=0
    local checks_failed=0
    local check_results=()

    # Run Actuator Check
    if [[ "$DISABLE_ACTUATOR" != "true" ]]; then
        if check_actuator; then
            check_results+=("actuator:PASSED")
            ((checks_passed++))
        else
            check_results+=("actuator:FAILED")
            ((checks_failed++))
        fi
    else
        log_debug "Actuator check DISABLED"
        check_results+=("actuator:SKIPPED")
    fi

    # Run TCP Port Check
    if [[ "$DISABLE_PORT_CHECK" != "true" ]]; then
        if check_tcp_port; then
            check_results+=("tcp_port:PASSED")
            ((checks_passed++))
        else
            check_results+=("tcp_port:FAILED")
            ((checks_failed++))
        fi
    else
        log_debug "TCP port check DISABLED"
        check_results+=("tcp_port:SKIPPED")
    fi

    # Run Process Check
    if [[ "$DISABLE_PROCESS_CHECK" != "true" ]]; then
        if check_process; then
            check_results+=("process:PASSED")
            ((checks_passed++))
        else
            check_results+=("process:FAILED")
            ((checks_failed++))
        fi
    else
        log_debug "Process check DISABLED"
        check_results+=("process:SKIPPED")
    fi

    # Log summary
    log_debug "Health check results: ${check_results[*]}"
    log_debug "Passed: $checks_passed, Failed: $checks_failed"

    # Determine overall health
    # We consider the container healthy if at least one check passed
    if (( checks_passed > 0 )); then
        log_info "Health check SUCCESSFUL ($checks_passed/$((checks_passed + checks_failed)) checks passed)"
        exit 0
    else
        log_error "Health check FAILED (all checks failed)"
        exit 1
    fi
}

# =============================================================================
# Script Entry Point
# =============================================================================

# Run main function
main "$@"
