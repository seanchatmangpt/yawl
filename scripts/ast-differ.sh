#!/usr/bin/env bash
# ==========================================================================
# ast-differ.sh — Semantic Diff Tool for Java Source Changes
#
# Compares two versions of Java files at the semantic level, detecting
# actual code changes while ignoring formatting, comments, and whitespace.
#
# Usage:
#   bash scripts/ast-differ.sh <module> [--since <commit>|--cached]
#   bash scripts/ast-differ.sh <module> --file <path>
#   bash scripts/ast-differ.sh <module> --impact-graph
#
# Output:
#   JSON with changed classes, methods, fields, and affected tests
#
# Environment:
#   VERBOSE=1          Show detailed diff output
#   SHOW_IMPACT=1      Compute affected tests
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
readonly VERBOSE="${VERBOSE:-0}"
readonly SHOW_IMPACT="${SHOW_IMPACT:-0}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# ============================================================================
# Core Functions
# ============================================================================

log() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${C_CYAN}[DIFFER]${C_RESET} $*" >&2
    fi
}

error() {
    echo -e "${C_RED}[DIFFER]${C_RESET} ERROR: $*" >&2
}

# Extract semantic fingerprint from Java file at a specific commit
extract_semantic_at_commit() {
    local file_path="$1"
    local commit="${2:-HEAD}"

    # Get file content at commit
    local content
    content=$(git show "${commit}:${file_path}" 2>/dev/null) || return 1

    # Extract package
    local package_name
    package_name=$(echo "$content" | grep -E '^package ' | head -1 | sed 's/^package //;s/;$//' || echo "")

    # Extract imports
    local imports
    imports=$(echo "$content" | grep -E '^import ' | sed 's/^import //;s/;$//' | LC_ALL=C sort | jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract class declarations
    local classes
    classes=$(echo "$content" | grep -E '^\s*(public\s+)?(abstract\s+)?(final\s+)?(sealed\s+)?(class|interface|enum|record)\s+' | \
        sed 's/^[[:space:]]*//;s/{$//' | LC_ALL=C sort | jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Extract public method signatures
    local methods
    methods=$(echo "$content" | grep -E '^\s+(public|protected)\s+' | \
        grep -E '(void|[A-Z][a-zA-Z0-9<>.*\[\]]*)\s+[a-zA-Z_][a-zA-Z0-9_]*\s*\(' | \
        sed 's/^[[:space:]]*//;s/{.*$//' | sed 's/\s\+/ /g' | LC_ALL=C sort | \
        jq -R -s -c 'split("\n")[:-1]' || echo '[]')

    # Construct JSON
    local result
    result=$(cat <<EOF
{
  "package": "$package_name",
  "imports": $imports,
  "classes": $classes,
  "methods": $methods,
  "file": "$(basename "$file_path")"
}
EOF
)

    echo "$result" | jq -c '.'
}

# Detect which Java files changed between commits
detect_changed_files() {
    local module="$1"
    local since="${2:-HEAD}"

    # Get all changed Java files in module
    git diff --name-only "${since}..HEAD" -- "${module}/src/main/java/*.java" 2>/dev/null | \
        grep -E '\.java$' || true
}

# Compute semantic diff between two versions
semantic_diff() {
    local file_path="$1"
    local old_commit="${2:-HEAD~1}"
    local new_commit="${3:-HEAD}"

    log "Diffing: $file_path"

    # Get semantics at both commits
    local old_semantic
    local new_semantic

    old_semantic=$(extract_semantic_at_commit "$file_path" "$old_commit" 2>/dev/null) || old_semantic="{}"
    new_semantic=$(extract_semantic_at_commit "$file_path" "$new_commit" 2>/dev/null) || new_semantic="{}"

    # Compare classes
    local old_classes
    local new_classes
    old_classes=$(echo "$old_semantic" | jq '.classes | map(.) | sort' 2>/dev/null || echo '[]')
    new_classes=$(echo "$new_semantic" | jq '.classes | map(.) | sort' 2>/dev/null || echo '[]')

    # Compare methods
    local old_methods
    local new_methods
    old_methods=$(echo "$old_semantic" | jq '.methods | map(.) | sort' 2>/dev/null || echo '[]')
    new_methods=$(echo "$new_semantic" | jq '.methods | map(.) | sort' 2>/dev/null || echo '[]')

    # Compute added/removed classes and methods
    local added_classes
    local removed_classes
    local added_methods
    local removed_methods

    added_classes=$(echo "$new_classes $old_classes" | jq -s '.[0] - .[1]' || echo '[]')
    removed_classes=$(echo "$old_classes $new_classes" | jq -s '.[0] - .[1]' || echo '[]')
    added_methods=$(echo "$new_methods $old_methods" | jq -s '.[0] - .[1]' || echo '[]')
    removed_methods=$(echo "$old_methods $new_methods" | jq -s '.[0] - .[1]' || echo '[]')

    # Determine if changed
    local is_changed=false
    if [[ "$added_classes" != "[]" || "$removed_classes" != "[]" || "$added_methods" != "[]" || "$removed_methods" != "[]" ]]; then
        is_changed=true
    fi

    # Output diff result
    local result
    result=$(cat <<EOF
{
  "file": "$file_path",
  "changed": $is_changed,
  "added_classes": $added_classes,
  "removed_classes": $removed_classes,
  "added_methods": $added_methods,
  "removed_methods": $removed_methods,
  "old_semantic": $old_semantic,
  "new_semantic": $new_semantic
}
EOF
)

    echo "$result" | jq -c '.'
}

# Extract class name from method signature or class declaration
extract_class_name() {
    local declaration="$1"
    echo "$declaration" | sed -E 's/^[^[:space:]]+[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/' | head -1
}

# Find test files that import a changed class
find_affected_tests() {
    local changed_class="$1"
    local module="$2"

    # Search for test files that import this class
    find "${REPO_ROOT}/${module}/src/test/java" -name "*Test.java" -type f 2>/dev/null | while read -r test_file; do
        if grep -q "import.*${changed_class}" "$test_file" 2>/dev/null || \
           grep -q "${changed_class}" "$test_file" 2>/dev/null; then
            echo "$(basename "$test_file" .java)"
        fi
    done | sort -u || true
}

# Build impact graph: class changes → affected tests
build_impact_graph() {
    local module="$1"
    local diff_json="$2"

    local affected_tests="[]"

    # Extract changed classes
    local added_classes
    local removed_classes

    added_classes=$(echo "$diff_json" | jq '.added_classes[]' -r 2>/dev/null || true)
    removed_classes=$(echo "$diff_json" | jq '.removed_classes[]' -r 2>/dev/null || true)

    # Find tests for each changed class
    local tests_array="[]"

    while IFS= read -r class_decl; do
        [[ -z "$class_decl" ]] && continue
        local class_name
        class_name=$(extract_class_name "$class_decl")
        [[ -z "$class_name" ]] && continue

        local affected
        affected=$(find_affected_tests "$class_name" "$module")

        while IFS= read -r test; do
            [[ -z "$test" ]] && continue
            tests_array=$(echo "$tests_array" | jq \
                --arg test "$test" \
                --arg class "$class_name" \
                '.+=[{"test": $test, "triggers_for": $class}]' || echo '[]')
        done <<< "$affected"

    done <<< "$added_classes$removed_classes"

    echo "$tests_array" | jq -c '.'
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    local module="${1:-}"
    local action="${2:---since}"
    local param="${3:-HEAD~1}"

    if [[ -z "$module" ]]; then
        error "Usage: $0 <module> [--since <commit>|--cached|--file <path>]"
        exit 1
    fi

    case "$action" in
        --since)
            log "Detecting changes since: $param"
            local changed_files
            changed_files=$(git diff --name-only "$param..HEAD" -- "${module}/src/main/java/*.java" 2>/dev/null | \
                grep -E '\.java$' || echo "")

            local all_diffs="[]"

            while IFS= read -r file_path; do
                [[ -z "$file_path" ]] && continue

                local diff
                diff=$(semantic_diff "$file_path" "$param" "HEAD")

                all_diffs=$(echo "$all_diffs" | jq \
                    --argjson diff "$diff" \
                    '.+=$diff')

            done <<< "$changed_files"

            local result
            result=$(cat <<EOF
{
  "module": "$module",
  "since": "$param",
  "changed_files": $(echo "$changed_files" | jq -R -s 'split("\n")[:-1]'),
  "diffs": $all_diffs,
  "summary": {
    "files_changed": $(echo "$changed_files" | wc -l)
  }
}
EOF
)

            echo "$result" | jq '.'
            ;;

        --cached)
            log "Detecting staged changes"
            local staged_files
            staged_files=$(git diff --name-only --cached -- "${module}/src/main/java/*.java" 2>/dev/null | \
                grep -E '\.java$' || echo "")

            local all_diffs="[]"

            while IFS= read -r file_path; do
                [[ -z "$file_path" ]] && continue

                local diff
                diff=$(semantic_diff "$file_path" "HEAD" "")

                all_diffs=$(echo "$all_diffs" | jq \
                    --argjson diff "$diff" \
                    '.+=$diff')

            done <<< "$staged_files"

            local result
            result=$(cat <<EOF
{
  "module": "$module",
  "mode": "staged",
  "changed_files": $(echo "$staged_files" | jq -R -s 'split("\n")[:-1]'),
  "diffs": $all_diffs
}
EOF
)

            echo "$result" | jq '.'
            ;;

        --file)
            local file_path="$param"
            log "Computing diff for file: $file_path"

            local diff
            diff=$(semantic_diff "$file_path" "HEAD~1" "HEAD")

            if [[ "$SHOW_IMPACT" == "1" ]]; then
                local impact
                impact=$(build_impact_graph "$module" "$diff")

                echo "$diff" | jq --argjson impact "$impact" '.impact = $impact'
            else
                echo "$diff" | jq '.'
            fi
            ;;

        *)
            error "Unknown action: $action"
            exit 1
            ;;
    esac
}

main "$@"
