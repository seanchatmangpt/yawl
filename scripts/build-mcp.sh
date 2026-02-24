#!/usr/bin/env bash
#
# Build YAWL MCP Server
#
# This script compiles the YAWL codebase including MCP integration.
# It will FAIL if compilation errors occur.
#
# Usage: ./build-mcp.sh
#
# Exit codes:
#   0 - Build successful
#   1 - Build failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "==========================================="
echo "YAWL MCP Build"
echo "==========================================="
echo "Project: $PROJECT_DIR"
echo "==========================================="

cd "$PROJECT_DIR"

# Check for MCP SDK dependencies
echo ""
echo "Checking Maven dependencies..."

if [ ! -f "pom.xml" ]; then
    echo "ERROR: pom.xml not found"
    exit 1
fi

# Verify MCP SDK is declared
if ! grep -q "io.modelcontextprotocol" pom.xml; then
    echo "ERROR: MCP SDK not found in pom.xml"
    echo "Add the following dependencies:"
    echo "  <dependency>"
    echo "    <groupId>io.modelcontextprotocol</groupId>"
    echo "    <artifactId>mcp</artifactId>"
    echo "    <version>0.5.0</version>"
    echo "  </dependency>"
    exit 1
fi

echo "MCP SDK dependencies found"

# Compile with Maven
echo ""
echo "Compiling with Maven..."
echo ""

if mvn clean compile; then
    echo ""
    echo "==========================================="
    echo "BUILD SUCCESSFUL"
    echo "==========================================="
    exit 0
else
    echo ""
    echo "==========================================="
    echo "BUILD FAILED"
    echo "==========================================="
    exit 1
fi
