#!/bin/bash
# YAWL Deployment Automation Script
# Supports staging and production deployments with rollback
# Version: 5.2

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $*"; }

# Configuration
ENVIRONMENT="${1:-staging}"
IMAGE_TAG="${2:-latest}"
DEPLOYMENT_STRATEGY="${3:-blue-green}"

# ============================================
# Pre-deployment Checks
# ============================================
pre_deployment_checks() {
    log_step "Running pre-deployment checks..."

    # Check required tools
    local required_tools=("kubectl" "helm" "docker")
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "$tool is required but not installed"
            return 1
        fi
        log_info "✓ $tool installed"
    done

    # Check cluster connectivity
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        return 1
    fi
    log_info "✓ Kubernetes cluster accessible"

    # Verify image exists
    local image_name="yawl-engine:${IMAGE_TAG}"
    if ! docker pull "$image_name" &> /dev/null; then
        log_warn "Image $image_name not found in local cache"
    else
        log_info "✓ Docker image verified: $image_name"
    fi

    log_info "Pre-deployment checks passed"
}

# ============================================
# Blue-Green Deployment
# ============================================
deploy_blue_green() {
    local namespace="yawl-${ENVIRONMENT}"
    local current_color=$(kubectl get service yawl-engine -n "$namespace" -o jsonpath='{.spec.selector.color}' 2>/dev/null || echo "blue")
    local new_color="green"

    if [ "$current_color" = "green" ]; then
        new_color="blue"
    fi

    log_step "Blue-Green Deployment: Deploying to $new_color environment..."

    # Deploy new version
    kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine-${new_color}
  namespace: ${namespace}
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
      color: ${new_color}
  template:
    metadata:
      labels:
        app: yawl-engine
        color: ${new_color}
        version: ${IMAGE_TAG}
    spec:
      containers:
      - name: yawl-engine
        image: yawl-engine:${IMAGE_TAG}
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "${ENVIRONMENT}"
        - name: ZHIPU_API_KEY
          valueFrom:
            secretKeyRef:
              name: yawl-secrets
              key: zhipu-api-key
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
EOF

    log_info "Waiting for new deployment to be ready..."
    kubectl rollout status deployment/yawl-engine-${new_color} -n "$namespace" --timeout=10m

    # Run smoke tests on new deployment
    log_step "Running smoke tests on $new_color environment..."
    if ! run_smoke_tests "$namespace" "$new_color"; then
        log_error "Smoke tests failed on $new_color environment"
        return 1
    fi

    # Switch traffic to new deployment
    log_step "Switching traffic from $current_color to $new_color..."
    kubectl patch service yawl-engine -n "$namespace" -p "{\"spec\":{\"selector\":{\"color\":\"${new_color}\"}}}"

    log_info "Traffic switched to $new_color environment"

    # Wait and verify
    sleep 30
    if ! run_smoke_tests "$namespace" "$new_color"; then
        log_error "Post-switch smoke tests failed - initiating rollback"
        kubectl patch service yawl-engine -n "$namespace" -p "{\"spec\":{\"selector\":{\"color\":\"${current_color}\"}}}"
        return 1
    fi

    # Scale down old deployment
    log_step "Scaling down old $current_color environment..."
    kubectl scale deployment yawl-engine-${current_color} -n "$namespace" --replicas=0

    log_info "✓ Blue-Green deployment completed successfully"
}

# ============================================
# Rolling Update Deployment
# ============================================
deploy_rolling() {
    local namespace="yawl-${ENVIRONMENT}"

    log_step "Rolling Update: Deploying version ${IMAGE_TAG}..."

    kubectl set image deployment/yawl-engine \
        yawl-engine=yawl-engine:${IMAGE_TAG} \
        -n "$namespace"

    log_info "Waiting for rolling update to complete..."
    kubectl rollout status deployment/yawl-engine -n "$namespace" --timeout=10m

    if [ $? -eq 0 ]; then
        log_info "✓ Rolling update completed successfully"
    else
        log_error "Rolling update failed - initiating rollback"
        kubectl rollout undo deployment/yawl-engine -n "$namespace"
        return 1
    fi
}

# ============================================
# Canary Deployment
# ============================================
deploy_canary() {
    local namespace="yawl-${ENVIRONMENT}"
    local canary_replicas=1
    local stable_replicas=3

    log_step "Canary Deployment: Starting with 1 canary replica..."

    # Create canary deployment
    kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine-canary
  namespace: ${namespace}
spec:
  replicas: ${canary_replicas}
  selector:
    matchLabels:
      app: yawl-engine
      track: canary
  template:
    metadata:
      labels:
        app: yawl-engine
        track: canary
        version: ${IMAGE_TAG}
    spec:
      containers:
      - name: yawl-engine
        image: yawl-engine:${IMAGE_TAG}
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "${ENVIRONMENT}"
EOF

    kubectl rollout status deployment/yawl-engine-canary -n "$namespace" --timeout=5m

    # Monitor canary for 5 minutes
    log_step "Monitoring canary deployment for 5 minutes..."
    sleep 300

    # Check canary health
    if ! check_deployment_health "$namespace" "yawl-engine-canary"; then
        log_error "Canary deployment unhealthy - rolling back"
        kubectl delete deployment yawl-engine-canary -n "$namespace"
        return 1
    fi

    # Promote canary
    log_step "Promoting canary to full deployment..."
    kubectl set image deployment/yawl-engine \
        yawl-engine=yawl-engine:${IMAGE_TAG} \
        -n "$namespace"

    kubectl delete deployment yawl-engine-canary -n "$namespace"

    log_info "✓ Canary deployment promoted successfully"
}

# ============================================
# Smoke Tests
# ============================================
run_smoke_tests() {
    local namespace="$1"
    local color="${2:-}"

    log_info "Running smoke tests..."

    # Get service endpoint
    local service_url=$(kubectl get service yawl-engine -n "$namespace" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "localhost")

    # Test 1: Health endpoint
    if ! curl -sf "http://${service_url}:8080/actuator/health" > /dev/null; then
        log_error "Health check failed"
        return 1
    fi
    log_info "✓ Health check passed"

    # Test 2: Engine interface
    if ! curl -sf "http://${service_url}:8080/yawl/ib" > /dev/null; then
        log_error "Engine interface check failed"
        return 1
    fi
    log_info "✓ Engine interface check passed"

    # Test 3: MCP endpoint
    curl -sf "http://${service_url}:8080/mcp/v1/capabilities" > /dev/null || log_warn "MCP endpoint not available (optional)"

    # Test 4: A2A endpoint
    curl -sf "http://${service_url}:8080/a2a/agents" > /dev/null || log_warn "A2A endpoint not available (optional)"

    log_info "✓ All smoke tests passed"
    return 0
}

# ============================================
# Health Check
# ============================================
check_deployment_health() {
    local namespace="$1"
    local deployment="$2"

    local ready_replicas=$(kubectl get deployment "$deployment" -n "$namespace" -o jsonpath='{.status.readyReplicas}')
    local desired_replicas=$(kubectl get deployment "$deployment" -n "$namespace" -o jsonpath='{.spec.replicas}')

    if [ "$ready_replicas" = "$desired_replicas" ]; then
        return 0
    else
        return 1
    fi
}

# ============================================
# Rollback
# ============================================
rollback_deployment() {
    local namespace="yawl-${ENVIRONMENT}"

    log_warn "Initiating rollback..."

    kubectl rollout undo deployment/yawl-engine -n "$namespace"
    kubectl rollout status deployment/yawl-engine -n "$namespace" --timeout=5m

    if [ $? -eq 0 ]; then
        log_info "✓ Rollback completed successfully"
    else
        log_error "Rollback failed - manual intervention required"
        return 1
    fi
}

# ============================================
# Post-deployment Verification
# ============================================
post_deployment_verification() {
    local namespace="yawl-${ENVIRONMENT}"

    log_step "Post-deployment verification..."

    # Check pod status
    local pod_count=$(kubectl get pods -n "$namespace" -l app=yawl-engine --field-selector=status.phase=Running --no-headers | wc -l)
    if [ "$pod_count" -lt 1 ]; then
        log_error "No running pods found"
        return 1
    fi
    log_info "✓ $pod_count pod(s) running"

    # Check service
    if ! kubectl get service yawl-engine -n "$namespace" &> /dev/null; then
        log_error "Service not found"
        return 1
    fi
    log_info "✓ Service exists"

    # Run smoke tests
    if ! run_smoke_tests "$namespace"; then
        log_error "Post-deployment smoke tests failed"
        return 1
    fi

    log_info "✓ Post-deployment verification passed"
}

# ============================================
# Notification
# ============================================
send_notification() {
    local status="$1"
    local message="$2"

    if [ -n "${SLACK_WEBHOOK_URL:-}" ]; then
        local color="good"
        if [ "$status" != "success" ]; then
            color="danger"
        fi

        curl -X POST "$SLACK_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{
                \"attachments\": [{
                    \"color\": \"$color\",
                    \"title\": \"YAWL Deployment - $status\",
                    \"text\": \"$message\",
                    \"fields\": [
                        {\"title\": \"Environment\", \"value\": \"$ENVIRONMENT\", \"short\": true},
                        {\"title\": \"Image Tag\", \"value\": \"$IMAGE_TAG\", \"short\": true},
                        {\"title\": \"Strategy\", \"value\": \"$DEPLOYMENT_STRATEGY\", \"short\": true}
                    ],
                    \"footer\": \"YAWL CI/CD\",
                    \"ts\": $(date +%s)
                }]
            }" &> /dev/null
    fi
}

# ============================================
# Main
# ============================================
main() {
    log_info "═══════════════════════════════════════════"
    log_info "YAWL Deployment Automation"
    log_info "═══════════════════════════════════════════"
    log_info "Environment: $ENVIRONMENT"
    log_info "Image Tag: $IMAGE_TAG"
    log_info "Strategy: $DEPLOYMENT_STRATEGY"
    log_info ""

    # Pre-deployment
    if ! pre_deployment_checks; then
        send_notification "failed" "Pre-deployment checks failed"
        exit 1
    fi

    # Execute deployment strategy
    case "$DEPLOYMENT_STRATEGY" in
        blue-green)
            if deploy_blue_green; then
                log_info "✓ Blue-green deployment successful"
            else
                log_error "Blue-green deployment failed"
                send_notification "failed" "Blue-green deployment failed"
                exit 1
            fi
            ;;
        rolling)
            if deploy_rolling; then
                log_info "✓ Rolling update successful"
            else
                log_error "Rolling update failed"
                send_notification "failed" "Rolling update failed"
                exit 1
            fi
            ;;
        canary)
            if deploy_canary; then
                log_info "✓ Canary deployment successful"
            else
                log_error "Canary deployment failed"
                send_notification "failed" "Canary deployment failed"
                exit 1
            fi
            ;;
        *)
            log_error "Unknown deployment strategy: $DEPLOYMENT_STRATEGY"
            exit 1
            ;;
    esac

    # Post-deployment verification
    if ! post_deployment_verification; then
        log_error "Post-deployment verification failed - initiating rollback"
        rollback_deployment
        send_notification "failed" "Post-deployment verification failed - rolled back"
        exit 1
    fi

    log_info ""
    log_info "═══════════════════════════════════════════"
    log_info "✓ Deployment completed successfully!"
    log_info "═══════════════════════════════════════════"

    send_notification "success" "Deployment completed successfully"
}

main "$@"
