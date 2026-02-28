#!/usr/bin/env bash
# ==========================================================================
# run-all-shards.sh - Run all test shards sequentially (for local testing)
#
# This script runs all shards one by one, useful for testing the sharding
# logic locally before pushing to CI.
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# Configuration
CONFIG_FILE="${1:-.yawl/ci/test-shards.json}"
SHARD_COUNT=8

if [[ ! -f "$CONFIG_FILE" ]]; then
    printf "${C_YELLOW}Config file not found. Generating...${C_RESET}\n"
    bash "$SCRIPT_DIR/cluster-tests.sh"
fi

# Extract shard count from config
SHARD_COUNT=$(python3 -c "import json; print(json.load(open('$CONFIG_FILE')).get('shard_count', 8))")

printf "${C_CYAN}Running %d test shards sequentially${C_RESET}\n\n" "$SHARD_COUNT"

failed_shards=()
total_time=0

# Run each shard
for ((shard=0; shard<SHARD_COUNT; shard++)); do
    printf "${C_CYAN}========== Shard %d/%d ==========${C_RESET}\n" "$((shard+1))" "$SHARD_COUNT"
    
    start_time=$(date +%s)
    
    if bash "$SCRIPT_DIR/run-shard.sh" "$shard" "$CONFIG_FILE"; then
        printf "${C_GREEN}✓ Shard %d passed${C_RESET}\n\n" "$shard"
    else
        printf "${C_RED}✗ Shard %d failed${C_RESET}\n\n" "$shard"
        failed_shards+=("$shard")
    fi
    
    end_time=$(date +%s)
    elapsed=$((end_time - start_time))
    total_time=$((total_time + elapsed))
done

# Summary
printf "\n${C_CYAN}========== SUMMARY ==========${C_RESET}\n"
printf "Total time: %d seconds\n" "$total_time"
printf "Shards run: %d\n" "$SHARD_COUNT"

if [[ ${#failed_shards[@]} -eq 0 ]]; then
    printf "${C_GREEN}✓ All shards passed${C_RESET}\n"
    exit 0
else
    printf "${C_RED}✗ Failed shards: ${failed_shards[*]}${C_RESET}\n"
    exit 1
fi
