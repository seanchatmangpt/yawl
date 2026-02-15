# Terraform Variables
# Configuration for Teradata Vantage Deployment

# =============================================================================
# General Configuration
# =============================================================================

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["development", "staging", "production"], var.environment)
    error_message = "Environment must be development, staging, or production."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "yawl-teradata"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,28}[a-z0-9]$", var.project_name))
    error_message = "Project name must be 3-30 characters, lowercase alphanumeric with hyphens."
  }
}

variable "owner" {
  description = "Owner of the resources"
  type        = string
  default     = "platform-team"
}

variable "cost_center" {
  description = "Cost center for billing"
  type        = string
  default     = "yawl-platform"
}

# =============================================================================
# Network Configuration
# =============================================================================

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "VPC CIDR must be a valid IPv4 CIDR block."
  }
}

variable "secondary_vpc_cidr" {
  description = "Secondary CIDR block for VPC"
  type        = string
  default     = "10.1.0.0/16"
}

variable "enable_secondary_cidr" {
  description = "Enable secondary CIDR block"
  type        = bool
  default     = false
}

variable "primary_subnet_cidr" {
  description = "CIDR block for primary subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "secondary_subnet_cidr" {
  description = "CIDR block for secondary subnet"
  type        = string
  default     = "10.0.2.0/24"
}

variable "public_subnet_cidr" {
  description = "CIDR block for public subnet"
  type        = string
  default     = "10.0.100.0/24"
}

# =============================================================================
# Network Features
# =============================================================================

variable "enable_bastion" {
  description = "Enable bastion host for SSH access"
  type        = bool
  default     = false
}

variable "enable_nat_gateway" {
  description = "Enable NAT gateway for private subnet egress"
  type        = bool
  default     = false
}

variable "enable_vpn" {
  description = "Enable VPN connection to VPC"
  type        = bool
  default     = false
}

variable "vpn_cidr" {
  description = "CIDR block for VPN clients"
  type        = string
  default     = "192.168.255.0/24"
}

# =============================================================================
# Security Configuration
# =============================================================================

variable "allowed_console_cidr_blocks" {
  description = "CIDR blocks allowed to access Teradata console"
  type        = list(string)
  default     = ["10.0.0.0/8"]
}

variable "allowed_application_cidr_blocks" {
  description = "CIDR blocks allowed to access YAWL application"
  type        = list(string)
  default     = ["10.0.0.0/8", "172.16.0.0/12"]
}

variable "allowed_ssh_cidr_blocks" {
  description = "CIDR blocks allowed SSH access"
  type        = list(string)
  default     = []
}

variable "yawl_engine_security_group_ids" {
  description = "Security group IDs for YAWL engine"
  type        = list(string)
  default     = []
}

variable "admin_security_group_ids" {
  description = "Security group IDs for admin access"
  type        = list(string)
  default     = []
}

variable "kms_key_arn" {
  description = "ARN of KMS key for encryption"
  type        = string
  default     = ""
}

variable "secrets_manager_secret_arn" {
  description = "ARN of Secrets Manager secret for credentials"
  type        = string
  default     = ""
}

# =============================================================================
# Storage Configuration
# =============================================================================

variable "enable_bucket_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = true
}

variable "enable_backup_bucket" {
  description = "Enable backup S3 bucket"
  type        = bool
  default     = true
}

# =============================================================================
# Monitoring Configuration
# =============================================================================

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 30

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1827, 3653], var.log_retention_days)
    error_message = "Log retention must be a valid CloudWatch retention period."
  }
}

variable "alert_email" {
  description = "Email address for alerts"
  type        = string
  default     = ""
}

variable "alert_sns_endpoint" {
  description = "SNS endpoint for webhook alerts"
  type        = string
  default     = ""
}

variable "enable_detailed_monitoring" {
  description = "Enable detailed CloudWatch monitoring"
  type        = bool
  default     = true
}

# =============================================================================
# Teradata Vantage Configuration
# =============================================================================

variable "vantage_package" {
  description = "VantageCloud Lake package tier"
  type        = string
  default     = "lake"

  validation {
    condition     = contains(["standard", "lake", "lake_plus"], var.vantage_package)
    error_message = "Package must be standard, lake, or lake_plus."
  }
}

variable "primary_cluster_size" {
  description = "Primary cluster size"
  type        = string
  default     = "TD_COMPUTE_SMALL"

  validation {
    condition     = contains(["TD_COMPUTE_SMALL", "TD_COMPUTE_MEDIUM", "TD_COMPUTE_LARGE", "TD_COMPUTE_XLARGE"], var.primary_cluster_size)
    error_message = "Invalid cluster size."
  }
}

variable "primary_min_instances" {
  description = "Minimum primary cluster instances"
  type        = number
  default     = 2

  validation {
    condition     = var.primary_min_instances >= 1 && var.primary_min_instances <= 64
    error_message = "Min instances must be between 1 and 64."
  }
}

variable "primary_max_instances" {
  description = "Maximum primary cluster instances"
  type        = number
  default     = 8

  validation {
    condition     = var.primary_max_instances >= var.primary_min_instances && var.primary_max_instances <= 64
    error_message = "Max instances must be >= min instances and <= 64."
  }
}

variable "block_storage_tb" {
  description = "Block storage in TB"
  type        = number
  default     = 1

  validation {
    condition     = var.block_storage_tb >= 0.1
    error_message = "Block storage must be at least 0.1 TB."
  }
}

variable "object_storage_tb" {
  description = "Object storage in TB"
  type        = number
  default     = 10

  validation {
    condition     = var.object_storage_tb >= 0
    error_message = "Object storage must be non-negative."
  }
}

variable "teradata_console_url" {
  description = "Teradata Cloud Console URL"
  type        = string
  default     = "https://console.intellicloud.teradata.com"
}

variable "teradata_api_token" {
  description = "Teradata API token for provisioning"
  type        = string
  sensitive   = true
  default     = ""
}

# =============================================================================
# YAWL Engine Configuration
# =============================================================================

variable "yawl_engine_instance_type" {
  description = "EC2 instance type for YAWL engine"
  type        = string
  default     = "m5.xlarge"
}

variable "yawl_engine_min_instances" {
  description = "Minimum YAWL engine instances"
  type        = number
  default     = 2
}

variable "yawl_engine_max_instances" {
  description = "Maximum YAWL engine instances"
  type        = number
  default     = 10
}

# =============================================================================
# Database Configuration
# =============================================================================

variable "teradata_database_name" {
  description = "Name of the YAWL database in Teradata"
  type        = string
  default     = "yawl"
}

variable "teradata_staging_database_name" {
  description = "Name of the staging database"
  type        = string
  default     = "yawl_staging"
}

variable "teradata_analytics_database_name" {
  description = "Name of the analytics database"
  type        = string
  default     = "yawl_analytics"
}

# =============================================================================
# Compute Cluster Configuration
# =============================================================================

variable "compute_clusters" {
  description = "Compute cluster configurations"
  type = list(object({
    name         = string
    size         = string
    min_instances = number
    max_instances = number
    type         = string  # STANDARD or ANALYTIC
    start_time   = string  # Cron expression
    end_time     = string  # Cron expression
  }))
  default = [
    {
      name          = "yawl-batch"
      size          = "TD_COMPUTE_SMALL"
      min_instances = 1
      max_instances = 4
      type          = "STANDARD"
      start_time    = "0 6 * * MON-FRI"
      end_time      = "0 22 * * MON-FRI"
    },
    {
      name          = "yawl-realtime"
      size          = "TD_COMPUTE_SMALL"
      min_instances = 1
      max_instances = 2
      type          = "STANDARD"
      start_time    = ""
      end_time      = ""
    }
  ]
}

# =============================================================================
# Tags
# =============================================================================

variable "additional_tags" {
  description = "Additional tags to apply to resources"
  type        = map(string)
  default     = {}
}
