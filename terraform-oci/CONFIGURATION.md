# YAWL OCI Terraform Configuration Guide

This guide provides detailed information about configuring the YAWL Terraform modules for Oracle Cloud Infrastructure.

## Quick Start

### 1. Prerequisites
- Terraform >= 1.0
- OCI account with credentials
- SSH key pair
- Text editor

### 2. Get OCI Credentials

#### Create API Signing Key
```bash
mkdir -p ~/.oci
openssl genrsa -out ~/.oci/oci_api_key.pem 2048
openssl rsa -pubout -in ~/.oci/oci_api_key.pem -out ~/.oci/oci_api_key_public.pem
chmod 600 ~/.oci/oci_api_key.pem
```

#### Upload Public Key to OCI Console
1. Go to OCI Console → Profile → User Settings
2. Click "API Keys"
3. Click "Add API Key"
4. Select "Paste Public Key"
5. Paste contents of `~/.oci/oci_api_key_public.pem`
6. Copy the fingerprint

#### Get Required OCIDs
1. Tenancy OCID: Profile dropdown → Tenancy (copy from page)
2. User OCID: Profile dropdown → User Settings (copy from page)
3. Compartment OCID: Go to Identity → Compartments (select your compartment, copy OCID)

### 3. Generate SSH Key

```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/yawl_oci -C "yawl@oci" -N ""
```

### 4. Configure Terraform Variables

```bash
cd terraform-oci
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
# OCI Credentials
oci_tenancy_ocid = "ocid1.tenancy.oc1..."          # Your Tenancy OCID
oci_user_ocid    = "ocid1.user.oc1..."             # Your User OCID
oci_fingerprint  = "12:34:56:78:90:ab:cd:ef:..."   # Your API Key Fingerprint
oci_private_key  = file("~/.oci/oci_api_key.pem")  # Path to private key

# Environment
compartment_ocid = "ocid1.compartment.oc1..."      # Your Compartment OCID
oci_region       = "us-phoenix-1"                  # Your region
environment      = "production"

# SSH Key
ssh_public_key   = file("~/.ssh/yawl_oci.pub")     # Your SSH public key

# Database
mysql_admin_password = "YourSecurePassword123!"    # Strong password required

# Tags
common_tags = {
  "Environment" = "production"
  "Project"     = "YAWL"
  "Owner"       = "Your Team"
  "CostCenter"  = "Engineering"
}
```

### 5. Initialize and Deploy

```bash
terraform init
terraform plan
terraform apply
```

## Variable Reference

### OCI Provider Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `oci_tenancy_ocid` | string | Yes | - | OCI Tenancy OCID |
| `oci_user_ocid` | string | Yes | - | OCI User OCID |
| `oci_fingerprint` | string | Yes | - | OCI API Key Fingerprint |
| `oci_private_key` | string | Yes | - | OCI Private Key content |
| `oci_region` | string | No | us-phoenix-1 | OCI Region |

### Environment Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `environment` | string | No | production | Environment (dev/staging/production) |
| `project_name` | string | No | yawl | Project name for resource naming |
| `compartment_ocid` | string | Yes | - | OCI Compartment OCID |

### Network Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `vcn_cidr` | string | No | 10.0.0.0/16 | VCN CIDR block |
| `public_subnet_cidr` | string | No | 10.0.1.0/24 | Public subnet CIDR |
| `private_subnet_cidr` | string | No | 10.0.2.0/24 | Private subnet CIDR |
| `database_subnet_cidr` | string | No | 10.0.3.0/24 | Database subnet CIDR |
| `availability_domain` | string | No | 1 | Availability domain (1, 2, or 3) |

### Compute Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `compute_instance_count` | number | No | 2 | Number of compute instances (1-10) |
| `compute_shape` | string | No | VM.Standard.E2.1.Micro | OCI Compute shape |
| `compute_ocpus` | number | No | 1 | OCPUs per instance |
| `compute_memory_gb` | number | No | 6 | Memory in GB per instance |
| `compute_image_os` | string | No | Ubuntu | Operating system |
| `compute_image_version` | string | No | 22.04 | OS version |
| `ssh_public_key` | string | Yes | - | SSH public key for instances |

**Available Compute Shapes:**
- Free Tier: `VM.Standard.E2.1.Micro` (750 hours/month free)
- Standard: `VM.Standard.E2.1.Small`, `VM.Standard.E3.Flex`
- High Performance: `VM.Optimized3.Flex`, `VM.GPU3.1`

### Database Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `database_display_name` | string | No | yawl-mysql-db | Database display name |
| `mysql_db_version` | string | No | 8.0 | MySQL version |
| `mysql_db_name` | string | No | yawldb | Initial database name |
| `mysql_admin_username` | string | No | admin | Admin username |
| `mysql_admin_password` | string | Yes | - | Admin password (sensitive) |
| `mysql_shape` | string | No | MySQL.VM.Standard.E3.1.8GB | Database shape |
| `mysql_backup_retention_days` | number | No | 7 | Backup retention period |
| `mysql_enable_high_availability` | bool | No | true | Enable MySQL HA |

**Available Database Shapes:**
- Development: `MySQL.VM.Standard.E3.1.8GB`
- Production: `MySQL.VM.Standard.E3.2.16GB`, `MySQL.VM.Standard.E3.4.32GB`

**Password Requirements:**
- Minimum 8 characters
- Must contain uppercase letters (A-Z)
- Must contain lowercase letters (a-z)
- Must contain numbers (0-9)
- Must contain special characters (!@#$%^&*)

Example: `YawL@2024#Secure!`

### Load Balancer Variables

| Variable | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `load_balancer_display_name` | string | No | yawl-lb | LB display name |
| `load_balancer_shape` | string | No | flexible | LB shape |
| `load_balancer_min_bandwidth` | number | No | 10 | Minimum bandwidth (Mbps) |
| `load_balancer_max_bandwidth` | number | No | 100 | Maximum bandwidth (Mbps) |
| `lb_listener_port` | number | No | 443 | LB listener port |
| `lb_backend_port` | number | No | 8080 | Backend application port |

## Configuration Examples

### Development Environment

```hcl
environment                   = "dev"
compute_instance_count        = 1
compute_shape                 = "VM.Standard.E2.1.Micro"
compute_ocpus                 = 1
compute_memory_gb             = 6
mysql_shape                   = "MySQL.VM.Standard.E3.1.8GB"
mysql_enable_high_availability = false
load_balancer_max_bandwidth   = 10
mysql_backup_retention_days   = 1

common_tags = {
  "Environment" = "dev"
  "CostCenter"  = "R&D"
}
```

**Estimated Monthly Cost**: $20-30

### Staging Environment

```hcl
environment                   = "staging"
compute_instance_count        = 2
compute_shape                 = "VM.Standard.E2.1.Small"
compute_ocpus                 = 1
compute_memory_gb             = 8
mysql_shape                   = "MySQL.VM.Standard.E3.2.16GB"
mysql_enable_high_availability = true
load_balancer_max_bandwidth   = 50
mysql_backup_retention_days   = 7

common_tags = {
  "Environment" = "staging"
  "CostCenter"  = "QA"
}
```

**Estimated Monthly Cost**: $80-120

### Production Environment

```hcl
environment                   = "production"
compute_instance_count        = 3
compute_shape                 = "VM.Standard.E3.Flex"
compute_ocpus                 = 2
compute_memory_gb             = 16
mysql_shape                   = "MySQL.VM.Standard.E3.4.32GB"
mysql_enable_high_availability = true
load_balancer_max_bandwidth   = 200
mysql_backup_retention_days   = 14

common_tags = {
  "Environment" = "production"
  "CostCenter"  = "Operations"
}
```

**Estimated Monthly Cost**: $200-400

## Validation Rules

### Compute Instance Count
- Minimum: 1
- Maximum: 10
- Recommended for HA: 3+

### Database Password
```
Pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/
```

### Environment
- Allowed values: `dev`, `staging`, `production`
- Used for resource naming and configuration

### Availability Domains
- Format: 1, 2, or 3
- Check available ADs in your region
- Cannot be changed after deployment

## Networking Considerations

### IP Address Planning

Default CIDR allocation:
```
VCN CIDR: 10.0.0.0/16
├── Public Subnet: 10.0.1.0/24 (256 IPs)
├── Private Subnet: 10.0.2.0/24 (256 IPs)
└── Database Subnet: 10.0.3.0/24 (256 IPs)
```

**Custom Example:**
```hcl
vcn_cidr             = "172.16.0.0/16"
public_subnet_cidr   = "172.16.10.0/24"
private_subnet_cidr  = "172.16.20.0/24"
database_subnet_cidr = "172.16.30.0/24"
```

### Security Group Rules

Automatically configured for:
- HTTP (80) → LB ingress
- HTTPS (443) → LB ingress
- SSH (22) → LB ingress (restricted)
- MySQL (3306) → App to DB
- App Port (8080) → LB to App

### Private IP Assignment

Compute instances receive private IPs:
```
Instance 1: 10.0.2.10
Instance 2: 10.0.2.11
Instance 3: 10.0.2.12 (if created)
```

## Database Configuration

### MySQL Version Compatibility

- MySQL 5.7: Basic support
- MySQL 8.0: **Recommended** (features, performance)
- MySQL 8.1: Latest (new features)

### Backup Strategy

#### Development
```hcl
mysql_backup_retention_days = 1  # Only 1 day
```

#### Staging
```hcl
mysql_backup_retention_days = 7  # 1 week
```

#### Production
```hcl
mysql_backup_retention_days = 30  # 1 month
```

### High Availability

Enable for production:
```hcl
mysql_enable_high_availability = true
```

Benefits:
- Automatic failover
- Read replicas
- Increased availability
- Higher cost

## Tags and Metadata

### Recommended Tags

```hcl
common_tags = {
  "Project"      = "YAWL"
  "Environment"  = "production"
  "ManagedBy"    = "Terraform"
  "Owner"        = "DevOps Team"
  "CostCenter"   = "Engineering"
  "Backup"       = "daily"
  "CreatedDate"  = "2024-02-14"
}
```

### Cost Allocation Tags

Use for chargeback:
```hcl
tags = {
  "CostCenter"  = "12345"
  "Department"  = "Engineering"
  "Project"     = "YAWL"
  "BillingCode" = "CC-001"
}
```

## Update Procedure

### Updating Variables

1. Update `terraform.tfvars`
2. Run `terraform plan` to review changes
3. Run `terraform apply` to implement

### Safe Update Order

1. **Tags** - No downtime
2. **Network** - Minimal impact
3. **Compute** - Plan for downtime
4. **Database** - Plan maintenance window
5. **Load Balancer** - Minimal impact

### Rolling Updates

Scale down, then up:
```hcl
# Phase 1: Scale to 1 instance
compute_instance_count = 1

# Phase 2: Update instance
compute_shape = "VM.Standard.E3.Flex"

# Phase 3: Scale back up
compute_instance_count = 3
```

## Troubleshooting

### Invalid Credentials
```
Error: 401 Unauthorized
```
**Solution:** Verify OCI credentials in `terraform.tfvars`

### Region Not Available
```
Error: Region unavailable in this tenancy
```
**Solution:** Check available regions in your account

### Insufficient Quota
```
Error: ServiceQuotaExceeded
```
**Solution:** Request quota increase or use different shape

### Variable Validation Failed
```
Error: Validation failed
```
**Solution:** Check variable value against constraints

## Security Best Practices

### Credentials
- Never commit `terraform.tfvars`
- Use environment variables for sensitive data
- Rotate API keys regularly
- Use OCI Vault for secrets

### Network
- Keep private subnets isolated
- Use Network Security Groups
- Enable VPN for remote access
- Monitor security group changes

### Database
- Use strong passwords
- Enable encryption
- Enable automated backups
- Test backup restoration
- Monitor access logs

### Infrastructure
- Keep Terraform state secure
- Enable audit logging
- Use tags for cost allocation
- Regular security reviews
- Update OS and packages

## Performance Tuning

### Database Performance
```hcl
# Production optimization
mysql_shape = "MySQL.VM.Standard.E3.4.32GB"
mysql_enable_high_availability = true
```

### Compute Performance
```hcl
# High-traffic scenario
compute_instance_count = 5
compute_shape = "VM.Standard.E3.Flex"
compute_ocpus = 4
compute_memory_gb = 32
```

### Load Balancer Performance
```hcl
# High-bandwidth scenario
load_balancer_max_bandwidth = 400
```

## Compliance and Governance

### Required Tags for Compliance
```hcl
common_tags = {
  "DataClassification" = "public"
  "Compliance"         = "SOC2"
  "BackupRequired"     = "true"
  "EncryptionRequired" = "true"
}
```

### Audit Logging
Enable in OCI Console for:
- API calls
- Resource changes
- Data access

### Data Retention
```hcl
mysql_backup_retention_days = 90  # 3 months
```

---

For detailed information on specific topics, refer to:
- `README.md` - Architecture and overview
- `terraform-oci/` - Configuration files
- OCI Documentation - https://docs.oracle.com/
