#!/bin/bash
# =============================================================================
# Run ZAI + MCP + A2A integration tests (Docker/devcontainer-first)
# =============================================================================
# Start A2A server in background if needed, then run SelfPlayTest via Ant.
# MCP test uses connectStdio (spawns MCP server subprocess); A2A test needs
# A2A server running. YAWL engine must be reachable for MCP and Function Calling.
#
# Prereqs: ZAI_API_KEY, YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD
# Run inside: docker compose run --rm yawl-dev bash scripts/run-zai-mcp-a2a.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
export YAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}"
export A2A_PORT="${A2A_PORT:-8081}"
export A2A_AGENT_URL="${A2A_AGENT_URL:-http://localhost:8081}"

echo "========================================"
echo "ZAI + MCP + A2A Integration"
echo "========================================"
echo "YAWL_ENGINE_URL: $YAWL_ENGINE_URL"
echo "A2A_AGENT_URL:   $A2A_AGENT_URL"
echo "========================================"
echo ""

if [ -z "$ZAI_API_KEY" ]; then
    echo "Error: ZAI_API_KEY is required. Set it and run again."
    exit 1
fi

# Build if needed
if [ ! -d "classes" ]; then
    echo "Building YAWL..."
    ant -f build/build.xml compile
    echo ""
fi

# Start A2A server in background if not already reachable
if ! curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "$A2A_AGENT_URL" 2>/dev/null | grep -q '200\|404'; then
    echo "Starting A2A server in background..."
    if [ -x "$PROJECT_DIR/run-a2a-server.sh" ]; then
        "$PROJECT_DIR/run-a2a-server.sh" &
    else
        CLASSPATH="classes:build/3rdParty/lib/*"
        java -cp "$CLASSPATH" \
            -DYAWL_ENGINE_URL="$YAWL_ENGINE_URL" \
            -DYAWL_USERNAME="$YAWL_USERNAME" \
            -DYAWL_PASSWORD="$YAWL_PASSWORD" \
            -DA2A_PORT="$A2A_PORT" \
            org.yawlfoundation.yawl.integration.a2a.YawlA2AServer &
    fi
    A2A_PID=$!
    echo "A2A server PID: $A2A_PID"
    for i in 1 2 3 4 5 6 7 8 9 10; do
        if curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "$A2A_AGENT_URL" 2>/dev/null | grep -q '200\|404'; then
            echo "A2A server ready."
            break
        fi
        [ $i -eq 10 ] && { echo "Warning: A2A server may not be ready yet."; }
        sleep 1
    done
    echo ""
else
    echo "A2A server already reachable at $A2A_AGENT_URL"
    echo ""
fi

# Run ZAI integration tests (MCP uses connectStdio; A2A uses A2A_AGENT_URL)
echo "Running ZAI/MCP/A2A integration tests..."
ant -f build/build.xml run-zai-integration

echo ""
echo "Done."
