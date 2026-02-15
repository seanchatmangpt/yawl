# YAWL on Google Cloud Platform

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

YAWL Workflow Engine is available on Google Cloud Marketplace, providing enterprise workflow automation with seamless GCP integration. This document covers GCP-specific features, pricing, and deployment options.

### 1.1 Key Features on GCP

- **One-Click Deployment**: Deploy directly from GCP Marketplace
- **GKE Integration**: Optimized for Google Kubernetes Engine (Autopilot and Standard)
- **Cloud SQL**: Managed PostgreSQL with high availability
- **Memorystore**: Managed Redis for caching and sessions
- **Cloud Logging**: Native integration with Cloud Logging
- **Cloud Monitoring**: Built-in metrics and dashboards
- **Workload Identity**: Secure service account integration
- **VPC Service Controls**: Enterprise security boundaries

### 1.2 Architecture on GCP

```
                    +-------------------+
                    |  Cloud DNS        |
                    +--------+----------+
                             |
                    +--------v----------+
                    |  Cloud Load       |
                    |  Balancing        |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v------------+ +----v------------+ +----v------------+
    | GKE Autopilot   | | GKE Autopilot   | | GKE Autopilot   |
    | Engine Pod 1    | | Engine Pod 2    | | Engine Pod N    |
    +-----------------+ +-----------------+ +-----------------+
         |                   |                   |
         +-------------------+-------------------+
                             |
              +--------------v--------------+
              |     Cloud SQL (PostgreSQL)  |
              |     Regional HA + Read      |
              |     Replicas               |
              +-------------+---------------+
                            |
              +-------------v---------------+
              |     Memorystore (Redis)     |
              |     Standard HA             |
              +-----------------------------+
```

---

## 2. GCP Marketplace Listing

### 2.1 Product Information

| Field | Value |
|-------|-------|
| **Product Name** | YAWL Workflow Engine |
| **Short Description** | Enterprise workflow automation with Petri Net formal foundation |
| **Categories** | Developer Tools, Business Applications |
| **Pricing Model** | Pay-as-you-go + Bring Your Own License (BYOL) |
| **Support** | Community + Commercial options |

### 2.2 Pricing Tiers

| Tier | Resources | Estimated Monthly Cost | Use Case |
|------|-----------|------------------------|----------|
| **Starter** | 2 vCPU, 4 GB RAM | $150-200 | Development, Testing |
| **Standard** | 4 vCPU, 16 GB RAM | $400-600 | Production workloads |
| **Enterprise** | 8+ vCPU, 32+ GB RAM | $800-1500+ | High-volume production |

*Note: Costs include compute, database, and caching infrastructure*

### 2.3 Free Trial

- 14-day free trial available
- Full feature access during trial
- No credit card required to start trial
- Automatic conversion to paid at trial end

---

## 3. GCP Services Integration

### 3.1 Required GCP Services

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **Compute Engine** | GKE nodes | Enable API |
| **Kubernetes Engine** | Container orchestration | GKE 1.28+ |
| **Cloud SQL** | Database | PostgreSQL 15+ |
| **Memorystore** | Redis cache | Standard HA tier |
| **Cloud Storage** | Backups, logs | Standard class |
| **Secret Manager** | Secrets management | Enable API |
| **Cloud Logging** | Log aggregation | Automatic |
| **Cloud Monitoring** | Metrics, alerts | Automatic |

### 3.2 Optional GCP Services

| Service | Purpose | Benefit |
|---------|---------|---------|
| **Cloud Armor** | WAF protection | DDoS protection, WAF rules |
| **Identity Platform** | Authentication | Federated identity |
| **Cloud KMS** | Key management | Customer-managed keys |
| **Cloud Trace** | Distributed tracing | Performance analysis |
| **BigQuery** | Log analytics | Advanced log analysis |
| **Pub/Sub** | Event streaming | Event-driven workflows |

### 3.3 IAM Permissions

Minimum permissions required for deployment:

```yaml
# Custom Role: YAWL Deployer
title: "YAWL Deployer"
description: "Permissions to deploy YAWL Workflow Engine"
includedPermissions:
  # Compute
  - compute.instances.list
  - compute.networks.get
  - compute.subnetworks.get
  # GKE
  - container.clusters.create
  - container.clusters.get
  - container.clusters.update
  - container.pods.list
  - container.pods.create
  # Cloud SQL
  - cloudsql.instances.create
  - cloudsql.instances.get
  - cloudsql.instances.update
  - cloudsql.databases.create
  # Memorystore
  - redis.instances.create
  - redis.instances.get
  # Secret Manager
  - secretmanager.secrets.create
  - secretmanager.versions.add
  # Storage
  - storage.buckets.create
  - storage.objects.create
```

---

## 4. Deployment Methods

### 4.1 GCP Marketplace (Recommended)

1. Navigate to [Google Cloud Marketplace](https://console.cloud.google.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Configure" to start deployment
4. Configure deployment parameters:
   - Zone/Region
   - Machine types
   - Database size
   - Redis configuration
5. Review pricing estimate
6. Click "Deploy"

### 4.2 Google Cloud CLI

```bash
# Install gcloud CLI
brew install google-cloud-sdk

# Authenticate
gcloud auth login

# Set project
gcloud config set project YOUR_PROJECT_ID

# Deploy using Deployment Manager
gcloud deployment-manager deployments create yawl-deployment \
  --config https://storage.googleapis.com/yawl-marketplace/gcp/deployment.yaml
```

### 4.3 Terraform

```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl/terraform/gcp

# Configure
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# Deploy
terraform init
terraform apply
```

---

## 5. GKE Configuration

### 5.1 GKE Autopilot (Recommended)

GKE Autopilot provides fully managed Kubernetes with automatic scaling and security.

```yaml
# GKE Autopilot cluster configuration
apiVersion: container.cnrm.cloud.google.com/v1beta1
kind: ContainerCluster
metadata:
  name: yawl-cluster
spec:
  location: us-central1
  enableAutopilot: true
  networkRef:
    name: yawl-network
  subnetworkRef:
    name: yawl-subnet
  workloadIdentityConfig:
    workloadPool: PROJECT_ID.svc.id.goog
  releaseChannel:
    channel: REGULAR
  maintenancePolicy:
    window:
      dailyMaintenanceWindow:
        startTime: 02:00
```

### 5.2 GKE Standard

For more control over node configuration:

```yaml
# GKE Standard cluster configuration
apiVersion: container.cnrm.cloud.google.com/v1beta1
kind: ContainerCluster
metadata:
  name: yawl-cluster
spec:
  location: us-central1
  nodePools:
    - name: default-pool
      initialNodeCount: 3
      autoscaling:
        minNodeCount: 3
        maxNodeCount: 10
      nodeConfig:
        machineType: n2-standard-4
        diskSizeGb: 100
        diskType: pd-ssd
        oauthScopes:
          - https://www.googleapis.com/auth/cloud-platform
        workloadMetadataConfig:
          mode: GKE_METADATA
```

### 5.3 Workload Identity

Configure Workload Identity for secure service account access:

```bash
# Create GCP service account
gcloud iam service-accounts create yawl-engine-sa \
  --display-name "YAWL Engine Service Account"

# Grant required roles
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member "serviceAccount:yawl-engine-sa@PROJECT_ID.iam.gserviceaccount.com" \
  --role "roles/cloudsql.client"

gcloud projects add-iam-policy-binding PROJECT_ID \
  --member "serviceAccount:yawl-engine-sa@PROJECT_ID.iam.gserviceaccount.com" \
  --role "roles/secretmanager.secretAccessor"

# Create Kubernetes service account binding
kubectl create serviceaccount yawl-engine -n yawl

# Annotate for Workload Identity
kubectl annotate serviceaccount yawl-engine \
  --namespace yawl \
  iam.gke.io/gcp-service-account=yawl-engine-sa@PROJECT_ID.iam.gserviceaccount.com
```

---

## 6. Cloud SQL Configuration

### 6.1 Instance Configuration

```bash
# Create Cloud SQL instance
gcloud sql instances create yawl-postgres \
  --database-version=POSTGRES_15 \
  --tier=db-custom-4-16384 \
  --region=us-central1 \
  --storage-type=SSD \
  --storage-size=500GB \
  --storage-auto-increase \
  --availability-type=REGIONAL \
  --backup-start-time=02:00 \
  --enable-point-in-time-recovery \
  --maintenance-window-day=SUN \
  --maintenance-window-hour=03 \
  --database-flags=max_connections=500
```

### 6.2 Private IP Configuration

For enhanced security, use Private IP:

```bash
# Create VPC peering
gcloud compute addresses create google-managed-services-range \
  --global \
  --purpose=VPC_PEERING \
  --prefix-length=16 \
  --network=yawl-vpc

# Create peering connection
gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-range \
  --network=yawl-vpc

# Create instance with private IP
gcloud sql instances create yawl-postgres \
  --database-version=POSTGRES_15 \
  --tier=db-custom-4-16384 \
  --region=us-central1 \
  --network=yawl-vpc \
  --no-assign-ip
```

### 6.3 Connection Configuration

```yaml
# Cloud SQL Proxy configuration for Kubernetes
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  template:
    spec:
      containers:
        - name: cloud-sql-proxy
          image: gcr.io/cloud-sql-connectors/cloud-sql-proxy:2.1.0
          args:
            - "--private-ip"
            - "--port=5432"
            - "PROJECT_ID:us-central1:yawl-postgres"
          securityContext:
            runAsNonRoot: true
        - name: yawl-engine
          env:
            - name: YAWL_DB_HOST
              value: "127.0.0.1"
            - name: YAWL_DB_PORT
              value: "5432"
```

---

## 7. Memorystore Configuration

### 7.1 Standard HA Instance

```bash
# Create Memorystore Redis instance
gcloud redis instances create yawl-redis \
  --size=8 \
  --region=us-central1 \
  --tier=STANDARD_HA \
  --redis-version=redis_7_0 \
  --connect-mode=PRIVATE_SERVICE_ACCESS \
  --enable-auth \
  --transit-encryption-mode=SERVER_AUTHENTICATION
```

### 7.2 Redis Configuration for YAWL

```yaml
# Redis connection configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-redis-config
data:
  REDIS_HOST: "10.0.0.3"  # Memorystore IP
  REDIS_PORT: "6379"
  REDIS_TLS: "true"
---
apiVersion: v1
kind: Secret
metadata:
  name: yawl-redis-auth
stringData:
  REDIS_PASSWORD: "<AUTH_STRING_FROM_GCLOUD>"
```

---

## 8. Logging and Monitoring

### 8.1 Cloud Logging Integration

YAWL automatically integrates with Cloud Logging via Fluent Bit:

```yaml
# Fluent Bit configuration for Cloud Logging
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
data:
  fluent-bit.conf: |
    [SERVICE]
      Flush         1
      Log_Level     info
      Parsers_File  parsers.conf

    [INPUT]
      Name              tail
      Tag               kube.*
      Path              /var/log/containers/*.log
      Parser            cri
      Mem_Buf_Limit     5MB

    [FILTER]
      Name                kubernetes
      Match               kube.*
      Kube_URL            https://kubernetes.default.svc:443
      Kube_CA_File        /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
      Kube_Token_File     /var/run/secrets/kubernetes.io/serviceaccount/token
      Merge_Log           On
      K8S-Logging.Parser  On
      K8S-Logging.Exclude On

    [OUTPUT]
      Name            stackdriver
      Match           *
      Resource        gke_container
      google_service_credentials /var/run/secrets/google-credentials.json
```

### 8.2 Cloud Monitoring Metrics

YAWL exports custom metrics to Cloud Monitoring:

| Metric | Type | Description |
|--------|------|-------------|
| `yawl/cases/active` | GAUGE | Active workflow cases |
| `yawl/cases/completed` | DELTA | Completed cases per minute |
| `yawl/workitems/pending` | GAUGE | Pending work items |
| `yawl/tasks/duration` | DISTRIBUTION | Task execution duration |
| `yawl/engine/heap_used` | GAUGE | JVM heap usage |

### 8.3 Alerting Policies

```yaml
# Alert policy for high case duration
displayName: "YAWL High Case Duration"
conditions:
  - displayName: "Case duration > 1 hour"
    conditionThreshold:
      filter: 'metric.type="custom.googleapis.com/yawl/cases/duration"'
      aggregations:
        - alignmentPeriod: 300s
          perSeriesAligner: ALIGN_PERCENTILE_99
      comparison: COMPARISON_GT
      thresholdValue: 3600
      duration: 300s
notificationChannels:
  - projects/PROJECT_ID/notificationChannels/CHANNEL_ID
```

---

## 9. Security Configuration

### 9.1 VPC Service Controls

For enterprise security, enable VPC Service Controls:

```yaml
# VPC Service Controls access policy
apiVersion: accesscontextmanager.cnrm.cloud.google.com/v1beta1
kind: AccessPolicy
metadata:
  name: yawl-access-policy
spec:
  parent:
    organizationRef:
      external: "ORGANIZATION_ID"
  title: "YAWL Access Policy"
```

### 9.2 Cloud Armor WAF

```yaml
# Cloud Armor security policy
apiVersion: compute.cnrm.cloud.google.com/v1beta1
kind: ComputeSecurityPolicy
metadata:
  name: yawl-security-policy
spec:
  rule:
    - action: deny(403)
      match:
        expr:
          expression: "evaluatePreconfiguredWaf('xss-v33-stable')"
      priority: 1000
    - action: deny(403)
      match:
        expr:
          expression: "evaluatePreconfiguredWaf('sqli-v33-stable')"
      priority: 1001
    - action: allow
      match:
        versionedExpr: SRC_IPS_V1
        config:
          srcIpRanges:
            - "*"
      priority: 2147483647
```

### 9.3 Secret Manager Integration

```bash
# Create secrets in Secret Manager
echo -n "yawl_admin" | gcloud secrets create yawl-db-user --data-file=-
echo -n "SecurePassword123!" | gcloud secrets create yawl-db-password --data-file=-
echo -n "RedisAuthString" | gcloud secrets create yawl-redis-password --data-file==
```

```yaml
# Kubernetes secret synced from Secret Manager
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-credentials
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcpsm-secret-store
    kind: SecretStore
  target:
    name: yawl-db-credentials
  data:
    - secretKey: username
      remoteRef:
        key: yawl-db-user
    - secretKey: password
      remoteRef:
        key: yawl-db-password
```

---

## 10. Cost Optimization

### 10.1 Committed Use Discounts

For predictable workloads, purchase committed use:

| Commitment Term | Discount |
|-----------------|----------|
| 1 Year | ~20% off |
| 3 Years | ~40% off |

### 10.2 Right-Sizing Recommendations

| Environment | Instance Type | Monthly Cost |
|-------------|---------------|--------------|
| Development | db-custom-2-8192 | ~$100 |
| Staging | db-custom-4-16384 | ~$300 |
| Production | db-custom-8-32768 | ~$700 |

### 10.3 Cost Monitoring

```bash
# Set budget alert
gcloud billing budgets create \
  --billing-account=BILLING_ACCOUNT_ID \
  --display-name="YAWL Budget" \
  --budget-amount=1000USD \
  --threshold-rule=percent=50 \
  --threshold-rule=percent=80 \
  --threshold-rule=percent=100
```

---

## 11. Support and Resources

### 11.1 GCP-Specific Support

- **Documentation**: [YAWL on GCP](https://cloud.google.com/marketplace/docs/yawl)
- **Community**: [YAWL Google Group](https://groups.google.com/g/yawl-users)
- **GitHub**: [yawlfoundation/yawl](https://github.com/yawlfoundation/yawl)

### 11.2 Getting Help

| Issue | Contact |
|-------|---------|
| GCP Infrastructure | Google Cloud Support |
| YAWL Application | YAWL Community / Commercial Support |
| Marketplace Issues | Google Cloud Marketplace Support |

---

## 12. Next Steps

1. [Deploy YAWL on GCP](deployment-guide.md)
2. [Configure Security](../security/security-overview.md)
3. [Set up Monitoring](../operations/scaling-guide.md)
4. [Getting Started Guide](../user-guide/getting-started.md)
