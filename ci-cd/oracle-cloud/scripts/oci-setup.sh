#!/bin/bash
# Oracle Cloud Infrastructure Setup Script for YAWL
# This script initializes OCI resources required for CI/CD pipeline
# Version: 1.0.0

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/oci-config.env"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check OCI CLI
    if ! command -v oci &> /dev/null; then
        log_error "OCI CLI not found. Please install it first."
        log_info "Installation: bash -c \"\$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)\""
        exit 1
    fi

    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl not found. Please install it first."
        exit 1
    fi

    # Check docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker not found. Please install it first."
        exit 1
    fi

    # Verify OCI CLI configuration
    if ! oci os ns get &> /dev/null; then
        log_error "OCI CLI not configured. Please run 'oci setup config' first."
        exit 1
    fi

    log_success "All prerequisites met."
}

# Load or create configuration
load_config() {
    if [ -f "$CONFIG_FILE" ]; then
        log_info "Loading configuration from $CONFIG_FILE"
        source "$CONFIG_FILE"
    else
        log_info "Creating new configuration..."
        create_config
    fi
}

# Create configuration file
create_config() {
    log_info "Please provide the following OCI configuration values:"

    read -p "OCI Compartment OCID: " OCI_COMPARTMENT_ID
    read -p "OCI Region (e.g., us-phoenix-1): " OCI_REGION
    read -p "OCI Tenancy Namespace: " OCI_TENANCY_NAMESPACE
    read -p "OKE Cluster OCID: " OKE_CLUSTER_ID
    read -p "OCI Vault OCID (or press Enter to skip): " OCI_VAULT_ID
    read -p "Notification Email: " NOTIFICATION_EMAIL
    read -p "Git Repository URL: " GIT_REPOSITORY_URL

    cat > "$CONFIG_FILE" << EOF
# OCI Configuration for YAWL CI/CD Pipeline
# Generated at: $(date)

# OCI Core Configuration
export OCI_COMPARTMENT_ID="${OCI_COMPARTMENT_ID}"
export OCI_REGION="${OCI_REGION}"
export OCI_TENANCY_NAMESPACE="${OCI_TENANCY_NAMESPACE}"

# OKE Configuration
export OKE_CLUSTER_ID="${OKE_CLUSTER_ID}"

# OCI Vault
export OCI_VAULT_ID="${OCI_VAULT_ID}"

# Notifications
export NOTIFICATION_EMAIL="${NOTIFICATION_EMAIL}"

# Git Repository
export GIT_REPOSITORY_URL="${GIT_REPOSITORY_URL}"
EOF

    log_success "Configuration saved to $CONFIG_FILE"
    source "$CONFIG_FILE"
}

# Create OCI DevOps project
create_devops_project() {
    log_info "Creating OCI DevOps Project..."

    PROJECT_NAME="yawl-cicd"
    PROJECT_DESCRIPTION="YAWL Workflow Engine CI/CD Pipeline"

    # Check if project exists
    EXISTING_PROJECT=$(oci devops project list \
        --compartment-id "$OCI_COMPARTMENT_ID" \
        --query "data[?display-name=='$PROJECT_NAME'].id" \
        --raw-output 2>/dev/null || echo "")

    if [ -n "$EXISTING_PROJECT" ]; then
        log_warn "Project $PROJECT_NAME already exists"
        OCI_PROJECT_ID=$(echo "$EXISTING_PROJECT" | jq -r '.[0]')
    else
        OCI_PROJECT_ID=$(oci devops project create \
            --compartment-id "$OCI_COMPARTMENT_ID" \
            --name "$PROJECT_NAME" \
            --description "$PROJECT_DESCRIPTION" \
            --query 'data.id' \
            --raw-output)

        log_success "Created DevOps Project: $OCI_PROJECT_ID"
    fi

    export OCI_PROJECT_ID
    echo "export OCI_PROJECT_ID=\"$OCI_PROJECT_ID\"" >> "$CONFIG_FILE"
}

# Create OCI Vault secrets
create_vault_secrets() {
    if [ -z "${OCI_VAULT_ID:-}" ]; then
        log_warn "No Vault OCID provided. Skipping secret creation."
        return
    fi

    log_info "Creating Vault secrets..."

    # Database password secret
    read -sp "Enter Database Password: " DB_PASSWORD
    echo

    oci vault secret create-base64 \
        --compartment-id "$OCI_COMPARTMENT_ID" \
        --vault-id "$OCI_VAULT_ID" \
        --secret-name "yawl-db-password" \
        --secret-content-content "$(echo -n "$DB_PASSWORD" | base64)" \
        --description "YAWL Database Password"

    log_success "Created secret: yawl-db-password"
}

# Create build pipeline
create_build_pipeline() {
    log_info "Creating Build Pipeline..."

    BUILD_PIPELINE_ID=$(oci devops build-pipeline create \
        --project-id "$OCI_PROJECT_ID" \
        --display-name "yawl-build-pipeline" \
        --description "YAWL Engine Build Pipeline" \
        --query 'data.id' \
        --raw-output)

    export OCI_BUILD_PIPELINE_ID="$BUILD_PIPELINE_ID"
    echo "export OCI_BUILD_PIPELINE_ID=\"$OCI_BUILD_PIPELINE_ID\"" >> "$CONFIG_FILE"

    log_success "Created Build Pipeline: $BUILD_PIPELINE_ID"
}

# Create deploy pipeline
create_deploy_pipeline() {
    log_info "Creating Deploy Pipeline..."

    DEPLOY_PIPELINE_ID=$(oci devops deploy-pipeline create \
        --project-id "$OCI_PROJECT_ID" \
        --display-name "yawl-deploy-pipeline" \
        --description "YAWL Engine Deploy Pipeline" \
        --query 'data.id' \
        --raw-output)

    export OCI_DEPLOY_PIPELINE_ID="$DEPLOY_PIPELINE_ID"
    echo "export OCI_DEPLOY_PIPELINE_ID=\"$OCI_DEPLOY_PIPELINE_ID\"" >> "$CONFIG_FILE"

    log_success "Created Deploy Pipeline: $DEPLOY_PIPELINE_ID"
}

# Create Container Registry repository
create_container_registry() {
    log_info "Setting up OCI Container Registry..."

    for service in engine resource-service worklet-service; do
        REPO_NAME="yawl-${service}"

        # Create repository (will succeed if exists)
        oci artifacts container repository create \
            --compartment-id "$OCI_COMPARTMENT_ID" \
            --display-name "$REPO_NAME" \
            --is-public false \
            2>/dev/null || log_warn "Repository $REPO_NAME may already exist"

        log_success "Configured repository: $REPO_NAME"
    done
}

# Setup OKE cluster access
setup_oke_access() {
    log_info "Setting up OKE cluster access..."

    # Create kubeconfig for the cluster
    oci ce cluster create-kubeconfig \
        --cluster-id "$OKE_CLUSTER_ID" \
        --file "$HOME/.kube/config_yawl" \
        --region "$OCI_REGION" \
        --token-version 2.0.0

    # Set as current context
    export KUBECONFIG="$HOME/.kube/config_yawl"

    # Verify access
    if kubectl cluster-info &> /dev/null; then
        log_success "OKE cluster access configured successfully"
    else
        log_error "Failed to configure OKE cluster access"
        exit 1
    fi
}

# Create OCI Notification Topic
create_notification_topic() {
    log_info "Creating Notification Topic..."

    TOPIC_ID=$(oci ons topic create \
        --compartment-id "$OCI_COMPARTMENT_ID" \
        --name "yawl-deployments" \
        --description "YAWL Deployment Notifications" \
        --query 'data."topic-id"' \
        --raw-output)

    # Create email subscription
    oci ons subscription create \
        --compartment-id "$OCI_COMPARTMENT_ID" \
        --topic-id "$TOPIC_ID" \
        --protocol "EMAIL" \
        --subscription-endpoint "$NOTIFICATION_EMAIL" \
        --description "YAWL Deployment Email Notifications"

    log_success "Created notification topic with email subscription"
}

# Create IAM policies for DevOps
create_iam_policies() {
    log_info "Creating IAM policies for DevOps..."

    POLICY_NAME="yawl-devops-policy"

    POLICY_STATEMENT="
Allow dynamic-group yawl-devops-dynamic-group to manage all-resources in compartment id ${OCI_COMPARTMENT_ID}
Allow service devops to use ons-topics in compartment id ${OCI_COMPARTMENT_ID}
Allow service devops to manage container-instances in compartment id ${OCI_COMPARTMENT_ID}
"

    oci iam policy create \
        --compartment-id "$OCI_COMPARTMENT_ID" \
        --name "$POLICY_NAME" \
        --description "Policy for YAWL DevOps Pipeline" \
        --statements "[$(echo "$POLICY_STATEMENT" | jq -Rs .)]" \
        2>/dev/null || log_warn "Policy may already exist"

    log_success "IAM policies configured"
}

# Print summary
print_summary() {
    echo ""
    echo "========================================"
    echo "  YAWL OCI CI/CD Setup Complete"
    echo "========================================"
    echo ""
    echo "Configuration saved to: $CONFIG_FILE"
    echo ""
    echo "Created Resources:"
    echo "  - DevOps Project: ${OCI_PROJECT_ID:-Not created}"
    echo "  - Build Pipeline: ${OCI_BUILD_PIPELINE_ID:-Not created}"
    echo "  - Deploy Pipeline: ${OCI_DEPLOY_PIPELINE_ID:-Not created}"
    echo ""
    echo "Container Registry:"
    echo "  ${OCI_REGION}.ocir.io/${OCI_TENANCY_NAMESPACE}/yawl-engine"
    echo "  ${OCI_REGION}.ocir.io/${OCI_TENANCY_NAMESPACE}/yawl-resource-service"
    echo "  ${OCI_REGION}.ocir.io/${OCI_TENANCY_NAMESPACE}/yawl-worklet-service"
    echo ""
    echo "Next Steps:"
    echo "  1. Review and update configuration in $CONFIG_FILE"
    echo "  2. Configure your Git repository webhook"
    echo "  3. Run: kubectl apply -f ci-cd/oracle-cloud/k8s-manifests/deployment.yaml"
    echo ""
}

# Main execution
main() {
    log_info "Starting YAWL OCI CI/CD Setup..."

    check_prerequisites
    load_config

    # Create resources
    create_devops_project
    create_container_registry
    create_build_pipeline
    create_deploy_pipeline
    setup_oke_access
    create_notification_topic

    print_summary

    log_success "Setup completed successfully!"
}

# Run main function
main "$@"
