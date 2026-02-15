#!/bin/bash
# IBM Cloud Databases Setup for YAWL
# This script configures IBM Cloud Databases for PostgreSQL

set -euo pipefail

# Configuration
RESOURCE_GROUP="${RESOURCE_GROUP:-default}"
REGION="${REGION:-us-south}"
SERVICE_NAME="${SERVICE_NAME:-yawl-db}"
PLAN="${PLAN:-standard}"
DATABASE_NAME="${DATABASE_NAME:-yawl}"
ADMIN_USER="${ADMIN_USER:-yawl}"
MEMORY_ALLOCATION_MB="${MEMORY_ALLOCATION_MB:-4096}"
DISK_ALLOCATION_MB="${DISK_ALLOCATION_MB:-10240}"
CPU_ALLOCATION_COUNT="${CPU_ALLOCATION_COUNT:-2}"

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

    if ! command -v ibmcloud &> /dev/null; then
        log_error "IBM Cloud CLI not found. Please install IBM Cloud CLI."
        exit 1
    fi

    # Check IBM Cloud authentication
    if ! ibmcloud target > /dev/null 2>&1; then
        log_error "IBM Cloud not authenticated. Run 'ibmcloud login'."
        exit 1
    fi

    # Install database plugin if not present
    if ! ibmcloud plugin list | grep -q "cloud-databases"; then
        log_info "Installing cloud-databases plugin..."
        ibmcloud plugin install cloud-databases
    fi

    log_info "Prerequisites satisfied."
}

# Set target resource group
set_resource_group() {
    log_info "Setting resource group to: ${RESOURCE_GROUP}"

    ibmcloud target -g "$RESOURCE_GROUP"

    log_info "Resource group set."
}

# Create IBM Cloud Databases for PostgreSQL
create_database() {
    log_info "Creating IBM Cloud Databases for PostgreSQL..."

    # Create service instance
    ibmcloud resource service-instance-create \
        "$SERVICE_NAME" \
        "databases-for-postgresql" \
        "$PLAN" \
        "$REGION" \
        --parameters "{
            \"name\": \"${SERVICE_NAME}\",
            \"memory_allocation_mb\": ${MEMORY_ALLOCATION_MB},
            \"disk_allocation_mb\": ${DISK_ALLOCATION_MB},
            \"cpu_allocation_count\": ${CPU_ALLOCATION_COUNT}
        }"

    log_info "Database service creation initiated."
}

# Wait for database provisioning
wait_for_provisioning() {
    log_info "Waiting for database provisioning..."

    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        STATUS=$(ibmcloud resource service-instance "$SERVICE_NAME" --output json | jq -r '.[0].state')

        if [ "$STATUS" = "active" ]; then
            log_info "Database is active."
            return 0
        fi

        log_info "Current status: $STATUS (attempt $((attempt+1))/$max_attempts)"
        sleep 30
        attempt=$((attempt+1))
    done

    log_error "Database provisioning timed out."
    return 1
}

# Create database and user
setup_database() {
    log_info "Setting up database and user..."

    # Get admin connection strings
    log_info "Retrieving connection information..."

    # Create database
    ibmcloud cdb deployment-task-create "$SERVICE_NAME" "{
        \"type\": \"database\",
        \"database\": {
            \"name\": \"${DATABASE_NAME}\"
        }
    }" || log_warn "Database may already exist."

    # Create user
    USER_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)

    ibmcloud cdb deployment-task-create "$SERVICE_NAME" "{
        \"type\": \"user\",
        \"user\": {
            \"username\": \"${ADMIN_USER}\",
            \"password\": \"${USER_PASSWORD}\"
        }
    }" || log_warn "User may already exist."

    echo "USER_PASSWORD=${USER_PASSWORD}" > /tmp/yawl_ibmdb_password.txt

    log_info "Database and user created."
}

# Configure whitelist (VPC)
configure_whitelist() {
    log_info "Configuring IP whitelist..."

    # Get VPC subnet CIDR
    # For production, configure specific IP ranges

    ibmcloud cdb deployment-whitelist-set "$SERVICE_NAME" "{
        \"ip_addresses\": [
            {
                \"address\": \"10.0.0.0/8\",
                \"description\": \"Internal VPC access\"
            }
        ]
    }" || log_warn "Whitelist configuration may already exist."

    log_info "IP whitelist configured."
}

# Enable autoscaling
configure_autoscaling() {
    log_info "Configuring autoscaling..."

    ibmcloud cdb deployment-autoscaling-set "$SERVICE_NAME" "{
        \"memory\": {
            \"scalers\": {
                \"capacity\": {
                    \"enabled\": true,
                    \"free_space_remaining_percent\": 15
                },
                \"io_utilization\": {
                    \"enabled\": true,
                    \"over_period\": \"30m\",
                    \"above_percent\": 90
                }
            },
            \"rate\": {
                \"increase_percent\": 10,
                \"period_seconds\": 300,
                \"limit_mb_per_member\": 8192,
                \"cooldown_seconds\": 300
            }
        },
        \"disk\": {
            \"scalers\": {
                \"capacity\": {
                    \"enabled\": true,
                    \"free_space_remaining_percent\": 15
                }
            },
            \"rate\": {
                \"increase_percent\": 10,
                \"period_seconds\": 300,
                \"limit_mb_per_member\": 20480,
                \"cooldown_seconds\": 300
            }
        },
        \"cpu\": {
            \"scalers\": {
                \"io_utilization\": {
                    \"enabled\": true,
                    \"over_period\": \"30m\",
                    \"above_percent\": 90
                }
            },
            \"rate\": {
                \"increase_percent\": 10,
                \"period_seconds\": 300,
                \"limit_count_per_member\": 4,
                \"cooldown_seconds\": 300
            }
        }
    }"

    log_info "Autoscaling configured."
}

# Configure backup
configure_backup() {
    log_info "Configuring backup settings..."

    # IBM Cloud Databases automatically handles backups
    # Backups are retained for 30 days by default

    # Get backup information
    ibmcloud cdb deployment-backups "$SERVICE_NAME"

    log_info "Backup configuration verified."
}

# Create service credentials
create_credentials() {
    log_info "Creating service credentials..."

    # Create service key for application access
    ibmcloud resource service-key-create \
        "yawl-db-credentials" \
        "Writer" \
        --instance-name "$SERVICE_NAME" \
        --parameters "{
            \"database\": \"${DATABASE_NAME}\",
            \"username\": \"${ADMIN_USER}\"
        }"

    log_info "Service credentials created."
}

# Get connection information
get_connection_info() {
    log_info "Retrieving connection information..."

    # Get connection strings
    ibmcloud cdb deployment-connections "$SERVICE_NAME" --output json > /tmp/yawl_connections.json

    # Extract PostgreSQL connection string
    POSTGRES_URI=$(cat /tmp/yawl_connections.json | jq -r '.postgres.uri')

    # Get certificate
    ibmcloud cdb deployment-certificate "$SERVICE_NAME" > /tmp/yawl_ibm_certificate.crt

    log_info "Connection information retrieved."
}

# Configure logging
configure_logging() {
    log_info "Configuring logging..."

    # Enable Log Analysis integration
    log_info "Enabling IBM Log Analysis integration..."

    # Create logDNA instance if needed
    ibmcloud resource service-instance-create \
        "yawl-logging" \
        "logdna" \
        "7-day" \
        "$REGION" || true

    log_info "Logging configured."
}

# Configure monitoring
configure_monitoring() {
    log_info "Configuring monitoring..."

    # Enable Sysdig monitoring integration
    log_info "Enabling IBM Cloud Monitoring integration..."

    # Create Sysdig instance if needed
    ibmcloud resource service-instance-create \
        "yawl-monitoring" \
        "sysdig-monitor" \
        "graduated-tier" \
        "$REGION" || true

    log_info "Monitoring configured."
}

# Print connection info
print_info() {
    source /tmp/yawl_ibmdb_password.txt 2>/dev/null || USER_PASSWORD="(check service key)"

    # Get connection details
    POSTGRES_URI=$(ibmcloud cdb deployment-connections "$SERVICE_NAME" --output json | jq -r '.postgres.uri')
    POSTGRES_HOST=$(ibmcloud cdb deployment-connections "$SERVICE_NAME" --output json | jq -r '.postgres.hosts[0].host')
    POSTGRES_PORT=$(ibmcloud cdb deployment-connections "$SERVICE_NAME" --output json | jq -r '.postgres.hosts[0].port')

    echo ""
    echo "========================================"
    echo "IBM Cloud Databases Setup Complete"
    echo "========================================"
    echo ""
    echo "Database Details:"
    echo "  Service Name:  ${SERVICE_NAME}"
    echo "  Database Name: ${DATABASE_NAME}"
    echo "  User:          ${ADMIN_USER}"
    echo "  Host:          ${POSTGRES_HOST}"
    echo "  Port:          ${POSTGRES_PORT}"
    echo ""
    echo "Connection URI:"
    echo "  ${POSTGRES_URI}"
    echo ""
    echo "JDBC URL:"
    echo "  jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${DATABASE_NAME}?sslmode=verify-full&sslrootcert=/tmp/yawl_ibm_certificate.crt"
    echo ""
    echo "Spring Boot Configuration:"
    echo "  spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${DATABASE_NAME}?sslmode=verify-full"
    echo "  spring.datasource.username=${ADMIN_USER}"
    echo ""
    echo "Service Credentials:"
    echo "  ibmcloud resource service-key yawl-db-credentials"
    echo ""
    echo "Management Commands:"
    echo "  ibmcloud cdb deployment-show ${SERVICE_NAME}"
    echo "  ibmcloud cdb deployment-connections ${SERVICE_NAME}"
    echo "  ibmcloud cdb deployment-backups ${SERVICE_NAME}"
    echo ""
    echo "Scaling Commands:"
    echo "  ibmcloud cdb deployment-scale ${SERVICE_NAME} --memory 8192 --disk 20480"
    echo ""

    # Cleanup
    rm -f /tmp/yawl_ibmdb_password.txt /tmp/yawl_connections.json
}

# Main execution
main() {
    log_info "Starting IBM Cloud Databases setup..."

    check_prerequisites
    set_resource_group
    create_database
    wait_for_provisioning
    setup_database
    configure_whitelist
    configure_autoscaling
    configure_backup
    create_credentials
    get_connection_info
    configure_logging
    configure_monitoring
    print_info

    log_info "Setup complete!"
}

# Run main function
main "$@"
