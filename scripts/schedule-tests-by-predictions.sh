#!/usr/bin/env bash
# ==========================================================================
# schedule-tests-by-predictions.sh — Test Scheduling Optimizer
#
# Uses predicted test execution times to reorder tests for optimal
# parallel execution. Implements bin-packing strategy:
#   1. Start long-running tests first (higher wall-clock time priority)
#   2. Fill gaps with medium/short tests for CPU utilization
#   3. Minimize total wall-clock time
#
# Usage:
#   bash scripts/schedule-tests-by-predictions.sh <tier-number> [--dry-run]
#
# Example:
#   bash scripts/schedule-tests-by-predictions.sh 1
#   bash scripts/schedule-tests-by-predictions.sh 2 --dry-run
#
# Environment:
#   PREDICTIONS_FILE   Path to predictions JSON (default: .yawl/ci/test-predictions.json)
#   TEP_DRY_RUN        Show schedule without executing (default: false)
#
# Output:
#   .yawl/ci/test-schedule-tier-N.json   (optimized test order for tier N)
#   STDOUT: Summary of scheduling optimization
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
PREDICTIONS_FILE="${PREDICTIONS_FILE:-${REPO_ROOT}/.yawl/ci/test-predictions.json}"
TIER_CONFIG="${REPO_ROOT}/.yawl/ci/tier-definitions.json"
SHARD_CONFIG="${REPO_ROOT}/.yawl/ci/test-shards.json"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_RESET='\033[0m'

# ── Parse arguments ───────────────────────────────────────────────────────
TIER_NUMBER="${1:-}"
DRY_RUN="${TEP_DRY_RUN:-0}"

if [[ -z "$TIER_NUMBER" ]]; then
    echo "Usage: $0 <tier-number> [--dry-run]"
    exit 1
fi

shift || true
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)  DRY_RUN=1; shift ;;
        *)          echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ── Validate files exist ───────────────────────────────────────────────────
if [[ ! -f "$PREDICTIONS_FILE" ]]; then
    echo "ERROR: Predictions file not found at $PREDICTIONS_FILE"
    echo "Run: bash scripts/predict-test-times.sh"
    exit 1
fi

if [[ ! -f "$TIER_CONFIG" ]]; then
    echo "ERROR: Tier definitions not found at $TIER_CONFIG"
    exit 1
fi

# ── Get tests for tier ─────────────────────────────────────────────────────
get_tests_for_tier() {
    local tier_num=$1
    local cluster_ids
    cluster_ids=$(jq -r ".tier_definitions.tier_${tier_num}.cluster_ids[]" "$TIER_CONFIG" 2>/dev/null || echo "")

    if [[ -z "$cluster_ids" ]]; then
        echo ""
        return
    fi

    local tests=""
    while IFS= read -r cluster_id; do
        [[ -z "$cluster_id" ]] && continue
        local shard_tests
        shard_tests=$(jq -r ".shards[] | select(.cluster == $cluster_id) | .tests[]?" "$SHARD_CONFIG" 2>/dev/null || true)
        if [[ -n "$shard_tests" ]]; then
            tests="${tests}${shard_tests}"$'\n'
        fi
    done <<< "$cluster_ids"

    echo "$tests" | grep -v '^$' || true
}

# ── Main scheduling logic ──────────────────────────────────────────────────
printf "${C_CYAN}[TIP] Test Scheduling Optimizer — Tier ${TIER_NUMBER}${C_RESET}\n"

# Get tests for this tier
TIER_TESTS=$(get_tests_for_tier "$TIER_NUMBER")
TEST_COUNT=$(echo "$TIER_TESTS" | grep -c . || echo 0)

if [[ $TEST_COUNT -eq 0 ]]; then
    printf "${C_YELLOW}→${C_RESET} No tests found for tier %d\n" "$TIER_NUMBER"
    exit 0
fi

# Create optimized schedule using Python
SCHEDULE_FILE="${REPO_ROOT}/.yawl/ci/test-schedule-tier-${TIER_NUMBER}.json"
mkdir -p "$(dirname "$SCHEDULE_FILE")"

python3 << PYTHON_EOF
import json
import sys
from datetime import datetime

# Load predictions and tier tests
try:
    with open('${PREDICTIONS_FILE}', 'r') as f:
        predictions_data = json.load(f)

    tier_tests = '''${TIER_TESTS}'''.strip().split('\n')
    tier_tests = [t for t in tier_tests if t.strip()]
except Exception as e:
    print(f"ERROR: Failed to load data: {e}", file=sys.stderr)
    sys.exit(1)

# Create lookup map: test_name -> predicted_ms
predictions_map = {}
for test in predictions_data.get('tests', []):
    test_name = test.get('test_name', '')
    predicted_ms = test.get('predicted_ms', 1000)
    predictions_map[test_name] = predicted_ms

# Sort tests by predicted duration (descending) for optimal packing
# Longer tests should run first to minimize wall-clock time
def get_predicted_time(test_name):
    return predictions_map.get(test_name, 1000)

sorted_tests = sorted(tier_tests, key=get_predicted_time, reverse=True)

# Calculate total time and parallelism estimates
total_ms = sum(get_predicted_time(t) for t in sorted_tests)
avg_ms = total_ms / len(sorted_tests) if sorted_tests else 0

# Estimate wall-clock time with bin-packing
# Simple heuristic: assume 4 parallel workers, distribute greedily
num_workers = 4
worker_times = [0] * num_workers
worker_tests = [[] for _ in range(num_workers)]

for test in sorted_tests:
    test_time = get_predicted_time(test)
    # Assign to worker with least total time (bin-packing)
    min_worker = min(range(num_workers), key=lambda i: worker_times[i])
    worker_times[min_worker] += test_time
    worker_tests[min_worker].append(test)

# Calculate metrics
wall_clock_ms = max(worker_times) if worker_times else total_ms
speedup = total_ms / wall_clock_ms if wall_clock_ms > 0 else 1.0
utilization = (total_ms / (wall_clock_ms * num_workers)) * 100 if wall_clock_ms > 0 else 0

# Build output schedule
schedule = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "tier": ${TIER_NUMBER},
    "total_tests": len(sorted_tests),
    "scheduling_strategy": "bin_packing_by_duration",
    "parallelism": num_workers,
    "timings": {
        "total_ms": int(total_ms),
        "avg_ms": int(avg_ms),
        "wall_clock_ms": int(wall_clock_ms),
        "speedup": round(speedup, 2),
        "cpu_utilization_percent": round(utilization, 1)
    },
    "worker_allocations": [
        {
            "worker": i,
            "allocated_ms": int(worker_times[i]),
            "test_count": len(worker_tests[i]),
            "tests": worker_tests[i]
        }
        for i in range(num_workers)
    ],
    "sequential_order": sorted_tests,
    "confidence": {
        "estimated_wall_clock_lower_ms": int(wall_clock_ms * 0.8),
        "estimated_wall_clock_upper_ms": int(wall_clock_ms * 1.2)
    }
}

# Write schedule
with open('${SCHEDULE_FILE}', 'w') as f:
    json.dump(schedule, f, indent=2)

print(f"✓ Schedule saved: ${SCHEDULE_FILE}")

# Print summary to stdout
print(f"\nScheduling Summary:")
print(f"  Total tests: {schedule['total_tests']}")
print(f"  Total duration (serial): {schedule['timings']['total_ms']}ms ({schedule['timings']['total_ms']/1000:.1f}s)")
print(f"  Wall-clock estimate: {schedule['timings']['wall_clock_ms']}ms ({schedule['timings']['wall_clock_ms']/1000:.1f}s)")
print(f"  Speedup: {schedule['timings']['speedup']}×")
print(f"  CPU utilization: {schedule['timings']['cpu_utilization_percent']:.1f}%")
print(f"\nWorker Allocations ({num_workers} parallel workers):")
for wa in schedule['worker_allocations']:
    print(f"  Worker {wa['worker']}: {wa['test_count']} tests, {wa['allocated_ms']}ms")

PYTHON_EOF

# Display schedule summary
printf "\n${C_GREEN}✓ Test schedule optimized${C_RESET}\n"

# Show confidence intervals
WALL_CLOCK=$(jq -r '.timings.wall_clock_ms' "$SCHEDULE_FILE" 2>/dev/null || echo "0")
LOWER=$(jq -r '.confidence.estimated_wall_clock_lower_ms' "$SCHEDULE_FILE" 2>/dev/null || echo "0")
UPPER=$(jq -r '.confidence.estimated_wall_clock_upper_ms' "$SCHEDULE_FILE" 2>/dev/null || echo "0")

printf "  Tier ${TIER_NUMBER} estimated time: ${WALL_CLOCK}ms (±$(((UPPER - LOWER) / 2))ms)\n"

if [[ "$DRY_RUN" == "1" ]]; then
    printf "\n${C_CYAN}Test Execution Order${C_RESET}:\n"
    jq -r '.sequential_order[]' "$SCHEDULE_FILE" | nl
fi

printf "\n${C_GREEN}[TIP] Scheduling complete${C_RESET}\n"
exit 0
