#!/usr/bin/env bash
# ==========================================================================
# cache-config.sh — Test Result Caching Infrastructure for YAWL
#
# Provides cache lookup, validation, and management functions for the
# test result caching system. Enables 30% reduction in build times on
# warm builds by avoiding re-running tests with unchanged dependencies.
#
# Usage:
#   source .mvn/cache-config.sh
#   if cache_is_valid "$module"; then
#       result=$(cache_get_result "$module")
#   else
#       # run tests and cache result
#   fi
#
# Environment:
#   YAWL_CACHE_ROOT    Path to cache root (default: .yawl/cache)
#   YAWL_CACHE_TTL_HOURS TTL in hours (default: 24)
#   YAWL_CACHE_MAX_SIZE Max cache size in bytes (default: 5GB)
#   YAWL_CACHE_ENTRIES Per module (default: 50)
# ==========================================================================
set -euo pipefail

# Configuration with sensible defaults
export YAWL_CACHE_ROOT="${YAWL_CACHE_ROOT:-.yawl/cache}"
export YAWL_CACHE_TEST_RESULTS="${YAWL_CACHE_ROOT}/test-results"
export YAWL_CACHE_TTL_HOURS="${YAWL_CACHE_TTL_HOURS:-24}"
export YAWL_CACHE_MAX_SIZE_BYTES="${YAWL_CACHE_MAX_SIZE_BYTES:-5368709120}" # 5GB
export YAWL_CACHE_MAX_ENTRIES_PER_MODULE="${YAWL_CACHE_MAX_ENTRIES_PER_MODULE:-50}"

# Ensure cache directory exists
mkdir -p "${YAWL_CACHE_TEST_RESULTS}"

# ── Hash Functions ───────────────────────────────────────────────────────

# Compute Blake3 hash of a file (with fallback to SHA256 if b3sum unavailable)
# Usage: hash=$(compute_file_hash /path/to/file)
# Returns: 64-char hex hash
compute_file_hash() {
    local file="$1"
    if [[ ! -f "$file" ]]; then
        echo "ERROR: file not found: $file" >&2
        return 1
    fi

    if command -v b3sum >/dev/null 2>&1; then
        b3sum "$file" | awk '{print $1}'
    elif command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    else
        # Fallback: use md5 if available (less ideal but works everywhere)
        md5sum "$file" | awk '{print $1}'
    fi
}

# Compute hash of multiple files
# Usage: combined_hash=$(compute_multi_hash file1 file2 file3)
# Returns: Single hash representing all files
compute_multi_hash() {
    local combined=""
    for file in "$@"; do
        if [[ -f "$file" ]]; then
            combined+="$(compute_file_hash "$file")"
        fi
    done

    if [[ -z "$combined" ]]; then
        echo "ERROR: No files found for hashing" >&2
        return 1
    fi

    # Hash the concatenated hashes
    echo -n "$combined" | {
        if command -v b3sum >/dev/null 2>&1; then
            b3sum | awk '{print $1}'
        elif command -v sha256sum >/dev/null 2>&1; then
            sha256sum | awk '{print $1}'
        else
            md5sum | awk '{print $1}'
        fi
    }
}

# Compute hash of source directory (all .java files)
# Usage: src_hash=$(compute_source_hash "yawl-engine")
compute_source_hash() {
    local module="$1"
    local src_dir

    # Try src/main/java first, fall back to src/java (for test modules)
    if [[ -d "${module}/src/main/java" ]]; then
        src_dir="${module}/src/main/java"
    elif [[ -d "${module}/src/java" ]]; then
        src_dir="${module}/src/java"
    elif [[ -d "${module}/src" ]]; then
        src_dir="${module}/src"
    else
        echo "ERROR: source directory not found for module: $module" >&2
        return 1
    fi

    # Hash all Java files in source directory
    find "$src_dir" -name "*.java" -type f -print0 2>/dev/null | \
        sort -z | \
        xargs -0 cat 2>/dev/null | \
        {
            if command -v b3sum >/dev/null 2>&1; then
                b3sum | awk '{print $1}'
            elif command -v sha256sum >/dev/null 2>&1; then
                sha256sum | awk '{print $1}'
            else
                md5sum | awk '{print $1}'
            fi
        }
}

# Compute combined hash of all test configuration files
# Usage: config_hash=$(compute_test_config_hash "yawl-engine")
compute_test_config_hash() {
    local module="$1"
    local temp_config="/tmp/test-config-${module}.txt"

    # Collect all test-related configuration
    {
        # pom.xml test configuration (surefire section)
        if [[ -f "${module}/pom.xml" ]]; then
            grep -A50 "<plugin>" "${module}/pom.xml" | \
                grep -A40 "maven-surefire-plugin" || true
        fi

        # junit-platform.properties
        if [[ -f "test/resources/junit-platform.properties" ]]; then
            cat "test/resources/junit-platform.properties"
        fi

        # .mvn/maven.config (test-related settings)
        if [[ -f ".mvn/maven.config" ]]; then
            grep -E "(junit|surefire|test)" ".mvn/maven.config" || true
        fi
    } > "$temp_config"

    local config_hash
    config_hash=$(compute_file_hash "$temp_config")
    rm -f "$temp_config"
    echo "$config_hash"
}

# Compute combined dependency hash from pom.xml and parent pom
# Usage: dep_hash=$(compute_dependency_hash "yawl-engine")
compute_dependency_hash() {
    local module="$1"
    local temp_deps="/tmp/deps-${module}.txt"

    # Collect dependency declarations
    {
        # All dependency declarations in pom
        if [[ -f "${module}/pom.xml" ]]; then
            grep -E "<(dependency|parent)>" "${module}/pom.xml" | \
                grep -A2 "<dependency>" || true
        fi

        # Parent pom dependencies
        if [[ -f "pom.xml" ]]; then
            grep -E "<dependency>" "pom.xml" | grep -A2 "<dependency>" || true
        fi
    } > "$temp_deps"

    local dep_hash
    dep_hash=$(compute_file_hash "$temp_deps")
    rm -f "$temp_deps"
    echo "$dep_hash"
}

# ── Cache Key Generation ─────────────────────────────────────────────────

# Generate cache key for module test results
# Format: {source_hash}-{dep_hash}-{config_hash}.json
# Usage: cache_key=$(cache_generate_key "yawl-engine")
cache_generate_key() {
    local module="$1"

    local src_hash
    src_hash=$(compute_source_hash "$module") || return 1

    local dep_hash
    dep_hash=$(compute_dependency_hash "$module") || return 1

    local config_hash
    config_hash=$(compute_test_config_hash "$module") || return 1

    # Return combined key (truncate to 16 chars per component for readability)
    echo "${src_hash:0:16}-${dep_hash:0:16}-${config_hash:0:16}.json"
}

# ── Cache Operations ─────────────────────────────────────────────────────

# Check if cache entry exists and is valid
# Returns 0 (valid) or 1 (invalid/missing)
# Usage: if cache_is_valid "yawl-engine"; then ...
cache_is_valid() {
    local module="$1"
    local cache_key
    cache_key=$(cache_generate_key "$module") || return 1

    local cache_file="${YAWL_CACHE_TEST_RESULTS}/${module}/${cache_key}"

    # Entry must exist
    [[ -f "$cache_file" ]] || return 1

    # Parse TTL and check expiration
    local ttl_expires
    ttl_expires=$(jq -r '.ttl_expires' "$cache_file" 2>/dev/null) || return 1

    local current_time
    current_time=$(date -u +%s)
    local expires_time
    expires_time=$(date -d "$ttl_expires" +%s 2>/dev/null) || return 1

    # Valid if not expired
    [[ $current_time -lt $expires_time ]]
}

# Retrieve cached test results
# Usage: result=$(cache_get_result "yawl-engine")
# Returns: JSON with test_results
cache_get_result() {
    local module="$1"
    local cache_key
    cache_key=$(cache_generate_key "$module") || return 1

    local cache_file="${YAWL_CACHE_TEST_RESULTS}/${module}/${cache_key}"

    if [[ ! -f "$cache_file" ]]; then
        echo "ERROR: cache file not found: $cache_file" >&2
        return 1
    fi

    cat "$cache_file"
}

# Store test results in cache
# Usage: cache_store_result "yawl-engine" "$test_results_json"
# Where test_results_json has structure:
#   {
#     "passed": N, "failed": 0, "skipped": K, "duration_ms": M
#   }
cache_store_result() {
    local module="$1"
    local test_results="$2"
    local cache_key
    cache_key=$(cache_generate_key "$module") || return 1

    local module_cache_dir="${YAWL_CACHE_TEST_RESULTS}/${module}"
    mkdir -p "$module_cache_dir"

    local cache_file="${module_cache_dir}/${cache_key}"

    # Build complete cache entry
    local src_hash
    src_hash=$(compute_source_hash "$module") || return 1
    local dep_hash
    dep_hash=$(compute_dependency_hash "$module") || return 1
    local config_hash
    config_hash=$(compute_test_config_hash "$module") || return 1

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local ttl_expires
    ttl_expires=$(date -u -d "+${YAWL_CACHE_TTL_HOURS} hours" +"%Y-%m-%dT%H:%M:%SZ")

    # Create complete cache entry
    local entry
    entry=$(jq -n \
        --arg code_hash "$src_hash" \
        --arg dep_hash "$dep_hash" \
        --arg test_config_hash "$config_hash" \
        --arg timestamp "$timestamp" \
        --arg ttl_expires "$ttl_expires" \
        --argjson test_results "$test_results" \
        '{code_hash: $code_hash, dependency_hashes: [$dep_hash], test_config_hash: $test_config_hash, timestamp: $timestamp, test_results: $test_results, ttl_expires: $ttl_expires}')

    echo "$entry" > "$cache_file"

    # Trigger cleanup if cache is getting too large
    cache_cleanup_if_needed
}

# ── Cache Cleanup ────────────────────────────────────────────────────────

# Check if cleanup is needed and run if cache exceeds size limit
# Keeps last YAWL_CACHE_MAX_ENTRIES_PER_MODULE entries per module
cache_cleanup_if_needed() {
    local total_size
    total_size=$(du -sb "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || total_size=0

    if [[ $total_size -gt $YAWL_CACHE_MAX_SIZE_BYTES ]]; then
        cache_cleanup_lru
    fi

    # Also enforce per-module limits
    for module_dir in "${YAWL_CACHE_TEST_RESULTS}"/*; do
        [[ -d "$module_dir" ]] || continue
        local entry_count
        entry_count=$(find "$module_dir" -name "*.json" -type f | wc -l)
        if [[ $entry_count -gt $YAWL_CACHE_MAX_ENTRIES_PER_MODULE ]]; then
            cache_cleanup_module_lru "$(basename "$module_dir")"
        fi
    done
}

# LRU cleanup: Keep only latest YAWL_CACHE_MAX_ENTRIES_PER_MODULE entries per module
# Removes oldest entries by modification time when exceeding limit
cache_cleanup_module_lru() {
    local module="$1"
    local module_dir="${YAWL_CACHE_TEST_RESULTS}/${module}"

    [[ -d "$module_dir" ]] || return 0

    local entry_count
    entry_count=$(find "$module_dir" -name "*.json" -type f | wc -l)
    if [[ $entry_count -le $YAWL_CACHE_MAX_ENTRIES_PER_MODULE ]]; then
        return 0
    fi

    # Remove oldest entries
    local remove_count=$((entry_count - YAWL_CACHE_MAX_ENTRIES_PER_MODULE))
    find "$module_dir" -name "*.json" -type f -printf '%T@ %p\n' | \
        sort -n | \
        head -n "$remove_count" | \
        awk '{print $2}' | \
        xargs rm -f
}

# Full LRU cleanup across entire cache
# Removes oldest entries globally until cache size is under limit
cache_cleanup_lru() {
    local target_size=$((YAWL_CACHE_MAX_SIZE_BYTES / 2)) # Target 50% of max
    local current_size
    current_size=$(du -sb "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || current_size=0

    while [[ $current_size -gt $target_size ]]; do
        # Find oldest entry across entire cache
        local oldest
        oldest=$(find "${YAWL_CACHE_TEST_RESULTS}" -name "*.json" -type f \
            -printf '%T@ %p\n' | sort -n | head -1 | awk '{print $2}')

        if [[ -z "$oldest" ]]; then
            break
        fi

        rm -f "$oldest"
        current_size=$(du -sb "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || current_size=0
    done
}

# ── Cache Statistics ─────────────────────────────────────────────────────

# Show cache statistics
# Usage: cache_stats
cache_stats() {
    echo "Cache Statistics:"
    echo "  Location: ${YAWL_CACHE_TEST_RESULTS}"

    local total_entries
    total_entries=$(find "${YAWL_CACHE_TEST_RESULTS}" -name "*.json" -type f 2>/dev/null | wc -l) || total_entries=0
    echo "  Total entries: ${total_entries}"

    local total_size
    total_size=$(du -sh "${YAWL_CACHE_TEST_RESULTS}" 2>/dev/null | awk '{print $1}') || total_size="0B"
    echo "  Total size: ${total_size}"

    echo ""
    echo "  Per-module breakdown:"
    for module_dir in "${YAWL_CACHE_TEST_RESULTS}"/*; do
        [[ -d "$module_dir" ]] || continue
        local module
        module=$(basename "$module_dir")
        local count
        count=$(find "$module_dir" -name "*.json" -type f | wc -l)
        local size
        size=$(du -sh "$module_dir" | awk '{print $1}')
        printf "    %-30s %3d entries, %5s\n" "$module:" "$count" "$size"
    done
}

# Show cache hit/miss statistics
# Usage: cache_hitrate
cache_hitrate() {
    if [[ ! -f "${YAWL_CACHE_ROOT}/cache-manifest.json" ]]; then
        echo "No cache statistics available yet"
        return 0
    fi

    jq -r '.stats | "Cache Hit Rate: \(.hits / (.hits + .misses) * 100 | floor)% (\(.hits) hits, \(.misses) misses)"' \
        "${YAWL_CACHE_ROOT}/cache-manifest.json"
}

# Clear entire cache
# Usage: cache_clear
cache_clear() {
    rm -rf "${YAWL_CACHE_TEST_RESULTS:?}/"*
    echo "Cache cleared"
}

# ── Exports ──────────────────────────────────────────────────────────────

export -f compute_file_hash
export -f compute_multi_hash
export -f compute_source_hash
export -f compute_test_config_hash
export -f compute_dependency_hash
export -f cache_generate_key
export -f cache_is_valid
export -f cache_get_result
export -f cache_store_result
export -f cache_cleanup_if_needed
export -f cache_cleanup_module_lru
export -f cache_cleanup_lru
export -f cache_stats
export -f cache_hitrate
export -f cache_clear
