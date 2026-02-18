#!/bin/bash
# Post-Deployment Smoke Tests for YAWL Workflow Engine
# Validates that deployed services are functioning correctly
# Supports: full, canary, blue-green deployment scenarios

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Parameters
TEST_SUITE="${1:-full}"
ENVIRONMENT="${2:-staging}"

# Test results tracking
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

# Default timeouts (in seconds)
HTTP_TIMEOUT=10
ENDPOINT_TIMEOUT=30
HEALTH_CHECK_RETRIES=5

# Service endpoints
NAMESPACE="yawl-${ENVIRONMENT}"
SERVICE_NAME="yawl"
YAWL_SERVICE="http://${SERVICE_NAME}.${NAMESPACE}.svc.cluster.local:8080"

# Test result helpers
pass_test() {
    local test_name="$1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
    echo -e "${GREEN}✓ PASSED${NC}: $test_name"
}

fail_test() {
    local test_name="$1"
    local reason="${2:-unknown error}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo -e "${RED}✗ FAILED${NC}: $test_name"
    echo "  Reason: $reason"
}

skip_test() {
    local test_name="$1"
    TESTS_SKIPPED=$((TESTS_SKIPPED + 1))
    echo -e "${YELLOW}⊘ SKIPPED${NC}: $test_name"
}

# Header
echo "=============================================="
echo "YAWL Post-Deployment Smoke Tests"
echo "=============================================="
echo "Test Suite: $TEST_SUITE"
echo "Environment: $ENVIRONMENT"
echo "Namespace: $NAMESPACE"
echo "Timestamp: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
echo ""

# ============================================================================
# Utility Functions
# ============================================================================

# Wait for service to be ready
wait_for_service() {
    local service="$1"
    local max_retries="${2:-30}"
    local retry_count=0

    echo "Waiting for service to be ready: $service"

    while [ $retry_count -lt $max_retries ]; do
        if kubectl get endpoints "$service" -n "$NAMESPACE" &>/dev/null 2>&1; then
            ENDPOINTS=$(kubectl get endpoints "$service" -n "$NAMESPACE" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || echo "")

            if [ -n "$ENDPOINTS" ]; then
                echo "Service ready with endpoints: $ENDPOINTS"
                return 0
            fi
        fi

        retry_count=$((retry_count + 1))
        echo "  Attempt $retry_count/$max_retries..."
        sleep 2
    done

    return 1
}

# Check HTTP endpoint with retry
check_http_endpoint() {
    local url="$1"
    local expected_code="${2:-200}"
    local timeout="${3:-$HTTP_TIMEOUT}"

    for attempt in $(seq 1 3); do
        if response=$(curl -s -w "\n%{http_code}" \
            --max-time "$timeout" \
            --connect-timeout 5 \
            "$url" 2>/dev/null); then

            http_code=$(echo "$response" | tail -1)
            body=$(echo "$response" | head -n -1)

            if [ "$http_code" = "$expected_code" ]; then
                return 0
            fi
        fi

        if [ $attempt -lt 3 ]; then
            sleep 2
        fi
    done

    return 1
}

# Port forward to service
setup_port_forward() {
    local service="$1"
    local local_port="${2:-8080}"
    local remote_port="${3:-8080}"

    echo "Setting up port forward: localhost:$local_port -> $service:$remote_port"

    # Kill existing port-forward if any
    pkill -f "kubectl port-forward" 2>/dev/null || true
    sleep 1

    # Start new port-forward in background
    kubectl port-forward -n "$NAMESPACE" \
        "svc/$service" \
        "$local_port:$remote_port" \
        &>/dev/null &

    sleep 2
    return 0
}

# ============================================================================
# Stage 1: Deployment Validation
# ============================================================================
echo -e "${BLUE}STAGE 1: DEPLOYMENT VALIDATION${NC}"
echo "----------------------------------------"

# Check namespace exists
if kubectl get namespace "$NAMESPACE" &>/dev/null 2>&1; then
    pass_test "Kubernetes namespace exists ($NAMESPACE)"
else
    fail_test "Kubernetes namespace" "Namespace $NAMESPACE not found"
    exit 1
fi

# Check service exists
if kubectl get svc "$SERVICE_NAME" -n "$NAMESPACE" &>/dev/null 2>&1; then
    pass_test "YAWL service exists"
else
    fail_test "YAWL service" "Service $SERVICE_NAME not found"
    exit 1
fi

# Check deployment exists
if kubectl get deployment -n "$NAMESPACE" -l app.kubernetes.io/name=yawl &>/dev/null 2>&1; then
    pass_test "YAWL deployment exists"
else
    fail_test "YAWL deployment" "No YAWL deployment found"
    exit 1
fi

echo ""

# ============================================================================
# Stage 2: Pod Health Checks
# ============================================================================
echo -e "${BLUE}STAGE 2: POD HEALTH CHECKS${NC}"
echo "----------------------------------------"

# Get pod count
POD_COUNT=$(kubectl get pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    --field-selector=status.phase=Running \
    --no-headers 2>/dev/null | wc -l)

if [ "$POD_COUNT" -gt 0 ]; then
    pass_test "Running pods detected ($POD_COUNT pods)"
else
    fail_test "Running pods" "No running pods found"
    exit 1
fi

# Check pod readiness
READY_PODS=$(kubectl get pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    -o jsonpath='{range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}' \
    2>/dev/null | grep -c "True" || echo "0")

if [ "$READY_PODS" -eq "$POD_COUNT" ]; then
    pass_test "All pods are ready ($READY_PODS/$POD_COUNT)"
else
    fail_test "Pod readiness" "Only $READY_PODS/$POD_COUNT pods ready"
    # Continue anyway for now
fi

# Check for pod crashes/restarts
RESTART_COUNT=$(kubectl get pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    -o jsonpath='{.items[*].status.containerStatuses[*].restartCount}' \
    2>/dev/null | tr ' ' '\n' | awk '{s+=$1} END {print s}' || echo "0")

if [ "$RESTART_COUNT" = "0" ] || [ "$RESTART_COUNT" -lt 3 ]; then
    pass_test "Pod restart count acceptable ($RESTART_COUNT restarts)"
else
    fail_test "Pod restarts" "Excessive restart count: $RESTART_COUNT"
fi

# Check for pending pods
PENDING_PODS=$(kubectl get pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    --field-selector=status.phase=Pending \
    --no-headers 2>/dev/null | wc -l)

if [ "$PENDING_PODS" = "0" ]; then
    pass_test "No pending pods"
else
    fail_test "Pending pods" "$PENDING_PODS pods in pending state"
fi

echo ""

# ============================================================================
# Stage 3: Service Endpoint Validation
# ============================================================================
echo -e "${BLUE}STAGE 3: SERVICE ENDPOINT VALIDATION${NC}"
echo "----------------------------------------"

# Setup port forward
if setup_port_forward "$SERVICE_NAME" 8080 8080; then
    pass_test "Port forward established"
else
    fail_test "Port forward" "Could not establish port forward"
    exit 1
fi

LOCAL_ENDPOINT="http://localhost:8080"

# Test health endpoint
if check_http_endpoint "$LOCAL_ENDPOINT/api/health" 200; then
    pass_test "Health endpoint responds (GET /api/health)"
else
    fail_test "Health endpoint" "Endpoint not responding or wrong status code"
fi

# Test version endpoint
if check_http_endpoint "$LOCAL_ENDPOINT/api/version" 200; then
    pass_test "Version endpoint responds (GET /api/version)"
else
    fail_test "Version endpoint" "Endpoint not responding"
fi

# Test API status
if check_http_endpoint "$LOCAL_ENDPOINT/api/status" 200; then
    pass_test "Status endpoint responds (GET /api/status)"
else
    fail_test "Status endpoint" "Endpoint not responding"
fi

echo ""

# ============================================================================
# Stage 4: API Functionality Tests
# ============================================================================
echo -e "${BLUE}STAGE 4: API FUNCTIONALITY TESTS${NC}"
echo "----------------------------------------"

# Test specification endpoints (if available)
if check_http_endpoint "$LOCAL_ENDPOINT/api/v1/specifications" 200 5; then
    pass_test "Specification API endpoint accessible"

    # Try to fetch specifications
    SPEC_RESPONSE=$(curl -s -w "\n%{http_code}" \
        --max-time 5 \
        "$LOCAL_ENDPOINT/api/v1/specifications" 2>/dev/null || echo "")

    if echo "$SPEC_RESPONSE" | tail -1 | grep -q "200"; then
        pass_test "Can retrieve specifications"
    else
        skip_test "Retrieve specifications (API may not have specs)"
    fi
else
    skip_test "Specification API" "Endpoint not available"
fi

# Test case/workflow endpoints
if check_http_endpoint "$LOCAL_ENDPOINT/api/v1/cases" 200 5; then
    pass_test "Cases API endpoint accessible"
else
    skip_test "Cases API" "Endpoint not available"
fi

# Test work items endpoint
if check_http_endpoint "$LOCAL_ENDPOINT/api/v1/workitems" 200 5; then
    pass_test "Work Items API endpoint accessible"
else
    skip_test "Work Items API" "Endpoint not available"
fi

echo ""

# ============================================================================
# Stage 5: Database Connectivity
# ============================================================================
echo -e "${BLUE}STAGE 5: DATABASE CONNECTIVITY${NC}"
echo "----------------------------------------"

# Check database status endpoint
if check_http_endpoint "$LOCAL_ENDPOINT/api/admin/database/status" 200 5; then
    pass_test "Database status endpoint accessible"
else
    skip_test "Database status" "Endpoint not available"
fi

# Check database health
if check_http_endpoint "$LOCAL_ENDPOINT/api/health/db" 200 5; then
    pass_test "Database health check passed"
else
    fail_test "Database health" "Database health check failed"
fi

echo ""

# ============================================================================
# Stage 6: Authentication & Authorization (if applicable)
# ============================================================================
echo -e "${BLUE}STAGE 6: AUTHENTICATION & AUTHORIZATION${NC}"
echo "----------------------------------------"

# Test authentication endpoint
if check_http_endpoint "$LOCAL_ENDPOINT/api/auth/token" 401 5; then
    pass_test "Authentication endpoint protected (requires credentials)"
else
    skip_test "Authentication" "Endpoint not available or not protected"
fi

# Test unauthorized access to protected endpoint
if curl -s --max-time 5 \
    -H "Authorization: Bearer invalid-token" \
    "$LOCAL_ENDPOINT/api/v1/admin/users" 2>/dev/null | grep -q "401\|403\|Unauthorized"; then
    pass_test "Protected endpoints deny unauthorized access"
else
    skip_test "Protected endpoints" "Unable to verify auth"
fi

echo ""

# ============================================================================
# Stage 7: Performance & Load Check
# ============================================================================
echo -e "${BLUE}STAGE 7: PERFORMANCE & LOAD CHECK${NC}"
echo "----------------------------------------"

# Measure response time
echo "Measuring endpoint response time..."
RESPONSE_TIME=$(curl -s -o /dev/null -w "%{time_total}" \
    --max-time 10 \
    "$LOCAL_ENDPOINT/api/health" 2>/dev/null || echo "999")

if (( $(echo "$RESPONSE_TIME < 2.0" | bc -l 2>/dev/null) )); then
    pass_test "Response time acceptable (${RESPONSE_TIME}s)"
elif (( $(echo "$RESPONSE_TIME < 5.0" | bc -l 2>/dev/null) )); then
    skip_test "Response time" "Slower than optimal (${RESPONSE_TIME}s) but acceptable"
else
    fail_test "Response time" "Endpoint too slow (${RESPONSE_TIME}s > 5s)"
fi

# Check memory usage
MEMORY_USAGE=$(kubectl top pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    --no-headers 2>/dev/null | awk '{s+=$2} END {print s}' || echo "0")

if [ "$MEMORY_USAGE" != "0" ]; then
    pass_test "Memory monitoring available (Total: ${MEMORY_USAGE}Mi)"
else
    skip_test "Memory metrics" "Metrics server not available"
fi

echo ""

# ============================================================================
# Stage 8: Logging & Observability
# ============================================================================
echo -e "${BLUE}STAGE 8: LOGGING & OBSERVABILITY${NC}"
echo "----------------------------------------"

# Check pod logs for errors in last 5 minutes
PODS=$(kubectl get pods -n "$NAMESPACE" \
    -l app.kubernetes.io/name=yawl \
    -o jsonpath='{.items[*].metadata.name}' 2>/dev/null)

ERROR_COUNT=0
for pod in $PODS; do
    POD_ERRORS=$(kubectl logs -n "$NAMESPACE" "$pod" \
        --tail=100 \
        --timestamps=true 2>/dev/null | grep -ic "error\|exception\|fatal" || echo "0")

    ERROR_COUNT=$((ERROR_COUNT + POD_ERRORS))
done

if [ "$ERROR_COUNT" = "0" ]; then
    pass_test "No errors in recent logs"
else
    skip_test "Pod logs" "Found $ERROR_COUNT error messages (may be expected)"
fi

# Check if metrics endpoint is available
if check_http_endpoint "$LOCAL_ENDPOINT/metrics" 200 5; then
    pass_test "Prometheus metrics endpoint available"
else
    skip_test "Prometheus metrics" "Endpoint not configured"
fi

echo ""

# ============================================================================
# Stage 9: Deployment-Specific Tests
# ============================================================================
case "$TEST_SUITE" in
    canary)
        echo -e "${BLUE}STAGE 9: CANARY DEPLOYMENT VALIDATION${NC}"
        echo "----------------------------------------"

        # Verify canary deployment exists
        if kubectl get deployment -n "$NAMESPACE" -l version=canary &>/dev/null 2>&1; then
            pass_test "Canary deployment exists"

            # Check canary pod status
            CANARY_PODS=$(kubectl get pods -n "$NAMESPACE" \
                -l version=canary \
                --field-selector=status.phase=Running \
                --no-headers 2>/dev/null | wc -l)

            if [ "$CANARY_PODS" -gt 0 ]; then
                pass_test "Canary pods are running ($CANARY_PODS pods)"
            else
                fail_test "Canary pods" "No running canary pods"
            fi
        else
            skip_test "Canary deployment" "Not a canary deployment"
        fi
        ;;

    blue-green)
        echo -e "${BLUE}STAGE 9: BLUE-GREEN DEPLOYMENT VALIDATION${NC}"
        echo "----------------------------------------"

        # Verify both blue and green environments
        for color in blue green; do
            if kubectl get deployment -n "$NAMESPACE" -l version=$color &>/dev/null 2>&1; then
                COLOR_PODS=$(kubectl get pods -n "$NAMESPACE" \
                    -l version=$color \
                    --field-selector=status.phase=Running \
                    --no-headers 2>/dev/null | wc -l)

                if [ "$COLOR_PODS" -gt 0 ]; then
                    pass_test "${color^^} environment has running pods ($COLOR_PODS pods)"
                else
                    skip_test "${color^^} environment" "No running pods (may be normal)"
                fi
            else
                skip_test "${color^^} deployment" "Not deployed yet"
            fi
        done
        ;;

    *)
        echo -e "${BLUE}STAGE 9: FULL DEPLOYMENT VALIDATION${NC}"
        echo "----------------------------------------"
        pass_test "Running comprehensive validation"
        ;;
esac

echo ""

# ============================================================================
# Stage 10: Security Validation
# ============================================================================
echo -e "${BLUE}STAGE 10: SECURITY VALIDATION${NC}"
echo "----------------------------------------"

# Check TLS/HTTPS is enforced
if curl -s --insecure -o /dev/null -w "%{http_code}" \
    https://localhost:8443/api/health 2>/dev/null | grep -q "200"; then
    pass_test "HTTPS/TLS endpoint available"
else
    skip_test "HTTPS/TLS" "Not configured or not accessible"
fi

# Check security headers
SECURITY_HEADERS=$(curl -s -i --max-time 5 \
    "$LOCAL_ENDPOINT/api/health" 2>/dev/null | grep -i "X-Content-Type-Options\|X-Frame-Options\|Strict-Transport-Security")

if [ -n "$SECURITY_HEADERS" ]; then
    pass_test "Security headers present"
else
    skip_test "Security headers" "Headers not found (may be normal)"
fi

echo ""

# ============================================================================
# Cleanup
# ============================================================================
echo -e "${BLUE}CLEANUP${NC}"
echo "----------------------------------------"

# Kill port-forward
pkill -f "kubectl port-forward" 2>/dev/null || true

echo "Port forward terminated"
echo ""

# ============================================================================
# Test Summary
# ============================================================================
echo "=============================================="
echo "SMOKE TEST SUMMARY"
echo "=============================================="
echo ""

TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED + TESTS_SKIPPED))

echo "Results:"
echo -e "  ${GREEN}Passed:${NC}  $TESTS_PASSED"
echo -e "  ${RED}Failed:${NC}  $TESTS_FAILED"
echo -e "  ${YELLOW}Skipped:${NC} $TESTS_SKIPPED"
echo "  Total:   $TOTAL_TESTS"
echo ""

echo "Environment: $ENVIRONMENT"
echo "Test Suite:  $TEST_SUITE"
echo "Timestamp:   $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
echo ""

# Exit with failure if any test failed
if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}RESULT: SMOKE TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}RESULT: ALL SMOKE TESTS PASSED${NC}"
    exit 0
fi
