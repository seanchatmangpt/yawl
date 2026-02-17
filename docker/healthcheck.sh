#!/bin/bash
# YAWL v6.0.0 container health check
# Three-tier strategy matches both Docker HEALTHCHECK and Kubernetes init-container probing

set -euo pipefail

ACTUATOR_URL="http://localhost:${MANAGEMENT_SERVER_PORT:-9090}/actuator/health/liveness"
ENGINE_PORT="${ENGINE_HTTP_PORT:-8080}"

# Tier 1: Spring Boot Actuator liveness (preferred - most accurate)
if curl -sf --max-time 5 "${ACTUATOR_URL}" > /dev/null 2>&1; then
    exit 0
fi

# Tier 2: TCP port open check (engine accepting connections)
if command -v nc > /dev/null 2>&1; then
    if nc -z -w 3 localhost "${ENGINE_PORT}" > /dev/null 2>&1; then
        exit 0
    fi
fi

# Tier 3: JVM process still alive (startup phase before port bind)
if pgrep -f "yawl\.jar" > /dev/null 2>&1; then
    # Process alive but not yet ready - return 0 during start-period
    exit 0
fi

# All checks failed - container is unhealthy
exit 1
