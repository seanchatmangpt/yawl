#!/usr/bin/env bash
# ==========================================================================
# build-test-impact-graph.sh — Test Impact Graph Builder
#
# Analyzes test dependencies and builds a bidirectional impact graph:
# - test_to_source: which production classes are tested by each test
# - source_to_tests: which tests cover each production class
#
# Output: .yawl/cache/test-impact-graph.json
#
# Usage:
#   bash scripts/build-test-impact-graph.sh          # rebuild cache
#   bash scripts/build-test-impact-graph.sh --force  # force rebuild
#   bash scripts/build-test-impact-graph.sh --verbose # show progress
#
# Environment:
#   IMPACT_FORCE=1      Force rebuild (ignore cache age)
#   IMPACT_VERBOSE=1    Show detailed progress
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ──────────────────────────────────────────────────────
CACHE_DIR="${REPO_ROOT}/.yawl/cache"
GRAPH_FILE="${CACHE_DIR}/test-impact-graph.json"
METADATA_FILE="${CACHE_DIR}/metadata.json"
TEMP_GRAPH="${CACHE_DIR}/.impact-graph.tmp.json"
JAVA_VERSION=$(java -version 2>&1 | grep 'version' | head -1 || echo "unknown")
MVN_VERSION=$(mvn --version 2>/dev/null | head -1 || echo "unknown")

# Parse arguments
FORCE_REBUILD="${IMPACT_FORCE:-0}"
VERBOSE="${IMPACT_VERBOSE:-0}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --force)   FORCE_REBUILD=1; shift ;;
        --verbose) VERBOSE=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)         echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# ── Cache validity check ───────────────────────────────────────────────
# Skip rebuild if graph is <24h old and no sources changed
should_rebuild() {
    [[ "$FORCE_REBUILD" == "1" ]] && return 0

    if [[ ! -f "$GRAPH_FILE" ]]; then
        [[ "$VERBOSE" == "1" ]] && echo "Graph file missing, rebuilding..."
        return 0
    fi

    local graph_mtime=$(stat -f %m "$GRAPH_FILE" 2>/dev/null || stat -c %Y "$GRAPH_FILE" 2>/dev/null || echo 0)
    local now=$(date +%s)
    local age=$((now - graph_mtime))
    local max_age=$((24 * 3600))

    if [[ $age -gt $max_age ]]; then
        [[ "$VERBOSE" == "1" ]] && echo "Graph file older than 24h ($((age/3600))h), rebuilding..."
        return 0
    fi

    # Check if any source or test files changed since last build
    local find_mtime_flag="+%T"
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        find_mtime_flag="-newer"
    fi

    if find "yawl-"*/src -name "*.java" -type f 2>/dev/null | head -1 | xargs ls -ld 2>/dev/null | \
       awk -v graph_time="$(date -r "$GRAPH_FILE" +%s)" '{
           file_time=$(stat -c %Y "$0" 2>/dev/null || stat -f %m "$0" 2>/dev/null)
           if (file_time > graph_time) exit 1
       }' 2>/dev/null; then
        [[ "$VERBOSE" == "1" ]] && echo "Graph is fresh, using cached version"
        return 1
    fi

    [[ "$VERBOSE" == "1" ]] && echo "Source files changed, rebuilding..."
    return 0
}

# ── Extract YAWL imports from file ─────────────────────────────────────
# Returns a space-separated list of YAWL production classes imported
extract_yawl_imports() {
    local file="$1"

    # Extract import statements, filter for org.yawlfoundation.yawl.* (except test packages)
    # and exclude org.junit, org.slf4j, java.*, etc.
    grep -E '^\s*import\s+org\.yawlfoundation\.yawl\.' "$file" 2>/dev/null | \
        grep -v '\.test\.' | \
        sed 's/^[[:space:]]*import[[:space:]]\+//' | \
        sed 's/;$//' | \
        sed 's/\.\*/\.*/g' | \
        sort -u
}

# ── Resolve wildcard imports ───────────────────────────────────────────
# Expands org.yawlfoundation.yawl.engine.* to actual classes in that package
resolve_wildcard_import() {
    local import_spec="$1"

    # If not a wildcard, return as-is
    if [[ ! "$import_spec" =~ \.\*$ ]]; then
        echo "$import_spec"
        return
    fi

    # Convert import to directory path and find all .java files
    local package_path="${import_spec%.*}"
    package_path="${package_path//\./\/}"

    # Search in src/main/java across all modules
    find "yawl-"*/src/main/java/"$package_path" \
         src/main/java/"$package_path" \
         2>/dev/null \
        | grep '\.java$' \
        | sed 's/.*\/src\/main\/java\///' \
        | sed 's/\.java$//' \
        | sed 's/\//\./g' \
        | sort -u
}

# ── Parse test file and extract production class dependencies ───────────
# Returns a space-separated list of full class names from production
analyze_test_file() {
    local test_file="$1"
    local test_class_name

    # Extract package and class name from test file
    test_class_name=$(grep -E '^\s*package\s+' "$test_file" | sed 's/^[[:space:]]*package[[:space:]]\+//' | sed 's/;$//')
    local filename=$(basename "$test_file" .java)
    test_class_name="${test_class_name}.${filename}"

    # Extract all YAWL imports
    local imports
    imports=$(extract_yawl_imports "$test_file")

    local all_deps=""

    # Resolve each import and collect all classes
    while IFS= read -r import_spec; do
        [[ -z "$import_spec" ]] && continue

        local resolved
        resolved=$(resolve_wildcard_import "$import_spec")

        while IFS= read -r class_name; do
            [[ -z "$class_name" ]] && continue
            all_deps+="$class_name "
        done <<< "$resolved"
    done <<< "$imports"

    # Output in format: test_class=dep1,dep2,dep3
    if [[ -n "$all_deps" ]]; then
        echo "${test_class_name}=$(echo "$all_deps" | tr ' ' '\n' | sort -u | tr '\n' ',' | sed 's/,$//')"
    fi
}

# ── Find all test files ────────────────────────────────────────────────
find_all_test_files() {
    find "yawl-"*/src/test/java -name "*Test.java" -type f 2>/dev/null | \
        grep -v target | \
        sort
}

# ── Build the impact graph ─────────────────────────────────────────────
build_impact_graph() {
    local test_files
    test_files=$(find_all_test_files)
    local test_count=$(echo "$test_files" | wc -l)

    [[ "$VERBOSE" == "1" ]] && echo "Found $test_count test files, analyzing dependencies..."

    # Create temporary files for processing
    local test_deps_file="${CACHE_DIR}/.test-deps.tmp"
    local source_to_tests_file="${CACHE_DIR}/.source-to-tests.tmp"

    > "$test_deps_file"
    > "$source_to_tests_file"

    local processed=0

    # Analyze each test file
    while IFS= read -r test_file; do
        [[ -z "$test_file" ]] && continue

        if [[ "$VERBOSE" == "1" ]]; then
            ((processed++))
            if (( processed % 10 == 0 )); then
                echo "  Processed $processed/$test_count test files..."
            fi
        fi

        local test_data
        test_data=$(analyze_test_file "$test_file")
        [[ -n "$test_data" ]] && echo "$test_data" >> "$test_deps_file"
    done <<< "$test_files"

    [[ "$VERBOSE" == "1" ]] && echo "Building bidirectional graph..."

    # Build JSON structure with two maps
    # Start with test_to_source map
    local json_test_to_source="{"
    local first_test=true

    while IFS='=' read -r test_class deps; do
        [[ -z "$test_class" ]] && continue

        # Format deps as JSON array
        local deps_json=$(echo "$deps" | tr ',' '\n' | \
            sed 's/^/"/' | sed 's/$/"/' | paste -sd ',' - | sed 's/^/[/' | sed 's/$/]/')

        if [[ "$first_test" == true ]]; then
            json_test_to_source+="\"$test_class\":$deps_json"
            first_test=false
        else
            json_test_to_source+=",\"$test_class\":$deps_json"
        fi
    done < "$test_deps_file"

    json_test_to_source+="}"

    # Build source_to_tests map (invert the relationship)
    local json_source_to_tests="{"
    local source_map="${CACHE_DIR}/.source-map.tmp"
    > "$source_map"

    while IFS='=' read -r test_class deps; do
        [[ -z "$test_class" ]] && continue

        # For each dependency, add this test to its list
        echo "$deps" | tr ',' '\n' | while read -r source_class; do
            [[ -z "$source_class" ]] && continue
            echo "$source_class:$test_class" >> "$source_map"
        done
    done < "$test_deps_file"

    # Sort and aggregate
    local first_source=true
    sort "$source_map" | cut -d: -f1 | uniq | while read -r source_class; do
        [[ -z "$source_class" ]] && continue

        local tests=$(grep "^$source_class:" "$source_map" | cut -d: -f2 | sort -u | \
            sed 's/^/"/' | sed 's/$/"/' | paste -sd ',' - | sed 's/^/[/' | sed 's/$/]/')

        if [[ "$first_source" == true ]]; then
            json_source_to_tests+="\"$source_class\":$tests"
            first_source=false
        else
            json_source_to_tests+=",\"$source_class\":$tests"
        fi
    done

    json_source_to_tests+="}"

    # Create final JSON structure using jq for proper formatting
    jq -n \
        --arg version "1.0" \
        --arg generated_at "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
        --argjson test_to_source "$json_test_to_source" \
        --argjson source_to_tests "$json_source_to_tests" \
        --arg test_count "$test_count" \
        '{
            version: $version,
            generated_at: $generated_at,
            metadata: {
                test_files_analyzed: ($test_count | tonumber),
                java_version: env.JAVA_VERSION,
                maven_version: env.MAVEN_VERSION
            },
            test_to_source: $test_to_source,
            source_to_tests: $source_to_tests
        }' > "$TEMP_GRAPH"

    # Atomic rename
    mv "$TEMP_GRAPH" "$GRAPH_FILE"

    # Clean up temp files
    rm -f "$test_deps_file" "$source_to_tests_file" "$source_map"

    [[ "$VERBOSE" == "1" ]] && echo "Impact graph built: $GRAPH_FILE ($test_count test files analyzed)"
}

# ── Write metadata ─────────────────────────────────────────────────────
write_metadata() {
    mkdir -p "$CACHE_DIR"

    jq -n \
        --arg timestamp "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
        --arg java_version "$JAVA_VERSION" \
        --arg maven_version "$MVN_VERSION" \
        --arg graph_file "$GRAPH_FILE" \
        '{
            timestamp: $timestamp,
            java_version: $java_version,
            maven_version: $maven_version,
            graph_file: $graph_file,
            cache_valid_hours: 24
        }' > "$METADATA_FILE"
}

# ── Main execution ─────────────────────────────────────────────────────
main() {
    mkdir -p "$CACHE_DIR"

    if should_rebuild; then
        echo "Building test impact graph..."
        build_impact_graph
        write_metadata
        echo "Successfully generated: $GRAPH_FILE"
        exit 0
    else
        exit 0
    fi
}

main "$@"
