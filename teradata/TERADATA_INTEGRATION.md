# YAWL Workflow Engine - Teradata Integration Guide

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Setup Instructions](#setup-instructions)
5. [Docker Configuration](#docker-configuration)
6. [Schema and Database Setup](#schema-and-database-setup)
7. [Terraform Deployment](#terraform-deployment)
8. [JDBC Configuration](#jdbc-configuration)
9. [Security Best Practices](#security-best-practices)
10. [Monitoring and Operations](#monitoring-and-operations)
11. [Troubleshooting](#troubleshooting)
12. [Performance Tuning](#performance-tuning)
13. [Disaster Recovery](#disaster-recovery)
14. [Migration Guide](#migration-guide)

---

## Overview

This document provides comprehensive guidance for integrating YAWL (Yet Another Workflow Language) workflow engine with Teradata database. This integration enables YAWL to leverage Teradata's high-performance data warehousing capabilities for storing and managing workflow data, process definitions, and execution history.

### Benefits of Teradata Integration

- **Scalability**: Handle millions of workflow instances and transactions
- **Performance**: Ultra-fast query execution for complex workflow analytics
- **Reliability**: Enterprise-grade reliability with 99.99% uptime SLA
- **Analytics**: Advanced analytics on workflow execution metrics
- **Compliance**: Support for data residency and compliance requirements
- **Cost Efficiency**: Optimized resource utilization and management

### Use Cases

1. **High-volume Workflow Processing**: Financial institutions processing millions of daily transactions
2. **Complex Analytics**: Real-time workflow performance metrics and bottleneck identification
3. **Historical Analysis**: Long-term workflow pattern analysis and process optimization
4. **Multi-tenant Systems**: Isolated workflow execution for different organizational units
5. **Compliance-heavy Industries**: Healthcare, finance, and government workflows with strict data requirements

---

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                     YAWL Workflow Engine                     │
│                    (Tomcat Container)                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            │                     │
    ┌───────▼────────┐   ┌───────▼────────┐
    │  PostgreSQL    │   │    Teradata    │
    │  (Metadata)    │   │   (Workflows)  │
    └────────────────┘   └────────────────┘
            │                     │
    ┌───────▼─────────────────────▼────────┐
    │   Cloud Storage (Backups)            │
    └──────────────────────────────────────┘
```

### Data Storage Strategy

- **PostgreSQL**: YAWL metadata, configuration, user preferences
- **Teradata**: Workflow process definitions, execution instances, audit logs, analytics tables
- **Cloud Storage**: Backups, archived data, documents

### Connection Architecture

```
YAWL Application
    ↓
JDBC Connection Pool (20 connections)
    ↓
Teradata JDBC Driver
    ↓
Teradata Database (TCP/1025)
    ↓
Teradata Execution Engine
```

---

## Prerequisites

### System Requirements

- **GCP Project**: Active GCP account with billing enabled
- **Terraform**: >= 1.3.0
- **Docker**: >= 20.10 (for container deployment)
- **kubectl**: >= 1.20 (for Kubernetes operations)
- **gcloud CLI**: Latest version with GCP SDK

### Required GCP APIs

Enable these APIs in your GCP project:

```bash
gcloud services enable compute.googleapis.com
gcloud services enable container.googleapis.com
gcloud services enable sqladmin.googleapis.com
gcloud services enable redis.googleapis.com
gcloud services enable storage.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable logging.googleapis.com
gcloud services enable monitoring.googleapis.com
gcloud services enable kms.googleapis.com
```

### Required Credentials and Permissions

```bash
# Create service account for Terraform
gcloud iam service-accounts create yawl-teradata-terraform \
    --display-name="YAWL Teradata Terraform"

# Grant necessary roles
gcloud projects add-iam-policy-binding PROJECT_ID \
    --member="serviceAccount:yawl-teradata-terraform@PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/compute.admin"

gcloud projects add-iam-policy-binding PROJECT_ID \
    --member="serviceAccount:yawl-teradata-terraform@PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/container.admin"

# ... (add other necessary roles)
```

### Teradata License

- Obtain Teradata Express Edition or production license
- Ensure license includes required modules (SQL Engine, JDBC driver)

---

## Setup Instructions

### 1. Prepare GCP Project

```bash
export PROJECT_ID="your-gcp-project"
export REGION="us-central1"

gcloud config set project $PROJECT_ID
gcloud config set compute/region $REGION

# Create Terraform state bucket
gsutil mb -p $PROJECT_ID gs://yawl-terraform-state

# Enable versioning on state bucket
gsutil versioning set on gs://yawl-terraform-state
```

### 2. Download Teradata JDBC Driver

```bash
# Download from Teradata website (requires account)
# https://downloads.teradata.com/

# Place in teradata/lib/ directory
mkdir -p /home/user/yawl/teradata/lib
cp terajdbc4.jar /home/user/yawl/teradata/lib/
cp tdgssconfig.jar /home/user/yawl/teradata/lib/
```

### 3. Prepare Configuration

```bash
cd /home/user/yawl/teradata

# Copy example variables
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
vi terraform.tfvars

# Validate configuration
terraform validate
```

### 4. Initialize Terraform

```bash
terraform init \
    -backend-config="bucket=yawl-terraform-state" \
    -backend-config="prefix=teradata" \
    -backend-config="project=$PROJECT_ID"
```

---

## Docker Configuration

### Building the Docker Image

```dockerfile
# The provided Dockerfile includes:
# - Multi-stage build for efficient image size
# - YAWL build with Ant
# - Tomcat runtime with Java 11
# - Teradata JDBC driver integration
# - PostgreSQL client for health checks
```

### Build Command

```bash
cd /home/user/yawl

docker build \
    -f teradata/Dockerfile \
    -t gcr.io/$PROJECT_ID/yawl-teradata:latest \
    -t gcr.io/$PROJECT_ID/yawl-teradata:4.3 \
    .

# Push to Artifact Registry
docker push gcr.io/$PROJECT_ID/yawl-teradata:latest
```

### Environment Variables

The Docker container supports these environment variables:

```bash
# PostgreSQL (YAWL Metadata)
YAWL_DB_HOST=cloudsql-proxy
YAWL_DB_PORT=5432
YAWL_DB_NAME=yawl_metadata
YAWL_DB_USER=yawl_user
YAWL_DB_PASSWORD=<password>

# Teradata (Workflow Data)
TERADATA_HOST=<teradata-instance-ip>
TERADATA_PORT=1025
TERADATA_USER=yawl_user
TERADATA_PASSWORD=<password>
TERADATA_DATABASE=yawl_workflow
TERADATA_CHARSET=UTF8
TERADATA_LOGON_TIMEOUT=30

# Tomcat Configuration
TOMCAT_HEAP_SIZE=1024m
```

### Docker Compose Example

```yaml
version: '3.8'

services:
  yawl-teradata:
    image: gcr.io/project-id/yawl-teradata:latest
    ports:
      - "8080:8080"
    environment:
      YAWL_DB_HOST: postgres
      YAWL_DB_PORT: 5432
      TERADATA_HOST: teradata
      TERADATA_PORT: 1025
    depends_on:
      - postgres
      - teradata
    volumes:
      - ./logs:/usr/local/tomcat/logs
      - ./webapps:/usr/local/tomcat/webapps

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: yawl_metadata
      POSTGRES_USER: yawl_user
      POSTGRES_PASSWORD: changeme

  teradata:
    image: teradata/teradata-express:latest
    ports:
      - "1025:1025"
```

---

## Schema and Database Setup

### Database Creation

```bash
# Connect to Teradata
bteq

# Execute schema creation script
.RUN FILE = schema-yawl-base.sql;
.RUN FILE = schema-yawl-extensions.sql;

.EXIT;
```

### Schema Components

#### Core Tables

1. **process_definitions**: Process templates and specifications
2. **process_instances**: Running or completed workflow instances
3. **tasks**: Task definitions within processes
4. **task_instances**: Individual task executions
5. **participants**: Workflow users and resources
6. **worklist_items**: User task assignments
7. **audit_log**: Complete audit trail
8. **exceptions**: Exception and error tracking

#### Extension Tables

1. **worklet_definitions**: Dynamic workflow templates
2. **process_versions**: Version history
3. **process_metrics**: Performance metrics
4. **documents**: Attached documents
5. **calendars**: Business calendars
6. **notifications**: Notification queue

### Initial Data Population

```sql
-- Initialize system properties
INSERT INTO system_properties (property_key, property_value)
VALUES ('yawl.version', '4.3');

-- Create initial users
INSERT INTO participants (user_id, name, email, role)
VALUES ('admin', 'Administrator', 'admin@example.com', 'ADMIN');

-- Create resource pools
INSERT INTO resource_pools (pool_name, pool_type)
VALUES ('General Users', 'USER_POOL');
```

---

## Terraform Deployment

### Deployment Plan

```bash
cd /home/user/yawl/teradata

# Generate and review execution plan
terraform plan -out=tfplan

# Review proposed changes
terraform show tfplan
```

### Deploy Infrastructure

```bash
# Apply Terraform configuration
terraform apply tfplan

# Save outputs
terraform output > deployment-outputs.txt
```

### Verifying Deployment

```bash
# Check Teradata instance
gcloud compute instances list --filter="name:yawl-teradata"

# Check GKE cluster
gcloud container clusters list --region=$REGION

# Check Cloud SQL
gcloud sql instances list

# Check Redis
gcloud redis instances list --region=$REGION
```

### Post-Deployment Steps

```bash
# Get GKE credentials
gcloud container clusters get-credentials yawl-teradata-cluster \
    --region $REGION

# Verify connectivity to Teradata
gcloud compute ssh yawl-teradata --zone=$REGION-a \
    --command="bteq < EOF
        .logevent console;
        SELECT COUNT(*) FROM dbc.tables;
        .EXIT;
EOF"
```

---

## JDBC Configuration

### JDBC Connection String Format

```
jdbc:teradata://<host>/DBS_PORT=<port>,DATABASE=<database>,USER=<user>,PASSWORD=<password>,CHARSET=<charset>
```

### Example Connection Properties

```xml
<!-- Tomcat context.xml -->
<Resource
    name="jdbc/TeradataWorkflow"
    auth="Container"
    type="javax.sql.DataSource"
    url="jdbc:teradata://teradata-instance/DBS_PORT=1025,DATABASE=yawl_workflow,CHARSET=UTF8"
    username="yawl_user"
    password="${DB_PASSWORD}"
    driverClassName="com.teradata.jdbc.TeraDriver"
    maxActive="20"
    maxIdle="10"
    maxWait="30000"
    validationQuery="SELECT 1"
    testOnBorrow="true"
    testWhileIdle="true"
    timeBetweenEvictionRunsMillis="30000"
/>
```

### Connection Pool Configuration

```properties
# Teradata Connection Pool Settings
teradata.connection.pool.size=20
teradata.connection.pool.min.idle=5
teradata.connection.pool.max.lifetime=1800000
teradata.connection.pool.idle.timeout=600000
teradata.connection.pool.connection.timeout=30000
```

### Performance Tuning

```java
// Java configuration for optimal performance
Properties props = new Properties();
props.setProperty("LOGON_TIMEOUT", "30");
props.setProperty("SESSION_MODE", "TERADATA");
props.setProperty("DATABASE", "yawl_workflow");
props.setProperty("CHARSET", "UTF8");
props.setProperty("LOB_SUPPORT", "true");
props.setProperty("LOB_MAX_SIZE", "104857600");  // 100MB
```

---

## Security Best Practices

### 1. Secret Management

```bash
# Store credentials in GCP Secret Manager
echo -n "teradata-password" | gcloud secrets create teradata-password --data-file=-

# Grant access to service accounts
gcloud secrets add-iam-policy-binding teradata-password \
    --member=serviceAccount:yawl-sa@$PROJECT_ID.iam.gserviceaccount.com \
    --role=roles/secretmanager.secretAccessor
```

### 2. Network Security

```hcl
# Terraform: Restrict network access
resource "google_compute_firewall" "teradata_restricted" {
  name    = "teradata-restricted"
  network = google_compute_network.yawl_teradata.name

  allow {
    protocol = "tcp"
    ports    = ["1025"]
  }

  source_ranges = ["10.0.0.0/20"]  # Only from GKE subnet
  target_tags   = ["teradata"]
}
```

### 3. Encryption

```hcl
# Enable encryption at rest with KMS
boot_disk {
  kms_key_name = google_kms_crypto_key.database.id
}

# Enable encryption in transit
transit_encryption_mode = "SERVER_AUTHENTICATION"
```

### 4. IAM Permissions

```bash
# Create custom role with minimal permissions
gcloud iam roles create yawlTeradataOperator --project=$PROJECT_ID \
    --title="YAWL Teradata Operator" \
    --description="Minimal permissions for YAWL Teradata operations" \
    --permissions="compute.instances.get,compute.instances.list,cloudsql.databases.list"
```

### 5. SSL/TLS Configuration

```xml
<Connector
    protocol="org.apache.coyote.http11.Http11NioProtocol"
    port="8443"
    maxThreads="150"
    scheme="https"
    secure="true"
    keystoreFile="${catalina.base}/conf/keystore.jks"
    keystorePass="${KEYSTORE_PASSWORD}"
    keyAlias="yawl-teradata"
/>
```

### 6. Data Masking

```sql
-- Create masked view for sensitive data
CREATE VIEW vw_task_instances_masked AS
SELECT
    id,
    task_id,
    case_id,
    status,
    SUBSTR(data, 1, 100) || '***' AS data_preview,
    created_at
FROM task_instances;
```

---

## Monitoring and Operations

### Cloud Monitoring Setup

```bash
# Install monitoring agent on Teradata instance
gcloud compute ssh yawl-teradata --zone=$REGION-a \
    --command="curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
sudo sh add-google-cloud-ops-agent-repo.sh --also-install"
```

### Key Metrics to Monitor

1. **Database Metrics**
   - CPU Utilization
   - Memory Usage
   - Disk Space
   - Query Performance
   - Connection Count

2. **YAWL Metrics**
   - Active Workflow Instances
   - Task Completion Rate
   - Average Task Duration
   - Exception Rate
   - System Throughput (tasks/hour)

3. **System Metrics**
   - Backup Success Rate
   - Replication Lag
   - Network Latency
   - Error Rates

### Alert Policies

```hcl
# Terraform: Create alert for high CPU
resource "google_monitoring_alert_policy" "cpu_high" {
  display_name = "YAWL Teradata CPU High"
  combiner     = "OR"

  conditions {
    display_name = "CPU > 80%"
    condition_threshold {
      filter          = "metric.type=\"compute.googleapis.com/instance/cpu/utilization\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0.8
      duration        = "300s"
    }
  }

  notification_channels = [google_monitoring_notification_channel.ops_email.id]
}
```

### Logging

```bash
# View logs for troubleshooting
gcloud logging read "resource.type=gce_instance AND resource.labels.instance_id=<instance-id>" \
    --limit 100 \
    --format json

# Export logs to Cloud Storage
gcloud logging sinks create yawl-teradata-logs \
    storage.googleapis.com/yawl-logs \
    --log-filter='resource.type=gce_instance'
```

### Backup Verification

```bash
# List available backups
gsutil ls gs://yawl-teradata-backups/

# Verify backup integrity
gsutil hash gs://yawl-teradata-backups/backup-latest.bak
```

---

## Troubleshooting

### Common Issues and Solutions

#### 1. Connection Timeout

**Symptom**: "Connection refused" or "Connection timeout"

**Solution**:
```bash
# Verify Teradata is running
gcloud compute ssh yawl-teradata --command="teradatactl status"

# Check firewall rules
gcloud compute firewall-rules list --filter="name:teradata"

# Verify network connectivity
gcloud compute ssh yawl-teradata --command="nc -zv teradata-instance 1025"
```

#### 2. Memory Errors

**Symptom**: "OutOfMemory" in YAWL logs

**Solution**:
```bash
# Increase heap size
export TOMCAT_HEAP_SIZE=2048m

# Check memory usage
gcloud compute instances describe yawl-teradata --format="value(machineType)"

# Scale up instance if needed
gcloud compute instances set-machine-type yawl-teradata --machine-type=n2-highmem-16
```

#### 3. Slow Queries

**Symptom**: YAWL operations are slow

**Solution**:
```sql
-- Check table statistics
SHOW STATISTICS FOR TABLE process_instances;

-- Rebuild indexes if needed
COLLECT STATISTICS ON process_instances;

-- Analyze slow queries
EXPLAIN SELECT * FROM process_instances WHERE status = 'ACTIVE';
```

#### 4. Storage Full

**Symptom**: "No space available" errors

**Solution**:
```bash
# Check disk usage
gcloud compute ssh yawl-teradata --command="df -h"

# Archive old data
# Expand disk (GCP auto-expands if autoresize is enabled)

# Clean up temporary files
gcloud compute ssh yawl-teradata \
    --command="sudo rm -rf /tmp/teradata-*"
```

### Debug Logging

```xml
<!-- Enable debug logging in Tomcat -->
<Logger name="com.teradata" level="DEBUG"/>
<Logger name="org.yawl" level="DEBUG"/>

<!-- JDBC logging -->
<Logger name="com.teradata.jdbc" level="DEBUG"/>
```

---

## Performance Tuning

### Database Tuning

```sql
-- Teradata system parameters optimization
MODIFY SYSTEM SESSION SET QueryJobLimit = 100;
MODIFY SYSTEM SESSION SET QueryTimeout = 600;

-- Enable parallel processing
SET QUERY_BAND = 'ParallelGrades=1' FOR SESSION;

-- Optimize spool settings
MODIFY SYSTEM PARAMETER MaxSpoolRecordSize = 10485760;
```

### Connection Pool Tuning

```properties
# HikariCP Configuration (recommended for JDBC)
hikaricp.maximum-pool-size=20
hikaricp.minimum-idle=5
hikaricp.connection-timeout=30000
hikaricp.idle-timeout=600000
hikaricp.max-lifetime=1800000
hikaricp.auto-commit=true
```

### Index Optimization

```sql
-- Create indexes for frequently queried columns
CREATE INDEX idx_process_instances_status
    ON process_instances (status);

CREATE INDEX idx_task_instances_assigned
    ON task_instances (assigned_to);

CREATE INDEX idx_audit_log_timestamp
    ON audit_log (timestamp);

-- Composite indexes for common queries
CREATE INDEX idx_worklist_participant_status
    ON worklist_items (participant_id, status);
```

### Caching Strategy

```java
// Enable Redis caching for workflow queries
@Cacheable(value = "processDefinitions", key = "#name")
public ProcessDefinition getProcessDefinition(String name) {
    return processDefinitionRepository.findByName(name);
}

// Cache invalidation on updates
@CacheEvict(value = "processDefinitions", key = "#result.name")
public ProcessDefinition saveProcessDefinition(ProcessDefinition def) {
    return processDefinitionRepository.save(def);
}
```

---

## Disaster Recovery

### Backup Strategy

```bash
# Automated daily backups (Terraform managed)
# Backup window: 2:00 AM UTC
# Retention: 30 days
# Location: Cloud Storage with versioning

# Manual backup before major changes
gsutil cp -r gs://yawl-teradata-backups/latest \
    gs://yawl-teradata-backups/pre-maintenance-$(date +%Y%m%d)
```

### Restore Procedures

#### From Cloud Storage Backup

```bash
# List available backups
gsutil ls gs://yawl-teradata-backups/

# Download backup
gsutil cp gs://yawl-teradata-backups/backup-latest.bak ./

# Restore to Teradata
gcloud compute ssh yawl-teradata --command="bteq < restore-backup.sql"
```

#### Point-in-Time Recovery

```sql
-- Teradata PITR (requires transaction log retention)
RESTORE DATABASE yawl_workflow
    FROM BACKUP
    UNTIL TIMESTAMP '2024-02-14 14:30:00';
```

### RTO and RPO

```
Recovery Time Objective (RTO):
- Database: 1 hour
- Application: 30 minutes
- Total: 1.5 hours

Recovery Point Objective (RPO):
- Transactions: 15 minutes (backup frequency)
- Data: 15 minutes
```

### Testing Recovery

```bash
# Monthly disaster recovery drill
# Restore to test environment
# Verify data integrity
# Document results

#!/bin/bash
# dr-test.sh
echo "Starting DR test..."

# Restore latest backup to test DB
gcloud sql instances clone yawl-metadata yawl-metadata-test

# Verify data
gcloud sql connect yawl-metadata-test --user=yawl_user

echo "DR test completed"
```

---

## Migration Guide

### Pre-Migration Checklist

- [ ] Backup current YAWL database
- [ ] Verify Teradata connectivity
- [ ] Create equivalent schema in Teradata
- [ ] Test JDBC driver compatibility
- [ ] Plan maintenance window
- [ ] Notify users of downtime
- [ ] Prepare rollback plan

### Migration Steps

```bash
# 1. Export data from existing database
mysqldump -u root -p yawl_workflow > yawl_export.sql

# 2. Transform data to Teradata format
python3 transform-sql.py yawl_export.sql > yawl_teradata.sql

# 3. Load data into Teradata
gcloud compute ssh yawl-teradata --command="bteq < yawl_teradata.sql"

# 4. Verify data integrity
./verify-migration.sh

# 5. Update JDBC connection strings
# Update teradata-connection.properties

# 6. Restart YAWL application
kubectl rollout restart deployment/yawl-deployment

# 7. Validate functionality
./smoke-tests.sh
```

### Post-Migration Validation

```sql
-- Verify data counts
SELECT 'process_definitions' AS table_name, COUNT(*) AS row_count
FROM process_definitions
UNION ALL
SELECT 'process_instances', COUNT(*) FROM process_instances
UNION ALL
SELECT 'audit_log', COUNT(*) FROM audit_log;

-- Check for data anomalies
SELECT * FROM process_instances WHERE start_time > CURRENT_TIMESTAMP;
SELECT * FROM audit_log WHERE timestamp > CURRENT_TIMESTAMP;
```

---

## Additional Resources

### Documentation Links

- [YAWL Foundation](https://yawlfoundation.github.io)
- [Teradata Database Documentation](https://docs.teradata.com)
- [Teradata JDBC Driver Guide](https://downloads.teradata.com/docs/JDBC)
- [GCP Terraform Provider](https://registry.terraform.io/providers/hashicorp/google/latest/docs)

### Support Contacts

- YAWL Support: support@yawlfoundation.org
- Teradata Support: support.teradata.com
- GCP Support: cloud.google.com/support

### Useful Commands Reference

```bash
# Terraform
terraform init          # Initialize Terraform
terraform plan          # Generate execution plan
terraform apply         # Apply changes
terraform destroy       # Destroy infrastructure
terraform output        # Display outputs
terraform state list    # List resources
terraform fmt           # Format configuration

# Teradata
bteq                    # Teradata command-line interface
tpt                     # Teradata Parallel Transporter
tdwm                    # Teradata Workload Manager
teradatactl             # Teradata control utility

# GCP
gcloud compute ssh      # SSH to instance
gcloud logging read     # View logs
gcloud monitoring       # Monitoring commands
gsutil                  # Google Cloud Storage CLI
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-02-14 | Initial release |

---

## License

This integration guide is provided as part of the YAWL Workflow Engine project.
Refer to the main LICENSE file for terms and conditions.

---

**Last Updated**: 2024-02-14
**Maintained By**: YAWL Development Team
