#!/usr/bin/env bash
# ==========================================================================
# scripts/manage-warm-cache.sh — Warm Module Bytecode Caching
#
# Keeps yawl-engine and yawl-elements compiled bytecode in warm cache,
# enabling skip-compilation on subsequent builds if code is unchanged.
#
# Mechanism:
#   1. After successful compile, copy compiled classes to .yawl/warm-cache/
#   2. On next build, check if cache is valid (hashes match)
#   3. If valid, symlink/copy cached classes to target/classes/
#   4. Skip Maven compilation for that module
#
# TTL: 8 hours or until pom.xml/dependencies change
# Validation: SHA256 hash of source + dependencies must match cached metadata
#
# Usage:
#   bash scripts/manage-warm-cache.sh save <module>        # Save compiled classes
#   bash scripts/manage-warm-cache.sh load <module>        # Load from cache if valid
#   bash scripts/manage-warm-cache.sh validate <module>    # Check cache validity
#   bash scripts/manage-warm-cache.sh info <module>        # Show cache info
#   bash scripts/manage-warm-cache.sh clean [<module>]     # Clean old entries
#   bash scripts/manage-warm-cache.sh stats                # Show cache statistics
#
# Environment:
#   WARM_CACHE_TTL_HOURS    TTL in hours (default: 8)
#   WARM_CACHE_MAX_SIZE     Max total size (default: 500MB)
#   WARM_CACHE_DEBUG=1      Show detailed output
# ==========================================================================
set -eu
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration with sensible defaults
WARM_CACHE_ROOT="${REPO_ROOT}/.yawl/warm-cache"
WARM_CACHE_TTL_HOURS="${WARM_CACHE_TTL_HOURS:-8}"
WARM_CACHE_MAX_SIZE_BYTES="${WARM_CACHE_MAX_SIZE_BYTES:-536870912}" # 500MB
WARM_CACHE_METADATA="${WARM_CACHE_ROOT}/metadata.json"

# Hot modules that benefit from warm caching
HOT_MODULES=("yawl-engine" "yawl-elements")

# Color codes for output
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# Ensure warm cache directory exists
mkdir -p "${WARM_CACHE_ROOT}"

# Initialize metadata file if needed
if [[ ! -f "${WARM_CACHE_METADATA}" ]]; then
    jq -n '{version: "1.0", created: (now | todate), cache_hits: 0, cache_misses: 0, modules: {}}' > "${WARM_CACHE_METADATA}"
fi

# ── Hash Functions ──────────────────────────────────────────────────────

# Compute combined hash of source + dependencies for a module
# Returns: SHA256 hash
compute_module_hash() {
    local module="$1"
    local temp_hash_input="/tmp/warm-cache-hash-${module}.txt"

    {
        # Hash all Java files in src/main/java
        if [[ -d "${module}/src/main/java" ]]; then
            find "${module}/src/main/java" -name "*.java" -type f -print0 2>/dev/null | \
                sort -z | xargs -0 cat 2>/dev/null || true
        fi

        # Hash pom.xml for dependency changes
        if [[ -f "${module}/pom.xml" ]]; then
            cat "${module}/pom.xml"
        fi

        # Hash parent pom for parent dependency changes
        if [[ -f "pom.xml" ]]; then
            cat "pom.xml"
        fi
    } > "$temp_hash_input"

    local hash
    if command -v sha256sum >/dev/null 2>&1; then
        hash=$(sha256sum "$temp_hash_input" | awk '{print $1}')
    else
        hash=$(md5sum "$temp_hash_input" | awk '{print $1}')
    fi

    rm -f "$temp_hash_input"
    echo "$hash"
}

# Get Java version for cache validation
get_java_version() {
    java -version 2>&1 | grep 'version "' | grep -oE '[0-9]+' | head -1
}

# ── Metadata Management ────────────────────────────────────────────────

# Update module metadata in cache manifest
update_module_metadata() {
    local module="$1"
    local status="$2"
    local hash="${3:-}"
    local size="${4:-0}"
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    local ttl_expires
    ttl_expires=$(date -u -d "+${WARM_CACHE_TTL_HOURS} hours" +"%Y-%m-%dT%H:%M:%SZ")

    local java_version
    java_version=$(get_java_version)

    # Create or update module entry
    local metadata
    metadata=$(jq \
        --arg mod "$module" \
        --arg st "$status" \
        --arg h "$hash" \
        --arg sz "$size" \
        --arg ts "$timestamp" \
        --arg ttl "$ttl_expires" \
        --arg jv "$java_version" \
        '.modules[$mod] = {
            status: $st,
            hash: $h,
            size: $sz,
            timestamp: $ts,
            ttl_expires: $ttl,
            java_version: $jv
        } | .updated = $ts' \
        "${WARM_CACHE_METADATA}")

    echo "$metadata" > "${WARM_CACHE_METADATA}"
}

# Get module metadata
get_module_metadata() {
    local module="$1"
    jq -r ".modules[\"$module\"] // empty" "${WARM_CACHE_METADATA}"
}

# ── Cache Operations ────────────────────────────────────────────────────

# Save compiled classes to warm cache
# Usage: save_to_cache "yawl-engine"
save_to_cache() {
    local module="$1"
    local module_cache_dir="${WARM_CACHE_ROOT}/${module}"
    local target_classes="${REPO_ROOT}/${module}/target/classes"

    [[ -d "$target_classes" ]] || {
        printf "${C_YELLOW}⊘${C_RESET} Module not compiled yet: %s\n" "$module" >&2
        return 1
    }

    mkdir -p "$module_cache_dir"

    # Compute hash of source + dependencies
    local module_hash
    module_hash=$(compute_module_hash "$module")

    # Create timestamped backup directory
    local cache_entry="${module_cache_dir}/classes-${module_hash}"
    mkdir -p "$cache_entry"

    # Copy compiled classes (preserve directory structure)
    printf "${C_CYAN}→${C_RESET} Saving bytecode for %s...\n" "$module" >&2
    cp -r "${target_classes}"/* "$cache_entry/" 2>/dev/null || {
        printf "${C_RED}✗${C_RESET} Failed to save cache for %s\n" "$module" >&2
        return 1
    }

    # Calculate size
    local size
    size=$(du -sb "$cache_entry" 2>/dev/null | awk '{print $1}') || size=0

    # Update metadata
    update_module_metadata "$module" "cached" "$module_hash" "$size"

    printf "${C_GREEN}✓${C_RESET} Cached %s (%.1f MB)\n" \
        "$module" "$(echo "scale=1; $size / 1048576" | bc 2>/dev/null || echo '0')" >&2

    # Trigger cleanup if cache is getting too large
    cleanup_if_needed >/dev/null 2>&1 || true

    return 0
}

# Load compiled classes from cache if valid
# Returns 0 if successfully loaded, 1 if cache invalid/missing
# Usage: if load_from_cache "yawl-engine"; then ... fi
load_from_cache() {
    local module="$1"
    local module_cache_dir="${WARM_CACHE_ROOT}/${module}"

    [[ -d "$module_cache_dir" ]] || {
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} No cache for %s\n" "$module" >&2
        return 1
    }

    # Validate cache
    if ! validate_cache "$module"; then
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} Cache invalid for %s\n" "$module" >&2
        return 1
    fi

    # Get current module hash
    local current_hash
    current_hash=$(compute_module_hash "$module")

    # Find cache entry matching current hash
    local cache_entry="${module_cache_dir}/classes-${current_hash}"

    [[ -d "$cache_entry" ]] || {
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} Cache entry mismatch for %s\n" "$module" >&2
        return 1
    }

    # Ensure target directory exists
    local target_classes="${REPO_ROOT}/${module}/target/classes"
    mkdir -p "$target_classes"

    # Clear and restore from cache
    rm -rf "${target_classes:?}"/*
    cp -r "$cache_entry"/* "$target_classes/" 2>/dev/null || {
        printf "${C_RED}✗${C_RESET} Failed to restore cache for %s\n" "$module" >&2
        return 1
    }

    # Update hit count in metadata
    jq '.cache_hits += 1' "${WARM_CACHE_METADATA}" > "${WARM_CACHE_METADATA}.tmp"
    mv "${WARM_CACHE_METADATA}.tmp" "${WARM_CACHE_METADATA}"

    printf "${C_GREEN}✓${C_RESET} Restored %s from warm cache\n" "$module" >&2
    return 0
}

# Validate cache for a module
# Returns 0 if valid and not expired, 1 otherwise
validate_cache() {
    local module="$1"
    local meta
    meta=$(get_module_metadata "$module")

    [[ -n "$meta" ]] || return 1

    # Check TTL expiration
    local ttl_expires
    ttl_expires=$(echo "$meta" | jq -r '.ttl_expires // empty')
    [[ -n "$ttl_expires" ]] || return 1

    local current_time
    current_time=$(date -u +%s)
    local expires_time
    expires_time=$(date -d "$ttl_expires" +%s 2>/dev/null) || return 1

    if [[ $current_time -gt $expires_time ]]; then
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} Cache expired for %s\n" "$module" >&2
        return 1
    fi

    # Check Java version match
    local cached_java_version
    cached_java_version=$(echo "$meta" | jq -r '.java_version // empty')
    local current_java_version
    current_java_version=$(get_java_version)

    if [[ "$cached_java_version" != "$current_java_version" ]]; then
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} Java version mismatch for %s (cached: %s, current: %s)\n" \
                "$module" "$cached_java_version" "$current_java_version" >&2
        return 1
    fi

    # Check hash match against current source
    local cached_hash
    cached_hash=$(echo "$meta" | jq -r '.hash // empty')
    local current_hash
    current_hash=$(compute_module_hash "$module")

    if [[ "$cached_hash" != "$current_hash" ]]; then
        [[ "${WARM_CACHE_DEBUG:-0}" == "1" ]] && \
            printf "${C_YELLOW}◇${C_RESET} Source/dependency hash mismatch for %s\n" "$module" >&2
        return 1
    fi

    return 0
}

# ── Cache Maintenance ───────────────────────────────────────────────────

# Clean up old cache entries
# Usage: cleanup_if_needed
cleanup_if_needed() {
    local total_size
    total_size=$(du -sb "${WARM_CACHE_ROOT}" 2>/dev/null | awk '{print $1}') || total_size=0

    # If under limit, nothing to do
    if [[ $total_size -le $WARM_CACHE_MAX_SIZE_BYTES ]]; then
        return 0
    fi

    # Remove expired entries first
    local current_time
    current_time=$(date -u +%s)
    local removed_count=0

    for module in "${HOT_MODULES[@]}"; do
        local meta
        meta=$(get_module_metadata "$module")
        [[ -n "$meta" ]] || continue

        local ttl_expires
        ttl_expires=$(echo "$meta" | jq -r '.ttl_expires // empty')
        [[ -n "$ttl_expires" ]] || continue

        local expires_time
        expires_time=$(date -d "$ttl_expires" +%s 2>/dev/null) || continue

        if [[ $current_time -gt $expires_time ]]; then
            rm -rf "${WARM_CACHE_ROOT}/${module}"
            jq ".modules |= del(.[\"$module\"])" "${WARM_CACHE_METADATA}" > "${WARM_CACHE_METADATA}.tmp"
            mv "${WARM_CACHE_METADATA}.tmp" "${WARM_CACHE_METADATA}"
            ((removed_count++))
        fi
    done

    [[ ${removed_count} -gt 0 ]] && printf "${C_CYAN}Cache: Removed %d expired entries\n" ${removed_count} >&2

    # If still over limit, remove oldest entries per module
    total_size=$(du -sb "${WARM_CACHE_ROOT}" 2>/dev/null | awk '{print $1}') || total_size=0

    if [[ $total_size -gt $WARM_CACHE_MAX_SIZE_BYTES ]]; then
        for module in "${HOT_MODULES[@]}"; do
            local module_cache_dir="${WARM_CACHE_ROOT}/${module}"
            [[ -d "$module_cache_dir" ]] || continue

            # Remove oldest entry for this module
            local oldest
            oldest=$(find "$module_cache_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | \
                sort -n | head -1 | awk '{print $2}')

            [[ -n "$oldest" ]] && rm -rf "$oldest"
        done
    fi
}

# ── Utility Commands ────────────────────────────────────────────────────

# Show cache information for a module
show_module_info() {
    local module="$1"
    local meta
    meta=$(get_module_metadata "$module")

    if [[ -z "$meta" ]]; then
        printf "${C_YELLOW}No cache entry for %s\n" "$module"
        return 0
    fi

    printf "\n${C_CYAN}Warm Cache Info: %s${C_RESET}\n" "$module"
    printf "  Status:       %s\n" "$(echo "$meta" | jq -r '.status')"
    printf "  Size:         %s MB\n" "$(echo "$meta" | jq -r '.size | . / 1048576 | floor')"
    printf "  Hash:         %s\n" "$(echo "$meta" | jq -r '.hash' | cut -c1-16)..."
    printf "  Created:      %s\n" "$(echo "$meta" | jq -r '.timestamp')"
    printf "  Expires:      %s\n" "$(echo "$meta" | jq -r '.ttl_expires')"
    printf "  Java Version: %s\n\n" "$(echo "$meta" | jq -r '.java_version')"
}

# Show cache statistics
show_stats() {
    printf "\n${C_CYAN}═══════════════════════════════════════════${C_RESET}\n"
    printf "${C_CYAN}Warm Module Cache Statistics${C_RESET}\n"
    printf "${C_CYAN}═══════════════════════════════════════════${C_RESET}\n\n"

    # Read stats from metadata
    local stats
    stats=$(jq '{hits: .cache_hits, misses: .cache_misses, modules: (.modules | length)}' "${WARM_CACHE_METADATA}")

    local hits
    local misses
    local module_count
    hits=$(echo "$stats" | jq '.hits')
    misses=$(echo "$stats" | jq '.misses')
    module_count=$(echo "$stats" | jq '.modules')

    local hit_rate=0
    if [[ $((hits + misses)) -gt 0 ]]; then
        hit_rate=$((hits * 100 / (hits + misses)))
    fi

    printf "Cache Directory:  %s\n" "${WARM_CACHE_ROOT}"
    printf "Hit Rate:         %d%% (%d hits, %d misses)\n" "$hit_rate" "$hits" "$misses"
    printf "Modules Cached:   %d / ${#HOT_MODULES[@]}\n\n" "$module_count"

    # Size analysis
    local total_size
    total_size=$(du -sb "${WARM_CACHE_ROOT}" 2>/dev/null | awk '{print $1}') || total_size=0
    local max_size_mb=$((WARM_CACHE_MAX_SIZE_BYTES / 1048576))
    local current_size_mb=$((total_size / 1048576))

    printf "Total Size:       %d / %d MB (%.0f%%)\n\n" \
        "$current_size_mb" "$max_size_mb" \
        "$(echo "scale=0; $total_size * 100 / $WARM_CACHE_MAX_SIZE_BYTES" | bc 2>/dev/null || echo '0')"

    # Per-module breakdown
    printf "${C_CYAN}Per-Module Breakdown:${C_RESET}\n"
    for module in "${HOT_MODULES[@]}"; do
        local meta
        meta=$(get_module_metadata "$module")

        if [[ -n "$meta" ]]; then
            local size
            size=$(echo "$meta" | jq '.size')
            local hash
            hash=$(echo "$meta" | jq -r '.hash' | cut -c1-12)
            printf "  %-20s %6d MB  [hash: %s...]\n" "$module:" "$((size / 1048576))" "$hash"
        else
            printf "  %-20s %6s   (no cache)\n" "$module:" "—"
        fi
    done

    printf "\n${C_CYAN}═══════════════════════════════════════════${C_RESET}\n\n"
}

# ── Main Command Dispatcher ────────────────────────────────────────────

main() {
    local command="${1:-help}"

    case "$command" in
        save)
            [[ -n "${2:-}" ]] || { echo "Usage: $0 save <module>"; exit 1; }
            save_to_cache "$2"
            ;;
        load)
            [[ -n "${2:-}" ]] || { echo "Usage: $0 load <module>"; exit 1; }
            load_from_cache "$2"
            ;;
        validate)
            [[ -n "${2:-}" ]] || { echo "Usage: $0 validate <module>"; exit 1; }
            if validate_cache "$2"; then
                printf "${C_GREEN}✓${C_RESET} Cache valid for %s\n" "$2"
                exit 0
            else
                printf "${C_RED}✗${C_RESET} Cache invalid for %s\n" "$2"
                exit 1
            fi
            ;;
        info)
            [[ -n "${2:-}" ]] || { echo "Usage: $0 info <module>"; exit 1; }
            show_module_info "$2"
            ;;
        clean)
            cleanup_if_needed
            printf "${C_GREEN}✓${C_RESET} Cache cleanup complete\n"
            ;;
        stats)
            show_stats
            ;;
        help|-h)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            ;;
        *)
            echo "Unknown command: $command"
            echo "Use: $0 help"
            exit 1
            ;;
    esac
}

main "$@"
