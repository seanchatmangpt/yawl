#!/usr/bin/env bash
# Run PM4Py MCP server (STDIO transport).
# MCP clients spawn this and communicate via stdin/stdout.
set -euo pipefail
cd "$(dirname "$0")"
exec uv run mcp_server.py
