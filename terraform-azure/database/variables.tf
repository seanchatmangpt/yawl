variable "resource_group_name" {
  description = "Name of the resource group"
  type        = string
}

variable "location" {
  description = "Azure region for resources"
  type        = string
}

variable "server_name" {
  description = "Name of the database server"
  type        = string
}

variable "database_name" {
  description = "Name of the database"
  type        = string
}

variable "administrator_login" {
  description = "Administrator username for the database"
  type        = string
  sensitive   = true
}

variable "administrator_password" {
  description = "Administrator password for the database"
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_sku_name" {
  description = "SKU name for the database"
  type        = string
  default     = "B_Standard_B1s"
}

variable "db_version" {
  description = "MySQL database version"
  type        = string
  default     = "8.0"
}

variable "backup_retention_days" {
  description = "Backup retention in days"
  type        = number
  default     = 7
  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35
    error_message = "Backup retention must be between 1 and 35 days."
  }
}

variable "geo_redundant_backup_enabled" {
  description = "Enable geo-redundant backups"
  type        = bool
  default     = false
}

variable "enable_ha" {
  description = "Enable high availability with zone redundancy"
  type        = bool
  default     = false
}

variable "subnet_id" {
  description = "ID of the subnet for database delegation"
  type        = string
  default     = ""
}

variable "enable_private_endpoint" {
  description = "Enable private endpoint for the database"
  type        = bool
  default     = false
}

variable "app_username" {
  description = "Username for application database user"
  type        = string
  default     = "appuser"
  sensitive   = true
}

variable "app_password" {
  description = "Password for application database user"
  type        = string
  sensitive   = true
  default     = ""
}

variable "enable_backup" {
  description = "Enable automated backups"
  type        = bool
  default     = false
}

variable "backup_redundancy" {
  description = "Backup vault redundancy level"
  type        = string
  default     = "LocallyRedundant"
  validation {
    condition     = contains(["LocallyRedundant", "GeoRedundant"], var.backup_redundancy)
    error_message = "Backup redundancy must be LocallyRedundant or GeoRedundant."
  }
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}

variable "log_analytics_workspace_id" {
  description = "ID of the Log Analytics Workspace for diagnostics"
  type        = string
  default     = ""
}
