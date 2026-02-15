#------------------------------------------------------------------------------
# YAWL Oracle Cloud Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "compartment_id" {
  type = string
}

variable "tenancy_id" {
  type    = string
  default = ""
}

variable "region" {
  type = string
}

variable "vcn_id" {
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
  default = "v1.28.3"
}

variable "availability_domains" {
  type    = list(string)
  default = []
}

variable "node_shape" {
  type    = string
  default = "VM.Standard.E4.Flex"
}

variable "node_shape_flex" {
  type    = bool
  default = true
}

variable "node_ocpus" {
  type    = number
  default = 4
}

variable "node_memory_gb" {
  type    = number
  default = 16
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

variable "node_image_id" {
  type    = string
  default = ""
}

variable "enable_autoscaling" {
  type    = bool
  default = true
}

variable "node_pool_labels" {
  type    = map(string)
  default = {}
}

variable "max_pods_per_node" {
  type    = number
  default = 31
}

variable "ssh_public_key" {
  type    = string
  default = ""
}

variable "enable_workload_node_pool" {
  type    = bool
  default = true
}

variable "workload_node_shape" {
  type    = string
  default = "VM.Standard.E4.Flex"
}

variable "workload_node_ocpus" {
  type    = number
  default = 8
}

variable "workload_node_memory_gb" {
  type    = number
  default = 32
}

variable "database_cpu_core_count" {
  type    = number
  default = 2
}

variable "database_storage_tbs" {
  type    = number
  default = 1
}

variable "database_auto_scaling" {
  type    = bool
  default = true
}

variable "database_backup_days" {
  type    = number
  default = 30
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

variable "create_compartment" {
  type    = bool
  default = false
}

variable "common_tags" {
  type    = map(string)
  default = {}
}
