# Azure Deployment Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

Step-by-step guide for deploying YAWL on Microsoft Azure using AKS, ARM templates, and Terraform.

---

## 2. Prerequisites

```bash
# Verify Azure CLI
az --version

# Verify kubectl
kubectl version --client

# Verify Helm
helm version

# Login to Azure
az login

# Set subscription
az account set --subscription SUBSCRIPTION_ID
```

---

## 3. Quick Start: Azure Marketplace

1. Go to [Azure Marketplace](https://azuremarketplace.microsoft.com)
2. Search "YAWL Workflow Engine"
3. Click "Get it now"
4. Create new resource group
5. Configure settings
6. Review and create

---

## 4. Manual Deployment: AKS

### 4.1 Create Resource Group

```bash
az group create --name yawl-rg --location eastus
```

### 4.2 Create AKS Cluster

```bash
az aks create \
  --resource-group yawl-rg \
  --name yawl-aks \
  --node-count 3 \
  --node-vm-size Standard_D4s_v5 \
  --enable-managed-identity \
  --network-plugin azure \
  --zones 1 2 3

az aks get-credentials --resource-group yawl-rg --name yawl-aks
```

### 4.3 Create PostgreSQL

```bash
az postgres flexible-server create \
  --name yawl-postgres \
  --resource-group yawl-rg \
  --location eastus \
  --admin-user yawl_admin \
  --admin-password SecurePassword123 \
  --sku-name Standard_D4s_v3 \
  --tier GeneralPurpose \
  --storage-size 512 \
  --high-availability ZoneRedundant

az postgres flexible-server db create \
  --server-name yawl-postgres \
  --resource-group yawl-rg \
  --database-name yawl
```

### 4.4 Create Redis

```bash
az redis create \
  --name yawl-redis \
  --resource-group yawl-rg \
  --location eastus \
  --sku Premium \
  --vm-size p1
```

### 4.5 Deploy YAWL

```bash
kubectl create namespace yawl

helm repo add yawl https://helm.yawl.io
helm repo update

helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl \
  --set global.environment=production \
  --set externalDatabase.host=yawl-postgres.postgres.database.azure.com \
  --set externalRedis.host=yawl-redis.redis.cache.windows.net
```

---

## 5. Verify Deployment

```bash
kubectl get pods -n yawl
kubectl get ingress -n yawl
```

---

## 6. Cleanup

```bash
az group delete --name yawl-rg --yes --no-wait
```
