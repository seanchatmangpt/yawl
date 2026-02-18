#!/bin/bash
# run-mcp-server.sh - Launch YAWL MCP Server (STDIO transport)
#
# JVM tuned for low-latency agent response:
#   ZGC (Generational) for sub-millisecond GC pauses
#   CompactObjectHeaders for 5-10% throughput gain
#   AOT cache warms JIT on startup to reduce cold-start latency
#   Virtual thread parallelism = 16 (matches CI runner core count)
#
# Environment variables:
#   YAWL_ENGINE_URL  - YAWL engine base URL (default: http://localhost:8080/yawl)
#   YAWL_USERNAME    - YAWL admin username (default: admin)
#   YAWL_PASSWORD    - YAWL admin password (default: YAWL)
#   MCP_HEAP_MAX     - JVM max heap (default: 512m; increase for large workflows)
#   MCP_LOG_LEVEL    - Log level: ERROR|WARN|INFO|DEBUG (default: INFO)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/build/3rdParty/lib"

CLASSPATH="${SCRIPT_DIR}/classes"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/mcp-0.17.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/mcp-core-0.17.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/mcp-json-0.17.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/mcp-json-jackson2-0.17.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/reactor-core-3.7.0.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/reactive-streams-1.0.4.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/json-schema-validator-2.0.0.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-core-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-databind-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-annotations-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-1.2-api-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-api-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-core-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jdom-2.0.6.1.jar"

MCP_HEAP_MAX="${MCP_HEAP_MAX:-512m}"
MCP_LOG_LEVEL="${MCP_LOG_LEVEL:-INFO}"
LAUNCH_TS=$(date +%s%N)

echo "Starting YAWL MCP Server (STDIO transport)..." >&2
echo "Engine URL: ${YAWL_ENGINE_URL:-http://localhost:8080/yawl}" >&2
echo "Heap max: ${MCP_HEAP_MAX}" >&2
echo "Press Ctrl+C to stop" >&2
echo "" >&2

# Java 25 flags (ZGenerational, CompactObjectHeaders, AOTCache) require JDK 25+
# On Java 21 only base ZGC flags are set; on Java 25 the full flag set is used.
JAVA_MAJOR=$(java -version 2>&1 | grep -oP '(?<=version ")\d+' | head -1)
JAVA25_FLAGS=""
if [[ "${JAVA_MAJOR}" -ge 25 ]]; then
  JAVA25_FLAGS="-XX:+ZGenerational -XX:+UseCompactObjectHeaders -XX:+UseAOTCache"
fi

exec java \
  -Xms128m \
  -Xmx"${MCP_HEAP_MAX}" \
  -XX:+UseZGC \
  ${JAVA25_FLAGS} \
  -Djava.util.concurrent.ForkJoinPool.common.parallelism=16 \
  --enable-preview \
  -Dlog4j2.level="${MCP_LOG_LEVEL}" \
  -DYAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}" \
  -DYAWL_USERNAME="${YAWL_USERNAME:-admin}" \
  -DYAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}" \
  -DMCP_LAUNCH_TIMESTAMP="${LAUNCH_TS}" \
  -cp "${CLASSPATH}" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
