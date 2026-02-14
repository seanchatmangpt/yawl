# YAWL Workflow Engine - IBM Cloud Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on IBM Cloud through the IBM Cloud Marketplace. Follow these instructions to set up a production-ready, highly available YAWL environment using IBM Kubernetes Service (IKS), Databases for PostgreSQL, and Cloud Object Storage.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Architecture Overview](#2-architecture-overview)
3. [Deployment Steps](#3-deployment-steps)
4. [Post-Deployment Configuration](#4-post-deployment-configuration)
5. [Verification and Testing](#5-verification-and-testing)
6. [Operational Procedures](#6-operational-procedures)
7. [Troubleshooting](#7-troubleshooting)
8. [Cost Estimation](#8-cost-estimation)

---

## 1. Prerequisites

### 1.1 IBM Cloud Account Requirements

| Requirement | Details |
|-------------|---------|
| IBM Cloud Account | Pay-as-you-go or Subscription account in good standing |
| IAM Permissions | Administrator access for Kubernetes Service, Databases, COS |
| Resource Quotas | Sufficient quotas for VPC, IKS, and Databases |
| Region Support | us-south, us-east, eu-de, eu-gb, jp-tok, au-syd |

### 1.2 Required Tools

```bash
# Install IBM Cloud CLI
curl -fsSL https://clis.cloud.ibm.com/install/linux | sh

# Login to IBM Cloud
ibmcloud login

# Install required plugins
ibmcloud plugin install container-service
ibmcloud plugin install container-registry
ibmcloud plugin install cloud-object-storage
ibmcloud plugin install databases

# Target your resource group
ibmcloud target -g Default

# Install kubectl
curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
chmod +x kubectl
sudo mv kubectl /usr/local/bin/

# Install helm
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh

# Install Terraform (optional, for automated deployment)
wget https://releases.hashicorp.com/terraform/1.7.0/terraform_1.7.0_linux_amd64.zip
unzip terraform_1.7.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/
```

### 1.3 Network Requirements

Ensure your VPC has the following:
- At least 3 availability zones for high availability
- Public gateway for NAT connectivity
- Private subnets for worker nodes
- Public subnets for load balancer
- DNS resolution enabled

### 1.4 Container Registry Setup

```bash
# Create IBM Cloud Container Registry namespace
ibmcloud cr namespace-add yawl

# Login to the registry
ibmcloud cr login

# Push YAWL images to your registry
docker tag yawl/engine:5.2 us.icr.io/yawl/engine:5.2
docker push us.icr.io/yawl/engine:5.2

# Repeat for all YAWL services
for service in resource-service worklet-service monitor-service scheduling-service cost-service; do
  docker tag yawl/${service}:5.2 us.icr.io/yawl/${service}:5.2
  docker push us.icr.io/yawl/${service}:5.2
done
```

---

## 2. Architecture Overview

### 2.1 High-Level Architecture

```
                                    +-------------------+
                                    |   IBM Cloud       |
                                    |   Internet        |
                                    |   Services (CIS)  |
                                    |   (CDN/WAF)       |
                                    +--------+----------+
                                             |
                                             | HTTPS
                                             v
+-------------------+                +-------+--------+
|   IBM Cloud       |<---------------| IBM Cloud       |
|   WAF Rules       |                | Load Balancer   |
|   (Protection)    |                | (ALB)           |
+-------------------+                +-------+--------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           |  IKS Node 1     |       |  IKS Node 2     |       |  IKS Node N     |
           |  (AZ-1)         |       |  (AZ-2)         |       |  (AZ-N)         |
           +--------+-------+       +--------+-------+       +--------+-------+
                    |                        |                        |
                    +------------------------+------------------------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           | YAWL Engine     |       | YAWL Resource   |       | YAWL Worklet    |
           | Pod             |       | Service Pod     |       | Service Pod     |
           +--------+-------+       +--------+-------+       +--------+-------+
                    |                        |                        |
                    +------------------------+------------------------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           | Databases for   |       | Cloud Object   |       | Key Protect     |
           | PostgreSQL      |       | Storage (COS)  |       | (Encryption)    |
           | (Multi-AZ)      |       | (Persistence)  |       |                 |
           +-----------------+       +----------------+       +-----------------+
```

### 2.2 Component Responsibilities

| Component | Purpose | Scaling |
|-----------|---------|---------|
| IBM Cloud Load Balancer | SSL termination, traffic distribution | IBM Managed |
| IKS Cluster | Container orchestration | 3-10 nodes (auto-scaling) |
| YAWL Engine | Core workflow execution | 2-5 pods (HPA) |
| YAWL Resource Service | Resource allocation | 2-3 pods |
| YAWL Worklet Service | Dynamic adaptation | 2-3 pods |
| Databases for PostgreSQL | State persistence | 3 members (HA) |
| Cloud Object Storage | File storage, specs, logs | IBM Managed |

### 2.3 IBM Cloud Services Used

| Service | Tier | Purpose |
|---------|------|---------|
| IBM Kubernetes Service (IKS) | Standard | Container orchestration |
| Databases for PostgreSQL | Standard | Managed PostgreSQL |
| Cloud Object Storage | Standard | Object storage |
| IBM Cloud Load Balancer | Application | Layer 7 load balancing |
| Key Protect | Tiered | Key management |
| Secrets Manager | Standard | Secrets management |
| Monitoring (Sysdig) | Graduated | Monitoring |
| Log Analysis | Standard | Centralized logging |
| Activity Tracker | Premium | Audit logging |

---

## 3. Deployment Steps

### 3.1 Step 1: Subscribe via IBM Cloud Marketplace

1. Navigate to IBM Cloud Marketplace
2. Search for "YAWL Workflow Engine"
3. Select the appropriate pricing tier (Basic, Professional, Enterprise, or Unlimited)
4. Configure deployment options (region, admin email)
5. Click "Create" to provision the service instance
6. Wait for provisioning to complete (typically 15-30 minutes)

### 3.2 Step 2: Create VPC and Network Infrastructure (Manual/Advanced)

```bash
# Set environment variables
export IBM_REGION="us-south"
export RESOURCE_GROUP="Default"
export PROJECT_NAME="yawl"
export ENVIRONMENT="prod"

# Create VPC
VPC_ID=$(ibmcloud is vpc-create ${PROJECT_NAME}-${ENVIRONMENT}-vpc --resource-group-name ${RESOURCE_GROUP} --output JSON | jq -r '.id')

# Create subnets in each zone
for zone in 1 2 3; do
  ibmcloud is subnet-create ${PROJECT_NAME}-${ENVIRONMENT}-private-${zone} ${VPC_ID} --zone ${IBM_REGION}-${zone} --ipv4-address-count 256
  ibmcloud is subnet-create ${PROJECT_NAME}-${ENVIRONMENT}-public-${zone} ${VPC_ID} --zone ${IBM_REGION}-${zone} --ipv4-address-count 256
done

# Create public gateway
for zone in 1 2 3; do
  PGW_ID=$(ibmcloud is public-gateway-create ${PROJECT_NAME}-${ENVIRONMENT}-pgw-${zone} ${VPC_ID} --zone ${IBM_REGION}-${zone} --output JSON | jq -r '.id')
  SUBNET_ID=$(ibmcloud is subnets --output JSON | jq -r ".[] | select(.name==\"${PROJECT_NAME}-${ENVIRONMENT}-public-${zone}\") | .id")
  ibmcloud is subnet-public-gateway-update ${SUBNET_ID} ${PGW_ID}
done
```

### 3.3 Step 3: Create IKS Cluster

```bash
# Set variables
export CLUSTER_NAME="yawl-prod-cluster"
export KUBE_VERSION="1.28"
export FLAVOR="bx2.4x16"
export WORKERS="3"

# Get subnet IDs
PRIVATE_SUBNETS=$(ibmcloud is subnets --output JSON | jq -r '.[] | select(.name | contains("private")) | .id' | tr '\n' ',' | sed 's/,$//')

# Create IKS cluster
ibmcloud ks cluster create vpc-gen2 \
  --name ${CLUSTER_NAME} \
  --zone ${IBM_REGION}-1 \
  --vpc-id ${VPC_ID} \
  --subnet-id $(echo ${PRIVATE_SUBNETS} | cut -d',' -f1) \
  --flavor ${FLAVOR} \
  --workers ${WORKERS} \
  --kube-version ${KUBE_VERSION} \
  --resource-group ${RESOURCE_GROUP}

# Add zones for high availability
ibmcloud ks zone add vpc-gen2 \
  --cluster ${CLUSTER_NAME} \
  --zone ${IBM_REGION}-2 \
  --subnet-id $(echo ${PRIVATE_SUBNETS} | cut -d',' -f2)

ibmcloud ks zone add vpc-gen2 \
  --cluster ${CLUSTER_NAME} \
  --zone ${IBM_REGION}-3 \
  --subnet-id $(echo ${PRIVATE_SUBNETS} | cut -d',' -f3)

# Wait for cluster to be normal (takes 15-30 minutes)
ibmcloud ks cluster get --cluster ${CLUSTER_NAME}

# Download kubeconfig
ibmcloud ks cluster config --cluster ${CLUSTER_NAME}

# Verify cluster
kubectl get nodes
```

### 3.4 Step 4: Create Databases for PostgreSQL

```bash
# Create Databases for PostgreSQL instance
ibmcloud resource service-instance-create \
  ${PROJECT_NAME}-${ENVIRONMENT}-postgresql \
  databases-for-postgresql \
  standard \
  ${IBM_REGION} \
  --resource-group-name ${RESOURCE_GROUP} \
  -p '{"members_memory_allocation_mb": 4096, "members_disk_allocation_mb": 102400}'

# Get instance ID
DB_INSTANCE_ID=$(ibmcloud resource service-instance ${PROJECT_NAME}-${ENVIRONMENT}-postgresql --output JSON | jq -r '.[0].id')

# Create database (or use CLI)
ibmcloud cdb deployment-create ${PROJECT_NAME}-${ENVIRONMENT}-db \
  --service-instance-id ${DB_INSTANCE_ID} \
  --admin-password $(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)

# Get connection information
ibmcloud cdb deployment-connections ${PROJECT_NAME}-${ENVIRONMENT}-db \
  --service-instance-id ${DB_INSTANCE_ID} \
  --type postgresql \
  --endpoint-type private
```

### 3.5 Step 5: Create Cloud Object Storage

```bash
# Create COS instance
ibmcloud resource service-instance-create \
  ${PROJECT_NAME}-${ENVIRONMENT}-cos \
  cloud-object-storage \
  standard \
  global \
  --resource-group-name ${RESOURCE_GROUP}

# Get instance CRN
COS_CRN=$(ibmcloud resource service-instance ${PROJECT_NAME}-${ENVIRONMENT}-cos --output JSON | jq -r '.[0].id')

# Create buckets using COS API or console
# Note: COS buckets must be created via console or API
echo "Create the following buckets in the IBM Cloud Console:"
echo "1. ${PROJECT_NAME}-${ENVIRONMENT}-data"
echo "2. ${PROJECT_NAME}-${ENVIRONMENT}-logs"
echo "3. ${PROJECT_NAME}-${ENVIRONMENT}-backups"
```

### 3.6 Step 6: Create IBM Cloud Load Balancer

```bash
# Create ALB for IKS (automatic with IKS)
# Verify ALB status
ibmcloud ks alb ls --cluster ${CLUSTER_NAME}

# Enable default ALB
ibmcloud ks alb enable --alb public-cr0123456789abcdef-alb1 --cluster ${CLUSTER_NAME}

# Create Ingress TLS secret with IBM Certificate Manager
# (Or use Let's Encrypt via cert-manager)
```

### 3.7 Step 7: Install IBM Cloud Operator (Optional)

```bash
# Install IBM Cloud Operator for service binding
kubectl apply -f https://raw.githubusercontent.com/IBM/cloud-operator/master/deploy/releases/0.2.0/ibm_cloud_operator.yaml

# Create service binding for PostgreSQL
cat <<EOF | kubectl apply -f -
apiVersion: ibmcloud.ibm.com/v1alpha1
kind: Binding
metadata:
  name: yawl-db-binding
  namespace: yawl
spec:
  serviceName: ${PROJECT_NAME}-${ENVIRONMENT}-postgresql
  serviceInstanceId: ${DB_INSTANCE_ID}
  secretName: yawl-db-credentials
EOF
```

### 3.8 Step 8: Deploy YAWL Application

```bash
# Create YAWL namespace
kubectl create namespace yawl

# Create secrets (if not using IBM Cloud Operator)
kubectl create secret generic yawl-db-credentials \
  --from-literal=username=yawl_admin \
  --from-literal=password=${DB_PASSWORD} \
  --from-literal=host=${DB_HOST} \
  --from-literal=port=5432 \
  --namespace=yawl

# Create ConfigMap
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  DATABASE_HOST: "${DB_HOST}"
  DATABASE_PORT: "5432"
  DATABASE_NAME: "yawl"
  LOG_LEVEL: "INFO"
  JAVA_OPTS: "-Xms1g -Xmx2g"
  IBM_REGION: "${IBM_REGION}"
EOF

# Add YAWL Helm repository
helm repo add yawl https://yawlfoundation.github.io/yawl/helm
helm repo update

# Deploy YAWL using Helm
helm install yawl yawl/yawl \
  --namespace yawl \
  --set image.repository=us.icr.io/yawl/engine \
  --set image.tag=5.2.0 \
  --set replicaCount=2 \
  --set resources.requests.cpu=500m \
  --set resources.requests.memory=1Gi \
  --set resources.limits.cpu=2000m \
  --set resources.limits.memory=4Gi \
  --set ingress.enabled=true \
  --set ingress.className=public-iks-k8s-nginx \
  --set ingress.hosts[0].host=yawl.yourcompany.com \
  --set ingress.hosts[0].paths[0].path=/yawl \
  --set ingress.hosts[0].paths[0].pathType=Prefix \
  --set ingress.tls[0].secretName=yawl-tls \
  --set ingress.tls[0].hosts[0]=yawl.yourcompany.com

# Verify deployment
kubectl get pods -n yawl
kubectl get services -n yawl
kubectl get ingress -n yawl
```

---

## 4. Post-Deployment Configuration

### 4.1 Configure Autoscaling

```bash
# Install Metrics Server (if not already installed)
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

# Create Horizontal Pod Autoscaler
kubectl autoscale deployment yawl-engine \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n yawl

# Verify HPA
kubectl get hpa -n yawl
```

### 4.2 Configure IBM Cloud Monitoring

```bash
# Create Sysdig agent daemonset
# Get Sysdig access key from IBM Cloud Monitoring instance
MONITORING_KEY=$(ibmcloud resource service-key ${PROJECT_NAME}-monitoring-key --output JSON | jq -r '.[0].credentials.SysdigAccessKey')

# Deploy Sysdig agent
kubectl create namespace sysdig-agent
kubectl create secret generic sysdig-agent --from-literal=access-key=${MONITORING_KEY} -n sysdig-agent

# Apply Sysdig agent daemonset
kubectl apply -f https://raw.githubusercontent.com/draios/sysdig-cloud-scripts/master/agent_deploy/kubernetes/ibm/ibm-ks-sysdig-agent.yaml
```

### 4.3 Configure Log Analysis

```bash
# Create Log Analysis secret
LOG_SECRET=$(ibmcloud resource service-key ${PROJECT_NAME}-logs-key --output JSON | jq -r '.[0].credentials.logging_token')
REGION_TOKEN=$(ibmcloud resource service-key ${PROJECT_NAME}-logs-key --output JSON | jq -r '.[0].credentials.logging_host')

# Deploy Fluentd to send logs
kubectl create namespace ibm-observe
kubectl create secret generic logging-secret \
  --from-literal=loging-token=${LOG_SECRET} \
  --from-literal=region-token=${REGION_TOKEN} \
  -n ibm-observe

# Apply Fluentd daemonset
kubectl apply -f https://raw.githubusercontent.com/IBM-Cloud/kube-logging/master/kube-fluentd/iks/kube-fluentd.yaml
```

### 4.4 Initialize YAWL Database

```bash
# Get YAWL engine pod
YAWL_POD=$(kubectl get pods -n yawl -l app=yawl-engine -o jsonpath='{.items[0].metadata.name}')

# Run database initialization
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/init-database.sh

# Verify database
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/check-database.sh
```

### 4.5 Configure COS for Persistence

```bash
# Create COS directory structure
COS_BUCKET="${PROJECT_NAME}-${ENVIRONMENT}-data"
COS_ENDPOINT="s3.${IBM_REGION}.cloud-object-storage.appdomain.cloud"

# Use IBM Cloud COS CLI or console to create:
# - specifications/
# - logs/
# - exports/
# - worklets/

# Update YAWL configuration
kubectl set env deployment/yawl-engine \
  YAWL_COS_BUCKET=${COS_BUCKET} \
  YAWL_COS_ENDPOINT=${COS_ENDPOINT} \
  -n yawl
```

---

## 5. Verification and Testing

### 5.1 Health Check Verification

```bash
# Get ALB hostname
ALB_HOST=$(kubectl get ingress -n yawl yawl -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# Test health endpoint
curl -k https://${ALB_HOST}/yawl/health

# Expected response
# {"status":"healthy","version":"5.2.0","timestamp":"2025-01-15T10:00:00Z"}
```

### 5.2 API Endpoint Verification

```bash
# Test API Interface B (Client)
curl -k -X GET "https://${ALB_HOST}/yawl/ws/interfaceB/1.0" \
  -H "Content-Type: application/xml"

# Test Engine Status
curl -k -X GET "https://${ALB_HOST}/yawl/engine/status"
```

### 5.3 Database Connectivity Test

```bash
# Test connection from pod
kubectl run pg-test --rm -it --image=postgres:15 --restart=Never -n yawl -- \
  psql "postgresql://${DB_USERNAME}:${DB_PASSWORD}@${DB_HOST}:5432/yawl" -c "SELECT version();"
```

### 5.4 Workflow Execution Test

```bash
# Upload test specification
kubectl exec -n yawl ${YAWL_POD} -- \
  curl -X POST "http://localhost:8080/yawl/ws/interfaceA/1.0" \
  -H "Content-Type: application/xml" \
  -d @/opt/yawl/samples/OrderFulfillment.yawl

# Launch test case
kubectl exec -n yawl ${YAWL_POD} -- \
  curl -X POST "http://localhost:8080/yawl/ws/interfaceB/1.0" \
  -H "Content-Type: application/xml" \
  -d '<launchCase xmlns="http://www.yawlfoundation.org/yawl"><specID>OrderFulfillment</specID></launchCase>'
```

---

## 6. Operational Procedures

### 6.1 Scaling the Cluster

```bash
# Scale worker pool
ibmcloud ks worker-pool resize \
  --cluster ${CLUSTER_NAME} \
  --worker-pool default \
  --size-per-zone 5

# Scale YAWL pods
kubectl scale deployment yawl-engine --replicas=5 -n yawl
```

### 6.2 Backup Procedures

```bash
# Create PostgreSQL backup
ibmcloud cdb deployment-backup ${PROJECT_NAME}-${ENVIRONMENT}-db \
  --service-instance-id ${DB_INSTANCE_ID}

# Backup COS data
# Use rclone or IBM Cloud CLI to sync
ibmcloud cos object-copy \
  --bucket ${COS_BUCKET} \
  --key specifications/ \
  --target-bucket ${COS_BUCKET}-backup/$(date +%Y%m%d)/
```

### 6.3 Log Collection

```bash
# Collect application logs
kubectl logs -n yawl -l app=yawl-engine --since=1h > yawl-engine-logs.txt

# View Log Analysis logs
# Access via IBM Cloud Console:
# https://cloud.ibm.com/observe/logging
```

### 6.4 Monitoring Dashboard

Access Sysdig dashboard:
```
https://cloud.ibm.com/observe/monitoring
```

---

## 7. Troubleshooting

### 7.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| Pods not starting | ImagePullBackOff | Check ICR permissions, verify image exists |
| Database connection failed | Connection refused | Verify security groups, check credentials |
| ALB health checks failing | 502 errors | Check pod health, verify target registration |
| High latency | Slow API responses | Check pod resources, verify PostgreSQL performance |
| COS access denied | 403 Forbidden | Verify IAM policies, check COS credentials |

### 7.2 Diagnostic Commands

```bash
# Check pod status
kubectl describe pod -n yawl -l app=yawl-engine

# Check pod logs
kubectl logs -n yawl -l app=yawl-engine --tail=100

# Check events
kubectl get events -n yawl --sort-by='.lastTimestamp'

# Check cluster status
ibmcloud ks cluster get --cluster ${CLUSTER_NAME}

# Check worker nodes
ibmcloud ks workers --cluster ${CLUSTER_NAME}

# Check ALB status
ibmcloud ks alb ls --cluster ${CLUSTER_NAME}

# Check database status
ibmcloud cdb deployment-show ${PROJECT_NAME}-${ENVIRONMENT}-db \
  --service-instance-id ${DB_INSTANCE_ID}
```

### 7.3 Support Contacts

- **Technical Support**: support@yawlfoundation.org
- **IBM Cloud Support**: https://cloud.ibm.com/unifiedsupport/supportcenter
- **IBM Cloud Marketplace**: cloudmarketplace@us.ibm.com
- **Documentation**: https://yawlfoundation.github.io/yawl/

---

## 8. Cost Estimation

### 8.1 Monthly Cost Breakdown (Production)

| Component | Configuration | Estimated Monthly Cost |
|-----------|--------------|----------------------|
| IKS Cluster | 1 control plane | $0 (included) |
| IKS Workers | 3 x bx2.4x16 | $312.00 |
| Databases for PostgreSQL | Standard, 3 members | $450.00 |
| Cloud Object Storage | 100 GB | $5.00 |
| IBM Cloud Load Balancer | Application | $45.00 |
| Key Protect | Tiered | $10.00 |
| Monitoring (Sysdig) | Graduated tier | $75.00 |
| Log Analysis | Standard | $50.00 |
| Data Transfer | 500 GB | $45.00 |
| **Total Infrastructure** | | **~$992/month** |
| **YAWL License (Professional)** | | **$999/month** |
| **Total** | | **~$1,991/month** |

### 8.2 Cost Optimization Tips

1. Use reserved capacity for steady-state workloads
2. Enable COS Intelligent Tiering for log storage
3. Right-size instances based on actual usage
4. Use Spot instances for non-critical workloads
5. Monitor costs with IBM Cloud Cost Management
6. Consider annual commitment for 17% discount

---

## Appendix A: Quick Reference Commands

```bash
# Get cluster kubeconfig
ibmcloud ks cluster config --cluster ${CLUSTER_NAME}

# View all YAWL resources
kubectl get all -n yawl

# Port forward for local testing
kubectl port-forward -n yawl svc/yawl-engine 8080:8080

# Emergency scale down
kubectl scale deployment -n yawl --replicas=1 --all

# Emergency scale up
kubectl scale deployment -n yawl --replicas=5 yawl-engine

# View cluster status
ibmcloud ks cluster get --cluster ${CLUSTER_NAME}

# View database connections
ibmcloud cdb deployment-connections ${PROJECT_NAME}-${ENVIRONMENT}-db

# Check COS bucket contents
ibmcloud cos bucket-list
```

---

## Appendix B: Terraform Deployment (Alternative)

```bash
# Clone the marketplace repository
git clone https://github.com/yawlfoundation/yawl-marketplace.git
cd yawl-marketplace/ibm/terraform

# Create terraform.tfvars
cat > terraform.tfvars <<EOF
ibm_api_key       = "your-ibm-cloud-api-key"
ibm_region        = "us-south"
ibm_resource_group = "Default"
project_name      = "yawl"
environment       = "prod"
database_password = "$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)"
EOF

# Initialize Terraform
terraform init

# Plan deployment
terraform plan -out=tfplan

# Apply deployment
terraform apply tfplan
```

---

*Document Version: 1.0*
*Last Updated: February 2025*
