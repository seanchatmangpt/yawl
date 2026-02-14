#!/bin/bash
set -e

# YAWL Application Initialization Script
# This script runs on compute instance startup

ENVIRONMENT="${environment}"
PROJECT_NAME="${project_name}"
MYSQL_HOST="${mysql_host}"
MYSQL_PORT="${mysql_port}"
MYSQL_DATABASE="${mysql_database}"
MYSQL_USER="${mysql_user}"
MYSQL_PASSWORD_SECRET="${mysql_password_secret}"
REDIS_HOST="${redis_host}"
REDIS_PORT="${redis_port}"

# Logging
LOG_FILE="/var/log/yawl-init.log"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

echo "=== YAWL Application Initialization ==="
echo "Time: $(date)"
echo "Environment: $ENVIRONMENT"
echo "Project: $PROJECT_NAME"

# Update system packages
echo "Updating system packages..."
apt-get update
apt-get upgrade -y

# Install required dependencies
echo "Installing dependencies..."
apt-get install -y \
    curl \
    wget \
    git \
    build-essential \
    python3-pip \
    docker.io \
    docker-compose \
    mysql-client \
    redis-tools \
    jq

# Install Node.js (for YAWL if needed)
echo "Installing Node.js..."
curl -fsSL https://deb.nodesource.com/setup_lts.x | sudo -E bash -
apt-get install -y nodejs

# Create application user
echo "Creating application user..."
useradd -m -d /home/yawl -s /bin/bash yawl || true
usermod -aG docker yawl || true

# Create application directories
echo "Creating application directories..."
mkdir -p /opt/yawl
mkdir -p /var/log/yawl
mkdir -p /var/lib/yawl
chown -R yawl:yawl /opt/yawl /var/log/yawl /var/lib/yawl

# Create environment configuration file
echo "Creating environment configuration..."
cat > /etc/yawl/config.env <<EOF
# YAWL Configuration
ENVIRONMENT=$ENVIRONMENT
PROJECT_NAME=$PROJECT_NAME

# Database Configuration
MYSQL_HOST=$MYSQL_HOST
MYSQL_PORT=$MYSQL_PORT
MYSQL_DATABASE=$MYSQL_DATABASE
MYSQL_USER=$MYSQL_USER
MYSQL_PASSWORD_SECRET=$MYSQL_PASSWORD_SECRET

# Redis Configuration
REDIS_HOST=$REDIS_HOST
REDIS_PORT=$REDIS_PORT

# Application Configuration
NODE_ENV=$ENVIRONMENT
LOG_LEVEL=info
APP_PORT=8080
EOF

# Set proper permissions
chmod 640 /etc/yawl/config.env
chown root:yawl /etc/yawl/config.env

# Wait for database to be ready
echo "Waiting for database to be ready..."
RETRY_COUNT=30
RETRY_DELAY=10
DATABASE_READY=false

for i in $(seq 1 $RETRY_COUNT); do
    if mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD_SECRET" -D "$MYSQL_DATABASE" -e "SELECT 1" > /dev/null 2>&1; then
        echo "Database is ready!"
        DATABASE_READY=true
        break
    else
        echo "Attempt $i/$RETRY_COUNT: Waiting for database... (retry in ${RETRY_DELAY}s)"
        sleep $RETRY_DELAY
    fi
done

if [ "$DATABASE_READY" = false ]; then
    echo "WARNING: Database not ready after $((RETRY_COUNT * RETRY_DELAY)) seconds"
    echo "Application will attempt to connect anyway"
fi

# Create systemd service file for YAWL
echo "Creating systemd service for YAWL..."
cat > /etc/systemd/system/yawl.service <<'SYSTEMD_EOF'
[Unit]
Description=YAWL Application Server
After=network.target mysql.service redis.service
Wants=network-online.target

[Service]
Type=simple
User=yawl
Group=yawl
WorkingDirectory=/opt/yawl
EnvironmentFile=/etc/yawl/config.env
ExecStart=/usr/bin/node /opt/yawl/server.js
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

# Resource limits
LimitNOFILE=65535
LimitNPROC=65535

[Install]
WantedBy=multi-user.target
SYSTEMD_EOF

# Create health check script
echo "Creating health check script..."
cat > /usr/local/bin/yawl-health-check.sh <<'HEALTH_EOF'
#!/bin/bash
# YAWL Health Check Script

# Check if application is responding
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health)

if [ "$HTTP_CODE" = "200" ]; then
    echo "OK"
    exit 0
else
    echo "FAIL: HTTP $HTTP_CODE"
    exit 1
fi
HEALTH_EOF

chmod +x /usr/local/bin/yawl-health-check.sh

# Create cron job for health monitoring
echo "Setting up health monitoring..."
cat > /etc/cron.d/yawl-health <<'CRON_EOF'
*/5 * * * * yawl /usr/local/bin/yawl-health-check.sh >> /var/log/yawl/health-check.log 2>&1
CRON_EOF

# Setup log rotation
echo "Setting up log rotation..."
cat > /etc/logrotate.d/yawl <<'LOGROTATE_EOF'
/var/log/yawl/*.log {
    daily
    rotate 14
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
    create 0640 yawl yawl
}
LOGROTATE_EOF

# Setup monitoring agent (optional - install CloudMonitoring if needed)
echo "Setup monitoring (optional)..."
# Add OCI monitoring agent installation if required

# Initialize database schema (optional - if scripts provided)
if [ -f /opt/yawl/schema.sql ]; then
    echo "Initializing database schema..."
    mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD_SECRET" -D "$MYSQL_DATABASE" < /opt/yawl/schema.sql || true
fi

# Enable and start YAWL service
echo "Enabling YAWL service..."
systemctl daemon-reload
systemctl enable yawl || true

# System information
echo "=== System Information ==="
echo "Hostname: $(hostname)"
echo "IP Address: $(hostname -I)"
echo "Kernel: $(uname -r)"
echo "Memory: $(free -h | grep Mem)"
echo "Disk: $(df -h / | tail -1)"

echo "=== Initialization Complete ==="
echo "Time: $(date)"
echo "YAWL service will start shortly..."

# Note: The YAWL service will be started by instance boot sequence
# or can be manually started with: systemctl start yawl

exit 0
