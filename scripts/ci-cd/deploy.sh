#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0.0 - Deployment Script
# =============================================================================
# Usage:
#   ./scripts/ci-cd/deploy.sh --environment <env> [--version <ver>] [--strategy <strategy>]
#
# Strategies:
#   - rolling: Standard rolling update (default)
#   - blue-green: Deploy to inactive slot, switch traffic
#   - canary: Gradual traffic shift to new version
#
# Prerequisites:
#   - kubectl configured with target cluster
#   - Helm 3.x installed
#   - jq for JSON parsing
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
HELM_CHART="${PROJECT_ROOT}/helm/yawl"
TIMEOUT="10m"

# Default values
ENVIRONMENT=""
VERSION=""
STRATEGY="rolling"
DRY_RUN=false
ROLLBACK=false
NAMESPACE=""
VALUES_FILE=""

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Options:
    -e, --environment <env>      Target environment (dev|staging|production) [required]
    -v, --version <version>      Version to deploy (default: from Chart.yaml)
    -s, --strategy <strategy>    Deployment strategy (rolling|blue-green|canary)
    -n, --namespace <namespace>  Kubernetes namespace (default: yawl-<env>)
    -f, --values <file>          Additional values file
    --dry-run                    Show what would be deployed without applying
    --rollback                   Rollback to previous version
    -h, --help                   Show this help message

Examples:
    # Deploy to staging with rolling update
    $(basename "$0") -e staging -v 6.0.0

    # Production canary deployment
    $(basename "$0") -e production -v 6.0.1 -s canary

    # Blue-green deployment
    $(basename "$0") -e production -v 6.0.1 -s blue-green

    # Rollback production
    $(basename "$0") -e production --rollback
EOF
    exit 1
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is required but not installed"
        exit 1
    fi

    # Check helm
    if ! command -v helm &> /dev/null; then
        log_error "helm is required but not installed"
        exit 1
    fi

    # Check jq
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed"
        exit 1
    fi

    # Check cluster connectivity
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        exit 1
    fi

    log_success "Prerequisites check passed"
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -v|--version)
                VERSION="$2"
                shift 2
                ;;
            -s|--strategy)
                STRATEGY="$2"
                shift 2
                ;;
            -n|--namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            -f|--values)
                VALUES_FILE="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --rollback)
                ROLLBACK=true
                shift
                ;;
            -h|--help)
                usage
                ;;
            *)
                log_error "Unknown option: $1"
                usage
                ;;
        esac
    done

    # Validate required arguments
    if [[ -z "${ENVIRONMENT}" ]]; then
        log_error "Environment is required"
        usage
    fi

    # Set namespace if not provided
    if [[ -z "${NAMESPACE}" ]]; then
        NAMESPACE="yawl-${ENVIRONMENT}"
    fi

    # Get version from Chart.yaml if not provided
    if [[ -z "${VERSION}" ]]; then
        VERSION=$(grep -oP '^version:\s*\K.*' "${HELM_CHART}/Chart.yaml" | tr -d ' ')
    fi
}

validate_environment() {
    log_info "Validating environment configuration..."

    local values_file="${HELM_CHART}/envs/values-${ENVIRONMENT}.yaml"

    if [[ ! -f "${values_file}" ]]; then
        log_error "Values file not found: ${values_file}"
        exit 1
    fi

    if [[ ! -f "${HELM_CHART}/values.yaml" ]]; then
        log_error "Base values file not found: ${HELM_CHART}/values.yaml"
        exit 1
    fi

    log_success "Environment configuration validated"
}

# =============================================================================
# Deployment Functions
# =============================================================================

deploy_rolling() {
    log_info "Starting rolling deployment to ${ENVIRONMENT}..."

    local helm_args=(
        upgrade
        --install
        yawl
        "${HELM_CHART}"
        --namespace "${NAMESPACE}"
        --values "${HELM_CHART}/values.yaml"
        --values "${HELM_CHART}/envs/values-${ENVIRONMENT}.yaml"
        --set image.tag="${VERSION}"
        --set yawlVersion="${VERSION}"
        --timeout "${TIMEOUT}"
        --history-max 10
        --wait
        --wait-for-jobs
    )

    if [[ -n "${VALUES_FILE}" ]]; then
        helm_args+=(--values "${VALUES_FILE}")
    fi

    if [[ "${DRY_RUN}" == true ]]; then
        helm_args+=(--dry-run --debug)
    fi

    helm "${helm_args[@]}"

    if [[ "${DRY_RUN}" == false ]]; then
        log_success "Rolling deployment completed"
        verify_deployment
    fi
}

deploy_blue_green() {
    log_info "Starting blue-green deployment to ${ENVIRONMENT}..."

    local current_color
    local target_color

    # Determine current active deployment
    current_color=$(kubectl get service yawl-engine -n "${NAMESPACE}" -o jsonpath='{.spec.selector.color}' 2>/dev/null || echo "blue")

    if [[ "${current_color}" == "blue" ]]; then
        target_color="green"
    else
        target_color="blue"
    fi

    log_info "Current: ${current_color}, Target: ${target_color}"

    # Deploy to inactive slot
    local release_name="yawl-${target_color}"
    local helm_args=(
        upgrade
        --install
        "${release_name}"
        "${HELM_CHART}"
        --namespace "${NAMESPACE}"
        --values "${HELM_CHART}/values.yaml"
        --values "${HELM_CHART}/envs/values-${ENVIRONMENT}.yaml"
        --set image.tag="${VERSION}"
        --set yawlVersion="${VERSION}"
        --set services.engine.podLabels.color="${target_color}"
        --timeout "${TIMEOUT}"
        --wait
    )

    if [[ "${DRY_RUN}" == true ]]; then
        helm_args+=(--dry-run --debug)
    fi

    helm "${helm_args[@]}"

    if [[ "${DRY_RUN}" == true ]]; then
        log_info "Dry run completed - would switch traffic to ${target_color}"
        return
    fi

    # Run smoke tests on target
    log_info "Running smoke tests on ${target_color}..."
    run_smoke_tests "${target_color}"

    # Switch traffic
    log_info "Switching traffic to ${target_color}..."
    kubectl patch service yawl-engine -n "${NAMESPACE}" -p "{\"spec\":{\"selector\":{\"color\":\"${target_color}\"}}}"

    # Verify traffic switch
    sleep 30
    verify_deployment

    # Cleanup old deployment after delay
    log_info "Scheduling cleanup of ${current_color} deployment..."
    (sleep 300 && helm uninstall "yawl-${current_color}" --namespace "${NAMESPACE}") &

    log_success "Blue-green deployment completed - now serving ${target_color}"
}

deploy_canary() {
    log_info "Starting canary deployment to ${ENVIRONMENT}..."

    local canary_weight=5
    local max_weight=100
    local step_weight=20
    local wait_seconds=180

    if [[ "${DRY_RUN}" == true ]]; then
        log_info "Dry run: Would deploy canary with gradual traffic shift"
        return
    fi

    # Deploy canary version
    local helm_args=(
        upgrade
        --install
        yawl-canary
        "${HELM_CHART}"
        --namespace "${NAMESPACE}"
        --values "${HELM_CHART}/values.yaml"
        --values "${HELM_CHART}/envs/values-${ENVIRONMENT}.yaml"
        --set image.tag="${VERSION}"
        --set yawlVersion="${VERSION}"
        --set services.engine.replicaCount=1
        --set services.engine.podLabels.track=canary
        --timeout "${TIMEOUT}"
        --wait
    )

    helm "${helm_args[@]}"

    # Progressive traffic shift
    while [[ ${canary_weight} -lt ${max_weight} ]]; do
        log_info "Setting canary weight to ${canary_weight}%..."

        # Update Istio VirtualService or Nginx Ingress
        if kubectl get virtualservice yawl-engine -n "${NAMESPACE}" &>/dev/null; then
            update_istio_canary "${canary_weight}"
        else
            # Fallback to weighted service (without Istio)
            log_warning "Istio not detected - manual canary weight adjustment required"
        fi

        # Wait and monitor
        log_info "Monitoring for ${wait_seconds} seconds..."
        sleep "${wait_seconds}"

        # Check error rate
        if ! check_canary_health; then
            log_error "Canary health check failed - aborting"
            rollback_canary
            exit 1
        fi

        canary_weight=$((canary_weight + step_weight))
    done

    # Promote canary to stable
    log_info "Promoting canary to stable..."
    helm upgrade --install yawl "${HELM_CHART}" \
        --namespace "${NAMESPACE}" \
        --values "${HELM_CHART}/values.yaml" \
        --values "${HELM_CHART}/envs/values-${ENVIRONMENT}.yaml" \
        --set image.tag="${VERSION}" \
        --wait

    # Cleanup canary
    helm uninstall yawl-canary --namespace "${NAMESPACE}" 2>/dev/null || true

    log_success "Canary deployment completed"
}

update_istio_canary() {
    local weight="$1"

    kubectl patch virtualservice yawl-engine -n "${NAMESPACE}" --type=merge -p "
{
    \"spec\": {
        \"http\": [{
            \"route\": [
                {
                    \"destination\": { \"host\": \"yawl-engine\", \"subset\": \"stable\" },
                    \"weight\": $((100 - weight))
                },
                {
                    \"destination\": { \"host\": \"yawl-engine\", \"subset\": \"canary\" },
                    \"weight\": ${weight}
                }
            ]
        }]
    }
}"
}

check_canary_health() {
    local error_rate

    # Check error rate from Prometheus (if available)
    if command -v curl &>/dev/null; then
        error_rate=$(curl -s "http://prometheus-server.monitoring/api/v1/query?query=rate(yawl_engine_errors_total[5m])" 2>/dev/null | jq -r '.data.result[0].value[1] // "0"' || echo "0")

        if (( $(echo "${error_rate} > 0.05" | bc -l) )); then
            return 1
        fi
    fi

    # Check pod health
    local ready_pods
    ready_pods=$(kubectl get pods -n "${NAMESPACE}" -l track=canary -o json | jq -r '.items | map(select(.status.phase=="Running")) | length')

    if [[ "${ready_pods}" -lt 1 ]]; then
        return 1
    fi

    return 0
}

rollback_canary() {
    log_warning "Rolling back canary deployment..."

    # Remove canary traffic
    if kubectl get virtualservice yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        update_istio_canary 0
    fi

    # Uninstall canary release
    helm uninstall yawl-canary --namespace "${NAMESPACE}" 2>/dev/null || true

    log_success "Canary rollback completed"
}

rollback_deployment() {
    log_info "Rolling back deployment in ${ENVIRONMENT}..."

    local history
    history=$(helm history yawl --namespace "${NAMESPACE}" -o json 2>/dev/null || echo "[]")

    local previous_revision
    previous_revision=$(echo "${history}" | jq -r 'map(select(.status=="superseded")) | sort_by(.revision) | last | .revision // empty')

    if [[ -z "${previous_revision}" ]]; then
        log_error "No previous revision found for rollback"
        exit 1
    fi

    log_info "Rolling back to revision ${previous_revision}..."

    if [[ "${DRY_RUN}" == true ]]; then
        helm rollback yawl "${previous_revision}" --namespace "${NAMESPACE}" --dry-run
    else
        helm rollback yawl "${previous_revision}" --namespace "${NAMESPACE}"
        verify_deployment
    fi

    log_success "Rollback completed"
}

run_smoke_tests() {
    local color="${1:-current}"
    log_info "Running smoke tests on ${color} deployment..."

    # Get service endpoint
    local endpoint
    endpoint=$(kubectl get service yawl-engine -n "${NAMESPACE}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [[ -z "${endpoint}" ]]; then
        endpoint="yawl-engine.${NAMESPACE}.svc.cluster.local"
    fi

    # Health check
    local health_url="http://${endpoint}:8080/actuator/health"

    if ! curl -sf "${health_url}" > /dev/null 2>&1; then
        log_error "Health check failed: ${health_url}"
        return 1
    fi

    log_success "Smoke tests passed"
}

verify_deployment() {
    log_info "Verifying deployment..."

    # Wait for rollout
    kubectl rollout status deployment/yawl-engine -n "${NAMESPACE}" --timeout="${TIMEOUT}"

    # Check pod health
    local ready_pods
    ready_pods=$(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/name=yawl-engine -o json | jq -r '[.items[] | select(.status.phase=="Running" and (.status.containerStatuses[0].ready // false))] | length')

    local desired_pods
    desired_pods=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o jsonpath='{.spec.replicas}')

    if [[ "${ready_pods}" -lt "${desired_pods}" ]]; then
        log_error "Not enough ready pods: ${ready_pods}/${desired_pods}"
        exit 1
    fi

    # Run health checks
    run_smoke_tests

    log_success "Deployment verified - ${ready_pods}/${desired_pods} pods ready"
}

generate_deployment_report() {
    local status="success"
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    cat << EOF
================================================================================
YAWL Deployment Report
================================================================================
Environment:    ${ENVIRONMENT}
Namespace:      ${NAMESPACE}
Version:        ${VERSION}
Strategy:       ${STRATEGY}
Timestamp:      ${timestamp}
Status:         ${status}

Cluster Info:
$(kubectl cluster-info 2>/dev/null | head -2)

Pods:
$(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null || echo "No pods found")

Services:
$(kubectl get services -n "${NAMESPACE}" -l app.kubernetes.io/part-of=yawl --no-headers 2>/dev/null || echo "No services found")

================================================================================
EOF
}

# =============================================================================
# Main
# =============================================================================

main() {
    parse_arguments "$@"
    check_prerequisites
    validate_environment

    # Create namespace if it doesn't exist
    if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        log_info "Creating namespace: ${NAMESPACE}"
        kubectl create namespace "${NAMESPACE}"
    fi

    if [[ "${ROLLBACK}" == true ]]; then
        rollback_deployment
    else
        case "${STRATEGY}" in
            rolling)
                deploy_rolling
                ;;
            blue-green)
                deploy_blue_green
                ;;
            canary)
                deploy_canary
                ;;
            *)
                log_error "Unknown deployment strategy: ${STRATEGY}"
                usage
                ;;
        esac
    fi

    if [[ "${DRY_RUN}" == false ]]; then
        generate_deployment_report
    fi
}

main "$@"
