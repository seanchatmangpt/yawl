#!/bin/bash
# Multi-Cloud Deployment Script for YAWL Workflow Engine
# Supports: Kubernetes (GKE, EKS, AKS, OKE, IKS), Docker Swarm, and direct deployment

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

# Default values
ENVIRONMENT="${ENVIRONMENT:-development}"
VERSION="${VERSION:-latest}"
TARGET="${TARGET:-kubernetes}"
NAMESPACE="${NAMESPACE:-yawl-${ENVIRONMENT}}"
IMAGE="${IMAGE:-yawl:${VERSION}}"
CHART_PATH="${CHART_PATH:-$PROJECT_ROOT/helm/yawl}"
MANIFEST_PATH="${MANIFEST_PATH:-$PROJECT_ROOT/k8s}"
TIMEOUT="${TIMEOUT:-600s}"
DRY_RUN="${DRY_RUN:-false}"

# Cloud provider configurations
GCP_PROJECT="${GCP_PROJECT:-}"
GCP_REGION="${GCP_REGION:-us-central1}"
GKE_CLUSTER="${GKE_CLUSTER:-yawl-${ENVIRONMENT}}"

AWS_REGION="${AWS_REGION:-us-east-1}"
EKS_CLUSTER="${EKS_CLUSTER:-yawl-${ENVIRONMENT}}"

AZURE_RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-yawl-${ENVIRONMENT}-rg}"
AKS_CLUSTER="${AKS_CLUSTER:-yawl-${ENVIRONMENT}-aks}"

OCI_COMPARTMENT="${OCI_COMPARTMENT:-}"
OKE_CLUSTER="${OKE_CLUSTER:-yawl-${ENVIRONMENT}}"

IBM_REGION="${IBM_REGION:-us-south}"
IKS_CLUSTER="${IKS_CLUSTER:-yawl-${ENVIRONMENT}}"

# Usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Multi-Cloud Deployment Script for YAWL Workflow Engine

Options:
    -e, --environment    Environment (development|staging|production) [default: development]
    -v, --version        Version to deploy [default: latest]
    -t, --target         Deployment target [default: kubernetes]
                         Options: kubernetes, gke, eks, aks, oke, iks, openshift, docker, helm
    -n, --namespace      Kubernetes namespace [default: yawl-{environment}]
    -i, --image          Docker image [default: yawl:{version}]
    --chart              Path to Helm chart [default: helm/yawl]
    --manifest           Path to Kubernetes manifests [default: k8s]
    --timeout            Deployment timeout [default: 600s]
    --dry-run            Dry run without actual deployment
    --rollback           Rollback to previous version
    -h, --help           Show this help message

Examples:
    # Deploy to GKE
    $0 -e staging -v 5.2.0 -t gke

    # Deploy to EKS with custom namespace
    $0 -e production -v 5.2.0 -t eks -n yawl-prod

    # Deploy using Helm
    $0 -e staging -v 5.2.0 -t helm

    # Dry run
    $0 -e staging -v 5.2.0 -t kubernetes --dry-run

    # Rollback
    $0 -e production -t kubernetes --rollback

EOF
    exit 0
}

# Parse arguments
parse_args() {
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
            -t|--target)
                TARGET="$2"
                shift 2
                ;;
            -n|--namespace)
                NAMESPACE="$2"
                shift 2
                ;;
            -i|--image)
                IMAGE="$2"
                shift 2
                ;;
            --chart)
                CHART_PATH="$2"
                shift 2
                ;;
            --manifest)
                MANIFEST_PATH="$2"
                shift 2
                ;;
            --timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN="true"
                shift
                ;;
            --rollback)
                ROLLBACK="true"
                shift
                ;;
            -h|--help)
                usage
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                usage
                ;;
        esac
    done
}

# Check required tools
check_requirements() {
    echo -e "${BLUE}Checking requirements...${NC}"

    case "$TARGET" in
        kubernetes|gke|eks|aks|oke|iks|helm|openshift)
            if ! command -v kubectl &> /dev/null; then
                echo -e "${RED}ERROR: kubectl is required for Kubernetes deployments${NC}"
                exit 1
            fi
            ;;
    esac

    case "$TARGET" in
        helm|gke|eks|aks|oke|iks)
            if ! command -v helm &> /dev/null; then
                echo -e "${RED}ERROR: helm is required for Helm deployments${NC}"
                exit 1
            fi
            ;;
    esac

    case "$TARGET" in
        gke)
            if ! command -v gcloud &> /dev/null; then
                echo -e "${RED}ERROR: gcloud CLI is required for GKE deployments${NC}"
                exit 1
            fi
            ;;
        eks)
            if ! command -v aws &> /dev/null; then
                echo -e "${RED}ERROR: AWS CLI is required for EKS deployments${NC}"
                exit 1
            fi
            ;;
        aks)
            if ! command -v az &> /dev/null; then
                echo -e "${RED}ERROR: Azure CLI is required for AKS deployments${NC}"
                exit 1
            fi
            ;;
        oke)
            if ! command -v oci &> /dev/null; then
                echo -e "${RED}ERROR: OCI CLI is required for OKE deployments${NC}"
                exit 1
            fi
            ;;
        docker)
            if ! command -v docker &> /dev/null; then
                echo -e "${RED}ERROR: Docker is required for Docker deployments${NC}"
                exit 1
            fi
            ;;
    esac

    echo -e "${GREEN}All requirements met${NC}"
    echo ""
}

# Print deployment info
print_info() {
    echo "=============================================="
    echo "YAWL Deployment"
    echo "=============================================="
    echo ""
    echo "Environment: $ENVIRONMENT"
    echo "Version: $VERSION"
    echo "Target: $TARGET"
    echo "Namespace: $NAMESPACE"
    echo "Image: $IMAGE"
    echo "Timeout: $TIMEOUT"
    echo "Dry Run: $DRY_RUN"
    echo ""
}

# Configure GKE access
configure_gke() {
    echo -e "${BLUE}Configuring GKE access...${NC}"

    gcloud container clusters get-credentials "$GKE_CLUSTER" \
        --region "$GCP_REGION" \
        --project "$GCP_PROJECT"

    echo -e "${GREEN}GKE access configured${NC}"
    echo ""
}

# Configure EKS access
configure_eks() {
    echo -e "${BLUE}Configuring EKS access...${NC}"

    aws eks update-kubeconfig \
        --name "$EKS_CLUSTER" \
        --region "$AWS_REGION"

    echo -e "${GREEN}EKS access configured${NC}"
    echo ""
}

# Configure AKS access
configure_aks() {
    echo -e "${BLUE}Configuring AKS access...${NC}"

    az aks get-credentials \
        --resource-group "$AZURE_RESOURCE_GROUP" \
        --name "$AKS_CLUSTER" \
        --overwrite-existing

    echo -e "${GREEN}AKS access configured${NC}"
    echo ""
}

# Configure OKE access
configure_oke() {
    echo -e "${BLUE}Configuring OKE access...${NC}"

    oci ce cluster create-kubeconfig \
        --cluster-id "$OKE_CLUSTER" \
        --file "$HOME/.kube/config" \
        --region "$OCI_REGION"

    echo -e "${GREEN}OKE access configured${NC}"
    echo ""
}

# Configure IKS access
configure_iks() {
    echo -e "${BLUE}Configuring IKS access...${NC}"

    ibmcloud ks cluster config \
        --cluster "$IKS_CLUSTER"

    echo -e "${GREEN}IKS access configured${NC}"
    echo ""
}

# Create namespace
create_namespace() {
    echo -e "${BLUE}Creating namespace: $NAMESPACE${NC}"

    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

    echo -e "${GREEN}Namespace ready${NC}"
    echo ""
}

# Create secrets
create_secrets() {
    echo -e "${BLUE}Creating secrets...${NC}"

    # Database credentials
    if [ -n "${DB_PASSWORD:-}" ]; then
        kubectl create secret generic yawl-db-credentials \
            --from-literal=password="$DB_PASSWORD" \
            --namespace="$NAMESPACE" \
            --dry-run=client -o yaml | kubectl apply -f -
    fi

    # API keys
    if [ -n "${API_KEY:-}" ]; then
        kubectl create secret generic yawl-api-keys \
            --from-literal=api-key="$API_KEY" \
            --namespace="$NAMESPACE" \
            --dry-run=client -o yaml | kubectl apply -f -
    fi

    echo -e "${GREEN}Secrets created${NC}"
    echo ""
}

# Deploy using kubectl
deploy_kubectl() {
    echo -e "${BLUE}Deploying using kubectl...${NC}"

    if [ "$DRY_RUN" = "true" ]; then
        echo "Dry run - would apply manifests from $MANIFEST_PATH"

        for manifest in "$MANIFEST_PATH"/*.yaml; do
            echo "  - $manifest"
            envsubst < "$manifest" | kubectl apply --dry-run=client -f - || true
        done
        return
    fi

    # Apply manifests
    for manifest in "$MANIFEST_PATH"/*.yaml; do
        echo "Applying: $manifest"
        envsubst < "$manifest" | kubectl apply -n "$NAMESPACE" -f -
    done

    # Wait for rollout
    kubectl rollout status deployment/yawl \
        --namespace="$NAMESPACE" \
        --timeout="$TIMEOUT"

    echo -e "${GREEN}Deployment completed${NC}"
}

# Deploy using Helm
deploy_helm() {
    echo -e "${BLUE}Deploying using Helm...${NC}"

    local helm_cmd="helm upgrade --install yawl $CHART_PATH"
    helm_cmd+=" --namespace $NAMESPACE"
    helm_cmd+=" --set image.tag=$VERSION"
    helm_cmd+=" --set environment=$ENVIRONMENT"

    # Add environment-specific values
    if [ -f "$CHART_PATH/values-$ENVIRONMENT.yaml" ]; then
        helm_cmd+=" --values $CHART_PATH/values-$ENVIRONMENT.yaml"
    fi

    if [ "$DRY_RUN" = "true" ]; then
        helm_cmd+=" --dry-run"
    fi

    helm_cmd+=" --wait --timeout $TIMEOUT"

    echo "Executing: $helm_cmd"
    eval "$helm_cmd"

    echo -e "${GREEN}Helm deployment completed${NC}"
}

# Deploy to Docker
deploy_docker() {
    echo -e "${BLUE}Deploying using Docker...${NC}"

    local container_name="yawl-$ENVIRONMENT"

    # Stop existing container
    docker stop "$container_name" 2>/dev/null || true
    docker rm "$container_name" 2>/dev/null || true

    if [ "$DRY_RUN" = "true" ]; then
        echo "Dry run - would start container with image: $IMAGE"
        return
    fi

    # Run new container
    docker run -d \
        --name "$container_name" \
        --publish 8080:8080 \
        --env "SPRING_PROFILES_ACTIVE=$ENVIRONMENT" \
        --restart unless-stopped \
        "$IMAGE"

    echo -e "${GREEN}Docker deployment completed${NC}"
    echo "Container $container_name is running"
    echo "Access at: http://localhost:8080"
}

# Deploy to OpenShift
deploy_openshift() {
    echo -e "${BLUE}Deploying to OpenShift...${NC}"

    # Check for oc command
    if ! command -v oc &> /dev/null; then
        echo -e "${RED}ERROR: oc CLI is required for OpenShift deployments${NC}"
        exit 1
    fi

    # Create project if not exists
    oc new-project "$NAMESPACE" 2>/dev/null || oc project "$NAMESPACE"

    if [ "$DRY_RUN" = "true" ]; then
        echo "Dry run - would deploy to OpenShift"
        oc new-app "$IMAGE" --name=yawl --dry-run -o yaml
        return
    fi

    # Deploy
    oc new-app "$IMAGE" \
        --name=yawl \
        --env="SPRING_PROFILES_ACTIVE=$ENVIRONMENT" || true

    # Expose service
    oc expose svc/yawl || true

    # Wait for rollout
    oc rollout status dc/yawl --timeout="$TIMEOUT"

    echo -e "${GREEN}OpenShift deployment completed${NC}"
}

# Rollback deployment
rollback_deployment() {
    echo -e "${YELLOW}Rolling back deployment...${NC}"

    case "$TARGET" in
        kubernetes|gke|eks|aks|oke|iks|helm)
            kubectl rollout undo deployment/yawl --namespace="$NAMESPACE"
            kubectl rollout status deployment/yawl --namespace="$NAMESPACE" --timeout="$TIMEOUT"
            ;;
        openshift)
            oc rollout undo dc/yawl
            oc rollout status dc/yawl --timeout="$TIMEOUT"
            ;;
        docker)
            # Docker doesn't have native rollback
            echo -e "${YELLOW}Docker does not support automatic rollback${NC}"
            echo "Re-deploy the previous version manually"
            ;;
    esac

    echo -e "${GREEN}Rollback completed${NC}"
}

# Verify deployment
verify_deployment() {
    echo -e "${BLUE}Verifying deployment...${NC}"

    case "$TARGET" in
        kubernetes|gke|eks|aks|oke|iks|helm)
            echo "Pods:"
            kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=yawl

            echo ""
            echo "Services:"
            kubectl get services -n "$NAMESPACE"

            echo ""
            echo "Ingress:"
            kubectl get ingress -n "$NAMESPACE" 2>/dev/null || true
            ;;

        openshift)
            echo "Pods:"
            oc get pods -n "$NAMESPACE"

            echo ""
            echo "Routes:"
            oc get routes -n "$NAMESPACE"
            ;;

        docker)
            docker ps --filter "name=yawl-$ENVIRONMENT"
            ;;
    esac

    echo ""
    echo -e "${GREEN}Deployment verification completed${NC}"
}

# Print access info
print_access_info() {
    echo ""
    echo "=============================================="
    echo "Access Information"
    echo "=============================================="
    echo ""

    case "$TARGET" in
        kubernetes|gke|eks|aks|oke|iks|helm)
            # Get external IP
            EXTERNAL_IP=$(kubectl get service yawl -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
            EXTERNAL_HOST=$(kubectl get service yawl -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")

            if [ -n "$EXTERNAL_IP" ]; then
                echo "External IP: $EXTERNAL_IP"
                echo "URL: http://$EXTERNAL_IP:8080"
            elif [ -n "$EXTERNAL_HOST" ]; then
                echo "External Host: $EXTERNAL_HOST"
                echo "URL: http://$EXTERNAL_HOST:8080"
            else
                echo "Service is not exposed via LoadBalancer"
                echo "Use port-forward to access:"
                echo "  kubectl port-forward -n $NAMESPACE svc/yawl 8080:8080"
            fi
            ;;

        openshift)
            ROUTE=$(oc get route yawl -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || echo "")
            if [ -n "$ROUTE" ]; then
                echo "Route: https://$ROUTE"
            fi
            ;;

        docker)
            echo "URL: http://localhost:8080"
            ;;
    esac

    echo ""
}

# Main deployment function
deploy() {
    print_info
    check_requirements

    # Configure cloud access
    case "$TARGET" in
        gke) configure_gke ;;
        eks) configure_eks ;;
        aks) configure_aks ;;
        oke) configure_oke ;;
        iks) configure_iks ;;
    esac

    # Create namespace (for Kubernetes targets)
    case "$TARGET" in
        kubernetes|gke|eks|aks|oke|iks|helm|openshift)
            create_namespace
            create_secrets
            ;;
    esac

    # Handle rollback
    if [ "${ROLLBACK:-false}" = "true" ]; then
        rollback_deployment
        verify_deployment
        print_access_info
        return
    fi

    # Deploy
    case "$TARGET" in
        kubernetes)
            deploy_kubectl
            ;;
        gke|eks|aks|oke|iks|helm)
            deploy_helm
            ;;
        openshift)
            deploy_openshift
            ;;
        docker)
            deploy_docker
            ;;
        *)
            echo -e "${RED}Unknown target: $TARGET${NC}"
            exit 1
            ;;
    esac

    verify_deployment
    print_access_info

    echo -e "${GREEN}Deployment completed successfully!${NC}"
}

# Run
parse_args "$@"
deploy
