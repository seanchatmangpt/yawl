# YAWL Marketplace Deployment Guide

Complete marketplace-ready configurations for deploying YAWL across multiple cloud platforms and container registries.

## Directory Structure

```
marketplace/
├── gcp-marketplace/           # Google Cloud Platform Marketplace
│   └── metadata.yaml          # GCP application metadata
├── aws-marketplace/           # Amazon Web Services Marketplace
│   └── product.yaml           # CloudFormation template
├── azure-marketplace/         # Microsoft Azure Marketplace
│   └── createUiDefinition.json  # Azure deployment UI
├── docker-hub/               # Docker Hub Container Registry
│   ├── Dockerfile            # Multi-stage Docker image
│   ├── docker-compose.yml    # Complete Docker stack
│   ├── .dockerignore         # Docker build exclusions
│   ├── .env.example          # Environment configuration template
│   └── README.md             # Docker Hub deployment guide
└── helm-repo/                # Kubernetes Helm Charts
    ├── Chart.yaml            # Helm chart metadata
    ├── values.yaml           # Default configuration
    ├── values-gcp.yaml       # GCP-specific values
    ├── values-aws.yaml       # AWS-specific values
    ├── values-azure.yaml     # Azure-specific values
    └── README.md             # Helm deployment guide
```

## Quick Start Guide

### 1. Google Cloud Platform (GCP) Marketplace

**File:** `/home/user/yawl/marketplace/gcp-marketplace/metadata.yaml`

This metadata file defines the YAWL application for GCP Marketplace including:
- Application title, description, and version
- Container images configuration
- Required cluster constraints (Kubernetes 1.24+)
- Configurable properties (replicas, storage class, monitoring)
- Support information and links

**Deployment:**
```bash
# Deploy through GCP Console
# Upload metadata.yaml to GCP Marketplace
# Or use gcloud CLI:
gcloud marketplace operations create \
  --source=/home/user/yawl/marketplace/gcp-marketplace/metadata.yaml
```

### 2. AWS Marketplace

**File:** `/home/user/yawl/marketplace/aws-marketplace/product.yaml`

CloudFormation template providing:
- VPC and subnet configuration
- RDS PostgreSQL database setup with Multi-AZ
- Application Load Balancer with SSL/TLS
- Auto Scaling Group for EC2 instances
- Security groups and networking
- IAM roles and permissions
- CloudWatch monitoring

**Deployment:**
```bash
# Deploy via AWS CloudFormation
aws cloudformation create-stack \
  --stack-name yawl-deployment \
  --template-body file:///home/user/yawl/marketplace/aws-marketplace/product.yaml \
  --parameters \
    ParameterKey=DomainName,ParameterValue=yawl.example.com \
    ParameterKey=DBPassword,ParameterValue=your-secure-password \
  --capabilities CAPABILITY_IAM
```

### 3. Azure Marketplace

**File:** `/home/user/yawl/marketplace/azure-marketplace/createUiDefinition.json`

Azure deployment UI definition providing:
- Application and database settings
- Network configuration (VNet, subnets)
- Security settings (Azure AD, encryption)
- Resource tagging
- Interactive form wizard for deployment

**Deployment:**
```bash
# Deploy via Azure Portal or ARM template
az deployment group create \
  --name yawl-deployment \
  --resource-group yawl-rg \
  --template-file createUiDefinition.json
```

### 4. Docker Hub Registry

**Files:**
- `Dockerfile` - Multi-stage Alpine-based image
- `docker-compose.yml` - Complete stack with PostgreSQL, Redis, Prometheus, Grafana
- `.env.example` - Environment configuration template

**Docker Image Build & Push:**
```bash
cd /home/user/yawl/marketplace/docker-hub

# Build image
docker build -t yawl:1.0.0 .
docker tag yawl:1.0.0 yawl/yawl:1.0.0

# Push to Docker Hub
docker login
docker push yawl/yawl:1.0.0

# Run standalone
docker run -d \
  -p 8080:8080 \
  -e DATABASE_HOST=yawl-db \
  -e DATABASE_PASSWORD=changeme \
  yawl/yawl:1.0.0

# Or use Docker Compose
cp .env.example .env
docker-compose up -d
```

### 5. Kubernetes Helm Charts

**Files:**
- `Chart.yaml` - Helm chart metadata
- `values.yaml` - Default values for all cloud providers
- `values-gcp.yaml` - GCP-specific overrides
- `values-aws.yaml` - AWS-specific overrides
- `values-azure.yaml` - Azure-specific overrides

**Helm Installation:**
```bash
cd /home/user/yawl/marketplace/helm-repo

# Install with default values
helm install yawl . \
  --namespace yawl \
  --create-namespace

# Install with GCP configuration
helm install yawl . \
  -f values-gcp.yaml \
  --set global.gcp.project=my-project \
  --namespace yawl \
  --create-namespace

# Install with AWS configuration
helm install yawl . \
  -f values-aws.yaml \
  --set global.aws.region=us-east-1 \
  --namespace yawl \
  --create-namespace

# Install with Azure configuration
helm install yawl . \
  -f values-azure.yaml \
  --set global.azure.resourceGroup=yawl-rg \
  --namespace yawl \
  --create-namespace
```

## Feature Highlights

### GCP Marketplace (metadata.yaml)
- ✅ Kubernetes cluster constraints
- ✅ Application properties with constraints
- ✅ Deployment service account
- ✅ Support and documentation links
- ✅ Container image specifications

### AWS Marketplace (product.yaml)
- ✅ Complete infrastructure as code
- ✅ RDS PostgreSQL with backup
- ✅ Application Load Balancer
- ✅ Auto Scaling Group
- ✅ SSL/TLS support
- ✅ Security groups and networking
- ✅ CloudWatch integration

### Azure Marketplace (createUiDefinition.json)
- ✅ Interactive deployment wizard
- ✅ VM and database sizing
- ✅ Network configuration
- ✅ Security settings
- ✅ Monitoring integration
- ✅ Resource tagging
- ✅ Multiple certificate options

### Docker Hub
- ✅ Multi-stage Docker build
- ✅ Security best practices (non-root user)
- ✅ Health checks
- ✅ Complete docker-compose stack
- ✅ Monitoring (Prometheus/Grafana)
- ✅ PostgreSQL and Redis services
- ✅ Production-ready configuration

### Helm Charts
- ✅ High availability (3+ replicas)
- ✅ Auto-scaling (HPA)
- ✅ Cloud-specific values files
- ✅ Monitoring and observability
- ✅ Persistent data storage
- ✅ Backup and recovery
- ✅ Security policies
- ✅ Network policies
- ✅ External service integration

## Configuration Examples

### GCP Deployment with Monitoring

```yaml
# From metadata.yaml
properties:
  - name: YAWL_REPLICAS
    default: 3
  - name: ENABLE_MONITORING
    default: true
```

### AWS Auto Scaling Configuration

```yaml
# From product.yaml
AutoScalingGroup:
  MinSize: 1
  MaxSize: 10
  DesiredCapacity: 3
  TargetGroupARNs:
    - !GetAtt TargetGroup.TargetGroupArn
```

### Azure Database Configuration

```json
// From createUiDefinition.json
{
  "name": "dbAdminPassword",
  "type": "Microsoft.Common.PasswordBox",
  "constraints": {
    "validations": [{
      "regex": "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
      "message": "Strong password required"
    }]
  }
}
```

### Docker Compose Stack

```yaml
# From docker-compose.yml
services:
  yawl:
    image: yawl/yawl:1.0.0
    ports:
      - "8080:8080"
    depends_on:
      - yawl-db
      - yawl-cache
  yawl-db:
    image: postgres:14-alpine
  yawl-cache:
    image: redis:7-alpine
```

### Helm High Availability

```yaml
# From values.yaml
replicaCount: 3
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
```

## Environment Variables

### Docker Compose (.env.example)
```bash
YAWL_ENVIRONMENT=production
DATABASE_HOST=yawl-db
DATABASE_PASSWORD=changeme123!
REDIS_PASSWORD=redis123!
ENABLE_MONITORING=true
```

### AWS CloudFormation Parameters
```bash
VpcId=vpc-xxxxx
InstanceType=t3.medium
DesiredCapacity=3
DBPassword=changeme123!
EnableSSL=true
```

### Azure Deployment UI
- YAWL Version Selection
- VM Size Configuration
- Database Type Selection
- Network Settings
- Security Options

### Helm Values
```bash
--set image.tag=1.0.0
--set replicaCount=3
--set postgresql.auth.password=secure-password
--set ingress.hosts[0].host=yawl.example.com
```

## Cloud-Specific Integration

### GCP Features
- GCP Marketplace catalog integration
- Kubernetes Engine support
- Cloud SQL compatibility
- Cloud Monitoring integration
- Multi-zone deployment

### AWS Features
- CloudFormation templates
- RDS database service
- Application Load Balancer
- Auto Scaling Groups
- CloudWatch monitoring
- Secrets Manager integration

### Azure Features
- Azure Resource Manager templates
- Azure Database for PostgreSQL
- Application Gateway
- Azure Monitor
- Key Vault integration
- Managed Identity support

### Kubernetes Features
- Multi-cloud deployment
- StatefulSet configuration
- PersistentVolumes
- ServiceMonitor for Prometheus
- Network Policies
- RBAC support
- Pod Disruption Budgets
- Horizontal Pod Autoscaling

## Security Best Practices

All configurations include:
- ✅ Non-root container users
- ✅ Read-only filesystems
- ✅ Health checks and probes
- ✅ Network policies
- ✅ RBAC configurations
- ✅ Secret management
- ✅ SSL/TLS encryption
- ✅ Database encryption
- ✅ Backup and recovery
- ✅ Audit logging

## Production Checklist

Before deploying to production:

- [ ] Update all placeholder passwords
- [ ] Configure domain names
- [ ] Enable SSL/TLS certificates
- [ ] Set up monitoring and alerting
- [ ] Configure backup schedules
- [ ] Enable auto-scaling
- [ ] Set resource limits
- [ ] Configure security groups
- [ ] Test disaster recovery
- [ ] Review access controls

## Support and Documentation

- **GCP Marketplace:** https://yawl.org/gcp
- **AWS Marketplace:** https://yawl.org/aws
- **Azure Marketplace:** https://yawl.org/azure
- **Docker Hub:** https://hub.docker.com/r/yawl/yawl
- **Helm Chart:** https://helm.yawl.org
- **Documentation:** https://docs.yawl.org
- **GitHub:** https://github.com/yawl/yawl

## File Summary

| File | Purpose | Type |
|------|---------|------|
| `gcp-marketplace/metadata.yaml` | GCP marketplace metadata | YAML |
| `aws-marketplace/product.yaml` | AWS CloudFormation template | YAML |
| `azure-marketplace/createUiDefinition.json` | Azure deployment UI | JSON |
| `docker-hub/Dockerfile` | Container image definition | Dockerfile |
| `docker-hub/docker-compose.yml` | Complete Docker stack | YAML |
| `docker-hub/.env.example` | Environment template | Env |
| `helm-repo/Chart.yaml` | Helm chart metadata | YAML |
| `helm-repo/values.yaml` | Default Helm values | YAML |
| `helm-repo/values-gcp.yaml` | GCP-specific Helm values | YAML |
| `helm-repo/values-aws.yaml` | AWS-specific Helm values | YAML |
| `helm-repo/values-azure.yaml` | Azure-specific Helm values | YAML |

Total: **12 complete configuration files** ready for marketplace deployment.
