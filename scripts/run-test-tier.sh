#!/usr/bin/env bash
# ==========================================================================
# run-test-tier.sh — Execute a single test tier with parallel execution
#
# Implements multi-tier test execution pipeline (TEP) with fail-fast support.
# Tiers are executed sequentially, but tests within a tier run in parallel.
#
# Usage:
#   bash scripts/run-test-tier.sh <tier-number> [options]
#
# Examples:
#   bash scripts/run-test-tier.sh 1                    # Run Tier 1 (fast unit tests)
#   bash scripts/run-test-tier.sh 2                    # Run Tier 2 (medium integration tests)
#   bash scripts/run-test-tier.sh 3 --continue         # Run Tier 3, continue on failure
#   bash scripts/run-test-tier.sh 1 --dry-run          # Show what would run
#
# Environment:
#   TIER_CONFIG         Path to tier-definitions.json (default: .yawl/ci/tier-definitions.json)
#   SHARD_CONFIG        Path to test-shards.json (default: .yawl/ci/test-shards.json)
#   DX_VERBOSE          Show Maven output (default: quiet)
#   TEP_DRY_RUN         Show tests without executing (default: false)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ──────────────────────────────────────────────────────
TIER_CONFIG="${TIER_CONFIG:-${REPO_ROOT}/.yawl/ci/tier-definitions.json}"
SHARD_CONFIG="${SHARD_CONFIG:-${REPO_ROOT}/.yawl/ci/test-shards.json}"

# Validate configuration files exist
if [[ ! -f "$TIER_CONFIG" ]]; then
    echo "ERROR: Tier definitions not found at $TIER_CONFIG"
    exit 1
fi

if [[ ! -f "$SHARD_CONFIG" ]]; then
    echo "ERROR: Test shards not found at $SHARD_CONFIG"
    exit 1
fi

# ── Parse arguments ────────────────────────────────────────────────────
TIER_NUMBER="${1:-}"
CONTINUE_ON_FAILURE=false
DRY_RUN=false

if [[ -z "$TIER_NUMBER" ]]; then
    echo "Usage: $0 <tier-number> [--continue] [--dry-run]"
    exit 1
fi

shift || true
while [[ $# -gt 0 ]]; do
    case "$1" in
        --continue)    CONTINUE_ON_FAILURE=true; shift ;;
        --dry-run)     DRY_RUN=true; shift ;;
        *)             echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Color codes ────────────────────────────────────────────────────────
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'
readonly E_OK='✓'
readonly E_FAIL='✗'

# ── Helper functions ───────────────────────────────────────────────────

# Extract tier configuration for given tier number
get_tier_config() {
    local tier_num=$1
    jq ".tier_definitions.tier_${tier_num}" "$TIER_CONFIG" 2>/dev/null || echo "null"
}

# Get all tests for a given tier
get_tests_for_tier() {
    local tier_num=$1
    local cluster_ids
    cluster_ids=$(jq -r ".tier_definitions.tier_${tier_num}.cluster_ids[]" "$TIER_CONFIG" 2>/dev/null || echo "")

    if [[ -z "$cluster_ids" ]]; then
        echo ""
        return
    fi

    # Extract tests from shards that match cluster IDs for this tier
    local tests=""
    while IFS= read -r cluster_id; do
        [[ -z "$cluster_id" ]] && continue

        # Find all shards with this cluster ID and extract their tests
        local shard_tests
        shard_tests=$(jq -r ".shards[] | select(.cluster == $cluster_id) | .tests[]?" "$SHARD_CONFIG" 2>/dev/null || true)

        if [[ -n "$shard_tests" ]]; then
            tests="${tests}${shard_tests}"$'\n'
        fi
    done <<< "$cluster_ids"

    # Remove empty lines
    echo "$tests" | grep -v '^$' || true
}

# Format test name to Surefire include pattern
test_to_surefire_pattern() {
    local test_full=$1
    # Convert "org.yawlfoundation.yawl.engine.YNetRunnerTest.testSimpleWorkflow" to "**/YNetRunnerTest.java"
    local test_class=$(echo "$test_full" | cut -d'.' -f1-7 | sed 's/\./\//g')
    local test_name=$(echo "$test_full" | cut -d'.' -f8)
    echo "**/${test_class}.java"
}

# ── Load tier configuration ────────────────────────────────────────────

TIER_CONFIG_JSON=$(get_tier_config "$TIER_NUMBER")

if [[ "$TIER_CONFIG_JSON" == "null" || -z "$TIER_CONFIG_JSON" ]]; then
    printf "${C_RED}${E_FAIL} ERROR${C_RESET}: Tier %d not found in %s\n" "$TIER_NUMBER" "$TIER_CONFIG"
    exit 1
fi

TIER_NAME=$(echo "$TIER_CONFIG_JSON" | jq -r '.name')
TIER_DESC=$(echo "$TIER_CONFIG_JSON" | jq -r '.description')
TIER_TIMEOUT=$(echo "$TIER_CONFIG_JSON" | jq -r '.timeout_seconds')
TIER_PARALLELISM=$(echo "$TIER_CONFIG_JSON" | jq -r '.parallelism')

# Get tests for this tier
TIER_TESTS=$(get_tests_for_tier "$TIER_NUMBER")
TEST_COUNT=$(echo "$TIER_TESTS" | grep -c . || true)

# ── Display tier information ───────────────────────────────────────────

echo ""
printf "${C_CYAN}Tier ${TIER_NUMBER}${C_RESET}: ${C_BLUE}%s${C_RESET}\n" "$TIER_NAME"
printf "${C_CYAN}Description${C_RESET}: %s\n" "$TIER_DESC"
printf "${C_CYAN}Tests${C_RESET}: %d | ${C_CYAN}Timeout${C_RESET}: %ds | ${C_CYAN}Parallelism${C_RESET}: %d\n" \
    "$TEST_COUNT" "$TIER_TIMEOUT" "$TIER_PARALLELISM"

if [[ $TEST_COUNT -eq 0 ]]; then
    printf "${C_YELLOW}→${C_RESET} No tests found for tier %d\n" "$TIER_NUMBER"
    exit 0
fi

# ── Display tests if dry-run ───────────────────────────────────────────

if [[ "$DRY_RUN" == "true" ]]; then
    printf "\n${C_CYAN}Tests that would run:${C_RESET}\n"
    echo "$TIER_TESTS" | nl
    exit 0
fi

# ── Prepare Maven arguments ────────────────────────────────────────────

MVN_ARGS=("-q")

# Add profile
MVN_ARGS+=("-P" "agent-dx")

# Configure parallelism for this tier
# JUnit 5 parallel execution: threads = parallelism
# Mode classes=concurrent: run test classes in parallel
# Mode methods=concurrent: run test methods in parallel within each class
MVN_ARGS+=("-Djunit.jupiter.execution.parallel.mode.default=concurrent")
MVN_ARGS+=("-Djunit.jupiter.execution.parallel.mode.classes.default=concurrent")
MVN_ARGS+=("-Djunit.jupiter.execution.parallel.config.fixed.parallelism=${TIER_PARALLELISM}")

# Enable test timeout per method (in seconds)
# Surefire timeout is cumulative for all tests in tier
MVN_ARGS+=("-Dsurefire.timeout=${TIER_TIMEOUT}000")

# Skip other phases, only run test
MVN_ARGS+=("-DskipExec" "-DskipIntegrationTests")

# Offline flag
OFFLINE_FLAG=""
if [[ "${DX_OFFLINE:-auto}" == "1" ]]; then
    OFFLINE_FLAG="-o"
elif [[ "${DX_OFFLINE:-auto}" == "auto" ]]; then
    if [[ -d "${HOME}/.m2/repository/org/yawlfoundation/yawl-parent" ]]; then
        OFFLINE_FLAG="-o"
    fi
fi
[[ -n "$OFFLINE_FLAG" ]] && MVN_ARGS+=("$OFFLINE_FLAG")

# Verbose if enabled
if [[ "${DX_VERBOSE:-0}" == "1" ]]; then
    MVN_ARGS=("${MVN_ARGS[@]//-q/}")  # Remove -q if verbose
fi

# ── Build test inclusion patterns ──────────────────────────────────────

# Method 1: Use test class patterns to include specific tests
declare -a INCLUDES=()
while IFS= read -r test_full; do
    [[ -z "$test_full" ]] && continue

    # Extract class path: convert "org.yawlfoundation.yawl.engine.YNetRunnerTest.testSimpleWorkflow"
    # to "org/yawlfoundation/yawl/engine/YNetRunnerTest.java"
    local class_path=$(echo "$test_full" | cut -d'.' -f1-8 | sed 's/\./\//g')
    INCLUDES+=("**/${class_path}.java")
done <<< "$TIER_TESTS"

# Remove duplicates
INCLUDES=($(printf '%s\n' "${INCLUDES[@]}" | sort -u))

# Add includes to Maven (Surefire uses -Dincludes or -DincludedTests)
for include in "${INCLUDES[@]}"; do
    MVN_ARGS+=("-Dincludes=${include}")
done

# ── Execute Maven test run ────────────────────────────────────────────

START_SEC=$(date +%s)

echo ""
printf "${C_CYAN}→${C_RESET} Running tests...\n"

set +e
mvn test "${MVN_ARGS[@]}" 2>&1 | tee /tmp/tep-tier-${TIER_NUMBER}-log.txt
EXIT_CODE=$?
set -euo pipefail

END_SEC=$(date +%s)
ELAPSED=$((END_SEC - START_SEC))

# ── Parse test results ─────────────────────────────────────────────────

# Extract test result summary from log
# Format: "Tests run: N, Failures: F, Errors: E, Skipped: S, Time elapsed: T sec"
TESTS_RUN=0
TESTS_FAILED=0
TESTS_ERRORS=0
TESTS_SKIPPED=0

if grep -q "Tests run:" /tmp/tep-tier-${TIER_NUMBER}-log.txt; then
    TESTS_RUN=$(grep "Tests run:" /tmp/tep-tier-${TIER_NUMBER}-log.txt | tail -1 | grep -oE 'Tests run: [0-9]+' | cut -d' ' -f3)
    TESTS_FAILED=$(grep "Tests run:" /tmp/tep-tier-${TIER_NUMBER}-log.txt | tail -1 | grep -oE 'Failures: [0-9]+' | cut -d' ' -f2)
    TESTS_ERRORS=$(grep "Tests run:" /tmp/tep-tier-${TIER_NUMBER}-log.txt | tail -1 | grep -oE 'Errors: [0-9]+' | cut -d' ' -f2)
    TESTS_SKIPPED=$(grep "Tests run:" /tmp/tep-tier-${TIER_NUMBER}-log.txt | tail -1 | grep -oE 'Skipped: [0-9]+' | cut -d' ' -f2)
fi

TESTS_PASSED=$((TESTS_RUN - TESTS_FAILED - TESTS_ERRORS - TESTS_SKIPPED))

# ── Display tier results ───────────────────────────────────────────────

echo ""
printf "${C_CYAN}Tier ${TIER_NUMBER} Results${C_RESET}:\n"
printf "  ${C_GREEN}Passed${C_RESET}: %d\n" "$TESTS_PASSED"
printf "  ${C_RED}Failed${C_RESET}: %d\n" "$TESTS_FAILED"
printf "  ${C_YELLOW}Errors${C_RESET}: %d\n" "$TESTS_ERRORS"
printf "  ${C_YELLOW}Skipped${C_RESET}: %d\n" "$TESTS_SKIPPED"
printf "  ${C_CYAN}Duration${C_RESET}: %ds\n" "$ELAPSED"

# Emit JSON metrics for parsing by parent scripts
METRICS_FILE="/tmp/tep-tier-${TIER_NUMBER}-metrics.json"
jq -n \
  --arg tier "$TIER_NUMBER" \
  --argjson passed "$TESTS_PASSED" \
  --argjson failed "$TESTS_FAILED" \
  --argjson errors "$TESTS_ERRORS" \
  --argjson skipped "$TESTS_SKIPPED" \
  --argjson duration "$ELAPSED" \
  '{tier: $tier, passed: $passed, failed: $failed, errors: $errors, skipped: $skipped, duration_sec: $duration}' \
  > "$METRICS_FILE" 2>/dev/null || true

# ── Handle failure ─────────────────────────────────────────────────────

if [[ $EXIT_CODE -ne 0 ]]; then
    echo ""
    printf "${C_RED}${E_FAIL} Tier %d FAILED${C_RESET}\n" "$TIER_NUMBER"

    if [[ "$CONTINUE_ON_FAILURE" != "true" ]]; then
        printf "\n${C_YELLOW}→${C_RESET} Debug: ${C_CYAN}cat /tmp/tep-tier-${TIER_NUMBER}-log.txt | tail -50${C_RESET}\n"
        printf "${C_YELLOW}→${C_RESET} Retry: ${C_CYAN}bash scripts/run-test-tier.sh ${TIER_NUMBER} --continue${C_RESET}\n"
        exit $EXIT_CODE
    else
        printf "${C_YELLOW}→${C_RESET} Continuing despite failures (--continue flag set)\n"
    fi
else
    echo ""
    printf "${C_GREEN}${E_OK} Tier %d PASSED${C_RESET}\n" "$TIER_NUMBER"
fi

exit 0
