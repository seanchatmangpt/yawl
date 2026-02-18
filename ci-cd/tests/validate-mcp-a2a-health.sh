#!/usr/bin/env bash
# =============================================================================
# YAWL MCP and A2A Server Health Validation Script
#
# Validates that MCP and A2A servers are reachable and responding correctly.
# Used in deployment validation and smoke testing.
#
# Usage:
#   ./ci-cd/tests/validate-mcp-a2a-health.sh [--a2a-port PORT] [--engine-url URL]
#
# Environment variables:
#   A2A_SERVER_HOST  - A2A server hostname (default: localhost)
#   A2A_SERVER_PORT  - A2A server port (default: 8081)
#   YAWL_ENGINE_URL  - YAWL engine base URL (default: http://localhost:8080/yawl)
#   REQUEST_TIMEOUT  - HTTP request timeout in seconds (default: 5)
#
# Exit codes:
#   0 - All health checks passed
#   1 - One or more health checks failed
# =============================================================================

set -euo pipefail

A2A_HOST="${A2A_SERVER_HOST:-localhost}"
A2A_PORT="${A2A_SERVER_PORT:-8081}"
ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
TIMEOUT="${REQUEST_TIMEOUT:-5}"

FAILURES=0

pass() { echo "[PASS] $*"; }
fail() { echo "[FAIL] $*"; FAILURES=$((FAILURES + 1)); }
info() { echo "[INFO] $*"; }

echo "================================================================"
echo " YAWL MCP and A2A Health Validation"
echo "================================================================"
echo " A2A server   : http://${A2A_HOST}:${A2A_PORT}"
echo " Engine URL   : ${ENGINE_URL}"
echo " Timeout      : ${TIMEOUT}s"
echo "================================================================"

# --------------------------------------------------------------------------
# A2A Server: agent card discovery endpoint
# --------------------------------------------------------------------------

info "Checking A2A agent card endpoint..."
AGENT_CARD_URL="http://${A2A_HOST}:${A2A_PORT}/.well-known/agent.json"

HTTP_CODE=$(curl --silent --output /dev/null \
    --write-out "%{http_code}" \
    --connect-timeout "${TIMEOUT}" \
    --max-time "${TIMEOUT}" \
    "${AGENT_CARD_URL}" 2>/dev/null || echo "000")

if [ "${HTTP_CODE}" = "200" ]; then
    pass "A2A agent card endpoint responds with 200"
else
    fail "A2A agent card endpoint returned HTTP ${HTTP_CODE} (expected 200)"
fi

# --------------------------------------------------------------------------
# A2A Server: agent card contains required fields
# --------------------------------------------------------------------------

info "Validating A2A agent card content..."
AGENT_CARD=$(curl --silent \
    --connect-timeout "${TIMEOUT}" \
    --max-time "${TIMEOUT}" \
    "${AGENT_CARD_URL}" 2>/dev/null || echo "{}")

if echo "${AGENT_CARD}" | grep -q "YAWL"; then
    pass "Agent card contains YAWL identifier"
else
    fail "Agent card does not contain 'YAWL'"
fi

if echo "${AGENT_CARD}" | grep -q "launch_workflow"; then
    pass "Agent card declares launch_workflow skill"
else
    fail "Agent card does not declare launch_workflow skill"
fi

if echo "${AGENT_CARD}" | grep -q "query_workflows"; then
    pass "Agent card declares query_workflows skill"
else
    fail "Agent card does not declare query_workflows skill"
fi

if echo "${AGENT_CARD}" | grep -q "manage_workitems"; then
    pass "Agent card declares manage_workitems skill"
else
    fail "Agent card does not declare manage_workitems skill"
fi

if echo "${AGENT_CARD}" | grep -q "cancel_workflow"; then
    pass "Agent card declares cancel_workflow skill"
else
    fail "Agent card does not declare cancel_workflow skill"
fi

# --------------------------------------------------------------------------
# A2A Server: unauthenticated requests return 401
# --------------------------------------------------------------------------

info "Checking A2A authentication enforcement..."
AUTH_CODE=$(curl --silent --output /dev/null \
    --write-out "%{http_code}" \
    --connect-timeout "${TIMEOUT}" \
    --max-time "${TIMEOUT}" \
    --request POST \
    --header "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0","method":"tasks/send","id":1}' \
    "http://${A2A_HOST}:${A2A_PORT}/" 2>/dev/null || echo "000")

if [ "${AUTH_CODE}" = "401" ]; then
    pass "A2A server rejects unauthenticated POST with 401"
else
    fail "A2A server returned HTTP ${AUTH_CODE} for unauthenticated POST (expected 401)"
fi

# --------------------------------------------------------------------------
# A2A Server: 401 response includes WWW-Authenticate header
# --------------------------------------------------------------------------

info "Checking WWW-Authenticate header in 401 response..."
WWW_AUTH=$(curl --silent --head \
    --connect-timeout "${TIMEOUT}" \
    --max-time "${TIMEOUT}" \
    --request POST \
    --header "Content-Type: application/json" \
    --data '{"jsonrpc":"2.0"}' \
    "http://${A2A_HOST}:${A2A_PORT}/" 2>/dev/null | grep -i "WWW-Authenticate" || echo "")

if [ -n "${WWW_AUTH}" ]; then
    pass "401 response includes WWW-Authenticate header"
else
    fail "401 response missing WWW-Authenticate header"
fi

# --------------------------------------------------------------------------
# YAWL Engine: connectivity check (if engine URL is reachable)
# --------------------------------------------------------------------------

info "Checking YAWL engine reachability..."
ENGINE_CODE=$(curl --silent --output /dev/null \
    --write-out "%{http_code}" \
    --connect-timeout "${TIMEOUT}" \
    --max-time "${TIMEOUT}" \
    "${ENGINE_URL}" 2>/dev/null || echo "000")

if [ "${ENGINE_CODE}" != "000" ]; then
    pass "YAWL engine is reachable at ${ENGINE_URL} (HTTP ${ENGINE_CODE})"
else
    info "YAWL engine not reachable at ${ENGINE_URL} (offline test mode)"
fi

# --------------------------------------------------------------------------
# Summary
# --------------------------------------------------------------------------

echo ""
echo "================================================================"
if [ "${FAILURES}" -eq 0 ]; then
    echo " RESULT: ALL HEALTH CHECKS PASSED"
    exit 0
else
    echo " RESULT: ${FAILURES} HEALTH CHECK(S) FAILED"
    exit 1
fi
echo "================================================================"
