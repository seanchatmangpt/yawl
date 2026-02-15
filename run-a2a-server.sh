#!/bin/bash

# Run YAWL A2A Server using the official A2A Java SDK
# Exposes YAWL engine as an A2A agent over HTTP REST
#
# Environment variables:
#   YAWL_ENGINE_URL - YAWL engine base URL (default: http://localhost:8080/yawl)
#   YAWL_USERNAME   - YAWL admin username (default: admin)
#   YAWL_PASSWORD   - YAWL admin password (default: YAWL)
#   A2A_PORT        - Port to run on (default: 8081)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$SCRIPT_DIR/build/3rdParty/lib"

CLASSPATH="$SCRIPT_DIR/classes"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-spec-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-common-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-server-common-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-jsonrpc-common-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-transport-rest-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-transport-jsonrpc-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-http-client-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-client-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-client-transport-spi-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-client-transport-rest-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/a2a-java-sdk-client-transport-jsonrpc-1.0.0.Alpha2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-core-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-databind-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jackson-annotations-2.18.2.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-1.2-api-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-api-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/log4j-core-2.23.1.jar"
CLASSPATH="$CLASSPATH:$LIB_DIR/jdom-2.0.6.1.jar"

echo "Starting YAWL A2A Server..."
echo "Engine URL: ${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
echo "A2A Port: ${A2A_PORT:-8081}"
echo "Press Ctrl+C to stop"
echo ""

java -cp "$CLASSPATH" org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
