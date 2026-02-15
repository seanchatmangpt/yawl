#------------------------------------------------------------------------------
# YAWL Development Environment Variables Definition
# Version: 1.0.0
#------------------------------------------------------------------------------

# Include all variables from root module
# This file imports the variable definitions

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "cloud_provider" {
  type = string
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

# Networking
variable "vpc_cidr" {
  type = string
}

variable "subnet_cidrs" {
  type = object({
    private = list(string)
    public  = list(string)
    data    = list(string)
  })
}

variable "enable_nat_gateway" {
  type = bool
}

variable "enable_vpn_gateway" {
  type = bool
}

variable "allowed_cidr_blocks" {
  type = list(string)
}

# Kubernetes
variable "kubernetes_version" {
  type = string
}

variable "node_count_min" {
  type = number
}

variable "node_count_max" {
  type = number
}

variable "node_disk_size_gb" {
  type = number
}

variable "enable_node_auto_scaling" {
  type = bool
}

variable "node_pool_labels" {
  type    = map(string)
  default = {}
}

variable "node_pool_taints" {
  type = list(object({
    key    = string
    value  = string
    effect = string
  }))
  default = []
}

# Database
variable "database_engine" {
  type    = string
  default = "postgresql"
}

variable "database_version" {
  type = string
}

variable "database_instance_class" {
  type    = string
  default = ""
}

variable "database_storage_gb" {
  type = number
}

variable "database_multi_az" {
  type = bool
}

variable "database_backup_retention_days" {
  type = number
}

variable "database_deletion_protection" {
  type = bool
}

variable "database_encryption" {
  type = bool
}

variable "database_username" {
  type = string
}

# Storage
variable "storage_bucket_names" {
  type = list(string)
}

variable "storage_versioning" {
  type = bool
}

variable "storage_lifecycle_days" {
  type = number
}

variable "storage_archive_days" {
  type = number
}

# Monitoring
variable "enable_monitoring" {
  type = bool
}

variable "enable_logging" {
  type = bool
}

variable "enable_alerting" {
  type = bool
}

variable "log_retention_days" {
  type = number
}

variable "metric_retention_days" {
  type    = number
  default = 30
}

variable "alert_email_recipients" {
  type    = list(string)
  default = []
}

variable "alert_slack_webhook" {
  type    = string
  default = ""
}

# Security
variable "enable_waf" {
  type    = bool
  default = false
}

variable "enable_ddos_protection" {
  type    = bool
  default = false
}

variable "ssl_certificate_arn" {
  type    = string
  default = ""
}

variable "enable_private_endpoint" {
  type = bool
}

variable "kms_key_id" {
  type    = string
  default = ""
}

# AWS-specific
variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_account_id" {
  type    = string
  default = ""
}

variable "aws_availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_instance_type" {
  type    = string
  default = "m5.xlarge"
}

variable "eks_ami_type" {
  type    = string
  default = "AL2_x86_64"
}

# GCP-specific
variable "gcp_project_id" {
  type    = string
  default = ""
}

variable "gcp_region" {
  type    = string
  default = "us-central1"
}

variable "gcp_zone" {
  type    = string
  default = "us-central1-a"
}

variable "gke_machine_type" {
  type    = string
  default = "e2-standard-4"
}

variable "gke_preemptible" {
  type    = bool
  default = false
}

# Azure-specific
variable "azure_subscription_id" {
  type    = string
  default = ""
}

variable "azure_location" {
  type    = string
  default = "East US"
}

variable "azure_resource_group_name" {
  type    = string
  default = ""
}

variable "aks_vm_size" {
  type    = string
  default = "Standard_D4s_v3"
}

variable "aks_sku_tier" {
  type    = string
  default = "Free"
}

# Oracle-specific
variable "oci_tenancy_id" {
  type    = string
  default = ""
}

variable "oci_compartment_id" {
  type    = string
  default = ""
}

variable "oci_region" {
  type    = string
  default = "us-phoenix-1"
}

variable "oke_node_shape" {
  type    = string
  default = "VM.Standard.E4.Flex"
}

variable "oke_node_ocpus" {
  type    = number
  default = 4
}

variable "oke_node_memory_gb" {
  type    = number
  default = 16
}

# IBM-specific
variable "ibm_api_key" {
  type    = string
  default = ""
  sensitive = true
}

variable "ibm_region" {
  type    = string
  default = "us-south"
}

variable "ibm_resource_group" {
  type    = string
  default = "default"
}

variable "iks_flavor" {
  type    = string
  default = "bx2.4x16"
}

variable "iks_hardware" {
  type    = string
  default = "shared"
}
