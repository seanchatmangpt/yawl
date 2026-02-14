# YAWL Terraform Azure Infrastructure

Complete modular Terraform configuration for deploying YAWL on Azure with enterprise-grade infrastructure.

## Architecture Overview

This Terraform configuration creates a complete Azure deployment infrastructure with the following components:

- **Resource Group**: Container for all Azure resources
- **Virtual Network**: Network with multiple subnets for different components
- **App Service**: Web application hosting with staging slots
- **MySQL Database**: Managed MySQL Flexible Server with backups and monitoring
- **Monitoring**: Log Analytics Workspace and Application Insights

## Directory Structure

```
terraform-azure/
├── main.tf                 # Root module with provider and module declarations
├── variables.tf            # Root-level variables
├── outputs.tf              # Root-level outputs
├── terraform.tfvars.example # Example variables file
├── .gitignore              # Git ignore rules
├── README.md               # This file
│
├── resource_group/
│   ├── main.tf             # Resource group resources
│   ├── variables.tf        # Variables
│   └── outputs.tf          # Outputs
│
├── vnet/
│   ├── main.tf             # VNet, subnets, NSG, and security rules
│   ├── variables.tf        # Variables
│   └── outputs.tf          # Outputs
│
├── app_service/
│   ├── main.tf             # App Service Plan, Web App, and diagnostic settings
│   ├── variables.tf        # Variables
│   └── outputs.tf          # Outputs
│
├── database/
│   ├── main.tf             # MySQL Flexible Server with backups and monitoring
│   ├── variables.tf        # Variables
│   └── outputs.tf          # Outputs
│
└── monitoring/
    ├── main.tf             # Log Analytics, Application Insights, and alerts
    ├── variables.tf        # Variables
    └── outputs.tf          # Outputs
```

## Prerequisites

1. **Terraform**: >= 1.0
2. **Azure CLI**: Installed and configured
3. **Azure Account**: Active subscription with appropriate permissions
4. **Authentication**: Logged in to Azure via CLI

### Install Terraform

```bash
# macOS (Homebrew)
brew tap hashicorp/tap
brew install hashicorp/tap/terraform

# Linux
wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
unzip terraform_1.6.0_linux_amd64.zip
sudo mv terraform /usr/local/bin/
```

### Authenticate to Azure

```bash
az login
az account set --subscription "<subscription-id>"
```

## Usage

### 1. Initialize Terraform

```bash
cd terraform-azure
terraform init
```

### 2. Create Variables File

```bash
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your specific values
nano terraform.tfvars
```

### 3. Review the Plan

```bash
terraform plan -out=tfplan
```

### 4. Apply the Configuration

```bash
terraform apply tfplan
```

### 5. View Outputs

```bash
terraform output
```

## Configuration Variables

### Environment Variables
- `environment`: Environment name (dev, staging, prod)
- `project_name`: Project name (default: yawl)
- `location`: Azure region (default: East US)

### Resource Group
- `resource_group_name`: Name of the resource group

### Virtual Network
- `vnet_name`: Name of the virtual network
- `vnet_cidr`: CIDR block for VNet (default: 10.0.0.0/16)
- `subnet_configs`: Map of subnet configurations
- `enable_ddos_protection`: Enable DDoS protection (default: false)

### App Service
- `app_service_plan_name`: Name of the App Service Plan
- `app_service_name`: Name of the App Service
- `app_service_plan_sku`: SKU for App Service Plan (default: S1)
- `app_runtime_stack`: Runtime stack (default: DOTNETCORE|6.0)

### Database
- `db_server_name`: Database server name
- `db_database_name`: Database name
- `db_admin_username`: Administrator username
- `db_admin_password`: Administrator password (use secrets management)
- `db_sku_name`: Database SKU (default: B_Standard_B1s)
- `db_storage_mb`: Storage capacity (default: 51200)

### Monitoring
- `log_analytics_workspace_name`: Log Analytics Workspace name
- `log_analytics_sku`: Workspace SKU (default: PerGB2018)
- `app_insights_name`: Application Insights name
- `log_retention_days`: Log retention period (default: 30)

## Security Best Practices

1. **Secrets Management**:
   - Never commit `terraform.tfvars` containing actual passwords
   - Use Azure Key Vault for sensitive values
   - Use `-var` flag or environment variables for secrets

2. **Private Endpoints**:
   - Enable `enable_private_endpoint = true` for databases
   - This restricts access to private networks only

3. **Network Security**:
   - NSGs restrict traffic to necessary ports (80, 443)
   - Internal communication within VNet allowed
   - Modify rules based on your specific requirements

4. **Backup and Disaster Recovery**:
   - Enable `enable_backup = true` for production
   - Configure `geo_redundant_backup_enabled = true`

5. **Monitoring and Alerts**:
   - Monitoring module enabled by default
   - Configure alert recipients in Action Group

## Example Deployments

### Development Environment

```bash
terraform apply -var-file="terraform.tfvars" \
  -var="environment=dev" \
  -var="app_service_plan_sku=B1"
```

### Production Environment

```bash
terraform apply -var-file="terraform.tfvars" \
  -var="environment=prod" \
  -var="app_service_plan_sku=P1V2" \
  -var="enable_backup=true" \
  -var="geo_redundant_backup_enabled=true" \
  -var="enable_ddos_protection=true"
```

## Terraform State Management

### Local State (Development)
Local state is stored in `.terraform/` directory.

### Remote State (Recommended for Production)

Create a storage account for Terraform state:

```bash
# Create storage account
STORAGE_ACCOUNT_NAME="tfstorage$(date +%s)"
az storage account create \
  --name "$STORAGE_ACCOUNT_NAME" \
  --resource-group "rg-terraform-state" \
  --location "East US" \
  --sku Standard_LRS

# Create storage container
az storage container create \
  --name "state" \
  --account-name "$STORAGE_ACCOUNT_NAME"
```

Then uncomment and configure the backend in `main.tf`:

```hcl
backend "azurerm" {
  resource_group_name  = "rg-terraform-state"
  storage_account_name = "tfstorage..."
  container_name       = "state"
  key                  = "yawl.tfstate"
}
```

## Common Commands

```bash
# Format code
terraform fmt -recursive

# Validate configuration
terraform validate

# Check for syntax errors
terraform fmt -check -recursive

# Destroy all resources
terraform destroy

# Destroy specific resource
terraform destroy -target=module.app_service

# Output specific value
terraform output app_service_default_hostname

# Get state
terraform state show

# List resources
terraform state list
```

## Troubleshooting

### Authentication Issues

```bash
# Check current account
az account show

# Set subscription
az account set --subscription "<subscription-id>"

# Re-authenticate if needed
az login
```

### Resource Naming Conflicts

If you get naming conflicts:

1. Use unique resource group name
2. Database server names must be globally unique in Azure
3. App Service names must be globally unique

### Insufficient Quotas

Check Azure quotas:

```bash
az vm list-usage --location "East US" -o table
```

## Outputs

After successful deployment, Terraform provides these key outputs:

- `resource_group_id`: Resource group identifier
- `vnet_id`: Virtual network identifier
- `app_service_default_hostname`: Application URL
- `database_server_fqdn`: Database connection endpoint
- `app_insights_instrumentation_key`: Application Insights key

Access outputs:

```bash
terraform output app_service_default_hostname
```

## Cost Estimation

To estimate costs:

```bash
terraform plan -var-file="terraform.tfvars" | grep -i cost
```

Or use the [Azure Pricing Calculator](https://azure.microsoft.com/en-us/pricing/calculator/).

## Maintenance

### Updating Resources

Modify variables and reapply:

```bash
terraform apply -var="app_service_plan_sku=S2"
```

### Scaling

To scale your App Service Plan:

```bash
terraform apply -var="app_service_plan_sku=P1V2"
```

## Support and Contribution

For issues or contributions, please create an issue or pull request in the YAWL repository.

## License

This Terraform configuration is part of the YAWL project and follows the same license terms.
