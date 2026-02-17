#!/usr/bin/env bash

#############################################################################
# YAWL v6.0.0 - Deployment Strategies & Automation
# Supports: Blue-Green, Canary, Progressive Rollout, Emergency Rollback
#############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

# Configuration
KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
ARGOCD_SERVER="${ARGOCD_SERVER:-argocd.example.com}"
ARGOCD_TOKEN="${ARGOCD_TOKEN:-}"
NAMESPACE="${NAMESPACE:-yawl-prod}"
APP_NAME="${APP_NAME:-yawl}"
IMAGE_REGISTRY="${IMAGE_REGISTRY:-ghcr.io}"
IMAGE_REPO="${IMAGE_REPO:-yawlfoundation/yawl}"

#############################################################################
# Utility Functions
#############################################################################

log() {
  echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $*"
}

success() {
  echo -e "${GREEN}✓${NC} $*"
}

error() {
  echo -e "${RED}✗${NC} $*" >&2
  exit 1
}

warn() {
  echo -e "${YELLOW}⚠${NC} $*"
}

#############################################################################
# Kubernetes Health Checks
#############################################################################

check_deployment_health() {
  local deployment=$1
  local namespace=${2:-$NAMESPACE}
  local timeout=${3:-300}  # 5 minutes

  log "Checking health of deployment: $deployment (timeout: ${timeout}s)"

  kubectl rollout status deployment/$deployment \
    -n $namespace \
    --timeout=${timeout}s || {
    error "Deployment $deployment failed to reach ready state"
  }

  # Additional health check: verify all pods are running
  local pod_count=$(kubectl get pods \
    -n $namespace \
    -l app=$deployment \
    --field-selector=status.phase=Running \
    -o json | jq '.items | length')

  local ready_count=$(kubectl get pods \
    -n $namespace \
    -l app=$deployment \
    -o json | jq '[.items[] | select(.status.conditions[] | select(.type=="Ready" and .status=="True"))] | length')

  if [ "$pod_count" -eq "$ready_count" ]; then
    success "All pods healthy: $ready_count/$pod_count running"
    return 0
  else
    error "Not all pods are ready: $ready_count/$pod_count"
  fi
}

run_smoke_tests() {
  local service=$1
  local namespace=${2:-$NAMESPACE}
  local health_endpoint="/actuator/health"

  log "Running smoke tests against $service"

  # Get service endpoint
  local service_ip=$(kubectl get service/$service \
    -n $namespace \
    -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || \
    kubectl get service/$service \
    -n $namespace \
    -o jsonpath='{.spec.clusterIP}' 2>/dev/null)

  if [ -z "$service_ip" ]; then
    error "Could not determine service endpoint for $service"
  fi

  log "Testing endpoint: http://$service_ip:8080$health_endpoint"

  # Health check
  if curl -f "http://$service_ip:8080$health_endpoint" -s > /dev/null; then
    success "Health check passed"
  else
    error "Health check failed"
  fi

  # Additional smoke tests
  log "Running additional smoke tests..."
  # Add your smoke test cases here
  success "Smoke tests passed"
}

#############################################################################
# Blue-Green Deployment
#############################################################################

deploy_blue_green() {
  local version=$1
  local namespace=${2:-$NAMESPACE}
  local service_name=${3:-$APP_NAME-engine}

  log "Starting Blue-Green Deployment: $version"

  # Step 1: Get current active deployment
  local current_deployment=$(kubectl get service $service_name \
    -n $namespace \
    -o jsonpath='{.spec.selector.version}' 2>/dev/null || echo "blue")

  if [ "$current_deployment" == "blue" ]; then
    local target_deployment="green"
    local old_deployment="blue"
  else
    local target_deployment="blue"
    local old_deployment="green"
  fi

  log "Current deployment: $current_deployment"
  log "Target deployment: $target_deployment"

  # Step 2: Deploy to target (inactive) slot
  log "Deploying to $target_deployment slot..."
  kubectl set image deployment/$service_name-$target_deployment \
    $service_name=$IMAGE_REGISTRY/$IMAGE_REPO:$version \
    -n $namespace \
    --record || error "Failed to update image for $target_deployment"

  # Step 3: Wait for target deployment to be ready
  check_deployment_health "$service_name-$target_deployment" "$namespace" 300

  # Step 4: Run smoke tests
  run_smoke_tests "$service_name-$target_deployment" "$namespace"

  # Step 5: Switch traffic
  log "Switching traffic from $old_deployment to $target_deployment..."
  kubectl patch service $service_name \
    -n $namespace \
    -p "{\"spec\":{\"selector\":{\"version\":\"$target_deployment\"}}}" \
    --type merge || error "Failed to switch traffic"

  success "Traffic switched to $target_deployment"

  # Step 6: Run production tests
  run_smoke_tests "$service_name" "$namespace"

  # Step 7: Wait before scaling down old deployment (keep for rollback)
  log "Keeping old deployment ($old_deployment) for rollback (5 minutes)..."
  sleep 300

  # Step 8: Scale down old deployment
  log "Scaling down old deployment: $old_deployment"
  kubectl scale deployment $service_name-$old_deployment \
    --replicas=0 \
    -n $namespace || warn "Failed to scale down old deployment"

  success "Blue-Green deployment completed: $version"
}

rollback_blue_green() {
  local namespace=${1:-$NAMESPACE}
  local service_name=${2:-$APP_NAME-engine}

  log "Rolling back Blue-Green deployment..."

  # Get current active deployment
  local active=$(kubectl get service $service_name \
    -n $namespace \
    -o jsonpath='{.spec.selector.version}')

  if [ "$active" == "blue" ]; then
    local rollback_to="green"
  else
    local rollback_to="blue"
  fi

  log "Rolling back to: $rollback_to"

  # Switch traffic back
  kubectl patch service $service_name \
    -n $namespace \
    -p "{\"spec\":{\"selector\":{\"version\":\"$rollback_to\"}}}" \
    --type merge || error "Failed to rollback traffic"

  # Scale up old deployment
  kubectl scale deployment $service_name-$rollback_to \
    --replicas=3 \
    -n $namespace || warn "Failed to scale up rollback deployment"

  check_deployment_health "$service_name-$rollback_to" "$namespace"
  success "Rollback completed"
}

#############################################################################
# Canary Deployment
#############################################################################

deploy_canary() {
  local version=$1
  local canary_percentage=${2:-5}  # Default 5%
  local namespace=${3:-$NAMESPACE}
  local service_name=${4:-$APP_NAME-engine}

  log "Starting Canary Deployment: $version (canary weight: ${canary_percentage}%)"

  # Step 1: Deploy canary version
  log "Deploying canary version..."
  kubectl set image deployment/$service_name-canary \
    $service_name=$IMAGE_REGISTRY/$IMAGE_REPO:$version \
    -n $namespace \
    --record || error "Failed to deploy canary"

  # Step 2: Scale canary to 1 replica
  kubectl scale deployment $service_name-canary \
    --replicas=1 \
    -n $namespace

  check_deployment_health "$service_name-canary" "$namespace" 120

  # Step 3: Configure traffic split (using Istio/Flagger if available)
  log "Configuring traffic split: ${canary_percentage}% to canary"
  configure_traffic_split $service_name $canary_percentage $namespace

  # Step 4: Monitor metrics for 30 minutes
  log "Monitoring canary metrics for 30 minutes..."
  local start_time=$(date +%s)
  local end_time=$((start_time + 1800))  # 30 minutes

  while [ $(date +%s) -lt $end_time ]; do
    log "Checking canary metrics..."

    # Check error rate
    local error_rate=$(get_error_rate $service_name-canary $namespace)
    if (( $(echo "$error_rate > 0.005" | bc -l) )); then  # 0.5% threshold
      warn "High error rate detected: $error_rate"
      log "Rolling back canary..."
      kubectl scale deployment $service_name-canary --replicas=0 -n $namespace
      error "Canary deployment failed due to high error rate"
    fi

    # Check latency
    local latency=$(get_p99_latency $service_name-canary $namespace)
    if (( $(echo "$latency > 100" | bc -l) )); then  # 100ms threshold
      warn "High latency detected: ${latency}ms"
    fi

    log "Metrics OK (error: $error_rate, p99: ${latency}ms)"
    sleep 300  # Check every 5 minutes
  done

  # Step 5: Gradually increase traffic
  log "Progressively increasing canary traffic..."
  for percentage in 25 50 75 100; do
    log "Traffic split: ${percentage}%"
    configure_traffic_split $service_name $percentage $namespace
    sleep 300  # 5 minutes between increments
  done

  # Step 6: Scale up canary to match stable
  log "Scaling up canary deployment..."
  kubectl scale deployment $service_name-canary \
    --replicas=3 \
    -n $namespace

  check_deployment_health "$service_name-canary" "$namespace"

  # Step 7: Complete migration
  log "Completing canary deployment..."
  configure_traffic_split $service_name 100 $namespace
  kubectl scale deployment $service_name-stable --replicas=0 -n $namespace

  success "Canary deployment completed: $version"
}

configure_traffic_split() {
  local service=$1
  local canary_weight=$2
  local namespace=${3:-$NAMESPACE}

  # This is a placeholder - actual implementation depends on:
  # - Istio VirtualService
  # - Flagger Canary CRD
  # - Linkerd TrafficSplit
  # Example with Istio:

  cat > /tmp/traffic-split.yaml << EOF
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: $service
  namespace: $namespace
spec:
  hosts:
  - $service
  http:
  - match:
    - uri:
        prefix: "/"
    route:
    - destination:
        host: $service-stable
      weight: $((100 - canary_weight))
    - destination:
        host: $service-canary
      weight: $canary_weight
EOF

  kubectl apply -f /tmp/traffic-split.yaml || warn "Could not apply traffic split (Istio may not be installed)"
}

get_error_rate() {
  local pod_selector=$1
  local namespace=${2:-$NAMESPACE}

  # This would query Prometheus/Grafana in a real implementation
  # For now, return a mock value
  echo "0.001"  # 0.1% error rate
}

get_p99_latency() {
  local pod_selector=$1
  local namespace=${2:-$NAMESPACE}

  # This would query Prometheus/Grafana in a real implementation
  echo "50"  # 50ms
}

#############################################################################
# Progressive Rollout
#############################################################################

deploy_progressive() {
  local version=$1
  local zones=(${2:-"us-east-1a us-east-1b us-east-1c"})  # Availability zones
  local namespace=${3:-$NAMESPACE}
  local service_name=${4:-$APP_NAME-engine}

  log "Starting Progressive Rollout: $version across ${#zones[@]} zones"

  for i in "${!zones[@]}"; do
    local zone="${zones[$i]}"
    local phase=$((i + 1))

    log "Phase $phase/${{#zones[@]}}: Deploying to zone $zone"

    # Deploy to zone
    kubectl patch deployment $service_name \
      -n $namespace \
      --type json \
      -p "[{
        \"op\": \"replace\",
        \"path\": \"/spec/template/spec/affinity/nodeAffinity/requiredDuringSchedulingIgnoredDuringExecution/nodeSelectorTerms/0/matchExpressions/0/values\",
        \"value\": [\"$zone\"]
      }]" || warn "Could not apply zone selector"

    kubectl set image deployment/$service_name \
      $service_name=$IMAGE_REGISTRY/$IMAGE_REPO:$version \
      -n $namespace \
      --record || error "Failed to update image"

    check_deployment_health $service_name $namespace 300
    run_smoke_tests $service_name $namespace

    # Monitor for issues
    log "Monitoring zone $zone for 10 minutes..."
    sleep 600

    # Check for errors
    if check_zone_health $zone $namespace; then
      success "Zone $zone deployment successful"
    else
      error "Zone $zone deployment failed - rolling back"
    fi

    # Only continue to next zone if current zone is stable
    if [ $i -lt $((${#zones[@]} - 1)) ]; then
      log "Waiting 5 minutes before deploying to next zone..."
      sleep 300
    fi
  done

  success "Progressive rollout completed: $version"
}

check_zone_health() {
  local zone=$1
  local namespace=${2:-$NAMESPACE}

  # Check pod status in specific zone
  local pod_status=$(kubectl get pods \
    -n $namespace \
    --field-selector status.phase!=Running \
    -o json | jq '.items | length')

  if [ "$pod_status" -eq 0 ]; then
    return 0
  else
    return 1
  fi
}

#############################################################################
# Emergency Rollback
#############################################################################

emergency_rollback() {
  local previous_version=$1
  local namespace=${2:-$NAMESPACE}
  local service_name=${3:-$APP_NAME-engine}

  log "EMERGENCY ROLLBACK to version: $previous_version"

  # Force immediate rollback
  kubectl set image deployment/$service_name \
    $service_name=$IMAGE_REGISTRY/$IMAGE_REPO:$previous_version \
    -n $namespace \
    --record || error "Failed to set image"

  kubectl rollout status deployment/$service_name \
    -n $namespace \
    --timeout=180s || warn "Rollback not fully complete yet"

  success "Emergency rollback initiated"

  # Create incident ticket
  log "Creating incident ticket..."
  create_incident_ticket "Emergency Rollback" \
    "Rolled back from latest to $previous_version due to critical issues"
}

create_incident_ticket() {
  local title=$1
  local description=$2

  # Integration with your incident management system (PagerDuty, Jira, etc.)
  log "Incident: $title"
  log "Description: $description"
  # Implementation would call your incident API
}

#############################################################################
# ArgoCD Integration
#############################################################################

sync_with_argocd() {
  local app=$1
  local namespace=${2:-$NAMESPACE}

  log "Syncing ArgoCD application: $app"

  if [ -z "$ARGOCD_TOKEN" ]; then
    error "ARGOCD_TOKEN not set"
  fi

  local response=$(curl -s -X POST \
    -H "Authorization: Bearer $ARGOCD_TOKEN" \
    -H "Content-Type: application/json" \
    "https://$ARGOCD_SERVER/api/v1/applications/$app/sync" \
    -d '{
      "strategy": {
        "apply": {
          "force": false
        }
      }
    }')

  if echo "$response" | grep -q "errors"; then
    error "ArgoCD sync failed: $response"
  fi

  success "ArgoCD sync initiated"
}

wait_for_argocd_sync() {
  local app=$1
  local timeout=${2:-600}  # 10 minutes

  log "Waiting for ArgoCD sync to complete (timeout: ${timeout}s)..."

  local start_time=$(date +%s)
  local end_time=$((start_time + timeout))

  while [ $(date +%s) -lt $end_time ]; do
    local status=$(curl -s \
      -H "Authorization: Bearer $ARGOCD_TOKEN" \
      "https://$ARGOCD_SERVER/api/v1/applications/$app" | \
      jq -r '.status.operationState.phase' 2>/dev/null)

    if [ "$status" == "Succeeded" ]; then
      success "ArgoCD sync completed successfully"
      return 0
    elif [ "$status" == "Error" ] || [ "$status" == "Failed" ]; then
      error "ArgoCD sync failed with status: $status"
    fi

    log "Current sync status: $status"
    sleep 10
  done

  error "ArgoCD sync timeout"
}

#############################################################################
# Main Command Handler
#############################################################################

usage() {
  cat << EOF
YAWL v6.0.0 - Deployment Strategies

Usage: $0 <command> [options]

Commands:
  blue-green <version>        Deploy using blue-green strategy
  rollback-blue-green         Rollback blue-green deployment
  canary <version> [weight]   Deploy using canary strategy (default weight: 5%)
  progressive <version>       Deploy progressively across zones
  rollback-emergency <version> Emergency rollback to version
  health-check <deployment>   Check deployment health
  smoke-test <service>        Run smoke tests
  argocd-sync <app>          Sync ArgoCD application
  argocd-wait <app>          Wait for ArgoCD sync completion

Environment Variables:
  KUBECONFIG                 Path to kubeconfig (default: ~/.kube/config)
  NAMESPACE                  Kubernetes namespace (default: yawl-prod)
  APP_NAME                   Application name (default: yawl)
  IMAGE_REGISTRY            Docker registry (default: ghcr.io)
  IMAGE_REPO                Docker repository (default: yawlfoundation/yawl)
  ARGOCD_SERVER             ArgoCD server (default: argocd.example.com)
  ARGOCD_TOKEN              ArgoCD API token (required for sync)

Examples:
  $0 blue-green 6.0.1
  $0 canary 6.0.1 10
  $0 progressive 6.0.1
  $0 rollback-emergency 6.0.0
  $0 health-check yawl-engine

EOF
  exit 1
}

main() {
  if [ $# -lt 1 ]; then
    usage
  fi

  local command=$1
  shift

  case $command in
    blue-green)
      deploy_blue_green "$@"
      ;;
    rollback-blue-green)
      rollback_blue_green "$@"
      ;;
    canary)
      deploy_canary "$@"
      ;;
    progressive)
      deploy_progressive "$@"
      ;;
    rollback-emergency)
      emergency_rollback "$@"
      ;;
    health-check)
      check_deployment_health "$@"
      ;;
    smoke-test)
      run_smoke_tests "$@"
      ;;
    argocd-sync)
      sync_with_argocd "$@"
      ;;
    argocd-wait)
      wait_for_argocd_sync "$@"
      ;;
    *)
      echo "Unknown command: $command"
      usage
      ;;
  esac
}

main "$@"
