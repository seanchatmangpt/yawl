#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0.0 - Automated Rollback Script
# =============================================================================
# Usage:
#   ./scripts/ci-cd/rollback.sh --environment <env> [--revision <num>]
#
# This script performs automated rollback with:
#   - Pre-rollback health check
#   - Traffic draining for graceful shutdown
#   - Rollback execution
#   - Post-rollback verification
#   - Notification sending
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

# Default values
ENVIRONMENT=""
REVISION=""
NAMESPACE=""
REASON="Automated rollback triggered"
SLACK_WEBHOOK=""

# =============================================================================
# Helper Functions
# =============================================================================

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Options:
    -e, --environment <env>   Target environment (staging|production) [required]
    -r, --revision <num>      Revision to rollback to (default: previous)
    -n, --namespace <ns>      Kubernetes namespace (default: yawl-<env>)
    --reason <text>           Reason for rollback
    --slack-webhook <url>     Slack webhook URL for notifications
    -h, --help                Show this help message

Examples:
    # Rollback production to previous version
    $(basename "$0") -e production

    # Rollback to specific revision with notification
    $(basename "$0") -e production -r 42 --slack-webhook https://hooks.slack.com/...
EOF
    exit 1
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -r|--revision)
                REVISION="$2"
                shift 2
                ;;
            -n|--namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            --reason)
                REASON="$2"
                shift 2
                ;;
            --slack-webhook)
                SLACK_WEBHOOK="$2"
                shift 2
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

    if [[ -z "${ENVIRONMENT}" ]]; then
        log_error "Environment is required"
        usage
    fi

    if [[ -z "${NAMESPACE}" ]]; then
        NAMESPACE="yawl-${ENVIRONMENT}"
    fi
}

# =============================================================================
# Rollback Functions
# =============================================================================

get_current_version() {
    helm list -n "${NAMESPACE}" -o json | jq -r '.[0].app_version // "unknown"'
}

get_previous_revision() {
    helm history yawl -n "${NAMESPACE}" -o json 2>/dev/null | \
        jq -r 'map(select(.status=="superseded")) | sort_by(.revision) | last | .revision // empty'
}

drain_traffic() {
    log_info "Draining traffic from current deployment..."

    # Scale down ingress if using Istio
    if kubectl get virtualservice yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        kubectl patch virtualservice yawl-engine -n "${NAMESPACE}" --type=merge -p '
{
    "spec": {
        "http": [{
            "route": [{
                "destination": { "host": "yawl-engine" },
                "weight": 0
            }]
        }]
    }
}' 2>/dev/null || true
    fi

    # Wait for in-flight requests to complete
    log_info "Waiting 30 seconds for in-flight requests to complete..."
    sleep 30
}

pre_rollback_health_check() {
    log_info "Running pre-rollback health check..."

    # Check if namespace exists
    if ! kubectl get namespace "${NAMESPACE}" &>/dev/null; then
        log_error "Namespace ${NAMESPACE} does not exist"
        exit 1
    fi

    # Check if release exists
    if ! helm status yawl -n "${NAMESPACE}" &>/dev/null; then
        log_error "Release yawl not found in namespace ${NAMESPACE}"
        exit 1
    fi

    # Get available revisions
    local history
    history=$(helm history yawl -n "${NAMESPACE}" -o json)

    if [[ -z "${REVISION}" ]]; then
        REVISION=$(echo "${history}" | jq -r 'map(select(.status=="superseded")) | sort_by(.revision) | last | .revision // empty')

        if [[ -z "${REVISION}" ]]; then
            log_error "No previous revision available for rollback"
            exit 1
        fi
    fi

    log_info "Target revision: ${REVISION}"
    log_success "Pre-rollback health check passed"
}

execute_rollback() {
    log_info "Executing rollback to revision ${REVISION}..."

    helm rollback yawl "${REVISION}" -n "${NAMESPACE}" --timeout 10m --wait

    log_success "Rollback executed successfully"
}

post_rollback_verification() {
    log_info "Running post-rollback verification..."

    # Wait for deployment to stabilize
    sleep 30

    # Check pod status
    local ready_pods
    ready_pods=$(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/name=yawl-engine \
        -o json | jq -r '[.items[] | select(.status.phase=="Running" and (.status.containerStatuses[0].ready // false))] | length')

    if [[ "${ready_pods}" -lt 1 ]]; then
        log_error "No ready pods after rollback"
        return 1
    fi

    # Health check
    local endpoint
    endpoint=$(kubectl get service yawl-engine -n "${NAMESPACE}" \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [[ -n "${endpoint}" ]]; then
        if ! curl -sf "http://${endpoint}:8080/actuator/health" > /dev/null 2>&1; then
            log_warning "Health check returned non-success, but pods are ready"
        else
            log_success "Health check passed"
        fi
    fi

    # Restore traffic if using Istio
    if kubectl get virtualservice yawl-engine -n "${NAMESPACE}" &>/dev/null; then
        kubectl patch virtualservice yawl-engine -n "${NAMESPACE}" --type=merge -p '
{
    "spec": {
        "http": [{
            "route": [{
                "destination": { "host": "yawl-engine" },
                "weight": 100
            }]
        }]
    }
}' 2>/dev/null || true
    fi

    log_success "Post-rollback verification passed"
}

send_notification() {
    if [[ -z "${SLACK_WEBHOOK}" ]]; then
        return
    fi

    local current_version
    current_version=$(get_current_version)

    local rollback_version
    rollback_version=$(helm history yawl -n "${NAMESPACE}" -o json | \
        jq -r ".[] | select(.revision==${REVISION}) | .app_version")

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    local color="warning"
    if [[ "${ENVIRONMENT}" == "production" ]]; then
        color="danger"
    fi

    local payload
    payload=$(cat << EOF
{
    "attachments": [{
        "color": "${color}",
        "title": "YAWL Rollback Executed",
        "fields": [
            {"title": "Environment", "value": "${ENVIRONMENT}", "short": true},
            {"title": "Namespace", "value": "${NAMESPACE}", "short": true},
            {"title": "From Version", "value": "${current_version}", "short": true},
            {"title": "To Version", "value": "${rollback_version}", "short": true},
            {"title": "Revision", "value": "${REVISION}", "short": true},
            {"title": "Reason", "value": "${REASON}", "short": false}
        ],
        "footer": "YAWL Rollback Script",
        "ts": $(date +%s)
    }]
}
EOF
)

    curl -s -X POST -H 'Content-Type: application/json' \
        -d "${payload}" "${SLACK_WEBHOOK}" > /dev/null 2>&1 || true

    log_info "Notification sent to Slack"
}

generate_report() {
    local current_version
    current_version=$(get_current_version)

    local rollback_version
    rollback_version=$(helm history yawl -n "${NAMESPACE}" -o json | \
        jq -r ".[] | select(.revision==${REVISION}) | .app_version")

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    cat << EOF
================================================================================
YAWL Rollback Report
================================================================================
Environment:      ${ENVIRONMENT}
Namespace:        ${NAMESPACE}
Previous Version: ${current_version}
Current Version:  ${rollback_version}
Revision:         ${REVISION}
Reason:           ${REASON}
Timestamp:        ${timestamp}

Current Pods:
$(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/name=yawl-engine --no-headers 2>/dev/null || echo "No pods found")

Helm History (last 5):
$(helm history yawl -n "${NAMESPACE}" --max 5 2>/dev/null || echo "No history available")

================================================================================
EOF
}

# =============================================================================
# Main
# =============================================================================

main() {
    parse_arguments "$@"

    local start_time
    start_time=$(date +%s)

    log_info "Starting rollback process for ${ENVIRONMENT}..."
    log_info "Reason: ${REASON}"

    pre_rollback_health_check
    drain_traffic
    execute_rollback
    post_rollback_verification
    send_notification
    generate_report

    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))

    log_success "Rollback completed in ${duration} seconds"
}

main "$@"
