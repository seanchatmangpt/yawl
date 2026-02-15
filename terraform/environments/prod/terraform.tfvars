#------------------------------------------------------------------------------
# YAWL Production Environment Variables
# Version: 1.0.0
#
# Production environment - full HA, security, and compliance
# Copy this file to terraform.tfvars and customize for your environment
# WARNING: Review all settings before deploying to production
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# General Configuration
#------------------------------------------------------------------------------

project_name     = "yawl"
environment      = "prod"
cloud_provider   = "aws"  # Options: gcp, aws, azure, oracle, ibm

common_tags = {
  Team        = "platform"
  CostCenter  = "production"
  Owner       = "platform-team"
  Compliance  = "SOC2"
}

#------------------------------------------------------------------------------
# Networking Configuration
#------------------------------------------------------------------------------

vpc_cidr = "10.10.0.0/16"

subnet_cidrs = {
  private = ["10.10.1.0/24", "10.10.2.0/24", "10.10.3.0/24"]
  public  = ["10.10.101.0/24", "10.10.102.0/24", "10.10.103.0/24"]
  data    = ["10.10.201.0/24", "10.10.202.0/24", "10.10.203.0/24"]
}

enable_nat_gateway  = true
enable_vpn_gateway  = true  # Enable VPN for hybrid connectivity

# Restrict access - UPDATE THESE FOR YOUR ORGANIZATION
allowed_cidr_blocks = [
  "10.0.0.0/8",       # Internal network
  "172.16.0.0/12",    # Internal network
  "192.168.0.0/16",   # Internal network
  "YOUR_OFFICE_IP/32" # Replace with your office IP
]

#------------------------------------------------------------------------------
# Kubernetes Configuration
#------------------------------------------------------------------------------

kubernetes_version    = "1.28"
node_count_min        = 5
node_count_max        = 20
node_disk_size_gb     = 200
enable_node_auto_scaling = true

node_pool_labels = {
  environment = "production"
}

# Add dedicated workload pool with taints
node_pool_taints = []

#------------------------------------------------------------------------------
# Database Configuration
#------------------------------------------------------------------------------

database_engine              = "aurora-postgresql"  # Use Aurora for production HA
database_version             = "15"
database_storage_gb          = 500
database_multi_az            = true        # REQUIRED for production
database_backup_retention_days = 30        # 30 days for production compliance
database_deletion_protection = true        # REQUIRED - prevents accidental deletion
database_encryption          = true        # REQUIRED for production
database_username            = "yawl_admin"

#------------------------------------------------------------------------------
# Storage Configuration
#------------------------------------------------------------------------------

storage_bucket_names   = ["data", "logs", "backups", "audit"]
storage_versioning     = true
storage_lifecycle_days = 90
storage_archive_days   = 365

#------------------------------------------------------------------------------
# Monitoring Configuration
#------------------------------------------------------------------------------

enable_monitoring  = true
enable_logging     = true
enable_alerting    = true
log_retention_days = 90          # 90 days for production compliance
metric_retention_days = 30

alert_email_recipients = [
  "prod-alerts@yourcompany.com",
  "oncall@yourcompany.com"
]
alert_slack_webhook    = "https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK"

#------------------------------------------------------------------------------
# Security Configuration
#------------------------------------------------------------------------------

enable_waf              = true        # REQUIRED for production
enable_ddos_protection  = true        # REQUIRED for production
ssl_certificate_arn     = ""          # ADD YOUR SSL CERTIFICATE ARN
enable_private_endpoint = true        # REQUIRED for production
kms_key_id              = ""          # ADD YOUR KMS KEY FOR ENCRYPTION

#------------------------------------------------------------------------------
# AWS-Specific Configuration
#------------------------------------------------------------------------------

aws_region      = "us-east-1"
aws_account_id  = ""  # Your AWS account ID

aws_availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

eks_instance_type = "m5.2xlarge"       # Larger instances for production
eks_ami_type      = "AL2_x86_64"

database_instance_class = "db.r6g.xlarge"  # Production-grade

ssl_certificate_arn = ""  # ADD YOUR ACM CERTIFICATE ARN

#------------------------------------------------------------------------------
# GCP-Specific Configuration
#------------------------------------------------------------------------------

gcp_project_id = "your-production-project-id"
gcp_region     = "us-central1"
gcp_zone       = "us-central1-a"

gke_machine_type = "e2-standard-8"    # Production sizing
gke_preemptible  = false              # NEVER use preemptible in production

#------------------------------------------------------------------------------
# Azure-Specific Configuration
#------------------------------------------------------------------------------

azure_subscription_id     = ""
azure_location            = "East US"
azure_resource_group_name = ""

aks_vm_size   = "Standard_D8s_v3"     # Production sizing
aks_sku_tier  = "Paid"                # REQUIRED for production SLA

#------------------------------------------------------------------------------
# Oracle Cloud-Specific Configuration
#------------------------------------------------------------------------------

oci_tenancy_id     = ""
oci_compartment_id = ""
oci_region         = "us-phoenix-1"

oke_node_shape     = "VM.Standard.E4.Flex"
oke_node_ocpus     = 8
oke_node_memory_gb = 32

#------------------------------------------------------------------------------
# IBM Cloud-Specific Configuration
#------------------------------------------------------------------------------

ibm_api_key      = ""
ibm_region       = "us-south"
ibm_resource_group = "production"

iks_flavor    = "bx2.8x32"            # Production sizing
iks_hardware  = "dedicated"           # Use dedicated for production isolation

#------------------------------------------------------------------------------
# Production Checklist
# Before deploying, ensure the following are configured:
#
# [ ] SSL certificate ARN configured
# [ ] KMS key for encryption configured
# [ ] allowed_cidr_blocks updated to restrict access
# [ ] alert_email_recipients configured
# [ ] Slack webhook configured for alerts
# [ ] VPN gateway enabled if hybrid connectivity needed
# [ ] All production-specific instance sizes reviewed
# [ ] Backup retention meets compliance requirements
# [ ] Log retention meets compliance requirements
#------------------------------------------------------------------------------
