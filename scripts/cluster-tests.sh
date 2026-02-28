#!/usr/bin/env bash
# ==========================================================================
# cluster-tests.sh - Generate test shards configuration with load balancing
#
# This script generates .yawl/ci/test-shards.json by:
# 1. Reading test times from test-times.json
# 2. Assigning tests to clusters based on execution time
# 3. Distributing tests across shards using greedy bin-packing algorithm
# 4. Minimizing total duration variance across shards
#
# Usage:
#   bash scripts/cluster-tests.sh [shard-count] [input-file] [output-file]
#
# Examples:
#   bash scripts/cluster-tests.sh                    # Default: 8 shards
#   bash scripts/cluster-tests.sh 16                 # Custom: 16 shards
#   bash scripts/cluster-tests.sh 8 /tmp/times.json  # Custom input file
#
# Environment:
#   SHARD_COUNT   - Number of shards (default: 8)
#   INPUT_FILE    - Test times JSON (default: .yawl/ci/test-times.json)
#   OUTPUT_FILE   - Shards config (default: .yawl/ci/test-shards.json)
#
# Exit codes:
#   0 - Success
#   1 - Error (missing input or calculation failure)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
SHARD_COUNT="${1:-8}"
INPUT_FILE="${2:-.yawl/ci/test-times.json}"
OUTPUT_FILE="${3:-.yawl/ci/test-shards.json}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# Ensure output directory exists
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Validate inputs
if [[ ! -f "$INPUT_FILE" ]]; then
    # If input file doesn't exist, run analyze-test-times.sh first
    printf "${C_YELLOW}Input file not found: %s${C_RESET}\n" "$INPUT_FILE"
    printf "${C_CYAN}Running analyze-test-times.sh to generate test times...${C_RESET}\n"
    bash "$SCRIPT_DIR/analyze-test-times.sh"
fi

if [[ ! -f "$INPUT_FILE" ]]; then
    printf "${C_RED}✗ Failed to generate test times${C_RESET}\n"
    exit 1
fi

if [[ ! $SHARD_COUNT =~ ^[0-9]+$ ]] || [[ $SHARD_COUNT -lt 1 ]] || [[ $SHARD_COUNT -gt 32 ]]; then
    printf "${C_RED}✗ Invalid shard count: %s (must be 1-32)${C_RESET}\n" "$SHARD_COUNT"
    exit 1
fi

printf "${C_CYAN}Generating test shards configuration...${C_RESET}\n"

# Use Python to parse JSON and perform greedy bin-packing
python3 << 'PYTHON_SCRIPT'
import json
import sys
from datetime import datetime

# Configuration from bash
shard_count = int(sys.argv[1])
input_file = sys.argv[2]
output_file = sys.argv[3]

# Read test times
with open(input_file, 'r') as f:
    data = json.load(f)

tests = data.get('tests', [])

if not tests:
    print(f"Error: No tests found in {input_file}", file=sys.stderr)
    sys.exit(1)

# Classify tests into clusters based on execution time
cluster_definitions = {
    1: {"description": "Fast (<100ms)", "min": 0, "max": 100},
    2: {"description": "Medium (100ms-5s)", "min": 100, "max": 5000},
    3: {"description": "Slow (5s-30s)", "min": 5000, "max": 30000},
    4: {"description": "Heavy (>30s)", "min": 30000, "max": float('inf')}
}

def get_cluster(time_ms):
    """Determine cluster for a test based on execution time."""
    for cluster_id, bounds in cluster_definitions.items():
        if bounds['min'] <= time_ms <= bounds['max']:
            return cluster_id
    return 4

# Classify tests
classified_tests = []
for test in tests:
    cluster = get_cluster(test['time_ms'])
    classified_tests.append({
        'name': test['name'],
        'time_ms': test['time_ms'],
        'cluster': cluster
    })

# Sort by time descending (greedy bin-packing algorithm)
classified_tests.sort(key=lambda x: x['time_ms'], reverse=True)

# Initialize shards
shards = [{'shard_id': i, 'tests': [], 'estimated_duration_ms': 0, 'clusters': set()}
          for i in range(shard_count)]

# Greedy bin-packing: assign each test to the shard with shortest duration
for test in classified_tests:
    # Find shard with minimum duration
    min_shard = min(shards, key=lambda s: s['estimated_duration_ms'])
    min_shard['tests'].append(test['name'])
    min_shard['estimated_duration_ms'] += int(test['time_ms'])
    min_shard['clusters'].add(test['cluster'])

# Convert clusters set to list and calculate final stats
for shard in shards:
    shard['clusters'] = sorted(list(shard['clusters']))
    shard['cluster_description'] = ', '.join(
        [f"C{c}: {cluster_definitions[c]['description']}" for c in shard['clusters']]
    )

# Calculate duration variance
durations = [s['estimated_duration_ms'] for s in shards]
avg_duration = sum(durations) / len(durations) if durations else 0
variance = sum((d - avg_duration) ** 2 for d in durations) / len(durations) if durations else 0
std_dev = variance ** 0.5
max_duration = max(durations) if durations else 0
min_duration = min(durations) if durations else 0
duration_range = max_duration - min_duration

# Output configuration
config = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "shard_count": shard_count,
    "total_tests": len(classified_tests),
    "total_estimated_duration_ms": sum(durations),
    "shards": [
        {
            "shard_id": s['shard_id'],
            "tests": s['tests'],
            "test_count": len(s['tests']),
            "estimated_duration_ms": s['estimated_duration_ms'],
            "cluster": s['clusters'][0] if s['clusters'] else 0,
            "clusters": s['clusters'],
            "cluster_description": s['cluster_description']
        }
        for s in shards
    ],
    "cluster_definitions": {
        str(k): v['description'] for k, v in cluster_definitions.items()
    },
    "load_distribution": {
        "min_duration_ms": int(min_duration),
        "max_duration_ms": int(max_duration),
        "avg_duration_ms": int(avg_duration),
        "duration_range_ms": int(duration_range),
        "std_deviation_ms": int(std_dev),
        "variance": float(variance),
        "balance_score": float(1.0 - (duration_range / max_duration if max_duration > 0 else 1.0))
    }
}

# Write output
with open(output_file, 'w') as f:
    json.dump(config, f, indent=2)

print(f"✓ Generated shards configuration: {output_file}")
print(f"  Shards: {shard_count}")
print(f"  Total tests: {len(classified_tests)}")
print(f"  Total duration: {sum(durations) / 1000:.1f}s")
print(f"  Min shard duration: {min_duration / 1000:.1f}s")
print(f"  Max shard duration: {max_duration / 1000:.1f}s")
print(f"  Avg shard duration: {avg_duration / 1000:.1f}s")
print(f"  Duration std dev: {std_dev / 1000:.1f}s")
print(f"  Load balance score: {config['load_distribution']['balance_score']:.2%}")

sys.exit(0)
PYTHON_SCRIPT
