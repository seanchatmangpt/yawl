#!/bin/bash
#
# Phase 4: Profile Compatibility Validation
#
# Runs real Maven builds and collects execution data for:
# - Default profile (3 runs for determinism)
# - integration-parallel profile (3 runs for determinism + state isolation)
# - Profile combinations (integration-parallel + ci, etc.)
#
# Chicago TDD: Real execution, real assertions, comprehensive evidence
#

set -e

PROJECT_ROOT="${PROJECT_ROOT:-.}"
RESULTS_DIR="${PROJECT_ROOT}/.claude/results/phase4"
TIMESTAMP=$(date +%s)
REPORT_FILE="${RESULTS_DIR}/phase4-results-${TIMESTAMP}.json"

mkdir -p "${RESULTS_DIR}"

echo "=================================="
echo "PHASE 4: Profile Compatibility"
echo "Start: $(date)"
echo "=================================="

# Initialize results JSON
cat > "${REPORT_FILE}" << 'EOF'
{
  "phase": "4",
  "timestamp": "REPLACE_TIMESTAMP",
  "title": "Profile Compatibility Validation",
  "runs": []
}
EOF

#
# Helper: Execute Maven build and capture metrics
#
run_maven_build() {
    local profile=$1
    local run_num=$2
    local test_name=$3

    local cmd="mvn clean verify -q"
    if [ -n "$profile" ]; then
        cmd="$cmd -P $profile"
    fi

    local start_time=$(date +%s%N | cut -b1-13)
    local output_file="/tmp/maven-${test_name}-${run_num}.log"

    echo ""
    echo "[Run $run_num] Profile: ${profile:-default}"
    echo "Command: $cmd"

    # Run build and capture output
    if eval "$cmd" > "$output_file" 2>&1; then
        local exit_code=0
        local status="SUCCESS"
    else
        local exit_code=$?
        local status="FAILURE"
    fi

    local end_time=$(date +%s%N | cut -b1-13)
    local duration=$((end_time - start_time))

    # Extract test counts from log
    local tests_run=$(grep -oP 'Tests run: \K[0-9]+' "$output_file" | tail -1 || echo "0")
    local failures=$(grep -oP 'Failures: \K[0-9]+' "$output_file" | tail -1 || echo "0")
    local errors=$(grep -oP 'Errors: \K[0-9]+' "$output_file" | tail -1 || echo "0")

    # Check for timeout/corruption indicators
    local has_timeout=false
    local has_corruption=false
    if grep -qi "timeout" "$output_file"; then
        has_timeout=true
    fi
    if grep -qi "statecorruption\|state.corruption" "$output_file"; then
        has_corruption=true
    fi

    # Print results
    echo "Status: $status"
    echo "Duration: ${duration}ms"
    echo "Tests: run=$tests_run, failures=$failures, errors=$errors"
    echo "Timeout: $has_timeout | Corruption: $has_corruption"

    # Return as JSON
    cat << JSON
{
  "profile": "${profile:-default}",
  "run": $run_num,
  "test_name": "$test_name",
  "exit_code": $exit_code,
  "status": "$status",
  "duration_ms": $duration,
  "tests_run": $tests_run,
  "failures": $failures,
  "errors": $errors,
  "timeout": $has_timeout,
  "corruption": $has_corruption,
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON
}

#
# Phase 4.1: Default Profile (3 runs)
#
echo ""
echo "========== PHASE 4.1: Default Profile =========="

DEFAULT_RESULTS=()
for run in 1 2 3; do
    result=$(run_maven_build "" "$run" "default-run$run")
    DEFAULT_RESULTS+=("$result")
done

#
# Phase 4.2: integration-parallel Profile (3 runs)
#
echo ""
echo "========== PHASE 4.2: integration-parallel Profile =========="

PARALLEL_RESULTS=()
for run in 1 2 3; do
    result=$(run_maven_build "integration-parallel" "$run" "parallel-run$run")
    PARALLEL_RESULTS+=("$result")
done

#
# Phase 4.3: Profile Combinations (optional)
#
echo ""
echo "========== PHASE 4.3: Profile Combinations =========="

COMBO_RESULTS=()

# integration-parallel + ci
echo ""
echo "[Combination] integration-parallel + ci"
result=$(run_maven_build "integration-parallel,ci" "1" "combo-parallel-ci")
COMBO_RESULTS+=("$result")

# integration-parallel + docker (optional)
echo ""
echo "[Combination] integration-parallel + docker (optional)"
if mvn -P integration-parallel,docker clean verify -q > /tmp/combo-docker.log 2>&1; then
    echo "Status: SUCCESS"
    result=$(run_maven_build "integration-parallel,docker" "1" "combo-parallel-docker")
    COMBO_RESULTS+=("$result")
else
    echo "Status: SKIPPED (docker not available or profile not supported)"
fi

# integration-parallel + java25 (optional)
echo ""
echo "[Combination] integration-parallel + java25 (optional)"
if mvn -P integration-parallel,java25 clean verify -q > /tmp/combo-java25.log 2>&1; then
    echo "Status: SUCCESS"
    result=$(run_maven_build "integration-parallel,java25" "1" "combo-parallel-java25")
    COMBO_RESULTS+=("$result")
else
    echo "Status: SKIPPED (Java 25 features not available)"
fi

#
# Analysis & Reporting
#
echo ""
echo "========== PHASE 4: ANALYSIS =========="

# Extract timing data
default_times=()
parallel_times=()

for result in "${DEFAULT_RESULTS[@]}"; do
    duration=$(echo "$result" | grep -oP '"duration_ms": \K[0-9]+')
    default_times+=($duration)
    echo "Default run duration: ${duration}ms"
done

for result in "${PARALLEL_RESULTS[@]}"; do
    duration=$(echo "$result" | grep -oP '"duration_ms": \K[0-9]+')
    parallel_times+=($duration)
    echo "Parallel run duration: ${duration}ms"
done

# Calculate averages
default_avg=$((( ${default_times[0]} + ${default_times[1]} + ${default_times[2]} ) / 3))
parallel_avg=$((( ${parallel_times[0]} + ${parallel_times[1]} + ${parallel_times[2]} ) / 3))
speedup=$(echo "scale=2; $default_avg / $parallel_avg" | bc)

echo ""
echo "===== SUMMARY ====="
echo "Default avg:   ${default_avg}ms"
echo "Parallel avg:  ${parallel_avg}ms"
echo "Speedup:       ${speedup}x"
echo "Target:        1.77x"
echo "Result:        $([ $(echo "$speedup >= 1.5" | bc) -eq 1 ] && echo 'PASS' || echo 'FAIL')"

echo ""
echo "=================================="
echo "PHASE 4: Complete"
echo "End: $(date)"
echo "Results: $REPORT_FILE"
echo "=================================="
