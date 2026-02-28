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

    # Extract import statements (sorted, order-independent)
    # Include both regular and static imports, deduplicate
    local imports
    imports=$(grep -E '^import (static )?' "$java_file" 2>/dev/null | \
        sed 's/^import //' | sed 's/;$//' | \
        LC_ALL=C sort -u | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]')

    # Extract module declaration (if Java 9+)
    local module_decl
    module_decl=$(grep -E '^module\s+' "$java_file" 2>/dev/null | head -1 | sed 's/;$//' || echo "")

    # Extract class/interface/enum/record declarations with full signatures
    # Includes: modifiers, name, parent class, interfaces, type parameters, sealed list
    local class_decls
    class_decls=$(extract_class_declarations "$java_file")

    # Extract method signatures (all visibility levels, skip body, include throws)
    # Pattern: visibility returnType methodName(params) throws ...
    local method_sigs
    method_sigs=$(extract_method_signatures "$java_file")

    # Extract field declarations (all visibility + modifiers)
    # Include: visibility, static, final, type, name (not initializers)
    local field_decls
    field_decls=$(extract_field_declarations "$java_file")

    # Extract type annotations and class-level annotations (before class declaration)
    local annotations
    annotations=$(extract_type_annotations "$java_file")

    # Extract record components (for record declarations)
    local record_components
    record_components=$(extract_record_components "$java_file")

    # Construct canonical JSON (deterministic key order, sorted)
    local semantic_json
    semantic_json=$(cat <<EOF
{
  "annotations": $annotations,
  "classes": $class_decls,
  "fields": $field_decls,
  "file": "$(basename "$java_file")",
  "imports": $imports,
  "methods": $method_sigs,
  "module": "$module_decl",
  "package": "$package_name",
  "records": $record_components
}
EOF
)

    # Return canonical JSON (no extra whitespace, sorted keys)
    echo "$semantic_json" | jq -S -c '.'
}

# Extract class/interface/enum/record declarations with full signatures
extract_class_declarations() {
    local java_file="$1"

    grep -E '^\s*(public\s+)?(abstract\s+)?(final\s+)?(sealed\s+)?(class|interface|enum|record)\s+' "$java_file" 2>/dev/null | \
        sed 's/^[[:space:]]*//;s/\s*[{].*$//;s/\s\+/ /g' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]'
}

# Extract method signatures including modifiers and exceptions
extract_method_signatures() {
    local java_file="$1"

    # Capture: visibility + optional (synchronized|static|default|native) + returnType + name + params + optional throws
    grep -E '^\s+(public|protected|private|static|final|synchronized|default|native|abstract)\s+' "$java_file" 2>/dev/null | \
        grep -E '(void|[A-Z][a-zA-Z0-9<>.*\[\]?]*|boolean|int|long|double|float|char|byte|short)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(' | \
        sed 's/^[[:space:]]*//;s/\s*[{;].*$//' | \
        sed 's/\s\+/ /g' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]'
}

# Extract field declarations with visibility and modifiers
extract_field_declarations() {
    local java_file="$1"

    # Capture: visibility/modifiers + type + name (but not initializers)
    grep -E '^\s+(public|protected|private|static|final|transient|volatile)\s+' "$java_file" 2>/dev/null | \
        grep -vE '\s+\{' | \
        sed 's/^[[:space:]]*//;s/\s*[=;].*$//;s/\s\+/ /g' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]'
}

# Extract type annotations and class-level annotations
extract_type_annotations() {
    local java_file="$1"

    # Match lines starting with @ (annotations) and capture name
    grep -E '^\s*@[A-Za-z]' "$java_file" 2>/dev/null | \
        sed 's/^[[:space:]]*@//' | \
        sed 's/\(.*\)$/\1/' | \
        LC_ALL=C sort -u | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]'
}

# Extract record component declarations (Java 16+)
extract_record_components() {
    local java_file="$1"

    # Record components are between 'record ClassName(' and ')'
    # Pattern: type name, type name, ...
    grep -E 'record\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(' "$java_file" 2>/dev/null | \
        sed 's/^.*\(//;s/).*//' | \
        sed 's/,/\n/g' | \
        sed 's/^\s*//;s/\s*$//' | \
        LC_ALL=C sort | \
        jq -R -s -c 'split("\n") | map(select(length > 0))' || echo '[]'
}

# Compute hash of semantic structure
# Input: semantic JSON string
# Output: hash hex string
compute_hash() {
    local semantic_json="$1"

    # Try blake3 first (faster, better for caching)
    if command -v b3sum >/dev/null 2>&1; then
        echo -n "$semantic_json" | b3sum | awk '{print $1}'
    elif command -v blake3sum >/dev/null 2>&1; then
        echo -n "$semantic_json" | blake3sum | awk '{print $1}'
    else
        # Fallback to sha256 (universal, slower)
        echo -n "$semantic_json" | sha256sum | awk '{print $1}'
    fi
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
    # Collect all file semantics and hashes in order for deterministic module hash
    local -a semantic_lines=()

    # Process each file and collect hashes (in sorted order for determinism)
    while IFS= read -r java_file; do
        [[ -z "$java_file" ]] && continue

        local semantic
        semantic=$(extract_semantic_structure "$java_file" 2>/dev/null) || continue

        local file_hash
        file_hash=$(compute_hash "$semantic")

        local relative_path
        relative_path=$(echo "$java_file" | sed "s|^${REPO_ROOT}/||")

        # Collect semantics for module-level hash (deterministic order via sorted java_files)
        semantic_lines+=("${file_hash}")
        ((file_count++))

        # Track file hash
        file_hashes=$(echo "$file_hashes" | jq \
            --arg file "$relative_path" \
            --arg hash "$file_hash" \
            '. += [{"file": $file, "hash": $hash}]')

        log "File: $relative_path → $file_hash"

    done <<< "$java_files"

    # Compute module-level hash: hash of concatenated file hashes (deterministic)
    local module_hash
    if [[ $file_count -gt 0 ]]; then
        # Join all file hashes with newlines for canonical representation
        local combined
        combined=$(printf '%s\n' "${semantic_lines[@]}")
        module_hash=$(compute_hash "$combined")
    else
        module_hash=$(compute_hash "")
    fi

    # Detect hash algorithm used
    local algo_used="sha256"
    if command -v b3sum >/dev/null 2>&1 || command -v blake3sum >/dev/null 2>&1; then
        algo_used="blake3"
    fi

    # Construct result JSON
    local result
    result=$(cat <<EOF
{
  "algorithm": "$algo_used",
  "file_count": $file_count,
  "files": $file_hashes,
  "hash": "$module_hash",
  "module": "$module",
  "status": "computed",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
)

    echo "$result" | jq -S -c '.'
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
