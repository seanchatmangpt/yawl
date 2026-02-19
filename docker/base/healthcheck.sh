#!/bin/sh
# =============================================================================
# healthcheck.sh — YAWL Base JRE Container Health Check
# =============================================================================
# Lightweight health check for base runtime images.
# Verifies JVM is responsive and basic container health.
#
# Exit codes:
#   0 - Healthy
#   1 - Unhealthy
# =============================================================================

set -e

# Tier 1: Check if JVM can start (java -version succeeds)
if java -version >/dev/null 2>&1; then
    exit 0
fi

# Tier 2: Check if a Java process is running
if pgrep -f "java" >/dev/null 2>&1; then
    exit 0
fi

# Health check failed
exit 1
