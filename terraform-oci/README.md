# YAWL Terraform Modules for Oracle Cloud Infrastructure (OCI)

This directory contains Terraform modules to deploy YAWL (Yet Another Workflow Language) on Oracle Cloud Infrastructure with a complete infrastructure setup including compute instances, MySQL database, networking, and load balancing.

## Directory Structure

```
terraform-oci/
├── provider.tf              # Terraform and provider configuration
├── variables.tf             # Input variables
├── outputs.tf               # Output values
├── locals.tf                # Local variables and computed values
├── network.tf               # VCN, subnets, security lists, NSGs
├── compute.tf               # VM instances, block storage, vnics
├── database.tf              # MySQL DBaaS, backups, users
├── load_balancer.tf         # Load balancer, backend sets, listeners
├── user_data.sh             # Instance initialization script
├── terraform.tfvars.example # Example configuration values
└── README.md                # This file
```

## Prerequisites

### Required Tools
- Terraform >= 1.0
- OCI CLI configured with credentials
- SSH key pair for instance access

### OCI Setup
1. Create an OCI account and compartment
2. Generate API signing key:
   ```bash
   mkdir -p ~/.oci
   openssl genrsa -out ~/.oci/oci_api_key.pem 2048
   openssl rsa -pubout -in ~/.oci/oci_api_key.pem -out ~/.oci/oci_api_key_public.pem
   ```
3. Upload public key to OCI console
4. Note your Tenancy OCID, User OCID, and Fingerprint

### SSH Setup
Generate SSH key pair for compute instances:
```bash
ssh-keygen -t rsa -b 4096 -f ~/.ssh/yawl_oci -C "yawl@oci"
```

## Configuration

### 1. Create terraform.tfvars

Copy the example file and update with your values:
```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` with:
- OCI credentials (tenancy, user, fingerprint, private key path)
- Compartment OCID
- SSH public key content
- Database password (use a strong password)
- Region and availability domain
- Any other customizations

### 2. Example Configuration

```hcl
oci_tenancy_ocid = "ocid1.tenancy.oc1..."
oci_user_ocid    = "ocid1.user.oc1..."
oci_fingerprint  = "12:34:56:78:90:ab:cd:ef:..."
oci_private_key  = file("~/.oci/oci_api_key.pem")

compartment_ocid = "ocid1.compartment.oc1..."
environment      = "production"
oci_region       = "us-phoenix-1"

ssh_public_key   = file("~/.ssh/yawl_oci.pub")

# Database
mysql_admin_password = "YourStrongPassword123!"

# Compute
compute_instance_count = 2

# Load Balancer
load_balancer_display_name = "yawl-lb"
```

## Deployment

### Initialize Terraform

```bash
cd terraform-oci
terraform init
```

### Plan Deployment

```bash
terraform plan -out=tfplan
```

Review the plan carefully before applying.

### Apply Configuration

```bash
terraform apply tfplan
```

The deployment will create:
- VCN with 3 subnets (public, private, database)
- 2 compute instances running Ubuntu
- MySQL 8.0 database with automatic backups
- Load balancer with HTTPS support
- Security groups and network security groups
- Block storage volumes for application data

### Get Outputs

After deployment, retrieve important information:

```bash
# Get all outputs
terraform output

# Get specific outputs
terraform output load_balancer_ip_address
terraform output mysql_db_endpoint
terraform output compute_instance_private_ips
terraform output access_instructions
```

## Architecture Overview

### Network Architecture
```
Internet
   |
   v
Load Balancer (Public Subnet)
   |
   v
Compute Instances (Private Subnet)
   |
   v
MySQL Database (Database Subnet)
```

### Components

#### 1. Virtual Cloud Network (VCN)
- CIDR: 10.0.0.0/16 (configurable)
- Internet Gateway for public access
- Route tables for public/private routing
- Network Security Groups for fine-grained access control

#### 2. Subnets
- **Public Subnet**: 10.0.1.0/24 - Load balancer
- **Private Subnet**: 10.0.2.0/24 - Application servers
- **Database Subnet**: 10.0.3.0/24 - MySQL database

#### 3. Compute Instances
- Shape: VM.Standard.E2.1.Micro (customizable, free tier eligible)
- OS: Ubuntu 22.04 LTS
- Auto-initialization via user_data.sh
- Block storage volumes for persistent data
- Private IPs with security group access control

#### 4. Database
- MySQL 8.0 DBaaS
- Automatic daily backups
- High availability option
- Encrypted storage
- Subnet isolation

#### 5. Load Balancer
- Shape: Flexible (auto-scaling)
- HTTP (redirects to HTTPS)
- HTTPS with self-signed certificate
- Round-robin backend set
- Session persistence
- Health checks

## Security Features

### Network Security
- Compute instances in private subnet (no direct internet access)
- Database in isolated subnet
- Load balancer in public subnet only
- Security lists restrict traffic to necessary ports

### Access Control
- Security group rules for each tier
- NSG rules for granular control
- SSH access only from specified sources
- Database access only from private subnet

### Data Protection
- MySQL automatic backups (7 days retention)
- Encrypted storage volumes
- Private key encryption for API calls
- Sensitive outputs marked as sensitive in Terraform

## Accessing the Deployment

### Load Balancer
```bash
# Get IP address
LB_IP=$(terraform output -raw load_balancer_ip_address)

# HTTP (redirects to HTTPS)
curl http://$LB_IP/

# HTTPS (self-signed cert warning expected)
curl -k https://$LB_IP/
```

### Compute Instances
To SSH to instances, use a bastion host or setup:

```bash
# Get private IPs
terraform output compute_instance_private_ips

# SSH via bastion/VPN
ssh -i ~/.ssh/yawl_oci ubuntu@10.0.2.10
```

### Database
```bash
# Get connection details
terraform output mysql_db_endpoint
terraform output mysql_db_connection_string

# Connect from compute instance
mysql -h <endpoint> -u admin -p -D yawldb
```

## Management

### Scaling Compute Instances

Update `terraform.tfvars`:
```hcl
compute_instance_count = 3  # Increase from 2 to 3
```

Apply changes:
```bash
terraform apply
```

### Updating Database Configuration

Modify database settings in `terraform.tfvars`:
```hcl
mysql_backup_retention_days   = 14
mysql_enable_high_availability = true
```

### Adding/Removing Tags

Update `terraform.tfvars`:
```hcl
tags = {
  "Owner"   = "New Team"
  "Version" = "2.0"
}
```

## Monitoring and Logging

### Application Logs
Located on compute instances at:
- `/var/log/yawl/` - Application logs
- `/var/log/yawl-init.log` - Initialization log

### Database Backups
Automatic backups stored in OCI Object Storage:
```bash
# List backups via OCI CLI
oci mysql backup list --compartment-id <compartment-ocid>
```

## Cost Estimation

Using OCI Free Tier eligible resources:
- **Compute**: 2x VM.Standard.E2.1.Micro (Free tier - 750 hours/month)
- **Storage**: 100GB block storage (~$5/month after free tier)
- **Database**: MySQL.VM.Standard.E3.1.8GB (~$50-100/month)
- **Load Balancer**: Flexible LB (charges based on traffic)

**Estimated Monthly Cost**: $50-150 (excluding network egress)

## Troubleshooting

### Terraform State Issues
```bash
# Refresh state
terraform refresh

# View state
terraform show

# View specific resource
terraform state show oci_mysql_mysql_db_system.yawl_db
```

### Connectivity Issues
```bash
# Check security group rules
terraform output compute_nsg_id
oci network nsg rules list --nsg-id <nsg-id>

# Check route tables
oci network route-table list --compartment-id <compartment-ocid>
```

### Database Connection Issues
1. Verify MySQL is running: Check instance status
2. Verify security groups allow port 3306
3. Check firewall rules on compute instance
4. Verify database is in ACTIVE state

### Destroy Deployment

⚠️ **Warning**: This permanently deletes all resources.

```bash
terraform destroy
```

## Security Best Practices

1. **Never commit secrets**: Use OCI Vault for sensitive data
2. **Rotate credentials**: Periodically rotate API keys
3. **Use strong passwords**: Database and application passwords
4. **Enable audit logs**: Track API and resource changes
5. **Monitor costs**: Set up billing alerts
6. **Regular backups**: Verify backup integrity
7. **Use HTTPS**: Replace self-signed certificate in production
8. **Update regularly**: Keep OS and software current

## Production Considerations

### High Availability
- Enable MySQL High Availability
- Add more compute instances
- Use load balancer health checks
- Implement auto-scaling

### Disaster Recovery
- Configure backup retention
- Test backup restoration
- Document recovery procedures
- Use multiple availability domains

### Performance
- Monitor CPU/Memory/Disk usage
- Optimize database queries
- Use caching (Redis)
- Implement CDN for static content

### Compliance
- Enable audit logging
- Implement encryption
- Document security measures
- Regular security assessments

## Module Documentation

### network.tf
Manages network infrastructure:
- VCN and subnets
- Internet Gateway
- Route tables
- Security lists and NSGs
- Security rules

### compute.tf
Manages compute resources:
- Compute instances
- Block storage volumes
- Network interfaces (VNICs)
- Instance metadata/user data

### database.tf
Manages database resources:
- MySQL database system
- Database users and privileges
- Automated backups
- Database schemas

### load_balancer.tf
Manages load balancing:
- Load balancer
- Backend sets
- Listeners (HTTP/HTTPS)
- SSL certificates
- Routing rules

## Support and Updates

For issues or feature requests:
1. Check OCI documentation: https://docs.oracle.com/
2. Review Terraform OCI provider: https://registry.terraform.io/providers/oracle/oci/latest
3. YAWL documentation: Review YAWL project docs
4. Community forums: OCI and Terraform communities

## License

These Terraform modules are provided as-is for YAWL deployment on OCI.

## Examples

### Development Environment
```hcl
environment                   = "dev"
compute_instance_count        = 1
compute_shape                 = "VM.Standard.E2.1.Micro"
mysql_shape                   = "MySQL.VM.Standard.E3.1.8GB"
mysql_enable_high_availability = false
load_balancer_max_bandwidth   = 10
```

### Production Environment
```hcl
environment                   = "production"
compute_instance_count        = 3
compute_shape                 = "VM.Standard.E3.Flex"
compute_ocpus                 = 2
compute_memory_gb             = 16
mysql_shape                   = "MySQL.VM.Standard.E3.2.16GB"
mysql_enable_high_availability = true
load_balancer_max_bandwidth   = 200
```

---

**Last Updated**: February 2026
**Terraform Version**: >= 1.0
**OCI Provider Version**: ~> 5.0
