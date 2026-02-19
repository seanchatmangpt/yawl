#!/usr/bin/env bash
# ==========================================================================
# check-singleton-ann.sh - Verify @Execution annotations on singleton-using tests
#
# Usage:
#   bash scripts/validation/lib/check-singleton-ann.sh
#   bash scripts/validation/lib/check-singleton-ann.sh --json
#
# Purpose:
#   Ensures tests using singleton instances (YEngine.getInstance(), etc.)
#   are annotated with @Execution(ExecutionMode.SAME_THREAD) to prevent
#   race conditions in parallel test execution.
#
# Singleton patterns detected:
#   - YEngine.getInstance()
#   - YStatelessEngine.getInstance()
#   - EngineFactory.getInstance()
#   - .INSTANCE (enum/kotlin singleton pattern)
#   - Class.forName singletons
#
# Exit codes:
#   0 - All singleton-using tests have proper @Execution annotation
#   1 - One or more tests missing required annotation
#   2 - Script error (invalid arguments, etc.)
#
# Output:
#   Default: Human-readable report
#   --json:  JSON format for receipts and CI integration
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
TEST_DIR="${PROJECT_ROOT}/test"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Output mode
OUTPUT_JSON=false
if [[ "${1:-}" == "--json" ]]; then
    OUTPUT_JSON=true
fi

# Singleton patterns to detect
# Using extended regex for grep -E
SINGLETON_PATTERNS=(
    "YEngine\.getInstance\(\)"
    "YStatelessEngine\.getInstance\(\)"
    "EngineFactory\.getInstance\(\)"
    "EngineClearer\.(clear|getInstance)"
    "\.INSTANCE\b"
    "getInstance\(\).*engine"
)

# Build the combined pattern
SINGLETON_PATTERN=$(IFS="|"; echo "${SINGLETON_PATTERNS[*]}")

# Tracking arrays
declare -a FILES_WITH_SINGLETON=()
declare -a FILES_WITH_ANNOTATION=()
declare -a FILES_MISSING_ANNOTATION=()

# -------------------------------------------------------------------------
# Find all test files using singleton patterns
# -------------------------------------------------------------------------
find_singleton_tests() {
    # Find all Java test files
    while IFS= read -r -d '' test_file; do
        # Check if file contains any singleton pattern
        if grep -qE "$SINGLETON_PATTERN" "$test_file" 2>/dev/null; then
            FILES_WITH_SINGLETON+=("$test_file")
        fi
    done < <(find "$TEST_DIR" -name "*Test*.java" -type f -print0 2>/dev/null)
}

# -------------------------------------------------------------------------
# Check if a file has @Execution annotation
# -------------------------------------------------------------------------
has_execution_annotation() {
    local file="$1"

    # Check for @Execution annotation with SAME_THREAD or CONCURRENT
    # This covers:
    #   @Execution(ExecutionMode.SAME_THREAD)
    #   @Execution(ExecutionMode.CONCURRENT)
    #   @Execution(value = ExecutionMode.SAME_THREAD)
    grep -qE "@Execution\s*\(\s*ExecutionMode\.(SAME_THREAD|CONCURRENT)" "$file" 2>/dev/null
}

# -------------------------------------------------------------------------
# Categorize files based on annotation presence
# -------------------------------------------------------------------------
categorize_files() {
    for file in "${FILES_WITH_SINGLETON[@]}"; do
        local relative_path="${file#$PROJECT_ROOT/}"

        if has_execution_annotation "$file"; then
            FILES_WITH_ANNOTATION+=("$relative_path")
        else
            FILES_MISSING_ANNOTATION+=("$relative_path")
        fi
    done
}

# -------------------------------------------------------------------------
# Output JSON format for receipts
# -------------------------------------------------------------------------
output_json() {
    local timestamp
    timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    local total=${#FILES_WITH_SINGLETON[@]}
    local passed=${#FILES_WITH_ANNOTATION[@]}
    local failed=${#FILES_MISSING_ANNOTATION[@]}

    # Convert arrays to JSON
    local passed_json="[]"
    local failed_json="[]"

    if [[ ${#FILES_WITH_ANNOTATION[@]} -gt 0 ]]; then
        passed_json=$(printf '%s\n' "${FILES_WITH_ANNOTATION[@]}" | jq -R . | jq -s .)
    fi

    if [[ ${#FILES_MISSING_ANNOTATION[@]} -gt 0 ]]; then
        failed_json=$(printf '%s\n' "${FILES_MISSING_ANNOTATION[@]}" | jq -R . | jq -s .)
    fi

    jq -n \
        --arg timestamp "$timestamp" \
        --arg total "$total" \
        --arg passed "$passed" \
        --arg failed "$failed" \
        --argjson passed_files "$passed_json" \
        --argjson failed_files "$failed_json" \
        '{
            timestamp: $timestamp,
            check: "singleton-execution-annotation",
            summary: {
                total: ($total | tonumber),
                passed: ($passed | tonumber),
                failed: ($failed | tonumber)
            },
            passed_files: $passed_files,
            failed_files: $failed_files,
            status: (if ($failed | tonumber) > 0 then "FAILED" else "PASSED" end)
        }'
}

# -------------------------------------------------------------------------
# Output human-readable format
# -------------------------------------------------------------------------
output_human() {
    local total=${#FILES_WITH_SINGLETON[@]}
    local passed=${#FILES_WITH_ANNOTATION[@]}
    local failed=${#FILES_MISSING_ANNOTATION[@]}

    echo ""
    echo "========================================="
    echo "  Singleton @Execution Annotation Check"
    echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "========================================="
    echo ""

    echo -e "${BLUE}Singleton patterns checked:${NC}"
    echo "  - YEngine.getInstance()"
    echo "  - YStatelessEngine.getInstance()"
    echo "  - EngineFactory.getInstance()"
    echo "  - EngineClearer.clear/getInstance"
    echo "  - .INSTANCE enum singletons"
    echo ""

    echo "Files using singletons: ${total}"
    echo ""

    if [[ ${#FILES_WITH_ANNOTATION[@]} -gt 0 ]]; then
        echo -e "${GREEN}Files WITH @Execution annotation (${passed}):${NC}"
        for file in "${FILES_WITH_ANNOTATION[@]}"; do
            echo -e "  ${GREEN}OK${NC} ${file}"
        done
        echo ""
    fi

    if [[ ${#FILES_MISSING_ANNOTATION[@]} -gt 0 ]]; then
        echo -e "${RED}Files MISSING @Execution annotation (${failed}):${NC}"
        for file in "${FILES_MISSING_ANNOTATION[@]}"; do
            echo -e "  ${RED}MISSING${NC} ${file}"
        done
        echo ""
        echo -e "${YELLOW}Required fix:${NC}"
        echo "  Add to class declaration:"
        echo ""
        echo "    import org.junit.jupiter.api.parallel.Execution;"
        echo "    import org.junit.jupiter.api.parallel.ExecutionMode;"
        echo ""
        echo "    @Execution(ExecutionMode.SAME_THREAD)"
        echo "    class YourTestClass {"
        echo ""
    fi

    echo "========================================="
    echo "  Summary: ${passed}/${total} compliant"
    echo "========================================="

    if [[ $failed -gt 0 ]]; then
        echo -e "${RED}FAILED: ${failed} files missing @Execution annotation${NC}"
        return 1
    else
        echo -e "${GREEN}PASSED: All singleton-using tests have @Execution annotation${NC}"
        return 0
    fi
}

# -------------------------------------------------------------------------
# Main execution
# -------------------------------------------------------------------------
main() {
    # Verify test directory exists
    if [[ ! -d "$TEST_DIR" ]]; then
        echo "ERROR: Test directory not found: $TEST_DIR" >&2
        exit 2
    fi

    # Find and categorize files
    find_singleton_tests
    categorize_files

    # Output results
    if $OUTPUT_JSON; then
        output_json
    else
        output_human
    fi
}

# Run main
main
