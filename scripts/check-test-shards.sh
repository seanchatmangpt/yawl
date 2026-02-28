#!/usr/bin/env bash
# ==========================================================================
# check-test-shards.sh - Display current test shards configuration
# ==========================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

CONFIG_FILE="${1:-.yawl/ci/test-shards.json}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

if [[ ! -f "$CONFIG_FILE" ]]; then
    printf "${C_RED}✗ Config file not found: %s${C_RESET}\n" "$CONFIG_FILE"
    printf "${C_YELLOW}  Run: bash scripts/cluster-tests.sh${C_RESET}\n"
    exit 1
fi

python3 -c "
import json
import sys

config_file = '$CONFIG_FILE'

try:
    with open(config_file, 'r') as f:
        config = json.load(f)
except Exception as e:
    print(f'Error reading config: {e}', file=sys.stderr)
    sys.exit(1)

print('\n' + '='*70)
print('TEST SHARDS CONFIGURATION')
print('='*70)

print(f'\nGenerated: {config[\"timestamp\"]}')
print(f'Total tests: {config[\"total_tests\"]}')
print(f'Total shards: {config[\"shard_count\"]}')
print(f'Total estimated duration: {config[\"total_estimated_duration_ms\"] / 1000:.1f}s')

print(f'\nLoad Distribution:')
load = config['load_distribution']
print(f'  Min shard duration:  {load[\"min_duration_ms\"] / 1000:.1f}s')
print(f'  Max shard duration:  {load[\"max_duration_ms\"] / 1000:.1f}s')
print(f'  Avg shard duration:  {load[\"avg_duration_ms\"] / 1000:.1f}s')
print(f'  Std deviation:       {load[\"std_deviation_ms\"] / 1000:.1f}s')
print(f'  Balance score:       {load[\"balance_score\"]:.1%}')

print(f'\nShard Details:')
print(f'{\"Shard\":<8} {\"Tests\":<8} {\"Duration\":<12} {\"Clusters\":<30}')
print('-' * 70)

for shard in config['shards']:
    shard_id = shard['shard_id']
    test_count = shard['test_count']
    duration = shard['estimated_duration_ms'] / 1000
    clusters = ','.join(str(c) for c in shard['clusters']) or '—'

    print(f'{shard_id:<8} {test_count:<8} {duration:>6.2f}s      {clusters:<30}')

    if shard['tests']:
        for test in shard['tests']:
            parts = test.rsplit('.', 1)
            if len(parts) == 2:
                class_name = parts[0].split('.')[-1]
                method = parts[1]
                print(f'         └─ {class_name}#{method}')

print('\nCluster Definitions:')
for cluster_id, description in sorted(config['cluster_definitions'].items()):
    print(f'  {cluster_id}: {description}')

print('\n' + '='*70)
"

exit 0
