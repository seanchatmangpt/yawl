#!/bin/bash
#
# Run YAWL MCP Server
#
# Usage: ./run-mcp-server.sh [port]
#
# Environment Variables:
#   YAWL_ENGINE_URL  - YAWL engine URL (default: http://localhost:8080/yawl/ib)
#   YAWL_USERNAME    - YAWL username (default: admin)
#   YAWL_PASSWORD    - YAWL password (default: YAWL)
#   MCP_TRANSPORT    - Transport type: SSE, STDIO (default: SSE)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/scripts/start-mcp-server.sh" "$@"
