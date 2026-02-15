#!/bin/bash

# Run YAWL MCP Server using the official MCP Java SDK
# Uses STDIO transport (communicates via stdin/stdout)
#
# Environment variables:
#   YAWL_ENGINE_URL - YAWL engine base URL (default: http://localhost:8080/yawl)
#   YAWL_USERNAME   - YAWL admin username (default: admin)
#   YAWL_PASSWORD   - YAWL admin password (default: YAWL)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$SCRIPT_DIR/build/3rdParty/lib"

CLASSPATH="$SCRIPT_DIR/classes"
CLASSPATH="$CLASSPATH:$LIB_DIR/mcp-0.17.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/mcp-core-0.17.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/mcp-json-0.17.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/mcp-json-jackson2-0.17.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/reactor-core-3.7.0.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/reactive-streams-1.0.4.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/json-schema-validator-2.0.0.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-core-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-databind-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-annotations-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-1.2-api-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-api-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-core-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jdom-2.0.6.1.jar"

echo "Starting YAWL MCP Server (STDIO transport)..." >&2
echo "Engine URL: ${YAWL_ENGINE_URL:-http://localhost:8080/yawl}" >&2
echo "Press Ctrl+C to stop" >&2
echo "" >&2

java -cp "$CLASSPATH" org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
