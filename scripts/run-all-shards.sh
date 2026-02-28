#!/usr/bin/env bash
# ==========================================================================
# run-all-shards.sh - Execute all test shards in parallel
#
# Usage:
#   bash scripts/run-all-shards.sh [shard-count] [parallel-jobs]
#
# Examples:
#   bash scripts/run-all-shards.sh           # Run 8 shards with 4 parallel jobs
#   bash scripts/run-all-shards.sh 4 2       # Run 4 shards with 2 parallel jobs
#
# Environment:
#   SHARD_COUNT    - Total shards (default: 8)
#   PARALLEL_JOBS  - Parallel shard executions (default: 4)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
SHARD_COUNT="${1:-${SHARD_COUNT:-8}}"
PARALLEL_JOBS="${2:-${PARALLEL_JOBS:-4}}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

echo ""
printf "${C_CYAN}Running ${SHARD_COUNT} shards with ${PARALLEL_JOBS} parallel jobs${C_RESET}\n"

# Create temp directory for shard logs
LOG_DIR=$(mktemp -d)
trap "rm -rf $LOG_DIR" EXIT

# Run shards in parallel using GNU parallel or xargs
START_TIME=$(date +%s)

if command -v parallel >/dev/null 2>&1; then
    # Use GNU parallel if available
    seq 0 $((SHARD_COUNT - 1)) | \
        parallel -j "$PARALLEL_JOBS" --lb --eta \
            "bash scripts/run-shard.sh {} $SHARD_COUNT > $LOG_DIR/shard-{}.log 2>&1; echo \$? > $LOG_DIR/shard-{}.exit"
else
    # Fallback to xargs
    seq 0 $((SHARD_COUNT - 1)) | \
        xargs -P "$PARALLEL_JOBS" -I {} \
            bash -c "bash scripts/run-shard.sh {} $SHARD_COUNT > $LOG_DIR/shard-{}.log 2>&1; echo \$? > $LOG_DIR/shard-{}.exit"
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# Check results
FAILED=0
PASSED=0
for i in $(seq 0 $((SHARD_COUNT - 1))); do
    EXIT_CODE=$(cat "$LOG_DIR/shard-$i.exit" 2>/dev/null || echo "1")
    if [[ "$EXIT_CODE" -eq 0 ]]; then
        PASSED=$((PASSED + 1))
    else
        FAILED=$((FAILED + 1))
        echo ""
        printf "${C_RED}Shard $i failed:${C_RESET}\n"
        tail -20 "$LOG_DIR/shard-$i.log" 2>/dev/null || true
    fi
done

# Summary
echo ""
printf "${C_CYAN}========================================${C_RESET}\n"
if [[ $FAILED -eq 0 ]]; then
    printf "${C_GREEN}✓ ALL SHARDS PASSED${C_RESET} | ${PASSED}/${SHARD_COUNT} | ${ELAPSED}s\n"
    exit 0
else
    printf "${C_RED}✗ SHARDS FAILED${C_RESET} | ${PASSED} passed, ${FAILED} failed | ${ELAPSED}s\n"
    exit 1
fi
