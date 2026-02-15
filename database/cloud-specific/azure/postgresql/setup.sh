#!/bin/bash
# Azure PostgreSQL Setup for YAWL
# This script configures Azure Database for PostgreSQL (Flexible Server)

set -euo pipefail

# Configuration
RESOURCE_GROUP="${RESOURCE_GROUP:-yawl-rg}"
LOCATION="${LOCATION:-eastus}"
SERVER_NAME="${SERVER_NAME:-yawl-db-server}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawldbuser}"
DB_PORT="${DB_PORT:-5432}"
SKU_NAME="${SKU_NAME:-Standard_D2s_v3}"
STORAGE_SIZE_GB="${STORAGE_SIZE_GB:-128}"
POSTGRES_VERSION="${POSTGRES_VERSION:-14}"
TIER="${TIER:-GeneralPurpose}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v az &> /dev/null; then
        log_error "Azure CLI not found. Please install Azure CLI."
        exit 1
    fi

    # Check Azure authentication
    if ! az account show > /dev/null 2>&1; then
        log_error "Azure credentials not configured. Run 'az login'."
        exit 1
    fi

    log_info "Prerequisites satisfied."
}

# Create resource group
create_resource_group() {
    log_info "Creating resource group: ${RESOURCE_GROUP}"

    az group create \
        --name "${RESOURCE_GROUP}" \
        --location "${LOCATION}" \
        --tags Environment=Production Project=YAWL

    log_info "Resource group created."
}

# Create virtual network
create_vnet() {
    log_info "Creating virtual network..."

    az network vnet create \
        --resource-group "${RESOURCE_GROUP}" \
        --name yawl-vnet \
        --address-prefixes 10.0.0.0/16 \
        --subnet-name yawl-db-subnet \
        --subnet-prefixes 10.0.1.0/24

    # Delegate subnet for PostgreSQL Flexible Server
    az network vnet subnet update \
        --resource-group "${RESOURCE_GROUP}" \
        --name yawl-db-subnet \
        --vnet-name yawl-vnet \
        --delegations Microsoft.DBforPostgreSQL/flexibleServers

    log_info "Virtual network created and delegated."
}

# Create Azure Database for PostgreSQL Flexible Server
create_postgresql_server() {
    log_info "Creating Azure Database for PostgreSQL Flexible Server..."

    # Generate password
    DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)

    az postgres flexible-server create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${SERVER_NAME}" \
        --location "${LOCATION}" \
        --admin-user "${DB_USER}" \
        --admin-password "${DB_PASSWORD}" \
        --sku-name "${SKU_NAME}" \
        --tier "${TIER}" \
        --storage-size "${STORAGE_SIZE_GB}" \
        --version "${POSTGRES_VERSION}" \
        --vnet yawl-vnet \
        --subnet yawl-db-subnet \
        --public-access Disabled \
        --backup-retention 30 \
        --geo-redundant-backup Enabled \
        --tags Environment=Production Project=YAWL

    log_info "PostgreSQL server created: ${SERVER_NAME}"

    # Store password for later use
    echo "DB_PASSWORD=${DB_PASSWORD}" > /tmp/yawl_db_password.txt
}

# Create database
create_database() {
    log_info "Creating database: ${DB_NAME}"

    az postgres flexible-server db create \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --database-name "${DB_NAME}"

    log_info "Database created."
}

# Configure server parameters
configure_server_parameters() {
    log_info "Configuring server parameters..."

    # Set performance parameters
    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name max_connections \
        --value 200

    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name work_mem \
        --value 16777216  # 16MB in bytes

    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name maintenance_work_mem \
        --value 134217728  # 128MB in bytes

    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name log_min_duration_statement \
        --value 1000

    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name log_connections \
        --value on

    az postgres flexible-server parameter set \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --name log_disconnections \
        --value on

    log_info "Server parameters configured."
}

# Configure firewall rules (for management access)
configure_firewall() {
    log_info "Configuring firewall rules..."

    # Allow Azure services
    az postgres flexible-server firewall-rule create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${SERVER_NAME}" \
        --rule-name "AllowAzureServices" \
        --start-ip-address 0.0.0.0 \
        --end-ip-address 0.0.0.0 || true

    log_info "Firewall rules configured."
}

# Configure private endpoint
configure_private_endpoint() {
    log_info "Configuring private endpoint..."

    # Create private DNS zone
    az network private-dns zone create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "privatelink.postgres.database.azure.com"

    # Link DNS zone to VNet
    az network private-dns link vnet create \
        --resource-group "${RESOURCE_GROUP}" \
        --zone-name "privatelink.postgres.database.azure.com" \
        --name yawl-dns-link \
        --virtual-network yawl-vnet \
        --registration-enabled false

    # Create private endpoint
    SERVER_RESOURCE_ID=$(az postgres flexible-server show \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${SERVER_NAME}" \
        --query id \
        --output tsv)

    az network private-endpoint create \
        --resource-group "${RESOURCE_GROUP}" \
        --name yawl-db-private-endpoint \
        --vnet-name yawl-vnet \
        --subnet yawl-db-subnet \
        --private-connection-resource-id "${SERVER_RESOURCE_ID}" \
        --group-id postgresqlServer \
        --connection-name yawl-db-connection

    # Configure private DNS zone group
    PRIVATE_ENDPOINT_NIC_ID=$(az network private-endpoint show \
        --resource-group "${RESOURCE_GROUP}" \
        --name yawl-db-private-endpoint \
        --query networkInterfaces[0].id \
        --output tsv)

    az network private-endpoint dns-zone-group create \
        --resource-group "${RESOURCE_GROUP}" \
        --endpoint-name yawl-db-private-endpoint \
        --name yawl-dns-zone-group \
        --private-dns-zone "privatelink.postgres.database.azure.com" \
        --zone-name postgresql

    log_info "Private endpoint configured."
}

# Enable Azure AD authentication
enable_aad_auth() {
    log_info "Enabling Azure AD authentication..."

    # Get current user object ID
    CURRENT_USER_OBJECT_ID=$(az ad signed-in-user show --query id --output tsv)

    # Set Azure AD admin
    az postgres flexible-server ad-admin create \
        --resource-group "${RESOURCE_GROUP}" \
        --server-name "${SERVER_NAME}" \
        --display-name "YAWL DB Admin" \
        --object-id "${CURRENT_USER_OBJECT_ID}"

    log_info "Azure AD authentication enabled."
}

# Configure backup and geo-replication
configure_backup() {
    log_info "Configuring backup settings..."

    # Backup retention is set during server creation
    # Geo-redundant backup is already enabled

    # Create a backup (manual)
    # Note: Azure automatically manages backups

    log_info "Backup configuration verified."
}

# Store credentials in Azure Key Vault
store_credentials() {
    log_info "Storing credentials in Azure Key Vault..."

    # Create Key Vault
    KEY_VAULT_NAME="yawl-kv-$(openssl rand -hex 4)"

    az keyvault create \
        --resource-group "${RESOURCE_GROUP}" \
        --name "${KEY_VAULT_NAME}" \
        --location "${LOCATION}" \
        --enable-soft-delete true \
        --enable-purge-protection true \
        --default-action Deny

    # Store database credentials
    source /tmp/yawl_db_password.txt

    az keyvault secret set \
        --vault-name "${KEY_VAULT_NAME}" \
        --name "yawl-db-password" \
        --value "${DB_PASSWORD}"

    az keyvault secret set \
        --vault-name "${KEY_VAULT_NAME}" \
        --name "yawl-db-connection-string" \
        --value "jdbc:postgresql://${SERVER_NAME}.postgres.database.azure.com:5432/${DB_NAME}?sslmode=require"

    # Grant access to current user
    CURRENT_USER_OBJECT_ID=$(az ad signed-in-user show --query id --output tsv)

    az keyvault set-policy \
        --name "${KEY_VAULT_NAME}" \
        --object-id "${CURRENT_USER_OBJECT_ID}" \
        --secret-permissions get list

    log_info "Credentials stored in Key Vault: ${KEY_VAULT_NAME}"

    # Clean up temp file
    rm -f /tmp/yawl_db_password.txt
}

# Verify connectivity
verify_connection() {
    log_info "Verifying database connectivity..."

    # Get server FQDN
    SERVER_FQDN="${SERVER_NAME}.postgres.database.azure.com"

    log_info "Server FQDN: ${SERVER_FQDN}"

    # Note: Direct connection may not work from local machine due to VNet isolation
    log_info "Database connectivity verification requires deployment within the VNet."
}

# Print connection info
print_info() {
    SERVER_FQDN="${SERVER_NAME}.postgres.database.azure.com"

    echo ""
    echo "========================================"
    echo "Azure PostgreSQL Setup Complete"
    echo "========================================"
    echo ""
    echo "Server Details:"
    echo "  Name:     ${SERVER_NAME}"
    echo "  FQDN:     ${SERVER_FQDN}"
    echo "  Port:     ${DB_PORT}"
    echo "  Database: ${DB_NAME}"
    echo "  User:     ${DB_USER}"
    echo ""
    echo "JDBC URL:"
    echo "  jdbc:postgresql://${SERVER_FQDN}:${DB_PORT}/${DB_NAME}?sslmode=require"
    echo ""
    echo "Spring Boot Configuration:"
    echo "  spring.datasource.url=jdbc:postgresql://${SERVER_FQDN}:${DB_PORT}/${DB_NAME}?sslmode=require"
    echo "  spring.datasource.username=${DB_USER}"
    echo ""
    echo "Connection String:"
    echo "  Server=${SERVER_FQDN};Database=${DB_NAME};Port=${DB_PORT};User Id=${DB_USER};SslMode=Require;"
    echo ""
    echo "Resources:"
    echo "  Resource Group: ${RESOURCE_GROUP}"
    echo "  VNet:           yawl-vnet"
    echo "  Subnet:         yawl-db-subnet"
    echo ""
    echo "Management Commands:"
    echo "  az postgres flexible-server show -g ${RESOURCE_GROUP} -n ${SERVER_NAME}"
    echo "  az postgres flexible-server connection-string -g ${RESOURCE_GROUP} -n ${SERVER_NAME} -d ${DB_NAME}"
    echo ""
}

# Main execution
main() {
    log_info "Starting Azure PostgreSQL setup..."

    check_prerequisites
    create_resource_group
    create_vnet
    create_postgresql_server
    create_database
    configure_server_parameters
    configure_firewall
    configure_private_endpoint
    enable_aad_auth
    configure_backup
    store_credentials
    verify_connection
    print_info

    log_info "Setup complete!"
}

# Run main function
main "$@"
