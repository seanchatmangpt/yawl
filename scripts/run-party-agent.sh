#!/usr/bin/env bash
# =============================================================================
# Run an autonomous party agent for order fulfillment simulation
# =============================================================================
# Requires: AGENT_CAPABILITY, ZAI_API_KEY, YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD
# Optional: AGENT_PORT (default 8091)
#
# Example:
#   AGENT_CAPABILITY="Ordering: procurement, purchase orders, approvals" \
#   AGENT_PORT=8091 \
#   ./scripts/run-party-agent.sh
#
# Run inside: docker compose run --rm yawl-dev bash scripts/run-party-agent.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
export YAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}"
export AGENT_PORT="${AGENT_PORT:-8091}"

if [ -z "$AGENT_CAPABILITY" ]; then
    echo "Error: AGENT_CAPABILITY is required."
    echo "Example: AGENT_CAPABILITY=\"Ordering: procurement, purchase orders, approvals\""
    exit 1
fi

if [ -z "$ZAI_API_KEY" ]; then
    echo "Error: ZAI_API_KEY is required for agent reasoning."
    exit 1
fi

if [ ! -d "classes" ]; then
    echo "Building YAWL..."
    ant -f build/build.xml compile
fi

echo "Starting Party Agent..."
echo "  AGENT_CAPABILITY: $AGENT_CAPABILITY"
echo "  AGENT_PORT: $AGENT_PORT"
echo "  YAWL_ENGINE_URL: $YAWL_ENGINE_URL"
echo ""

ant -f build/build.xml run-party-agent
