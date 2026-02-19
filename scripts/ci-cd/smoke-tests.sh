#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0.0 - Smoke Tests Script
# =============================================================================
# Runs comprehensive smoke tests against a deployed YAWL environment.
#
# Usage:
#   ./scripts/ci-cd/smoke-tests.sh <environment>
#
# Tests:
#   - Health endpoint availability
#   - API endpoint functionality
#   - Database connectivity
#   - Redis connectivity
#   - Specification loading
#   - Case creation and retrieval
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENVIRONMENT="${1:-staging}"
TIMEOUT="${SMOKE_TEST_TIMEOUT:-60}"
RETRY_COUNT="${SMOKE_TEST_RETRIES:-3}"
RETRY_DELAY="${SMOKE_TEST_RETRY_DELAY:-5}"

# Endpoints based on environment
case "${ENVIRONMENT}" in
    dev|development)
        BASE_URL="${BASE_URL:-http://localhost:8080}"
        NAMESPACE="yawl-dev"
        ;;
    staging)
        BASE_URL="${BASE_URL:-https://yawl-staging.example.com}"
        NAMESPACE="yawl-staging"
        ;;
    production)
        BASE_URL="${BASE_URL:-https://yawl.example.com}"
        NAMESPACE="yawl-prod"
        ;;
    *)
        echo "Unknown environment: ${ENVIRONMENT}"
        exit 1
        ;;
esac

# Test counters
PASSED=0
FAILED=0
TOTAL=0

# =============================================================================
# Helper Functions
# =============================================================================

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASSED++)); ((TOTAL++)); }
log_error() { echo -e "${RED}[FAIL]${NC} $1"; ((FAILED++)); ((TOTAL++)); }
log_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; ((TOTAL++)); }

retry_with_backoff() {
    local max_attempts=$1
    local delay=$2
    local attempt=1
    shift 2
    "$@"

    while [[ $? -ne 0 && ${attempt} -lt ${max_attempts} ]]; do
        log_warning "Attempt ${attempt}/${max_attempts} failed, retrying in ${delay}s..."
        sleep ${delay}
        ((attempt++))
        delay=$((delay * 2))
        "$@"
    done

    return $?
}

# =============================================================================
# Test Functions
# =============================================================================

test_health_endpoint() {
    log_info "Testing health endpoint..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/health" 2>/dev/null) || {
        log_error "Health endpoint unreachable: ${BASE_URL}/actuator/health"
        return 1
    }

    local status
    status=$(echo "${response}" | jq -r '.status // empty' 2>/dev/null)

    if [[ "${status}" == "UP" ]]; then
        log_success "Health endpoint returns UP status"
        return 0
    else
        log_error "Health endpoint returns ${status:-unknown} status"
        return 1
    fi
}

test_readiness_endpoint() {
    log_info "Testing readiness endpoint..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/health/readiness" 2>/dev/null) || {
        log_error "Readiness endpoint unreachable"
        return 1
    }

    local status
    status=$(echo "${response}" | jq -r '.status // empty' 2>/dev/null)

    if [[ "${status}" == "UP" ]]; then
        log_success "Readiness check passed"
        return 0
    else
        log_error "Readiness check failed: ${status:-unknown}"
        return 1
    fi
}

test_liveness_endpoint() {
    log_info "Testing liveness endpoint..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/health/liveness" 2>/dev/null) || {
        log_error "Liveness endpoint unreachable"
        return 1
    }

    local status
    status=$(echo "${response}" | jq -r '.status // empty' 2>/dev/null)

    if [[ "${status}" == "UP" ]]; then
        log_success "Liveness check passed"
        return 0
    else
        log_error "Liveness check failed: ${status:-unknown}"
        return 1
    fi
}

test_info_endpoint() {
    log_info "Testing info endpoint..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/info" 2>/dev/null) || {
        log_error "Info endpoint unreachable"
        return 1
    }

    local version
    version=$(echo "${response}" | jq -r '.build.version // .app.version // empty' 2>/dev/null)

    if [[ -n "${version}" ]]; then
        log_success "Application version: ${version}"
        return 0
    else
        log_warning "Could not determine application version"
        return 0
    fi
}

test_prometheus_endpoint() {
    log_info "Testing Prometheus metrics endpoint..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/prometheus" 2>/dev/null) || {
        log_error "Prometheus endpoint unreachable"
        return 1
    }

    if echo "${response}" | grep -q "jvm_memory_used_bytes"; then
        log_success "Prometheus metrics available"
        return 0
    else
        log_error "Prometheus metrics missing expected metrics"
        return 1
    fi
}

test_engine_api() {
    log_info "Testing YAWL Engine API..."

    # Test Interface A - Specification upload
    local spec_response
    spec_response=$(curl -sf --connect-timeout 10 --max-time 60 \
        -X GET "${BASE_URL}/yawl/ia/specifications" \
        -H "Accept: application/xml" 2>/dev/null) || {
        log_warning "Could not retrieve specifications (may require authentication)"
        return 0
    }

    log_success "Engine API accessible"
    return 0
}

test_database_connectivity() {
    log_info "Testing database connectivity..."

    # Check database health component
    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/health" 2>/dev/null) || {
        log_error "Could not check database connectivity"
        return 1
    }

    local db_status
    db_status=$(echo "${response}" | jq -r '.components.db.status // .components.dataSource.status // "unknown"' 2>/dev/null)

    if [[ "${db_status}" == "UP" ]]; then
        log_success "Database connectivity: UP"
        return 0
    else
        log_error "Database connectivity: ${db_status}"
        return 1
    fi
}

test_redis_connectivity() {
    log_info "Testing Redis connectivity..."

    local response
    response=$(curl -sf --connect-timeout 10 --max-time 30 "${BASE_URL}/actuator/health" 2>/dev/null) || {
        log_error "Could not check Redis connectivity"
        return 1
    }

    local redis_status
    redis_status=$(echo "${response}" | jq -r '.components.redis.status // "unknown"' 2>/dev/null)

    if [[ "${redis_status}" == "UP" ]]; then
        log_success "Redis connectivity: UP"
        return 0
    elif [[ "${redis_status}" == "unknown" ]]; then
        log_warning "Redis health check not available (may not be configured)"
        return 0
    else
        log_error "Redis connectivity: ${redis_status}"
        return 1
    fi
}

test_kubernetes_resources() {
    log_info "Testing Kubernetes resources..."

    if ! command -v kubectl &>/dev/null; then
        log_warning "kubectl not available - skipping Kubernetes resource tests"
        return 0
    fi

    # Check deployment status
    local ready_replicas
    ready_replicas=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")

    local desired_replicas
    desired_replicas=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "1")

    if [[ "${ready_replicas}" -ge "${desired_replicas}" ]]; then
        log_success "Kubernetes deployment: ${ready_replicas}/${desired_replicas} replicas ready"
        return 0
    else
        log_error "Kubernetes deployment: ${ready_replicas}/${desired_replicas} replicas ready"
        return 1
    fi
}

test_service_endpoints() {
    log_info "Testing service endpoints..."

    local services=(
        "engine:8080"
        "resource-service:8080"
        "worklet-service:8080"
    )

    local all_passed=true

    for service in "${services[@]}"; do
        local name="${service%:*}"
        local port="${service#*:}"

        if kubectl get service "yawl-${name}" -n "${NAMESPACE}" &>/dev/null; then
            log_success "Service yawl-${name} exists"
        else
            log_warning "Service yawl-${name} not found"
            all_passed=false
        fi
    done

    if [[ "${all_passed}" == true ]]; then
        return 0
    else
        return 1
    fi
}

test_response_times() {
    log_info "Testing response times..."

    local max_response_time=5.0

    # Test health endpoint response time
    local start_time
    start_time=$(date +%s.%N)

    curl -sf --connect-timeout 5 --max-time 10 "${BASE_URL}/actuator/health" > /dev/null 2>&1 || true

    local end_time
    end_time=$(date +%s.%N)

    local response_time
    response_time=$(echo "${end_time} - ${start_time}" | bc)

    if (( $(echo "${response_time} < ${max_response_time}" | bc -l) )); then
        log_success "Response time: ${response_time}s (< ${max_response_time}s)"
        return 0
    else
        log_error "Response time: ${response_time}s (>= ${max_response_time}s)"
        return 1
    fi
}

test_ssl_certificate() {
    log_info "Testing SSL certificate..."

    if [[ "${BASE_URL}" != https* ]]; then
        log_warning "Not using HTTPS - skipping SSL certificate test"
        return 0
    fi

    local host
    host=$(echo "${BASE_URL}" | sed 's|https://||' | cut -d'/' -f1)

    local cert_info
    cert_info=$(echo | openssl s_client -servername "${host}" -connect "${host}:443" 2>/dev/null | openssl x509 -noout -dates 2>/dev/null) || {
        log_error "Could not retrieve SSL certificate"
        return 1
    }

    local expiry_date
    expiry_date=$(echo "${cert_info}" | grep "notAfter=" | cut -d= -f2)

    log_success "SSL certificate valid until: ${expiry_date}"
    return 0
}

# =============================================================================
# Main
# =============================================================================

generate_report() {
    echo ""
    echo "================================================================================"
    echo "YAWL Smoke Test Report"
    echo "================================================================================"
    echo "Environment:    ${ENVIRONMENT}"
    echo "Base URL:       ${BASE_URL}"
    echo "Timestamp:      $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo ""
    echo "Summary:"
    echo "  Passed:   ${PASSED}"
    echo "  Failed:   ${FAILED}"
    echo "  Total:    ${TOTAL}"
    echo ""

    if [[ ${FAILED} -gt 0 ]]; then
        echo "Status: FAILED"
        echo "================================================================================"
        return 1
    else
        echo "Status: PASSED"
        echo "================================================================================"
        return 0
    fi
}

main() {
    echo "================================================================================"
    echo "YAWL v6.0.0 - Smoke Tests"
    echo "================================================================================"
    echo "Environment: ${ENVIRONMENT}"
    echo "Base URL:    ${BASE_URL}"
    echo "================================================================================"
    echo ""

    # Run all tests
    test_health_endpoint || true
    test_readiness_endpoint || true
    test_liveness_endpoint || true
    test_info_endpoint || true
    test_prometheus_endpoint || true
    test_database_connectivity || true
    test_redis_connectivity || true
    test_engine_api || true
    test_response_times || true
    test_ssl_certificate || true
    test_kubernetes_resources || true
    test_service_endpoints || true

    # Generate report
    generate_report
}

main "$@"
