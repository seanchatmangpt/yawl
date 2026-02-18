#!/usr/bin/env bash
# ==========================================================================
# test-analyze-flaky.sh — Flaky Test Detection
#
# Runs test suite N times and analyzes JUnit XML reports to detect
# flaky tests (tests that sometimes pass and sometimes fail).
#
# Usage:
#   bash scripts/test-analyze-flaky.sh 3       # Run tests 3 times
#   bash scripts/test-analyze-flaky.sh 5 -m module  # Run specific module 5 times
#
# Output:
#   reports/flaky-tests-report.md
#   reports/flaky-tests-report.json
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPORTS_DIR="${REPO_ROOT}/reports"
cd "${REPO_ROOT}"

# ── Parse arguments ───────────────────────────────────────────────────────
RUN_COUNT="${1:-3}"
shift || true
MODULE_FLAG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -m|--module) MODULE_FLAG="-pl $2 -amd"; shift 2 ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *) shift ;;
    esac
done

mkdir -p "${REPORTS_DIR}"

echo "=== YAWL Flaky Test Analyzer ==="
echo "Run count: ${RUN_COUNT}"
echo "Module filter: ${MODULE_FLAG:-all}"
echo ""

# ── Cleanup previous reports ──────────────────────────────────────────────
rm -rf "${REPORTS_DIR}/flaky-run-*"
rm -f "${REPORTS_DIR}/flaky-tests-report."*

# ── Run tests N times ─────────────────────────────────────────────────────
declare -A TEST_RESULTS  # test_class -> list of pass/fail
declare -A TEST_COUNTS   # test_class -> total runs
declare -A TEST_PASSES   # test_class -> pass count

for run in $(seq 1 "$RUN_COUNT"); do
    echo "=== Run ${run}/${RUN_COUNT} ==="

    RUN_DIR="${REPORTS_DIR}/flaky-run-${run}"
    mkdir -p "$RUN_DIR"

    set +e
    mvn test $MODULE_FLAG \
        -Dagent-dx \
        -Dsurefire.useFile=true \
        -Dsurefire.reportDirectory="$RUN_DIR" \
        -q 2>&1 | tee "${RUN_DIR}/output.log"
    EXIT_CODE=$?
    set -e

    if [[ $EXIT_CODE -eq 0 ]]; then
        echo "Run ${run}: PASSED"
    else
        echo "Run ${run}: FAILED"
    fi

    echo ""
done

# ── Analyze results ───────────────────────────────────────────────────────
echo "=== Analyzing Results ==="

# Parse JUnit XML files from all runs
for run in $(seq 1 "$RUN_COUNT"); do
    RUN_DIR="${REPORTS_DIR}/flaky-run-${run}"

    # Find all TEST-*.xml files
    find "$RUN_DIR" -name "TEST-*.xml" -type f 2>/dev/null | while read -r xml_file; do
        # Extract test class name
        test_class=$(basename "$xml_file" .xml | sed 's/TEST-//')

        # Count failures and errors
        failures=$(grep -o '<failure' "$xml_file" 2>/dev/null | wc -l | tr -d ' ')
        errors=$(grep -o '<error' "$xml_file" 2>/dev/null | wc -l | tr -d ' ')

        # Record result
        if [[ "$failures" -gt 0 || "$errors" -gt 0 ]]; then
            echo "${test_class}:FAIL" >> "${REPORTS_DIR}/flaky-results.txt"
        else
            echo "${test_class}:PASS" >> "${REPORTS_DIR}/flaky-results.txt"
        fi
    done
done

# ── Aggregate results ─────────────────────────────────────────────────────
declare -A TEST_AGGREGATE

while IFS=: read -r test_class result; do
    [[ -z "$test_class" ]] && continue
    key="${test_class}"
    current="${TEST_AGGREGATE[$key]:-}"
    TEST_AGGREGATE[$key]="${current}${result},"
done < "${REPORTS_DIR}/flaky-results.txt"

# ── Identify flaky tests ──────────────────────────────────────────────────
FLAKY_TESTS=()
STABLE_PASSES=()
STABLE_FAILURES=()

for test_class in "${!TEST_AGGREGATE[@]}"; do
    results="${TEST_AGGREGATE[$test_class]}"

    # Count passes and fails
    passes=$(echo "$results" | grep -o "PASS" | wc -l | tr -d ' ')
    fails=$(echo "$results" | grep -o "FAIL" | wc -l | tr -d ' ')
    total=$((passes + fails))

    if [[ $total -eq 0 ]]; then
        continue
    fi

    if [[ $passes -gt 0 && $fails -gt 0 ]]; then
        FLAKY_TESTS+=("${test_class}:${passes}/${total} passes")
    elif [[ $fails -eq $total ]]; then
        STABLE_FAILURES+=("${test_class}")
    else
        STABLE_PASSES+=("${test_class}")
    fi
done

# ── Generate reports ──────────────────────────────────────────────────────

# Markdown report
cat > "${REPORTS_DIR}/flaky-tests-report.md" << EOF
# YAWL Flaky Test Analysis Report

**Generated:** $(date -Iseconds)
**Runs:** ${RUN_COUNT}
**Module filter:** ${MODULE_FLAG:-all}

## Summary

| Category | Count |
|----------|-------|
| Total tests analyzed | ${#TEST_AGGREGATE[@]} |
| Stable passes | ${#STABLE_PASSES[@]} |
| Stable failures | ${#STABLE_FAILURES[@]} |
| **Flaky tests** | **${#FLAKY_TESTS[@]}** |

## Flaky Tests

These tests showed inconsistent results across ${RUN_COUNT} runs:

| Test Class | Pass Rate |
|------------|-----------|
EOF

for entry in "${FLAKY_TESTS[@]}"; do
    test_class="${entry%%:*}"
    rate="${entry##*:}"
    echo "| \`${test_class}\` | ${rate} |" >> "${REPORTS_DIR}/flaky-tests-report.md"
done

if [[ ${#FLAKY_TESTS[@]} -eq 0 ]]; then
    echo "_No flaky tests detected._" >> "${REPORTS_DIR}/flaky-tests-report.md"
fi

cat >> "${REPORTS_DIR}/flaky-tests-report.md" << EOF

## Stable Failures

These tests failed on every run:

EOF

for test_class in "${STABLE_FAILURES[@]}"; do
    echo "- \`${test_class}\`" >> "${REPORTS_DIR}/flaky-tests-report.md"
done

if [[ ${#STABLE_FAILURES[@]} -eq 0 ]]; then
    echo "_No stable failures._" >> "${REPORTS_DIR}/flaky-tests-report.md"
fi

# JSON report
cat > "${REPORTS_DIR}/flaky-tests-report.json" << EOF
{
  "generated": "$(date -Iseconds)",
  "run_count": ${RUN_COUNT},
  "module_filter": "${MODULE_FLAG:-all}",
  "summary": {
    "total_tests": ${#TEST_AGGREGATE[@]},
    "stable_passes": ${#STABLE_PASSES[@]},
    "stable_failures": ${#STABLE_FAILURES[@]},
    "flaky_tests": ${#FLAKY_TESTS[@]}
  },
  "flaky_tests": [
EOF

first=true
for entry in "${FLAKY_TESTS[@]}"; do
    test_class="${entry%%:*}"
    rate="${entry##*:}"
    $first || echo "," >> "${REPORTS_DIR}/flaky-tests-report.json"
    first=false
    echo "    {\"class\": \"${test_class}\", \"pass_rate\": \"${rate}\"}" >> "${REPORTS_DIR}/flaky-tests-report.json"
done

cat >> "${REPORTS_DIR}/flaky-tests-report.json" << EOF
  ],
  "stable_failures": [
EOF

first=true
for test_class in "${STABLE_FAILURES[@]}"; do
    $first || echo "," >> "${REPORTS_DIR}/flaky-tests-report.json"
    first=false
    echo "    \"${test_class}\"" >> "${REPORTS_DIR}/flaky-tests-report.json"
done

cat >> "${REPORTS_DIR}/flaky-tests-report.json" << EOF
  ]
}
EOF

# ── Output summary ────────────────────────────────────────────────────────
echo ""
echo "=== Flaky Test Analysis Complete ==="
echo ""
echo "Results:"
echo "  Total tests analyzed: ${#TEST_AGGREGATE[@]}"
echo "  Stable passes: ${#STABLE_PASSES[@]}"
echo "  Stable failures: ${#STABLE_FAILURES[@]}"
echo "  Flaky tests: ${#FLAKY_TESTS[@]}"
echo ""
echo "Reports:"
echo "  ${REPORTS_DIR}/flaky-tests-report.md"
echo "  ${REPORTS_DIR}/flaky-tests-report.json"
echo ""

if [[ ${#FLAKY_TESTS[@]} -gt 0 ]]; then
    echo "⚠️  ${#FLAKY_TESTS[@]} flaky test(s) detected!"
    echo ""
    echo "Flaky tests:"
    for entry in "${FLAKY_TESTS[@]}"; do
        echo "  - ${entry}"
    done
    exit 1
else
    echo "✓ No flaky tests detected"
    exit 0
fi
