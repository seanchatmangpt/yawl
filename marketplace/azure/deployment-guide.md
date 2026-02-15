# YAWL Workflow Engine - Azure Marketplace Deployment Guide

## Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on Azure through the Azure Marketplace. Follow these instructions to set up a production-ready, highly available YAWL environment on Azure Kubernetes Service (AKS).

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Architecture Overview](#2-architecture-overview)
3. [Deployment Steps](#3-deployment-steps)
4. [Post-Deployment Configuration](#4-post-configuration)
5. [Verification and Testing](#5-verification-and-testing)
6. [Operational Procedures](#6-operational-procedures)
7. [Troubleshooting](#7-troubleshooting)
8. [Cost Estimation](#8-cost-estimation)

---

## 1. Prerequisites

### 1.1 Azure Account Requirements

| Requirement | Details |
|-------------|---------|
| Azure Subscription | Active subscription with appropriate permissions |
| Azure AD Access | Global Administrator or Application Administrator for AAD setup |
| Service Principal | Contributor role on the subscription |
| Resource Quotas | Sufficient quotas for AKS, PostgreSQL, and Storage |
| Region Support | East US, West US 2, West Europe, North Europe, Southeast Asia, Japan East |

### 1.2 Required Tools

```bash
# Install Azure CLI
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Login to Azure
az login

# Set default subscription
az account set --subscription <subscription-id>

# Install kubectl
az aks install-cli

# Install Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Verify installations
az --version
kubectl version --client
helm version
```

### 1.3 Required Azure Resource Providers

```bash
# Register required resource providers
az provider register --namespace Microsoft.ContainerService
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.Storage
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.KeyVault
az provider register --namespace Microsoft.ContainerRegistry
az provider register --namespace Microsoft.Insights
az provider register --namespace Microsoft.OperationalInsights
az provider register --namespace Microsoft.ManagedIdentity

# Verify registration
az provider list --query "[?contains(['Microsoft.ContainerService', 'Microsoft.DBforPostgreSQL', 'Microsoft.Storage'], namespace)]" -o table
```

### 1.4 Network Requirements

- VNet with sufficient IP address space (/16 minimum recommended)
- At least 2 subnets in different availability zones
- Network Security Groups configured
- DNS resolution enabled

---

## 2. Architecture Overview

### 2.1 High-Level Architecture

```
                                    +-------------------+
                                    |   Azure Front     |
                                    |   Door / CDN      |
                                    |   (Optional)      |
                                    +--------+----------+
                                             |
                                             | HTTPS
                                             v
+-------------------+                +-------+--------+
|   Azure WAF       |<---------------| Application     |
|   (Protection)    |                | Gateway         |
+-------------------+                +-------+--------+
                                             |
                    +------------------------+------------------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           |  AKS Node 1     |       |  AKS Node 2     |       |  AKS Node N     |
           |  (Zone 1)       |       |  (Zone 2)       |       |  (Zone 3)       |
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
           | Private         |       | Azure DB for    |       | Azure Blob     |
           | Endpoint        |       | PostgreSQL      |       | Storage        |
           +-----------------+       +-----------------+       +----------------+
                    |                        |                        |
                    v                        v                        v
           +--------+-------+       +--------+-------+       +--------+-------+
           | Azure Key       |       | Azure Monitor   |       | Container      |
           | Vault           |       | / Log Analytics |       | Registry       |
           +-----------------+       +-----------------+       +----------------+
```

### 2.2 Component Responsibilities

| Component | Purpose | Scaling |
|-----------|---------|---------|
| Application Gateway | WAF, SSL termination, routing | Azure Managed |
| AKS Cluster | Container orchestration | 2-10 nodes (auto-scaling) |
| YAWL Engine | Core workflow execution | 2-5 pods (HPA) |
| YAWL Resource Service | Resource allocation | 2-3 pods |
| YAWL Worklet Service | Dynamic adaptation | 2-3 pods |
| Azure DB for PostgreSQL | State persistence | Zone-redundant |
| Azure Blob Storage | File storage, specs, logs | RA-GRS |
| Azure Key Vault | Secrets management | Azure Managed |

---

## 3. Deployment Steps

### 3.1 Step 1: Subscribe via Azure Marketplace

1. Navigate to Azure Marketplace
2. Search for "YAWL Workflow Engine"
3. Select the appropriate plan (Basic, Professional, or Enterprise)
4. Click "Get it now"
5. You will be redirected to the Azure portal deployment page

### 3.2 Step 2: Create Resource Group

```bash
# Set environment variables
export LOCATION="eastus"
export RESOURCE_GROUP="yawl-production"
export DEPLOYMENT_NAME="yawl-deployment-$(date +%Y%m%d%H%M%S)"

# Create resource group
az group create \
  --name ${RESOURCE_GROUP} \
  --location ${LOCATION}
```

### 3.3 Step 3: Deploy Using ARM Template

```bash
# Deploy main template (Azure Portal does this automatically)
az deployment group create \
  --name ${DEPLOYMENT_NAME} \
  --resource-group ${RESOURCE_GROUP} \
  --template-file arm-templates/main.json \
  --parameters \
      location=${LOCATION} \
      aksClusterName=yawl-aks \
      aksNodeCount=3 \
      aksNodeSize=Standard_D4s_v3 \
      postgresqlServerName=yawl-postgres \
      postgresqlAdminLogin=yawladmin \
      postgresqlAdminPassword=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24) \
      storageAccountName=yawlstorage${RANDOM} \
      keyVaultName=yawl-kv-${RANDOM} \
      acrName=yawlacr${RANDOM} \
      adminEmail=admin@yourcompany.com

# Wait for deployment to complete (15-30 minutes)
az deployment group wait \
  --name ${DEPLOYMENT_NAME} \
  --resource-group ${RESOURCE_GROUP} \
  --created
```

### 3.4 Step 4: Get AKS Credentials

```bash
# Get AKS cluster credentials
az aks get-credentials \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-aks \
  --overwrite-existing

# Verify cluster connection
kubectl get nodes
kubectl get namespaces
```

### 3.5 Step 5: Configure Azure AD Workload Identity

```bash
# Get the managed identity object ID
IDENTITY_OBJECT_ID=$(az identity show \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-app-identity \
  --query principalId -o tsv)

# Create Kubernetes service account with Azure AD annotation
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: yawl-engine-sa
  namespace: yawl
  annotations:
    azure.workload.identity/client-id: $(az identity show --resource-group ${RESOURCE_GROUP} --name yawl-app-identity --query clientId -o tsv)
EOF
```

### 3.6 Step 6: Deploy YAWL Application

```bash
# Create namespace
kubectl create namespace yawl

# Get Key Vault URI
KEYVAULT_URI=$(az keyvault show \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-kv-${RANDOM} \
  --query properties.vaultUri -o tsv)

# Get PostgreSQL connection string from Key Vault
DB_CONNECTION_STRING=$(az keyvault secret show \
  --vault-name yawl-kv-${RANDOM} \
  --name DatabaseConnectionString \
  --query value -o tsv)

# Create Kubernetes secrets
kubectl create secret generic yawl-db-credentials \
  --from-literal=connection-string="${DB_CONNECTION_STRING}" \
  --namespace=yawl

# Add YAWL Helm repository
helm repo add yawl https://yawlfoundation.github.io/yawl/helm
helm repo update

# Deploy YAWL using Helm
helm install yawl yawl/yawl \
  --namespace yawl \
  --set image.repository=yawlfoundation.azurecr.io/yawl \
  --set image.tag=5.2.0 \
  --set replicaCount=2 \
  --set resources.requests.cpu=500m \
  --set resources.requests.memory=1Gi \
  --set resources.limits.cpu=2000m \
  --set resources.limits.memory=4Gi \
  --set serviceAccount.create=false \
  --set serviceAccount.name=yawl-engine-sa \
  --set azure.keyvault.enabled=true \
  --set azure.keyvault.uri=${KEYVAULT_URI} \
  --set azure.managedIdentity.enabled=true \
  --set ingress.enabled=true \
  --set ingress.className=azure-application-gateway \
  --set ingress.annotations."appgw\.ingress\.kubernetes\.io/ssl-redirect"=true

# Verify deployment
kubectl get pods -n yawl
kubectl get services -n yawl
kubectl get ingress -n yawl
```

### 3.7 Step 7: Configure Application Gateway

```bash
# Get Application Gateway public IP
APP_GW_IP=$(az network public-ip show \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-appgw-pip \
  --query ipAddress -o tsv)

# Create DNS record (optional)
az network dns record-set a add-record \
  --resource-group ${RESOURCE_GROUP} \
  --zone-name yourcompany.com \
  --record-set-name yawl \
  --ipv4-address ${APP_GW_IP}
```

---

## 4. Post-Deployment Configuration

### 4.1 Configure Autoscaling

```bash
# Install Kubernetes Metrics Server (if not installed)
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

### 4.2 Configure Azure Monitor for Containers

```bash
# Get Log Analytics Workspace ID
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group ${RESOURCE_GROUP} \
  --workspace-name yawl-logs \
  --query customerId -o tsv)

# Enable Azure Monitor for containers (should be enabled by ARM template)
# Verify by checking the insightmetrics table
az monitor log-analytics query \
  --workspace ${WORKSPACE_ID} \
  --analytics-query "InsightMetrics | summarize count() by Origin" \
  --output table
```

### 4.3 Initialize YAWL Database

```bash
# Get YAWL engine pod
YAWL_POD=$(kubectl get pods -n yawl -l app=yawl-engine -o jsonpath='{.items[0].metadata.name}')

# Run database initialization
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/init-database.sh

# Verify database connection
kubectl exec -n yawl ${YAWL_POD} -- /opt/yawl/bin/check-database.sh
```

### 4.4 Configure Azure Blob Storage

```bash
# Get storage account name
STORAGE_ACCOUNT=$(az storage account list \
  --resource-group ${RESOURCE_GROUP} \
  --query "[?contains(name, 'yawl')].name" -o tsv)

# Create container structure
az storage container create --name specifications --account-name ${STORAGE_ACCOUNT}
az storage container create --name logs --account-name ${STORAGE_ACCOUNT}
az storage container create --name exports --account-name ${STORAGE_ACCOUNT}
az storage container create --name worklets --account-name ${STORAGE_ACCOUNT}

# Update YAWL configuration
kubectl set env deployment/yawl-engine \
  YAWL_STORAGE_ACCOUNT=${STORAGE_ACCOUNT} \
  YAWL_STORAGE_CONTAINER=specifications \
  -n yawl
```

### 4.5 Configure Azure AD Authentication (Optional)

```bash
# Create Azure AD application for YAWL
APP_ID=$(az ad app create \
  --display-name "YAWL Workflow Engine" \
  --identifier-uris "api://yawl-${RANDOM}" \
  --query appId -o tsv)

# Create service principal
az ad sp create --id ${APP_ID}

# Configure redirect URIs
az ad app update \
  --id ${APP_ID} \
  --reply-urls "https://yawl.yourcompany.com/auth/callback"

# Update YAWL with Azure AD configuration
kubectl set env deployment/yawl-engine \
  AZURE_AD_ENABLED=true \
  AZURE_AD_CLIENT_ID=${APP_ID} \
  AZURE_AD_TENANT_ID=$(az account show --query tenantId -o tsv) \
  -n yawl
```

---

## 5. Verification and Testing

### 5.1 Health Check Verification

```bash
# Get Application Gateway endpoint
ENDPOINT="https://yawl.yourcompany.com"

# Test health endpoint
curl -k ${ENDPOINT}/yawl/health

# Expected response
# {"status":"healthy","version":"5.2.0","timestamp":"2025-01-15T10:00:00Z"}
```

### 5.2 API Endpoint Verification

```bash
# Test API Interface B (Client)
curl -k -X GET "${ENDPOINT}/yawl/ws/interfaceB/1.0" \
  -H "Content-Type: application/xml"

# Test Engine Status
curl -k -X GET "${ENDPOINT}/yawl/engine/status"
```

### 5.3 Database Connectivity Test

```bash
# Test connection from pod
kubectl run pg-test --rm -it --image=postgres:15 --restart=Never -n yawl -- \
  psql "${DB_CONNECTION_STRING}" -c "SELECT version();"
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

### 5.5 Azure Monitor Verification

```bash
# Query container logs
az monitor log-analytics query \
  --workspace ${WORKSPACE_ID} \
  --analytics-query "ContainerLog | where Name contains 'yawl' | take 10" \
  --output table

# Query performance metrics
az monitor log-analytics query \
  --workspace ${WORKSPACE_ID} \
  --analytics-query "Perf | where ObjectName == 'K8SContainer' | where InstanceName contains 'yawl' | take 10" \
  --output table
```

---

## 6. Operational Procedures

### 6.1 Scaling the Cluster

```bash
# Scale AKS node pool
az aks scale \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-aks \
  --node-count 5 \
  --nodepool-name nodepool1

# Scale YAWL pods
kubectl scale deployment yawl-engine --replicas=5 -n yawl
```

### 6.2 Backup Procedures

```bash
# Create PostgreSQL backup
az postgres flexible-server geo-backup create \
  --resource-group ${RESOURCE_GROUP} \
  --server-name yawl-postgres \
  --backup-name yawl-backup-$(date +%Y%m%d)

# Backup blob storage
az storage blob download-batch \
  --destination ./backup/ \
  --source specifications \
  --account-name ${STORAGE_ACCOUNT}

# Backup Kubernetes resources
kubectl get all -n yawl -o yaml > yawl-k8s-backup.yaml
```

### 6.3 Log Collection

```bash
# Collect application logs
kubectl logs -n yawl -l app=yawl-engine --since=1h > yawl-engine-logs.txt

# Export logs from Log Analytics
az monitor log-analytics query \
  --workspace ${WORKSPACE_ID} \
  --analytics-query "ContainerLog | where Name contains 'yawl' | where TimeGenerated > ago(1h)" \
  --output json > yawl-logs.json
```

### 6.4 Monitoring Dashboard

Access Azure Monitor dashboard:
```
https://portal.azure.com/#@{tenant}/resource/subscriptions/{subscription}/resourceGroups/${RESOURCE_GROUP}/providers/Microsoft.OperationsManagement/solutions/ContainerInsights
```

---

## 7. Troubleshooting

### 7.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| Pods not starting | ImagePullBackOff | Check ACR permissions, verify image exists |
| Database connection failed | Connection refused | Verify private endpoint, check credentials in Key Vault |
| Application Gateway 502 | Backend unreachable | Check pod health, verify ingress annotations |
| High latency | Slow API responses | Check pod resources, verify PostgreSQL performance |
| AD authentication fails | 401 errors | Verify Azure AD app registration, check redirect URIs |

### 7.2 Diagnostic Commands

```bash
# Check pod status
kubectl describe pod -n yawl -l app=yawl-engine

# Check pod logs
kubectl logs -n yawl -l app=yawl-engine --tail=100

# Check events
kubectl get events -n yawl --sort-by='.lastTimestamp'

# Check AKS cluster health
az aks show \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-aks \
  --query "{provisioningState: provisioningState, powerState: powerState}"

# Check PostgreSQL status
az postgres flexible-server show \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-postgres

# Check Key Vault access
az keyvault secret list --vault-name yawl-kv-${RANDOM}
```

### 7.3 Support Contacts

- **Technical Support**: support@yawlfoundation.org
- **Azure Marketplace Support**: https://portal.azure.com/#blade/Microsoft_Azure_Support/HelpAndSupportBlade
- **Documentation**: https://yawlfoundation.github.io/yawl/

---

## 8. Cost Estimation

### 8.1 Monthly Cost Breakdown (Professional Tier)

| Component | Configuration | Estimated Monthly Cost |
|-----------|--------------|----------------------|
| AKS Cluster | Control plane | $73.00 |
| AKS Nodes | 3 x Standard_D4s_v3 | $420.00 |
| Azure DB for PostgreSQL | Standard_D4s_v3, Zone-redundant | $360.00 |
| Azure Blob Storage | 100 GB RA-GRS | $5.00 |
| Application Gateway | WAF v2 | $300.00 |
| Key Vault | Standard | $5.00 |
| Container Registry | Premium | $25.00 |
| Log Analytics | 10 GB | $25.00 |
| Data Transfer | 500 GB | $45.00 |
| **Total Infrastructure** | | **~$1,258/month** |
| **YAWL License** | Professional | **$999/month** |
| **Total** | | **~$2,257/month** |

### 8.2 Cost Optimization Tips

1. Use Azure Reserved VM Instances for steady-state workloads (up to 40% savings)
2. Enable auto-scaling to reduce costs during low usage
3. Use Cool tier for log archival
4. Right-size instances based on actual usage
5. Use Azure Cost Management for tracking

---

## Appendix A: Quick Reference Commands

```bash
# Get all resources
az resource list --resource-group ${RESOURCE_GROUP} --output table

# Update kubeconfig
az aks get-credentials --resource-group ${RESOURCE_GROUP} --name yawl-aks

# View all YAWL resources
kubectl get all -n yawl

# Port forward for local testing
kubectl port-forward -n yawl svc/yawl-engine 8080:8080

# Emergency scale down
kubectl scale deployment -n yawl --replicas=1 --all

# Emergency scale up
kubectl scale deployment -n yawl --replicas=5 yawl-engine

# Check Azure resource health
az network application-gateway show-health \
  --resource-group ${RESOURCE_GROUP} \
  --name yawl-appgw
```

---

## Appendix B: ARM Template Parameters

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| location | Azure region | resourceGroup().location | No |
| aksClusterName | AKS cluster name | yawl-aks | Yes |
| aksNodeCount | Number of AKS nodes | 3 | No |
| aksNodeSize | AKS node VM size | Standard_D4s_v3 | No |
| postgresqlServerName | PostgreSQL server name | yawl-postgres | Yes |
| postgresqlAdminLogin | PostgreSQL admin username | yawladmin | Yes |
| postgresqlAdminPassword | PostgreSQL admin password | (generated) | Yes |
| storageAccountName | Storage account name | (generated) | Yes |
| keyVaultName | Key Vault name | (generated) | Yes |
| acrName | Container registry name | (generated) | Yes |
| adminEmail | Administrator email | (required) | Yes |

---

*Document Version: 1.0*
*Last Updated: February 2025*
