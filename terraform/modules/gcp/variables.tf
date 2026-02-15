#------------------------------------------------------------------------------
# YAWL GCP Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "gcp_project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "zone" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "kubernetes_version" {
  type    = string
  default = "1.28"
}

variable "node_machine_type" {
  type    = string
  default = "e2-standard-4"
}

variable "node_count_min" {
  type    = number
  default = 3
}

variable "node_count_max" {
  type    = number
  default = 10
}

variable "node_disk_size_gb" {
  type    = number
  default = 100
}

variable "enable_autoscaling" {
  type    = bool
  default = true
}

variable "preemptible_nodes" {
  type    = bool
  default = false
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

variable "database_version" {
  type    = string
  default = "15"
}

variable "database_tier" {
  type    = string
  default = "db-custom-4-16384"
}

variable "database_storage_gb" {
  type    = number
  default = 100
}

variable "database_multi_az" {
  type    = bool
  default = true
}

variable "database_backup_days" {
  type    = number
  default = 30
}

variable "database_deletion_protection" {
  type    = bool
  default = true
}

variable "database_encryption" {
  type    = bool
  default = true
}

variable "database_username" {
  type    = string
  default = "yawl_admin"
}

variable "enable_query_insights" {
  type    = bool
  default = true
}

variable "storage_bucket_names" {
  type    = list(string)
  default = ["data", "logs", "backups"]
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

variable "kms_key_id" {
  type    = string
  default = ""
}

variable "enable_private_endpoint" {
  type    = bool
  default = true
}

variable "authorized_networks" {
  type    = list(string)
  default = []
}

variable "common_tags" {
  type    = map(string)
  default = {}
}
