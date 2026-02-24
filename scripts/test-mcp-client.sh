#!/usr/bin/env bash
#
# Test YAWL MCP Server using MCP Client
#
# This script runs the YawlMcpClient against the MCP server
# to perform real integration testing.
#
# Usage: ./test-mcp-client.sh [server_url]
#
# Environment Variables:
#   MCP_SERVER_URL   - MCP server URL (default: http://localhost:3000)
#   ZHIPU_API_KEY    - Z.AI API key for AI-enhanced testing (optional)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
MCP_SERVER_URL="${1:-${MCP_SERVER_URL:-http://localhost:3000}}"
TIMEOUT="${TIMEOUT:-30}"

# Build classpath
build_classpath() {
    local cp="$PROJECT_DIR/classes"

    if [ -d "$PROJECT_DIR/build/3rdParty/lib" ]; then
        for jar in "$PROJECT_DIR/build/3rdParty/lib"/*.jar; do
            if [ -f "$jar" ]; then
                cp="$cp:$jar"
            fi
        done
    fi

    echo "$cp"
}

CLASSPATH=$(build_classpath)

echo "==========================================="
echo "YAWL MCP Client Test"
echo "==========================================="
echo "Server URL: $MCP_SERVER_URL"
echo "==========================================="
echo ""

# Check server availability
check_server() {
    echo "Checking MCP server availability..."

    local max_attempts=10
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s --connect-timeout 2 "$MCP_SERVER_URL" > /dev/null 2>&1; then
            echo "MCP server is reachable at $MCP_SERVER_URL"
            return 0
        fi

        echo "Attempt $attempt/$max_attempts: Server not ready, waiting..."
        sleep 2
        ((attempt++))
    done

    echo "ERROR: Cannot connect to MCP server at $MCP_SERVER_URL"
    echo "Make sure the server is running: ./start-mcp-server.sh"
    return 1
}

# Run client tests
run_client_tests() {
    echo ""
    echo "Running MCP client tests..."
    echo ""

    java -cp "$CLASSPATH" \
        -Dmcp.server.url="$MCP_SERVER_URL" \
        org.yawlfoundation.yawl.integration.mcp.YawlMcpClient "$MCP_SERVER_URL"
}

# Main
if check_server; then
    run_client_tests
    echo ""
    echo "==========================================="
    echo "Test Complete"
    echo "==========================================="
else
    exit 1
fi
