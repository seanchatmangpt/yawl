#------------------------------------------------------------------------------
# YAWL IBM Cloud Marketplace - Terraform Variables
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
  default     = "prod"

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default     = {
    Team       = "platform"
    CostCenter = "marketplace"
    Owner      = "yawl-foundation"
  }
}

#------------------------------------------------------------------------------
# IBM Cloud Configuration
#------------------------------------------------------------------------------

variable "ibm_api_key" {
  description = "IBM Cloud API key"
  type        = string
  sensitive   = true
}

variable "ibm_region" {
  description = "IBM Cloud region"
  type        = string
  default     = "us-south"

  validation {
    condition     = contains(["us-south", "us-east", "eu-de", "eu-gb", "jp-tok", "au-syd"], var.ibm_region)
    error_message = "IBM Cloud region must be one of: us-south, us-east, eu-de, eu-gb, jp-tok, au-syd."
  }
}

variable "ibm_resource_group" {
  description = "IBM Cloud resource group name"
  type        = string
  default     = "Default"
}

variable "availability_zones" {
  description = "List of availability zones to deploy to"
  type        = list(string)
  default     = ["1", "2", "3"]
}

#------------------------------------------------------------------------------
# Networking Configuration
#------------------------------------------------------------------------------

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

variable "enable_private_endpoint" {
  description = "Enable private endpoint for cluster"
  type        = bool
  default     = false
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to access resources"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

#------------------------------------------------------------------------------
# IBM Kubernetes Service (IKS) Configuration
#------------------------------------------------------------------------------

variable "kubernetes_version" {
  description = "Kubernetes version for the cluster"
  type        = string
  default     = "1.28"
}

variable "iks_flavor" {
  description = "Flavor for IKS worker nodes"
  type        = string
  default     = "bx2.4x16"
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

variable "enable_node_auto_scaling" {
  description = "Enable cluster autoscaler"
  type        = bool
  default     = true
}

variable "node_pool_labels" {
  description = "Labels to apply to node pool"
  type        = map(string)
  default     = {
    environment = "prod"
    workload    = "general"
  }
}

#------------------------------------------------------------------------------
# Databases for PostgreSQL Configuration
#------------------------------------------------------------------------------

variable "database_plan" {
  description = "Databases for PostgreSQL plan"
  type        = string
  default     = "standard"
}

variable "database_flavor" {
  description = "Database deployment flavor"
  type        = string
  default     = "standard"
}

variable "database_cpu_cores" {
  description = "CPU cores per database member"
  type        = number
  default     = 4
}

variable "database_memory_mb" {
  description = "Memory in MB per database member"
  type        = number
  default     = 4096
}

variable "database_disk_mb" {
  description = "Disk in MB per database member"
  type        = number
  default     = 102400
}

variable "database_username" {
  description = "Database admin username"
  type        = string
  default     = "yawl_admin"
}

variable "database_password" {
  description = "Database admin password"
  type        = string
  sensitive   = true
}

variable "database_whitelist_cidr" {
  description = "CIDR block allowed to access database"
  type        = string
  default     = "10.0.0.0/16"
}

#------------------------------------------------------------------------------
# Cloud Object Storage (COS) Configuration
#------------------------------------------------------------------------------

variable "cos_plan" {
  description = "Cloud Object Storage plan"
  type        = string
  default     = "standard"
}

variable "cos_storage_class" {
  description = "COS storage class"
  type        = string
  default     = "standard"
}

variable "storage_bucket_names" {
  description = "Names for storage buckets to create"
  type        = list(string)
  default     = ["data", "logs", "backups"]
}

variable "storage_lifecycle_days" {
  description = "Days before transitioning to cold storage"
  type        = number
  default     = 90
}

variable "storage_archive_days" {
  description = "Days before archiving"
  type        = number
  default     = 365
}

#------------------------------------------------------------------------------
# Security Configuration
#------------------------------------------------------------------------------

variable "enable_encryption" {
  description = "Enable encryption with Key Protect"
  type        = bool
  default     = true
}

variable "enable_secrets_manager" {
  description = "Enable IBM Cloud Secrets Manager"
  type        = bool
  default     = true
}

#------------------------------------------------------------------------------
# Monitoring Configuration
#------------------------------------------------------------------------------

variable "enable_monitoring" {
  description = "Enable IBM Cloud Monitoring with Sysdig"
  type        = bool
  default     = true
}

variable "monitoring_plan" {
  description = "Monitoring plan"
  type        = string
  default     = "graduated-tier"
}

variable "enable_logging" {
  description = "Enable IBM Cloud Log Analysis"
  type        = bool
  default     = true
}

variable "logging_plan" {
  description = "Log Analysis plan"
  type        = string
  default     = "standard"
}

#------------------------------------------------------------------------------
# IBM Cloud Internet Services Configuration
#------------------------------------------------------------------------------

variable "enable_cdn_waf" {
  description = "Enable IBM Cloud Internet Services (CDN/WAF)"
  type        = bool
  default     = true
}

variable "cis_plan" {
  description = "IBM Cloud Internet Services plan"
  type        = string
  default     = "standard"
}

#------------------------------------------------------------------------------
# Kubernetes Deployment Configuration
#------------------------------------------------------------------------------

variable "kubernetes_namespace" {
  description = "Kubernetes namespace for YAWL"
  type        = string
  default     = "yawl"
}

variable "log_level" {
  description = "YAWL log level"
  type        = string
  default     = "INFO"
}

#------------------------------------------------------------------------------
# YAWL Application Configuration
#------------------------------------------------------------------------------

variable "helm_repository" {
  description = "Helm repository URL for YAWL"
  type        = string
  default     = "https://yawlfoundation.github.io/yawl/helm"
}

variable "helm_chart_name" {
  description = "Helm chart name"
  type        = string
  default     = "yawl"
}

variable "helm_chart_version" {
  description = "Helm chart version"
  type        = string
  default     = "5.2.0"
}

variable "yawl_image_repository" {
  description = "YAWL container image repository"
  type        = string
  default     = "icr.io/yawl/engine"
}

variable "yawl_image_tag" {
  description = "YAWL container image tag"
  type        = string
  default     = "5.2.0"
}

variable "yawl_replica_count" {
  description = "Number of YAWL replicas"
  type        = number
  default     = 2
}

variable "yawl_heap_size" {
  description = "YAWL JVM heap size"
  type        = string
  default     = "1g"
}

variable "yawl_max_heap_size" {
  description = "YAWL JVM max heap size"
  type        = string
  default     = "2g"
}

variable "yawl_cpu_request" {
  description = "YAWL CPU request"
  type        = string
  default     = "500m"
}

variable "yawl_memory_request" {
  description = "YAWL memory request"
  type        = string
  default     = "1Gi"
}

variable "yawl_cpu_limit" {
  description = "YAWL CPU limit"
  type        = string
  default     = "2000m"
}

variable "yawl_memory_limit" {
  description = "YAWL memory limit"
  type        = string
  default     = "4Gi"
}

variable "yawl_autoscaling_enabled" {
  description = "Enable YAWL pod autoscaling"
  type        = bool
  default     = true
}

variable "yawl_min_replicas" {
  description = "Minimum YAWL replicas for autoscaling"
  type        = number
  default     = 2
}

variable "yawl_max_replicas" {
  description = "Maximum YAWL replicas for autoscaling"
  type        = number
  default     = 10
}

variable "yawl_cpu_target" {
  description = "Target CPU utilization percentage"
  type        = number
  default     = 70
}

variable "yawl_memory_target" {
  description = "Target memory utilization percentage"
  type        = number
  default     = 80
}

variable "yawl_ingress_enabled" {
  description = "Enable Ingress for YAWL"
  type        = bool
  default     = true
}

variable "yawl_ingress_hosts" {
  description = "Ingress hosts configuration"
  type = list(object({
    host  = string
    paths = list(object({
      path     = string
      pathType = string
    }))
  }))
  default = [
    {
      host = "yawl.example.com"
      paths = [
        {
          path     = "/yawl"
          pathType = "Prefix"
        }
      ]
    }
  ]
}

variable "yawl_ingress_tls" {
  description = "Ingress TLS configuration"
  type = list(object({
    secretName = string
    hosts      = list(string)
  }))
  default = [
    {
      secretName = "yawl-tls"
      hosts      = ["yawl.example.com"]
    }
  ]
}
