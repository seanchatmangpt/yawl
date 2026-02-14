# Quick Start Guide - YAWL Terraform on Azure

Get YAWL infrastructure running in Azure with Terraform in 5 minutes.

## Prerequisites

```bash
# Install Terraform (macOS)
brew tap hashicorp/tap
brew install hashicorp/tap/terraform

# Install Azure CLI (macOS)
brew install azure-cli

# Login to Azure
az login
```

## 5-Minute Deployment

### Step 1: Prepare Variables (1 min)

```bash
cd /home/user/yawl/terraform-azure

# Copy example file
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
nano terraform.tfvars
```

**Minimal Changes Needed**:
```hcl
environment = "dev"
resource_group_name = "rg-yawl-dev"
db_admin_password = "YourSecurePassword123!"
```

### Step 2: Initialize (1 min)

```bash
terraform init
```

Expected output:
```
Terraform has been successfully initialized!
```

### Step 3: Validate (30 sec)

```bash
terraform validate
```

### Step 4: Plan (1 min)

```bash
terraform plan
```

Review the output to see what will be created.

### Step 5: Deploy (1-2 min)

```bash
terraform apply
```

Type `yes` when prompted.

## After Deployment

### Get Outputs

```bash
# All outputs
terraform output

# Specific output
terraform output app_service_default_hostname
```

### Test Application

```bash
# Get the URL
APP_URL=$(terraform output -raw app_service_default_hostname)

# Test it
curl https://$APP_URL

# Open in browser
open https://$APP_URL
```

### Connect to Database

```bash
# Get database details
terraform output database_server_fqdn
terraform output database_name

# Connection string
mysql -h <server_fqdn> -u adminyawl -p <password> <database_name>
```

## Using Helper Scripts

```bash
# Deploy with automated checks
./scripts/deploy.sh dev

# Show all outputs
./scripts/output.sh

# Get specific output
./scripts/output.sh app_service_default_hostname

# Destroy infrastructure
./scripts/destroy.sh dev
```

## Common Tasks

### Scale Up

```bash
terraform apply -var="app_service_plan_sku=S2"
```

### View Logs

```bash
APP_SERVICE=$(terraform output -raw app_service_name)
RESOURCE_GROUP=$(terraform output -raw resource_group_name)

az webapp log tail \
  --name "$APP_SERVICE" \
  --resource-group "$RESOURCE_GROUP"
```

### Update Database Password

```bash
terraform apply -var="db_admin_password=NewSecurePassword123!"
```

### Destroy Resources

```bash
terraform destroy
```

## Troubleshooting

### Check Authentication
```bash
az account show
```

### Validate Configuration
```bash
terraform fmt -recursive
terraform validate
```

### Check Resource Status
```bash
az group show --name rg-yawl-dev
az resource list --resource-group rg-yawl-dev -o table
```

### View Error Details
```bash
terraform apply -var-file="terraform.tfvars" 2>&1 | tee debug.log
```

## Environment Variables

Set these for different deployments:

### Development
```hcl
environment = "dev"
app_service_plan_sku = "B1"
db_sku_name = "B_Standard_B1s"
enable_backup = false
```

### Production
```hcl
environment = "prod"
app_service_plan_sku = "P1V2"
db_sku_name = "GP_Standard_D4s"
enable_backup = true
geo_redundant_backup_enabled = true
```

## File Structure

```
terraform-azure/
├── main.tf                   # Main configuration
├── variables.tf              # Variables
├── outputs.tf                # Outputs
├── terraform.tfvars.example  # Example values
├── README.md                 # Full documentation
├── DEPLOYMENT_GUIDE.md       # Detailed guide
│
├── resource_group/           # Module: Resource Group
├── vnet/                     # Module: Virtual Network
├── app_service/              # Module: Web App
├── database/                 # Module: MySQL Database
├── monitoring/               # Module: Monitoring
│
└── scripts/
    ├── deploy.sh            # Deployment automation
    ├── destroy.sh           # Destruction
    ├── output.sh            # Show outputs
    └── setup-backend.sh     # Remote state setup
```

## Key Outputs

After deployment, you get:

```
app_service_default_hostname     = "app-yawl-dev.azurewebsites.net"
database_server_fqdn             = "mysql-yawl-dev.mysql.database.azure.com"
database_name                    = "yawl_db"
log_analytics_workspace_id       = "/subscriptions/..."
app_insights_instrumentation_key = "..." (sensitive)
```

## Cost Estimation

**Development Environment**: ~$50-100/month
- B1 App Service Plan
- B_Standard_B1s Database
- 30-day retention logs

**Production Environment**: ~$500-1000+/month
- P1V2 App Service Plan
- GP_Standard_D4s Database
- 30-day retention logs
- Geo-redundant backups
- DDoS protection

## Next Steps

1. Deploy the infrastructure
2. Configure your application
3. Set up CI/CD pipeline
4. Configure monitoring alerts
5. Plan disaster recovery

## Documentation

- **Full Documentation**: See `README.md`
- **Detailed Guide**: See `DEPLOYMENT_GUIDE.md`
- **Architecture**: See `STRUCTURE.md`

## Support

For issues:
1. Check error messages carefully
2. Run validation: `terraform validate`
3. Check Azure status: `az account show`
4. Review logs in Azure Portal
5. Consult YAWL documentation

## Quick Reference Commands

```bash
# Initialize
terraform init

# Validate
terraform validate

# Format code
terraform fmt -recursive

# Plan changes
terraform plan -out=tfplan

# Apply changes
terraform apply tfplan

# Destroy infrastructure
terraform destroy

# Show outputs
terraform output

# Show specific output
terraform output app_service_default_hostname

# Show state
terraform state list
terraform state show module.app_service.azurerm_linux_web_app.main

# Refresh state
terraform refresh

# Import resource
terraform import module.app_service.azurerm_linux_web_app.main \
  /subscriptions/{sub-id}/resourceGroups/{rg}/providers/Microsoft.Web/sites/{name}

# Get providers
terraform providers

# Lock versions
terraform init -upgrade

# Validate JSON
terraform fmt -check -recursive

# Show plan in JSON
terraform plan -json > plan.json
```

## Environment Files

Create multiple `.tfvars` files:

```bash
# Development
cp terraform.tfvars.example terraform.tfvars.dev
# Edit terraform.tfvars.dev

# Staging
cp terraform.tfvars.example terraform.tfvars.staging
# Edit terraform.tfvars.staging

# Production
cp terraform.tfvars.example terraform.tfvars.prod
# Edit terraform.tfvars.prod

# Deploy
terraform apply -var-file="terraform.tfvars.dev"
terraform apply -var-file="terraform.tfvars.staging"
terraform apply -var-file="terraform.tfvars.prod"
```

## Tips & Tricks

### Target Specific Resources

```bash
# Apply only App Service
terraform apply -target=module.app_service

# Destroy only Database
terraform destroy -target=module.database
```

### Dry Run

```bash
# See what would change without applying
terraform plan -out=tfplan
# Don't run: terraform apply tfplan
```

### Save Plan

```bash
# Save plan for later
terraform plan -out=tfplan

# Apply saved plan
terraform apply tfplan
```

### Use Workspace

```bash
# Create workspace
terraform workspace new prod

# Switch workspace
terraform workspace select prod

# List workspaces
terraform workspace list
```

### Debug Mode

```bash
# Enable debug logging
export TF_LOG=DEBUG
terraform apply

# Disable
unset TF_LOG
```

## Performance

### Parallel Operations

```bash
# Default: -parallelism=10
terraform apply -parallelism=20  # Faster but uses more API calls
```

### Refresh State

```bash
terraform refresh  # Update state from Azure
```

## Monitoring

### Watch Deployment

```bash
# Monitor resource group
watch -n 5 'az resource list --resource-group rg-yawl-dev -o table'

# Monitor App Service
watch -n 5 'az webapp show --name app-yawl-dev --resource-group rg-yawl-dev'
```

## Security Best Practices

1. **Never commit secrets**
   ```bash
   # Add to .gitignore
   terraform.tfvars
   *.tfvars
   ```

2. **Use Azure Key Vault**
   ```bash
   # Fetch secret
   SECRET=$(az keyvault secret show --vault-name mykeyvault --name db-password -o tsv --query value)
   terraform apply -var="db_admin_password=$SECRET"
   ```

3. **Use remote state**
   ```bash
   # See setup-backend.sh
   ./scripts/setup-backend.sh
   ```

4. **Enable state locking**
   - Configured automatically with Azure backend

## Useful Links

- [Terraform Docs](https://www.terraform.io/docs)
- [Azure Provider](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/)
- [Azure Pricing](https://azure.microsoft.com/en-us/pricing/calculator/)

---

**Happy Deploying!** 🚀

Questions? Check the full documentation in `README.md` or `DEPLOYMENT_GUIDE.md`.
