#!/bin/bash
# Cloud SQL Proxy Setup for YAWL on GCP
# This script configures Cloud SQL Proxy for secure database connectivity

set -euo pipefail

# Configuration
PROJECT_ID="${GCP_PROJECT_ID:-my-project}"
REGION="${GCP_REGION:-us-central1}"
INSTANCE_NAME="${CLOUD_SQL_INSTANCE:-yawl-db}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PORT="${DB_PORT:-5432}"
PROXY_PORT="${PROXY_PORT:-5432}"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:-yawl-db-proxy@${PROJECT_ID}.iam.gserviceaccount.com}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

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

    # Check gcloud CLI
    if ! command -v gcloud &> /dev/null; then
        log_error "gcloud CLI not found. Please install Google Cloud SDK."
        exit 1
    fi

    # Check Cloud SQL Proxy
    if ! command -v cloud_sql_proxy &> /dev/null; then
        log_info "Installing Cloud SQL Proxy..."
        curl -o cloud_sql_proxy https://dl.google.com/cloudsql/cloud_sql_proxy.linux.amd64
        chmod +x cloud_sql_proxy
        sudo mv cloud_sql_proxy /usr/local/bin/
    fi

    log_info "Prerequisites satisfied."
}

# Create Cloud SQL instance
create_instance() {
    log_info "Creating Cloud SQL instance..."

    gcloud sql instances create "${INSTANCE_NAME}" \
        --project="${PROJECT_ID}" \
        --region="${REGION}" \
        --database-version=POSTGRES_14 \
        --tier=db-custom-2-8192 \
        --storage-type=SSD \
        --storage-size=100GB \
        --storage-auto-increase \
        --backup-start-time=02:00 \
        --enable-point-in-time-recovery \
        --maintenance-window-day=SUN \
        --maintenance-window-hour=03 \
        --database-flags=max_connections=200,shared_buffers=256MB \
        --availability-type=REGIONAL \
        --deletion-protection

    log_info "Cloud SQL instance created: ${INSTANCE_NAME}"
}

# Create database and user
setup_database() {
    log_info "Setting up database and user..."

    # Create database
    gcloud sql databases create "${DB_NAME}" \
        --instance="${INSTANCE_NAME}" \
        --project="${PROJECT_ID}"

    # Create user with IAM authentication
    gcloud sql users create "${DB_USER}" \
        --instance="${INSTANCE_NAME}" \
        --project="${PROJECT_ID}" \
        --password="$(openssl rand -base64 24)"

    log_info "Database and user created."
}

# Configure private IP
setup_private_ip() {
    log_info "Configuring private IP connectivity..."

    # Create VPC network if not exists
    gcloud compute networks create yawl-vpc \
        --project="${PROJECT_ID}" \
        --subnet-mode=custom \
        --bgp-routing-mode=regional || true

    # Allocate IP range for Cloud SQL
    gcloud compute addresses create google-managed-services-yawl \
        --project="${PROJECT_ID}" \
        --global \
        --purpose=VPC_PEERING \
        --prefix-length=16 \
        --network=projects/${PROJECT_ID}/global/networks/yawl-vpc || true

    # Create private connection
    gcloud services vpc-peerings connect \
        --project="${PROJECT_ID}" \
        --service=servicenetworking.googleapis.com \
        --ranges=google-managed-services-yawl \
        --network=yawl-vpc || true

    # Enable private IP on Cloud SQL instance
    gcloud sql instances patch "${INSTANCE_NAME}" \
        --project="${PROJECT_ID}" \
        --network=projects/${PROJECT_ID}/global/networks/yawl-vpc \
        --no-assign-ip

    log_info "Private IP configured."
}

# Setup IAM authentication
setup_iam_auth() {
    log_info "Configuring IAM database authentication..."

    # Enable Cloud SQL IAM authentication
    gcloud sql instances patch "${INSTANCE_NAME}" \
        --project="${PROJECT_ID}" \
        --database-flags=cloudsql.iam_authentication=on

    # Create service account for proxy
    gcloud iam service-accounts create yawl-db-proxy \
        --project="${PROJECT_ID}" \
        --display-name="YAWL Database Proxy Service Account" || true

    # Grant Cloud SQL Client role
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${SERVICE_ACCOUNT}" \
        --role="roles/cloudsql.client"

    # Grant Cloud SQL Instance User role
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${SERVICE_ACCOUNT}" \
        --role="roles/cloudsql.instanceUser"

    log_info "IAM authentication configured."
}

# Generate proxy configuration
generate_proxy_config() {
    log_info "Generating proxy configuration..."

    INSTANCE_CONNECTION_NAME="${PROJECT_ID}:${REGION}:${INSTANCE_NAME}"

    cat > /etc/cloud-sql-proxy/config.yaml <<EOF
# Cloud SQL Proxy Configuration for YAWL
instance_connection_name: "${INSTANCE_CONNECTION_NAME}"
proxy_port: ${PROXY_PORT}
database: ${DB_NAME}
user: ${DB_USER}

# Connection pool settings
max_connections: 100
max_idle_connections: 10
connection_timeout: 30s

# Health check settings
health_check_port: 9090
health_check_path: /health

# TLS settings
enable_iam_auth: true
EOF

    # Generate systemd service file
    cat > /etc/systemd/system/cloud-sql-proxy.service <<EOF
[Unit]
Description=Cloud SQL Proxy for YAWL
After=network.target

[Service]
Type=simple
ExecStart=/usr/local/bin/cloud_sql_proxy \\
    --instances=${INSTANCE_CONNECTION_NAME}=tcp:${PROXY_PORT} \\
    --credential_file=/etc/cloud-sql-proxy/service-account.json \\
    --enable_iam_login \\
    --max_connections=100 \\
    --term_timeout=30s

Restart=always
RestartSec=5

User=postgres
Group=postgres

[Install]
WantedBy=multi-user.target
EOF

    log_info "Proxy configuration generated."
}

# Download service account key
download_credentials() {
    log_info "Downloading service account credentials..."

    mkdir -p /etc/cloud-sql-proxy

    gcloud iam service-accounts keys create /etc/cloud-sql-proxy/service-account.json \
        --project="${PROJECT_ID}" \
        --iam-account="${SERVICE_ACCOUNT}"

    chmod 600 /etc/cloud-sql-proxy/service-account.json

    log_info "Credentials downloaded."
}

# Start proxy service
start_proxy() {
    log_info "Starting Cloud SQL Proxy service..."

    systemctl daemon-reload
    systemctl enable cloud-sql-proxy
    systemctl start cloud-sql-proxy

    sleep 5

    if systemctl is-active --quiet cloud-sql-proxy; then
        log_info "Cloud SQL Proxy is running."
    else
        log_error "Failed to start Cloud SQL Proxy."
        journalctl -u cloud-sql-proxy -n 50
        exit 1
    fi
}

# Verify connectivity
verify_connection() {
    log_info "Verifying database connectivity..."

    # Wait for proxy to be ready
    sleep 10

    # Test connection
    if PGPASSWORD="${DB_PASSWORD:-}" psql \
        --host=127.0.0.1 \
        --port="${PROXY_PORT}" \
        --username="${DB_USER}" \
        --dbname="${DB_NAME}" \
        --command="SELECT version();" > /dev/null 2>&1; then
        log_info "Database connection successful!"
    else
        log_warn "Could not verify connection. Please check credentials."
    fi
}

# Print connection info
print_info() {
    echo ""
    echo "========================================"
    echo "Cloud SQL Proxy Setup Complete"
    echo "========================================"
    echo ""
    echo "Connection Details:"
    echo "  Instance: ${PROJECT_ID}:${REGION}:${INSTANCE_NAME}"
    echo "  Database: ${DB_NAME}"
    echo "  Host:     127.0.0.1"
    echo "  Port:     ${PROXY_PORT}"
    echo "  User:     ${DB_USER}"
    echo ""
    echo "JDBC URL:"
    echo "  jdbc:postgresql://127.0.0.1:${PROXY_PORT}/${DB_NAME}"
    echo ""
    echo "Connection String (Spring Boot):"
    echo "  spring.datasource.url=jdbc:postgresql://127.0.0.1:${PROXY_PORT}/${DB_NAME}"
    echo "  spring.datasource.username=${DB_USER}"
    echo ""
    echo "To manage the proxy:"
    echo "  sudo systemctl status cloud-sql-proxy"
    echo "  sudo systemctl restart cloud-sql-proxy"
    echo "  sudo systemctl stop cloud-sql-proxy"
    echo ""
}

# Main execution
main() {
    log_info "Starting Cloud SQL Proxy setup..."

    check_prerequisites
    create_instance
    setup_database
    setup_private_ip
    setup_iam_auth
    generate_proxy_config
    download_credentials
    start_proxy
    verify_connection
    print_info

    log_info "Setup complete!"
}

# Run main function
main "$@"
