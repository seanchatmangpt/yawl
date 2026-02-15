#------------------------------------------------------------------------------
# YAWL Multi-Cloud Terraform Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# General Configuration
#------------------------------------------------------------------------------

variable "project_name" {
  description = "Name of the project (used for resource naming)"
  type        = string
  default     = "yawl"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,28}[a-z0-9]$", var.project_name))
    error_message = "Project name must be 3-30 characters, lowercase alphanumeric with hyphens, starting with a letter."
  }
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "cloud_provider" {
  description = "Primary cloud provider to deploy to"
  type        = string
  default     = "gcp"

  validation {
    condition     = contains(["gcp", "aws", "azure", "oracle", "ibm"], var.cloud_provider)
    error_message = "Cloud provider must be one of: gcp, aws, azure, oracle, ibm."
  }
}

variable "region" {
  description = "Primary region for cloud resources"
  type        = string
  default     = ""
}

variable "secondary_region" {
  description = "Secondary region for high availability"
  type        = string
  default     = ""
}

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {}
}

#------------------------------------------------------------------------------
# Networking Configuration
#------------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR block for VPC/VNet"
  type        = string
  default     = "10.0.0.0/16"
}

variable "subnet_cidrs" {
  description = "CIDR blocks for subnets"
  type = object({
    private = list(string)
    public  = list(string)
    data    = list(string)
  })
  default = {
    private = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
    public  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
    data    = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]
  }
}

variable "enable_nat_gateway" {
  description = "Enable NAT gateway for private subnets"
  type        = bool
  default     = true
}

variable "enable_vpn_gateway" {
  description = "Enable VPN gateway for hybrid connectivity"
  type        = bool
  default     = false
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access resources"
  type        = list(string)
  default     = []
}

#------------------------------------------------------------------------------
# Kubernetes Configuration
#------------------------------------------------------------------------------

variable "kubernetes_version" {
  description = "Kubernetes version for the cluster"
  type        = string
  default     = "1.28"
}

variable "node_instance_type" {
  description = "Instance type for worker nodes"
  type        = string
  default     = ""
}

variable "node_count_min" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 3
}

variable "node_count_max" {
  description = "Maximum number of worker nodes (for autoscaling)"
  type        = number
  default     = 10
}

variable "node_disk_size_gb" {
  description = "Disk size for worker nodes in GB"
  type        = number
  default     = 100
}

variable "enable_node_auto_scaling" {
  description = "Enable cluster autoscaler"
  type        = bool
  default     = true
}

variable "node_pool_labels" {
  description = "Labels to apply to node pool"
  type        = map(string)
  default     = {}
}

variable "node_pool_taints" {
  description = "Taints to apply to node pool"
  type = list(object({
    key    = string
    value  = string
    effect = string
  }))
  default = []
}

#------------------------------------------------------------------------------
# Database Configuration
#------------------------------------------------------------------------------

variable "database_engine" {
  description = "Database engine type"
  type        = string
  default     = "postgresql"
}

variable "database_version" {
  description = "Database engine version"
  type        = string
  default     = "15"
}

variable "database_instance_class" {
  description = "Database instance class/type"
  type        = string
  default     = ""
}

variable "database_storage_gb" {
  description = "Database storage size in GB"
  type        = number
  default     = 100
}

variable "database_multi_az" {
  description = "Enable multi-AZ deployment for high availability"
  type        = bool
  default     = true
}

variable "database_backup_retention_days" {
  description = "Database backup retention period in days"
  type        = number
  default     = 30
}

variable "database_deletion_protection" {
  description = "Enable deletion protection for database"
  type        = bool
  default     = true
}

variable "database_encryption" {
  description = "Enable encryption at rest for database"
  type        = bool
  default     = true
}

variable "database_username" {
  description = "Master username for database"
  type        = string
  default     = "yawl_admin"
  sensitive   = true
}

#------------------------------------------------------------------------------
# Object Storage Configuration
#------------------------------------------------------------------------------

variable "storage_bucket_names" {
  description = "Names for storage buckets to create"
  type        = list(string)
  default     = ["data", "logs", "backups"]
}

variable "storage_versioning" {
  description = "Enable versioning for storage buckets"
  type        = bool
  default     = true
}

variable "storage_lifecycle_days" {
  description = "Days before transitioning to cold storage"
  type        = number
  default     = 90
}

variable "storage_archive_days" {
  description = "Days before archiving or deleting"
  type        = number
  default     = 365
}

#------------------------------------------------------------------------------
# Monitoring Configuration
#------------------------------------------------------------------------------

variable "enable_monitoring" {
  description = "Enable monitoring and observability stack"
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Enable centralized logging"
  type        = bool
  default     = true
}

variable "enable_alerting" {
  description = "Enable alerting"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Log retention period in days"
  type        = number
  default     = 90
}

variable "metric_retention_days" {
  description = "Metric retention period in days"
  type        = number
  default     = 30
}

variable "alert_email_recipients" {
  description = "Email addresses for alert notifications"
  type        = list(string)
  default     = []
}

variable "alert_slack_webhook" {
  description = "Slack webhook URL for alert notifications"
  type        = string
  default     = ""
  sensitive   = true
}

#------------------------------------------------------------------------------
# Security Configuration
#------------------------------------------------------------------------------

variable "enable_waf" {
  description = "Enable Web Application Firewall"
  type        = bool
  default     = true
}

variable "enable_ddos_protection" {
  description = "Enable DDoS protection"
  type        = bool
  default     = true
}

variable "ssl_certificate_arn" {
  description = "ARN of SSL certificate for HTTPS"
  type        = string
  default     = ""
}

variable "enable_private_endpoint" {
  description = "Enable private endpoints for services"
  type        = bool
  default     = true
}

variable "kms_key_id" {
  description = "KMS key ID for encryption"
  type        = string
  default     = ""
}

#------------------------------------------------------------------------------
# Marketplace Configuration
#------------------------------------------------------------------------------

variable "marketplace_enabled" {
  description = "Enable deployment via cloud marketplace"
  type        = bool
  default     = false
}

variable "marketplace_plan_id" {
  description = "Marketplace plan identifier"
  type        = string
  default     = ""
}

variable "marketplace_offer_id" {
  description = "Marketplace offer identifier"
  type        = string
  default     = "yawl-workflow-engine"
}

variable "marketplace_publisher" {
  description = "Marketplace publisher identifier"
  type        = string
  default     = "yawlfoundation"
}

#------------------------------------------------------------------------------
# GCP-Specific Variables
#------------------------------------------------------------------------------

variable "gcp_project_id" {
  description = "GCP project ID"
  type        = string
  default     = ""
}

variable "gcp_region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "gcp_zone" {
  description = "GCP zone"
  type        = string
  default     = "us-central1-a"
}

variable "gke_machine_type" {
  description = "Machine type for GKE nodes"
  type        = string
  default     = "e2-standard-4"
}

variable "gke_preemptible" {
  description = "Use preemptible VMs for GKE nodes"
  type        = bool
  default     = false
}

#------------------------------------------------------------------------------
# AWS-Specific Variables
#------------------------------------------------------------------------------

variable "aws_account_id" {
  description = "AWS account ID"
  type        = string
  default     = ""
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "aws_availability_zones" {
  description = "AWS availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "eks_instance_type" {
  description = "EC2 instance type for EKS nodes"
  type        = string
  default     = "m5.xlarge"
}

variable "eks_ami_type" {
  description = "AMI type for EKS nodes"
  type        = string
  default     = "AL2_x86_64"
}

#------------------------------------------------------------------------------
# Azure-Specific Variables
#------------------------------------------------------------------------------

variable "azure_subscription_id" {
  description = "Azure subscription ID"
  type        = string
  default     = ""
}

variable "azure_location" {
  description = "Azure location/region"
  type        = string
  default     = "East US"
}

variable "azure_resource_group_name" {
  description = "Azure resource group name"
  type        = string
  default     = ""
}

variable "aks_vm_size" {
  description = "VM size for AKS nodes"
  type        = string
  default     = "Standard_D4s_v3"
}

variable "aks_sku_tier" {
  description = "AKS SKU tier (Free or Paid)"
  type        = string
  default     = "Paid"
}

#------------------------------------------------------------------------------
# Oracle Cloud-Specific Variables
#------------------------------------------------------------------------------

variable "oci_tenancy_id" {
  description = "OCI tenancy OCID"
  type        = string
  default     = ""
}

variable "oci_compartment_id" {
  description = "OCI compartment OCID"
  type        = string
  default     = ""
}

variable "oci_region" {
  description = "OCI region"
  type        = string
  default     = "us-phoenix-1"
}

variable "oke_node_shape" {
  description = "Shape for OKE nodes"
  type        = string
  default     = "VM.Standard.E4.Flex"
}

variable "oke_node_ocpus" {
  description = "OCPUs for OKE nodes"
  type        = number
  default     = 4
}

variable "oke_node_memory_gb" {
  description = "Memory in GB for OKE nodes"
  type        = number
  default     = 16
}

#------------------------------------------------------------------------------
# IBM Cloud-Specific Variables
#------------------------------------------------------------------------------

variable "ibm_api_key" {
  description = "IBM Cloud API key"
  type        = string
  default     = ""
  sensitive   = true
}

variable "ibm_region" {
  description = "IBM Cloud region"
  type        = string
  default     = "us-south"
}

variable "ibm_resource_group" {
  description = "IBM Cloud resource group"
  type        = string
  default     = "default"
}

variable "iks_flavor" {
  description = "Flavor for IKS worker nodes"
  type        = string
  default     = "bx2.4x16"
}

variable "iks_hardware" {
  description = "Hardware isolation for IKS workers (shared or dedicated)"
  type        = string
  default     = "shared"
}
