#!/bin/bash
#
# Run YAWL MCP Integration Tests
#
# Usage: ./test-mcp.sh
#
# This script runs all MCP tests including HTTP endpoint tests
# and client integration tests.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$SCRIPT_DIR/scripts/run-integration-tests.sh" "$@"
