# YAWL Deployment Prerequisites

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This document outlines all prerequisites for deploying YAWL Workflow Engine on any supported cloud platform. Ensure all requirements are met before beginning deployment.

---

## 2. Account Requirements

### 2.1 Cloud Platform Accounts

| Platform | Account Type | Required Access | Notes |
|----------|--------------|-----------------|-------|
| **AWS** | AWS Account | Administrator or PowerUser | Enable billing, create support cases |
| **Azure** | Azure Subscription | Owner or Contributor | Enable Microsoft.ContainerRegistry |
| **GCP** | Google Cloud Project | Owner or Editor | Enable billing, APIs |
| **Oracle** | OCI Tenancy | Administrator | Compartment, policies |
| **IBM** | IBM Cloud Account | Administrator | Resource group, IAM |
| **Teradata** | Vantage Account | Admin | Database, compute clusters |

### 2.2 Required Permissions

Minimum IAM permissions for deployment:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:*",
        "eks:*",
        "rds:*",
        "elasticache:*",
        "ecr:*",
        "secretsmanager:*",
        "iam:*",
        "s3:*",
        "logs:*",
        "cloudwatch:*"
      ],
      "Resource": "*"
    }
  ]
}
```

### 2.3 Service Quotas

Ensure sufficient quotas for production deployment:

| Resource | Minimum | Recommended | Notes |
|----------|---------|-------------|-------|
| vCPUs | 16 | 64 | Request quota increase if needed |
| Database Instances | 2 | 4 | Multi-AZ requires 2 per instance |
| Cache Nodes | 3 | 6 | Redis cluster mode |
| Load Balancers | 2 | 4 | Application + Network LBs |
| EBS Volumes | 500 GB | 2 TB | Database + storage |

---

## 3. Infrastructure Requirements

### 3.1 Kubernetes Cluster

| Requirement | Minimum | Recommended | Notes |
|-------------|---------|-------------|-------|
| **Version** | 1.26+ | 1.29+ | Latest stable |
| **Nodes** | 3 | 5+ | Spread across AZs |
| **Node Size** | 4 vCPU, 16 GB | 8 vCPU, 32 GB | Memory-optimized |
| **Total vCPUs** | 12 | 40+ | |
| **Total Memory** | 48 GB | 160 GB+ | |

#### Cloud-Specific Kubernetes Services

| Cloud | Service | Recommended Instance |
|-------|---------|---------------------|
| AWS | EKS | m6i.xlarge (managed node group) |
| Azure | AKS | Standard_D4s_v5 |
| GCP | GKE | n2-standard-4 (Autopilot recommended) |
| Oracle | OKE | VM.Standard.E4.Flex |
| IBM | ROKS | bx2.4x16 |

### 3.2 Database Requirements

#### PostgreSQL (Recommended)

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Version** | 13+ | 15+ |
| **vCPUs** | 2 | 4+ |
| **Memory** | 4 GB | 16 GB+ |
| **Storage** | 100 GB | 500 GB+ |
| **IOPS** | 1000 | 5000+ |
| **Connections** | 100 | 500+ |

#### MySQL (Alternative)

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Version** | 8.0+ | 8.0+ |
| **vCPUs** | 2 | 4+ |
| **Memory** | 4 GB | 16 GB+ |
| **Storage** | 100 GB | 500 GB+ |

### 3.3 Redis Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Version** | 6.x | 7.x |
| **Memory** | 1 GB | 8 GB+ |
| **Mode** | Sentinel | Cluster |
| **Nodes** | 3 | 6+ (3 master, 3 replica) |
| **TLS** | Optional | Required (production) |

### 3.4 Storage Requirements

| Storage Type | Purpose | Size | Tier |
|--------------|---------|------|------|
| Block | Database | 500 GB | SSD/Premium |
| Block | Redis | 50 GB | SSD |
| Object | Backups | 1 TB | Standard |
| Object | Logs | 500 GB | Archive (after 30d) |

---

## 4. Network Requirements

### 4.1 VPC/VNet Configuration

| Requirement | Specification |
|-------------|---------------|
| **CIDR Block** | /16 or larger |
| **Subnets** | 3+ AZs, public and private |
| **NAT Gateway** | Required for private subnets |
| **DNS Resolution** | Enabled |
| **Flow Logs** | Enabled (production) |

### 4.2 Subnet Planning

| Subnet Type | CIDR | Purpose | AZ Count |
|-------------|------|---------|----------|
| Public | /20 | Load balancers, bastion | 3 |
| Private (App) | /18 | Kubernetes nodes | 3 |
| Private (Data) | /24 | Database, cache | 3 |

### 4.3 Ports and Protocols

#### Inbound (From Internet)

| Port | Protocol | Source | Purpose |
|------|----------|--------|---------|
| 80 | TCP | 0.0.0.0/0 | HTTP (redirect to HTTPS) |
| 443 | TCP | 0.0.0.0/0 | HTTPS (API/UI) |

#### Inbound (Internal)

| Port | Protocol | Source | Purpose |
|------|----------|--------|---------|
| 8080 | TCP | Load Balancer | Application HTTP |
| 5432 | TCP | App Subnet | PostgreSQL |
| 6379 | TCP | App Subnet | Redis |
| 26379 | TCP | App Subnet | Redis Sentinel |

#### Outbound

| Port | Protocol | Destination | Purpose |
|------|----------|-------------|---------|
| 443 | TCP | Container Registry | Image pulls |
| 443 | TCP | Package Repositories | Dependencies |
| 5432 | TCP | Database Subnet | Database access |
| 6379 | TCP | Cache Subnet | Redis access |

### 4.4 DNS Requirements

| Requirement | Specification |
|-------------|---------------|
| **Domain** | Valid domain for production |
| **SSL Certificate** | Valid certificate for domain |
| **DNS Zone** | Managed DNS (Route 53, Azure DNS, Cloud DNS) |

---

## 5. Client Tool Requirements

### 5.1 Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| **kubectl** | 1.28+ | Kubernetes CLI |
| **helm** | 3.12+ | Package manager |
| **terraform** | 1.6+ | Infrastructure as Code |
| **docker** | 24+ | Container runtime |
| **git** | 2.40+ | Version control |
| **openssl** | 3.0+ | Certificate management |

### 5.2 Cloud-Specific CLIs

| Cloud | CLI | Installation |
|-------|-----|--------------|
| AWS | `aws` | `pip install awscli` or `brew install awscli` |
| Azure | `az` | `brew install azure-cli` |
| GCP | `gcloud` | `brew install google-cloud-sdk` |
| Oracle | `oci` | `brew install oci-cli` |
| IBM | `ibmcloud` | `brew install ibm-cloud-cli` |

### 5.3 Verification Commands

```bash
# Verify tool versions
kubectl version --client
helm version
terraform version
docker --version
git --version

# Verify cloud CLI authentication
aws sts get-caller-identity
az account show
gcloud auth list
oci os ns get
ibmcloud target
```

---

## 6. Security Prerequisites

### 6.1 SSL/TLS Certificates

| Certificate Type | Options |
|------------------|---------|
| **Managed** | AWS ACM, Azure Key Vault, GCP Certificate Manager |
| **Custom** | Valid certificate from CA (Let's Encrypt, DigiCert) |
| **Self-Signed** | Development only (not for production) |

### 6.2 Secrets Management

Prepare the following secrets before deployment:

| Secret Name | Content | Format |
|-------------|---------|--------|
| `yawl-db-credentials` | Username, password | JSON |
| `yawl-redis-password` | Redis auth password | String |
| `yawl-engine-admin` | Admin username, password | JSON |
| `yawl-jwt-secret` | JWT signing key | Base64 |

### 6.3 Network Security

| Requirement | Implementation |
|-------------|----------------|
| **TLS 1.2+** | Required for all external traffic |
| **mTLS** | Optional for internal service mesh |
| **WAF** | Enable for production (OWASP Core Rule Set) |
| **DDoS Protection** | Enable cloud-native protection |

---

## 7. Environment Variables

### 7.1 Required Variables

```bash
# Database Configuration
export YAWL_DB_HOST="your-db-host"
export YAWL_DB_PORT="5432"
export YAWL_DB_NAME="yawl"
export YAWL_DB_USER="yawl_admin"
export YAWL_DB_PASSWORD="secure-password"

# Redis Configuration
export YAWL_REDIS_HOST="your-redis-host"
export YAWL_REDIS_PORT="6379"
export YAWL_REDIS_PASSWORD="secure-password"

# Application Configuration
export YAWL_ENV="production"
export YAWL_LOG_LEVEL="INFO"
export YAWL_JWT_SECRET="your-jwt-secret"

# Cloud Provider
export CLOUD_PROVIDER="aws"  # aws, azure, gcp, oracle, ibm
```

### 7.2 Optional Variables

```bash
# Performance Tuning
export YAWL_DB_POOL_SIZE="50"
export YAWL_DB_POOL_MIN="10"
export YAWL_REDIS_POOL_SIZE="20"
export YAWL_JVM_OPTS="-Xms2g -Xmx4g"

# Monitoring
export YAWL_METRICS_ENABLED="true"
export YAWL_TRACING_ENABLED="true"
export YAWL_LOG_FORMAT="json"

# Feature Flags
export YAWL_WORKLETS_ENABLED="true"
export YAWL_SCHEDULING_ENABLED="true"
export YAWL_COST_TRACKING_ENABLED="false"
```

---

## 8. Helm Chart Requirements

### 8.1 Helm Repositories

```bash
# Add required Helm repositories
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
```

### 8.2 Values File Structure

Create `values-production.yaml`:

```yaml
# YAWL Engine Configuration
engine:
  replicaCount: 3
  image:
    repository: your-registry/yawl-engine
    tag: "5.2.0"
    pullPolicy: Always

  resources:
    requests:
      cpu: 500m
      memory: 1Gi
    limits:
      cpu: 2000m
      memory: 4Gi

  env:
    - name: YAWL_DB_HOST
      valueFrom:
        secretKeyRef:
          name: yawl-db-credentials
          key: host
    - name: YAWL_DB_PASSWORD
      valueFrom:
        secretKeyRef:
          name: yawl-db-credentials
          key: password

# Resource Service Configuration
resourceService:
  replicaCount: 2
  image:
    repository: your-registry/yawl-resource-service
    tag: "5.2.0"

# Worklet Service Configuration
workletService:
  replicaCount: 2
  image:
    repository: your-registry/yawl-worklet-service
    tag: "5.2.0"

# Ingress Configuration
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

# PostgreSQL Configuration (if using sub-chart)
postgresql:
  enabled: false  # Use managed database

# Redis Configuration (if using sub-chart)
redis:
  enabled: false  # Use managed Redis
```

---

## 9. Monitoring Prerequisites

### 9.1 Prometheus Stack

```bash
# Install Prometheus Operator
helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set prometheus.prometheusSpec.retention=30d \
  --set alertmanager.alertmanagerSpec.storage.volumeClaimTemplate.spec.resources.requests.storage=50Gi
```

### 9.2 Logging Stack

```bash
# Install Fluent Bit
helm upgrade --install fluent-bit bitnami/fluent-bit \
  --namespace logging --create-namespace
```

### 9.3 Required Dashboards

Import the following Grafana dashboards:
- YAWL Engine Overview (dashboard ID: TBD)
- JVM Metrics (dashboard ID: 4701)
- PostgreSQL Database (dashboard ID: 9628)
- Redis Overview (dashboard ID: 14091)

---

## 10. Backup Prerequisites

### 10.1 Database Backup

| Cloud | Service | Retention |
|-------|---------|-----------|
| AWS | RDS Automated Backups | 35 days |
| Azure | Azure Database Backup | 35 days |
| GCP | Cloud SQL Backup | 365 days |
| Oracle | Autonomous DB Backup | 60 days |
| IBM | Databases Backup | 30 days |

### 10.2 Object Storage for Backups

| Purpose | Storage Class | Retention |
|---------|---------------|-----------|
| Database Backups | Standard | 90 days |
| Configuration Backups | Standard | 365 days |
| Log Archives | Archive/Glacier | 7 years |

---

## 11. Checklist

### Pre-Deployment Checklist

- [ ] Cloud account with required permissions
- [ ] Service quotas verified and increased if needed
- [ ] Kubernetes cluster provisioned (3+ nodes)
- [ ] Managed PostgreSQL database created
- [ ] Redis cluster deployed
- [ ] VPC/VNet configured with required subnets
- [ ] SSL/TLS certificate provisioned
- [ ] DNS zone configured
- [ ] Secrets stored in cloud secret manager
- [ ] Client tools installed and configured
- [ ] Cloud CLI authenticated
- [ ] Helm repositories added
- [ ] values-production.yaml configured
- [ ] Monitoring stack deployed
- [ ] Backup configuration verified

### Production-Ready Checklist

- [ ] Multi-AZ deployment configured
- [ ] High availability enabled for database
- [ ] Redis cluster mode with replicas
- [ ] Load balancer health checks configured
- [ ] Auto-scaling policies defined
- [ ] Log aggregation configured
- [ ] Alerting rules configured
- [ ] Disaster recovery tested
- [ ] Security scan completed
- [ ] Performance baseline established

---

## 12. Next Steps

After completing all prerequisites:

1. Review [Architecture](architecture.md) for deployment design
2. Follow [Deployment Guide](deployment-guide.md) for step-by-step instructions
3. Configure [Security](../security/security-overview.md) for production
4. Set up [Operations](../operations/scaling-guide.md) for ongoing management

---

## 13. Support

If you encounter issues with prerequisites:

- **Documentation**: [YAWL Foundation](https://yawlfoundation.github.io)
- **GitHub Issues**: [YAWL Repository](https://github.com/yawlfoundation/yawl)
- **Community Support**: [YAWL Mailing List](mailto:yawl@list.unsw.edu.au)
