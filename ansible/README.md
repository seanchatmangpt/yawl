# YAWL Multi-Node Deployment with Ansible

This directory contains a complete Ansible-based automation framework for deploying YAWL Workflow Engine across multiple nodes with full monitoring, logging, and high availability configuration.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   YAWL Multi-Node Deployment                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │  Load Balancer   │  │   Monitoring     │                 │
│  │  (nginx/HAProxy) │  │   (Prometheus)   │                 │
│  └────────┬─────────┘  │   (Grafana)      │                 │
│           │            │   (Elasticsearch)│                 │
│  ┌────────▼────────┬───┬────────────────┐ │                 │
│  │                 │   │                │ │                 │
│  ▼                 ▼   ▼                ▼ ▼                 │
│┌──────────┐  ┌──────────┐  ┌──────────┐┌──────────┐        │
││ App Node │  │ App Node │  │ App Node ││Monitoring        │
││    1     │  │    2     │  │    3     ││  Server        │
││ Tomcat   │  │ Tomcat   │  │ Tomcat   │└──────────┘        │
││ Docker   │  │ Docker   │  │ Docker   │                    │
│└────┬─────┘  └────┬─────┘  └────┬─────┘                    │
│     │             │             │                           │
│  ┌──┴─────────────┴─────────────┴──┐                        │
│  │   Shared Network / Cache Layer   │                       │
│  │   (Redis / Session Store)        │                       │
│  └──┬─────────────────────────────┬─┘                        │
│     │                             │                         │
│  ┌──▼──┐                    ┌────▼───┐                      │
│  │ DB  │ ◄─ Replication ──► │ DB     │                      │
│  │Prim │                    │Standby │                      │
│  │(RW) │                    │(RO)    │                      │
│  └─────┘                    └────────┘                      │
└─────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
ansible/
├── playbook.yml                 # Main deployment playbook
├── inventory.ini                # Host inventory configuration
├── README.md                    # This file
├── ansible.cfg                  # Ansible configuration
│
├── group_vars/
│   ├── all.yml                 # Global variables for all hosts
│   ├── db_servers.yml          # Database server variables
│   ├── app_servers.yml         # Application server variables
│   └── monitoring_servers.yml  # Monitoring server variables
│
├── host_vars/
│   ├── db-primary.yml          # Primary DB configuration
│   ├── db-secondary.yml        # Standby DB configuration
│   ├── app-node-1.yml          # App node 1 configuration
│   ├── app-node-2.yml          # App node 2 configuration
│   ├── app-node-3.yml          # App node 3 configuration
│   └── monitor-primary.yml     # Monitoring server configuration
│
└── roles/
    ├── postgres/               # PostgreSQL role
    │   ├── tasks/
    │   │   ├── main.yml       # Main tasks
    │   │   ├── ssl.yml        # SSL configuration
    │   │   ├── replication-primary.yml
    │   │   ├── replication-standby.yml
    │   │   └── monitoring.yml
    │   ├── templates/         # Jinja2 templates
    │   ├── defaults/          # Default variables
    │   ├── vars/              # Role-specific variables
    │   ├── handlers/          # Event handlers
    │   └── files/             # Static files
    │
    ├── docker/                # Docker role
    │   ├── tasks/
    │   │   └── main.yml       # Docker installation & config
    │   ├── templates/
    │   ├── defaults/
    │   ├── handlers/
    │   └── files/
    │
    ├── tomcat/                # Tomcat/Java application role
    │   ├── tasks/
    │   │   ├── main.yml       # Tomcat setup
    │   │   └── monitoring.yml
    │   ├── templates/
    │   ├── defaults/
    │   ├── handlers/
    │   └── files/
    │
    └── monitoring/            # Monitoring stack role
        ├── tasks/
        │   ├── main.yml       # Main monitoring tasks
        │   ├── prometheus.yml
        │   ├── grafana.yml
        │   ├── elasticsearch.yml
        │   ├── kibana.yml
        │   ├── logstash.yml
        │   ├── alertmanager.yml
        │   └── filebeat.yml
        ├── templates/
        ├── defaults/
        ├── handlers/
        └── files/
```

## Prerequisites

### System Requirements

- **Ubuntu 20.04 LTS or newer** on all nodes
- **Minimum Hardware per Node**:
  - Database servers: 4 vCPU, 16GB RAM, 100GB storage
  - Application servers: 4 vCPU, 8GB RAM, 50GB storage
  - Monitoring server: 4 vCPU, 8GB RAM, 100GB storage

### Network Requirements

- All nodes must be able to communicate with each other
- Ports must be accessible:
  - SSH: 22 (all nodes)
  - PostgreSQL: 5432 (db servers)
  - Tomcat: 8080 (app servers)
  - Prometheus: 9090 (monitoring)
  - Grafana: 3000 (monitoring)
  - Elasticsearch: 9200 (monitoring)
  - Kibana: 5601 (monitoring)

### Controller Node Setup

Install Ansible on your control machine:

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y ansible

# Or using pip
pip install ansible>=2.12

# Verify installation
ansible --version
```

## Quick Start

### 1. Configure Inventory

Edit `inventory.ini` with your actual node IP addresses:

```ini
[db_servers]
db-primary    ansible_host=10.0.1.10
db-secondary  ansible_host=10.0.1.11

[app_servers]
app-node-1    ansible_host=10.0.2.10
app-node-2    ansible_host=10.0.2.11
app-node-3    ansible_host=10.0.2.12

[monitoring_servers]
monitor-primary   ansible_host=10.0.3.10
```

### 2. Setup SSH Access

Ensure SSH key-based authentication to all nodes:

```bash
# Copy SSH key to all nodes
ssh-copy-id -i ~/.ssh/id_rsa.pub ubuntu@10.0.1.10
ssh-copy-id -i ~/.ssh/id_rsa.pub ubuntu@10.0.1.11
# ... repeat for all nodes

# Test connectivity
ansible all -i inventory.ini -m ping
```

### 3. Configure Variables

#### Vault Sensitive Data

Create an encrypted vault for sensitive variables:

```bash
# Create vault file with sensitive data
ansible-vault create group_vars/all-vault.yml

# Add vault variables:
vault_postgres_password: "secure_password_here"
vault_postgres_admin_password: "admin_password"
vault_postgres_monitoring_password: "monitor_password"
vault_postgres_replication_password: "replication_password"
vault_yawl_db_password: "yawl_password"
vault_redis_password: "redis_password"
vault_grafana_admin_password: "grafana_password"
vault_tomcat_ssl_key_password: "ssl_key_password"
vault_slack_webhook_url: "https://hooks.slack.com/..."
```

#### Environment-Specific Variables

Customize `group_vars/` and `host_vars/` for your environment.

### 4. Run Deployment

#### Full Deployment

```bash
# Dry run (check mode)
ansible-playbook -i inventory.ini playbook.yml --check

# Execute deployment
ansible-playbook -i inventory.ini playbook.yml

# With vault password
ansible-playbook -i inventory.ini playbook.yml --ask-vault-pass
```

#### Deployment by Component

```bash
# Deploy only database servers
ansible-playbook -i inventory.ini playbook.yml --limit db_servers

# Deploy only application servers
ansible-playbook -i inventory.ini playbook.yml --limit app_servers

# Deploy only monitoring
ansible-playbook -i inventory.ini playbook.yml --limit monitoring_servers
```

#### Deploy Specific Roles

```bash
# Deploy PostgreSQL role only
ansible-playbook -i inventory.ini playbook.yml --tags postgres

# Deploy Docker and Tomcat
ansible-playbook -i inventory.ini playbook.yml --tags "docker,tomcat"

# Deploy monitoring stack
ansible-playbook -i inventory.ini playbook.yml --tags monitoring
```

## Post-Deployment Verification

### 1. Check Service Status

```bash
# SSH to each node and verify services
ansible all -i inventory.ini -m shell -a "systemctl status postgresql docker tomcat prometheus grafana-server"
```

### 2. Verify Database Replication

```bash
# On primary DB node
ssh ubuntu@10.0.1.10 "psql -U postgres -d yawl -c 'SELECT * FROM pg_stat_replication;'"
```

### 3. Access Web Interfaces

- **YAWL Application**: http://10.0.2.10:8080/yawl
- **Grafana Dashboard**: http://10.0.3.10:3000 (admin/password)
- **Kibana Logs**: http://10.0.3.10:5601
- **Prometheus**: http://10.0.3.10:9090

### 4. Test Application Health

```bash
# Check YAWL health endpoint
curl http://10.0.2.10:8080/resourceService/
curl http://10.0.2.11:8080/resourceService/
curl http://10.0.2.12:8080/resourceService/
```

## Configuration Guide

### Database Configuration

Edit `group_vars/db_servers.yml`:

- `postgres_max_connections`: Maximum concurrent connections
- `postgres_shared_buffers`: Memory for caching
- `postgres_effective_cache_size`: Total available memory
- `postgres_max_wal_size`: Write-ahead log size

### Application Server Configuration

Edit `group_vars/app_servers.yml`:

- `java_heap_max`: Maximum Java heap size
- `tomcat_max_threads`: Tomcat thread pool size
- `yawl_db_pool_size`: Database connection pool size

### Monitoring Configuration

Edit `group_vars/monitoring_servers.yml`:

- `prometheus_retention_time`: Metrics retention period
- `elasticsearch_retention_days`: Log retention days
- `grafana_admin_password`: Grafana admin password

## Troubleshooting

### SSH Connection Issues

```bash
# Test SSH connectivity
ansible all -i inventory.ini -m ping

# Check SSH keys
ssh -vvv ubuntu@10.0.1.10
```

### Service Startup Issues

```bash
# Check service logs
ansible db_servers -i inventory.ini -m shell -a "journalctl -u postgresql -n 50"
ansible app_servers -i inventory.ini -m shell -a "journalctl -u tomcat -n 50"
```

### Database Connection Issues

```bash
# Test database connectivity
ansible db_servers -i inventory.ini -m shell -a "psql -h localhost -U yawl -d yawl -c 'SELECT 1'"
```

### Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| "Failed to connect to host" | Verify SSH keys, firewall rules, network connectivity |
| PostgreSQL won't start | Check data directory permissions, check logs |
| Tomcat won't deploy YAWL | Verify WAR file location, check Tomcat logs |
| Monitoring not collecting metrics | Verify Prometheus targets, check firewall for port 9090 |
| Elasticsearch disk full | Adjust retention policies, increase disk space |

## Security Hardening

### 1. Firewall Rules

Edit `/etc/ufw/applications.d/yawl` on each node to restrict access:

```bash
# Allow traffic only from trusted networks
ansible all -i inventory.ini -m ufw -a "rule=allow from=10.0.0.0/8 to_port=5432 proto=tcp"
```

### 2. SSL/TLS Configuration

Generate certificates:

```bash
# PostgreSQL certificates
openssl req -new -newkey rsa:2048 -days 365 -nodes -x509 \
  -keyout server.key -out server.crt

# Copy to servers
ansible db_servers -i inventory.ini -m copy -a "src=server.crt dest=/etc/postgresql/ssl/"
```

### 3. SSH Key Management

```bash
# Disable password authentication
ansible all -i inventory.ini -m lineinfile \
  -a "path=/etc/ssh/sshd_config regexp='^PasswordAuthentication' line='PasswordAuthentication no'"
```

## Performance Tuning

### Database Tuning

```yaml
# Update group_vars/db_servers.yml
postgres_shared_buffers: 4GB        # 25% of RAM
postgres_effective_cache_size: 12GB # 75% of RAM
postgres_work_mem: 64MB
postgres_maintenance_work_mem: 1GB
```

### Application Server Tuning

```yaml
# Update group_vars/app_servers.yml
java_heap_max: 4096m
tomcat_max_threads: 500
yawl_db_pool_size: 50
```

## Backup and Recovery

### Database Backups

```bash
# Manual backup
ansible db_servers -i inventory.ini -m shell \
  -a "pg_basebackup -h localhost -U replicator -D /backups/yawl -Pv"

# Restore from backup
pg_basebackup -h 10.0.1.10 -U replicator -D /var/lib/postgresql/14/main -Pv
```

### Application Backups

```bash
# Backup Tomcat webapps
ansible app_servers -i inventory.ini -m shell \
  -a "tar -czf /backups/tomcat-$(date +%Y%m%d).tar.gz /usr/local/tomcat/webapps"
```

## Scaling

### Add New Application Node

1. Add to inventory.ini:
```ini
[app_servers]
app-node-4    ansible_host=10.0.2.13  environment=production  app_server_id=4
```

2. Run role for new node:
```bash
ansible-playbook -i inventory.ini playbook.yml --limit app-node-4
```

3. Update load balancer configuration
4. Update monitoring targets

### Add New Database Replica

```bash
# Setup new standby server
ansible-playbook -i inventory.ini playbook.yml --limit db-tertiary
```

## Maintenance

### Regular Tasks

```bash
# System updates
ansible all -i inventory.ini -m apt -a "update_cache=yes upgrade=full"

# Check service health
ansible all -i inventory.ini -m systemd -a "name=postgresql state=started enabled=yes"

# Cleanup old backups
ansible db_servers -i inventory.ini -m shell \
  -a "find /backups -name '*.tar.gz' -mtime +30 -delete"
```

## Advanced Topics

### Custom Metrics

Add custom Prometheus metrics:

```yaml
# roles/monitoring/templates/prometheus.yml.j2
  - job_name: 'custom_yawl_metrics'
    static_configs:
      - targets: ['app-node-1:9090', 'app-node-2:9090', 'app-node-3:9090']
```

### Alert Rules

Edit `roles/monitoring/templates/prometheus-alert-rules.yml.j2`:

```yaml
groups:
  - name: YAWL Alerts
    rules:
      - alert: YawlHighErrorRate
        expr: rate(yawl_errors_total[5m]) > 0.05
        for: 5m
        annotations:
          summary: "High error rate on {{ $labels.instance }}"
```

### Custom Dashboards

Import Grafana JSON dashboards:

```bash
# From file
ansible monitoring_servers -i inventory.ini -m grafana_dashboard \
  -a "state=present grafana_url=http://localhost:3000 state=present dashboard=lookup('file','dashboard.json')"
```

## Support and References

- **YAWL Foundation**: https://www.yawlfoundation.org
- **Ansible Documentation**: https://docs.ansible.com
- **PostgreSQL Documentation**: https://www.postgresql.org/docs
- **Tomcat Documentation**: https://tomcat.apache.org
- **Prometheus Documentation**: https://prometheus.io/docs
- **Grafana Documentation**: https://grafana.com/docs

## License

This Ansible deployment framework is provided as-is for YAWL deployment. See parent YAWL project license for terms.

## Contact

For deployment support and questions:
- Email: support@yawlfoundation.org
- Forum: https://forum.yawlfoundation.org
- GitHub: https://github.com/yawlfoundation/yawl
