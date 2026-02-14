# YAWL OCI Terraform - Quick Reference Card

## Essential Commands

### Initial Setup
```bash
cd terraform-oci
cp terraform.tfvars.example terraform.tfvars  # Edit with your values
terraform init                                 # Initialize
terraform validate                             # Validate syntax
```

### Deploy
```bash
terraform plan                                 # Preview changes
terraform apply                                # Deploy infrastructure
```

### Inspect
```bash
terraform output                               # Show all outputs
terraform show                                 # Show full state
terraform state list                           # List resources
```

### Update
```bash
# Edit terraform.tfvars, then:
terraform plan
terraform apply
```

### Remove
```bash
terraform destroy                              # Delete all resources
```

## Key Outputs

```bash
# Load Balancer IP
terraform output -raw load_balancer_ip_address

# Database Endpoint
terraform output -raw mysql_db_endpoint

# Compute Instance IPs
terraform output -raw compute_instance_private_ips

# SSH Details
terraform output compute_instance_ssh_details

# Access Guide
terraform output access_instructions
```

## File Structure

| File | Purpose |
|------|---------|
| `provider.tf` | Provider and version configs |
| `variables.tf` | Input variables |
| `locals.tf` | Local computed values |
| `network.tf` | VCN, subnets, security |
| `compute.tf` | VM instances, storage |
| `database.tf` | MySQL database |
| `load_balancer.tf` | Load balancer, listeners |
| `outputs.tf` | Output values |
| `terraform.tfvars` | Your configuration (not in git) |

## Variable Essentials

### Required
```hcl
oci_tenancy_ocid     # From OCI Console
oci_user_ocid        # From OCI Console
oci_fingerprint      # From API Key setup
oci_private_key      # Path to private key
compartment_ocid     # Your compartment
ssh_public_key       # Your SSH public key
mysql_admin_password # Database admin password
```

### Common Customizations
```hcl
oci_region                = "us-phoenix-1"       # Change region
environment               = "production"          # dev/staging/production
compute_instance_count    = 2                     # Scale up/down
compute_shape             = "VM.Standard.E2.1.Micro"
mysql_backup_retention_days = 7                  # Backup period
```

## Network Defaults

| Component | CIDR | Details |
|-----------|------|---------|
| VCN | 10.0.0.0/16 | Main network |
| Public Subnet | 10.0.1.0/24 | Load balancer |
| Private Subnet | 10.0.2.0/24 | Application servers |
| Database Subnet | 10.0.3.0/24 | MySQL database |

## Security Groups

| Component | Ports | Traffic |
|-----------|-------|---------|
| Public LB | 80, 443 | Internet → LB |
| Compute | 8080, 22 | LB → App Servers |
| Database | 3306 | App → MySQL |

## Compute Instance Details

- **Default Shape:** VM.Standard.E2.1.Micro (free tier)
- **OS:** Ubuntu 22.04 LTS
- **User:** ubuntu
- **Default Count:** 2 instances
- **Storage:** 50GB root + 100GB block volume per instance

## Database Details

- **Type:** MySQL DBaaS (fully managed)
- **Default Version:** 8.0
- **Default Shape:** MySQL.VM.Standard.E3.1.8GB
- **Backup:** Daily, 7-day retention
- **HA:** Enabled by default
- **Port:** 3306 (MySQL), 33060 (X Protocol)

## Load Balancer Details

- **Shape:** Flexible
- **Bandwidth:** 10-100 Mbps (configurable)
- **Listeners:** HTTP (redirects to HTTPS) + HTTPS
- **Backend:** Round-robin to compute instances
- **Health Check:** /health endpoint
- **SSL:** Self-signed (replace for production)

## SSH Access Examples

```bash
# List instances and IPs
terraform output -json compute_instance_ssh_details

# SSH to instance (from bastion or VPN)
ssh -i ~/.ssh/yawl_oci ubuntu@10.0.2.10

# Copy SSH key to instance
ssh-copy-id -i ~/.ssh/yawl_oci.pub ubuntu@10.0.2.10
```

## Database Access Examples

```bash
# Get endpoint
DB=$(terraform output -raw mysql_db_endpoint)

# Connect as admin
mysql -h $DB -u admin -p

# Connect with app user
mysql -h $DB -u yawl_app -p -D yawldb

# Backup database
mysqldump -h $DB -u admin -p yawldb > backup.sql

# Restore database
mysql -h $DB -u admin -p yawldb < backup.sql
```

## Common Tasks

### Scale Instances
```hcl
# In terraform.tfvars
compute_instance_count = 3  # Was 2
```
```bash
terraform apply
```

### Change Instance Type
```hcl
# In terraform.tfvars
compute_shape = "VM.Standard.E3.Flex"
compute_ocpus = 2
```
```bash
terraform apply
```

### Increase Database Size
```hcl
# In terraform.tfvars
mysql_shape = "MySQL.VM.Standard.E3.2.16GB"
```
```bash
terraform apply
```

### Add Tags
```hcl
# In terraform.tfvars
tags = {
  "Owner"   = "DevOps Team"
  "Version" = "2.0"
}
```
```bash
terraform apply
```

## Troubleshooting Quick Fixes

| Issue | Solution |
|-------|----------|
| Auth error | Check OCI credentials in terraform.tfvars |
| Region error | Update oci_region to valid region |
| Quota error | Use smaller shape or reduce count |
| Password error | Check password meets complexity requirements |
| SSH key error | Verify ssh_public_key path is correct |

## Cost Estimation

| Component | Monthly Cost |
|-----------|--------------|
| 2x VM.E2.1.Micro | Free (750 hrs included) |
| 100GB Block Storage | $5-10 |
| MySQL 1x8GB | $50-80 |
| Load Balancer | $0+ (usage-based) |
| **Total Estimate** | **$60-100** |

*Note: Actual costs vary by region and usage*

## OCI Console Shortcuts

```bash
# Open OCI Console
open https://console.oracle.com/

# View Compute Instances
# Identity & Security → Compartments → Your Compartment
# → Compute → Instances

# View MySQL Database
# Databases → MySQL Database Systems

# View Load Balancer
# Networking → Load Balancers

# View VCN
# Networking → Virtual Cloud Networks
```

## Useful Terraform Commands

```bash
# Format files
terraform fmt -recursive .

# Show specific resource
terraform show 'oci_core_instance.yawl_instances[0]'

# Taint resource (force replace)
terraform taint 'oci_core_instance.yawl_instances[0]'

# Show execution plan (human-readable)
terraform plan -out=plan.tfplan
terraform show plan.tfplan

# Destroy specific resource
terraform destroy -target 'oci_core_instance.yawl_instances[0]'

# Import existing resource
terraform import 'oci_core_instance.yawl_instances[0]' ocid1.instance.oc1...

# Validate and format all
terraform fmt -recursive .
terraform validate
```

## Environment-Specific Configs

### Development
```hcl
environment = "dev"
compute_instance_count = 1
mysql_enable_high_availability = false
mysql_backup_retention_days = 1
```

### Staging
```hcl
environment = "staging"
compute_instance_count = 2
mysql_enable_high_availability = true
mysql_backup_retention_days = 7
```

### Production
```hcl
environment = "production"
compute_instance_count = 3
mysql_enable_high_availability = true
mysql_backup_retention_days = 30
```

## Documentation

| File | Purpose |
|------|---------|
| `README.md` | Full architecture and overview |
| `CONFIGURATION.md` | Detailed configuration guide |
| `DEPLOYMENT_GUIDE.md` | Step-by-step deployment |
| `QUICK_REFERENCE.md` | This file |

## Make Commands

```bash
make help              # Show all commands
make init              # Initialize Terraform
make plan              # Plan deployment
make apply             # Apply changes
make destroy           # Destroy infrastructure
make output            # Show outputs
make validate          # Validate config
make fmt               # Format files
make cost-estimate     # Show estimated costs
make clean             # Clean temporary files
```

## Important Reminders

⚠️ **NEVER:**
- Commit `terraform.tfvars` to git
- Commit `.tfstate` files to git
- Share API credentials
- Use default passwords

✅ **ALWAYS:**
- Use `.gitignore` to exclude sensitive files
- Backup terraform.tfstate
- Enable backups for database
- Monitor costs and usage
- Test disaster recovery
- Use strong passwords

## Quick Deploy Checklist

- [ ] OCI account and credentials ready
- [ ] SSH key pair generated
- [ ] `terraform.tfvars` configured
- [ ] `terraform validate` passes
- [ ] `terraform plan` reviewed
- [ ] `terraform apply` completed
- [ ] Outputs collected
- [ ] Load balancer responds
- [ ] Database accessible
- [ ] Application running

## Support Resources

- **OCI Docs:** https://docs.oracle.com/
- **Terraform Docs:** https://www.terraform.io/docs
- **OCI Provider:** https://registry.terraform.io/providers/oracle/oci/
- **YAWL Project:** https://github.com/yawl/yawl

---

**Quick Reference v1.0** | February 2026 | YAWL OCI Terraform Module
