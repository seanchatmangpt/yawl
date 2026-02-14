#!/bin/bash

# YAWL Terraform Deployment Script
# Usage: ./scripts/deploy.sh [environment]
# Example: ./scripts/deploy.sh dev

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}! $1${NC}"
}

# Check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check if terraform is installed
    if ! command -v terraform &> /dev/null; then
        print_error "Terraform is not installed"
        exit 1
    fi
    print_success "Terraform installed: $(terraform version | head -n1)"

    # Check if az cli is installed
    if ! command -v az &> /dev/null; then
        print_error "Azure CLI is not installed"
        exit 1
    fi
    print_success "Azure CLI installed: $(az --version | head -n1)"

    # Check Azure login
    if ! az account show &> /dev/null; then
        print_error "Not logged into Azure. Run 'az login'"
        exit 1
    fi

    SUBSCRIPTION=$(az account show --query name -o tsv)
    print_success "Logged in to Azure subscription: $SUBSCRIPTION"
}

# Validate terraform configuration
validate_config() {
    print_header "Validating Terraform Configuration"

    cd "$PROJECT_ROOT"

    if terraform validate; then
        print_success "Configuration is valid"
    else
        print_error "Configuration validation failed"
        exit 1
    fi

    # Check formatting
    if terraform fmt -check -recursive &> /dev/null; then
        print_success "Code formatting is correct"
    else
        print_warning "Code formatting issues found. Run: terraform fmt -recursive"
    fi
}

# Initialize terraform
init_terraform() {
    print_header "Initializing Terraform"

    cd "$PROJECT_ROOT"

    if terraform init; then
        print_success "Terraform initialized"
    else
        print_error "Terraform initialization failed"
        exit 1
    fi
}

# Get environment from arguments
ENVIRONMENT="${1:-dev}"

if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    print_error "Invalid environment: $ENVIRONMENT"
    echo "Usage: $0 [dev|staging|prod]"
    exit 1
fi

VARS_FILE="$PROJECT_ROOT/terraform.tfvars.$ENVIRONMENT"

# Check if variables file exists
if [ ! -f "$VARS_FILE" ]; then
    if [ -f "$PROJECT_ROOT/terraform.tfvars" ]; then
        print_warning "Using terraform.tfvars for $ENVIRONMENT environment"
        VARS_FILE="$PROJECT_ROOT/terraform.tfvars"
    else
        print_error "Variables file not found: $VARS_FILE"
        echo "Please create $VARS_FILE or use terraform.tfvars"
        exit 1
    fi
fi

print_header "YAWL Terraform Deployment"
echo "Environment: $ENVIRONMENT"
echo "Variables file: $VARS_FILE"
echo ""

# Run checks
check_prerequisites
validate_config
init_terraform

# Plan deployment
print_header "Planning Deployment"

cd "$PROJECT_ROOT"

PLAN_FILE="$PROJECT_ROOT/tfplan-$ENVIRONMENT"

if terraform plan -var-file="$VARS_FILE" -out="$PLAN_FILE"; then
    print_success "Plan created successfully"
else
    print_error "Plan creation failed"
    exit 1
fi

# Ask for confirmation
echo ""
print_warning "Review the plan above carefully"
read -p "Do you want to apply this plan? (yes/no): " CONFIRM

if [[ "$CONFIRM" != "yes" ]]; then
    print_warning "Deployment cancelled"
    rm -f "$PLAN_FILE"
    exit 0
fi

# Apply deployment
print_header "Applying Deployment"

if terraform apply "$PLAN_FILE"; then
    print_success "Deployment completed successfully"
else
    print_error "Deployment failed"
    exit 1
fi

# Clean up plan file
rm -f "$PLAN_FILE"

# Show outputs
print_header "Deployment Outputs"

terraform output

print_success "Deployment completed for environment: $ENVIRONMENT"
