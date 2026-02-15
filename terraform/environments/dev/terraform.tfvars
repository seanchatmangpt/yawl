#------------------------------------------------------------------------------
# YAWL Development Environment Variables
# Version: 1.0.0
#
# Copy this file to terraform.tfvars and customize for your environment
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# General Configuration
#------------------------------------------------------------------------------

project_name     = "yawl"
environment      = "dev"
cloud_provider   = "aws"  # Options: gcp, aws, azure, oracle, ibm

common_tags = {
  Team        = "platform"
  CostCenter  = "development"
  Owner       = "platform-team"
}

#------------------------------------------------------------------------------
# Networking Configuration
#------------------------------------------------------------------------------

vpc_cidr = "10.0.0.0/16"

subnet_cidrs = {
  private = ["10.0.1.0/24", "10.0.2.0/24"]
  public  = ["10.0.101.0/24", "10.0.102.0/24"]
  data    = ["10.0.201.0/24", "10.0.202.0/24"]
}

enable_nat_gateway  = true
enable_vpn_gateway  = false

allowed_cidr_blocks = ["0.0.0.0/0"]  # Open for dev - restrict in production

#------------------------------------------------------------------------------
# Kubernetes Configuration
#------------------------------------------------------------------------------

kubernetes_version    = "1.28"
node_count_min        = 2
node_count_max        = 5
node_disk_size_gb     = 50
enable_node_auto_scaling = true

node_pool_labels = {
  environment = "dev"
}

node_pool_taints = []

#------------------------------------------------------------------------------
# Database Configuration
#------------------------------------------------------------------------------

database_engine              = "postgresql"
database_version             = "15"
database_storage_gb          = 50
database_multi_az            = false
database_backup_retention_days = 7
database_deletion_protection = false
database_encryption          = true
database_username            = "yawl_admin"

#------------------------------------------------------------------------------
# Storage Configuration
#------------------------------------------------------------------------------

storage_bucket_names   = ["data", "logs", "backups"]
storage_versioning     = true
storage_lifecycle_days = 30
storage_archive_days   = 90

#------------------------------------------------------------------------------
# Monitoring Configuration
#------------------------------------------------------------------------------

enable_monitoring  = true
enable_logging     = true
enable_alerting    = false  # Disable alerts for dev
log_retention_days = 7
metric_retention_days = 7

alert_email_recipients = []
alert_slack_webhook    = ""

#------------------------------------------------------------------------------
# Security Configuration
#------------------------------------------------------------------------------

enable_waf              = false
enable_ddos_protection  = false
ssl_certificate_arn     = ""
enable_private_endpoint = false
kms_key_id              = ""

#------------------------------------------------------------------------------
# AWS-Specific Configuration
#------------------------------------------------------------------------------

aws_region      = "us-east-1"
aws_account_id  = ""

aws_availability_zones = ["us-east-1a", "us-east-1b"]

eks_instance_type = "m5.large"
eks_ami_type      = "AL2_x86_64"

database_instance_class = "db.t3.medium"

ssl_certificate_arn = ""

#------------------------------------------------------------------------------
# GCP-Specific Configuration
#------------------------------------------------------------------------------

gcp_project_id = "your-project-id"
gcp_region     = "us-central1"
gcp_zone       = "us-central1-a"

gke_machine_type = "e2-medium"
gke_preemptible  = true  # Use preemptible for dev cost savings

#------------------------------------------------------------------------------
# Azure-Specific Configuration
#------------------------------------------------------------------------------

azure_subscription_id     = ""
azure_location            = "East US"
azure_resource_group_name = ""

aks_vm_size   = "Standard_D2s_v3"
aks_sku_tier  = "Free"

#------------------------------------------------------------------------------
# Oracle Cloud-Specific Configuration
#------------------------------------------------------------------------------

oci_tenancy_id     = ""
oci_compartment_id = ""
oci_region         = "us-phoenix-1"

oke_node_shape     = "VM.Standard.E4.Flex"
oke_node_ocpus     = 2
oke_node_memory_gb = 8

#------------------------------------------------------------------------------
# IBM Cloud-Specific Configuration
#------------------------------------------------------------------------------

ibm_api_key      = ""
ibm_region       = "us-south"
ibm_resource_group = "default"

iks_flavor    = "bx2.2x8"
iks_hardware  = "shared"
