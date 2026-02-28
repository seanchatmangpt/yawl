#!/usr/bin/env bash
# ==========================================================================
# compute-semantic-hash.sh — Semantic Change Detection for YAWL Build
#
# Parses Java source files to extract semantic fingerprints and compute
# hashes based on structural elements (not formatting or comments).
# Enables fast cache validation by detecting true code changes vs formatting.
#
# Usage:
#   bash scripts/compute-semantic-hash.sh <module>          # compute hash for module
#   bash scripts/compute-semantic-hash.sh <module> --cache  # save to cache
#   bash scripts/compute-semantic-hash.sh <module> --compare <prev_hash>
#
# Output:
#   JSON with semantic hash, file count, and structural summary
#
# Environment:
#   SEMANTIC_HASH_ALGO=sha256     Hash algorithm (sha256 default, blake3 if available)
#   SEMANTIC_CACHE_DIR=.yawl/cache/semantic-hashes
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
readonly SEMANTIC_CACHE_DIR="${REPO_ROOT}/.yawl/cache/semantic-hashes"
readonly SEMANTIC_HASH_ALGO="${SEMANTIC_HASH_ALGO:-sha256}"
readonly VERBOSE="${VERBOSE:-0}"

# Ensure cache directory exists
mkdir -p "${SEMANTIC_CACHE_DIR}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# ============================================================================
# Core Functions
# ============================================================================

# Log function
log() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${C_CYAN}[SEMANTIC]${C_RESET} $*" >&2
    fi
}

# Error function
error() {
    echo -e "${C_YELLOW}[SEMANTIC]${C_RESET} ERROR: $*" >&2
}

# Extract semantic structure from a single Java file
# Outputs canonical JSON representation (sorted keys, no whitespace)
extract_semantic_structure() {
    local java_file="$1"

    # Validate file exists
    if [[ ! -f "$java_file" ]]; then
        echo "null"
        return 1
    fi

    # Extract package declaration
    local package_name
    package_name=$(grep -E '^package ' "$java_file" 2>/dev/null | head -1 | sed 's/^package //;s/;$//' || echo "")

    # Extract import statements (sorted)
    local imports
    imports=$(grep -E '^import ' "$java_file" 2>/dev/null | sed 's/^import //;s/;$//' | LC_ALL=C sort | jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract class/interface/enum/record declarations with modifiers
    local class_decls
    class_decls=$(grep -E '^\s*(public\s+)?(abstract\s+)?(final\s+)?(sealed\s+)?(class|interface|enum|record)\s+' "$java_file" 2>/dev/null | \
        sed 's/^[[:space:]]*//;s/{$//' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract method signatures (public/protected methods only, skip formatting)
    # Pattern: visibility returnType methodName(params) {throws ...}
    local method_sigs
    method_sigs=$(grep -E '^\s+(public|protected)\s+' "$java_file" 2>/dev/null | \
        grep -E '(void|[A-Z][a-zA-Z0-9<>.*\[\]]*)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(' | \
        sed 's/^[[:space:]]*//;s/{.*$//' | \
        sed 's/\s\+/ /g' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract field declarations (public/protected only)
    local field_decls
    field_decls=$(grep -E '^\s+(public|protected|private\s+static\s+final)\s+' "$java_file" 2>/dev/null | \
        grep -vE '\s+\{' | \
        sed 's/^[[:space:]]*//;s/;$//' | \
        sed 's/\s\+/ /g' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract annotations (top-level only, not method-level)
    local annotations
    annotations=$(grep -E '^@[A-Za-z]' "$java_file" 2>/dev/null | \
        sed 's/^@//' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Construct canonical JSON (deterministic key order)
    local semantic_json
    semantic_json=$(cat <<EOF
{
  "package": "$package_name",
  "imports": $imports,
  "classes": $class_decls,
  "methods": $method_sigs,
  "fields": $field_decls,
  "annotations": $annotations,
  "file": "$(basename "$java_file")"
}
EOF
)

    # Return canonical JSON (no extra whitespace)
    echo "$semantic_json" | jq -c '.'
}

# Compute hash of semantic structure
# Input: semantic JSON string
# Output: hash hex string
compute_hash() {
    local semantic_json="$1"

    # Use sha256 as universal fallback (blake3 not in standard coreutils)
    echo -n "$semantic_json" | sha256sum | awk '{print $1}'
}

# Collect all Java files from module source directories
# Input: module name
# Output: newline-separated file paths
collect_java_files() {
    local module="$1"

    # Collect from src/main/java (primary sources)
    find "${REPO_ROOT}/${module}/src/main/java" -name "*.java" -type f 2>/dev/null | \
        LC_ALL=C sort || true
}

# Compute semantic fingerprint for entire module
# Input: module name
# Output: JSON object with module hash and file details
compute_module_semantic_hash() {
    local module="$1"

    if [[ ! -d "${REPO_ROOT}/${module}" ]]; then
        error "Module not found: ${module}"
        return 1
    fi

    local java_files
    java_files=$(collect_java_files "$module")

    local file_count=0
    local file_hashes="[]"
    local all_semantics=""

    # Process each file and collect hashes
    while IFS= read -r java_file; do
        [[ -z "$java_file" ]] && continue

        ((file_count++))

        local semantic
        semantic=$(extract_semantic_structure "$java_file" 2>/dev/null) || continue

        local file_hash
        file_hash=$(compute_hash "$semantic")

        local relative_path
        relative_path=$(echo "$java_file" | sed "s|^${REPO_ROOT}/||")

        # Accumulate semantics for module-level hash
        all_semantics+="${semantic}${file_hash}"

        # Track file hash
        file_hashes=$(echo "$file_hashes" | jq \
            --arg file "$relative_path" \
            --arg hash "$file_hash" \
            '. += [{"file": $file, "hash": $hash}]')

        log "File: $relative_path → $file_hash"

    done <<< "$java_files"

    # Compute module-level hash (hash of all file hashes in order)
    local module_hash
    if [[ $file_count -gt 0 ]]; then
        module_hash=$(compute_hash "${all_semantics}")
    else
        module_hash=$(compute_hash "")
    fi

    # Construct result JSON
    local result
    result=$(cat <<EOF
{
  "module": "$module",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "algorithm": "$SEMANTIC_HASH_ALGO",
  "hash": "$module_hash",
  "file_count": $file_count,
  "files": $file_hashes,
  "status": "computed"
}
EOF
)

    echo "$result" | jq -c '.'
}

# Load cached semantic hash for module
load_cached_hash() {
    local module="$1"
    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"

    if [[ -f "$cache_file" ]]; then
        cat "$cache_file"
    else
        echo "null"
    fi
}

# Save semantic hash to cache
save_to_cache() {
    local module="$1"
    local semantic_json="$2"

    local cache_file="${SEMANTIC_CACHE_DIR}/${module}.json"
    echo "$semantic_json" | jq '.' > "${cache_file}.tmp"
    mv "${cache_file}.tmp" "$cache_file"

    log "Cached: $cache_file"
}

# Compare two semantic hashes
# Input: current hash, previous hash
# Output: JSON object with comparison result
compare_hashes() {
    local current="$1"
    local previous="$2"

    if [[ "$previous" == "null" || -z "$previous" ]]; then
        # No previous hash (first run)
        echo '{"changed": true, "reason": "no_previous_hash"}'
        return 0
    fi

    local current_hash
    local previous_hash

    current_hash=$(echo "$current" | jq -r '.hash // empty' 2>/dev/null) || current_hash=""
    previous_hash=$(echo "$previous" | jq -r '.hash // empty' 2>/dev/null) || previous_hash=""

    if [[ "$current_hash" == "$previous_hash" ]]; then
        echo '{"changed": false, "reason": "hash_match"}'
    else
        echo '{"changed": true, "reason": "hash_mismatch"}'
    fi
}

# Detect which classes/methods changed between two semantic hashes
# Input: current semantic JSON, previous semantic JSON
# Output: JSON with changed items
detect_changes() {
    local current="$1"
    local previous="$2"

    # Compare file-level changes
    local current_files
    local previous_files

    current_files=$(echo "$current" | jq '.files | map(.hash) | sort' 2>/dev/null) || current_files="[]"
    previous_files=$(echo "$previous" | jq '.files | map(.hash) | sort' 2>/dev/null) || previous_files="[]"

    # Simple comparison: if file hashes differ, changes detected
    if [[ "$current_files" == "$previous_files" ]]; then
        echo '{"changed": false, "changed_files": [], "changed_classes": []}'
    else
        # Extract changed files
        local changed_files
        changed_files=$(echo "$current" | jq '.files[] | select(.hash != .previous_hash) | .file' 2>/dev/null || echo '[]')

        echo "{\"changed\": true, \"changed_files\": $changed_files, \"changed_classes\": []}"
    fi
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    local module="${1:-}"
    local action="${2:-compute}"
    local compare_hash="${3:-}"

    if [[ -z "$module" ]]; then
        error "Usage: $0 <module> [--cache|--compare <prev_hash>]"
        exit 1
    fi

    log "Computing semantic hash for module: $module"

    # Compute current semantic hash
    local current_semantic
    current_semantic=$(compute_module_semantic_hash "$module" || echo "null")

    if [[ "$current_semantic" == "null" ]]; then
        error "Failed to compute semantic hash for $module"
        exit 1
    fi

    case "$action" in
        --cache)
            # Compute and cache
            save_to_cache "$module" "$current_semantic"
            echo "$current_semantic" | jq '.'
            ;;
        --compare)
            # Compare with previous hash
            if [[ -z "$compare_hash" ]]; then
                compare_hash=$(load_cached_hash "$module")
            fi

            local comparison
            comparison=$(compare_hashes "$current_semantic" "$compare_hash")

            # Output combined result
            local result
            result=$(echo "$current_semantic" | jq \
                --argjson comparison "$comparison" \
                '.comparison = $comparison')

            echo "$result" | jq '.'
            ;;
        *)
            # Default: just compute and output
            echo "$current_semantic" | jq '.'
            ;;
    esac
}

main "$@"
