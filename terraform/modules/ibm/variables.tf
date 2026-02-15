#------------------------------------------------------------------------------
# YAWL IBM Cloud Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "region" {
  type = string
}

variable "resource_group" {
  type    = string
  default = "default"
}

variable "vpc_name" {
  type = string
}

variable "vpc_id" {
  type    = string
  default = ""
}

variable "private_subnet_ids" {
  type    = list(string)
  default = []
}

variable "public_subnet_ids" {
  type    = list(string)
  default = []
}

variable "private_subnet_names" {
  type    = list(string)
  default = []
}

variable "public_subnet_names" {
  type    = list(string)
  default = []
}

variable "zones" {
  type    = list(string)
  default = []
}

variable "kubernetes_version" {
  type    = string
  default = "1.28"
}

variable "node_flavor" {
  type    = string
  default = "bx2.4x16"
}

variable "node_hardware" {
  type    = string
  default = "shared"
}

variable "node_count_min" {
  type    = number
  default = 3
}

variable "node_count_max" {
  type    = number
  default = 10
}

variable "enable_autoscaling" {
  type    = bool
  default = true
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

variable "enable_workload_node_pool" {
  type    = bool
  default = true
}

variable "workload_node_flavor" {
  type    = string
  default = "bx2.8x32"
}

variable "workload_node_count_min" {
  type    = number
  default = 2
}

variable "workload_node_count_max" {
  type    = number
  default = 8
}

variable "enable_private_endpoint" {
  type    = bool
  default = true
}

variable "database_name" {
  type    = string
  default = "yawl-db"
}

variable "database_plan" {
  type    = string
  default = "standard"
}

variable "database_version" {
  type    = string
  default = "15"
}

variable "database_members" {
  type    = number
  default = 3
}

variable "database_memory_gb" {
  type    = number
  default = 4
}

variable "database_disk_gb" {
  type    = number
  default = 100
}

variable "database_cpu_count" {
  type    = number
  default = 4
}

variable "cos_bucket_names" {
  type    = list(string)
  default = ["data", "logs", "backups"]
}

variable "cos_plan" {
  type    = string
  default = "standard"
}

variable "storage_class" {
  type    = string
  default = "standard"
}

variable "storage_versioning" {
  type    = bool
  default = true
}

variable "storage_lifecycle_days" {
  type    = number
  default = 90
}

variable "storage_archive_days" {
  type    = number
  default = 365
}

variable "enable_retention" {
  type    = bool
  default = false
}

variable "retention_days" {
  type    = number
  default = 90
}

variable "enable_archive" {
  type    = bool
  default = true
}

variable "enable_expiry" {
  type    = bool
  default = true
}

variable "enable_secrets_manager" {
  type    = bool
  default = true
}

variable "common_tags" {
  type    = map(string)
  default = {}
}
