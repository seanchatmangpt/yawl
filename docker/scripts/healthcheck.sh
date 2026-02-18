#!/bin/sh
# =============================================================================
# healthcheck.sh — YAWL Container Health Check
# =============================================================================
# Checks if the YAWL engine is responding to HTTP requests.
# Used by Docker HEALTHCHECK directive.
#
# Exit codes:
#   0 - Healthy
#   1 - Unhealthy
# =============================================================================

# Default health check URL
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health/liveness}"

# Fallback to simple port check if actuator not available
PORT="${PORT:-8080}"

# Try actuator health endpoint first
if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
    exit 0
fi

# Fallback: check if port is listening
if nc -z localhost "$PORT" 2>/dev/null; then
    exit 0
fi

# Health check failed
echo "Health check failed: $HEALTH_URL and port $PORT not responding"
exit 1
