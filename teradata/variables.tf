variable "gcp_project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "gcp_region" {
  description = "GCP Region"
  type        = string
  default     = "us-central1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

# Teradata Configuration
variable "teradata_instance_name" {
  description = "Teradata instance name"
  type        = string
  default     = "yawl-teradata"
}

variable "teradata_instance_type" {
  description = "Teradata instance type/machine type"
  type        = string
  default     = "n2-highmem-8"
}

variable "teradata_disk_size" {
  description = "Teradata disk size in GB"
  type        = number
  default     = 500
}

variable "teradata_disk_type" {
  description = "Teradata disk type"
  type        = string
  default     = "pd-ssd"
  validation {
    condition     = contains(["pd-ssd", "pd-standard"], var.teradata_disk_type)
    error_message = "Disk type must be pd-ssd or pd-standard."
  }
}

variable "teradata_version" {
  description = "Teradata database version"
  type        = string
  default     = "17.20"
}

variable "teradata_admin_user" {
  description = "Teradata administrator username"
  type        = string
  default     = "dbc"
  sensitive   = true
}

variable "teradata_admin_password" {
  description = "Teradata administrator password"
  type        = string
  sensitive   = true
}

variable "teradata_port" {
  description = "Teradata listening port"
  type        = number
  default     = 1025
  validation {
    condition     = var.teradata_port >= 1024 && var.teradata_port <= 65535
    error_message = "Port must be between 1024 and 65535."
  }
}

variable "teradata_charset" {
  description = "Teradata character set"
  type        = string
  default     = "UTF8"
}

variable "teradata_spool_space_gb" {
  description = "Teradata spool space in GB"
  type        = number
  default     = 100
}

variable "teradata_temp_space_gb" {
  description = "Teradata temporary space in GB"
  type        = number
  default     = 50
}

# YAWL Database Configuration
variable "yawl_database_name" {
  description = "YAWL database name in Teradata"
  type        = string
  default     = "yawl_workflow"
}

variable "yawl_db_user" {
  description = "YAWL database user"
  type        = string
  default     = "yawl_user"
  sensitive   = true
}

variable "yawl_db_password" {
  description = "YAWL database password"
  type        = string
  sensitive   = true
}

variable "yawl_db_initial_space_gb" {
  description = "YAWL database initial space allocation in GB"
  type        = number
  default     = 50
}

# Compute Configuration for YAWL Engine
variable "gke_node_count" {
  description = "GKE initial node count"
  type        = number
  default     = 3
}

variable "gke_min_nodes" {
  description = "GKE minimum nodes for autoscaling"
  type        = number
  default     = 3
}

variable "gke_max_nodes" {
  description = "GKE maximum nodes for autoscaling"
  type        = number
  default     = 10
}

variable "gke_machine_type" {
  description = "GKE node machine type"
  type        = string
  default     = "n2-standard-4"
}

# Networking Configuration
variable "network_name" {
  description = "VPC network name"
  type        = string
  default     = "yawl-teradata-network"
}

variable "subnet_cidr" {
  description = "Subnet CIDR range"
  type        = string
  default     = "10.0.0.0/20"
}

variable "enable_private_endpoint" {
  description = "Enable private endpoint for Teradata"
  type        = bool
  default     = true
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to connect to Teradata"
  type        = list(string)
  default     = ["10.0.0.0/8"]
}

# Security Configuration
variable "enable_encryption_at_rest" {
  description = "Enable encryption at rest for Teradata"
  type        = bool
  default     = true
}

variable "enable_encryption_in_transit" {
  description = "Enable encryption in transit"
  type        = bool
  default     = true
}

variable "backup_retention_days" {
  description = "Number of days to retain backups"
  type        = number
  default     = 30
}

variable "enable_automatic_backups" {
  description = "Enable automatic backups"
  type        = bool
  default     = true
}

variable "backup_window_start_hour" {
  description = "Backup window start hour (UTC)"
  type        = number
  default     = 2
}

# Monitoring and Logging
variable "enable_monitoring" {
  description = "Enable Cloud Monitoring"
  type        = bool
  default     = true
}

variable "enable_logging" {
  description = "Enable Cloud Logging"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Log retention period in days"
  type        = number
  default     = 90
}

variable "alert_email" {
  description = "Email address for alerts"
  type        = string
}

variable "cpu_threshold_percent" {
  description = "CPU utilization threshold for alerts (%)"
  type        = number
  default     = 80
}

variable "memory_threshold_percent" {
  description = "Memory utilization threshold for alerts (%)"
  type        = number
  default     = 85
}

variable "disk_threshold_percent" {
  description = "Disk utilization threshold for alerts (%)"
  type        = number
  default     = 90
}

# Tagging
variable "labels" {
  description = "GCP labels to apply to all resources"
  type        = map(string)
  default = {
    application = "yawl"
    database    = "teradata"
    managed_by  = "terraform"
  }
}

variable "tags" {
  description = "Tags to apply to network resources"
  type        = list(string)
  default     = ["yawl", "teradata", "production"]
}

# Docker and Container Configuration
variable "docker_image_repository" {
  description = "Docker image repository for YAWL"
  type        = string
  default     = "yawl-teradata"
}

variable "docker_image_tag" {
  description = "Docker image tag"
  type        = string
  default     = "latest"
}

# High Availability
variable "enable_multi_zone_deployment" {
  description = "Deploy across multiple availability zones"
  type        = bool
  default     = true
}

variable "enable_cross_region_replication" {
  description = "Enable cross-region replication for disaster recovery"
  type        = bool
  default     = false
}

variable "replica_region" {
  description = "Secondary region for cross-region replication"
  type        = string
  default     = "us-west1"
}

# Performance Tuning
variable "teradata_connection_pool_size" {
  description = "Teradata connection pool size"
  type        = number
  default     = 20
}

variable "teradata_query_timeout_seconds" {
  description = "Teradata query timeout in seconds"
  type        = number
  default     = 600
}

variable "enable_query_caching" {
  description = "Enable query result caching"
  type        = bool
  default     = true
}
