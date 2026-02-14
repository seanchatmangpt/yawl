# YAWL Azure Deployment - Troubleshooting Guide

## Table of Contents
1. [Pre-Deployment Issues](#pre-deployment-issues)
2. [Deployment Failures](#deployment-failures)
3. [Post-Deployment Issues](#post-deployment-issues)
4. [Application Issues](#application-issues)
5. [Database Issues](#database-issues)
6. [Networking Issues](#networking-issues)
7. [Performance Issues](#performance-issues)
8. [Monitoring & Logging](#monitoring--logging)
9. [Common Error Messages](#common-error-messages)

---

## Pre-Deployment Issues

### Azure CLI Not Installed

**Symptom:** `az: command not found`

**Solution:**
```bash
# macOS
brew install azure-cli

# Ubuntu/Debian
curl -sL https://aka.ms/InstallAzureCLIDeb | sudo bash

# Windows (PowerShell)
choco install azure-cli

# Or download from
https://aka.ms/installazurecliwindows
```

### Not Authenticated to Azure

**Symptom:** `ERROR: Please run 'az login' to set up account.`

**Solution:**
```bash
# Interactive login
az login

# Or use service principal
az login --service-principal \
  -u <app-id> \
  -p <app-password> \
  --tenant <tenant-id>

# Verify authentication
az account show
```

### Insufficient Permissions

**Symptom:** `ERROR: The user does not have permission to perform action`

**Solution:**
```bash
# Check current role
az role assignment list \
  --assignee <user-email> \
  --scope /subscriptions/<subscription-id>

# Request additional permissions from subscription owner
# Required roles:
# - Contributor (for resource creation)
# - User Access Administrator (for RBAC)
# - Key Vault Administrator (for Key Vault management)
```

### Quota Exceeded

**Symptom:** `ERROR: The subscription has reached its quota limit`

**Solution:**
```bash
# Check current usage
az vm list --resource-group <resource-group> --query "length([])"

# Request quota increase
# 1. Open Azure Portal
# 2. Navigate to "Quotas"
# 3. Select service (Compute, App Service, etc.)
# 4. Request increase
# 5. Provide business justification
```

### Resource Provider Not Registered

**Symptom:** `ERROR: 'Microsoft.Web' is not registered`

**Solution:**
```bash
# Register required providers
az provider register --namespace Microsoft.Web
az provider register --namespace Microsoft.DBforPostgreSQL
az provider register --namespace Microsoft.Network
az provider register --namespace Microsoft.KeyVault
az provider register --namespace Microsoft.Insights

# Verify registration (may take several minutes)
az provider list --query "[?registrationState=='Registered'].namespace" -o tsv
```

---

## Deployment Failures

### Template Validation Errors

**Symptom:** `ERROR: Invalid template: ...`

**Solution:**
```bash
# 1. Validate template syntax
az deployment group validate \
  --resource-group <resource-group> \
  --template-file azuredeploy.json \
  --parameters parameters.json

# 2. Check JSON syntax in editor
# - VS Code: Install "Azure Resource Manager Tools" extension
# - Online: Use jsonlint.com

# 3. Verify parameter file is valid JSON
cat parameters.json | jq '.' > /dev/null

# 4. Ensure all required parameters are provided
grep "\"type\": \"securestring\"" azuredeploy.json
grep "\"type\": \"string\"" azuredeploy.json
```

### Deployment Timeout

**Symptom:** `ERROR: The deployment operation timed out after 120 minutes`

**Solution:**
```bash
# Check deployment status
az deployment group show \
  --resource-group <resource-group> \
  --name <deployment-name> \
  --query properties.provisioningState

# Check which resources are stuck
az deployment group show \
  --resource-group <resource-group> \
  --name <deployment-name> \
  --query "properties.outputResources[*].{id:id, status:properties.provisioningState}"

# Common causes and solutions:
# 1. Database creation slow - Wait for completion or check database logs
# 2. Network configuration issues - Verify subnets and NSGs
# 3. Application Gateway provisioning - Check for configuration errors

# Retry failed resources
az resource update \
  --resource-group <resource-group> \
  --name <resource-name> \
  --resource-type <resource-type>
```

### Resource Group Already Exists

**Symptom:** `ERROR: The resource group '<name>' already exists`

**Solution:**
```bash
# Option 1: Use different name
export RESOURCE_GROUP="yawl-production-v2"

# Option 2: Reuse existing resource group
az group show --name <existing-resource-group>

# Option 3: Delete and recreate (CAUTION - destructive)
az group delete --name <resource-group> --yes --no-wait
# Wait for deletion to complete before redeploying
```

### Invalid Parameter Values

**Symptom:** `ERROR: Invalid value for parameter 'databaseAdminPassword'`

**Solution:**
```bash
# Password must meet Azure requirements:
# - Minimum 8 characters
# - Contain uppercase letters (A-Z)
# - Contain lowercase letters (a-z)
# - Contain numbers (0-9)
# - Contain special characters (!@#$%^&*)
# - Cannot contain username or database name

# Valid example:
export DB_PASSWORD="Abc123!@#XyZ"

# Test password strength
echo "Password: $(echo -n "$DB_PASSWORD" | wc -c) chars"
[[ "$DB_PASSWORD" =~ [A-Z] ]] && echo "✓ Has uppercase"
[[ "$DB_PASSWORD" =~ [a-z] ]] && echo "✓ Has lowercase"
[[ "$DB_PASSWORD" =~ [0-9] ]] && echo "✓ Has numbers"
[[ "$DB_PASSWORD" =~ [!@#$%^&*] ]] && echo "✓ Has special chars"
```

### Insufficient Quota for VM Size

**Symptom:** `ERROR: The quota for VM family 'Standard_P' is exceeded`

**Solution:**
```bash
# Check current quotas
az vm list-usage --location <region> -o table

# Option 1: Use smaller SKU
appServicePlanSku: "P1V2"  # Instead of P3V2

# Option 2: Request quota increase
# Azure Portal > Help + Support > New Support Request

# Option 3: Use different region
location: "westus"  # Instead of eastus
```

---

## Post-Deployment Issues

### Resources Not Visible in Portal

**Symptom:** Deployment succeeded but resources not showing in portal

**Solution:**
```bash
# List all resources in resource group
az resource list --resource-group <resource-group> -o table

# If resources exist but not visible:
# 1. Refresh Azure Portal (Ctrl+F5)
# 2. Check resource filters in portal
# 3. Verify correct subscription is selected
# 4. Wait 5-10 minutes for portal cache to update
```

### Resource Group Accessible but Resources Missing

**Symptom:** Resource group exists but created resources not found

**Solution:**
```bash
# Check deployment operations
az deployment group operation list \
  --resource-group <resource-group> \
  --name <deployment-name> \
  -o table

# Check for failed operations
az deployment group operation list \
  --resource-group <resource-group> \
  --name <deployment-name> \
  --query "[?properties.provisioningState=='Failed']" \
  -o json

# Retry deployment
az deployment group create \
  --resource-group <resource-group> \
  --template-file azuredeploy.json \
  --parameters parameters.json
```

---

## Application Issues

### App Service Not Accessible

**Symptom:** Cannot reach application at `https://<app-name>.azurewebsites.net`

**Diagnosis:**
```bash
# 1. Check App Service status
az webapp show \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --query "{state:state, status:status}"

# 2. Check Application Gateway health
az network application-gateway probe show \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayProbe \
  -o json

# 3. Check backend address pool
az network application-gateway address-pool show \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayBackendPool
```

**Solutions:**
```bash
# 1. Restart App Service
az webapp restart \
  --resource-group <resource-group> \
  --name <app-service-name>

# 2. Add App Service to Application Gateway backend pool
APP_IP=$(az webapp show \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --query outboundIpAddresses -o tsv)

az network application-gateway address-pool update \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayBackendPool \
  --servers $APP_IP

# 3. Check NSG rules
az network nsg rule list \
  --resource-group <resource-group> \
  --nsg-name <nsg-name> \
  -o table
```

### HTTP 502 Bad Gateway

**Symptom:** Accessing application returns "502 Bad Gateway"

**Diagnosis:**
```bash
# 1. Check Application Gateway operational status
az network application-gateway show \
  --resource-group <resource-group> \
  --name <appgw-name> \
  --query operationalState

# 2. Check backend health status
curl -v https://<app-gateway-ip>/ \
  -H "Host: <app-service-name>.azurewebsites.net"

# 3. Review Application Gateway logs
az monitor log-analytics query \
  --workspace <workspace-id> \
  --analytics-query "AzureDiagnostics | where ResourceType == 'APPLICATIONGATEWAYS'"
```

**Solutions:**
```bash
# 1. Verify Application Gateway configuration
az network application-gateway http-settings show \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayBackendHttpSettings

# 2. Check backend connectivity
# From a test VM in the VNet:
curl -v http://<app-service-private-ip>:80/resourceService/

# 3. Increase application gateway timeout
az network application-gateway http-settings update \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayBackendHttpSettings \
  --timeout 60

# 4. Restart Application Gateway
az network application-gateway restart \
  --resource-group <resource-group> \
  --name <appgw-name>
```

### High Memory Usage

**Symptom:** Application crashes with "OutOfMemory" or "Heap space" errors

**Solution:**
```bash
# 1. Check current memory limits
az webapp config show \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --query appSettings

# 2. Increase JAVA_OPTS memory settings
az webapp config appsettings set \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --settings \
    JAVA_OPTS="-Xms1024m -Xmx2048m -XX:+UseG1GC"

# 3. Increase App Service Plan capacity
az appservice plan update \
  --resource-group <resource-group> \
  --name <app-service-plan-name> \
  --sku P2V2

# 4. Monitor memory metrics
az monitor metrics list \
  --resource /subscriptions/<sub-id>/resourceGroups/<rg>/providers/Microsoft.Web/sites/<app-name> \
  --metric MemoryPercentage \
  --aggregation Average
```

### Deployment Slots Issues

**Symptom:** Staging slot deployment fails or traffic doesn't switch

**Solution:**
```bash
# List deployment slots
az webapp deployment slot list \
  --resource-group <resource-group> \
  --name <app-service-name>

# Create staging slot if missing
az webapp deployment slot create \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --slot staging

# Swap slots
az webapp deployment slot swap \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --slot staging
```

---

## Database Issues

### Cannot Connect to Database

**Symptom:** `psql: FATAL: Ident authentication failed for user "yawladmin"`

**Diagnosis:**
```bash
# 1. Get database FQDN
DB_FQDN=$(az postgres server show \
  --resource-group <resource-group> \
  --name <db-server-name> \
  --query fullyQualifiedDomainName -o tsv)

echo $DB_FQDN

# 2. Test connectivity
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "SELECT version();"

# 3. Check firewall rules
az postgres server firewall-rule list \
  --resource-group <resource-group> \
  --server-name <db-server-name>
```

**Solutions:**
```bash
# 1. Verify credentials
# - Username format: yawladmin@<db-server-name>
# - Use password from Key Vault
az keyvault secret show \
  --vault-name <keyvault-name> \
  --name DatabasePassword \
  --query value -o tsv

# 2. Add firewall rule for your IP
az postgres server firewall-rule create \
  --resource-group <resource-group> \
  --server-name <db-server-name> \
  --name AllowMyIP \
  --start-ip-address <your-ip> \
  --end-ip-address <your-ip>

# 3. Allow Azure services
az postgres server firewall-rule create \
  --resource-group <resource-group> \
  --server-name <db-server-name> \
  --name AllowAzureServices \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# 4. Check SSL requirement
az postgres server show \
  --resource-group <resource-group> \
  --name <db-server-name> \
  --query sslEnforcement

# Connect with SSL required
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  --ssl=require \
  -c "SELECT version();"
```

### Database Performance Issues

**Symptom:** Slow queries, high CPU, connection pool exhaustion

**Diagnosis:**
```bash
# 1. Check database metrics
az monitor metrics list \
  --resource /subscriptions/<sub-id>/resourceGroups/<rg>/providers/Microsoft.DBforPostgreSQL/servers/<db-name> \
  --metric cpu_percent,memory_percent,active_connections \
  --aggregation Average \
  --interval PT5M \
  --start-time 2024-02-14T12:00:00Z

# 2. Identify slow queries
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "
  SELECT query, calls, total_time, mean_time
  FROM pg_stat_statements
  ORDER BY mean_time DESC
  LIMIT 20;
  "

# 3. Check table sizes
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "
  SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
  FROM pg_tables
  WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
  "
```

**Solutions:**
```bash
# 1. Increase database SKU
az postgres server update \
  --resource-group <resource-group> \
  --name <db-server-name> \
  --sku-name Standard_D4s_v3

# 2. Increase storage
az postgres server update \
  --resource-group <resource-group> \
  --name <db-server-name> \
  --storage-size 256

# 3. Optimize queries
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "EXPLAIN ANALYZE SELECT ..."

# 4. Create indexes
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "CREATE INDEX idx_name ON table(column);"

# 5. Analyze and vacuum
psql -h $DB_FQDN \
  -U yawladmin@<db-server-name> \
  -d yawl \
  -c "ANALYZE;"
```

### Backup and Restore Issues

**Symptom:** Cannot restore from backup or backup creation fails

**Solution:**
```bash
# 1. List available backups
az postgres server backup list \
  --resource-group <resource-group> \
  --server-name <db-server-name> \
  -o table

# 2. Create manual backup
az postgres server backup create \
  --resource-group <resource-group> \
  --server-name <db-server-name> \
  --backup-name manual-backup-$(date +%s)

# 3. Restore to point-in-time
az postgres server restore \
  --resource-group <resource-group> \
  --source-server <source-db-name> \
  --name <restored-db-name> \
  --restore-point-in-time "2024-02-14T12:00:00Z"

# 4. Check backup retention settings
az postgres server show \
  --resource-group <resource-group> \
  --name <db-server-name> \
  --query storageProfile
```

---

## Networking Issues

### VNet Integration Problems

**Symptom:** App Service cannot reach database despite being in same VNet

**Diagnosis:**
```bash
# 1. Check VNet integration
az webapp vnet-integration list \
  --resource-group <resource-group> \
  --name <app-service-name>

# 2. Verify subnet configuration
az network vnet subnet show \
  --resource-group <resource-group> \
  --vnet-name <vnet-name> \
  --name <subnet-name> \
  -o json

# 3. Check NSG rules
az network nsg rule list \
  --resource-group <resource-group> \
  --nsg-name <nsg-name> \
  --query "[?direction=='Inbound'].{name:name, priority:priority, sourcePort:sourcePortRange, protocol:protocol}" \
  -o table
```

**Solutions:**
```bash
# 1. Add VNet integration if missing
az webapp vnet-integration add \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --vnet <vnet-name> \
  --subnet <app-service-subnet>

# 2. Add NSG rule for database subnet
az network nsg rule create \
  --resource-group <resource-group> \
  --nsg-name <nsg-name> \
  --name AllowPostgreSQL \
  --priority 120 \
  --direction Inbound \
  --access Allow \
  --protocol Tcp \
  --source-address-prefixes VirtualNetwork \
  --destination-address-prefixes VirtualNetwork \
  --destination-port-ranges 5432
```

### DNS Resolution Issues

**Symptom:** Cannot resolve database hostname or application URL

**Solution:**
```bash
# 1. Test DNS resolution from App Service
az webapp remote-debugging disable \
  --resource-group <resource-group> \
  --name <app-service-name>

# 2. Check DNS settings
nslookup <db-server-name>.postgres.database.azure.com

# 3. Verify CNAME record for custom domain
nslookup yawl.example.com

# 4. Check Application Gateway DNS
az network application-gateway frontend-ip show \
  --resource-group <resource-group> \
  --gateway-name <appgw-name> \
  --name appGatewayFrontendIP
```

---

## Performance Issues

### High CPU Utilization

**Symptom:** CPU consistently above 80%, application slow

**Diagnosis:**
```bash
# Check CPU metrics
az monitor metrics list \
  --resource /subscriptions/<sub-id>/resourceGroups/<rg>/providers/Microsoft.Web/sites/<app-name> \
  --metric CpuPercentage \
  --aggregation Average \
  --interval PT1M \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S)Z
```

**Solutions:**
```bash
# 1. Scale up App Service Plan
az appservice plan update \
  --resource-group <resource-group> \
  --name <app-service-plan> \
  --sku P3V2

# 2. Add more instances
az appservice plan update \
  --resource-group <resource-group> \
  --name <app-service-plan> \
  --number-of-workers 5

# 3. Optimize JAVA_OPTS
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 4. Enable Application Insights profiler
az webapp config appsettings set \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --settings \
    ApplicationInsightsAgent_EXTENSION_VERSION="~3"
```

### High Memory Usage

**Symptom:** Memory consistently above 80%, application crashes

**Solution:**
```bash
# 1. Increase heap size
JAVA_OPTS="-Xms1024m -Xmx2048m"

# 2. Use G1GC garbage collector
JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 3. Monitor memory leaks
# Review Application Insights Memory metrics
# Check for unclosed resources in application code

# 4. Increase App Service Plan
az appservice plan update \
  --resource-group <resource-group> \
  --name <app-service-plan> \
  --sku P3V2
```

### Slow Response Times

**Symptom:** Application responses taking >5 seconds

**Diagnosis:**
```bash
# Check Application Insights performance data
az monitor app-insights metrics show \
  --app <app-insights-name> \
  --resource-group <resource-group> \
  --metric "requests/duration"

# Identify slow endpoints
# Azure Portal > Application Insights > Performance > Operations
```

**Solutions:**
```bash
# 1. Enable caching
# 2. Optimize database queries
# 3. Add Application Gateway caching
# 4. Increase database compute
# 5. Scale up App Service Plan
```

---

## Monitoring & Logging

### No Data in Application Insights

**Symptom:** Application Insights shows no metrics or traces

**Solution:**
```bash
# 1. Verify Application Insights is enabled
az webapp config appsettings list \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --query "[?name=='APPLICATIONINSIGHTS_CONNECTION_STRING']"

# 2. Get instrumentation key
az monitor app-insights component show \
  --app <app-insights-name> \
  --resource-group <resource-group> \
  --query instrumentationKey

# 3. Enable Java agent
az webapp config appsettings set \
  --resource-group <resource-group> \
  --name <app-service-name> \
  --settings \
    ApplicationInsightsAgent_EXTENSION_VERSION="~3" \
    XDT_MicrosoftApplicationInsights_Mode="recommended"

# 4. Restart App Service
az webapp restart \
  --resource-group <resource-group> \
  --name <app-service-name>
```

### Log Analytics Query Examples

```kql
// Query Application logs
AppServiceHTTPLogs
| where TimeGenerated > ago(1h)
| summarize Count=count() by ResultCode
| sort by Count desc

// Query database logs
AzureDiagnostics
| where ResourceProvider=="MICROSOFT.DBFORPOSTGRESQL"
| where TimeGenerated > ago(1h)
| summarize Count=count() by Message

// Query Application Gateway logs
AzureDiagnostics
| where ResourceType=="APPLICATIONGATEWAYS"
| where TimeGenerated > ago(1h)
| summarize Count=count() by httpStatus_d
```

---

## Common Error Messages

### "InvalidTemplateDeployment"
**Cause:** Template syntax error or missing parameter
**Solution:** Run `az deployment group validate` and check JSON syntax

### "InsufficientQuota"
**Cause:** Subscription quota exceeded for resource type
**Solution:** Reduce SKU size or request quota increase

### "ResourceNotFound"
**Cause:** Referenced resource doesn't exist
**Solution:** Check resource names and dependencies

### "DeploymentFailed"
**Cause:** Resource creation failed
**Solution:** Check resource-specific logs and error messages

### "ForbiddenError"
**Cause:** Insufficient permissions
**Solution:** Verify user has required roles

### "InvalidRequestFormat"
**Cause:** Invalid JSON or parameter syntax
**Solution:** Validate JSON, check parameter values

---

## Getting Help

### Azure Support
- **Azure Support Portal**: https://portal.azure.com/#blade/Microsoft_Azure_Support
- **Service Health**: https://status.azure.com/
- **Azure Status**: https://azure.microsoft.com/en-us/status/

### YAWL Support
- **YAWL Foundation**: https://www.yawlfoundation.org
- **GitHub Issues**: https://github.com/yawlfoundation/yawl/issues
- **Forum**: https://forum.yawlfoundation.org

### Documentation
- **Azure Docs**: https://docs.microsoft.com/azure/
- **YAWL Docs**: https://docs.yawlfoundation.org
- **Deployment Guide**: See `DEPLOYMENT_GUIDE.md`

---

## Diagnostic Commands Reference

```bash
# Resource group info
az group show --name <resource-group>

# List all resources
az resource list --resource-group <resource-group>

# Get resource details
az resource show \
  --resource-group <resource-group> \
  --name <resource-name> \
  --resource-type <resource-type>

# View deployment status
az deployment group show \
  --resource-group <resource-group> \
  --name <deployment-name> \
  --query properties.provisioningState

# Get deployment errors
az deployment group operation list \
  --resource-group <resource-group> \
  --name <deployment-name> \
  --query "[?properties.provisioningState=='Failed']"

# Check Azure CLI version
az --version

# Check current subscription
az account show

# List available regions
az account list-locations -o table
```

---

**Last Updated:** 2024-02-14
**Version:** 1.0.0
