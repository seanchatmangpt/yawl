# OCI Provider Configuration Variables
variable "oci_tenancy_ocid" {
  description = "OCI Tenancy OCID"
  type        = string
  sensitive   = true
}

variable "oci_user_ocid" {
  description = "OCI User OCID"
  type        = string
  sensitive   = true
}

variable "oci_fingerprint" {
  description = "OCI API Key Fingerprint"
  type        = string
  sensitive   = true
}

variable "oci_private_key" {
  description = "OCI Private Key"
  type        = string
  sensitive   = true
}

variable "oci_region" {
  description = "OCI Region"
  type        = string
  default     = "us-phoenix-1"
}

# Environment Configuration
variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["dev", "staging", "production"], var.environment)
    error_message = "Environment must be dev, staging, or production."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "yawl"
}

variable "compartment_ocid" {
  description = "OCI Compartment OCID where resources will be created"
  type        = string
}

# Network Configuration
variable "vcn_cidr" {
  description = "CIDR block for VCN"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR block for public subnet"
  type        = string
  default     = "10.0.1.0/24"
}

variable "private_subnet_cidr" {
  description = "CIDR block for private subnet"
  type        = string
  default     = "10.0.2.0/24"
}

variable "database_subnet_cidr" {
  description = "CIDR block for database subnet"
  type        = string
  default     = "10.0.3.0/24"
}

variable "availability_domain" {
  description = "Availability domain for resources"
  type        = string
  default     = "1"
}

# Compute Configuration
variable "compute_instance_count" {
  description = "Number of compute instances to create"
  type        = number
  default     = 2

  validation {
    condition     = var.compute_instance_count >= 1 && var.compute_instance_count <= 10
    error_message = "Compute instance count must be between 1 and 10."
  }
}

variable "compute_shape" {
  description = "OCI Compute Shape for YAWL instances"
  type        = string
  default     = "VM.Standard.E2.1.Micro"
}

variable "compute_ocpus" {
  description = "Number of OCPUs for compute instances"
  type        = number
  default     = 1
}

variable "compute_memory_gb" {
  description = "Memory in GB for compute instances"
  type        = number
  default     = 6
}

variable "compute_image_os" {
  description = "Operating system for compute instances"
  type        = string
  default     = "Ubuntu"
}

variable "compute_image_version" {
  description = "OS version for compute instances"
  type        = string
  default     = "22.04"
}

variable "ssh_public_key" {
  description = "SSH public key for compute instances"
  type        = string
}

# Database Configuration
variable "database_display_name" {
  description = "Display name for the MySQL database"
  type        = string
  default     = "yawl-mysql-db"
}

variable "mysql_db_version" {
  description = "MySQL Database version"
  type        = string
  default     = "8.0"
}

variable "mysql_db_name" {
  description = "MySQL database name"
  type        = string
  default     = "yawldb"
}

variable "mysql_admin_username" {
  description = "MySQL admin username"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "mysql_admin_password" {
  description = "MySQL admin password"
  type        = string
  sensitive   = true
}

variable "mysql_shape" {
  description = "MySQL Database Shape"
  type        = string
  default     = "MySQL.VM.Standard.E3.1.8GB"
}

variable "mysql_backup_retention_days" {
  description = "Number of days to retain MySQL backups"
  type        = number
  default     = 7
}

variable "mysql_enable_high_availability" {
  description = "Enable MySQL High Availability"
  type        = bool
  default     = true
}

# Load Balancer Configuration
variable "load_balancer_display_name" {
  description = "Display name for the load balancer"
  type        = string
  default     = "yawl-lb"
}

variable "load_balancer_shape" {
  description = "Load balancer shape"
  type        = string
  default     = "flexible"
}

variable "load_balancer_min_bandwidth" {
  description = "Minimum bandwidth for load balancer in Mbps"
  type        = number
  default     = 10
}

variable "load_balancer_max_bandwidth" {
  description = "Maximum bandwidth for load balancer in Mbps"
  type        = number
  default     = 100
}

variable "lb_listener_port" {
  description = "Load balancer listener port"
  type        = number
  default     = 443
}

variable "lb_backend_port" {
  description = "Load balancer backend port"
  type        = number
  default     = 8080
}

# Tags
variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    "Project"     = "YAWL"
    "ManagedBy"   = "Terraform"
  }
}

variable "tags" {
  description = "Additional tags for resources"
  type        = map(string)
  default     = {}
}
