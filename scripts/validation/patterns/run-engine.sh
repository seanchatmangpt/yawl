#!/bin/bash
#
# Run YAWL Engine directly with Java
# Uses pre-built JAR file for development/testing
#

set -euo pipefail

# Configuration
JAR_FILE="yawl-engine/target/yawl-engine-6.0.0-Alpha.jar"
JAVA_OPTS="-Xmx2g -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=development"
ENGINE_PORT=8080
MGMT_PORT=9090

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "${RED}ERROR:${NC} $1" >&2
    exit 1
}

success() {
    echo "${GREEN}SUCCESS:${NC} $1"
}

warn() {
    echo "${YELLOW}WARNING:${NC} $1"
}

# Check if JAR exists
if [[ ! -f "$JAR_FILE" ]]; then
    error "JAR file not found: $JAR_FILE"
    echo "Please build the engine first:"
    echo "  ./mvnw clean package -pl yawl-engine -DskipTests"
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    error "Java is not installed. Java 25 is required."
fi

# Check if ports are available
check_port() {
    local port="$1"
    if netstat -ltn 2>/dev/null | grep -q ":$port "; then
        warn "Port $port is already in use"
        return 1
    fi
    return 0
}

if ! check_port "$ENGINE_PORT"; then
    error "Port $ENGINE_PORT is already in use. Please stop the service using this port."
fi

if ! check_port "$MGMT_PORT"; then
    warn "Port $MGMT_PORT is already in use. Engine might still start but management endpoints might not be accessible."
fi

# Create necessary directories
mkdir -p /tmp/yawl/{data,logs,temp}

# Start the engine
log "Starting YAWL Engine..."
log "JAR File: $JAR_FILE"
log "Java Options: $JAVA_OPTS"
log "Engine Port: $ENGINE_PORT"
log "Management Port: $MGMT_PORT"
log ""

# Run the engine
nohup java $JAVA_OPTS -jar "$JAR_FILE" > /tmp/yawl/engine.log 2>&1 &
ENGINE_PID=$!

log "Engine started with PID: $ENGINE_PID"
log "Waiting for engine to be ready..."

# Wait for engine to start
for i in {1..60}; do
    if curl -s -f "http://localhost:$ENGINE_PORT/actuator/health/liveness" >/dev/null 2>&1; then
        success "Engine is ready!"
        log ""
        log "Engine Information:"
        log "  Main PID: $ENGINE_PID"
        log "  Engine API: http://localhost:$ENGINE_PORT"
        log "  Management API: http://localhost:$MGMT_PORT"
        log "  Actuator Health: http://localhost:$MGMT_PORT/actuator/health"
        log ""
        log "View logs:"
        log "  tail -f /tmp/yawl/engine.log"
        log ""
        log "Stop engine:"
        log "  kill $ENGINE_PID"
        log ""
        log "Pattern validation commands:"
        log "  ./scripts/validation/patterns/validate-basic.sh"
        log "  ./scripts/validation/patterns/validate-all-patterns.sh"
        break
    fi
    log "Waiting... ($i/60)"
    sleep 1
done

# Check if engine failed to start
if ! kill -0 $ENGINE_PID 2>/dev/null; then
    error "Engine failed to start. Check logs: /tmp/yawl/engine.log"
fi

# Save PID for later use
echo "$ENGINE_PID" > /tmp/yawl/engine.pid

# Wait for user input or background
if [[ "${1:-}" != "--daemon" ]]; then
    log "Press Ctrl+C to stop the engine..."
    trap "kill $ENGINE_PID; log 'Engine stopped'" INT
    wait
fi