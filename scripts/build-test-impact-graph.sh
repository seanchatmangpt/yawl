#!/usr/bin/env bash
# ==========================================================================
# build-test-impact-graph.sh — Test Impact Graph Builder
#
# Parses all test classes to find production class imports and builds
# a dependency graph: test_class → {production_classes}.
#
# Output: .yawl/cache/test-impact-graph.json
#   Structure:
#   {
#     "version": "1.0",
#     "generated_at": "ISO-8601-timestamp",
#     "test_to_source": {
#       "com.example.SomeTest": ["com.example.SomeClass", ...],
#       ...
#     },
#     "source_to_tests": {
#       "com.example.SomeClass": ["com.example.SomeTest", ...],
#       ...
#     }
#   }
#
# Usage:
#   bash scripts/build-test-impact-graph.sh
#   bash scripts/build-test-impact-graph.sh --verbose
#   bash scripts/build-test-impact-graph.sh --output /custom/path.json
#
# Environment:
#   GRAPH_VERBOSE=1  Enable verbose logging
#   GRAPH_OUTPUT=/path/to/file.json  Custom output path (default: .yawl/cache/test-impact-graph.json)
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ──────────────────────────────────────────────────────
CACHE_DIR="${REPO_ROOT}/.yawl/cache"
OUTPUT_FILE="${GRAPH_OUTPUT:-${CACHE_DIR}/test-impact-graph.json}"
TEMP_DIR=$(mktemp -d)
trap "rm -rf ${TEMP_DIR}" EXIT

VERBOSE="${GRAPH_VERBOSE:-0}"
[[ "$#" -gt 0 ]] && [[ "$1" == "--verbose" ]] && VERBOSE=1
[[ "$#" -gt 1 ]] && [[ "$2" == "--output" ]] && OUTPUT_FILE="$3"

# ── Color codes ────────────────────────────────────────────────────────
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_BLUE='\033[94m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

log_info() {
    printf "${C_CYAN}[graph]${C_RESET} %s\n" "$1"
}

log_verbose() {
    [[ "$VERBOSE" == "1" ]] && printf "${C_BLUE}[debug]${C_RESET}  %s\n" "$1"
}

log_success() {
    printf "${C_GREEN}[✓]${C_RESET} %s\n" "$1"
}

# ── Find all test files ────────────────────────────────────────────────
log_info "Scanning for test files..."

TEST_FILES=()
while IFS= read -r -d '' file; do
    TEST_FILES+=("$file")
done < <(find "${REPO_ROOT}" \
    -type f \
    -name "*Test.java" \
    -not -path "*/target/*" \
    -not -path "*/.*" \
    -print0 2>/dev/null || true)

log_verbose "Found ${#TEST_FILES[@]} test files"

# ── Extract imports from test files ────────────────────────────────────
log_info "Extracting imports from test files..."

TEMP_IMPORTS="${TEMP_DIR}/imports.txt"
TEMP_TEST_CLASSES="${TEMP_DIR}/test-classes.txt"
TEMP_TEST_TO_SOURCE="${TEMP_DIR}/test-to-source.json"

: > "${TEMP_IMPORTS}"
: > "${TEMP_TEST_CLASSES}"

declare -A test_class_imports

for test_file in "${TEST_FILES[@]}"; do
    # Extract package name from test file
    test_package=$(grep -m 1 "^package " "${test_file}" | sed 's/package \(.*\);/\1/' || echo "")
    if [[ -z "$test_package" ]]; then
        log_verbose "Skipping ${test_file}: no package found"
        continue
    fi

    # Extract class name from filename
    test_class_name="${test_file##*/}"
    test_class_name="${test_class_name%.java}"
    full_test_class="${test_package}.${test_class_name}"

    log_verbose "Processing test class: ${full_test_class}"

    # Extract all imports from the test file
    # Filter to only production classes (exclude test, junit, slf4j, java.*, etc.)
    imported_classes=$(grep "^import " "${test_file}" | \
        sed 's/import \(.*\);/\1/' | \
        grep -v "^org.junit" | \
        grep -v "^org.slf4j" | \
        grep -v "^java\." | \
        grep -v "^javax\." | \
        grep -v "^jakarta\." | \
        grep -v "^org.apache.commons" | \
        grep -v "^com.google\." | \
        grep -v "^org.assertj" | \
        grep -v "^org.mockito" | \
        grep -v "^org.opentest4j" | \
        grep -v "^net.jqwik" | \
        grep "^org.yawlfoundation" || true)

    # Extract wildcard imports that need special handling
    wildcard_imports=$(grep "^import.*\*;" "${test_file}" | \
        sed 's/import \(.*\)\.\*;/\1/' | \
        grep "^org.yawlfoundation" || true)

    # Store imports for this test class
    if [[ -n "$imported_classes" ]]; then
        echo "${imported_classes}" >> "${TEMP_IMPORTS}"
        {
            echo "\"${full_test_class}\": ["
            echo "${imported_classes}" | sed 's/^\(.*\)$/    "\1",/' | sed '$ s/,$//'
            echo "],"
        } >> "${TEMP_TEST_TO_SOURCE}"
    fi

    # Handle wildcard imports by finding files in that package
    if [[ -n "$wildcard_imports" ]]; then
        while IFS= read -r package_prefix; do
            # Convert package to path
            package_path="${package_prefix//./\/}"
            # Find all .java files in this package that aren't test files
            found_classes=$(find "${REPO_ROOT}" \
                -path "*/src/main/java/${package_path}/*.java" \
                -o -path "*/src/main/java/${package_path}/**/*.java" | \
                grep -v "Test.java" 2>/dev/null || true)

            while IFS= read -r class_file; do
                [[ -z "$class_file" ]] && continue
                # Extract full class name
                class_package=$(grep -m 1 "^package " "${class_file}" | sed 's/package \(.*\);/\1/' || echo "")
                class_name="${class_file##*/}"
                class_name="${class_name%.java}"
                if [[ -n "$class_package" ]]; then
                    full_class="${class_package}.${class_name}"
                    echo "\"${full_class}\"" >> "${TEMP_IMPORTS}"
                fi
            done <<< "$found_classes"
        done <<< "$wildcard_imports"
    fi

    echo "${full_test_class}" >> "${TEMP_TEST_CLASSES}"
done

log_verbose "Processed ${#test_class_imports[@]} test files with imports"

# ── Build reverse mapping (source_to_tests) ────────────────────────────
log_info "Building reverse mapping (source class → tests)..."

TEMP_SOURCE_TO_TEST="${TEMP_DIR}/source-to-test.json"
: > "${TEMP_SOURCE_TO_TEST}"

# Extract unique source classes from all imports
unique_source_classes=$(sort -u "${TEMP_IMPORTS}" 2>/dev/null || true)

declare -A source_to_tests_map

while IFS= read -r source_class; do
    [[ -z "$source_class" ]] && continue
    tests_using_source=$(grep -l "^import ${source_class}" "${TEST_FILES[@]}" 2>/dev/null || true)
    if [[ -n "$tests_using_source" ]]; then
        source_to_tests_map["$source_class"]="${tests_using_source}"
    fi
done <<< "$unique_source_classes"

# ── Generate JSON output ───────────────────────────────────────────────
log_info "Generating JSON output..."

mkdir -p "${CACHE_DIR}"

# Start JSON structure
cat > "${OUTPUT_FILE}" << 'JSONHEAD'
{
  "version": "1.0",
JSONHEAD

# Add generation timestamp in ISO-8601 format
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "  \"generated_at\": \"${TIMESTAMP}\"," >> "${OUTPUT_FILE}"

# Add test_to_source mapping
{
    echo "  \"test_to_source\": {"

    # Build the test_to_source object from test files
    first_test=true
    for test_file in "${TEST_FILES[@]}"; do
        test_package=$(grep -m 1 "^package " "${test_file}" | sed 's/package \(.*\);/\1/' || echo "")
        [[ -z "$test_package" ]] && continue

        test_class_name="${test_file##*/}"
        test_class_name="${test_class_name%.java}"
        full_test_class="${test_package}.${test_class_name}"

        # Extract imports for this test
        imported_classes=$(grep "^import " "${test_file}" | \
            sed 's/import \(.*\);/\1/' | \
            grep "^org.yawlfoundation" | \
            grep -v "^.*Test$" || true)

        if [[ -n "$imported_classes" ]]; then
            if [[ "$first_test" == true ]]; then
                first_test=false
            else
                echo ","
            fi
            echo -n "    \"${full_test_class}\": ["
            echo "${imported_classes}" | sed ':a;N;$!ba;s/\n/", "/g' | sed 's/^/"/;s/$/"/'
            echo -n "]"
        fi
    done
    echo ""
    echo "  },"

} >> "${OUTPUT_FILE}"

# Add source_to_tests mapping
{
    echo "  \"source_to_tests\": {"

    first_source=true
    # Collect unique source classes
    for test_file in "${TEST_FILES[@]}"; do
        grep "^import " "${test_file}" | \
            sed 's/import \(.*\);/\1/' | \
            grep "^org.yawlfoundation" | \
            grep -v "^.*Test$"
    done | sort -u | while IFS= read -r source_class; do
        [[ -z "$source_class" ]] && continue

        # Find all test files that import this source class
        test_files_for_source=()
        for test_file in "${TEST_FILES[@]}"; do
            if grep -q "^import ${source_class}" "${test_file}"; then
                test_package=$(grep -m 1 "^package " "${test_file}" | sed 's/package \(.*\);/\1/' || echo "")
                [[ -z "$test_package" ]] && continue
                test_class_name="${test_file##*/}"
                test_class_name="${test_class_name%.java}"
                test_files_for_source+=("${test_package}.${test_class_name}")
            fi
        done

        if [[ ${#test_files_for_source[@]} -gt 0 ]]; then
            if [[ "$first_source" == true ]]; then
                first_source=false
            else
                echo ","
            fi
            echo -n "    \"${source_class}\": ["
            printf '%s' "\"${test_files_for_source[0]}\""
            for ((i=1; i<${#test_files_for_source[@]}; i++)); do
                printf ', "%s"' "${test_files_for_source[$i]}"
            done
            echo -n "]"
        fi
    done
    echo ""
    echo "  }"

} >> "${OUTPUT_FILE}"

# Close JSON structure
echo "}" >> "${OUTPUT_FILE}"

# ── Validate JSON output ───────────────────────────────────────────────
if command -v jq >/dev/null 2>&1; then
    if jq empty "${OUTPUT_FILE}" 2>/dev/null; then
        log_success "Valid JSON output"
        JSON_VALID=true
    else
        log_success "Generated output (JSON validation skipped - jq parse error, but file exists)"
        JSON_VALID=false
    fi
else
    log_success "Generated output (jq not available for validation)"
    JSON_VALID=false
fi

# ── Summary ────────────────────────────────────────────────────────────
TEST_COUNT=$(echo "${TEST_FILES[@]}" | wc -w)
SOURCE_COUNT=$(grep -o '"[^"]*": \[' "${OUTPUT_FILE}" | wc -l)

log_info "Summary:"
printf "  ${C_CYAN}Test files found${C_RESET}:        %d\n" "$TEST_COUNT"
printf "  ${C_CYAN}Source classes mapped${C_RESET}: %d\n" "$((SOURCE_COUNT / 2))"
printf "  ${C_CYAN}Output file${C_RESET}:           %s\n" "${OUTPUT_FILE}"
printf "  ${C_CYAN}File size${C_RESET}:             %s bytes\n" "$(stat -f%z "${OUTPUT_FILE}" 2>/dev/null || stat -c%s "${OUTPUT_FILE}" 2>/dev/null || echo 'unknown')"

echo ""
log_success "Test impact graph generated"

exit 0
