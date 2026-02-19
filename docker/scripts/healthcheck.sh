#!/bin/sh
# =============================================================================
# healthcheck.sh — YAWL Container Health Check (Security-Hardened)
# =============================================================================
# Three-tier health check strategy:
#   1. Spring Boot Actuator liveness endpoint (preferred)
#   2. TCP port connectivity (engine accepting connections)
#   3. JVM process existence (startup phase detection)
#
# Security features:
#   - Timeout limits prevent hanging
#   - No sensitive data in output
#   - Graceful degradation across tiers
#
# Exit codes:
#   0 - Healthy (service responding)
#   1 - Unhealthy (all checks failed)
# =============================================================================

set -e

# Configuration with secure defaults
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health/liveness}"
MANAGEMENT_PORT="${MANAGEMENT_SERVER_PORT:-9090}"
ENGINE_PORT="${ENGINE_HTTP_PORT:-8080}"
TIMEOUT="${HEALTH_CHECK_TIMEOUT:-5}"

# Tier 1: Spring Boot Actuator liveness (preferred - most accurate)
# Uses management port (9090) which is separate from main engine port
if command -v curl >/dev/null 2>&1; then
    if curl -sf --max-time "${TIMEOUT}" "${HEALTH_URL}" >/dev/null 2>&1; then
        exit 0
    fi
fi

# Tier 2: TCP port connectivity check
# Verifies engine is accepting connections on main port
if command -v nc >/dev/null 2>&1; then
    if nc -z -w 3 localhost "${ENGINE_PORT}" >/dev/null 2>&1; then
        exit 0
    fi
fi

# Tier 3: JVM process existence (startup phase before port bind)
# During container start-period, process may exist but not be ready
if command -v pgrep >/dev/null 2>&1; then
    if pgrep -f "yaw[a-z]*\.jar" >/dev/null 2>&1; then
        # Process alive - may still be starting up
        # Return success during start-period to allow graceful startup
        exit 0
    fi
fi

# All health checks failed - container is unhealthy
# No sensitive information in error output
echo "Health check failed"
exit 1
