# Terraform Module Structure for YAWL Azure Deployment

## Complete Directory Layout

```
terraform-azure/
├── Root Configuration Files
│   ├── main.tf                  # Provider config, module declarations, local variables
│   ├── variables.tf             # Root-level variable definitions
│   ├── outputs.tf               # Root-level output definitions
│   ├── backend.tf               # Remote state backend configuration (commented)
│   └── terraform.tfvars.example # Example variables file
│
├── Modules (Reusable Components)
│   │
│   ├── resource_group/
│   │   ├── main.tf              # Azure Resource Group resource
│   │   ├── variables.tf         # Resource group variables
│   │   └── outputs.tf           # Resource group outputs
│   │
│   ├── vnet/
│   │   ├── main.tf              # VNet, Subnets, NSGs, firewall rules
│   │   ├── variables.tf         # VNet configuration variables
│   │   └── outputs.tf           # VNet outputs
│   │
│   ├── app_service/
│   │   ├── main.tf              # App Service Plan, Web App, staging slot
│   │   ├── variables.tf         # App Service variables
│   │   └── outputs.tf           # App Service outputs
│   │
│   ├── database/
│   │   ├── main.tf              # MySQL Flexible Server, backups, users
│   │   ├── variables.tf         # Database variables
│   │   └── outputs.tf           # Database outputs
│   │
│   └── monitoring/
│       ├── main.tf              # Log Analytics, Application Insights, alerts
│       ├── variables.tf         # Monitoring variables
│       └── outputs.tf           # Monitoring outputs
│
├── Helper Scripts
│   └── scripts/
│       ├── deploy.sh            # Automated deployment script
│       ├── destroy.sh           # Infrastructure destruction script
│       ├── output.sh            # Output helper
│       └── setup-backend.sh     # Remote state backend setup
│
└── Documentation
    ├── README.md                # Main documentation
    ├── DEPLOYMENT_GUIDE.md      # Step-by-step deployment guide
    ├── STRUCTURE.md             # This file
    └── .gitignore               # Git ignore rules
```

## Module Descriptions

### 1. Resource Group Module (`resource_group/`)

**Purpose**: Creates and manages the Azure Resource Group

**Resources**:
- `azurerm_resource_group`: Container for all infrastructure

**Key Features**:
- Single resource group for logical grouping
- Automatic tagging with environment and project info
- Location management

**Inputs**:
```hcl
resource_group_name  # Name of the resource group
location            # Azure region
environment         # Environment (dev/staging/prod)
project_name        # Project name
tags               # Common tags
```

**Outputs**:
```hcl
resource_group_id    # Resource group ID
resource_group_name  # Resource group name
location            # Location
```

---

### 2. Virtual Network Module (`vnet/`)

**Purpose**: Creates network infrastructure with subnets and security

**Resources**:
- `azurerm_virtual_network`: Main VNet
- `azurerm_subnet`: Multiple subnets (app-service, database, monitoring)
- `azurerm_network_security_group`: Network firewall
- `azurerm_network_security_rule`: Inbound/outbound rules
- `azurerm_ddos_protection_plan`: Optional DDoS protection

**Key Features**:
- Subnet segmentation for different workloads
- NSG rules for HTTP (80), HTTPS (443), and internal traffic
- Service endpoints for SQL and Storage
- Optional DDoS protection
- Private endpoint support for secure connections

**Inputs**:
```hcl
vnet_name               # VNet name
vnet_cidr              # VNet CIDR block (10.0.0.0/16)
subnet_configs         # Subnet configuration map
enable_ddos_protection # Enable DDoS protection (default: false)
```

**Outputs**:
```hcl
vnet_id              # VNet ID
vnet_name            # VNet name
app_service_subnet_id # App Service subnet
database_subnet_id   # Database subnet
monitoring_subnet_id # Monitoring subnet
nsg_id              # Network Security Group ID
```

---

### 3. App Service Module (`app_service/`)

**Purpose**: Manages web application hosting

**Resources**:
- `azurerm_service_plan`: App Service Plan (compute resources)
- `azurerm_linux_web_app`: Linux-based Web App
- `azurerm_app_service_slot`: Staging deployment slot
- `azurerm_app_service_virtual_network_swift_connection`: VNet integration
- `azurerm_monitor_diagnostic_setting`: Application logging

**Key Features**:
- Linux-based web application support
- Configurable SKU (B1-B3, S1-S3, P1V2, P2V2, etc.)
- HTTPS enforcement
- TLS 1.2 minimum
- Staging slot for blue-green deployments
- VNet integration for secure connections
- HTTP/2 support
- Auto-scaling ready
- Diagnostic logging to Log Analytics

**Inputs**:
```hcl
app_service_plan_name   # Service plan name
app_service_name        # Web app name
app_service_plan_sku    # Pricing tier (B1, S1, P1V2, etc.)
app_runtime_stack       # Runtime (DOTNETCORE|6.0, NODE|18-lts, etc.)
subnet_id              # VNet integration subnet
log_analytics_workspace_id  # For diagnostics
```

**Outputs**:
```hcl
app_service_id           # App Service ID
app_service_name         # App Service name
app_service_default_hostname  # Default domain
app_service_possible_outbound_ip_addresses  # Outbound IPs
staging_slot_id          # Staging slot ID
staging_slot_default_hostname  # Staging domain
```

---

### 4. Database Module (`database/`)

**Purpose**: Manages MySQL database infrastructure

**Resources**:
- `azurerm_mysql_flexible_server`: MySQL Flexible Server
- `azurerm_mysql_flexible_server_database`: Database instance
- `azurerm_mysql_flexible_server_firewall_rule`: Firewall rules
- `azurerm_mysql_flexible_server_configuration`: Database configurations
- `azurerm_mysql_flexible_server_database_user`: Application user
- `azurerm_private_endpoint`: Optional private access
- `azurerm_backup_vault`: Backup storage
- `azurerm_monitor_diagnostic_setting`: Database logging

**Key Features**:
- MySQL 5.7 and 8.0 support
- Flexible Server (modern architecture)
- SSL/TLS enforcement
- Automatic password generation if not provided
- Slow query logging enabled
- Character set UTF8MB4
- High availability with zone redundancy (optional)
- Geo-redundant backups (optional)
- Private endpoints for secure connections
- Automated backups with retention policies
- Database-level diagnostic logging

**Inputs**:
```hcl
server_name             # MySQL server name
database_name           # Database name
administrator_login     # Admin username
administrator_password  # Admin password
db_sku_name            # Server SKU (B_Standard_B1s, GP_Standard_D2s)
db_version             # MySQL version (5.7 or 8.0)
backup_retention_days  # Retention period (1-35 days)
enable_ha              # Zone redundancy (default: false)
enable_private_endpoint # Private endpoint (default: false)
enable_backup          # Automated backups (default: false)
app_username           # Application user (default: appuser)
```

**Outputs**:
```hcl
server_id              # Server ID
server_fqdn            # Fully qualified domain name
database_name          # Database name
private_endpoint_id    # Private endpoint ID
connection_string_admin # Admin connection string
backup_vault_id        # Backup vault ID
```

---

### 5. Monitoring Module (`monitoring/`)

**Purpose**: Manages observability and alerting

**Resources**:
- `azurerm_log_analytics_workspace`: Central logging platform
- `azurerm_application_insights`: Application performance monitoring
- `azurerm_monitor_metric_alert`: CPU, Memory, Response Time, HTTP errors
- `azurerm_monitor_action_group`: Alert notification group
- `azurerm_monitor_activity_log_alert`: Resource health alerts
- `azurerm_log_analytics_query_pack`: Saved KQL queries
- `azurerm_application_insights_web_test`: Availability testing
- `azurerm_application_insights_smart_detection_rule`: Anomaly detection

**Key Features**:
- Centralized logging with Log Analytics
- Application Performance Monitoring (APM)
- Pre-configured alerts:
  - High CPU usage (>80%)
  - High memory usage (>85%)
  - Slow responses (>2000ms)
  - HTTP 5xx errors (>10 in 5 min)
- Saved queries for:
  - Failed requests analysis
  - Slow queries analysis
  - Database connections monitoring
- Optional availability tests
- Smart detection for anomalies
- Resource health monitoring
- Configurable retention (1-730 days)
- Sampling percentage control

**Inputs**:
```hcl
workspace_name         # Log Analytics Workspace name
workspace_sku          # SKU (PerGB2018, Free, CapacityReservation)
app_insights_name      # Application Insights name
retention_days         # Log retention (default: 30)
sampling_percentage    # APM sampling (default: 100)
app_service_id         # App Service to monitor
enable_availability_tests  # Enable uptime tests
test_url              # URL for availability tests
enable_smart_detection # Enable anomaly detection
```

**Outputs**:
```hcl
workspace_id           # Log Analytics Workspace ID
app_insights_id        # Application Insights ID
instrumentation_key    # APM instrumentation key
connection_string      # App Insights connection string
action_group_id        # Alert action group ID
query_pack_id          # Query pack ID
availability_test_id   # Availability test ID
```

---

## Module Dependencies

```
resource_group
├── vnet (depends on resource_group)
├── app_service (depends on resource_group, vnet)
├── database (depends on resource_group, vnet)
└── monitoring (depends on resource_group, app_service)
```

## Root Module Variables

All module variables are defined at the root level in `/variables.tf`:

### Environment Variables
- `environment`: dev/staging/prod with validation
- `project_name`: Project identifier
- `location`: Azure region

### Resource Group Variables
- `resource_group_name`: Unique name required

### Networking Variables
- `vnet_name`: VNet name
- `vnet_cidr`: CIDR block
- `subnet_configs`: Map of subnet configurations
- `enable_ddos_protection`: Optional DDoS protection

### App Service Variables
- `app_service_plan_name`: Service plan name
- `app_service_name`: Web app name
- `app_service_plan_sku`: Pricing tier
- `app_runtime_stack`: Runtime environment

### Database Variables
- `db_server_name`: Server name
- `db_database_name`: Database name
- `db_admin_username`: Admin user
- `db_admin_password`: Admin password
- `db_sku_name`: Server SKU
- `db_version`: MySQL version

### Monitoring Variables
- `log_analytics_workspace_name`: Workspace name
- `log_analytics_sku`: Workspace SKU
- `app_insights_name`: Application Insights name
- `log_retention_days`: Retention period

### Feature Flags
- `enable_backup`: Enable automated backups
- `enable_monitoring`: Enable monitoring
- `enable_private_endpoint`: Use private endpoints

## Helper Scripts

### 1. deploy.sh
Automated deployment with validation and confirmation.

```bash
./scripts/deploy.sh dev      # Deploy to dev
./scripts/deploy.sh staging  # Deploy to staging
./scripts/deploy.sh prod     # Deploy to prod
```

Features:
- Prerequisite checking (terraform, az cli)
- Configuration validation
- Plan review
- User confirmation
- Automatic output display

### 2. destroy.sh
Safe infrastructure destruction with multiple confirmations.

```bash
./scripts/destroy.sh dev
```

Features:
- Environment confirmation
- Destruction plan review
- Requires explicit confirmation
- Removes plan file after completion

### 3. output.sh
Display Terraform outputs.

```bash
./scripts/output.sh                    # Show all outputs
./scripts/output.sh app_service_name   # Show specific output
```

### 4. setup-backend.sh
Configure Azure storage backend for remote state.

```bash
./scripts/setup-backend.sh [resource-group] [storage-account]
```

Features:
- Creates resource group
- Creates storage account
- Creates container
- Generates backend configuration
- Option to cleanup local state

---

## File Organization Best Practices

### Root Configuration
- `main.tf`: Provider config, module calls, locals
- `variables.tf`: All variable definitions
- `outputs.tf`: Root-level outputs
- `backend.tf`: Remote state configuration

### Module Structure
Each module follows the standard pattern:
```
module_name/
├── main.tf      # Resource definitions
├── variables.tf # Variable definitions
└── outputs.tf   # Output definitions
```

### Documentation
- `README.md`: Overview and usage
- `DEPLOYMENT_GUIDE.md`: Step-by-step instructions
- `STRUCTURE.md`: Architecture documentation
- `.gitignore`: Version control exclusions

### Scripts
- `/scripts/`: Automation helpers
  - `deploy.sh`: Deployment automation
  - `destroy.sh`: Destruction automation
  - `output.sh`: Output helper
  - `setup-backend.sh`: Backend setup

---

## Variable Precedence (Highest to Lowest)

1. Command-line variables: `-var "key=value"`
2. Environment variables: `TF_VAR_key=value`
3. `.tfvars` file: `terraform apply -var-file="file.tfvars"`
4. `terraform.tfvars` (auto-loaded)
5. Default values in variable definitions

---

## State Management

### Local State (Development)
- Stored in `.terraform/terraform.tfstate`
- Quick for local development
- Not suitable for production
- Should not be committed to git

### Remote State (Production)
- Configure in `backend.tf`
- Stored in Azure Storage Account
- Enables team collaboration
- Supports state locking
- Better for CI/CD pipelines

---

## Naming Conventions

- **Resource Groups**: `rg-{project}-{env}`
- **VNets**: `vnet-{project}-{env}`
- **Subnets**: `{project}-subnet-{purpose}`
- **App Services**: `app-{project}-{env}`
- **Databases**: `mysql-{project}-{env}`
- **Monitoring**: `law-{project}-{env}` (Log Analytics)
- **Alerts**: `{resource}-{condition}`

Example:
- `rg-yawl-dev` (Resource Group)
- `vnet-yawl-dev` (Virtual Network)
- `app-yawl-dev` (App Service)
- `mysql-yawl-dev` (Database)
- `law-yawl-dev` (Log Analytics)

---

## Security Considerations

1. **Sensitive Values**
   - Database passwords marked as `sensitive = true`
   - Use Azure Key Vault for secrets in production
   - Never commit `.tfvars` with real values

2. **Network Security**
   - Private endpoints for database
   - NSG rules restrict traffic
   - VNet integration isolates resources

3. **Access Control**
   - HTTPS enforcement
   - TLS 1.2 minimum
   - No public database endpoints

4. **Monitoring**
   - All resources logged to Log Analytics
   - Real-time alerts configured
   - Diagnostic settings enabled

---

## Cost Optimization

### Development Environment
- Basic/Standard SKUs
- Single instance
- Minimal retention (7 days)
- No geo-redundancy
- Estimated: $50-100/month

### Production Environment
- Premium SKUs
- Multiple instances
- Extended retention (30 days)
- Geo-redundant backups
- DDoS protection
- Estimated: $500-1000+/month

---

## Extending the Configuration

### Adding New Modules

1. Create new directory: `modules/new-module/`
2. Create: `main.tf`, `variables.tf`, `outputs.tf`
3. Add module block in root `main.tf`
4. Add variables in root `variables.tf`
5. Add outputs in root `outputs.tf`

### Adding New Resources

1. Add resource in appropriate module's `main.tf`
2. Add variables to module's `variables.tf`
3. Add outputs to module's `outputs.tf`
4. Update root-level variable definitions if needed

---

## Troubleshooting Guide

See `DEPLOYMENT_GUIDE.md` for comprehensive troubleshooting steps.

---

## References

- [Terraform Azure Provider Documentation](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [Azure Virtual Machines documentation](https://docs.microsoft.com/en-us/azure/)
- [Terraform Best Practices](https://www.terraform.io/docs/cloud/guides/recommended-practices.html)
- [Azure Naming Conventions](https://docs.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/naming-and-tagging)

---

Last Updated: 2024
Version: 1.0
