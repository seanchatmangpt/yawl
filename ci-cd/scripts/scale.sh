#!/usr/bin/env bash
# =============================================================================
# YAWL Service Scaling Script
# =============================================================================
# Manages scaling of YAWL services in Docker Compose or Kubernetes.
#
# Usage:
#   ./scale.sh [docker|k8s] <service> <replicas> [namespace]
#   ./scale.sh [docker|k8s] status [namespace]
#   ./scale.sh [docker|k8s] preset <profile> [namespace]
#
# Examples:
#   ./scale.sh k8s engine 5                     # Scale engine to 5 replicas
#   ./scale.sh k8s status                       # Show current scaling
#   ./scale.sh k8s preset high-load yawl        # Apply high-load preset
#   ./scale.sh docker engine 3                  # Scale Docker engine to 3
# =============================================================================

set -euo pipefail

MODE="${1:-k8s}"
shift || true
ACTION="${1:-status}"
shift || true
NAMESPACE="${NAMESPACE:-yawl}"

# Service name mapping (user-friendly -> k8s deployment name)
resolve_k8s_name() {
    local service="$1"
    case "$service" in
        engine)             echo "yawl-engine" ;;
        resource|resource-service)  echo "yawl-resource-service" ;;
        worklet|worklet-service)    echo "yawl-worklet-service" ;;
        monitor|monitor-service)    echo "yawl-monitor-service" ;;
        cost|cost-service)          echo "yawl-cost-service" ;;
        scheduling|scheduling-service) echo "yawl-scheduling-service" ;;
        balancer)           echo "yawl-balancer" ;;
        mail|mail-service)  echo "yawl-mail-service" ;;
        *)                  echo "$service" ;;
    esac
}

scale_k8s() {
    local service="$1"
    local replicas="$2"
    local ns="${3:-$NAMESPACE}"

    local deployment
    deployment=$(resolve_k8s_name "$service")

    echo "Scaling ${deployment} to ${replicas} replica(s) in namespace ${ns}..."
    kubectl scale deployment "${deployment}" --replicas="${replicas}" -n "${ns}"

    echo "Waiting for rollout..."
    kubectl rollout status deployment "${deployment}" -n "${ns}" --timeout=300s

    echo "Current state:"
    kubectl get deployment "${deployment}" -n "${ns}"
}

scale_docker() {
    local service="$1"
    local replicas="$2"

    echo "Scaling Docker Compose service '${service}' to ${replicas} replica(s)..."
    docker compose up -d --scale "${service}=${replicas}" --no-recreate "${service}"

    echo "Current state:"
    docker compose ps "${service}"
}

status_k8s() {
    local ns="${1:-$NAMESPACE}"

    echo "=========================================="
    echo "  YAWL Scaling Status (K8s)"
    echo "  Namespace: ${ns}"
    echo "=========================================="
    echo ""

    echo "--- Deployments ---"
    printf "  %-35s %10s %10s %10s\n" "DEPLOYMENT" "DESIRED" "READY" "AVAILABLE"
    printf "  %-35s %10s %10s %10s\n" "----------" "-------" "-----" "---------"

    while IFS= read -r line; do
        local name desired ready available
        name=$(echo "$line" | awk '{print $1}')
        ready=$(echo "$line" | awk '{print $2}')
        available=$(echo "$line" | awk '{print $4}')
        desired=$(echo "$ready" | cut -d'/' -f2)
        ready_count=$(echo "$ready" | cut -d'/' -f1)

        printf "  %-35s %10s %10s %10s\n" "$name" "$desired" "$ready_count" "$available"
    done < <(kubectl get deployments -n "${ns}" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null)

    echo ""

    # HPA status
    echo "--- Horizontal Pod Autoscalers ---"
    local hpa_output
    hpa_output=$(kubectl get hpa -n "${ns}" --no-headers 2>/dev/null)
    if [ -z "$hpa_output" ]; then
        echo "  No HPAs configured"
    else
        printf "  %-35s %8s %8s %8s %10s\n" "HPA" "MIN" "MAX" "CURRENT" "CPU"
        printf "  %-35s %8s %8s %8s %10s\n" "---" "---" "---" "-------" "---"
        while IFS= read -r line; do
            local hpa_name min max current cpu
            hpa_name=$(echo "$line" | awk '{print $1}')
            min=$(echo "$line" | awk '{print $4}')
            max=$(echo "$line" | awk '{print $5}')
            current=$(echo "$line" | awk '{print $6}')
            cpu=$(echo "$line" | awk '{print $3}')
            printf "  %-35s %8s %8s %8s %10s\n" "$hpa_name" "$min" "$max" "$current" "$cpu"
        done <<< "$hpa_output"
    fi

    echo ""

    # Resource usage
    echo "--- Resource Usage ---"
    kubectl top pods -n "${ns}" -l app.kubernetes.io/part-of=yawl 2>/dev/null | head -20 || echo "  (metrics-server not available)"

    echo ""
}

status_docker() {
    echo "=========================================="
    echo "  YAWL Scaling Status (Docker Compose)"
    echo "=========================================="
    echo ""

    docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null
    echo ""
}

# Scaling presets
apply_preset_k8s() {
    local preset="$1"
    local ns="${2:-$NAMESPACE}"

    case "$preset" in
        minimal)
            echo "Applying 'minimal' preset (1 replica each)..."
            kubectl scale deployment -n "${ns}" -l app.kubernetes.io/part-of=yawl --replicas=1
            ;;
        standard)
            echo "Applying 'standard' preset..."
            kubectl scale deployment yawl-engine --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-resource-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-worklet-service --replicas=1 -n "${ns}"
            kubectl scale deployment yawl-monitor-service --replicas=1 -n "${ns}"
            kubectl scale deployment yawl-balancer --replicas=2 -n "${ns}"
            ;;
        high-load)
            echo "Applying 'high-load' preset..."
            kubectl scale deployment yawl-engine --replicas=5 -n "${ns}"
            kubectl scale deployment yawl-resource-service --replicas=3 -n "${ns}"
            kubectl scale deployment yawl-worklet-service --replicas=3 -n "${ns}"
            kubectl scale deployment yawl-monitor-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-balancer --replicas=3 -n "${ns}"
            ;;
        ha)
            echo "Applying 'ha' (high-availability) preset..."
            kubectl scale deployment yawl-engine --replicas=3 -n "${ns}"
            kubectl scale deployment yawl-resource-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-worklet-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-monitor-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-cost-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-scheduling-service --replicas=2 -n "${ns}"
            kubectl scale deployment yawl-balancer --replicas=2 -n "${ns}"
            ;;
        *)
            echo "Unknown preset: ${preset}"
            echo "Available presets: minimal, standard, high-load, ha"
            exit 1
            ;;
    esac

    echo ""
    echo "Waiting for rollouts to complete..."
    kubectl rollout status deployment -n "${ns}" -l app.kubernetes.io/part-of=yawl --timeout=300s 2>/dev/null || true

    echo ""
    status_k8s "${ns}"
}

# Main dispatch
case "$MODE" in
    k8s|kubernetes)
        case "$ACTION" in
            status)
                status_k8s "${1:-$NAMESPACE}"
                ;;
            preset)
                PRESET="${1:?Preset name required (minimal|standard|high-load|ha)}"
                NS="${2:-$NAMESPACE}"
                apply_preset_k8s "$PRESET" "$NS"
                ;;
            *)
                # ACTION is the service name, first arg is replicas
                SERVICE="$ACTION"
                REPLICAS="${1:?Number of replicas required}"
                NS="${2:-$NAMESPACE}"
                scale_k8s "$SERVICE" "$REPLICAS" "$NS"
                ;;
        esac
        ;;
    docker)
        case "$ACTION" in
            status)
                status_docker
                ;;
            *)
                SERVICE="$ACTION"
                REPLICAS="${1:?Number of replicas required}"
                scale_docker "$SERVICE" "$REPLICAS"
                ;;
        esac
        ;;
    *)
        echo "YAWL Service Scaling Tool"
        echo ""
        echo "Usage:"
        echo "  $0 [docker|k8s] <service> <replicas> [namespace]"
        echo "  $0 [docker|k8s] status [namespace]"
        echo "  $0 k8s preset <minimal|standard|high-load|ha> [namespace]"
        echo ""
        echo "Services: engine, resource, worklet, monitor, cost, scheduling, balancer"
        echo "Presets:  minimal (1 each), standard (2+1), high-load (5+3), ha (3+2)"
        ;;
esac
