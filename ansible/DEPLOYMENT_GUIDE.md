# YAWL Multi-Node Deployment Guide

Complete step-by-step guide for deploying YAWL using Ansible.

## Phase 1: Pre-Deployment Preparation

### Step 1.1: Infrastructure Provisioning

Set up your infrastructure with the following nodes:

```
Database Layer:
  - db-primary (10.0.1.10): PostgreSQL Primary
  - db-secondary (10.0.1.11): PostgreSQL Standby

Application Layer:
  - app-node-1 (10.0.2.10): YAWL Application Server
  - app-node-2 (10.0.2.11): YAWL Application Server
  - app-node-3 (10.0.2.12): YAWL Application Server

Monitoring/Management Layer:
  - monitor-primary (10.0.3.10): Monitoring Stack
```

### Step 1.2: Network Configuration

Ensure the following:
- All nodes are on the same VPC/network
- Security groups allow inter-node communication
- SSH access to all nodes from your control machine
- Outbound internet access for package downloads

### Step 1.3: Control Machine Setup

On your local machine or control node:

```bash
# Install Ansible
sudo apt-get update
sudo apt-get install -y ansible python3-psycopg2 python3-pymongo

# Verify installation
ansible --version

# Clone/copy the ansible directory
cd /home/user/yawl/ansible
```

## Phase 2: Ansible Configuration

### Step 2.1: Update Inventory

Edit `inventory.ini` with your actual IP addresses:

```bash
nano inventory.ini
```

Replace placeholder IPs with your actual infrastructure IPs.

### Step 2.2: Configure SSH Access

```bash
# Copy SSH key to all nodes
for ip in 10.0.1.10 10.0.1.11 10.0.2.10 10.0.2.11 10.0.2.12 10.0.3.10; do
  ssh-copy-id -i ~/.ssh/id_rsa.pub ubuntu@$ip
done

# Test connectivity
ansible all -i inventory.ini -m ping
```

Expected output:
```
db-primary | SUCCESS => {
    "ping": "pong"
}
db-secondary | SUCCESS => {
    "ping": "pong"
}
...
```

### Step 2.3: Setup Ansible Vault for Secrets

```bash
# Create vault file
ansible-vault create group_vars/vault-secrets.yml

# Add the following content (customize passwords):
---
vault_postgres_password: "your_secure_yawl_db_password"
vault_postgres_admin_password: "your_secure_postgres_admin_password"
vault_postgres_monitoring_password: "your_secure_monitoring_password"
vault_postgres_replication_password: "your_secure_replication_password"
vault_yawl_db_password: "your_secure_yawl_app_password"
vault_redis_password: "your_secure_redis_password"
vault_grafana_admin_password: "your_secure_grafana_password"
vault_tomcat_ssl_key_password: "your_secure_ssl_password"
vault_slack_webhook_url: "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"

# Verify vault file
ansible-vault view group_vars/vault-secrets.yml
```

### Step 2.4: Customize Group Variables

Update deployment-specific variables:

```bash
# Database configuration
nano group_vars/db_servers.yml

# Application servers
nano group_vars/app_servers.yml

# Monitoring servers
nano group_vars/monitoring_servers.yml
```

Key configurations to customize:
- `postgres_max_connections`: Adjust based on expected load
- `java_heap_max`: Adjust based on server RAM
- `prometheus_retention_time`: Log retention period
- `email_smarthost`: For alert notifications

### Step 2.5: Customize Host Variables

Update host-specific configurations:

```bash
# Primary database
nano host_vars/db-primary.yml

# Secondary database
nano host_vars/db-secondary.yml

# App nodes
nano host_vars/app-node-1.yml
nano host_vars/app-node-2.yml
nano host_vars/app-node-3.yml

# Monitoring server
nano host_vars/monitor-primary.yml
```

## Phase 3: Deployment Execution

### Step 3.1: Syntax Validation

```bash
# Check playbook syntax
ansible-playbook --syntax-check -i inventory.ini playbook.yml

# Expected output:
# playbook.yml syntax is OK
```

### Step 3.2: Dry Run (Check Mode)

```bash
# Execute in check mode to preview changes
ansible-playbook -i inventory.ini playbook.yml --check --ask-vault-pass

# This shows what will be changed without making actual changes
```

### Step 3.3: Deploy Database Servers

```bash
# Deploy PostgreSQL infrastructure first
ansible-playbook -i inventory.ini playbook.yml \
  --limit db_servers \
  --ask-vault-pass \
  -v

# Wait for PostgreSQL to be fully initialized
# Check replication status on primary:
ansible db-primary -i inventory.ini -m shell \
  -a "psql -U postgres -d yawl -c 'SELECT * FROM pg_stat_replication;'" \
  --ask-vault-pass
```

### Step 3.4: Deploy Application Servers

```bash
# Deploy Docker and Tomcat after database is ready
ansible-playbook -i inventory.ini playbook.yml \
  --limit app_servers \
  --ask-vault-pass \
  -v

# Wait for all three nodes to complete
# Typical deployment time: 15-20 minutes per node
```

### Step 3.5: Deploy Monitoring Stack

```bash
# Deploy monitoring infrastructure
ansible-playbook -i inventory.ini playbook.yml \
  --limit monitoring_servers \
  --ask-vault-pass \
  -v

# Typical deployment time: 10-15 minutes
```

### Step 3.6: Full Deployment (All Components)

```bash
# If you prefer to deploy everything at once:
ansible-playbook -i inventory.ini playbook.yml \
  --ask-vault-pass \
  -v

# Total deployment time: 45-60 minutes
```

## Phase 4: Post-Deployment Validation

### Step 4.1: Verify Service Status

```bash
# Check all services are running
ansible all -i inventory.ini -m systemd \
  -a "name=postgresql,docker,tomcat,prometheus,grafana-server state=started" \
  --ask-vault-pass

# Check service status
ansible all -i inventory.ini -m shell \
  -a "systemctl status postgresql docker tomcat prometheus grafana-server 2>/dev/null | grep -E 'active|failed'" \
  --ask-vault-pass
```

### Step 4.2: Database Verification

```bash
# Verify PostgreSQL primary is running
ssh ubuntu@10.0.1.10 "psql -U postgres -d yawl -c 'SELECT version();'"

# Verify replication
ssh ubuntu@10.0.1.10 "psql -U postgres -d yawl -c 'SELECT pid, usename, application_name, state FROM pg_stat_replication;'"

# Expected output: One row showing standby connected and streaming
```

### Step 4.3: Application Server Verification

```bash
# Test YAWL health endpoint on each app node
for node in 10.0.2.10 10.0.2.11 10.0.2.12; do
  echo "Testing node $node..."
  curl -s http://$node:8080/resourceService/ | head -5
done

# Expected: XML response with YAWL service information
```

### Step 4.4: Database Connectivity from App Servers

```bash
# Verify app servers can connect to database
ansible app_servers -i inventory.ini -m shell \
  -a "psql -h 10.0.1.10 -U yawl -d yawl -c 'SELECT 1;'" \
  --ask-vault-pass

# Expected output: (1 row)
```

### Step 4.5: Monitoring Stack Verification

```bash
# Verify Prometheus can reach targets
curl -s http://10.0.3.10:9090/api/v1/targets | jq '.data.activeTargets | length'

# Verify Grafana dashboards
curl -s http://10.0.3.10:3000/api/health | jq '.'

# Verify Elasticsearch cluster health
curl -s http://10.0.3.10:9200/_cluster/health | jq '.status'

# Expected output: "green" or "yellow" (not "red")
```

## Phase 5: Application Deployment

### Step 5.1: Deploy YAWL WAR File

```bash
# Copy YAWL WAR file to app servers
ansible app_servers -i inventory.ini \
  -m copy \
  -a "src=/path/to/yawl.war dest=/usr/local/tomcat/webapps/" \
  --ask-vault-pass

# Restart Tomcat to deploy application
ansible app_servers -i inventory.ini \
  -m systemd \
  -a "name=tomcat state=restarted" \
  --ask-vault-pass

# Wait 30-60 seconds for Tomcat to start
sleep 60
```

### Step 5.2: Verify Application Deployment

```bash
# Check YAWL logs for startup messages
for node in app-node-1 app-node-2 app-node-3; do
  echo "=== $node ==="
  ansible $node -i inventory.ini -m shell \
    -a "tail -20 /usr/local/tomcat/logs/catalina.out" \
    --ask-vault-pass
done

# Check application health
for node in 10.0.2.10 10.0.2.11 10.0.2.12; do
  curl -s http://$node:8080/yawl/resourseService/ 2>/dev/null | head -1
done
```

### Step 5.3: Check Database Initialization

```bash
# Verify YAWL tables exist in database
ssh ubuntu@10.0.1.10 "psql -U yawl -d yawl -c '\dt' | head -10"

# Expected: List of YAWL tables
```

## Phase 6: Load Balancer Configuration

### Step 6.1: Setup Nginx/HAProxy

```bash
# Deploy load balancer configuration
ansible-playbook -i inventory.ini playbook.yml \
  --limit load_balancers \
  --ask-vault-pass

# Verify load balancer
curl -s http://10.0.0.10/health
```

### Step 6.2: Test Load Balancing

```bash
# Make multiple requests - should hit different backends
for i in {1..10}; do
  curl -s http://10.0.0.10/yawl/resourceService/ | grep -o 'node-[0-9]'
done

# Should see different node IDs in responses
```

## Phase 7: Monitoring Configuration

### Step 7.1: Access Monitoring Dashboards

```bash
# Grafana
http://10.0.3.10:3000
  Username: admin
  Password: (from vault_grafana_admin_password)

# Prometheus
http://10.0.3.10:9090

# Kibana
http://10.0.3.10:5601
```

### Step 7.2: Import Custom Dashboards

```bash
# Grafana will auto-provision dashboards in:
# roles/monitoring/templates/dashboard-*.json.j2
```

### Step 7.3: Configure Alerts

```bash
# Edit alert rules if needed
nano roles/monitoring/templates/prometheus-alert-rules.yml.j2

# Redeploy monitoring
ansible-playbook -i inventory.ini playbook.yml \
  --limit monitoring_servers \
  --ask-vault-pass
```

## Phase 8: Testing and Validation

### Step 8.1: Load Testing

```bash
# Install Apache Bench or similar tool
sudo apt-get install apache2-utils

# Perform load test
ab -n 1000 -c 10 http://10.0.0.10/yawl/resourceService/

# Check metrics in Grafana during test
```

### Step 8.2: Failover Testing

```bash
# Test database failover
# 1. Stop primary PostgreSQL
ssh ubuntu@10.0.1.10 "sudo systemctl stop postgresql"

# 2. Application should still work via secondary
curl http://10.0.0.10/yawl/resourceService/

# 3. Promote secondary to primary (manual process - outside Ansible)
ssh ubuntu@10.0.1.11 "sudo -u postgres pg_ctl promote -D /var/lib/postgresql/14/main"

# 4. Restart primary
ssh ubuntu@10.0.1.10 "sudo systemctl start postgresql"
```

### Step 8.3: Log Verification

```bash
# Check application logs
ansible app_servers -i inventory.ini -m shell \
  -a "tail -50 /usr/local/tomcat/logs/catalina.out" \
  --ask-vault-pass

# Check Kibana for aggregated logs
# Access http://10.0.3.10:5601
```

## Phase 9: Production Hardening

### Step 9.1: Enable Firewall Rules

```bash
# Enable UFW and configure rules
ansible all -i inventory.ini -m ufw \
  -a "state=enabled" \
  --ask-vault-pass

# Allow SSH
ansible all -i inventory.ini -m ufw \
  -a "rule=allow port=22 proto=tcp" \
  --ask-vault-pass

# Allow application-specific ports based on group
# See roles/*/tasks/firewall.yml for specifics
```

### Step 9.2: Enable SSL/TLS

```bash
# Generate SSL certificates
# Update: tomcat_ssl_enabled: true
nano group_vars/app_servers.yml

# Redeploy with SSL enabled
ansible-playbook -i inventory.ini playbook.yml \
  --limit app_servers \
  --ask-vault-pass
```

### Step 9.3: Enable Host Key Checking

```bash
# Update ansible.cfg after initial deployment
nano ansible.cfg

# Set: host_key_checking = True
```

## Phase 10: Ongoing Maintenance

### Regular Monitoring

```bash
# Check system health
ansible all -i inventory.ini -m shell \
  -a "df -h | grep -E '^Filesystem|/$|yawl|postgres'"

# Check disk usage
ansible all -i inventory.ini -m shell \
  -a "du -sh /var/lib/postgresql/* /var/lib/elasticsearch/* /data/yawl/*" \
  --ask-vault-pass
```

### Database Maintenance

```bash
# Run VACUUM ANALYZE on primary
ssh ubuntu@10.0.1.10 "psql -U postgres -d yawl -c 'VACUUM ANALYZE;'"

# Check replication lag
ssh ubuntu@10.0.1.10 "psql -U postgres -d yawl -c 'SELECT slot_name, restart_lsn FROM pg_replication_slots;'"
```

### Backup Verification

```bash
# Verify backups are being created
ansible db_servers -i inventory.ini -m shell \
  -a "ls -lh /backups/postgres/" \
  --ask-vault-pass
```

## Troubleshooting Common Issues

### Issue: PostgreSQL won't connect

```bash
# Check PostgreSQL logs
ssh ubuntu@10.0.1.10 "sudo tail -50 /var/log/postgresql/postgresql.log"

# Check pg_hba.conf
ssh ubuntu@10.0.1.10 "sudo cat /etc/postgresql/14/main/pg_hba.conf"

# Verify TCP port is listening
ssh ubuntu@10.0.1.10 "netstat -tlnp | grep 5432"
```

### Issue: Tomcat won't start

```bash
# Check Java installation
ansible app_servers -i inventory.ini -m shell -a "java -version"

# Check Tomcat logs
ansible app_servers -i inventory.ini -m shell \
  -a "tail -100 /usr/local/tomcat/logs/catalina.out"

# Check port conflict
ansible app_servers -i inventory.ini -m shell -a "netstat -tlnp | grep 8080"
```

### Issue: Metrics not appearing in Prometheus

```bash
# Check Prometheus targets
curl -s http://10.0.3.10:9090/api/v1/targets | jq '.data.activeTargets'

# Check if targets are up
curl -s http://10.0.3.10:9090/api/v1/targets | jq '.data.activeTargets | map(select(.health=="down"))'
```

### Issue: Elasticsearch disk full

```bash
# Check disk usage
curl -s http://10.0.3.10:9200/_cat/shards | tail -10

# Adjust index retention
curl -X PUT -H "Content-Type: application/json" \
  -d '{"index.max_result_window": 5000}' \
  http://10.0.3.10:9200/logstash-*/_settings
```

## Rollback Procedures

### Rollback Application

```bash
# Restore previous YAWL WAR
ansible app_servers -i inventory.ini -m shell \
  -a "cp /backups/tomcat-previous.war /usr/local/tomcat/webapps/yawl.war"

# Restart Tomcat
ansible app_servers -i inventory.ini -m systemd \
  -a "name=tomcat state=restarted"
```

### Rollback Database

```bash
# From backup
ssh ubuntu@10.0.1.10 "pg_basebackup -h /backups/postgres-previous"

# Stop primary, restore, restart
```

## Next Steps

1. Configure DNS/load balancer for production access
2. Setup automated backups (backup scripts provided)
3. Configure alert notifications (email/Slack)
4. Schedule regular maintenance windows
5. Document runbooks for your team
6. Perform security audit

## Support

- Check logs: `/var/log/yawl/`, `/var/log/ansible.log`
- Review Ansible debug output: Add `-vvv` flag to playbook commands
- YAWL documentation: https://www.yawlfoundation.org/docs
