# YAWL Observability Suite - Setup Instructions

Complete step-by-step guide to deploy the full observability stack.

## Prerequisites

- Docker and Docker Compose (version 3.8+)
- Linux-based system (Ubuntu 18.04+, CentOS 7+)
- At least 8GB RAM
- 20GB free disk space
- Administrative/sudo access

## Directory Structure

```
observability/
├── prometheus-config.yaml
├── alerting-rules.yaml
├── datadog-config.yaml
├── newrelic-config.yaml
├── elk-docker-compose.yaml
├── docker-compose.monitoring.yml
├── logstash.conf
├── filebeat.yml
├── metricbeat.yml
├── packetbeat.yml
├── alertmanager-config.yml
├── blackbox-config.yml
├── grafana-provisioning-datasources.yaml
├── env.example
└── README.md
```

## Phase 1: Environment Configuration

### Step 1: Create Environment File

```bash
cd /home/user/yawl/observability
cp env.example .env
```

### Step 2: Update .env with Your Values

Edit `.env` and set:

```bash
# Essential configs
ELASTIC_PASSWORD=your_secure_password
GRAFANA_PASSWORD=your_secure_password
DATADOG_API_KEY=your_datadog_api_key
NEW_RELIC_LICENSE_KEY=your_newrelic_license_key

# Integration endpoints
SLACK_WEBHOOK_URL=your_slack_webhook
SMTP_HOST=your_smtp_host
SMTP_USER=your_smtp_user
SMTP_PASSWORD=your_smtp_password

# Database credentials
POSTGRES_PASSWORD=your_postgres_password
```

### Step 3: Verify Configuration

```bash
# Check YAML syntax
for file in *.yaml *.yml; do
  echo "Checking $file..."
  python3 -c "import yaml; yaml.safe_load(open('$file'))"
done
```

## Phase 2: Deploy Prometheus + Grafana + Alertmanager

### Step 1: Create Required Directories

```bash
mkdir -p prometheus-data grafana-data alertmanager-data
chmod 777 prometheus-data grafana-data alertmanager-data
```

### Step 2: Copy Grafana Provisioning Files

```bash
mkdir -p grafana-provisioning/datasources grafana-provisioning/dashboards

# Copy datasources config
cp grafana-provisioning-datasources.yaml grafana-provisioning/datasources/datasources.yaml

# Create dashboard provisioning config
cat > grafana-provisioning/dashboards/dashboards.yaml << 'EOF'
apiVersion: 1

providers:
  - name: 'Dashboards'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/dashboards
EOF
```

### Step 3: Start Monitoring Stack

```bash
# Start services
docker-compose -f docker-compose.monitoring.yml up -d

# Verify all services are running
docker-compose -f docker-compose.monitoring.yml ps

# Check logs
docker-compose -f docker-compose.monitoring.yml logs -f prometheus
docker-compose -f docker-compose.monitoring.yml logs -f grafana
```

### Step 4: Wait for Services to Initialize

```bash
# Wait for Prometheus to be ready
until curl -s http://localhost:9090/-/healthy; do
  echo "Waiting for Prometheus..."
  sleep 5
done

# Wait for Grafana to be ready
until curl -s http://localhost:3000/api/health; do
  echo "Waiting for Grafana..."
  sleep 5
done

echo "Prometheus and Grafana are ready!"
```

### Step 5: Access Web Interfaces

- **Prometheus**: http://localhost:9090
- **Alertmanager**: http://localhost:9093
- **Grafana**: http://localhost:3000 (admin/admin, change password)
- **Node Exporter**: http://localhost:9100
- **Pushgateway**: http://localhost:9091

### Step 6: Verify Prometheus Scrape Targets

```bash
# Check Prometheus targets
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | head -50

# Should show targets: prometheus, node-exporter, application, etc.
```

### Step 7: Configure Grafana

```bash
# Generate API token
curl -X POST http://localhost:3000/api/auth/keys \
  -H "Content-Type: application/json" \
  -u admin:admin \
  -d '{
    "name": "Provisioning API key",
    "role": "Admin",
    "secondsToLive": 0
  }' | python3 -m json.tool
```

## Phase 3: Deploy ELK Stack

### Step 1: Prepare ELK Configuration

```bash
# Create elasticsearch.yml if needed
cat > elasticsearch.yml << 'EOF'
cluster.name: yawl-cluster
node.name: es-node-1
xpack.security.enabled: true
xpack.security.authc.api_key.enabled: true
index.lifecycle.name: default_policy
index.lifecycle.rollover_alias: logs-alias
EOF

# Verify Logstash configuration
docker run -v $(pwd)/logstash.conf:/config.conf \
  docker.elastic.co/logstash/logstash:8.5.0 \
  -f /config.conf --dry-run
```

### Step 2: Start ELK Stack

```bash
# Start ELK services
docker-compose -f elk-docker-compose.yaml up -d

# Monitor startup
docker-compose -f elk-docker-compose.yaml logs -f elasticsearch

# Wait for Elasticsearch
until curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cluster/health; do
  echo "Waiting for Elasticsearch..."
  sleep 5
done
```

### Step 3: Initialize Elasticsearch

```bash
# Create index patterns
curl -X POST http://localhost:9200/_template/filebeat-template -u elastic:$ELASTIC_PASSWORD \
  -H "Content-Type: application/json" \
  -d '{
    "index_patterns": ["filebeat-*"],
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  }'

# Check cluster health
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cluster/health | python3 -m json.tool
```

### Step 4: Access Kibana and Configure

```bash
# Access Kibana
open http://localhost:5601

# Create index patterns:
# 1. filebeat-* (Logs)
# 2. metricbeat-* (Metrics)
# 3. packetbeat-* (Network)
```

### Step 5: Verify Data Flow

```bash
# Check Elasticsearch indexes
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cat/indices?v

# Sample logs
curl -u elastic:$ELASTIC_PASSWORD "http://localhost:9200/filebeat-*/_search?size=5" | python3 -m json.tool
```

## Phase 4: Configure Datadog Integration

### Step 1: Install Datadog Agent

For Ubuntu/Debian:
```bash
DD_AGENT_MAJOR_VERSION=7 \
DD_API_KEY=$DATADOG_API_KEY \
DD_SITE="datadoghq.com" \
bash -c "$(curl -L https://s3.amazonaws.com/dd-agent/scripts/install_agent.sh)"
```

For CentOS/RHEL:
```bash
DD_AGENT_MAJOR_VERSION=7 \
DD_API_KEY=$DATADOG_API_KEY \
DD_SITE="datadoghq.com" \
bash -c "$(curl -L https://s3.amazonaws.com/dd-agent/scripts/install_agent.sh)"
```

### Step 2: Deploy Configuration

```bash
# Copy configuration
sudo cp datadog-config.yaml /etc/datadog-agent/datadog.yaml

# Update permissions
sudo chown dd-agent:dd-agent /etc/datadog-agent/datadog.yaml
sudo chmod 640 /etc/datadog-agent/datadog.yaml

# Verify configuration
sudo datadog-agent config
```

### Step 3: Start Agent

```bash
# Start service
sudo systemctl restart datadog-agent

# Check status
sudo systemctl status datadog-agent

# Verify agent health
sudo datadog-agent status
```

### Step 4: Validate Integration

```bash
# Check agent logs
sudo tail -f /var/log/datadog/agent.log

# View collected metrics
curl http://localhost:17123/agent/stats | python3 -m json.tool
```

## Phase 5: Configure New Relic Integration

### Step 1: Install New Relic Infrastructure Agent

For Ubuntu/Debian:
```bash
curl -s https://download.newrelic.com/install/newrelic-cli/scripts/install.sh | bash
```

For CentOS/RHEL:
```bash
sudo rpm -Uvh https://download.newrelic.com/install/newrelic-cli/releases/latest/newrelic-cli_Linux_x86_64.rpm
```

### Step 2: Initialize Agent

```bash
sudo newrelic agent install --help
```

### Step 3: Configure

```bash
# Copy configuration
sudo cp newrelic-config.yaml /etc/newrelic-infra.yml

# Update permissions
sudo chown root:root /etc/newrelic-infra.yml
sudo chmod 644 /etc/newrelic-infra.yml
```

### Step 4: Start Agent

```bash
# Start service
sudo systemctl restart newrelic-infra

# Check status
sudo systemctl status newrelic-infra

# View logs
sudo tail -f /var/log/newrelic-infra/newrelic-infra.log
```

### Step 5: Deploy APM Agent (for applications)

For Node.js:
```bash
npm install newrelic
```

For Python:
```bash
pip install newrelic
```

For Java:
```bash
# Download agent
wget https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip
unzip newrelic-java.zip
```

## Phase 6: Verification and Testing

### Step 1: Verify Prometheus Metrics

```bash
# Check Prometheus targets
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool

# Expected output should show all targets with "UP" status
```

### Step 2: Verify Grafana Dashboards

```bash
# List dashboards
curl -s http://localhost:3000/api/search \
  -H "Authorization: Bearer YOUR_API_KEY" | python3 -m json.tool
```

### Step 3: Test Alerts

```bash
# Send test alert to Alertmanager
curl -X POST http://localhost:9093/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '[{
    "labels": {
      "alertname": "TestAlert",
      "severity": "critical"
    },
    "annotations": {
      "summary": "Test alert"
    }
  }]'
```

### Step 4: Generate Sample Data

```bash
# Create load on application to generate metrics
for i in {1..100}; do
  curl -s http://localhost:8080/api/endpoint &
done
wait
```

### Step 5: Verify Log Collection

```bash
# Check Kibana for logs
# Navigate to http://localhost:5601
# Create index patterns and dashboards
```

## Phase 7: Production Hardening

### Step 1: Security Configuration

```bash
# Change default passwords
# 1. Grafana: http://localhost:3000 -> Admin -> Change Password
# 2. Elasticsearch: Use credentials from .env

# Enable authentication
# Configure LDAP/OAuth in Grafana if needed
```

### Step 2: Configure TLS/SSL

```bash
# Generate self-signed certificates
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /path/to/key.pem -out /path/to/cert.pem

# Update docker-compose services to use SSL
```

### Step 3: Configure Backup

```bash
# Backup Prometheus data
docker exec yawl-prometheus tar czf /prometheus/backup.tar.gz /prometheus

# Backup Elasticsearch
curl -X PUT http://localhost:9200/_snapshot/backup \
  -H "Content-Type: application/json" \
  -d '{
    "type": "fs",
    "settings": {
      "location": "/backup"
    }
  }'
```

### Step 4: Configure Log Rotation

```bash
# For Docker logs
cat > /etc/docker/daemon.json << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

systemctl restart docker
```

### Step 5: Set Up Monitoring Alerts

```bash
# Configure email notifications in Alertmanager
# Configure Slack webhook integration
# Configure PagerDuty integration
```

## Maintenance Tasks

### Weekly
- Review alerts and adjust thresholds
- Check disk usage for logs and metrics
- Review authentication logs

### Monthly
- Update agent versions
- Review and optimize alert rules
- Check data retention settings
- Review performance metrics

### Quarterly
- Security audit
- Disaster recovery testing
- Capacity planning review
- Update documentation

## Troubleshooting Commands

```bash
# Prometheus
curl http://localhost:9090/api/v1/status/config
curl http://localhost:9090/api/v1/targets
promtool check rules alerting-rules.yaml

# Grafana
curl -s http://localhost:3000/api/health
curl -s http://localhost:3000/api/datasources

# Elasticsearch
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cluster/health
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cat/indices

# Docker
docker-compose -f docker-compose.monitoring.yml ps
docker-compose -f docker-compose.monitoring.yml logs -f [service-name]
docker-compose -f elk-docker-compose.yaml ps
docker-compose -f elk-docker-compose.yaml logs -f [service-name]
```

## Support

For detailed information:
- See README.md for component overview
- Check configuration files for detailed settings
- Review vendor documentation links in README.md
