# YAWL on Azure - Complete Deployment Package

## Quick Navigation

### Getting Started (Start Here!)
1. **[README.md](README.md)** - Overview and quick start guide
2. **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** - Comprehensive deployment instructions
3. **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Issue resolution and diagnostics

### Deployment Artifacts
- **[azuredeploy.json](azuredeploy.json)** - Main ARM template (860+ lines)
  - Virtual Network with 3 subnets
  - App Service with Tomcat 9.0 + Java 11
  - PostgreSQL 14 Database
  - Application Gateway WAF v2
  - Key Vault Premium
  - Application Insights & Log Analytics
  - Managed Identity
  - Auto-scaling policies
  - Monitoring & alerts
  - Diagnostic settings

- **[parameters.json](parameters.json)** - Production parameter values
  - Ready-to-use configuration
  - Customizable for different environments

### Deployment Scripts
- **[deploy.sh](deploy.sh)** - Bash deployment automation (Linux/macOS)
  - Prerequisites validation
  - Template validation
  - Interactive password entry
  - Resource group creation
  - Deployment orchestration
  - Output capture and display
  - Error handling and cleanup

- **[deploy.ps1](deploy.ps1)** - PowerShell deployment (Windows)
  - Full-featured deployment script
  - Dry-run mode support
  - Colored output
  - Validation and confirmation prompts

### Configuration Reference
- **[config-examples.json](config-examples.json)** - Configuration templates
  - 4 deployment scenarios (Dev, Staging, Prod-Balanced, Prod-HighPerf)
  - Azure region information
  - SKU specifications and costs
  - Monitoring configuration
  - Security recommendations
  - Backup strategies
  - Disaster recovery settings

### Documentation
- **[INDEX.md](INDEX.md)** - This file

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Azure Subscription                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Virtual Network: 10.0.0.0/16                                   │
│  ├─ App Gateway Subnet: 10.0.1.0/24                            │
│  │  └─ Application Gateway (WAF v2)                            │
│  │     └─ SSL/TLS, WebSocket, HTTP/2                          │
│  │        └─ Session Affinity, Connection Draining            │
│  │                                                              │
│  ├─ App Service Subnet: 10.0.2.0/24                           │
│  │  └─ App Service Plan (P1V2-P3V2)                           │
│  │     └─ 3-10 Instances (Auto-scaling)                       │
│  │        └─ Tomcat 9.0 + Java 11                             │
│  │           └─ Managed Identity                              │
│  │              └─ Application Insights Agent                 │
│  │                                                              │
│  ├─ Database Subnet: 10.0.3.0/24                              │
│  │  └─ PostgreSQL 14 Server                                   │
│  │     └─ 2-8 vCPU, 4-32 GB RAM                              │
│  │        └─ 32-256 GB Storage                                │
│  │           └─ Geo-redundant Backups (30 days)              │
│  │                                                              │
│  Monitoring & Logging:                                          │
│  ├─ Application Insights (Real-time monitoring)               │
│  ├─ Log Analytics Workspace (Centralized logs)                │
│  ├─ Metric Alerts (CPU, Memory, Storage)                      │
│  ├─ Diagnostic Settings (All resources)                       │
│  │                                                              │
│  Security:                                                      │
│  ├─ Key Vault Premium (Secret management)                     │
│  ├─ Network Security Groups (Traffic control)                 │
│  ├─ Managed Identity (Secure authentication)                  │
│  ├─ Private database subnet (Network isolation)              │
│  ├─ SSL/TLS enforcement (Encryption in transit)              │
│  │                                                              │
│  Storage:                                                       │
│  └─ Storage Account (Geo-redundant, backups, logs)           │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Deployment Scenarios

### Production - High Performance
**Best for:** Large enterprise workloads, high availability requirements

```bash
./deploy.sh production eastus
```

**Configuration:**
- App Service: P3V2 (5 instances)
- Database: Standard_D8s_v3 (8 vCPU, 32 GB RAM)
- Storage: 256 GB, geo-redundant
- AppGW: WAF v2 (3-20 units)
- Backup: 30-day retention, geo-redundant
- **Cost:** ~$1,800/month

### Production - Balanced (Recommended)
**Best for:** General enterprise production, cost-conscious

```bash
./deploy.sh production eastus
# Uses parameters.json defaults
```

**Configuration:**
- App Service: P2V2 (3 instances)
- Database: Standard_D4s_v3 (4 vCPU, 16 GB)
- Storage: 128 GB, geo-redundant
- AppGW: WAF v2 (2-10 units)
- Backup: 30-day retention
- **Cost:** ~$1,325/month

### Staging - Optimized
**Best for:** QA, testing, pre-production

```bash
./deploy.sh staging eastus
```

**Configuration:**
- App Service: P1V2 (2 instances)
- Database: Standard_B4ms (4 vCPU, 16 GB)
- Storage: 64 GB
- AppGW: Standard v2 (1-5 units)
- Backup: 14-day retention
- **Cost:** ~$650/month

### Development - Minimal
**Best for:** Development, POC, non-critical testing

```bash
./deploy.sh development eastus
```

**Configuration:**
- App Service: P1V2 (1 instance)
- Database: Standard_B2s (2 vCPU, 4 GB)
- Storage: 32 GB
- AppGW: Standard v2 (1-2 units)
- Backup: 7-day retention
- **Cost:** ~$350/month

---

## Files Summary

| File | Size | Type | Purpose |
|------|------|------|---------|
| **azuredeploy.json** | 42 KB | Template | Main ARM template with all resources |
| **parameters.json** | 1.9 KB | Config | Production parameter values |
| **deploy.sh** | 11 KB | Script | Bash deployment automation |
| **deploy.ps1** | 13 KB | Script | PowerShell deployment automation |
| **config-examples.json** | 12 KB | Reference | Configuration examples and SKU info |
| **README.md** | 16 KB | Docs | Quick start and overview |
| **DEPLOYMENT_GUIDE.md** | 18 KB | Docs | Detailed deployment instructions |
| **TROUBLESHOOTING.md** | 23 KB | Docs | Problem resolution and diagnostics |
| **INDEX.md** | This | Docs | Navigation and file reference |

---

## Key Features Included

### Compute
✓ App Service (Linux, Tomcat 9.0, Java 11)
✓ 3 deployment tiers (P1V2, P2V2, P3V2)
✓ 1-10 instance auto-scaling
✓ Health probes and auto-recovery
✓ Application Insights integration

### Database
✓ PostgreSQL 14 (Managed)
✓ 2-8 vCPU, 4-32 GB RAM
✓ 32-256 GB storage with auto-grow
✓ Geo-redundant backups (7-30 days)
✓ High Availability regional setup
✓ Automatic failover
✓ Performance Insights enabled

### Networking
✓ Virtual Network with 3 subnets
✓ Application Gateway WAF v2
✓ Network Security Groups
✓ Private database isolation
✓ SSL/TLS enforcement
✓ Session affinity
✓ Connection draining

### Monitoring
✓ Application Insights (Real-time metrics)
✓ Log Analytics (Centralized logging)
✓ Metric Alerts (CPU, Memory, Storage)
✓ Diagnostic Settings (All resources)
✓ Health probes (Liveness + Readiness)
✓ Auto-recovery policies

### Security
✓ Azure Key Vault (Premium)
✓ Managed Identity (No hardcoded secrets)
✓ RBAC integration
✓ Network isolation (Private subnets)
✓ SSL/TLS encryption (In-transit)
✓ Secrets rotation support
✓ Compliance ready (Audit logs)

### Automation
✓ Infrastructure as Code (ARM)
✓ Deployment scripts (Bash + PowerShell)
✓ Auto-scaling policies
✓ Automated backups
✓ Alert automation ready
✓ Infrastructure validation

---

## Deployment Workflow

### Step 1: Preparation
```bash
# Check prerequisites
az --version          # Azure CLI 2.30+
bash --version        # Bash 4+
jq --version          # Optional but recommended

# Authenticate to Azure
az login

# Set environment
export AZURE_SUBSCRIPTION_ID="your-subscription-id"
cd /path/to/yawl/azure
```

### Step 2: Validation
```bash
# Review configuration
cat parameters.json | jq '.'

# Validate template
az deployment group validate \
  --resource-group yawl-production \
  --template-file azuredeploy.json \
  --parameters parameters.json
```

### Step 3: Deployment
```bash
# Run deployment script (Linux/macOS)
chmod +x deploy.sh
./deploy.sh production eastus

# OR on Windows with PowerShell
.\deploy.ps1 -Environment production -Region eastus
```

### Step 4: Verification
```bash
# Get deployment outputs
az deployment group show \
  --resource-group yawl-production \
  --query properties.outputs

# Test connectivity
curl https://<app-service-name>.azurewebsites.net/resourceService/
```

### Step 5: Post-Deployment
```bash
# Configure custom domain (optional)
az webapp config hostname add \
  --resource-group yawl-production \
  --webapp-name <app-service> \
  --hostname yawl.example.com

# Configure CI/CD (optional)
az webapp deployment container config \
  --resource-group yawl-production \
  --name <app-service>

# Monitor application
az webapp log tail \
  --resource-group yawl-production \
  --name <app-service>
```

---

## Cost Estimation

### Production - Balanced (Recommended)
- **App Service**: 3 × P2V2 instances = $300/month
- **PostgreSQL**: Standard_D4s_v3 = $550/month
- **Application Gateway**: WAF v2 (avg 5 units) = $250/month
- **Key Vault**: Premium tier = $25/month
- **Application Insights**: ~30 GB = $50/month
- **Log Analytics**: ~15 GB = $30/month
- **Storage Account**: Backup + logs = $50/month
- **Total**: ~$1,325/month

### Optimization Opportunities
- Reserved instances: 30-72% savings
- Right-sizing: Monitor actual usage
- Backup reduction: 30 → 7 days saves ~$50
- Network optimization: Consolidate resources
- Estimated savings: 20-40% with optimization

---

## Support Resources

### Documentation
- **Azure Documentation**: https://docs.microsoft.com/azure/
- **YAWL Foundation**: https://www.yawlfoundation.org
- **YAWL Documentation**: https://docs.yawlfoundation.org

### Community
- **YAWL Forum**: https://forum.yawlfoundation.org
- **GitHub Issues**: https://github.com/yawlfoundation/yawl/issues
- **Stack Overflow**: Tag `yawl-workflow-engine`

### Support Channels
- **Azure Support**: https://portal.azure.com/#blade/Microsoft_Azure_Support
- **YAWL Support**: support@yawlfoundation.org
- **Commercial Support**: https://www.yawlfoundation.org/support

---

## Quick Command Reference

```bash
# Deployment
./deploy.sh production eastus          # Bash deployment
.\deploy.ps1 -Environment production   # PowerShell deployment

# Validation
az deployment group validate --resource-group <rg> --template-file azuredeploy.json

# Monitoring
az webapp log tail --resource-group <rg> --name <app-name>
az monitor metrics list --resource <resource-id> --metric CpuPercentage

# Database
psql -h <db-fqdn> -U <user>@<db-name> -d <database>
az postgres server show --resource-group <rg> --name <db-name>

# Scaling
az appservice plan update --resource-group <rg> --name <plan> --sku P3V2

# Cleanup
az group delete --name <resource-group> --yes --no-wait
```

---

## Environment Variables Reference

```bash
# Azure
AZURE_SUBSCRIPTION_ID        # Subscription ID
AZURE_TENANT_ID              # Tenant ID
AZURE_LOCATION               # Region (eastus, westus, etc.)
AZURE_RESOURCE_GROUP         # Resource group name

# Deployment
ENVIRONMENT                  # production, staging, development
PROJECT_NAME                 # YAWL (default)
DEPLOYMENT_NAME              # Auto-generated with timestamp

# Database
DB_ADMIN_USER               # yawladmin
DB_ADMIN_PASSWORD           # Strong password (min 8 chars)
DB_NAME                     # yawl

# Application
JAVA_OPTS                   # -Xms512m -Xmx1024m -XX:+UseG1GC
TOMCAT_HEAP_SIZE            # 1024m
LOG_LEVEL                   # INFO, DEBUG, ERROR
```

---

## Security Checklist

Before deploying to production:

- [ ] Use strong database password (16+ chars, special chars)
- [ ] Enable Key Vault soft delete and purge protection
- [ ] Configure NSG rules for your IP ranges
- [ ] Review and adjust database backup retention
- [ ] Enable Application Insights for monitoring
- [ ] Configure alerts for high CPU/memory/storage
- [ ] Test database backup/restore procedures
- [ ] Configure custom domain with SSL certificate
- [ ] Enable VNet integration for App Service
- [ ] Implement application-level authentication
- [ ] Review Key Vault access policies
- [ ] Configure Azure AD authentication (optional)
- [ ] Enable diagnostic logging for all resources
- [ ] Plan disaster recovery procedures
- [ ] Document access credentials and procedures

---

## Performance Tuning

### Application Service
- Use G1GC garbage collector for better pause times
- Monitor heap usage and adjust Xmx as needed
- Enable persistent HTTP connections
- Use connection pooling for database

### Database
- Monitor query performance via Query Store
- Create indexes for frequently queried columns
- Use prepared statements
- Tune shared_buffers and effective_cache_size
- Monitor connection count and adjust max_connections

### Network
- Enable caching at Application Gateway level
- Use content compression for static assets
- Configure connection draining appropriately
- Monitor bandwidth usage

---

## Troubleshooting Quick Links

| Issue | Solution |
|-------|----------|
| App not accessible | See TROUBLESHOOTING.md - App Service Not Accessible |
| 502 Bad Gateway | See TROUBLESHOOTING.md - HTTP 502 Bad Gateway |
| Database connection fails | See TROUBLESHOOTING.md - Cannot Connect to Database |
| High CPU usage | See TROUBLESHOOTING.md - High CPU Utilization |
| Memory issues | See TROUBLESHOOTING.md - High Memory Usage |
| Deployment timeout | See TROUBLESHOOTING.md - Deployment Timeout |
| Template validation error | See TROUBLESHOOTING.md - Template Validation Errors |

---

## Version Information

- **Package Version**: 1.0.0
- **YAWL Version**: 5.2+
- **Tomcat Version**: 9.0
- **Java Version**: 11
- **PostgreSQL Version**: 14
- **ARM API Version**: 2019-04-01
- **Azure CLI Required**: 2.30+
- **Last Updated**: 2024-02-14

---

## License

YAWL is distributed under the GNU LGPL 3.0 License.
These deployment templates are provided as-is for deploying YAWL on Azure.

---

## Changelog

### v1.0.0 (2024-02-14)
- Initial release
- Complete ARM templates with all production features
- Bash and PowerShell deployment scripts
- Comprehensive documentation and guides
- Configuration examples for multiple scenarios
- Detailed troubleshooting guide

---

## Next Steps

1. **Read [README.md](README.md)** for overview and quick start
2. **Review [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** for detailed instructions
3. **Customize [parameters.json](parameters.json)** for your environment
4. **Run deployment script** (deploy.sh or deploy.ps1)
5. **Verify deployment** using Azure Portal or CLI
6. **Monitor with [TROUBLESHOOTING.md](TROUBLESHOOTING.md)** if issues arise

---

**Questions?** Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) or contact support@yawlfoundation.org
