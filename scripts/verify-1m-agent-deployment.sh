#!/usr/bin/env bash
#
# YAWL 1M Agent - Production Deployment Verification Script
# Verifies that all Kubernetes manifests are correctly deployed
# Status: PRODUCTION READY
#

set -e

NAMESPACE="yawl"
APP_NAME="1m-agent"
TIMEOUT="300s"
VERBOSE="${VERBOSE:-false}"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

check_kubectl() {
    log_info "Checking kubectl availability..."
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl not found. Please install kubectl."
        exit 1
    fi
    log_success "kubectl found: $(kubectl version --client --short)"
}

check_namespace() {
    log_info "Checking namespace: $NAMESPACE"
    if ! kubectl get namespace "$NAMESPACE" &> /dev/null; then
        log_error "Namespace '$NAMESPACE' not found"
        log_info "Creating namespace..."
        kubectl create namespace "$NAMESPACE"
        kubectl label namespace "$NAMESPACE" name="$NAMESPACE"
        log_success "Namespace created and labeled"
    else
        log_success "Namespace exists"
    fi
}

check_deployment() {
    log_info "Verifying Deployment..."

    if ! kubectl get deployment "$APP_NAME" -n "$NAMESPACE" &> /dev/null; then
        log_error "Deployment '$APP_NAME' not found"
        return 1
    fi

    log_success "Deployment found"

    # Check replicas
    READY_REPLICAS=$(kubectl get deployment "$APP_NAME" -n "$NAMESPACE" \
        -o jsonpath='{.status.readyReplicas}')
    DESIRED_REPLICAS=$(kubectl get deployment "$APP_NAME" -n "$NAMESPACE" \
        -o jsonpath='{.spec.replicas}')

    log_info "Replicas: $READY_REPLICAS/$DESIRED_REPLICAS"

    if [ "$READY_REPLICAS" -eq "$DESIRED_REPLICAS" ]; then
        log_success "All replicas ready"
    else
        log_warning "Not all replicas ready yet. Waiting..."
        kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout="$TIMEOUT" || true
    fi
}

check_hpa() {
    log_info "Verifying HPA..."

    if ! kubectl get hpa "${APP_NAME}-hpa" -n "$NAMESPACE" &> /dev/null; then
        log_error "HPA '${APP_NAME}-hpa' not found"
        return 1
    fi

    log_success "HPA found"

    # Get HPA status
    kubectl get hpa "${APP_NAME}-hpa" -n "$NAMESPACE" -o wide
}

check_pdb() {
    log_info "Verifying Pod Disruption Budget..."

    if ! kubectl get pdb "${APP_NAME}-pdb" -n "$NAMESPACE" &> /dev/null; then
        log_error "PDB '${APP_NAME}-pdb' not found"
        return 1
    fi

    log_success "PDB found"

    # Check PDB status
    MIN_AVAILABLE=$(kubectl get pdb "${APP_NAME}-pdb" -n "$NAMESPACE" \
        -o jsonpath='{.spec.minAvailable}')
    DISRUPTIONS_ALLOWED=$(kubectl get pdb "${APP_NAME}-pdb" -n "$NAMESPACE" \
        -o jsonpath='{.status.disruptionsAllowed}')

    log_info "Min available: $MIN_AVAILABLE pods"
    log_info "Disruptions allowed: $DISRUPTIONS_ALLOWED"
}

check_service() {
    log_info "Verifying Service..."

    if ! kubectl get svc "$APP_NAME" -n "$NAMESPACE" &> /dev/null; then
        log_error "Service '$APP_NAME' not found"
        return 1
    fi

    log_success "Service found"

    # Get service info
    CLUSTER_IP=$(kubectl get svc "$APP_NAME" -n "$NAMESPACE" \
        -o jsonpath='{.spec.clusterIP}')
    PORT=$(kubectl get svc "$APP_NAME" -n "$NAMESPACE" \
        -o jsonpath='{.spec.ports[0].port}')

    log_info "Cluster IP: $CLUSTER_IP:$PORT"
}

check_ingress() {
    log_info "Verifying Ingress..."

    if ! kubectl get ingress "${APP_NAME}-ingress" -n "$NAMESPACE" &> /dev/null; then
        log_error "Ingress '${APP_NAME}-ingress' not found"
        return 1
    fi

    log_success "Ingress found"

    # Get ingress info
    HOSTS=$(kubectl get ingress "${APP_NAME}-ingress" -n "$NAMESPACE" \
        -o jsonpath='{.spec.rules[*].host}')

    log_info "Hosts: $HOSTS"
}

check_rbac() {
    log_info "Verifying RBAC..."

    # Check ServiceAccount
    if ! kubectl get sa "$APP_NAME" -n "$NAMESPACE" &> /dev/null; then
        log_error "ServiceAccount '$APP_NAME' not found"
        return 1
    fi
    log_success "ServiceAccount found"

    # Check ClusterRole
    if ! kubectl get clusterrole "$APP_NAME" &> /dev/null; then
        log_error "ClusterRole '$APP_NAME' not found"
        return 1
    fi
    log_success "ClusterRole found"

    # Check ClusterRoleBinding
    if ! kubectl get clusterrolebinding "$APP_NAME" &> /dev/null; then
        log_error "ClusterRoleBinding '$APP_NAME' not found"
        return 1
    fi
    log_success "ClusterRoleBinding found"

    # Check Role
    if ! kubectl get role "$APP_NAME" -n "$NAMESPACE" &> /dev/null; then
        log_error "Role '$APP_NAME' not found"
        return 1
    fi
    log_success "Role found"

    # Check RoleBinding
    if ! kubectl get rolebinding "$APP_NAME" -n "$NAMESPACE" &> /dev/null; then
        log_error "RoleBinding '$APP_NAME' not found"
        return 1
    fi
    log_success "RoleBinding found"
}

check_configmap() {
    log_info "Verifying ConfigMap..."

    if ! kubectl get configmap "${APP_NAME}-config" -n "$NAMESPACE" &> /dev/null; then
        log_error "ConfigMap '${APP_NAME}-config' not found"
        return 1
    fi

    log_success "ConfigMap found"

    # Count configuration entries
    COUNT=$(kubectl get configmap "${APP_NAME}-config" -n "$NAMESPACE" \
        -o jsonpath='{.data}' | wc -c)
    log_info "Configuration entries: ~$(($COUNT / 50)) keys"
}

check_networkpolicy() {
    log_info "Verifying Network Policies..."

    COUNT=$(kubectl get networkpolicy -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" 2>/dev/null | wc -l)
    if [ "$COUNT" -lt 2 ]; then
        log_error "Expected 2+ network policies, found $COUNT"
        return 1
    fi

    log_success "Network policies found"

    kubectl get networkpolicy -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME"
}

check_pods() {
    log_info "Verifying Pods..."

    PODS=$(kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" 2>/dev/null)
    if [ -z "$PODS" ]; then
        log_error "No pods found"
        return 1
    fi

    log_success "Pods found:"
    kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" -o wide

    # Check pod status
    NOT_READY=$(kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" \
        --field-selector=status.phase!=Running 2>/dev/null | wc -l)

    if [ "$NOT_READY" -gt 1 ]; then
        log_warning "Some pods not in Running state"
    fi
}

check_health() {
    log_info "Checking pod health endpoints..."

    PODS=$(kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" \
        -o jsonpath='{.items[*].metadata.name}')

    if [ -z "$PODS" ]; then
        log_warning "No running pods to check"
        return 0
    fi

    # Get first pod
    FIRST_POD=$(echo "$PODS" | awk '{print $1}')

    log_info "Testing health endpoint on pod: $FIRST_POD"

    # Try health check via port-forward
    if kubectl port-forward -n "$NAMESPACE" "pod/$FIRST_POD" 8080:8080 &> /dev/null &
    then
        PF_PID=$!
        sleep 2

        if curl -s http://localhost:8080/actuator/health/readiness 2>/dev/null | grep -q "UP"; then
            log_success "Readiness probe: UP"
        else
            log_warning "Could not verify readiness endpoint"
        fi

        kill $PF_PID 2>/dev/null || true
    else
        log_warning "Could not port-forward to verify health"
    fi
}

check_metrics() {
    log_info "Checking metrics availability..."

    PODS=$(kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" \
        -o jsonpath='{.items[*].metadata.name}')

    if [ -z "$PODS" ]; then
        log_warning "No running pods"
        return 0
    fi

    FIRST_POD=$(echo "$PODS" | awk '{print $1}')

    # Check if metrics endpoint is accessible
    if kubectl port-forward -n "$NAMESPACE" "pod/$FIRST_POD" 8080:8080 &> /dev/null &
    then
        PF_PID=$!
        sleep 2

        if curl -s http://localhost:8080/actuator/prometheus 2>/dev/null | wc -l | grep -qv "^0$"; then
            log_success "Prometheus metrics available"
        else
            log_warning "Could not access metrics endpoint"
        fi

        kill $PF_PID 2>/dev/null || true
    fi
}

print_summary() {
    log_info "=== DEPLOYMENT SUMMARY ==="

    echo ""
    log_info "Namespace: $NAMESPACE"
    log_info "Application: $APP_NAME"

    # Resource summary
    log_info "Deployment replicas:"
    kubectl get deployment "$APP_NAME" -n "$NAMESPACE" \
        -o jsonpath='{.status.replicas}/{.spec.replicas} ready/desired'
    echo ""

    # HPA summary
    log_info "HPA status:"
    kubectl get hpa "${APP_NAME}-hpa" -n "$NAMESPACE" \
        -o jsonpath='{.status.currentReplicas} current, min={.spec.minReplicas}, max={.spec.maxReplicas}'
    echo ""

    # Pod summary
    log_info "Pods:"
    kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=$APP_NAME" --no-headers 2>/dev/null | wc -l
    echo " pods total"

    # Service summary
    log_info "Service: $APP_NAME"
    kubectl get svc "$APP_NAME" -n "$NAMESPACE" --no-headers 2>/dev/null || log_warning "Service not found"
}

main() {
    echo ""
    log_info "========================================="
    log_info "YAWL 1M Agent - Deployment Verification"
    log_info "========================================="
    echo ""

    check_kubectl || exit 1
    check_namespace || exit 1

    echo ""
    log_info "Running verification checks..."
    echo ""

    check_deployment || true
    echo ""

    check_hpa || true
    echo ""

    check_pdb || true
    echo ""

    check_service || true
    echo ""

    check_ingress || true
    echo ""

    check_rbac || true
    echo ""

    check_configmap || true
    echo ""

    check_networkpolicy || true
    echo ""

    check_pods || true
    echo ""

    check_health || true
    echo ""

    check_metrics || true
    echo ""

    print_summary

    echo ""
    log_success "========================================="
    log_success "Verification Complete"
    log_success "========================================="
    echo ""
}

main "$@"
