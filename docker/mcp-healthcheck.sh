#!/bin/sh
# MCP Server Health Check
#
# MCP servers use STDIO transport (not HTTP). This script verifies the
# Java process is alive and the JVM is not in an error state.
#
# Exit codes:
#   0 - healthy (process running, no OOM indicators)
#   1 - unhealthy (process dead or OOM detected)

set -eu

# Check if the yawl-mcp-server.jar process is running under the mcp user
if ! pgrep -f "yawl-mcp-server.jar" > /dev/null 2>&1; then
    echo "UNHEALTHY: MCP server process not found"
    exit 1
fi

# Check for heap dump (OOM indicator) - created only on out-of-memory errors
if [ -f "/app/logs/heap-dump.hprof" ]; then
    echo "UNHEALTHY: Heap dump detected (OOM occurred)"
    exit 1
fi

echo "HEALTHY: MCP server process running"
exit 0
