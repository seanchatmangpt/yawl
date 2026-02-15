#!/bin/bash
# =============================================================================
# Order Fulfillment Permutation Suite - Challenge autonomous agents
# =============================================================================
# Runs multiple scenario permutations (baseline, concurrent, rapid, sequential)
# to stress-test agent coordination, throughput, and latency.
#
# Prerequisites:
#   - YAWL engine running
#   - Party agents running (docker-compose simulation or separate)
#   - ZAI_API_KEY set for agents
#
# Usage:
#   ./scripts/run-orderfulfillment-permutations.sh
#
# Run specific permutations only:
#   PERMUTATION_IDS=baseline,rapid ./scripts/run-orderfulfillment-permutations.sh
#
# With custom config:
#   PERMUTATION_CONFIG=my-permutations.json ./scripts/run-orderfulfillment-permutations.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8888/yawl}"
export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
export YAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}"
export UPLOAD_SPEC="${UPLOAD_SPEC:-true}"
export PERMUTATION_CONFIG="${PERMUTATION_CONFIG:-config/orderfulfillment-permutations.json}"

if [ ! -f "$PERMUTATION_CONFIG" ]; then
    echo "Config not found: $PERMUTATION_CONFIG"
    exit 1
fi

if [ ! -d "classes" ]; then
    echo "Building..."
    ant -f build/build.xml compile -q
fi

echo "Order Fulfillment Permutation Suite"
echo "  Engine: $YAWL_ENGINE_URL"
echo "  Config: $PERMUTATION_CONFIG"
echo ""

ant -f build/build.xml run-orderfulfillment-permutations
exit $?
