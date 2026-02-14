# Complete Observability Suite for YAWL

This directory contains a comprehensive observability and monitoring stack for the YAWL application, including Prometheus, Grafana, ELK Stack, Datadog, and New Relic integrations.

## Directory Structure

```
observability/
├── README.md                                   # This file
├── prometheus-config.yaml                     # Prometheus configuration
├── alerting-rules.yaml                        # Prometheus alerting rules
├── datadog-config.yaml                        # Datadog agent configuration
├── newrelic-config.yaml                       # New Relic monitoring configuration
├── elk-docker-compose.yaml                    # ELK Stack Docker Compose setup
├── logstash.conf                              # Logstash pipeline configuration
├── filebeat.yml                               # Filebeat configuration
├── metricbeat.yml                             # Metricbeat configuration
├── packetbeat.yml                             # Packetbeat configuration
├── grafana-dashboards/
│   ├── system-overview-dashboard.json         # System metrics dashboard
│   └── application-metrics-dashboard.json     # Application metrics dashboard
├── docker-compose.monitoring.yml              # Prometheus + Grafana + Alertmanager setup
├── prometheus-docker.sh                       # Prometheus setup script
├── grafana-docker.sh                          # Grafana setup script
├── env.example                                # Environment variables example
└── docs/
    ├── SETUP_GUIDE.md                         # Detailed setup guide
    ├── PROMETHEUS_GUIDE.md                    # Prometheus configuration guide
    ├── GRAFANA_GUIDE.md                       # Grafana setup guide
    ├── ELK_GUIDE.md                           # ELK Stack guide
    ├── DATADOG_GUIDE.md                       # Datadog integration guide
    └── NEWRELIC_GUIDE.md                      # New Relic integration guide
```

## Components Overview

### 1. Prometheus (prometheus-config.yaml)
- **Purpose**: Time-series metrics collection and storage
- **Key Features**:
  - Node exporter for system metrics
  - Docker container metrics
  - Application metrics scraping
  - Service discovery
  - Remote write/read capabilities
- **Scrape Intervals**: 15s (default), 10s (application)
- **Alert Rules**: Comprehensive alerting rules defined in `alerting-rules.yaml`

### 2. Grafana Dashboards (grafana-dashboards/)
- **System Overview Dashboard**: CPU, Memory, Disk, Network metrics
- **Application Metrics Dashboard**: Request rates, error rates, latency, HTTP status distribution
- **Auto-provisioning**: Dashboards configured for automatic loading

### 3. Alerting Rules (alerting-rules.yaml)
Organized alert groups:
- **System Alerts**: CPU, Memory, Disk usage
- **Application Alerts**: Service availability, error rates, latency
- **Database Alerts**: Connection limits, replication lag, slow queries
- **Container Alerts**: Restart events, OOM kills
- **Infrastructure Alerts**: I/O errors, network issues
- **Monitoring Alerts**: Alertmanager health, pending alerts

### 4. ELK Stack (elk-docker-compose.yaml)
Complete log aggregation and analysis platform:

#### Components:
- **Elasticsearch**: Search and analytics engine
- **Kibana**: Visualization and exploration
- **Logstash**: Data processing pipeline
- **Filebeat**: Log shipping and collection
- **Metricbeat**: Metrics collection
- **Packetbeat**: Network data collection
- **APM Server**: Application Performance Monitoring

#### Features:
- Docker containers with volume persistence
- Health checks and auto-restart
- Resource limits and reservations
- Custom pipeline processing
- Multiple log sources support
- Network isolation with Docker bridge

### 5. Datadog Integration (datadog-config.yaml)
Unified monitoring, logging, and analytics platform:

#### Modules:
- **APM**: Application Performance Monitoring with trace sampling
- **Logs**: Docker, Syslog, File-based log collection
- **Metrics**: Custom metrics, API endpoint monitoring
- **Process Agent**: Process and container monitoring
- **System Probe**: Network and service monitoring
- **Integrations**: PostgreSQL, MySQL, Redis, Nginx, Kubernetes
- **Synthetics**: API and browser-based synthetic tests
- **Monitors**: Alert definitions and thresholds

### 6. New Relic Setup (newrelic-config.yaml)
Enterprise-grade APM and monitoring platform:

#### Capabilities:
- **APM Agent**: Transaction tracing, error collection
- **Infrastructure Monitoring**: System and container metrics
- **Log Management**: Centralized log forwarding and analysis
- **Synthetics**: API and browser monitoring
- **Dashboards**: Pre-configured dashboards
- **Alerts**: Threshold-based alerting with incident management
- **High-Security Mode**: Optional enhanced security

## Quick Start

### Prerequisites
- Docker and Docker Compose (for ELK Stack)
- Prometheus instance
- Grafana instance
- API keys for Datadog and New Relic (optional)
- Basic understanding of YAML configuration

### 1. Environment Setup

Create `.env` file:
```bash
cp env.example .env
# Edit .env with your configuration
```

### 2. Prometheus Setup

```bash
# Copy configuration
cp prometheus-config.yaml /etc/prometheus/prometheus.yml

# Reload Prometheus
curl -X POST http://localhost:9090/-/reload

# Check status
curl http://localhost:9090/api/v1/status/config
```

### 3. Grafana Setup

```bash
# Import dashboards via Grafana UI
# - Go to http://localhost:3000
# - Create new Datasource (Prometheus)
# - Import JSON dashboard files

# Or use API
curl -X POST http://localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d @grafana-dashboards/system-overview-dashboard.json
```

### 4. ELK Stack Setup

```bash
# Start ELK stack
docker-compose -f elk-docker-compose.yaml up -d

# Verify services
docker-compose -f elk-docker-compose.yaml ps

# Access services
# - Elasticsearch: http://localhost:9200
# - Kibana: http://localhost:5601
# - Logstash: localhost:5000
# - APM Server: localhost:8200
```

### 5. Datadog Integration

```bash
# Install Datadog Agent
DD_AGENT_MAJOR_VERSION=7 \
DD_API_KEY=$DATADOG_API_KEY \
DD_SITE="datadoghq.com" \
bash -c "$(curl -L https://s3.amazonaws.com/dd-agent/scripts/install_agent.sh)"

# Copy configuration
cp datadog-config.yaml /etc/datadog-agent/datadog.yaml

# Restart agent
sudo systemctl restart datadog-agent
```

### 6. New Relic Integration

```bash
# Install New Relic Infrastructure Agent
curl -s https://download.newrelic.com/install/newrelic-cli/scripts/install.sh | bash

# Copy configuration
cp newrelic-config.yaml /etc/newrelic-infra.yml

# Restart agent
sudo systemctl restart newrelic-infra
```

## Configuration Details

### Prometheus Scrape Configs
- `prometheus`: Self-monitoring (9090)
- `node-exporter`: System metrics (9100)
- `application`: Custom app metrics (8080)
- `docker`: Container metrics (9323)
- `cadvisor`: Container resource metrics (8080)
- `kubernetes`: Kubernetes cluster metrics (if applicable)
- `service-discovery`: Consul-based service discovery (8500)
- `blackbox`: Endpoint availability checks (9115)

### Alert Severity Levels
- **critical**: Immediate action required (1-2 min for: service down, max connections, container OOM)
- **warning**: Investigation required (5 min for: high resource usage, high error rates, replication lag)
- **info**: Informational (anomaly detection)

### ELK Stack Features
- **Elasticsearch**: 1GB heap, 8.5.0, single-node cluster
- **Kibana**: Available at http://localhost:5601
- **Logstash**: Processes logs, enriches with geolocation and user agent data
- **Filebeat**: Monitors Docker, system, app, and web server logs
- **Metricbeat**: Collects system, Docker, database, and web server metrics
- **Packetbeat**: Monitors DNS, HTTP, TLS, database protocols
- **APM Server**: Receives traces on port 8200

### Datadog Integration Points
- Logs from Docker, Syslog, and files
- APM with distributed tracing and infinite tracing
- Browser monitoring and RUM
- Custom metrics and attributes
- JMX monitoring for Java apps
- Docker and Kubernetes integrations
- Database monitoring (PostgreSQL, MySQL, Redis)
- Synthetic tests and monitors

### New Relic Features
- Distributed tracing and infinite tracing
- Browser monitoring and RUM
- Infrastructure monitoring with process tracking
- Log forwarding and centralized logging
- Synthetic API and browser monitoring
- Customizable dashboards and alerts
- High-security mode option

## Monitoring Checklist

- [ ] Prometheus scraping all targets successfully
- [ ] Grafana dashboards displaying metrics
- [ ] Alertmanager receiving and routing alerts
- [ ] Elasticsearch ingesting logs
- [ ] Kibana visualizations working
- [ ] Logstash pipelines processing logs
- [ ] Filebeat collecting system and app logs
- [ ] Metricbeat collecting host metrics
- [ ] Datadog agent reporting metrics and logs
- [ ] New Relic agent reporting transactions
- [ ] Synthetic tests passing
- [ ] Alerts firing correctly
- [ ] Log retention policies configured
- [ ] Backup and recovery procedures documented

## Troubleshooting

### Prometheus Issues
```bash
# Check config validity
promtool check config prometheus-config.yaml

# Check alert rules
promtool check rules alerting-rules.yaml

# View scrape targets
curl http://localhost:9090/api/v1/targets

# Check specific target
curl http://localhost:9090/api/v1/query?query=up{job="application"}
```

### ELK Stack Issues
```bash
# Check Elasticsearch health
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cluster/health

# View indexes
curl -u elastic:$ELASTIC_PASSWORD http://localhost:9200/_cat/indices

# Check Logstash config
docker-compose -f elk-docker-compose.yaml logs logstash

# Verify Kibana connection
curl http://localhost:5601/api/status
```

### Datadog Agent Issues
```bash
# Check agent status
sudo datadog-agent status

# View agent configuration
sudo datadog-agent config

# Check logs
sudo tail -f /var/log/datadog/agent.log
```

### New Relic Agent Issues
```bash
# Check agent status
sudo systemctl status newrelic-infra

# View logs
sudo tail -f /var/log/newrelic-infra/newrelic-infra.log

# Verify connectivity
curl -v https://api.newrelic.com
```

## Performance Tuning

### Prometheus
- Adjust `scrape_interval` based on storage capacity
- Use `metric_relabel_configs` to reduce cardinality
- Configure `remote_write` for long-term storage

### Elasticsearch
- Adjust JVM heap size: `-Xms1g -Xmx1g`
- Tune shard count and replica settings
- Implement index lifecycle management (ILM)

### Datadog
- Adjust `trace_sample_rate` for APM cost optimization
- Filter unnecessary metrics with `include`/`exclude` patterns
- Monitor agent CPU and memory usage

### New Relic
- Use `distributed_tracing` for microservices
- Adjust transaction thresholds to capture important transactions
- Use sampling for high-volume services

## Security Considerations

1. **Credentials Management**
   - Use environment variables for sensitive data
   - Never commit `.env` files
   - Rotate API keys regularly

2. **Network Security**
   - Restrict Prometheus access to internal networks
   - Use TLS for remote communication
   - Implement authentication on all dashboards

3. **Data Protection**
   - Enable encryption at rest for Elasticsearch
   - Use role-based access control (RBAC)
   - Implement audit logging

4. **Log Sanitization**
   - Exclude sensitive fields (passwords, tokens, API keys)
   - Use Logstash filters for PII redaction
   - Configure attribute filtering in APM agents

## Useful Commands

```bash
# Prometheus query examples
# System CPU usage
100 - (avg(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# Request rate
sum(rate(http_requests_total[5m])) by (job)

# Error rate
sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))

# Response time p95
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# Active connections
sum(http_active_connections)
```

## Resources

- Prometheus Documentation: https://prometheus.io/docs/
- Grafana Documentation: https://grafana.com/docs/
- Elastic Stack Documentation: https://www.elastic.co/guide/
- Datadog Documentation: https://docs.datadoghq.com/
- New Relic Documentation: https://docs.newrelic.com/

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review configuration files for syntax errors
3. Check agent/service logs
4. Consult vendor documentation

## Version Information

- Prometheus: Latest stable
- Grafana: Latest stable
- Elasticsearch: 8.5.0
- Kibana: 8.5.0
- Logstash: 8.5.0
- Elastic Beats: 8.5.0
- Datadog Agent: 7.x
- New Relic Agent: Latest stable

## License

These configurations are provided as-is for use with YAWL monitoring setup.
