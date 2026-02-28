#!/bin/bash
# Claude Code Autorun Self-Update Hook
# SessionStart hook that detects and initiates auto-update cycles
# Runs at: SessionStart lifecycle event
# Exit: 0=success (continue), 1=warning (non-blocking), 2=critical (halt)

set -euo pipefail

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${HOOK_DIR}/../.." && pwd)"
CONFIG_FILE="${REPO_ROOT}/.claude/autorun-config.toml"
MEMORY_DIR="${REPO_ROOT}/.claude/memory"
LOG_FILE="${REPO_ROOT}/.claude/logs/autorun.log"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Initialize memory directory
mkdir -p "$MEMORY_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

# Function: Log message
log_info() {
    echo -e "${BLUE}[AUTORUN-HOOK]${NC} $*" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[AUTORUN-HOOK]${NC} ✅ $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[AUTORUN-HOOK]${NC} ⚠️  $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[AUTORUN-HOOK]${NC} ❌ $*" | tee -a "$LOG_FILE" >&2
}

# Function: Read TOML config value
read_toml_value() {
    local key=$1
    local default=$2

    if [ ! -f "$CONFIG_FILE" ]; then
        echo "$default"
        return 0
    fi

    # Simple TOML parsing for key=value pairs
    grep "^${key}\s*=" "$CONFIG_FILE" 2>/dev/null | \
        sed 's/.*=\s*//; s/^["'"'"']//; s/["'"'"']$//' || echo "$default"
}

# Function: Check if autorun is enabled
is_autorun_enabled() {
    local enabled=$(read_toml_value "enabled" "true")
    if [[ "$enabled" == "false" ]]; then
        log_warn "Autorun self-update is disabled in $CONFIG_FILE"
        return 1
    fi
    return 0
}

# Function: Check if update is needed based on detection mode
should_update() {
    local on_session_start=$(read_toml_value "on_session_start" "true")

    if [[ "$on_session_start" != "true" ]]; then
        log_info "SessionStart update detection disabled"
        return 1
    fi

    log_info "Checking if update is needed..."

    # Check 1: Version difference
    if check_version_difference; then
        log_info "Update available: version difference detected"
        return 0
    fi

    # Check 2: Periodic interval
    if check_periodic_interval; then
        log_info "Update needed: periodic interval exceeded"
        return 0
    fi

    # Check 3: Git events
    if check_git_events; then
        log_info "Update needed: git events detected"
        return 0
    fi

    log_info "No update needed at this time"
    return 1
}

# Function: Check version difference via Observatory
check_version_difference() {
    local facts_dir="${REPO_ROOT}/docs/v6/latest/facts"
    local version_file="${facts_dir}/version.json"

    if [ ! -f "$version_file" ]; then
        return 1
    fi

    local current_version=$(cat "$version_file" 2>/dev/null || echo "unknown")
    # In real implementation, fetch latest version from remote
    # For now, always false to avoid unnecessary updates
    return 1
}

# Function: Check if periodic update interval has elapsed
check_periodic_interval() {
    local interval_minutes=$(read_toml_value "periodic_interval_minutes" "60")

    if [ "$interval_minutes" -le 0 ]; then
        return 1
    fi

    local last_update_file="${MEMORY_DIR}/last-update-timestamp"

    if [ ! -f "$last_update_file" ]; then
        log_info "First run, update needed"
        return 0
    fi

    local last_update_ms=$(cat "$last_update_file" 2>/dev/null || echo 0)
    local now_ms=$(($(date +%s) * 1000))
    local elapsed_ms=$((now_ms - last_update_ms))
    local interval_ms=$((interval_minutes * 60 * 1000))

    if [ "$elapsed_ms" -ge "$interval_ms" ]; then
        log_info "Periodic interval exceeded: ${interval_minutes} minutes"
        return 0
    fi

    return 1
}

# Function: Check for git events (commits, merges)
check_git_events() {
    if [ ! -d "${REPO_ROOT}/.git" ]; then
        return 1
    fi

    local on_git_events=$(read_toml_value "on_git_events" "true")
    if [[ "$on_git_events" != "true" ]]; then
        return 1
    fi

    local last_commit_file="${MEMORY_DIR}/last-processed-commit"

    local current_commit=$(cd "$REPO_ROOT" && git rev-parse HEAD 2>/dev/null || echo "")
    if [ -z "$current_commit" ]; then
        return 1
    fi

    if [ ! -f "$last_commit_file" ]; then
        log_info "First run with git repo"
        return 0
    fi

    local last_commit=$(cat "$last_commit_file" 2>/dev/null || echo "")

    if [ "$current_commit" != "$last_commit" ]; then
        log_info "Git event detected: new commit"
        return 0
    fi

    return 1
}

# Function: Run Observatory to refresh facts
run_observatory() {
    local observatory_script="${REPO_ROOT}/scripts/observatory/observatory.sh"

    if [ ! -f "$observatory_script" ]; then
        log_warn "Observatory script not found: $observatory_script"
        return 0
    fi

    log_info "Running Observatory to refresh facts..."
    if bash "$observatory_script" >/dev/null 2>&1; then
        log_success "Observatory refresh completed"
        return 0
    else
        log_warn "Observatory refresh failed (continuing anyway)"
        return 0
    fi
}

# Function: Trigger autorun self-update
trigger_autorun() {
    log_info "Triggering Claude Code autorun self-update..."

    # Record that update is starting
    echo "$(date +%s)000" > "${MEMORY_DIR}/last-update-timestamp"

    # Record current commit
    if [ -d "${REPO_ROOT}/.git" ]; then
        cd "$REPO_ROOT" && git rev-parse HEAD > "${MEMORY_DIR}/last-processed-commit" 2>/dev/null || true
    fi

    log_success "Autorun self-update cycle initiated"
    log_info "Phase: DETECT → PLAN → EXECUTE → VALIDATE → COMMIT"

    # Note: Actual autorun execution happens via AutoUpdateSkill
    # This hook just sets up the trigger
    return 0
}

# Main hook logic
main() {
    log_info "======================================"
    log_info "Claude Code Autorun Self-Update Hook"
    log_info "======================================"
    log_info "Starting SessionStart lifecycle check"

    # Check if autorun is enabled
    if ! is_autorun_enabled; then
        log_warn "Autorun is disabled, skipping"
        exit 0
    fi

    # Refresh Observatory facts if configured
    local refresh_observatory=$(read_toml_value "refresh_observatory" "true")
    if [[ "$refresh_observatory" == "true" ]]; then
        run_observatory
    fi

    # Check if update is needed
    if should_update; then
        trigger_autorun
        log_success "Autorun SessionStart hook complete"
        exit 0
    else
        log_info "No update needed, continuing with normal session"
        exit 0
    fi
}

# Run main function
main "$@"
