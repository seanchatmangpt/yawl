# YAWL OCI Deployment Guide - Step by Step

This guide provides detailed step-by-step instructions for deploying YAWL on Oracle Cloud Infrastructure using Terraform.

## Prerequisites Checklist

Before starting, ensure you have:

- [ ] OCI Account with active subscription
- [ ] Terraform installed (version >= 1.0)
  ```bash
  terraform --version
  ```
- [ ] OCI CLI installed
  ```bash
  oci --version
  ```
- [ ] SSH key pair for instance access
- [ ] Git installed (optional, for version control)
- [ ] Text editor (VS Code, Vim, Nano, etc.)

## Part 1: OCI Account Setup

### Step 1.1: Create OCI Compartment

1. Log into OCI Console: https://console.oracle.com/
2. Navigate to Identity & Security → Compartments
3. Click "Create Compartment"
4. Fill in details:
   - Name: `yawl-deployment`
   - Description: `Compartment for YAWL infrastructure`
5. Click "Create Compartment"
6. Copy the Compartment OCID

### Step 1.2: Create API Signing Key

1. Click your profile picture (top right)
2. Select "User Settings"
3. Scroll down to "API Keys"
4. Click "Add API Key"
5. Run on your local machine:
   ```bash
   # Create directory
   mkdir -p ~/.oci

   # Generate private key
   openssl genrsa -out ~/.oci/oci_api_key.pem 2048

   # Generate public key
   openssl rsa -pubout -in ~/.oci/oci_api_key.pem \
     -out ~/.oci/oci_api_key_public.pem

   # Set permissions
   chmod 600 ~/.oci/oci_api_key.pem

   # Display public key
   cat ~/.oci/oci_api_key_public.pem
   ```

6. In OCI Console, select "Paste Public Key"
7. Paste the public key content
8. Click "Add"
9. **Important**: Copy and save the Fingerprint

### Step 1.3: Get Your OCIDs

Collect these values (you'll need them):

**Tenancy OCID:**
1. Click profile → Tenancy
2. Copy the OCID value from "Tenancy Information"

**User OCID:**
1. Click profile → User Settings
2. Copy the OCID value

**Compartment OCID:**
1. Navigate to Identity → Compartments
2. Click your `yawl-deployment` compartment
3. Copy the OCID

Store these values in a secure file:
```bash
# Create secure notes
cat > ~/.oci/credentials.txt <<EOF
Tenancy OCID: ocid1.tenancy.oc1...
User OCID: ocid1.user.oc1...
Fingerprint: 12:34:56:78:90:ab:cd:ef...
Compartment OCID: ocid1.compartment.oc1...
EOF

chmod 600 ~/.oci/credentials.txt
```

## Part 2: Local Environment Setup

### Step 2.1: Install Terraform

**macOS:**
```bash
brew install terraform
```

**Ubuntu/Debian:**
```bash
curl -fsSL https://apt.releases.hashicorp.com/gpg | sudo apt-key add -
sudo apt-add-repository "deb [arch=amd64] https://apt.releases.hashicorp.com $(lsb_release -cs) main"
sudo apt-get update && sudo apt-get install terraform
```

**Windows (PowerShell):**
```powershell
choco install terraform
# or
scoop install terraform
```

**Verify installation:**
```bash
terraform --version
```

### Step 2.2: Generate SSH Key Pair

```bash
# Generate SSH key for OCI instances
ssh-keygen -t rsa -b 4096 -f ~/.ssh/yawl_oci -C "yawl@oci" -N ""

# Verify
ls -la ~/.ssh/yawl_oci*

# Get public key (you'll need this)
cat ~/.ssh/yawl_oci.pub
```

### Step 2.3: Clone and Prepare Repository

```bash
# Navigate to YAWL directory
cd /home/user/yawl

# Initialize git if needed
git status

# Check terraform-oci directory
cd terraform-oci
ls -la
```

## Part 3: Configure Terraform

### Step 3.1: Create terraform.tfvars

```bash
cd /home/user/yawl/terraform-oci

# Copy example file
cp terraform.tfvars.example terraform.tfvars

# Open for editing
nano terraform.tfvars
# or
vim terraform.tfvars
```

### Step 3.2: Fill in Configuration Values

Edit `terraform.tfvars` with your values:

```hcl
# === OCI Credentials ===
oci_tenancy_ocid = "ocid1.tenancy.oc1.xxx..."      # From Step 1.3
oci_user_ocid    = "ocid1.user.oc1.xxx..."         # From Step 1.3
oci_fingerprint  = "12:34:56:78:90:ab:cd:ef:..."   # From Step 1.2
oci_private_key  = file("~/.oci/oci_api_key.pem")  # Keep as is

# === Environment Setup ===
compartment_ocid = "ocid1.compartment.oc1.xxx..."  # From Step 1.3
oci_region       = "us-phoenix-1"                  # Or your preferred region
environment      = "production"                     # or "staging", "dev"

# === SSH Configuration ===
ssh_public_key   = file("~/.ssh/yawl_oci.pub")     # Keep as is

# === Database Configuration ===
# Password must contain:
# - At least 8 characters
# - Uppercase letters (A-Z)
# - Lowercase letters (a-z)
# - Numbers (0-9)
# - Special characters (!@#$%^&*)
mysql_admin_password = "YawL@2024#Secure!"

# === Compute Configuration ===
compute_instance_count = 2                         # 2 for HA

# === Tags ===
common_tags = {
  "Project"     = "YAWL"
  "ManagedBy"   = "Terraform"
  "Environment" = "production"
  "Owner"       = "Your Name"
}
```

**Save and exit:**
- Nano: Ctrl+O, Enter, Ctrl+X
- Vim: Esc, :wq, Enter

### Step 3.3: Verify Configuration

```bash
# Check file syntax
terraform fmt -check .

# Validate configuration
terraform validate
```

## Part 4: Deploy Infrastructure

### Step 4.1: Initialize Terraform

```bash
terraform init
```

**Output should show:**
```
Terraform has been successfully configured!

The version of Terraform you're running has the following required providers:
 - oci (~> 5.0)
 - random (~> 3.1)
 - tls (~> 4.0)
```

### Step 4.2: Plan Deployment

```bash
terraform plan -out=tfplan
```

**Review the output:**
- Number of resources to create
- Resource details (names, IPs, ports, etc.)
- Any errors or warnings

**Example output:**
```
Plan: 45 to add, 0 to change, 0 to destroy.
```

### Step 4.3: Apply Configuration

```bash
# Apply using saved plan
terraform apply tfplan
```

**This will create:**
- Virtual Cloud Network (VCN)
- 3 Subnets (public, private, database)
- 2 Compute Instances
- MySQL Database
- Load Balancer
- Security Groups and Network Security Groups
- Block Storage Volumes

**Expected duration:** 10-15 minutes

### Step 4.4: Monitor Deployment

Watch the deployment progress:

```bash
# In another terminal, check resource creation
watch -n 5 'terraform show | grep "resource"'

# Or use OCI CLI
oci compute instance list --compartment-id <your-compartment-ocid>
oci mysql db-system list --compartment-id <your-compartment-ocid>
```

**Completion indicators:**
- Terraform apply completes
- All resource states are "Active"
- No error messages

## Part 5: Verify Deployment

### Step 5.1: Get Deployment Information

```bash
# Show all outputs
terraform output

# Get specific information
terraform output load_balancer_ip_address
terraform output mysql_db_endpoint
terraform output compute_instance_private_ips
```

### Step 5.2: Test Load Balancer

```bash
# Get load balancer IP
LB_IP=$(terraform output -raw load_balancer_ip_address)

# Test HTTP (should redirect to HTTPS)
curl -I http://$LB_IP/

# Test HTTPS (ignore self-signed cert warning)
curl -k https://$LB_IP/
```

### Step 5.3: Access Compute Instances

**Via SSH bastion (if you have one):**
```bash
# Get compute instance private IPs
terraform output compute_instance_private_ips

# SSH to instance (through bastion)
ssh -J bastion_user@bastion_ip ubuntu@10.0.2.10
```

**Or view logs on instances:**
```bash
# Through OCI Console → Compute → Instances
# → Select instance → Console Connection
```

### Step 5.4: Test Database Connection

```bash
# Get database endpoint
DB_ENDPOINT=$(terraform output -raw mysql_db_endpoint)

# From a compute instance:
# ssh ubuntu@<instance-ip>
# mysql -h $DB_ENDPOINT -u admin -p
# Enter password: (what you set in terraform.tfvars)

# Or from local machine (if access allows):
mysql -h $DB_ENDPOINT -u admin -p
```

## Part 6: Post-Deployment Configuration

### Step 6.1: Update Application Configuration

On compute instances:

```bash
# SSH to instance
ssh ubuntu@<instance-private-ip>

# Check environment file
sudo cat /etc/yawl/config.env

# View application logs
sudo journalctl -u yawl -f

# Check YAWL service status
sudo systemctl status yawl
```

### Step 6.2: Configure SSL Certificate

Replace self-signed certificate with real certificate:

1. Obtain certificate (Let's Encrypt, commercial CA, etc.)
2. Update load_balancer.tf:
   ```hcl
   resource "tls_self_signed_cert" "main" {
     # Replace with your certificate
   }
   ```
3. Redeploy: `terraform apply`

### Step 6.3: Configure Firewall Rules

**If needed, add ingress rules for administrative access:**

```bash
# Edit security groups in OCI Console
# Or update network.tf security group rules
terraform apply
```

### Step 6.4: Setup Monitoring

1. In OCI Console → Monitoring → Alarms
2. Create alarms for:
   - CPU usage
   - Memory usage
   - Disk space
   - Database connections

## Part 7: Backup and Disaster Recovery

### Step 7.1: Verify Database Backups

```bash
# List backups
oci mysql backup list --compartment-id <compartment-ocid>

# Restore from backup (if needed)
oci mysql backup describe --backup-id <backup-ocid>
```

### Step 7.2: Save Terraform State

```bash
# Backup current state
cp terraform.tfstate terraform.tfstate.backup

# Consider using remote state backend
# Uncomment backend block in provider.tf
# Create OCI Object Storage bucket
# Run: terraform init -reconfigure
```

### Step 7.3: Document Configuration

```bash
# Save deployment outputs
terraform output > deployment_info.txt

# Save resource IDs
terraform state list > resources.txt
terraform show > full_state.txt

# Archive configuration
tar -czf yawl-oci-config.tar.gz terraform-oci/
```

## Part 8: Cost Optimization

### Step 8.1: Review Resource Sizing

Check if you can reduce costs:

```bash
# Current resources
terraform plan

# Options:
# - Use VM.Standard.E2.1.Micro (free tier)
# - Reduce backup retention
# - Scale down to 1 instance for dev
```

### Step 8.2: Enable OCI Budgets

1. Go to Billing → Budgets
2. Click "Create Budget"
3. Set alert threshold (e.g., $100)
4. Add email notification

### Step 8.3: Use Free Tier Resources

- VM.Standard.E2.1.Micro: Free (2 instances, 750 hours/month)
- Object Storage: Free (20GB)
- Load Balancer: Free tier eligible
- Always-free MySQL shape available

## Part 9: Troubleshooting

### Issue: "Unauthorized" Error

**Solution:**
```bash
# Verify credentials
cat ~/.oci/credentials.txt

# Check file permissions
ls -la ~/.oci/oci_api_key.pem  # Should be 600

# Regenerate key if needed
rm ~/.oci/oci_api_key*
openssl genrsa -out ~/.oci/oci_api_key.pem 2048
# ... repeat key setup steps
```

### Issue: "Region Not Available"

**Solution:**
```bash
# List available regions
oci iam region-subscription list

# Update oci_region in terraform.tfvars
oci_region = "us-ashburn-1"  # Or available region
```

### Issue: "Quota Exceeded"

**Solution:**
1. Use smaller instance shape
2. Reduce compute instance count
3. Request quota increase in OCI Console

### Issue: "Database Won't Start"

**Solution:**
```bash
# Check database status
oci mysql db-system describe --db-system-id <ocid>

# View database logs
# In OCI Console → MySQL Database System → Activity
```

## Part 10: Cleanup and Destruction

### Step 10.1: Stop Services (Optional)

```bash
# SSH to instance
ssh ubuntu@<instance-ip>

# Stop YAWL service
sudo systemctl stop yawl
```

### Step 10.2: Destroy Infrastructure

⚠️ **WARNING: This permanently deletes all resources!**

```bash
# Review what will be destroyed
terraform plan -destroy

# Destroy all resources
terraform destroy

# Confirm by typing 'yes' when prompted
```

### Step 10.3: Verify Cleanup

```bash
# Check OCI Console
# All resources should be deleted

# Check Terraform state
terraform state list  # Should be empty

# Clean local files
rm -rf .terraform
rm -f terraform.tfstate*
rm -f tfplan*
```

## Part 11: Maintenance and Updates

### Monthly Tasks

- [ ] Review AWS billing and costs
- [ ] Check backup integrity
- [ ] Review security group rules
- [ ] Update OS patches
- [ ] Monitor disk space

### Quarterly Tasks

- [ ] Update Terraform to latest version
- [ ] Review and rotate credentials
- [ ] Test disaster recovery
- [ ] Optimize resource allocation

### Annual Tasks

- [ ] Security audit
- [ ] Architecture review
- [ ] Capacity planning
- [ ] Vendor evaluation

## Getting Help

### Resources

- OCI Documentation: https://docs.oracle.com/
- Terraform OCI Provider: https://registry.terraform.io/providers/oracle/oci/latest
- YAWL Project: https://github.com/yawl/yawl
- Community Forums: Stack Overflow, Oracle Forums

### Support Channels

- OCI Support: https://support.oracle.com/
- Terraform Community: https://discuss.hashicorp.com/
- YAWL Community: Project-specific forums

## Success Checklist

Deployment is successful when:

- [ ] Terraform apply completed without errors
- [ ] All resources show "Active" status in OCI Console
- [ ] Load balancer responds to HTTP/HTTPS requests
- [ ] Compute instances are accessible
- [ ] Database accepts connections
- [ ] Application logs show normal operation
- [ ] Backups are created automatically
- [ ] Monitoring/alarms are configured
- [ ] Costs are within budget
- [ ] Disaster recovery plan is documented

---

**Congratulations!** You have successfully deployed YAWL on Oracle Cloud Infrastructure.

For ongoing management, refer to the README.md and CONFIGURATION.md files.

**Last Updated:** February 2026
