#!/bin/bash
# YAWL Timoni Deployment Script
# Usage: ./deploy.sh [environment] [action]
# Example: ./deploy.sh production apply

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENT="${1:-production}"
ACTION="${2:-apply}"
MODULE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE=""

# Validate environment
case "$ENVIRONMENT" in
    production|staging|development)
        NAMESPACE="yawl-${ENVIRONMENT}"
        if [ "$ENVIRONMENT" = "production" ]; then
            NAMESPACE="yawl-prod"
        fi
        ;;
    *)
        echo -e "${RED}Error: Invalid environment '$ENVIRONMENT'${NC}"
        echo "Valid options: production, staging, development"
        exit 1
        ;;
esac

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}YAWL Timoni Deployment${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Environment: ${ENVIRONMENT}${NC}"
echo -e "${YELLOW}Namespace: ${NAMESPACE}${NC}"
echo -e "${YELLOW}Action: ${ACTION}${NC}"
echo ""

# Function to check prerequisites
check_prerequisites() {
    echo -e "${BLUE}Checking prerequisites...${NC}"

    # Check if timoni is installed
    if ! command -v timoni &> /dev/null; then
        echo -e "${RED}Error: timoni is not installed${NC}"
        echo "Install from: https://timoni.sh/install"
        exit 1
    fi

    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        echo -e "${RED}Error: kubectl is not installed${NC}"
        exit 1
    fi

    # Check kubectl connectivity
    if ! kubectl cluster-info &> /dev/null; then
        echo -e "${RED}Error: Cannot connect to Kubernetes cluster${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Prerequisites check passed${NC}"
    echo ""
}

# Function to create namespace
create_namespace() {
    if kubectl get namespace "$NAMESPACE" &> /dev/null; then
        echo -e "${YELLOW}Namespace ${NAMESPACE} already exists${NC}"
    else
        echo -e "${BLUE}Creating namespace ${NAMESPACE}...${NC}"
        kubectl create namespace "$NAMESPACE"
        echo -e "${GREEN}✓ Namespace created${NC}"
    fi
    echo ""
}

# Function to setup secrets
setup_secrets() {
    echo -e "${BLUE}Checking database secrets...${NC}"

    if kubectl get secret yawl-db-credentials -n "$NAMESPACE" &> /dev/null; then
        echo -e "${YELLOW}Secret yawl-db-credentials already exists${NC}"
    else
        echo -e "${YELLOW}Warning: Secret yawl-db-credentials not found${NC}"
        echo -e "${YELLOW}Please create it manually:${NC}"
        echo ""
        echo "kubectl create secret generic yawl-db-credentials \\"
        echo "  --from-literal=password='your-password' \\"
        echo "  -n ${NAMESPACE}"
        echo ""
        read -p "Continue without secret? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    echo ""
}

# Function to validate configuration
validate_config() {
    echo -e "${BLUE}Validating configuration...${NC}"

    timoni bundle apply yawl \
        --values "${MODULE_PATH}/values.cue" \
        --values "${MODULE_PATH}/values-${ENVIRONMENT}.cue" \
        --dry-run > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Configuration validation passed${NC}"
    else
        echo -e "${RED}✗ Configuration validation failed${NC}"
        exit 1
    fi
    echo ""
}

# Function to show deployment plan
show_plan() {
    echo -e "${BLUE}Deployment Plan:${NC}"
    echo ""

    timoni bundle apply yawl \
        --values "${MODULE_PATH}/values.cue" \
        --values "${MODULE_PATH}/values-${ENVIRONMENT}.cue" \
        --dry-run --output yaml
}

# Function to apply deployment
apply_deployment() {
    echo -e "${BLUE}Applying deployment to namespace ${NAMESPACE}...${NC}"
    echo ""

    timoni bundle apply yawl \
        --values "${MODULE_PATH}/values.cue" \
        --values "${MODULE_PATH}/values-${ENVIRONMENT}.cue" \
        --namespace "${NAMESPACE}"

    echo ""
    echo -e "${GREEN}✓ Deployment applied successfully${NC}"
    echo ""
}

# Function to rollout status
check_rollout() {
    echo -e "${BLUE}Waiting for deployment to complete...${NC}"
    echo ""

    if kubectl rollout status deployment/yawl -n "${NAMESPACE}" --timeout=5m; then
        echo -e "${GREEN}✓ Deployment completed successfully${NC}"
    else
        echo -e "${YELLOW}⚠ Deployment is still rolling out or encountered issues${NC}"
        echo "Check status with:"
        echo "  kubectl get pods -n ${NAMESPACE}"
        echo "  kubectl describe pod <pod-name> -n ${NAMESPACE}"
    fi
    echo ""
}

# Function to show deployment info
show_deployment_info() {
    echo -e "${BLUE}Deployment Information:${NC}"
    echo ""

    echo -e "${YELLOW}Pods:${NC}"
    kubectl get pods -n "${NAMESPACE}" -l app=yawl
    echo ""

    echo -e "${YELLOW}Services:${NC}"
    kubectl get services -n "${NAMESPACE}"
    echo ""

    echo -e "${YELLOW}ConfigMaps:${NC}"
    kubectl get configmaps -n "${NAMESPACE}" | grep yawl
    echo ""

    if [ "$ENVIRONMENT" = "production" ]; then
        echo -e "${YELLOW}HPA Status:${NC}"
        kubectl get hpa -n "${NAMESPACE}"
        echo ""
    fi
}

# Function to delete deployment
delete_deployment() {
    echo -e "${RED}⚠ WARNING: This will delete the YAWL deployment in ${NAMESPACE}${NC}"
    read -p "Are you sure? Type 'yes' to confirm: " -r
    echo

    if [ "$REPLY" = "yes" ]; then
        echo -e "${BLUE}Deleting deployment...${NC}"

        timoni bundle delete yawl \
            --namespace "${NAMESPACE}"

        echo -e "${GREEN}✓ Deployment deleted${NC}"
    else
        echo -e "${YELLOW}Cancelled${NC}"
    fi
    echo ""
}

# Function to show logs
show_logs() {
    POD_NAME=$(kubectl get pods -n "${NAMESPACE}" -l app=yawl -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [ -z "$POD_NAME" ]; then
        echo -e "${RED}No pods found for app=yawl in namespace ${NAMESPACE}${NC}"
        return
    fi

    echo -e "${BLUE}Showing logs from ${POD_NAME}...${NC}"
    echo ""

    kubectl logs "$POD_NAME" -n "${NAMESPACE}" --all-containers=true -f
}

# Main execution
case "$ACTION" in
    apply)
        check_prerequisites
        create_namespace
        setup_secrets
        validate_config
        echo -e "${YELLOW}Ready to apply deployment. Review the plan above.${NC}"
        read -p "Continue with deployment? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            apply_deployment
            check_rollout
            show_deployment_info
        else
            echo -e "${YELLOW}Deployment cancelled${NC}"
        fi
        ;;

    plan)
        check_prerequisites
        show_plan
        ;;

    validate)
        check_prerequisites
        validate_config
        echo -e "${GREEN}✓ Configuration is valid${NC}"
        ;;

    status)
        create_namespace
        show_deployment_info
        ;;

    logs)
        show_logs
        ;;

    delete)
        delete_deployment
        ;;

    *)
        echo -e "${RED}Error: Unknown action '$ACTION'${NC}"
        echo ""
        echo "Usage: $0 [environment] [action]"
        echo ""
        echo "Environments: production, staging, development"
        echo "Actions:"
        echo "  apply     - Apply deployment (default)"
        echo "  plan      - Show deployment plan"
        echo "  validate  - Validate configuration"
        echo "  status    - Show deployment status"
        echo "  logs      - Show pod logs"
        echo "  delete    - Delete deployment"
        echo ""
        echo "Examples:"
        echo "  $0 production apply"
        echo "  $0 staging plan"
        echo "  $0 development validate"
        exit 1
        ;;
esac

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
