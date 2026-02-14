# Backup & Disaster Recovery Infrastructure

Comprehensive multi-cloud backup and disaster recovery solution with detailed RTO/RPO calculations for AWS, GCP, and Azure.

## Overview

This backup-recovery suite provides enterprise-grade backup and disaster recovery infrastructure across three major cloud providers. All components include Recovery Time Objective (RTO) and Recovery Point Objective (RPO) calculations to meet stringent recovery requirements.

### Key Metrics

| Cloud Provider | RTO | RPO | Status |
|---|---|---|---|
| **AWS** | 15-30 min | 5-15 min | ✓ Production Ready |
| **GCP** | 20-30 min | 10-15 min | ✓ Production Ready |
| **Azure** | 25-35 min | 15-20 min | ✓ Production Ready |

---

## 📁 Directory Structure

```
backup-recovery/
├── backup-strategy.sh                    # Automated backup orchestration script
├── disaster_recovery_plan.md             # Comprehensive DRP documentation
├── aws-cross-region-backup.tf            # AWS Terraform infrastructure
├── gcp-backup-templates.yaml             # GCP Kubernetes manifests
├── azure-backup-arm-templates.json       # Azure ARM templates
└── README.md                             # This file
```

---

## 📄 File Descriptions

### 1. backup-strategy.sh
**Purpose**: Orchestrates automated backups across all three cloud providers

**Features**:
- Multi-cloud backup automation (AWS, GCP, Azure)
- RDS, S3, EBS snapshots (AWS)
- Cloud SQL, GCS, GKE backups (GCP)
- VM snapshots, SQL, Storage backups (Azure)
- Backup verification and checksums
- Retention policy enforcement (default: 30 days)
- Email notifications and alerting
- Comprehensive logging with color-coded output

**Usage**:
```bash
chmod +x /home/user/yawl/backup-recovery/backup-strategy.sh
./backup-strategy.sh
```

**Configuration**:
- Edit variables at the top of the script to customize:
  - `RETENTION_DAYS`: How long to keep backups (default: 30)
  - `BACKUP_TIMEOUT`: Max execution time (default: 3600s)
  - `ALERT_EMAIL`: Notification recipient

**RTO/RPO Configuration**:
```bash
AWS_RTO_MINUTES=15      # Database failover to backup region
AWS_RPO_MINUTES=5       # Transaction log backup frequency
GCP_RTO_MINUTES=20      # Cluster restoration time
GCP_RPO_MINUTES=10      # Binary log replication lag
AZURE_RTO_MINUTES=25    # Database geo-restore time
AZURE_RPO_MINUTES=15    # Backup frequency
```

---

### 2. disaster_recovery_plan.md
**Purpose**: Complete disaster recovery documentation with procedures and calculations

**Sections**:
1. **Executive Summary** - Business continuity overview
2. **RTO/RPO Matrices** - Detailed calculations for each cloud service
3. **Disaster Scenarios** - 5 recovery scenarios with step-by-step procedures:
   - AWS Region Failure
   - GCP Multi-Zone Cluster Failure
   - Azure Storage Account Corruption
   - Database Ransomware Encryption
   - Complete Multi-Cloud Failure
4. **Backup Strategy** - Schedule, retention, and verification procedures
5. **Communication Plan** - Escalation procedures and templates
6. **Testing & Maintenance** - Quarterly DR test schedule and validation checklist
7. **Appendix** - RTO/RPO calculation formulas and references

**RTO/RPO Calculations**:

The document includes detailed calculations such as:

**RTO Formula**:
```
RTO = Detection Time (5 min)
    + Assessment Time (5 min)
    + Failover Time (15 min)
    + Validation Time (5 min)
    = 30 minutes
```

**RPO Formula**:
```
RPO = Max[Backup Interval (5 min), Replication Lag (2 min)]
    = 5 minutes maximum acceptable data loss
```

---

### 3. aws-cross-region-backup.tf
**Purpose**: Terraform infrastructure for AWS cross-region backup and disaster recovery

**Components**:

#### RDS (Relational Database Service)
- **RTO**: 15 minutes | **RPO**: 5 minutes
- Primary RDS instance with Multi-AZ deployment
- Automated daily snapshots with 30-day retention
- Point-in-time recovery with transaction log backups
- Cross-region read replica in backup region
- KMS encryption for data at rest
- Enhanced monitoring and performance insights

#### S3 (Simple Storage Service)
- **RTO**: 30 minutes | **RPO**: 15 minutes
- Cross-region replication with 15-minute replication time metrics
- Versioning enabled for object recovery
- Lifecycle policies for automatic tiering to Glacier
- Server-side encryption with KMS

#### EBS (Elastic Block Store)
- **RTO**: 20 minutes | **RPO**: 10 minutes
- Data Lifecycle Manager (DLM) for automated snapshots
- Daily snapshots with cross-region copies
- Encrypted snapshots in backup region
- 30-day retention (configurable)

#### AWS Backup Service
- Centralized backup management
- Cross-region backup vaults
- Backup plans for RDS and EBS
- Long-term retention policies
- Backup copy to cold storage after 7-14 days

**Deployment**:
```bash
cd /home/user/yawl/backup-recovery
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

**Variables**:
```hcl
primary_region              = "us-east-1"      # Production region
backup_region               = "us-west-2"      # DR region
backup_retention_days       = 30               # Retention period
rds_instance_id             = "production-db"  # RDS instance name
s3_bucket_name              = "production-data" # S3 bucket prefix
```

**Outputs**:
```
primary_db_endpoint      - Primary RDS endpoint
backup_db_endpoint       - Read replica endpoint
primary_bucket_name      - Primary S3 bucket
backup_bucket_name       - Backup S3 bucket
rto_estimate            - Recovery Time Objective
rpo_estimate            - Recovery Point Objective
```

---

### 4. gcp-backup-templates.yaml
**Purpose**: Kubernetes manifests for GCP backup infrastructure using Config Connector

**Components** (20 Templates):

1. **Cloud SQL Backup** (RTO: 20 min, RPO: 10 min)
   - REGIONAL availability (multi-zone)
   - Binary logs enabled for PITR
   - 30-day backup retention
   - Query Insights enabled

2. **GCS Cross-Region Replication**
   - Primary bucket: us-central1 (STANDARD)
   - Backup bucket: us-west1 (COLDLINE)
   - Daily transfer jobs
   - Automatic tiering lifecycle policies

3. **GKE Cluster Backup** (RTO: 30 min, RPO: 15 min)
   - Backup plan with daily scheduling
   - All namespaces included
   - Persistent volumes backed up
   - Secrets encrypted

4. **Firestore Database** (RTO: 15 min, RPO: 5 min)
   - Point-in-time recovery enabled
   - Daily exports to BigQuery
   - Delete protection enabled

5. **Cloud Spanner Backups** (RTO: 25 min, RPO: 5 min)
   - Automated daily backups
   - 30-day retention

6. **BigQuery Snapshots**
   - Table snapshots for recovery
   - Cost optimization with lifecycle policies

7. **KMS Encryption** (Key Management Service)
   - Encryption keys for all services
   - Automatic key rotation (30 days)
   - Separate keys per service

8. **Monitoring & Alerts**
   - RTO/RPO status dashboard
   - Backup failure alerts via Pub/Sub
   - Replication lag monitoring

**Deployment**:
```bash
# Prerequisites: GCP project with Config Connector enabled
kubectl apply -f gcp-backup-templates.yaml

# Verify resources
kubectl get sqlinstances -n gcp-backup
kubectl get storagebuckets -n gcp-backup
kubectl get gkebackupbackupplans -n gcp-backup
```

---

### 5. azure-backup-arm-templates.json
**Purpose**: Azure Resource Manager templates for multi-region backup infrastructure

**Components**:

#### Storage Accounts
- **Primary**: GRS (Geo-Redundant Storage) in primary region
- **Backup**: ZRS (Zone-Redundant Storage) in backup region
- Blob containers with versioning enabled
- Encryption with customer-managed keys

#### Azure SQL Database
- **RTO**: 25 minutes | **RPO**: 15 minutes
- Zone-redundant database
- Geo-restore enabled
- 30-day backup retention
- Long-term retention (LTR): 4W, 12M, 5Y

#### MySQL & PostgreSQL
- Geo-redundant automatic backups
- 30-day backup retention
- Storage autogrow enabled
- SSL/TLS enforcement

#### Recovery Services Vault
- **Cross-region restore**: Enabled
- **Storage redundancy**: Geo-Redundant
- Daily backup policy with instant recovery points
- Weekly, monthly, yearly retention

#### Azure Key Vault
- Customer-managed encryption keys
- Role-based access control
- Network ACLs with AzureServices bypass
- Key rotation every 90 days

#### Monitoring
- Log Analytics workspace for backup logs
- Action group for alerting
- Metric alerts for backup failures
- Diagnostic settings for auditing

#### Disk Encryption Set (DES)
- Azure Disk Encryption with customer-managed keys
- Automatic key rotation
- Audit logging

**Deployment**:
```bash
# Deploy to primary region
az deployment group create \
  --resource-group production-rg \
  --template-file azure-backup-arm-templates.json \
  --parameters primaryRegion=eastus backupRegion=westus2

# Verify deployment
az resource list --resource-group production-rg \
  --query "[].{name:name, type:type}"
```

**Parameters**:
```json
{
  "projectPrefix": "prod",
  "primaryRegion": "eastus",
  "backupRegion": "westus2",
  "backupRetentionDays": 30,
  "environment": "production",
  "rtoMinutes": 25,
  "rpoMinutes": 15
}
```

---

## 🔄 RTO/RPO Calculations

### How RTO is Calculated

**RTO = Detection Time + Assessment Time + Failover Time + Validation Time**

Example for AWS:
```
Detection (5 min):     Health checks detect region failure
Assessment (5 min):    Verify failure scope and backup integrity
Failover (15 min):     Launch instances, restore databases from snapshots
Validation (5 min):    Test application connectivity and data
─────────────────────────────────────────
Total RTO = 30 minutes
```

### How RPO is Calculated

**RPO = Max[Backup Frequency, Replication Lag]**

Example for AWS:
```
Backup Frequency (5 min):   Transaction logs backed up every 5 minutes
Replication Lag (2 min):    Cross-region snapshot copy delay ~2 minutes
─────────────────────────────────────────
Maximum Data Loss (RPO) = 5 minutes
```

### Service-Specific Calculations

**AWS RDS**:
- Backup interval: Every 5 minutes (transaction logs)
- Replication to backup region: 2-5 minutes
- Instance provisioning: 5-10 minutes
- **Total RTO**: 15-20 minutes
- **Total RPO**: 5-7 minutes

**GCP Cloud SQL**:
- Binary log replication: Real-time to replica
- Failover time (HA): 5 minutes
- Backup restoration: 15-20 minutes
- **Total RTO**: 5-20 minutes (depends on HA enabled)
- **Total RPO**: 0-10 minutes

**Azure SQL Database**:
- Geo-replication lag: 5 minutes typical
- Geo-restore time: 15-20 minutes
- Point-in-time restore: 10-15 minutes
- **Total RTO**: 15-25 minutes
- **Total RPO**: 5-15 minutes

---

## 🚀 Deployment Guide

### Prerequisites

```bash
# AWS
pip install awscli boto3
aws configure

# GCP
pip install google-cloud-storage google-cloud-sql
gcloud auth login
gcloud config set project PROJECT_ID

# Azure
pip install azure-cli
az login

# Terraform (for AWS)
terraform version  # >= 1.0

# kubectl (for GCP)
kubectl version
gcloud container clusters get-credentials CLUSTER_NAME --region REGION
```

### Quick Start

#### 1. AWS Deployment
```bash
cd /home/user/yawl/backup-recovery
terraform init -backend-config="bucket=your-tf-state-bucket"
terraform validate
terraform apply -var-file="production.tfvars"
```

#### 2. GCP Deployment
```bash
# Ensure Config Connector is installed
kubectl get crds | grep cnrm

# Apply templates
kubectl apply -f gcp-backup-templates.yaml

# Monitor deployment
kubectl get sqlinstances,storagebuckets,gkebackupbackupplans -n gcp-backup
```

#### 3. Azure Deployment
```bash
# Create resource group
az group create --name production-rg --location eastus

# Deploy ARM template
az deployment group create \
  --resource-group production-rg \
  --template-file azure-backup-arm-templates.json \
  --parameters projectPrefix=prod

# Verify resources
az resource list --resource-group production-rg
```

#### 4. Execute Backups
```bash
chmod +x /home/user/yawl/backup-recovery/backup-strategy.sh
./backup-strategy.sh

# Check logs
tail -f logs/backup_*.log
```

---

## 📊 Monitoring & Alerts

### AWS CloudWatch
```bash
# Monitor RDS backups
aws rds describe-db-backups --query 'DBBackups[].[DBInstanceIdentifier,DBBackupIdentifier,BackupCreateTime]'

# Check snapshot status
aws ec2 describe-snapshots --owner-ids self --query 'Snapshots[].[SnapshotId,State,StartTime]'

# View backup vault metrics
aws backup describe-backup-vault --backup-vault-name primary-backup-vault-us-east-1
```

### GCP Monitoring
```bash
# Check Cloud SQL backups
gcloud sql backups list --instance=production-cloudsql-instance

# View GKE backup status
gcloud container backup-restore backups list --project=PROJECT_ID

# Monitor GCS replication
gsutil stat gs://production-data-backup/
```

### Azure Monitoring
```bash
# Check backup vault status
az backup vault list --resource-group production-rg

# View backup jobs
az backup job list --resource-group production-rg --vault-name prod-backup-vault-eastus

# Monitor SQL backups
az sql db backup short-term-retention-policy show \
  --resource-group production-rg \
  --server prod-sql-primary \
  --database productiondb
```

---

## 🧪 Testing & Validation

### Monthly DR Drill Checklist

```markdown
## First Sunday of Each Month - 02:00 UTC

### Pre-Test (30 min before)
- [ ] Notify all stakeholders
- [ ] Confirm backup integrity
- [ ] Document current metrics

### Test Execution (2 hours)
- [ ] Verify backup accessibility
- [ ] Test restore to non-production
- [ ] Validate data integrity (checksums)
- [ ] Confirm RTO targets (within ±10%)
- [ ] Confirm RPO compliance

### Post-Test (1 hour)
- [ ] Document issues found
- [ ] Create remediation tickets
- [ ] Update runbooks if needed
- [ ] Send results report to stakeholders
```

### Health Check Script
```bash
# Create health check script
cat > /home/user/yawl/backup-recovery/health-check.sh << 'EOF'
#!/bin/bash

echo "=== Backup Health Check ==="
echo "Last AWS backup:" && aws rds describe-db-backups --max-results 1 --query 'DBBackups[0].CreateTime'
echo "Last GCP backup:" && gcloud sql backups list --instance=prod --limit=1
echo "Last Azure backup:" && az backup job list --resource-group prod-rg --limit=1

echo "=== Replication Status ==="
echo "AWS S3 replication:" && aws s3api get-bucket-replication --bucket prod-data
echo "GCP GCS replication:" && gsutil logging get gs://prod-data-backup/
echo "Azure storage replication:" && az storage account show --name prodstorage --query replicationSettings
EOF

chmod +x /home/user/yawl/backup-recovery/health-check.sh
./health-check.sh
```

---

## 🔐 Security Best Practices

1. **Encryption**
   - Use customer-managed keys (CMK) for all backups
   - Enable key rotation (30-90 days)
   - Separate keys per cloud provider

2. **Access Control**
   - Use IAM roles with least privilege
   - Require MFA for vault access
   - Audit all backup access logs

3. **Backup Isolation**
   - Store backups in separate accounts
   - Use different storage classes (cold/archive)
   - Test restore-only permissions

4. **Ransomware Protection**
   - Enable immutable backups (WORM)
   - Air-gap critical backups
   - Implement point-in-time recovery
   - Version all backups

---

## 📝 Runbook Templates

### AWS RDS Failover
```bash
# Step 1: Confirm primary region failure
aws ec2 describe-regions --region-names us-east-1

# Step 2: Promote read replica
aws rds promote-read-replica \
  --db-instance-identifier production-db-replica-us-west-2

# Step 3: Update application endpoints
# Edit configs to point to production-db-replica-us-west-2.xxxxx.us-west-2.rds.amazonaws.com

# Step 4: Verify connectivity
mysql -h production-db-replica-us-west-2.xxxxx.us-west-2.rds.amazonaws.com -u admin -p
```

### GCP Cluster Recovery
```bash
# Step 1: List available backups
gcloud container backup-restore backups list

# Step 2: Create restore plan
gcloud container backup-restore restores create recovery-restore \
  --backup=PROJECT_ID/locations/LOCATION/backupPlans/PLAN_ID/backups/BACKUP_ID

# Step 3: Monitor restore progress
gcloud container backup-restore restores describe recovery-restore

# Step 4: Update DNS/Load balancer
gcloud compute backend-services update service --global --instance-group=new-ig
```

### Azure VM Recovery
```bash
# Step 1: List available snapshots
az snapshot list --resource-group production-rg

# Step 2: Restore from snapshot
az disk create --resource-group production-rg \
  --name restored-disk \
  --source /subscriptions/.../snapshots/snapshot-id

# Step 3: Create new VM from disk
az vm create --resource-group production-rg \
  --name restored-vm \
  --attach-os-disk restored-disk
```

---

## 📞 Support & Contacts

| Role | Contact | Availability |
|------|---------|--------------|
| Backup Administrator | backup-admin@example.com | 24/7 On-Call |
| AWS Specialist | aws-support@example.com | 24/7 On-Call |
| GCP Specialist | gcp-support@example.com | 24/7 On-Call |
| Azure Specialist | azure-support@example.com | 24/7 On-Call |
| Incident Commander | incident-commander@example.com | 24/7 On-Call |

---

## 📚 References

- [AWS Disaster Recovery](https://docs.aws.amazon.com/whitepapers/latest/disaster-recovery-workloads-on-aws/)
- [GCP Backup & Recovery](https://cloud.google.com/architecture/disaster-recovery-architecture-gcp)
- [Azure Business Continuity](https://docs.microsoft.com/en-us/azure/cloud-adoption-framework/ready/landing-zone/design-area/resource-org-management-platform-automation)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [GCP Config Connector](https://cloud.google.com/config-connector/docs)
- [Azure ARM Templates](https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/)

---

## 📄 Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-14 | Initial release with AWS, GCP, Azure support |

---

**Last Updated**: 2026-02-14
**Maintainer**: DevOps Team
**Status**: Production Ready
