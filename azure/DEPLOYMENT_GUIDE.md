# YAWL Workflow Engine - Azure Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying YAWL Workflow Engine to Microsoft Azure using ARM (Azure Resource Manager) templates. The deployment includes:

- **App Service (Linux)** - Tomcat 9.0 with Java 11 hosting
- **Azure Database for PostgreSQL** - Version 14 with high availability
- **Application Gateway** - WAF v2 with SSL/TLS termination and autoscaling
- **Azure Key Vault** - Secret management and encryption
- **Application Insights** - Performance monitoring and diagnostics
- **Log Analytics** - Centralized logging and analytics
- **Managed Identity** - Secure authentication without credentials
- **Virtual Network** - Isolated network architecture with subnets
- **Autoscaling** - CPU and memory-based scaling policies
- **Backup & Recovery** - Automated daily backups with 30-day retention

## Prerequisites

### Required Tools
- Azure CLI (version 2.30+)
- PowerShell 7+ or Bash
- Git
- Docker (optional, for building custom images)

### Azure Resources
- Active Azure Subscription
- Sufficient quota for:
  - App Service Plans (Premium tier)
  - PostgreSQL Database Servers
  - Application Gateway (WAF v2)
  - Key Vault (Premium tier)
  - Public IP addresses
  - Virtual Networks
  - Network Security Groups

### Required Permissions
- Subscription Owner or Contributor role
- Ability to create service principals (for automation)
- Key Vault Administrator permissions

## Quick Start Deployment

### Step 1: Set Environment Variables

```bash
# Azure environment
export AZURE_SUBSCRIPTION_ID="your-subscription-id"
export AZURE_TENANT_ID="your-tenant-id"
export AZURE_LOCATION="eastus"
export AZURE_RESOURCE_GROUP="yawl-production"
export ENVIRONMENT="production"
export PROJECT_NAME="yawl"

# Database credentials (use strong passwords in production)
export DB_ADMIN_USER="yawladmin"
export DB_ADMIN_PASSWORD="$(openssl rand -base64 32)"

# Deployment
export DEPLOYMENT_NAME="yawl-deployment-$(date +%s)"
```

### Step 2: Create Resource Group

```bash
az group create \
  --name $AZURE_RESOURCE_GROUP \
  --location $AZURE_LOCATION \
  --tags environment=$ENVIRONMENT application=$PROJECT_NAME managed-by=arm-template
```

### Step 3: Validate ARM Template

```bash
az deployment group validate \
  --resource-group $AZURE_RESOURCE_GROUP \
  --template-file azuredeploy.json \
  --parameters parameters.json \
  --parameters \
    environment=$ENVIRONMENT \
    location=$AZURE_LOCATION \
    projectName=$PROJECT_NAME \
    databaseAdminPassword="$DB_ADMIN_PASSWORD"
```

### Step 4: Deploy Template

```bash
az deployment group create \
  --resource-group $AZURE_RESOURCE_GROUP \
  --template-file azuredeploy.json \
  --parameters parameters.json \
  --parameters \
    environment=$ENVIRONMENT \
    location=$AZURE_LOCATION \
    projectName=$PROJECT_NAME \
    databaseAdminPassword="$DB_ADMIN_PASSWORD" \
  --name $DEPLOYMENT_NAME
```

### Step 5: Verify Deployment

```bash
# Check deployment status
az deployment group show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name $DEPLOYMENT_NAME \
  --query properties.provisioningState

# Get deployment outputs
az deployment group show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name $DEPLOYMENT_NAME \
  --query properties.outputs
```

## Detailed Configuration

### App Service Configuration

The App Service is configured with:

**Runtime Stack:**
- Base OS: Linux
- Runtime: Tomcat 9.0
- Java: JDK 11
- Memory: 1 GB (configurable)

**Environment Variables:**
```
YAWL_DB_HOST          = PostgreSQL FQDN
YAWL_DB_PORT          = 5432
YAWL_DB_NAME          = yawl
YAWL_DB_USER          = yawladmin
YAWL_DB_PASSWORD      = (from Key Vault)
JAVA_OPTS             = -Xms512m -Xmx1024m -XX:+UseG1GC
LOG_LEVEL             = INFO
ENVIRONMENT           = production
```

**Health Checks:**
- Liveness probe: `/resourceService/` (HTTP GET)
- Readiness probe: `/resourceService/` (HTTP GET)
- Interval: 30 seconds
- Timeout: 10 seconds
- Failure threshold: 3

**AutoHeal Configuration:**
- Enabled for automatic recovery
- Recycles on 10+ HTTP 500 errors within 1 minute

### Database Configuration

**PostgreSQL 14:**
- Edition: Standard (High Availability available)
- Compute: Standard_D4s_v3 (4 vCPU, 16 GB RAM)
- Storage: 128 GB with autogrow
- Backup: Daily, 30-day retention, geo-redundant
- SSL/TLS: Required, minimum TLS 1.2
- Max Connections: 200
- Performance Insights: Enabled

**Database Configuration Parameters:**
```sql
max_connections = 200
shared_preload_libraries = pg_stat_statements
log_statement = all
log_duration = on
```

**Firewall:**
- Allow all VNet traffic (10.0.0.0/16)
- Deny external connections by default
- Configure additional IP ranges as needed

### Application Gateway Configuration

**WAF v2 (Web Application Firewall):**
- Tier: Standard_v2 or WAF_v2 (WAF recommended)
- Autoscaling: 2-10 capacity units
- Protocols: HTTP/2 enabled
- Affinity: Cookie-based session affinity
- Connection draining: 60 seconds

**Rules & Policies:**
- OWASP CRS 3.2 ruleset
- Detection mode (changeable to Prevention)
- Logging to Log Analytics

**Health Probe:**
- Path: `/resourceService/`
- Protocol: HTTP
- Interval: 30 seconds
- Unhealthy threshold: 3 failures

### Key Vault Configuration

**Security Features:**
- SKU: Premium (HSM-backed keys available)
- Soft delete: Enabled (90-day recovery)
- Purge protection: Enabled
- Access policy: Managed Identity only
- Network: Service endpoints enabled

**Stored Secrets:**
- `DatabasePassword` - PostgreSQL admin password
- `DatabaseUsername` - PostgreSQL admin username
- `DatabaseName` - Database name

### Monitoring & Alerting

**Application Insights:**
- Automatic instrumentation for Java
- Real-time performance monitoring
- Dependency tracking
- Exception analytics
- Custom metrics support

**Log Analytics:**
- Workspace-based logs
- 30-day retention
- Workspace queries and dashboards
- Integration with monitoring tools

**Metric Alerts:**
1. **App Service High CPU** - Threshold: 85%
2. **Database High CPU** - Threshold: 85%
3. **Database Low Storage** - Threshold: 85% full

### Autoscaling Policies

**App Service Autoscaling:**
```
Min Instances:  3
Max Instances:  10
Scale-up Trigger:   CPU > 70% for 5 minutes
Scale-down Trigger: CPU < 30% for 5 minutes
Memory Trigger: Memory > 80% for 5 minutes
Cooldown Period: 5 minutes
```

**Application Gateway Autoscaling:**
```
Min Capacity:   2 units
Max Capacity:   10 units
Scale Duration: Automatic
```

## Deployment Scenarios

### Scenario 1: Production Deployment

```bash
# High-performance setup with WAF
az deployment group create \
  --resource-group yawl-production \
  --template-file azuredeploy.json \
  --parameters \
    environment=production \
    appServicePlanSku=P3V2 \
    appServiceInstanceCount=5 \
    databaseSkuName=Standard_D8s_v3 \
    databaseStorageSizeGB=256 \
    applicationGatewaySkuName=WAF_v2 \
    applicationGatewayMinCapacity=3 \
    applicationGatewayMaxCapacity=20 \
    keyVaultSkuName=premium \
    databaseBackupRetentionDays=30
```

### Scenario 2: Staging Deployment

```bash
# Cost-optimized setup with basic protection
az deployment group create \
  --resource-group yawl-staging \
  --template-file azuredeploy.json \
  --parameters \
    environment=staging \
    appServicePlanSku=P1V2 \
    appServiceInstanceCount=2 \
    databaseSkuName=Standard_B4ms \
    databaseStorageSizeGB=64 \
    applicationGatewaySkuName=Standard_v2 \
    applicationGatewayMinCapacity=1 \
    applicationGatewayMaxCapacity=5 \
    keyVaultSkuName=standard \
    databaseBackupRetentionDays=7
```

### Scenario 3: Development Deployment

```bash
# Minimal setup for development/testing
az deployment group create \
  --resource-group yawl-dev \
  --template-file azuredeploy.json \
  --parameters \
    environment=development \
    appServicePlanSku=P1V2 \
    appServiceInstanceCount=1 \
    databaseSkuName=Standard_B2s \
    databaseStorageSizeGB=32 \
    applicationGatewaySkuName=Standard_v2 \
    applicationGatewayMinCapacity=1 \
    applicationGatewayMaxCapacity=2 \
    keyVaultSkuName=standard \
    databaseBackupRetentionDays=7
```

## Post-Deployment Configuration

### Step 1: Update Database Connection Strings

```bash
# Get database FQDN
DB_FQDN=$(az postgres server show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-db-production-* \
  --query fullyQualifiedDomainName -o tsv)

echo "Database FQDN: $DB_FQDN"
```

### Step 2: Configure Application Gateway Backend

```bash
# Get App Service IP
APP_SERVICE_IP=$(az webapp show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-* \
  --query outboundIpAddresses -o tsv)

# Add App Service to Application Gateway backend pool
az network application-gateway address-pool update \
  --resource-group $AZURE_RESOURCE_GROUP \
  --gateway-name yawl-appgw-production-* \
  --name appGatewayBackendPool \
  --servers $APP_SERVICE_IP
```

### Step 3: Configure Custom Domain

```bash
# Add custom domain to App Service
az webapp config hostname add \
  --resource-group $AZURE_RESOURCE_GROUP \
  --webapp-name yawl-app-production-* \
  --hostname yawl.example.com

# Add TLS/SSL certificate
az webapp config ssl bind \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-* \
  --certificate-file certificate.pfx \
  --certificate-password $CERT_PASSWORD \
  --ssl-type SNI
```

### Step 4: Initialize Database

```bash
# Connect to database
psql -h $DB_FQDN \
  -U yawladmin@yawl-db-production-* \
  -d yawl

# Run initialization scripts (if needed)
\i init.sql
```

### Step 5: Deploy YAWL Application

Option A: Using Docker image from registry
```bash
az webapp config container set \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-* \
  --docker-custom-image-name yawlregistry.azurecr.io/yawl:latest \
  --docker-registry-server-url https://yawlregistry.azurecr.io \
  --docker-registry-server-user $REGISTRY_USER \
  --docker-registry-server-password $REGISTRY_PASSWORD
```

Option B: Deploy from Azure Container Registry
```bash
az webapp deployment container config \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-* \
  --enable-continuous-deployment true
```

## Monitoring & Maintenance

### Viewing Logs

```bash
# Stream App Service logs
az webapp log tail \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-*

# Query Log Analytics
az monitor log-analytics query \
  --workspace $LAW_WORKSPACE_ID \
  --analytics-query "AppServiceHTTPLogs | where TimeGenerated > ago(1h)"
```

### Performance Monitoring

```bash
# View Application Insights metrics
az monitor app-insights metrics show \
  --app yawl-ai-production-* \
  --resource-group $AZURE_RESOURCE_GROUP \
  --metric "requests/count"

# View database metrics
az monitor metrics list \
  --resource /subscriptions/$AZURE_SUBSCRIPTION_ID/resourceGroups/$AZURE_RESOURCE_GROUP/providers/Microsoft.DBforPostgreSQL/servers/yawl-db-production-* \
  --metric cpu_percent
```

### Database Maintenance

```bash
# Check database size
psql -h $DB_FQDN -U yawladmin@yawl-db-* -d yawl -c "SELECT pg_size_pretty(pg_database_size(current_database()));"

# Analyze query performance
psql -h $DB_FQDN -U yawladmin@yawl-db-* -d yawl -c "SELECT query, calls, total_time FROM pg_stat_statements ORDER BY total_time DESC LIMIT 10;"

# Reindex tables
psql -h $DB_FQDN -U yawladmin@yawl-db-* -d yawl -c "REINDEX DATABASE yawl;"
```

## Scaling Configuration

### Vertical Scaling (App Service)

```bash
# Scale up App Service Plan
az appservice plan update \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-asp-production-* \
  --sku P3V2
```

### Horizontal Scaling

Autoscaling is automatically configured based on CPU and memory metrics. To adjust:

```bash
# Modify autoscale settings
az monitor autoscale-settings update \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-asp-production-*-autoscale \
  --max-count 15 \
  --min-count 3
```

### Database Scaling

```bash
# Scale database compute
az postgres server update \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-db-production-* \
  --sku-name Standard_D8s_v3

# Increase storage
az postgres server update \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-db-production-* \
  --storage-size 256
```

## Backup and Disaster Recovery

### Automated Backups

Backups are configured to run automatically:
- **Frequency**: Daily
- **Retention**: 30 days
- **Geo-redundancy**: Enabled
- **Point-in-time restore**: Available for past 30 days

### Manual Backup

```bash
# Trigger manual database backup
az postgres server backup create \
  --resource-group $AZURE_RESOURCE_GROUP \
  --server-name yawl-db-production-* \
  --backup-name manual-backup-$(date +%s)
```

### Restore from Backup

```bash
# List available backups
az postgres server backup list \
  --resource-group $AZURE_RESOURCE_GROUP \
  --server-name yawl-db-production-*

# Restore to point in time
az postgres server restore \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-db-restored \
  --source-server yawl-db-production-* \
  --restore-point-in-time 2024-02-14T12:00:00Z
```

## Security Best Practices

### Access Control

1. **Enable Managed Identity**
   - Already configured in template
   - Used for secure Key Vault access

2. **Configure RBAC**
   ```bash
   # Grant App Service read access to Key Vault
   az role assignment create \
     --role "Key Vault Secrets User" \
     --assignee $MANAGED_IDENTITY_ID \
     --scope /subscriptions/$AZURE_SUBSCRIPTION_ID/resourceGroups/$AZURE_RESOURCE_GROUP/providers/Microsoft.KeyVault/vaults/yawl-kv-*
   ```

3. **Configure Network Security**
   - VNet integration enabled
   - NSG rules configured
   - Firewall rules for database

### Encryption

1. **Data in Transit**
   - HTTPS/TLS 1.2+ enforced
   - SSL required for database connections

2. **Data at Rest**
   - Key Vault premium tier (HSM-backed)
   - Database encryption enabled
   - Storage account encryption enabled

### Compliance

- Audit logging enabled
- Retention policies configured
- Network isolation implemented
- Security monitoring active

## Troubleshooting

### App Service Issues

**Site not accessible:**
```bash
# Check App Service health
az webapp show-hostname-binding \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-*

# Restart App Service
az webapp restart \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-*

# View deployment logs
az webapp deployment log show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-*
```

**High CPU usage:**
```bash
# Check current metrics
az monitor metrics list \
  --resource /subscriptions/$AZURE_SUBSCRIPTION_ID/resourceGroups/$AZURE_RESOURCE_GROUP/providers/Microsoft.Web/sites/yawl-app-production-* \
  --metric CpuPercentage \
  --start-time 2024-02-14T12:00:00Z

# View application insights
# Open Azure Portal > Application Insights > Performance
```

### Database Issues

**Connection failures:**
```bash
# Test database connectivity
psql -h $DB_FQDN \
  -U yawladmin@yawl-db-* \
  -d yawl \
  -c "SELECT version();"

# Check firewall rules
az postgres server firewall-rule list \
  --resource-group $AZURE_RESOURCE_GROUP \
  --server-name yawl-db-production-*
```

**Slow queries:**
```bash
# Query performance insights
psql -h $DB_FQDN -U yawladmin@yawl-db-* -d yawl -c "
SELECT query, calls, total_time, mean_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;
"
```

### Application Gateway Issues

**502 Bad Gateway:**
```bash
# Check backend health
az network application-gateway probe show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --gateway-name yawl-appgw-production-* \
  --name appGatewayProbe

# View WAF logs
az monitor diagnostic-settings show \
  --resource /subscriptions/$AZURE_SUBSCRIPTION_ID/resourceGroups/$AZURE_RESOURCE_GROUP/providers/Microsoft.Network/applicationGateways/yawl-appgw-production-*
```

## Cost Optimization

### Estimated Monthly Costs

| Component | SKU | Estimated Cost |
|-----------|-----|-----------------|
| App Service Plan | P2V2 (3 instances) | $450 |
| PostgreSQL | Standard_D4s_v3 | $550 |
| Application Gateway | WAF v2 (2-10 units) | $200-400 |
| Key Vault | Premium | $25 |
| Application Insights | Pay-per-GB | $30-100 |
| Log Analytics | Per-GB | $20-50 |
| Storage | Geo-redundant | $50 |
| **Total** | | **~$1,325-1,625/month** |

### Cost Reduction Tips

1. Use Standard tier for non-critical environments
2. Reduce backup retention
3. Consolidate resources
4. Right-size database based on workload
5. Use reserved instances (30% savings)
6. Implement auto-shutdown for dev environments

## Decommissioning

```bash
# Delete entire deployment
az group delete \
  --name $AZURE_RESOURCE_GROUP \
  --yes \
  --no-wait

# Or delete specific resources
az resource delete \
  --resource-group $AZURE_RESOURCE_GROUP \
  --name yawl-app-production-* \
  --resource-type "Microsoft.Web/sites"
```

## Support and Documentation

- **YAWL Foundation**: https://www.yawlfoundation.org
- **Azure Documentation**: https://docs.microsoft.com/azure/
- **Deployment Issues**: https://github.com/yawlfoundation/yawl
- **Community Forum**: https://forum.yawlfoundation.org

## Version History

- **v1.0.0** (2024-02-14): Initial release with complete ARM templates

## License

YAWL is distributed under the GNU LGPL 3.0 License.
