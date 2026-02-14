#!/usr/bin/env bash
# =============================================================================
# YAWL Health Check Script
# =============================================================================
# Checks health of all YAWL services in Docker Compose or Kubernetes.
#
# Usage:
#   ./health-check.sh [docker|k8s] [namespace]
#
# Examples:
#   ./health-check.sh docker                  # Check Docker Compose services
#   ./health-check.sh k8s                     # Check K8s services (yawl namespace)
#   ./health-check.sh k8s yawl-staging        # Check K8s services in staging
# =============================================================================

set -euo pipefail

MODE="${1:-docker}"
NAMESPACE="${2:-yawl}"
EXIT_CODE=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    local service="$1"
    local status="$2"
    local detail="${3:-}"

    if [ "$status" = "healthy" ]; then
        printf "  ${GREEN}[OK]${NC}   %-30s %s\n" "$service" "$detail"
    elif [ "$status" = "degraded" ]; then
        printf "  ${YELLOW}[WARN]${NC} %-30s %s\n" "$service" "$detail"
    else
        printf "  ${RED}[FAIL]${NC} %-30s %s\n" "$service" "$detail"
        EXIT_CODE=1
    fi
}

check_docker_service() {
    local service="$1"
    local port="$2"
    local url="http://localhost:${port}/"

    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null) || http_code="000"

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 400 ]; then
        print_status "$service" "healthy" "HTTP ${http_code} on port ${port}"
    elif [ "$http_code" -ge 400 ] && [ "$http_code" -lt 500 ]; then
        print_status "$service" "degraded" "HTTP ${http_code} on port ${port}"
    else
        print_status "$service" "failed" "HTTP ${http_code} on port ${port}"
    fi
}

check_docker() {
    echo "=========================================="
    echo "  YAWL Docker Compose Health Check"
    echo "=========================================="
    echo ""

    # Check PostgreSQL
    if docker exec yawl-postgres pg_isready -U yawl > /dev/null 2>&1; then
        print_status "PostgreSQL" "healthy" "pg_isready OK"
    else
        print_status "PostgreSQL" "failed" "pg_isready failed"
    fi

    # Check Redis (if running)
    if docker ps --format '{{.Names}}' | grep -q yawl-redis; then
        if docker exec yawl-redis redis-cli -a yawl-redis ping > /dev/null 2>&1; then
            print_status "Redis" "healthy" "PONG"
        else
            print_status "Redis" "failed" "no response"
        fi
    fi

    # Check YAWL services
    local -A SERVICES=(
        ["Engine"]=8888
        ["Resource Service"]=8081
        ["Worklet Service"]=8082
        ["Monitor Service"]=8083
        ["Cost Service"]=8084
        ["Scheduling Service"]=8085
        ["Balancer"]=8086
    )

    for service in "${!SERVICES[@]}"; do
        local port="${SERVICES[$service]}"
        if docker ps --format '{{.Names}}' | grep -q "yawl-$(echo "$service" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')"; then
            check_docker_service "$service" "$port"
        else
            print_status "$service" "degraded" "container not running"
        fi
    done

    echo ""
    echo "=========================================="
    if [ "$EXIT_CODE" -eq 0 ]; then
        echo "  ${GREEN}All services healthy${NC}"
    else
        echo "  ${RED}Some services unhealthy${NC}"
    fi
    echo "=========================================="
}

check_k8s() {
    echo "=========================================="
    echo "  YAWL Kubernetes Health Check"
    echo "  Namespace: ${NAMESPACE}"
    echo "=========================================="
    echo ""

    # Check namespace exists
    if ! kubectl get namespace "$NAMESPACE" > /dev/null 2>&1; then
        echo "${RED}Namespace '${NAMESPACE}' not found${NC}"
        exit 1
    fi

    # Pod status summary
    echo "--- Pod Status ---"
    local total ready not_ready
    total=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null | wc -l)
    ready=$(kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/part-of=yawl --field-selector=status.phase=Running --no-headers 2>/dev/null | wc -l)
    not_ready=$((total - ready))

    printf "  Total: %d  Running: %d  Not Ready: %d\n\n" "$total" "$ready" "$not_ready"

    # Check each deployment
    echo "--- Deployments ---"
    while IFS= read -r line; do
        local name ready_str
        name=$(echo "$line" | awk '{print $1}')
        ready_str=$(echo "$line" | awk '{print $2}')

        local desired available
        desired=$(echo "$ready_str" | cut -d'/' -f2)
        available=$(echo "$ready_str" | cut -d'/' -f1)

        if [ "$available" = "$desired" ]; then
            print_status "$name" "healthy" "${available}/${desired} ready"
        elif [ "$available" -gt 0 ]; then
            print_status "$name" "degraded" "${available}/${desired} ready"
        else
            print_status "$name" "failed" "${available}/${desired} ready"
        fi
    done < <(kubectl get deployments -n "$NAMESPACE" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null)

    echo ""

    # Check services have endpoints
    echo "--- Service Endpoints ---"
    while IFS= read -r svc; do
        local endpoints
        endpoints=$(kubectl get endpoints "$svc" -n "$NAMESPACE" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null)
        local count
        count=$(echo "$endpoints" | wc -w)

        if [ "$count" -gt 0 ]; then
            print_status "$svc" "healthy" "${count} endpoint(s)"
        else
            print_status "$svc" "failed" "no endpoints"
        fi
    done < <(kubectl get services -n "$NAMESPACE" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null | awk '{print $1}')

    echo ""

    # HPA status
    echo "--- Autoscaling ---"
    while IFS= read -r line; do
        local hpa_name current_replicas max_replicas cpu_pct
        hpa_name=$(echo "$line" | awk '{print $1}')
        current_replicas=$(echo "$line" | awk '{print $6}')
        max_replicas=$(echo "$line" | awk '{print $5}')
        cpu_pct=$(echo "$line" | awk '{print $3}')

        print_status "$hpa_name" "healthy" "replicas: ${current_replicas}/${max_replicas}, CPU: ${cpu_pct}"
    done < <(kubectl get hpa -n "$NAMESPACE" --no-headers 2>/dev/null)

    echo ""

    # Recent events (warnings only)
    echo "--- Recent Warnings (last 30m) ---"
    local warnings
    warnings=$(kubectl get events -n "$NAMESPACE" --field-selector type=Warning --sort-by='.lastTimestamp' 2>/dev/null | tail -5)
    if [ -z "$warnings" ] || [ "$warnings" = "No resources found in ${NAMESPACE} namespace." ]; then
        printf "  ${GREEN}No warnings${NC}\n"
    else
        echo "$warnings" | while IFS= read -r w; do
            printf "  ${YELLOW}%s${NC}\n" "$w"
        done
    fi

    echo ""
    echo "=========================================="
    if [ "$EXIT_CODE" -eq 0 ]; then
        printf "  ${GREEN}All services healthy${NC}\n"
    else
        printf "  ${RED}Some services unhealthy${NC}\n"
    fi
    echo "=========================================="
}

case "$MODE" in
    docker)
        check_docker
        ;;
    k8s|kubernetes)
        check_k8s
        ;;
    *)
        echo "Usage: $0 [docker|k8s] [namespace]"
        exit 1
        ;;
esac

exit "$EXIT_CODE"
