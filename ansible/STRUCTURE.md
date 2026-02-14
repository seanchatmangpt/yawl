# YAWL Ansible Structure and Components

Complete documentation of all files created for YAWL multi-node deployment automation.

## Root Level Files

### Playbook and Configuration Files

| File | Purpose | Description |
|------|---------|-------------|
| `playbook.yml` | Main Deployment Playbook | Orchestrates deployment across all nodes; includes common setup, role assignments, and post-deployment validation |
| `inventory.ini` | Host Inventory | Defines all nodes (database, application, monitoring) with IP addresses and group assignments |
| `ansible.cfg` | Ansible Configuration | Global Ansible settings including inventory path, timeouts, connection options, and logging |
| `README.md` | Main Documentation | Comprehensive guide with architecture, prerequisites, quick start, configuration, troubleshooting, and maintenance |
| `DEPLOYMENT_GUIDE.md` | Step-by-Step Deployment | Detailed deployment process in 10 phases with verification steps and rollback procedures |
| `QUICK_START.sh` | Automated Setup Script | Bash script to automate initial setup, SSH configuration, vault creation, and pre-flight checks |
| `STRUCTURE.md` | This File | Complete documentation of all created files and their purposes |

---

## Variables Files

### group_vars/ Directory

Global and group-specific variables applied to multiple hosts.

#### `group_vars/all.yml`
Applied to: ALL hosts

**Purpose**: Global configuration applied to every node in the deployment

**Key Sections**:
- Deployment user and group settings
- System timezone, NTP configuration
- Firewall configuration
- SSH settings
- Log and backup directories
- Docker, Postgres, Tomcat, Redis versions
- Performance tuning parameters (vm.max_map_count, socket limits)
- Database connection pooling defaults
- Cache settings
- Service restart policies

#### `group_vars/db_servers.yml`
Applied to: db_servers group (db-primary, db-secondary)

**Purpose**: PostgreSQL-specific configuration for database tier

**Key Sections**:
- PostgreSQL version (14) and port (5432)
- Database credentials and settings
- Data directories (postgres_data_dir, postgres_backup_dir)
- Performance tuning (max_connections, shared_buffers, etc.)
- Replication configuration (max_wal_senders, wal_keep_size)
- Backup and WAL archiving settings
- Network settings (listen_addresses, max_prepared_transactions)
- Security settings (SSL, password encryption)
- Authentication configuration (pg_hba)
- Extensions (pg_stat_statements, uuid-ossp, pgcrypto)
- Autovacuum settings
- Monitoring user configuration
- Replication node role determination

#### `group_vars/app_servers.yml`
Applied to: app_servers group (app-node-1, app-node-2, app-node-3)

**Purpose**: Application server configuration for YAWL deployment

**Key Sections**:
- Docker installation and daemon configuration
- Docker network setup (yawl-network, subnet)
- Tomcat version, ports, user configuration
- Java JVM settings (heap size, garbage collection)
- Thread pool configuration
- Tomcat session management
- YAWL application settings
- Database connection pooling
- Redis cache configuration
- Application clustering settings
- Health check configuration
- SSL/TLS settings
- Performance tuning (backlog, accept count, keepalive)

#### `group_vars/monitoring_servers.yml`
Applied to: monitoring_servers group (monitor-primary)

**Purpose**: Monitoring stack configuration (Prometheus, Grafana, ELK)

**Key Sections**:
- Prometheus settings (retention, scrape interval, targets)
- Grafana settings (admin user, datasources, dashboards)
- Elasticsearch configuration (cluster, heap, security)
- Kibana configuration (port, elasticsearch hosts)
- Logstash configuration (inputs, outputs, workers)
- Alertmanager configuration (resolution timeout, notification channels)
- Filebeat configuration
- Node exporter settings
- Alert rules
- Retention and cleanup policies

### host_vars/ Directory

Host-specific variables overriding group defaults for individual nodes.

#### `host_vars/db-primary.yml`
Applied to: db-primary host

**Purpose**: Primary database server-specific configuration

**Contents**:
- Replication role set to "primary"
- Primary-specific backup scheduling
- Replication slot configuration
- Monitoring interval settings
- Memory and CPU constraints

#### `host_vars/db-secondary.yml`
Applied to: db-secondary host

**Purpose**: Standby database server-specific configuration

**Contents**:
- Replication role set to "standby"
- Primary connection details
- Recovery command configuration
- Backup disabled (backup from primary only)
- Standby-specific recovery settings

#### `host_vars/app-node-1.yml`, `app-node-2.yml`, `app-node-3.yml`
Applied to: app-node-1, app-node-2, app-node-3 hosts

**Purpose**: Individual application node configuration

**Contents** (per node):
- Node ID (1, 2, or 3)
- Tomcat JVM route for session affinity
- Shutdown port (8005, 8006, 8007)
- Container name prefix
- Prometheus exporter port (9090, 9091, 9092)
- Node monitoring configuration

#### `host_vars/monitor-primary.yml`
Applied to: monitor-primary host

**Purpose**: Monitoring server-specific configuration

**Contents**:
- Monitoring role designation
- Primary monitoring server flag
- Resource allocation
- Alert channel configuration
- Data retention settings

---

## Roles

### Role: `postgres/`

PostgreSQL database server installation and configuration.

#### Tasks Files

**`tasks/main.yml`** (210 lines)
- Install PostgreSQL repository and packages
- Create PostgreSQL system user and directories
- Configure PostgreSQL server (postgresql.conf)
- Configure client authentication (pg_hba.conf)
- Configure pgBouncer connection pooling
- Setup SSL/TLS certificates
- Create databases and users
- Initialize replication settings
- Setup monitoring exporters
- Configure automated backups
- Verify installation

**`tasks/ssl.yml`** (28 lines)
- Generate self-signed certificates if needed
- Set proper certificate file permissions
- Configure SSL in PostgreSQL
- Enable SSL in postgresql.conf

**`tasks/replication-primary.yml`** (52 lines)
- Create replication user with proper privileges
- Configure WAL archiving
- Setup replication slots
- Update pg_hba.conf for replication
- Create base backup directory

**`tasks/replication-standby.yml`** (57 lines)
- Stop PostgreSQL if running
- Backup existing data directory
- Get base backup from primary
- Create recovery configuration
- Configure standby postgresql.conf
- Setup passwordless connections
- Start PostgreSQL in recovery mode
- Verify replication status

**`tasks/monitoring.yml`** (56 lines)
- Install PostgreSQL exporter (Prometheus)
- Create monitoring database user
- Setup exporter as systemd service
- Install node exporter
- Configure node exporter service
- Verify both exporters are running

#### Default Variables (`defaults/main.yml`)
- PostgreSQL version: 14
- PostgreSQL port: 5432
- Database name: yawl
- User: yawl
- Performance tuning defaults
- Replication settings
- Backup configuration

#### Handlers (`handlers/main.yml`)
- `restart postgresql`
- `reload postgresql`
- `restart pgbouncer`

#### Templates

**`templates/postgresql.conf.j2`**
- Complete PostgreSQL server configuration
- Jinja2 templated for variable substitution
- Includes performance tuning, logging, replication settings

**`templates/pg_hba.conf.j2`**
- Client authentication configuration
- Defines connection rules for different users/databases
- Includes local, IPv4, IPv6, and replication rules

**`templates/pgbouncer.ini.j2`** (referenced)
- PgBouncer connection pool configuration

**`templates/pgbouncer-users.txt.j2`** (referenced)
- PgBouncer user list

**`templates/recovery.conf.j2`** (referenced)
- Standby recovery configuration

**`templates/postgresql-standby.conf.j2`** (referenced)
- Standby-specific postgresql.conf

**`templates/pg_hba-standby.conf.j2`** (referenced)
- Standby pg_hba.conf

**`templates/pgpass.j2`** (referenced)
- Password file for passwordless connections

**`templates/postgres-exporter.service.j2`** (referenced)
- Systemd service for Postgres exporter

**`templates/postgres-exporter.env.j2`** (referenced)
- Environment variables for Postgres exporter

**`templates/node-exporter.service.j2`** (referenced)
- Systemd service for node exporter

**`templates/pgbackrest.conf.j2`** (referenced)
- pgBackRest backup configuration

**`templates/init-database.sql.j2`** (referenced)
- Initial database schema

---

### Role: `docker/`

Docker installation and configuration for containerized deployment.

#### Tasks Files

**`tasks/main.yml`** (120 lines)
- Install Docker prerequisites
- Add Docker GPG key and repository
- Install Docker CE and Docker Compose
- Create Docker daemon configuration
- Setup Docker network for YAWL
- Pull required container images
- Create Docker volumes
- Setup log rotation
- Configure resource limits
- Install health check scripts
- Verify Docker installation

#### Default Variables (`defaults/main.yml`)
- Docker version: 24.0.0
- Docker storage driver: overlay2
- YAWL version: latest
- Docker network: yawl-network
- Cache backend: redis
- Monitoring settings

#### Handlers (`handlers/main.yml`)
- `restart docker`
- `reload docker`
- `restart docker containers`

#### Templates

**`templates/daemon.json.j2`**
- Docker daemon configuration
- Logging configuration
- Storage settings
- Registry mirrors and insecure registries

**`templates/logrotate-docker.j2`** (referenced)
- Log rotation policy for Docker containers

**`templates/docker-limits.conf.j2`** (referenced)
- Systemd resource limits for Docker

**`templates/docker-health-check.sh.j2`** (referenced)
- Health check script for Docker containers

---

### Role: `tomcat/`

Tomcat application server and YAWL application deployment.

#### Tasks Files

**`tasks/main.yml`** (155 lines)
- Install Java (OpenJDK 11)
- Download and install Tomcat
- Create Tomcat user and group
- Configure Tomcat server.xml
- Configure Tomcat context.xml, web.xml
- Configure logging
- Create systemd service
- Deploy YAWL WAR file
- Create application configuration
- Setup security policies
- Setup log rotation
- Install monitoring agents
- Verify installation

**`tasks/monitoring.yml`** (50 lines)
- Download JMX Exporter JAR
- Create JMX exporter configuration
- Configure Tomcat JVM for JMX
- Install node exporter
- Setup metrics collection scripts
- Create custom metrics endpoint

#### Default Variables (`defaults/main.yml`)
- Tomcat version: 9.0.68
- Java version: 11
- Tomcat ports (8080, 8005, 8009)
- JVM heap settings
- Thread pool configuration
- Session timeout: 1440 minutes

#### Handlers (`handlers/main.yml`)
- `restart tomcat`
- `stop tomcat`
- `start tomcat`
- `reload systemd`

#### Templates

**`templates/server.xml.j2`** (150 lines)
- Complete Tomcat server configuration
- HTTP connector settings
- AJP connector for load balancer
- HTTPS connector (if enabled)
- Engine and Host configuration
- Database connection pool definition
- Resource links to JNDI resources

**`templates/context.xml.j2`** (referenced)
- Tomcat context configuration
- Session manager settings
- Resource definitions

**`templates/web.xml.j2`** (referenced)
- Tomcat web application configuration
- Servlet and filter definitions

**`templates/logging.properties.j2`** (referenced)
- Tomcat logging configuration

**`templates/tomcat.service.j2`** (referenced)
- Systemd service definition

**`templates/tomcat.env.j2`** (referenced)
- Tomcat environment variables including JAVA_OPTS

**`templates/yawl-config.properties.j2`** (referenced)
- YAWL application configuration

**`templates/catalina.policy.j2`** (referenced)
- Java security policy for Tomcat

**`templates/logrotate-tomcat.j2`** (referenced)
- Log rotation for Tomcat logs

**`templates/jmx-exporter-config.yml.j2`** (referenced)
- JMX exporter Prometheus configuration

**`templates/node-exporter.service.j2`** (referenced)
- Node exporter systemd service

**`templates/yawl-metrics-collector.sh.j2`** (referenced)
- Custom metrics collection script

**`templates/tomcat-health-check.sh.j2`** (referenced)
- Health check script for Tomcat

**`templates/metrics-endpoint.jsp.j2`** (referenced)
- Custom metrics endpoint JSP

---

### Role: `monitoring/`

Monitoring stack with Prometheus, Grafana, Elasticsearch, Kibana, Logstash.

#### Tasks Files

**`tasks/main.yml`** (80 lines)
- Create monitoring user
- Create monitoring directories
- Include all monitoring component tasks
- Configure firewall rules
- Setup dashboard generation
- Setup monitoring backups
- Verify monitoring stack

**`tasks/prometheus.yml`** (65 lines)
- Create prometheus user
- Download Prometheus binary
- Create configuration directories
- Configure prometheus.yml with targets
- Create alert rules
- Create systemd service
- Start and verify Prometheus

**`tasks/grafana.yml`** (100 lines)
- Add Grafana repository
- Install Grafana
- Create provisioning directories
- Configure grafana.ini
- Configure datasources (Prometheus, Elasticsearch)
- Configure dashboard provisioning
- Create dashboard JSON files
- Setup notification channels
- Start and verify Grafana

**`tasks/elasticsearch.yml`** (90 lines)
- Install Java
- Add Elasticsearch repository
- Install Elasticsearch
- Configure elasticsearch.yml
- Configure JVM options
- Create index lifecycle management policy
- Create index template
- Verify cluster health

**`tasks/kibana.yml`** (40 lines)
- Install Kibana
- Configure kibana.yml
- Start Kibana
- Configure index patterns
- Create dashboards

**`tasks/logstash.yml`** (40 lines)
- Install Logstash
- Configure logstash.yml
- Configure pipeline
- Start Logstash
- Verify operation

**`tasks/alertmanager.yml`** (50 lines)
- Create alertmanager user
- Download Alertmanager
- Configure alertmanager.yml
- Create systemd service
- Start Alertmanager

**`tasks/filebeat.yml`** (30 lines)
- Install Filebeat
- Configure filebeat.yml
- Load Filebeat modules
- Setup index management
- Start Filebeat

#### Default Variables (`defaults/main.yml`)
- Prometheus port: 9090
- Grafana port: 3000
- Elasticsearch port: 9200
- Kibana port: 5601
- Retention settings
- Cluster configuration

#### Handlers (`handlers/main.yml`)
- `restart prometheus`
- `restart grafana`
- `restart elasticsearch`
- `restart kibana`
- `restart logstash`
- `restart filebeat`

#### Templates (Referenced)

**`templates/prometheus.yml.j2`**
- Prometheus scrape configuration
- Target definitions
- Alert rules

**`templates/prometheus-alert-rules.yml.j2`**
- Alert rules for YAWL metrics

**`templates/grafana.ini.j2`**
- Grafana configuration

**`templates/grafana-datasources.yml.j2`**
- Grafana datasource definitions

**`templates/grafana-dashboards.yml.j2`**
- Grafana dashboard provisioning

**`templates/dashboard-*.json.j2`** (Multiple)
- Individual dashboard definitions (YAWL overview, app performance, DB performance, etc.)

**`templates/elasticsearch.yml.j2`**
- Elasticsearch configuration

**`templates/jvm.options.j2`**
- Elasticsearch JVM options

**`templates/kibana.yml.j2`**
- Kibana configuration

**`templates/kibana-dashboard-yawl.json.j2`**
- Kibana dashboard for YAWL logs

**`templates/logstash.yml.j2`**
- Logstash configuration

**`templates/logstash-pipeline.conf.j2`**
- Logstash pipeline configuration

**`templates/logstash-jvm.options.j2`**
- Logstash JVM options

**`templates/alertmanager.yml.j2`**
- Alertmanager configuration

**`templates/alertmanager.service.j2`**
- Alertmanager systemd service

**`templates/filebeat.yml.j2`**
- Filebeat configuration

**`templates/ufw-monitoring.rules.j2`**
- UFW firewall rules for monitoring

**`templates/generate-dashboards.sh.j2`**
- Script to generate dashboards

**`templates/backup-monitoring.sh.j2`**
- Backup script for monitoring data

---

## Key Features

### Multi-Node Architecture
- **Database Tier**: Primary/Standby PostgreSQL with streaming replication
- **Application Tier**: 3 Tomcat nodes running YAWL with Docker containerization
- **Monitoring Tier**: Centralized monitoring with Prometheus, Grafana, and ELK stack

### High Availability
- PostgreSQL replication for automatic failover
- Multiple application nodes for load balancing
- Centralized monitoring and alerting

### Automation
- Complete infrastructure as code
- Idempotent tasks for safe re-runs
- Automatic health checks and verification
- Self-healing capabilities

### Monitoring & Observability
- Prometheus for metrics collection
- Grafana for visualization
- Elasticsearch and Kibana for centralized logging
- Custom dashboards for YAWL-specific metrics

### Security
- Vault integration for secrets management
- SSL/TLS configuration support
- Network policies and firewall rules
- PostgreSQL authentication and authorization

### Maintainability
- Comprehensive documentation
- Quick start automation script
- Structured variable organization
- Clear role separation

---

## File Count Summary

```
Total Playbooks: 1
Total Inventory Files: 1
Total Configuration Files: 1
Total Documentation: 4
Total Script Files: 1

Roles: 4
- Role Tasks: 13 files
- Role Handlers: 4 files
- Role Defaults: 4 files
- Role Templates: 50+ files (referenced in tasks)

Variable Files:
- Group Variables: 4 files
- Host Variables: 6 files

Total Configuration Items: 100+
Total Lines of Code/Config: 5000+
```

---

## Usage Summary

```bash
# Quick setup
./QUICK_START.sh

# Full deployment
ansible-playbook -i inventory.ini playbook.yml --ask-vault-pass

# Component-specific deployment
ansible-playbook -i inventory.ini playbook.yml --limit db_servers --ask-vault-pass
ansible-playbook -i inventory.ini playbook.yml --limit app_servers --ask-vault-pass
ansible-playbook -i inventory.ini playbook.yml --limit monitoring_servers --ask-vault-pass

# Dry run
ansible-playbook -i inventory.ini playbook.yml --check --ask-vault-pass

# Verbose output
ansible-playbook -i inventory.ini playbook.yml -vvv --ask-vault-pass
```

---

## Next Steps

1. Review all variable files for your environment
2. Run QUICK_START.sh to setup inventory and SSH access
3. Follow DEPLOYMENT_GUIDE.md for step-by-step deployment
4. Refer to README.md for detailed documentation
5. Consult role-specific templates for customization
