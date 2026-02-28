#!/usr/bin/env bash
# ==========================================================================
# run-shard.sh - Execute a single test shard
#
# Usage:
#   bash scripts/run-shard.sh <shard-index> [shard-count]
#
# Examples:
#   bash scripts/run-shard.sh 0       # Run shard 0 of 8 (default)
#   bash scripts/run-shard.sh 3 4     # Run shard 3 of 4
#
# Environment:
#   SHARD_INDEX  - Zero-based shard index (default: 0)
#   SHARD_COUNT  - Total shards (default: 8)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Parse arguments
SHARD_INDEX="${1:-${SHARD_INDEX:-0}}"
SHARD_COUNT="${2:-${SHARD_COUNT:-8}}"

# Validate
if [[ "$SHARD_INDEX" -lt 0 ]] || [[ "$SHARD_INDEX" -ge "$SHARD_COUNT" ]]; then
    echo "ERROR: Shard index must be 0 to $((SHARD_COUNT - 1)), got: $SHARD_INDEX"
    exit 1
fi

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_RESET='\033[0m'

echo ""
printf "${C_CYAN}Shard ${SHARD_INDEX}/${SHARD_COUNT}${C_RESET}\n"

# Set shard properties for JUnit
export yawl_test_shard_index="$SHARD_INDEX"
export yawl_test_shard_count="$SHARD_COUNT"

# Run tests for this shard
mvn test \
    -Dmaven.test.skip=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Djunit.jupiter.execution.parallel.config.dynamic.factor=3.0 \
    -Dyawl.test.shard.index="$SHARD_INDEX" \
    -Dyawl.test.shard.count="$SHARD_COUNT" \
    -q

EXIT_CODE=$?

if [[ $EXIT_CODE -eq 0 ]]; then
    printf "${C_GREEN}✓ Shard ${SHARD_INDEX} passed${C_RESET}\n"
else
    printf "\033[91m✗ Shard ${SHARD_INDEX} failed (exit ${EXIT_CODE})\033[0m\n"
fi

exit $EXIT_CODE
