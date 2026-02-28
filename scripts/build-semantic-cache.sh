#!/usr/bin/env bash
# ==========================================================================
# build-semantic-cache.sh — Build Semantic Cache for All Modules
#
# Computes and stores semantic hashes for all modules in the YAWL project.
# Used during build setup to enable incremental change detection.
#
# Usage:
#   bash scripts/build-semantic-cache.sh               # cache all modules
#   bash scripts/build-semantic-cache.sh <module>      # cache single module
#   bash scripts/build-semantic-cache.sh --refresh     # force recompute
#   bash scripts/build-semantic-cache.sh --status      # show cache status
#
# Output:
#   Cached files in: .yawl/cache/semantic-hashes/{module}.json
#   Summary of cache hits/misses
#
# Environment:
#   VERBOSE=1          Show detailed output
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
readonly SEMANTIC_CACHE_DIR="${REPO_ROOT}/.yawl/cache/semantic-hashes"
readonly VERBOSE="${VERBOSE:-0}"

# Ensure cache directory exists
mkdir -p "${SEMANTIC_CACHE_DIR}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'
readonly C_BLUE='\033[94m'

# ============================================================================
# Utility Functions
# ============================================================================

log() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${C_CYAN}[CACHE]${C_RESET} $*" >&2
    fi
}

error() {
    echo -e "${C_YELLOW}[CACHE]${C_RESET} ERROR: $*" >&2
}

success() {
    echo -e "${C_GREEN}✓${C_RESET} $*"
}

# Get list of all YAWL modules
get_all_modules() {
    # From dx.sh topology (build order matters but we cache independently)
    cat <<'EOF'
yawl-utilities
yawl-security
yawl-graalpy
yawl-graaljs
yawl-elements
yawl-ggen
yawl-graalwasm
yawl-dmn
yawl-data-modelling
yawl-engine
yawl-stateless
yawl-authentication
yawl-scheduling
yawl-monitoring
yawl-worklet
yawl-control-panel
yawl-integration
yawl-webapps
yawl-pi
yawl-resourcing
yawl-mcp-a2a-app
EOF
}

# Check if cached hash is valid (exists and is not stale)
is_cache_fresh() {
    local module="$1"
    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"
    local ttl_hours="${2:-24}"

    if [[ ! -f "$cache_file" ]]; then
        return 1
    fi

    # Check TTL: if cache is older than ttl_hours, it's stale
    local cache_age_sec
    cache_age_sec=$(( $(date +%s) - $(stat -f%m "$cache_file" 2>/dev/null || stat -c%Y "$cache_file" 2>/dev/null || echo 0) ))
    local ttl_sec=$((ttl_hours * 3600))

    [[ $cache_age_sec -lt $ttl_sec ]]
}

# Compute and store semantic hash for a module
cache_module() {
    local module="$1"
    local force_refresh="${2:-0}"

    # Check if module directory exists
    if [[ ! -d "${REPO_ROOT}/${module}" ]]; then
        log "Module not found: $module (skipping)"
        return 2
    fi

    # Check if cache is fresh (unless forced)
    if [[ "$force_refresh" != "1" ]] && is_cache_fresh "$module"; then
        log "Cache hit: $module (fresh)"
        return 0
    fi

    # Compute semantic hash
    local semantic_hash
    semantic_hash=$(bash "${SCRIPT_DIR}/compute-semantic-hash.sh" "$module" 2>/dev/null) || {
        error "Failed to compute hash for $module"
        return 1
    }

    # Save to cache
    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"
    echo "$semantic_hash" | jq '.' > "${cache_file}.tmp"
    mv "${cache_file}.tmp" "$cache_file"

    # Extract file count from result
    local file_count
    file_count=$(echo "$semantic_hash" | jq -r '.file_count // 0')

    log "Cached: $module ($file_count files)"
    return 0
}

# Validate cache integrity for a module
validate_cache() {
    local module="$1"
    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"

    if [[ ! -f "$cache_file" ]]; then
        echo "MISSING"
        return 1
    fi

    # Verify JSON structure
    if ! jq -e '.hash and .module and .file_count' "$cache_file" >/dev/null 2>&1; then
        echo "INVALID"
        return 1
    fi

    echo "VALID"
    return 0
}

# Show cache statistics
show_cache_status() {
    echo ""
    printf "${C_BLUE}Semantic Cache Status${C_RESET}\n"
    echo "===================="

    local total=0
    local valid=0
    local stale=0
    local missing=0

    local modules
    modules=$(get_all_modules)

    while IFS= read -r module; do
        [[ -z "$module" ]] && continue
        ((total++))

        local status
        status=$(validate_cache "$module")

        if [[ "$status" == "VALID" ]]; then
            if is_cache_fresh "$module"; then
                printf "  ${C_GREEN}✓${C_RESET} %-40s fresh\n" "$module"
                ((valid++))
            else
                printf "  ${C_YELLOW}~${C_RESET} %-40s stale (>24h)\n" "$module"
                ((stale++))
            fi
        else
            printf "  ${C_YELLOW}!${C_RESET} %-40s ${status}\n" "$module"
            ((missing++))
        fi
    done <<< "$modules"

    echo ""
    printf "Summary: ${C_GREEN}%d fresh${C_RESET} | ${C_YELLOW}%d stale${C_RESET} | ${C_YELLOW}%d missing${C_RESET} / ${total} total\n" \
        "$valid" "$stale" "$missing"

    if [[ $missing -gt 0 ]] || [[ $stale -gt 0 ]]; then
        printf "\nRun: ${C_CYAN}bash scripts/build-semantic-cache.sh${C_RESET} to refresh\n"
    fi
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    local action="${1:-cache-all}"
    local target="${2:-}"

    case "$action" in
        --status)
            show_cache_status
            exit 0
            ;;

        --refresh)
            printf "${C_CYAN}Refreshing semantic cache for all modules...${C_RESET}\n\n"
            local modules
            modules=$(get_all_modules)
            local success_count=0
            local fail_count=0

            while IFS= read -r module; do
                [[ -z "$module" ]] && continue
                if cache_module "$module" 1; then
                    ((success_count++))
                else
                    ((fail_count++))
                fi
            done <<< "$modules"

            echo ""
            printf "${C_GREEN}✓${C_RESET} Cached %d modules | ${C_YELLOW}Failed: %d${C_RESET}\n" "$success_count" "$fail_count"
            exit $([[ $fail_count -eq 0 ]] && echo 0 || echo 1)
            ;;

        # Cache single module or all modules
        *)
            if [[ -n "$target" ]]; then
                # User specified --refresh as target, treat as global refresh
                if [[ "$target" == "--refresh" ]]; then
                    bash "$0" --refresh
                    exit $?
                fi
                # Otherwise cache specified module
                printf "${C_CYAN}Caching semantic hash for module: ${target}${C_RESET}\n"
                if cache_module "$target" 0; then
                    success "Semantic hash cached for $target"
                    exit 0
                else
                    error "Failed to cache $target"
                    exit 1
                fi
            else
                # Cache all modules
                printf "${C_CYAN}Building semantic cache for all modules...${C_RESET}\n\n"
                local modules
                modules=$(get_all_modules)
                local success_count=0
                local fail_count=0
                local hit_count=0

                while IFS= read -r module; do
                    [[ -z "$module" ]] && continue
                    if cache_module "$module" 0; then
                        if is_cache_fresh "$module"; then
                            ((hit_count++))
                        else
                            ((success_count++))
                        fi
                    else
                        ((fail_count++))
                    fi
                done <<< "$modules"

                echo ""
                printf "Result: ${C_GREEN}%d hits${C_RESET} | ${C_GREEN}%d cached${C_RESET} | ${C_YELLOW}%d failed${C_RESET}\n" \
                    "$hit_count" "$success_count" "$fail_count"

                if [[ $fail_count -gt 0 ]]; then
                    printf "\n${C_YELLOW}Some modules failed. Use VERBOSE=1 for details:${C_RESET}\n"
                    printf "${C_CYAN}VERBOSE=1 bash scripts/build-semantic-cache.sh${C_RESET}\n"
                    exit 1
                fi
                exit 0
            fi
            ;;
    esac
}

main "$@"
