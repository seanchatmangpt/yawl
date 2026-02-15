#------------------------------------------------------------------------------
# YAWL Azure Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type    = string
  default = ""
}

variable "vnet_id" {
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

variable "node_vm_size" {
  type    = string
  default = "Standard_D4s_v3"
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

variable "sku_tier" {
  type    = string
  default = "Paid"
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

variable "workload_node_vm_size" {
  type    = string
  default = "Standard_D8s_v3"
}

variable "workload_node_count_min" {
  type    = number
  default = 2
}

variable "workload_node_count_max" {
  type    = number
  default = 8
}

variable "database_version" {
  type    = string
  default = "15"
}

variable "database_sku" {
  type    = string
  default = "GP_Standard_D4s_v3"
}

variable "database_storage_gb" {
  type    = number
  default = 100
}

variable "database_geo_redundant" {
  type    = bool
  default = true
}

variable "database_backup_days" {
  type    = number
  default = 30
}

variable "database_username" {
  type    = string
  default = "yawl_admin"
}

variable "enable_aad_admin" {
  type    = bool
  default = false
}

variable "aad_admin_login" {
  type    = string
  default = ""
}

variable "aad_admin_object_id" {
  type    = string
  default = ""
}

variable "aad_tenant_id" {
  type    = string
  default = ""
}

variable "storage_container_names" {
  type    = list(string)
  default = ["data", "logs", "backups"]
}

variable "storage_account_tier" {
  type    = string
  default = "Standard"
}

variable "storage_replication" {
  type    = string
  default = "GRS"
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

variable "storage_allowed_ips" {
  type    = list(string)
  default = []
}

variable "enable_monitoring" {
  type    = bool
  default = true
}

variable "log_retention_days" {
  type    = number
  default = 90
}

variable "enable_managed_identity" {
  type    = bool
  default = true
}

variable "http_proxy_config" {
  type = object({
    http_proxy  = string
    https_proxy = string
    no_proxy    = list(string)
  })
  default = null
}

variable "enable_private_endpoint" {
  type    = bool
  default = true
}

variable "common_tags" {
  type    = map(string)
  default = {}
}
