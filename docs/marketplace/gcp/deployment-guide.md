# GCP Deployment Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on Google Cloud Platform using GCP Marketplace, Google Kubernetes Engine (GKE), and Terraform.

---

## 2. Prerequisites

### 2.1 Required Tools

```bash
# Verify gcloud CLI
gcloud version

# Verify kubectl
kubectl version --client

# Verify Helm
helm version

# Verify Terraform (optional)
terraform version
```

### 2.2 API Enablement

```bash
# Enable required APIs
gcloud services enable \
  container.googleapis.com \
  sqladmin.googleapis.com \
  redis.googleapis.com \
  secretmanager.googleapis.com \
  compute.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  servicenetworking.googleapis.com
```

### 2.3 IAM Permissions

Ensure your account has the following roles:
- `roles/container.admin`
- `roles/cloudsql.admin`
- `roles/redis.admin`
- `roles/secretmanager.admin`
- `roles/iam.serviceAccountAdmin`

---

## 3. Quick Start: GCP Marketplace

### 3.1 Access Marketplace

1. Navigate to [Google Cloud Console](https://console.cloud.google.com)
2. Go to **Marketplace** > **Browse solutions**
3. Search for "YAWL Workflow Engine"
4. Click on the YAWL listing

### 3.2 Configure Deployment

```yaml
# Deployment configuration options
Deployment Name: yawl-production
Zone: us-central1-a
Machine Type: n2-standard-4
Node Count: 3

Database:
  Instance Type: db-custom-4-16384
  Storage: 500 GB
  High Availability: Enabled

Redis:
  Memory: 8 GB
  Tier: Standard HA

Network:
  VPC: Default (or custom)
  Enable Private Google Access: Yes
```

### 3.3 Deploy

1. Review configuration and pricing estimate
2. Click **Deploy**
3. Wait for deployment to complete (typically 15-20 minutes)
4. Access YAWL via the provided URL

---

## 4. Manual Deployment: GKE

### 4.1 Create VPC Network

```bash
# Create VPC
gcloud compute networks create yawl-vpc \
  --subnet-mode=custom \
  --bgp-routing-mode=regional

# Create subnet for GKE
gcloud compute networks subnets create yawl-gke-subnet \
  --network=yawl-vpc \
  --region=us-central1 \
  --range=10.0.0.0/16 \
  --secondary-range=pods=10.4.0.0/14,services=10.0.32.0/20 \
  --enable-private-ip-google-access

# Create subnet for Cloud SQL
gcloud compute networks subnets create yawl-db-subnet \
  --network=yawl-vpc \
  --region=us-central1 \
  --range=10.1.0.0/24

# Create firewall rules
gcloud compute firewall-rules create yawl-allow-internal \
  --network=yawl-vpc \
  --allow=tcp,udp,icmp \
  --source-ranges=10.0.0.0/8

gcloud compute firewall-rules create yawl-allow-health-checks \
  --network=yawl-vpc \
  --allow=tcp:80,tcp:443,tcp:8080 \
  --source-ranges=130.211.0.0/22,35.191.0.0/16
```

### 4.2 Create GKE Cluster

```bash
# Create GKE Autopilot cluster (recommended)
gcloud container clusters create-auto yawl-cluster \
  --region=us-central1 \
  --network=yawl-vpc \
  --subnetwork=yawl-gke-subnet

# OR create GKE Standard cluster
gcloud container clusters create yawl-cluster \
  --region=us-central1 \
  --network=yawl-vpc \
  --subnetwork=yawl-gke-subnet \
  --machine-type=n2-standard-4 \
  --num-nodes=1 \
  --enable-autoscaling \
  --min-nodes=1 \
  --max-nodes=5 \
  --enable-ip-alias \
  --cluster-secondary-range-name=pods \
  --services-secondary-range-name=services \
  --workload-pool=PROJECT_ID.svc.id.goog

# Get credentials
gcloud container clusters get-credentials yawl-cluster \
  --region=us-central1
```

### 4.3 Create Cloud SQL Instance

```bash
# Create private connection
gcloud compute addresses create google-managed-services-range \
  --global \
  --purpose=VPC_PEERING \
  --prefix-length=16 \
  --network=yawl-vpc

gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-range \
  --network=yawl-vpc

# Create Cloud SQL instance
gcloud sql instances create yawl-postgres \
  --database-version=POSTGRES_15 \
  --tier=db-custom-4-16384 \
  --region=us-central1 \
  --storage-size=500GB \
  --storage-type=SSD \
  --availability-type=REGIONAL \
  --network=yawl-vpc \
  --no-assign-ip \
  --database-flags=max_connections=500

# Create database
gcloud sql databases create yawl --instance=yawl-postgres

# Create user
gcloud sql users create yawl_admin \
  --instance=yawl-postgres \
  --password=SecurePassword123
```

### 4.4 Create Memorystore Redis

```bash
# Create Redis instance
gcloud redis instances create yawl-redis \
  --size=8 \
  --region=us-central1 \
  --tier=STANDARD_HA \
  --redis-version=redis_7_0 \
  --network=yawl-vpc \
  --connect-mode=PRIVATE_SERVICE_ACCESS \
  --enable-auth

# Get Redis host
REDIS_HOST=$(gcloud redis instances describe yawl-redis \
  --region=us-central1 \
  --format='value(host)')

# Get Redis auth string
REDIS_AUTH=$(gcloud redis instances describe yawl-redis \
  --region=us-central1 \
  --format='value(authString)')
```

### 4.5 Configure Secrets

```bash
# Create secrets in Secret Manager
echo -n "yawl_admin" | \
  gcloud secrets create yawl-db-user --data-file=-

echo -n "SecurePassword123" | \
  gcloud secrets create yawl-db-password --data-file==

echo -n "$REDIS_AUTH" | \
  gcloud secrets create yawl-redis-password --data-file==
```

### 4.6 Deploy YAWL

```bash
# Create namespace
kubectl create namespace yawl

# Add YAWL Helm repository
helm repo add yawl https://helm.yawl.io
helm repo update

# Create values file
cat > values-gcp.yaml <<EOF
global:
  environment: production
  imageRegistry: gcr.io/yawl-project
  imageTag: "5.2.0"

engine:
  replicaCount: 2
  serviceAccount:
    create: true
    annotations:
      iam.gke.io/gcp-service-account: yawl-engine-sa@PROJECT_ID.iam.gserviceaccount.com
  extraEnv:
    - name: YAWL_DB_HOST
      value: "/cloudsql/PROJECT_ID:us-central1:yawl-postgres"
    - name: YAWL_REDIS_HOST
      value: "$REDIS_HOST"

  sidecars:
    - name: cloud-sql-proxy
      image: gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.1.0
      args:
        - "--private-ip"
        - "--port=5432"
        - "PROJECT_ID:us-central1:yawl-postgres"

resourceService:
  replicaCount: 2
  serviceAccount:
    create: true
    annotations:
      iam.gke.io/gcp-service-account: yawl-resource-sa@PROJECT_ID.iam.gserviceaccount.com

workletService:
  replicaCount: 1

ingress:
  enabled: true
  className: gce
  annotations:
    kubernetes.io/ingress.global-static-ip-name: "yawl-ip"
    networking.gke.io/managed-certificates: "yawl-cert"
  hosts:
    - host: yawl.yourdomain.com
      paths:
        - path: /*
          pathType: ImplementationSpecific

externalDatabase:
  host: "127.0.0.1"  # Via Cloud SQL Proxy
  port: 5432
  database: yawl
  existingSecret: yawl-db-credentials

externalRedis:
  host: "$REDIS_HOST"
  port: 6379
  existingSecret: yawl-redis-credentials
EOF

# Deploy
helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl \
  --values values-gcp.yaml \
  --timeout 15m
```

---

## 5. Terraform Deployment

### 5.1 Clone and Configure

```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl/terraform/gcp

# Copy example variables
cp terraform.tfvars.example terraform.tfvars
```

### 5.2 Configure Variables

```hcl
# terraform.tfvars
project_id   = "yawl-project"
region       = "us-central1"
environment  = "production"

# Network
vpc_name     = "yawl-vpc"
vpc_cidr     = "10.0.0.0/16"

# GKE
gke_name              = "yawl-cluster"
gke_version           = "1.29"
gke_node_count        = 3
gke_machine_type      = "n2-standard-4"
gke_enable_autopilot  = true

# Cloud SQL
sql_name              = "yawl-postgres"
sql_tier              = "db-custom-4-16384"
sql_disk_size         = 500
sql_version           = "POSTGRES_15"
sql_ha_enabled        = true
sql_user              = "yawl_admin"
sql_password          = "SecurePassword123!"

# Memorystore
redis_name            = "yawl-redis"
redis_memory_size     = 8
redis_tier            = "STANDARD_HA"

# YAWL
yawl_version          = "5.2.0"
yawl_domain           = "yawl.yourdomain.com"
yawl_admin_user       = "admin"
yawl_admin_password   = "AdminPassword123!"
```

### 5.3 Deploy

```bash
# Initialize Terraform
terraform init

# Plan deployment
terraform plan -out=tfplan

# Apply
terraform apply tfplan

# Get outputs
terraform output yawl_url
```

---

## 6. Post-Deployment

### 6.1 Configure DNS

```bash
# Get ingress IP
kubectl get ingress -n yawl

# Create DNS record (example using Cloud DNS)
gcloud dns record-sets create yawl.yourdomain.com \
  --zone=yawl-zone \
  --type=A \
  --ttl=300 \
  --rrdatas=INGRESS_IP
```

### 6.2 Configure SSL Certificate

```bash
# Create managed certificate
gcloud compute ssl-certificates create yawl-cert \
  --domains=yawl.yourdomain.com \
  --global
```

### 6.3 Verify Deployment

```bash
# Check pods
kubectl get pods -n yawl

# Check services
kubectl get services -n yawl

# Check logs
kubectl logs -f -l app.kubernetes.io/name=yawl-engine -n yawl

# Test endpoint
curl -k https://yawl.yourdomain.com/ib/api/health
```

---

## 7. Scaling

### 7.1 Horizontal Pod Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
  namespace: yawl
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

### 7.2 Database Scaling

```bash
# Increase Cloud SQL resources
gcloud sql instances patch yawl-postgres \
  --tier=db-custom-8-32768

# Add read replica
gcloud sql instances create yawl-postgres-replica \
  --master-instance-name=yawl-postgres \
  --region=us-central1
```

---

## 8. Troubleshooting

### 8.1 Common Issues

| Issue | Command | Resolution |
|-------|---------|------------|
| Pod not starting | `kubectl describe pod <POD> -n yawl` | Check events, resource limits |
| Database connection | `kubectl logs <POD> -n yawl` | Verify Cloud SQL Proxy |
| Redis connection | `kubectl exec -it <POD> -n yawl -- nc -zv $REDIS_HOST 6379` | Check network connectivity |
| IAM permissions | `gcloud auth list` | Verify Workload Identity |

### 8.2 Diagnostic Commands

```bash
# Cloud SQL connectivity
gcloud sql connect yawl-postgres --user=yawl_admin

# Redis connectivity
gcloud redis instances describe yawl-redis --region=us-central1

# GKE cluster info
gcloud container clusters describe yawl-cluster --region=us-central1
```

---

## 9. Cleanup

### 9.1 Delete Deployment

```bash
# Delete Helm release
helm uninstall yawl -n yawl

# Delete namespace
kubectl delete namespace yawl
```

### 9.2 Destroy Infrastructure (Terraform)

```bash
terraform destroy
```

### 9.3 Manual Cleanup

```bash
# Delete GKE cluster
gcloud container clusters delete yawl-cluster --region=us-central1

# Delete Cloud SQL
gcloud sql instances delete yawl-postgres

# Delete Redis
gcloud redis instances delete yawl-redis --region=us-central1

# Delete VPC
gcloud compute networks delete yawl-vpc
```
