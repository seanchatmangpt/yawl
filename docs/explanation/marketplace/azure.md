# YAWL on Microsoft Azure

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

YAWL Workflow Engine is available on Azure Marketplace, providing enterprise workflow automation with seamless Azure integration.

### 1.1 Key Features on Azure

- **Azure Marketplace**: One-click deployment
- **AKS Integration**: Optimized for Azure Kubernetes Service
- **Azure Database for PostgreSQL**: Managed database with HA
- **Azure Cache for Redis**: Enterprise-grade caching
- **Azure Key Vault**: Secrets and certificate management
- **Azure Monitor**: Native logging and metrics
- **Managed Identity**: Passwordless authentication
- **Virtual Networks**: Private connectivity

### 1.2 Architecture on Azure

```
                    +-------------------+
                    |  Azure DNS        |
                    +--------+----------+
                             |
                    +--------v----------+
                    |  Application      |
                    |  Gateway          |
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v------------+ +----v------------+ +----v------------+
    | AKS Node Pool   | | AKS Node Pool   | | AKS Node Pool   |
    | Engine Pod 1    | | Engine Pod 2    | | Engine Pod N    |
    +-----------------+ +-----------------+ +-----------------+
         |                   |                   |
         +-------------------+-------------------+
                             |
              +--------------v--------------+
              |  Azure Database for         |
              |  PostgreSQL Flexible Server |
              |  Zone-Redundant HA          |
              +-------------+---------------+
                            |
              +-------------v---------------+
              |  Azure Cache for Redis      |
              |  Premium Tier               |
              +-----------------------------+
```

---

## 2. Azure Marketplace Listing

### 2.1 Product Information

| Field | Value |
|-------|-------|
| **Product Name** | YAWL Workflow Engine |
| **Short Description** | Enterprise workflow automation with Petri Net formal foundation |
| **Categories** | Business Applications, Developer Tools |
| **Pricing Model** | Hourly (infrastructure) + BYOL |
| **Delivery Methods** | Azure Kubernetes Service (AKS), ARM Template |

### 2.2 Pricing Estimate

| Component | Tier | Monthly Est. |
|-----------|------|--------------|
| AKS Nodes (3x) | Standard_D4s_v5 | $420 |
| Azure Database | GP_Standard_D4s_v3 | $440 |
| Azure Cache | Premium P1 | $350 |
| Application Gateway | Standard_v2 | $200 |
| **Total** | | **~$1,400/mo** |

### 2.3 Free Trial

- 14-day free trial available
- Limited to B-series instances
- Full feature access
- No commitment

---

## 3. Azure Services Integration

### 3.1 Required Services

| Service | Purpose | SKU |
|---------|---------|-----|
| **AKS** | Container orchestration | Standard tier |
| **Azure Database for PostgreSQL** | Database | Flexible Server, GP tier |
| **Azure Cache for Redis** | Caching | Premium tier |
| **Key Vault** | Secrets management | Standard |
| **Virtual Network** | Network isolation | With private endpoints |
| **Azure Monitor** | Logging/metrics | Log Analytics |

### 3.2 Optional Services

| Service | Purpose |
|---------|---------|
| **Application Gateway** | L7 load balancing, WAF |
| **Azure Front Door** | Global load balancing |
| **API Management** | API gateway |
| **Event Hubs** | Event streaming |
| **Azure AD** | Identity management |

### 3.3 Managed Identity

```json
{
  "name": "yawl-engine-identity",
  "type": "Microsoft.ManagedIdentity/userAssignedIdentities",
  "location": "[resourceGroup().location]",
  "properties": {}
}
```

---

## 4. Deployment Methods

### 4.1 Azure Marketplace

1. Navigate to [Azure Marketplace](https://azuremarketplace.microsoft.com)
2. Search for "YAWL Workflow Engine"
3. Click "Get it now"
4. Select subscription and resource group
5. Configure deployment settings
6. Review and create

### 4.2 ARM Template

```bash
# Deploy using ARM template
az deployment group create \
  --resource-group yawl-rg \
  --template-uri https://raw.githubusercontent.com/yawlfoundation/yawl/main/azure/arm/template.json \
  --parameters @parameters.json
```

### 4.3 Terraform

```bash
cd terraform/azure
terraform init
terraform apply
```

---

## 5. AKS Configuration

### 5.1 Create AKS Cluster

```bash
# Create resource group
az group create --name yawl-rg --location eastus

# Create AKS cluster
az aks create \
  --resource-group yawl-rg \
  --name yawl-aks \
  --node-count 3 \
  --node-vm-size Standard_D4s_v5 \
  --enable-managed-identity \
  --enable-oidc-issuer \
  --enable-workload-identity \
  --network-plugin azure \
  --network-policy calico \
  --zones 1 2 3 \
  --attach-acr yawlacr

# Get credentials
az aks get-credentials --resource-group yawl-rg --name yawl-aks
```

### 5.2 Workload Identity

```bash
# Create managed identity
az identity create \
  --name yawl-engine-identity \
  --resource-group yawl-rg

# Get identity client ID
IDENTITY_CLIENT_ID=$(az identity show \
  --name yawl-engine-identity \
  --resource-group yawl-rg \
  --query clientId --output tsv)

# Create federated credential
az identity federated-credential create \
  --name yawl-engine-federated \
  --identity-name yawl-engine-identity \
  --resource-group yawl-rg \
  --issuer $(az aks show --name yawl-aks --resource-group yawl-rg --query oidcIssuerProfile.issuerUrl -o tsv) \
  --subject system:serviceaccount:yawl:yawl-engine
```

---

## 6. Database Configuration

### 6.1 Create PostgreSQL Flexible Server

```bash
# Create PostgreSQL server
az postgres flexible-server create \
  --name yawl-postgres \
  --resource-group yawl-rg \
  --location eastus \
  --admin-user yawl_admin \
  --admin-password SecurePassword123 \
  --sku-name Standard_D4s_v3 \
  --tier GeneralPurpose \
  --storage-size 512 \
  --version 15 \
  --high-availability ZoneRedundant \
  --zone 1 \
  --standby-zone 2

# Create database
az postgres flexible-server db create \
  --resource-group yawl-rg \
  --server-name yawl-postgres \
  --database-name yawl

# Configure firewall (VNet)
az postgres flexible-server vnet-rule create \
  --resource-group yawl-rg \
  --name yawl-aks-vnet \
  --server-name yawl-postgres \
  --vnet-name yawl-vnet \
  --subnet yawl-aks-subnet
```

---

## 7. Redis Configuration

### 7.1 Create Azure Cache for Redis

```bash
# Create Redis cache
az redis create \
  --name yawl-redis \
  --resource-group yawl-rg \
  --location eastus \
  --sku Premium \
  --vm-size p1 \
  --enable-non-ssl-port false \
  --minimum-tls-version 1.2 \
  --zones 1 2

# Get Redis keys
az redis list-keys \
  --name yawl-redis \
  --resource-group yawl-rg
```

---

## 8. Key Vault Integration

### 8.1 Create Key Vault

```bash
# Create Key Vault
az keyvault create \
  --name yawl-kv \
  --resource-group yawl-rg \
  --enable-rbac-authorization

# Store secrets
az keyvault secret set \
  --vault-name yawl-kv \
  --name db-password \
  --value SecurePassword123

az keyvault secret set \
  --vault-name yawl-kv \
  --name redis-key \
  --value RedisPrimaryKey
```

### 8.2 Secrets Provider

```yaml
# CSI Secrets Store Provider
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: yawl-secrets
spec:
  provider: azure
  parameters:
    keyvaultName: "yawl-kv"
    objects: |
      array:
        - |
          objectName: db-password
          secretName: DB_PASSWORD
        - |
          objectName: redis-key
          secretName: REDIS_PASSWORD
    tenantId: "TENANT_ID"
```

---

## 9. Next Steps

1. [Deployment Guide](deployment-guide.md)
2. [Security Overview](../security/security-overview.md)
3. [Operations Guide](../operations/scaling-guide.md)
