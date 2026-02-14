#!/bin/bash

# Setup Remote State Backend in Azure
# Usage: ./scripts/setup-backend.sh [resource-group] [storage-account-name]
# Example: ./scripts/setup-backend.sh rg-terraform-state tfstate

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Get parameters
RESOURCE_GROUP="${1:-rg-terraform-state}"
STORAGE_ACCOUNT="${2:-tfstate$(date +%s | tail -c 5)}"
CONTAINER_NAME="state"
LOCATION="eastus"

# Check if az cli is installed
if ! command -v az &> /dev/null; then
    print_error "Azure CLI is not installed"
    exit 1
fi

# Check Azure login
if ! az account show &> /dev/null; then
    print_error "Not logged into Azure. Run 'az login'"
    exit 1
fi

print_header "Setting Up Terraform Backend"

# Get subscription
SUBSCRIPTION=$(az account show --query name -o tsv)
print_success "Logged in to Azure subscription: $SUBSCRIPTION"

# Create resource group
print_header "Creating Resource Group"

if az group exists --name "$RESOURCE_GROUP" | grep -q true; then
    print_success "Resource group already exists: $RESOURCE_GROUP"
else
    if az group create \
        --name "$RESOURCE_GROUP" \
        --location "$LOCATION"; then
        print_success "Resource group created: $RESOURCE_GROUP"
    else
        print_error "Failed to create resource group"
        exit 1
    fi
fi

# Create storage account
print_header "Creating Storage Account"

if az storage account show \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RESOURCE_GROUP" &> /dev/null; then
    print_success "Storage account already exists: $STORAGE_ACCOUNT"
else
    if az storage account create \
        --name "$STORAGE_ACCOUNT" \
        --resource-group "$RESOURCE_GROUP" \
        --location "$LOCATION" \
        --sku Standard_LRS \
        --encryption-services blob \
        --https-only true; then
        print_success "Storage account created: $STORAGE_ACCOUNT"
    else
        print_error "Failed to create storage account"
        exit 1
    fi
fi

# Get storage account key
print_header "Getting Storage Account Key"

STORAGE_KEY=$(az storage account keys list \
    --account-name "$STORAGE_ACCOUNT" \
    --resource-group "$RESOURCE_GROUP" \
    --query '[0].value' -o tsv)

if [ -z "$STORAGE_KEY" ]; then
    print_error "Failed to get storage account key"
    exit 1
fi

print_success "Storage account key retrieved"

# Create storage container
print_header "Creating Storage Container"

if az storage container exists \
    --name "$CONTAINER_NAME" \
    --account-name "$STORAGE_ACCOUNT" \
    --account-key "$STORAGE_KEY" \
    --query exists | grep -q true; then
    print_success "Container already exists: $CONTAINER_NAME"
else
    if az storage container create \
        --name "$CONTAINER_NAME" \
        --account-name "$STORAGE_ACCOUNT" \
        --account-key "$STORAGE_KEY"; then
        print_success "Container created: $CONTAINER_NAME"
    else
        print_error "Failed to create container"
        exit 1
    fi
fi

# Create backend.tf with configuration
print_header "Creating Backend Configuration"

BACKEND_CONFIG="$PROJECT_ROOT/backend-config.hcl"

cat > "$BACKEND_CONFIG" <<EOF
resource_group_name  = "$RESOURCE_GROUP"
storage_account_name = "$STORAGE_ACCOUNT"
container_name       = "$CONTAINER_NAME"
key                  = "yawl/terraform.tfstate"
EOF

print_success "Backend configuration created: $BACKEND_CONFIG"

# Show next steps
echo ""
print_header "Next Steps"
echo ""
echo "1. Uncomment the backend block in $PROJECT_ROOT/main.tf:"
echo ""
echo "   terraform {"
echo "     backend \"azurerm\" {"
echo "       resource_group_name  = \"$RESOURCE_GROUP\""
echo "       storage_account_name = \"$STORAGE_ACCOUNT\""
echo "       container_name       = \"$CONTAINER_NAME\""
echo "       key                  = \"yawl/terraform.tfstate\""
echo "     }"
echo "   }"
echo ""
echo "2. Run terraform init to migrate state:"
echo "   terraform init"
echo ""
echo "3. Verify the backend:"
echo "   terraform state list"
echo ""

# Optional: Cleanup local state
read -p "Do you want to remove the local state files? (yes/no): " CLEANUP

if [[ "$CLEANUP" == "yes" ]]; then
    cd "$PROJECT_ROOT"
    rm -rf .terraform/terraform.tfstate*
    print_success "Local state files removed"
fi

print_success "Backend setup completed!"
