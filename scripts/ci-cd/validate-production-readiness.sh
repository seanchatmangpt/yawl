#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0.0 - Production Readiness Validation Script
# =============================================================================
# Validates that the deployment is ready for production by checking:
#   - Infrastructure requirements
#   - Security configurations
#   - Resource allocations
#   - Monitoring and alerting setup
#   - Database connectivity
#   - Secret management
#
# Exit codes:
#   0 - All checks passed
#   1 - Critical checks failed
#   2 - Warnings only (proceed with caution)
# =============================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
PASSED=0
FAILED=0
WARNINGS=0

# Default values
ENVIRONMENT=""
NAMESPACE=""
SKIP_CHECKS=""

# =============================================================================
# Helper Functions
# =============================================================================

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASSED++)); }
log_warning() { echo -e "${YELLOW}[WARN]${NC} $1"; ((WARNINGS++)); }
log_error() { echo -e "${RED}[FAIL]${NC} $1"; ((FAILED++)); }

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS] <environment>

Arguments:
    environment          Target environment (staging|production)

Options:
    -n, --namespace <ns> Kubernetes namespace (default: yawl-<env>)
    --skip <checks>      Comma-separated checks to skip
    -h, --help           Show this help message

Available checks:
    - cluster: Kubernetes cluster connectivity and version
    - resources: Resource quotas and limits
    - security: Security contexts and policies
    - network: Network policies and ingress
    - monitoring: Prometheus and alerting setup
    - database: Database connectivity and migrations
    - secrets: Secret management validation
    - images: Container image validation
EOF
    exit 1
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -n|--namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            --skip)
                SKIP_CHECKS="$2"
                shift 2
                ;;
            -h|--help)
                usage
                ;;
            *)
                if [[ -z "${ENVIRONMENT}" ]]; then
                    ENVIRONMENT="$1"
                fi
                shift
                ;;
        esac
    done

    if [[ -z "${ENVIRONMENT}" ]]; then
        echo "Error: Environment is required"
        usage
    fi

    if [[ -z "${NAMESPACE}" ]]; then
        NAMESPACE="yawl-${ENVIRONMENT}"
    fi
}

should_skip() {
    local check="$1"
    if [[ -n "${SKIP_CHECKS}" ]]; then
        IFS=',' read -ra skips <<< "${SKIP_CHECKS}"
        for skip in "${skips[@]}"; do
            if [[ "${skip}" == "${check}" ]]; then
                return 0
            fi
        done
    fi
    return 1
}

# =============================================================================
# Validation Functions
# =============================================================================

check_cluster() {
    log_info "Checking cluster connectivity..."

    if should_skip "cluster"; then
        log_warning "Skipping cluster checks"
        return
    fi

    # Check cluster connectivity
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        return
    fi
    log_success "Cluster connectivity OK"

    # Check Kubernetes version
    local k8s_version
    k8s_version=$(kubectl version -o json | jq -r '.serverVersion.gitVersion' | sed 's/v//')
    local major_minor
    major_minor=$(echo "${k8s_version}" | cut -d. -f1,2)

    if [[ "$(echo "${major_minor} >= 1.27" | bc)" -eq 1 ]]; then
        log_success "Kubernetes version ${k8s_version} OK (>= 1.27)"
    else
        log_error "Kubernetes version ${k8s_version} too old (requires >= 1.27)"
    fi

    # Check namespace exists
    if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        log_warning "Namespace ${NAMESPACE} does not exist (will be created)"
    else
        log_success "Namespace ${NAMESPACE} exists"
    fi
}

check_resources() {
    log_info "Checking resource allocations..."

    if should_skip "resources"; then
        log_warning "Skipping resource checks"
        return
    fi

    # Check resource quotas
    if kubectl get resourcequota -n "${NAMESPACE}" &>/dev/null; then
        log_success "Resource quotas configured"
    else
        log_warning "No resource quotas found in ${NAMESPACE}"
    fi

    # Check limit ranges
    if kubectl get limitrange -n "${NAMESPACE}" &>/dev/null; then
        log_success "Limit ranges configured"
    else
        log_warning "No limit ranges found in ${NAMESPACE}"
    fi

    # Check node resources
    local node_capacity
    node_capacity=$(kubectl top nodes 2>/dev/null || echo "")

    if [[ -n "${node_capacity}" ]]; then
        log_success "Node metrics available"
    else
        log_warning "Metrics server not available - cannot check node resources"
    fi

    # Validate deployment resource requirements
    if kubectl get deployment yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        local cpu_request
        cpu_request=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.template.spec.containers[0].resources.requests.cpu // empty')

        if [[ -n "${cpu_request}" ]]; then
            log_success "Engine CPU request: ${cpu_request}"
        else
            log_error "Engine deployment missing CPU request"
        fi

        local memory_request
        memory_request=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.template.spec.containers[0].resources.requests.memory // empty')

        if [[ -n "${memory_request}" ]]; then
            log_success "Engine memory request: ${memory_request}"
        else
            log_error "Engine deployment missing memory request"
        fi
    fi
}

check_security() {
    log_info "Checking security configurations..."

    if should_skip "security"; then
        log_warning "Skipping security checks"
        return
    fi

    # Check Pod Security Standards
    local pss_labels
    pss_labels=$(kubectl get namespace "${NAMESPACE}" -o json 2>/dev/null | \
        jq -r '.metadata.labels["pod-security.kubernetes.io/enforce"] // empty')

    if [[ -n "${pss_labels}" ]]; then
        log_success "Pod Security Standard enforced: ${pss_labels}"
    else
        log_warning "Pod Security Standard not enforced on namespace"
    fi

    # Check security context on deployment
    if kubectl get deployment yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        local run_as_non_root
        run_as_non_root=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.template.spec.securityContext.runAsNonRoot // false')

        if [[ "${run_as_non_root}" == "true" ]]; then
            log_success "Deployment runs as non-root user"
        else
            log_error "Deployment does not enforce non-root user"
        fi

        local read_only_root
        read_only_root=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.template.spec.containers[0].securityContext.readOnlyRootFilesystem // false')

        if [[ "${read_only_root}" == "true" ]]; then
            log_success "Deployment uses read-only root filesystem"
        else
            log_warning "Deployment does not use read-only root filesystem"
        fi

        local drop_capabilities
        drop_capabilities=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.template.spec.containers[0].securityContext.capabilities.drop // [] | join(",")')

        if [[ "${drop_capabilities}" == *"ALL"* ]]; then
            log_success "All capabilities dropped"
        else
            log_error "Not all capabilities dropped: ${drop_capabilities}"
        fi
    fi

    # Check Service Account
    local automount_token
    automount_token=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json 2>/dev/null | \
        jq -r '.spec.template.spec.automountServiceAccountToken // true')

    if [[ "${automount_token}" == "false" ]]; then
        log_success "Service account token not auto-mounted"
    else
        log_warning "Service account token is auto-mounted"
    fi
}

check_network() {
    log_info "Checking network policies..."

    if should_skip "network"; then
        log_warning "Skipping network checks"
        return
    fi

    # Check network policies
    local netpol_count
    netpol_count=$(kubectl get networkpolicies -n "${NAMESPACE}" -o json 2>/dev/null | \
        jq -r '.items | length')

    if [[ "${netpol_count}" -gt 0 ]]; then
        log_success "${netpol_count} network policies configured"
    else
        log_warning "No network policies found - consider adding default deny"
    fi

    # Check ingress
    if kubectl get ingress -n "${NAMESPACE}" &>/dev/null; then
        local ingress_class
        ingress_class=$(kubectl get ingress -n "${NAMESPACE}" -o json | \
            jq -r '.items[0].spec.ingressClassName // "default"')
        log_success "Ingress configured with class: ${ingress_class}"

        # Check TLS
        local tls_enabled
        tls_enabled=$(kubectl get ingress -n "${NAMESPACE}" -o json | \
            jq -r '.items[0].spec.tls // empty')

        if [[ -n "${tls_enabled}" ]]; then
            log_success "TLS enabled on ingress"
        else
            log_error "TLS not enabled on ingress"
        fi
    else
        log_warning "No ingress configured"
    fi
}

check_monitoring() {
    log_info "Checking monitoring setup..."

    if should_skip "monitoring"; then
        log_warning "Skipping monitoring checks"
        return
    fi

    # Check ServiceMonitor
    if kubectl get servicemonitor -n "${NAMESPACE}" &>/dev/null; then
        log_success "ServiceMonitor configured"
    else
        log_warning "No ServiceMonitor found - Prometheus scraping may not work"
    fi

    # Check PrometheusRules
    if kubectl get prometheusrules -n "${NAMESPACE}" &>/dev/null; then
        log_success "PrometheusRules configured"
    else
        log_warning "No PrometheusRules found - alerting may not work"
    fi

    # Check Grafana dashboard
    if kubectl get configmap -n "${NAMESPACE}" -l grafana_dashboard=1 &>/dev/null; then
        log_success "Grafana dashboard ConfigMap found"
    else
        log_warning "No Grafana dashboard ConfigMap found"
    fi

    # Check HPA
    if kubectl get hpa yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        local min_replicas
        min_replicas=$(kubectl get hpa yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.minReplicas')
        local max_replicas
        max_replicas=$(kubectl get hpa yawl-engine -n "${NAMESPACE}" -o json | \
            jq -r '.spec.maxReplicas')
        log_success "HPA configured (${min_replicas}-${max_replicas} replicas)"
    else
        log_warning "No HPA configured for engine"
    fi

    # Check PDB
    if kubectl get pdb -n "${NAMESPACE}" &>/dev/null; then
        log_success "PodDisruptionBudget configured"
    else
        log_warning "No PodDisruptionBudget found"
    fi
}

check_database() {
    log_info "Checking database configuration..."

    if should_skip "database"; then
        log_warning "Skipping database checks"
        return
    fi

    # Check database secret
    if kubectl get secret yawl-postgresql -n "${NAMESPACE}" &>/dev/null; then
        log_success "Database credentials secret exists"
    else
        log_error "Database credentials secret not found"
    fi

    # Check PostgreSQL StatefulSet
    if kubectl get statefulset -n "${NAMESPACE}" -l app.kubernetes.io/name=postgresql &>/dev/null; then
        local pg_ready
        pg_ready=$(kubectl get statefulset -n "${NAMESPACE}" -l app.kubernetes.io/name=postgresql -o json | \
            jq -r '.items[0].status.readyReplicas // 0')
        local pg_replicas
        pg_replicas=$(kubectl get statefulset -n "${NAMESPACE}" -l app.kubernetes.io/name=postgresql -o json | \
            jq -r '.items[0].spec.replicas')

        if [[ "${pg_ready}" -ge "${pg_replicas}" ]]; then
            log_success "PostgreSQL ready (${pg_ready}/${pg_replicas})"
        else
            log_error "PostgreSQL not fully ready (${pg_ready}/${pg_replicas})"
        fi
    else
        log_warning "PostgreSQL StatefulSet not found (may use external database)"
    fi
}

check_secrets() {
    log_info "Checking secret management..."

    if should_skip "secrets"); then
        log_warning "Skipping secrets checks"
        return
    fi

    local required_secrets=(
        "yawl-postgresql"
        "yawl-api-keys"
        "yawl-tls-secret"
    )

    for secret in "${required_secrets[@]}"; do
        if kubectl get secret "${secret}" -n "${NAMESPACE}" &>/dev/null; then
            log_success "Secret ${secret} exists"
        else
            log_warning "Secret ${secret} not found"
        fi
    done

    # Check for hardcoded secrets in configmaps
    local configmaps_with_secrets
    configmaps_with_secrets=$(kubectl get configmaps -n "${NAMESPACE}" -o json 2>/dev/null | \
        jq -r '.items[] | select(.data | to_entries | any(.value | test("password|secret|key|token"; "i"))) | .metadata.name' || echo "")

    if [[ -n "${configmaps_with_secrets}" ]]; then
        log_error "Potential secrets found in ConfigMaps: ${configmaps_with_secrets}"
    else
        log_success "No secrets detected in ConfigMaps"
    fi
}

check_images() {
    log_info "Checking container images..."

    if should_skip "images"; then
        log_warning "Skipping image checks"
        return
    fi

    if ! kubectl get deployment yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        log_warning "Deployment not found - cannot check images"
        return
    fi

    local image
    image=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
        jq -r '.spec.template.spec.containers[0].image')

    log_info "Engine image: ${image}"

    # Check for latest tag
    if [[ "${image}" == *":latest" ]]; then
        log_error "Image uses 'latest' tag - should use specific version"
    else
        log_success "Image uses specific tag"
    fi

    # Check for digest
    if [[ "${image}" == *"@"* ]]; then
        log_success "Image uses immutable digest"
    else
        log_warning "Image does not use immutable digest"
    fi

    # Check image pull policy
    local pull_policy
    pull_policy=$(kubectl get deployment yawl-engine -n "${NAMESPACE}" -o json | \
        jq -r '.spec.template.spec.containers[0].imagePullPolicy')

    if [[ "${pull_policy}" == "Always" ]] || [[ "${pull_policy}" == "IfNotPresent" ]]; then
        log_success "Image pull policy: ${pull_policy}"
    else
        log_warning "Image pull policy: ${pull_policy}"
    fi
}

# =============================================================================
# Report Generation
# =============================================================================

generate_report() {
    local total=$((PASSED + FAILED + WARNINGS))
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    echo ""
    echo "================================================================================"
    echo "YAWL Production Readiness Report"
    echo "================================================================================"
    echo "Environment:    ${ENVIRONMENT}"
    echo "Namespace:      ${NAMESPACE}"
    echo "Timestamp:      ${timestamp}"
    echo ""
    echo "Summary:"
    echo "  Passed:   ${PASSED}"
    echo "  Failed:   ${FAILED}"
    echo "  Warnings: ${WARNINGS}"
    echo "  Total:    ${total}"
    echo ""

    if [[ ${FAILED} -gt 0 ]]; then
        echo "Status: FAILED - Critical issues must be resolved"
        echo "================================================================================"
        return 1
    elif [[ ${WARNINGS} -gt 0 ]]; then
        echo "Status: WARNING - Proceed with caution"
        echo "================================================================================"
        return 2
    else
        echo "Status: PASSED - Ready for production"
        echo "================================================================================"
        return 0
    fi
}

# =============================================================================
# Main
# =============================================================================

main() {
    parse_arguments "$@"

    echo "================================================================================"
    echo "YAWL v6.0.0 - Production Readiness Validation"
    echo "================================================================================"
    echo "Environment: ${ENVIRONMENT}"
    echo "Namespace:   ${NAMESPACE}"
    echo "================================================================================"
    echo ""

    check_cluster
    check_resources
    check_security
    check_network
    check_monitoring
    check_database
    check_secrets
    check_images

    generate_report
}

main "$@"
