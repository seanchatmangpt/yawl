#!/bin/bash
# run-a2a-server.sh - Launch YAWL A2A Server (HTTP REST transport)
#
# JVM tuned for throughput + low-latency:
#   ZGC (Generational): sub-millisecond GC pauses (target < 10ms p99)
#   CompactObjectHeaders: -4-8 bytes per object, 5-10% throughput gain
#   AOT cache: reduces JVM warm-up latency from ~30s to <5s
#   Virtual thread parallelism = 16: handles burst of concurrent agent calls
#
# Environment variables:
#   YAWL_ENGINE_URL  - YAWL engine base URL (default: http://localhost:8080/yawl)
#   YAWL_USERNAME    - YAWL admin username (default: admin)
#   YAWL_PASSWORD    - YAWL admin password (default: YAWL)
#   A2A_PORT         - Port for REST server (default: 8081)
#   A2A_HEAP_MAX     - JVM max heap (default: 1g; increase for high concurrency)
#   A2A_LOG_LEVEL    - Log level: ERROR|WARN|INFO|DEBUG (default: INFO)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/build/3rdParty/lib"

CLASSPATH="${SCRIPT_DIR}/classes"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-spec-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-common-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-server-common-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-jsonrpc-common-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-transport-rest-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-transport-jsonrpc-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-http-client-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-client-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-client-transport-spi-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-client-transport-rest-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/a2a-java-sdk-client-transport-jsonrpc-1.0.0.Alpha2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-core-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-databind-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jackson-annotations-2.18.2.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-1.2-api-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-api-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/log4j-core-2.23.1.jar"
CLASSPATH="${CLASSPATH}:${LIB_DIR}/jdom-2.0.6.1.jar"

A2A_HEAP_MAX="${A2A_HEAP_MAX:-1g}"
A2A_LOG_LEVEL="${A2A_LOG_LEVEL:-INFO}"
LAUNCH_TS=$(date +%s%N)

echo "Starting YAWL A2A Server (HTTP REST transport)..."
echo "Engine URL: ${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
echo "A2A Port: ${A2A_PORT:-8081}"
echo "Heap max: ${A2A_HEAP_MAX}"
echo "Press Ctrl+C to stop"
echo ""

# Java 25 flags (ZGenerational, CompactObjectHeaders, AOTCache) require JDK 25+
JAVA_MAJOR=$(java -version 2>&1 | grep -oP '(?<=version ")\d+' | head -1)
JAVA25_FLAGS=""
if [[ "${JAVA_MAJOR}" -ge 25 ]]; then
  JAVA25_FLAGS="-XX:+ZGenerational -XX:+UseCompactObjectHeaders -XX:+UseAOTCache"
fi

exec java \
  -Xms256m \
  -Xmx"${A2A_HEAP_MAX}" \
  -XX:+UseZGC \
  ${JAVA25_FLAGS} \
  -Djava.util.concurrent.ForkJoinPool.common.parallelism=16 \
  --enable-preview \
  -Dlog4j2.level="${A2A_LOG_LEVEL}" \
  -DYAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}" \
  -DYAWL_USERNAME="${YAWL_USERNAME:-admin}" \
  -DYAWL_PASSWORD="${YAWL_PASSWORD:-YAWL}" \
  -DA2A_PORT="${A2A_PORT:-8081}" \
  -DA2A_LAUNCH_TIMESTAMP="${LAUNCH_TS}" \
  -cp "${CLASSPATH}" \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
