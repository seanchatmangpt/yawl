# YAWL Azure Deployment Guide

Step-by-step guide to deploy YAWL infrastructure on Azure using Terraform.

## Prerequisites Checklist

- [ ] Azure subscription with owner or contributor access
- [ ] Terraform installed (v1.0 or later)
- [ ] Azure CLI installed and authenticated
- [ ] Git installed
- [ ] Text editor (VS Code recommended)

## Quick Start

### Step 1: Clone/Navigate to Repository

```bash
cd /home/user/yawl/terraform-azure
```

### Step 2: Initialize Terraform

```bash
terraform init
```

This will:
- Download required Azure provider
- Initialize the local state file
- Prepare the working directory

Expected output:
```
Terraform has been successfully initialized!
```

### Step 3: Create Variables File

Copy the example variables file:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with your specific configuration:

```bash
nano terraform.tfvars
```

**Key values to update:**
- `environment`: dev, staging, or prod
- `resource_group_name`: Must be unique in your subscription
- `app_service_name`: Must be globally unique across Azure
- `db_server_name`: Must be globally unique
- `db_admin_password`: Strong password for database admin

### Step 4: Validate Configuration

```bash
terraform validate
```

Check for syntax errors:

```bash
terraform fmt -check -recursive
```

### Step 5: Plan Deployment

```bash
terraform plan -out=tfplan
```

This shows all resources that will be created. Review carefully:
- Resource names
- Costs
- Dependencies

Save plan to file for reproducible deployments.

### Step 6: Apply Deployment

```bash
terraform apply tfplan
```

Deployment typically takes 5-10 minutes:
1. Resource Group creation (~1 min)
2. Virtual Network and subnets (~2 min)
3. App Service Plan and Web App (~3 min)
4. MySQL Database (~4 min)
5. Monitoring resources (~2 min)

### Step 7: Verify Deployment

```bash
# Get all outputs
terraform output

# Get specific output
terraform output app_service_default_hostname

# Check resource group in Azure
az group show --name rg-yawl-dev
```

## Environment-Specific Deployments

### Development Environment

1. Create variables file:
```bash
cp terraform.tfvars.example terraform.tfvars.dev
```

2. Edit for dev settings:
```hcl
environment = "dev"
app_service_plan_sku = "B1"
db_sku_name = "B_Standard_B1s"
enable_backup = false
```

3. Deploy:
```bash
terraform apply -var-file="terraform.tfvars.dev"
```

### Staging Environment

1. Create variables file:
```bash
cp terraform.tfvars.example terraform.tfvars.staging
```

2. Edit for staging settings:
```hcl
environment = "staging"
app_service_plan_sku = "S1"
db_sku_name = "GP_Standard_D2s"
enable_backup = true
enable_ddos_protection = false
```

3. Deploy:
```bash
terraform apply -var-file="terraform.tfvars.staging"
```

### Production Environment

1. Create variables file:
```bash
cp terraform.tfvars.example terraform.tfvars.prod
```

2. Edit for production settings:
```hcl
environment = "prod"
app_service_plan_sku = "P1V2"
db_sku_name = "GP_Standard_D4s"
enable_backup = true
geo_redundant_backup_enabled = true
enable_ddos_protection = true
enable_private_endpoint = true
```

3. Deploy with confirmation:
```bash
terraform apply -var-file="terraform.tfvars.prod"
```

## Post-Deployment Configuration

### 1. Configure Database Connection

Get database connection details:

```bash
terraform output database_server_fqdn
terraform output database_name
```

Connection string format:
```
Server=<server_fqdn>;User ID=adminyawl;Password=<your_password>;Database=<database_name>;
```

### 2. Configure Application Settings

Set environment variables in App Service:

```bash
# Get App Service name
APP_SERVICE=$(terraform output -raw app_service_name)

# Set environment variables
az webapp config appsettings set \
  --name "$APP_SERVICE" \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --settings \
    DATABASE_HOST="$(terraform output -raw database_server_fqdn)" \
    DATABASE_NAME="$(terraform output -raw database_name)" \
    DATABASE_USER="adminyawl" \
    NODE_ENV="production"
```

### 3. Deploy Application Code

Deploy your YAWL application:

```bash
# Using Azure CLI
az webapp up --name "$APP_SERVICE" --resource-group "rg-yawl-dev"

# Or using Git
git remote add azure https://<username>@<app_name>.scm.azurewebsites.net:443/<app_name>.git
git push azure main
```

### 4. Configure Custom Domain (Optional)

```bash
# Add custom domain
az webapp config hostname add \
  --webapp-name "$(terraform output -raw app_service_name)" \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --hostname "yawl.yourdomain.com"
```

### 5. Configure SSL Certificate (Optional)

```bash
# Upload certificate
az webapp config ssl upload \
  --name "$(terraform output -raw app_service_name)" \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --certificate-file app-cert.pfx \
  --certificate-password "<password>"
```

## Monitoring and Alerts

### View Application Logs

```bash
# Stream logs in real-time
az webapp log tail \
  --name "$(terraform output -raw app_service_name)" \
  --resource-group "$(terraform output -raw resource_group_name)"
```

### Access Application Insights

```bash
# Get instrumentation key
terraform output -raw app_insights_instrumentation_key
```

View in Azure Portal:
1. Navigate to Application Insights resource
2. Click "Logs" to run KQL queries
3. Set up alerts for specific metrics

### Common Queries

Failed requests in last 24 hours:
```kusto
requests
| where timestamp > ago(24h)
| where success == false
| summarize count() by tostring(resultCode)
```

Slow requests:
```kusto
requests
| where duration > 3000
| summarize avg(duration) by name
```

## Scaling Resources

### Scale App Service

```bash
# Increase instance count
az appservice plan update \
  --name "$(terraform output -raw app_service_plan_id)" \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --number-of-workers 3
```

Or update Terraform:
```bash
terraform apply -var="app_service_plan_sku=P1V2"
```

### Scale Database

```bash
terraform apply -var="db_sku_name=GP_Standard_D4s"
```

## Backup and Disaster Recovery

### Manual Database Backup

```bash
RESOURCE_GROUP=$(terraform output -raw resource_group_name)
SERVER_NAME=$(terraform output -raw database_server_fqdn | cut -d. -f1)

az mysql flexible-server backup create \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$SERVER_NAME" \
  --backup-name "manual-backup-$(date +%Y%m%d%H%M%S)"
```

### Restore Database

```bash
az mysql flexible-server restore \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$SERVER_NAME" \
  --source-server "$SERVER_NAME" \
  --restore-point-in-time "2024-01-15T10:00:00"
```

## Troubleshooting

### Deployment Fails

1. Check Terraform syntax:
```bash
terraform validate
terraform fmt -check -recursive
```

2. Check Azure credentials:
```bash
az account show
```

3. Check quotas:
```bash
az vm list-usage --location "East US" -o table
```

4. View error logs:
```bash
terraform apply -var-file="terraform.tfvars" -var="TF_LOG=DEBUG" 2>&1 | tee deploy.log
```

### Resource Already Exists

```bash
# Check what resources exist
az resource list --resource-group "rg-yawl-dev" -o table

# Import existing resource
terraform import module.app_service.azurerm_linux_web_app.main \
  /subscriptions/<sub-id>/resourceGroups/rg-yawl-dev/providers/Microsoft.Web/sites/app-yawl-dev
```

### Cannot Connect to Database

```bash
# Check firewall rules
az mysql flexible-server firewall-rule list \
  --resource-group "rg-yawl-dev" \
  --server-name "mysql-yawl-dev"

# Allow current IP
CURRENT_IP=$(curl -s https://api.ipify.org)
az mysql flexible-server firewall-rule create \
  --resource-group "rg-yawl-dev" \
  --server-name "mysql-yawl-dev" \
  --name "allow-current-ip" \
  --start-ip-address "$CURRENT_IP" \
  --end-ip-address "$CURRENT_IP"
```

## Cleanup and Destruction

### Destroy All Resources

```bash
terraform destroy -var-file="terraform.tfvars"
```

### Destroy Specific Module

```bash
terraform destroy -target=module.app_service
```

### Keep Some Resources

Edit `terraform.tf` to comment out modules before destroying:

```hcl
# module "app_service" {
#   source = "./app_service"
#   ...
# }
```

Then run destroy:
```bash
terraform destroy
```

## Cost Optimization

### Reduce Costs in Development

```hcl
environment = "dev"
app_service_plan_sku = "B1"
db_sku_name = "B_Standard_B1s"
enable_backup = false
log_analytics_sku = "Free"
log_retention_days = 7
```

### Scheduled Shutdowns

Use Azure Automation to stop resources during off-hours:

```bash
# Stop App Service on schedule
az webapp stop --name "$APP_SERVICE" --resource-group "rg-yawl-dev"

# Start App Service
az webapp start --name "$APP_SERVICE" --resource-group "rg-yawl-dev"
```

## Advanced Configuration

### Use Remote State

See `backend.tf` for configuration instructions.

### Multi-Environment Setup

Create separate directories:

```bash
mkdir -p terraform-azure/{dev,staging,prod}
cp terraform-azure/*.tf terraform-azure/dev/
# Repeat for staging and prod
```

### Custom Domain with HTTPS

1. Update DNS records with CNAME to `*.azurewebsites.net`
2. Configure custom domain in Azure Portal
3. Create/upload SSL certificate
4. Bind certificate to domain

## Next Steps

1. Configure CI/CD pipeline (Azure DevOps or GitHub Actions)
2. Set up monitoring and alerts
3. Configure backup and disaster recovery
4. Implement auto-scaling policies
5. Plan capacity and cost management

## Support

For issues or questions:
1. Check YAWL documentation
2. Review Azure documentation
3. Check Terraform Azure provider documentation
4. Create issue in YAWL repository

## Useful Links

- [Azure Documentation](https://docs.microsoft.com/en-us/azure/)
- [Terraform Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [YAWL Documentation](https://yawlwfms.org/)
- [Azure Pricing Calculator](https://azure.microsoft.com/en-us/pricing/calculator/)
