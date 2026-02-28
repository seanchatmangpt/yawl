#!/usr/bin/env bash
# ==========================================================================
# run-shard.sh - Run a single test shard
#
# This script runs all tests assigned to a specific shard based on the
# test-shards.json configuration.
# ==========================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
SHARD_INDEX="${1:-0}"
CONFIG_FILE="${2:-.yawl/ci/test-shards.json}"
SHARD_COUNT="${SHARD_COUNT:-8}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# Validate inputs
if ! [[ "$SHARD_INDEX" =~ ^[0-9]+$ ]] || [[ $SHARD_INDEX -lt 0 ]] || [[ $SHARD_INDEX -ge $SHARD_COUNT ]]; then
    printf "${C_RED}✗ Invalid shard index: %s (must be 0-%d)${C_RESET}\n" "$SHARD_INDEX" "$((SHARD_COUNT - 1))"
    exit 1
fi

if [[ ! -f "$CONFIG_FILE" ]]; then
    printf "${C_YELLOW}⚠ Config file not found: %s${C_RESET}\n" "$CONFIG_FILE"
    printf "${C_CYAN}  Run: bash scripts/cluster-tests.sh${C_RESET}\n"
    exit 1
fi

printf "${C_CYAN}Running test shard %d of %d${C_RESET}\n" "$SHARD_INDEX" "$SHARD_COUNT"

# Extract test names for this shard using Python
test_names=$(python3 << 'PYTHON'
import json
import sys

shard_index = int(sys.argv[1])
config_file = sys.argv[2]

try:
    with open(config_file, 'r') as f:
        config = json.load(f)
except Exception as e:
    print(f"Error reading config: {e}", file=sys.stderr)
    sys.exit(1)

# Find shard configuration
shard_config = None
for shard in config.get('shards', []):
    if shard['shard_id'] == shard_index:
        shard_config = shard
        break

if not shard_config:
    print(f"Error: Shard {shard_index} not found in config", file=sys.stderr)
    sys.exit(1)

tests = shard_config.get('tests', [])
if not tests:
    print("# No tests in shard")
    sys.exit(0)

# Convert test class names to Maven test filter format
for test in tests:
    parts = test.rsplit('.', 1)
    if len(parts) == 2:
        class_name, method_name = parts
        class_simple = class_name.split('.')[-1]
        print(f"{class_simple}#{method_name}")
    else:
        print(test)

sys.exit(0)
PYTHON
"$SHARD_INDEX" "$CONFIG_FILE"
)

exit_code=$?
if [[ $exit_code -ne 0 ]]; then
    exit $exit_code
fi

# Get shard info
shard_info=$(python3 << 'PYTHON'
import json
import sys

shard_index = int(sys.argv[1])
config_file = sys.argv[2]

with open(config_file, 'r') as f:
    config = json.load(f)

for shard in config.get('shards', []):
    if shard['shard_id'] == shard_index:
        print(f"duration:{shard['estimated_duration_ms']}")
        print(f"tests:{shard['test_count']}")
        print(f"clusters:{','.join(str(c) for c in shard['clusters'])}")
        break

sys.exit(0)
PYTHON
"$SHARD_INDEX" "$CONFIG_FILE"
)

# Parse shard info
duration_ms=0
test_count=0
while IFS=':' read -r key val; do
    case "$key" in
        duration) duration_ms=$val ;;
        tests) test_count=$val ;;
        clusters) clusters=$val ;;
    esac
done <<< "$shard_info"

printf "${C_CYAN}  Tests: %d${C_RESET}\n" "$test_count"
printf "${C_CYAN}  Estimated duration: %.1fs${C_RESET}\n" "$(awk "BEGIN {printf \"%.1f\", $duration_ms / 1000}")"
printf "${C_CYAN}  Clusters: %s${C_RESET}\n" "$clusters"

if [[ -z "$test_names" ]] || [[ "$test_names" == "# No tests in shard" ]]; then
    printf "\n${C_YELLOW}⚠ No tests to run in shard %d${C_RESET}\n" "$SHARD_INDEX"
    exit 0
fi

# Build Maven test filter argument
test_filter=$(echo "$test_names" | paste -sd ',' -)

if [[ -z "$test_filter" ]]; then
    printf "\n${C_YELLOW}⚠ Could not build test filter for shard %d${C_RESET}\n" "$SHARD_INDEX"
    exit 0
fi

printf "\n${C_CYAN}Test filter: %s${C_RESET}\n" "$test_filter"

# Run Maven tests for this shard
printf "\n${C_GREEN}Starting test execution...${C_RESET}\n\n"

# Use simpler test execution (no pipes to allow proper exit code)
mvn clean verify \
    -DskipITs=false \
    -Dtest="$test_filter" \
    -Dit.test="$test_filter" \
    -Dsurefire.forkCount=2 \
    -Dtest.parallel.shard.index="$SHARD_INDEX" \
    -Dtest.parallel.shard.count="$SHARD_COUNT" \
    -P integration-parallel

exit_code=$?

printf "\n${C_GREEN}Shard %d test execution complete (exit code: %d)${C_RESET}\n" "$SHARD_INDEX" "$exit_code"

exit $exit_code
