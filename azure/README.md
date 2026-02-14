# YAWL Workflow Engine - Azure ARM Templates

This directory contains production-ready Azure Resource Manager (ARM) templates for deploying YAWL Workflow Engine on Microsoft Azure.

## Overview

The deployment includes a fully configured, enterprise-grade infrastructure with:

### Core Services
- **App Service** - Linux Tomcat 9.0 with Java 11
- **Azure Database for PostgreSQL** - Version 14 with HA and backups
- **Application Gateway** - WAF v2 with SSL/TLS and autoscaling
- **Azure Key Vault** - Premium tier for secret management
- **Virtual Network** - Private, isolated network architecture

### Monitoring & Observability
- **Application Insights** - Real-time application monitoring
- **Log Analytics** - Centralized logging and analytics
- **Metric Alerts** - Automated alerting for critical thresholds
- **Diagnostic Settings** - Comprehensive resource logging

### Security & Compliance
- **Managed Identity** - Secure authentication without passwords
- **Network Security Groups** - Network traffic control
- **Private Subnets** - Database isolation
- **SSL/TLS Enforcement** - End-to-end encryption
- **Soft Delete & Purge Protection** - Data protection in Key Vault

### Availability & Performance
- **Auto-scaling** - CPU and memory-based scaling
- **Geo-redundant Backups** - Daily backups with 30-day retention
- **Connection Pooling** - Optimized database connections
- **Session Affinity** - Sticky sessions via Application Gateway
- **Health Probes** - Continuous endpoint monitoring

## Files

### Core Templates
- **`azuredeploy.json`** - Main ARM template with all resources (860+ lines)
- **`parameters.json`** - Parameter values for production deployment

### Deployment Scripts
- **`deploy.sh`** - Bash deployment script for Linux/macOS
- **`deploy.ps1`** - PowerShell deployment script for Windows

### Documentation
- **`README.md`** - This file
- **`DEPLOYMENT_GUIDE.md`** - Detailed deployment guide (600+ lines)
- **`CONFIG.md`** - Configuration reference (to be created)
- **`TROUBLESHOOTING.md`** - Troubleshooting guide (to be created)

## Quick Start

### Prerequisites
```bash
# Azure CLI
az --version  # Requires 2.30+

# Bash (Linux/macOS)
bash --version

# PowerShell (Windows)
pwsh --version
```

### Deployment (Linux/macOS)
```bash
# Make script executable
chmod +x deploy.sh

# Run deployment
./deploy.sh production eastus

# OR for staging
./deploy.sh staging eastus

# OR for development
./deploy.sh development eastus
```

### Deployment (Windows)
```powershell
# Set execution policy if needed
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

# Run deployment
.\deploy.ps1 -Environment production -Region eastus

# OR with dry-run
.\deploy.ps1 -Environment production -Region eastus -DryRun
```

### Manual Deployment via Azure CLI
```bash
# Set variables
export AZURE_SUBSCRIPTION_ID="your-subscription-id"
export AZURE_RESOURCE_GROUP="yawl-production"
export AZURE_LOCATION="eastus"
export ENVIRONMENT="production"

# Create resource group
az group create \
  --name $AZURE_RESOURCE_GROUP \
  --location $AZURE_LOCATION

# Validate template
az deployment group validate \
  --resource-group $AZURE_RESOURCE_GROUP \
  --template-file azuredeploy.json \
  --parameters parameters.json \
  --parameters \
    environment=$ENVIRONMENT \
    location=$AZURE_LOCATION \
    databaseAdminPassword="YourSecurePassword123!"

# Deploy
az deployment group create \
  --resource-group $AZURE_RESOURCE_GROUP \
  --template-file azuredeploy.json \
  --parameters parameters.json \
  --parameters \
    environment=$ENVIRONMENT \
    location=$AZURE_LOCATION \
    databaseAdminPassword="YourSecurePassword123!"

# Retrieve outputs
az deployment group show \
  --resource-group $AZURE_RESOURCE_GROUP \
  --query properties.outputs \
  --output json
```

## Deployment Scenarios

### Production (High-Performance)
```bash
./deploy.sh production eastus
```
**Configuration:**
- App Service: P3V2 (5 instances)
- Database: Standard_D8s_v3 (8 vCPU, 32 GB)
- Storage: 256 GB with geo-redundant backups
- Application Gateway: WAF v2 (3-20 units)
- **Estimated Cost: $1,500-1,800/month**

### Staging (Balanced)
```bash
./deploy.sh staging eastus
```
**Configuration:**
- App Service: P1V2 (2 instances)
- Database: Standard_B4ms (4 vCPU, 16 GB)
- Storage: 64 GB
- Application Gateway: Standard v2 (1-5 units)
- **Estimated Cost: $600-800/month**

### Development (Cost-Optimized)
```bash
./deploy.sh development eastus
```
**Configuration:**
- App Service: P1V2 (1 instance)
- Database: Standard_B2s (2 vCPU, 4 GB)
- Storage: 32 GB
- Application Gateway: Standard v2 (1-2 units)
- **Estimated Cost: $300-400/month**

## Template Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Azure Subscription                    │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────────────────────────────────────┐    │
│  │            Virtual Network (10.0.0.0/16)       │    │
│  │                                                 │    │
│  │  ┌──────────────────────────────────────────┐ │    │
│  │  │  App Gateway Subnet (10.0.1.0/24)        │ │    │
│  │  │  ┌────────────────────────────────────┐  │ │    │
│  │  │  │ Application Gateway (WAF v2)       │  │ │    │
│  │  │  │ - SSL/TLS Termination              │  │ │    │
│  │  │  │ - Autoscaling: 2-10 units          │  │ │    │
│  │  │  │ - Connection: App Service          │  │ │    │
│  │  │  └────────────────────────────────────┘  │ │    │
│  │  └──────────────────────────────────────────┘ │    │
│  │                     ↓                          │    │
│  │  ┌──────────────────────────────────────────┐ │    │
│  │  │  App Service Subnet (10.0.2.0/24)        │ │    │
│  │  │  ┌────────────────────────────────────┐  │ │    │
│  │  │  │ App Service (Tomcat 9.0 + Java11) │  │ │    │
│  │  │  │ - Linux Premium Plans (P1V2-P3V2) │  │ │    │
│  │  │  │ - 3-10 Instances (Autoscaling)    │  │ │    │
│  │  │  │ - Always On                       │  │ │    │
│  │  │  │ - Managed Identity                │  │ │    │
│  │  │  │ - Application Insights            │  │ │    │
│  │  │  └────────────────────────────────────┘  │ │    │
│  │  └──────────────────────────────────────────┘ │    │
│  │                     ↓                          │    │
│  │  ┌──────────────────────────────────────────┐ │    │
│  │  │  Database Subnet (10.0.3.0/24)           │ │    │
│  │  │  ┌────────────────────────────────────┐  │ │    │
│  │  │  │ PostgreSQL 14 Database             │  │ │    │
│  │  │  │ - Standard_D4s_v3 (4 vCPU, 16 GB)  │  │ │    │
│  │  │  │ - 128 GB Storage (Autogrow)        │  │ │    │
│  │  │  │ - SSL/TLS Required                 │  │ │    │
│  │  │  │ - Geo-redundant Backups (30 days)  │  │ │    │
│  │  │  │ - High Availability                │  │ │    │
│  │  │  └────────────────────────────────────┘  │ │    │
│  │  └──────────────────────────────────────────┘ │    │
│  └────────────────────────────────────────────────┘    │
│                                                           │
│  ┌──────────────────┐  ┌──────────────────┐            │
│  │  Key Vault       │  │  Storage Account │            │
│  │  (Premium)       │  │  (GRS)           │            │
│  │  - Secrets       │  │  - Backups       │            │
│  │  - Keys          │  │  - Diagnostics   │            │
│  │  - Certificates  │  │  - Logs          │            │
│  └──────────────────┘  └──────────────────┘            │
│                                                           │
│  ┌──────────────────┐  ┌──────────────────┐            │
│  │ Application      │  │ Log Analytics    │            │
│  │ Insights         │  │ Workspace        │            │
│  │ - Performance    │  │ - Centralized    │            │
│  │ - Diagnostics    │  │   Logging        │            │
│  │ - Monitoring     │  │ - Query & Alerts │            │
│  └──────────────────┘  └──────────────────┘            │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

## Key Features

### Autoscaling
- **App Service**: CPU > 70% adds 1 instance (max 10)
- **App Service**: CPU < 30% removes 1 instance (min 3)
- **App Service**: Memory > 80% adds 1 instance
- **Application Gateway**: 2-10 units based on capacity

### High Availability
- Multiple App Service instances with load balancing
- PostgreSQL regional high availability
- Geo-redundant backups across regions
- Session affinity via Application Gateway
- Automatic health checks and recovery

### Security
- End-to-end TLS/TLS encryption
- Private subnets for database isolation
- Network Security Groups (NSG) for traffic control
- Managed Identity for secure authentication
- Key Vault for secret management
- WAF with OWASP CRS 3.2 ruleset

### Monitoring
- Real-time Application Insights metrics
- Centralized logging in Log Analytics
- Automated alerts for high CPU/memory/storage
- Diagnostic settings for all major components
- Query analytics for database performance

## Resource Naming Convention

All resources follow a consistent naming convention:

```
{projectName}-{resourceType}-{environment}-{uniqueSuffix}
```

Examples:
- `yawl-asp-production-a1b2c3d4`
- `yawl-app-production-a1b2c3d4`
- `yawl-db-production-a1b2c3d4`
- `yawl-appgw-production-a1b2c3d4`
- `yawl-kv-production-a1b2c3d4`

## Customization

### Modify SKUs
Edit `parameters.json` to change resource sizes:

```json
{
  "appServicePlanSku": {
    "value": "P3V2"  // Change from P2V2 to P3V2
  },
  "databaseSkuName": {
    "value": "Standard_D8s_v3"  // Increase compute
  },
  "databaseStorageSizeGB": {
    "value": 256  // Increase storage
  }
}
```

### Add Custom Domain
```bash
# Add custom domain
az webapp config hostname add \
  --resource-group yawl-production \
  --webapp-name yawl-app-production-* \
  --hostname yawl.example.com

# Add SSL certificate
az webapp config ssl bind \
  --resource-group yawl-production \
  --name yawl-app-production-* \
  --certificate-file cert.pfx \
  --certificate-password $CERT_PASSWORD
```

### Configure CI/CD
```bash
# Enable continuous deployment
az webapp deployment container config \
  --resource-group yawl-production \
  --name yawl-app-production-* \
  --enable-continuous-deployment true
```

## Cost Optimization

### Tips for reducing costs:
1. **Use Standard tier** for non-critical environments
2. **Reduce backup retention** from 30 to 7 days
3. **Right-size database** SKU based on actual workload
4. **Use reserved instances** (30% savings)
5. **Enable auto-shutdown** for dev environments
6. **Scale down min instances** from 3 to 1 for staging

### Cost breakdown:
- App Service: $150-450/month
- PostgreSQL: $400-1000/month
- Application Gateway: $150-300/month
- Key Vault: $25/month
- Application Insights: $30-100/month
- Log Analytics: $20-50/month
- Storage: $20-50/month
- **Total: $800-2000/month** (depending on configuration)

## Troubleshooting

### Common Issues

**Deployment fails with "Invalid template"**
- Validate template: `az deployment group validate --template-file azuredeploy.json`
- Check JSON syntax in editor
- Ensure all parameters are provided

**App Service not accessible**
- Check Application Gateway health probes
- Verify backend address pool configuration
- Review NSG rules for port 80/443

**Database connection failures**
- Verify firewall rules allow VNet traffic
- Check database credentials in Key Vault
- Ensure database server is running

**High costs**
- Review resource utilization in metrics
- Consider right-sizing SKUs
- Check for unused resources

See `DEPLOYMENT_GUIDE.md` for detailed troubleshooting steps.

## Security Considerations

1. **Passwords**: Use strong, complex passwords for database admin
2. **Secrets**: Store all sensitive data in Key Vault
3. **Certificates**: Use valid SSL/TLS certificates
4. **Access Control**: Restrict resource access via RBAC
5. **Monitoring**: Enable diagnostic logging for compliance
6. **Backups**: Test restore procedures regularly
7. **Updates**: Keep Azure CLI and tools updated

## Support & Documentation

- **YAWL Foundation**: https://www.yawlfoundation.org
- **Azure Documentation**: https://docs.microsoft.com/azure/
- **Deployment Issues**: https://github.com/yawlfoundation/yawl/issues
- **Community Forum**: https://forum.yawlfoundation.org

## Version Information

- **Template Version**: 1.0.0
- **YAWL Version**: 5.2
- **ARM API Version**: 2019-04-01
- **Last Updated**: 2024-02-14

## License

YAWL is distributed under the GNU LGPL 3.0 License.
These ARM templates are provided as-is for deploying YAWL on Azure.

## Contributing

To contribute improvements to these templates:
1. Fork the repository
2. Create a feature branch
3. Make improvements
4. Test thoroughly
5. Submit a pull request

## Change Log

### v1.0.0 (2024-02-14)
- Initial release of production-ready ARM templates
- Complete infrastructure for YAWL deployment
- Bash and PowerShell deployment scripts
- Comprehensive documentation and guides

---

**Need Help?** See `DEPLOYMENT_GUIDE.md` for detailed instructions or contact support@yawlfoundation.org
