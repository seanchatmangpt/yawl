#------------------------------------------------------------------------------
# YAWL Staging Environment Variables
# Version: 1.0.0
#
# Staging environment - production-like but with reduced scale
# Copy this file to terraform.tfvars and customize for your environment
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# General Configuration
#------------------------------------------------------------------------------

project_name     = "yawl"
environment      = "staging"
cloud_provider   = "aws"  # Options: gcp, aws, azure, oracle, ibm

common_tags = {
  Team        = "platform"
  CostCenter  = "staging"
  Owner       = "platform-team"
}

#------------------------------------------------------------------------------
# Networking Configuration
#------------------------------------------------------------------------------

vpc_cidr = "10.1.0.0/16"

subnet_cidrs = {
  private = ["10.1.1.0/24", "10.1.2.0/24", "10.1.3.0/24"]
  public  = ["10.1.101.0/24", "10.1.102.0/24", "10.1.103.0/24"]
  data    = ["10.1.201.0/24", "10.1.202.0/24", "10.1.203.0/24"]
}

enable_nat_gateway  = true
enable_vpn_gateway  = false

# Restrict to corporate network for staging
allowed_cidr_blocks = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]

#------------------------------------------------------------------------------
# Kubernetes Configuration
#------------------------------------------------------------------------------

kubernetes_version    = "1.28"
node_count_min        = 3
node_count_max        = 10
node_disk_size_gb     = 100
enable_node_auto_scaling = true

node_pool_labels = {
  environment = "staging"
}

node_pool_taints = []

#------------------------------------------------------------------------------
# Database Configuration
#------------------------------------------------------------------------------

database_engine              = "postgresql"
database_version             = "15"
database_storage_gb          = 100
database_multi_az            = true       # Enable HA for staging
database_backup_retention_days = 14       # 2 weeks for staging
database_deletion_protection = true
database_encryption          = true
database_username            = "yawl_admin"

#------------------------------------------------------------------------------
# Storage Configuration
#------------------------------------------------------------------------------

storage_bucket_names   = ["data", "logs", "backups"]
storage_versioning     = true
storage_lifecycle_days = 60
storage_archive_days   = 180

#------------------------------------------------------------------------------
# Monitoring Configuration
#------------------------------------------------------------------------------

enable_monitoring  = true
enable_logging     = true
enable_alerting    = true
log_retention_days = 30
metric_retention_days = 30

alert_email_recipients = ["staging-alerts@yourcompany.com"]
alert_slack_webhook    = ""  # Add Slack webhook if desired

#------------------------------------------------------------------------------
# Security Configuration
#------------------------------------------------------------------------------

enable_waf              = true
enable_ddos_protection  = true
ssl_certificate_arn     = ""  # Add your SSL certificate ARN
enable_private_endpoint = false
kms_key_id              = ""  # Add KMS key for encryption

#------------------------------------------------------------------------------
# AWS-Specific Configuration
#------------------------------------------------------------------------------

aws_region      = "us-east-1"
aws_account_id  = ""

aws_availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

eks_instance_type = "m5.xlarge"
eks_ami_type      = "AL2_x86_64"

database_instance_class = "db.r6g.large"

ssl_certificate_arn = ""

#------------------------------------------------------------------------------
# GCP-Specific Configuration
#------------------------------------------------------------------------------

gcp_project_id = "your-project-id"
gcp_region     = "us-central1"
gcp_zone       = "us-central1-a"

gke_machine_type = "e2-standard-4"
gke_preemptible  = false  # No preemptible for staging

#------------------------------------------------------------------------------
# Azure-Specific Configuration
#------------------------------------------------------------------------------

azure_subscription_id     = ""
azure_location            = "East US"
azure_resource_group_name = ""

aks_vm_size   = "Standard_D4s_v3"
aks_sku_tier  = "Paid"  # Use Paid tier for staging SLA

#------------------------------------------------------------------------------
# Oracle Cloud-Specific Configuration
#------------------------------------------------------------------------------

oci_tenancy_id     = ""
oci_compartment_id = ""
oci_region         = "us-phoenix-1"

oke_node_shape     = "VM.Standard.E4.Flex"
oke_node_ocpus     = 4
oke_node_memory_gb = 16

#------------------------------------------------------------------------------
# IBM Cloud-Specific Configuration
#------------------------------------------------------------------------------

ibm_api_key      = ""
ibm_region       = "us-south"
ibm_resource_group = "staging"

iks_flavor    = "bx2.4x16"
iks_hardware  = "shared"
