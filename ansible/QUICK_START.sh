#!/bin/bash
# Quick Start Script for YAWL Ansible Deployment
# This script automates the initial setup process

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
ANSIBLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INVENTORY_FILE="$ANSIBLE_DIR/inventory.ini"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}YAWL Ansible Deployment - Quick Start${NC}"
echo -e "${GREEN}========================================${NC}"
echo

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Step 1: Check prerequisites
echo -e "${YELLOW}[Step 1] Checking prerequisites...${NC}"

if ! command_exists ansible; then
    echo -e "${RED}❌ Ansible is not installed${NC}"
    echo "Install it with: sudo apt-get install -y ansible"
    exit 1
fi

if ! command_exists python3; then
    echo -e "${RED}❌ Python3 is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Ansible $(ansible --version | head -1)${NC}"
echo -e "${GREEN}✓ Python $(python3 --version)${NC}"
echo

# Step 2: Verify Ansible structure
echo -e "${YELLOW}[Step 2] Verifying Ansible directory structure...${NC}"

required_dirs=(
    "roles/postgres/tasks"
    "roles/docker/tasks"
    "roles/tomcat/tasks"
    "roles/monitoring/tasks"
    "group_vars"
    "host_vars"
)

for dir in "${required_dirs[@]}"; do
    if [ -d "$ANSIBLE_DIR/$dir" ]; then
        echo -e "${GREEN}✓ $dir${NC}"
    else
        echo -e "${RED}❌ Missing directory: $dir${NC}"
        exit 1
    fi
done
echo

# Step 3: Verify inventory file
echo -e "${YELLOW}[Step 3] Checking inventory file...${NC}"

if [ ! -f "$INVENTORY_FILE" ]; then
    echo -e "${RED}❌ Inventory file not found: $INVENTORY_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Inventory file found${NC}"
echo

# Step 4: Get IP addresses for setup
echo -e "${YELLOW}[Step 4] Updating inventory IPs...${NC}"

read -p "Enter Primary DB IP (e.g., 10.0.1.10): " DB_PRIMARY_IP
read -p "Enter Secondary DB IP (e.g., 10.0.1.11): " DB_SECONDARY_IP
read -p "Enter App Node 1 IP (e.g., 10.0.2.10): " APP_NODE1_IP
read -p "Enter App Node 2 IP (e.g., 10.0.2.11): " APP_NODE2_IP
read -p "Enter App Node 3 IP (e.g., 10.0.2.12): " APP_NODE3_IP
read -p "Enter Monitoring Server IP (e.g., 10.0.3.10): " MONITOR_IP

# Backup original inventory
cp "$INVENTORY_FILE" "$INVENTORY_FILE.bak"
echo -e "${GREEN}✓ Backed up inventory to $INVENTORY_FILE.bak${NC}"

# Update inventory
sed -i "s/10\.0\.1\.10/$DB_PRIMARY_IP/g" "$INVENTORY_FILE"
sed -i "s/10\.0\.1\.11/$DB_SECONDARY_IP/g" "$INVENTORY_FILE"
sed -i "s/10\.0\.2\.10/$APP_NODE1_IP/g" "$INVENTORY_FILE"
sed -i "s/10\.0\.2\.11/$APP_NODE2_IP/g" "$INVENTORY_FILE"
sed -i "s/10\.0\.2\.12/$APP_NODE3_IP/g" "$INVENTORY_FILE"
sed -i "s/10\.0\.3\.10/$MONITOR_IP/g" "$INVENTORY_FILE"

echo -e "${GREEN}✓ Updated inventory with provided IPs${NC}"
echo

# Step 5: Setup SSH keys
echo -e "${YELLOW}[Step 5] Setting up SSH access...${NC}"

IPS=("$DB_PRIMARY_IP" "$DB_SECONDARY_IP" "$APP_NODE1_IP" "$APP_NODE2_IP" "$APP_NODE3_IP" "$MONITOR_IP")

for ip in "${IPS[@]}"; do
    echo -n "Testing SSH to $ip... "
    if ssh -o ConnectTimeout=5 -o BatchMode=yes ubuntu@"$ip" "echo 'OK'" &>/dev/null; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${RED}❌ Cannot connect to $ip${NC}"
        echo "  Run: ssh-copy-id -i ~/.ssh/id_rsa.pub ubuntu@$ip"
    fi
done
echo

# Step 6: Verify inventory with Ansible
echo -e "${YELLOW}[Step 6] Verifying inventory with Ansible...${NC}"

if ansible all -i "$INVENTORY_FILE" --list-hosts > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Inventory validation successful${NC}"
    echo "  Hosts found:"
    ansible all -i "$INVENTORY_FILE" --list-hosts | sed 's/^/    /'
else
    echo -e "${RED}❌ Inventory validation failed${NC}"
    exit 1
fi
echo

# Step 7: Create vault file
echo -e "${YELLOW}[Step 7] Setting up Ansible Vault for secrets...${NC}"

VAULT_FILE="$ANSIBLE_DIR/group_vars/vault-secrets.yml"

if [ ! -f "$VAULT_FILE" ]; then
    echo "Creating vault file: $VAULT_FILE"

    read -sp "Enter PostgreSQL password: " POSTGRES_PASS
    echo
    read -sp "Enter Grafana admin password: " GRAFANA_PASS
    echo
    read -sp "Enter Redis password: " REDIS_PASS
    echo

    # Create vault file
    cat > "$VAULT_FILE" << EOF
---
vault_postgres_password: "$POSTGRES_PASS"
vault_postgres_admin_password: "$(openssl rand -base64 12)"
vault_postgres_monitoring_password: "$(openssl rand -base64 12)"
vault_postgres_replication_password: "$(openssl rand -base64 12)"
vault_yawl_db_password: "$POSTGRES_PASS"
vault_redis_password: "$REDIS_PASS"
vault_grafana_admin_password: "$GRAFANA_PASS"
vault_tomcat_ssl_key_password: "$(openssl rand -base64 12)"
vault_slack_webhook_url: "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
EOF

    chmod 600 "$VAULT_FILE"
    echo -e "${GREEN}✓ Vault file created and secured${NC}"
else
    echo -e "${GREEN}✓ Vault file already exists${NC}"
fi
echo

# Step 8: Syntax check
echo -e "${YELLOW}[Step 8] Validating Ansible playbook syntax...${NC}"

if ansible-playbook --syntax-check -i "$INVENTORY_FILE" "$ANSIBLE_DIR/playbook.yml" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Playbook syntax is valid${NC}"
else
    echo -e "${RED}❌ Playbook has syntax errors${NC}"
    ansible-playbook --syntax-check -i "$INVENTORY_FILE" "$ANSIBLE_DIR/playbook.yml"
    exit 1
fi
echo

# Step 9: Summary and next steps
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ Quick Start Setup Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo
echo "Next steps:"
echo
echo "1. Review and customize variables:"
echo "   - group_vars/db_servers.yml"
echo "   - group_vars/app_servers.yml"
echo "   - group_vars/monitoring_servers.yml"
echo "   - host_vars/*.yml"
echo
echo "2. Perform a dry run:"
echo "   ansible-playbook -i $INVENTORY_FILE playbook.yml --check --ask-vault-pass"
echo
echo "3. Deploy database servers:"
echo "   ansible-playbook -i $INVENTORY_FILE playbook.yml --limit db_servers --ask-vault-pass"
echo
echo "4. Deploy application servers:"
echo "   ansible-playbook -i $INVENTORY_FILE playbook.yml --limit app_servers --ask-vault-pass"
echo
echo "5. Deploy monitoring stack:"
echo "   ansible-playbook -i $INVENTORY_FILE playbook.yml --limit monitoring_servers --ask-vault-pass"
echo
echo "For detailed instructions, see: DEPLOYMENT_GUIDE.md"
echo "For full documentation, see: README.md"
echo
