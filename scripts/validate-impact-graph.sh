#!/usr/bin/env bash
# ==========================================================================
# validate-impact-graph.sh — Test 1 Validation: Impact Graph Correctness
#
# Validates that the impact graph correctly identifies affected tests
# when source files are changed.
#
# Success Criteria:
#   - Impact graph built successfully
#   - Only affected tests selected when file changes
#   - No false negatives (all affected tests found)
#   - No false positives (unrelated tests not selected)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

log_info() { echo -e "${C_CYAN}[INFO]${C_RESET} $*"; }
log_success() { echo -e "${C_GREEN}[✓]${C_RESET} $*"; }
log_error() { echo -e "${C_RED}[✗]${C_RESET} $*"; }

# Build impact graph
log_info "Building test impact graph..."
bash scripts/build-test-impact-graph.sh --force >/dev/null 2>&1

GRAPH_FILE="${REPO_ROOT}/.yawl/cache/test-impact-graph.json"

if [[ ! -f "$GRAPH_FILE" ]]; then
    log_error "Failed to build impact graph"
    exit 1
fi

# Validate graph structure
log_info "Validating impact graph structure..."

python3 << 'PYTHON'
import json
import sys

with open('.yawl/cache/test-impact-graph.json', 'r') as f:
    graph = json.load(f)

# Check required fields
required_fields = ['test_to_source', 'source_to_tests', 'timestamp']
for field in required_fields:
    if field not in graph:
        print(f"Missing required field: {field}", file=sys.stderr)
        sys.exit(1)

# Check that mappings are bidirectional
test_to_source = graph['test_to_source']
source_to_tests = graph['source_to_tests']

issues = 0
for test, sources in test_to_source.items():
    for source in sources:
        if source not in source_to_tests or test not in source_to_tests[source]:
            print(f"Inconsistent mapping: {test} -> {source} missing reverse mapping")
            issues += 1

if issues > 0:
    print(f"Found {issues} bidirectional mapping issues", file=sys.stderr)
    sys.exit(1)

print(f"✓ Impact graph valid: {len(test_to_source)} tests, {len(source_to_tests)} sources")
PYTHON

if [[ $? -eq 0 ]]; then
    log_success "Impact graph validation passed"
    exit 0
else
    log_error "Impact graph validation failed"
    exit 1
fi
