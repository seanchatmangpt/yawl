#!/bin/bash

# YAWL Terraform Destroy Script
# Usage: ./scripts/destroy.sh [environment]
# Example: ./scripts/destroy.sh dev

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

# Get environment
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
        exit 1
    fi
fi

print_header "YAWL Terraform Destroy"
echo "Environment: $ENVIRONMENT"
echo "Variables file: $VARS_FILE"
echo ""

# Confirmation
print_warning "This will DESTROY all infrastructure in the $ENVIRONMENT environment"
print_warning "This action cannot be undone"
echo ""

read -p "Type 'destroy $ENVIRONMENT' to confirm: " CONFIRM

if [[ "$CONFIRM" != "destroy $ENVIRONMENT" ]]; then
    print_warning "Destruction cancelled"
    exit 0
fi

# Check Azure login
if ! az account show &> /dev/null; then
    print_error "Not logged into Azure. Run 'az login'"
    exit 1
fi

SUBSCRIPTION=$(az account show --query name -o tsv)
print_success "Logged in to Azure subscription: $SUBSCRIPTION"

# Plan destruction
print_header "Planning Destruction"

cd "$PROJECT_ROOT"

PLAN_FILE="$PROJECT_ROOT/tfplan-destroy-$ENVIRONMENT"

if terraform plan -destroy -var-file="$VARS_FILE" -out="$PLAN_FILE"; then
    print_success "Destruction plan created"
else
    print_error "Destruction plan failed"
    exit 1
fi

# Final confirmation
echo ""
print_warning "Review the destruction plan above"
read -p "Type 'yes' to proceed with destruction: " FINAL_CONFIRM

if [[ "$FINAL_CONFIRM" != "yes" ]]; then
    print_warning "Destruction cancelled"
    rm -f "$PLAN_FILE"
    exit 0
fi

# Destroy resources
print_header "Destroying Infrastructure"

if terraform apply "$PLAN_FILE"; then
    print_success "Infrastructure destroyed successfully"
else
    print_error "Destruction failed"
    exit 1
fi

# Clean up
rm -f "$PLAN_FILE"

print_success "Destruction completed for environment: $ENVIRONMENT"
