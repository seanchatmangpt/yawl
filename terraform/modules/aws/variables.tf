#------------------------------------------------------------------------------
# YAWL AWS Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "availability_zones" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
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

variable "node_instance_type" {
  type    = string
  default = "m5.xlarge"
}

variable "node_ami_type" {
  type    = string
  default = "AL2_x86_64"
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

variable "database_engine" {
  type    = string
  default = "postgresql"
}

variable "database_version" {
  type    = string
  default = "15"
}

variable "database_instance_class" {
  type    = string
  default = "db.r6g.xlarge"
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

variable "ssl_certificate_arn" {
  type    = string
  default = ""
}

variable "enable_private_endpoint" {
  type    = bool
  default = true
}

variable "common_tags" {
  type    = map(string)
  default = {}
}
