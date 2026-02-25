# YAWL Deployment Guide

**Version:** 6.0.0
**Last Updated:** 2026-02-14

---

## 1. Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on any supported cloud platform. Follow the appropriate cloud-specific section after completing the common setup.

### 1.1 Deployment Options

| Option | Description | Time | Complexity |
|--------|-------------|------|------------|
| **Marketplace** | One-click deployment from cloud marketplace | 15 min | Low |
| **Helm Chart** | Kubernetes deployment via Helm | 30 min | Medium |
| **Terraform** | Full infrastructure + application | 45 min | Medium-High |
| **Manual** | Step-by-step manual setup | 2+ hours | High |

### 1.2 Supported Platforms

| Platform | Marketplace | Helm | Terraform |
|----------|-------------|------|-----------|
| AWS | Yes | Yes | Yes |
| Azure | Yes | Yes | Yes |
| GCP | Yes | Yes | Yes |
| Oracle Cloud | Planned | Yes | Yes |
| IBM Cloud | Planned | Yes | Planned |
| Teradata | N/A | Yes | N/A |

---

## 2. Prerequisites Verification

Before starting, verify all prerequisites are met:

```bash
# Verify Kubernetes cluster access
kubectl cluster-info

# Verify Helm installation
helm version

# Verify required tools
which kubectl helm terraform docker git

# Verify cloud CLI authentication
aws sts get-caller-identity      # AWS
az account show                   # Azure
gcloud auth list                  # GCP
```

---

## 3. Quick Start: Helm Deployment

The fastest way to deploy YAWL on any Kubernetes cluster.

### 3.1 Add YAWL Helm Repository

```bash
helm repo add yawl https://helm.yawl.io
helm repo update
```

### 3.2 Create Namespace

```bash
kubectl create namespace yawl
```

### 3.3 Create Secrets

```bash
# Database credentials
kubectl create secret generic yawl-db-credentials \
  --from-literal=host=<DATABASE_HOST> \
  --from-literal=port=5432 \
  --from-literal=username=yawl_admin \
  --from-literal=password=<SECURE_PASSWORD> \
  --namespace yawl

# Redis password
kubectl create secret generic yawl-redis-credentials \
  --from-literal=password=<REDIS_PASSWORD> \
  --namespace yawl

# Admin credentials
kubectl create secret generic yawl-admin-credentials \
  --from-literal=username=admin \
  --from-literal=password=<ADMIN_PASSWORD> \
  --namespace yawl
```

### 3.4 Create values.yaml

```yaml
# values.yaml
global:
  environment: production
  imageRegistry: your-registry.io/yawl
  imageTag: "5.2.0"

engine:
  replicaCount: 2
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 4Gi

resourceService:
  replicaCount: 2
  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 4Gi

workletService:
  replicaCount: 1
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 2Gi

monitorService:
  enabled: true
  replicaCount: 1

schedulingService:
  enabled: true
  replicaCount: 1

balancer:
  enabled: true
  replicaCount: 1

ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: yawl.yourdomain.com
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: yawl-tls
      hosts:
        - yawl.yourdomain.com

postgresql:
  enabled: false  # Using external database

redis:
  enabled: false  # Using external Redis

externalDatabase:
  host: <DATABASE_HOST>
  port: 5432
  database: yawl
  existingSecret: yawl-db-credentials

externalRedis:
  host: <REDIS_HOST>
  port: 6379
  existingSecret: yawl-redis-credentials
```

### 3.5 Deploy YAWL

```bash
helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl \
  --values values.yaml \
  --timeout 10m
```

### 3.6 Verify Deployment

```bash
# Check pod status
kubectl get pods -n yawl

# Check services
kubectl get services -n yawl

# Check ingress
kubectl get ingress -n yawl

# Check logs
kubectl logs -f deployment/yawl-engine -n yawl
```

---

## 4. AWS Deployment

### 4.1 Using AWS Marketplace

1. Navigate to [AWS Marketplace](https://aws.amazon.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Subscribe"
4. Configure deployment settings:
   - Region
   - VPC
   - Instance types
   - Database settings
5. Click "Launch"

### 4.2 Using Terraform

```bash
# Clone YAWL repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl/terraform/aws

# Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your settings

# Initialize Terraform
terraform init

# Plan deployment
terraform plan

# Apply configuration
terraform apply
```

#### terraform.tfvars Example

```hcl
# AWS Configuration
region = "us-east-1"
profile = "default"

# Network Configuration
vpc_cidr = "10.0.0.0/16"
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

# EKS Configuration
cluster_name = "yawl-cluster"
cluster_version = "1.29"
node_instance_type = "m6i.xlarge"
node_count = 3

# RDS Configuration
db_instance_class = "db.r6g.xlarge"
db_allocated_storage = 500
db_username = "yawl_admin"
db_password = "SecurePassword123!"

# ElastiCache Configuration
redis_node_type = "cache.r6g.xlarge"
redis_num_cache_nodes = 3

# YAWL Configuration
yawl_version = "5.2.0"
yawl_domain = "yawl.yourdomain.com"
```

### 4.3 AWS-Specific Configuration

#### IAM Roles

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:yawl/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "kms:Decrypt"
      ],
      "Resource": "*"
    }
  ]
}
```

#### RDS Configuration

```bash
# Create RDS subnet group
aws rds create-db-subnet-group \
  --db-subnet-group-name yawl-db-subnet \
  --db-subnet-group-description "YAWL Database Subnet Group" \
  --subnet-ids subnet-xxx subnet-yyy subnet-zzz

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier yawl-postgres \
  --db-instance-class db.r6g.xlarge \
  --engine postgres \
  --engine-version 15.4 \
  --master-username yawl_admin \
  --master-user-password SecurePassword123 \
  --allocated-storage 500 \
  --storage-encrypted \
  --db-subnet-group-name yawl-db-subnet \
  --vpc-security-group-ids sg-xxx \
  --multi-az \
  --backup-retention-period 35
```

#### ElastiCache Configuration

```bash
# Create Redis subnet group
aws elasticache create-cache-subnet-group \
  --cache-subnet-group-name yawl-redis-subnet \
  --cache-subnet-group-description "YAWL Redis Subnet Group" \
  --subnet-ids subnet-xxx subnet-yyy subnet-zzz

# Create Redis replication group
aws elasticache create-replication-group \
  --replication-group-id yawl-redis \
  --replication-group-description "YAWL Redis Cluster" \
  --cache-node-type cache.r6g.xlarge \
  --engine redis \
  --engine-version 7.0 \
  --num-cache-clusters 3 \
  --cache-subnet-group-name yawl-redis-subnet \
  --security-group-ids sg-xxx \
  --at-rest-encryption-enabled \
  --transit-encryption-enabled \
  --auth-token YourRedisAuthToken123
```

---

## 5. Azure Deployment

### 5.1 Using Azure Marketplace

1. Navigate to [Azure Marketplace](https://azuremarketplace.microsoft.com)
2. Search for "YAWL Workflow Engine"
3. Click "Get it now"
4. Configure deployment:
   - Resource group
   - Region
   - AKS cluster settings
   - Azure Database settings
5. Click "Create"

### 5.2 Using Terraform

```bash
# Clone and navigate
cd yawl/terraform/azure

# Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# Initialize and apply
terraform init
terraform plan
terraform apply
```

#### terraform.tfvars Example

```hcl
# Azure Configuration
location = "eastus"
resource_group_name = "yawl-rg"

# AKS Configuration
aks_cluster_name = "yawl-aks"
aks_dns_prefix = "yawl"
aks_node_count = 3
aks_node_size = "Standard_D4s_v5"

# Azure Database Configuration
postgres_server_name = "yawl-postgres"
postgres_sku_name = "GP_Standard_D4s_v3"
postgres_storage_mb = 512000
postgres_admin_login = "yawl_admin"
postgres_admin_password = "SecurePassword123!"

# Azure Redis Configuration
redis_cache_name = "yawl-redis"
redis_sku = "Premium"
redis_family = "P"
redis_capacity = 2

# YAWL Configuration
yawl_version = "5.2.0"
domain_name = "yawl.yourdomain.com"
```

### 5.3 Azure-Specific Configuration

#### Managed Identity

```bash
# Create managed identity
az identity create \
  --name yawl-identity \
  --resource-group yawl-rg

# Assign role to identity
az role assignment create \
  --assignee <PRINCIPAL_ID> \
  --role "Key Vault Secrets User" \
  --scope /subscriptions/<SUB_ID>/resourceGroups/yawl-rg
```

#### Azure Database for PostgreSQL

```bash
# Create PostgreSQL server
az postgres flexible-server create \
  --name yawl-postgres \
  --resource-group yawl-rg \
  --location eastus \
  --admin-user yawl_admin \
  --admin-password SecurePassword123 \
  --sku-name Standard_D4s_v3 \
  --storage-size 512 \
  --version 15 \
  --high-availability ZoneRedundant
```

#### Azure Cache for Redis

```bash
# Create Redis cache
az redis create \
  --name yawl-redis \
  --resource-group yawl-rg \
  --location eastus \
  --sku Premium \
  --vm-size p2 \
  --enable-non-ssl-port false \
  --minimum-tls-version 1.2
```

---

## 6. GCP Deployment

### 6.1 Using GCP Marketplace

1. Navigate to [Google Cloud Marketplace](https://console.cloud.google.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Launch"
4. Configure deployment:
   - Project
   - Region
   - GKE cluster settings
   - Cloud SQL settings
5. Click "Deploy"

### 6.2 Using Terraform

```bash
# Clone and navigate
cd yawl/terraform/gcp

# Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# Initialize and apply
terraform init
terraform plan
terraform apply
```

#### terraform.tfvars Example

```hcl
# GCP Configuration
project_id = "yawl-project"
region = "us-central1"

# GKE Configuration
cluster_name = "yawl-gke"
machine_type = "n2-standard-4"
node_count = 3
enable_autopilot = true

# Cloud SQL Configuration
sql_instance_name = "yawl-postgres"
sql_tier = "db-custom-4-16384"
sql_disk_size = 500
sql_user = "yawl_admin"
sql_password = "SecurePassword123!"

# Memorystore Configuration
redis_name = "yawl-redis"
redis_tier = "STANDARD_HA"
redis_memory_size_gb = 8

# YAWL Configuration
yawl_version = "5.2.0"
domain_name = "yawl.yourdomain.com"
```

### 6.3 GCP-Specific Configuration

#### Workload Identity

```bash
# Create service account
gcloud iam service-accounts create yawl-sa \
  --display-name "YAWL Service Account"

# Enable Workload Identity
gcloud container clusters update yawl-gke \
  --workload-pool=yawl-project.svc.id.goog

# Bind Kubernetes service account
gcloud iam service-accounts add-iam-policy-binding \
  yawl-sa@yawl-project.iam.gserviceaccount.com \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:yawl-project.svc.id.goog[yawl/yawl-engine]"
```

#### Cloud SQL

```bash
# Create Cloud SQL instance
gcloud sql instances create yawl-postgres \
  --database-version=POSTGRES_15 \
  --tier=db-custom-4-16384 \
  --region=us-central1 \
  --storage-size=500GB \
  --storage-type=SSD \
  --availability-type=REGIONAL \
  --backup-start-time=02:00

# Create database
gcloud sql databases create yawl --instance=yawl-postgres

# Create user
gcloud sql users create yawl_admin \
  --instance=yawl-postgres \
  --password=SecurePassword123
```

#### Memorystore Redis

```bash
# Create Redis instance
gcloud redis instances create yawl-redis \
  --size=8 \
  --region=us-central1 \
  --tier=STANDARD_HA \
  --redis-version=redis_7_0
```

---

## 7. Oracle Cloud Deployment

### 7.1 Using Terraform

```bash
# Clone and navigate
cd yawl/terraform/oracle

# Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# Initialize and apply
terraform init
terraform plan
terraform apply
```

#### terraform.tfvars Example

```hcl
# OCI Configuration
tenancy_ocid = "ocid1.tenancy..."
compartment_ocid = "ocid1.compartment..."
region = "us-ashburn-1"

# OKE Configuration
cluster_name = "yawl-oke"
node_shape = "VM.Standard.E4.Flex"
node_count = 3

# ATP Configuration
atp_display_name = "yawl-atp"
atp_cpu_core_count = 4
atp_data_storage_size_in_tbs = 1
atp_admin_password = "SecurePassword123!"

# YAWL Configuration
yawl_version = "5.2.0"
```

### 7.2 OCI-Specific Configuration

#### Autonomous Transaction Processing

```bash
# Create ATP instance
oci db autonomous-database create \
  --compartment-id $COMPARTMENT_ID \
  --display-name yawl-atp \
  --db-workload OLTP \
  --cpu-core-count 4 \
  --data-storage-size-in-tbs 1 \
  --admin-password SecurePassword123 \
  --is-auto-scaling-enabled true
```

---

## 8. Post-Deployment Configuration

### 8.1 Initialize Database

```bash
# Port-forward to engine
kubectl port-forward svc/yawl-engine 8080:8080 -n yawl

# Run initialization
curl -X POST http://localhost:8080/ia/api/setup \
  -H "Content-Type: application/json" \
  -d '{"adminUser": "admin", "adminPassword": "AdminPassword123"}'
```

### 8.2 Configure SSL

```bash
# Install cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml

# Create ClusterIssuer
cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@yourdomain.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
EOF
```

### 8.3 Configure Monitoring

```bash
# Install Prometheus stack
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace

# Import YAWL dashboards
kubectl apply -f https://raw.githubusercontent.com/yawlfoundation/yawl/main/monitoring/dashboards/
```

### 8.4 Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n yawl

# Check services are accessible
kubectl get ingress -n yawl

# Test API endpoint
curl -k https://yawl.yourdomain.com/ib/api/health

# Check logs
kubectl logs -f -l app=yawl-engine -n yawl
```

---

## 9. Upgrade Procedure

### 9.1 Backup Before Upgrade

```bash
# Backup database
kubectl exec -it yawl-postgres-0 -n yawl -- \
  pg_dump -U yawl_admin yawl > yawl_backup_$(date +%Y%m%d).sql

# Backup secrets
kubectl get secrets -n yawl -o yaml > secrets_backup.yaml
```

### 9.2 Upgrade Helm Chart

```bash
# Update repository
helm repo update

# View available versions
helm search repo yawl --versions

# Upgrade to new version
helm upgrade yawl yawl/yawl-stack \
  --namespace yawl \
  --values values.yaml \
  --version 5.3.0
```

### 9.3 Verify Upgrade

```bash
# Check rollout status
kubectl rollout status deployment/yawl-engine -n yawl

# Verify version
curl https://yawl.yourdomain.com/ib/api/version
```

---

## 10. Troubleshooting

### 10.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| Image Pull Failure | `ImagePullBackOff` | Check image registry credentials |
| Database Connection | `Connection refused` | Verify secrets and network policies |
| Redis Connection | `NOAUTH` | Check Redis password |
| TLS Errors | `certificate invalid` | Verify cert-manager and DNS |

### 10.2 Diagnostic Commands

```bash
# Describe pod for events
kubectl describe pod <POD_NAME> -n yawl

# Check pod logs
kubectl logs <POD_NAME> -n yawl --previous

# Execute into pod
kubectl exec -it <POD_NAME> -n yawl -- /bin/sh

# Check network connectivity
kubectl run tmp-shell --rm -i --tty --image nicolaka/netshoot -- /bin/bash

# Port-forward for local debugging
kubectl port-forward svc/yawl-engine 8080:8080 -n yawl
```

### 10.3 Health Checks

```bash
# Engine health
curl http://yawl-engine:8080/health

# Database connectivity
kubectl exec -it yawl-engine-xxx -n yawl -- \
  pg_isready -h $YAWL_DB_HOST -p 5432

# Redis connectivity
kubectl exec -it yawl-engine-xxx -n yawl -- \
  redis-cli -h $YAWL_REDIS_HOST ping
```

---

## 11. Rollback Procedure

### 11.1 Helm Rollback

```bash
# View history
helm history yawl -n yawl

# Rollback to previous version
helm rollback yawl -n yawl

# Rollback to specific revision
helm rollback yawl 2 -n yawl
```

### 11.2 Database Restore

```bash
# Restore from backup
kubectl exec -i yawl-postgres-0 -n yawl -- \
  psql -U yawl_admin yawl < yawl_backup_20260214.sql
```

---

## 12. Next Steps

After successful deployment:

1. Review [Security Overview](../security/security-overview.md)
2. Configure [Operations](../operations/scaling-guide.md)
3. Read the [User Guide](../user-guide/getting-started.md)
4. Review [FAQ](../user-guide/faq.md)
