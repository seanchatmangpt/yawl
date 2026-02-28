#!/usr/bin/env bash
# ==========================================================================
# scripts/cache-cleanup.sh — LRU Cache Cleanup Maintenance Script
#
# Periodically runs cache cleanup to maintain size and performance.
# Designed to be called from CI/CD pipelines or as a cron job.
#
# Usage:
#   bash scripts/cache-cleanup.sh                  # Run cleanup
#   bash scripts/cache-cleanup.sh --stats          # Show cache stats
#   bash scripts/cache-cleanup.sh --clear          # Clear all cache
#   bash scripts/cache-cleanup.sh --hitrate        # Show hit/miss ratio
#   bash scripts/cache-cleanup.sh --prune <hours>  # Remove entries older than N hours
#
# Environment:
#   YAWL_CACHE_TTL_HOURS     TTL in hours (default: 24)
#   YAWL_CACHE_MAX_SIZE_BYTES Max cache size in bytes (default: 5GB)
#   YAWL_VERBOSE=1           Show detailed output
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Source cache configuration
source ".mvn/cache-config.sh"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_BLUE='\033[94m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# ── Parse arguments ───────────────────────────────────────────────────────
COMMAND="cleanup"
ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --stats)   COMMAND="stats"; shift ;;
        --clear)   COMMAND="clear"; shift ;;
        --hitrate) COMMAND="hitrate"; shift ;;
        --prune)   COMMAND="prune"; ARGS+=("$2"); shift 2 ;;
        --help|-h) COMMAND="help"; shift ;;
        *)         echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Commands ──────────────────────────────────────────────────────────────

cleanup_command() {
    printf "${C_CYAN}Cleaning test result cache...${C_RESET}\n"

    local pre_size
    pre_size=$(du -sb "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || pre_size=0

    # Remove expired entries (by TTL)
    remove_expired_entries

    # Run LRU cleanup if needed
    cache_cleanup_if_needed

    local post_size
    post_size=$(du -sb "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || post_size=0

    local freed=$((pre_size - post_size))

    printf "\n${C_GREEN}Cleanup complete${C_RESET}\n"
    printf "  Before: %s\n" "$(numfmt --to=iec-i --suffix=B $pre_size 2>/dev/null || echo "${pre_size} bytes")"
    printf "  After:  %s\n" "$(numfmt --to=iec-i --suffix=B $post_size 2>/dev/null || echo "${post_size} bytes")"
    printf "  Freed:  %s\n" "$(numfmt --to=iec-i --suffix=B $freed 2>/dev/null || echo "${freed} bytes")"
}

stats_command() {
    printf "\n${C_CYAN}═════════════════════════════════════════${C_RESET}\n"
    cache_stats
    printf "\n${C_CYAN}═════════════════════════════════════════${C_RESET}\n"
}

clear_command() {
    printf "${C_YELLOW}Clearing entire test result cache...${C_RESET}\n"
    cache_clear
    printf "${C_GREEN}Cache cleared successfully${C_RESET}\n"
}

hitrate_command() {
    cache_hitrate
}

prune_command() {
    local hours="${ARGS[0]:-24}"
    printf "${C_CYAN}Removing cache entries older than ${hours} hours...${C_RESET}\n"

    local cutoff_time
    cutoff_time=$(date -d "${hours} hours ago" +%s)
    local removed_count=0

    for entry in "${YAWL_CACHE_TEST_RESULTS}"/*/*.json; do
        [[ -f "$entry" ]] || continue

        local mtime
        mtime=$(stat -c %Y "$entry" 2>/dev/null || stat -f %m "$entry" 2>/dev/null)

        if [[ $mtime -lt $cutoff_time ]]; then
            rm -f "$entry"
            ((removed_count++))
        fi
    done

    printf "${C_GREEN}Removed %d entries older than %d hours${C_RESET}\n" "$removed_count" "$hours"
}

help_command() {
    sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
}

remove_expired_entries() {
    local current_time
    current_time=$(date -u +%s)
    local removed_count=0

    for entry in "${YAWL_CACHE_TEST_RESULTS}"/*/*.json; do
        [[ -f "$entry" ]] || continue

        local ttl_expires
        ttl_expires=$(jq -r '.ttl_expires // empty' "$entry" 2>/dev/null) || continue

        local expires_time
        expires_time=$(date -d "$ttl_expires" +%s 2>/dev/null) || {
            # Invalid date format, remove the entry
            rm -f "$entry"
            ((removed_count++))
            continue
        }

        if [[ $current_time -gt $expires_time ]]; then
            rm -f "$entry"
            ((removed_count++))
        fi
    done

    if [[ $removed_count -gt 0 ]]; then
        printf "  Removed %d expired entries\n" "$removed_count"
    fi
}

# ── Main execution ────────────────────────────────────────────────────────

case "$COMMAND" in
    cleanup) cleanup_command ;;
    stats)   stats_command ;;
    clear)   clear_command ;;
    hitrate) hitrate_command ;;
    prune)   prune_command ;;
    help)    help_command ;;
    *)       echo "Unknown command: $COMMAND"; exit 1 ;;
esac

exit 0
