# YAWL Multi-Cloud Deployment Guide

## Table of Contents

1. [Overview](#overview)
2. [GCP Deployment](#gcp-deployment)
3. [AWS Deployment](#aws-deployment)
4. [Azure Deployment](#azure-deployment)
5. [Oracle Cloud Deployment](#oracle-cloud-deployment)
6. [On-Premises Deployment](#on-premises-deployment)
7. [Hybrid Cloud Architecture](#hybrid-cloud-architecture)
8. [Cost Comparison](#cost-comparison)
9. [Security Considerations](#security-considerations)
10. [Migration Paths](#migration-paths-between-clouds)

---

## Overview

This guide provides comprehensive instructions for deploying YAWL Workflow Engine across multiple cloud platforms and on-premises infrastructure. Each platform offers unique advantages, and this document helps you evaluate and implement the best solution for your organizational needs.

### Supported Deployment Targets

- **Google Cloud Platform (GCP)**: Kubernetes Engine (GKE), Cloud Run, Compute Engine
- **Amazon Web Services (AWS)**: Elastic Kubernetes Service (EKS), Elastic Container Service (ECS), EC2
- **Microsoft Azure**: Azure Kubernetes Service (AKS), Container Instances, Virtual Machines
- **Oracle Cloud Infrastructure (OCI)**: Container Engine for Kubernetes (OKE), Compute, Database
- **On-Premises**: Kubernetes, Docker Swarm, Traditional VMs
- **Hybrid**: Multi-cloud federation with federated identity management

### Common Requirements

All deployment targets require:
- YAWL application container image (base: Tomcat 9.0, Java 11+)
- Relational database (PostgreSQL 12+, MySQL 8.0+, Oracle 19c+, SQL Server 2019+)
- Message broker (optional, for advanced features)
- Load balancer or ingress controller
- 2GB+ RAM minimum for single instance
- 4GB+ RAM recommended for production

---

## GCP Deployment

### Architecture Overview

GCP provides the most tightly integrated Kubernetes experience with native managed services.

```
┌─────────────────────────────────────────────────────┐
│                  GCP Organization                   │
│  ┌──────────────────────────────────────────────┐   │
│  │          GCP Marketplace                     │   │
│  │  ┌────────────────────────────────────────┐  │   │
│  │  │  Cloud Load Balancer (HTTPS)           │  │   │
│  │  │  - SSL/TLS Certificate Management      │  │   │
│  │  └────────────────┬───────────────────────┘  │   │
│  │                   │                          │   │
│  │  ┌────────────────▼───────────────────────┐  │   │
│  │  │  GKE Cluster (Regional)                │  │   │
│  │  │  ┌──────────────────────────────────┐  │  │   │
│  │  │  │  Workload Identity (OIDC)        │  │  │   │
│  │  │  │  Service Account Integration     │  │  │   │
│  │  │  └──────────────────────────────────┘  │  │   │
│  │  │                                        │  │   │
│  │  │  ┌──────────────────────────────────┐  │  │   │
│  │  │  │  Pod 1    Pod 2    Pod 3   Pod N │  │  │   │
│  │  │  │  (YAWL Replicas with HPA)       │  │  │   │
│  │  │  │  CPU: 2-4 cores each             │  │  │   │
│  │  │  │  Memory: 2-4GB each              │  │  │   │
│  │  │  └──────┬───────┬───────┬──────────┘  │  │   │
│  │  │         └───────┼───────┘             │  │   │
│  │  │                 │                     │  │   │
│  │  │  ┌──────────────▼──────────────────┐  │  │   │
│  │  │  │  Cloud SQL Proxy Service        │  │  │   │
│  │  │  │  (Secure connections)           │  │  │   │
│  │  │  └──────────────┬──────────────────┘  │  │   │
│  │  └────────────────┼─────────────────────┘  │   │
│  │                   │                        │   │
│  │  ┌────────────────▼─────────────────────┐  │   │
│  │  │  Cloud Memorystore (Redis)           │  │   │
│  │  │  - Session caching                   │  │   │
│  │  │  - Workflow state optimization       │  │   │
│  │  └──────────────────────────────────────┘  │   │
│  │                                            │   │
│  │  ┌──────────────────────────────────────┐  │   │
│  │  │  Cloud Storage (Persistent Volumes)  │  │   │
│  │  │  - Document storage                  │  │   │
│  │  │  - Workflow definitions              │  │   │
│  │  │  - Audit logs backup                 │  │   │
│  │  └──────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │  Cloud SQL Instance                      │   │
│  │  - PostgreSQL 14 (Primary)               │   │
│  │  - Multi-region replicas                 │   │
│  │  - Automated backups                     │   │
│  │  - Point-in-time recovery                │   │
│  └──────────────────────────────────────────┘   │
│                                                 │
│  ┌──────────────────────────────────────────┐   │
│  │  Monitoring & Logging                    │   │
│  │  - Cloud Monitoring                      │   │
│  │  - Cloud Logging                         │   │
│  │  - Cloud Trace                           │   │
│  │  - Cloud Profiler                        │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### GCP Deployment Options

#### Option 1: GKE Autopilot (Recommended for Production)

**Pros:**
- Fully managed Kubernetes with automated node management
- Built-in security best practices
- Automatic scaling and updates
- Integrated monitoring and logging

**Cons:**
- Limited customization of node configuration
- Slightly higher cost due to management overhead

```bash
# Create GKE Autopilot cluster
gcloud container clusters create yawl-prod \
  --region=us-central1 \
  --enable-autoscaling \
  --min-nodes=3 \
  --max-nodes=10 \
  --enable-autorepair \
  --enable-autoupgrade \
  --enable-workload-identity \
  --enable-ip-alias \
  --network="yawl-vpc" \
  --subnetwork="yawl-subnet" \
  --addons=HorizontalPodAutoscaling,HttpLoadBalancing \
  --workload-pool=PROJECT_ID.svc.id.goog

# Deploy YAWL
kubectl apply -f gke/namespace.yaml
kubectl apply -f gke/configmap.yaml
kubectl apply -f gke/secret.yaml
kubectl apply -f gke/deployment.yaml
kubectl apply -f gke/service.yaml
kubectl apply -f gke/ingress.yaml
kubectl apply -f gke/hpa.yaml
```

#### Option 2: GKE Standard (Custom Control)

**Pros:**
- Greater control over node configuration
- Lower cost for long-running workloads
- Flexibility in machine types

**Cons:**
- Manual node management required
- Responsibility for security patches

```bash
# Create GKE Standard cluster
gcloud container clusters create yawl-standard \
  --region=us-central1 \
  --num-nodes=3 \
  --machine-type=n2-standard-4 \
  --enable-autoscaling \
  --min-nodes=3 \
  --max-nodes=10 \
  --enable-workload-identity \
  --enable-stackdriver-kubernetes \
  --addons=HttpLoadBalancing,HorizontalPodAutoscaling
```

#### Option 3: Cloud Run (Serverless)

**Pros:**
- Fully serverless, pay-per-use
- Automatic scaling from 0 to 1000 instances
- Integrated with Google Cloud services
- Simplified deployment

**Cons:**
- Stateless workloads only
- 15-minute timeout limit
- Less suitable for long-running workflows

```bash
# Build and push container image
gcloud builds submit --tag gcr.io/PROJECT_ID/yawl:latest

# Deploy to Cloud Run
gcloud run deploy yawl-workflow \
  --image=gcr.io/PROJECT_ID/yawl:latest \
  --region=us-central1 \
  --memory=2Gi \
  --cpu=2 \
  --timeout=3600 \
  --max-instances=100 \
  --set-cloudsql-instances=PROJECT_ID:us-central1:yawl-db \
  --set-env-vars=CLOUDSQL_USER=yawl,CLOUDSQL_PASSWORD=secure_password \
  --allow-unauthenticated
```

#### Option 4: Compute Engine (IaaS)

**Pros:**
- Full control over infrastructure
- Cost-effective for sustained workloads
- Easy integration with existing tools

**Cons:**
- Manual management and patching
- Responsibility for scaling and availability

```bash
# Create instance template
gcloud compute instance-templates create yawl-template \
  --machine-type=n2-standard-4 \
  --image=ubuntu-2004-lts \
  --boot-disk-size=100GB \
  --preemptible

# Create managed instance group
gcloud compute instance-groups managed create yawl-ig \
  --base-instance-name=yawl \
  --template=yawl-template \
  --size=3 \
  --region=us-central1
```

### GCP Configuration Details

#### Database Setup

```yaml
# Cloud SQL Instance
machine_type: db-custom-2-7680  # 2 vCPU, 7.68GB RAM
storage: 100GB SSD
tier: Premium
backups:
  enabled: true
  location: us
  retention_days: 30
  frequency: daily
  time: 03:00 UTC
ha_configuration:
  kind: Regional
  replica_configuration:
    kind: Regional
flags:
  max_connections: 500
  shared_buffers: "16GB"
  effective_cache_size: "20GB"
  maintenance_work_mem: "512MB"
```

#### Costs (GCP)

| Component | Size | Monthly Cost |
|-----------|------|--------------|
| GKE Cluster | 3 nodes (n2-std-4) | $360 |
| Node compute | Included | $0 |
| Cloud SQL | db-custom-2-7680 | $450 |
| Cloud SQL Storage | 100GB SSD | $15 |
| Cloud SQL Backups | 30 days | $50 |
| Cloud Memorystore | 5GB | $30 |
| Cloud Load Balancer | Standard | $20 |
| Cloud Storage | Backups (50GB) | $5 |
| Monitoring/Logging | Standard | $20 |
| **Total** | | **~$950/month** |

---

## AWS Deployment

### Architecture Overview

AWS provides the most comprehensive set of services with excellent scaling capabilities.

```
┌──────────────────────────────────────────────────────────┐
│              AWS Region (Multi-AZ)                       │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Elastic Load Balancing (ALB/NLB)                  │  │
│  │  - Application Load Balancer (Layer 7)             │  │
│  │  - TLS 1.3 termination                             │  │
│  │  - Route to ECS or EKS                             │  │
│  └────────┬──────────────────────────────────┬────────┘  │
│           │                                  │            │
│  ┌────────▼─────────────────┐     ┌─────────▼────────┐  │
│  │  EKS Cluster (Option A)  │     │ ECS Cluster      │  │
│  │  ┌────────────────────┐  │     │ (Option B)       │  │
│  │  │ Worker Nodes       │  │     │ ┌──────────────┐ │  │
│  │  │ (ASG: 3-10)        │  │     │ │ Task Defs    │ │  │
│  │  │ EC2: t3.xlarge     │  │     │ │ YAWL Services│ │  │
│  │  │ or c5.2xlarge      │  │     │ │ (3-10 tasks) │ │  │
│  │  │ ┌─────────────────┐│  │     │ └──────────────┘ │  │
│  │  │ │Pod 1  Pod 2     ││  │     │                  │  │
│  │  │ │Pod 3  Pod 4 ... ││  │     │                  │  │
│  │  │ │(YAWL Replicas)  ││  │     │                  │  │
│  │  │ └─────────────────┘│  │     │                  │  │
│  │  │                    │  │     │                  │  │
│  │  └────────────────────┘  │     └──────────────────┘  │
│  └────────┬──────────────────┘                           │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐  │
│  │  ElastiCache (Redis/Memcached)                    │  │
│  │  - Session caching                               │  │
│  │  - Workflow state                                │  │
│  │  - Multi-AZ replication                          │  │
│  └────────────────────────────────────────────────┘  │
│                                                       │
│  ┌──────────────────────────────────────────────┐   │
│  │  RDS (Relational Database Service)           │   │
│  │  - PostgreSQL 14 (Multi-AZ)                  │   │
│  │  - Read replicas (cross-region)              │   │
│  │  - Automated backups (35-day retention)      │   │
│  │  - Enhanced monitoring                       │   │
│  └──────────────────────────────────────────────┘   │
│                                                       │
│  ┌──────────────────────────────────────────────┐   │
│  │  S3 (Object Storage)                         │   │
│  │  - Document storage                          │   │
│  │  - Workflow archives                         │   │
│  │  - Backup storage (versioning)               │   │
│  │  - Cross-region replication                  │   │
│  └──────────────────────────────────────────────┘   │
│                                                       │
│  ┌──────────────────────────────────────────────┐   │
│  │  CloudWatch & X-Ray                          │   │
│  │  - Metrics and dashboards                    │   │
│  │  - Log aggregation                           │   │
│  │  - Distributed tracing                       │   │
│  │  - Alarms and notifications                  │   │
│  └──────────────────────────────────────────────┘   │
│                                                       │
│  ┌──────────────────────────────────────────────┐   │
│  │  AWS Secrets Manager                         │   │
│  │  - Database credentials                      │   │
│  │  - API keys                                  │   │
│  │  - SSL certificates                          │   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### AWS Deployment Options

#### Option 1: EKS (Elastic Kubernetes Service)

**Pros:**
- Full Kubernetes compatibility
- AWS-managed control plane
- Excellent integration with AWS services
- Strong security posture

**Cons:**
- Control plane charges ($0.10/hour)
- Requires manual worker node management
- Steeper learning curve for Kubernetes

```bash
# Create EKS cluster
aws eks create-cluster \
  --name yawl-prod \
  --version 1.27 \
  --role-arn arn:aws:iam::ACCOUNT_ID:role/eks-service-role \
  --resources-vpc-config subnetIds=subnet-12345,subnet-67890

# Add worker nodes (via Auto Scaling Group)
aws ec2 launch-template create \
  --launch-template-name yawl-lt \
  --version-description "YAWL worker template" \
  --launch-template-data '{
    "ImageId": "ami-0c55b159cbfafe1f0",
    "InstanceType": "c5.2xlarge",
    "KeyName": "yawl-key",
    "SecurityGroupIds": ["sg-12345"]
  }'

# Deploy application
kubectl apply -f eks/namespace.yaml
kubectl apply -f eks/serviceaccount.yaml
kubectl apply -f eks/deployment.yaml
kubectl apply -f eks/service.yaml
kubectl apply -f eks/ingress.yaml
```

#### Option 2: ECS (Elastic Container Service)

**Pros:**
- Simpler management than Kubernetes
- Native AWS service with deep integration
- Lower operational overhead
- Excellent auto-scaling

**Cons:**
- Not Kubernetes-compatible
- Less portable to other cloud providers
- Requires AWS-specific knowledge

```bash
# Create ECS cluster
aws ecs create-cluster --cluster-name yawl-prod

# Register task definition
aws ecs register-task-definition \
  --family yawl \
  --network-mode awsvpc \
  --requires-compatibilities FARGATE \
  --cpu 1024 \
  --memory 2048 \
  --container-definitions '[
    {
      "name": "yawl",
      "image": "ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/yawl:latest",
      "portMappings": [{"containerPort": 8080}],
      "environment": [
        {"name": "DB_HOST", "value": "yawl-db.cluster.amazonaws.com"},
        {"name": "DB_USER", "value": "yawl"}
      ],
      "secrets": [
        {"name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:..."}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/yawl",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]'

# Create ECS service
aws ecs create-service \
  --cluster yawl-prod \
  --service-name yawl \
  --task-definition yawl:1 \
  --desired-count 3 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-12345],securityGroups=[sg-12345],assignPublicIp=DISABLED}"
```

#### Option 3: EC2 with Docker Swarm

**Pros:**
- Full control over infrastructure
- Lower costs for sustained workloads
- Simple orchestration with Docker Swarm

**Cons:**
- Manual scaling and management
- No native auto-scaling like Kubernetes
- Less sophisticated monitoring

```bash
# Launch EC2 instances
aws ec2 run-instances \
  --image-id ami-0c55b159cbfafe1f0 \
  --count 3 \
  --instance-type c5.2xlarge \
  --key-name yawl-key \
  --security-group-ids sg-12345

# Initialize Docker Swarm
docker swarm init

# Join worker nodes
docker swarm join --token SWMTKN-1-... MANAGER_IP:2377

# Deploy stack
docker stack deploy -c docker-compose.yml yawl
```

#### Option 4: AWS Fargate (Serverless Containers)

**Pros:**
- Fully serverless, pay-per-use
- No infrastructure management
- Excellent for variable workloads
- Built-in security isolation

**Cons:**
- Higher cost per vCPU-hour
- Limited to ECS
- Cold start latency

```bash
# Register Fargate task definition
aws ecs register-task-definition \
  --family yawl-fargate \
  --network-mode awsvpc \
  --requires-compatibilities FARGATE \
  --cpu 2048 \
  --memory 4096 \
  --execution-role-arn arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole \
  --container-definitions '[
    {
      "name": "yawl",
      "image": "ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/yawl:latest",
      "portMappings": [{"containerPort": 8080, "protocol": "tcp"}]
    }
  ]'

# Create Fargate service with auto-scaling
aws ecs create-service \
  --cluster yawl-prod \
  --service-name yawl-fargate \
  --task-definition yawl-fargate \
  --desired-count 3 \
  --launch-type FARGATE \
  --platform-version LATEST \
  --load-balancers targetGroupArn=arn:aws:elasticloadbalancing:...
```

### AWS Configuration Details

#### RDS Database Setup

```yaml
# RDS Instance
engine: PostgreSQL
engine_version: "14.7"
instance_class: db.r6i.2xlarge
allocated_storage: 100
storage_type: gp3
storage_iops: 3000
multi_az: true
backup_retention_period: 35
preferred_backup_window: "03:00-04:00"
preferred_maintenance_window: "sun:04:00-sun:05:00"
enable_cloudwatch_logs_exports:
  - postgresql
enable_enhanced_monitoring: true
monitoring_interval: 60
parameters:
  max_connections: 500
  shared_buffers: "16GB"
  effective_cache_size: "20GB"
```

#### Costs (AWS - us-east-1)

| Component | Size | Monthly Cost |
|-----------|------|--------------|
| EKS Control Plane | Standard | $73 |
| EC2 Worker Nodes | 3x c5.2xlarge (On-Demand) | $720 |
| ELB/ALB | Standard | $22 |
| RDS PostgreSQL | db.r6i.2xlarge (Multi-AZ) | $800 |
| RDS Storage | 100GB gp3 | $25 |
| ElastiCache | cache.r6g.large (Multi-AZ) | $150 |
| S3 Storage | 100GB | $2 |
| CloudWatch Logs | 50GB ingestion | $25 |
| Data Transfer | 1TB | $100 |
| **Total** | | **~$1,917/month** |

---

## Azure Deployment

### Architecture Overview

Azure provides deep integration with enterprise tools and excellent hybrid capabilities.

```
┌──────────────────────────────────────────────────────┐
│          Azure Region (Multi-Zone)                   │
│  ┌────────────────────────────────────────────────┐  │
│  │  Azure Application Gateway                     │  │
│  │  - Layer 7 load balancing                      │  │
│  │  - WAF (Web Application Firewall)              │  │
│  │  - TLS termination                             │  │
│  └────────┬──────────────────────────────────────┘  │
│           │                                          │
│  ┌────────▼──────────────────────────────────────┐  │
│  │  AKS Cluster (Azure Kubernetes Service)       │  │
│  │  ┌──────────────────────────────────────────┐ │  │
│  │  │  System Node Pool                        │ │  │
│  │  │  (2 nodes: Standard_D4s_v3)              │ │  │
│  │  └──────────────────────────────────────────┘ │  │
│  │                                              │  │
│  │  ┌──────────────────────────────────────────┐ │  │
│  │  │  User Node Pool (Auto-Scale)             │ │  │
│  │  │  (3-10 nodes: Standard_D8s_v3)           │ │  │
│  │  │  ┌──────────────────────────────────────┐│ │  │
│  │  │  │  Pod 1    Pod 2    Pod 3  ...  Pod N││ │  │
│  │  │  │  (YAWL Replicas)                    ││ │  │
│  │  │  └──────────────────────────────────────┘│ │  │
│  │  └──────────────────────────────────────────┘ │  │
│  └────────┬──────────────────────────────────────┘  │
│           │                                          │
│  ┌────────▼──────────────────────────────────────┐  │
│  │  Azure Cache for Redis                        │  │
│  │  - Premium tier (6GB)                         │  │
│  │  - Zone-redundant                             │  │
│  └────────────────────────────────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │  Azure Database for PostgreSQL               │  │
│  │  - Single Server or Flexible Server          │  │
│  │  - Automatic backups (35 days)               │  │
│  │  - Read replicas (cross-region)              │  │
│  │  - Geo-redundant backups                     │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │  Azure Blob Storage                          │  │
│  │  - Document storage                          │  │
│  │  - Backup archives                           │  │
│  │  - Geo-redundant replication                 │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │  Azure Monitor + Log Analytics               │  │
│  │  - Metrics and dashboards                    │  │
│  │  - Log aggregation (KQL queries)             │  │
│  │  - Application Insights                      │  │
│  │  - Alerts and automation                     │  │
│  └──────────────────────────────────────────────┘  │
│                                                    │
│  ┌──────────────────────────────────────────────┐  │
│  │  Azure Key Vault                             │  │
│  │  - Secrets management                        │  │
│  │  - Certificate management                    │  │
│  │  - HSM support (premium)                     │  │
│  └──────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

### Azure Deployment Options

#### Option 1: AKS (Azure Kubernetes Service)

**Pros:**
- Managed Kubernetes with Azure integration
- Excellent DevOps tooling (Azure DevOps)
- Strong Active Directory integration
- Cost-effective for variable workloads

**Cons:**
- Different from GKE/EKS in some areas
- Less mature container ecosystem than AWS

```bash
# Create resource group
az group create \
  --name yawl-rg \
  --location eastus

# Create AKS cluster
az aks create \
  --resource-group yawl-rg \
  --name yawl-prod \
  --node-count 3 \
  --vm-set-type VirtualMachineScaleSets \
  --load-balancer-sku standard \
  --enable-managed-identity \
  --network-plugin azure \
  --enable-addons monitoring \
  --workspace-resource-id /subscriptions/SUB_ID/resourcegroups/yawl-rg/providers/microsoft.operationalinsights/workspaces/yawl-logs \
  --kubernetes-version 1.27 \
  --node-vm-size Standard_D8s_v3 \
  --enable-cluster-autoscaling \
  --min-count 3 \
  --max-count 10 \
  --zones 1 2 3

# Get credentials
az aks get-credentials \
  --resource-group yawl-rg \
  --name yawl-prod

# Deploy YAWL
kubectl apply -f aks/namespace.yaml
kubectl apply -f aks/configmap.yaml
kubectl apply -f aks/secret.yaml
kubectl apply -f aks/deployment.yaml
kubectl apply -f aks/service.yaml
kubectl apply -f aks/ingress.yaml
```

#### Option 2: App Service (PaaS)

**Pros:**
- Fully managed application hosting
- Integrated deployment pipelines
- Built-in auto-scaling
- No container orchestration needed

**Cons:**
- Less flexible than containers
- Stateless applications only
- Limited customization

```bash
# Create App Service plan
az appservice plan create \
  --name yawl-plan \
  --resource-group yawl-rg \
  --sku P2V2 \
  --is-linux

# Create web app
az webapp create \
  --resource-group yawl-rg \
  --plan yawl-plan \
  --name yawl-app \
  --deployment-container-image-name-user REGISTRY/yawl:latest

# Configure container
az webapp config container set \
  --name yawl-app \
  --resource-group yawl-rg \
  --docker-custom-image-name REGISTRY/yawl:latest \
  --docker-registry-server-url https://REGISTRY \
  --docker-registry-server-user USERNAME \
  --docker-registry-server-password PASSWORD
```

#### Option 3: Container Instances (Serverless)

**Pros:**
- Fastest way to run containers
- Pay-per-second billing
- No orchestration needed
- Good for dev/test

**Cons:**
- Limited scaling
- No built-in load balancing
- Stateless only

```bash
# Create container instance
az container create \
  --resource-group yawl-rg \
  --name yawl \
  --image REGISTRY/yawl:latest \
  --cpu 2 \
  --memory 4 \
  --environment-variables DB_HOST=yawl-db.postgres.database.azure.com DB_USER=yawl \
  --secure-environment-variables DB_PASSWORD=$DB_PASSWORD \
  --ip-address public \
  --ports 8080 \
  --protocol TCP
```

### Azure Configuration Details

#### Database Setup

```bash
# Create Azure Database for PostgreSQL
az postgres server create \
  --resource-group yawl-rg \
  --name yawl-db \
  --location eastus \
  --admin-user yawl \
  --admin-password secure_password \
  --sku-name Standard_B2s \
  --storage-size 102400 \
  --backup-retention 35 \
  --geo-redundant-backup Enabled \
  --enable-major-version-upgrade false \
  --version 14

# Configure firewall
az postgres server firewall-rule create \
  --resource-group yawl-rg \
  --server-name yawl-db \
  --name AllowAKS \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 255.255.255.255
```

#### Costs (Azure - East US)

| Component | Size | Monthly Cost |
|-----------|------|--------------|
| AKS Cluster | Management | $73 |
| AKS Nodes | 3x Standard_D8s_v3 (Spot) | $400 |
| Application Gateway | Standard | $25 |
| Azure Database PostgreSQL | Standard_B2s | $200 |
| Database Storage | 100GB | $20 |
| Azure Cache Redis | Premium (6GB) | $120 |
| Blob Storage | 100GB | $2 |
| Log Analytics | Pay-as-you-go | $30 |
| Application Insights | Standard | $5 |
| **Total** | | **~$875/month** |

---

## Oracle Cloud Deployment

### Architecture Overview

Oracle Cloud provides excellent database capabilities and tight integration with Oracle products.

```
┌──────────────────────────────────────────────────────────┐
│           Oracle Cloud Infrastructure (OCI)              │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Load Balancer (Layer 4 & 7)                       │  │
│  │  - SSL/TLS termination                             │  │
│  │  - Path-based routing                              │  │
│  │  - DDoS protection                                 │  │
│  └────────┬──────────────────────────────────────────┘  │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐  │
│  │  OKE (Container Engine for Kubernetes)            │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  Master Nodes (Managed)                      │ │  │
│  │  │  Kubernetes 1.27                             │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  │                                                  │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  Worker Nodes (VMs)                          │ │  │
│  │  │  3-10 nodes: VM.Standard.E4.Flex             │ │  │
│  │  │  ┌──────────────────────────────────────────┐│ │  │
│  │  │  │  Pod 1    Pod 2    Pod 3  ...  Pod N  ││ │  │
│  │  │  │  (YAWL Replicas)                      ││ │  │
│  │  │  └──────────────────────────────────────────┘│ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  └────────┬──────────────────────────────────────────┘  │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐  │
│  │  Cache (Redis)                                    │  │
│  │  - High performance caching                       │  │
│  │  - Multi-AZ deployment                            │  │
│  └────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  DB System (Managed Database)                    │  │
│  │  - PostgreSQL 14 or Oracle 19c                   │  │
│  │  - High Availability with Data Guard             │  │
│  │  - Automated backups                             │  │
│  │  - Point-in-time recovery                        │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Object Storage                                  │  │
│  │  - Document storage                              │  │
│  │  - Workflow archives                             │  │
│  │  - Cross-region replication                      │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Monitoring & Logging                            │  │
│  │  - Metrics and dashboards                        │  │
│  │  - Logging service                               │  │
│  │  - Events service                                │  │
│  │  - Alarms                                        │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Vault (Secret Management)                       │  │
│  │  - Database credentials                          │  │
│  │  - API keys                                      │  │
│  │  - SSL certificates                              │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Oracle Cloud Deployment Options

#### Option 1: OKE (Container Engine for Kubernetes)

**Pros:**
- Fully managed Kubernetes
- Excellent database integration
- Cost-effective compute options
- Strong security features

**Cons:**
- Smaller ecosystem than AWS/GCP
- Less mature monitoring tools
- Smaller community

```bash
# Create OKE cluster
oci ce cluster create \
  --name yawl-prod \
  --compartment-id ocid1.compartment.oc1... \
  --kubernetes-version v1.27 \
  --kubernetes-network-config networkType=FLANNEL_OVERLAY

# Create node pool
oci ce node-pool create \
  --cluster-id yawl-cluster-id \
  --compartment-id ocid1.compartment.oc1... \
  --name yawl-nodes \
  --node-image-id Oracle-Linux-8.7 \
  --node-shape VM.Standard.E4.Flex \
  --initial-node-labels "key=workload-type,value=production" \
  --quantity-per-subnet 3

# Get kubeconfig
oci ce cluster create-kubeconfig \
  --cluster-id yawl-cluster-id \
  --file $HOME/.kube/config \
  --region us-phoenix-1

# Deploy YAWL
kubectl apply -f oke/namespace.yaml
kubectl apply -f oke/configmap.yaml
kubectl apply -f oke/secret.yaml
kubectl apply -f oke/deployment.yaml
kubectl apply -f oke/service.yaml
kubectl apply -f oke/ingress.yaml
```

#### Option 2: Compute with Docker (IaaS)

**Pros:**
- Full control over infrastructure
- Cost-effective for sustained workloads
- Simple management

**Cons:**
- Manual scaling
- Responsibility for updates
- No auto-scaling

```bash
# Create compute instance
oci compute instance launch \
  --compartment-id ocid1.compartment.oc1... \
  --image-id ocid1.image.oc1.phx... \
  --shape VM.Standard.E4.Flex \
  --display-name yawl-1 \
  --subnet-id ocid1.subnet.oc1...

# Install Docker and deploy
ssh opc@instance_ip
sudo yum install docker-engine
sudo systemctl start docker
sudo docker run -d -p 8080:8080 yawl/yawl:latest
```

#### Option 3: MySQL Database Service

**Pros:**
- Alternative to PostgreSQL
- MySQL-compatible
- High availability built-in
- Automated backups

**Cons:**
- May require schema migration
- Less advanced features than PostgreSQL

```bash
# Create MySQL Database Service
oci mysql db-system create \
  --admin-username admin \
  --admin-password secure_password \
  --availability-domain AD-1 \
  --compartment-id ocid1.compartment.oc1... \
  --configuration-id ocid1.mysqlconfiguration.oc1... \
  --display-name yawl-db \
  --shape-name MySQL.8.0 \
  --backup-policy-is-enabled true \
  --backup-policy-retention-in-days 30
```

### Oracle Cloud Configuration Details

#### Database Setup

```bash
# Create PostgreSQL Database System
oci mysql db-system create \
  --admin-username yawl \
  --admin-password secure_password \
  --availability-domain AD-1 \
  --compartment-id ocid1.compartment.oc1... \
  --display-name yawl-db \
  --shape-name MySQL.8.0 \
  --data-storage-size-in-gb 100 \
  --backup-policy-is-enabled true

# Alternative: Import existing PostgreSQL instance
oci database autonomous-db create \
  --compartment-id ocid1.compartment.oc1... \
  --db-name yawldb \
  --admin-password secure_password \
  --db-workload OLTP
```

#### Costs (OCI - Phoenix Region)

| Component | Size | Monthly Cost |
|-----------|------|--------------|
| OKE Control Plane | Standard | $0 |
| OKE Worker Nodes | 3x VM.Standard.E4.Flex | $300 |
| Network Load Balancer | Standard | $15 |
| MySQL Database Service | DB.Standard.E4.8 | $350 |
| Database Storage | 100GB | $10 |
| Cache | 20GB Redis | $100 |
| Object Storage | 100GB | $2 |
| Logging/Monitoring | Standard | $20 |
| **Total** | | **~$797/month** |

---

## On-Premises Deployment

### Architecture Overview

On-premises deployment provides complete control but requires more operational overhead.

```
┌──────────────────────────────────────────────────────────┐
│           Data Center (On-Premises)                      │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Firewall & Load Balancer (Hardware/Virtual)       │  │
│  │  - Reverse proxy (NGINX/HAProxy)                   │  │
│  │  - SSL/TLS termination                             │  │
│  │  - Session persistence                             │  │
│  └────────┬──────────────────────────────────────────┘  │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐  │
│  │  Kubernetes Cluster (Self-Managed)                │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  Control Plane (3 masters)                   │ │  │
│  │  │  - etcd (clustered)                          │ │  │
│  │  │  - API Server (replicated)                   │ │  │
│  │  │  - Controller Manager                        │ │  │
│  │  │  - Scheduler                                 │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  │                                                  │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  Worker Nodes (6-12)                         │ │  │
│  │  │  Physical: 32GB RAM, 8 vCPU, 500GB SSD      │ │  │
│  │  │  ┌──────────────────────────────────────────┐│ │  │
│  │  │  │  Pod 1    Pod 2    Pod 3  ...  Pod N  ││ │  │
│  │  │  │  (YAWL Replicas)                      ││ │  │
│  │  │  └──────────────────────────────────────────┘│ │  │
│  │  │                                              │ │  │
│  │  │  ┌──────────────────────────────────────────┐│ │  │
│  │  │  │  Local Storage (PersistentVolumes)      ││ │  │
│  │  │  │  - NFS/iSCSI backends                   ││ │  │
│  │  │  │  - SSD for databases                    ││ │  │
│  │  │  └──────────────────────────────────────────┘│ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  └────────┬──────────────────────────────────────────┘  │
│           │                                              │
│  ┌────────▼──────────────────────────────────────────┐  │
│  │  Storage (SAN/NAS)                                │  │
│  │  - Database storage (SSD-backed)                  │  │
│  │  - Document archives                             │  │
│  │  - Backup destination                            │  │
│  │  - RAID 6/10 configuration                        │  │
│  └────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Database Server (Physical/VM)                   │  │
│  │  - PostgreSQL 14 (Master-Slave replication)      │  │
│  │  - 64GB RAM, 16 vCPU                             │  │
│  │  - High-performance SSD                          │  │
│  │  - RAID 10 for data integrity                    │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Monitoring & Backup                             │  │
│  │  - Prometheus + Grafana                          │  │
│  │  - ELK Stack (Elasticsearch, Logstash, Kibana)  │  │
│  │  - Backup server (Bacula/Duplicati)              │  │
│  │  - Network monitoring (Nagios/Zabbix)            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Security                                        │  │
│  │  - Firewall appliance                            │  │
│  │  - VPN gateway                                   │  │
│  │  - Physical access control                       │  │
│  │  - Intrusion detection (IDS/IPS)                 │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### On-Premises Deployment Options

#### Option 1: Kubernetes (Recommended)

**Pros:**
- Full control and customization
- Portable to cloud if needed
- No vendor lock-in
- Excellent for complex workloads

**Cons:**
- Requires Kubernetes expertise
- High operational overhead
- Self-managed updates and security

```bash
# Using kubeadm for cluster initialization
sudo kubeadm init \
  --apiserver-advertise-address=10.0.0.10 \
  --pod-network-cidr=10.244.0.0/16 \
  --kubernetes-version=v1.27.0

# Join worker nodes
sudo kubeadm join 10.0.0.10:6443 \
  --token abc123.xyz789 \
  --discovery-token-ca-cert-hash sha256:abc123...

# Install networking (Flannel)
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml

# Deploy storage class (NFS example)
kubectl apply -f nfs-storage-class.yaml

# Deploy YAWL
kubectl apply -f on-prem/namespace.yaml
kubectl apply -f on-prem/pvc.yaml
kubectl apply -f on-prem/configmap.yaml
kubectl apply -f on-prem/secret.yaml
kubectl apply -f on-prem/deployment.yaml
kubectl apply -f on-prem/service.yaml
```

#### Option 2: Docker Swarm

**Pros:**
- Simpler than Kubernetes
- Lower learning curve
- Built into Docker
- Good for small teams

**Cons:**
- Limited scaling capabilities
- Less sophisticated than Kubernetes
- Smaller ecosystem

```bash
# Initialize Swarm on manager node
docker swarm init --advertise-addr=10.0.0.10

# Join worker nodes
docker swarm join \
  --token SWMTKN-1-abc123... \
  10.0.0.10:2377

# Deploy YAWL stack
docker stack deploy -c docker-compose-swarm.yml yawl

# Verify deployment
docker service ls
docker service logs yawl_yawl-app
```

#### Option 3: Standalone VMs (Simple)

**Pros:**
- Simplest to set up
- Excellent for single-node deployments
- Full control

**Cons:**
- No built-in redundancy
- Manual failover required
- Doesn't scale well

```bash
# Install Java and Tomcat
sudo apt-get install openjdk-11-jdk
wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.70/bin/apache-tomcat-9.0.70.tar.gz
tar -xzf apache-tomcat-9.0.70.tar.gz
sudo mv apache-tomcat-9.0.70 /opt/tomcat

# Deploy YAWL WAR file
cp yawl.war /opt/tomcat/webapps/
/opt/tomcat/bin/startup.sh

# Configure database
psql -h localhost -U yawl -d yawl < yawl-schema.sql
```

### On-Premises Hardware Requirements

#### Minimum Configuration

```yaml
Control Plane Nodes:
  - Count: 3 (for high availability)
  - CPU: 8 cores minimum
  - RAM: 16GB minimum
  - Storage: 50GB SSD
  - Network: 1Gbps minimum

Worker Nodes:
  - Count: 6-12 (depends on workload)
  - CPU: 16 cores per node
  - RAM: 32GB per node
  - Storage: 500GB SSD (local fast storage)
  - Network: 10Gbps recommended

Storage:
  - Type: SAN/NAS with RAID 6 or 10
  - Capacity: Minimum 500GB, recommended 2TB
  - Performance: 10K+ IOPS for databases
  - Backup: 1TB for offsite backups

Database Server:
  - CPU: 16+ cores
  - RAM: 64GB+ for PostgreSQL
  - Storage: 1TB SSD RAID 10
  - Network: 10Gbps

Load Balancer:
  - CPU: 8 cores
  - RAM: 8GB
  - Network: 10Gbps
  - Storage: 100GB

Total Capital Expenditure (CapEx):
  - Hardware: $50,000 - $100,000
  - Installation/Configuration: $10,000 - $20,000
  - Licensing (if applicable): $5,000 - $15,000
```

#### Operational Requirements

```yaml
Personnel:
  - Kubernetes Administrator: 1 FTE
  - Database Administrator: 1 FTE
  - Network Administrator: 0.5 FTE
  - System Administrator: 0.5 FTE

Maintenance Tasks:
  - Daily:
    - Monitor cluster health
    - Check database backups
    - Review error logs
  - Weekly:
    - Security patches
    - Performance analysis
  - Monthly:
    - Database maintenance
    - Capacity planning
  - Quarterly:
    - Disaster recovery drills
    - Security audits

Backup Strategy:
  - Database: Daily full + hourly incremental
  - Application state: Daily snapshots
  - Offsite: Weekly to remote location
  - Retention: 30 days local, 1 year offsite
```

---

## Hybrid Cloud Architecture

### Hybrid Deployment Model

```
┌─────────────────────────────────────────────────────────────┐
│                    Hybrid Architecture                       │
│                                                              │
│  ┌─────────────────────┐          ┌──────────────────────┐  │
│  │  On-Premises        │          │  Public Cloud (AWS)  │  │
│  │  ┌───────────────┐  │          │  ┌────────────────┐  │  │
│  │  │ Kubernetes    │  │          │  │ EKS Cluster    │  │  │
│  │  │ (Core YAWL)   │  │          │  │ (Burst/Dev)    │  │  │
│  │  │ 3-5 nodes     │  │          │  │ 3-10 nodes     │  │  │
│  │  └───────────────┘  │          │  └────────────────┘  │  │
│  │                     │          │                      │  │
│  │  ┌───────────────┐  │          │  ┌────────────────┐  │  │
│  │  │ Primary DB    │  │ Repl.    │  │ Read Replica   │  │  │
│  │  │ PostgreSQL    │◄─┼─────────►│  │ PostgreSQL     │  │  │
│  │  └───────────────┘  │ (SSL)    │  └────────────────┘  │  │
│  │                     │          │                      │  │
│  │  ┌───────────────┐  │          │  ┌────────────────┐  │  │
│  │  │ Storage (NAS) │  │          │  │ S3 Backup      │  │  │
│  │  └───────────────┘  │          │  └────────────────┘  │  │
│  └─────────────────────┘          └──────────────────────┘  │
│           │                               │                 │
│           │                               │                 │
│           └───────────┬───────────────────┘                 │
│                       │                                    │
│              ┌────────▼──────────┐                         │
│              │   VPN/DX Gateway   │                        │
│              │  Encrypted Tunnel  │                        │
│              │  (Site-to-Site)    │                        │
│              └────────────────────┘                        │
│                                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │          Federated Identity Management             │  │
│  │  - Okta/Azure AD for SSO                           │  │
│  │  - Cross-cloud RBAC                                │  │
│  │  - Unified logging and monitoring                  │  │
│  │  - Centralized policy enforcement                  │  │
│  └────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │          Shared Services                           │  │
│  │  - DNS (Route53, on-prem DNS)                      │  │
│  │  - NTP synchronization                             │  │
│  │  - Certificate management                          │  │
│  │  - Backup and disaster recovery                    │  │
│  └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Hybrid Implementation Strategies

#### Strategy 1: Cloud Burst (Primary: On-Premises)

**Use Case:** Predictable baseline workload on-premises with cloud overflow for peaks.

```yaml
Primary Deployment:
  - Location: On-Premises
  - Cluster: 3 master, 6 worker nodes
  - Capacity: 50% of average load
  - SLA: 99.9%
  - Cost: Fixed (CapEx + maintenance)

Burst Deployment:
  - Location: AWS/GCP/Azure
  - Cluster: 2-10 worker nodes (auto-scaling)
  - Capacity: Variable based on demand
  - SLA: 99% (lower because temporary)
  - Cost: Variable (Pay-as-you-go)

Traffic Distribution:
  - Primary: 70% traffic to on-premises
  - Secondary: 30% traffic to cloud
  - Automatic failover to cloud if on-premises fails
  - DNS-based load balancing (GeoDNS)
```

Implementation:

```bash
# Configure multi-cloud DNS failover
# Route53 health checks (AWS)
aws route53 create-health-check \
  --health-check-config IPAddress=on-prem-ip,Type=HTTP,Port=8080

# Weighted routing policy
aws route53 change-resource-record-sets \
  --hosted-zone-id ZONE_ID \
  --change-batch '{
    "Changes": [
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "yawl.example.com",
          "Type": "A",
          "SetIdentifier": "OnPrem",
          "Weight": 70,
          "TTL": 60,
          "ResourceRecords": [{"Value": "on-prem-ip"}]
        }
      },
      {
        "Action": "CREATE",
        "ResourceRecordSet": {
          "Name": "yawl.example.com",
          "Type": "A",
          "SetIdentifier": "AWS",
          "Weight": 30,
          "TTL": 60,
          "ResourceRecords": [{"Value": "aws-alb-ip"}]
        }
      }
    ]
  }'

# Database replication (on-prem master, cloud read replica)
# PostgreSQL streaming replication
sudo -u postgres psql -c "
  CREATE PUBLICATION yawl_pub FOR ALL TABLES;
  ALTER SYSTEM SET wal_level = logical;
"
```

#### Strategy 2: Cloud-Native Primary with On-Premises Disaster Recovery

**Use Case:** Primarily cloud-deployed with on-premises backup for compliance.

```yaml
Primary Deployment:
  - Location: AWS/GCP/Azure
  - Cluster: 3-10 nodes (auto-scaling)
  - Capacity: 100% of peak load
  - SLA: 99.95%
  - Cost: Variable (Pay-as-you-go)

Disaster Recovery:
  - Location: On-Premises
  - Cluster: 3 nodes (cold standby)
  - Capacity: 10-20% (for DR testing)
  - SLA: 99% (recovery time: 4 hours)
  - Cost: Fixed (CapEx + maintenance)

Replication:
  - Cloud to on-premises daily
  - Database snapshots and archives
  - Annual recovery drills
```

#### Strategy 3: Multi-Region Cloud with On-Premises

**Use Case:** Global deployment with on-premises for local compliance/data residency.

```yaml
Primary Cloud Region:
  - Location: AWS us-east-1
  - Cluster: 5 nodes
  - Role: Global hub

Secondary Cloud Region:
  - Location: AWS eu-west-1
  - Cluster: 5 nodes
  - Role: European operations

On-Premises:
  - Location: Local data center
  - Cluster: 3-5 nodes
  - Role: Local deployment for compliance

Replication:
  - Cross-region database replication
  - Cross-region object storage replication
  - On-premises receives local data only
```

### Network Architecture for Hybrid

#### VPN Configuration

```bash
# AWS Site-to-Site VPN setup
aws ec2 create-customer-gateway \
  --type ipsec.1 \
  --public-ip 203.0.113.1 \
  --bgp-asn 65000

aws ec2 create-vpn-gateway \
  --type ipsec.1 \
  --amazon-side-asn 64512

# On-premises VPN client
sudo ipsec start
# IKEv2 configuration: /etc/ipsec.d/yawl-tunnel.conf

# Azure ExpressRoute setup
az network express-route create \
  --resource-group yawl-rg \
  --name yawl-expressroute \
  --peering-location "Seattle" \
  --provider "Equinix" \
  --bandwidth 100 \
  --sku-tier Premium \
  --sku-family MeteredData
```

### Federated Identity Management

```bash
# Azure AD / Okta configuration for hybrid
# Service Principal authentication across clouds

# 1. Create Azure AD application
az ad app create \
  --display-name yawl-federation

# 2. Configure OIDC in Kubernetes
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-fed
  namespace: yawl
  annotations:
    azure.workload.identity/client-id: CLIENT_ID
---
apiVersion: v1
kind: Pod
metadata:
  name: yawl-pod
  namespace: yawl
  labels:
    azure.workload.identity/use: "true"
spec:
  serviceAccountName: yawl-fed
  containers:
  - name: yawl
    image: yawl:latest
EOF

# 3. Cross-cloud role assumption
aws sts assume-role-with-web-identity \
  --role-arn arn:aws:iam::ACCOUNT_ID:role/yawl-fed \
  --role-session-name yawl-session \
  --web-identity-token $OIDC_TOKEN
```

---

## Cost Comparison

### Annual Cost Analysis

```
┌──────────────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐
│ Platform             │ GCP         │ AWS         │ Azure       │ Oracle      │ On-Prem     │
├──────────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ Compute (annual)     │ $4,320      │ $8,640      │ $4,800      │ $3,600      │ $15,000*    │
│ Database (annual)    │ $5,400      │ $9,600      │ $2,400      │ $4,200      │ $8,000*     │
│ Storage (annual)     │ $600        │ $1,200      │ $240        │ $240        │ $2,000*     │
│ Networking (annual)  │ $240        │ $1,200      │ $300        │ $180        │ $3,000*     │
│ Management (annual)  │ $240        │ $240        │ $240        │ $120        │ $50,000*    │
│ Support (annual)     │ $500        │ $1,000      │ $500        │ $300        │ $5,000*     │
├──────────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ **Total (annual)**   │ **$11,300** │ **$22,180** │ **$8,480**  │ **$8,640**  │ **$83,000** │
│ **Monthly Avg.**     │ **$942**    │ **$1,848**  │ **$707**    │ **$720**    │ **$6,917**  │
├──────────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────┤
│ Commitment Discount  | -10%        | -20% (1yr)  | -20% (1yr)  | -15%        | N/A         │
│ **Final Cost**       │ **$10,170** │ **$17,744** │ **$6,784**  │ **$7,344**  │ **$83,000** │
└──────────────────────┴─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘

* On-premises includes personnel (1.5 FTE @ $150k average = $225k) minus 3-year hardware amortization
```

### Cost Optimization Strategies

#### By Platform

**GCP:**
- Use Committed Use Discounts (10-70% savings)
- Leverage Preemptible VMs for non-critical workloads
- Enable autoscaling to avoid over-provisioning
- Use Cloud Run for burst workloads
- Reserved IP addresses only when necessary

**AWS:**
- Purchase Savings Plans (1-year 20%, 3-year 33%)
- Use Spot Instances for batch workloads
- Implement Lambda for event-driven tasks
- Optimize RDS with Reserved Instances
- Consider AWS Graviton processors (cheaper)

**Azure:**
- Use Azure Hybrid Benefit for SQL Server
- Purchase Reserved Instances (1-year 20%, 3-year 40%)
- Leverage Spot VMs (50-90% discount)
- Use Azure Dev/Test subscription (lower rates)
- Implement auto-shutdown for non-production

**Oracle:**
- Negotiate enterprise agreements
- Use Always Free tier for dev/test
- Implement Oracle Autonomous Database (self-managing)
- Use Compute Optimized instances for YAWL

**On-Premises:**
- Extend hardware lifecycle to 5-7 years
- Negotiate volume discounts on components
- Use open-source software where possible
- Optimize data center cooling (energy efficiency)
- Implement capacity planning to reduce waste

---

## Security Considerations

### Comprehensive Security Matrix

```
┌────────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│ Security Domain        │ GCP          │ AWS          │ Azure        │ Oracle       │ On-Prem      │
├────────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Data Encryption (rest) │ AES-256      │ KMS (AES)    │ TDE/AES-256  │ TDE/AES-256  │ LUKS/AES-256 │
│ Data Encryption (TLS)  │ 1.3          │ 1.3          │ 1.3          │ 1.3          │ 1.3          │
│ IAM/Access Control     │ Cloud IAM    │ IAM          │ Azure AD     │ Vault        │ LDAP/AD      │
│ Secrets Management     │ Secret Mgr   │ Secrets Mgr  │ Key Vault    │ Vault        │ HashiCorp    │
│ Network Security       │ VPC/Firewall │ VPC/SG       │ VNET/NSG     │ Security List│ Firewall/VPN │
│ DDoS Protection        │ Cloud Armor  │ Shield       │ DDoS          │ Basic        │ Self-manage  │
│ Compliance             │ SOC2, ISO27k │ SOC2, ISO27k │ SOC2, ISO27k │ SOC2         │ Self-manage  │
│ Audit Logging          │ Cloud Audit  │ CloudTrail   │ Activity Log  │ Audit Trail  │ System log   │
│ Penetration Testing    │ Allowed*     │ Allowed*     │ Allowed*     │ Allowed*     │ Customer     │
│ Encryption Key Custody │ CSP/Customer │ CSP/Customer │ CSP/Customer │ CSP/Customer │ Customer     │
├────────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Zero Trust Support     │ ✓ (Excellent)│ ✓ (Good)     │ ✓ (Excellent)│ ○ (Basic)    │ ○ (Manual)   │
│ SIEM Integration       │ ✓            │ ✓            │ ✓            │ ✓            │ ✓            │
│ WAF Included           │ Yes (Cloud   │ Yes (WAF)    │ Yes (WAF)    │ No           │ Optional     │
└────────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### Application-Level Security

#### Authentication & Authorization

```yaml
OAuth 2.0 / OIDC Flow:
  - Provider: Okta/Azure AD/Google Identity
  - Token: JWT with HMAC-SHA256 / RS256
  - Scope: Minimal privilege access
  - Refresh: 60-minute access, 7-day refresh

RBAC Implementation:
  - Roles: Admin, Operator, Viewer, Developer
  - Resource-based ACLs on workflows
  - Workflow-level permission matrix
  - Audit trail of all access changes

Multi-Factor Authentication:
  - TOTP (Google Authenticator)
  - Hardware keys (Yubikey)
  - Biometric where supported
  - Backup codes (printed, encrypted)
```

#### Data Protection

```yaml
Data Classification:
  - Public: Workflow definitions (non-sensitive)
  - Internal: Workflow instances, execution logs
  - Confidential: Personal data, credentials
  - Restricted: PII, financial data

Encryption Strategy:
  - Data at rest: AES-256-GCM (authenticated encryption)
  - Data in transit: TLS 1.3 (minimum)
  - Database columns: Column-level encryption for PII
  - File storage: Client-side encryption before upload

Key Management:
  - HSM for root keys (Cloud KMS, AWS KMS, Azure Key Vault)
  - Automatic key rotation (annual minimum)
  - Key version isolation
  - Access logging for all key operations
```

#### Network Security

```yaml
Network Architecture:
  - Private subnets for databases
  - Bastion host for administrative access
  - WAF on public endpoints
  - Network policies enforce pod-to-pod rules

DDoS Mitigation:
  - Cloud DDoS protection (auto-enabled)
  - Rate limiting (100 req/min per IP)
  - Geo-blocking for non-target regions
  - Anycast CDN for distribution

Firewalls:
  - Inbound: HTTPS only (port 443)
  - Outbound: Whitelist required services
  - Database: Private endpoint only
  - Admin: VPN required
```

### Compliance & Governance

#### Required Certifications

```yaml
Standards Alignment:
  - ISO 27001: Information security management
  - SOC 2 Type II: Security, availability, processing integrity
  - HIPAA: If handling health information
  - PCI DSS: If handling payment data
  - GDPR: EU personal data processing
  - CCPA: California privacy requirements

Auditing Requirements:
  - Monthly access reviews
  - Quarterly vulnerability scans
  - Annual penetration testing
  - Annual audit of security controls
  - Real-time alerting for suspicious activity
```

#### Security Implementation Checklist

```yaml
Pre-Deployment:
  □ Security architecture review
  □ Threat modeling
  □ Code security scanning
  □ Container image scanning
  □ Dependency vulnerability check

Deployment:
  □ Enable all audit logging
  □ Configure IAM roles (least privilege)
  □ Enable encryption at rest
  □ Enable encryption in transit (TLS 1.3)
  □ Configure firewall rules
  □ Set up WAF rules
  □ Enable DDoS protection
  □ Configure VPC/private networking
  □ Setup secrets management
  □ Enable monitoring/alerting

Post-Deployment:
  □ Vulnerability scanning (weekly)
  □ Access reviews (monthly)
  □ Security patching (monthly)
  □ Penetration testing (annual)
  □ Disaster recovery testing (quarterly)
  □ Compliance audits (annual)
```

---

## Migration Paths Between Clouds

### Data Migration Strategy

#### Phase 1: Assessment & Planning (2-4 weeks)

```
1. Inventory Assessment
   - Current YAWL deployment size and configuration
   - Data volume and growth rate
   - Dependencies and integrations
   - Performance baselines (latency, throughput)
   - Compliance and regulatory requirements

2. Target Environment Design
   - Cloud selection and justification
   - Architecture and deployment model
   - Sizing and capacity planning
   - High availability and disaster recovery
   - Cost projection and ROI

3. Risk Assessment
   - Downtime tolerance
   - Data loss tolerance (RPO)
   - Availability requirements (RTO)
   - Compatibility testing needs
   - Rollback procedures
```

#### Phase 2: Pre-Migration (2-4 weeks)

```
1. Infrastructure Provisioning
   - Cloud account setup
   - VPC/networking configuration
   - Database provisioning
   - Load balancer configuration
   - Monitoring and logging setup

2. Testing Environment
   - Deploy YAWL in target cloud (non-production)
   - Test all integrations
   - Verify performance (latency, throughput)
   - Security validation
   - Compliance verification

3. Data Migration Tooling
   - Select migration tool (AWS DMS, Azure DMS, native tools)
   - Configure replication rules
   - Set up validation mechanisms
   - Test failure scenarios
   - Document runbooks
```

#### Phase 3: Migration Execution (Variable, typically 24-72 hours)

```
Migration Strategy by Workload:

1. Stateless Components (YAWL application pods)
   - Method: Blue-green deployment
   - Downtime: Near-zero
   - Rollback: Instant DNS revert

2. Database (PostgreSQL/MySQL)
   - Method: Logical replication
   - Downtime: 15-30 minutes (switchover)
   - Rollback: Return to master-slave replication

3. Persistent Data (Documents, archives)
   - Method: Parallel transfer + validation
   - Downtime: 5-15 minutes (final sync)
   - Rollback: Reverse sync to source

4. DNS and Routing
   - Method: Gradual traffic shift (weighted routing)
   - Downtime: 0 (if gradual)
   - Rollback: Revert DNS within 5 minutes

Migration Timeline Example (GCP to AWS):

T-0:00:00  Freeze changes to source system
T+00:15:00 Complete database replication validation
T+00:30:00 Failover database read/write to AWS
T+00:45:00 Health check AWS application pods
T+01:00:00 Shift 10% traffic to AWS (canary)
T+02:00:00 Monitor error rates and performance
T+03:00:00 Shift 50% traffic to AWS (half traffic)
T+04:00:00 Shift 100% traffic to AWS
T+05:00:00 Decommission GCP resources (keep backup 48hrs)
T+24:00:00 Release migration resources
```

#### Phase 4: Post-Migration (1-2 weeks)

```
1. Validation
   - Verify all workflows executing correctly
   - Performance benchmarking
   - Security scanning
   - Compliance verification

2. Optimization
   - Resource right-sizing
   - Cost optimization
   - Performance tuning
   - Caching strategy review

3. Documentation & Training
   - Update runbooks
   - Document new procedures
   - Train ops team
   - Knowledge transfer

4. Decommission Source
   - Backup retention (30-90 days minimum)
   - Decommission VMs/resources
   - Close old accounts (retain billing records)
   - Archive migration documentation
```

### Migration Path Examples

#### GCP to AWS Migration

```bash
#!/bin/bash
# Migration script from GCP to AWS

source /etc/migration-config.sh

echo "Starting GCP to AWS migration..."

# 1. Backup source database
echo "Backing up PostgreSQL from GCP Cloud SQL..."
gcloud sql backups create \
  --instance=$GCP_DB_INSTANCE \
  --description="Pre-migration backup"

# Wait for backup to complete
sleep 120

# 2. Export backup to Cloud Storage
gcloud sql export sql $GCP_DB_INSTANCE \
  gs://$GCP_BACKUP_BUCKET/yawl-pre-migration.sql \
  --database=$GCP_DB_NAME

# 3. Create AWS RDS database
echo "Creating RDS PostgreSQL instance..."
aws rds create-db-instance \
  --db-instance-identifier $AWS_DB_INSTANCE \
  --db-instance-class $AWS_DB_CLASS \
  --engine postgres \
  --master-username $DB_USER \
  --master-user-password $DB_PASSWORD \
  --allocated-storage 100 \
  --multi-az

# Wait for RDS to be available
aws rds wait db-instance-available \
  --db-instance-identifier $AWS_DB_INSTANCE

# 4. Download and restore backup
echo "Downloading backup from GCS..."
gsutil cp gs://$GCP_BACKUP_BUCKET/yawl-pre-migration.sql /tmp/

echo "Restoring to AWS RDS..."
psql -h $AWS_RDS_ENDPOINT \
  -U $DB_USER \
  -d $DB_NAME \
  -f /tmp/yawl-pre-migration.sql

# 5. Validate data
echo "Validating migration..."
psql -h $AWS_RDS_ENDPOINT \
  -U $DB_USER \
  -d $DB_NAME \
  -c "SELECT COUNT(*) FROM workflows;"

# 6. Deploy EKS application
echo "Deploying YAWL to EKS..."
kubectl apply -f eks-deployment.yaml

# 7. Test connectivity
echo "Testing database connectivity..."
curl -X POST http://$EKS_LOAD_BALANCER/resourceService/workflowCount \
  -H "Content-Type: application/json"

echo "Migration completed successfully!"
```

#### Azure to GCP Migration

```bash
#!/bin/bash
# Migration script from Azure to GCP

# 1. Export AKS cluster configuration
az aks export control-plane-logs \
  --resource-group $AZURE_RG \
  --name $AZURE_CLUSTER \
  --logs all \
  --interval-duration 5m \
  --storage-account $BACKUP_STORAGE

# 2. Backup database
az postgres db create-backup \
  --resource-group $AZURE_RG \
  --server-name $AZURE_DB_SERVER \
  --backup-name "pre-migration"

# 3. Export backup
az storage blob download \
  --account-name $BACKUP_STORAGE \
  --container-name backups \
  --name azure-db-backup.sql \
  --file /tmp/azure-db-backup.sql

# 4. Create GCP Cloud SQL instance
gcloud sql instances create $GCP_DB_INSTANCE \
  --database-version=POSTGRES_14 \
  --tier=db-custom-4-16384 \
  --region=$GCP_REGION

# 5. Restore database
gcloud sql import sql $GCP_DB_INSTANCE \
  gs://$GCP_BACKUP_BUCKET/azure-db-backup.sql \
  --database=$DB_NAME

# 6. Create GKE cluster
gcloud container clusters create $GCP_CLUSTER \
  --zone=$GCP_ZONE \
  --num-nodes=3

# 7. Deploy YAWL
kubectl apply -f gke-deployment.yaml

echo "Azure to GCP migration complete!"
```

### Rollback Procedures

```yaml
Rollback Decision Criteria:
  - Error rate > 2% for > 5 minutes
  - Latency increase > 50% for > 10 minutes
  - Database replication lag > 30 seconds
  - Critical workflow failures
  - Compliance violations detected

Rollback Steps (Immediate):
  1. Notify stakeholders (1 minute)
  2. Stop incoming requests (DNS revert) (2 minutes)
  3. Verify source system is healthy (3 minutes)
  4. Reverse database failover to master-slave (5 minutes)
  5. Route traffic back to source (2 minutes)
  6. Verify stability (5 minutes)
  Total: ~18 minutes from decision

Post-Rollback:
  - Capture all logs from failed deployment
  - Root cause analysis
  - System stabilization on source
  - Corrective actions
  - Retry migration with fixes

Automated Rollback Triggers:
  - CloudWatch/GCP Monitoring alarm breach
  - Canary error rate threshold exceeded
  - Database replication lag exceeds threshold
  - Health check failures > 3
```

### Cutover Strategies

#### Blue-Green Deployment (Zero-Downtime)

```yaml
Blue Environment (Source):
  - GCP GKE cluster
  - YAWL v1.0
  - Database master (PostgreSQL)
  - All traffic routed here

Green Environment (Target):
  - AWS EKS cluster
  - YAWL v1.0 (identical version)
  - Database read replica (replication lag < 100ms)
  - No traffic initially

Parallel Testing:
  1. Run synthetic transactions on Green
  2. Compare results with Blue
  3. Verify database replication is current
  4. Monitor Green for 24 hours

Traffic Shift:
  1. Canary: 5% to Green for 30 minutes
  2. Early Adopter: 25% to Green for 2 hours
  3. Ramp Up: 50% to Green for 4 hours
  4. Full Traffic: 100% to Green
  5. Monitor for 72 hours

Rollback Ready:
  - DNS revert takes < 5 minutes
  - Database write operations still go to Blue master
  - Green becomes read-only during initial phase
```

#### Rolling Deployment (Gradual)

```yaml
Phase 1 (20% workload, 4 hours):
  - Deploy YAWL v1.0 on target cloud
  - Validate basic functionality
  - Monitor metrics and errors
  - Success criteria: < 0.1% error rate

Phase 2 (50% workload, 8 hours):
  - Add more YAWL replicas on target
  - Load balance between source and target
  - Monitor database replication lag
  - Success criteria: Latency < +10%

Phase 3 (100% workload, 4 hours):
  - All traffic flows to target cloud
  - Source becomes backup
  - Final validation
  - Keep source active for 72 hours

Phase 4 (Decommission, ongoing):
  - Backup all data (minimum 30 days)
  - Monitor source cloud for issues
  - Gradually reduce monitoring
  - Close accounts after 90 days
```

### Multi-Cloud Management Tools

```yaml
Recommended Tools:

Infrastructure Management:
  - Terraform: Cross-cloud IaC
  - Pulumi: IaC with programming languages
  - CloudFormation + Cloud Deployment Manager
  - ARM templates

Container Orchestration:
  - Kubernetes: Unified across all clouds
  - KubeFlow: ML workflow management
  - Istio: Service mesh for routing

Observability:
  - Prometheus + Grafana: Metrics
  - ELK Stack: Logging
  - Jaeger: Distributed tracing
  - Datadog: Multi-cloud SaaS

CI/CD:
  - Jenkins: Multi-cloud deployment
  - GitLab CI/CD: Integrated
  - GitHub Actions: Cloud-agnostic
  - HashiCorp Vault: Secrets management

Cost Management:
  - CloudHealth by VMware
  - Kubernetes Cost Allocation
  - Cloud Usage Alerts
  - Custom cost tracking dashboards

Configuration Management:
  - Ansible: Multi-cloud provisioning
  - Puppet: Cross-cloud configuration
  - Chef: Infrastructure automation
```

---

## Conclusion

Choosing the right cloud platform for YAWL deployment depends on multiple factors:

- **Cost**: Azure and Oracle offer best pricing; AWS most expensive
- **Kubernetes Maturity**: GCP and AWS have most mature offerings
- **Integration**: Azure for Microsoft stack; AWS for legacy; GCP for Google services
- **On-Premises**: Consider hybrid for compliance or legacy system integration
- **Vendor Lock-in**: Kubernetes and Terraform reduce lock-in

**Recommendation Matrix:**

| Use Case | Recommended | Rationale |
|----------|------------|-----------|
| Startup/Cost-sensitive | Azure or Oracle | Best pricing, good feature set |
| Enterprise, AWS ecosystem | AWS (EKS) | Deep integration, mature tooling |
| Google Cloud ecosystem | GCP (GKE) | Best Kubernetes experience, advanced features |
| Multi-cloud strategy | Hybrid with K8s | Maximum flexibility and portability |
| Compliance/Data residency | On-Premises | Full control, regulatory compliance |
| Burst workloads | GCP (Cloud Run) | Most efficient auto-scaling |
| Regulated industries | Azure | Strong compliance and governance |

For more information on YAWL, visit [https://www.yawlfoundation.org](https://www.yawlfoundation.org)

---

**Document Version:** 1.0
**Last Updated:** February 2026
**Maintainer:** YAWL Foundation
**License:** Creative Commons Attribution 4.0 International
