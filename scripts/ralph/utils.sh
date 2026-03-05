#!/usr/bin/env bash
# Ralph Utilities — Common functions for loop orchestration
set -euo pipefail

# Paths
RALPH_HOME="${RALPH_HOME:-.}"
RALPH_STATE_DIR="${RALPH_HOME}/.claude/.ralph-state"
RALPH_LOGS_DIR="${RALPH_HOME}/.claude/.ralph-logs"

# Ensure directories exist
mkdir -p "${RALPH_STATE_DIR}" "${RALPH_LOGS_DIR}"

# JSON utilities
json_get() {
    local file="$1" key="$2" default="${3:-}"
    if [[ -f "$file" ]]; then
        jq -r ".${key} // \"${default}\"" "$file" 2>/dev/null || echo "${default}"
    else
        echo "${default}"
    fi
}

json_set() {
    local file="$1" key="$2" value="$3"
    mkdir -p "$(dirname "$file")"
    if [[ -f "$file" ]]; then
        jq ".${key} = \"${value}\"" "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"
    else
        echo "{\"${key}\": \"${value}\"}" | jq . > "$file"
    fi
}

# UUID generation
generate_uuid() {
    if command -v uuidgen &>/dev/null; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        # Fallback for systems without uuidgen
        python3 -c "import uuid; print(uuid.uuid4())" 2>/dev/null || \
        echo "ralph-$(date +%s)-$$"
    fi
}

# Logging
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO] $*" >&2
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2
}

log_debug() {
    if [[ "${DEBUG:-0}" == "1" ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DEBUG] $*" >&2
    fi
}

# Git utilities with retry logic
git_fetch_latest() {
    local retries=3
    local backoff=2

    for attempt in $(seq 1 $retries); do
        if git fetch origin -q 2>/dev/null; then
            return 0
        fi

        if [[ $attempt -lt $retries ]]; then
            log_debug "git fetch failed (attempt ${attempt}/${retries}), retrying in ${backoff}s"
            sleep $backoff
            backoff=$((backoff * 2))
        fi
    done

    log_error "git fetch failed after ${retries} attempts"
    return 1
}

git_log_since() {
    local timestamp="$1" branch="${2:-HEAD}"
    git log --since="${timestamp}" --oneline "${branch}" 2>/dev/null | wc -l
}

git_current_commit() {
    git rev-parse --short HEAD 2>/dev/null || echo "unknown"
}

# Validation check
check_dx_status() {
    # Run dx.sh all with 5-minute timeout
    # 0 = GREEN (success)
    # 2 = RED (failure)
    # 1 = transient (retry)
    # 124 = timeout (transient)
    local log_file="${RALPH_LOGS_DIR}/dx-$(date +%s).log"

    if timeout 300 bash scripts/dx.sh all > "$log_file" 2>&1; then
        return 0
    fi

    local exit_code=$?
    if [[ $exit_code -eq 124 ]]; then
        log_error "dx.sh timeout (5 minutes exceeded)"
        return 1  # Transient, will retry
    fi

    return $exit_code
}

# Wait with timeout
wait_with_timeout() {
    local timeout_secs=$1
    local check_cmd=$2
    local start_time
    start_time=$(date +%s)

    while true; do
        if eval "${check_cmd}"; then
            return 0
        fi

        local current_time
        current_time=$(date +%s)
        local elapsed=$((current_time - start_time))

        if [[ $elapsed -ge $timeout_secs ]]; then
            log_error "Timeout waiting for: ${check_cmd}"
            return 1
        fi

        sleep 5
    done
}

# Export functions for subshells
export -f json_get json_set generate_uuid log_info log_error log_debug
export -f git_fetch_latest git_log_since git_current_commit check_dx_status wait_with_timeout
