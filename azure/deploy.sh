#!/bin/bash

# YAWL Azure Deployment Script
# Automated ARM template deployment with configuration validation
# Usage: ./deploy.sh [environment] [region]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_FILE="$SCRIPT_DIR/azuredeploy.json"
PARAMETERS_FILE="$SCRIPT_DIR/parameters.json"

# Default values
ENVIRONMENT="${1:-production}"
REGION="${2:-eastus}"
PROJECT_NAME="yawl"

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(development|staging|production)$ ]]; then
    echo -e "${RED}Error: Invalid environment. Must be 'development', 'staging', or 'production'${NC}"
    exit 1
fi

# Derived values
RESOURCE_GROUP="${PROJECT_NAME}-${ENVIRONMENT}"
DEPLOYMENT_NAME="${PROJECT_NAME}-deployment-$(date +%s)"

# Functions
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

check_prerequisites() {
    log_info "Checking prerequisites..."

    # Check Azure CLI
    if ! command -v az &> /dev/null; then
        log_error "Azure CLI is not installed. Please install it first."
        exit 1
    fi

    # Check jq
    if ! command -v jq &> /dev/null; then
        log_warning "jq is not installed. Some features may be limited."
    fi

    # Check Azure CLI version
    local az_version=$(az --version | grep "azure-cli" | awk '{print $2}')
    log_info "Azure CLI version: $az_version"

    # Check authentication
    if ! az account show > /dev/null 2>&1; then
        log_error "Not authenticated to Azure. Run 'az login' first."
        exit 1
    fi

    log_success "Prerequisites check passed"
}

validate_files() {
    log_info "Validating template files..."

    if [ ! -f "$TEMPLATE_FILE" ]; then
        log_error "Template file not found: $TEMPLATE_FILE"
        exit 1
    fi

    if [ ! -f "$PARAMETERS_FILE" ]; then
        log_error "Parameters file not found: $PARAMETERS_FILE"
        exit 1
    fi

    log_success "Template files found and accessible"
}

get_database_password() {
    log_info "Enter PostgreSQL admin password (at least 8 characters, must contain uppercase, lowercase, numbers, and special characters):"
    read -s -p "Password: " db_password
    echo
    read -s -p "Confirm password: " db_password_confirm
    echo

    if [ "$db_password" != "$db_password_confirm" ]; then
        log_error "Passwords do not match"
        exit 1
    fi

    # Validate password strength
    if [ ${#db_password} -lt 8 ]; then
        log_error "Password must be at least 8 characters"
        exit 1
    fi

    if ! [[ "$db_password" =~ [A-Z] ]]; then
        log_error "Password must contain uppercase letters"
        exit 1
    fi

    if ! [[ "$db_password" =~ [a-z] ]]; then
        log_error "Password must contain lowercase letters"
        exit 1
    fi

    if ! [[ "$db_password" =~ [0-9] ]]; then
        log_error "Password must contain numbers"
        exit 1
    fi

    if ! [[ "$db_password" =~ [!@#$%^&*\(\)_+\-=\[\]{};:,.<>?] ]]; then
        log_error "Password must contain special characters"
        exit 1
    fi

    echo "$db_password"
}

get_subscription_info() {
    log_info "Getting Azure subscription information..."

    local current_sub=$(az account show --query "id" -o tsv)
    local sub_name=$(az account show --query "name" -o tsv)

    log_info "Current subscription: $sub_name ($current_sub)"

    read -p "Do you want to continue with this subscription? (yes/no) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_error "Deployment cancelled"
        exit 1
    fi
}

create_resource_group() {
    log_info "Creating resource group: $RESOURCE_GROUP"

    # Check if resource group exists
    if az group exists --name "$RESOURCE_GROUP" | grep -q "true"; then
        log_warning "Resource group $RESOURCE_GROUP already exists"
    else
        az group create \
            --name "$RESOURCE_GROUP" \
            --location "$REGION" \
            --tags \
                environment="$ENVIRONMENT" \
                application="$PROJECT_NAME" \
                managed-by="arm-template" \
                deployed="$(date +%Y-%m-%d)" \
            > /dev/null
        log_success "Resource group created"
    fi
}

validate_template() {
    log_info "Validating ARM template..."

    local validation_result=$(az deployment group validate \
        --resource-group "$RESOURCE_GROUP" \
        --template-file "$TEMPLATE_FILE" \
        --parameters "$PARAMETERS_FILE" \
        --parameters \
            environment="$ENVIRONMENT" \
            location="$REGION" \
            projectName="$PROJECT_NAME" \
            databaseAdminPassword="$DB_PASSWORD" \
        2>&1)

    if echo "$validation_result" | grep -q "Valid"; then
        log_success "Template validation passed"
        return 0
    else
        log_error "Template validation failed"
        echo "$validation_result"
        return 1
    fi
}

deploy_template() {
    log_info "Starting ARM template deployment..."
    log_info "Resource Group: $RESOURCE_GROUP"
    log_info "Region: $REGION"
    log_info "Environment: $ENVIRONMENT"
    log_info "Deployment Name: $DEPLOYMENT_NAME"

    echo
    read -p "Do you want to proceed with the deployment? (yes/no) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_error "Deployment cancelled"
        exit 1
    fi

    local start_time=$(date +%s)

    az deployment group create \
        --resource-group "$RESOURCE_GROUP" \
        --template-file "$TEMPLATE_FILE" \
        --parameters "$PARAMETERS_FILE" \
        --parameters \
            environment="$ENVIRONMENT" \
            location="$REGION" \
            projectName="$PROJECT_NAME" \
            databaseAdminPassword="$DB_PASSWORD" \
        --name "$DEPLOYMENT_NAME"

    if [ $? -eq 0 ]; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        log_success "Deployment completed in $((duration / 60)) minutes and $((duration % 60)) seconds"
        return 0
    else
        log_error "Deployment failed"
        return 1
    fi
}

display_outputs() {
    log_info "Retrieving deployment outputs..."

    echo
    echo -e "${BLUE}=== Deployment Outputs ===${NC}"

    az deployment group show \
        --resource-group "$RESOURCE_GROUP" \
        --name "$DEPLOYMENT_NAME" \
        --query properties.outputs \
        -o json | jq '.' 2>/dev/null || \
    az deployment group show \
        --resource-group "$RESOURCE_GROUP" \
        --name "$DEPLOYMENT_NAME" \
        --query properties.outputs

    echo
    log_info "Resources deployed:"
    az resource list \
        --resource-group "$RESOURCE_GROUP" \
        --query "[].{Name:name, Type:type}" \
        -o table
}

save_outputs() {
    log_info "Saving deployment outputs to file..."

    local output_file="$SCRIPT_DIR/deployment-outputs-${ENVIRONMENT}.json"

    az deployment group show \
        --resource-group "$RESOURCE_GROUP" \
        --name "$DEPLOYMENT_NAME" \
        --query properties.outputs \
        -o json > "$output_file"

    log_success "Outputs saved to $output_file"
}

post_deployment_info() {
    log_info "Post-deployment information:"

    local app_service_name=$(az resource list \
        --resource-group "$RESOURCE_GROUP" \
        --query "[?type=='Microsoft.Web/sites'].name" \
        -o tsv | head -1)

    local db_server_name=$(az resource list \
        --resource-group "$RESOURCE_GROUP" \
        --query "[?type=='Microsoft.DBforPostgreSQL/servers'].name" \
        -o tsv | head -1)

    local appgw_name=$(az resource list \
        --resource-group "$RESOURCE_GROUP" \
        --query "[?type=='Microsoft.Network/applicationGateways'].name" \
        -o tsv | head -1)

    echo
    echo -e "${BLUE}=== Next Steps ===${NC}"
    echo "1. Verify deployment in Azure Portal"
    echo "   https://portal.azure.com"
    echo
    echo "2. Configure Application Gateway backend:"
    echo "   az network application-gateway address-pool update \\"
    echo "     --resource-group $RESOURCE_GROUP \\"
    echo "     --gateway-name $appgw_name \\"
    echo "     --name appGatewayBackendPool \\"
    echo "     --servers <app-service-ip>"
    echo
    echo "3. Deploy YAWL application:"
    echo "   az webapp deployment container config \\"
    echo "     --resource-group $RESOURCE_GROUP \\"
    echo "     --name $app_service_name"
    echo
    echo "4. View Application Insights:"
    echo "   az monitor app-insights metrics show \\"
    echo "     --app yawl-ai-* \\"
    echo "     --resource-group $RESOURCE_GROUP"
    echo
    echo "5. Monitor logs:"
    echo "   az webapp log tail \\"
    echo "     --resource-group $RESOURCE_GROUP \\"
    echo "     --name $app_service_name"
    echo
}

cleanup_on_error() {
    local exit_code=$?
    if [ $exit_code -ne 0 ]; then
        log_error "Deployment failed with exit code $exit_code"
        echo
        read -p "Do you want to delete the resource group to clean up? (yes/no) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            log_warning "Deleting resource group: $RESOURCE_GROUP"
            az group delete --name "$RESOURCE_GROUP" --yes --no-wait
        fi
    fi
}

# Main execution
main() {
    echo -e "${BLUE}"
    echo "╔════════════════════════════════════════════════╗"
    echo "║   YAWL Workflow Engine - Azure Deployment     ║"
    echo "║                                                ║"
    echo "║   Environment: $ENVIRONMENT"
    echo "║   Region: $REGION"
    echo "╚════════════════════════════════════════════════╝"
    echo -e "${NC}"
    echo

    # Set trap for cleanup
    trap cleanup_on_error EXIT

    # Execute deployment steps
    check_prerequisites
    validate_files
    get_subscription_info
    DB_PASSWORD=$(get_database_password)
    create_resource_group
    validate_template
    deploy_template
    display_outputs
    save_outputs
    post_deployment_info

    log_success "YAWL deployment completed successfully!"
}

# Run main function
main "$@"
