#!/usr/bin/env bash
#
# Start YAWL MCP Server
#
# This script starts the MCP server with real YAWL engine integration.
# It will FAIL if:
#   - YAWL engine is not running
#   - MCP server cannot connect to engine
#   - Any stub/unsupported operation is encountered
#
# Usage: ./start-mcp-server.sh [port]
#
# Environment Variables:
#   YAWL_ENGINE_URL  - YAWL engine URL (default: http://localhost:8080/yawl/ib)
#   YAWL_USERNAME    - YAWL username (default: admin)
#   YAWL_PASSWORD    - YAWL password (default: YAWL)
#   MCP_TRANSPORT    - Transport type: SSE, STDIO, STREAMABLE_HTTP (default: SSE)
#   MCP_ENDPOINT     - MCP endpoint path (default: /mcp)
#   MCP_LOG_LEVEL    - Log level: DEBUG, INFO, WARNING, ERROR (default: INFO)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"
export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
export YAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}"
export MCP_TRANSPORT="${MCP_TRANSPORT:-SSE}"
export MCP_PORT="${MCP_PORT:-3000}"
export MCP_ENDPOINT="${MCP_ENDPOINT:-/mcp}"
export MCP_LOG_LEVEL="${MCP_LOG_LEVEL:-INFO}"

# Port from command line
if [ -n "${1:-}" ]; then
    MCP_PORT="$1"
    export MCP_PORT
fi

echo "==========================================="
echo "YAWL MCP Server Startup"
echo "==========================================="
echo "Engine URL:  $YAWL_ENGINE_URL"
echo "Transport:   $MCP_TRANSPORT"
echo "Port:        $MCP_PORT"
echo "Endpoint:    $MCP_ENDPOINT"
echo "Log Level:   $MCP_LOG_LEVEL"
echo "==========================================="

cd "$PROJECT_DIR"

# Verify build exists
if [ ! -d "target/classes" ] && [ ! -d "classes" ]; then
    echo ""
    echo "ERROR: Project not built. Run ./scripts/build-mcp.sh first"
    exit 1
fi

# Check YAWL engine connectivity
echo ""
echo "Checking YAWL engine connectivity..."

ENGINE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$YAWL_ENGINE_URL" 2>/dev/null || echo "000")

if [ "$ENGINE_STATUS" = "000" ]; then
    echo "ERROR: Cannot reach YAWL engine at $YAWL_ENGINE_URL"
    echo ""
    echo "Make sure YAWL engine is running:"
    echo "  1. Deploy yawl-engine.war to Tomcat"
    echo "  2. Verify engine at: $YAWL_ENGINE_URL"
    echo "  3. Check YAWL_USERNAME and YAWL_PASSWORD are correct"
    exit 1
fi

echo "YAWL engine reachable (HTTP $ENGINE_STATUS)"

# Build classpath
echo ""
echo "Building classpath..."

if command -v mvn &> /dev/null; then
    # Use Maven to build classpath
    CLASSPATH=$(mvn dependency:build-classpath -DincludeScope=runtime -q -Dmdep.outputFile=/dev/stdout 2>/dev/null || echo "")
    if [ -d "target/classes" ]; then
        CLASSPATH="target/classes:$CLASSPATH"
    fi
fi

if [ -z "$CLASSPATH" ]; then
    # Fallback to Ant-style classpath
    CLASSPATH="$PROJECT_DIR/classes"
    if [ -d "$PROJECT_DIR/lib" ]; then
        for jar in "$PROJECT_DIR/lib"/*.jar; do
            [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
        done
    fi
    if [ -d "$PROJECT_DIR/build/3rdParty/lib" ]; then
        for jar in "$PROJECT_DIR/build/3rdParty/lib"/*.jar; do
            [ -f "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
        done
    fi
fi

echo "Classpath ready"

# Start server
echo ""
echo "Starting MCP server..."
echo ""

# Run with assertions enabled to catch any contract violations
exec java \
    -ea \
    -cp "$CLASSPATH" \
    -DYAWL_ENGINE_URL="$YAWL_ENGINE_URL" \
    -DYAWL_USERNAME="$YAWL_USERNAME" \
    -DYAWL_PASSWORD="$YAWL_PASSWORD" \
    -DMCP_TRANSPORT="$MCP_TRANSPORT" \
    -DMCP_PORT="$MCP_PORT" \
    -DMCP_ENDPOINT="$MCP_ENDPOINT" \
    -DMCP_LOG_LEVEL="$MCP_LOG_LEVEL" \
    org.yawlfoundation.yawl.integration.mcp.YawlMcpServer "$MCP_PORT"
