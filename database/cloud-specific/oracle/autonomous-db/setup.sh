#!/bin/bash
# Oracle Cloud Autonomous Database Setup for YAWL
# This script configures Oracle Autonomous Database for YAWL

set -euo pipefail

# Configuration
COMPARTMENT_ID="${COMPARTMENT_ID:-}"
DISPLAY_NAME="${DISPLAY_NAME:-yawl-db}"
DB_NAME="${DB_NAME:-YAWLDB}"
DB_WORKLOAD="${DB_WORKLOAD:-OLTP}"
CPU_CORE_COUNT="${CPU_CORE_COUNT:-2}"
DATA_STORAGE_SIZE_IN_TBS="${DATA_STORAGE_SIZE_IN_TBS:-1}"
DB_VERSION="${DB_VERSION:-19c}"
LICENSE_MODEL="${LICENSE_MODEL:-LICENSE_INCLUDED}"
IS_AUTO_SCALING_ENABLED="${IS_AUTO_SCALING_ENABLED:-true}"
IS_DEDICATED="${IS_DEDICATED:-false}"
SUBNET_ID="${SUBNET_ID:-}"
PRIVATE_ENDPOINT_LABEL="${PRIVATE_ENDPOINT_LABEL:-yawldb}"

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

    if ! command -v oci &> /dev/null; then
        log_error "OCI CLI not found. Please install Oracle Cloud Infrastructure CLI."
        exit 1
    fi

    # Check OCI configuration
    if ! oci os ns get > /dev/null 2>&1; then
        log_error "OCI CLI not configured. Run 'oci setup config'."
        exit 1
    fi

    # Check for compartment ID
    if [ -z "$COMPARTMENT_ID" ]; then
        # Try to get root compartment (tenancy)
        COMPARTMENT_ID=$(oci os ns get --query 'data' --raw-output)
        log_warn "Using root compartment: $COMPARTMENT_ID"
    fi

    log_info "Prerequisites satisfied."
}

# Create or get VCN
setup_vcn() {
    log_info "Setting up Virtual Cloud Network..."

    VCN_ID=$(oci network vcn create \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "yawl-vcn" \
        --cidr-block "10.0.0.0/16" \
        --dns-label "yawlvcn" \
        --query 'data.id' \
        --raw-output) || VCN_ID=$(oci network vcn list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "yawl-vcn" \
        --query 'data[0].id' \
        --raw-output)

    log_info "VCN ID: $VCN_ID"

    # Create subnet
    SUBNET_ID=$(oci network subnet create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "yawl-db-subnet" \
        --cidr-block "10.0.1.0/24" \
        --prohibit-public-ip-on-vnic true \
        --query 'data.id' \
        --raw-output) || SUBNET_ID=$(oci network subnet list \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "yawl-db-subnet" \
        --query 'data[0].id' \
        --raw-output)

    log_info "Subnet ID: $SUBNET_ID"

    # Create security list
    SECURITY_LIST_ID=$(oci network security-list create \
        --compartment-id "$COMPARTMENT_ID" \
        --vcn-id "$VCN_ID" \
        --display-name "yawl-db-security-list" \
        --egress-security-rules '[{"destination":"0.0.0.0/0","protocol":"all"}]' \
        --ingress-security-rules '[{"source":"10.0.0.0/16","protocol":"6","tcp-options":{"destinationPortRange":{"min":1521,"max":1522}}}]' \
        --query 'data.id' \
        --raw-output) || true

    log_info "VCN setup complete."
}

# Create Autonomous Database
create_autonomous_db() {
    log_info "Creating Autonomous Database..."

    # Generate admin password
    ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=@#' | head -c 16)$(openssl rand -hex 4)

    oci adb autonomous-database create \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$DISPLAY_NAME" \
        --db-name "$DB_NAME" \
        --db-workload "$DB_WORKLOAD" \
        --cpu-core-count "$CPU_CORE_COUNT" \
        --data-storage-size-in-tbs "$DATA_STORAGE_SIZE_IN_TBS" \
        --admin-password "$ADMIN_PASSWORD" \
        --db-version "$DB_VERSION" \
        --license-model "$LICENSE_MODEL" \
        --is-auto-scaling-enabled "$IS_AUTO_SCALING_ENABLED" \
        --is-dedicated "$IS_DEDICATED" \
        --subnet-id "$SUBNET_ID" \
        --private-endpoint-label "$PRIVATE_ENDPOINT_LABEL" \
        --is-mtls-connection-required false \
        --whitelisted-ips '[]' \
        --query 'data.id' \
        --raw-output

    log_info "Autonomous Database creation initiated."

    # Store admin password
    echo "ADMIN_PASSWORD=${ADMIN_PASSWORD}" > /tmp/yawl_adb_password.txt
}

# Wait for database provisioning
wait_for_provisioning() {
    log_info "Waiting for database provisioning..."

    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        STATUS=$(oci adb autonomous-database list \
            --compartment-id "$COMPARTMENT_ID" \
            --display-name "$DISPLAY_NAME" \
            --query 'data[0]."lifecycle-state"' \
            --raw-output)

        if [ "$STATUS" = "AVAILABLE" ]; then
            log_info "Database is available."
            return 0
        fi

        log_info "Current status: $STATUS (attempt $((attempt+1))/$max_attempts)"
        sleep 60
        attempt=$((attempt+1))
    done

    log_error "Database provisioning timed out."
    return 1
}

# Get connection details
get_connection_details() {
    log_info "Getting connection details..."

    DB_ID=$(oci adb autonomous-database list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$DISPLAY_NAME" \
        --query 'data[0].id' \
        --raw-output)

    # Get connection strings
    CONNECTION_STRINGS=$(oci adb autonomous-database get \
        --autonomous-database-id "$DB_ID" \
        --query 'data."connection-strings"."profiles"' \
        --raw-output)

    # Get wallet
    log_info "Downloading connection wallet..."

    WALLET_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=')

    oci adb autonomous-database generate-wallet \
        --autonomous-database-id "$DB_ID" \
        --password "$WALLET_PASSWORD" \
        --file /tmp/yawl_wallet.zip

    log_info "Wallet downloaded to /tmp/yawl_wallet.zip"
    echo "WALLET_PASSWORD=${WALLET_PASSWORD}" >> /tmp/yawl_adb_password.txt
}

# Configure backup retention
configure_backup() {
    log_info "Configuring backup retention..."

    DB_ID=$(oci adb autonomous-database list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$DISPLAY_NAME" \
        --query 'data[0].id' \
        --raw-output)

    oci adb autonomous-database update \
        --autonomous-database-id "$DB_ID" \
        --backup-retention-period-in-days 30

    log_info "Backup retention configured to 30 days."
}

# Create user for YAWL application
create_app_user() {
    log_info "Creating YAWL application user..."

    DB_ID=$(oci adb autonomous-database list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$DISPLAY_NAME" \
        --query 'data[0].id' \
        --raw-output)

    APP_PASSWORD=$(openssl rand -base64 16 | tr -d '/+=')

    # Note: This requires executing SQL via the console or a connected client
    # Here we store the credentials for manual execution

    cat > /tmp/create_yawl_user.sql <<EOF
-- Run this SQL after connecting to the database as ADMIN

-- Create YAWL user
CREATE USER YAWL IDENTIFIED BY "${APP_PASSWORD}";
GRANT CONNECT, RESOURCE TO YAWL;
GRANT CREATE SESSION TO YAWL;
GRANT CREATE TABLE TO YAWL;
GRANT CREATE SEQUENCE TO YAWL;
GRANT CREATE PROCEDURE TO YAWL;
GRANT CREATE TRIGGER TO YAWL;
GRANT CREATE VIEW TO YAWL;
GRANT CREATE TYPE TO YAWL;
GRANT UNLIMITED TABLESPACE TO YAWL;

-- Grant additional privileges for YAWL operations
GRANT EXECUTE ON DBMS_CRYPTO TO YAWL;
GRANT EXECUTE ON DBMS_LOB TO YAWL;
GRANT EXECUTE ON DBMS_SQL TO YAWL;
EOF

    echo "APP_PASSWORD=${APP_PASSWORD}" >> /tmp/yawl_adb_password.txt

    log_info "SQL script created at /tmp/create_yawl_user.sql"
    log_warn "Please execute the SQL script manually after connecting to the database."
}

# Print connection info
print_info() {
    DB_ID=$(oci adb autonomous-database list \
        --compartment-id "$COMPARTMENT_ID" \
        --display-name "$DISPLAY_NAME" \
        --query 'data[0].id' \
        --raw-output)

    DB_OCID=$(oci adb autonomous-database get \
        --autonomous-database-id "$DB_ID" \
        --query 'data.id' \
        --raw-output)

    PRIVATE_ENDPOINT=$(oci adb autonomous-database get \
        --autonomous-database-id "$DB_ID" \
        --query 'data."private-endpoint"' \
        --raw-output)

    source /tmp/yawl_adb_password.txt

    echo ""
    echo "========================================"
    echo "Oracle Autonomous Database Setup Complete"
    echo "========================================"
    echo ""
    echo "Database Details:"
    echo "  Name:      ${DISPLAY_NAME}"
    echo "  DB Name:   ${DB_NAME}"
    echo "  OCID:      ${DB_OCID}"
    echo "  Version:   ${DB_VERSION}"
    echo "  Workload:  ${DB_WORKLOAD}"
    echo ""
    echo "Connection Details:"
    echo "  Private Endpoint: ${PRIVATE_ENDPOINT}"
    echo "  Port:             1521"
    echo "  Service:          ${DB_NAME}_HIGH (for high priority)"
    echo "                    ${DB_NAME}_MEDIUM (for standard)"
    echo "                    ${DB_NAME}_LOW (for batch)"
    echo ""
    echo "JDBC URL (using wallet):"
    echo "  jdbc:oracle:thin:@${DB_NAME}_high?TNS_ADMIN=/path/to/wallet"
    echo ""
    echo "JDBC URL (without wallet):"
    echo "  jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=${PRIVATE_ENDPOINT})(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=${DB_NAME}_HIGH)))"
    echo ""
    echo "Credentials:"
    echo "  Admin User: ADMIN"
    echo "  Wallet Password: ${WALLET_PASSWORD}"
    echo "  Wallet File: /tmp/yawl_wallet.zip"
    echo ""
    echo "Management Commands:"
    echo "  oci adb autonomous-database get --autonomous-database-id ${DB_OCID}"
    echo "  oci adb autonomous-database list --compartment-id ${COMPARTMENT_ID}"
    echo ""
    echo "Next Steps:"
    echo "  1. Download and extract the wallet"
    echo "  2. Execute /tmp/create_yawl_user.sql to create the YAWL user"
    echo "  3. Configure your application with the wallet credentials"
    echo ""

    # Cleanup
    rm -f /tmp/yawl_adb_password.txt
}

# Main execution
main() {
    log_info "Starting Oracle Autonomous Database setup..."

    check_prerequisites
    setup_vcn
    create_autonomous_db
    wait_for_provisioning
    get_connection_details
    configure_backup
    create_app_user
    print_info

    log_info "Setup complete!"
}

# Run main function
main "$@"
